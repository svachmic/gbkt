/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import eu.rekawek.coffeegb.core.events.EventBus
import eu.rekawek.coffeegb.core.events.Event
import eu.rekawek.coffeegb.core.events.Subscriber
import eu.rekawek.coffeegb.core.joypad.ButtonPressEvent
import eu.rekawek.coffeegb.core.joypad.ButtonReleaseEvent
import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [InputScriptPlayer].
 *
 * Uses a stub [GbEmulator] to count [stepFrame] calls and a recording [EventBus]
 * to verify the correct sequence of [ButtonPressEvent] / [ButtonReleaseEvent].
 */
class InputScriptPlayerTest {

    // ── Recorded events ───────────────────────────────────────────────────────

    private val postedEvents = mutableListOf<Event>()
    private var stepFrameCount = 0

    /** Minimal EventBus stub that records posted events. */
    private val recordingBus =
        object : EventBus {
            override fun <E : Event> register(
                subscriber: Subscriber<E>,
                eventClass: Class<E>,
                name: String,
            ) = Unit

            override fun <E : Event> register(
                subscriber: Subscriber<E>,
                eventClass: Class<E>,
            ) = Unit

            override fun <E : Event> post(event: E) {
                postedEvents += event
            }

            override fun <E : Event> postAsync(event: E) = post(event)

            override fun fork(name: String): EventBus = this

            override fun close() = Unit
        }

    /** Minimal GbEmulator stub that tracks stepFrame invocations. Starts paused. */
    private val pausedEmulator =
        object : GbEmulator {
            override fun start() = Unit
            override fun stop() = Unit
            override fun pause() = Unit
            override fun resume() = Unit
            override fun stepFrame() { stepFrameCount++ }
            override fun setSpeed(multiplier: Float) = Unit
            override fun getFrameBuffer(): IntArray = IntArray(160 * 144)
            override fun getMemory(): MemoryAccess = error("not used")
            override fun getDebugLog(): List<DebugLogEntry> = emptyList()
            override fun isRunning(): Boolean = true
            override fun isPaused(): Boolean = true
            override val isHeadless: Boolean = true
        }

    /** Stub emulator that reports itself as NOT paused. */
    private val runningEmulator =
        object : GbEmulator by pausedEmulator {
            override fun isPaused(): Boolean = false
        }

    private lateinit var player: InputScriptPlayer

    @BeforeEach
    fun setup() {
        postedEvents.clear()
        stepFrameCount = 0
        player = InputScriptPlayer(pausedEmulator, recordingBus)
    }

    // ── Precondition check ────────────────────────────────────────────────────

    @Test
    fun `play throws ISE when emulator is not paused`() {
        val notPausedPlayer = InputScriptPlayer(runningEmulator, recordingBus)
        assertThrows(IllegalStateException::class.java) {
            notPausedPlayer.play(inputScript { press(Button.A) })
        }
    }

    // ── Press step ────────────────────────────────────────────────────────────

    @Test
    fun `Press dispatches ButtonPressEvent, steps N frames, then ButtonReleaseEvent`() {
        player.play(inputScript { press(Button.A, frames = 3) })
        assertEquals(3, stepFrameCount)
        assertEquals(2, postedEvents.size)
        val coffeeButton = eu.rekawek.coffeegb.core.joypad.Button.A
        assertEquals(coffeeButton, (postedEvents[0] as ButtonPressEvent).button())
        assertEquals(coffeeButton, (postedEvents[1] as ButtonReleaseEvent).button())
    }

    @Test
    fun `Press with 1 frame steps once between press and release`() {
        player.play(inputScript { press(Button.B) })
        assertEquals(1, stepFrameCount)
        assertEquals(2, postedEvents.size)
        assert(postedEvents[0] is ButtonPressEvent)
        assert(postedEvents[1] is ButtonReleaseEvent)
    }

    // ── Hold step ─────────────────────────────────────────────────────────────

    @Test
    fun `Hold dispatches only ButtonPressEvent with no frame advance`() {
        player.play(inputScript { hold(Button.LEFT) })
        assertEquals(0, stepFrameCount)
        assertEquals(1, postedEvents.size)
        assert(postedEvents[0] is ButtonPressEvent)
        assertEquals(
            eu.rekawek.coffeegb.core.joypad.Button.LEFT,
            (postedEvents[0] as ButtonPressEvent).button(),
        )
    }

    // ── Release step ──────────────────────────────────────────────────────────

    @Test
    fun `Release dispatches only ButtonReleaseEvent with no frame advance`() {
        player.play(inputScript { release(Button.RIGHT) })
        assertEquals(0, stepFrameCount)
        assertEquals(1, postedEvents.size)
        assert(postedEvents[0] is ButtonReleaseEvent)
        assertEquals(
            eu.rekawek.coffeegb.core.joypad.Button.RIGHT,
            (postedEvents[0] as ButtonReleaseEvent).button(),
        )
    }

    // ── Wait step ─────────────────────────────────────────────────────────────

    @Test
    fun `Wait advances N frames with no events`() {
        player.play(inputScript { wait(10) })
        assertEquals(10, stepFrameCount)
        assertEquals(0, postedEvents.size)
    }

    // ── Combined script ───────────────────────────────────────────────────────

    @Test
    fun `complex script fires events and frames in correct order`() {
        player.play(
            inputScript {
                hold(Button.RIGHT)
                wait(30)
                press(Button.A)
                release(Button.RIGHT)
            }
        )
        // hold: 1 event, 0 frames
        // wait(30): 0 events, 30 frames
        // press(A,1): 2 events, 1 frame
        // release(RIGHT): 1 event, 0 frames
        assertEquals(31, stepFrameCount)
        assertEquals(4, postedEvents.size)
        assert(postedEvents[0] is ButtonPressEvent)  // hold RIGHT
        assert(postedEvents[1] is ButtonPressEvent)  // press A start
        assert(postedEvents[2] is ButtonReleaseEvent) // press A end
        assert(postedEvents[3] is ButtonReleaseEvent) // release RIGHT
    }

    // ── Button mapping ────────────────────────────────────────────────────────

    @Test
    fun `all Button enum values map to Coffee-GB Button correctly`() {
        val mappings = listOf(
            Button.UP to eu.rekawek.coffeegb.core.joypad.Button.UP,
            Button.DOWN to eu.rekawek.coffeegb.core.joypad.Button.DOWN,
            Button.LEFT to eu.rekawek.coffeegb.core.joypad.Button.LEFT,
            Button.RIGHT to eu.rekawek.coffeegb.core.joypad.Button.RIGHT,
            Button.A to eu.rekawek.coffeegb.core.joypad.Button.A,
            Button.B to eu.rekawek.coffeegb.core.joypad.Button.B,
            Button.START to eu.rekawek.coffeegb.core.joypad.Button.START,
            Button.SELECT to eu.rekawek.coffeegb.core.joypad.Button.SELECT,
        )
        for ((agentButton, coffeeButton) in mappings) {
            postedEvents.clear()
            player.play(inputScript { press(agentButton) })
            assertEquals(
                coffeeButton,
                (postedEvents[0] as ButtonPressEvent).button(),
                "Button.$agentButton should map to Coffee-GB Button.$coffeeButton",
            )
        }
    }
}
