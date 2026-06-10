/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Phase 11.2 Plan 06 — bank file #include for synthesized zone tileset header
//
// Plan 05 emits `set_bkg_data(0u, _zone_<id>_tileset_count, _zone_<id>_tileset)`
// in scene_enter when a ZoneIR with tilesetPath != null is bound to the scene.
// Those symbols are declared in `_zone_<sanitized>_tileset.h` (synthesized by
// the Gradle task in plan 03/04). Without the `#include` in the consuming bank
// file, lcc cannot resolve the symbols and the link step fails (Pitfall 4).
//
// This test locks the connector: `buildSceneFile()` MUST emit exactly one
// `#include "_zone_<sanitized>_tileset.h"` line per unique zone-tilesetPath
// pair referenced via SceneIR.zoneRefs, and MUST NOT emit any such line when
// no scene references a tileset zone (regression guard against an
// unconditional include).
//
// Pitfall 2 mitigation (mirrors Seed009BankIncludeTest): the test escapes the
// BankingAnalysisPass single-scene HOME fast-path by constructing 2 scenes,
// and asserts `bank1.c` is non-null BEFORE checking the include — otherwise a
// fast-path collapse would silently pass the assertion against `main.c`.
//
// D-claude-2 / D-A1 mirror: the sanitizer chain MUST match the inline pattern
// used at the four other call sites (lines 665, 703, 726, 1956, ~1471). The
// positive test uses an id with a `-` so the `.replace('-', '_')` step is
// exercised end-to-end.
// =============================================================================

class ZoneTilesetIncludeTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a 2-scene GameIR that escapes BankingAnalysisPass's single-scene HOME fast-path. The
     * `play` scene binds [boundZoneIds] via zoneRefs; the `title` scene is an empty stub used
     * solely to force multi-scene path.
     */
    private fun build2SceneGame(zones: List<ZoneIR>, boundZoneIds: List<String>): GameIR =
        GameIR(
            name = "ZoneTilesetIncludeGame",
            scenes = listOf(SceneIR(id = "title"), SceneIR(id = "play", zoneRefs = boundZoneIds)),
            zones = zones,
            startScene = "title",
        )

    // -------------------------------------------------------------------------
    // Test 1 — positive: a scene binds a zone whose tilesetPath != null
    //                     → bank1.c MUST include "_zone_<sanitized>_tileset.h"
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_includes_zone_tileset_header_when_scene_binds_zone_with_tilesetPath() {
        val zone =
            ZoneIR(
                id = "play_zone",
                name = "Play Zone",
                tilesetPath = "tiles/checker.png",
                mapWidth = 20,
                mapHeight = 18,
            )
        val game = build2SceneGame(zones = listOf(zone), boundZoneIds = listOf("play_zone"))

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        // Pitfall 2 mitigation: if bank1.c does not exist (fast-path collapse),
        // the include-check would silently pass against main.c. Fail loudly.
        assertNotNull(
            bank1,
            "bank1.c was not generated — multi-scene setup failed to escape " +
                "BankingAnalysisPass single-scene HOME fast-path. " +
                "Inspect output.files keys: ${output.files.keys}",
        )

        assertTrue(
            bank1.contains("#include \"_zone_play_zone_tileset.h\""),
            "bank1.c MUST include \"_zone_play_zone_tileset.h\" when a scene " +
                "binds a zone with tilesetPath != null (Plan 11.2-06 / D-B3). " +
                "Generated includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — sanitizer: zone id contains `-` and ` ` (replaced inline)
    //                     → header name uses underscores throughout
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_include_uses_sanitized_zone_id_for_header_name() {
        val zone =
            ZoneIR(
                id = "boss-room area",
                name = "Boss Room",
                tilesetPath = "tiles/boss.png",
                mapWidth = 20,
                mapHeight = 18,
            )
        val game = build2SceneGame(zones = listOf(zone), boundZoneIds = listOf("boss-room area"))

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        assertNotNull(bank1, "bank1.c not generated. Files: ${output.files.keys}")

        assertTrue(
            bank1.contains("#include \"_zone_boss_room_area_tileset.h\""),
            "bank1.c MUST sanitize the zone id (replace '-' and ' ' with '_') " +
                "before forming the header filename. Expected substring: " +
                "#include \"_zone_boss_room_area_tileset.h\". Actual includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — distinct: two scenes bind the SAME zone → exactly one include
    //                    line (header guards make duplicates harmless, but
    //                    the .distinct() call must keep the include list clean)
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_emits_single_include_line_when_two_scenes_share_same_zone() {
        val zone =
            ZoneIR(
                id = "shared_zone",
                name = "Shared Zone",
                tilesetPath = "tiles/shared.png",
                mapWidth = 20,
                mapHeight = 18,
            )
        val game =
            GameIR(
                name = "SharedZoneGame",
                scenes =
                    listOf(
                        SceneIR(id = "title", zoneRefs = listOf("shared_zone")),
                        SceneIR(id = "play", zoneRefs = listOf("shared_zone")),
                    ),
                zones = listOf(zone),
                startScene = "title",
            )

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        assertNotNull(bank1, "bank1.c not generated. Files: ${output.files.keys}")

        val includeCount = bank1.lines().count { it == "#include \"_zone_shared_zone_tileset.h\"" }
        assertEquals(
            1,
            includeCount,
            "bank1.c MUST emit exactly ONE \"_zone_shared_zone_tileset.h\" include " +
                "line even when two scenes bind the same zone (distinct() chain). " +
                "Actual count: $includeCount. Full include block:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 — negative (tilesetPath=null): procedural zone bound to a scene
    //                    → no include line emitted (the NEW path doesn't
    //                    synthesize a header for procedural zones, so emitting
    //                    an include would cause "no such file" at lcc time)
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_omits_include_when_bound_zone_has_null_tilesetPath() {
        val zone =
            ZoneIR(
                id = "procedural_zone",
                name = "Procedural Zone",
                tilesetPath = null, // Procedural — handled by genre visitor's LEGACY path
                mapWidth = 20,
                mapHeight = 18,
            )
        val game = build2SceneGame(zones = listOf(zone), boundZoneIds = listOf("procedural_zone"))

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        assertNotNull(bank1, "bank1.c not generated. Files: ${output.files.keys}")

        assertFalse(
            bank1.contains("_zone_procedural_zone_tileset.h"),
            "bank1.c MUST NOT include a _zone_<id>_tileset.h header for a zone " +
                "whose tilesetPath is null (Pitfall: header was never synthesized). " +
                "Generated includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 5 — negative (no zone-binding): scene has empty zoneRefs
    //                    → no include line emitted (regression guard against
    //                    an unconditional include for any zone in gameIR.zones)
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_omits_include_when_no_scene_binds_any_zone() {
        // Zone EXISTS with tilesetPath, but NO scene binds it via zoneRefs.
        // The include must be driven by SceneIR.zoneRefs, NOT by gameIR.zones
        // membership alone — otherwise an unreferenced zone would force an
        // include that the bank file does not need (and may not even resolve
        // if its header generation was skipped upstream).
        val zone =
            ZoneIR(
                id = "orphan_zone",
                name = "Orphan",
                tilesetPath = "tiles/orphan.png",
                mapWidth = 20,
                mapHeight = 18,
            )
        val game = build2SceneGame(zones = listOf(zone), boundZoneIds = emptyList())

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        assertNotNull(bank1, "bank1.c not generated. Files: ${output.files.keys}")

        assertFalse(
            bank1.contains("_zone_orphan_zone_tileset.h"),
            "bank1.c MUST NOT include _zone_orphan_zone_tileset.h when no scene " +
                "binds the orphan zone via zoneRefs (regression guard for " +
                "SceneIR.zoneRefs as the include-driver). Generated includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }
}
