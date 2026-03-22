/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.TestRomFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Integration tests that verify the agent layer works with the real CoffeeGbEmulator.
 *
 * All tests use [TestRomFactory.createMinimalRom] — always runs in CI, no GBDK needed.
 * This closes the gap between "unit-tested with stubs" and "battle-tested with real emulator".
 */
class RealEmulatorAgentTest {

    @TempDir
    lateinit var tempDir: Path

    // ── Test 1: AgentDebugSession boots real CoffeeGbEmulator ──

    @Test
    fun `AgentDebugSession boots real CoffeeGbEmulator with minimal ROM`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            // If we get here without exception, boot chain works
            assertEquals(0, session.frameCount)
        }
    }

    // ── Test 2: runFrames advances real emulator ──

    @Test
    fun `AgentDebugSession runFrames advances real emulator`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(10)
            assertEquals(10, session.frameCount)

            val fb = session.getFrameBuffer()
            assertEquals(160 * 144, fb.size, "Frame buffer should have 23040 elements")
        }
    }

    // ── Test 3: readAllVariables returns empty without sym file ──

    @Test
    fun `AgentDebugSession readAllVariables returns empty without sym file`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(1)

            val vars = session.readAllVariables()
            assertTrue(vars.isEmpty(), "Without sym file, readAllVariables should return empty map")

            val score = session.readVariable("score")
            assertNull(score, "Without sym file, readVariable should return null")
        }
    }

    // ── Test 4: readVariable with sym file resolves address ──

    @Test
    fun `AgentDebugSession readVariable with sym file resolves address`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        // Create a .noi sym file pointing at a WRAM address
        val symFile = tempDir.resolve("test.noi").toFile()
        symFile.writeText("DEF _score 00:C100\n")

        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(1)

            // Read — should resolve to some value (WRAM is zeroed on boot)
            val initial = session.readVariable("score")
            assertNotNull(initial, "score should resolve via sym file")

            // Write then read back
            val wrote = session.writeVariable("score", 42)
            assertTrue(wrote, "writeVariable should succeed for known symbol")
            assertEquals(42, session.readVariable("score"), "score should read back 42 after write")
        }
    }

    // ── Test 5: captureScreenshot produces PNG ──

    @Test
    fun `AgentDebugSession captureScreenshot produces PNG`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val screenshotDir = tempDir.resolve("screenshots").toFile()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = screenshotDir,
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(5)

            val png = session.captureScreenshot("test_capture")
            assertTrue(png.exists(), "Screenshot PNG should exist")
            assertTrue(png.length() > 0, "Screenshot PNG should not be empty")
            assertTrue(png.name.endsWith(".png"), "Screenshot should be a PNG file")
        }
    }

    // ── Test 6: StepAgent step with real emulator returns valid Observation ──

    @Test
    fun `StepAgent step with real emulator returns valid Observation`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        StepAgent(config).use { agent ->
            agent.start()

            val obs = agent.step()
            assertEquals(1, obs.frame, "First step should be frame 1")
            assertEquals(18, obs.bgText.size, "bgText should have 18 rows")
            assertEquals(18, obs.winText.size, "winText should have 18 rows")
        }
    }

    // ── Test 7: StepAgent stepN accumulates frames ──

    @Test
    fun `StepAgent stepN accumulates frames`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        StepAgent(config).use { agent ->
            agent.start()

            val obs = agent.stepN(30)
            assertEquals(30, obs.frame, "stepN(30) should produce frame 30")
            assertEquals(30, agent.frameCount, "frameCount should be 30")
        }
    }

    // ── Test 8: StepAgent waitUntil returns after condition met ──

    @Test
    fun `StepAgent waitUntil returns after condition met`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        StepAgent(config).use { agent ->
            agent.start()

            // waitUntil with a condition that triggers at frame 5
            val obs = agent.waitUntil(maxFrames = 60) { it.frame >= 5 }
            assertEquals(5, obs.frame, "waitUntil should return at frame 5")
        }
    }

    // ── Test 9: waitUntil returns final observation on timeout ──

    @Test
    fun `waitUntil returns final observation on timeout with real emulator`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        StepAgent(config).use { agent ->
            agent.start()

            // Condition that never triggers — should exhaust maxFrames and return final obs
            val obs = agent.waitUntil(maxFrames = 10) { false }
            assertEquals(10, obs.frame, "waitUntil timeout should return obs at frame 10")
            assertNotNull(obs, "Timeout observation should not be null")
        }
    }

    // ── Test 10: variable persists across frames ──

    @Test
    fun `variable persists across frames with real emulator`() {
        val rom = TestRomFactory.createMinimalRom(tempDir)
        val symFile = tempDir.resolve("test.noi").toFile()
        symFile.writeText("DEF _score 00:C100\n")

        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = tempDir.resolve("screenshots").toFile(),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(1)

            // Write 42 to WRAM
            session.writeVariable("score", 42)
            assertEquals(42, session.readVariable("score"), "score should be 42 after write")

            // Step 10 frames — WRAM should persist
            session.runFrames(10)
            assertEquals(42, session.readVariable("score"), "score should still be 42 after 10 frames")
        }
    }
}
