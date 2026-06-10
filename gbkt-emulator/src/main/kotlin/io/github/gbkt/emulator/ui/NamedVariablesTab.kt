/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.MemoryAccess
import java.awt.BorderLayout
import java.awt.Font
import java.io.File
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

/**
 * A [JPanel] that displays DSL variable names, types, addresses, and current values in a table
 * view.
 *
 * Variables can be loaded automatically from an SDCC `.sym` file (produced by the lcc compiler
 * during `buildRom`) or added manually via the "Add Variable..." button. Call [refresh] to re-read
 * all variable values from the emulator's address space.
 *
 * Symbol file format (SDCC .sym / .noi DEF lines):
 * ```
 * DEF _score 00:C100
 * DEF _lives 00:C101
 * ```
 *
 * The leading underscore is the C name-mangling convention; gbkt strips it for display.
 *
 * @param memoryProvider Provides the current [MemoryAccess] (null when emulator is stopped).
 * @param symbolFile Optional path to an SDCC `.sym` file for automatic variable discovery.
 */
class NamedVariablesTab(
    private val memoryProvider: () -> MemoryAccess?,
    private val symbolFile: File? = null,
) : JPanel(BorderLayout()) {

    /**
     * A single tracked variable entry.
     *
     * @param name Human-readable DSL variable name (without C underscore prefix).
     * @param type Variable type string used for value decoding (UINT8, INT8, UINT16, INT16).
     * @param address Game Boy address space address (0x0000–0xFFFF).
     * @param value Last read value as a display string. "?" until first refresh.
     */
    data class VariableEntry(
        val name: String,
        var type: String,
        val address: Int,
        var value: String = "?",
    )

    private val tableModel: DefaultTableModel =
        object : DefaultTableModel(arrayOf("Name", "Type", "Address", "Value"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

    /** The backing table for display. Public for testing. */
    val table: JTable =
        JTable(tableModel).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.font = Font(Font.MONOSPACED, Font.BOLD, 12)
        }

    private val variables = mutableListOf<VariableEntry>()

    init {
        val toolbar =
            JToolBar().apply {
                isFloatable = false
                add(
                    JButton("Refresh").apply {
                        toolTipText = "Re-read all variable values from emulator memory"
                        addActionListener { refresh() }
                    }
                )
                add(
                    JButton("Add Variable...").apply {
                        toolTipText = "Manually register a variable by address"
                        addActionListener { addManualVariable() }
                    }
                )
            }
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)

        if (symbolFile != null && symbolFile.exists()) {
            loadSymbols(symbolFile)
        }
    }

    /**
     * Re-reads all tracked variable values from the emulator address space. Silently no-ops if the
     * emulator is not running (provider returns null).
     *
     * Memory reads happen on the calling thread to avoid blocking the EDT with I/O. The Swing table
     * model is updated on the EDT for thread safety.
     *
     * Decodes values by type:
     * - UINT8: unsigned byte (0–255)
     * - INT8: signed byte (-128–127)
     * - UINT16: little-endian unsigned word (0–65535)
     * - INT16: little-endian signed word (-32768–32767)
     * - Other: raw hex byte
     */
    fun refresh() {
        val memory = memoryProvider() ?: return
        // Read memory and decode values on the calling thread (avoid blocking EDT with I/O)
        for (i in variables.indices) {
            val v = variables[i]
            val rawByte = memory.readByte(v.address) // Already 0–255 per MemoryAccess contract
            v.value =
                when (v.type) {
                    "UINT8" -> "$rawByte"
                    "INT8" -> "${rawByte.toByte()}"
                    "UINT16" -> {
                        val lo = rawByte
                        val hi = memory.readByte(v.address + 1)
                        "${(hi shl 8) or lo}"
                    }
                    "INT16" -> {
                        val lo = rawByte
                        val hi = memory.readByte(v.address + 1)
                        "${((hi shl 8) or lo).toShort()}"
                    }
                    else -> "0x${rawByte.toString(16).uppercase().padStart(2, '0')}"
                }
        }
        // Update Swing table model on the EDT
        val snapshot = variables.mapIndexed { i, v -> i to v.value }
        SwingUtilities.invokeLater {
            for ((i, value) in snapshot) {
                tableModel.setValueAt(value, i, 3)
            }
        }
    }

    /**
     * Loads variables from an SDCC `.sym` / `.noi` file.
     *
     * Parses lines of the form:
     * ```
     * DEF _symbolName bank:ADDR
     * ```
     *
     * where `ADDR` is a 4-digit hex address. Only symbols starting with `_` (C name-mangling
     * convention applied by lcc to all global GBDK variables) are loaded. The leading underscore is
     * stripped for display.
     */
    fun loadSymbols(symFile: File) {
        symFile.readLines().forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3 && parts[0] == "DEF" && parts[1].startsWith("_")) {
                val name = parts[1].removePrefix("_")
                val addrStr = parts[2]
                val address =
                    try {
                        // Format: "00:C100" (bank:address) — extract the address part
                        addrStr.substringAfter(":").toInt(16)
                    } catch (_: Exception) {
                        return@forEach
                    }
                addEntry(VariableEntry(name, inferVariableType(name), address))
            }
        }
    }

    /**
     * Shows a multi-step dialog for manually adding a variable by name, type, and address.
     * Validates the hex address and shows an error dialog on invalid input.
     */
    private fun addManualVariable() {
        val name = JOptionPane.showInputDialog(this, "Variable name:") ?: return
        if (name.isBlank()) return

        val types = arrayOf("UINT8", "INT8", "UINT16", "INT16")
        val typeChoice =
            JOptionPane.showInputDialog(
                this,
                "Variable type:",
                "Add Variable",
                JOptionPane.QUESTION_MESSAGE,
                null,
                types,
                types[0],
            ) as? String ?: return

        val addressStr = JOptionPane.showInputDialog(this, "Address (hex, e.g. C100):") ?: return
        val address =
            try {
                addressStr.trim().toInt(16)
            } catch (_: Exception) {
                JOptionPane.showMessageDialog(this, "Invalid hex address: $addressStr")
                return
            }

        addEntry(VariableEntry(name, typeChoice, address))
    }

    private fun addEntry(entry: VariableEntry) {
        variables.add(entry)
        tableModel.addRow(
            arrayOf(
                entry.name,
                entry.type,
                "0x${entry.address.toString(16).uppercase().padStart(4, '0')}",
                entry.value,
            )
        )
    }

    /**
     * Infers a plausible variable type from the symbol name using naming conventions.
     *
     * Heuristic:
     * - Names containing "16", "addr", or "ptr" → UINT16 (likely 16-bit values or pointers)
     * - Names containing "dx", "dy", "vel", or "dir" → INT8 (likely signed deltas/velocities)
     * - Everything else → UINT8 (default for Game Boy variables)
     */
    private fun inferVariableType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("16") || lower.contains("addr") || lower.contains("ptr") -> "UINT16"
            lower.contains("dx") ||
                lower.contains("dy") ||
                lower.contains("vel") ||
                lower.contains("dir") -> "INT8"
            else -> "UINT8"
        }
    }

    /** Returns the current number of tracked variables. For testing. */
    fun variableCount(): Int = variables.size

    /** Returns a copy of the current variable list. For testing. */
    fun getVariables(): List<VariableEntry> = variables.toList()

    /**
     * Programmatically changes the type of a tracked variable by name.
     *
     * Updates both the backing [VariableEntry] and the table model display. Valid types are UINT8,
     * INT8, UINT16, INT16.
     *
     * @param name The variable name (without C underscore prefix).
     * @param type The new type string.
     * @throws IllegalArgumentException if no variable with the given name exists.
     */
    fun setVariableType(name: String, type: String) {
        val index = variables.indexOfFirst { it.name == name }
        require(index >= 0) { "No variable named '$name'" }
        variables[index].type = type
        tableModel.setValueAt(type, index, 1)
    }
}
