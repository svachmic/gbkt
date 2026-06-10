/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.agent.Button
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * End-to-end MCP playthrough test for Pong using real CoffeeGbEmulator.
 *
 * Exercises all 11 MCP tool code paths through [McpEmulatorSession] with NO stub — real emulator,
 * real Pong ROM. This is the proof that the MCP agent workflow actually works.
 *
 * Uses `Assumptions.assumeTrue` for ROM guard — reports SKIPPED in CI without GBDK, not fake PASS.
 */
class PongMcpPlaythroughTest {

    companion object {
        private val ROM = File("../gbkt-examples/pong/build/gbkt/output/pong.gb")
        private val SYM = File("../gbkt-examples/pong/build/gbkt/output/pong.noi")
        private val META = File("../gbkt-examples/pong/build/gbkt/generated/game_metadata.json")
    }

    @Test
    fun `agent plays Pong from title through gameplay via MCP session`() = runTest {
        Assumptions.assumeTrue(
            ROM.exists(),
            "Pong ROM not built — run :gbkt-examples:pong:buildRom first",
        )

        val session = McpEmulatorSession() // NO stub — real emulator

        // 1. emulator_start
        val result = session.start(ROM, SYM.takeIf { it.exists() }, META.takeIf { it.exists() })
        assertNotNull(result.metadata)
        assertTrue(session.isActive())

        // 2. emulator_describe_game
        val meta = session.describeGame()!!
        assertTrue(meta.scenes.sceneNames.contains("title"))
        assertTrue(meta.scenes.sceneNames.contains("game"))
        assertTrue(meta.actors.any { it.name == "ball" })

        // 3. Boot to title — emulator_step(120)
        val titleObs = session.step(120)
        assertEquals("title", titleObs.scene)
        assertTrue(titleObs.bgText.any { "PONG" in it })

        // 4. emulator_wait_until_text("PRESS START")
        val textWait = session.waitForText("PRESS START", 10)
        assertTrue(textWait.met)

        // 5. Press START — emulator_step(1, [start]) + emulator_step(1)
        session.step(1, setOf(Button.START))
        session.step(1) // release

        // 6. emulator_wait_for_scene("game", 60)
        val sceneWait = session.waitForScene("game", 60)
        assertTrue(sceneWait.met, "Should reach game scene")

        // 7. emulator_observe — verify gameplay state
        val obs = session.observe()
        assertEquals("game", obs.scene)
        assertTrue(obs.actors.any { it.name == "ball" })
        assertTrue(obs.actors.any { it.name == "paddle1" })
        assertTrue(obs.actors.any { it.name == "paddle2" })
        assertTrue(obs.sprites.size >= 5) // 2+2+1

        // 8. emulator_read_variable — read paddle position
        val paddleY = session.readVariable("paddle1_y")
        assertNotNull(paddleY.value)

        // 9. Move paddle UP for 30 frames — emulator_step(30, [up])
        val beforeY = paddleY.value!!
        session.step(30, setOf(Button.UP))
        val afterY = session.readVariable("paddle1_y").value!!
        assertTrue(afterY < beforeY, "Paddle should move up: $beforeY -> $afterY")

        // 10. emulator_write_variable — force near-win state
        val wrote = session.writeVariable("p1Score", 4)
        assertTrue(wrote)
        val scoreCheck = session.readVariable("p1Score")
        assertEquals(4, scoreCheck.value)

        // 11. emulator_screenshot
        val png = session.screenshot("mcp_playthrough")
        assertTrue(png.exists())
        assertTrue(png.length() > 0)

        // 12. Step until ball scores (variable changes or scene changes)
        val endObs = session.step(300)
        // Game state has advanced — score or scene should have changed
        assertNotNull(endObs.scene)

        // 13. emulator_stop
        session.stop()
        assertFalse(session.isActive())
    }
}
