/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.MemoryAccess
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.SwingUtilities

/**
 * A [JPanel] that renders a hex dump view of the Game Boy address space.
 *
 * Displays 16 rows of 16 bytes each (256 bytes total) starting at [baseAddress]. Each row shows the
 * address, 16 hex bytes (grouped as 8+8 for readability), and the ASCII representation of those
 * bytes (printable characters only).
 *
 * Quick-navigation buttons jump to Game Boy memory regions:
 * - WRAM (0xC000): Work RAM — where GBDK global variables live
 * - HRAM (0xFF80): High RAM — fast scratch area
 * - OAM (0xFE00): Sprite attribute table
 * - IO (0xFF00): I/O registers
 *
 * Call [refresh] to re-read the current 256-byte window from the emulator.
 *
 * @param memoryProvider Provides the current [MemoryAccess] (null when emulator is stopped).
 */
class HexViewTab(private val memoryProvider: () -> MemoryAccess?) : JPanel(BorderLayout()) {

    private val textArea: JTextArea
    private val addressField: JTextField

    /** Base address of the 256-byte window currently displayed. */
    var baseAddress: Int = 0xC000
        private set

    companion object {
        private const val BYTES_PER_ROW = 16
        private const val ROWS_TO_SHOW = 16
        private const val HEADER =
            "Address   00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F  ASCII"
        private const val SEPARATOR =
            "---------------------------------------------------------------------------"
    }

    init {
        textArea =
            JTextArea().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                isEditable = false
                background = Color(30, 30, 30)
                foreground = Color(200, 200, 200)
                caretColor = Color(200, 200, 200)
                // Pre-populate with placeholder so the panel has correct size before first refresh
                text = buildPlaceholder()
            }

        addressField =
            JTextField("C000", 6).apply {
                toolTipText = "Hex address (e.g. C000)"
                addActionListener { navigateTo() }
            }

        val toolbar =
            JToolBar().apply {
                isFloatable = false
                add(JLabel(" Address: 0x"))
                add(addressField)
                add(
                    JButton("Go").apply {
                        toolTipText = "Navigate to address"
                        addActionListener { navigateTo() }
                    }
                )
                add(
                    JButton("Refresh").apply {
                        toolTipText = "Re-read current memory window"
                        addActionListener { refresh() }
                    }
                )
                addSeparator()
                add(JLabel(" Jump: "))
                add(
                    JButton("WRAM").apply {
                        toolTipText = "Work RAM — GBDK global variables (0xC000)"
                        addActionListener { goTo(0xC000) }
                    }
                )
                add(
                    JButton("HRAM").apply {
                        toolTipText = "High RAM — fast scratch (0xFF80)"
                        addActionListener { goTo(0xFF80) }
                    }
                )
                add(
                    JButton("OAM").apply {
                        toolTipText = "Sprite attribute table (0xFE00)"
                        addActionListener { goTo(0xFE00) }
                    }
                )
                add(
                    JButton("IO").apply {
                        toolTipText = "I/O registers (0xFF00)"
                        addActionListener { goTo(0xFF00) }
                    }
                )
            }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(textArea), BorderLayout.CENTER)
        preferredSize = Dimension(620, 400)
    }

    /**
     * Re-reads the 256-byte window starting at [baseAddress] and renders it as a hex dump. Silently
     * no-ops if the emulator is not running (provider returns null).
     */
    fun refresh() {
        val memory = memoryProvider() ?: return
        // Read memory and build dump text outside EDT to avoid blocking UI with I/O
        val dump = buildDump(memory, baseAddress)
        SwingUtilities.invokeLater { textArea.text = dump }
    }

    /**
     * Navigates to the address typed in the address field and refreshes the view. Shows an error
     * dialog if the address text is not valid hexadecimal.
     */
    private fun navigateTo() {
        val addr =
            try {
                addressField.text.trim().toInt(16)
            } catch (_: Exception) {
                JOptionPane.showMessageDialog(this, "Invalid hex address: '${addressField.text}'")
                return
            }
        goTo(addr)
    }

    /**
     * Navigates to [address] and refreshes the display. [address] is clamped to valid Game Boy
     * address space (0x0000–0xFFFF).
     */
    private fun goTo(address: Int) {
        baseAddress = address.coerceIn(0x0000, 0xFFFF)
        addressField.text = baseAddress.toString(16).uppercase()
        refresh()
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /**
     * Builds the hex dump text for [ROWS_TO_SHOW] rows of [BYTES_PER_ROW] bytes starting at [base],
     * reading from [memory].
     *
     * Output format per row:
     * ```
     * C000:  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F  ................
     * ```
     */
    internal fun buildDump(memory: MemoryAccess, base: Int): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        sb.appendLine(SEPARATOR)
        for (row in 0 until ROWS_TO_SHOW) {
            val rowAddr = (base + row * BYTES_PER_ROW) and 0xFFFF
            sb.append(rowAddr.toString(16).uppercase().padStart(4, '0'))
            sb.append(":  ")
            val ascii = StringBuilder()
            for (col in 0 until BYTES_PER_ROW) {
                val addr = (rowAddr + col) and 0xFFFF
                val byte = memory.readByte(addr) // Returns 0–255 per MemoryAccess contract
                sb.append(byte.toString(16).uppercase().padStart(2, '0'))
                // Insert extra space after byte 7 for visual grouping
                sb.append(if (col == 7) "  " else " ")
                ascii.append(if (byte in 0x20..0x7E) byte.toChar() else '.')
            }
            sb.append(" $ascii")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildPlaceholder(): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        sb.appendLine(SEPARATOR)
        repeat(ROWS_TO_SHOW) {
            sb.appendLine(
                "----:  -- -- -- -- -- -- -- --  -- -- -- -- -- -- -- --  ................"
            )
        }
        return sb.toString()
    }
}
