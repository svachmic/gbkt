/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("CyclomaticComplexMethod") // PO parser has many branches for entry types

package io.github.gbkt.intellij.editors.strings

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Editor panel for GNU gettext .po/.pot files with Game Boy font preview.
 *
 * Features:
 * - Table view of all string entries grouped by namespace (msgctxt)
 * - GB font preview of selected string
 * - Character count validation (18 chars/line, 90 total)
 * - Parameter highlighting (%name, %damage, etc.)
 */
class PoEditorPanel(private val project: Project, private val file: VirtualFile) :
    JPanel(BorderLayout()) {

    private val tableModel = PoTableModel()
    private val table = JBTable(tableModel)
    private val previewPanel = StringPreviewPanel()
    private val statusLabel = JBLabel()

    /** Parsed PO entries. */
    private var entries: List<PoEntry> = emptyList()

    init {
        setupTable()
        setupLayout()
        loadFile()
    }

    private fun setupTable() {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                updatePreview()
            }
        }

        // Custom cell renderer for validation highlighting
        table.setDefaultRenderer(String::class.java, PoEntryCellRenderer())

        // Column widths
        table.columnModel.getColumn(0).preferredWidth = 100 // Namespace
        table.columnModel.getColumn(1).preferredWidth = 150 // Key
        table.columnModel.getColumn(2).preferredWidth = 300 // Value
        table.columnModel.getColumn(3).preferredWidth = 50 // Length
    }

    private fun setupLayout() {
        // Table in scroll pane
        val tableScrollPane = JBScrollPane(table)
        tableScrollPane.preferredSize = Dimension(600, 300)

        // Preview panel
        val previewContainer = JPanel(BorderLayout())
        previewContainer.border = BorderFactory.createTitledBorder("GB Preview")
        previewContainer.add(previewPanel, BorderLayout.CENTER)
        previewContainer.preferredSize = Dimension(400, 200)

        // Status bar
        statusLabel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        // Split pane
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, previewContainer)
        splitPane.resizeWeight = 0.7

        add(splitPane, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun loadFile() {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        entries = parsePoFile(content)
        tableModel.setEntries(entries)
        updateStatus()
    }

    private fun updatePreview() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0 && selectedRow < entries.size) {
            val entry = entries[selectedRow]
            previewPanel.text = entry.msgstr.ifEmpty { entry.msgid }
        } else {
            previewPanel.text = ""
        }
    }

    private fun updateStatus() {
        val total = entries.size
        val translated = entries.count { it.msgstr.isNotEmpty() }
        val issues = entries.count { hasValidationIssues(it) }

        statusLabel.text = buildString {
            append("$total strings")
            if (file.extension == "po") {
                append(" | $translated translated")
                if (translated < total) {
                    append(" (${total - translated} missing)")
                }
            }
            if (issues > 0) {
                append(" | $issues with issues")
            }
        }
    }

    private fun hasValidationIssues(entry: PoEntry): Boolean {
        val text = entry.msgstr.ifEmpty { entry.msgid }
        val lines = text.split("\n")

        // Check line length
        if (lines.any { it.length > MAX_CHARS_PER_LINE }) return true

        // Check total length
        if (text.length > MAX_TOTAL_CHARS) return true

        return false
    }

    // -------------------------------------------------------------------------
    // PO Parsing (simplified - uses same logic as core PoParser)
    // -------------------------------------------------------------------------

    private data class PoEntry(val context: String, val msgid: String, val msgstr: String)

    private fun parsePoFile(content: String): List<PoEntry> {
        val entries = mutableListOf<PoEntry>()
        val lines = content.lines()

        var currentContext: String? = null
        var currentMsgid: String? = null
        var currentMsgstr: String? = null
        var inMsgid = false
        var inMsgstr = false
        var inMsgctxt = false

        fun finishEntry() {
            if (currentMsgid != null && currentMsgid!!.isNotEmpty()) {
                entries.add(
                    PoEntry(
                        context = currentContext ?: "default",
                        msgid = currentMsgid ?: "",
                        msgstr = currentMsgstr ?: "",
                    )
                )
            }
            currentContext = null
            currentMsgid = null
            currentMsgstr = null
            inMsgid = false
            inMsgstr = false
            inMsgctxt = false
        }

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> finishEntry()
                trimmed.startsWith("#") -> {} // Comments
                trimmed.startsWith("msgctxt ") -> {
                    inMsgctxt = true
                    inMsgid = false
                    inMsgstr = false
                    currentContext = extractQuotedString(trimmed.removePrefix("msgctxt "))
                }
                trimmed.startsWith("msgid ") -> {
                    inMsgctxt = false
                    inMsgid = true
                    inMsgstr = false
                    currentMsgid = extractQuotedString(trimmed.removePrefix("msgid "))
                }
                trimmed.startsWith("msgstr ") -> {
                    inMsgctxt = false
                    inMsgid = false
                    inMsgstr = true
                    currentMsgstr = extractQuotedString(trimmed.removePrefix("msgstr "))
                }
                trimmed.startsWith("\"") -> {
                    val value = extractQuotedString(trimmed)
                    when {
                        inMsgctxt -> currentContext = (currentContext ?: "") + value
                        inMsgid -> currentMsgid = (currentMsgid ?: "") + value
                        inMsgstr -> currentMsgstr = (currentMsgstr ?: "") + value
                    }
                }
            }
        }

        finishEntry()
        return entries
    }

    private fun extractQuotedString(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) {
            return trimmed
        }
        return trimmed
            .drop(1)
            .dropLast(1)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    // -------------------------------------------------------------------------
    // Table Model
    // -------------------------------------------------------------------------

    private inner class PoTableModel : AbstractTableModel() {
        private var data: List<PoEntry> = emptyList()

        fun setEntries(entries: List<PoEntry>) {
            data = entries
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = data.size

        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String =
            when (column) {
                0 -> "Namespace"
                1 -> "Key"
                2 -> "Value"
                3 -> "Len"
                else -> ""
            }

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = data.getOrNull(rowIndex) ?: return ""
            return when (columnIndex) {
                0 -> entry.context
                1 -> entry.msgid
                2 -> entry.msgstr.ifEmpty { "(untranslated)" }
                3 -> entry.msgstr.length.toString()
                else -> ""
            }
        }

        fun getEntry(row: Int): PoEntry? = data.getOrNull(row)
    }

    // -------------------------------------------------------------------------
    // Cell Renderer with validation
    // -------------------------------------------------------------------------

    private inner class PoEntryCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: javax.swing.JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            val entry = tableModel.getEntry(row)
            if (entry != null && !isSelected) {
                when {
                    // Untranslated
                    entry.msgstr.isEmpty() && column == 2 -> {
                        foreground = JBColor.GRAY
                        font = font.deriveFont(Font.ITALIC)
                    }
                    // Too long
                    hasValidationIssues(entry) && column == 2 -> {
                        foreground = JBColor.RED
                    }
                    else -> {
                        foreground = JBColor.foreground()
                        font = font.deriveFont(Font.PLAIN)
                    }
                }
            }

            return component
        }
    }

    companion object {
        /** Maximum characters per line on Game Boy screen. */
        const val MAX_CHARS_PER_LINE = 18

        /** Maximum total characters in a dialog box. */
        const val MAX_TOTAL_CHARS = 90
    }
}
