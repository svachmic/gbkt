/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.core.ir.ZoneIR

// =============================================================================
// SCENE VISITOR
// Translates IR v2 SceneIR nodes into typed C AST CFunction nodes.
// Generates {id}_enter, {id}_frame, {id}_exit lifecycle functions.
// Empty lifecycle handlers are skipped (not generated).
// =============================================================================

/**
 * Visitor that converts IR v2 [SceneIR] nodes to lists of typed C AST [CFunction] nodes.
 *
 * Each [SceneIR] produces up to 3 lifecycle functions:
 * - `{id}_enter` — from [SceneIR.enterOps], if non-empty, OR if [SceneIR.zoneRefs] is non-empty
 *   (zone-load prepend requires an enter function even when user enterOps is empty). Carries the
 *   sectionComment.
 * - `{id}_frame` — from [SceneIR.frameOps], if non-empty.
 * - `{id}_exit` — from [SceneIR.exitOps], if non-empty.
 *
 * Bank assignment: if [SceneIR.bankSlot] is non-null with bank > 0, the generated functions carry
 * [CFunction.bank] set to that bank number and [CFunction.isBanked] = true. If [bankSlot] is null
 * (no analysis ran, e.g., in tests), functions default to isBanked = true, bank = null (inherits
 * from CFile).
 *
 * All functions are marked [CFunction.isBanked] = true — scene code lives in banked ROM. The
 * sectionComment ("Scene: {id}") is added only to the first function (enter) so that the emitter
 * can output a block separator comment without repetition.
 *
 * Delegates script op translation to [ScriptOpVisitor]. Delegates scene enum generation to
 * [generateSceneEnum].
 */
object SceneVisitor {

    /**
     * Convert a [SceneIR] node to a list of [CFunction] nodes.
     *
     * Only lifecycle handlers with at least one [ScriptOp] are generated. Empty handlers are
     * skipped to avoid emitting empty C functions — with one exception: when [SceneIR.zoneRefs] is
     * non-empty, the enter function is always generated (even if enterOps is empty) because the
     * zone-load prepend requires an enter hook.
     *
     * If [SceneIR.bankSlot] is non-null with bank > 0, the generated functions have bank set to
     * that bank number. If bankSlot is null (default, no analysis ran), bank is null and isBanked
     * defaults to true for backward compatibility.
     *
     * @param scene The scene IR node to convert.
     * @param actors Actor list passed to [ExprVisitor] for collision-aware expression codegen.
     *   Defaults to empty list for backward-compatible usage without actor context.
     * @param variables Global variable list passed to [ExprVisitor] so signed-comparison RHS
     *   literals lower to [io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral] (Phase 9 Plan 04
     *   Bug A). Defaults to empty list for backward-compatible usage without variable context; the
     *   visitor then falls back to pre-fix [io.github.gbkt.backend.gbdk.codegen.ast.CLiteral]
     *   emission (unchanged behavior).
     * @param zoneBankAllocation Map from zone ID to allocated bank number (from
     *   [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.allocateZoneBanks]). Required
     *   when [SceneIR.zoneRefs] is non-empty — callers must pass this to thread the bank allocation
     *   context. Defaults to emptyMap() for backwards-compatible callers; the visitor will throw
     *   [IllegalStateException] if a scene has zoneRefs but the caller did not thread this map
     *   (caller-miss fails fast, per RESEARCH §Pitfall 2).
     * @param zones Full list of [ZoneIR] from [io.github.gbkt.core.ir.GameIR.zones]. Required when
     *   [SceneIR.zoneRefs] is non-empty for zone dimension lookup. Defaults to emptyList() for
     *   backwards-compatible callers.
     * @param gbcTarget True when GameIR.config.gbcTarget != GbcTarget.DMG — gates per-zone
     *   set_bkg_palette emission per Phase 12.9 D-01. Defaults to false for backward-compat
     *   (existing visitor tests that don't supply it skip palette upload, which matches their
     *   behavior of not having zones with tilesetPath set anyway).
     * @param isMbcGame True when cartridge.maxRomBanks > 2 (genuine MBC cartridge — MBC1, MBC5,
     *   etc.). When true AND the scene has bankSlot.bank > 0 AND no user-declared exit {} block, an
     *   empty BANKED `${scene.id}_exit` function is auto-synthesized (Req #15 / D-07). Defaults to
     *   false for backward-compat; existing tests that omit this parameter retain the old behavior
     *   of never auto-emitting an exit. ROM_ONLY games have maxRomBanks=2 and pass isMbcGame=false
     *   — their scenes land at bankSlot.bank=1 from BankingAnalysisPass FFD but must NOT gain a new
     *   `*_exit` function (no-growth gate per RESEARCH §D-07).
     */
    fun visit(
        scene: SceneIR,
        actors: List<ActorIR> = emptyList(),
        variables: List<VariableDef> = emptyList(),
        zoneBankAllocation: Map<String, Int> = emptyMap(),
        zones: List<ZoneIR> = emptyList(),
        gbcTarget: Boolean = false,
        isMbcGame: Boolean = false,
    ): List<CFunction> {
        val exprVisitor = ExprVisitor(actors, variables)

        val sceneBank = scene.bankSlot?.bank
        // isBanked is true when:
        // - bankSlot is present and bank > 0 (explicit bank assignment from analysis), OR
        // - bankSlot is null (default backward-compat behavior — all scene functions are BANKED)
        val sceneBanked = sceneBank == null || sceneBank > 0

        // SEED-014 Phase 11.1 — caller-miss runtime guard (RESEARCH §Pitfall 2 mitigation):
        // if this scene has zone binders but the caller did not thread the allocation maps, fail
        // fast with a clear wiring error. Silently emitting an enter function without the
        // zone-load call would produce a BLACK screen at runtime (Anchor 1+2 RED) while the
        // INV-2 gate might still flip GREEN — a hard-to-diagnose bug. Better to throw here.
        if (scene.zoneRefs.isNotEmpty()) {
            check(zoneBankAllocation.isNotEmpty() && zones.isNotEmpty()) {
                "SceneVisitor.visit: scene '${scene.id}' has zoneRefs=${scene.zoneRefs} but " +
                    "caller did not thread zoneBankAllocation/zones. This is a wiring bug in " +
                    "the caller. See GBDKPipeline.buildSceneFile."
            }
        }

        return listOfNotNull(
            buildEnterFunction(
                scene,
                exprVisitor,
                zoneBankAllocation,
                zones,
                gbcTarget,
                sceneBank,
                sceneBanked,
            ),
            buildFrameFunction(scene, exprVisitor, sceneBank, sceneBanked),
            buildExitFunction(scene, exprVisitor, sceneBank, sceneBanked, isMbcGame),
        )
    }

    // -------------------------------------------------------------------------
    // Per-lifecycle scene sub-builders (extracted to reduce visit() CC — E-07)
    // Each returns null when the lifecycle is not needed, so listOfNotNull can
    // assemble the final list without changing emission order.
    // -------------------------------------------------------------------------

    private fun buildEnterFunction(
        scene: SceneIR,
        exprVisitor: ExprVisitor,
        zoneBankAllocation: Map<String, Int>,
        zones: List<ZoneIR>,
        gbcTarget: Boolean,
        sceneBank: Int?,
        sceneBanked: Boolean,
    ): CFunction? {
        if (scene.enterOps.isEmpty() && scene.zoneRefs.isEmpty()) return null
        val zoneLoadStatements =
            buildZoneLoadStatements(scene, zones, zoneBankAllocation, gbcTarget)
        val userEnterOps = scene.enterOps.map { ScriptOpVisitor.visit(it, exprVisitor) }
        return CFunction(
            name = "${scene.id}_enter",
            returnType = CVoid,
            body = zoneLoadStatements + userEnterOps,
            bank = sceneBank,
            isBanked = sceneBanked,
            sectionComment = "Scene: ${scene.id}",
        )
    }

    private fun buildFrameFunction(
        scene: SceneIR,
        exprVisitor: ExprVisitor,
        sceneBank: Int?,
        sceneBanked: Boolean,
    ): CFunction? {
        if (scene.frameOps.isEmpty()) return null
        return CFunction(
            name = "${scene.id}_frame",
            returnType = CVoid,
            body = scene.frameOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
            bank = sceneBank,
            isBanked = sceneBanked,
        )
    }

    private fun buildExitFunction(
        scene: SceneIR,
        exprVisitor: ExprVisitor,
        sceneBank: Int?,
        sceneBanked: Boolean,
        isMbcGame: Boolean,
    ): CFunction? =
        when {
            scene.exitOps.isNotEmpty() ->
                CFunction(
                    name = "${scene.id}_exit",
                    returnType = CVoid,
                    body = scene.exitOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                )
            // Req #15 / D-07 — auto-synthesize an empty BANKED `${scene.id}_exit` for MBC games.
            //
            // When a cross-bank scene has no user-declared exit {} block, the pipeline still needs
            // an exit function to pair with the HOME-bank `*_exit_trampoline` and the
            // `navigate_to_scene()` exit switch case. Without this stub, SDCC reports
            // `undefined identifier '${scene.id}_exit_trampoline'` at link time (RESEARCH Pitfall
            // 3).
            //
            // The predicate `isMbcGame` (= gameIR.config.cartridge.maxRomBanks > 2) distinguishes
            // genuine multi-bank cartridges (MBC1, MBC5, etc.) from ROM_ONLY games whose single
            // non-HOME bank (bank 1) is never switched — ROM_ONLY scenes must NOT gain new exit
            // functions (no-growth gate; RESEARCH §D-07 Critical Finding).
            sceneBank != null && sceneBank > 0 && isMbcGame ->
                CFunction(
                    name = "${scene.id}_exit",
                    returnType = CVoid,
                    body = emptyList(),
                    bank = sceneBank,
                    isBanked = true,
                )
            else -> null
        }

    /**
     * Build the zone-load statement sequence prepended to the enter function body.
     *
     * Assembles per-zone [CStatement] lists in the order mandated by GBDK:
     * 1. `set_bkg_data(...)` — pixel bytes to VRAM tile-data area (NEW-path zones only).
     * 2. `_bkg_tiles_load_banked(...)` — tile-index map to VRAM tile-map area (or skipped for
     *    card-overdraw scenes).
     * 3. `set_bkg_palette(...)` — GBC palette upload (NEW-path zones when gbcTarget).
     * 4. `DISPLAY_ON;` — re-enable master LCD (omitted when enterOps ends with DISPLAY_ON).
     *
     * Returns an empty list when [SceneIR.zoneRefs] is empty (no zone binding).
     */
    private fun buildZoneLoadStatements(
        scene: SceneIR,
        zones: List<ZoneIR>,
        zoneBankAllocation: Map<String, Int>,
        gbcTarget: Boolean,
    ): List<CStatement> =
        scene.zoneRefs.flatMap { zoneId ->
            val zone =
                zones.firstOrNull { it.id == zoneId }
                    ?: error(
                        "SceneIR '${scene.id}' references zoneId='$zoneId' but no ZoneIR " +
                            "with that id exists in GameIR.zones"
                    )
            // Phase 13.8 Plan 06 (D-01 / Req 6): Read bank from scene.allocatedZoneBank
            // (the single-source field populated by buildCFiles after allocateZoneBanks).
            // Falls back to zoneBankAllocation[zoneId] for:
            //   - Pre-field callers that do not populate the field (backward compat)
            //   - Multi-zone scenes where allocatedZoneBank stores only the first zone's bank
            val bank =
                scene.allocatedZoneBank
                    ?: zoneBankAllocation[zoneId]
                    ?: error(
                        "Zone '$zoneId' is bound to scene '${scene.id}' but has no bank " +
                            "allocation. Run BankingAnalysisPass first."
                    )
            buildSingleZoneLoad(scene, zone, bank, gbcTarget)
        }

    /**
     * Assembles [CStatement] list for a single zone binding in the enter function.
     *
     * Extracted from [buildZoneLoadStatements] to flatten the flatMap lambda nesting (SonarCloud
     * S3776 18-28).
     */
    private fun buildSingleZoneLoad(
        scene: SceneIR,
        zone: ZoneIR,
        bank: Int,
        gbcTarget: Boolean,
    ): List<CStatement> {
        val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
        // Phase 11.2 (REQ-3, D-A3, D-claude-4): emit set_bkg_data BEFORE
        // _bkg_tiles_load_banked when the zone has a tilesetPath (NEW path).
        val pixelLoad = buildZonePixelLoad(zone, zoneSanitized)
        // Phase 12.9 D1 fix — trailing-DISPLAY_ON heuristic (WR-01 exact-match):
        // Check whether the scene's enterOps already ends with a DISPLAY_ON statement
        // (e.g., LevelCardSceneBuilder.materialize() appends cEmit("DISPLAY_ON;")).
        // If so, omit the inline DISPLAY_ON from the zone-load block to avoid turning
        // on the LCD BEFORE the user clear runs.
        //
        // WR-01 fix: use EXACT match `code.trim() == "DISPLAY_ON;"` instead of the
        // fragile `code.contains("DISPLAY_ON")` substring check.
        val sceneEndsWithDisplayOn =
            scene.enterOps.lastOrNull().let {
                it is RawOp && it.code.trim() == "DISPLAY_ON;"
            }
        // Phase 12.9 Polish — card-overdraw signal:
        // Detect whether this scene's enterOps contain the full-BG-plane clear emitted
        // by LevelCardSceneBuilder.materialize(): fill_bkg_rect(0u, 0u, 32u, 32u, 0u).
        // When the signal is true, skip the (0,0) tilemap-place to eliminate the flash.
        val sceneHasCardOverdraw =
            scene.enterOps.any {
                it is RawOp && it.code.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)")
            }
        return pixelLoad +
            buildZoneTilemapAndPalette(
                zone,
                zoneSanitized,
                bank,
                gbcTarget,
                sceneEndsWithDisplayOn,
                sceneHasCardOverdraw,
            )
    }

    /**
     * Builds the tilemap-placement, palette-upload, and DISPLAY_ON statements for one zone.
     *
     * Extracted from [buildSingleZoneLoad] to keep cognitive complexity ≤ 15 (SonarCloud S3776
     * 18-28).
     */
    private fun buildZoneTilemapAndPalette(
        zone: ZoneIR,
        zoneSanitized: String,
        bank: Int,
        gbcTarget: Boolean,
        sceneEndsWithDisplayOn: Boolean,
        sceneHasCardOverdraw: Boolean,
    ): List<CStatement> {
        // Phase 12.2 debug fix (D-01 Path A): NEW-path zones (tilesetPath != null)
        // MUST use the Gradle-task-emitted `_zone_<id>_tilemap_WIDTH` /
        // `_zone_<id>_tilemap_HEIGHT` macros for the w/h args of _bkg_tiles_load_banked.
        // LEGACY-path zones (tilesetPath == null) fall back to the ZoneIR literal.
        // See: .planning/debug/title-zone-path-a-render.md (Hypothesis A CONFIRMED).
        val widthArg =
            if (zone.tilesetPath != null) {
                CVar("_zone_${zoneSanitized}_tilemap_WIDTH")
            } else {
                // Legacy path: no ConvertZoneTilesetsTask — use explicit dim or 20×18
                // fallback (REQ-14 D-03; null sentinel means auto = 20×18).
                CLiteral(zone.mapWidth ?: 20)
            }
        val heightArg =
            if (zone.tilesetPath != null) {
                CVar("_zone_${zoneSanitized}_tilemap_HEIGHT")
            } else {
                CLiteral(zone.mapHeight ?: 18)
            }
        // Phase 12.9 D-01 / Phase 13.5-02: screenMode superset branch.
        // When zone.screenMode == true (synthesized by SceneBuilder.screen()), replace
        // the (0,0) tilemap-place with the full superset:
        //   1. hide_sprites_range(0u, MAX_HARDWARE_SPRITES) — D-06 sprite reset
        //   2. move_bkg(0u, 0u)                             — D-06 scroll reset
        //   3. fill_bkg_rect(0u, 0u, 32u, 32u, 0u)          — D-05 BG full-plane clear
        //   4. _bkg_tiles_load_banked(bank, centered-x, centered-y, W, H, tilemap)
        // DISPLAY_ON is handled by the existing inline emission below (unchanged).
        val tilemapPlace =
            when {
                // Phase 13.5-02: screenMode superset — centered draw + sprite/scroll reset.
                zone.screenMode -> {
                    // WR-02 fix: guard against programmatically constructed ZoneIR
                    // with screenMode=true but tilesetPath=null (the DSL always sets
                    // tilesetPath via screen(asset(...)), but the data class is copyable).
                    require(zone.tilesetPath != null) {
                        "Zone '${zone.id}' has screenMode=true but tilesetPath is null. " +
                            "screen() always sets tilesetPath; do not set screenMode=true " +
                            "on zones without an asset."
                    }
                    listOf(
                        // D-06: sprite reset — hide all hardware sprites (OAM clear).
                        CRawCode("hide_sprites_range(0u, MAX_HARDWARE_SPRITES);"),
                        // D-06: scroll reset — move BG to origin before centering.
                        CExprStatement(CCall("move_bkg", listOf(CLiteral(0), CLiteral(0)))),
                        // D-05: BG full-plane clear — wipe all 32x32 BG tile slots.
                        // 32x32 covers the full GBC hardware-addressable BG map;
                        // 20x18 would leave columns 20-31 with residual tile indices.
                        CRawCode("fill_bkg_rect(0u, 0u, 32u, 32u, 0u);"),
                        // Centered tilemap placement — exact formula from
                        // LevelCardSceneBuilder.materialize():889-895 (PATTERNS.md
                        // § "Don't Hand-Roll" — do NOT re-derive this formula).
                        // widthArg/heightArg already set to _zone_<id>_tilemap_WIDTH/HEIGHT
                        // macros (NEW-path, tilesetPath != null, ensured by screen() DSL).
                        CExprStatement(
                            CCall(
                                "_bkg_tiles_load_banked",
                                listOf(
                                    CLiteral(bank),
                                    CVar(
                                        "(DEVICE_SCREEN_WIDTH - _zone_${zoneSanitized}_tilemap_WIDTH) / 2u"
                                    ),
                                    CVar(
                                        "(DEVICE_SCREEN_HEIGHT - _zone_${zoneSanitized}_tilemap_HEIGHT) / 2u"
                                    ),
                                    widthArg,
                                    heightArg,
                                    CVar("_zone_${zoneSanitized}_tilemap"),
                                ),
                            )
                        ),
                    )
                }
                // Phase 12.9 Polish: skip the (0,0) tilemap-place when the card-overdraw
                // signal is detected (fill_bkg_rect(0u,0u,32u,32u,0u) in enterOps).
                // Normal scenes (sceneHasCardOverdraw == false) always include it.
                sceneHasCardOverdraw -> emptyList()
                // Default: normal (0,0) zone-origin placement.
                else ->
                    listOf(
                        CExprStatement(
                            CCall(
                                "_bkg_tiles_load_banked",
                                listOf(
                                    CLiteral(bank),
                                    CLiteral(0),
                                    CLiteral(0),
                                    widthArg,
                                    heightArg,
                                    // Plan 11.1-17 (Phase D): NEW-path zones consume the
                                    // Gradle-task-emitted _zone_<id>_tilemap symbol.
                                    // LEGACY-path zones (tilesetPath == null) keep
                                    // referencing _zone_<id>_tiles verbatim.
                                    // See Plan 11.1-15 SUMMARY.
                                    CVar(
                                        if (zone.tilesetPath != null) {
                                            "_zone_${zoneSanitized}_tilemap"
                                        } else {
                                            "_zone_${zoneSanitized}_tiles"
                                        }
                                    ),
                                ),
                            )
                        )
                    )
            }
        // Re-enable the master LCD after set_bkg_data/set_bkg_tiles' implicit
        // display_off() (clears LCDC.7 / LCDCF_ON = 0b10000000, bit 7).
        // Phase 12.4 D-08 Rule 1 Bug fix; Phase 12.9 D1 fix: omit when scene already
        // ends with DISPLAY_ON (sceneEndsWithDisplayOn). Prevents flip-frame 0F artifact.
        val displayOn = if (sceneEndsWithDisplayOn) emptyList() else listOf(CRawCode("DISPLAY_ON;"))
        return tilemapPlace + buildZonePaletteLoad(zone, zoneSanitized, gbcTarget) + displayOn
    }

    /**
     * Builds the `set_bkg_data` pixel-load statement for a NEW-path zone, or an empty list for
     * LEGACY-path zones ([ZoneIR.tilesetPath] == null).
     *
     * Phase 11.2 (REQ-3, D-A3, D-claude-4): emit set_bkg_data BEFORE _bkg_tiles_load_banked. Zones
     * without tilesetPath (e.g. sport-racing's synthesized tile data) skip this call.
     */
    private fun buildZonePixelLoad(zone: ZoneIR, zoneSanitized: String): List<CStatement> =
        if (zone.tilesetPath != null) {
            listOf(
                CExprStatement(
                    CCall(
                        "set_bkg_data",
                        listOf(
                            CLiteral(0),
                            CVar("_zone_${zoneSanitized}_tileset_count"),
                            CVar("_zone_${zoneSanitized}_tileset"),
                        ),
                    )
                )
            )
        } else {
            emptyList()
        }

    /**
     * Builds the GBC per-zone palette upload statement, or an empty list for LEGACY-path zones
     * ([ZoneIR.tilesetPath] == null) or non-GBC targets.
     *
     * Phase 12.9 D-01: GBC-gated per-zone palette upload. NEW-path only (zone.tilesetPath != null):
     * LEGACY-path zones do not have a `_zone_<id>_tileset_palettes` array (SEED-017 deferred
     * unification). GBC-gated: DMG targets do not have BG palette RAM.
     */
    private fun buildZonePaletteLoad(
        zone: ZoneIR,
        zoneSanitized: String,
        gbcTarget: Boolean,
    ): List<CStatement> =
        if (zone.tilesetPath != null && gbcTarget) {
            listOf(
                CRawCode(
                    "set_bkg_palette(0u, _zone_${zoneSanitized}_tileset_PALETTE_COUNT," +
                        " _zone_${zoneSanitized}_tileset_palettes);"
                )
            )
        } else {
            emptyList()
        }

    /**
     * Generate `#define` constants for a list of scene IDs.
     *
     * Scene IDs are uppercased and prefixed with `SCENE_`:
     * - `"title"` → `CDefine("SCENE_TITLE", "0")`
     * - `"game"` → `CDefine("SCENE_GAME", "1")`
     *
     * These constants are used by [ScriptOpVisitor.visitNavigateTo] to produce type-safe scene
     * references in the generated C code.
     */
    fun generateSceneEnum(sceneIds: List<String>): List<CDefine> {
        return sceneIds.mapIndexed { index, id -> CDefine("SCENE_${id.uppercase()}", "$index") }
    }
}
