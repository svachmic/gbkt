/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import io.github.gbkt.emulator.debug.DebugLogEntry

/**
 * Public API for controlling an embedded Game Boy emulator instance.
 *
 * All emulator control surfaces (headless debug, UI display, step-through debugging) are accessed
 * through this interface. Downstream plans implement this via `CoffeeGbEmulator` (wrapping
 * Coffee-GB) and add debug interception on top.
 *
 * Frame buffer format: 160*144 = 23040 RGB pixels (0xRRGGBB), row-major.
 */
interface GbEmulator {

    /** Starts the emulator. If already running, this is a no-op. */
    fun start()

    /** Stops the emulator and releases resources. */
    fun stop()

    /** Pauses emulation without releasing resources. */
    fun pause()

    /** Resumes a paused emulator. */
    fun resume()

    /**
     * Advances emulation by exactly one frame (70224 CPU cycles at 4.194 MHz). Useful for
     * deterministic step-through debugging.
     */
    fun stepFrame()

    /**
     * Sets the emulation speed multiplier relative to real-time Game Boy speed. 1.0 = normal, 2.0 =
     * double speed, 0.5 = half speed.
     */
    fun setSpeed(multiplier: Float)

    /**
     * Returns the current LCD frame buffer. Array length is always 160 * 144 = 23040 elements. Each
     * element is a packed RGB integer: 0x00RRGGBB.
     */
    fun getFrameBuffer(): IntArray

    /**
     * Returns a handle to the emulator's address space for direct memory access. Reads and writes
     * go through the Game Boy MMU (memory-mapped I/O applies).
     */
    fun getMemory(): MemoryAccess

    /**
     * Returns all captured debug log entries since the emulator started. Older entries are evicted
     * when the limit is reached. Bounded by [EmulatorConfig.maxLogEntries].
     */
    fun getDebugLog(): List<DebugLogEntry>

    /** Returns true if the emulator is currently running (not stopped or paused). */
    fun isRunning(): Boolean

    /** Returns true if the emulator is currently paused. */
    fun isPaused(): Boolean

    /**
     * True when the emulator runs without a display window (headless mode). In headless mode,
     * [getFrameBuffer] still returns valid pixel data.
     */
    val isHeadless: Boolean

    /**
     * Returns the EventBus for posting input events, or null if this emulator implementation does
     * not support event-driven input.
     */
    fun getEventBus(): eu.rekawek.coffeegb.core.events.EventBus? = null

    /**
     * Requests cooperative cancellation of an in-flight [stepFrame] call. Safe to invoke from any
     * thread without holding any lock. The default implementation is a no-op; concrete emulators
     * (e.g. `CoffeeGbEmulator`) set a `@Volatile` flag that the tick loop polls each iteration.
     *
     * Used by stop/teardown paths to preempt a runaway frame before falling back to the watchdog
     * timeout.
     */
    fun requestCancellation() {}
}

/**
 * Provides direct read/write access to the Game Boy address space (0x0000–0xFFFF).
 *
 * Access goes through the Game Boy MMU, so:
 * - ROM reads (0x0000–0x7FFF) are subject to MBC banking
 * - I/O register writes (0xFF00–0xFF7F) trigger hardware side-effects
 * - OAM (0xFE00–0xFE9F) and WRAM (0xC000–0xDFFF) are general-purpose
 */
interface MemoryAccess {
    /**
     * Reads one byte from [address] in the Game Boy address space. Returns the byte value as an
     * unsigned int (0–255).
     */
    fun readByte(address: Int): Int

    /** Writes [value] (0–255) to [address] in the Game Boy address space. */
    fun writeByte(address: Int, value: Int)
}

/**
 * Severity levels for debug log entries captured by the emulator.
 * - [GAME]: Messages emitted by game DSL (score updates, scene transitions, etc.)
 * - [EMU]: Internal emulator lifecycle events (ROM load, bank switches, etc.)
 * - [WARN]: Non-fatal anomalies (unimplemented opcodes, palette fallbacks)
 * - [ERROR]: Fatal errors (invalid ROM, stack overflow, illegal memory access)
 */
enum class LogLevel {
    GAME,
    EMU,
    WARN,
    ERROR,
}
