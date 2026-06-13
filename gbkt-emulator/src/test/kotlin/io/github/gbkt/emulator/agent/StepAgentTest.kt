/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StepAgentTest {

    @TempDir lateinit var tempDir: File

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeRom(name: String = "test.gb"): File =
        File(tempDir, name).also { it.writeBytes(ByteArray(64)) }

    private fun writeSymFile(
        content: String = "DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n"
    ): File = File(tempDir, "test.sym").also { it.writeText(content) }

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

    /** Writes a 4-byte OAM entry into the memory patch list. */
    private fun oamPatches(
        slot: Int,
        rawY: Int,
        rawX: Int,
        tile: Int,
        attr: Int,
    ): List<Pair<Int, Int>> {
        val base = OamSpriteReader.OAM_START + slot * OamSpriteReader.BYTES_PER_SPRITE
        return listOf(base to rawY, base + 1 to rawX, base + 2 to tile, base + 3 to attr)
    }

    private fun stubEmulator(
        memory: MemoryAccess = mockMemory(),
        debugLog: List<DebugLogEntry> = emptyList(),
        onStepFrame: () -> Unit = {},
    ): GbEmulator =
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

            override fun stepFrame() {
                onStepFrame()
            }

            override fun setSpeed(multiplier: Float) = Unit

            override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0x00FF00 }

            override fun getMemory(): MemoryAccess = memory

            override fun getDebugLog(): List<DebugLogEntry> = debugLog

            override fun isRunning(): Boolean = _running

            override fun isPaused(): Boolean = _paused

            override val isHeadless: Boolean = true
        }

    private fun makeAgent(
        memory: MemoryAccess = mockMemory(),
        metadata: GameMetadata? = null,
        debugLog: List<DebugLogEntry> = emptyList(),
        symContent: String = "DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n",
        onStepFrame: () -> Unit = {},
    ): StepAgent {
        val rom = fakeRom()
        val sym = writeSymFile(symContent)
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                screenshotDir = File(tempDir, "screenshots"),
            )
        return StepAgent(
            config = config,
            metadata = metadata,
            stubEmulatorFactory = { stubEmulator(memory, debugLog, onStepFrame) },
        )
    }

    private fun pongMetadata(): GameMetadata =
        GameMetadata.of(
            scenes = SceneMap(mapOf("gameover" to 0, "game" to 1, "title" to 2)),
            actors =
                listOf(
                    ActorMetadata(
                        "paddle1",
                        oamStart = 0,
                        oamCount = 2,
                        spriteWidth = 4,
                        spriteHeight = 16,
                        xVar = "paddle1_x",
                        yVar = "paddle1_y",
                    ),
                    ActorMetadata(
                        "ball",
                        oamStart = 4,
                        oamCount = 1,
                        spriteWidth = 4,
                        spriteHeight = 4,
                        xVar = "ball_x",
                        yVar = "ball_y",
                    ),
                ),
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `step with no input advances one frame`() {
        makeAgent().use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals(1, obs.frame)
            assertEquals(1, agent.frameCount)
        }
    }

    @Test
    fun `step returns variables from sym file`() {
        val memory = mockMemory(0xC100 to 42, 0xC101 to 3)
        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals(42, obs.variables["score"])
            assertEquals(3, obs.variables["lives"])
        }
    }

    @Test
    fun `step returns visible sprites from OAM`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        patches += oamPatches(0, rawY = 32, rawX = 24, tile = 5, attr = 0)
        val memory = mockMemory(*patches.toTypedArray())

        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.step()
            assertTrue(obs.sprites.isNotEmpty())
            assertEquals(5, obs.sprites[0].tileIndex)
        }
    }

    /**
     * Writes a string into the BG tilemap at tile position (x, y) with GBDK encoding (char - 0x20).
     */
    private fun writeVramText(patches: MutableList<Pair<Int, Int>>, text: String, x: Int, y: Int) {
        val base = VramTextVerifier.BG_TILEMAP_BASE
        for ((i, c) in text.withIndex()) {
            patches.add((base + y * VramTextVerifier.ROW_STRIDE + x + i) to (c.code - 0x20))
        }
    }

    @Test
    fun `step returns VRAM text rows`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeVramText(patches, "HELLO", 0, 0)
        val memory = mockMemory(*patches.toTypedArray())

        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals(18, obs.bgText.size)
            assertTrue(obs.bgText[0].startsWith("HELLO"))
            assertEquals(18, obs.winText.size)
        }
    }

    @Test
    fun `step returns only new log entries`() {
        val log1 = DebugLogEntry(timestampMs = 100, level = LogLevel.GAME, message = "first")
        val log2 = DebugLogEntry(timestampMs = 200, level = LogLevel.GAME, message = "second")
        val allLogs = mutableListOf(log1)
        var stepCount = 0

        val rom = fakeRom()
        val sym = writeSymFile()
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                screenshotDir = File(tempDir, "screenshots"),
            )
        val agent =
            StepAgent(
                config = config,
                stubEmulatorFactory = {
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

                        override fun stepFrame() {
                            stepCount++
                            // Add log2 on the second stepFrame call (second step())
                            if (stepCount == 2) allLogs.add(log2)
                        }

                        override fun setSpeed(multiplier: Float) = Unit

                        override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0 }

                        override fun getMemory(): MemoryAccess = mockMemory()

                        override fun getDebugLog(): List<DebugLogEntry> = allLogs.toList()

                        override fun isRunning(): Boolean = _running

                        override fun isPaused(): Boolean = _paused

                        override val isHeadless: Boolean = true
                    }
                },
            )
        agent.use {
            it.start()
            val obs1 = it.step()
            assertEquals(1, obs1.newLogEntries.size)
            assertEquals("first", obs1.newLogEntries[0].message)

            val obs2 = it.step()
            assertEquals(1, obs2.newLogEntries.size)
            assertEquals("second", obs2.newLogEntries[0].message)
        }
    }

    @Test
    fun `step holds and releases buttons via set diff`() {
        val rom = fakeRom()
        val sym = writeSymFile()
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                screenshotDir = File(tempDir, "screenshots"),
            )
        // Track what the agent holds/releases indirectly by checking frame advance works
        val agent = StepAgent(config = config, stubEmulatorFactory = { stubEmulator() })
        agent.use {
            it.start()
            // Step with RIGHT held
            it.step(setOf(Button.RIGHT))
            assertEquals(1, it.frameCount)
            // Step with RIGHT + A held
            it.step(setOf(Button.RIGHT, Button.A))
            assertEquals(2, it.frameCount)
            // Step with only A — RIGHT should be released
            it.step(setOf(Button.A))
            assertEquals(3, it.frameCount)
        }
    }

    @Test
    fun `step with empty set releases previously held`() {
        makeAgent().use { agent ->
            agent.start()
            agent.step(setOf(Button.RIGHT))
            // Now release all
            val obs = agent.step()
            assertEquals(2, obs.frame)
        }
    }

    @Test
    fun `stepN advances N frames and returns final observation`() {
        makeAgent().use { agent ->
            agent.start()
            val obs = agent.stepN(10)
            assertEquals(10, obs.frame)
            assertEquals(10, agent.frameCount)
        }
    }

    @Test
    fun `scene resolved via metadata`() {
        val memory = mockMemory(0xC102 to 2) // current_scene = 2 → "title"
        val metadata = pongMetadata()

        makeAgent(memory = memory, metadata = metadata).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals("title", obs.scene)
        }
    }

    @Test
    fun `scene falls back to scene_N without metadata`() {
        val memory = mockMemory(0xC102 to 2) // current_scene = 2
        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals("scene_2", obs.scene)
        }
    }

    @Test
    fun `actors populated from metadata and OAM and variables`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // ball at OAM slot 4: visible sprite
        patches += oamPatches(4, rawY = 88, rawX = 88, tile = 10, attr = 0)
        // paddle1 at OAM slots 0-1: visible sprites
        patches += oamPatches(0, rawY = 60, rawX = 20, tile = 0, attr = 0)
        patches += oamPatches(1, rawY = 68, rawX = 20, tile = 1, attr = 0)
        val memory = mockMemory(*patches.toTypedArray())

        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("game" to 1)),
                actors =
                    listOf(
                        ActorMetadata(
                            "paddle1",
                            oamStart = 0,
                            oamCount = 2,
                            spriteWidth = 4,
                            spriteHeight = 16,
                            xVar = "paddle1_x",
                            yVar = "paddle1_y",
                        ),
                        ActorMetadata(
                            "ball",
                            oamStart = 4,
                            oamCount = 1,
                            spriteWidth = 4,
                            spriteHeight = 4,
                            xVar = "ball_x",
                            yVar = "ball_y",
                        ),
                    ),
            )
        // No sym file mapping for paddle1_x etc., so x/y will be null
        makeAgent(memory = memory, metadata = metadata).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals(2, obs.actors.size)
            assertEquals("paddle1", obs.actors[0].name)
            assertEquals(2, obs.actors[0].sprites.size)
            assertEquals("ball", obs.actors[1].name)
            assertEquals(1, obs.actors[1].sprites.size)
        }
    }

    @Test
    fun `actors empty when no metadata provided`() {
        makeAgent().use { agent ->
            agent.start()
            val obs = agent.step()
            assertTrue(obs.actors.isEmpty())
        }
    }

    @Test
    fun `actor sprites grouped by OAM slot range`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // paddle1 uses OAM slots 0 and 1
        patches += oamPatches(0, rawY = 60, rawX = 20, tile = 0, attr = 0)
        patches += oamPatches(1, rawY = 68, rawX = 20, tile = 1, attr = 0)
        // unrelated sprite at slot 10
        patches += oamPatches(10, rawY = 100, rawX = 100, tile = 20, attr = 0)
        val memory = mockMemory(*patches.toTypedArray())

        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("game" to 0)),
                actors =
                    listOf(
                        ActorMetadata(
                            "paddle1",
                            oamStart = 0,
                            oamCount = 2,
                            spriteWidth = 4,
                            spriteHeight = 16,
                            xVar = "paddle1_x",
                            yVar = "paddle1_y",
                        )
                    ),
            )

        makeAgent(memory = memory, metadata = metadata).use { agent ->
            agent.start()
            val obs = agent.step()
            val paddle = obs.actors.find { it.name == "paddle1" }
            assertNotNull(paddle)
            assertEquals(2, paddle!!.sprites.size)
            assertEquals(0, paddle.sprites[0].index)
            assertEquals(1, paddle.sprites[1].index)
        }
    }

    // ── toSummary tests ───────────────────────────────────────────────────────

    private fun emptyRows(count: Int = 18): List<String> = List(count) { "                    " }

    @Test
    fun `toSummary includes frame and scene`() {
        val obs =
            Observation(
                frame = 42,
                variables = emptyMap(),
                scene = "gameplay",
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("Frame 42"))
        assertTrue(summary.contains("Scene: gameplay"))
    }

    @Test
    fun `toSummary includes all variables sorted`() {
        val obs =
            Observation(
                frame = 1,
                variables = mapOf("score" to 42, "lives" to 3, "ballDx" to 1),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("Vars: ballDx=1 lives=3 score=42"))
    }

    @Test
    fun `toSummary includes actors with positions`() {
        val obs =
            Observation(
                frame = 1,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors =
                    listOf(
                        ActorState("ball", x = 80, y = 72, sprites = emptyList()),
                        ActorState("paddle1", x = 16, y = 64, sprites = emptyList()),
                    ),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("ball(80,72)"))
        assertTrue(summary.contains("paddle1(16,64)"))
    }

    @Test
    fun `toSummary shows question marks for null positions`() {
        val obs =
            Observation(
                frame = 1,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = listOf(ActorState("npc", x = null, y = null, sprites = emptyList())),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("npc(?,?)"))
    }

    @Test
    fun `toSummary filters empty text rows`() {
        val bg = MutableList(18) { "                    " }
        bg[0] = "   SCORE: 42        "
        bg[2] = "P1:3     P2:2       "
        val win = emptyRows()

        val obs =
            Observation(
                frame = 1,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = bg,
                winText = win,
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("BG: [row 0] \"   SCORE: 42        \""))
        assertTrue(summary.contains("BG: [row 2] \"P1:3     P2:2       \""))
        assertFalse(summary.contains("BG: [row 1]"))
        assertTrue(summary.contains("WIN: (empty)"))
    }

    @Test
    fun `toSummary includes formatted log entries`() {
        val entry =
            DebugLogEntry(
                timestampMs = 2341,
                level = LogLevel.GAME,
                message = "Bounce",
                context = "gameplay/frame",
            )
        val obs =
            Observation(
                frame = 5,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = listOf(entry),
            )
        val summary = obs.toSummary()
        assertTrue(summary.contains("Log:"))
        assertTrue(summary.contains("Bounce"))
        assertTrue(summary.contains("(gameplay/frame)"))
    }

    @Test
    fun `toSummary minimal observation`() {
        val obs =
            Observation(
                frame = 0,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val summary = obs.toSummary()
        assertFalse(summary.contains("Vars:"))
        assertFalse(summary.contains("Actors:"))
        assertTrue(summary.contains("Sprites: 0 visible"))
        assertTrue(summary.contains("BG: (empty)"))
        assertTrue(summary.contains("WIN: (empty)"))
        assertFalse(summary.contains("Log:"))
    }

    // ── waitUntil tests ──────────────────────────────────────────────────────

    @Test
    fun `waitUntil returns on first match`() {
        val memory = mockMemory(0xC100 to 42)
        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.waitUntil(10) { it.variables["score"] == 42 }
            assertEquals(1, obs.frame)
            assertEquals(42, obs.variables["score"])
        }
    }

    @Test
    fun `waitUntil exhausts maxFrames when predicate never matches`() {
        makeAgent().use { agent ->
            agent.start()
            val obs = agent.waitUntil(5) { false }
            assertEquals(5, obs.frame)
            assertEquals(5, agent.frameCount)
        }
    }

    @Test
    fun `waitUntil returns mid-wait when predicate matches`() {
        val memory = mockMemory()
        var stepCount = 0
        makeAgent(
                memory = memory,
                onStepFrame = {
                    stepCount++
                    if (stepCount == 5) {
                        // Simulate game setting score=42 on frame 5
                        memory.writeByte(0xC100, 42)
                    }
                },
            )
            .use { agent ->
                agent.start()
                val obs = agent.waitUntil(20) { it.variables["score"] == 42 }
                assertEquals(5, obs.frame)
                assertEquals(42, obs.variables["score"])
            }
    }

    @Test
    fun `waitForScene resolves via metadata`() {
        val memory = mockMemory()
        val metadata = pongMetadata()
        var stepCount = 0
        makeAgent(
                memory = memory,
                metadata = metadata,
                onStepFrame = {
                    stepCount++
                    if (stepCount == 3) {
                        // Simulate scene transition: current_scene = 2 → "title"
                        memory.writeByte(0xC102, 2)
                    }
                },
            )
            .use { agent ->
                agent.start()
                val obs = agent.waitForScene("title", 10)
                assertEquals("title", obs.scene)
                assertEquals(3, obs.frame)
            }
    }

    @Test
    fun `auto-loads metadata from config metadataFile`() {
        val rom = fakeRom()
        val sym = writeSymFile()
        val metaJson =
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
              ]
            }
            """
                .trimIndent()
        val metaFile = File(tempDir, "game_metadata.json").also { it.writeText(metaJson) }
        val memory = mockMemory(0xC102 to 0) // current_scene = 0 → "title"
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                metadataFile = metaFile,
                screenshotDir = File(tempDir, "screenshots"),
            )
        val agent =
            StepAgent(
                config = config,
                metadata = null, // should auto-load from metadataFile
                stubEmulatorFactory = { stubEmulator(memory) },
            )
        agent.use {
            it.start()
            val obs = it.step()
            assertEquals("title", obs.scene)
            assertEquals(1, obs.actors.size)
            assertEquals("ball", obs.actors[0].name)
        }
    }

    @Test
    fun `corrupt metadata file falls back to null metadata gracefully`() {
        val rom = fakeRom()
        val sym = writeSymFile()
        val metaFile =
            File(tempDir, "game_metadata.json").also { it.writeText("not valid json {{{") }
        val memory = mockMemory(0xC102 to 2) // current_scene = 2
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                metadataFile = metaFile,
                screenshotDir = File(tempDir, "screenshots"),
            )
        val agent =
            StepAgent(
                config = config,
                metadata = null,
                stubEmulatorFactory = { stubEmulator(memory) },
            )
        agent.use {
            it.start()
            val obs = it.step()
            // Falls back to scene_N without metadata
            assertEquals("scene_2", obs.scene)
            // Actors empty without metadata
            assertTrue(obs.actors.isEmpty())
        }
    }

    @Test
    fun `explicit metadata takes priority over metadataFile`() {
        val rom = fakeRom()
        val sym = writeSymFile()
        // File metadata has scene "fileTitle" at index 0
        val metaJson =
            """
            {
              "scenes": { "fileTitle": 0 },
              "actors": []
            }
            """
                .trimIndent()
        val metaFile = File(tempDir, "game_metadata.json").also { it.writeText(metaJson) }
        val memory = mockMemory(0xC102 to 0)
        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = sym,
                metadataFile = metaFile,
                screenshotDir = File(tempDir, "screenshots"),
            )
        // Explicit metadata has scene "explicitTitle" at index 0
        val explicit =
            GameMetadata.of(scenes = SceneMap(mapOf("explicitTitle" to 0)), actors = emptyList())
        val agent =
            StepAgent(
                config = config,
                metadata = explicit,
                stubEmulatorFactory = { stubEmulator(memory) },
            )
        agent.use {
            it.start()
            val obs = it.step()
            assertEquals("explicitTitle", obs.scene) // explicit wins
        }
    }

    @Test
    fun `waitUntilTextOnScreen returns when text found`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        val memory = mockMemory()
        var stepCount = 0
        makeAgent(
                memory = memory,
                onStepFrame = {
                    stepCount++
                    if (stepCount == 5) {
                        val base = VramTextVerifier.BG_TILEMAP_BASE
                        for ((i, c) in "HELLO".withIndex()) {
                            memory.writeByte(base + i, c.code - 0x20)
                        }
                    }
                },
            )
            .use { agent ->
                agent.start()
                val obs = agent.waitUntilTextOnScreen("HELLO", 20)
                assertEquals(5, obs.frame)
                assertTrue(obs.hasText("HELLO"))
            }
    }

    @Test
    fun `waitUntilTextOnScreen exhausts maxFrames`() {
        makeAgent().use { agent ->
            agent.start()
            val obs = agent.waitUntilTextOnScreen("MISSING", 10)
            assertEquals(10, obs.frame)
            assertFalse(obs.hasText("MISSING"))
        }
    }

    @Test
    fun `isTerminal true when scene in terminalScenes`() {
        val memory = mockMemory(0xC102 to 0) // current_scene = 0 → "gameover"
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("gameover" to 0, "game" to 1)),
                actors = emptyList(),
                terminalScenes = setOf("gameover"),
            )
        makeAgent(memory = memory, metadata = metadata).use { agent ->
            agent.start()
            val obs = agent.step()
            assertEquals("gameover", obs.scene)
            assertTrue(obs.isTerminal)
        }
    }

    @Test
    fun `isTerminal false without metadata`() {
        val memory = mockMemory(0xC102 to 0)
        makeAgent(memory = memory).use { agent ->
            agent.start()
            val obs = agent.step()
            assertFalse(obs.isTerminal)
        }
    }

    @Test
    fun `waitForVariable matches expected value`() {
        val memory = mockMemory(0xC101 to 3) // lives starts at 3
        var stepCount = 0
        makeAgent(
                memory = memory,
                onStepFrame = {
                    stepCount++
                    if (stepCount == 10) {
                        // Simulate lives dropping to 0
                        memory.writeByte(0xC101, 0)
                    }
                },
            )
            .use { agent ->
                agent.start()
                val obs = agent.waitForVariable("lives", 0, 20)
                assertEquals(0, obs.variables["lives"])
                assertEquals(10, obs.frame)
            }
    }

    // ── Introspection tests ──────────────────────────────────────────────────

    @Test
    fun `describeGame returns metadata when loaded`() {
        val metadata = pongMetadata()
        makeAgent(metadata = metadata).use { agent ->
            agent.start()
            val game = agent.describeGame()
            assertNotNull(game)
            assertEquals(3, game!!.scenes.sceneNames.size)
            assertEquals(2, game.actors.size)
        }
    }

    @Test
    fun `listVariables returns sym file symbols`() {
        makeAgent().use { agent ->
            agent.start()
            val vars = agent.listVariables()
            assertTrue(vars.contains("score"))
            assertTrue(vars.contains("lives"))
            assertTrue(vars.contains("current_scene"))
        }
    }

    @Test
    fun `listScenes returns scene names from metadata`() {
        val metadata = pongMetadata()
        makeAgent(metadata = metadata).use { agent ->
            agent.start()
            val scenes = agent.listScenes()
            assertEquals(setOf("gameover", "game", "title"), scenes)
        }
    }
}
