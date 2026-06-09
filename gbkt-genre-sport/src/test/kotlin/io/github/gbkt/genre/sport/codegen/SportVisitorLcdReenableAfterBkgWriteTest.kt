/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

// =============================================================================
// Plan 07.4-30 — RED test for the LCD re-enable (DISPLAY_ON) fix.
//
// Closes GAP-RACE-BLANK-AFTER-START: every scene-enter that calls set_bkg_data /
// set_bkg_tiles must emit DISPLAY_ON after the last VRAM write.
//
// GBDK's _set_bkg_data / _set_bkg_tiles call display_off() internally, leaving
// LCDC.7 = 0 after the VRAM writes. Without DISPLAY_ON after these calls the
// main loop's wait_vbl_done() spins forever (no VBlank while LCD is off) →
// EmulatorFrameHangException at frame 124.
//
// Invariants (per DIAGNOSIS.md `## RED test contract for Plan 30`):
//   A: DISPLAY_ON appears in the race_enter enterOps text.
//   B: DISPLAY_ON comes AFTER the last set_bkg_tiles call (positional ordering).
//   C: No set_bkg_tiles appears after DISPLAY_ON (no VRAM write left without wrap).
//
// Pre-fix (HEAD): all three invariants FAIL — SportVisitor does not emit DISPLAY_ON.
// Post-fix: all three invariants PASS after SportVisitor.buildRaceEnterOps() emits
//   RawOp("DISPLAY_ON;") after the set_bkg_tiles RawOp per D-N-05/D-N-06.
//
// Brace-walk scope extraction: the test operates on the enterOps text returned by
// SportVisitor.visit() (not on generated C file text), which IS the race_enter scope
// — it is directly the list of ops that will be spliced into race_enter. This is
// equivalent to brace-walking race_enter in generated C because the ops list is the
// canonical source-of-truth; the C shape is a deterministic rendering of this list.
//
// Per CLAUDE.md Scope-level grep gates corollary: assertions are made WITHIN the
// race_enter scope (the enterOps list), not on a whole-file text search.
// =============================================================================

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportVisitorLcdReenableAfterBkgWriteTest {

    // ----- Fixture builder -------------------------------------------------------

    /**
     * Minimal racer IR: one player vehicle, one AI rival, three-waypoint track. Mirrors
     * RacingEnterOpsEmissionTest.buildRacerLikeIR() — uses the same fixture shape so this test is
     * consistent with existing contract tests.
     */
    private fun buildRacerIR(): GameIR =
        game("LcdTest") {
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

    /** Concatenate the code text of every RawOp in an ops list, in order. */
    private fun rawOpsText(ops: List<ScriptOp>): String =
        ops.filterIsInstance<RawOp>().joinToString("\n") { it.code }

    /**
     * Extract the race_enter scope text from SportVisitor enterOps. This is the canonical scope —
     * what gets spliced into race_enter in the generated C. Equivalent to brace-walking race_enter
     * in bank1.c because the ops list is the upstream source that the C rendering deterministically
     * derives from.
     */
    private fun raceEnterBody(ir: GameIR): String {
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        val result = SportVisitor().visit("sport_racing", racingSystem.config, ir)
        val enterOps =
            result.enterOps["race"]
                ?: error("SportVisitor must populate enterOps[\"race\"] for a racing scene")
        return rawOpsText(enterOps)
    }

    // ----- Test A: DISPLAY_ON present in race_enter scope ------------------------

    @Test
    fun display_on_present_after_set_bkg_tiles_in_race_enter() {
        val ir = buildRacerIR()
        val body = raceEnterBody(ir)

        // Invariant A: DISPLAY_ON must appear in the race_enter scope.
        assertTrue(
            body.contains("DISPLAY_ON"),
            "race_enter enterOps must emit DISPLAY_ON after set_bkg_tiles to re-enable LCD.\n" +
                "GBDK set_bkg_data/set_bkg_tiles call display_off() internally; without DISPLAY_ON " +
                "the main loop's wait_vbl_done() hangs (no VBlank while LCD is off).\n" +
                "race_enter body:\n$body",
        )
    }

    // ----- Test B: DISPLAY_ON comes AFTER set_bkg_tiles -------------------------

    @Test
    fun display_on_after_set_bkg_tiles_positional() {
        val ir = buildRacerIR()
        val body = raceEnterBody(ir)

        val displayOnIdx = body.indexOf("DISPLAY_ON")
        val setBkgTilesIdx = body.indexOf("set_bkg_tiles")

        assertTrue(displayOnIdx >= 0, "race_enter body must contain DISPLAY_ON; body:\n$body")
        assertTrue(setBkgTilesIdx >= 0, "race_enter body must contain set_bkg_tiles; body:\n$body")

        // Invariant B: DISPLAY_ON must appear AFTER set_bkg_tiles.
        assertTrue(
            displayOnIdx > setBkgTilesIdx,
            "DISPLAY_ON (idx=$displayOnIdx) must appear AFTER set_bkg_tiles (idx=$setBkgTilesIdx) " +
                "in race_enter scope to re-enable LCD after the VRAM write.\n" +
                "race_enter body:\n$body",
        )
    }

    // ----- Test C: no set_bkg_tiles after DISPLAY_ON (all VRAM writes wrapped) --

    @Test
    fun no_set_bkg_tiles_after_display_on() {
        val ir = buildRacerIR()
        val body = raceEnterBody(ir)

        val displayOnIdx = body.indexOf("DISPLAY_ON")
        assertTrue(displayOnIdx >= 0, "race_enter body must contain DISPLAY_ON; body:\n$body")

        val afterDisplayOn = body.substring(displayOnIdx + "DISPLAY_ON".length)

        // Invariant C: no set_bkg_tiles should appear after DISPLAY_ON.
        // If a set_bkg_tiles appears after DISPLAY_ON, that VRAM write would re-disable
        // the LCD without a subsequent re-enable — the race is not yet fixed.
        assertFalse(
            afterDisplayOn.contains("set_bkg_tiles"),
            "No set_bkg_tiles should appear after DISPLAY_ON in race_enter scope.\n" +
                "All VRAM writes must be followed by DISPLAY_ON; text after DISPLAY_ON:\n$afterDisplayOn",
        )
    }
}
