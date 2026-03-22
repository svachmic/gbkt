/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.input

/** Physical buttons on the Game Boy. */
enum class Button {
    /** The A action button. */
    A,

    /** The B action button. */
    B,

    /** The Start button. */
    START,

    /** The Select button. */
    SELECT,
}

/** D-pad directional inputs on the Game Boy. */
enum class DpadDirection {
    /** D-pad up. */
    UP,

    /** D-pad down. */
    DOWN,

    /** D-pad left. */
    LEFT,

    /** D-pad right. */
    RIGHT,
}

/**
 * Runtime input state query interface.
 *
 * Provides per-button held and pressed (edge-triggered) queries for both face buttons and the
 * D-pad. Implementations are provided by the engine runtime and backed by the GBDK joypad state.
 */
interface InputState {
    /** Returns `true` while [button] is continuously held down. */
    fun isHeld(button: Button): Boolean

    /**
     * Returns `true` on the first frame [button] transitions from not-held to held. Subsequent
     * frames return `false` until the button is released and re-pressed.
     */
    fun isPressed(button: Button): Boolean

    /** Returns `true` while the D-pad is held in [direction]. */
    fun isDpadHeld(direction: DpadDirection): Boolean

    /** Returns `true` on the first frame the D-pad registers [direction]. */
    fun isDpadPressed(direction: DpadDirection): Boolean
}
