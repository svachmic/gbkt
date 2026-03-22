/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.agent.ActorMetadata
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.OamSpriteReader
import io.github.gbkt.emulator.agent.SceneMap
import io.github.gbkt.emulator.agent.VramTextVerifier
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for all 5 test recipes in [GbktTestRecipes].
 *
 * Uses stub emulator injection via [GbktTestExtension.stubEmulatorFactory] to drive recipes without
 * a real ROM or emulator. Follows the same mock patterns established in StepAgentTest.
 */
class GbktTestRecipesTest {

    @TempDir lateinit var tempDir: File

    // ── Test infrastructure ──────────────────────────────────────────────────

    /**
     * Creates a [MemoryAccess] with statically patched addresses. All unpatched addresses return 0.
     */
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

    /**
     * Creates a [MemoryAccess] together with an `onFrame` callback that mutates memory values after
     * a specified number of `stepFrame()` calls. This decouples mutation timing from the number of
     * `readByte()` calls inside `buildObservation()`, making tests resilient to observation-format
     * changes.
     *
     * @return A pair of (memory, onFrame). Wire `onFrame` into [createExtension]'s `onStepFrame`.
     */
    private fun mutatingMemoryByFrame(
        initialPatches: Map<Int, Int>,
        mutateAfterFrames: Int,
        mutations: Map<Int, Int>,
    ): Pair<MemoryAccess, () -> Unit> {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in initialPatches) {
            mem[addr] = value
        }
        var frameCount = 0
        var mutated = false
        val onFrame: () -> Unit = {
            frameCount++
            if (!mutated && frameCount >= mutateAfterFrames) {
                mutated = true
                for ((addr, value) in mutations) {
                    mem[addr] = value
                }
            }
        }
        val memory =
            object : MemoryAccess {
                override fun readByte(address: Int): Int = mem[address]

                override fun writeByte(address: Int, value: Int) {
                    mem[address] = value
                }
            }
        return memory to onFrame
    }

    /** Writes OAM entries into a patch list for an actor with visible sprites. */
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

    /** Writes ASCII text at the given VRAM BG tilemap row using GBDK encoding. */
    private fun writeVramText(row: Int, text: String): List<Pair<Int, Int>> {
        val base = VramTextVerifier.BG_TILEMAP_BASE + row * VramTextVerifier.ROW_STRIDE
        // GBDK BG decoder: tile index 0x00 = space (0x20), so char.code - 0x20 gives tile index
        return text.mapIndexed { i, c -> base + i to (c.code - 0x20) }
    }

    private fun stubEmulator(
        memory: MemoryAccess = mockMemory(),
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

            override fun getDebugLog(): List<DebugLogEntry> = emptyList()

            override fun isRunning(): Boolean = _running

            override fun isPaused(): Boolean = _paused

            override val isHeadless: Boolean = true
        }

    /**
     * Creates a [GbktTestExtension] with a stub emulator, sets up the file layout matching
     * [io.github.gbkt.emulator.agent.AgentSessionConfig.discoverFiles] conventions, and calls
     * `beforeEach` to initialize the extension lifecycle (agent start, metadata load).
     *
     * File layout: tempDir/gbkt/output/test.gb (ROM) tempDir/gbkt/output/test.sym (sym file)
     * tempDir/gbkt/generated/game_metadata.json (metadata, if provided)
     */
    private fun createExtension(
        memory: MemoryAccess = mockMemory(),
        symContent: String = "DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n",
        metadata: GameMetadata? = null,
        onStepFrame: () -> Unit = {},
    ): GbktTestExtension {
        // Set up conventional file layout for AgentSessionConfig.discoverFiles
        val outputDir = File(tempDir, "gbkt/output").also { it.mkdirs() }
        val generatedDir = File(tempDir, "gbkt/generated").also { it.mkdirs() }

        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        File(outputDir, "test.sym").also { it.writeText(symContent) }

        if (metadata != null) {
            writeMetadataJsonTo(metadata, File(generatedDir, "game_metadata.json"))
        }

        val ext =
            GbktTestExtension(
                gameName = "test",
                customRomFile = rom,
                stubEmulatorFactory = { stubEmulator(memory, onStepFrame) },
            )

        // Invoke JUnit5 lifecycle to initialize agent and metadata
        ext.beforeEach(stubExtensionContext())
        return ext
    }

    /**
     * Creates a minimal [ExtensionContext] proxy for calling [GbktTestExtension.beforeEach] outside
     * of a real JUnit5 test run. Uses a JDK dynamic proxy to avoid implementing the full interface.
     */
    private fun stubExtensionContext(): ExtensionContext {
        return java.lang.reflect.Proxy.newProxyInstance(
            ExtensionContext::class.java.classLoader,
            arrayOf(ExtensionContext::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                java.util.Optional::class.java -> java.util.Optional.empty<Any>()
                String::class.java -> "test"
                Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as ExtensionContext
    }

    /**
     * Serializes [GameMetadata] to a JSON file for discovery by the extension. This is a minimal
     * serialization matching what [GameMetadata.fromJsonFile] expects.
     */
    private fun writeMetadataJsonTo(metadata: GameMetadata, target: File) {
        val scenesJson = buildString {
            append("{")
            val entries =
                metadata.scenes.sceneNames.map { name ->
                    "\"$name\": ${metadata.scenes.indexOf(name)}"
                }
            append(entries.joinToString(", "))
            append("}")
        }

        val actorsJson = buildString {
            append("[")
            val entries =
                metadata.actors.map { actor ->
                    """{"name": "${actor.name}", "oamStart": ${actor.oamStart}, """ +
                        """"oamCount": ${actor.oamCount}, "spriteWidth": ${actor.spriteWidth}, """ +
                        """"spriteHeight": ${actor.spriteHeight}, """ +
                        """"vars": {"x": "${actor.xVar}", "y": "${actor.yVar}"}}"""
                }
            append(entries.joinToString(", "))
            append("]")
        }

        val variablesJson = buildString {
            append("[")
            val entries =
                metadata.variables.map { v -> """{"name": "${v.name}", "type": "${v.type}"}""" }
            append(entries.joinToString(", "))
            append("]")
        }

        val terminalJson = buildString {
            append("[")
            append(metadata.terminalScenes.joinToString(", ") { "\"$it\"" })
            append("]")
        }

        val json =
            """{
            "scenes": $scenesJson,
            "actors": $actorsJson,
            "variables": $variablesJson,
            "texts": [],
            "terminalScenes": $terminalJson
        }"""
                .trimIndent()

        target.writeText(json)
    }

    // ── verifyTitleScreen ────────────────────────────────────────────────────

    @Test
    fun `verifyTitleScreen passes with metadata and title scene at index 0`() {
        // Scene index 0 = "title", current_scene memory address C102 = 0
        val memory = mockMemory(0xC102 to 0)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val obs = ext.verifyTitleScreen()
        assertEquals("title", obs.scene)
    }

    @Test
    fun `verifyTitleScreen passes with lowest-index scene that is not title-named`() {
        // "intro_cutscene" at index 0 -- lowest index, accepted as title
        val memory = mockMemory(0xC102 to 0)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("intro_cutscene" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val obs = ext.verifyTitleScreen()
        assertEquals("intro_cutscene", obs.scene)
    }

    @Test
    fun `verifyTitleScreen passes with heuristic match when no metadata`() {
        // Without metadata, scene resolved as "scene_0" -- no assertion on name
        val memory = mockMemory(0xC102 to 0)
        val ext = createExtension(memory = memory, metadata = null)
        val obs = ext.verifyTitleScreen()
        // Without metadata the recipe accepts any scene -- just verify it returned
        assertEquals(120, obs.frame)
    }

    @Test
    fun `verifyTitleScreen validates expectedTexts against VRAM content`() {
        val textPatches = writeVramText(0, "PONG").toTypedArray()
        val scenePatches = arrayOf(0xC102 to 0)
        val allPatches = textPatches + scenePatches
        val memory = mockMemory(*allPatches)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val obs = ext.verifyTitleScreen(expectedTexts = listOf("PONG"))
        assertEquals("title", obs.scene)
    }

    @Test
    fun `verifyTitleScreen fails when expected text missing from VRAM`() {
        val memory = mockMemory(0xC102 to 0)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val error =
            assertFailsWith<AssertionError> {
                ext.verifyTitleScreen(expectedTexts = listOf("MISSING TEXT"))
            }
        assertTrue(
            error.message!!.contains("MISSING TEXT"),
            "Error should mention the missing text",
        )
    }

    @Test
    fun `verifyTitleScreen fails when scene is not title and not lowest index`() {
        // Scene index 1 = "gameplay", but lowest is "title" at 0
        val memory = mockMemory(0xC102 to 1)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val error = assertFailsWith<AssertionError> { ext.verifyTitleScreen() }
        assertTrue(
            error.message!!.contains("gameplay"),
            "Error should mention the actual scene 'gameplay'",
        )
    }

    // ── verifyFirstSceneTransition ───────────────────────────────────────────

    @Test
    fun `verifyFirstSceneTransition is no-op without metadata`() {
        val memory = mockMemory(0xC102 to 0)
        val ext = createExtension(memory = memory, metadata = null)
        // Should complete without error -- returns early when no metadata
        ext.verifyFirstSceneTransition()
    }

    @Test
    fun `verifyFirstSceneTransition is no-op with single scene in metadata`() {
        val memory = mockMemory(0xC102 to 0)
        val metadata = GameMetadata.of(scenes = SceneMap(mapOf("title" to 0)), actors = emptyList())
        val ext = createExtension(memory = memory, metadata = metadata)
        // Should complete without error -- returns early when < 2 scenes
        ext.verifyFirstSceneTransition()
    }

    @Test
    fun `verifyFirstSceneTransition logs warning when transition does not happen`() {
        // Scene stays at 0 (title) forever, never transitions
        val memory = mockMemory(0xC102 to 0)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        // Should not throw -- best-effort check logs a warning instead
        ext.verifyFirstSceneTransition()
    }

    // ── verifyInputResponds ──────────────────────────────────────────────────

    @Test
    fun `verifyInputResponds passes when variable increases`() {
        // current_scene=1 ("gameplay"), score starts at 5 then mutates to 10.
        // bootToScene: step() = frame 1 → already at "gameplay", returns immediately.
        // readVariable("score") → 5 (no frame advance).
        // stepN(30): frames 2-31. Mutation at frame 2 changes score to 10.
        // step() (release): frame 32.
        // readVariable("score") → 10 (no frame advance).
        val (memory, onFrame) =
            mutatingMemoryByFrame(
                initialPatches = mapOf(0xC100 to 5, 0xC101 to 3, 0xC102 to 1),
                mutateAfterFrames = 2,
                mutations = mapOf(0xC100 to 10),
            )
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata, onStepFrame = onFrame)
        ext.verifyInputResponds(scene = "gameplay", button = Button.RIGHT, variableName = "score")
    }

    @Test
    fun `verifyInputResponds passes when variable decreases with expectDecrease`() {
        // Same frame flow as "increases" test: mutation at frame 2 (first frame of stepN(30)).
        val (memory, onFrame) =
            mutatingMemoryByFrame(
                initialPatches = mapOf(0xC100 to 10, 0xC101 to 3, 0xC102 to 1),
                mutateAfterFrames = 2,
                mutations = mapOf(0xC100 to 3),
            )
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata, onStepFrame = onFrame)
        ext.verifyInputResponds(
            scene = "gameplay",
            button = Button.LEFT,
            variableName = "score",
            expectDecrease = true,
        )
    }

    @Test
    fun `verifyInputResponds fails when variable unchanged`() {
        // Score stays at 5 -- no change
        val memory = mockMemory(0xC100 to 5, 0xC101 to 3, 0xC102 to 1)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val error =
            assertFailsWith<AssertionError> {
                ext.verifyInputResponds(
                    scene = "gameplay",
                    button = Button.RIGHT,
                    variableName = "score",
                )
            }
        assertTrue(error.message!!.contains("score"), "Error should mention variable name 'score'")
        assertTrue(error.message!!.contains("before=5"), "Error should include before value")
        assertTrue(error.message!!.contains("after=5"), "Error should include after value")
    }

    // ── verifySpriteVisibility ───────────────────────────────────────────────

    @Test
    fun `verifySpriteVisibility passes when actors present in OAM`() {
        // OAM sprite at slot 0 with visible position (rawY=96, rawX=48)
        val oam = oamPatches(0, 96, 48, 1, 0) + oamPatches(1, 96, 56, 2, 0)
        val scenePatches = listOf(0xC102 to 1, 0xC100 to 40, 0xC101 to 80)
        val allPatches = (oam + scenePatches).toTypedArray()
        val memory = mockMemory(*allPatches)

        val symContent =
            "DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n" +
                "DEF _paddle_x 00:C100\nDEF _paddle_y 00:C101\n"

        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors =
                    listOf(
                        ActorMetadata(
                            name = "paddle",
                            oamStart = 0,
                            oamCount = 2,
                            spriteWidth = 8,
                            spriteHeight = 16,
                            xVar = "paddle_x",
                            yVar = "paddle_y",
                        )
                    ),
            )
        val ext = createExtension(memory = memory, symContent = symContent, metadata = metadata)
        ext.verifySpriteVisibility("gameplay", listOf("paddle"))
    }

    @Test
    fun `verifySpriteVisibility fails when actor missing`() {
        val memory = mockMemory(0xC102 to 1)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val error =
            assertFailsWith<AssertionError> {
                ext.verifySpriteVisibility("gameplay", listOf("ball"))
            }
        assertTrue(
            error.message!!.contains("ball"),
            "Error should mention the missing actor 'ball'",
        )
    }

    // ── bootToScene ──────────────────────────────────────────────────────────

    @Test
    fun `bootToScene returns immediately when already at target scene`() {
        val memory = mockMemory(0xC102 to 1)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val obs = ext.bootToScene("gameplay")
        assertEquals("gameplay", obs.scene)
    }

    @Test
    fun `bootToScene finds scene after waiting with mutating memory`() {
        // Start at title (scene 0). bootToScene flow:
        // step() = frame 1 → scene "title", not "gameplay".
        // waitForScene("gameplay", 600): step() = frame 2 → mutation fires, scene becomes
        // "gameplay".
        val (memory, onFrame) =
            mutatingMemoryByFrame(
                initialPatches = mapOf(0xC102 to 0),
                mutateAfterFrames = 2,
                mutations = mapOf(0xC102 to 1),
            )
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata, onStepFrame = onFrame)
        val obs = ext.bootToScene("gameplay")
        assertEquals("gameplay", obs.scene)
    }

    @Test
    fun `bootToScene throws when scene unreachable after maxFrames`() {
        // Scene stays at 0 forever
        val memory = mockMemory(0xC102 to 0)
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata)
        val error = assertFailsWith<AssertionError> { ext.bootToScene("gameplay", maxFrames = 10) }
        assertTrue(error.message!!.contains("gameplay"), "Error should mention the target scene")
        assertTrue(
            error.message!!.contains("could not reach"),
            "Error should indicate scene was unreachable",
        )
    }

    @Test
    fun `bootToScene finds scene after START press`() {
        // bootToScene("gameplay", maxFrames=3) flow:
        // step() = frame 1 → scene "title".
        // waitForScene("gameplay", 3): frames 2-4 → all "title" (exhausted).
        // step(START) = frame 5, step() = frame 6 (release).
        // waitForScene("gameplay", 3): step() = frame 7 → mutation fires, scene "gameplay".
        val (memory, onFrame) =
            mutatingMemoryByFrame(
                initialPatches = mapOf(0xC102 to 0),
                mutateAfterFrames = 7,
                mutations = mapOf(0xC102 to 1),
            )
        val metadata =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "gameplay" to 1)),
                actors = emptyList(),
            )
        val ext = createExtension(memory = memory, metadata = metadata, onStepFrame = onFrame)
        val obs = ext.bootToScene("gameplay", maxFrames = 3)
        assertEquals("gameplay", obs.scene)
    }
}
