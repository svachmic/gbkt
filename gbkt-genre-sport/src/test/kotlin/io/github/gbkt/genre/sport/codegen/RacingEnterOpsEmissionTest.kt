/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Plan 07.4-11 — RED tests for SportVisitor.visitRacingNew enterOps emission.
//
// Closes GAP-A (AI pool spawn), GAP-B (track tileset + tilemap load), and
// GAP-D (camera target bind). The visitor must populate
// GenreVisitorResult.enterOps[raceSceneId] with three op groups, in this
// order:
//   1. _camera_target = <playerActorIdx>u;                         (GAP-D)
//   2. set_bkg_data(0, 3, _racing_<id>_tileset);                   (GAP-B)
//      set_bkg_tiles(0, 0, <mapW>u, <mapH>u, _zone_<id>_tiles);    (GAP-B)
//      _current_tileset_id = 1u;                                   (GAP-B sentinel)
//   3. pool_<aiVehicleId>_spawn(<rivalX>u, <rivalY>u);             (GAP-A)
//
// Plus result.varDecls must contain a CVarDecl named _racing_<id>_tileset
// (48-byte builtin 3-tile track tileset).
//
// Scene-discovery fallback: with the DSL test fixture we use start = raceScene
// so gameIR.startScene == "race" — Plan 05's scene-discovery chain step 3
// (gameIR.startScene) selects the race scene as the splice target.
// =============================================================================

class RacingEnterOpsEmissionTest {

    // ----- Fixture builders -----------------------------------------------------

    /**
     * Standard one-AI-vehicle fixture used by Tests 1, 3, 4, 5, 7, 8.
     *
     * Player actor `pCar` at (10, 20). Rival actor `rCar` at (80, 96) — pixel coords flow into the
     * emitted `pool_carAi_spawn(80u, 96u);` RawOp args.
     *
     * Racing id == "track1", AI vehicle id == "carAi". Race scene id == "race". start = raceScene so
     * the scene-discovery fallback resolves to "race".
     */
    private fun buildRacerLikeIR(): GameIR =
        game("EnterT") {
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

    /**
     * Two-AI-vehicle fixture used by Test 2 (multi-slot pool spawn).
     *
     * Two distinct rival actors at (40, 40) and (60, 80). aiOpponents() is called twice with count
     * = 1 each so each AI vehicle is its own pool with its own spawn helper.
     */
    private fun buildTwoAiIR(): GameIR =
        game("EnterT2") {
                val pCar by actor { position(10, 20) }
                val rA by actor { position(40, 40) }
                val rB by actor { position(60, 80) }
                val carPlayer by vehicle { actor(pCar) }
                val carAiA by vehicle { actor(rA) }
                val carAiB by vehicle { actor(rB) }
                val track1 by racing {
                    player(carPlayer)
                    aiOpponents(carAiA, count = 1)
                    aiOpponents(carAiB, count = 1)
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

    /**
     * Generic-id fixture used by Test 6 — D-04 / D-02 reflexive guard.
     *
     * Property names share NO substring with Racer-specific tokens (carAi / track1 / carPlayer).
     */
    private fun buildGenericIR(): GameIR =
        game("Generic") {
                val p1 by actor { position(11, 22) }
                val r1 by actor { position(33, 44) }
                val pVeh by vehicle { actor(p1) }
                val rVeh by vehicle { actor(r1) }
                val t1 by racing {
                    player(pVeh)
                    aiOpponents(rVeh, count = 1)
                    track {
                        waypoint(x = 5, y = 5, checkpoint = true)
                        waypoint(x = 15, y = 5)
                        waypoint(x = 15, y = 15, checkpoint = true)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val keep = t1
                val raceScene = scene("race") { enter {} }
                start = raceScene
            }
            .build()

    private fun visitRacing(ir: GameIR, racingId: String): GenreVisitorResult {
        val racingSystem = ir.systems.find { it.id == racingId } as GenericSystem
        return SportVisitor().visit("sport_racing", racingSystem.config, ir)
    }

    /** Concatenate the rendered text of every RawOp in a list, in order. */
    private fun rawOpsText(ops: List<io.github.gbkt.core.ir.ScriptOp>): String =
        ops.filterIsInstance<RawOp>().joinToString("\n") { it.code }

    // ----- Test 1: pool spawn emitted per AI slot -------------------------------

    @Test
    fun pool_spawn_emitted_per_ai_slot() {
        val ir = buildRacerLikeIR()
        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"]
        assertNotNull(ops, "Expected enterOps to contain entry for race scene")
        val poolSpawns =
            ops.filterIsInstance<RawOp>().filter { it.code.contains("pool_carAi_spawn(") }
        assertEquals(
            1,
            poolSpawns.size,
            "Expected exactly 1 pool_carAi_spawn RawOp; got: ${poolSpawns.map { it.code }}",
        )
        // Coordinate args derive from the rival actor's position(80, 96).
        assertTrue(
            poolSpawns[0].code.contains("80") && poolSpawns[0].code.contains("96"),
            "Expected pool_carAi_spawn args derived from rival.position(80, 96), got: ${poolSpawns[0].code}",
        )
    }

    // ----- Test 2: multiple AI vehicles → multiple spawn calls ------------------

    @Test
    fun pool_spawn_emitted_per_each_ai_when_multiple() {
        val ir = buildTwoAiIR()
        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)
        assertTrue(
            text.contains("pool_carAiA_spawn(") && text.contains("pool_carAiB_spawn("),
            "Expected pool spawn ops for both carAiA and carAiB; got:\n$text",
        )
        // Declared-order preservation: carAiA is declared first via aiOpponents(carAiA, ...)
        // so its spawn op must appear before carAiB's.
        val idxA = text.indexOf("pool_carAiA_spawn(")
        val idxB = text.indexOf("pool_carAiB_spawn(")
        assertTrue(
            idxA in 0 until idxB,
            "Expected carAiA spawn before carAiB spawn (declared order); got idxA=$idxA idxB=$idxB",
        )
    }

    // ----- Test 3: tileset load triple emitted ---------------------------------

    @Test
    fun tileset_load_ops_emitted() {
        val ir = buildRacerLikeIR()
        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)
        assertTrue(
            text.contains("set_bkg_data(0, 3, _racing_track1_tileset)"),
            "Expected set_bkg_data(0, 3, _racing_track1_tileset); in enterOps; got:\n$text",
        )
        assertTrue(
            Regex("set_bkg_tiles\\(0, 0, \\d+u, \\d+u, _zone_track1_tiles\\)")
                .containsMatchIn(text),
            "Expected set_bkg_tiles(0, 0, <w>u, <h>u, _zone_track1_tiles); in enterOps; got:\n$text",
        )
        assertTrue(
            text.contains("_current_tileset_id = 1u"),
            "Expected _current_tileset_id = 1u; sentinel in enterOps; got:\n$text",
        )
    }

    // ----- Test 4: camera target assigned ---------------------------------------

    @Test
    fun camera_target_assigned() {
        val ir = buildRacerLikeIR()
        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)
        assertTrue(
            Regex("_camera_target = \\d+u;").containsMatchIn(text),
            "Expected _camera_target = <idx>u; in enterOps; got:\n$text",
        )
        // Player actor pCar is the FIRST actor declared in the fixture, so playerActorIdx == 0.
        // (If the racing delegate adds synthetic actors before pCar, this test will need
        // adjustment; for the current Plan 03 delegate it does not.)
        val pCarIdx = ir.actors.indexOfFirst { it.id == "pCar" }
        assertEquals(
            0,
            pCarIdx,
            "Sanity check on fixture: pCar is expected to be actor index 0 (got $pCarIdx)",
        )
        assertTrue(
            text.contains("_camera_target = ${pCarIdx}u;"),
            "Expected _camera_target = ${pCarIdx}u; (idx of pCar in gameIR.actors); got:\n$text",
        )
    }

    // ----- Test 5: builtin tileset varDecl emitted ------------------------------

    @Test
    fun builtin_tileset_var_decl_emitted() {
        val ir = buildRacerLikeIR()
        val result = visitRacing(ir, "track1")
        val tilesetDecl =
            result.varDecls.filterIsInstance<CVarDecl>().firstOrNull {
                it.name == "_racing_track1_tileset"
            }
        assertNotNull(
            tilesetDecl,
            "Expected varDecls to contain CVarDecl named '_racing_track1_tileset'",
        )
    }

    // ----- Test 6: generic contract — no magic strings --------------------------

    @Test
    fun generic_contract_no_magic_strings() {
        val ir = buildGenericIR()
        val result = visitRacing(ir, "t1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)

        assertTrue(
            text.contains("pool_rVeh_spawn("),
            "Expected pool_rVeh_spawn(... in generic enterOps; got:\n$text",
        )
        assertTrue(
            text.contains("_zone_t1_tiles"),
            "Expected _zone_t1_tiles reference in generic enterOps; got:\n$text",
        )
        assertTrue(
            text.contains("_racing_t1_tileset"),
            "Expected _racing_t1_tileset reference in generic enterOps; got:\n$text",
        )

        // D-04 / D-02 reflexive guard: forbidden Racer-specific tokens must not appear.
        for (forbidden in listOf("carAi", "track1", "carPlayer")) {
            assertTrue(
                !text.contains(forbidden),
                "Generic enterOps must not contain Racer-specific token '$forbidden'; got:\n$text",
            )
        }
        // Same guard on emitted varDecls — the builtin tileset name carries the racingId.
        val tilesetNames = result.varDecls.filterIsInstance<CVarDecl>().map { it.name }
        for (forbidden in listOf("carAi", "track1", "carPlayer")) {
            for (name in tilesetNames) {
                assertTrue(
                    !name.contains(forbidden),
                    "Generic varDecl '$name' contains Racer-specific token '$forbidden'",
                )
            }
        }
    }

    // ----- Test 7: ordering — camera, tileset, pool spawn -----------------------

    @Test
    fun ordering_camera_target_before_tileset_before_pool_spawn() {
        val ir = buildRacerLikeIR()
        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)

        val idxCam = text.indexOf("_camera_target = ")
        val idxBkgData = text.indexOf("set_bkg_data(0, 3,")
        val idxBkgTiles = text.indexOf("set_bkg_tiles(0, 0,")
        val idxSentinel = text.indexOf("_current_tileset_id = 1u")
        val idxPool = text.indexOf("pool_carAi_spawn(")

        assertTrue(idxCam >= 0, "expected _camera_target in: $text")
        assertTrue(idxBkgData >= 0, "expected set_bkg_data in: $text")
        assertTrue(idxBkgTiles >= 0, "expected set_bkg_tiles in: $text")
        assertTrue(idxSentinel >= 0, "expected _current_tileset_id sentinel in: $text")
        assertTrue(idxPool >= 0, "expected pool_carAi_spawn in: $text")

        assertTrue(
            idxCam < idxBkgData,
            "Expected _camera_target before set_bkg_data; got idxCam=$idxCam idxBkgData=$idxBkgData",
        )
        assertTrue(
            idxBkgData < idxBkgTiles,
            "Expected set_bkg_data before set_bkg_tiles; got idxBkgData=$idxBkgData idxBkgTiles=$idxBkgTiles",
        )
        assertTrue(
            idxBkgTiles < idxSentinel,
            "Expected set_bkg_tiles before _current_tileset_id sentinel; got idxBkgTiles=$idxBkgTiles idxSentinel=$idxSentinel",
        )
        assertTrue(
            idxSentinel < idxPool,
            "Expected _current_tileset_id sentinel before pool_carAi_spawn; got idxSentinel=$idxSentinel idxPool=$idxPool",
        )
    }

    // ----- Test 8: zone dimensions used (mapWidth / mapHeight) ------------------

    @Test
    fun set_bkg_tiles_uses_actual_zone_dimensions() {
        val ir = buildRacerLikeIR()
        val zone = ir.zones.firstOrNull { it.id == "track1" }
        assertNotNull(zone, "Expected synthesized ZoneIR id == 'track1'")
        val expectedW = zone.mapWidth
        val expectedH = zone.mapHeight

        val result = visitRacing(ir, "track1")
        val ops = result.enterOps["race"] ?: error("Expected enterOps[race]")
        val text = rawOpsText(ops)
        assertTrue(
            text.contains("set_bkg_tiles(0, 0, ${expectedW}u, ${expectedH}u, _zone_track1_tiles)"),
            "Expected set_bkg_tiles to use mapWidth=$expectedW mapHeight=$expectedH from synthesized ZoneIR; got:\n$text",
        )
    }
}
