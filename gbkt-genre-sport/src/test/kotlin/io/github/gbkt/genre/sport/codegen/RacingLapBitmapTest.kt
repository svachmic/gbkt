/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Plan 05 Wave-4 GREEN-flip of the Wave 0 RED stubs.
//
// VALIDATION.md row 01-T1 — covers SC-5 (real-circuit lap detection),
// D-15 (must-visit checkpoints in declared order), D-16 (non-checkpoint
// waypoints exist only for AI pathing), D-17 ("a lap means a real lap" —
// no ping-pong loophole).
//
// Contract assertions:
//  - _racing_visited_<id> UINT8 global emitted with initial value 0.
//  - emitted C contains both "mask_below" and "& mask_below".
//  - lap counter increments only when proximity-to-CP-0 AND _racing_visited
//    == all_set are both true.
//  - _racing_cp_x_<id>[] length == count of waypoints with isCheckpoint=true.
//  - pure-Kotlin simulation: 6× ping-pong across CP 0/CP 1 keeps lap counter at 0.
// =============================================================================

class RacingLapBitmapTest {

    /** Build a 3-checkpoint, 4-waypoint track and visit it through SportVisitor. */
    private fun visitFixture(): GenreVisitorResult {
        val ir =
            game("LapT") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val track1 by racing {
                        player(carPlayer)
                        track {
                            waypoint(x = 5, y = 5, checkpoint = true) // CP 0
                            waypoint(x = 15, y = 5) // non-CP, AI-only
                            waypoint(x = 15, y = 15, checkpoint = true) // CP 1
                            waypoint(x = 5, y = 15, checkpoint = true) // CP 2
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = track1
                    val raceScene = scene("race") { enter {} }
                    start = raceScene
                }
                .build()
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        return SportVisitor().visit("sport_racing", racingSystem.config, ir)
    }

    private fun bodyText(result: GenreVisitorResult): String {
        val fn =
            result.functions.filterIsInstance<CFunction>().first {
                it.name.startsWith("racing_tick_")
            }
        return CEmitter.emitFunction(fn)
    }

    @Test
    fun visited_bitmap_global_emitted() {
        val result = visitFixture()
        val decl =
            result.varDecls.filterIsInstance<CVarDecl>().firstOrNull {
                it.name == "_racing_visited_track1"
            }
        assertNotNull(decl, "Expected _racing_visited_track1 UINT8 global with initial 0")
    }

    @Test
    fun mask_below_order_check_present() {
        val result = visitFixture()
        val text = bodyText(result)
        assertTrue(text.contains("mask_below"), "Expected 'mask_below' in racing_tick body")
        assertTrue(
            text.contains("& mask_below"),
            "Expected '& mask_below' (AND with the order-check mask) in racing_tick body",
        )
    }

    @Test
    fun lap_counter_only_advances_on_first_checkpoint_with_full_bitmap() {
        val result = visitFixture()
        val text = bodyText(result)
        // The lap-completion CIf must reference both CP-0 proximity AND visited == all_set.
        // For 3 checkpoints, all_set = (1 << 3) - 1 = 7.
        assertTrue(
            text.contains("_racing_visited_track1 == 7"),
            "Expected lap-completion guard '_racing_visited_track1 == 7' (all_set for 3 CPs), got:\n$text",
        )
        assertTrue(
            text.contains("_racing_lap_count_track1 +="),
            "Expected lap counter to increment via compound add",
        )
        // Reset to 1 (CP 0 bit kept) — D-17 ping-pong defense by construction.
        assertTrue(
            text.contains("_racing_visited_track1 = 1"),
            "Expected lap-completion to reset _racing_visited to 1 (CP 0 bit kept), got:\n$text",
        )
    }

    @Test
    fun non_checkpoint_waypoints_used_only_for_ai() {
        val result = visitFixture()
        // 3 of 4 waypoints have isCheckpoint=true.
        val cpX =
            result.varDecls.filterIsInstance<CVarDecl>().firstOrNull {
                it.name == "_racing_cp_x_track1"
            }
        assertNotNull(cpX, "Expected _racing_cp_x_track1 array")
        // Verify the array initializer references exactly 3 entries (3 checkpoints out of 4
        // waypoints).
        val init = cpX.initializer.toString()
        // Cheap proxy: count comma-separated tokens — 3 entries → 2 commas inside the literal.
        val commaCount = init.count { it == ',' }
        assertEquals(
            2,
            commaCount,
            "Expected 3-entry checkpoint x array (2 commas inside braces), got initializer: $init",
        )
        // Full waypoint array is bigger — used by AI pool.
        val wpX =
            result.varDecls.filterIsInstance<CVarDecl>().firstOrNull {
                it.name == "_racing_wp_x_track1"
            }
        assertNotNull(wpX, "Expected _racing_wp_x_track1 array (used by AI pathing)")
        val wpInit = wpX.initializer.toString()
        val wpCommaCount = wpInit.count { it == ',' }
        assertEquals(
            3,
            wpCommaCount,
            "Expected 4-entry waypoint x array (3 commas inside braces), got initializer: $wpInit",
        )
    }

    @Test
    fun ping_pong_does_not_advance_lap_counter_in_simulation() {
        // Pure-Kotlin simulator of the emitted state machine semantics. Checkpoints in declared
        // order: CP 0 at (40, 40) px (= tile 5×8), CP 1 at (120, 120) px (= tile 15×8), CP 2 at
        // (40, 120) px. Player ping-pongs across CP 0 and CP 1 only — never visits CP 2.
        val cps = listOf(40 to 40, 120 to 120, 40 to 120)
        val numCps = cps.size
        val allSet = (1 shl numCps) - 1
        var visited = 0
        var checkpointIdx = 0
        var lapCount = 0
        var x = 40
        var y = 40

        fun tick() {
            // Bitmap advance per emitted machine.
            if (checkpointIdx < numCps) {
                val (cpX, cpY) = cps[checkpointIdx]
                val dx = if (x > cpX) x - cpX else cpX - x
                val dy = if (y > cpY) y - cpY else cpY - y
                if (dx < 8 && dy < 8) {
                    val maskBelow = (1 shl checkpointIdx) - 1
                    if ((visited and maskBelow) == maskBelow) {
                        visited = visited or (1 shl checkpointIdx)
                        checkpointIdx += 1
                    }
                }
            }
            // Lap-completion check.
            val (cp0X, cp0Y) = cps[0]
            val dx0 = if (x > cp0X) x - cp0X else cp0X - x
            val dy0 = if (y > cp0Y) y - cp0Y else cp0Y - y
            if (dx0 < 8 && dy0 < 8 && visited == allSet) {
                lapCount += 1
                visited = 1
                checkpointIdx = 1
            }
        }

        repeat(6) {
            // Move from CP 0 to CP 1.
            x = 120
            y = 120
            tick()
            // Back to CP 0.
            x = 40
            y = 40
            tick()
        }

        assertEquals(
            0,
            lapCount,
            "Ping-pong across CP 0/CP 1 must NOT increment lap counter (D-17). Got lapCount=$lapCount, visited=$visited",
        )
    }
}
