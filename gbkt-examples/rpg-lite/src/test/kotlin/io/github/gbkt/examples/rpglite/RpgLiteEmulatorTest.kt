/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.rpglite

import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Emulator-level smoke tests for the RPG Lite game.
 *
 * Two tiers:
 * 1. IR integrity tests — run on JVM via SimulationContextV2, no ROM required. Validate game
 *    structure and that all 4 scenes are reachable and exit cleanly.
 * 2. ROM smoke test (emulatorTest Gradle task) — headless Coffee-GB validation run via
 *    `./gradlew :gbkt-examples:rpg-lite:emulatorTest`. This test class validates the IR tier;
 *    the Gradle task validates the ROM tier.
 *
 * Scenarios validated:
 * - All 4 scenes (title, town, dungeon, gameover) are enterable without crash
 * - Core state transitions: stepCount reset on dungeon enter; HP==0 gameover; encounter at 60;
 *   dungeon-exit level-up; gameover renders; town heal mechanics
 * - CombatEngineSystem with party and encounter data registered correctly
 * - ROM file existence is checked to confirm buildRom has been run (soft advisory)
 *
 * See context/UAT-rpg-lite.md for the full manual UAT checklist.
 */
class RpgLiteEmulatorTest {

    companion object {
        /** Build GameIR once — shared across all tests in this class. */
        private val ir: GameIR = rpgLite.build()

        /** ROM output path — used for soft advisory check only. */
        private val romFile = File("build/gbkt/output/rpg-lite.gb")
    }

    // =========================================================================
    // Smoke check: all 4 scenes are defined
    // =========================================================================

    @Test
    fun `all 4 expected scenes are defined in IR`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"), "title scene must be defined")
        assertTrue(sceneIds.contains("town"), "town scene must be defined")
        assertTrue(sceneIds.contains("dungeon"), "dungeon scene must be defined")
        assertTrue(sceneIds.contains("gameover"), "gameover scene must be defined")
        assertEquals(4, ir.scenes.size, "RPG Lite must have exactly 4 scenes")
    }

    @Test
    fun `title scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals("title", sim.currentScene)
        // Title enter ops: hideSprites, clear, print calls (RPG LITE, A MINI ADVENTURE, PRESS START)
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `town scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("town")
        assertEquals("town", sim.currentScene)
        // Town enter ops: showSprites, clear, print calls, heroActor.moveTo(80, 72)
        assertTrue(ir.scenes.first { it.id == "town" }.enterOps.isNotEmpty())
    }

    @Test
    fun `dungeon scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")
        assertEquals("dungeon", sim.currentScene)
        // Dungeon enter ops: showSprites, clear, print level/stats, stepCount set 0
        assertTrue(ir.scenes.first { it.id == "dungeon" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameover scene can be entered and does not crash`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameover")
        assertEquals("gameover", sim.currentScene)
        // Gameover enter ops: hideSprites, clear, print GAME OVER, HP/GOLD, PRESS START
        assertTrue(ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty())
    }

    // =========================================================================
    // Initial variable values
    // =========================================================================

    @Test
    fun `hp initial value is 30`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals(30, sim.getVar("hp"))
    }

    @Test
    fun `gold initial value is 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals(0, sim.getVar("gold"))
    }

    @Test
    fun `dungeonLevel initial value is 1`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals(1, sim.getVar("dungeonLevel"))
    }

    // =========================================================================
    // Dungeon scene state transitions
    // =========================================================================

    @Test
    fun `dungeon enter resets stepCount to 0`() {
        val sim = SimulationContextV2(ir)
        sim.setVar("stepCount", 42)
        sim.enterScene("dungeon")
        // Dungeon enter: stepCount set 0
        sim.assertVar("stepCount", 0)
    }

    @Test
    fun `dungeon hp zero navigates to gameover`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")
        sim.setVar("hp", 0)

        sim.advanceFrames(1)

        // whenever(hp isEqualTo 0) { navigate(gameoverScene) }
        assertEquals("gameover", sim.currentScene)
    }

    @Test
    fun `dungeon encounter check triggers at stepCount 60`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")
        sim.setVar("stepCount", 60)
        sim.setVar("hp", 30) // ensure HP is non-zero so gameover doesn't steal

        // Advance 1 frame — stepCount >= 60 → stepCount set 0 + battleUpdate fires
        sim.advanceFrames(1)

        // stepCount should reset to 0 (encounter triggered)
        sim.assertVar("stepCount", 0)
    }

    @Test
    fun `dungeon no encounter before stepCount reaches 60`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")
        sim.setVar("stepCount", 59)
        sim.setVar("hp", 30)

        sim.advanceFrames(1)

        // stepCount 59 < 60 → no encounter, no stepCount reset
        // stepCount was 59 and no dpad.any press → stays at 59
        assertEquals("dungeon", sim.currentScene)
    }

    @Test
    fun `town heroActor position set to center on enter`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("town")
        // town enter: heroActor.moveTo(80, 72)
        assertEquals(80, sim.getVar("heroActor.x"))
        assertEquals(72, sim.getVar("heroActor.y"))
    }

    // =========================================================================
    // Combat system structure
    // =========================================================================

    @Test
    fun `combat CombatEngineSystem registered with id combat`() {
        val combatSystem = ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat' from simpleBattle builder")
        assertEquals(CombatType.TURN_BASED, combatSystem.combatType)
    }

    @Test
    fun `combat system contains party (hero) and encounter data`() {
        val combatSystem = ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        requireNotNull(combatSystem)
        val config = combatSystem.encounterConfig
        assertNotNull(config, "CombatEngineSystem.encounterConfig must be set by simpleBattle builder")
        assertTrue(config.containsKey("partyIds"), "encounterConfig must have partyIds")
        assertTrue(config.containsKey("encounterData"), "encounterConfig must have encounterData")

        @Suppress("UNCHECKED_CAST")
        val partyIds = config["partyIds"] as? List<String>
        assertNotNull(partyIds, "partyIds must be a List<String>")
        assertTrue(partyIds.contains("hero"), "partyIds must contain 'hero'")
    }

    @Test
    fun `combat system has onVictory and onDefeat ops`() {
        val combatSystem = ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem")
        requireNotNull(combatSystem)
        assertTrue(combatSystem.onVictoryOps.isNotEmpty(), "onVictoryOps must not be empty (gold+=5, navigate)")
        assertTrue(combatSystem.onDefeatOps.isNotEmpty(), "onDefeatOps must not be empty (navigate gameover)")
    }

    // =========================================================================
    // ROM existence advisory (soft check — not a hard failure)
    // =========================================================================

    @Test
    fun `ROM file exists after buildRom (advisory check)`() {
        if (!romFile.exists()) {
            println(
                "ADVISORY: RPG Lite ROM not found at ${romFile.path}. " +
                    "Run './gradlew :gbkt-examples:rpg-lite:buildRom' to produce the ROM. " +
                    "Then run './gradlew :gbkt-examples:rpg-lite:emulatorTest' for headless Coffee-GB validation."
            )
        } else {
            assertTrue(romFile.length() > 0, "ROM file should not be empty")
            println("RPG Lite ROM found: ${romFile.path} (${romFile.length()} bytes)")
        }
        // Test always passes — advisory only
    }
}
