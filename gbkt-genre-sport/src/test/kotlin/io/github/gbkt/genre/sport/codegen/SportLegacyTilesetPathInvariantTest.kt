/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.gbdk.GBDKBackend
import io.github.gbkt.core.dsl.game
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 11.2 — INV-8 / D-D3 LEGACY-path sentinel.
 *
 * Phase 11.2 introduces a NEW tileset pipeline (PNG → 2bpp → ConvertZoneTilesetsTask → synthesized
 * `_zone_<id>_tileset` arrays consumed via a generated `#include "_zone_<id>_tileset.h"`). The
 * pipeline coexists with the LEGACY hand-coded `_racing_<id>_tileset[48]` const-array path that
 * `SportVisitor` has emitted since Phase 07.4. The two paths run side-by-side per the
 * CONVENTIONS.md two-path coexistence (D-D1) and SportVisitor's deferred unification under
 * SEED-017.
 *
 * INV-7 (sibling, in `gbkt-backend-gbdk` BanksEmissionTest) locks the NEW-path positive shape.
 * INV-8 (this test) locks the LEGACY-path positive shape AND adds a negative assertion that
 * sport-racing has NOT been retrofitted onto the NEW path. Together INV-7 + INV-8 form the D-D3
 * sentinel pair guarding the two-path coexistence (D-03-inherited preservation).
 *
 * Failure mode this prevents (RESEARCH Pitfall 5 — partial retrofit): a future maintainer applies
 * the NEW-path symbolic-count `#include` pattern to SportVisitor but does not synthesize the
 * corresponding `_racing_<id>_tileset_count` macro — producing a half-rewired state that breaks the
 * GBDK build with an undefined identifier at link time. The negative assertion deterministically
 * catches this half-rewired state at the JVM tier BEFORE the ROM build runs.
 *
 * When a future SEED-017 unification phase ships and racing-tracks move onto the NEW pipeline, THIS
 * test is the deterministic owner of the boundary — flip both assertions then.
 */
class SportLegacyTilesetPathInvariantTest {

    @Test
    fun `INV-8 SportVisitor still emits hand-coded _racing_ id _tileset inline`() {
        // Racing-using GameIR fixture — mirrors the standard one-AI-vehicle pattern from
        // RacingEnterOpsEmissionTest.buildRacerLikeIR(). Racing id == "track1"; race scene
        // id == "race" so the scene-discovery fallback (gameIR.startScene) selects "race"
        // as the splice target.
        val ir =
            game("LegacySentinel") {
                    val pCar by actor { position(10, 20) }
                    val rCar by actor { position(80, 96) }
                    val carPlayer by vehicle { actor(pCar) }
                    val carAi by vehicle { actor(rCar) }
                    val track1 by racing {
                        player(carPlayer)
                        aiOpponents(carAi, count = 1)
                        track {
                            waypoint(x = 5, y = 5, checkpoint = true)
                            waypoint(x = 15, y = 5)
                            waypoint(x = 15, y = 15, checkpoint = true)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = track1
                    val raceScene = scene("race") { enter {} }
                    start = raceScene
                }
                .build()

        val result = GBDKBackend().generate(ir)
        val homeC =
            result.files["main.c"]?.content
                ?: error(
                    "main.c not generated — backend result.success=${result.success}, error=${result.error}"
                )

        // POSITIVE — LEGACY-path lock. SportVisitor.buildBuiltinTrackTilesetVarDecl()
        // still emits the 48-byte inline const-array declaration. Both substrings must
        // appear in main.c; the racingId ("track1") sits between them.
        assertTrue(
            homeC.contains("const UINT8 _racing_") && homeC.contains("_tileset[48]"),
            "SportVisitor must still emit _racing_<id>_tileset[48] inline const array. " +
                "Phase 11.2 boundary: sport legacy path is locked. SEED-017 captures " +
                "the future unification. If you intentionally moved sport-racing onto the " +
                "NEW pipeline, flip this assertion as part of the unification plan.",
        )

        // NEGATIVE — Pitfall 5 partial-retrofit guard. SportVisitor must NOT include
        // a generated tileset header — racing tracks remain on the LEGACY hand-coded
        // path until SEED-017 ships. If either substring appears, sport's tile data
        // went through ConvertZoneTilesetsTask, which is the Pitfall 5 partial-retrofit
        // failure mode.
        assertFalse(
            homeC.contains("#include \"_zone_") || homeC.contains("_tileset.h\""),
            "SportVisitor must NOT include a generated tileset header — racing tracks " +
                "remain on the LEGACY hand-coded path until SEED-017 ships. A partial " +
                "retrofit (NEW-path include without the matching `_racing_<id>_tileset_count` " +
                "macro) will produce an undefined-identifier link error at ROM build time. " +
                "See .planning/seeds/SEED-017-sport-zone-tileset-pipeline-unification.md " +
                "for the deferred unification work.",
        )
    }
}
