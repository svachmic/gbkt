/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToolBar
import javax.swing.SwingUtilities

/**
 * A LogCat-style terminal panel for displaying emulator debug log entries.
 *
 * Styled like an Android Studio LogCat window: dark background, monospace font, auto-scrolling, and
 * filterable by log level. Each entry is prefixed with its severity level tag so entries are
 * distinguishable without color-coded text.
 *
 * Usage:
 * ```kotlin
 * val panel = LogCatPanel()
 * emulator.getDebugLog().forEach { panel.appendEntry(it) }
 * ```
 *
 * Thread safety: [appendEntry] may be called from any thread — it marshals display updates to the
 * EDT via [SwingUtilities.invokeLater].
 */
class LogCatPanel(private val maxEntries: Int = 10_000) : JPanel(BorderLayout()) {

    // Terminal-style text area displaying formatted log output
    private val textArea: JTextArea

    // Scrollable wrapper around the text area
    private val scrollPane: JScrollPane

    // Level filter dropdown — ALL means no filtering
    val filterCombo: JComboBox<String>

    // Clear button — wipes both the entry list and displayed text
    val clearButton: JButton

    // Accumulated log entries (all levels, unfiltered). ArrayDeque for O(1) removeFirst.
    internal val entries = ArrayDeque<DebugLogEntry>()

    // Active filter: null = show ALL, non-null = show only that level
    internal var currentFilter: LogLevel? = null
        private set

    // Count label displayed on right side of toolbar
    private val countLabel: JLabel

    init {
        // Terminal-style text area
        textArea =
            JTextArea().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                background = Color(30, 30, 30)
                foreground = Color(220, 220, 220)
                caretColor = Color(220, 220, 220)
                isEditable = false
                lineWrap = false
            }

        scrollPane =
            JScrollPane(textArea).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                background = Color(30, 30, 30)
                viewport.background = Color(30, 30, 30)
            }

        // Filter toolbar
        val filterToolbar =
            JToolBar().apply {
                isFloatable = false
                add(JLabel(" Filter: "))
            }

        filterCombo =
            JComboBox(arrayOf("ALL", "GAME", "EMU", "WARN", "ERROR")).apply {
                addActionListener {
                    currentFilter =
                        when (selectedItem as String) {
                            "ALL" -> null
                            "GAME" -> LogLevel.GAME
                            "EMU" -> LogLevel.EMU
                            "WARN" -> LogLevel.WARN
                            "ERROR" -> LogLevel.ERROR
                            else -> null
                        }
                    refreshDisplay()
                }
            }
        filterToolbar.add(filterCombo)

        clearButton =
            JButton("Clear").apply {
                addActionListener {
                    entries.clear()
                    textArea.text = ""
                    updateCountLabel()
                }
            }
        filterToolbar.add(clearButton)

        countLabel = JLabel(" 0 entries ")
        filterToolbar.add(Box.createHorizontalGlue())
        filterToolbar.add(countLabel)

        add(filterToolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        preferredSize = Dimension(700, 300)
    }

    /**
     * Appends a new log entry to the panel.
     *
     * Safe to call from any thread. If [currentFilter] is null or matches the entry's [LogLevel],
     * the entry is appended to the display and the panel auto-scrolls to the bottom.
     *
     * Level prefix tags provide visual distinction between entry types:
     * - `[GAME]` — game DSL output (score, scene transitions, etc.)
     * - ` [EMU]` — emulator internal events (bank switches, ROM load)
     * - `[WARN]` — non-fatal anomalies (unimplemented opcodes)
     * - ` [ERR]` — fatal errors (illegal memory access, stack overflow)
     */
    fun appendEntry(entry: DebugLogEntry) {
        SwingUtilities.invokeLater {
            entries.addLast(entry)
            if (entries.size > maxEntries) {
                entries.removeFirst()
                refreshDisplay()
            } else if (currentFilter == null || entry.level == currentFilter) {
                textArea.append(levelPrefix(entry.level) + entry.formatted())
                // Auto-scroll to bottom
                val len = textArea.document.length
                if (len > 0) {
                    textArea.caretPosition = len
                }
            }
            updateCountLabel()
        }
    }

    /**
     * Clears all entries and resets the display.
     *
     * Must be called on the EDT.
     */
    fun clear() {
        entries.clear()
        textArea.text = ""
        updateCountLabel()
    }

    /** Returns the number of visible (filtered) entries currently displayed. */
    fun visibleEntryCount(): Int {
        return if (currentFilter == null) entries.size
        else entries.count { it.level == currentFilter }
    }

    /**
     * Returns the full text content currently displayed in the panel. Useful in tests to verify
     * which entries are rendered.
     */
    fun displayText(): String = textArea.text

    /** Returns the current caret position in the text area. For testing auto-scroll behavior. */
    internal fun getCaretPosition(): Int = textArea.caretPosition

    /** Returns the current document length. For testing auto-scroll behavior. */
    internal fun getDocumentLength(): Int = textArea.document.length

    // Rebuilds the text area from the accumulated entries list applying the current filter.
    private fun refreshDisplay() {
        textArea.text = ""
        val filtered =
            if (currentFilter == null) entries else entries.filter { it.level == currentFilter }
        for (entry in filtered) {
            textArea.append(levelPrefix(entry.level) + entry.formatted())
        }
        val len = textArea.document.length
        if (len > 0) {
            textArea.caretPosition = len
        }
        updateCountLabel()
    }

    // Updates the count label to reflect the current total and visible entry counts.
    private fun updateCountLabel() {
        val total = entries.size
        val visible = visibleEntryCount()
        countLabel.text =
            if (currentFilter == null) " $total entries " else " $visible / $total entries "
    }

    // Returns the log level tag prefix for a given entry level.
    // Padded to 7 chars so all prefixes are the same width.
    private fun levelPrefix(level: LogLevel): String =
        when (level) {
            LogLevel.GAME -> "[GAME] "
            LogLevel.EMU -> " [EMU] "
            LogLevel.WARN -> "[WARN] "
            LogLevel.ERROR -> " [ERR] "
        }
}

/**
 * A standalone JFrame window containing a [LogCatPanel].
 *
 * Designed to be toggled from the emulator toolbar (show/hide on demand). Closing the window hides
 * it rather than disposing it, so its entry history is preserved across visibility toggles.
 *
 * Usage:
 * ```kotlin
 * val logWindow = LogCatWindow()
 * logWindow.isVisible = true
 *
 * // To toggle from toolbar:
 * logWindow.isVisible = !logWindow.isVisible
 * ```
 */
class LogCatWindow(title: String = "gbkt - Debug Log") : JFrame(title) {

    /** The embedded log panel. Use [logPanel.appendEntry] to add entries. */
    val logPanel = LogCatPanel()

    init {
        contentPane.add(logPanel)
        // Hide on close — preserves log history across visibility toggles
        defaultCloseOperation = HIDE_ON_CLOSE
        pack()
    }
}
