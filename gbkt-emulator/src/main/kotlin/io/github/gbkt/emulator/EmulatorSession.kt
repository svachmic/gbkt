/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import io.github.gbkt.emulator.ui.EmulatorWindow
import io.github.gbkt.emulator.ui.InputHandler
import io.github.gbkt.emulator.ui.LogCatWindow
import io.github.gbkt.emulator.ui.MemoryInspectorWindow
import java.io.File
import javax.swing.SwingUtilities

/**
 * Orchestrator that wires the [CoffeeGbEmulator] core to all UI components.
 *
 * [EmulatorSession] is the single entry point for launching the embedded emulator developer tool. A
 * single [launch] call creates and connects:
 * - [CoffeeGbEmulator] — the headless Game Boy core
 * - [EmulatorWindow] — main developer window with display, toolbar, and status bar
 * - [LogCatWindow] — floating debug log viewer, toggled from the toolbar
 * - [MemoryInspectorWindow] — floating memory inspector, toggled from the toolbar
 *
 * In **headless mode** (`config.headless = true`), no Swing components are created. The emulator
 * runs on a daemon thread, writing debug entries to the configured log file. This mode is safe to
 * use in CI/CD environments without a display server.
 *
 * In **GUI mode**, all windows are created on the EDT. The emulator starts after UI setup is
 * complete so the first frame is captured immediately.
 *
 * Usage:
 * ```kotlin
 * val session = EmulatorSession(
 *     EmulatorConfig(
 *         romFile = File("build/gbkt/output/game.gb"),
 *         headless = false,
 *         logFile = File("build/gbkt/logs/debug.log"),
 *         sourceMapsDir = File("build/gbkt/generated")
 *     )
 * )
 * session.launch()   // Non-blocking — returns immediately
 * // ...
 * session.shutdown() // Clean shutdown on exit
 * ```
 *
 * @param config Configuration for the emulator session.
 */
class EmulatorSession(private val config: EmulatorConfig) {

    /** The emulator instance (interface type). Non-null after [launch]. */
    var emulator: GbEmulator? = null
        private set

    /** The main emulator window (GUI mode only). Non-null after [launch] in GUI mode. */
    var window: EmulatorWindow? = null
        private set

    /** The floating log viewer window (GUI mode only). Non-null after [launch] in GUI mode. */
    var logWindow: LogCatWindow? = null
        private set

    /**
     * The floating memory inspector window (GUI mode only). Non-null after [launch] in GUI mode.
     */
    var memoryWindow: MemoryInspectorWindow? = null
        private set

    /**
     * Launches the emulator session. **Non-blocking — returns immediately.**
     *
     * In GUI mode:
     * 1. Creates [EmulatorWindow] on the EDT (with display, toolbar, status bar)
     * 2. Creates [LogCatWindow] and [MemoryInspectorWindow] (hidden by default)
     * 3. Wires all callbacks (frame-ready → display, debug entries → log panel)
     * 4. Starts the emulator on its daemon thread
     *
     * In headless mode:
     * 1. Creates [CoffeeGbEmulator] with the given config
     * 2. Starts the emulator on its daemon thread
     * 3. Log entries are written to [EmulatorConfig.logFile] if configured (enrichment via source
     *    maps is handled internally by [CoffeeGbEmulator])
     *
     * @throws IllegalStateException if called more than once without calling [shutdown] first.
     */
    fun launch() {
        check(emulator == null) { "EmulatorSession already launched. Call shutdown() first." }

        val emu = CoffeeGbEmulator(config)
        this.emulator = emu

        if (config.headless) {
            // Headless mode: no Swing components, just start the emulator.
            // CoffeeGbEmulator handles source map enrichment and log file writing internally.
            emu.start()
        } else {
            // GUI mode: create all windows on the EDT, then start the emulator.
            SwingUtilities.invokeAndWait {
                // ── Main emulator window ──────────────────────────────────────
                val win = EmulatorWindow(emu, config)
                this.window = win

                // Wire frame-ready: emulator thread → display panel + FPS counter
                emu.onFrameReady = { frameData -> win.onFrameReady(frameData) }

                // Wire shutdown request: closing the main window disposes all child windows
                win.onShutdownRequest = { shutdown() }

                // ── LogCat window (hidden until toolbar Log button is toggled) ──
                val logWin = LogCatWindow()
                this.logWindow = logWin

                // Wire debug entry callback: emulator → LogCat panel (direct call).
                // appendEntry() is safe to call from any thread — it handles its own
                // EDT dispatch internally, so no outer invokeLater is needed here.
                emu.onDebugEntry = { entry -> logWin.logPanel.appendEntry(entry) }

                // ── Memory Inspector window (hidden until Memory button toggled) ──
                val memWin =
                    MemoryInspectorWindow(
                        memoryProvider = {
                            if (emu.isRunning()) {
                                try {
                                    emu.getMemory()
                                } catch (_: IllegalStateException) {
                                    null
                                }
                            } else {
                                null
                            }
                        },
                        symbolFile = findSymFile(),
                    )
                this.memoryWindow = memWin

                // ── Wire toolbar toggles (override EmulatorWindow's stub callbacks) ──
                win.toolbar.onLogViewerToggle = { show ->
                    logWin.isVisible = show
                    if (show) logWin.toFront()
                }
                win.toolbar.onMemoryInspectorToggle = { show ->
                    memWin.isVisible = show
                    if (show) {
                        memWin.inspectorPanel.refresh()
                        memWin.toFront()
                    }
                }

                // ── Wire pause/step to refresh memory inspector ──────────────
                win.toolbar.onPauseToggle = { isPaused ->
                    if (isPaused && memoryWindow?.isVisible == true) {
                        memoryWindow?.inspectorPanel?.refresh()
                    }
                }
                win.toolbar.onStepFrame = {
                    if (memoryWindow?.isVisible == true) {
                        memoryWindow?.inspectorPanel?.refresh()
                    }
                }

                // Show main window and start the FPS timer
                win.isVisible = true
                win.startFpsTimer()
            }

            // Start the emulator after UI is fully set up on the EDT
            emu.start()

            // Wire game input after start() — EventBus is created inside start()
            val win = this.window
            val bus = emu.getEventBus()
            if (win != null && bus != null) {
                SwingUtilities.invokeLater { win.addKeyListener(InputHandler(bus)) }
            }
        }
    }

    /**
     * Shuts down the emulator session cleanly.
     *
     * Stops the emulator thread, closes the log writer, and disposes all windows. Safe to call even
     * if [launch] was not called (no-ops in that case).
     *
     * When called from the EDT (e.g. via window close), emulator stop runs on a background thread
     * to avoid blocking the UI during the thread join.
     */
    fun shutdown() {
        val emu = emulator
        val w = window
        val lw = logWindow
        val mw = memoryWindow
        emulator = null
        window = null
        logWindow = null
        memoryWindow = null

        val disposeWindows = Runnable {
            w?.dispose()
            lw?.dispose()
            mw?.dispose()
        }

        if (emu != null) {
            // Run emulator stop (which may block up to 2s on thread join) off the EDT
            val stopThread =
                Thread(
                    {
                        emu.stop()
                        SwingUtilities.invokeLater(disposeWindows)
                    },
                    "gbkt-shutdown",
                )
            stopThread.isDaemon = true
            stopThread.start()
        } else {
            SwingUtilities.invokeLater(disposeWindows)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Looks for a `.sym` file alongside the ROM file.
     *
     * The SDCC linker (lcc) produces a `.sym` file with addresses for each global variable in the
     * ROM. The memory inspector uses this to display DSL variable names and addresses alongside
     * their current in-memory values.
     *
     * @return The `.sym` file if it exists next to the ROM, otherwise null.
     */
    private fun findSymFile(): File? {
        val symFile =
            File(
                config.romFile.parentFile ?: return null,
                config.romFile.nameWithoutExtension + ".sym",
            )
        return if (symFile.exists()) symFile else null
    }
}
