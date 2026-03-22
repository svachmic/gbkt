/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.rpglite

import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.hasActor
import io.github.gbkt.examples.rpglite.GameConstants.Actors
import io.github.gbkt.examples.rpglite.GameConstants.Scenes
import io.github.gbkt.examples.rpglite.GameConstants.Texts
import io.github.gbkt.examples.rpglite.GameConstants.Variables
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
 * StepAgent integration tests for RPG Lite — proves the AI-testable workflow.
 */
class RpgLiteStepAgentTest {

    @JvmField
    @RegisterExtension
    val game = GbktTestExtension("rpg-lite")

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 4,
                expectedScenes = setOf(Scenes.TITLE, Scenes.TOWN, Scenes.DUNGEON, Scenes.GAMEOVER),
                expectedActors = setOf(Actors.HERO_ACTOR),
            ),
        )
    }

    @Test
    fun `full pipeline - boot to gameplay with observations`() {
        val metadata = game.metadata!!

        // Phase 1: Boot & Title Screen
        val titleObs = game.stepN(120)
        assertScene(titleObs, Scenes.TITLE, "Should be on title scene after boot")
        assertTextOnScreen(titleObs, Texts.RPG_LITE, "Title screen should show RPG LITE")
        assertTextOnScreen(titleObs, Texts.PRESS_START, "Title screen should show PRESS START")

        // Phase 2: Scene Transition (START → town)
        game.step(setOf(Button.START))
        game.step()
        val townObs = game.stepN(60)
        assertScene(townObs, Scenes.TOWN, "Should transition to town scene after START")

        // Phase 3: Sprite Observation
        val spriteObs = game.stepN(10)
        assertTrue(spriteObs.hasActor(Actors.HERO_ACTOR), "heroActor actor missing")
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

        // Phase 5: Input Affects State (A button → enters dungeon)
        game.step(setOf(Button.A))
        game.step()
        val dungeonObs = game.stepN(60)
        assertScene(dungeonObs, Scenes.DUNGEON, "A button in town should enter dungeon")

        // Phase 6: Screenshot Capture
        val file = game.captureScreenshot("rpglite_integration")
        assertTrue(file.exists(), "Screenshot file should exist")
        assertTrue(file.length() > 0, "Screenshot file should not be empty")
    }

    @Test
    fun `writeVariable round trip`() {
        game.stepN(120)
        game.step(setOf(Button.START))
        game.step()
        game.stepN(60)

        val wrote = game.writeVariable(Variables.HP, 42)
        assertTrue(wrote, "writeVariable should succeed for hp")
        assertEquals(42, game.readVariable(Variables.HP), "hp should read back 42 after write")
    }
}
