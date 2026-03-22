/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Unit tests for [UatRunner] using stub emulator.
 */
class UatRunnerTest {

    @TempDir
    lateinit var tempDir: File

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeRom(name: String = "test.gb"): File =
        File(tempDir, name).also { it.writeBytes(ByteArray(64)) }

    private fun writeSymFile(): File =
        File(tempDir, "test.sym").also {
            it.writeText("DEF _score 00:C100\nDEF _lives 00:C101\n")
        }

    private fun mockMemory(vararg patches: Pair<Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in patches) {
            mem[addr] = value
        }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]
            override fun writeByte(address: Int, value: Int) { mem[address] = value }
        }
    }

    private fun stubEmulator(
        memory: MemoryAccess = mockMemory(),
        onStepFrame: () -> Unit = {},
    ): GbEmulator =
        object : GbEmulator {
            private var _paused = true
            private var _running = false

            override fun start() { _running = true }
            override fun stop() { _running = false }
            override fun pause() { _paused = true }
            override fun resume() { _paused = false }
            override fun stepFrame() { onStepFrame() }
            override fun setSpeed(multiplier: Float) = Unit
            override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0x00FF00 }
            override fun getMemory(): MemoryAccess = memory
            override fun getDebugLog(): List<DebugLogEntry> = emptyList()
            override fun isRunning(): Boolean = _running
            override fun isPaused(): Boolean = _paused
            override val isHeadless: Boolean = true
        }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `wait increments frameCount tracked by session`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        runner.wait(10)
        runner.wait(5)

        // Verify via checkpoint — frameNumber should be 15
        val cp = runner.checkpoint("after_wait")
        assertEquals(15, cp.frameNumber)

        runner.close()
    }

    @Test
    fun `press increments frameCount`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        runner.press(Button.START, 5)
        runner.press(Button.A, 3)

        val cp = runner.checkpoint("after_press")
        assertEquals(8, cp.frameNumber)

        runner.close()
    }

    @Test
    fun `checkpoint captures screenshot and flushes assertions`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 42, 0xC101 to 3)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertVariable("score", 42)
        runner.assertVariable("lives", 5) // wrong — should fail

        val cp = runner.checkpoint("test_cp")

        assertTrue(cp.screenshotFile.exists(), "Screenshot PNG should exist")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed, "score == 42 should pass")
        assertFalse(cp.assertions[1].passed, "lives == 5 should fail (actual=3)")
        assertEquals("3", cp.assertions[1].actual)

        // Variables should be captured
        assertEquals(42, cp.variables["score"])
        assertEquals(3, cp.variables["lives"])

        runner.close()
    }

    @Test
    fun `assertVariableInRange records correctly`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 50)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertVariableInRange("score", 0..100) // should pass
        runner.assertVariableInRange("score", 60..100) // should fail (actual=50)

        val cp = runner.checkpoint("range_test")
        assertTrue(cp.assertions[0].passed)
        assertFalse(cp.assertions[1].passed)

        runner.close()
    }

    @Test
    fun `assertCustom records pass and fail`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        runner.assertCustom("visual check passed", true)
        runner.assertCustom("visual check failed", false)

        val cp = runner.checkpoint("custom_test")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)
        assertFalse(cp.assertions[1].passed)

        runner.close()
    }

    @Test
    fun `generateReport aggregates all checkpoints`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 10, 0xC101 to 3)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("pong", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertVariable("score", 10)
        runner.checkpoint("cp1")

        runner.assertVariable("lives", 3)
        runner.assertVariable("score", 99) // fail
        runner.checkpoint("cp2")

        val report = runner.generateReport()

        assertEquals("pong", report.gameName)
        assertEquals(2, report.checkpoints.size)
        assertEquals(3, report.totalAssertions)
        assertEquals(2, report.passedAssertions)
        assertEquals(1, report.failedAssertions)

        // Verify report JSON was written
        val reportFile = File(tempDir, "shots/uat_report.json")
        assertTrue(reportFile.exists(), "uat_report.json should be written")

        runner.close()
    }

    @Test
    fun `writeVariable modifies emulator memory`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 0)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        assertEquals(0, runner.readVariable("score"))
        assertTrue(runner.writeVariable("score", 42))
        assertEquals(42, runner.readVariable("score"))

        // Unknown variable returns false
        assertFalse(runner.writeVariable("nonexistent", 1))

        runner.close()
    }

    // ── Golden screenshot tests ─────────────────────────────────────────────

    private fun createGoldenPng(dir: File, name: String, color: Int = 0x00FF00): File {
        dir.mkdirs()
        val file = File(dir, "$name.png")
        val img = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 144) {
            for (x in 0 until 160) {
                img.setRGB(x, y, color)
            }
        }
        ImageIO.write(img, "png", file)
        return file
    }

    @Test
    fun `golden file exists and matches`() {
        val rom = fakeRom()
        val goldenDir = File(tempDir, "golden")
        createGoldenPng(goldenDir, "cp1", 0x00FF00) // same color as stub framebuffer
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, goldenDir = goldenDir, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val cp = runner.checkpoint("cp1")
        assertNotNull(cp.diffResult)
        assertTrue(cp.diffResult!!.match)

        runner.close()
    }

    @Test
    fun `golden file exists and mismatches`() {
        val rom = fakeRom()
        val goldenDir = File(tempDir, "golden")
        createGoldenPng(goldenDir, "cp1", 0xFF0000) // different color
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, goldenDir = goldenDir, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val cp = runner.checkpoint("cp1")
        val diff = cp.diffResult
        assertNotNull(diff)
        assertFalse(diff!!.match)
        assertNotNull(diff.diffImage)

        runner.close()
    }

    @Test
    fun `golden file missing results in null diffResult`() {
        val rom = fakeRom()
        val goldenDir = File(tempDir, "golden")
        goldenDir.mkdirs() // empty dir — no golden file
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, goldenDir = goldenDir, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val cp = runner.checkpoint("cp1")
        assertNull(cp.diffResult)

        runner.close()
    }

    @Test
    fun `no goldenDir means no comparison attempted`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val cp = runner.checkpoint("cp1")
        assertNull(cp.diffResult)

        runner.close()
    }

    @Test
    fun `per-checkpoint tolerance override`() {
        val rom = fakeRom()
        val goldenDir = File(tempDir, "golden")
        // Create a golden that's slightly different (all red vs all green)
        createGoldenPng(goldenDir, "cp1", 0xFF0000)
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        // Default tolerance = 0 (strict) but override cp1 to allow 100% diff
        val runner = UatRunner("test", config, goldenDir = goldenDir, goldenTolerance = 0.0, stubEmulatorFactory = { stubEmulator() })
        runner.setCheckpointTolerance("cp1", 1.0) // allow 100% difference
        runner.start()

        val cp = runner.checkpoint("cp1")
        assertNotNull(cp.diffResult)
        assertTrue(cp.diffResult!!.match, "Should pass with 100% tolerance")

        runner.close()
    }

    @Test
    fun `generateReport includes golden summary`() {
        val rom = fakeRom()
        val goldenDir = File(tempDir, "golden")
        createGoldenPng(goldenDir, "cp1", 0x00FF00) // matches
        createGoldenPng(goldenDir, "cp2", 0xFF0000) // mismatches
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, goldenDir = goldenDir, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        runner.checkpoint("cp1")
        runner.checkpoint("cp2")
        val report = runner.generateReport()

        assertEquals(2, report.goldenComparisons)
        assertEquals(1, report.goldenPassed)
        assertEquals(1, report.goldenFailed)

        runner.close()
    }

    // ── VRAM text assertion tests ────────────────────────────────────────────

    /** Creates a MemoryAccess with GBDK-encoded BG text at the given tile positions. */
    private fun memoryWithVramText(vararg entries: Triple<String, Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((text, x, y) in entries) {
            for ((i, c) in text.withIndex()) {
                mem[VramTextVerifier.BG_TILEMAP_BASE + y * VramTextVerifier.ROW_STRIDE + x + i] = c.code - 0x20
            }
        }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]
            override fun writeByte(address: Int, value: Int) { mem[address] = value }
        }
    }

    @Test
    fun `assertTextAt passes when text matches`() {
        val rom = fakeRom()
        val memory = memoryWithVramText(Triple("PONG", 8, 7))
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertTextAt("PONG", 8, 7)
        val cp = runner.checkpoint("text_test")

        assertEquals(1, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)

        runner.close()
    }

    @Test
    fun `assertTextAt fails when text does not match`() {
        val rom = fakeRom()
        val memory = memoryWithVramText(Triple("PONG", 8, 7))
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertTextAt("PING", 8, 7) // wrong text
        val cp = runner.checkpoint("text_test")

        assertEquals(1, cp.assertions.size)
        assertFalse(cp.assertions[0].passed)
        assertEquals("PONG", cp.assertions[0].actual)

        runner.close()
    }

    @Test
    fun `assertTextOnScreen finds text anywhere on screen`() {
        val rom = fakeRom()
        val memory = memoryWithVramText(
            Triple("PONG", 8, 7),
            Triple("PRESS START", 5, 10),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertTextOnScreen("PONG")
        runner.assertTextOnScreen("PRESS START")
        runner.assertTextOnScreen("MISSING")
        val cp = runner.checkpoint("text_search")

        assertEquals(3, cp.assertions.size)
        assertTrue(cp.assertions[0].passed, "PONG should be found")
        assertTrue(cp.assertions[1].passed, "PRESS START should be found")
        assertFalse(cp.assertions[2].passed, "MISSING should not be found")

        runner.close()
    }

    @Test
    fun `pending assertions are flushed per checkpoint`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        runner.assertCustom("first", true)
        val cp1 = runner.checkpoint("cp1")
        assertEquals(1, cp1.assertions.size)

        // Second checkpoint should have no assertions (they were flushed)
        val cp2 = runner.checkpoint("cp2")
        assertEquals(0, cp2.assertions.size)

        runner.close()
    }

    // ── Condition-based waiting tests ────────────────────────────────────────

    @Test
    fun `waitUntil returns immediately when condition is already true`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val result = runner.waitUntil(100) { true }

        assertTrue(result.met)
        assertEquals(0, result.framesElapsed)

        runner.close()
    }

    @Test
    fun `waitUntil steps until condition met`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 0)
        var steps = 0
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = {
            stubEmulator(memory) {
                steps++
                if (steps == 10) memory.writeByte(0xC100, 42)
            }
        })
        runner.start()

        val result = runner.waitUntilVariable("score", 42, 100)

        assertTrue(result.met)
        assertEquals(10, result.framesElapsed)

        runner.close()
    }

    @Test
    fun `waitUntil returns met=false when maxFrames exhausted`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator() })
        runner.start()

        val result = runner.waitUntil(50) { false }

        assertFalse(result.met)
        assertEquals(50, result.framesElapsed)

        runner.close()
    }

    @Test
    fun `waitUntilVariable waits for variable to reach expected value`() {
        val rom = fakeRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 10)
        var steps = 0
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = {
            stubEmulator(memory) {
                steps++
                if (steps == 5) memory.writeByte(0xC100, 99)
            }
        })
        runner.start()

        val result = runner.waitUntilVariable("score", 99, 100)

        assertTrue(result.met)
        assertEquals(5, result.framesElapsed)

        runner.close()
    }

    @Test
    fun `waitUntilTextOnScreen finds text after it appears`() {
        val rom = fakeRom()
        val memory = mockMemory()
        var steps = 0
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = {
            stubEmulator(memory) {
                steps++
                if (steps == 8) {
                    // Write "PONG" at tile position (8, 7) in BG tilemap with GBDK encoding
                    val base = VramTextVerifier.BG_TILEMAP_BASE
                    for ((i, c) in "PONG".withIndex()) {
                        memory.writeByte(base + 7 * VramTextVerifier.ROW_STRIDE + 8 + i, c.code - 0x20)
                    }
                }
            }
        })
        runner.start()

        val result = runner.waitUntilTextOnScreen("PONG", 100)

        assertTrue(result.met)
        assertEquals(8, result.framesElapsed)

        runner.close()
    }

    // ── Scene detection tests ────────────────────────────────────────────────

    @Test
    fun `currentScene returns scene name via metadata`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 2)
        val metadata = GameMetadata.of(
            scenes = SceneMap.of("gameover" to 0, "game" to 1, "title" to 2),
            actors = emptyList(),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, metadata = metadata, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        assertEquals("title", runner.currentScene())

        runner.close()
    }

    @Test
    fun `currentScene returns scene_N fallback without sceneMap`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 2)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        assertEquals("scene_2", runner.currentScene())

        runner.close()
    }

    @Test
    fun `waitForScene waits until current_scene matches`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 0) // starts at scene 0 (gameover)
        var steps = 0
        val metadata = GameMetadata.of(
            scenes = SceneMap.of("gameover" to 0, "game" to 1, "title" to 2),
            actors = emptyList(),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, metadata = metadata, stubEmulatorFactory = {
            stubEmulator(memory) {
                steps++
                if (steps == 15) memory.writeByte(0xC100, 1) // transition to "game"
            }
        })
        runner.start()

        val result = runner.waitForScene("game", 100)

        assertTrue(result.met)
        assertEquals(15, result.framesElapsed)

        runner.close()
    }

    @Test
    fun `assertScene records soft assertion`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 2)
        val metadata = GameMetadata.of(
            scenes = SceneMap.of("gameover" to 0, "game" to 1, "title" to 2),
            actors = emptyList(),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, metadata = metadata, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertScene("title")
        runner.assertScene("game") // should fail — actual is "title"

        val cp = runner.checkpoint("scene_test")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)
        assertFalse(cp.assertions[1].passed)
        assertEquals("title", cp.assertions[1].actual)

        runner.close()
    }

    @Test
    fun `waitForScene works via metadata parameter end-to-end`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 0)
        var steps = 0
        val metadata = GameMetadata.of(
            scenes = SceneMap.of("gameover" to 0, "game" to 1, "title" to 2),
            actors = listOf(
                ActorMetadata("ball", oamStart = 0, oamCount = 1, spriteWidth = 4, spriteHeight = 4, xVar = "ball_x", yVar = "ball_y"),
            ),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, metadata = metadata, stubEmulatorFactory = {
            stubEmulator(memory) {
                steps++
                if (steps == 5) memory.writeByte(0xC100, 1) // transition to "game"
            }
        })
        runner.start()

        val result = runner.waitForScene("game", 50)

        assertTrue(result.met)
        assertEquals(5, result.framesElapsed)
        assertEquals("game", runner.currentScene())

        runner.close()
    }

    @Test
    fun `assertScene works via metadata parameter end-to-end`() {
        val rom = fakeRom()
        val symFile = File(tempDir, "test.sym").also {
            it.writeText("DEF _current_scene 00:C100\n")
        }
        val memory = mockMemory(0xC100 to 1) // scene 1 → "game"
        val metadata = GameMetadata.of(
            scenes = SceneMap.of("gameover" to 0, "game" to 1, "title" to 2),
            actors = emptyList(),
            variables = listOf(VariableDef("current_scene", "U8")),
        )
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, metadata = metadata, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertScene("game") // should pass
        runner.assertScene("title") // should fail — actual is "game"

        val cp = runner.checkpoint("metadata_scene")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)
        assertFalse(cp.assertions[1].passed)
        assertEquals("game", cp.assertions[1].actual)

        runner.close()
    }

    // ── Sprite assertion tests ───────────────────────────────────────────────

    @Test
    fun `assertSpriteAt passes when sprite is at position`() {
        val rom = fakeRom()
        // Sprite at OAM slot 0: rawY=32, rawX=24 → screenY=16, screenX=16
        val oamBase = 0xFE00
        val memory = mockMemory(
            oamBase to 32,       // rawY
            oamBase + 1 to 24,   // rawX
            oamBase + 2 to 5,    // tile
            oamBase + 3 to 0,    // attr
        )
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertSpriteAt(16, 16, tileIndex = 5)
        runner.assertSpriteAt(17, 17, tolerance = 2) // within tolerance

        val cp = runner.checkpoint("sprite_test")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)
        assertTrue(cp.assertions[1].passed)

        runner.close()
    }

    @Test
    fun `assertSpriteAt fails when no sprite at position`() {
        val rom = fakeRom()
        val memory = mockMemory() // all OAM zeroed → no visible sprites
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertSpriteAt(80, 72)

        val cp = runner.checkpoint("sprite_miss")
        assertEquals(1, cp.assertions.size)
        assertFalse(cp.assertions[0].passed)

        runner.close()
    }

    @Test
    fun `assertSpriteCount checks visible sprite count`() {
        val rom = fakeRom()
        val oamBase = 0xFE00
        // Two visible sprites
        val memory = mockMemory(
            oamBase to 32, oamBase + 1 to 24, oamBase + 2 to 0, oamBase + 3 to 0,
            oamBase + 4 to 48, oamBase + 5 to 80, oamBase + 6 to 1, oamBase + 7 to 0,
        )
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val runner = UatRunner("test", config, stubEmulatorFactory = { stubEmulator(memory) })
        runner.start()

        runner.assertSpriteCount(2)
        runner.assertSpriteCount(5) // should fail

        val cp = runner.checkpoint("count_test")
        assertEquals(2, cp.assertions.size)
        assertTrue(cp.assertions[0].passed)
        assertFalse(cp.assertions[1].passed)

        runner.close()
    }
}
