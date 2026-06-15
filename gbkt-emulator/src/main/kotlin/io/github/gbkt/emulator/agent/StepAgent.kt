/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import java.util.logging.Logger

/**
 * Per-actor state snapshot within an [Observation], combining metadata identity with runtime
 * values.
 *
 * @param name Actor name from [GameMetadata] (e.g., `"ball"`, `"paddle1"`).
 * @param x X position variable value, or null if the symbol is not loaded.
 * @param y Y position variable value, or null if the symbol is not loaded.
 * @param sprites OAM [SpriteEntry] instances belonging to this actor (resolved via OAM slot range).
 */
data class ActorState(val name: String, val x: Int?, val y: Int?, val sprites: List<SpriteEntry>)

/**
 * Full game state snapshot returned by [StepAgent.step] after advancing one frame.
 *
 * Provides both raw hardware state (sprites, tilemap text) and semantic game state (named actors,
 * scene name, DSL variables). This is the observe step in an observe-decide-act agent loop.
 *
 * @param frame Frame number after the step.
 * @param variables All DSL variables mapped to their current byte values.
 * @param scene Resolved scene name (e.g., `"title"`) or `"scene_N"` fallback, or null.
 * @param sprites All visible OAM sprites (raw hardware state, always present).
 * @param actors Named actors with resolved positions and grouped sprites (empty without metadata).
 * @param bgText 18 rows × 20 chars from the background tilemap.
 * @param winText 18 rows × 20 chars from the window tilemap.
 * @param newLogEntries Debug log entries emitted since the previous step.
 */
data class Observation(
    val frame: Int,
    val variables: Map<String, Int>,
    val scene: String?,
    val sprites: List<SpriteEntry>,
    val actors: List<ActorState>,
    val bgText: List<String>,
    val winText: List<String>,
    val newLogEntries: List<DebugLogEntry>,
    val isTerminal: Boolean = false,
)

/**
 * Frame-by-frame agent that provides an observe-decide-act loop over the Game Boy emulator.
 *
 * Each call to [step] advances exactly one frame with the declared set of held buttons and returns
 * a full [Observation] of the game state. This follows the standard RL environment pattern
 * (Gymnasium/Atari): the caller declares which buttons are held each frame and never manages
 * press/release transitions.
 *
 * Usage:
 * ```kotlin
 * StepAgent(config, metadata).use { agent ->
 *     agent.start()
 *     // Title screen — press START
 *     repeat(60) { agent.step() }
 *     val obs = agent.step(setOf(Button.START))
 *     agent.step()  // release
 *     // Game loop
 *     while (obs.scene != "gameover") {
 *         val obs = agent.step(decideButtons(obs))
 *     }
 * }
 * ```
 *
 * @param config Session configuration (ROM, sym file, screenshot dir, etc.).
 * @param metadata Optional game metadata for actor resolution and scene naming.
 * @param stubEmulatorFactory Test-only: inject a stub emulator.
 */
class StepAgent(
    private val config: AgentSessionConfig,
    metadata: GameMetadata? = null,
    private val stubEmulatorFactory: (() -> GbEmulator)? = null,
) : AutoCloseable {

    private val logger = Logger.getLogger(StepAgent::class.java.name)

    private val resolvedMetadata: GameMetadata? =
        metadata
            ?: config.metadataFile
                ?.takeIf { it.exists() }
                ?.let { file ->
                    try {
                        GameMetadata.fromJsonFile(file)
                    } catch (e: MetadataParseException) {
                        logger.warning("Failed to parse metadata: ${e.message}")
                        null
                    }
                }

    private val session = AgentDebugSession(config, stubEmulatorFactory)
    private val heldButtons = mutableSetOf<Button>()
    private var lastLogIndex = 0

    /** Total number of emulator frames advanced via [step] or [stepN]. */
    val frameCount: Int
        get() = session.frameCount

    /**
     * Starts the emulator session. Must be called before [step].
     *
     * After session start, applies authoritative variable types from [GameMetadata] (if available)
     * to override the heuristic-inferred types in [VariableInspector]. This ensures INT8 signed
     * values and UINT16/INT16 multi-byte values are read correctly.
     */
    fun start() {
        session.start()
        // Wire authoritative IR types from metadata (overrides heuristic inference)
        resolvedMetadata?.let { meta ->
            val typeMap = meta.variables.associate { it.name to it.type }
            session.overrideVariableTypes(typeMap)
        }
    }

    /**
     * Advances one frame with the given [buttons] held and returns a full [Observation].
     *
     * Button state is declarative: [buttons] is the complete set of buttons held this frame.
     * StepAgent computes the delta from the previous frame and dispatches hold/release events
     * automatically.
     *
     * @param buttons Set of buttons to hold during this frame. Empty set means no buttons held.
     * @return [Observation] with the full game state after the frame.
     */
    fun step(buttons: Set<Button> = emptySet()): Observation {
        // 1. Button delta — release buttons no longer held, press newly held
        val toRelease = heldButtons - buttons
        val toPress = buttons - heldButtons
        for (b in toRelease) session.executeInputScript(inputScript { release(b) })
        for (b in toPress) session.executeInputScript(inputScript { hold(b) })
        heldButtons.clear()
        heldButtons.addAll(buttons)

        // 2. Advance one frame
        session.runFrames(1)

        // 3. Build observation
        return buildObservation()
    }

    /**
     * Advances [n] frames with the given [buttons] held and returns the final [Observation].
     *
     * Equivalent to calling [step] n times but more efficient — only builds one observation.
     *
     * @param n Number of frames to advance. Must be positive.
     * @param buttons Set of buttons to hold during all frames.
     * @return [Observation] with the game state after the last frame.
     */
    fun stepN(n: Int, buttons: Set<Button> = emptySet()): Observation {
        require(n > 0) { "n must be positive, got $n" }

        // Apply button delta once
        val toRelease = heldButtons - buttons
        val toPress = buttons - heldButtons
        for (b in toRelease) session.executeInputScript(inputScript { release(b) })
        for (b in toPress) session.executeInputScript(inputScript { hold(b) })
        heldButtons.clear()
        heldButtons.addAll(buttons)

        // Advance all frames
        session.runFrames(n)

        // Build observation from final state
        return buildObservation()
    }

    /**
     * Captures the current LCD frame buffer as a 160×144 RGB pixel array.
     *
     * @return 23040-element IntArray where each element is packed as 0x00RRGGBB.
     */
    fun captureFrameBuffer(): IntArray = session.getFrameBuffer()

    /**
     * Captures the current LCD frame as a PNG screenshot with JSON metadata sidecar.
     *
     * @param label Human-readable label for the file name prefix.
     * @return The PNG [File] that was written.
     */
    fun captureScreenshot(label: String): File = session.captureScreenshot(label)

    /**
     * Advances frames until the LCD frame buffer stabilizes, then returns the stabilized buffer.
     *
     * Immediately after a scene transition the PPU/VRAM has not latched the new scene, so reading
     * the frame buffer (via [captureFrameBuffer] or [captureScreenshot]) yields a stale or blank
     * frame. [settle] eliminates that by stepping frames until two consecutive
     * [AgentDebugSession.getFrameBuffer] snapshots are pixel-identical (proof the PPU did not
     * update the frame buffer between them —
     * [io.github.gbkt.emulator.CoffeeGbEmulator.getFrameBuffer] returns a fresh copy on each call,
     * so content equality is a true stability signal).
     *
     * Contract:
     * - **Stability rule:** capture once **2 consecutive** frame buffers are pixel-identical.
     * - **Cap:** at most **30** frames (~0.5s) are advanced. On reaching the cap without two
     *   identical frames, the **last** frame is returned (best-effort) — [settle] **never throws**.
     * - **Held buttons preserved:** the settle loop advances frames via
     *   [AgentDebugSession.runFrames] (NOT [step]), so it never re-dispatches input. Whatever
     *   buttons the caller currently holds (set by the prior [step]/[stepN] call) remain physically
     *   pressed in the emulator throughout settling — a held-input scene settles to the real
     *   mid-hold pose the player sees, not an idle pose. [heldButtons] is neither read nor mutated.
     *
     * @return The stabilized 23040-element frame buffer (or the last frame on cap).
     */
    fun settle(): IntArray {
        var prev = session.getFrameBuffer()
        var consecutiveMatch = 0
        repeat(SETTLE_FRAME_CAP) {
            // Advance ONE frame without re-dispatching input — held buttons remain pressed in the
            // emulator's input state. Must NOT use step()/stepN(): they would release held buttons.
            session.runFrames(1)
            val current = session.getFrameBuffer()
            if (prev.contentEquals(current)) {
                consecutiveMatch++
                if (consecutiveMatch >= SETTLE_STABLE_FRAMES) return current
            } else {
                consecutiveMatch = 0
            }
            prev = current
        }
        // Cap reached without stabilizing — best-effort capture, never throws.
        return prev
    }

    /**
     * Settles the frame buffer (see [settle]) then captures a PNG screenshot with the JSON metadata
     * sidecar via the existing [captureScreenshot] path.
     *
     * Use this instead of [captureScreenshot] for any capture taken immediately after a scene
     * transition or other VRAM-mutating event, so the written PNG reflects the rendered frame
     * rather than a stale/blank one. The variable-snapshot JSON sidecar is preserved because this
     * delegates to [AgentDebugSession.captureScreenshot] (it does NOT hand-roll a PNG encode).
     *
     * Held-button state is preserved across the settle (see [settle]).
     *
     * @param label Human-readable label for the file name prefix.
     * @return The PNG [File] that was written.
     */
    fun captureScreenshotSettled(label: String): File {
        settle()
        return session.captureScreenshot(label)
    }

    /**
     * Reads a single DSL variable by name.
     *
     * @param name Variable name (without C underscore prefix).
     * @return Byte value (0–255), or null if the symbol is not loaded.
     */
    fun readVariable(name: String): Int? = session.readVariable(name)

    /**
     * Writes a byte value for a named DSL variable.
     *
     * @param name Variable name (without C underscore prefix).
     * @param value Byte value to write (0–255).
     * @return `true` if the symbol was found and written.
     */
    fun writeVariable(name: String, value: Int): Boolean = session.writeVariable(name, value)

    /**
     * Reads a raw byte from the emulator's address space (0x0000..0xFFFF).
     *
     * Useful for reading hardware I/O registers (e.g., LCDC at 0xFF40, STAT at 0xFF41, IE at
     * 0xFFFF) that are not exposed through the symbol table.
     *
     * @param address Hardware address in the range 0x0000–0xFFFF.
     * @return Byte value in range 0–255.
     */
    fun readMemory(address: Int): Int = session.getMemory().readByte(address)

    /**
     * Writes a single byte to the emulator's address space (0x0000..0xFFFF).
     *
     * Required for driving hardware index registers (e.g., BCPS at 0xFF68 / OCPS at 0xFF6A) before
     * reading their data-port counterparts (BCPD/OCPD) via [readMemory]. The value is masked to a
     * byte; higher bits are discarded.
     *
     * @param address Hardware address in the range 0x0000–0xFFFF.
     * @param value Byte value (only the low 8 bits are written).
     */
    fun writeMemory(address: Int, value: Int): Unit =
        session.getMemory().writeByte(address, value and 0xFF)

    /**
     * Steps up to [maxFrames] frames, returning the first [Observation] where [predicate] is true.
     *
     * Always advances at least one frame. If the predicate never matches, the final observation is
     * returned — the caller can inspect it to detect timeout.
     *
     * @param maxFrames Maximum number of frames to advance. Must be positive.
     * @param buttons Set of buttons to hold during the wait.
     * @param predicate Returns true when the desired condition is met.
     * @return The matching [Observation], or the final one if [maxFrames] is exhausted.
     */
    fun waitUntil(
        maxFrames: Int,
        buttons: Set<Button> = emptySet(),
        predicate: (Observation) -> Boolean,
    ): Observation {
        require(maxFrames > 0) { "maxFrames must be positive, got $maxFrames" }
        var obs = step(buttons)
        if (predicate(obs)) return obs
        repeat(maxFrames - 1) {
            obs = step(buttons)
            if (predicate(obs)) return obs
        }
        return obs
    }

    /**
     * Waits until the current scene matches [name], or until [maxFrames] is exhausted.
     *
     * @return The [Observation] where the scene matched, or the final observation on timeout.
     */
    fun waitForScene(name: String, maxFrames: Int): Observation =
        waitUntil(maxFrames) { it.scene == name }

    /**
     * Waits until the variable [name] equals [expected], or until [maxFrames] is exhausted.
     *
     * @return The [Observation] where the variable matched, or the final observation on timeout.
     */
    fun waitForVariable(name: String, expected: Int, maxFrames: Int): Observation =
        waitUntil(maxFrames) { it.variables[name] == expected }

    /**
     * Waits until [text] appears on screen (either tilemap layer), or until [maxFrames] is
     * exhausted.
     *
     * @return The [Observation] where the text was found, or the final observation on timeout.
     */
    fun waitUntilTextOnScreen(text: String, maxFrames: Int): Observation =
        waitUntil(maxFrames) { it.hasText(text) }

    /** Returns game metadata if available, or null. */
    fun describeGame(): GameMetadata? = resolvedMetadata

    /** Returns all known variable names from the sym file. */
    fun listVariables(): List<String> = session.readAllVariables().keys.sorted()

    /** Returns all known scene names, or empty if no metadata. */
    fun listScenes(): Set<String> = resolvedMetadata?.scenes?.sceneNames ?: emptySet()

    /** Returns all known actor names, or empty if no metadata. */
    fun listActors(): List<String> = resolvedMetadata?.actors?.map { it.name } ?: emptyList()

    /**
     * Saves emulator memory state (WRAM+OAM+HRAM) to a binary GBST file.
     *
     * @param file Destination file. Created or overwritten.
     */
    fun saveState(file: File) = session.saveState(file)

    /**
     * Restores emulator memory state from a binary GBST file created by [saveState].
     *
     * @param file Source file previously created by [saveState].
     */
    fun loadState(file: File) = session.loadState(file)

    override fun close() {
        session.close()
    }

    /**
     * Requests cooperative cancellation of an in-flight [step]/[stepN]/[waitUntil] call. Safe to
     * invoke from any thread without holding any lock. See `AgentDebugSession.requestCancellation`
     * for the mechanism.
     */
    fun requestCancellation() {
        session.requestCancellation()
    }

    /**
     * Re-reads tilemap text from VRAM with optional scroll-aware offsets.
     *
     * Unlike the pre-decoded text in [Observation], this performs a fresh VRAM read that can apply
     * SCX/SCY hardware register offsets when [scrollAware] is true.
     *
     * @param layer Which tilemap layer to read.
     * @param scrollAware When true and layer is BACKGROUND, apply SCX/SCY offsets.
     * @return List of 18 strings, each 20 characters wide.
     */
    fun readTextRows(
        layer: VramTextVerifier.TilemapLayer = VramTextVerifier.TilemapLayer.BACKGROUND,
        scrollAware: Boolean = false,
    ): List<String> {
        val decoder =
            when (layer) {
                VramTextVerifier.TilemapLayer.BACKGROUND -> resolvedMetadata?.bgDecoder()
                VramTextVerifier.TilemapLayer.WINDOW -> resolvedMetadata?.winDecoder()
            }
        return VramTextVerifier.readAllRows(session.getMemory(), layer, decoder, scrollAware)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    companion object {
        /**
         * Number of consecutive pixel-identical frame buffers that proves the PPU latched a new
         * frame. The [settle] contract (see KDoc) locks this at 2.
         */
        const val SETTLE_STABLE_FRAMES = 2

        /**
         * Maximum frames [settle] advances before giving up and returning the last frame
         * (best-effort). ~0.5s at 60fps. The [settle] contract (see KDoc) locks this at 30.
         */
        const val SETTLE_FRAME_CAP = 30
    }

    private fun buildObservation(): Observation {
        try {
            val memory = session.getMemory()
            val allLog = session.getDebugLog()
            val newEntries = allLog.drop(lastLogIndex)
            lastLogIndex = allLog.size

            val variables = session.readAllVariables()
            val sceneIndex = variables["current_scene"]
            val scene = sceneIndex?.let { resolvedMetadata?.scenes?.nameOf(it) ?: "scene_$it" }
            val visibleSprites = OamSpriteReader.readVisible(memory)

            val actors =
                if (resolvedMetadata != null) {
                    resolvedMetadata.actors.map { actorMeta ->
                        ActorState(
                            name = actorMeta.name,
                            x = variables[actorMeta.xVar],
                            y = variables[actorMeta.yVar],
                            sprites =
                                visibleSprites.filter {
                                    resolvedMetadata.actorForSlot(it.index) == actorMeta.name
                                },
                        )
                    }
                } else {
                    emptyList()
                }

            val isTerminal =
                scene != null &&
                    resolvedMetadata != null &&
                    scene in resolvedMetadata.terminalScenes

            val bgDec = resolvedMetadata?.bgDecoder()
            val winDec = resolvedMetadata?.winDecoder()
            return Observation(
                frame = session.frameCount,
                variables = variables,
                scene = scene,
                sprites = visibleSprites,
                actors = actors,
                bgText =
                    VramTextVerifier.readAllRows(
                        memory,
                        VramTextVerifier.TilemapLayer.BACKGROUND,
                        bgDec,
                    ),
                winText =
                    VramTextVerifier.readAllRows(
                        memory,
                        VramTextVerifier.TilemapLayer.WINDOW,
                        winDec,
                    ),
                newLogEntries = newEntries,
                isTerminal = isTerminal,
            )
        } catch (e: Exception) {
            throw EmulatorObservationException(
                "Failed to build observation at frame ${session.frameCount}: ${e.message}",
                e,
            )
        }
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

/**
 * Returns a compact human-readable summary of this observation, suitable for agent logs and
 * debugging output.
 *
 * Format:
 * ```
 * Frame 123 | Scene: gameplay
 * Vars: ballDx=1 ballDy=-1 lives=3 score=42
 * Actors: ball(80,72) paddle1(16,64)
 * Sprites: 5 visible
 * BG: [row 0] "   SCORE: 42       "
 * WIN: (empty)
 * Log: [00:02.341] (gameplay/frame) > Bounce
 * ```
 */
/** Returns true if [text] appears anywhere on screen (either tilemap layer). */
fun Observation.hasText(text: String): Boolean =
    bgText.any { text in it } || winText.any { text in it }

/** Returns true if an actor with [name] is present in this observation. */
fun Observation.hasActor(name: String): Boolean = actors.any { it.name == name }

fun Observation.toSummary(): String = buildString {
    append("Frame $frame")
    if (scene != null) append(" | Scene: $scene")
    appendLine()
    if (variables.isNotEmpty()) {
        append("Vars: ")
        appendLine(
            variables.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }
        )
    }
    if (actors.isNotEmpty()) {
        append("Actors: ")
        appendLine(actors.joinToString(" ") { a -> "${a.name}(${a.x ?: "?"},${a.y ?: "?"})" })
    }
    appendLine("Sprites: ${sprites.size} visible")
    // BG text
    val bgRows = nonEmptyRowsFormatted(bgText)
    if (bgRows.isEmpty()) appendLine("BG: (empty)") else bgRows.forEach { appendLine("BG: $it") }
    // WIN text
    val winRows = nonEmptyRowsFormatted(winText)
    if (winRows.isEmpty()) appendLine("WIN: (empty)")
    else winRows.forEach { appendLine("WIN: $it") }
    // Log
    if (newLogEntries.isNotEmpty()) {
        newLogEntries.forEach { entry -> appendLine("Log: ${entry.formatted().trimEnd()}") }
    }
}

/**
 * Returns the non-blank rows from a tilemap layer formatted as `[row N] "..."`, for use in
 * [toSummary]. A row is considered blank if it contains only spaces and dots (the tilemap-text
 * decoder's empty-tile representation).
 */
private fun nonEmptyRowsFormatted(rows: List<String>): List<String> =
    rows.mapIndexedNotNull { i, row ->
        if (row.any { it != '.' && it != ' ' }) "[row $i] \"$row\"" else null
    }
