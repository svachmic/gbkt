/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.Observation
import io.github.gbkt.emulator.agent.StepAgent
import io.github.gbkt.emulator.agent.VramTextVerifier
import io.github.gbkt.test.GameDiscovery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Result of a condition-based wait in the MCP session.
 *
 * @param met Whether the condition was satisfied.
 * @param framesElapsed Number of frames stepped.
 * @param observation The final observation.
 */
data class WaitObservation(val met: Boolean, val framesElapsed: Int, val observation: Observation)

/**
 * Result of starting a session.
 *
 * @param metadata Game metadata if available.
 */
data class StartResult(val metadata: GameMetadata?)

/**
 * Result of reading a variable.
 *
 * @param name Variable name.
 * @param value Variable value, or null if not found.
 */
data class VariableResult(val name: String, val value: Int?)

/**
 * A single check in a batch assertion.
 *
 * @param type The assertion type: variable_equals, variable_in_range, scene_is,
 *   text_on_screen, actor_visible, sprite_count.
 * @param args Named arguments for the check (variable, expected values, etc.).
 */
data class AssertCheck(val type: String, val args: Map<String, Any>)

/**
 * Manages the lifecycle of a single [StepAgent] session for the MCP server.
 *
 * Single-session design: one agent at a time (MCP stdio is 1:1 with the client).
 * All mutating methods are `suspend` and guarded by a [Mutex] for thread safety.
 *
 * @param headed When true, each `emulator_start` opens a Swing window showing the Game Boy LCD.
 *   The agent still controls all input — the developer just watches. Default is false (headless).
 * @param stubEmulatorFactory Test-only: inject a stub emulator into the [StepAgent].
 */
class McpEmulatorSession(
    private val headed: Boolean = false,
    private val stubEmulatorFactory: (() -> GbEmulator)? = null,
) {

    private val mutex = Mutex()

    @Volatile
    private var agent: StepAgent? = null
    private var lastObservation: Observation? = null

    /** ROM file for the active session. Used to locate PLAYBOOK.md. */
    @Volatile
    private var activeRomFile: File? = null

    /** Whether a session is currently active. Safe to read without lock via @Volatile. */
    fun isActive(): Boolean = agent != null

    /**
     * Starts a new emulator session with the given ROM file.
     *
     * @throws IllegalStateException if a session is already active.
     */
    suspend fun start(
        romFile: File,
        symFile: File? = null,
        metadataFile: File? = null,
        gbcMode: Boolean = false,
    ): StartResult = mutex.withLock {
        check(!isActive()) { "Session already active. Call stop() first." }

        val config = AgentSessionConfig(
            romFile = romFile,
            symFile = symFile,
            metadataFile = metadataFile,
            gbcMode = gbcMode,
            headless = !headed,
        )
        val newAgent = StepAgent(config, metadata = null, stubEmulatorFactory = stubEmulatorFactory)
        newAgent.start()
        agent = newAgent
        activeRomFile = romFile
        lastObservation = null
        StartResult(metadata = newAgent.describeGame())
    }

    /**
     * Starts a new emulator session using convention-based game name discovery.
     *
     * Uses [GameDiscovery.configForGame] to resolve ROM/sym/metadata paths from the game name.
     *
     * @param gameName Game name matching the ROM file base name (e.g., "pong").
     * @param gbcMode Enable Game Boy Color mode. Default is false.
     * @return StartResult with metadata, or error JSON if the game is not found.
     * @throws IllegalStateException if a session is already active.
     */
    suspend fun startByName(gameName: String, gbcMode: Boolean = false): StartResult {
        val config = GameDiscovery.configForGame(gameName)
            ?: error("Game '$gameName' not found — run buildRom first")
        return start(config.romFile, config.symFile, config.metadataFile, gbcMode)
    }

    /** Stops the current session. */
    suspend fun stop() = mutex.withLock {
        agent?.close()
        agent = null
        activeRomFile = null
        lastObservation = null
    }

    /**
     * Advances [frames] frames with the given [buttons] held.
     *
     * @throws IllegalStateException if no session is active.
     */
    suspend fun step(frames: Int = 1, buttons: Set<Button> = emptySet()): Observation = mutex.withLock {
        val a = requireActive()
        val obs = if (frames == 1) a.step(buttons) else a.stepN(frames, buttons)
        lastObservation = obs
        obs
    }

    /**
     * Presses a single [button] for [frames] frames, releases it, and advances 1 more frame.
     *
     * Total frames advanced = [frames] + 1 (hold + release). The returned [Observation]
     * reflects the game state AFTER the release frame, matching GBDK `pressed()` edge-detection
     * semantics.
     *
     * @param button The button to press.
     * @param frames How many frames to hold the button. Default 1 (tap).
     * @return Observation after the release frame.
     * @throws IllegalStateException if no session is active.
     */
    suspend fun press(button: Button, frames: Int = 1): Observation = mutex.withLock {
        require(frames >= 1) { "frames must be positive" }
        val a = requireActive()
        // Hold for N frames
        if (frames == 1) {
            a.step(setOf(button))
        } else {
            a.stepN(frames, setOf(button))
        }
        // Release and advance 1 frame — observation after release
        val obs = a.step(emptySet())
        lastObservation = obs
        obs
    }

    /**
     * Returns the cached last observation, or steps 1 frame if none exists.
     */
    suspend fun observe(): Observation = mutex.withLock {
        lastObservation?.let { return@withLock it }
        val a = requireActive()
        val obs = a.step()
        lastObservation = obs
        obs
    }

    suspend fun waitForScene(scene: String, maxFrames: Int): WaitObservation = mutex.withLock {
        val a = requireActive()
        val startFrame = a.frameCount
        val obs = a.waitForScene(scene, maxFrames)
        val met = obs.scene == scene
        lastObservation = obs
        WaitObservation(met, a.frameCount - startFrame, obs)
    }

    suspend fun waitForVariable(name: String, expected: Int, maxFrames: Int): WaitObservation = mutex.withLock {
        val a = requireActive()
        val startFrame = a.frameCount
        val obs = a.waitForVariable(name, expected, maxFrames)
        val met = obs.variables[name] == expected
        lastObservation = obs
        WaitObservation(met, a.frameCount - startFrame, obs)
    }

    suspend fun waitForText(text: String, maxFrames: Int): WaitObservation = mutex.withLock {
        val a = requireActive()
        val startFrame = a.frameCount
        val obs = a.waitUntilTextOnScreen(text, maxFrames)
        val met = obs.bgText.any { text in it } || obs.winText.any { text in it }
        lastObservation = obs
        WaitObservation(met, a.frameCount - startFrame, obs)
    }

    suspend fun readVariable(name: String): VariableResult = mutex.withLock {
        val a = requireActive()
        VariableResult(name, a.readVariable(name))
    }

    suspend fun writeVariable(name: String, value: Int): Boolean = mutex.withLock {
        val a = requireActive()
        a.writeVariable(name, value)
    }

    suspend fun screenshot(label: String): File = mutex.withLock {
        requireActive().captureScreenshot(label)
    }

    /** Returns game metadata if available, or null. Safe to read without lock. */
    fun describeGame(): GameMetadata? = agent?.describeGame()

    /**
     * Saves the current emulator state with the given label.
     *
     * State is written to `build/gbkt/savestates/<label>.gbst`.
     *
     * @param label Identifier for the savestate (used as file name).
     * @return JSON with label, frame, scene, and file path.
     * @throws IllegalStateException if no session is active.
     */
    suspend fun saveState(label: String): JsonObject = mutex.withLock {
        require(label.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            "Invalid saveState label '$label': must be alphanumeric with hyphens/underscores only"
        }
        val a = requireActive()
        val savestateDir = File("build/gbkt/savestates")
        savestateDir.mkdirs()
        val saveFile = File(savestateDir, "$label.gbst")
        a.saveState(saveFile)
        val obs = lastObservation
        buildJsonObject {
            put("label", label)
            put("frame", a.frameCount)
            put("scene", obs?.scene)
            put("file", saveFile.absolutePath)
        }
    }

    /**
     * Loads a previously saved emulator state by label.
     *
     * Reads from `build/gbkt/savestates/<label>.gbst`. Steps one frame to refresh the observation.
     *
     * @param label The savestate label used when saving.
     * @return JSON with restored status, label, frame, and scene.
     * @throws IllegalStateException if no session is active.
     */
    suspend fun loadState(label: String): JsonObject = mutex.withLock {
        require(label.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            "Invalid loadState label '$label': must be alphanumeric with hyphens/underscores only"
        }
        val a = requireActive()
        val loadFile = File("build/gbkt/savestates/$label.gbst")
        if (!loadFile.exists()) {
            return@withLock buildJsonObject {
                put("error", "Savestate '$label' not found")
            }
        }
        a.loadState(loadFile)
        // Step one frame to refresh observation
        val obs = a.step()
        lastObservation = obs
        buildJsonObject {
            put("restored", true)
            put("label", label)
            put("frame", a.frameCount)
            put("scene", obs.scene)
        }
    }

    /**
     * Validates multiple conditions against the current game state in a single call.
     *
     * Supported check types:
     * - `variable_equals`: args `name`, `expected` — checks variable value equals expected
     * - `variable_in_range`: args `name`, `min`, `max` — checks variable value in range
     * - `scene_is`: args `scene` — checks current observation scene
     * - `text_on_screen`: args `text`, optional `scrollAware` — checks bgText/winText for substring
     * - `actor_visible`: args `name` — checks actor present in observation
     * - `sprite_count`: args `expected` — checks observation sprite count
     *
     * @param checks List of checks to perform.
     * @return JSON with passed/failed counts and per-check results.
     * @throws IllegalStateException if no session is active.
     */
    suspend fun batchAssert(checks: List<AssertCheck>): JsonObject = mutex.withLock {
        val a = requireActive()
        // Get a fresh observation for all checks
        val obs = lastObservation ?: a.step().also { lastObservation = it }
        val results = mutableListOf<JsonObject>()
        var passed = 0
        var failed = 0
        var extras: Map<String, Any> = emptyMap()

        for (check in checks) {
            val (checkPassed, actual) = when (check.type) {
                "variable_equals" -> {
                    val name = check.args["name"]?.toString() ?: ""
                    val expected = check.args["expected"].asIntOrNull()
                    val actual = obs.variables[name]
                    val pass = expected != null && actual == expected
                    pass to actual?.toString()
                }
                "variable_in_range" -> {
                    val name = check.args["name"]?.toString() ?: ""
                    val min = check.args["min"].asIntOrNull()
                    val max = check.args["max"].asIntOrNull()
                    val actual = obs.variables[name]
                    val pass = min != null && max != null && actual != null &&
                        actual >= min && actual <= max
                    pass to actual?.toString()
                }
                "scene_is" -> {
                    val expectedScene = check.args["scene"]?.toString() ?: ""
                    val actual = obs.scene
                    (actual == expectedScene) to actual
                }
                "text_on_screen" -> {
                    val text = check.args["text"]?.toString() ?: ""
                    val scrollAware = check.args["scrollAware"]?.toString()?.toBooleanStrictOrNull() ?: false
                    // When scrollAware is requested, re-read VRAM with SCX/SCY offsets
                    // instead of using the pre-decoded observation text.
                    val bgRows = if (scrollAware) {
                        a.readTextRows(VramTextVerifier.TilemapLayer.BACKGROUND, scrollAware = true)
                    } else {
                        obs.bgText
                    }
                    val winRows = obs.winText // Window layer is never affected by scroll
                    var foundX: Int? = null
                    var foundY: Int? = null
                    var foundLayer: String? = null
                    for ((y, row) in bgRows.withIndex()) {
                        val x = row.indexOf(text)
                        if (x >= 0) { foundX = x; foundY = y; foundLayer = "bg"; break }
                    }
                    if (foundLayer == null) {
                        for ((y, row) in winRows.withIndex()) {
                            val x = row.indexOf(text)
                            if (x >= 0) { foundX = x; foundY = y; foundLayer = "win"; break }
                        }
                    }
                    if (foundLayer != null) {
                        extras = mapOf("x" to foundX!!, "y" to foundY!!, "layer" to foundLayer)
                    }
                    (foundLayer != null) to if (foundLayer != null) "found" else "not found"
                }
                "actor_visible" -> {
                    val name = check.args["name"]?.toString() ?: ""
                    val visible = obs.actors.any { it.name == name }
                    visible to if (visible) "visible" else "not visible"
                }
                "sprite_count" -> {
                    val expected = check.args["expected"].asIntOrNull()
                    val actual = obs.sprites.size
                    val pass = expected != null && actual == expected
                    pass to actual.toString()
                }
                else -> false to "unknown check type: ${check.type}"
            }

            if (checkPassed) passed++ else failed++
            results.add(buildJsonObject {
                put("type", check.type)
                put("passed", checkPassed)
                if (actual != null) put("actual", actual)
                for ((k, v) in extras) {
                    when (v) { is Int -> put(k, v); is String -> put(k, v) }
                }
                extras = emptyMap()
                // Include the args for context
                put("args", buildJsonObject {
                    for ((k, v) in check.args) put(k, v.toString())
                })
            })
        }

        buildJsonObject {
            put("passed", passed)
            put("failed", failed)
            put("results", buildJsonArray {
                for (r in results) add(r)
            })
        }
    }

    /**
     * Returns the PLAYBOOK.md content for the currently loaded game.
     *
     * Searches for PLAYBOOK.md relative to the ROM file's project directory.
     * For example, if the ROM is at `gbkt-examples/pong/build/gbkt/output/pong.gb`,
     * looks at `gbkt-examples/pong/PLAYBOOK.md`. Also checks the project root.
     *
     * @return JSON with content and path, or content=null message if not found.
     * @throws IllegalStateException if no session is active.
     */
    suspend fun getPlaybook(): JsonObject = mutex.withLock {
        requireActive()
        val romFile = activeRomFile ?: return@withLock buildJsonObject {
            put("content", null as String?)
            put("message", "No active ROM file")
        }

        // Walk up from ROM file to find project root (contains build/gbkt/output)
        // ROM is at: <project>/build/gbkt/output/<name>.gb
        val projectDir = romFile.parentFile?.parentFile?.parentFile?.parentFile

        val candidates = buildList {
            if (projectDir != null) add(File(projectDir, "PLAYBOOK.md"))
            add(File("PLAYBOOK.md"))
        }

        val found = candidates.firstOrNull { it.exists() }
        if (found != null) {
            buildJsonObject {
                put("content", found.readText())
                put("path", found.absolutePath)
            }
        } else {
            buildJsonObject {
                put("content", null as String?)
                put("message", "No PLAYBOOK.md found for this game")
            }
        }
    }

    /**
     * Lists all built game ROMs found in the project.
     *
     * Does not require an active session.
     *
     * @return JSON with games array (name, romFile, hasMetadata).
     */
    suspend fun listGames(): JsonObject {
        val games = GameDiscovery.scanForBuiltRoms()
        return buildJsonObject {
            put("games", buildJsonArray {
                for (game in games) {
                    add(buildJsonObject {
                        put("name", game.name)
                        put("romFile", game.romFile.absolutePath)
                        put("hasMetadata", game.hasMetadata)
                    })
                }
            })
        }
    }

    private fun requireActive(): StepAgent =
        agent ?: throw IllegalStateException("No active session. Call emulator_start first.")
}

// Helper to convert Any? to Int? for assert checks
private fun Any?.asIntOrNull(): Int? = when (this) {
    is Int -> this
    is Long -> this.toInt()
    is Double -> this.toInt()
    is String -> this.toIntOrNull()
    else -> null
}
