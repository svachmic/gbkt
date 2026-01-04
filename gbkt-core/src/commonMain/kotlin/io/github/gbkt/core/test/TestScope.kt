/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.Button
import io.github.gbkt.core.ButtonState
import io.github.gbkt.core.DpadDirectionState

/** DSL marker to prevent scope leakage. */
@DslMarker annotation class TestDsl

/**
 * The test execution scope - where the magic happens.
 *
 * Provides a beautiful, fluent API for:
 * - Advancing frames
 * - Simulating input
 * - Making assertions
 *
 * ## Example
 *
 * ```kotlin
 * test {
 *     tap(buttons.a)
 *     advanceFrames(10)
 *     expect(playerX).toEqual(100)
 * }
 * ```
 */
@TestDsl
class TestScope(val simulation: SimulationContext, val input: MockInputProvider) {

    // =========================================================================
    // FRAME CONTROL
    // =========================================================================

    /** Advance the simulation by one frame. */
    fun advanceFrame() {
        simulation.joypadPrev = simulation.joypad
        simulation.joypad = input.joypad
        simulation.executeFrame()
    }

    /** Advance the simulation by multiple frames. */
    fun advanceFrames(count: Int) {
        repeat(count) { advanceFrame() }
    }

    /** Advance simulation by approximate seconds (60 FPS). */
    fun advanceSeconds(seconds: Float) {
        advanceFrames((seconds * 60).toInt())
    }

    /** Step one frame and execute assertions. */
    inline fun stepFrame(block: TestScope.() -> Unit = {}) {
        advanceFrame()
        block()
    }

    /**
     * Advance until a condition is met, with a safety limit.
     *
     * @param maxFrames Maximum frames to advance (default 600 = 10 seconds)
     * @param failOnTimeout If true (default), throws AssertionError when timeout is reached
     * @param predicate Condition to wait for
     * @return AdvanceResult indicating success and frame count
     */
    inline fun advanceUntil(
        maxFrames: Int = 600,
        failOnTimeout: Boolean = true,
        predicate: () -> Boolean
    ): AdvanceResult {
        var frames = 0
        while (!predicate() && frames < maxFrames) {
            advanceFrame()
            frames++
        }
        val result = AdvanceResult(frames, predicate())
        if (failOnTimeout && !result.conditionMet) {
            throw AssertionError(
                "advanceUntil timed out after $maxFrames frames without condition being met"
            )
        }
        return result
    }

    /** Advance while a condition is true, with a safety limit. */
    inline fun advanceWhile(
        maxFrames: Int = 600,
        failOnTimeout: Boolean = true,
        predicate: () -> Boolean
    ): AdvanceResult {
        return advanceUntil(maxFrames, failOnTimeout) { !predicate() }
    }

    // =========================================================================
    // INPUT CONTROL
    // =========================================================================

    /** Tap a button (press for one frame, then release). */
    fun tap(button: Button) {
        input.press(button)
        advanceFrame()
        input.release(button)
    }

    /** Tap a button via ButtonState (press for one frame, then release). */
    fun tap(button: ButtonState) {
        tap(Button.entries.first { it.mask == button.buttonMask })
    }

    /** Tap a d-pad direction (press for one frame, then release). */
    fun tap(direction: DpadDirectionState) {
        tap(Button.entries.first { it.mask == direction.buttonMask })
    }

    /** Tap multiple buttons simultaneously. */
    fun tap(vararg buttons: Button) {
        input.press(*buttons)
        advanceFrame()
        input.release(*buttons)
    }

    /**
     * Hold a button while executing a block, then release.
     *
     * ## Example
     *
     * ```kotlin
     * press(dpad.right) {
     *     advanceFrames(30)
     *     expect(playerX).toBeGreaterThan(80)
     * }
     * ```
     */
    inline fun press(button: Button, block: TestScope.() -> Unit) {
        input.press(button)
        try {
            block()
        } finally {
            input.release(button)
        }
    }

    /** Hold a ButtonState while executing a block, then release. */
    inline fun press(button: ButtonState, block: TestScope.() -> Unit) {
        press(Button.entries.first { it.mask == button.buttonMask }, block)
    }

    /** Hold a d-pad direction while executing a block, then release. */
    inline fun press(direction: DpadDirectionState, block: TestScope.() -> Unit) {
        press(Button.entries.first { it.mask == direction.buttonMask }, block)
    }

    /** Hold multiple buttons while executing a block. */
    inline fun press(vararg buttons: Button, block: TestScope.() -> Unit) {
        input.press(*buttons)
        try {
            block()
        } finally {
            input.release(*buttons)
        }
    }

    /** Press and hold a button (without auto-release). */
    fun hold(button: Button) {
        input.press(button)
    }

    /** Press and hold a ButtonState (without auto-release). */
    fun hold(button: ButtonState) {
        hold(Button.entries.first { it.mask == button.buttonMask })
    }

    /** Press and hold a d-pad direction (without auto-release). */
    fun hold(direction: DpadDirectionState) {
        hold(Button.entries.first { it.mask == direction.buttonMask })
    }

    /** Press and hold multiple buttons. */
    fun hold(vararg buttons: Button) {
        input.press(*buttons)
    }

    /** Release a held button. */
    fun release(button: Button) {
        input.release(button)
    }

    /** Release a held ButtonState. */
    fun release(button: ButtonState) {
        release(Button.entries.first { it.mask == button.buttonMask })
    }

    /** Release a held d-pad direction. */
    fun release(direction: DpadDirectionState) {
        release(Button.entries.first { it.mask == direction.buttonMask })
    }

    /** Release multiple buttons. */
    fun release(vararg buttons: Button) {
        input.release(*buttons)
    }

    /** Release all buttons. */
    fun releaseAll() {
        input.releaseAll()
    }

    // =========================================================================
    // SCENE CONTROL
    // =========================================================================

    /** Directly enter a scene (for test setup). */
    fun enterScene(sceneName: String) {
        simulation.enterScene(sceneName)
    }

    /** Listen for scene changes during test. */
    fun onSceneChange(callback: (from: String, to: String) -> Unit) {
        simulation.onSceneChange = callback
    }

    // =========================================================================
    // STATE ACCESS
    // =========================================================================

    /** Get the current value of a variable. */
    fun getVariable(name: String): Int = simulation.getVariable(name).toInt()

    /** Set a variable directly (for test setup). */
    fun setVariable(name: String, value: Int) {
        simulation.setVariable(name, value)
    }

    /** Current frame count. */
    val frameCount: Int
        get() = simulation.frameCount

    /** Current scene name. */
    val currentScene: String
        get() = simulation.currentScene

    // =========================================================================
    // ASSERTIONS - The beautiful fluent API
    // =========================================================================

    /**
     * Start an expectation chain for a variable value.
     *
     * ## Example
     *
     * ```kotlin
     * expect(playerX).toEqual(100)
     * expect(score).toBeGreaterThan(0)
     * ```
     */
    fun expect(variableName: String): IntExpectation {
        return IntExpectation(
            actual = simulation.getVariable(variableName).toInt(),
            name = variableName
        )
    }

    /** Start an expectation for a SimValue. */
    fun expect(value: SimValue): IntExpectation {
        return IntExpectation(actual = value.toInt(), name = "value")
    }

    /** Start an expectation for an integer value. */
    fun expect(value: Int): IntExpectation {
        return IntExpectation(actual = value, name = "value")
    }

    /** Start an expectation chain for a sprite. */
    fun expect(sprite: SimSprite?): SpriteExpectation {
        return SpriteExpectation(sprite)
    }

    /** Start an expectation for a sprite by name. */
    fun expectSprite(name: String): SpriteExpectation {
        return SpriteExpectation(simulation.getSprite(name))
    }

    /** Start an expectation for a pool. */
    fun expect(pool: SimPool?): PoolExpectation {
        return PoolExpectation(pool)
    }

    /** Start an expectation for a pool by name. */
    fun expectPool(name: String): PoolExpectation {
        return PoolExpectation(simulation.getPool(name))
    }

    /** Expect the game to be in a specific scene. */
    fun expectScene(sceneName: String) {
        if (simulation.currentScene != sceneName) {
            throw AssertionError(
                "Expected to be in scene '$sceneName', but was in '${simulation.currentScene}'"
            )
        }
    }

    /** Access the game simulation for advanced assertions. */
    val game: GameExpectation
        get() = GameExpectation(simulation)
}

/** Result of an advanceUntil/advanceWhile operation. */
class AdvanceResult(val frames: Int, val conditionMet: Boolean) {
    /**
     * Fail the test if the condition was not met.
     *
     * ## Example
     *
     * ```kotlin
     * advanceUntil { playerY >= groundY } orFail "Player never landed"
     * ```
     */
    infix fun orFail(message: String) {
        if (!conditionMet) {
            throw AssertionError("$message (waited $frames frames)")
        }
    }

    /** Assert the condition was met. */
    fun assertMet(message: String = "Condition was not met") {
        orFail(message)
    }
}
