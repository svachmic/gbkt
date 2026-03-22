/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.dungeon

import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.hasActor
import io.github.gbkt.examples.dungeon.GameConstants.Actors
import io.github.gbkt.examples.dungeon.GameConstants.Scenes
import io.github.gbkt.examples.dungeon.GameConstants.Texts
import io.github.gbkt.examples.dungeon.GameConstants.Variables
import io.github.gbkt.test.GbktTestExtension
import io.github.gbkt.test.MetadataExpectation
import io.github.gbkt.test.assertScene
import io.github.gbkt.test.assertTextOnScreen
import io.github.gbkt.test.verifyMetadataSymbolAgreement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * StepAgent integration tests for Dungeon — proves the AI-testable workflow.
 */
class DungeonStepAgentTest {

    @JvmField
    @RegisterExtension
    val game = GbktTestExtension("dungeon")

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 4,
                expectedScenes = setOf(Scenes.TITLE, Scenes.GAMEPLAY, Scenes.BATTLE, Scenes.GAMEOVER),
                expectedActors = setOf(Actors.PLAYER),
            ),
        )
    }

    @Test
    fun `full pipeline - boot to gameplay with observations`() {
        val metadata = game.metadata!!

        // Phase 1: Boot & Title Screen
        val titleObs = game.stepN(120)
        assertScene(titleObs, Scenes.TITLE, "Should be on title scene after boot")
        assertTextOnScreen(titleObs, Texts.DUNGEON, "Title screen should show DUNGEON")
        assertTextOnScreen(titleObs, Texts.PRESS_START, "Title screen should show PRESS START")

        // Phase 2: Scene Transition (START → gameplay)
        game.step(setOf(Button.START))
        game.step()
        val gameObs = game.stepN(60)
        assertScene(gameObs, Scenes.GAMEPLAY, "Should transition to gameplay scene after START")

        // Phase 3: Sprite Observation
        val spriteObs = game.stepN(10)
        assertTrue(spriteObs.hasActor(Actors.PLAYER), "player actor missing")
        for (actor in spriteObs.actors) {
            assertNotNull(actor.x, "Actor '${actor.name}' should have an x position")
            assertNotNull(actor.y, "Actor '${actor.name}' should have an y position")
        }

        // Phase 4: Actor-Variable Agreement
        val varObs = game.step()
        for (actor in varObs.actors) {
            val meta = metadata.actor(actor.name)!!
            assertEquals(game.readVariable(meta.xVar), actor.x, "Actor '${actor.name}' x mismatch")
            assertEquals(game.readVariable(meta.yVar), actor.y, "Actor '${actor.name}' y mismatch")
        }

        // Phase 5: Input Affects State (D-pad → player position)
        val beforeX = game.readVariable(Variables.PLAYER_X)!!
        game.stepN(30, setOf(Button.RIGHT))
        val afterRightX = game.readVariable(Variables.PLAYER_X)!!
        assertTrue(afterRightX > beforeX, "Player should move right: before=$beforeX, after=$afterRightX")

        // Phase 6: Screenshot Capture
        val file = game.captureScreenshot("dungeon_integration")
        assertTrue(file.exists(), "Screenshot file should exist")
        assertTrue(file.length() > 0, "Screenshot file should not be empty")
    }

    @Test
    fun `writeVariable round trip`() {
        game.stepN(120)
        game.step(setOf(Button.START))
        game.step()
        game.stepN(60)

        val wrote = game.writeVariable(Variables.TORCH_LEVEL, 42)
        assertTrue(wrote, "writeVariable should succeed for torchLevel")
        assertEquals(42, game.readVariable(Variables.TORCH_LEVEL), "torchLevel should read back 42 after write")
    }
}
