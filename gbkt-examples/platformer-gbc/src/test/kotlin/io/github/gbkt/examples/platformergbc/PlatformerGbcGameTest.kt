/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformergbc

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scenario-based game logic tests for the Platformer GBC game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required.
 *
 * Scenarios:
 * 1. Gameplay initializes — player position resets on scene enter
 * 2. Title scene enter ops execute cleanly
 */
class PlatformerGbcGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = platformerGbc.build()
    }

    // =========================================================================
    // Scenario 1: Gameplay scene initializes
    // =========================================================================

    @Test
    fun `gameplay scene initializes player position`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // After entering gameplay, player should be at spawn position (20, 104)
        assertEquals(20, sim.getVar("player.x"))
        assertEquals(104, sim.getVar("player.y"))
    }

    // =========================================================================
    // Scenario 2: Title scene enter ops
    // =========================================================================

    @Test
    fun `title scene enter ops execute without error`() {
        val sim = SimulationContextV2(ir)
        // Entering title should not throw — verify enter ops run cleanly
        sim.enterScene("title")
        // Title scene sets up display; current scene should be "title"
        assertEquals("title", sim.currentScene)
    }

    // =========================================================================
    // Scenario 3: Player position tracking
    // =========================================================================

    @Test
    fun `player position tracking after gameplay enter`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Player starts at exactly (20, 104)
        assertEquals(20, sim.getVar("player.x"))
        assertEquals(104, sim.getVar("player.y"))
    }
}
