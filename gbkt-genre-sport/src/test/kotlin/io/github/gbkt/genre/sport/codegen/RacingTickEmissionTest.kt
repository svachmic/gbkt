/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Plan 05 Wave-4 GREEN-flip of the Wave 0 RED stubs.
//
// VALIDATION.md row 01-T1 — covers SC-3 (camera follows player), D-06 (camera
// auto-wire from racing()), and the SportVisitor → scene-frame injection path.
//
// Contract assertions (must hold once Plan 05 lands):
//  - SportVisitor produces a CFunction whose name starts with `racing_tick_`
//    (one per RacingConfig id).
//  - The body of `racing_tick_<id>` contains a CCall to `update_camera_<id>`
//    (D-06: racing owns the per-frame camera tick).
//  - SportVisitor.visit(...).frameOps[bound scene id] contains a RawOp whose
//    rendered C is `racing_tick_<id>();` — the pipeline's addGenreFrameOps
//    prepend phase splices it into the scene frame block.
// =============================================================================

class RacingTickEmissionTest {

    private fun buildRacingGame() =
        game("TickT") {
                val car by actor { position(0, 0) }
                val carPlayer by vehicle { actor(car) }
                val track1 by racing {
                    player(carPlayer)
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

    private fun visitRacingSystem(): Pair<io.github.gbkt.backend.api.GenreVisitorResult, String> {
        val ir = buildRacingGame()
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        val visitor = SportVisitor()
        val result = visitor.visit("sport_racing", racingSystem.config, ir)
        return result to "track1"
    }

    /** Walk a list of CStatements, calling [action] on every CCall encountered (deep). */
    private fun forEachCallDeep(body: List<CStatement>, action: (CCall) -> Unit) {
        for (stmt in body) {
            when (stmt) {
                is CExprStatement -> {
                    val expr = stmt.expr
                    if (expr is CCall) action(expr)
                }
                is io.github.gbkt.backend.gbdk.codegen.ast.CIf -> {
                    forEachCallDeep(stmt.thenBody, action)
                    forEachCallDeep(stmt.elseBody, action)
                }
                is io.github.gbkt.backend.gbdk.codegen.ast.CFor ->
                    forEachCallDeep(stmt.body, action)
                is io.github.gbkt.backend.gbdk.codegen.ast.CWhile ->
                    forEachCallDeep(stmt.body, action)
                is io.github.gbkt.backend.gbdk.codegen.ast.CSwitch ->
                    stmt.cases.forEach { forEachCallDeep(it.body, action) }
                is io.github.gbkt.backend.gbdk.codegen.ast.CBlock ->
                    forEachCallDeep(stmt.statements, action)
                else -> Unit
            }
        }
    }

    @Test
    fun racing_tick_function_emitted() {
        val (result, racingId) = visitRacingSystem()
        val fn =
            result.functions.filterIsInstance<CFunction>().firstOrNull {
                it.name.startsWith("racing_tick_")
            }
        assertNotNull(fn, "Expected a CFunction whose name starts with 'racing_tick_'")
        assertTrue(
            fn.name == "racing_tick_$racingId",
            "Expected racing_tick_$racingId, got ${fn.name}",
        )
    }

    @Test
    fun racing_tick_body_calls_update_camera() {
        val (result, _) = visitRacingSystem()
        val fn =
            result.functions.filterIsInstance<CFunction>().first {
                it.name.startsWith("racing_tick_")
            }
        var found = false
        forEachCallDeep(fn.body) { call ->
            if (call.function.startsWith("update_camera_")) found = true
        }
        assertTrue(
            found,
            "Expected racing_tick_<id> body to contain a CCall to update_camera_<cameraId>",
        )
    }

    @Test
    fun race_frame_calls_racing_tick() {
        val (result, racingId) = visitRacingSystem()
        // race scene id is 'race' from buildRacingGame()
        val ops =
            result.frameOps["race"]
                ?: error("Expected frameOps to contain an entry for the 'race' scene")
        // RawOp is the documented escape hatch — the pipeline lowers it to CRawCode whose text
        // is the literal C call site.
        val expectedSnippet = "racing_tick_$racingId"
        val matches = ops.filterIsInstance<RawOp>().any { it.code.contains(expectedSnippet + "(") }
        assertTrue(
            matches,
            "Expected frameOps['race'] to contain a RawOp calling $expectedSnippet(); got: $ops",
        )
    }
}
