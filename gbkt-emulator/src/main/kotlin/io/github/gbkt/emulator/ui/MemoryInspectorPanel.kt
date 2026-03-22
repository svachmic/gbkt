/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.MemoryAccess
import java.awt.Dimension
import java.io.File
import javax.swing.JFrame
import javax.swing.JTabbedPane

/**
 * A two-tab [JTabbedPane] providing memory inspection capabilities for the embedded emulator.
 *
 * **Tab 1 — Variables:** Displays DSL variable names, types, addresses, and current values.
 * Variables are loaded from a `.sym` file or registered manually.
 *
 * **Tab 2 — Hex View:** Renders a raw hex dump of a 256-byte memory window, with quick navigation
 * buttons for WRAM, HRAM, OAM, and I/O regions.
 *
 * Typical usage:
 * ```kotlin
 * val inspector = MemoryInspectorPanel(
 *     memoryProvider = { emulator.getMemory() },
 *     symbolFile = File("build/gbkt/output/game.sym")
 * )
 * // After pause or step:
 * inspector.refresh()
 * ```
 *
 * @param memoryProvider Provides the current [MemoryAccess] (null when emulator is stopped).
 * @param symbolFile Optional path to the SDCC `.sym` file produced by lcc for automatic variable
 *   discovery.
 */
class MemoryInspectorPanel(memoryProvider: () -> MemoryAccess?, symbolFile: File? = null) :
    JTabbedPane() {

    /** The Named Variables tab — displays DSL variable names and values. */
    val namedVariablesTab = NamedVariablesTab(memoryProvider, symbolFile)

    /** The Hex View tab — displays raw memory as a hex dump. */
    val hexViewTab = HexViewTab(memoryProvider)

    init {
        addTab("Variables", namedVariablesTab)
        addTab("Hex View", hexViewTab)
    }

    /**
     * Refreshes both tabs by re-reading values from the emulator's address space. Call this after
     * the emulator is paused or after each frame step to keep values current.
     */
    fun refresh() {
        namedVariablesTab.refresh()
        hexViewTab.refresh()
    }
}

/**
 * A standalone [JFrame] window hosting a [MemoryInspectorPanel].
 *
 * Displayed as a separate window from the main emulator UI. Use [isVisible] to show or hide;
 * closing the window hides it rather than disposing it (HIDE_ON_CLOSE).
 *
 * Typical usage (wired from the main emulator toolbar):
 * ```kotlin
 * val inspectorWindow = MemoryInspectorWindow(
 *     memoryProvider = { emulator.getMemory() },
 *     symbolFile = File("build/gbkt/output/game.sym")
 * )
 * // Toggle from toolbar button:
 * inspectorButton.addActionListener {
 *     inspectorWindow.isVisible = !inspectorWindow.isVisible
 * }
 * // Refresh when emulator pauses:
 * emulator.onPaused = { inspectorWindow.inspectorPanel.refresh() }
 * ```
 *
 * @param memoryProvider Provides the current [MemoryAccess] (null when emulator is stopped).
 * @param symbolFile Optional path to the SDCC `.sym` file for automatic variable discovery.
 */
class MemoryInspectorWindow(memoryProvider: () -> MemoryAccess?, symbolFile: File? = null) :
    JFrame("gbkt - Memory Inspector") {

    /** The inspector panel hosted in this window. */
    val inspectorPanel = MemoryInspectorPanel(memoryProvider, symbolFile)

    init {
        contentPane.add(inspectorPanel)
        defaultCloseOperation = HIDE_ON_CLOSE
        preferredSize = Dimension(700, 500)
        pack()
    }
}
