/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.GbEmulator
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.SwingUtilities

/**
 * A [JToolBar] providing developer controls for the embedded Game Boy emulator.
 *
 * Buttons:
 * - **Pause/Resume** — toggles emulation pause state (keyboard: Space)
 * - **Step** — advances exactly one frame when paused (keyboard: F10)
 * - **1x / 2x / 4x** — speed multiplier buttons (mutually exclusive toggle group)
 * - **Log** — toggles the debug log viewer panel
 * - **Memory** — toggles the memory inspector panel
 *
 * The toolbar is non-floatable. Keyboard shortcuts are registered on the toolbar itself via
 * [InputMap]/[ActionMap] so they fire regardless of which child component has focus within the
 * toolbar.
 *
 * Usage:
 * ```kotlin
 * val toolbar = EmulatorToolbar(emulator)
 * toolbar.onMemoryInspectorToggle = { show -> ... }
 * toolbar.onLogViewerToggle = { show -> ... }
 * frame.add(toolbar, BorderLayout.NORTH)
 * ```
 *
 * @param emulator The [GbEmulator] instance to control.
 */
class EmulatorToolbar(private val emulator: GbEmulator) : JToolBar() {

    private val pauseResumeButton: JButton
    private val stepButton: JButton
    private val speedGroup: ButtonGroup
    private val speed1x: JToggleButton
    private val speed2x: JToggleButton
    private val speed4x: JToggleButton
    private val memoryButton: JToggleButton

    /**
     * Invoked when the Log viewer toggle button state changes. `true` means the viewer should be
     * shown, `false` means it should be hidden.
     */
    var onLogViewerToggle: ((Boolean) -> Unit)? = null

    /**
     * Invoked when the Memory inspector toggle button state changes. `true` means the inspector
     * should be shown, `false` means it should be hidden.
     */
    var onMemoryInspectorToggle: ((Boolean) -> Unit)? = null

    /**
     * Invoked when the emulator is paused or resumed via the toolbar button or keyboard shortcut.
     * `true` means the emulator is now paused, `false` means it was resumed.
     */
    var onPauseToggle: ((Boolean) -> Unit)? = null

    /** Invoked when a single frame is stepped via the toolbar button or keyboard shortcut. */
    var onStepFrame: (() -> Unit)? = null

    init {
        isFloatable = false

        // ── Pause/Resume button ───────────────────────────────────────────────
        pauseResumeButton =
            JButton("Pause").apply {
                toolTipText = "Pause/Resume emulation (Space)"
                addActionListener { togglePause() }
            }
        add(pauseResumeButton)

        // ── Step Frame button ─────────────────────────────────────────────────
        stepButton =
            JButton("Step").apply {
                toolTipText = "Advance one frame (F10)"
                isEnabled = false // Only enabled when paused
                addActionListener {
                    emulator.stepFrame()
                    onStepFrame?.invoke()
                }
            }
        add(stepButton)

        addSeparator()

        // ── Speed buttons ─────────────────────────────────────────────────────
        add(JLabel("Speed: "))
        speedGroup = ButtonGroup()
        speed1x =
            JToggleButton("1x", true).apply {
                toolTipText = "Normal speed (1x)"
                addActionListener { emulator.setSpeed(1.0f) }
            }
        speed2x =
            JToggleButton("2x").apply {
                toolTipText = "Double speed (2x)"
                addActionListener { emulator.setSpeed(2.0f) }
            }
        speed4x =
            JToggleButton("4x").apply {
                toolTipText = "Quadruple speed (4x)"
                addActionListener { emulator.setSpeed(4.0f) }
            }
        speedGroup.add(speed1x)
        speedGroup.add(speed2x)
        speedGroup.add(speed4x)
        add(speed1x)
        add(speed2x)
        add(speed4x)

        addSeparator()

        // ── Log Viewer toggle ─────────────────────────────────────────────────
        val logButton =
            JToggleButton("Log").apply {
                toolTipText = "Toggle debug log viewer"
                addActionListener { onLogViewerToggle?.invoke(isSelected) }
            }
        add(logButton)

        // ── Memory Inspector toggle ───────────────────────────────────────────
        memoryButton =
            JToggleButton("Memory").apply {
                toolTipText = "Toggle memory inspector"
                addActionListener { onMemoryInspectorToggle?.invoke(isSelected) }
            }
        add(memoryButton)
    }

    /**
     * Toggles the emulator's pause state and updates button labels accordingly.
     *
     * Uses [GbEmulator.isPaused] to determine current state:
     * - Not paused → pause → show "Resume", enable Step
     * - Already paused → resume → show "Pause", disable Step
     */
    private fun togglePause() {
        if (!emulator.isPaused()) {
            emulator.pause()
            pauseResumeButton.text = "Resume"
            stepButton.isEnabled = true
            onPauseToggle?.invoke(true)
        } else {
            emulator.resume()
            pauseResumeButton.text = "Pause"
            stepButton.isEnabled = false
            onPauseToggle?.invoke(false)
        }
    }

    /**
     * Updates the pause/step button states from outside the toolbar.
     *
     * Called by [EmulatorWindow] when keyboard shortcuts (Space / F10) trigger pause or step events
     * so the toolbar stays in sync with the actual emulator state.
     *
     * This method is safe to call from any thread — it dispatches to the EDT.
     *
     * @param paused `true` if the emulator is currently paused.
     */
    fun updatePauseState(paused: Boolean) {
        SwingUtilities.invokeLater {
            pauseResumeButton.text = if (paused) "Resume" else "Pause"
            stepButton.isEnabled = paused
        }
    }
}
