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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [InputHandler].
 *
 * Tests the key-to-button mapping table directly via [InputHandler.mapKey] (no real Swing
 * [KeyEvent] objects required) and verifies that keyPressed/keyReleased post the correct events to
 * the [EventBus].
 */
class InputHandlerTest {

    // ── Recorded events ───────────────────────────────────────────────────────

    private val pressedButtons = mutableListOf<Button>()
    private val releasedButtons = mutableListOf<Button>()

    /** Minimal EventBus stub that records posted events. */
    private val recordingBus =
        object : EventBus {
            override fun <E : eu.rekawek.coffeegb.core.events.Event> register(
                subscriber: eu.rekawek.coffeegb.core.events.Subscriber<E>,
                eventClass: Class<E>,
                name: String,
            ) = Unit

            override fun <E : eu.rekawek.coffeegb.core.events.Event> register(
                subscriber: eu.rekawek.coffeegb.core.events.Subscriber<E>,
                eventClass: Class<E>,
            ) = Unit

            override fun <E : eu.rekawek.coffeegb.core.events.Event> post(event: E) {
                when (event) {
                    is ButtonPressEvent -> pressedButtons += event.button()
                    is ButtonReleaseEvent -> releasedButtons += event.button()
                }
            }

            override fun <E : eu.rekawek.coffeegb.core.events.Event> postAsync(event: E) =
                post(event)

            override fun fork(name: String): EventBus = this

            override fun close() = Unit
        }

    private lateinit var handler: InputHandler

    @BeforeEach
    fun setup() {
        pressedButtons.clear()
        releasedButtons.clear()
        handler = InputHandler(recordingBus)
    }

    // ── mapKey() — keyboard mapping table ────────────────────────────────────

    @Test fun `arrow up maps to UP`() = assertMapping(KeyEvent.VK_UP, Button.UP)

    @Test fun `arrow down maps to DOWN`() = assertMapping(KeyEvent.VK_DOWN, Button.DOWN)

    @Test fun `arrow left maps to LEFT`() = assertMapping(KeyEvent.VK_LEFT, Button.LEFT)

    @Test fun `arrow right maps to RIGHT`() = assertMapping(KeyEvent.VK_RIGHT, Button.RIGHT)

    @Test fun `Z key maps to A button`() = assertMapping(KeyEvent.VK_Z, Button.A)

    @Test fun `X key maps to B button`() = assertMapping(KeyEvent.VK_X, Button.B)

    @Test fun `Enter maps to START`() = assertMapping(KeyEvent.VK_ENTER, Button.START)

    @Test fun `Backspace maps to SELECT`() = assertMapping(KeyEvent.VK_BACK_SPACE, Button.SELECT)

    @Test
    fun `unmapped key Q returns null`() {
        assertNull(handler.mapKey(KeyEvent.VK_Q))
    }

    @Test
    fun `unmapped key Escape returns null`() {
        assertNull(handler.mapKey(KeyEvent.VK_ESCAPE))
    }

    @Test
    fun `unmapped key Space returns null`() {
        assertNull(handler.mapKey(KeyEvent.VK_SPACE))
    }

    // ── Event posting via keyPressed/keyReleased ──────────────────────────────

    @Test
    fun `keyPressed posts ButtonPressEvent for each mapped key`() {
        val mappings =
            mapOf(
                KeyEvent.VK_UP to Button.UP,
                KeyEvent.VK_DOWN to Button.DOWN,
                KeyEvent.VK_LEFT to Button.LEFT,
                KeyEvent.VK_RIGHT to Button.RIGHT,
                KeyEvent.VK_Z to Button.A,
                KeyEvent.VK_X to Button.B,
                KeyEvent.VK_ENTER to Button.START,
                KeyEvent.VK_BACK_SPACE to Button.SELECT,
            )
        for ((keyCode, expectedButton) in mappings) {
            pressedButtons.clear()
            handler.keyPressed(makeKeyEvent(keyCode))
            assertEquals(
                listOf(expectedButton),
                pressedButtons,
                "keyPressed($keyCode) should post ButtonPressEvent($expectedButton)",
            )
        }
    }

    @Test
    fun `keyReleased posts ButtonReleaseEvent for each mapped key`() {
        val mappings =
            mapOf(
                KeyEvent.VK_UP to Button.UP,
                KeyEvent.VK_DOWN to Button.DOWN,
                KeyEvent.VK_LEFT to Button.LEFT,
                KeyEvent.VK_RIGHT to Button.RIGHT,
                KeyEvent.VK_Z to Button.A,
                KeyEvent.VK_X to Button.B,
                KeyEvent.VK_ENTER to Button.START,
                KeyEvent.VK_BACK_SPACE to Button.SELECT,
            )
        for ((keyCode, expectedButton) in mappings) {
            releasedButtons.clear()
            handler.keyReleased(makeKeyEvent(keyCode))
            assertEquals(
                listOf(expectedButton),
                releasedButtons,
                "keyReleased($keyCode) should post ButtonReleaseEvent($expectedButton)",
            )
        }
    }

    @Test
    fun `keyPressed with unmapped key posts no event`() {
        handler.keyPressed(makeKeyEvent(KeyEvent.VK_Q))
        assertEquals(emptyList<Button>(), pressedButtons)
    }

    @Test
    fun `keyReleased with unmapped key posts no event`() {
        handler.keyReleased(makeKeyEvent(KeyEvent.VK_Q))
        assertEquals(emptyList<Button>(), releasedButtons)
    }

    @Test
    fun `rapid key presses accumulate in order`() {
        handler.keyPressed(makeKeyEvent(KeyEvent.VK_Z)) // A
        handler.keyPressed(makeKeyEvent(KeyEvent.VK_X)) // B
        handler.keyPressed(makeKeyEvent(KeyEvent.VK_UP)) // UP
        assertEquals(listOf(Button.A, Button.B, Button.UP), pressedButtons)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertMapping(keyCode: Int, expected: Button) {
        assertEquals(expected, handler.mapKey(keyCode), "mapKey($keyCode) should be $expected")
    }

    /**
     * Constructs a minimal [KeyEvent] with the given [keyCode]. Uses [KeyEvent.KEY_PRESSED] type —
     * the handler only reads [KeyEvent.keyCode].
     */
    private fun makeKeyEvent(keyCode: Int): KeyEvent =
        KeyEvent(
            /* source    = */ java.awt.Component::class.java.getDeclaredConstructor().run {
                // We need a Component instance for KeyEvent source. Use a JPanel stub.
                javax.swing.JPanel()
            },
            /* id        = */ KeyEvent.KEY_PRESSED,
            /* when      = */ System.currentTimeMillis(),
            /* modifiers = */ 0,
            /* keyCode   = */ keyCode,
            /* keyChar   = */ KeyEvent.CHAR_UNDEFINED,
        )
}
