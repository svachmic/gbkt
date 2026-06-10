/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import eu.rekawek.coffeegb.core.Gameboy
import eu.rekawek.coffeegb.core.GameboyType
import eu.rekawek.coffeegb.core.debug.Console
import eu.rekawek.coffeegb.core.events.EventBusImpl
import eu.rekawek.coffeegb.core.gpu.Display
import eu.rekawek.coffeegb.core.memory.cart.Rom
import eu.rekawek.coffeegb.core.serial.SerialEndpoint
import io.github.gbkt.emulator.agent.EmulatorFrameHangException
import io.github.gbkt.emulator.debug.DebugLogEntry
import io.github.gbkt.emulator.debug.DebugLogWriter
import io.github.gbkt.emulator.debug.EmuPrintfInterceptor
import io.github.gbkt.emulator.debug.SourceMapResolver
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Coffee-GB backed implementation of [GbEmulator].
 *
 * Wraps [eu.rekawek.coffeegb.core.Gameboy] with a custom tick loop — NOT `gameboy.run()` — so that
 * downstream plans can intercept individual micro-ops via [onTick], step one frame at a time via
 * [stepFrame], and control emulation speed via [setSpeed].
 *
 * Thread safety:
 * - [running] uses [AtomicBoolean]; [paused] and [speedMultiplier] are `@Volatile`.
 * - The frame buffer is guarded by [frameBufferLock] (double-buffer swap).
 * - The debug log uses [ConcurrentLinkedDeque] for lock-free access with size bounds enforced on
 *   write.
 * - Tick operations in [emulatorLoop] and [stepFrame] are guarded by [tickLock] to prevent
 *   concurrent access to the Gameboy instance.
 *
 * @param config Configuration including the ROM file and headless flag.
 */
class CoffeeGbEmulator(private val config: EmulatorConfig) : GbEmulator {

    // ── Volatile state ────────────────────────────────────────────────────────

    private val running = AtomicBoolean(false)
    @Volatile private var paused = false
    @Volatile private var speedMultiplier: Float = 1.0f
    private var startTimeMs = 0L

    /**
     * Cooperative cancellation flag for [stepFrame]. Cleared on [start], set by
     * [requestCancellation] and [stop]. The tick loop in [stepFrame] checks this each iteration so
     * an in-flight runaway frame can be preempted without waiting for the watchdog ceiling.
     */
    @Volatile private var cancellationRequested = false

    /**
     * Hung-ROM watchdog ceiling: max t-cycles the [stepFrame] loop is allowed before declaring the
     * ROM hung and throwing [EmulatorFrameHangException]. A normal Game Boy video frame is 70 224
     * t-cycles (4.194 MHz × 16.74 ms); this default is ~14x that, comfortably above any legitimate
     * single-frame workload — including ROM init phases where the LCD is briefly disabled, which
     * legitimately stall the "frame complete" signal for tens of thousands of ticks.
     *
     * Exposed as a module-internal setter only for the watchdog unit test in [CoffeeGbEmulatorTest]
     * — production paths must use the default.
     */
    @Volatile internal var maxTicksPerFrame: Int = 1_000_000

    // ── Coffee-GB internals ───────────────────────────────────────────────────

    private var gameboy: Gameboy? = null
    private var emulatorThread: Thread? = null

    /** The EventBus shared with Coffee-GB. Exposed for InputHandler wiring. */
    internal var eventBus: EventBusImpl? = null
        private set

    // ── Frame buffer (double-buffered) ────────────────────────────────────────

    private val frameBufferLock = Any()
    private val internalFrameBuffer = IntArray(Display.DISPLAY_WIDTH * Display.DISPLAY_HEIGHT)
    private var publicFrameBuffer = IntArray(Display.DISPLAY_WIDTH * Display.DISPLAY_HEIGHT)

    // ── Debug log ─────────────────────────────────────────────────────────────

    private val debugLog = ConcurrentLinkedDeque<DebugLogEntry>()
    private val debugLogSize = AtomicInteger(0)

    // ── Source map resolver (C line → Kotlin DSL location) ───────────────────

    private val sourceMapResolver =
        SourceMapResolver(
            sourceMapsDir = config.sourceMapsDir,
            noiFile =
                config.romFile.parentFile?.let { dir ->
                    java.io.File(dir, config.romFile.nameWithoutExtension + ".noi")
                },
        )

    // ── Log file writer ───────────────────────────────────────────────────────

    private val logWriter = config.logFile?.let { DebugLogWriter(it) }

    // ── EMU_printf interceptor (stored for resetDedup on frame boundaries) ───

    private var interceptor: EmuPrintfInterceptor? = null

    // ── Tick synchronization ──────────────────────────────────────────────────

    private val tickLock = ReentrantLock()

    // ── Callback hooks (wired by downstream plans) ────────────────────────────

    /**
     * Invoked after every micro-op tick on the emulator thread. Plan 03 (EmuPrintfInterceptor)
     * wires this to check for the `ld d,d` trap. Must be fast — this fires ~4 million times per
     * second at 1x speed.
     */
    @Volatile var onTick: (() -> Unit)? = null

    /**
     * Invoked with a copy of the frame buffer after each completed frame. Plan 09 (display) wires
     * this to update the Swing panel.
     */
    @Volatile var onFrameReady: ((IntArray) -> Unit)? = null

    /**
     * Invoked when a new [DebugLogEntry] is added to the in-memory log. Plan 09 (log panel) wires
     * this to append entries to the UI list.
     */
    @Volatile var onDebugEntry: ((DebugLogEntry) -> Unit)? = null

    // ── GbEmulator interface ──────────────────────────────────────────────────

    override val isHeadless: Boolean
        get() = config.headless

    override fun start() {
        if (!running.compareAndSet(false, true)) return

        // Clear cancellation flag from any prior session — fresh starts must not inherit a stale
        // request that would immediately preempt the first stepFrame.
        cancellationRequested = false

        var success = false
        try {
            val rom = Rom(config.romFile)
            val gbConfig =
                Gameboy.GameboyConfiguration(rom)
                    .setGameboyType(if (config.gbcMode) GameboyType.CGB else GameboyType.DMG)
                    .setBootstrapMode(Gameboy.BootstrapMode.SKIP)

            val gb = gbConfig.build()

            // Use a real EventBus to receive frame-ready events with pixel data.
            // The Display fires DmgFrameReadyEvent in DMG mode and GbcFrameReadyEvent in CGB mode.
            // Both are registered here so that GBC-mode ROMs (gbcMode=true) produce a non-black
            // frame buffer. Previously only DmgFrameReadyEvent was wired, causing GBC screenshots
            // to remain all-black (frame buffer never updated in CGB mode).
            val eventBus = EventBusImpl()
            this.eventBus = eventBus
            eventBus.register(
                { event ->
                    event.toRgb(internalFrameBuffer, false)
                    synchronized(frameBufferLock) {
                        publicFrameBuffer = internalFrameBuffer.copyOf()
                    }
                },
                Display.DmgFrameReadyEvent::class.java,
            )
            eventBus.register(
                { event ->
                    event.toRgb(internalFrameBuffer)
                    synchronized(frameBufferLock) {
                        publicFrameBuffer = internalFrameBuffer.copyOf()
                    }
                },
                Display.GbcFrameReadyEvent::class.java,
            )

            // Create the EMU_printf interceptor. Fires each time GBDK EMU_printf() is called
            // in the ROM, routing the captured format string and PC address to addDebugEntry().
            this.startTimeMs = System.currentTimeMillis()
            val newInterceptor = EmuPrintfInterceptor { message, pc ->
                val entry =
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis() - this.startTimeMs,
                        level = LogLevel.GAME,
                        message = message,
                        pc = pc,
                    )
                addDebugEntry(entry)
            }
            interceptor = newInterceptor

            // Register the tick listener that runs after each CPU micro-op.
            // The interceptor checks for the EMU_printf trap signature and fires
            // at most once per call site per frame (deduplicated by lastInterceptedPc).
            gb.registerTickListener {
                val cpu = gb.cpu
                val addressSpace = gb.addressSpace
                newInterceptor.check(cpu.registers, addressSpace)
                onTick?.invoke()
            }

            gb.init(eventBus, SerialEndpoint.NULL_ENDPOINT, Console())

            gameboy = gb

            val thread = Thread(::emulatorLoop, "gbkt-emulator")
            thread.isDaemon = true
            thread.start()
            emulatorThread = thread
            success = true
        } finally {
            if (!success) {
                running.set(false)
                gameboy = null
                interceptor = null
                eventBus = null
            }
        }
    }

    override fun stop() {
        // Preempt any in-flight stepFrame BEFORE acquiring any lock. The tick-loop reads this on
        // its next iteration and throws, releasing tickLock so this stop() can proceed.
        cancellationRequested = true
        running.set(false)
        emulatorThread?.join(2_000L)
        emulatorThread = null
        try {
            gameboy?.close()
        } catch (_: Exception) {
            // Best-effort close — suppress exceptions during shutdown
        }
        gameboy = null
        interceptor = null
        eventBus = null
        onTick = null
        onFrameReady = null
        onDebugEntry = null
        debugLogSize.set(0)
        try {
            logWriter?.close()
        } catch (_: Exception) {
            // Best-effort close — suppress exceptions during shutdown
        }
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun stepFrame() {
        check(running.get()) { "stepFrame() requires the emulator to be running" }
        check(paused) { "stepFrame() requires the emulator to be paused" }
        val gb = checkNotNull(gameboy) { "Emulator not started" }
        tickLock.withLock {
            var frameDone: Boolean
            var ticks = 0
            do {
                // Cooperative cancellation: stop() (or any code that sets cancellationRequested)
                // can preempt a runaway frame without waiting for the watchdog ceiling.
                if (cancellationRequested) {
                    throw EmulatorFrameHangException(
                        "stepFrame cancelled after $ticks ticks (cancellation requested)"
                    )
                }
                frameDone = gb.tick()
                // onTick is already fired inside gb.tick() via the registered tick listener
                ticks++
                // Hung-ROM watchdog. A normal Game Boy frame is ~17 480 ticks; MAX_TICKS_PER_FRAME
                // is well past any legitimate workload. Without this guard, a CPU loop that never
                // reaches VBlank (e.g. LCD disabled + tight wait_vbl, or a corrupted scene init)
                // makes stepFrame() spin forever and locks the MCP server (CoffeeGbEmulatorTest +
                // gbkt-mcp-server CLAUDE.md document the hang mode).
                if (ticks >= maxTicksPerFrame) {
                    throw EmulatorFrameHangException(
                        "ROM did not complete a frame within $maxTicksPerFrame t-cycles " +
                            "(one Game Boy frame is 70 224 t-cycles) — likely an infinite CPU " +
                            "loop with no VBlank. Check LCDC (is BG/LCD enabled?), BGP, and the " +
                            "most recent scene-enter."
                    )
                }
            } while (!frameDone)
        }
        interceptor?.resetDedup()
        onFrameReady?.invoke(getFrameBuffer())
    }

    /**
     * Requests cancellation of a running [stepFrame] call. Safe to call from any thread without
     * holding any lock — sets a `@Volatile` flag that the next iteration of the tick loop checks.
     *
     * Used by `McpEmulatorSession.stop` to preempt a runaway frame so `emulator_stop` can recover
     * the session within one tick rather than waiting for the watchdog ceiling.
     *
     * Auto-cleared on the next [start].
     */
    override fun requestCancellation() {
        cancellationRequested = true
    }

    override fun setSpeed(multiplier: Float) {
        require(multiplier > 0f) { "Speed multiplier must be positive, got $multiplier" }
        speedMultiplier = multiplier
    }

    override fun getFrameBuffer(): IntArray {
        return synchronized(frameBufferLock) { publicFrameBuffer.copyOf() }
    }

    override fun getMemory(): MemoryAccess {
        val gb = checkNotNull(gameboy) { "Emulator not started" }
        val addressSpace = gb.getAddressSpace()
        return object : MemoryAccess {
            override fun readByte(address: Int): Int {
                check(running.get()) { "Emulator is not running" }
                return addressSpace.getByte(address) and 0xFF
            }

            override fun writeByte(address: Int, value: Int) {
                check(running.get()) { "Emulator is not running" }
                addressSpace.setByte(address, value)
            }
        }
    }

    override fun getDebugLog(): List<DebugLogEntry> = debugLog.toList()

    override fun isRunning(): Boolean = running.get()

    override fun isPaused(): Boolean = paused

    override fun getEventBus(): eu.rekawek.coffeegb.core.events.EventBus? = eventBus

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Adds a debug entry to the in-memory log (bounded by [EmulatorConfig.maxLogEntries]). Called
     * by [EmuPrintfInterceptor] when it detects a `gbkt_log()` trap.
     *
     * Source map enrichment: if [DebugLogEntry.cLine] is set, resolves to Kotlin source location
     * directly. If only [DebugLogEntry.pc] is available, attempts best-effort resolution via the
     * source map resolver's nearest-line lookup.
     *
     * This method is module-internal — not exposed on the [GbEmulator] interface.
     */
    internal fun addDebugEntry(entry: DebugLogEntry) {
        // Enrich the entry with Kotlin source location if available via source maps
        val enrichedEntry =
            if (entry.kotlinFile == null) {
                val cLine = entry.cLine ?: sourceMapResolver.resolveByPc(entry.pc)
                if (cLine != null) {
                    val location = sourceMapResolver.resolve(cLine)
                    if (location != null) {
                        entry.copy(
                            cLine = cLine,
                            kotlinFile = location.file,
                            kotlinLine = location.line,
                            context = entry.context ?: location.context.ifEmpty { null },
                        )
                    } else {
                        entry
                    }
                } else {
                    entry
                }
            } else {
                entry
            }

        debugLog.addLast(enrichedEntry)
        debugLogSize.incrementAndGet()
        // Evict oldest entries to enforce the bounded size
        while (debugLogSize.get() > config.maxLogEntries) {
            if (debugLog.pollFirst() != null) debugLogSize.decrementAndGet() else break
        }
        onDebugEntry?.invoke(enrichedEntry)
        logWriter?.let { writer ->
            try {
                writer.write(enrichedEntry)
            } catch (_: Exception) {
                // Best-effort log write — ignore I/O errors
            }
        }
    }

    // ── Custom tick loop ──────────────────────────────────────────────────────

    /**
     * The emulator's main loop. Runs on the "gbkt-emulator" daemon thread.
     *
     * CRITICAL: Uses [Gameboy.tick] NOT [Gameboy.run]. This enables:
     * 1. Per-tick interception via [onTick] (Plan 03 EMU_printf trap detection)
     * 2. Speed control via [speedMultiplier] (adjusts frame sleep duration)
     * 3. [stepFrame] support (tick until one frame completes, then stop)
     */
    private fun emulatorLoop() {
        val gb = gameboy ?: return
        // ~59.7275 FPS — one Game Boy frame every 16,742,706 nanoseconds
        val targetFrameNanos = 16_742_706L
        var lastFrameTime = System.nanoTime()

        while (running.get()) {
            try {
                if (paused) {
                    Thread.sleep(10L)
                    lastFrameTime = System.nanoTime() // Reset timing on unpause
                    continue
                }

                // Run one micro-op tick. onTick fires inside tick() via the registered listener.
                val frameDone = tickLock.withLock { gb.tick() }

                if (frameDone) {
                    // Reset dedup so the same EMU_printf call site can fire again next frame
                    interceptor?.resetDedup()

                    // Frame buffer is populated by DmgFrameReadyEvent handler via EventBus.
                    // Notify the display panel that a new frame is ready.
                    onFrameReady?.invoke(getFrameBuffer())

                    // Speed control: sleep to maintain target FPS adjusted by multiplier
                    val now = System.nanoTime()
                    val elapsed = now - lastFrameTime
                    val targetNanos = (targetFrameNanos / speedMultiplier).toLong()
                    val sleepNanos = targetNanos - elapsed
                    if (sleepNanos > 500_000L) { // Only sleep if > 0.5ms to avoid wakeup jitter
                        Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
                    }
                    lastFrameTime = System.nanoTime()
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                addDebugEntry(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis() - startTimeMs,
                        level = LogLevel.ERROR,
                        message = "Emulator tick crashed: ${e.message}",
                    )
                )
                running.set(false)
            }
        }
    }
}
