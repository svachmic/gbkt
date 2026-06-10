/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.SpriteMode

// =============================================================================
// METASPRITE VISITOR
// Translates IR v2 MetaspriteIR nodes into typed C AST nodes for GBDK
// metasprite rendering. Mirrors ActorVisitor for the metasprite sub-system.
//
// Sub-area A (this plan, 10-05): generateMetaspriteTileData()
//   Emits set_sprite_data(start, count, array) in scene-enter.
//   totalTiles = max(tileId across all frames) + spriteModeStride
//   where spriteModeStride = 2 for SPR8x16 (each tile ID references a pair)
//                            1 for SPR8x8 / null (single 8x8 slot per tile ID)
//
// Sub-area B (Plan 10-06; namespaced in Phase 10.1 Plan 05 / CR-03):
//   generateMetaspriteDescriptor() emits sprite_<id>_frame_N[] per-frame arrays +
//   sprite_<id>_frames[] pointer table (namespaced by metasprite.id to avoid
//   linker collisions when ≥2 metasprites coexist).
//
// Sub-area C (Plan 10-07; parameterized in Phase 10.1 Plan 05 / WR-01;
//   hiwater HOISTED in Phase 10.1 Plan 09 / WR-05):
//   generateMetaspriteFrameSwitch() emits per-frame switch on flip bitmask that
//   contributes `hiwater += move_metasprite_*(...)` per case to an outer
//   function-scope `hiwater` declared by GBDKPipeline.wrapFrameWithMetaspriteHiwater.
//   Reads var-ref names from posXVar/posYVar/idxVar/rotVar parameters with
//   canonical _posX/_posY/_idx/_rot fallbacks. Pre-Plan-10.1-09 this method
//   ALSO emitted its own per-call `uint8_t hiwater = 0u;` + trailing
//   `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` — both removed in
//   the hoist to eliminate multi-call OAM collision when a scene called
//   moveMetasprite() more than once per frame.
// =============================================================================

/**
 * Visitor that converts [MetaspriteIR] nodes to C AST nodes for GBDK metasprite rendering.
 *
 * **Tile data loading** ([generateMetaspriteTileData]): Emits `set_sprite_data(startTile,
 * totalTiles, tileDataArrayName)` to copy metasprite tile data into VRAM at scene-enter time.
 *
 * `totalTiles` is computed as `max(tileId across ALL tiles in ALL frames) + 1` — this gives the
 * number of unique VRAM slots occupied by the metasprite's tile data.
 *
 * Literal convention (Phase 07.9): `startTile` and `totalTiles` are emitted as [CLiteral] (not
 * [io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral]) because both are unsigned-context values
 * (VRAM tile indices, counts). See `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention".
 */
object MetaspriteVisitor {

    /**
     * Generate `set_sprite_data()` call to load tile data for this metasprite into sprite VRAM.
     *
     * The generated call copies `totalTiles` tiles starting at `startTile` VRAM slot from the C
     * array named `tileDataArrayName` into sprite tile VRAM. This is called once at scene-enter
     * before any `move_metasprite_*` calls.
     *
     * `totalTiles = max(tileId across all tiles in all frames) + spriteModeStride`, where
     * `spriteModeStride = 2` for `SpriteMode.SPR8x16` (each tile ID references the 8×16 pair `(N,
     * N+1)`) and `1` for `SpriteMode.SPR8x8` (or null `spriteMode` for back-compat with the Seed004
     * elephant example). See [tileCountForMetasprite] for the canonical implementation.
     *
     * @param metasprite The metasprite whose tile data to load.
     * @param tileDataArrayName C identifier of the tile data array (from the asset pipeline or
     *   declared via [io.github.gbkt.backend.gbdk.codegen.ast.CRawCode] pending Plan 13 wiring).
     * @param startTile First VRAM tile slot to place this metasprite's tiles.
     * @return A list containing the `set_sprite_data()` call statement, or empty if the metasprite
     *   has no frames or tiles.
     */
    fun generateMetaspriteTileData(
        metasprite: MetaspriteIR,
        tileDataArrayName: String,
        startTile: Int,
    ): List<CStatement> {
        val totalTiles = tileCountForMetasprite(metasprite) ?: return emptyList()
        return listOf(
            CExprStatement(
                CCall(
                    "set_sprite_data",
                    listOf(CLiteral(startTile), CLiteral(totalTiles), CVar(tileDataArrayName)),
                )
            )
        )
    }

    /**
     * Compute the VRAM tile count required to load a metasprite's tile data.
     *
     * In 8×8 sprite mode (SPR8x8 or null spriteMode for back-compat with the elephant Seed004
     * example) each tile ID references a single 8×8 tile slot; the count is `maxTileId + 1`.
     *
     * In 8×16 sprite mode (SPR8x16) each tile ID N references the 8×16 pair (tile N AND tile N+1) —
     * the hardware OAM-entry tile-ID LSB is forced to 0, so the second tile of every pair lives at
     * the next 8×8 slot. The count must therefore be `maxTileId + 2` to include the partner of the
     * highest- indexed pair.
     *
     * Debug E-04 (2026-05-24, debug/platformer-duck-malformed-blob.md): pre-fix the formula was
     * `maxTileId + 1` for both modes; the platformer duck (SPR8x16, maxTileId=60) loaded only 61
     * tiles instead of 62, dropping the partner tile of the highest pair. Returned as a nullable
     * Int so empty metasprites short-circuit to no set_sprite_data() emission.
     *
     * @return null when the metasprite has no tiles; otherwise the tile count.
     */
    fun tileCountForMetasprite(metasprite: MetaspriteIR): Int? {
        val allTiles = metasprite.frames.flatMap { it.tiles }
        if (allTiles.isEmpty()) return null
        val maxTileId = allTiles.maxOf { it.tileId }
        val stride = if (metasprite.spriteMode == SpriteMode.SPR8x16) 2 else 1
        return maxTileId + stride
    }

    /**
     * Generate the `sprite_<id>_frame_N[]` per-frame OAM descriptor arrays and the
     * `sprite_<id>_frames[]` pointer table.
     *
     * **Emitted C shape** (per metasprite `id`):
     * ```c
     * const metasprite_t sprite_elephant_frame_0[] = {
     *     {dy0, dx0, dtile0}, {dy1, dx1, dtile1}, ..., {metasprite_end}
     * };
     * const metasprite_t sprite_elephant_frame_1[] = { ... };
     * const metasprite_t* const sprite_elephant_frames[] = {
     *     sprite_elephant_frame_0,
     *     sprite_elephant_frame_1,
     * };
     * ```
     *
     * **Namespacing rationale (CR-03 / SEED-010):** Prior to Phase 10.1 Plan 05 the emitted symbols
     * were unnamespaced global-namespace literals (`sprite_metasprite_N` /
     * `sprite_metaspriteS`-style pointer table) that collide with linker "duplicate definition"
     * errors when ≥2 metasprites coexist. Namespacing by `metasprite.id` makes the symbols unique
     * per metasprite while preserving the metasprite-end sentinel + GBDK `{dy, dx, dtile}`
     * ordering.
     *
     * **Coordinate convention:** GBDK `METASPRITE_DEF` is `{int8_t dy, dx; uint8_t dtile}` — the Y
     * offset comes FIRST. [MetaspriteTile.relY] maps to `dy` and [MetaspriteTile.relX] maps to
     * `dx`.
     *
     * Uses [CRawCode] because the typed C AST has no struct-literal array primitive that can
     * represent `{int8_t, int8_t, uint8_t}` initializer lists.
     *
     * @param metasprite The metasprite whose frame descriptors to emit.
     * @return A [CRawCode] containing all per-frame arrays and the pointer table.
     */
    fun generateMetaspriteDescriptor(metasprite: MetaspriteIR): CRawCode {
        val buf = StringBuilder()

        // Plan 10.1-16 Task 4 — emit mirror-dedup opt-in sentinel marker when the
        // DSL called `mirrorDedup()` for this metasprite. ConvertSpritesTask
        // reads this comment from main.c via the [isMirrorDedupOptIn] companion
        // helper and OMITS `-noflip` when invoking png2asset for this metasprite's
        // PNG (allowing the dedup that the user opted into).
        //
        // The marker format `gbkt:mirror-dedup:<id>` uses the metasprite id so
        // ConvertSpritesTask's stem-boundary regex (mirroring Plan 11's
        // `sprite_<stem>_frames` pattern) can scope the opt-in to one specific
        // PNG -- not blanket-disable -noflip for every metasprite in the game.
        if (metasprite.mirrorDedup) {
            buf.append("/* gbkt:mirror-dedup:${metasprite.id} */\n")
        }

        // Emit per-frame arrays (CR-03: namespaced by metasprite.id)
        for ((index, frame) in metasprite.frames.withIndex()) {
            buf.append("const metasprite_t sprite_${metasprite.id}_frame_$index[] = {\n    ")
            val tileEntries =
                frame.tiles.map { tile ->
                    // GBDK METASPRITE_DEF: {dy, dx, dtile} — relY is dy, relX is dx
                    "{${tile.relY}, ${tile.relX}, ${tile.tileId}}"
                }
            buf.append(tileEntries.joinToString(", "))
            if (tileEntries.isNotEmpty()) buf.append(", ")
            buf.append("{metasprite_end}\n};\n")
        }

        // Emit pointer table (CR-03: namespaced by metasprite.id)
        buf.append("const metasprite_t* const sprite_${metasprite.id}_frames[] = {\n")
        for (index in metasprite.frames.indices) {
            buf.append("    sprite_${metasprite.id}_frame_$index,\n")
        }
        buf.append("};\n")

        return CRawCode(buf.toString())
    }

    /**
     * Generate an asset-driven Path A reference marker for main.c (Plan 13.3-05 D-01).
     *
     * For asset-driven metasprites ([MetaspriteIR.spritePath] != null), the gbkt-owned per-frame
     * OAM arrays (`sprite_<id>_frame_N[]`) are NOT emitted — they are replaced by png2asset's
     * native `<id>_metasprites[]` array in the #included `.c` sidecar (wired by Plan 13.3-06). This
     * function emits a short reference comment that:
     * - Documents that this metasprite uses the png2asset-native pointer array.
     * - Contains the literal `<id>_metasprites[idx]` usage pattern so codegen tests can assert its
     *   presence in main.c (grepping `<id>_metasprites[` confirms Path A landed).
     * - Is semantically harmless — C compilers ignore comments entirely.
     *
     * @param metasprite The asset-driven metasprite (spritePath != null).
     * @return A [CRawCode] containing the reference comment.
     */
    fun generateAssetDrivenDescriptor(metasprite: MetaspriteIR): CRawCode {
        val id = metasprite.id
        return CRawCode(
            "/* Path A — ${id} uses png2asset-native ${id}_metasprites[idx]" +
                " (see sprites/${id}.h; defined in sprites/${id}.c) */\n"
        )
    }

    /**
     * Generate the per-frame metasprite rendering block: switch on flip bitmask and accumulate
     * per-case `hiwater += move_metasprite_*(…)` contributions into the outer function-scope
     * `hiwater` declared by the scene frame function wrap.
     *
     * **Emitted C shape** (post-Plan 10.1-09 / WR-05; with `<id>` substituted from [metasprite].id
     * and `<rot>` / `<idx>` / `<posX>` / `<posY>` substituted from the parameterized var-ref
     * names):
     * ```c
     * {
     *     uint8_t subpal = <rot> >> 2;
     *     _<id>_subPalette = subpal;
     *     _<id>_flipX = (<rot> & 0x3u) >> 0u;
     *     _<id>_flipY = (<rot> & 0x3u) >> 1u;
     *     switch (<rot> & 0x3u) {
     *         case 1:
     *             hiwater += move_metasprite_flipy(sprite_<id>_frames[<idx>], 0, subpal, hiwater,
     *                                               DEVICE_SPRITE_PX_OFFSET_X + (<posX> >> 4),
     *                                               DEVICE_SPRITE_PX_OFFSET_Y + (<posY> >> 4));
     *             break;
     *         // ... cases 2/3/default analogous, all using sprite_<id>_frames[<idx>]
     *     }
     * }
     * ```
     *
     * **WR-05 closure (Plan 10.1-09):** the wrapping `uint8_t hiwater = 0u;` AND the trailing
     * `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` were HOISTED out of this method into the
     * SCENE FRAME function prelude/postlude by
     * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline.wrapFrameWithMetaspriteHiwater].
     * Pre-fix the per-call wrap RESET the OAM cursor when a frame called `moveMetasprite()` more
     * than once — the second call's `hiwater = 0u` clobbered the first metasprite's OAM allocation,
     * and the second `hide_sprites_range` then hid the first metasprite's slots. The inner block
     * scope below is preserved because the `subpal` local + Plan 04 global writes
     * (`_<id>_subPalette` etc.) still need their own scope; the `hiwater` reference in each case
     * branch now resolves to the outer function-scope declaration added by the wrap.
     *
     * **Pitfall 1 mitigation** (variable-length frame ghost sprites): post-Plan 10.1-09 the
     * `hide_sprites_range` call lives in the frame function postlude and runs ONCE per frame
     * regardless of metasprite count — blanking any leftover OAM slots accumulated by all
     * `move_metasprite_*` calls in the frame.
     *
     * **WR-01 / CR-03 closure (Phase 10.1 Plan 05):** Prior to this plan the method hardcoded the
     * canonical `_idx` / `_rot` / `_posX` / `_posY` literals and an unnamespaced pointer-table
     * reference. That made per-metasprite namespacing impossible — two metasprites would share the
     * same 4 user globals AND collide on the same descriptor pointer table. The 4 parameters below
     * substitute per-call var-ref names (with canonical fallbacks preserving Phase 10 emission
     * shape), and the descriptor reference now uses `sprite_${metasprite.id}_frames` matching
     * [generateMetaspriteDescriptor].
     *
     * **Variable name contract (canonical fallbacks):** When `posXVar` / `posYVar` / `idxVar` /
     * `rotVar` are NOT overridden, the emission uses the canonical `_posX` / `_posY` / `_idx` /
     * `_rot` globals that Phase 10's `Metasprites.kt` example declares via the DSL delegate
     * pattern. The port assembly continues to type-check without modification (Pitfall 6
     * mitigation). When a user calls the new `posX(varRef)` / `posY(varRef)` / `idx(varRef)` /
     * `rot(varRef)` DSL binders (Plan 03 substrate), the [io.github.gbkt.core.ir.MoveMetasprite]
     * caller in `ScriptOpVisitor.visitMoveMetasprite` passes the resolved names here.
     *
     * **Do NOT emit hide_sprites_range function body** — [ActorVisitor.generateHideSpritesRange]
     * already places the function definition in `main.c`. This method only calls it.
     *
     * @param metasprite The metasprite being rendered. Its [io.github.gbkt.core.ir.MetaspriteIR.id]
     *   is used to namespace both the descriptor pointer-table reference (`sprite_<id>_frames`) and
     *   the per-frame `_<id>_subPalette` / `_<id>_flipX` / `_<id>_flipY` global writes.
     * @param posXVar C identifier of the user-bound X-position variable. Defaults to the canonical
     *   `"_posX"` global for Phase 10 back-compat.
     * @param posYVar C identifier of the user-bound Y-position variable. Defaults to `"_posY"`.
     * @param idxVar C identifier of the user-bound frame-index variable. Defaults to `"_idx"`.
     * @param rotVar C identifier of the user-bound rotation/orientation variable. Defaults to
     *   `"_rot"`.
     * @param cameraOffsetX When non-null, emits screen-relative X formula
     *   `DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(<posXVar> >> 4)) - (INT16)<cameraOffsetX>)`.
     *   When null (default), emits absolute world-coordinate formula `DEVICE_SPRITE_PX_OFFSET_X +
     *   (<posXVar> >> 4)` — byte-identical to pre-Phase-12.3 emission for D-08 back-compat. Set to
     *   `"_camera_x"` by [ScriptOpVisitor.visitMoveMetasprite] when the game uses tilemap-camera
     *   mode (Phase 12.3 R4 / D-07 Option A).
     * @return A [CRawCode] containing the full rendering block.
     */
    fun generateMetaspriteFrameSwitch(
        metasprite: MetaspriteIR,
        posXVar: String = "_posX",
        posYVar: String = "_posY",
        idxVar: String = "_idx",
        rotVar: String = "_rot",
        cameraOffsetX: String? = null,
    ): CRawCode {
        val buf = StringBuilder()
        // Plan 13.3-05 D-01 Path A branch: for asset-driven metasprites (spritePath != null),
        // reference png2asset's native `<id>_metasprites[]` array instead of gbkt's
        // `sprite_<id>_frames[]` table. The escape-hatch D-04 path (spritePath == null)
        // keeps the legacy name unchanged.
        val frames =
            if (metasprite.spritePath != null) {
                "${metasprite.id}_metasprites"
            } else {
                "sprite_${metasprite.id}_frames"
            }
        // Phase 12.3 R4 / D-07 Option A — screen-relative vs absolute X formula.
        // When cameraOffsetX is null (default), the absolute formula is emitted byte-identically
        // to pre-Phase-12.3 (D-08 back-compat for all Phase 10/10.1 metasprite emission tests).
        val xExpr: String =
            if (cameraOffsetX != null) {
                "DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)($posXVar >> 4)) - (INT16)$cameraOffsetX)"
            } else {
                "DEVICE_SPRITE_PX_OFFSET_X + ($posXVar >> 4)"
            }
        val yExpr: String = "DEVICE_SPRITE_PX_OFFSET_Y + ($posYVar >> 4)"
        buf.append("{\n")
        // Plan 10.1-09 (WR-05 / SEED-011): `uint8_t hiwater = 0u;` HOISTED out of this per-call
        // switch block and into the SCENE FRAME function prelude by
        // `GBDKPipeline.wrapFrameWithMetaspriteHiwater`. The inner block scope is preserved
        // for `subpal` and the Plan 04 `_<id>_*` global writes; `hiwater` below resolves to the
        // outer function-scope declaration. Pre-fix the per-call init RESET the OAM cursor when
        // a frame called moveMetasprite() more than once, causing the second metasprite to
        // clobber the first metasprite's OAM allocation (Phase 12 platformer_template blocker).
        buf.append("    uint8_t subpal = $rotVar >> 2;\n")
        // D-V3 (SEED-006): sync `_<id>_subPalette` global so MCP sym-file reads reflect runtime
        // subpal.
        buf.append("    _${metasprite.id}_subPalette = subpal;\n")
        // D-12 (IN-01): sync `_<id>_flipX` global from low bit of `$rotVar & 0x3u`.
        buf.append("    _${metasprite.id}_flipX = ($rotVar & 0x3u) >> 0u;\n")
        // D-12 (IN-01): sync `_<id>_flipY` global from high bit of `$rotVar & 0x3u`.
        buf.append("    _${metasprite.id}_flipY = ($rotVar & 0x3u) >> 1u;\n")
        buf.append("    switch ($rotVar & 0x3u) {\n")

        // case 1: flip Y only
        buf.append("        case 1:\n")
        buf.append(
            "            hiwater += move_metasprite_flipy($frames[$idxVar], 0, subpal, hiwater,\n"
        )
        buf.append("                                              $xExpr,\n")
        buf.append("                                              $yExpr);\n")
        buf.append("            break;\n")

        // case 2: flip XY
        buf.append("        case 2:\n")
        buf.append(
            "            hiwater += move_metasprite_flipxy($frames[$idxVar], 0, subpal, hiwater,\n"
        )
        buf.append("                                              $xExpr,\n")
        buf.append("                                              $yExpr);\n")
        buf.append("            break;\n")

        // case 3: flip X only
        buf.append("        case 3:\n")
        buf.append(
            "            hiwater += move_metasprite_flipx($frames[$idxVar], 0, subpal, hiwater,\n"
        )
        buf.append("                                              $xExpr,\n")
        buf.append("                                              $yExpr);\n")
        buf.append("            break;\n")

        // default: no flip (move_metasprite_ex)
        buf.append("        default:\n")
        buf.append(
            "            hiwater += move_metasprite_ex($frames[$idxVar], 0, subpal, hiwater,\n"
        )
        buf.append("                                          $xExpr,\n")
        buf.append("                                          $yExpr);\n")
        buf.append("            break;\n")

        buf.append("    }\n")
        // Plan 10.1-09 (WR-05 / SEED-011): `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);`
        // HOISTED to the SCENE FRAME function postlude by
        // `GBDKPipeline.wrapFrameWithMetaspriteHiwater`. One call per frame, not one per
        // moveMetasprite() — pre-fix the second call's hide_sprites_range clobbered the OAM
        // slots written by the first metasprite.
        buf.append("}\n")

        return CRawCode(buf.toString())
    }
}
