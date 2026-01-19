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
package io.github.gbkt.intellij.editors.data

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.table.AbstractTableModel

/**
 * Editor panel for balance data tables with visualization.
 *
 * Features:
 * - Spreadsheet-style table editing
 * - Preset templates (exp curves, stat progression, tier multipliers)
 * - Live curve visualization
 * - Code generation preview
 * - Import/export functionality
 */
class BalanceDataTableEditor : JPanel(BorderLayout()) {

    private var model: BalanceDataModel = BalanceDataModel.createExpCurve("ExpCurve", 20)
    private val tableModel = BalanceTableModel()
    private val table = JBTable(tableModel)
    private val curvePanel = CurveVisualizationPanel()
    private val codePreview = JBTextArea()

    private val templateCombo =
        ComboBox(arrayOf("Experience Curve", "Stat Progression", "Tier Multipliers", "Custom"))

    init {
        setupUI()
        setupListeners()
        updateFromModel()
    }

    private fun setupUI() {
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

        toolbar.add(JBLabel("Template:"))
        toolbar.add(templateCombo)

        val newButton = JButton("New")
        val addRowButton = JButton("Add Row")
        val removeRowButton = JButton("Remove Row")
        val exportButton = JButton("Export Code")

        toolbar.add(newButton)
        toolbar.add(addRowButton)
        toolbar.add(removeRowButton)
        toolbar.add(exportButton)

        newButton.addActionListener { createFromTemplate() }
        addRowButton.addActionListener { addRow() }
        removeRowButton.addActionListener { removeSelectedRow() }
        exportButton.addActionListener { showExportDialog() }

        add(toolbar, BorderLayout.NORTH)

        // Table panel
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = BorderFactory.createTitledBorder("Data Table")
        table.autoResizeMode = javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS
        tablePanel.add(JBScrollPane(table), BorderLayout.CENTER)

        // Visualization panel
        val vizPanel = JPanel(BorderLayout())
        vizPanel.border = BorderFactory.createTitledBorder("Curve Visualization")
        vizPanel.add(curvePanel, BorderLayout.CENTER)

        // Code preview panel
        val codePanel = JPanel(BorderLayout())
        codePanel.border = BorderFactory.createTitledBorder("Generated Code")
        codePreview.isEditable = false
        codePreview.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11)
        codePanel.add(JBScrollPane(codePreview), BorderLayout.CENTER)

        // Right side split (visualization + code)
        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, vizPanel, codePanel)
        rightSplit.resizeWeight = 0.6
        rightSplit.dividerLocation = 300

        // Main split (table + right side)
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel, rightSplit)
        mainSplit.resizeWeight = 0.5
        mainSplit.dividerLocation = 400

        add(mainSplit, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        templateCombo.addActionListener {
            // Just update selection, don't auto-create
        }

        tableModel.addTableModelListener {
            updateVisualization()
            updateCodePreview()
        }
    }

    private fun createFromTemplate() {
        val template = templateCombo.selectedItem as String

        model =
            when (template) {
                "Experience Curve" -> BalanceDataModel.createExpCurve("ExpCurve", 20)
                "Stat Progression" ->
                    BalanceDataModel.createStatProgression(
                        "HeroStats",
                        listOf("HP", "ATK", "DEF", "AGL"),
                        20,
                    )
                "Tier Multipliers" -> BalanceDataModel.createTierMultiplier("TierMult")
                else -> createCustomModel()
            }

        updateFromModel()
    }

    private fun createCustomModel(): BalanceDataModel {
        // Show dialog for custom column definition
        val dialog = CustomTableDialog()
        if (dialog.showAndGet()) {
            return dialog.createModel()
        }
        return model // Keep existing if cancelled
    }

    private fun updateFromModel() {
        tableModel.fireTableStructureChanged()
        updateVisualization()
        updateCodePreview()
    }

    private fun updateVisualization() {
        curvePanel.setData(model)

        // Update axis labels based on model type
        when (model.type) {
            BalanceDataModel.DataType.EXP_CURVE -> {
                curvePanel.xAxisLabel = "Level"
                curvePanel.yAxisLabel = "Experience"
            }
            BalanceDataModel.DataType.STAT_PROGRESSION -> {
                curvePanel.xAxisLabel = "Level"
                curvePanel.yAxisLabel = "Stat Value"
            }
            BalanceDataModel.DataType.TIER_MULTIPLIER -> {
                curvePanel.xAxisLabel = "Tier"
                curvePanel.yAxisLabel = "Multiplier"
            }
            else -> {
                curvePanel.xAxisLabel = "Index"
                curvePanel.yAxisLabel = "Value"
            }
        }
    }

    private fun updateCodePreview() {
        codePreview.text = model.toKotlinCode()
    }

    private fun addRow() {
        model.addRow()
        tableModel.fireTableDataChanged()
    }

    private fun removeSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            model.removeRow(selectedRow)
            tableModel.fireTableDataChanged()
        }
    }

    private fun showExportDialog() {
        val code = model.toKotlinCode()
        DialogBuilder()
            .apply {
                setTitle("Exported Code")
                setCenterPanel(
                    JBScrollPane(
                        JBTextArea(code).apply {
                            isEditable = false
                            font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
                        }
                    )
                )
            }
            .show()
    }

    /** Sets the data model. */
    fun setModel(newModel: BalanceDataModel) {
        model = newModel
        updateFromModel()
    }

    /** Gets the current data model. */
    fun getModel(): BalanceDataModel = model

    /** Table model that wraps BalanceDataModel. */
    private inner class BalanceTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = model.rows.size

        override fun getColumnCount(): Int = model.columns.size

        override fun getColumnName(column: Int): String = model.columns[column].name

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            return model.getValue(rowIndex, columnIndex)
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val col = model.columns[columnIndex]
            val value =
                when (col.type) {
                    BalanceDataModel.ColumnType.INT -> (aValue as? String)?.toIntOrNull() ?: 0
                    BalanceDataModel.ColumnType.FLOAT ->
                        (aValue as? String)?.toDoubleOrNull() ?: 0.0
                    BalanceDataModel.ColumnType.STRING -> aValue?.toString() ?: ""
                    BalanceDataModel.ColumnType.TIER -> aValue?.toString() ?: "C"
                }
            model.setValue(rowIndex, columnIndex, value)
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return !model.columns[columnIndex].isKey
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (model.columns[columnIndex].type) {
                BalanceDataModel.ColumnType.INT -> Int::class.java
                BalanceDataModel.ColumnType.FLOAT -> Double::class.java
                else -> String::class.java
            }
        }
    }

    /** Dialog for creating custom data tables. */
    private class CustomTableDialog : com.intellij.openapi.ui.DialogWrapper(null) {
        private val nameField = com.intellij.ui.components.JBTextField("CustomData")
        private val columnsArea = JBTextArea("Column1:INT\nColumn2:INT")

        init {
            init()
            title = "Create Custom Table"
        }

        override fun createCenterPanel(): javax.swing.JComponent {
            val panel = JPanel(GridBagLayout())
            val gbc =
                GridBagConstraints().apply {
                    insets = JBUI.insets(5)
                    fill = GridBagConstraints.HORIZONTAL
                    anchor = GridBagConstraints.WEST
                }

            gbc.gridx = 0
            gbc.gridy = 0
            panel.add(JBLabel("Table Name:"), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            panel.add(nameField, gbc)

            gbc.gridx = 0
            gbc.gridy = 1
            gbc.weightx = 0.0
            panel.add(JBLabel("Columns (name:type):"), gbc)
            gbc.gridx = 1
            gbc.gridy = 1
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            panel.add(JBScrollPane(columnsArea), gbc)

            gbc.gridx = 0
            gbc.gridy = 2
            gbc.gridwidth = 2
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.weighty = 0.0
            panel.add(JBLabel("Types: INT, FLOAT, STRING, TIER"), gbc)

            return panel
        }

        fun createModel(): BalanceDataModel {
            val columns =
                columnsArea.text.lines().mapNotNull { line ->
                    val parts = line.trim().split(":")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val type =
                            when (parts[1].trim().uppercase()) {
                                "INT" -> BalanceDataModel.ColumnType.INT
                                "FLOAT" -> BalanceDataModel.ColumnType.FLOAT
                                "TIER" -> BalanceDataModel.ColumnType.TIER
                                else -> BalanceDataModel.ColumnType.STRING
                            }
                        BalanceDataModel.ColumnDefinition(name, type)
                    } else {
                        null
                    }
                }

            return BalanceDataModel(
                name = nameField.text,
                type = BalanceDataModel.DataType.CUSTOM,
                columns =
                    columns.ifEmpty {
                        listOf(
                            BalanceDataModel.ColumnDefinition(
                                "Value",
                                BalanceDataModel.ColumnType.INT,
                            )
                        )
                    },
                rows = mutableListOf(),
            )
        }
    }
}
