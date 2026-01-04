/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.Button

/**
 * Mock input provider for testing. Simulates Game Boy joypad state without actual hardware.
 *
 * ## Usage
 *
 * ```kotlin
 * val input = MockInputProvider()
 * input.press(Button.A, Button.RIGHT)
 * simulation.joypad = input.joypad
 * simulation.executeFrame()
 * input.release(Button.A)
 * ```
 */
class MockInputProvider {
    private var _joypad: Int = 0

    /** Current joypad state as a bitmask. */
    val joypad: Int
        get() = _joypad

    /** Press one or more buttons (add to current state). */
    fun press(vararg buttons: Button) {
        for (button in buttons) {
            _joypad = _joypad or button.mask
        }
    }

    /** Release one or more buttons (remove from current state). */
    fun release(vararg buttons: Button) {
        for (button in buttons) {
            _joypad = _joypad and button.mask.inv()
        }
    }

    /** Release all buttons. */
    fun releaseAll() {
        _joypad = 0
    }

    /** Set the exact joypad state (overwrite). */
    fun set(mask: Int) {
        _joypad = mask
    }

    /** Check if a specific button is currently pressed. */
    fun isPressed(button: Button): Boolean = (_joypad and button.mask) != 0

    /** Check if all specified buttons are currently pressed. */
    fun areAllPressed(vararg buttons: Button): Boolean = buttons.all { isPressed(it) }

    /** Check if any of the specified buttons is currently pressed. */
    fun isAnyPressed(vararg buttons: Button): Boolean = buttons.any { isPressed(it) }

    override fun toString(): String {
        val pressed = Button.entries.filter { isPressed(it) }
        return if (pressed.isEmpty()) "MockInput(none)"
        else "MockInput(${pressed.joinToString(", ")})"
    }
}

/** Extension to combine buttons into a bitmask. */
operator fun Button.plus(other: Button): Int = this.mask or other.mask

operator fun Int.plus(button: Button): Int = this or button.mask

/** Create a button combination for cleaner syntax. */
fun buttons(vararg buttons: Button): Int = buttons.fold(0) { acc, b -> acc or b.mask }
