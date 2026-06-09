/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.metasprite
import io.github.gbkt.core.dsl.moveMetasprite
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// METASPRITE DESCRIPTOR EMISSION TEST (Plan 10-15 Task 1 — RED→GREEN;
//  expectations updated for Phase 10.1 Plan 05 / CR-03 namespacing)
//
// Verifies that GBDKPipeline.buildHomeFile() calls
// MetaspriteVisitor.generateMetaspriteDescriptor() for each metasprite in
// gameIR.metasprites — emitting the sprite_<id>_frame_N[] per-frame OAM
// descriptor arrays and the sprite_<id>_frames[] pointer table into main.c.
//
// Root cause (Plan 10-15): generateMetaspriteDescriptor() was implemented in
// Plan 10-06 but never called from GBDKPipeline. The play_frame() body
// (emitted by ScriptOpVisitor.visitMoveMetasprite() via
// MetaspriteVisitor.generateMetaspriteFrameSwitch()) references the descriptor
// pointer table, which lcc reported as "Undefined identifier" (4 errors × 2
// error lines = 8 error lines, first-blocker-analysis.md).
//
// CR-03 / SEED-010 closure (Phase 10.1 Plan 05): the descriptor symbol names
// were renamed from the unnamespaced `sprite_metasprite_N` / `sprite_metaspriteS`
// pointer table to namespaced `sprite_<id>_frame_N` / `sprite_<id>_frames` so
// two metasprites no longer collide on the same global symbols. For the
// elephant metasprite used by these tests the expected emissions are
// `sprite_elephant_frame_0`, `sprite_elephant_frame_1`, `sprite_elephant_frame_2`
// and the `sprite_elephant_frames[]` pointer table.
//
// Tests 4-6 (Plan 10-15 continuation — RED→GREEN for set_sprite_data wiring):
// Verifies that GBDKPipeline.buildMainFunction() calls
// MetaspriteVisitor.generateMetaspriteTileData() for each metasprite in
// gameIR.metasprites — emitting set_sprite_data(0, N, <id>_tiles) in main() so
// VRAM tile data is loaded at game startup. The array name convention follows
// the metasprite's IR id: elephant → elephant_tiles.
// Without this, sprite tiles render blank even though descriptors compile.
// =============================================================================

class MetaspriteDescriptorEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // TEST 1 (RED→GREEN): Pipeline emits the namespaced sprite_<id>_frames[]
    // pointer table in main.c when a game uses a metasprite.
    //
    // Before Plan 10-15: main.c did NOT contain the descriptor at all → lcc
    // "Undefined identifier" error 20.
    // After Plan 10-15 + Phase 10.1 Plan 05 (CR-03): main.c DOES contain
    // `const metasprite_t* const sprite_elephant_frames[]` (namespaced by the
    // metasprite's id so multiple metasprites do not collide on a global name).
    // =========================================================================
    @Test
    fun `pipeline emits sprite_elephant_frames pointer table in main_c when metasprite is declared`() {
        val gameIR =
            game("MetaspriteDescriptorTest") {
                    val elephant by metasprite {
                        frame {
                            tile(0, 0, 0)
                            tile(8, 0, 1)
                        }
                        frame {
                            tile(0, 0, 2)
                            tile(8, 0, 3)
                        }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("sprite_elephant_frames"),
            "Expected 'sprite_elephant_frames' pointer table in main.c — " +
                "generateMetaspriteDescriptor() must be called from buildHomeFile() AND " +
                "must namespace symbols by metasprite id (CR-03).\n" +
                "main.c does not contain 'sprite_elephant_frames'.",
        )
    }

    // =========================================================================
    // TEST 2: Per-frame namespaced arrays (sprite_elephant_frame_0[],
    // sprite_elephant_frame_1[], sprite_elephant_frame_2[]) are emitted when the
    // metasprite has multiple frames. (CR-03 namespacing — Phase 10.1 Plan 05.)
    // =========================================================================
    @Test
    fun `pipeline emits per-frame descriptor arrays for each frame in main_c`() {
        val gameIR =
            game("MetaspriteDescriptorMultiFrameTest") {
                    val elephant by metasprite {
                        frame { tile(0, 0, 0) }
                        frame { tile(0, 0, 1) }
                        frame { tile(0, 0, 2) }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("sprite_elephant_frame_0"),
            "Expected 'sprite_elephant_frame_0' per-frame array in main.c",
        )
        assertTrue(
            mainC.contains("sprite_elephant_frame_1"),
            "Expected 'sprite_elephant_frame_1' per-frame array in main.c",
        )
        assertTrue(
            mainC.contains("sprite_elephant_frame_2"),
            "Expected 'sprite_elephant_frame_2' per-frame array in main.c",
        )
        assertTrue(
            mainC.contains("{metasprite_end}"),
            "Expected '{metasprite_end}' sentinel in per-frame array in main.c",
        )
    }

    // =========================================================================
    // TEST 3: No metasprite → no descriptor emitted (no regression for games
    // without metasprites — descriptor block must not appear for plain games).
    //
    // Post Phase 10.1 Plan 05 (CR-03): assertion is tightened to match the
    // namespaced symbol shape `const metasprite_t* const sprite_*_frames`
    // (the unnamespaced `sprite_metasprites` literal no longer exists in any
    // emission so checking for it would be trivially true).
    // =========================================================================
    @Test
    fun `pipeline does not emit any metasprite descriptor when game has no metasprites`() {
        val gameIR =
            game("NoMetaspriteTest") {
                    val playScene = scene("play") {}
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("const metasprite_t* const sprite_"),
            "Expected NO 'const metasprite_t* const sprite_…_frames[]' pointer table in " +
                "main.c for games without metasprites",
        )
        assertFalse(
            mainC.contains("const metasprite_t sprite_"),
            "Expected NO 'const metasprite_t sprite_…_frame_N[]' per-frame array in main.c " +
                "for games without metasprites",
        )
    }

    // =========================================================================
    // TESTS 4-6 (Plan 10-15 continuation — RED→GREEN: set_sprite_data wiring)
    //
    // Verifies that GBDKPipeline.buildMainFunction() emits
    // set_sprite_data(0, totalTiles, elephant_tiles) for each metasprite so VRAM
    // tile data is loaded at game startup.
    //
    // Convention: tileDataArrayName = "<metaspriteId>_tiles" (PHASE-13 gap:
    // MetaspriteBuilder.sprite() not yet implemented — fallback name derived from id).
    // totalTiles = max(tileId across all frames) + 1.
    // =========================================================================

    // TEST 4 (RED→GREEN): main.c contains set_sprite_data call for a metasprite
    @Test
    fun `pipeline emits set_sprite_data for metasprite tile data in main_c`() {
        val gameIR =
            game("MetaspriteTileDataTest") {
                    val elephant by metasprite {
                        frame {
                            tile(0, 0, 0)
                            tile(8, 0, 1)
                        }
                        frame {
                            tile(0, 0, 2)
                            tile(8, 0, 3)
                        }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("set_sprite_data"),
            "Expected 'set_sprite_data' call in main.c — " +
                "MetaspriteVisitor.generateMetaspriteTileData() must be called from " +
                "buildMainFunction() for each metasprite in gameIR.metasprites.\n" +
                "main.c does not contain 'set_sprite_data'.",
        )
    }

    // TEST 5 (RED→GREEN): set_sprite_data uses correct totalTiles (max tileId + 1)
    // For elephant with tileIds 0,1,2,3 → totalTiles = 4
    @Test
    fun `pipeline emits set_sprite_data with correct totalTiles for metasprite`() {
        val gameIR =
            game("MetaspriteTileCountTest") {
                    val elephant by metasprite {
                        frame {
                            tile(0, 0, 0)
                            tile(8, 0, 1)
                        }
                        frame {
                            tile(0, 0, 2)
                            tile(8, 0, 3)
                        }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        // totalTiles = max(tileId=3) + 1 = 4 → set_sprite_data(0u, 4u, elephant_tiles)
        assertTrue(
            mainC.contains("set_sprite_data(0u, 4u,"),
            "Expected 'set_sprite_data(0u, 4u,' in main.c for metasprite with max tileId=3.\n" +
                "totalTiles = max(tileId across all frames) + 1 = 4.\n" +
                "Actual main.c snippet around set_sprite_data:\n" +
                mainC.lines().filter { it.contains("set_sprite_data") }.joinToString("\n"),
        )
    }

    // TEST 6 (RED→GREEN): No metasprite → no set_sprite_data emitted (no regression for plain
    // games)
    @Test
    fun `pipeline does not emit set_sprite_data for metasprite when game has no metasprites`() {
        val gameIR =
            game("NoMetaspriteTileDataTest") {
                    val playScene = scene("play") {}
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("set_sprite_data"),
            "Expected NO 'set_sprite_data' in main.c for games without metasprites or actors with sprites",
        )
    }
}
