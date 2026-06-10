/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import eu.rekawek.coffeegb.core.events.EventBus
import eu.rekawek.coffeegb.core.joypad.Button
import eu.rekawek.coffeegb.core.joypad.ButtonPressEvent
import eu.rekawek.coffeegb.core.joypad.ButtonReleaseEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * [KeyListener] that translates keyboard events to Coffee-GB joypad button events.
 *
 * Posts [ButtonPressEvent] and [ButtonReleaseEvent] to the emulator's [EventBus] so the Coffee-GB
 * [eu.rekawek.coffeegb.core.joypad.Joypad] can update its internal state and trigger Game Boy
 * interrupt $60 (Joypad).
 *
 * Keyboard mapping (from CONTEXT.md specification):
 *
 * | Key         | Game Boy Button |
 * |-------------|-----------------|
 * | Arrow Up    | D-pad UP        |
 * | Arrow Down  | D-pad DOWN      |
 * | Arrow Left  | D-pad LEFT      |
 * | Arrow Right | D-pad RIGHT     |
 * | Z           | A               |
 * | X           | B               |
 * | Enter       | Start           |
 * | Backspace   | Select          |
 *
 * All other keys are silently ignored.
 *
 * Usage:
 * ```kotlin
 * val eventBus = EventBusImpl()
 * val inputHandler = InputHandler(eventBus)
 * frame.addKeyListener(inputHandler)
 * gameboy.init(eventBus, ...)
 * ```
 *
 * @param eventBus The [EventBus] shared with the [eu.rekawek.coffeegb.core.Gameboy] instance.
 *   Button press/release events are posted synchronously on the EDT.
 */
class InputHandler(private val eventBus: EventBus) : KeyListener {

    /**
     * Maps a [KeyEvent] key code to a Coffee-GB [Button], or returns `null` if the key has no Game
     * Boy equivalent.
     *
     * This function is `internal` so that [InputHandlerTest] can call it directly without
     * constructing real [KeyEvent] objects or a live Swing component.
     */
    internal fun mapKey(keyCode: Int): Button? =
        when (keyCode) {
            KeyEvent.VK_UP -> Button.UP
            KeyEvent.VK_DOWN -> Button.DOWN
            KeyEvent.VK_LEFT -> Button.LEFT
            KeyEvent.VK_RIGHT -> Button.RIGHT
            KeyEvent.VK_Z -> Button.A
            KeyEvent.VK_X -> Button.B
            KeyEvent.VK_ENTER -> Button.START
            KeyEvent.VK_BACK_SPACE -> Button.SELECT
            else -> null
        }

    /**
     * Posts a [ButtonPressEvent] to the emulator's event bus when a mapped key is pressed. Unmapped
     * keys are silently ignored.
     */
    override fun keyPressed(e: KeyEvent) {
        val button = mapKey(e.keyCode) ?: return
        eventBus.post(ButtonPressEvent(button))
    }

    /**
     * Posts a [ButtonReleaseEvent] to the emulator's event bus when a mapped key is released.
     * Unmapped keys are silently ignored.
     */
    override fun keyReleased(e: KeyEvent) {
        val button = mapKey(e.keyCode) ?: return
        eventBus.post(ButtonReleaseEvent(button))
    }

    /** Not used — key-typed events do not carry a reliable keyCode for mapping. */
    override fun keyTyped(e: KeyEvent) {
        // Intentionally empty — we use keyPressed/keyReleased for reliable keyCode mapping
    }
}
