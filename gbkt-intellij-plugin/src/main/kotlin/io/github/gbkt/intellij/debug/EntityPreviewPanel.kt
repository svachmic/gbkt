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
package io.github.gbkt.intellij.debug

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.table.AbstractTableModel

/**
 * Live entity preview panel for visualizing game entities.
 *
 * Features:
 * - Visual representation of entity positions on GB screen
 * - Entity property table
 * - Collision box overlay
 * - Selection and inspection
 */
class EntityPreviewPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val visualPanel = EntityVisualizationPanel()
    private val tableModel = EntityTableModel()
    private val entityTable = JBTable(tableModel)

    private val showHitboxes = JCheckBox("Show Hitboxes", true)
    private val showLabels = JCheckBox("Show Labels", true)
    private val showGrid = JCheckBox("Show Grid", false)
    private val zoomSpinner = JSpinner(SpinnerNumberModel(2, 1, 4, 1))

    private val refreshButton = JButton("Refresh")
    private val addEntityButton = JButton("Add Entity")
    private val removeEntityButton = JButton("Remove")

    private val statusLabel = JBLabel("No entities loaded")

    // Demo entities for preview
    private val entities = mutableListOf<EntityInfo>()

    init {
        setupUI()
        setupListeners()
        loadDemoEntities()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val titleLabel = JBLabel("Entity Preview")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        add(headerPanel, BorderLayout.NORTH)

        // Main split - visualization on left, table on right
        val mainPanel = JPanel(BorderLayout())

        // Visualization panel
        val vizContainer = JPanel(BorderLayout())
        vizContainer.border = BorderFactory.createTitledBorder("Screen View (160×144)")
        vizContainer.add(JBScrollPane(visualPanel), BorderLayout.CENTER)

        // Controls
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        controlPanel.add(showHitboxes)
        controlPanel.add(showLabels)
        controlPanel.add(showGrid)
        controlPanel.add(Box.createHorizontalStrut(10))
        controlPanel.add(JBLabel("Zoom:"))
        controlPanel.add(zoomSpinner)
        vizContainer.add(controlPanel, BorderLayout.SOUTH)

        mainPanel.add(vizContainer, BorderLayout.CENTER)

        // Entity table
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = BorderFactory.createTitledBorder("Entities")
        tablePanel.preferredSize = Dimension(300, 0)

        entityTable.rowHeight = 22
        tablePanel.add(JBScrollPane(entityTable), BorderLayout.CENTER)

        val tableButtons = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        tableButtons.add(addEntityButton)
        tableButtons.add(removeEntityButton)
        tableButtons.add(Box.createHorizontalStrut(10))
        tableButtons.add(refreshButton)
        tablePanel.add(tableButtons, BorderLayout.SOUTH)

        mainPanel.add(tablePanel, BorderLayout.EAST)

        add(mainPanel, BorderLayout.CENTER)

        // Status bar
        val statusBar = JPanel(BorderLayout())
        statusBar.border =
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    1,
                    0,
                    0,
                    0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                ),
                BorderFactory.createEmptyBorder(5, 10, 5, 10),
            )
        statusBar.add(statusLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        showHitboxes.addActionListener {
            visualPanel.showHitboxes = showHitboxes.isSelected
            visualPanel.repaint()
        }

        showLabels.addActionListener {
            visualPanel.showLabels = showLabels.isSelected
            visualPanel.repaint()
        }

        showGrid.addActionListener {
            visualPanel.showGrid = showGrid.isSelected
            visualPanel.repaint()
        }

        zoomSpinner.addChangeListener {
            visualPanel.zoom = zoomSpinner.value as Int
            visualPanel.updateSize()
            visualPanel.repaint()
        }

        entityTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = entityTable.selectedRow
                visualPanel.selectedEntity = if (row >= 0) entities.getOrNull(row) else null
                visualPanel.repaint()
            }
        }

        addEntityButton.addActionListener { addDemoEntity() }
        removeEntityButton.addActionListener { removeSelectedEntity() }
        refreshButton.addActionListener { refresh() }
    }

    private fun loadDemoEntities() {
        entities.clear()
        entities.add(
            EntityInfo(
                name = "player",
                x = 80,
                y = 72,
                width = 8,
                height = 16,
                hitboxX = 1,
                hitboxY = 8,
                hitboxW = 6,
                hitboxH = 8,
                sprite = "player.png",
                state = "idle",
            )
        )
        entities.add(
            EntityInfo(
                name = "enemy",
                x = 120,
                y = 80,
                width = 8,
                height = 8,
                hitboxX = 0,
                hitboxY = 0,
                hitboxW = 8,
                hitboxH = 8,
                sprite = "enemy.png",
                state = "patrol",
            )
        )
        entities.add(
            EntityInfo(
                name = "coin",
                x = 40,
                y = 60,
                width = 8,
                height = 8,
                hitboxX = 1,
                hitboxY = 1,
                hitboxW = 6,
                hitboxH = 6,
                sprite = "coin.png",
                state = "spinning",
            )
        )

        visualPanel.entities = entities
        tableModel.fireTableDataChanged()
        updateStatus()
    }

    private fun addDemoEntity() {
        val newEntity =
            EntityInfo(
                name = "entity_${entities.size}",
                x = (Math.random() * 140 + 10).toInt(),
                y = (Math.random() * 124 + 10).toInt(),
                width = 8,
                height = 8,
                hitboxX = 0,
                hitboxY = 0,
                hitboxW = 8,
                hitboxH = 8,
                sprite = "sprite.png",
                state = "default",
            )
        entities.add(newEntity)
        tableModel.fireTableDataChanged()
        visualPanel.repaint()
        updateStatus()
    }

    private fun removeSelectedEntity() {
        val row = entityTable.selectedRow
        if (row >= 0 && row < entities.size) {
            entities.removeAt(row)
            tableModel.fireTableDataChanged()
            visualPanel.repaint()
            updateStatus()
        }
    }

    private fun refresh() {
        // In a real implementation, this would reload entity data from the game
        tableModel.fireTableDataChanged()
        visualPanel.repaint()
        updateStatus()
    }

    private fun updateStatus() {
        statusLabel.text = "${entities.size} entities"
    }

    /** Information about a game entity. */
    data class EntityInfo(
        val name: String,
        var x: Int,
        var y: Int,
        val width: Int,
        val height: Int,
        val hitboxX: Int,
        val hitboxY: Int,
        val hitboxW: Int,
        val hitboxH: Int,
        val sprite: String,
        val state: String,
    )

    /** Table model for entity list. */
    private inner class EntityTableModel : AbstractTableModel() {
        private val columns = arrayOf("Name", "Position", "Size", "State")

        override fun getRowCount(): Int = entities.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entity = entities.getOrNull(rowIndex) ?: return ""
            return when (columnIndex) {
                0 -> entity.name
                1 -> "(${entity.x}, ${entity.y})"
                2 -> "${entity.width}×${entity.height}"
                3 -> entity.state
                else -> ""
            }
        }
    }

    /** Visual panel showing entities on a GB screen representation. */
    private inner class EntityVisualizationPanel : JPanel() {
        var entities: List<EntityInfo> = emptyList()
        var selectedEntity: EntityInfo? = null
        var showHitboxes = true
        var showLabels = true
        var showGrid = false
        var zoom = 2

        init {
            background = JBColor(Color(30, 30, 30), Color(30, 30, 30))
            updateSize()
        }

        fun updateSize() {
            val w = GB_WIDTH * zoom + PADDING * 2
            val h = GB_HEIGHT * zoom + PADDING * 2
            preferredSize = Dimension(w, h)
            minimumSize = Dimension(w, h)
            revalidate()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Draw GB screen background
            g2.color = GB_SCREEN_COLOR
            g2.fillRect(PADDING, PADDING, GB_WIDTH * zoom, GB_HEIGHT * zoom)

            // Draw grid
            if (showGrid) {
                drawGrid(g2)
            }

            // Draw entities
            for (entity in entities) {
                drawEntity(g2, entity, entity == selectedEntity)
            }

            // Draw screen border
            g2.color = JBColor(Color(100, 100, 100), Color(100, 100, 100))
            g2.drawRect(PADDING, PADDING, GB_WIDTH * zoom, GB_HEIGHT * zoom)
        }

        private fun drawGrid(g2: Graphics2D) {
            g2.color = Color(60, 60, 60, 100)

            // 8-pixel grid
            for (x in 0..GB_WIDTH step 8) {
                val px = PADDING + x * zoom
                g2.drawLine(px, PADDING, px, PADDING + GB_HEIGHT * zoom)
            }
            for (y in 0..GB_HEIGHT step 8) {
                val py = PADDING + y * zoom
                g2.drawLine(PADDING, py, PADDING + GB_WIDTH * zoom, py)
            }
        }

        private fun drawEntity(g2: Graphics2D, entity: EntityInfo, selected: Boolean) {
            val x = PADDING + entity.x * zoom
            val y = PADDING + entity.y * zoom
            val w = entity.width * zoom
            val h = entity.height * zoom

            // Entity sprite placeholder
            g2.color =
                if (selected) {
                    JBColor(Color(255, 200, 100), Color(255, 200, 100))
                } else {
                    JBColor(Color(100, 150, 255), Color(100, 150, 255))
                }
            g2.fillRect(x, y, w, h)

            // Entity border
            g2.color =
                if (selected) {
                    JBColor.YELLOW
                } else {
                    JBColor.WHITE
                }
            g2.drawRect(x, y, w, h)

            // Hitbox
            if (showHitboxes) {
                val hx = x + entity.hitboxX * zoom
                val hy = y + entity.hitboxY * zoom
                val hw = entity.hitboxW * zoom
                val hh = entity.hitboxH * zoom

                g2.color = Color(255, 0, 0, 100)
                g2.fillRect(hx, hy, hw, hh)
                g2.color = Color(255, 0, 0)
                g2.drawRect(hx, hy, hw, hh)
            }

            // Label
            if (showLabels) {
                g2.color = JBColor.WHITE
                g2.font = g2.font.deriveFont(9f)
                g2.drawString(entity.name, x, y - 2)
            }
        }
    }

    companion object {
        private const val PADDING = 10
        private const val GB_WIDTH = 160
        private const val GB_HEIGHT = 144
        private val GB_SCREEN_COLOR = JBColor(Color(155, 188, 15), Color(50, 60, 30))
    }
}
