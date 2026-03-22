/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.GbEmulator
import java.awt.BorderLayout
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicLong
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Main developer-facing emulator window.
 *
 * Hosts the [GbDisplayPanel] (center), [EmulatorToolbar] (top), and a status bar (bottom). The
 * title bar updates every second with the current FPS and frame count:
 * ```
 * gbkt - {rom_name} | 59.7 FPS | Frame 12345
 * ```
 *
 * Closing the window calls [GbEmulator.stop] and disposes the frame, allowing the JVM to exit if no
 * other non-daemon threads are running.
 *
 * Keyboard shortcuts (registered globally so they fire regardless of focus):
 * - **Space** — Pause/Resume emulation
 * - **F10** — Step one frame (only when paused)
 *
 * Input handler (game controls) is wired by the caller after construction:
 * ```kotlin
 * val inputHandler = InputHandler(eventBus)
 * window.addKeyListener(inputHandler)
 * ```
 *
 * Log viewer and memory inspector panels are wired via the toolbar callbacks:
 * ```kotlin
 * window.toolbar.onLogViewerToggle = { ... }
 * window.toolbar.onMemoryInspectorToggle = { ... }
 * ```
 *
 * Default size: 640x576 (160*4 x 144*4 Game Boy pixels). The window is resizable and the display
 * panel maintains a 160:144 aspect ratio with black letterboxing.
 *
 * @param emulator The [GbEmulator] to control.
 * @param config [EmulatorConfig] used for the ROM name and display scale.
 */
class EmulatorWindow(private val emulator: GbEmulator, private val config: EmulatorConfig) :
    JFrame() {

    /** The display panel that renders Game Boy LCD frames. */
    val displayPanel: GbDisplayPanel

    /** The developer toolbar (pause, step, speed, log, memory controls). */
    val toolbar: EmulatorToolbar

    private val statusBar: JLabel

    // Frame-counter state (written from emulator thread; read on EDT via Swing Timer)
    private val frameCount = AtomicLong(0)
    @Volatile private var lastFrameCount: Long = 0

    private var fpsTimer: Timer? = null
    @Volatile private var lastFpsTime: Long = System.nanoTime()

    private var keyDispatcher: KeyEventDispatcher? = null

    /** Optional callback invoked when the user closes the window (before local shutdown). */
    var onShutdownRequest: (() -> Unit)? = null

    init {
        val romName = config.romFile.nameWithoutExtension
        title = "gbkt - $romName | 0.0 FPS | Frame 0"

        // Close button stops the emulator before disposing the window
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    shutdown()
                }

                override fun windowClosed(e: WindowEvent) {
                    // Safety net: remove the global key dispatcher even if shutdown() was
                    // not called (e.g. dispose() called directly by EmulatorSession).
                    keyDispatcher?.let {
                        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                            .removeKeyEventDispatcher(it)
                    }
                    keyDispatcher = null
                }
            }
        )

        // ── Display panel (center) ────────────────────────────────────────────
        displayPanel = GbDisplayPanel(config.scale)

        // ── Toolbar (top) ─────────────────────────────────────────────────────
        toolbar = EmulatorToolbar(emulator)
        // ── Status bar (bottom) ───────────────────────────────────────────────
        statusBar = JLabel(" Ready").apply { border = BorderFactory.createEtchedBorder() }

        // ── Layout ────────────────────────────────────────────────────────────
        layout = BorderLayout()
        add(toolbar, BorderLayout.NORTH)
        add(displayPanel, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        // ── Keyboard shortcuts ────────────────────────────────────────────────
        // Use a KeyboardFocusManager dispatch listener so shortcuts fire
        // regardless of which child component holds focus (toolbar buttons steal
        // focus when clicked).
        keyDispatcher = KeyEventDispatcher { e ->
            if (e.id == KeyEvent.KEY_PRESSED && isActive) {
                when (e.keyCode) {
                    KeyEvent.VK_SPACE -> {
                        handleSpaceKey()
                        true // Consumed — prevent focus traversal
                    }
                    KeyEvent.VK_F10 -> {
                        handleF10Key()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)

        // Frame must be focusable to receive key events for game input handlers
        isFocusable = true

        pack()
        setLocationRelativeTo(null) // Center on screen
        isResizable = true
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the FPS update timer. Call this after [GbEmulator.start] so the title bar begins
     * reflecting real-time performance data.
     *
     * The timer fires every 1000 ms on the EDT to update the window title.
     */
    fun startFpsTimer() {
        fpsTimer =
            Timer(1000) {
                val currentFrameCount = frameCount.get()
                val now = System.nanoTime()
                val elapsed = now - lastFpsTime
                val fps =
                    if (elapsed > 0) {
                        (currentFrameCount - lastFrameCount) * 1_000_000_000.0 / elapsed
                    } else {
                        0.0
                    }
                lastFrameCount = currentFrameCount
                lastFpsTime = now
                val romName = config.romFile.nameWithoutExtension
                title = "gbkt - $romName | ${"%.1f".format(fps)} FPS | Frame $currentFrameCount"
            }
        fpsTimer?.start()
    }

    /**
     * Accepts a new frame from the emulator and forwards it to the [displayPanel].
     *
     * Wire this to [io.github.gbkt.emulator.CoffeeGbEmulator.onFrameReady]:
     * ```kotlin
     * emulator.onFrameReady = window::onFrameReady
     * ```
     *
     * Safe to call from the emulator thread — [GbDisplayPanel.onFrame] handles the EDT dispatch
     * internally.
     *
     * @param frameData RGB pixel array (length 23040, 0x00RRGGBB per pixel).
     */
    fun onFrameReady(frameData: IntArray) {
        frameCount.incrementAndGet()
        displayPanel.onFrame(frameData)
    }

    /**
     * Sets the text displayed in the status bar at the bottom of the window. Safe to call from any
     * thread — dispatches to the EDT.
     *
     * @param text Short message to display (e.g., "ROM loaded", "Paused").
     */
    fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusBar.text = " $text" }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Space key handler — toggles pause/resume and keeps toolbar in sync. */
    private fun handleSpaceKey() {
        if (emulator.isPaused()) {
            emulator.resume()
            toolbar.updatePauseState(false)
            toolbar.onPauseToggle?.invoke(false)
        } else {
            emulator.pause()
            toolbar.updatePauseState(true)
            toolbar.onPauseToggle?.invoke(true)
        }
    }

    /**
     * F10 key handler — steps one frame if the emulator is paused. Fires the toolbar's onStepFrame
     * callback to refresh the memory inspector. Silently no-ops when the emulator is running.
     */
    private fun handleF10Key() {
        if (emulator.isPaused()) {
            emulator.stepFrame()
            toolbar.onStepFrame?.invoke()
        }
    }

    /**
     * Stops the FPS timer and requests the session to shut down. The session handles emulator stop
     * and window disposal. Called when the user closes the window.
     */
    private fun shutdown() {
        fpsTimer?.stop()
        keyDispatcher?.let {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
        }
        keyDispatcher = null
        onShutdownRequest?.invoke()
    }
}
