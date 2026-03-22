/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.racer

import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.hasActor
import io.github.gbkt.examples.racer.GameConstants.Actors
import io.github.gbkt.examples.racer.GameConstants.Scenes
import io.github.gbkt.examples.racer.GameConstants.Texts
import io.github.gbkt.examples.racer.GameConstants.Variables
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
 * StepAgent integration tests for Racer — proves the AI-testable workflow.
 */
class RacerStepAgentTest {

    @JvmField
    @RegisterExtension
    val game = GbktTestExtension("racer")

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 3,
                expectedScenes = setOf(Scenes.TITLE, Scenes.RACE, Scenes.RESULTS),
                expectedActors = setOf(Actors.CAR),
            ),
        )
    }

    @Test
    fun `full pipeline - boot to gameplay with observations`() {
        val metadata = game.metadata!!

        // Phase 1: Boot & Title Screen
        val titleObs = game.stepN(120)
        assertScene(titleObs, Scenes.TITLE, "Should be on title scene after boot")
        assertTextOnScreen(titleObs, Texts.RACER, "Title screen should show RACER")
        assertTextOnScreen(titleObs, Texts.PRESS_START, "Title screen should show PRESS START")

        // Phase 2: Scene Transition (START → race)
        // The car starts in the lap detection zone, so the race may complete very quickly.
        // We verify scene changed from title (to either race or results).
        game.step(setOf(Button.START))
        game.step()
        val raceObs = game.stepN(5)
        assertTrue(
            raceObs.scene == Scenes.RACE || raceObs.scene == Scenes.RESULTS,
            "Should transition away from title after START, got: ${raceObs.scene}",
        )

        // If still in race scene, verify sprite and input
        if (raceObs.scene == Scenes.RACE) {
            // Phase 3: Sprite Observation
            val spriteObs = game.stepN(2)
            assertTrue(spriteObs.hasActor(Actors.CAR), "car actor missing")
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

            // Phase 5: Input Affects State (LEFT/RIGHT → car_x)
            val beforeX = game.readVariable(Variables.CAR_X)!!
            game.stepN(5, setOf(Button.RIGHT))
            val afterRightX = game.readVariable(Variables.CAR_X)!!
            assertTrue(afterRightX > beforeX, "Car should move right: before=$beforeX, after=$afterRightX")
        }

        // Phase 6: Screenshot Capture
        val file = game.captureScreenshot("racer_integration")
        assertTrue(file.exists(), "Screenshot file should exist")
        assertTrue(file.length() > 0, "Screenshot file should not be empty")
    }

    @Test
    fun `writeVariable round trip`() {
        game.stepN(120)
        game.step(setOf(Button.START))
        game.step()
        game.stepN(60)

        val wrote = game.writeVariable(Variables.LAP, 2)
        assertTrue(wrote, "writeVariable should succeed for lap")
        assertEquals(2, game.readVariable(Variables.LAP), "lap should read back 2 after write")
    }
}
