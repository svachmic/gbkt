/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.GameIR

// =============================================================================
// BUTTON AND DIRECTION ENUMS
// =============================================================================

/**
 * Game Boy button identifiers for input simulation.
 *
 * Bitmasks match GBDK joypad constants.
 */
enum class GameBoyButton(val mask: Int) {
    A(0x01),
    B(0x02),
    SELECT(0x04),
    START(0x08),
}

/**
 * D-pad direction identifiers for input simulation.
 *
 * Bitmasks match GBDK joypad constants.
 */
enum class DpadDirection(val mask: Int) {
    RIGHT(0x10),
    LEFT(0x20),
    UP(0x40),
    DOWN(0x80),
}

// =============================================================================
// SIMULATION CONTEXT V2
// =============================================================================

/**
 * Public test API for running v2 game scripts on the JVM without an emulator.
 *
 * Wraps [ScriptOpInterpreter] and exposes a clean, ergonomic API for:
 * - Frame advancing: [advanceFrames], [runUntil]
 * - Input simulation: [press], [release], [tap], [holdDpad]
 * - State inspection: [getVar], [setVar], [assertVar], [currentScene], [frameCount]
 * - Scene control: [enterScene]
 * - Debug tracing: [enableTracing], [getTraceLog]
 *
 * Usage:
 * ```kotlin
 * val sim = SimulationContextV2(myGame)
 * sim.setVar("ball.x", 80)
 * sim.advanceFrames(60)
 * sim.assertVar("score", 10)
 * ```
 */
class SimulationContextV2(game: GameIR) {

    /** The underlying execution engine. Exposed as `internal` for test access. */
    internal val interpreter = ScriptOpInterpreter(game)

    // =========================================================================
    // Frame advancing
    // =========================================================================

    /**
     * Execute [count] frames sequentially.
     *
     * Each frame runs the [io.github.gbkt.core.ir.SceneIR.frameOps] of the current scene in order.
     */
    fun advanceFrames(count: Int) {
        repeat(count) { interpreter.executeFrame() }
    }

    /**
     * Advance frames until [predicate] returns true or [maxFrames] is reached.
     *
     * The predicate receives `this` [SimulationContextV2] as the receiver, so test code can call
     * `getVar("score") >= 100` directly.
     *
     * @param maxFrames Maximum frames before throwing. Default 600 (10 seconds at 60fps).
     * @param predicate Condition evaluated after each frame. Stops if it returns true.
     * @throws IllegalStateException if [maxFrames] is reached without the predicate becoming true.
     */
    fun runUntil(maxFrames: Int = 600, predicate: SimulationContextV2.() -> Boolean) {
        var framesRun = 0
        do {
            if (framesRun >= maxFrames) {
                error(
                    "runUntil: condition not met after $maxFrames frames " +
                        "(frameCount=${interpreter.frameCount})"
                )
            }
            interpreter.executeFrame()
            framesRun++
        } while (!predicate())
    }

    // =========================================================================
    // Input simulation
    // =========================================================================

    /**
     * Set a joypad bit (low-level input control).
     *
     * Bits match GBDK: A=0x01, B=0x02, SELECT=0x04, START=0x08, RIGHT=0x10, LEFT=0x20, UP=0x40,
     * DOWN=0x80.
     */
    fun press(button: Int) {
        interpreter.joypad = interpreter.joypad or button
    }

    /** Clear a joypad bit (low-level input control). */
    fun release(button: Int) {
        interpreter.joypad = interpreter.joypad and button.inv()
    }

    /**
     * Press a button for one frame then release it.
     *
     * Sets the joypad bit, executes one frame so scripts can read it, then clears the bit. Use this
     * for edge-triggered (pressed) input.
     */
    fun tap(button: GameBoyButton) {
        press(button.mask)
        interpreter.executeFrame()
        release(button.mask)
    }

    /**
     * Hold a D-pad direction for [frames] frames.
     *
     * Sets the joypad bit, executes [frames] frames so scripts can read it each frame, then clears
     * the bit.
     */
    fun holdDpad(direction: DpadDirection, frames: Int) {
        press(direction.mask)
        repeat(frames) { interpreter.executeFrame() }
        release(direction.mask)
    }

    // =========================================================================
    // State inspection
    // =========================================================================

    /**
     * Read a game variable value.
     *
     * Returns 0 for variables that have not been set.
     */
    fun getVar(name: String): Int = interpreter.getVariable(name).toInt()

    /**
     * Directly set a game variable value (for test setup).
     *
     * This bypasses normal game logic — use for initializing state before tests.
     */
    fun setVar(name: String, value: Int) {
        interpreter.setVariable(name, value.toLong())
    }

    /**
     * Assert that a game variable has the [expected] value.
     *
     * @throws AssertionError with a descriptive message if the assertion fails. Message format:
     *   "assertVar failed: '$name' expected=$expected actual=$actual"
     */
    fun assertVar(name: String, expected: Int) {
        val actual = getVar(name)
        if (actual != expected) {
            throw AssertionError("assertVar failed: '$name' expected=$expected actual=$actual")
        }
    }

    /** The ID of the currently active scene. */
    val currentScene: String
        get() = interpreter.currentSceneId

    /** Total number of frames executed via [advanceFrames] or [tap]/[holdDpad]. */
    val frameCount: Int
        get() = interpreter.frameCount

    // =========================================================================
    // Scene control
    // =========================================================================

    /**
     * Transition to [sceneId] directly (for test setup).
     *
     * Runs exit ops of the current scene, then enter ops of the new scene. Use this to jump to a
     * specific scene in a test without running all preceding scenes.
     */
    fun enterScene(sceneId: String) {
        interpreter.enterScene(sceneId)
    }

    // =========================================================================
    // Tracing
    // =========================================================================

    /**
     * Enable frame trace logging on the interpreter.
     *
     * Once enabled, state changes (variable assignments, scene transitions) are logged with their
     * frame number. Retrieve entries via [getTraceLog].
     */
    fun enableTracing() {
        interpreter.tracingEnabled = true
    }

    /**
     * Return the current trace log entries.
     *
     * Entries are formatted as `"frame=$n: $event"`. Empty if tracing has not been enabled or no
     * state changes have occurred.
     */
    fun getTraceLog(): List<String> = interpreter.traceLog.toList()
}
