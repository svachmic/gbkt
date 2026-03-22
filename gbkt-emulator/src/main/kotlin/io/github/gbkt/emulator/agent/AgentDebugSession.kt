/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.CoffeeGbEmulator
import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.debug.DebugLogEntry
import io.github.gbkt.emulator.ui.EmulatorWindow
import java.io.Closeable
import java.io.File
import javax.swing.SwingUtilities

/**
 * Unified agent session that orchestrates all debug primitives for automated Game Boy ROM testing.
 *
 * [AgentDebugSession] is the single entry point for agent-driven ROM analysis and UAT playtest
 * scripts. It manages the emulator lifecycle (start, pause, stop), wires the EventBus for input
 * injection, and delegates to the individual agent primitives:
 * - [InputScriptPlayer] — deterministic button sequence execution
 * - [ScreenshotCapture] — 160x144 PNG capture with JSON metadata sidecar
 * - [VariableInspector] — .sym-backed DSL variable name resolution and memory read
 * - [SavestateManager] — binary GBST checkpoint/restore for WRAM+OAM+HRAM
 * - [VisualDiff] — pixel-level screenshot comparison with diff image output
 *
 * Usage:
 * ```kotlin
 * val config = AgentSessionConfig(
 *     romFile = File("game.gb"),
 *     symFile = File("game.sym"),
 *     screenshotDir = File("build/screenshots"),
 * )
 * AgentDebugSession(config).use { session ->
 *     session.start()
 *     session.runFrames(60)
 *     val png = session.captureScreenshot("title_screen")
 *     val score = session.readVariable("score")
 * }
 * ```
 *
 * All methods except [stop], [close], and [frameCount] throw [IllegalStateException] if called
 * before [start].
 *
 * @param config Configuration for the session (ROM, sym file, screenshot dir, watch variables, GBC
 *   mode).
 * @param stubEmulatorFactory Internal-use factory for injecting a stub emulator in tests. When null
 *   (the default), creates a [CoffeeGbEmulator] from [AgentSessionConfig.toEmulatorConfig].
 */
class AgentDebugSession(
    private val config: AgentSessionConfig,
    private val stubEmulatorFactory: (() -> GbEmulator)? = null,
) : Closeable {

    // ── Session state ─────────────────────────────────────────────────────────

    /** Total number of emulator frames advanced via [runFrames] or [executeInputScript]. */
    var frameCount: Int = 0
        private set

    // ── Internal references (null until start() is called) ────────────────────

    private var emulator: GbEmulator? = null
    private var inspector: VariableInspector? = null
    private var inputPlayer: InputScriptPlayer? = null
    private var viewerWindow: EmulatorWindow? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the emulator and initializes all debugging components.
     *
     * Creates a [CoffeeGbEmulator] (or the test stub if injected), calls [GbEmulator.start] then
     * [GbEmulator.pause] so the emulator is ready for deterministic frame stepping. Initialises
     * [VariableInspector] from the sym file (if provided) and wires [InputScriptPlayer] to the
     * Coffee-GB EventBus.
     *
     * @throws IllegalStateException if the emulator fails to start.
     */
    fun start() {
        check(emulator == null) {
            "AgentDebugSession already started. Call stop() before starting again."
        }
        val emu =
            try {
                val e =
                    if (stubEmulatorFactory != null) {
                        stubEmulatorFactory.invoke()
                    } else {
                        CoffeeGbEmulator(config.toEmulatorConfig())
                    }
                e.start()
                e.pause()
                e
            } catch (e: Exception) {
                throw EmulatorStartException(
                    "Failed to start emulator for ROM '${config.romFile.name}': ${e.message}",
                    e,
                )
            }

        // Wire VariableInspector to emulator memory
        val memory = emu.getMemory()
        val insp = VariableInspector(memory, config.symFile)

        // Wire InputScriptPlayer — use interface method, fall back to NoOpEventBus for stubs
        val eventBus = emu.getEventBus() ?: NoOpEventBus

        val player = InputScriptPlayer(emu, eventBus)

        this.emulator = emu
        this.inspector = insp
        this.inputPlayer = player

        // When not headless, open a viewer window so the developer can watch the agent play.
        if (!config.headless && emu is CoffeeGbEmulator) {
            SwingUtilities.invokeAndWait {
                val win = EmulatorWindow(emu, config.toEmulatorConfig())
                emu.onFrameReady = { frameData -> win.onFrameReady(frameData) }
                win.title = "gbkt agent — ${config.romFile.name}"
                win.isVisible = true
                viewerWindow = win
            }
        }
    }

    /**
     * Stops the emulator and releases all resources.
     *
     * Safe to call even if [start] was never called. Also safe to call multiple times.
     */
    fun stop() {
        viewerWindow?.let { win -> SwingUtilities.invokeLater { win.dispose() } }
        viewerWindow = null
        emulator?.stop()
        emulator = null
        inspector = null
        inputPlayer = null
    }

    /** Implements [Closeable] — delegates to [stop] for use-with-resources pattern. */
    override fun close() = stop()

    // ── Frame control ─────────────────────────────────────────────────────────

    /**
     * Advances emulation by exactly [n] frames.
     *
     * Each call to [GbEmulator.stepFrame] advances one Game Boy frame (70224 CPU cycles). The
     * emulator must remain paused; [stepFrame] works only in paused mode.
     *
     * @param n Number of frames to advance. Must be positive.
     * @throws IllegalStateException if [start] has not been called.
     */
    fun runFrames(n: Int) {
        val emu = requireStarted()
        try {
            repeat(n) { emu.stepFrame() }
        } catch (e: Exception) {
            throw EmulatorFrameException(
                "Emulator error during frame step (after $frameCount frames): ${e.message}",
                e,
            )
        }
        frameCount += n
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    /**
     * Captures the current LCD frame as a 160x144 PNG with a JSON metadata sidecar.
     *
     * The variable snapshot included in the sidecar depends on [AgentSessionConfig.watchVariables]:
     * - When empty: all variables from the sym file are included.
     * - When non-empty: only the listed variable names are included.
     *
     * @param label Human-readable label used as the file name prefix (e.g., "battle_start").
     * @return The PNG [File] that was written into [AgentSessionConfig.screenshotDir].
     * @throws IllegalStateException if [start] has not been called.
     */
    fun captureScreenshot(label: String): File {
        val emu = requireStarted()
        val insp = checkNotNull(inspector)

        val allVars = insp.readAll()
        val variableSnapshot =
            if (config.watchVariables.isEmpty()) {
                allVars
            } else {
                allVars.filterKeys { it in config.watchVariables }
            }

        return ScreenshotCapture.capture(
            frameBuffer = emu.getFrameBuffer(),
            label = label,
            frameNumber = frameCount,
            outputDir = config.screenshotDir,
            variableSnapshot = variableSnapshot,
        )
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    /**
     * Executes a scripted input sequence against the emulator.
     *
     * Delegates to [InputScriptPlayer.play]. Frame-advancing steps within the script (Press, Wait)
     * are tracked via [InputScript.totalFrames] to keep [frameCount] accurate.
     *
     * @param script The input sequence to execute.
     * @throws IllegalStateException if [start] has not been called or if the emulator is not
     *   paused.
     */
    fun executeInputScript(script: InputScript) {
        requireStarted()
        try {
            checkNotNull(inputPlayer).play(script)
        } catch (e: Exception) {
            throw EmulatorInputException(
                "Input script execution failed at frame $frameCount: ${e.message}",
                e,
            )
        }
        frameCount += script.totalFrames()
    }

    // ── Variable inspection ───────────────────────────────────────────────────

    /**
     * Reads the current byte value for a named DSL variable.
     *
     * @param name The DSL variable name (without the C underscore prefix, e.g. "score" not
     *   "_score").
     * @return The byte value at the symbol's address (0–255), or null if the symbol is not found.
     * @throws IllegalStateException if [start] has not been called.
     */
    fun readVariable(name: String): Int? {
        requireStarted()
        return checkNotNull(inspector).readNamed(name)
    }

    /**
     * Returns a snapshot of all loaded variables mapped to their current byte values.
     *
     * @return Map from variable name to current byte value (0–255). Empty if no sym file was
     *   provided.
     * @throws IllegalStateException if [start] has not been called.
     */
    fun readAllVariables(): Map<String, Int> {
        requireStarted()
        return inspector?.readAll() ?: emptyMap()
    }

    /**
     * Writes a byte value for a named DSL variable.
     *
     * Useful for test setup — e.g. forcing `p1Score` to 4 before testing win condition.
     *
     * @param name The DSL variable name (without the C underscore prefix, e.g. "score" not
     *   "_score").
     * @param value The byte value to write (0–255).
     * @return `true` if the symbol was found and written, `false` if the symbol is not loaded.
     * @throws IllegalStateException if [start] has not been called.
     */
    fun writeVariable(name: String, value: Int): Boolean {
        requireStarted()
        return checkNotNull(inspector).writeNamed(name, value)
    }

    /**
     * Overrides the inferred type for one or more variables in the [VariableInspector].
     *
     * Applies authoritative types from codegen metadata (e.g. "I8", "U16") to replace the
     * heuristic-inferred types. Must be called after [start].
     *
     * @param typeMap Map from variable name to type string (e.g. "I8", "U16", "INT16").
     */
    fun overrideVariableTypes(typeMap: Map<String, String>) {
        inspector?.overrideTypes(typeMap)
    }

    // ── Memory access ──────────────────────────────────────────────────────────

    /**
     * Returns the emulator's [MemoryAccess] for direct memory reads (e.g., VRAM tile maps).
     *
     * @throws IllegalStateException if [start] has not been called.
     */
    fun getMemory(): io.github.gbkt.emulator.MemoryAccess {
        val emu = requireStarted()
        return emu.getMemory()
    }

    // ── Savestate ─────────────────────────────────────────────────────────────

    /**
     * Saves emulator memory state (WRAM+OAM+HRAM) to a binary GBST file.
     *
     * @param file Destination file. Created or overwritten.
     * @throws IllegalStateException if [start] has not been called.
     * @throws IllegalArgumentException if the emulator is not paused.
     */
    fun saveState(file: File) {
        val emu = requireStarted()
        SavestateManager.save(emu, file)
    }

    /**
     * Restores emulator memory state from a binary GBST file created by [saveState].
     *
     * @param file Source file previously created by [saveState].
     * @throws IllegalStateException if [start] has not been called.
     * @throws IllegalArgumentException if the emulator is not paused or the file has wrong magic.
     */
    fun loadState(file: File) {
        val emu = requireStarted()
        SavestateManager.load(emu, file)
    }

    // ── Visual diff ───────────────────────────────────────────────────────────

    /**
     * Compares two PNG screenshot files pixel-by-pixel.
     *
     * Delegates to [VisualDiff.compare]. Does not require the session to be started.
     *
     * @param expected Reference PNG file.
     * @param actual Captured PNG file to compare against [expected].
     * @param tolerance Fraction of pixels allowed to differ (0.0 = pixel-perfect, 0.05 = 5%).
     * @return [DiffResult] with match status, pixel counts, and optional diff image.
     */
    fun diffScreenshots(expected: File, actual: File, tolerance: Double = 0.0): DiffResult =
        VisualDiff.compare(expected, actual, tolerance)

    // ── Debug log ─────────────────────────────────────────────────────────────

    /**
     * Returns all captured debug log entries since [start] was called.
     *
     * @throws IllegalStateException if [start] has not been called.
     */
    fun getDebugLog(): List<DebugLogEntry> {
        val emu = requireStarted()
        return emu.getDebugLog()
    }

    /**
     * Returns the current LCD frame buffer (160x144 RGB pixels).
     *
     * @return 23040-element IntArray where each element is packed as 0x00RRGGBB.
     * @throws IllegalStateException if [start] has not been called.
     */
    fun getFrameBuffer(): IntArray {
        val emu = requireStarted()
        return emu.getFrameBuffer()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun requireStarted(): GbEmulator =
        checkNotNull(emulator) {
            "AgentDebugSession not started. Call start() before using session methods."
        }

    /**
     * No-operation EventBus for stub emulators in tests. Accepts all posted events silently.
     *
     * Used when [stubEmulatorFactory] provides a non-[CoffeeGbEmulator] stub that doesn't have a
     * real [eu.rekawek.coffeegb.core.events.EventBusImpl].
     */
    private object NoOpEventBus : eu.rekawek.coffeegb.core.events.EventBus {
        override fun <E : eu.rekawek.coffeegb.core.events.Event> register(
            subscriber: eu.rekawek.coffeegb.core.events.Subscriber<E>,
            eventClass: Class<E>,
            name: String,
        ) = Unit

        override fun <E : eu.rekawek.coffeegb.core.events.Event> register(
            subscriber: eu.rekawek.coffeegb.core.events.Subscriber<E>,
            eventClass: Class<E>,
        ) = Unit

        override fun <E : eu.rekawek.coffeegb.core.events.Event> post(event: E) = Unit

        override fun <E : eu.rekawek.coffeegb.core.events.Event> postAsync(event: E) = Unit

        override fun fork(name: String): eu.rekawek.coffeegb.core.events.EventBus = this

        override fun close() = Unit
    }
}
