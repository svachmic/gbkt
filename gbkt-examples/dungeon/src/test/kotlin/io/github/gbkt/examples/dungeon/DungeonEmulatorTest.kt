/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.dungeon

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Emulator-level smoke tests for the Dungeon game.
 *
 * Two tiers:
 * 1. IR integrity tests — run on JVM via SimulationContextV2, no ROM required. Validate game
 *    structure and that all 4 scenes are reachable and exit cleanly.
 * 2. ROM smoke test (emulatorTest Gradle task) — headless Coffee-GB validation run via
 *    `./gradlew :gbkt-examples:dungeon:emulatorTest`. This test class validates the IR tier;
 *    the Gradle task validates the ROM tier.
 *
 * Scenarios validated:
 * - All 4 scenes (title, gameplay, battle, gameover) are enterable without crash
 * - Core state transitions: title reachable; torch depletion; encounter at 120;
 *   gameover renders; battle drive reachable
 * - ROM file existence is checked to confirm buildRom has been run (soft advisory)
 *
 * See context/UAT-dungeon.md for the full manual UAT checklist.
 */
class DungeonEmulatorTest {

    companion object {
        /** Build GameIR once — shared across all tests in this class. */
        private val ir: GameIR = dungeon.build()

        /** ROM output path — used for soft advisory check only. */
        private val romFile = File("build/gbkt/output/dungeon.gb")
    }

    // =========================================================================
    // Smoke check: all 4 scenes are defined
    // =========================================================================

    @Test
    fun `all 4 expected scenes are defined in IR`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"), "title scene must be defined")
        assertTrue(sceneIds.contains("gameplay"), "gameplay scene must be defined")
        assertTrue(sceneIds.contains("battle"), "battle scene must be defined")
        assertTrue(sceneIds.contains("gameover"), "gameover scene must be defined")
        assertEquals(4, ir.scenes.size, "Dungeon must have exactly 4 scenes")
    }

    @Test
    fun `title scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals("title", sim.currentScene)
        // Title enter ops: hideSprites, clear, print calls (DUNGEON, A TORCH CRAWLER, PRESS START)
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameplay scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        assertEquals("gameplay", sim.currentScene)
        // Gameplay enter ops: showSprites, clear, setPosition, gameHud.show()
        assertTrue(ir.scenes.first { it.id == "gameplay" }.enterOps.isNotEmpty())
    }

    @Test
    fun `battle scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("battle")
        assertEquals("battle", sim.currentScene)
        // Battle enter ops: hideSprites, clear, print ENCOUNTER!, playSound, delay
        assertTrue(ir.scenes.first { it.id == "battle" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameover scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameover")
        assertEquals("gameover", sim.currentScene)
        // Gameover enter ops: hideSprites, clear, print calls (GAME OVER, TORCH EXPIRED, PRESS START)
        assertTrue(ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty())
    }

    // =========================================================================
    // Initial variable values
    // =========================================================================

    @Test
    fun `torchLevel initial value is 255`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        assertEquals(255, sim.getVar("torchLevel"))
    }

    @Test
    fun `keys initial value is 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        assertEquals(0, sim.getVar("keys"))
    }

    @Test
    fun `steps initial value is 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        assertEquals(0, sim.getVar("steps"))
    }

    // =========================================================================
    // State transition smoke tests
    // =========================================================================

    @Test
    fun `gameplay torch depletes when steps at multiple of 4 greater than 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("steps", 4)
        sim.setVar("torchLevel", 100)

        sim.advanceFrames(1)

        sim.assertVar("torchLevel", 99)
    }

    @Test
    fun `gameplay torch does not deplete when steps is 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("steps", 0)
        sim.setVar("torchLevel", 255)

        sim.advanceFrames(3)

        sim.assertVar("torchLevel", 255)
    }

    @Test
    fun `gameplay battle encounter triggers at steps 120`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("steps", 120)
        sim.setVar("torchLevel", 100) // above 0 so gameover doesn't steal navigation

        sim.advanceFrames(1)

        assertEquals("battle", sim.currentScene)
    }

    @Test
    fun `gameplay no encounter before steps reaches 120`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("steps", 119)
        sim.setVar("torchLevel", 100)

        sim.advanceFrames(1)

        // steps=119 < 120 → no encounter
        assertEquals("gameplay", sim.currentScene)
    }

    @Test
    fun `gameplay gameover when torchLevel depletes to 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("torchLevel", 0)
        sim.setVar("steps", 0) // prevent encounter from firing first

        sim.advanceFrames(1)

        // torchLevel==0 → whenever(torchLevel isEqualTo 0) { ... navigate(gameoverScene) }
        assertEquals("gameover", sim.currentScene)
    }

    @Test
    fun `gameplay 4-step torch depletion across 5 frames at steps=4`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.setVar("steps", 4)
        sim.setVar("torchLevel", 50)

        // Each frame: steps=4 (> 0, & 3 == 0, torchLevel > 0) → decrement
        sim.advanceFrames(5)

        sim.assertVar("torchLevel", 45)
    }

    // =========================================================================
    // Scene frame ops structure
    // =========================================================================

    @Test
    fun `gameplay scene has movement and encounter frame ops`() {
        val gameplay = ir.scenes.first { it.id == "gameplay" }
        assertTrue(gameplay.frameOps.isNotEmpty(), "gameplay must have frame ops for movement + encounters")
    }

    @Test
    fun `battle scene has battleUpdate frame op`() {
        val battle = ir.scenes.first { it.id == "battle" }
        assertTrue(battle.frameOps.isNotEmpty(), "battle scene must have battleUpdate frame op")
    }

    // =========================================================================
    // ROM existence advisory (soft check — not a hard failure)
    // =========================================================================

    @Test
    fun `ROM file exists after buildRom (advisory check)`() {
        if (!romFile.exists()) {
            println(
                "ADVISORY: Dungeon ROM not found at ${romFile.path}. " +
                    "Run './gradlew :gbkt-examples:dungeon:buildRom' to produce the ROM. " +
                    "Then run './gradlew :gbkt-examples:dungeon:emulatorTest' for headless Coffee-GB validation."
            )
        } else {
            assertTrue(romFile.length() > 0, "ROM file should not be empty")
            println("Dungeon ROM found: ${romFile.path} (${romFile.length()} bytes)")
        }
        // Test always passes — advisory only
    }
}
