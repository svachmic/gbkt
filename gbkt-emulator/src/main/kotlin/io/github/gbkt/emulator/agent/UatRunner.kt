/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.debug.DebugLogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.logging.Logger

/**
 * Higher-level orchestrator for UAT gameplay testing, wrapping [AgentDebugSession].
 *
 * Provides a checkpoint-based workflow: advance emulation via [wait]/[press]/[hold]/[release],
 * then call [checkpoint] to capture a screenshot + all variables + debug log slice since the
 * last checkpoint. Soft assertions ([assertVariable], [assertVariableInRange], [assertCustom])
 * are recorded and included in the final [generateReport].
 *
 * Usage:
 * ```kotlin
 * UatRunner("pong", config).use { runner ->
 *     runner.start()
 *     runner.wait(120)
 *     runner.checkpoint("01_title")
 *     runner.press(Button.START, 5)
 *     runner.wait(60)
 *     runner.assertVariable("p1Score", 0)
 *     runner.checkpoint("02_gameplay")
 *     val report = runner.generateReport()
 * }
 * ```
 *
 * @param gameName Human-readable game name for the report.
 * @param config Session configuration (ROM, sym file, screenshot dir, etc.).
 * @param stubEmulatorFactory Test-only: inject a stub emulator.
 */
class UatRunner(
    private val gameName: String,
    private val config: AgentSessionConfig,
    private val goldenDir: File? = null,
    private val goldenTolerance: Double = 0.0,
    private val sceneMap: SceneMap? = null,
    private val metadata: GameMetadata? = null,
    private val stubEmulatorFactory: (() -> io.github.gbkt.emulator.GbEmulator)? = null,
) : AutoCloseable {

    private val logger = Logger.getLogger(UatRunner::class.java.name)

    private val resolvedMetadata: GameMetadata? = metadata
        ?: config.metadataFile?.takeIf { it.exists() }?.let { file ->
            try {
                GameMetadata.fromJsonFile(file)
            } catch (e: MetadataParseException) {
                logger.warning("Failed to parse metadata: ${e.message}")
                null
            }
        }

    private val resolvedSceneMap: SceneMap? = resolvedMetadata?.scenes ?: sceneMap

    init {
        if (sceneMap != null && metadata == null) {
            logger.warning("UatRunner sceneMap is deprecated. Use metadata = GameMetadata.of(scenes = sceneMap, ...) instead.")
        }
    }

    private val session = AgentDebugSession(config, stubEmulatorFactory)
    private val checkpoints = mutableListOf<UatCheckpoint>()
    private val pendingAssertions = mutableListOf<UatAssertion>()
    private var lastLogIndex = 0
    private val checkpointTolerances = mutableMapOf<String, Double>()

    /**
     * Sets a per-checkpoint golden comparison tolerance, overriding the default [goldenTolerance].
     *
     * @param label The checkpoint label.
     * @param tolerance Fraction of pixels allowed to differ (0.0 = pixel-perfect, 0.05 = 5%).
     */
    fun setCheckpointTolerance(label: String, tolerance: Double) {
        checkpointTolerances[label] = tolerance
    }

    /** Starts the emulator session. Must be called before any other method. */
    fun start() {
        session.start()
    }

    /** Advances emulation by [frames] frames with no input. */
    fun wait(frames: Int) {
        session.runFrames(frames)
    }

    /**
     * Advances emulation one frame at a time until [condition] returns true or [maxFrames] is exhausted.
     *
     * The condition is checked **before** the first frame step, so if already true this returns
     * immediately with `framesElapsed = 0`. After the last frame step the condition is checked
     * once more to detect the boundary case.
     *
     * @param maxFrames Maximum number of frames to step before giving up.
     * @param condition Predicate evaluated after each frame.
     * @return A [WaitResult] indicating whether the condition was met and how many frames elapsed.
     */
    fun waitUntil(maxFrames: Int, condition: () -> Boolean): WaitResult {
        for (i in 0 until maxFrames) {
            if (condition()) return WaitResult(met = true, framesElapsed = i)
            session.runFrames(1)
        }
        // Check once more after the last frame
        return WaitResult(met = condition(), framesElapsed = maxFrames)
    }

    /**
     * Waits until the named variable equals [expected], stepping one frame at a time.
     *
     * Convenience wrapper around [waitUntil].
     *
     * @param name DSL variable name (e.g., `"current_scene"`).
     * @param expected The value to wait for.
     * @param maxFrames Maximum frames before giving up.
     */
    fun waitUntilVariable(name: String, expected: Int, maxFrames: Int): WaitResult =
        waitUntil(maxFrames) { readVariable(name) == expected }

    /**
     * Waits until [text] appears on screen in either tilemap layer.
     *
     * Convenience wrapper around [waitUntil] using [VramTextVerifier.findTextAnyLayer].
     *
     * @param text The text to wait for.
     * @param maxFrames Maximum frames before giving up.
     */
    fun waitUntilTextOnScreen(text: String, maxFrames: Int): WaitResult =
        waitUntil(maxFrames) {
            VramTextVerifier.findTextAnyLayer(session.getMemory(), text) != null
        }

    /**
     * Presses [button] for [frames] frames, then releases.
     *
     * This advances the emulator by [frames] frames.
     */
    fun press(button: Button, frames: Int = 5) {
        session.executeInputScript(inputScript { press(button, frames) })
    }

    /** Holds [button] without releasing. Does not advance frames. */
    fun hold(button: Button) {
        session.executeInputScript(inputScript { hold(button) })
    }

    /** Releases a previously held [button]. Does not advance frames. */
    fun release(button: Button) {
        session.executeInputScript(inputScript { release(button) })
    }

    /**
     * Captures a checkpoint: screenshot + all variables + debug log slice since last checkpoint.
     *
     * All pending assertions (from [assertVariable], [assertVariableInRange], [assertCustom])
     * are flushed into this checkpoint.
     *
     * @param label Human-readable label (used as screenshot filename prefix).
     * @return The captured [UatCheckpoint].
     */
    fun checkpoint(label: String): UatCheckpoint {
        val allLog = session.getDebugLog()
        val logSlice = allLog.drop(lastLogIndex)
        lastLogIndex = allLog.size

        val variables = session.readAllVariables()

        val screenshotFile = ScreenshotCapture.capture(
            frameBuffer = getFrameBuffer(),
            label = label,
            frameNumber = session.frameCount,
            outputDir = config.screenshotDir,
            variableSnapshot = variables,
            debugLogEntries = logSlice,
        )

        // Golden screenshot comparison
        val diffResult = if (goldenDir != null) {
            val goldenFile = File(goldenDir, "$label.png")
            if (goldenFile.exists()) {
                val tol = checkpointTolerances[label] ?: goldenTolerance
                VisualDiff.compare(goldenFile, screenshotFile, tol, config.screenshotDir)
            } else {
                logger.info("GOLDEN MISSING: $goldenFile — promote with: cp ${screenshotFile.absolutePath} ${goldenFile.absolutePath}")
                null
            }
        } else {
            null
        }

        val assertions = pendingAssertions.toList()
        pendingAssertions.clear()

        val cp = UatCheckpoint(
            label = label,
            frameNumber = session.frameCount,
            screenshotFile = screenshotFile,
            variables = variables,
            debugLogSlice = logSlice,
            assertions = assertions,
            diffResult = diffResult,
        )
        checkpoints += cp
        return cp
    }

    /** Reads the current byte value for a named DSL variable, or null if not found. */
    fun readVariable(name: String): Int? = session.readVariable(name)

    /** Returns a snapshot of all loaded variables mapped to their current byte values. */
    fun readAllVariables(): Map<String, Int> = session.readAllVariables()

    /**
     * Writes a byte value for a named DSL variable. Useful for test setup.
     *
     * @return `true` if the symbol was found and written.
     */
    fun writeVariable(name: String, value: Int): Boolean = session.writeVariable(name, value)

    /**
     * Records a soft assertion that [name] equals [expected].
     *
     * The assertion is stored and flushed into the next [checkpoint]. Does not throw.
     */
    fun assertVariable(name: String, expected: Int) {
        val actual = session.readVariable(name)
        pendingAssertions += UatAssertion(
            description = "$name == $expected",
            passed = actual == expected,
            expected = expected.toString(),
            actual = actual?.toString() ?: "null",
        )
    }

    /**
     * Records a soft assertion that [name] is within [range].
     *
     * The assertion is stored and flushed into the next [checkpoint]. Does not throw.
     */
    fun assertVariableInRange(name: String, range: IntRange) {
        val actual = session.readVariable(name)
        pendingAssertions += UatAssertion(
            description = "$name in ${range.first}..${range.last}",
            passed = actual != null && actual in range,
            expected = "${range.first}..${range.last}",
            actual = actual?.toString() ?: "null",
        )
    }

    /**
     * Records a custom soft assertion with a user-provided description and pass/fail status.
     *
     * The assertion is stored and flushed into the next [checkpoint]. Does not throw.
     */
    fun assertCustom(description: String, passed: Boolean) {
        pendingAssertions += UatAssertion(
            description = description,
            passed = passed,
            expected = "true",
            actual = passed.toString(),
        )
    }

    /**
     * Records a soft assertion that [text] is present at tile position ([x], [y]) in the tilemap.
     *
     * Reads VRAM tile indices and decodes them as ASCII using [VramTextVerifier].
     *
     * @param text Expected text string.
     * @param x Tile column (0-based).
     * @param y Tile row (0-based).
     * @param layer Which tilemap layer to read from.
     */
    fun assertTextAt(
        text: String,
        x: Int,
        y: Int,
        layer: VramTextVerifier.TilemapLayer = VramTextVerifier.TilemapLayer.BACKGROUND,
    ) {
        val memory = session.getMemory()
        val actual = VramTextVerifier.readText(memory, x, y, text.length, layer)
        pendingAssertions += UatAssertion(
            description = "text at ($x,$y): \"$text\"",
            passed = actual == text,
            expected = text,
            actual = actual,
        )
    }

    /**
     * Records a soft assertion that [text] is present somewhere on screen in either tilemap layer.
     *
     * Searches both background and window tilemaps using [VramTextVerifier.findTextAnyLayer].
     *
     * @param text The text to search for.
     */
    fun assertTextOnScreen(text: String) {
        val memory = session.getMemory()
        val result = VramTextVerifier.findTextAnyLayer(memory, text)
        pendingAssertions += UatAssertion(
            description = "text on screen: \"$text\"",
            passed = result != null,
            expected = text,
            actual = if (result != null) "found at (${result.first},${result.second}) on ${result.third}" else "not found",
        )
    }

    /**
     * Returns the current scene name by reading the `current_scene` variable.
     *
     * If a [SceneMap] was provided, the numeric index is resolved to its name.
     * Otherwise, returns `"scene_N"` where N is the raw index. Returns null if
     * `current_scene` is not found in the symbol table.
     */
    fun currentScene(): String? {
        val index = readVariable("current_scene") ?: return null
        return resolvedSceneMap?.nameOf(index) ?: "scene_$index"
    }

    /**
     * Waits until the `current_scene` variable matches [sceneName].
     *
     * Requires a [SceneMap] to resolve the name to a numeric index.
     *
     * @param sceneName Lowercase scene name (e.g., `"title"`, `"game"`, `"gameover"`).
     * @param maxFrames Maximum frames before giving up.
     * @throws IllegalArgumentException if [sceneName] is not in the scene map.
     */
    fun waitForScene(sceneName: String, maxFrames: Int): WaitResult {
        val index = resolvedSceneMap?.indexOf(sceneName)
            ?: throw IllegalArgumentException("Unknown scene '$sceneName' — not in sceneMap")
        return waitUntilVariable("current_scene", index, maxFrames)
    }

    /**
     * Records a soft assertion that the current scene matches [sceneName].
     *
     * Uses [currentScene] to read the scene and compares against [sceneName].
     */
    fun assertScene(sceneName: String) {
        val actual = currentScene()
        pendingAssertions += UatAssertion(
            description = "scene == \"$sceneName\"",
            passed = actual == sceneName,
            expected = sceneName,
            actual = actual ?: "null",
        )
    }

    /**
     * Generates a [UatReport] aggregating all checkpoints and assertions.
     *
     * Also writes a `uat_report.json` file to the screenshot directory.
     */
    fun generateReport(): UatReport {
        val allAssertions = checkpoints.flatMap { it.assertions }
        val goldenResults = checkpoints.mapNotNull { it.diffResult }
        val report = UatReport(
            gameName = gameName,
            checkpoints = checkpoints.toList(),
            totalAssertions = allAssertions.size,
            passedAssertions = allAssertions.count { it.passed },
            failedAssertions = allAssertions.count { !it.passed },
            goldenComparisons = goldenResults.size,
            goldenPassed = goldenResults.count { it.match },
            goldenFailed = goldenResults.count { !it.match },
        )

        // Write report JSON
        val reportJson = JSONObject()
            .put("gameName", report.gameName)
            .put("totalAssertions", report.totalAssertions)
            .put("passedAssertions", report.passedAssertions)
            .put("failedAssertions", report.failedAssertions)
            .put("goldenComparisons", report.goldenComparisons)
            .put("goldenPassed", report.goldenPassed)
            .put("goldenFailed", report.goldenFailed)
        val checkpointsArray = JSONArray()
        for (cp in report.checkpoints) {
            val cpJson = JSONObject()
                .put("label", cp.label)
                .put("frameNumber", cp.frameNumber)
                .put("screenshotFile", cp.screenshotFile.name)
                .put("variables", JSONObject(cp.variables))
            val assertionsArray = JSONArray()
            for (a in cp.assertions) {
                assertionsArray.put(
                    JSONObject()
                        .put("description", a.description)
                        .put("passed", a.passed)
                        .put("expected", a.expected)
                        .put("actual", a.actual),
                )
            }
            cpJson.put("assertions", assertionsArray)
            checkpointsArray.put(cpJson)
        }
        reportJson.put("checkpoints", checkpointsArray)

        config.screenshotDir.mkdirs()
        File(config.screenshotDir, "uat_report.json").writeText(reportJson.toString(2))

        return report
    }

    /**
     * Records a soft assertion that a visible sprite exists near position ([x], [y]).
     *
     * Searches [OamSpriteReader.readVisible] for a sprite whose screen position is within
     * [tolerance] pixels of ([x], [y]). Optionally also checks [tileIndex].
     *
     * @param x Expected screen X position.
     * @param y Expected screen Y position.
     * @param tileIndex If non-null, the sprite must also have this tile index.
     * @param tolerance Maximum pixel distance in either axis (default 2).
     */
    fun assertSpriteAt(x: Int, y: Int, tileIndex: Int? = null, tolerance: Int = 2) {
        val memory = session.getMemory()
        val visible = OamSpriteReader.readVisible(memory)
        val match = visible.any { sprite ->
            val xMatch = (sprite.screenX - x) in -tolerance..tolerance
            val yMatch = (sprite.screenY - y) in -tolerance..tolerance
            val tileMatch = tileIndex == null || sprite.tileIndex == tileIndex
            xMatch && yMatch && tileMatch
        }
        val desc = if (tileIndex != null) {
            "sprite at ($x,$y) tile=$tileIndex ±$tolerance"
        } else {
            "sprite at ($x,$y) ±$tolerance"
        }
        pendingAssertions += UatAssertion(
            description = desc,
            passed = match,
            expected = "sprite present",
            actual = if (match) "found" else "not found (${visible.size} visible sprites)",
        )
    }

    /**
     * Records a soft assertion that exactly [expected] sprites are visible on screen.
     *
     * @param expected The expected number of visible OAM sprites.
     */
    fun assertSpriteCount(expected: Int) {
        val memory = session.getMemory()
        val visible = OamSpriteReader.readVisible(memory)
        pendingAssertions += UatAssertion(
            description = "visible sprite count == $expected",
            passed = visible.size == expected,
            expected = expected.toString(),
            actual = visible.size.toString(),
        )
    }

    override fun close() {
        session.close()
    }

    private fun getFrameBuffer(): IntArray = session.getFrameBuffer()
}

/**
 * Result of a condition-based wait operation.
 *
 * @param met Whether the condition was satisfied before [maxFrames] was exhausted.
 * @param framesElapsed Number of frames that were stepped. 0 if the condition was already true.
 */
data class WaitResult(val met: Boolean, val framesElapsed: Int)

/**
 * A single checkpoint captured during a UAT run.
 *
 * @param label Human-readable label for this checkpoint.
 * @param frameNumber The emulator frame number at capture time.
 * @param screenshotFile The PNG file that was written.
 * @param variables Snapshot of all loaded DSL variables at capture time.
 * @param debugLogSlice Debug log entries since the previous checkpoint (or session start).
 * @param assertions Soft assertions that were recorded before this checkpoint.
 */
data class UatCheckpoint(
    val label: String,
    val frameNumber: Int,
    val screenshotFile: File,
    val variables: Map<String, Int>,
    val debugLogSlice: List<DebugLogEntry>,
    val assertions: List<UatAssertion>,
    val diffResult: DiffResult? = null,
)

/**
 * A single soft assertion recorded during a UAT run.
 *
 * @param description Human-readable description of what was asserted.
 * @param passed Whether the assertion passed.
 * @param expected The expected value (as a string for display).
 * @param actual The actual value (as a string for display).
 */
data class UatAssertion(
    val description: String,
    val passed: Boolean,
    val expected: String,
    val actual: String,
)

/**
 * Aggregated report from a UAT run.
 *
 * @param gameName Human-readable game name.
 * @param checkpoints All checkpoints captured during the run.
 * @param totalAssertions Total number of assertions across all checkpoints.
 * @param passedAssertions Number of passed assertions.
 * @param failedAssertions Number of failed assertions.
 */
data class UatReport(
    val gameName: String,
    val checkpoints: List<UatCheckpoint>,
    val totalAssertions: Int,
    val passedAssertions: Int,
    val failedAssertions: Int,
    val goldenComparisons: Int = 0,
    val goldenPassed: Int = 0,
    val goldenFailed: Int = 0,
)
