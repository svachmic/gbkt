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
     *   etc.). When true AND the scene has bankSlot.bank > 0 AND no user-declared exit {} block,
     *   an empty BANKED `${scene.id}_exit` function is auto-synthesized (Req #15 / D-07). Defaults
     *   to false for backward-compat; existing tests that omit this parameter retain the old
     *   behavior of never auto-emitting an exit. ROM_ONLY games have maxRomBanks=2 and pass
     *   isMbcGame=false — their scenes land at bankSlot.bank=1 from BankingAnalysisPass FFD but
     *   must NOT gain a new `*_exit` function (no-growth gate per RESEARCH §D-07).
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
        val functions = mutableListOf<CFunction>()
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

        if (scene.enterOps.isNotEmpty() || scene.zoneRefs.isNotEmpty()) {
            // SEED-014 Phase 11.1 — prepend zone-load statements BEFORE user enter ops.
            // Order: [set_bkg_data + _bkg_tiles_load_banked + DISPLAY_ON per zoneRef, then user
            // enterOps].
            // DISPLAY_ON is required: set_bkg_data/set_bkg_tiles both call display_off() internally
            // (confirmed by gb.lib symbol table — gb.lib's _set_bkg_data/_set_bkg_tiles reference
            // .display_off). Without DISPLAY_ON the master LCD enable bit (LCDC.7 / LCDCF_ON) stays
            // 0
            // and the main loop's wait_vbl_done() hangs forever (no VBlank with LCD disabled).
            // Phase 12.4 D-08 / Rule 1 Bug: original SHOW_BKG was wrong — SHOW_BKG sets the BG
            // enable
            // bit (LCDCF_BGON = 0b00000001 on GB, bit 0 of LCDC) which is ALREADY set by main()'s
            // bootstrap SHOW_BKG call; it does NOT restore the LCD master enable (LCDCF_ON =
            // 0b10000000,
            // bit 7 of LCDC) that display_off() cleared. DISPLAY_ON (LCDC_REG |= LCDCF_ON) restores
            // bit 7. Confirmed by GBDK hardware.h: LCDCF_ON = 0b10000000 (bit 7), LCDCF_BGON =
            // 0b00000001 (bit 0) — they are orthogonal bits. Same class of bug as Phase 07.4 racer
            // (07.4-30-SUMMARY.md: "DISPLAY_ON must follow every set_bkg_data/set_bkg_tiles in
            // scene-
            // enter because set_bkg_tiles calls display_off() internally per gb.lib").
            /**
             * Phase 11.2 (D-D1) — NEW path: Gradle png2asset pipeline (zones, post-11.2).
             *
             * Emits the three-statement scene-enter prelude for a zone-binding scene:
             * 1. `set_bkg_data(0, _zone_<id>_tileset_count, _zone_<id>_tileset);` — pixel bytes to
             *    VRAM tile-data area (0x8000-0x97FF). Symbol resolution: the synthesized
             *    `_zone_<id>_tileset.h` header (produced by ConvertZoneTilesetsTask) provides
             *    `#define _zone_<id>_tileset <native>_tiles` (alias to the png2asset-emitted array)
             *    and `#define _zone_<id>_tileset_count <N>` (the symbolic count per D-A3).
             *    The #include directive is emitted by [GBDKPipeline.buildSceneFile].
             * 2. `_bkg_tiles_load_banked(bank, 0, 0, w, h, _zone_<id>_tiles);` — tile-index map to
             *    VRAM tile-map area (0x9800-0x9BFF). Indices reference VRAM tile slots populated by
             *    step 1; ordering is load-bearing (D-claude-4).
             * 3. `DISPLAY_ON;` — re-enable master LCD (set_bkg_data/set_bkg_tiles both call
             *    display_off() internally, clearing LCDC.7/LCDCF_ON; DISPLAY_ON restores it).
             *
             * The `if (zone.tilesetPath != null)` filter discriminates this NEW path from the
             * LEGACY path (sport-racing's [SportVisitor.buildBuiltinTrackTilesetVarDecl]; SEED-017
             * captures the deferred unification). Procedurally-authored zones whose tile data does
             * not flow through png2asset skip the set_bkg_data prepend.
             *
             * Invariant: `BanksEmissionTest.INV-7` locks the emission shape + ordering + the
             * consuming bank file's `#include "_zone_<id>_tileset.h"` directive.
             *
             * See also:
             * - .planning/codebase/CONVENTIONS.md §"Tile pixel data emission: two paths, when to
             *   use which"
             * - .planning/seeds/SEED-017-sport-zone-tileset-pipeline-unification.md
             * - .planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/
             */
            val zoneLoadStatements: List<CStatement> =
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
                    val zoneSanitized = zoneId.replace('-', '_').replace(' ', '_')
                    // Phase 11.2 (REQ-3, D-A3, D-claude-4): emit set_bkg_data BEFORE
                    // _bkg_tiles_load_banked when the zone has a tilesetPath (NEW path).
                    // Zones without tilesetPath (synthesized procedural tile data, e.g.
                    // sport-racing's _racing_<id>_tileset which lives on the LEGACY path —
                    // see SEED-017 / CONVENTIONS.md §"Tile pixel data emission: two paths")
                    // skip the set_bkg_data prepend — they author their own emission.
                    val pixelLoad: List<CStatement> =
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
                    // Phase 12.2 debug fix (D-01 Path A): NEW-path zones (tilesetPath != null)
                    // MUST use the Gradle-task-emitted `_zone_<id>_tilemap_WIDTH` /
                    // `_zone_<id>_tilemap_HEIGHT` macros for the w/h args of
                    // _bkg_tiles_load_banked.
                    // Using CLiteral(zone.mapWidth) / CLiteral(zone.mapHeight) emits the ZoneIR
                    // default (32, 32) even when the actual tilemap is smaller (e.g.
                    // title-screen.png
                    // is 20x9 tiles — 180 bytes). Passing 32x32 reads 1024 tile entries from a
                    // 180-byte buffer, producing the row-doubling visual defect.
                    // ConvertZoneTilesetsTask
                    // (Phase 12.2-06) emits these macros from the actual PNG IHDR dimensions — they
                    // are
                    // the single source of truth for tilemap geometry. LEGACY-path zones
                    // (tilesetPath
                    // == null) have no emitted macros, so they fall back to the ZoneIR literal.
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
                    // Phase 12.9 D1 fix — trailing-DISPLAY_ON heuristic (WR-01 exact-match):
                    // Check whether the scene's enterOps already ends with a DISPLAY_ON statement
                    // (e.g., LevelCardSceneBuilder.materialize() appends cEmit("DISPLAY_ON;")).
                    // If so, omit the inline DISPLAY_ON from the zone-load block to avoid turning
                    // on the LCD BEFORE the user clear runs.
                    //
                    // WR-01 fix: use EXACT match `code.trim() == "DISPLAY_ON;"` instead of the
                    // fragile `code.contains("DISPLAY_ON")` substring check. The substring form
                    // could accidentally suppress the LCD re-enable for a comment containing the
                    // token (e.g. cEmit("// DISPLAY_ON handled elsewhere")) or for a macro named
                    // DISPLAY_ONCE — leaving the LCD off after the scene enter → black screen.
                    val sceneEndsWithDisplayOn =
                        scene.enterOps.lastOrNull()
                            .let { it is RawOp && it.code.trim() == "DISPLAY_ON;" }

                    // Phase 12.9 Polish — card-overdraw signal:
                    // Detect whether this scene's enterOps contain the full-BG-plane clear emitted
                    // by LevelCardSceneBuilder.materialize(): fill_bkg_rect(0u, 0u, 32u, 32u, 0u).
                    // When the signal is true, the (0,0) tilemap-place _bkg_tiles_load_banked(bank,
                    // 0, 0, ...) is immediately wiped by the fill_bkg_rect clear and then the card
                    // redraws the tilemap CENTERED — the (0,0) place produces only a brief top-left
                    // flash before centering. Skipping the place eliminates the flash.
                    //
                    // The tile DATA load (pixelLoad = set_bkg_data) MUST be kept (the centered
                    // redraw references the loaded tile indices). The per-zone set_bkg_palette MUST
                    // be kept (palette RAM upload is independent of tile placement). Only the
                    // `_bkg_tiles_load_banked(bank, 0, 0, ...)` CExprStatement is skipped.
                    //
                    // Back-compat: non-card scenes (no full-screen clear in enterOps) keep the
                    // (0,0) tilemap-place UNCHANGED — byte-identical to pre-12.9-12 codegen.
                    val sceneHasCardOverdraw =
                        scene.enterOps.any {
                            it is RawOp &&
                                it.code.contains("fill_bkg_rect(0u, 0u, 32u, 32u, 0u)")
                        }

                    // Phase 12.9 D-01: GBC-gated set_bkg_palette appends between
                    // _bkg_tiles_load_banked and DISPLAY_ON; mirrors GBDKPipeline.kt:4701
                    // _gbkt_default_bg_pal pattern.
                    //
                    // Phase 13.5-02: screenMode superset branch.
                    // When zone.screenMode == true (synthesized by SceneBuilder.screen()), replace
                    // the (0,0) tilemap-place with the full superset:
                    //   1. hide_sprites_range(0u, MAX_HARDWARE_SPRITES) — D-06 sprite reset
                    //   2. move_bkg(0u, 0u)                             — D-06 scroll reset
                    //   3. fill_bkg_rect(0u, 0u, 32u, 32u, 0u)          — D-05 BG full-plane clear
                    //   4. _bkg_tiles_load_banked(bank, centered-x, centered-y, W, H, tilemap)
                    // This one code path serves both full-screen (20x18) and banner (20x9) images —
                    // centering math auto-derives (0,0) for 20x18 and (0,4) for 20x9.
                    // DISPLAY_ON is handled by the existing inline emission below (unchanged).
                    val tilemapPlaceStatement =
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
                                    CExprStatement(
                                        CCall("move_bkg", listOf(CLiteral(0), CLiteral(0)))
                                    ),
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
                                                CVar("(DEVICE_SCREEN_WIDTH - _zone_${zoneSanitized}_tilemap_WIDTH) / 2u"),
                                                CVar("(DEVICE_SCREEN_HEIGHT - _zone_${zoneSanitized}_tilemap_HEIGHT) / 2u"),
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
                                                // Plan 11.1-17 (Phase D): NEW-path zones (zone.tilesetPath
                                                // != null) consume the Gradle-task-emitted
                                                // _zone_<id>_tilemap symbol (a tiled-repeat of the
                                                // png2asset _tileset_map across mapWidth*mapHeight)
                                                // instead of the legacy _zone_<id>_tiles stub.
                                                // LEGACY-path zones (tilesetPath == null, e.g.
                                                // SportVisitor's racer track which populates
                                                // ZoneIR.tileData procedurally) keep referencing
                                                // _zone_<id>_tiles verbatim. See Plan 11.1-15 SUMMARY.
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
                    pixelLoad + tilemapPlaceStatement + (
                            // Phase 12.9 D-01: GBC-gated per-zone palette upload.
                            // Upload the zone's PNG-derived palette to BG palette RAM AFTER tile data
                            // load (BCPS/BCPD writes are independent of VRAM), BEFORE DISPLAY_ON.
                            // NEW-path only (zone.tilesetPath != null): LEGACY-path zones do not have
                            // a _zone_<id>_tileset_palettes array (SEED-017 deferred unification).
                            // GBC-gated: DMG targets do not have BG palette RAM.
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
                        ) + (
                            // Re-enable the master LCD after set_bkg_data/set_bkg_tiles' implicit
                            // display_off() (clears LCDC.7 / LCDCF_ON = 0b10000000, bit 7).
                            // SHOW_BKG only sets the BG-enable bit (LCDCF_BGON = 0b00000001, bit 0)
                            // which is already set by main()'s bootstrap — DISPLAY_ON is needed to
                            // restore bit 7 so wait_vbl_done() can receive a VBlank.
                            // Phase 12.4 D-08 Rule 1 Bug fix; same class as Phase 07.4 Plan 30.
                            //
                            // Phase 12.9 D1 fix: omit when scene already ends with DISPLAY_ON
                            // (sceneEndsWithDisplayOn hoisted above). Prevents flip-frame 0F artifact.
                            if (sceneEndsWithDisplayOn) emptyList() else listOf(CRawCode("DISPLAY_ON;"))
                        )
                }
            val userEnterOps = scene.enterOps.map { ScriptOpVisitor.visit(it, exprVisitor) }
            functions +=
                CFunction(
                    name = "${scene.id}_enter",
                    returnType = CVoid,
                    body = zoneLoadStatements + userEnterOps,
                    bank = sceneBank,
                    isBanked = sceneBanked,
                    sectionComment = "Scene: ${scene.id}",
                )
        }

        if (scene.frameOps.isNotEmpty()) {
            functions +=
                CFunction(
                    name = "${scene.id}_frame",
                    returnType = CVoid,
                    body = scene.frameOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                )
        }

        if (scene.exitOps.isNotEmpty()) {
            functions +=
                CFunction(
                    name = "${scene.id}_exit",
                    returnType = CVoid,
                    body = scene.exitOps.map { ScriptOpVisitor.visit(it, exprVisitor) },
                    bank = sceneBank,
                    isBanked = sceneBanked,
                )
        } else if (scene.exitOps.isEmpty() && sceneBank != null && sceneBank > 0 && isMbcGame) {
            // Req #15 / D-07 — auto-synthesize an empty BANKED `${scene.id}_exit` for MBC games.
            //
            // When a cross-bank scene has no user-declared exit {} block, the pipeline still needs
            // an exit function to pair with the HOME-bank `*_exit_trampoline` and the
            // `navigate_to_scene()` exit switch case. Without this stub, SDCC reports
            // `undefined identifier '${scene.id}_exit_trampoline'` at link time (RESEARCH Pitfall 3).
            //
            // The predicate `isMbcGame` (= gameIR.config.cartridge.maxRomBanks > 2) distinguishes
            // genuine multi-bank cartridges (MBC1, MBC5, etc.) from ROM_ONLY games whose single
            // non-HOME bank (bank 1) is never switched — ROM_ONLY scenes must NOT gain new exit
            // functions (no-growth gate; RESEARCH §D-07 Critical Finding).
            functions +=
                CFunction(
                    name = "${scene.id}_exit",
                    returnType = CVoid,
                    body = emptyList(),
                    bank = sceneBank,
                    isBanked = true,
                )
        }

        return functions
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
