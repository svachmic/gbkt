/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.game
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.5 Plan 02 — screen() primitive emission contract (Req #18)
//
// Tests the synthetic `_screen_<sceneId>` zone created by `SceneBuilder.screen()`
// and the SceneVisitor screenMode superset branch (hide_sprites_range +
// move_bkg + fill_bkg_rect + centered _bkg_tiles_load_banked).
//
// Two tests locked here:
//
//   1. screenMode superset emission:
//      `scene("title") { screen(asset("graphics/title-screen.png")) }` synthesizes
//      a `_screen_title` zone registered in GameIR.zones. The `title_enter`
//      function must contain: `set_bkg_data`, `_zone__screen_title_tileset`,
//      `hide_sprites_range`, `move_bkg`, `fill_bkg_rect`, and the centering
//      expression `DEVICE_SCREEN_WIDTH - _zone__screen_title_tilemap_WIDTH`.
//      It must NOT contain `_bkg_tiles_load_banked(<bank>, 0u, 0u,` (0,0 place).
//
//   2. setup_current_level() filter (A2):
//      The synthetic `_screen_title` ID starts with `_screen_` and contains
//      "title" — the title/nextlevel filter in buildSetupCurrentLevelFunctionIfNeeded
//      must exclude it. The generated `setup_current_level()` switch (if any) must
//      NOT include `_screen_title`.
//
// Convention notes:
//   - extractFunctionBody helper is COPIED VERBATIM from LevelCardSceneEmissionTest
//     (PATTERNS.md § Scope-level grep gates corollary — per-test EVIDENCE_DIR
//     + helper pattern intentional).
//   - EVIDENCE_DIR points to the phase-LOCAL evidence/tier1-shape/ directory.
//   - RED: before screenMode branch and screen() DSL exist, test fails on import.
//   - GREEN: after Task 1 + Task 2 implementation, all assertions pass.
// =============================================================================

class ScreenPrimitiveEmissionTest {

    companion object {
        /**
         * Evidence written under the active checkout root (worktree-safe).
         *
         * Same pattern as LevelCardSceneEmissionTest — `user.dir` resolves to the
         * `:gbkt-backend-gbdk:test` working directory; we ascend one level to the
         * repo (or worktree) root then descend into the phase-local evidence dir.
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/" +
                        "13.5-framework-primitives-graphics-level-codegen-inserted/" +
                        "evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers — brace-walk extraction (awk-equivalent)
    //
    // COPIED VERBATIM from LevelCardSceneEmissionTest (PATTERNS.md § Scope-level
    // grep gates corollary — duplication intentional per per-test EVIDENCE_DIR
    // + helper convention established by LevelCardSceneEmissionTest).
    // -------------------------------------------------------------------------

    private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
        if (startIdx == -1) return ""
        val body = StringBuilder()
        var depth = 0
        var started = false
        for (i in startIdx until lines.size) {
            val line = lines[i]
            body.appendLine(line)
            for (ch in line) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (ch == '}') depth--
            }
            if (started && depth == 0) break
        }
        return body.toString()
    }

    // =========================================================================
    // Test 1 — screenMode superset emission in title_enter
    //
    // Locks the D-03/D-04/D-05/D-06 contract: SceneBuilder.screen() synthesizes a
    // `_screen_title` zone; SceneVisitor's screenMode branch emits:
    //   1. set_bkg_data(0, _zone__screen_title_tileset_count, _zone__screen_title_tileset)
    //   2. hide_sprites_range(0u, MAX_HARDWARE_SPRITES)
    //   3. move_bkg(0u, 0u)
    //   4. fill_bkg_rect(0u, 0u, 32u, 32u, 0u)
    //   5. _bkg_tiles_load_banked(<bank>, (DEVICE_SCREEN_WIDTH - _zone__screen_title_tilemap_WIDTH) / 2u,
    //        (DEVICE_SCREEN_HEIGHT - _zone__screen_title_tilemap_HEIGHT) / 2u,
    //        _zone__screen_title_tilemap_WIDTH, _zone__screen_title_tilemap_HEIGHT,
    //        _zone__screen_title_tilemap)
    //   6. DISPLAY_ON
    //
    // Scope-level grep gate: assertions fire against the brace-walked `title_enter`
    // body, NOT against bank1.c at file scope.
    //
    // RED: `screen()` DSL does not exist → compilation error → test fails to compile.
    // GREEN: after Task 1 + Task 2, all assertions pass.
    // =========================================================================

    @Test
    fun `screen() on title scene synthesizes _screen_title zone and emits screenMode superset`() {
        val gameIR =
            game("ScreenPrimitiveTest") {
                    val titleScene = scene("title") {
                        screen(asset("graphics/title-screen.png"))
                    }
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks all assertions inside title_enter's scope.
        val enterBody = extractFunctionBody(bank1C, "void title_enter")
        File(EVIDENCE_DIR, "title_enter_screen_superset.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "title_enter body must be extractable via brace-walk from bank1.c. " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // 1. Tileset pixel load — set_bkg_data must be present (zone is NEW-path:
        //    tilesetPath = "graphics/title-screen.png").
        assertTrue(
            enterBody.contains("set_bkg_data"),
            "title_enter must contain `set_bkg_data` (NEW-path tileset pixel load). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 2. Tileset symbol — _zone__screen_title_tileset (double underscore: zone prefix _ + id
        //    _screen_title → sanitized _screen_title). The id is `_screen_title`; the sanitized
        //    form replaces - and space with _. Since no dashes/spaces in `_screen_title`, the
        //    symbol is `_zone__screen_title_tileset`.
        assertTrue(
            enterBody.contains("_zone__screen_title_tileset"),
            "title_enter must reference `_zone__screen_title_tileset` (synthetic zone symbol). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 3. Sprite reset (D-06) — hide_sprites_range emitted by screenMode branch.
        assertTrue(
            enterBody.contains("hide_sprites_range"),
            "title_enter must contain `hide_sprites_range` (D-06 sprite reset in screenMode superset). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 4. Scroll reset (D-06) — move_bkg emitted by screenMode branch.
        assertTrue(
            enterBody.contains("move_bkg"),
            "title_enter must contain `move_bkg` (D-06 scroll reset in screenMode superset). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 5. BG clear (D-05) — fill_bkg_rect emitted by screenMode branch.
        assertTrue(
            enterBody.contains("fill_bkg_rect"),
            "title_enter must contain `fill_bkg_rect` (D-05 BG clear in screenMode superset). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 6. Centering expression (D-03/D-04 contract) — _bkg_tiles_load_banked with
        //    DEVICE_SCREEN_WIDTH - _zone__screen_title_tilemap_WIDTH centered placement.
        assertTrue(
            enterBody.contains("DEVICE_SCREEN_WIDTH - _zone__screen_title_tilemap_WIDTH"),
            "title_enter must contain the centering expression " +
                "`DEVICE_SCREEN_WIDTH - _zone__screen_title_tilemap_WIDTH` " +
                "(screenMode superset: centered tilemap placement). " +
                "enter body:\n${enterBody.take(4000)}",
        )

        // 7. Negative: the (0,0) origin placement must be ABSENT. screenMode zones
        //    never place at (0,0) — they always use centered coords.
        assertFalse(
            enterBody.contains("_bkg_tiles_load_banked(") &&
                Regex("_bkg_tiles_load_banked\\([^)]*,\\s*0u,\\s*0u,").containsMatchIn(enterBody),
            "title_enter must NOT emit the (0,0) origin tilemap-place " +
                "`_bkg_tiles_load_banked(<bank>, 0u, 0u, ...)` — screenMode zones use " +
                "centered coords only. " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    // =========================================================================
    // Test 2 — setup_current_level() filter (A2 — title/nextlevel exclusion)
    //
    // The synthetic zone ID `_screen_title` starts with `_screen_` and its lowercase
    // form contains "title". buildSetupCurrentLevelFunctionIfNeeded filters zones
    // whose lower-cased IDs contain "title" or "nextlevel". This test asserts that
    // the setup_current_level() switch body (if generated) does NOT include `_screen_title`.
    //
    // NOTE: main.c will contain `#include "_zone__screen_title_tileset.h"` — this is
    // a legitimate include directive for the tileset data and is NOT the same as
    // appearing in the setup_current_level switch. The assertion uses brace-walk
    // extraction to scope the check to the switch body only.
    //
    // If setup_current_level is not generated at all (no platformer physics config),
    // extractFunctionBody returns "" and the assertion trivially passes — correct (no
    // switch, no _screen_title in switch). This is Assumption A2 verification.
    // =========================================================================

    @Test
    fun `setup_current_level switch does NOT include _screen_title (A2 title-filter)`() {
        val gameIR =
            game("ScreenPrimitiveTitleFilterTest") {
                    val titleScene = scene("title") {
                        screen(asset("graphics/title-screen.png"))
                    }
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "main_screen_title_filter.c").writeText(mainC.take(8000))

        // Brace-walk extraction — scope the assertion to the setup_current_level body.
        // A file-level grep on main.c would false-positive on
        // `#include "_zone__screen_title_tileset.h"` which is a legitimate include for
        // the tileset pixel data (not part of the setup_current_level switch).
        val setupBody = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")

        // screenMode filter: when setup_current_level is generated, _screen_title must NOT
        // appear in its body (zone.screenMode == true excludes the synthetic screen zone
        // regardless of scene name). When setup_current_level is NOT generated (no
        // platformer physics), setupBody is "" and the assertion trivially passes.
        assertFalse(
            setupBody.contains("_screen_title"),
            "setup_current_level() switch must NOT reference `_screen_title` " +
                "(CR-02: zone.screenMode filter excludes synthetic screen zones). " +
                "setup body:\n${setupBody.take(4000)}",
        )
    }

    // =========================================================================
    // Test 3 — CR-02: screen() on non-title/nextlevel scene is excluded from
    //           setup_current_level() via screenMode field, NOT name heuristic.
    //
    // RED-capability: revert CR-02 (restore old title/nextlevel string-contains filter
    // in buildSetupCurrentLevelFunctionIfNeeded and buildLevelSpawnTablesIfNeeded).
    // The assertion `assertFalse(setupBody.contains("_screen_intro"))` FAILS because
    // "intro" does not match any of "title", "nextlevel", "next_level" — the old
    // heuristic lets _screen_intro through into the switch body.
    //
    // Under the CR-02 fix (zone.screenMode predicate), _screen_intro is always
    // excluded because its screenMode=true flag is set by SceneBuilder.screen().
    // =========================================================================

    @Test
    fun `CR-02 screen() on intro scene excluded from setup_current_level via screenMode not name heuristic`() {
        // Construct a GameIR with:
        //   - A synthetic screen zone (_screen_intro, screenMode=true)
        //   - A genuine gameplay zone (level1, screenMode=false)
        //   - A tilemap_collision GenericSystem so buildSetupCurrentLevelFunctionIfNeeded
        //     returns non-null (gameUsesTilemapCollision == true).
        val screenZone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "_screen_intro",
                name = "_screen_intro",
                tilesetPath = "graphics/intro-screen.png",
                screenMode = true,
            )
        val gameplayZone =
            io.github.gbkt.core.ir.ZoneIR(
                id = "level1",
                name = "level1",
                tilesetPath = "tilesets/level1.png",
                screenMode = false,
            )
        val gameIRWithZones =
            io.github.gbkt.core.ir.GameIR(
                name = "CR02IntroScreenTest",
                zones = listOf(screenZone, gameplayZone),
                systems =
                    listOf(
                        io.github.gbkt.core.ir.GenericSystem(
                            id = "tilemap_collision",
                            config =
                                mapOf(
                                    "type" to "tilemap_collision",
                                    "posXVar" to "player_x",
                                    "posYVar" to "player_y",
                                    "vxVar" to "player_vx",
                                    "vyVar" to "player_vy",
                                    "groundedVar" to "grounded",
                                ),
                        )
                    ),
            )

        EVIDENCE_DIR.mkdirs()

        val setupBody =
            pipeline.buildSetupCurrentLevelFunctionIfNeeded(
                gameIRWithZones,
                bankAllocation = mapOf("level1" to 2, "_screen_intro" to 3),
            ) ?: ""

        File(EVIDENCE_DIR, "setup_current_level_cr02_intro.c").writeText(setupBody)

        // 1. Function must be emitted — level1 is a valid gameplay zone.
        assertTrue(
            setupBody.isNotEmpty(),
            "buildSetupCurrentLevelFunctionIfNeeded must emit a non-empty function " +
                "when a genuine gameplay zone (level1, screenMode=false) is present.",
        )

        // 2. The gameplay zone level1 must appear in the switch.
        assertTrue(
            setupBody.contains("level1"),
            "setup_current_level switch must include `level1` (screenMode=false gameplay zone). " +
                "setup body:\n${setupBody.take(4000)}",
        )

        // 3. The screen zone _screen_intro must NOT appear in the switch.
        //    RED: under the old filter this assertion fails ("intro" is not "title"/"nextlevel").
        //    GREEN: under the CR-02 screenMode fix this assertion passes.
        assertFalse(
            setupBody.contains("_screen_intro"),
            "setup_current_level switch must NOT reference `_screen_intro` " +
                "(CR-02: zone.screenMode=true excludes it regardless of scene name 'intro'). " +
                "The old title/nextlevel heuristic would include this zone. " +
                "setup body:\n${setupBody.take(4000)}",
        )
    }
}
