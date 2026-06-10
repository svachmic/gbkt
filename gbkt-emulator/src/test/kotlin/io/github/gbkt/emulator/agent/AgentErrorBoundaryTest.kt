/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Tests that agent-layer error boundaries wrap internal exceptions properly.
 *
 * Proves:
 * - Coffee-GB errors don't leak raw to callers
 * - Error messages contain ROM name, not absolute path
 * - Original cause is preserved for debugging
 * - I/O errors from screenshot capture are wrapped
 */
class AgentErrorBoundaryTest {

    @TempDir lateinit var tempDir: File

    // ── Test 1: start with zero-byte ROM throws EmulatorStartException ──

    @Test
    fun `start with zero-byte ROM throws EmulatorStartException`() {
        val rom = File(tempDir, "rom.gb").also { it.writeBytes(ByteArray(0)) }
        val config = AgentSessionConfig(romFile = rom, screenshotDir = File(tempDir, "screenshots"))

        val session = AgentDebugSession(config)
        assertThrows<EmulatorStartException> { session.start() }
    }

    // ── Test 2: EmulatorStartException message contains ROM name not path ──

    @Test
    fun `EmulatorStartException message contains ROM name not path`() {
        val rom = File(tempDir, "rom.gb").also { it.writeBytes(ByteArray(0)) }
        val config = AgentSessionConfig(romFile = rom, screenshotDir = File(tempDir, "screenshots"))

        val session = AgentDebugSession(config)
        val ex = assertThrows<EmulatorStartException> { session.start() }
        assertContains(ex.message!!, "rom.gb", message = "Message should contain ROM file name")
        assertTrue(
            !ex.message!!.contains("/Users/") && !ex.message!!.contains("/home/"),
            "Message should not contain absolute path prefixes",
        )
    }

    // ── Test 3: EmulatorStartException preserves cause ──

    @Test
    fun `EmulatorStartException preserves cause`() {
        val rom = File(tempDir, "rom.gb").also { it.writeBytes(ByteArray(0)) }
        val config = AgentSessionConfig(romFile = rom, screenshotDir = File(tempDir, "screenshots"))

        val session = AgentDebugSession(config)
        val ex = assertThrows<EmulatorStartException> { session.start() }
        assertNotNull(ex.cause, "EmulatorStartException should preserve the original cause")
    }

    // ── Test 4: ScreenshotCapture with unwritable dir throws ScreenshotCaptureException ──

    @Test
    fun `ScreenshotCapture with unwritable dir throws ScreenshotCaptureException`() {
        // Create a regular file where the screenshot dir should be — mkdirs() inside capture()
        // will succeed (creating parent), but writing to a file-as-dir will fail
        val blocker = File(tempDir, "blocked")
        blocker.writeText("not a directory")
        val fakeDir = File(blocker, "subdir")

        val frameBuffer = IntArray(160 * 144)

        assertThrows<ScreenshotCaptureException> {
            ScreenshotCapture.capture(
                frameBuffer = frameBuffer,
                label = "test",
                frameNumber = 1,
                outputDir = fakeDir,
            )
        }
    }
}
