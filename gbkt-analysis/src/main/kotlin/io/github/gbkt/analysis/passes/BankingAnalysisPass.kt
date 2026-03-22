/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
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
    }

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val config = context.config
        val effectiveCapacity = (ROM_BANK_SIZE * config.bankFillErrorThreshold).roundToInt()

        // 1. Build code units from scenes
        val codeUnits = buildCodeUnits(game, config.bytesPerStatement)

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
                ) ?: return bankOverflowError(unit, config.maxBanks)
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
     */
    private fun buildCodeUnits(game: GameIR, bytesPerStatement: Int): List<CodeUnit> =
        game.scenes.map { scene ->
            val opCount = scene.enterOps.size + scene.frameOps.size + scene.exitOps.size
            CodeUnit(id = scene.id, estimatedBytes = opCount * bytesPerStatement)
        }

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

    /** Returns a [PassResult.Failed] with an informative bank overflow diagnostic. */
    private fun bankOverflowError(unit: CodeUnit, maxBanks: Int): PassResult.Failed =
        PassResult.Failed(
            listOf(
                Diagnostic(
                    id = "ANLZ-02",
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
                        id = "ANLZ-02",
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
