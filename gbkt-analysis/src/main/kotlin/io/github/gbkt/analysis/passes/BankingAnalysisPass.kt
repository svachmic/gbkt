/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.DiagnosticCode
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.nextPowerOfTwo
import kotlin.math.roundToInt

/**
 * Analysis pass that automatically allocates ROM banks for scene code using First-Fit-Decreasing
 * (FFD) bin-packing with scene locality tie-breaking.
 *
 * ### Bank layout
 * - **Bank 0 (HOME):** Reserved for `main()`, `navigate_to_scene()`, trampolines, and forward
 *   declarations. **No scene code is placed here.**
 * - **Banks 1 .. maxBanks-1:** Scene enter/frame/exit functions are packed here.
 *
 * ### Algorithm
 * 1. Estimate each scene's code size: `(enterOps + frameOps + exitOps).size * bytesPerStatement`.
 * 2. Sort code units by estimated size descending (FFD ordering).
 * 3. For each unit: try the preferred bank (locality) first, then scan banks 1..maxBanks-1 for the
 *    first bank with sufficient remaining capacity.
 * 4. If no bank fits, return [PassResult.Failed] with a bank overflow diagnostic.
 *
 * ### Scene locality
 * If scene A has a [NavigateTo] pointing to scene B, the pass records an edge A→B in a transition
 * graph. When assigning B, if A is already in a bank that has capacity, that bank is preferred.
 * This keeps transitioning scenes co-located to reduce bank-switch overhead.
 *
 * ### Diagnostics
 * - **WARNING (ANLZ-02):** Bank fill level exceeds [AnalysisConfig.bankFillWarningThreshold].
 * - **ERROR (ANLZ-02):** No bank can accommodate a code unit — [PassResult.Failed] returned.
 *
 * The [PassContext.bankAssignments] map is extended with one [BankSlot] per scene after this pass
 * runs. Downstream codegen (plan 04-08) reads these slots to emit `BANKED` C functions and
 * HOME-bank trampoline stubs.
 */
class BankingAnalysisPass : AnalysisPass {

    private companion object {
        const val ROM_BANK_SIZE = 16_384

        /**
         * Conservative HOME-bank budget for scene code, used by the single-scene fast-path.
         *
         * Per 09.1-04 (gap-closure 2026-05-14, Option B decision): The HOME bank is 16 KB total. A
         * typical small game's main.c + GBDK helpers (sound driver stub, sprite helpers, input
         * helpers, navigate_to_scene, delay_frames, main loop) consume 8-12 KB in HOME. A 4 KB
         * scene budget leaves comfortable headroom: simple_physics has ~180 bytes of scene code (30
         * ops * 6 bytes/op), and any other small single-scene game that legitimately fits HOME will
         * also be well within 4 KB.
         *
         * Anti-overfitting rail (D-10 extended scope): this budget is STATIC and CONSERVATIVE. Do
         * NOT compute it dynamically from "actual HOME size minus actual scene size" — that would
         * couple the analysis pass to backend-specific pipeline state and introduce a circular
         * dependency (analysis → pipeline → analysis).
         */
        const val HOME_BANK_SCENE_BUDGET = 4_096

        /**
         * Phase 12.2 REQ-5: cumulative tilemap-bank byte budget. A single ROM bank is 16 KB (16384
         * bytes); we reserve 2 KB for headers/prologue/safety, leaving 14336 bytes for actual
         * tilemap data. SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS captures the deferred per-zone-bank
         * work that would obsolete this single-threshold check.
         */
        const val TILEMAP_BANK_THRESHOLD = 14_336
    }

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val config = context.config
        val effectiveCapacity = (ROM_BANK_SIZE * config.bankFillErrorThreshold).roundToInt()

        // Early guards, in priority order: WR-03 declared-romBanks-over-cartridge-cap, then the
        // Phase 12.2 REQ-5 / D-04 cumulative tilemap-bank overflow check. The overflow check is
        // computed ONCE up front so both the fast-path and the bin-packer path below consult the
        // same result without duplicating the check. It only runs when filesystem access is
        // available (assetRoot non-null) — existing JVM-only tests that leave assetRoot at its
        // null default are unaffected.
        val earlyFailure =
            checkDeclaredBanksCap(game)
                ?: context.assetRoot?.let { checkTilemapBankOverflow(game, it) }
        if (earlyFailure != null) return earlyFailure

        // 1. Build code units from scenes
        val codeUnits = buildCodeUnits(game, config.bytesPerStatement)

        // Per 09.1-04 (gap-closure 2026-05-14, Option B): single-scene games whose code fits
        // HOME budget are placed in bank 0 to avoid spurious MBC5 upgrade. The bin-packer below
        // handles multi-scene and oversize-single-scene cases.
        // Per 09.1-06 (gap-closure 2026-05-15, CR-01): the static `game.scenes.size == 1`
        // guard reinstates the Plan 04 must_haves.truths spec verbatim — "the fast-path
        // triggers ONLY when scenes.size == 1 (authored scene count, static property of the
        // GameIR)". Without this static guard, a multi-scene game with N-1 zero-op stub
        // scenes collapses to codeUnits.size == 1 after the D-10 filter and incorrectly
        // enters the fast-path, placing the one real scene's code in bank 0 even when the
        // author wrote a multi-scene game. The dual guard is strictly more restrictive than
        // either clause alone.

        if (
            game.scenes.size == 1 &&
                codeUnits.size == 1 &&
                codeUnits[0].estimatedBytes <= HOME_BANK_SCENE_BUDGET
        ) {
            val unit = codeUnits[0]
            val fastAssignments = mapOf(unit.id to BankSlot(bank = 0))
            return PassResult.Success(
                context.copy(bankAssignments = context.bankAssignments + fastAssignments)
            )
        }

        // 2. Build scene transition graph for locality tie-breaking
        val transitionGraph = buildTransitionGraph(game)

        // 3. FFD bin-packing: sort by size descending, pack into banks 1..maxBanks-1
        //    Bank 0 is HOME — skip it entirely.
        val bankUsed = mutableMapOf<Int, Int>() // bank number -> bytes used
        val assignments = mutableMapOf<String, BankSlot>()
        val sorted = codeUnits.sortedByDescending { it.estimatedBytes }

        for (unit in sorted) {
            val preferredBank = findPreferredBank(unit, assignments, transitionGraph)
            val assignedBank =
                findFirstFit(
                    unit.estimatedBytes,
                    bankUsed,
                    effectiveCapacity,
                    preferredBank,
                    config.maxBanks,
                ) ?: return bankOverflowError(unit, config.maxBanks, context)
            bankUsed[assignedBank] = (bankUsed[assignedBank] ?: 0) + unit.estimatedBytes
            assignments[unit.id] = BankSlot(assignedBank)
        }

        // 4. Generate diagnostics for bank fill levels
        val diagnostics = generateBankDiagnostics(bankUsed, ROM_BANK_SIZE, config)

        val newContext =
            context.copy(
                bankAssignments = context.bankAssignments + assignments,
                diagnostics = context.diagnostics + diagnostics,
            )
        return PassResult.Success(newContext)
    }

    /**
     * WR-03: if the author's declared romBanks exceeds the cartridge type's cap, emit a distinct
     * actionable error before any bin-packing. This catches the configuration mistake ("MBC1 can't
     * do 512 banks") rather than falling through to a confusing generic overflow message. Returns
     * null when the declared value is absent or within the cap.
     */
    private fun checkDeclaredBanksCap(game: GameIR): PassResult? {
        val declaredBanks = game.config.romBanks
        val cartridge = game.config.cartridge
        if (declaredBanks == null || declaredBanks <= cartridge.maxRomBanks) return null
        return PassResult.Failed(
            listOf(
                Diagnostic(
                    code = DiagnosticCode.VRAM_CAPACITY,
                    severity = Severity.ERROR,
                    message =
                        "${cartridge.name} supports at most ${cartridge.maxRomBanks} ROM banks; " +
                            "you declared romBanks=$declaredBanks. " +
                            "Switch to a larger cartridge type or reduce romBanks.",
                    location = "CartridgeConfig.romBanks",
                    suggestion =
                        "Use a cartridge type with a higher maxRomBanks (e.g. MBC5 supports 256), " +
                            "or set romBanks <= ${cartridge.maxRomBanks}.",
                )
            )
        )
    }

    // -------------------------------------------------------------------------
    // Code unit building
    // -------------------------------------------------------------------------

    /**
     * Represents a single banked code unit (one scene's enter+frame+exit functions combined).
     *
     * @property id Scene ID from [SceneIR.id].
     * @property estimatedBytes Heuristic byte count for the scene's generated C code.
     */
    private data class CodeUnit(val id: String, val estimatedBytes: Int)

    /**
     * Builds one [CodeUnit] per scene. Size is the total op count across all three lifecycle
     * functions multiplied by [bytesPerStatement].
     *
     * ### D-10 zero-op filter
     * The trailing `.filter` excludes scenes whose `enterOps`, `frameOps`, AND `exitOps` are ALL
     * empty (combined op count of zero). Such scenes produce no banked C functions in
     * `SceneVisitor`, so they do not need a bank slot. Partial-empty scenes (e.g. zero `enterOps`
     * but a non-empty `frameOps`) survive the filter and DO get assigned a bank.
     *
     * Caveat: when a filtered (all-empty) scene sits on a transition path (e.g. `A → stub → C`),
     * the transition edges through it remain in [buildTransitionGraph] but the lookup in
     * [findPreferredBank] returns null for the filtered scene — so the A↔C locality hint is lost.
     * Acceptable for the present examples; revisit if multi-scene games introduce zero-op stub
     * scenes that are meaningful waypoints (see WR-04 in 09.1-REVIEW.md).
     */
    private fun buildCodeUnits(game: GameIR, bytesPerStatement: Int): List<CodeUnit> =
        game.scenes
            .map { scene ->
                val opCount = scene.enterOps.size + scene.frameOps.size + scene.exitOps.size
                CodeUnit(id = scene.id, estimatedBytes = opCount * bytesPerStatement)
            }
            // D-10: skip scenes whose enter+frame+exit op count is zero — they produce no banked
            // functions in SceneVisitor and so do not need a bank slot. Partial-empty scenes
            // (e.g. zero enterOps but non-empty frameOps) still get assigned a bank.
            .filter { it.estimatedBytes > 0 }

    // -------------------------------------------------------------------------
    // Bank selection
    // -------------------------------------------------------------------------

    /**
     * Returns the preferred bank for [unit] based on scene locality: if any scene that [unit]
     * transitions to is already assigned, prefer that bank. Also checks reverse edges (scenes that
     * transition to this scene). Returns null if no locality preference exists.
     */
    private fun findPreferredBank(
        unit: CodeUnit,
        assignments: Map<String, BankSlot>,
        transitionGraph: Map<String, Set<String>>,
    ): Int? {
        // Forward edges: scenes this unit transitions to
        val targets = transitionGraph[unit.id] ?: emptySet()
        for (target in targets) {
            val slot = assignments[target]
            if (slot != null) return slot.bank
        }
        // Reverse edges: scenes that transition to this unit
        for ((sourceId, destinationIds) in transitionGraph) {
            if (unit.id in destinationIds) {
                val slot = assignments[sourceId]
                if (slot != null) return slot.bank
            }
        }
        return null
    }

    /**
     * Finds the first bank (starting from 1, skipping bank 0) that can hold [bytes] additional
     * bytes without exceeding [capacity]. [preferredBank] is tried first if non-null.
     *
     * @return The bank number to use, or null if no bank fits.
     */
    private fun findFirstFit(
        bytes: Int,
        bankUsed: Map<Int, Int>,
        capacity: Int,
        preferredBank: Int?,
        maxBanks: Int,
    ): Int? {
        // Try locality preference first
        if (preferredBank != null && preferredBank in 1 until maxBanks) {
            val used = bankUsed[preferredBank] ?: 0
            if (used + bytes <= capacity) return preferredBank
        }
        // Fall back to first-fit scan (skip bank 0 — HOME)
        for (bank in 1 until maxBanks) {
            val used = bankUsed[bank] ?: 0
            if (used + bytes <= capacity) return bank
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Error and diagnostic generation
    // -------------------------------------------------------------------------

    /**
     * Phase 12.2 REQ-5 / D-04: cumulative tilemap-bank overflow guard.
     *
     * Sums per-zone tilemap byte counts (computed from real PNG IHDR dimensions divided by 64 — one
     * byte per 8×8 tile index). Returns a [PassResult.Failed] when the cumulative total exceeds
     * [TILEMAP_BANK_THRESHOLD]; otherwise returns null.
     *
     * Zones whose PNG cannot be resolved (file missing, decode failure) are SKIPPED — this is a
     * budget check, not a correctness check. Missing-file diagnostics flow through
     * `io.github.gbkt.gradle.tasks.ConvertZoneTilesetsTask` (Phase 12.2 REQ-4).
     *
     * Implementation note (D-claude-3 duplicate-inline): the ImageIO read pattern mirrors
     * `ConvertZoneTilesetsTask.kt` lines 209-218. If a third call site appears, extract to a shared
     * helper in `gbkt-core` (currently the duplicate is too small to justify the move).
     */
    private fun checkTilemapBankOverflow(
        game: GameIR,
        assetRoot: java.io.File,
    ): PassResult.Failed? {
        val zonesWithSizes =
            game.zones.mapNotNull { zone -> tilemapByteCountForZone(zone, assetRoot) }
        val cumulative = zonesWithSizes.sumOf { it.second }
        if (cumulative <= TILEMAP_BANK_THRESHOLD) return null

        val zoneBreakdown = zonesWithSizes.joinToString(", ") { (id, sz) -> "'$id' ($sz bytes)" }
        return PassResult.Failed(
            listOf(
                Diagnostic(
                    code = DiagnosticCode.TILEMAP_BANK_OVERFLOW,
                    severity = Severity.ERROR,
                    message =
                        "Tilemap bank overflow: zones sum to $cumulative bytes " +
                            "(threshold $TILEMAP_BANK_THRESHOLD bytes = 16 KB bank minus " +
                            "2 KB margin). " +
                            "Zone breakdown: $zoneBreakdown. " +
                            "See SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS for the deferred " +
                            "per-zone-bank fix.",
                    location = "tilemap bank",
                    suggestion =
                        "Reduce tilemap PNG sizes, or split tilemap data across per-zone " +
                            "banks (SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS — out of scope for " +
                            "Phase 12.2).",
                )
            )
        )
    }

    /**
     * Resolves [zone]'s tilemap PNG against [assetRoot] and returns `zoneId to byteCount` for a
     * successful decode. Returns null when the zone has no PNG path, the file is missing, or
     * ImageIO cannot decode it — overflow checking is a budget hint, not a correctness check, so
     * unresolved zones are silently skipped (missing-file diagnostics flow through
     * `io.github.gbkt.gradle.tasks.ConvertZoneTilesetsTask` per Phase 12.2 REQ-4).
     */
    private fun tilemapByteCountForZone(
        zone: io.github.gbkt.core.ir.ZoneIR,
        assetRoot: java.io.File,
    ): Pair<String, Int>? {
        val pngRelPath = zone.tilemapPath ?: zone.tilesetPath ?: return null
        val pngFile = java.io.File(assetRoot, pngRelPath)
        if (!pngFile.isFile) return null
        val img = javax.imageio.ImageIO.read(pngFile) ?: return null
        val byteCount = (img.width / 8) * (img.height / 8)
        return zone.id to byteCount
    }

    /**
     * Returns a [PassResult.Failed] with an informative bank overflow diagnostic.
     *
     * When [context.game.config.romBanks] is non-null (explicitly declared by the author) and
     * equals the effective [maxBanks] cap, this is a D-06 undersized-romBanks error. In that case
     * the diagnostic uses the D-06 actionable message shape ("romBanks=$declared too small; … Set
     * romBanks >= $derived or remove romBanks to auto-derive.") so the author knows how to fix it.
     * Otherwise the generic bank-overflow message is emitted.
     */
    private fun bankOverflowError(
        unit: CodeUnit,
        maxBanks: Int,
        context: PassContext,
    ): PassResult.Failed {
        val declaredRomBanks = context.game.config.romBanks
        if (declaredRomBanks != null && declaredRomBanks <= maxBanks) {
            // D-06: author explicitly set romBanks too small. Derive the minimum needed
            // by probing with unconstrained maxBanks (type max from config).
            val typeMax = context.config.maxBanks.coerceAtLeast(256)
            val probeConfig = context.config.copy(maxBanks = typeMax)
            val probeCtx =
                context.copy(
                    game =
                        context.game.copy(
                            config =
                                context.game.config.copy(
                                    romBanks = null
                                ) // probe must not re-enter D-06
                        ),
                    config = probeConfig,
                    outputDirectory = null, // Pitfall 1: suppress BudgetAuditPass file writes
                    bankAssignments = emptyMap(),
                    diagnostics = emptyList(),
                )
            val probePass = BankingAnalysisPass()
            val probeResult = probePass.run(probeCtx)
            val maxAssigned =
                if (probeResult is PassResult.Success)
                    probeResult.context.bankAssignments.values.maxOfOrNull { it.bank } ?: 0
                else 0
            val cartridgeCap = context.game.config.cartridge.maxRomBanks
            val derived = minOf(maxOf(2, nextPowerOfTwo(maxAssigned + 1)), cartridgeCap)
            return PassResult.Failed(
                listOf(
                    Diagnostic(
                        code = DiagnosticCode.BANK_CAPACITY,
                        severity = Severity.ERROR,
                        message =
                            "romBanks=$declaredRomBanks too small; banking analysis needs $derived. " +
                                "Set romBanks >= $derived or remove romBanks to auto-derive.",
                        location = "CartridgeConfig.romBanks",
                        suggestion =
                            "Set romBanks >= $derived in your config {} block, " +
                                "or remove romBanks to auto-derive the count.",
                    )
                )
            )
        }
        return PassResult.Failed(
            listOf(
                Diagnostic(
                    code = DiagnosticCode.BANK_CAPACITY,
                    severity = Severity.ERROR,
                    message =
                        "Bank overflow: cannot fit '${unit.id}' (${unit.estimatedBytes} bytes). " +
                            "All ${maxBanks - 1} banked ROM banks (banks 1..${maxBanks - 1}) " +
                            "are full. Reduce scene code or increase maxBanks in AnalysisConfig.",
                    location = "scene '${unit.id}'",
                    suggestion =
                        "Split '${unit.id}' into smaller scenes, or increase maxBanks " +
                            "(current: $maxBanks).",
                )
            )
        )
    }

    /**
     * Generates WARNING diagnostics for banks whose fill level exceeds
     * [AnalysisConfig.bankFillWarningThreshold].
     */
    private fun generateBankDiagnostics(
        bankUsed: Map<Int, Int>,
        bankCapacity: Int,
        config: AnalysisConfig,
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        for ((bank, used) in bankUsed) {
            val fillRatio = used.toDouble() / bankCapacity
            if (fillRatio > config.bankFillWarningThreshold) {
                val fillPercent = (fillRatio * 100).roundToInt()
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.BANK_CAPACITY,
                        severity = Severity.WARNING,
                        message =
                            "Bank $bank is $fillPercent% full ($used / $bankCapacity bytes). " +
                                "Consider distributing code across more banks.",
                        location = "bank $bank",
                        suggestion =
                            "Move some scenes to a different bank or reduce scene code size.",
                    )
            }
        }
        return diagnostics
    }
}
