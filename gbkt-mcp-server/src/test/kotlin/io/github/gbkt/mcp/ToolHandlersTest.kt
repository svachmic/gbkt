/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.agent.VramTextVerifier
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [ToolHandlerLogic] — directly invokes handler suspend functions with real
 * [McpEmulatorSession] instances backed by stub emulators.
 */
class ToolHandlersTest {

    @TempDir lateinit var tempDir: File

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeRom(name: String = "test.gb"): File =
        File(tempDir, name).also { it.writeBytes(ByteArray(64)) }

    private fun writeSymFile(
        content: String = "DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n"
    ): File = File(tempDir, "test.sym").also { it.writeText(content) }

    private fun writeMetadataFile(): File {
        val json =
            """
            {
              "scenes": { "title": 0, "game": 1, "gameover": 2 },
              "actors": [
                {
                  "name": "ball",
                  "oamStart": 4,
                  "oamCount": 1,
                  "spriteWidth": 4,
                  "spriteHeight": 4,
                  "vars": { "x": "ball_x", "y": "ball_y" }
                }
              ],
              "variables": [
                { "name": "score", "type": "U8", "semantic": "score" }
              ],
              "controls": {
                "game": [
                  { "button": "UP", "type": "held" },
                  { "button": "DOWN", "type": "held" }
                ]
              },
              "transitions": [
                { "from": "title", "to": "game" },
                { "from": "game", "to": "gameover" }
              ]
            }
            """
                .trimIndent()
        return File(tempDir, "game_metadata.json").also { it.writeText(json) }
    }

    private fun mockMemory(vararg patches: Pair<Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in patches) {
            mem[addr] = value
        }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]

            override fun writeByte(address: Int, value: Int) {
                mem[address] = value
            }
        }
    }

    private fun stubEmulator(memory: MemoryAccess = mockMemory()): GbEmulator =
        object : GbEmulator {
            private var _paused = true
            private var _running = false

            override fun start() {
                _running = true
            }

            override fun stop() {
                _running = false
            }

            override fun pause() {
                _paused = true
            }

            override fun resume() {
                _paused = false
            }

            override fun stepFrame() = Unit

            override fun setSpeed(multiplier: Float) = Unit

            override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0x00FF00 }

            override fun getMemory(): MemoryAccess = memory

            override fun getDebugLog(): List<DebugLogEntry> = emptyList()

            override fun isRunning(): Boolean = _running

            override fun isPaused(): Boolean = _paused

            override val isHeadless: Boolean = true
        }

    private fun makeSession(memory: MemoryAccess = mockMemory()): McpEmulatorSession =
        McpEmulatorSession(stubEmulatorFactory = { stubEmulator(memory) })

    private suspend fun startSession(
        session: McpEmulatorSession,
        rom: File? = null,
        sym: File? = null,
        metadata: File? = null,
    ) {
        val r = rom ?: fakeRom()
        val s = sym ?: writeSymFile()
        val args = buildJsonObject {
            put("romFile", r.absolutePath)
            put("symFile", s.absolutePath)
            if (metadata != null) put("metadataFile", metadata.absolutePath)
        }
        val result = ToolHandlerLogic.handleStart(session, args)
        assertFalse(result.isError == true, "Start should succeed: ${result.textContent()}")
    }

    private fun resultText(
        result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
    ): String =
        (result.content.firstOrNull() as? io.modelcontextprotocol.kotlin.sdk.types.TextContent)
            ?.text ?: ""

    private fun resultJson(
        result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
    ): JsonObject = Json.parseToJsonElement(resultText(result)).jsonObject

    private fun io.modelcontextprotocol.kotlin.sdk.types.CallToolResult.textContent(): String =
        resultText(this)

    // ── handleStart tests ────────────────────────────────────────────────────

    @Test
    fun `handleStart starts session and returns metadata`() = runTest {
        val session = makeSession()
        val rom = fakeRom()
        val sym = writeSymFile()
        val meta = writeMetadataFile()
        val args = buildJsonObject {
            put("romFile", rom.absolutePath)
            put("symFile", sym.absolutePath)
            put("metadataFile", meta.absolutePath)
        }

        val result = ToolHandlerLogic.handleStart(session, args)

        assertFalse(result.isError == true)
        assertTrue(session.isActive())
        val json = resultJson(result)
        assertEquals(true, json["started"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())

        session.stop()
    }

    @Test
    fun `handleStart rejects missing romFile`() = runTest {
        val session = makeSession()
        val result = ToolHandlerLogic.handleStart(session, buildJsonObject {})

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("romFile"))
    }

    @Test
    fun `handleStart rejects nonexistent ROM`() = runTest {
        val session = makeSession()
        val args = buildJsonObject { put("romFile", "/nonexistent/path/game.gb") }

        val result = ToolHandlerLogic.handleStart(session, args)

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("not found"))
    }

    @Test
    fun `handleStart rejects missing arguments`() = runTest {
        val session = makeSession()
        val result = ToolHandlerLogic.handleStart(session, null)

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("Missing arguments"))
    }

    // ── handleStop tests ─────────────────────────────────────────────────────

    @Test
    fun `handleStop stops active session`() = runTest {
        val session = makeSession()
        startSession(session)
        assertTrue(session.isActive())

        val result = ToolHandlerLogic.handleStop(session)

        assertFalse(result.isError == true)
        assertFalse(session.isActive())
        assertTrue(resultText(result).contains("stopped"))
    }

    // ── handleStep tests ─────────────────────────────────────────────────────

    @Test
    fun `handleStep advances frames`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("frames", 5) }
        val result = ToolHandlerLogic.handleStep(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(5, json["frame"]?.jsonPrimitive?.content?.toIntOrNull())

        session.stop()
    }

    @Test
    fun `handleStep rejects negative frames`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("frames", -1) }
        val result = ToolHandlerLogic.handleStep(session, args)

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("positive"))

        session.stop()
    }

    @Test
    fun `handleStep parses valid buttons`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject {
            put("frames", 1)
            put(
                "buttons",
                buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("a"))
                    add(kotlinx.serialization.json.JsonPrimitive("right"))
                },
            )
        }
        val result = ToolHandlerLogic.handleStep(session, args)

        assertFalse(result.isError == true)

        session.stop()
    }

    @Test
    fun `handleStep rejects invalid button`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject {
            put(
                "buttons",
                buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("turbo")) },
            )
        }
        val result = ToolHandlerLogic.handleStep(session, args)

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("Invalid button"))

        session.stop()
    }

    // ── handleObserve tests ──────────────────────────────────────────────────

    @Test
    fun `handleObserve returns cached after step`() = runTest {
        val session = makeSession()
        startSession(session)

        // Step first
        val stepArgs = buildJsonObject { put("frames", 3) }
        ToolHandlerLogic.handleStep(session, stepArgs)

        // Observe should return same frame (cached)
        val result = ToolHandlerLogic.handleObserve(session)
        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(3, json["frame"]?.jsonPrimitive?.content?.toIntOrNull())

        session.stop()
    }

    @Test
    fun `handleObserve steps 1 frame when no cache`() = runTest {
        val session = makeSession()
        startSession(session)

        val result = ToolHandlerLogic.handleObserve(session)
        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["frame"]?.jsonPrimitive?.content?.toIntOrNull())

        session.stop()
    }

    // ── handleReadVariable / handleWriteVariable tests ───────────────────────

    @Test
    fun `handleReadVariable reads from sym file`() = runTest {
        val memory = mockMemory(0xC100 to 42)
        val session = makeSession(memory)
        startSession(session)

        val args = buildJsonObject { put("name", "score") }
        val result = ToolHandlerLogic.handleReadVariable(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals("score", json["name"]?.jsonPrimitive?.content)
        assertEquals(42, json["value"]?.jsonPrimitive?.content?.toIntOrNull())

        session.stop()
    }

    @Test
    fun `handleWriteVariable writes and reads back`() = runTest {
        val memory = mockMemory(0xC100 to 0)
        val session = makeSession(memory)
        startSession(session)

        val writeArgs = buildJsonObject {
            put("name", "score")
            put("value", 99)
        }
        val writeResult = ToolHandlerLogic.handleWriteVariable(session, writeArgs)
        assertFalse(writeResult.isError == true)

        val readArgs = buildJsonObject { put("name", "score") }
        val readResult = ToolHandlerLogic.handleReadVariable(session, readArgs)
        val json = resultJson(readResult)
        assertEquals(99, json["value"]?.jsonPrimitive?.content?.toIntOrNull())

        session.stop()
    }

    // ── handleReadMemory / handleWriteMemory tests (SEED-012 EXTENDED) ───────

    private fun hexByteToInt(hex: String): Int =
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex.substring(2).toInt(16)
        else hex.toInt()

    @Test
    fun `handleReadMemory returns hex bytes for valid hex address and count`() = runTest {
        val memory = mockMemory(0xFF40 to 0x91, 0xFF41 to 0x80, 0xFF42 to 0x00)
        val session = makeSession(memory)
        startSession(session)

        val args = buildJsonObject {
            put("address", "0xFF40")
            put("count", 3)
        }
        val result = ToolHandlerLogic.handleReadMemory(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals("0xFF40", json["address"]?.jsonPrimitive?.content)
        assertEquals(3, json["count"]?.jsonPrimitive?.int)
        val bytes = json["bytes"]?.jsonArray
        assertNotNull(bytes)
        assertEquals(3, bytes!!.size)
        assertEquals("0x91", bytes[0].jsonPrimitive.content)
        assertEquals("0x80", bytes[1].jsonPrimitive.content)
        assertEquals("0x00", bytes[2].jsonPrimitive.content)

        session.stop()
    }

    @Test
    fun `handleReadMemory observes LCDC bit 7 transition across DISPLAY_ON simulation`() = runTest {
        // Phase 1 — LCD off at boot (LCDC bit 7 clear).
        val memoryOff = mockMemory(0xFF40 to 0x00)
        val sessionOff = makeSession(memoryOff)
        startSession(sessionOff)

        val argsLcdc = buildJsonObject {
            put("address", "0xFF40")
            put("count", 1)
        }
        val resultOff = ToolHandlerLogic.handleReadMemory(sessionOff, argsLcdc)
        assertFalse(resultOff.isError == true)
        val byteOffHex = resultJson(resultOff)["bytes"]?.jsonArray?.get(0)?.jsonPrimitive?.content
        assertEquals("0x00", byteOffHex)
        val byteOff = hexByteToInt(byteOffHex!!)
        assertEquals(
            0,
            byteOff and 0x80,
            "LCDC bit 7 should be 0 at boot (LCD off), got 0x%02X".format(byteOff),
        )
        sessionOff.stop()

        // Phase 2 — LCD on after DISPLAY_ON simulation (LCDC = 0x91 = standard GBDK active).
        val memoryOn = mockMemory(0xFF40 to 0x91)
        val sessionOn = makeSession(memoryOn)
        startSession(sessionOn)

        val resultOn = ToolHandlerLogic.handleReadMemory(sessionOn, argsLcdc)
        assertFalse(resultOn.isError == true)
        val byteOnHex = resultJson(resultOn)["bytes"]?.jsonArray?.get(0)?.jsonPrimitive?.content
        val byteOn = hexByteToInt(byteOnHex!!)
        assertEquals(
            0x80,
            byteOn and 0x80,
            "LCDC bit 7 should be 1 after DISPLAY_ON, got 0x%02X".format(byteOn),
        )
        sessionOn.stop()
    }

    @Test
    fun `handleWriteMemory writes byte and read-back via handleReadMemory matches`() = runTest {
        val memory = mockMemory(0xC100 to 0x00)
        val session = makeSession(memory)
        startSession(session)

        // Write phase: 0xAB at 0xC100.
        val writeArgs = buildJsonObject {
            put("address", "0xC100")
            put("value", 0xAB)
        }
        val writeResult = ToolHandlerLogic.handleWriteMemory(session, writeArgs)
        assertFalse(writeResult.isError == true)
        val writeJson = resultJson(writeResult)
        assertEquals(true, writeJson["success"]?.jsonPrimitive?.boolean)
        assertEquals("0xC100", writeJson["address"]?.jsonPrimitive?.content)
        assertEquals("0xAB", writeJson["value"]?.jsonPrimitive?.content)

        // Read-back: prove the write mutated mockMemory's IntArray.
        val readArgs = buildJsonObject {
            put("address", "0xC100")
            put("count", 1)
        }
        val readResult = ToolHandlerLogic.handleReadMemory(session, readArgs)
        assertFalse(readResult.isError == true)
        val readBytes = resultJson(readResult)["bytes"]?.jsonArray
        assertEquals(
            "0xAB",
            readBytes?.get(0)?.jsonPrimitive?.content,
            "expected write of 0xAB at 0xC100 to round-trip via mockMemory IntArray; got: $readBytes",
        )

        // Out-of-range rejection: value=0x1FF (511) must now return an error (WR-01 fix).
        val maskArgs = buildJsonObject {
            put("address", "0xC101")
            put("value", 0x1FF)
        }
        val maskResult = ToolHandlerLogic.handleWriteMemory(session, maskArgs)
        assertTrue(maskResult.isError == true)
        assertTrue(
            resultText(maskResult).contains("out of range"),
            "Expected 'out of range' in error for value=511, got: ${resultText(maskResult)}",
        )

        session.stop()
    }

    // ── handleWaitForScene test ──────────────────────────────────────────────

    @Test
    fun `handleWaitForScene returns met when scene matches`() = runTest {
        val memory = mockMemory(0xC102 to 0) // current_scene = 0 -> "title"
        val session = makeSession(memory)
        val meta = writeMetadataFile()
        startSession(session, metadata = meta)

        val args = buildJsonObject {
            put("scene", "title")
            put("maxFrames", 10)
        }
        val result = ToolHandlerLogic.handleWaitForScene(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(true, json["met"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())

        session.stop()
    }

    // ── handleWaitUntilText test ─────────────────────────────────────────────

    @Test
    fun `handleWaitUntilText finds text`() = runTest {
        val memory = mockMemory()
        // Write "HELLO" at BG tilemap row 0 with GBDK encoding
        val base = VramTextVerifier.BG_TILEMAP_BASE
        for ((i, c) in "HELLO".withIndex()) {
            memory.writeByte(base + i, c.code - 0x20)
        }
        val session = makeSession(memory)
        startSession(session)

        val args = buildJsonObject {
            put("text", "HELLO")
            put("maxFrames", 10)
        }
        val result = ToolHandlerLogic.handleWaitUntilText(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(true, json["met"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())

        session.stop()
    }

    // ── handleDescribeGame test ──────────────────────────────────────────────

    @Test
    fun `handleDescribeGame returns null without metadata`() = runTest {
        val session = makeSession()
        startSession(session)

        val result = ToolHandlerLogic.handleDescribeGame(session)
        assertFalse(result.isError == true)
        assertTrue(resultText(result).contains("null"))

        session.stop()
    }

    @Test
    fun `handleDescribeGame returns metadata when loaded`() = runTest {
        val session = makeSession()
        val meta = writeMetadataFile()
        startSession(session, metadata = meta)

        val result = ToolHandlerLogic.handleDescribeGame(session)
        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertNotNull(json["scenes"])
        // Variables with semantic
        val vars = json["variables"]?.jsonArray
        assertNotNull(vars)
        assertEquals("score", vars!![0].jsonObject["semantic"]?.jsonPrimitive?.content)
        // Controls
        val controls = json["controls"]?.jsonObject
        assertNotNull(controls)
        val gameControls = controls!!["game"]?.jsonArray
        assertNotNull(gameControls)
        assertEquals(2, gameControls!!.size)
        // Transitions
        val transitions = json["transitions"]?.jsonArray
        assertNotNull(transitions)
        assertEquals(2, transitions!!.size)
        assertEquals("title", transitions[0].jsonObject["from"]?.jsonPrimitive?.content)

        session.stop()
    }

    // ── handleScreenshot test ────────────────────────────────────────────────

    @Test
    fun `handleScreenshot captures file`() = runTest {
        val session = makeSession()
        startSession(session)

        // Step first to get a frame
        ToolHandlerLogic.handleStep(session, null)

        val args = buildJsonObject { put("label", "test_capture") }
        val result = ToolHandlerLogic.handleScreenshot(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        val filePath = json["filePath"]?.jsonPrimitive?.content
        assertNotNull(filePath)
        assertTrue(File(filePath!!).exists())

        session.stop()
    }

    // ── handleAssert tests ────────────────────────────────────────────────────

    @Test
    fun `handleAssert variable_equals passing`() = runTest {
        val memory = mockMemory(0xC100 to 42)
        val session = makeSession(memory)
        startSession(session)

        // Step to populate observation with variables
        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "variable_equals")
                            put("name", "score")
                            put("expected", "42")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)

        session.stop()
    }

    @Test
    fun `handleAssert scene_is check`() = runTest {
        val memory = mockMemory(0xC102 to 0) // current_scene = 0 -> "title"
        val session = makeSession(memory)
        val meta = writeMetadataFile()
        startSession(session, metadata = meta)

        // Step to populate observation
        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "scene_is")
                            put("scene", "title")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)

        session.stop()
    }

    @Test
    fun `handleAssert mixed passing and failing`() = runTest {
        val memory = mockMemory(0xC100 to 42, 0xC102 to 0)
        val session = makeSession(memory)
        val meta = writeMetadataFile()
        startSession(session, metadata = meta)

        // Step to populate observation
        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    // This passes: score is 42
                    add(
                        buildJsonObject {
                            put("type", "variable_equals")
                            put("name", "score")
                            put("expected", "42")
                        }
                    )
                    // This fails: scene is "title" (index 0), not "game"
                    add(
                        buildJsonObject {
                            put("type", "scene_is")
                            put("scene", "game")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(1, json["failed"]?.jsonPrimitive?.int)
        assertEquals(2, json["results"]?.jsonArray?.size)

        session.stop()
    }

    @Test
    fun `handleAssert rejects missing checks`() = runTest {
        val session = makeSession()
        startSession(session)

        // No "checks" key in args
        val result = ToolHandlerLogic.handleAssert(session, buildJsonObject {})

        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("checks"))

        session.stop()
    }

    // ── handleSaveState / handleLoadState tests ───────────────────────────────

    @Test
    fun `handleSaveState creates file and returns metadata`() = runTest {
        val memory = mockMemory(0xC100 to 77)
        val session = makeSession(memory)
        startSession(session)

        // Step to populate frame count and observation
        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 3) })

        val args = buildJsonObject { put("label", "test_save_${System.nanoTime()}") }
        val result = ToolHandlerLogic.handleSaveState(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertNotNull(json["label"])
        assertNotNull(json["frame"])
        assertNotNull(json["file"])

        // Verify the file was actually created
        val filePath = json["file"]?.jsonPrimitive?.content
        assertNotNull(filePath)
        assertTrue(File(filePath!!).exists())

        // Clean up
        File(filePath).delete()
        session.stop()
    }

    @Test
    fun `handleLoadState round-trips with save`() = runTest {
        val memory = mockMemory(0xC100 to 55)
        val session = makeSession(memory)
        startSession(session)

        // Step first
        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val label = "roundtrip_${System.nanoTime()}"

        // Save
        val saveArgs = buildJsonObject { put("label", label) }
        val saveResult = ToolHandlerLogic.handleSaveState(session, saveArgs)
        assertFalse(saveResult.isError == true)

        // Load
        val loadArgs = buildJsonObject { put("label", label) }
        val loadResult = ToolHandlerLogic.handleLoadState(session, loadArgs)
        assertFalse(loadResult.isError == true)
        val json = resultJson(loadResult)
        assertEquals(true, json["restored"]?.jsonPrimitive?.boolean)

        // Clean up
        val saveJson = resultJson(saveResult)
        val filePath = saveJson["file"]?.jsonPrimitive?.content
        if (filePath != null) File(filePath).delete()

        session.stop()
    }

    @Test
    fun `handleLoadState returns error for missing state`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("label", "nonexistent_state_${System.nanoTime()}") }
        val result = ToolHandlerLogic.handleLoadState(session, args)

        assertFalse(result.isError == true) // The handler returns success with error field in JSON
        val json = resultJson(result)
        assertNotNull(json["error"])
        assertTrue(json["error"]!!.jsonPrimitive.content.contains("not found"))

        session.stop()
    }

    // ── handleListGames / handleGetPlaybook tests ─────────────────────────────

    @Test
    fun `handleListGames returns games array`() = runTest {
        val session = makeSession()

        val result = ToolHandlerLogic.handleListGames(session)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertNotNull(json["games"])
        // Verify it's an array (may be empty if no ROMs built)
        json["games"]!!.jsonArray

        // No stop needed — listGames does not require an active session
    }

    @Test
    fun `handleGetPlaybook returns content when PLAYBOOK exists`() = runTest {
        // Create directory structure: <root>/build/gbkt/output/test.gb
        val root = File(tempDir, "project")
        val outputDir = File(root, "build/gbkt/output")
        outputDir.mkdirs()
        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        val sym = File(root, "test.sym").also { it.writeText("DEF _score 00:C100\n") }

        // Place PLAYBOOK.md at project root
        val playbookContent = "# Test Playbook\n\nStep 1: Press START"
        File(root, "PLAYBOOK.md").writeText(playbookContent)

        val session = makeSession()
        startSession(session, rom = rom, sym = sym)

        val result = ToolHandlerLogic.handleGetPlaybook(session)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(playbookContent, json["content"]?.jsonPrimitive?.content)
        assertNotNull(json["path"])

        session.stop()
    }

    @Test
    fun `handleGetPlaybook returns null when no playbook`() = runTest {
        // Create a flat ROM path with no parent structure matching the convention
        val isolatedDir = File(tempDir, "isolated")
        isolatedDir.mkdirs()
        val rom = File(isolatedDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        val sym = File(isolatedDir, "test.sym").also { it.writeText("DEF _score 00:C100\n") }

        val session = makeSession()
        startSession(session, rom = rom, sym = sym)

        val result = ToolHandlerLogic.handleGetPlaybook(session)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        // Handler returns {"content": null, "message": "No PLAYBOOK.md found for this game"}
        assertNull(json["content"]?.jsonPrimitive?.contentOrNull)
        assertTrue(json.containsKey("message"))

        session.stop()
    }

    // ── Batch assert: variable_in_range ──────────────────────────────────

    @Test
    fun `handleAssert variable_in_range passing`() = runTest {
        val memory = mockMemory(0xC100 to 42)
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "variable_in_range")
                            put("name", "score")
                            put("min", "40")
                            put("max", "50")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(true, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("42", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    @Test
    fun `handleAssert variable_in_range failing`() = runTest {
        val memory = mockMemory(0xC100 to 42)
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "variable_in_range")
                            put("name", "score")
                            put("min", "50")
                            put("max", "60")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(0, json["passed"]?.jsonPrimitive?.int)
        assertEquals(1, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(false, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("42", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    // ── Batch assert: text_on_screen ─────────────────────────────────────

    @Test
    fun `handleAssert text_on_screen passing`() = runTest {
        // Write GBDK-encoded "HELLO" to BG tilemap at 0x9800 (tile = char.code - 0x20)
        val patches =
            "HELLO".mapIndexed { i, ch -> (0x9800 + i) to (ch.code - 0x20) }.toTypedArray()
        val memory = mockMemory(*patches)
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text_on_screen")
                            put("text", "HELLO")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(true, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("found", check?.get("actual")?.jsonPrimitive?.content)
        assertEquals(0, check?.get("x")?.jsonPrimitive?.int)
        assertEquals(0, check?.get("y")?.jsonPrimitive?.int)
        assertEquals("bg", check?.get("layer")?.jsonPrimitive?.content)

        session.stop()
    }

    @Test
    fun `handleAssert text_on_screen failing`() = runTest {
        // Empty VRAM -- no text written
        val memory = mockMemory()
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text_on_screen")
                            put("text", "HELLO")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(0, json["passed"]?.jsonPrimitive?.int)
        assertEquals(1, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(false, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("not found", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    @Test
    fun `handleAssert text_on_screen window layer`() = runTest {
        // Write "HI" to WIN tilemap at row 2, col 3 (0x9C00 + 2*32 + 3 = 0x9C43)
        // Window layer uses direct ASCII encoding (tile = char.code)
        val patches =
            "HI".mapIndexed { i, ch -> (0x9C00 + 2 * 32 + 3 + i) to ch.code }.toTypedArray()
        val memory = mockMemory(*patches)
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text_on_screen")
                            put("text", "HI")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(true, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("found", check?.get("actual")?.jsonPrimitive?.content)
        assertEquals(3, check?.get("x")?.jsonPrimitive?.int)
        assertEquals(2, check?.get("y")?.jsonPrimitive?.int)
        assertEquals("win", check?.get("layer")?.jsonPrimitive?.content)

        session.stop()
    }

    // ── Batch assert: actor_visible ──────────────────────────────────────

    @Test
    fun `handleAssert actor_visible passing`() = runTest {
        val memory = mockMemory()
        val session = makeSession(memory)
        val meta = writeMetadataFile() // metadata has actor "ball"
        startSession(session, metadata = meta)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "actor_visible")
                            put("name", "ball")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(true, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("visible", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    @Test
    fun `handleAssert actor_visible failing`() = runTest {
        val memory = mockMemory()
        val session = makeSession(memory)
        val meta = writeMetadataFile() // metadata has actor "ball" but NOT "paddle"
        startSession(session, metadata = meta)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "actor_visible")
                            put("name", "paddle")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(0, json["passed"]?.jsonPrimitive?.int)
        assertEquals(1, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(false, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("not visible", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    // ── Batch assert: sprite_count ───────────────────────────────────────

    @Test
    fun `handleAssert sprite_count passing`() = runTest {
        // Write 2 visible OAM sprites at 0xFE00 (4 bytes each: rawY, rawX, tile, flags)
        // Sprite 0 at OAM slot 0: rawY=32, rawX=24 -> visible (screenY=16, screenX=16)
        // Sprite 1 at OAM slot 1: rawY=48, rawX=40 -> visible (screenY=32, screenX=32)
        val memory =
            mockMemory(
                0xFE00 to 32,
                0xFE01 to 24,
                0xFE02 to 1,
                0xFE03 to 0,
                0xFE04 to 48,
                0xFE05 to 40,
                0xFE06 to 2,
                0xFE07 to 0,
            )
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "sprite_count")
                            put("expected", "2")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(1, json["passed"]?.jsonPrimitive?.int)
        assertEquals(0, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(true, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("2", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    @Test
    fun `handleAssert sprite_count failing`() = runTest {
        // Write 2 visible OAM sprites but expect 5
        val memory =
            mockMemory(
                0xFE00 to 32,
                0xFE01 to 24,
                0xFE02 to 1,
                0xFE03 to 0,
                0xFE04 to 48,
                0xFE05 to 40,
                0xFE06 to 2,
                0xFE07 to 0,
            )
        val session = makeSession(memory)
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject {
            put(
                "checks",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "sprite_count")
                            put("expected", "5")
                        }
                    )
                },
            )
        }
        val result = ToolHandlerLogic.handleAssert(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(0, json["passed"]?.jsonPrimitive?.int)
        assertEquals(1, json["failed"]?.jsonPrimitive?.int)
        val check = json["results"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(false, check?.get("passed")?.jsonPrimitive?.boolean)
        assertEquals("2", check?.get("actual")?.jsonPrimitive?.content)

        session.stop()
    }

    // ── Frame cap validation ─────────────────────────────────────────────

    @Test
    fun `handleStep rejects frames exceeding MAX_FRAMES`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("frames", 50_000) }
        val result = ToolHandlerLogic.handleStep(session, args)

        assertTrue(result.isError == true)
        val text = resultText(result)
        assertTrue(text.contains("50000"), "Error should include requested frame count, got: $text")
        assertTrue(text.contains("36000"), "Error should include max frame cap, got: $text")

        session.stop()
    }

    @Test
    fun `handleWaitForScene rejects maxFrames exceeding cap`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject {
            put("scene", "title")
            put("maxFrames", 100_000)
        }
        val result = ToolHandlerLogic.handleWaitForScene(session, args)

        assertTrue(result.isError == true)
        val text = resultText(result)
        assertTrue(text.contains("100000"), "Error should include requested maxFrames, got: $text")
        assertTrue(text.contains("36000"), "Error should include max frame cap, got: $text")

        session.stop()
    }

    // ── Label sanitization validation ────────────────────────────────────

    @Test
    fun `handleScreenshot rejects label with path traversal`() = runTest {
        val session = makeSession()
        startSession(session)

        ToolHandlerLogic.handleStep(session, null)

        val args = buildJsonObject { put("label", "../../../etc/passwd") }
        val result = ToolHandlerLogic.handleScreenshot(session, args)

        assertTrue(result.isError == true)
        val text = resultText(result)
        assertTrue(text.contains("Invalid label"), "Error should mention invalid label, got: $text")

        session.stop()
    }

    @Test
    fun `handleSaveState rejects label with special characters`() = runTest {
        val session = makeSession()
        startSession(session)

        ToolHandlerLogic.handleStep(session, buildJsonObject { put("frames", 1) })

        val args = buildJsonObject { put("label", "save;rm -rf /") }
        val result = ToolHandlerLogic.handleSaveState(session, args)

        assertTrue(result.isError == true)
        val text = resultText(result)
        assertTrue(text.contains("Invalid label"), "Error should mention invalid label, got: $text")

        session.stop()
    }

    @Test
    fun `handleScreenshot accepts valid label`() = runTest {
        val session = makeSession()
        startSession(session)

        ToolHandlerLogic.handleStep(session, null)

        val args = buildJsonObject { put("label", "my-screenshot_01") }
        val result = ToolHandlerLogic.handleScreenshot(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        val filePath = json["filePath"]?.jsonPrimitive?.content
        assertNotNull(filePath, "Screenshot should return a filePath")
        assertTrue(File(filePath!!).exists(), "Screenshot file should exist at: $filePath")

        session.stop()
    }

    // ── McpEmulatorSession.press() tests ─────────────────────────────────────

    @Test
    fun `press Button A with frames=1 advances 2 total frames`() = runTest {
        val session = makeSession()
        startSession(session)

        // press(Button.A, frames=1) should advance 2 total frames: 1 hold + 1 release
        val obs = session.press(io.github.gbkt.emulator.agent.Button.A, 1)

        assertEquals(2, obs.frame, "press(A, 1) should advance 2 total frames")

        session.stop()
    }

    @Test
    fun `press Button START with frames=5 advances 6 total frames`() = runTest {
        val session = makeSession()
        startSession(session)

        // press(Button.START, frames=5) should advance 6 total frames: 5 hold + 1 release
        val obs = session.press(io.github.gbkt.emulator.agent.Button.START, 5)

        assertEquals(6, obs.frame, "press(START, 5) should advance 6 total frames")

        session.stop()
    }

    @Test
    fun `press updates lastObservation for subsequent observe`() = runTest {
        val session = makeSession()
        startSession(session)

        session.press(io.github.gbkt.emulator.agent.Button.A, 1)

        // observe() should return the cached result from press (frame 2)
        val obs = session.observe()
        assertEquals(
            2,
            obs.frame,
            "observe() should return press result (frame 2), not step 1 more",
        )

        session.stop()
    }

    @Test
    fun `press on inactive session throws IllegalStateException`() = runTest {
        val session = makeSession()
        // No startSession call — session is inactive

        try {
            session.press(io.github.gbkt.emulator.agent.Button.A, 1)
            assertTrue(false, "press() on inactive session should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message?.contains("No active session") == true,
                "Error message should mention 'No active session', got: ${e.message}",
            )
        }
    }

    // ── handlePress tests ────────────────────────────────────────────────────

    @Test
    fun `handlePress with valid button returns Observation JSON`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject {
            put("button", "a")
            put("frames", 1)
        }
        val result = ToolHandlerLogic.handlePress(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(
            2,
            json["frame"]?.jsonPrimitive?.content?.toIntOrNull(),
            "press(a, 1) should result in frame=2",
        )

        session.stop()
    }

    @Test
    fun `handlePress with missing button arg returns error`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("frames", 1) }
        val result = ToolHandlerLogic.handlePress(session, args)

        assertTrue(result.isError == true)
        assertEquals("button is required", resultText(result))

        session.stop()
    }

    @Test
    fun `handlePress with invalid button name returns error with valid options`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject { put("button", "turbo") }
        val result = ToolHandlerLogic.handlePress(session, args)

        assertTrue(result.isError == true)
        val text = resultText(result)
        assertTrue(text.contains("Invalid button"), "Should say 'Invalid button', got: $text")
        assertTrue(text.contains("turbo"), "Should include the invalid value, got: $text")

        session.stop()
    }

    @Test
    fun `handlePress with frames=0 returns error frames must be positive`() = runTest {
        val session = makeSession()
        startSession(session)

        val args = buildJsonObject {
            put("button", "a")
            put("frames", 0)
        }
        val result = ToolHandlerLogic.handlePress(session, args)

        assertTrue(result.isError == true)
        assertEquals("frames must be positive", resultText(result))

        session.stop()
    }

    @Test
    fun `handlePress with default frames omitted succeeds`() = runTest {
        val session = makeSession()
        startSession(session)

        // No frames arg — should default to 1 (advance 2 total frames)
        val args = buildJsonObject { put("button", "start") }
        val result = ToolHandlerLogic.handlePress(session, args)

        assertFalse(result.isError == true)
        val json = resultJson(result)
        assertEquals(
            2,
            json["frame"]?.jsonPrimitive?.content?.toIntOrNull(),
            "Default frames=1 should result in frame=2",
        )

        session.stop()
    }

    // ── CR-01 regression guard: address+count overflow boundary ─────────────

    @Test
    fun `handleReadMemory rejects address plus count exceeding 0xFFFF`() = runTest {
        val session = makeSession()
        startSession(session)
        val args = buildJsonObject {
            put("address", "0xFF01")
            put("count", 256) // end = 0xFF01 + 255 = 0x10000, which exceeds 0xFFFF
        }
        val result = ToolHandlerLogic.handleReadMemory(session, args)
        assertTrue(result.isError == true)
        assertTrue(
            resultText(result).contains("exceeds 0xFFFF"),
            "Error must mention 'exceeds 0xFFFF' for overflow boundary, got: ${resultText(result)}",
        )
        session.stop()
    }
}
