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

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Dashboard panel for viewing and managing the asset pipeline.
 *
 * Features:
 * - Asset list with status indicators
 * - Build progress tracking
 * - Quick actions (build all, rebuild, clean)
 * - Error summary
 */
class AssetPipelineDashboard(private val project: Project) : JPanel(BorderLayout()) {

    private val pipeline = AssetPipeline(project)
    private val tableModel = AssetTableModel()
    private val table = JBTable(tableModel)

    private val statusLabel = JBLabel("Ready")
    private val progressBar = JProgressBar(0, 100)
    private val statsLabel = JBLabel()

    private val scanButton = JButton("Scan")
    private val buildAllButton = JButton("Build All")
    private val buildChangedButton = JButton("Build Changed")
    private val cleanButton = JButton("Clean")

    /** Callback when build is requested. */
    var onBuildRequested: ((List<AssetInfo>) -> Unit)? = null

    /** Callback when clean is requested. */
    var onCleanRequested: (() -> Unit)? = null

    init {
        setupUI()
        setupListeners()

        // Initial scan
        pipeline.scanProject()
        updateStats()
    }

    private fun setupUI() {
        // Header with stats
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val titleLabel = JBLabel("Asset Pipeline")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)
        headerPanel.add(statsLabel, BorderLayout.EAST)

        add(headerPanel, BorderLayout.NORTH)

        // Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.border =
            BorderFactory.createMatteBorder(
                0,
                0,
                1,
                0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
            )

        toolbar.add(scanButton)
        toolbar.add(buildAllButton)
        toolbar.add(buildChangedButton)
        toolbar.add(cleanButton)

        toolbar.add(javax.swing.Box.createHorizontalStrut(20))
        toolbar.add(statusLabel)
        toolbar.add(javax.swing.Box.createHorizontalStrut(10))

        progressBar.isStringPainted = true
        progressBar.isVisible = false
        toolbar.add(progressBar)

        add(toolbar, BorderLayout.NORTH)

        // Table
        table.setDefaultRenderer(Any::class.java, AssetCellRenderer())
        table.rowHeight = 24
        table.columnModel.getColumn(0).preferredWidth = 200 // Name
        table.columnModel.getColumn(1).preferredWidth = 80 // Type
        table.columnModel.getColumn(2).preferredWidth = 100 // Status
        table.columnModel.getColumn(3).preferredWidth = 300 // Path
        table.columnModel.getColumn(4).preferredWidth = 80 // Size

        val scrollPane = JBScrollPane(table)
        scrollPane.border = BorderFactory.createEmptyBorder()
        add(scrollPane, BorderLayout.CENTER)

        // Status bar
        val statusBar = JPanel(BorderLayout())
        statusBar.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        statusBar.add(JBLabel("Double-click an asset to open it"), BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        pipeline.addListener {
            tableModel.fireTableDataChanged()
            updateStats()
        }

        scanButton.addActionListener { pipeline.scanProject() }

        buildAllButton.addActionListener { onBuildRequested?.invoke(pipeline.allAssets) }

        buildChangedButton.addActionListener {
            onBuildRequested?.invoke(pipeline.assetsNeedingBuild)
        }

        cleanButton.addActionListener { onCleanRequested?.invoke() }

        table.addMouseListener(
            object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = table.selectedRow
                        if (row >= 0) {
                            openAsset(pipeline.allAssets[row])
                        }
                    }
                }
            }
        )
    }

    private fun updateStats() {
        val total = pipeline.totalAssets
        val upToDate = pipeline.upToDateCount
        val errors = pipeline.errorCount
        val needsBuild = pipeline.assetsNeedingBuild.size

        statsLabel.text = buildString {
            append("$total assets")
            if (upToDate > 0) append(" | $upToDate up to date")
            if (needsBuild > 0) append(" | $needsBuild need build")
            if (errors > 0) append(" | $errors errors")
        }

        buildChangedButton.isEnabled = needsBuild > 0
        buildChangedButton.text =
            if (needsBuild > 0) "Build Changed ($needsBuild)" else "Build Changed"
    }

    private fun openAsset(asset: AssetInfo) {
        // TODO: Open file in editor
        val basePath = project.basePath ?: return
        val file = java.io.File(basePath, asset.sourcePath)
        if (file.exists()) {
            val virtualFile =
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(file)
            if (virtualFile != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    .openFile(virtualFile, true)
            }
        }
    }

    /** Shows build progress. */
    fun showProgress(current: Int, total: Int, message: String) {
        progressBar.isVisible = true
        progressBar.maximum = total
        progressBar.value = current
        progressBar.string = message
        statusLabel.text = message
    }

    /** Hides build progress. */
    fun hideProgress() {
        progressBar.isVisible = false
        statusLabel.text = "Ready"
    }

    /** Gets the pipeline instance. */
    fun getPipeline(): AssetPipeline = pipeline

    /** Table model for assets. */
    private inner class AssetTableModel : AbstractTableModel() {
        private val columns = arrayOf("Name", "Type", "Status", "Path", "Size")

        override fun getRowCount(): Int = pipeline.allAssets.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val asset = pipeline.allAssets.getOrNull(rowIndex) ?: return ""
            return when (columnIndex) {
                0 -> asset.name
                1 -> asset.type.displayName
                2 -> asset.status.displayName
                3 -> asset.sourcePath
                4 -> formatSize(asset.sizeBytes)
                else -> ""
            }
        }

        private fun formatSize(bytes: Long?): String {
            if (bytes == null) return "-"
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }

    /** Custom cell renderer for status colors. */
    private inner class AssetCellRenderer : DefaultTableCellRenderer() {
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

            val asset = pipeline.allAssets.getOrNull(row)
            if (asset != null && !isSelected) {
                foreground =
                    when (asset.status) {
                        AssetInfo.AssetStatus.UP_TO_DATE -> JBColor.foreground()
                        AssetInfo.AssetStatus.NEEDS_BUILD ->
                            JBColor(Color(255, 165, 0), Color(255, 200, 100))
                        AssetInfo.AssetStatus.BUILDING -> JBColor.BLUE
                        AssetInfo.AssetStatus.ERROR -> JBColor.RED
                        AssetInfo.AssetStatus.NOT_FOUND -> JBColor.GRAY
                    }

                // Highlight status column
                if (column == 2) {
                    background =
                        when (asset.status) {
                            AssetInfo.AssetStatus.UP_TO_DATE ->
                                JBColor(Color(200, 255, 200), Color(50, 100, 50))
                            AssetInfo.AssetStatus.NEEDS_BUILD ->
                                JBColor(Color(255, 240, 200), Color(100, 80, 50))
                            AssetInfo.AssetStatus.ERROR ->
                                JBColor(Color(255, 200, 200), Color(100, 50, 50))
                            else -> background
                        }
                }
            }

            return component
        }
    }
}
