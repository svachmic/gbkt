/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.hasActor
import io.github.gbkt.emulator.agent.toSummary
import io.github.gbkt.examples.pong.GameConstants.Actors
import io.github.gbkt.examples.pong.GameConstants.Scenes
import io.github.gbkt.examples.pong.GameConstants.Texts
import io.github.gbkt.examples.pong.GameConstants.Variables
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
 * Integration tests that verify the StepAgent pipeline works end-to-end against the real Pong ROM.
 *
 * Three test groups:
 * 1. **Metadata ↔ Symbol Table** — no emulator, checks that codegen-emitted metadata and .noi
 *    agree.
 * 2. **Full Pipeline** — boots the emulator and verifies observations, actors, variables, and
 *    input.
 * 3. **writeVariable round trip** — writes a value, reads it back, confirms observation
 *    consistency.
 *
 * All tests are skipped gracefully if the ROM or metadata file is not found (run `buildRom` first).
 */
class PongStepAgentTest {

    @JvmField @RegisterExtension val game = GbktTestExtension("pong")

    // ── Test 1: Metadata and symbol table agree on variable names (no emulator) ──

    @Test
    fun `metadata and symbol table agree on variable names`() {
        game.verifyMetadataSymbolAgreement(
            MetadataExpectation(
                expectedSceneCount = 3,
                expectedScenes = setOf(Scenes.TITLE, Scenes.GAME, Scenes.GAMEOVER),
                expectedActors = setOf(Actors.PADDLE1, Actors.PADDLE2, Actors.BALL),
                expectedOamCounts =
                    mapOf(Actors.PADDLE1 to 2, Actors.PADDLE2 to 2, Actors.BALL to 1),
                expectedTotalOam = 5,
            )
        )
    }

    // ── Test 2: Full pipeline — boot to gameplay with observations ──

    @Test
    fun `full pipeline - boot to gameplay with observations`() {
        val metadata = game.metadata!!

        // ── Phase 1: Boot & Title Screen (120 frames) ──
        val titleObs = game.stepN(120)
        assertEquals(120, titleObs.frame, "Frame counter should be 120 after boot")
        assertScene(titleObs, Scenes.TITLE, "Should be on title scene after boot")
        assertEquals(
            2,
            titleObs.variables[Variables.CURRENT_SCENE],
            "Title scene index should be 2",
        )
        assertTextOnScreen(titleObs, Texts.PONG, "Title screen should show PONG")
        assertTextOnScreen(titleObs, Texts.PRESS_START, "Title screen should show PRESS START")
        assertTextOnScreen(titleObs, Texts.FIRST_TO_5, "Title screen should show FIRST TO 5")

        // ── Phase 2: Scene Transition (START → gameplay) ──
        game.step(setOf(Button.START)) // press
        game.step() // release
        val gameObs = game.stepN(60) // wait for transition
        assertScene(gameObs, Scenes.GAME, "Should transition to game scene after START")
        assertEquals(1, gameObs.variables[Variables.CURRENT_SCENE], "Game scene index should be 1")

        // ── Phase 3: Gameplay Sprite Observation ──
        val spriteObs = game.stepN(10)
        assertEquals(5, spriteObs.sprites.size, "Expected 5 visible sprites (2+2+1)")
        assertEquals(3, spriteObs.actors.size, "Expected 3 actors")
        assertTrue(spriteObs.hasActor(Actors.BALL), "ball actor missing")
        assertTrue(spriteObs.hasActor(Actors.PADDLE1), "paddle1 actor missing")
        assertTrue(spriteObs.hasActor(Actors.PADDLE2), "paddle2 actor missing")
        for (actor in spriteObs.actors) {
            assertNotNull(actor.x, "Actor '${actor.name}' should have an x position")
            assertNotNull(actor.y, "Actor '${actor.name}' should have an y position")
        }

        // ── Phase 4: Actor-Variable Agreement (the key seam test) ──
        val varObs = game.step()
        for (actor in varObs.actors) {
            val meta = metadata.actor(actor.name)!!
            val inspectedX = game.readVariable(meta.xVar)
            val inspectedY = game.readVariable(meta.yVar)
            assertEquals(
                inspectedX,
                actor.x,
                "Actor '${actor.name}' x: readVariable(${meta.xVar})=$inspectedX != obs.x=${actor.x}",
            )
            assertEquals(
                inspectedY,
                actor.y,
                "Actor '${actor.name}' y: readVariable(${meta.yVar})=$inspectedY != obs.y=${actor.y}",
            )
        }

        // ── Phase 5: OAM Slot ↔ Actor Agreement ──
        for (actor in varObs.actors) {
            val meta = metadata.actor(actor.name)!!
            val expectedRange = meta.oamStart until (meta.oamStart + meta.oamCount)
            for (sprite in actor.sprites) {
                assertTrue(
                    sprite.index in expectedRange,
                    "Actor '${actor.name}' sprite slot ${sprite.index} outside expected range $expectedRange",
                )
            }
            assertEquals(
                meta.oamCount,
                actor.sprites.size,
                "Actor '${actor.name}' sprite count ${actor.sprites.size} != metadata oamCount ${meta.oamCount}",
            )
        }

        // ── Phase 6: Input Affects State ──
        val beforeY = game.readVariable(Variables.PADDLE1_Y)!!
        game.stepN(30, setOf(Button.UP))
        val afterUpY = game.readVariable(Variables.PADDLE1_Y)!!
        assertTrue(afterUpY < beforeY, "Paddle should move up: before=$beforeY, after=$afterUpY")

        game.stepN(30, setOf(Button.DOWN))
        val afterDownY = game.readVariable(Variables.PADDLE1_Y)!!
        assertTrue(
            afterDownY > afterUpY,
            "Paddle should move down: afterUp=$afterUpY, afterDown=$afterDownY",
        )

        // ── Phase 7: Ball Movement ──
        val ballXBefore = game.readVariable(Variables.BALL_X)!!
        val ballYBefore = game.readVariable(Variables.BALL_Y)!!
        game.stepN(60)
        val ballXAfter = game.readVariable(Variables.BALL_X)!!
        val ballYAfter = game.readVariable(Variables.BALL_Y)!!
        assertTrue(
            ballXBefore != ballXAfter || ballYBefore != ballYAfter,
            "Ball should move: ($ballXBefore,$ballYBefore) → ($ballXAfter,$ballYAfter)",
        )

        // ── Phase 8: toSummary() from Live State ──
        val summary = game.step().toSummary()
        assertTrue("Scene: ${Scenes.GAME}" in summary, "Summary should contain scene name")
        assertTrue("Sprites:" in summary, "Summary should contain sprite info")
        assertTrue("${Actors.BALL}(" in summary, "Summary should mention ball actor")
        assertTrue("${Actors.PADDLE1}(" in summary, "Summary should mention paddle1 actor")
        assertTrue("${Actors.PADDLE2}(" in summary, "Summary should mention paddle2 actor")
        assertTrue("Vars:" in summary, "Summary should contain variables")
        assertTrue("${Variables.BALL_X}=" in summary, "Summary should contain ball_x variable")

        // ── Phase 9: Screenshot Capture ──
        val file = game.captureScreenshot("integration_test")
        assertTrue(file.exists(), "Screenshot file should exist")
        assertTrue(file.length() > 0, "Screenshot file should not be empty")
        assertTrue(file.name.endsWith(".png"), "Screenshot should be a PNG file")
    }

    // ── Test 3: writeVariable round trip ──

    @Test
    fun `writeVariable round trip`() {
        // Boot to gameplay
        game.stepN(120)
        game.step(setOf(Button.START))
        game.step()
        game.stepN(60)

        // Write known value
        val wrote = game.writeVariable(Variables.BALL_X, 42)
        assertTrue(wrote, "writeVariable should succeed for ball_x")

        // Read back immediately (before frame advance)
        assertEquals(
            42,
            game.readVariable(Variables.BALL_X),
            "ball_x should read back 42 after write",
        )

        // Step one frame — game logic applies ballDx, so value shifts slightly
        val obs = game.step()
        val ballActor = obs.actors.find { it.name == Actors.BALL }!!
        assertNotNull(ballActor.x)
        assertTrue(
            ballActor.x!! in 39..45,
            "ball.x should be near 42 after write+frame, got ${ballActor.x}",
        )
    }
}
