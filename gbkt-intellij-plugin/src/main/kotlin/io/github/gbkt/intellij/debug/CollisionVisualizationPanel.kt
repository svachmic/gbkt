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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
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
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Collision visualization panel for debugging hitboxes and collision detection.
 *
 * Features:
 * - Visual representation of collision boxes
 * - Collision layer filtering
 * - Collision pair highlighting
 * - Distance and overlap measurements
 */
class CollisionVisualizationPanel : JPanel(BorderLayout()) {

    private val visualPanel = CollisionCanvas()

    private val showEntityBoxes = JCheckBox("Entity Boxes", true)
    private val showTileCollision = JCheckBox("Tile Collision", true)
    private val showTriggers = JCheckBox("Triggers", true)
    private val showCollisionPairs = JCheckBox("Collision Pairs", true)
    private val zoomSpinner = JSpinner(SpinnerNumberModel(2, 1, 4, 1))

    private val statusLabel = JBLabel("Collision visualization")

    // Demo collision data
    private val collisionBoxes = mutableListOf<CollisionBox>()
    private val tileCollisions = mutableListOf<TileCollision>()
    private val triggers = mutableListOf<TriggerZone>()
    private val collisionPairs = mutableListOf<CollisionPair>()

    init {
        setupUI()
        setupListeners()
        loadDemoData()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val titleLabel = JBLabel("Collision Visualization")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        add(headerPanel, BorderLayout.NORTH)

        // Main content
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // Controls
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        controlPanel.border = BorderFactory.createTitledBorder("Display Options")
        controlPanel.add(showEntityBoxes)
        controlPanel.add(showTileCollision)
        controlPanel.add(showTriggers)
        controlPanel.add(showCollisionPairs)
        controlPanel.add(Box.createHorizontalStrut(20))
        controlPanel.add(JBLabel("Zoom:"))
        controlPanel.add(zoomSpinner)
        contentPanel.add(controlPanel)

        // Visualization canvas
        val canvasPanel = JPanel(BorderLayout())
        canvasPanel.border = BorderFactory.createTitledBorder("Collision View")
        canvasPanel.add(visualPanel, BorderLayout.CENTER)
        contentPanel.add(canvasPanel)

        // Legend
        val legendPanel = createLegendPanel()
        contentPanel.add(legendPanel)

        add(contentPanel, BorderLayout.CENTER)

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

    private fun createLegendPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 15, 5))
        panel.border = BorderFactory.createTitledBorder("Legend")

        // Entity hitbox
        panel.add(createLegendItem(Color(255, 100, 100, 150), "Entity Hitbox"))
        // Tile collision
        panel.add(createLegendItem(Color(100, 100, 255, 150), "Tile Collision"))
        // Trigger
        panel.add(createLegendItem(Color(100, 255, 100, 150), "Trigger Zone"))
        // Active collision
        panel.add(createLegendItem(Color(255, 255, 0), "Active Collision"))

        return panel
    }

    private fun createLegendItem(color: Color, label: String): JPanel {
        val item = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))

        val colorBox =
            object : JPanel() {
                init {
                    preferredSize = Dimension(16, 16)
                }

                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    g.color = color
                    g.fillRect(0, 0, width, height)
                    g.color = color.darker()
                    g.drawRect(0, 0, width - 1, height - 1)
                }
            }

        item.add(colorBox)
        item.add(JBLabel(label))
        return item
    }

    private fun setupListeners() {
        showEntityBoxes.addActionListener {
            visualPanel.showEntityBoxes = showEntityBoxes.isSelected
            visualPanel.repaint()
        }

        showTileCollision.addActionListener {
            visualPanel.showTileCollision = showTileCollision.isSelected
            visualPanel.repaint()
        }

        showTriggers.addActionListener {
            visualPanel.showTriggers = showTriggers.isSelected
            visualPanel.repaint()
        }

        showCollisionPairs.addActionListener {
            visualPanel.showCollisionPairs = showCollisionPairs.isSelected
            visualPanel.repaint()
        }

        zoomSpinner.addChangeListener {
            visualPanel.zoom = zoomSpinner.value as Int
            visualPanel.updateSize()
            visualPanel.repaint()
        }
    }

    private fun loadDemoData() {
        // Demo collision boxes (entities)
        collisionBoxes.add(CollisionBox("player", 80, 80, 6, 8, CollisionLayer.PLAYER))
        collisionBoxes.add(CollisionBox("enemy1", 120, 72, 8, 8, CollisionLayer.ENEMY))
        collisionBoxes.add(CollisionBox("enemy2", 40, 100, 8, 8, CollisionLayer.ENEMY))
        collisionBoxes.add(CollisionBox("projectile", 95, 82, 4, 4, CollisionLayer.PROJECTILE))

        // Demo tile collisions
        for (x in 0..19) {
            tileCollisions.add(TileCollision(x * 8, 136, 8, 8)) // Floor
        }
        tileCollisions.add(TileCollision(0, 0, 8, 144)) // Left wall
        tileCollisions.add(TileCollision(152, 0, 8, 144)) // Right wall
        tileCollisions.add(TileCollision(56, 80, 24, 8)) // Platform

        // Demo triggers
        triggers.add(TriggerZone("door", 72, 120, 16, 16, TriggerType.TRANSITION))
        triggers.add(TriggerZone("item", 100, 64, 8, 8, TriggerType.PICKUP))

        // Demo collision pairs (active collisions)
        collisionPairs.add(CollisionPair("projectile", "enemy1", true))

        visualPanel.collisionBoxes = collisionBoxes
        visualPanel.tileCollisions = tileCollisions
        visualPanel.triggers = triggers
        visualPanel.collisionPairs = collisionPairs
        visualPanel.repaint()

        updateStatus()
    }

    private fun updateStatus() {
        val activeCount = collisionPairs.count { it.isColliding }
        statusLabel.text =
            "${collisionBoxes.size} entities, " +
                "${tileCollisions.size} tiles, " +
                "${triggers.size} triggers, " +
                "$activeCount active collisions"
    }

    /** Collision box data. */
    data class CollisionBox(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val layer: CollisionLayer,
    )

    /** Tile collision data. */
    data class TileCollision(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Trigger zone data. */
    data class TriggerZone(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val type: TriggerType,
    )

    /** Collision pair representing two potentially colliding objects. */
    data class CollisionPair(val entityA: String, val entityB: String, val isColliding: Boolean)

    /** Collision layers. */
    enum class CollisionLayer(val color: Color) {
        PLAYER(Color(100, 200, 100)),
        ENEMY(Color(255, 100, 100)),
        PROJECTILE(Color(255, 200, 100)),
        ITEM(Color(100, 100, 255)),
        NPC(Color(200, 100, 255)),
    }

    /** Trigger types. */
    enum class TriggerType(val color: Color) {
        TRANSITION(Color(100, 255, 100)),
        PICKUP(Color(255, 255, 100)),
        DAMAGE(Color(255, 100, 100)),
        EVENT(Color(100, 200, 255)),
    }

    /** Canvas for drawing collision visualization. */
    private inner class CollisionCanvas : JPanel() {
        var collisionBoxes: List<CollisionBox> = emptyList()
        var tileCollisions: List<TileCollision> = emptyList()
        var triggers: List<TriggerZone> = emptyList()
        var collisionPairs: List<CollisionPair> = emptyList()

        var showEntityBoxes = true
        var showTileCollision = true
        var showTriggers = true
        var showCollisionPairs = true
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

            // Screen background
            g2.color = JBColor(Color(50, 50, 50), Color(50, 50, 50))
            g2.fillRect(PADDING, PADDING, GB_WIDTH * zoom, GB_HEIGHT * zoom)

            // Tile collisions
            if (showTileCollision) {
                for (tile in tileCollisions) {
                    drawTileCollision(g2, tile)
                }
            }

            // Triggers
            if (showTriggers) {
                for (trigger in triggers) {
                    drawTrigger(g2, trigger)
                }
            }

            // Entity boxes
            if (showEntityBoxes) {
                for (box in collisionBoxes) {
                    drawCollisionBox(g2, box)
                }
            }

            // Collision pairs
            if (showCollisionPairs) {
                for (pair in collisionPairs.filter { it.isColliding }) {
                    drawCollisionPair(g2, pair)
                }
            }

            // Screen border
            g2.color = JBColor(Color(100, 100, 100), Color(100, 100, 100))
            g2.drawRect(PADDING, PADDING, GB_WIDTH * zoom, GB_HEIGHT * zoom)
        }

        private fun drawTileCollision(g2: Graphics2D, tile: TileCollision) {
            val x = PADDING + tile.x * zoom
            val y = PADDING + tile.y * zoom
            val w = tile.width * zoom
            val h = tile.height * zoom

            g2.color = Color(100, 100, 255, 80)
            g2.fillRect(x, y, w, h)
            g2.color = Color(100, 100, 255, 150)
            g2.drawRect(x, y, w, h)
        }

        private fun drawTrigger(g2: Graphics2D, trigger: TriggerZone) {
            val x = PADDING + trigger.x * zoom
            val y = PADDING + trigger.y * zoom
            val w = trigger.width * zoom
            val h = trigger.height * zoom

            // Fill with trigger type color
            val fillColor =
                Color(trigger.type.color.red, trigger.type.color.green, trigger.type.color.blue, 60)
            g2.color = fillColor
            g2.fillRect(x, y, w, h)

            // Dashed border
            g2.color = trigger.type.color
            g2.stroke =
                BasicStroke(
                    1f,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10f,
                    floatArrayOf(3f),
                    0f,
                )
            g2.drawRect(x, y, w, h)
            g2.stroke = BasicStroke(1f)

            // Label
            g2.font = g2.font.deriveFont(8f)
            g2.drawString(trigger.name, x + 2, y + h - 2)
        }

        private fun drawCollisionBox(g2: Graphics2D, box: CollisionBox) {
            val x = PADDING + box.x * zoom
            val y = PADDING + box.y * zoom
            val w = box.width * zoom
            val h = box.height * zoom

            // Check if in active collision
            val isColliding =
                collisionPairs.any { pair ->
                    pair.isColliding && (pair.entityA == box.name || pair.entityB == box.name)
                }

            // Fill
            val fillColor =
                if (isColliding) {
                    Color(255, 255, 0, 100)
                } else {
                    Color(box.layer.color.red, box.layer.color.green, box.layer.color.blue, 100)
                }
            g2.color = fillColor
            g2.fillRect(x, y, w, h)

            // Border
            g2.color = if (isColliding) Color.YELLOW else box.layer.color
            g2.stroke = BasicStroke(if (isColliding) 2f else 1f)
            g2.drawRect(x, y, w, h)
            g2.stroke = BasicStroke(1f)

            // Label
            g2.color = Color.WHITE
            g2.font = g2.font.deriveFont(8f)
            g2.drawString(box.name, x, y - 2)
        }

        private fun drawCollisionPair(g2: Graphics2D, pair: CollisionPair) {
            val boxA = collisionBoxes.find { it.name == pair.entityA } ?: return
            val boxB = collisionBoxes.find { it.name == pair.entityB } ?: return

            val centerAX = PADDING + (boxA.x + boxA.width / 2) * zoom
            val centerAY = PADDING + (boxA.y + boxA.height / 2) * zoom
            val centerBX = PADDING + (boxB.x + boxB.width / 2) * zoom
            val centerBY = PADDING + (boxB.y + boxB.height / 2) * zoom

            // Draw line between colliding entities
            g2.color = Color(255, 255, 0, 200)
            g2.stroke = BasicStroke(2f)
            g2.drawLine(centerAX, centerAY, centerBX, centerBY)
            g2.stroke = BasicStroke(1f)
        }
    }

    companion object {
        private const val PADDING = 10
        private const val GB_WIDTH = 160
        private const val GB_HEIGHT = 144
    }
}
