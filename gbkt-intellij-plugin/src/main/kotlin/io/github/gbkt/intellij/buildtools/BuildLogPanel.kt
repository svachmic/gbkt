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
package io.github.gbkt.intellij.buildtools

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.regex.Pattern
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Build log panel with clickable error navigation.
 *
 * Features:
 * - Displays build output with syntax highlighting
 * - Clickable file:line references
 * - Error/warning filtering
 * - Clear and export functionality
 */
class BuildLogPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val logModel = DefaultListModel<LogEntry>()
    private val logList = JList(logModel)

    private val statusLabel = JBLabel("No build output")
    private val errorCountLabel = JBLabel()
    private val warningCountLabel = JBLabel()

    private val clearButton = JButton("Clear")
    private val showErrorsOnly = JButton("Errors Only")
    private val exportButton = JButton("Export")

    private var showOnlyErrors = false
    private val allEntries = mutableListOf<LogEntry>()

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        val titleLabel = JBLabel("Build Log")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 13f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val statsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        errorCountLabel.foreground = JBColor.RED
        warningCountLabel.foreground = JBColor(Color(255, 165, 0), Color(255, 200, 100))
        statsPanel.add(errorCountLabel)
        statsPanel.add(warningCountLabel)
        headerPanel.add(statsPanel, BorderLayout.EAST)

        add(headerPanel, BorderLayout.NORTH)

        // Log list
        logList.cellRenderer = LogEntryRenderer()
        logList.fixedCellHeight = 20

        val scrollPane = JBScrollPane(logList)
        scrollPane.border = BorderFactory.createEmptyBorder()
        add(scrollPane, BorderLayout.CENTER)

        // Footer toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        toolbar.border =
            BorderFactory.createMatteBorder(
                1,
                0,
                0,
                0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
            )

        toolbar.add(clearButton)
        toolbar.add(showErrorsOnly)
        toolbar.add(exportButton)
        toolbar.add(Box.createHorizontalStrut(20))
        toolbar.add(statusLabel)

        add(toolbar, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        clearButton.addActionListener { clear() }

        showErrorsOnly.addActionListener {
            showOnlyErrors = !showOnlyErrors
            showErrorsOnly.text = if (showOnlyErrors) "Show All" else "Errors Only"
            refreshList()
        }

        exportButton.addActionListener { exportLog() }

        logList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val index = logList.locationToIndex(e.point)
                        if (index >= 0) {
                            val entry = logModel.getElementAt(index)
                            navigateToSource(entry)
                        }
                    }
                }
            }
        )
    }

    /** Appends a line to the build log. */
    fun appendLine(line: String) {
        val entry = parseLine(line)
        allEntries.add(entry)

        if (!showOnlyErrors || entry.type == LogEntryType.ERROR) {
            logModel.addElement(entry)
            logList.ensureIndexIsVisible(logModel.size - 1)
        }

        updateStats()
    }

    /** Appends multiple lines to the build log. */
    fun appendLines(lines: List<String>) {
        lines.forEach { appendLine(it) }
    }

    /** Clears the build log. */
    fun clear() {
        allEntries.clear()
        logModel.clear()
        updateStats()
        statusLabel.text = "No build output"
    }

    /** Sets the build status message. */
    fun setStatus(status: String) {
        statusLabel.text = status
    }

    private fun parseLine(line: String): LogEntry {
        // Try to extract file:line information
        val fileLinePattern = Pattern.compile("([\\w./\\\\-]+\\.\\w+):(\\d+)(?::(\\d+))?")
        val matcher = fileLinePattern.matcher(line)

        var filePath: String? = null
        var lineNumber: Int? = null
        var column: Int? = null

        if (matcher.find()) {
            filePath = matcher.group(1)
            lineNumber = matcher.group(2).toIntOrNull()
            column = matcher.group(3)?.toIntOrNull()
        }

        // Determine entry type
        val type =
            when {
                line.contains("error:", ignoreCase = true) -> LogEntryType.ERROR
                line.contains("warning:", ignoreCase = true) -> LogEntryType.WARNING
                line.contains("note:", ignoreCase = true) -> LogEntryType.INFO
                line.startsWith("[") && line.contains("]") -> LogEntryType.PROGRESS
                else -> LogEntryType.NORMAL
            }

        return LogEntry(line, type, filePath, lineNumber, column)
    }

    private fun refreshList() {
        logModel.clear()
        val filtered =
            if (showOnlyErrors) {
                allEntries.filter { it.type == LogEntryType.ERROR }
            } else {
                allEntries
            }
        filtered.forEach { logModel.addElement(it) }
    }

    private fun updateStats() {
        val errorCount = allEntries.count { it.type == LogEntryType.ERROR }
        val warningCount = allEntries.count { it.type == LogEntryType.WARNING }

        errorCountLabel.text = if (errorCount > 0) "$errorCount errors" else ""
        warningCountLabel.text = if (warningCount > 0) "$warningCount warnings" else ""
    }

    private fun navigateToSource(entry: LogEntry) {
        val filePath = entry.filePath ?: return
        val lineNumber = entry.lineNumber ?: 1

        // Try to find the file
        val basePath = project.basePath ?: return
        val file = File(basePath, filePath)

        val virtualFile =
            if (file.exists()) {
                LocalFileSystem.getInstance().findFileByIoFile(file)
            } else {
                // Try as absolute path
                LocalFileSystem.getInstance().findFileByPath(filePath)
            }

        if (virtualFile != null) {
            val descriptor =
                OpenFileDescriptor(project, virtualFile, lineNumber - 1, entry.column ?: 0)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }

    private fun exportLog() {
        val content = allEntries.joinToString("\n") { it.text }
        // Deferred (SEED-024): native save-to-file dialog; stdout dump is the interim export path
        println(content)
    }

    /** Log entry types. */
    enum class LogEntryType {
        NORMAL,
        INFO,
        WARNING,
        ERROR,
        PROGRESS,
    }

    /** A single log entry. */
    data class LogEntry(
        val text: String,
        val type: LogEntryType,
        val filePath: String?,
        val lineNumber: Int?,
        val column: Int?,
    ) {
        val hasLocation: Boolean
            get() = filePath != null && lineNumber != null
    }

    /** Custom renderer for log entries. */
    private inner class LogEntryRenderer : ListCellRenderer<LogEntry> {
        private val label = JBLabel()

        init {
            label.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            label.border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
        }

        override fun getListCellRendererComponent(
            list: JList<out LogEntry>,
            value: LogEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            label.text = value.text

            if (isSelected) {
                label.background = list.selectionBackground
                label.foreground = list.selectionForeground
                label.isOpaque = true
            } else {
                label.isOpaque = false
                label.foreground =
                    when (value.type) {
                        LogEntryType.ERROR -> JBColor.RED
                        LogEntryType.WARNING -> JBColor(Color(255, 165, 0), Color(255, 200, 100))
                        LogEntryType.INFO -> JBColor.BLUE
                        LogEntryType.PROGRESS -> JBColor.GRAY
                        LogEntryType.NORMAL -> JBColor.foreground()
                    }
            }

            // Show clickable cursor for entries with location
            if (value.hasLocation) {
                label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            } else {
                label.cursor = Cursor.getDefaultCursor()
            }

            return label
        }
    }
}
