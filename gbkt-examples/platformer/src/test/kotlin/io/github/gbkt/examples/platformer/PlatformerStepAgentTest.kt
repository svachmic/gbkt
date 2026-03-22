/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer

import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.hasActor
import io.github.gbkt.examples.platformer.GameConstants.Actors
import io.github.gbkt.examples.platformer.GameConstants.Scenes
import io.github.gbkt.examples.platformer.GameConstants.Texts
import io.github.gbkt.examples.platformer.GameConstants.Variables
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
 * StepAgent integration tests for Platformer — proves the AI-testable workflow.
 */
class PlatformerStepAgentTest {

    @JvmField
    @RegisterExtension
    val game = GbktTestExtension("platformer")

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 3,
                expectedScenes = setOf(Scenes.TITLE, Scenes.GAMEPLAY, Scenes.WIN),
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
        assertTextOnScreen(titleObs, Texts.PLATFORMER, "Title screen should show PLATFORMER")
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

        // Phase 5: Input Affects State (LEFT/RIGHT → player_x)
        val beforeX = game.readVariable(Variables.PLAYER_X)!!
        game.stepN(30, setOf(Button.RIGHT))
        val afterRightX = game.readVariable(Variables.PLAYER_X)!!
        assertTrue(afterRightX > beforeX, "Player should move right: before=$beforeX, after=$afterRightX")

        // Phase 6: Screenshot Capture
        val file = game.captureScreenshot("platformer_integration")
        assertTrue(file.exists(), "Screenshot file should exist")
        assertTrue(file.length() > 0, "Screenshot file should not be empty")
    }

    @Test
    fun `writeVariable round trip`() {
        game.stepN(120)
        game.step(setOf(Button.START))
        game.step()
        game.stepN(60)

        val wrote = game.writeVariable(Variables.PLAYER_X, 42)
        assertTrue(wrote, "writeVariable should succeed for player_x")
        assertEquals(42, game.readVariable(Variables.PLAYER_X), "player_x should read back 42 after write")
    }
}
