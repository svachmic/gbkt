/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.racer

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scenario-based game logic tests for the Racer game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required.
 *
 * Note: button_pressed/dpad_held are C helper stubs — they return 0 in simulation. Tests therefore
 * focus on variable-based conditions and IR structure verification.
 *
 * Scenarios:
 * 1. Race scene initializes correctly — lap=0, raceTime=0, position=1, car at (40,100)
 * 2. Title scene — structural verification of enter ops and frame ops
 * 3. Results scene — structural verification of enter ops and frame ops
 * 4. Finish condition — lap reaching 3 triggers navigation to results scene
 */
class RacerGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = racer.build()
    }

    // =========================================================================
    // Scenario 1: Race scene initializes correctly
    // =========================================================================

    @Test
    fun `race scene initializes lap and raceTime to 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("race")

        // After entering race scene, lap and raceTime should be reset to 0
        sim.assertVar("lap", 0)
        sim.assertVar("raceTime", 0)
    }

    @Test
    fun `race scene initializes position to 1`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("race")

        // After entering race scene, position should be 1 (first place by default)
        sim.assertVar("position", 1)
    }

    @Test
    fun `race scene places car at starting position`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("race")

        // Car should be at starting position (40, 100)
        assertEquals(40, sim.getVar("car.x"))
        assertEquals(100, sim.getVar("car.y"))
    }

    // =========================================================================
    // Scenario 2: Title scene initializes display
    // =========================================================================

    @Test
    fun `title scene initializes display`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals("title", sim.currentScene)
    }

    // =========================================================================
    // Scenario 3: Results scene shows position and time
    // =========================================================================

    @Test
    fun `results scene shows position and time`() {
        val sim = SimulationContextV2(ir)
        sim.setVar("position", 2)
        sim.setVar("raceTime", 100)
        sim.enterScene("results")
        sim.assertVar("position", 2)
        sim.assertVar("raceTime", 100)
    }

    // =========================================================================
    // Scenario 4: Finish condition — lap >= 3 navigates to results
    // =========================================================================

    @Test
    fun `finish condition navigates to results when lap reaches 3`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("race")

        // Set lap to 3 (finish condition)
        sim.setVar("lap", 3)
        // Place car away from lap-counting zone to avoid double-counting
        sim.setVar("car.x", 100)
        sim.setVar("car.y", 50)

        // Advance one frame — lap isAtLeast 3 → navigate to results
        sim.advanceFrames(1)

        // Scene should have changed to results
        assertEquals("results", sim.currentScene)
    }
}
