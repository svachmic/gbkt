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
package io.github.gbkt.intellij.editors.tilemap

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.JToggleButton
import javax.swing.SwingConstants

/**
 * Main tilemap editor panel.
 *
 * Features:
 * - Tilemap view with zoom and grid
 * - Tileset palette for tile selection
 * - Tool palette (brush, fill, eraser, select)
 * - Object placement panel
 * - Exit editor panel
 * - Properties panel
 * - Code generation
 */
class TilemapEditorPanel : JPanel(BorderLayout()) {

    private val tilemapPanel = TilemapPanel()
    private val tilesetPanel = TilesetPanel()
    private val codeArea = JBTextArea()

    // Toolbar controls
    private val zoomSlider = JSlider(SwingConstants.HORIZONTAL, 1, 4, 2)
    private val gridCheckbox = JBCheckBox("Grid", true)
    private val attributesCheckbox = JBCheckBox("Attributes", false)
    private val objectsCheckbox = JBCheckBox("Objects", true)
    private val exitsCheckbox = JBCheckBox("Exits", true)

    // Tool buttons
    private val brushButton = JToggleButton("Brush")
    private val fillButton = JToggleButton("Fill")
    private val eraserButton = JToggleButton("Eraser")
    private val selectButton = JToggleButton("Select")

    // Map properties
    private val mapNameField = JBTextField("untitled")
    private val mapWidthField = JBTextField("20")
    private val mapHeightField = JBTextField("18")

    // Object panel
    private val objectTypeCombo =
        ComboBox(MapObjectData.ObjectType.entries.map { it.displayName }.toTypedArray())
    private val objectIdField = JBTextField()
    private val objectXField = JBTextField()
    private val objectYField = JBTextField()

    // Exit panel
    private val exitIdField = JBTextField()
    private val exitFromXField = JBTextField()
    private val exitFromYField = JBTextField()
    private val exitToMapField = JBTextField()
    private val exitToXField = JBTextField()
    private val exitToYField = JBTextField()
    private val exitDirectionCombo =
        ComboBox(ExitData.Direction.entries.map { it.name }.toTypedArray())

    private val objects = mutableListOf<MapObjectData>()
    private val exits = mutableListOf<ExitData>()

    init {
        setupToolbar()
        setupMainContent()
        setupListeners()

        // Create default tilemap
        createNewMap(20, 18)

        // Create sample tileset
        createSampleTileset()
    }

    private fun setupToolbar() {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.border =
            BorderFactory.createMatteBorder(
                0,
                0,
                1,
                0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
            )

        // Zoom controls
        toolbar.add(JBLabel("Zoom:"))
        zoomSlider.majorTickSpacing = 1
        zoomSlider.paintTicks = true
        zoomSlider.snapToTicks = true
        toolbar.add(zoomSlider)

        toolbar.add(javax.swing.Box.createHorizontalStrut(10))

        // View toggles
        toolbar.add(gridCheckbox)
        toolbar.add(attributesCheckbox)
        toolbar.add(objectsCheckbox)
        toolbar.add(exitsCheckbox)

        toolbar.add(javax.swing.Box.createHorizontalStrut(20))

        // Tool buttons
        val toolGroup = ButtonGroup()
        toolGroup.add(brushButton)
        toolGroup.add(fillButton)
        toolGroup.add(eraserButton)
        toolGroup.add(selectButton)
        brushButton.isSelected = true

        toolbar.add(JBLabel("Tool:"))
        toolbar.add(brushButton)
        toolbar.add(fillButton)
        toolbar.add(eraserButton)
        toolbar.add(selectButton)

        add(toolbar, BorderLayout.NORTH)
    }

    private fun setupMainContent() {
        // Left: Tilemap view
        val mapScrollPane = JBScrollPane(tilemapPanel)
        mapScrollPane.border = BorderFactory.createTitledBorder("Tilemap")

        // Right: Tabbed pane with tileset, objects, exits, properties
        val rightTabs = JBTabbedPane()

        // Tileset tab
        val tilesetScrollPane = JBScrollPane(tilesetPanel)
        tilesetScrollPane.border = BorderFactory.createEmptyBorder()
        rightTabs.addTab("Tileset", tilesetScrollPane)

        // Objects tab
        rightTabs.addTab("Objects", createObjectsPanel())

        // Exits tab
        rightTabs.addTab("Exits", createExitsPanel())

        // Properties tab
        rightTabs.addTab("Properties", createPropertiesPanel())

        // Code tab
        codeArea.isEditable = false
        codeArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11)
        rightTabs.addTab("Code", JBScrollPane(codeArea))

        // Split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapScrollPane, rightTabs)
        splitPane.resizeWeight = 0.7
        splitPane.dividerLocation = 600

        add(splitPane, BorderLayout.CENTER)
    }

    private fun createObjectsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val formPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(3)
                anchor = GridBagConstraints.WEST
            }

        var row = 0

        gbc.gridx = 0
        gbc.gridy = row
        formPanel.add(JBLabel("Type:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(objectTypeCombo, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("ID:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(objectIdField, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Position:"), gbc)
        gbc.gridx = 1
        val posPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        posPanel.add(JBLabel("X:"))
        objectXField.columns = 4
        posPanel.add(objectXField)
        posPanel.add(JBLabel("Y:"))
        objectYField.columns = 4
        posPanel.add(objectYField)
        formPanel.add(posPanel, gbc)
        row++

        val addObjectButton = JButton("Add Object")
        addObjectButton.addActionListener { addObject() }
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        formPanel.add(addObjectButton, gbc)

        panel.add(formPanel, BorderLayout.NORTH)

        // Object list
        val objectListArea = JBTextArea()
        objectListArea.isEditable = false
        panel.add(JBScrollPane(objectListArea), BorderLayout.CENTER)

        return panel
    }

    private fun createExitsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val formPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(3)
                anchor = GridBagConstraints.WEST
            }

        var row = 0

        gbc.gridx = 0
        gbc.gridy = row
        formPanel.add(JBLabel("ID:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(exitIdField, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("From:"), gbc)
        gbc.gridx = 1
        val fromPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        fromPanel.add(JBLabel("X:"))
        exitFromXField.columns = 4
        fromPanel.add(exitFromXField)
        fromPanel.add(JBLabel("Y:"))
        exitFromYField.columns = 4
        fromPanel.add(exitFromYField)
        formPanel.add(fromPanel, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("To Map:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(exitToMapField, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("To Pos:"), gbc)
        gbc.gridx = 1
        val toPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        toPanel.add(JBLabel("X:"))
        exitToXField.columns = 4
        toPanel.add(exitToXField)
        toPanel.add(JBLabel("Y:"))
        exitToYField.columns = 4
        toPanel.add(exitToYField)
        formPanel.add(toPanel, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Direction:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(exitDirectionCombo, gbc)
        row++

        val addExitButton = JButton("Add Exit")
        addExitButton.addActionListener { addExit() }
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        formPanel.add(addExitButton, gbc)

        panel.add(formPanel, BorderLayout.NORTH)

        return panel
    }

    private fun createPropertiesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val formPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(5)
                anchor = GridBagConstraints.WEST
            }

        var row = 0

        gbc.gridx = 0
        gbc.gridy = row
        formPanel.add(JBLabel("Map Name:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(mapNameField, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Width:"), gbc)
        gbc.gridx = 1
        mapWidthField.columns = 6
        formPanel.add(mapWidthField, gbc)
        row++

        gbc.gridx = 0
        gbc.gridy = row
        formPanel.add(JBLabel("Height:"), gbc)
        gbc.gridx = 1
        mapHeightField.columns = 6
        formPanel.add(mapHeightField, gbc)
        row++

        val resizeButton = JButton("Resize Map")
        resizeButton.addActionListener { resizeMap() }
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        formPanel.add(resizeButton, gbc)
        row++

        val newMapButton = JButton("New Map")
        newMapButton.addActionListener {
            val width = mapWidthField.text.toIntOrNull() ?: 20
            val height = mapHeightField.text.toIntOrNull() ?: 18
            createNewMap(width, height)
        }
        gbc.gridy = row
        formPanel.add(newMapButton, gbc)

        panel.add(formPanel, BorderLayout.NORTH)

        return panel
    }

    private fun setupListeners() {
        zoomSlider.addChangeListener {
            tilemapPanel.zoom = zoomSlider.value
            tilesetPanel.zoom = zoomSlider.value
        }

        gridCheckbox.addActionListener { tilemapPanel.showGrid = gridCheckbox.isSelected }
        attributesCheckbox.addActionListener {
            tilemapPanel.showAttributes = attributesCheckbox.isSelected
        }
        objectsCheckbox.addActionListener { tilemapPanel.showObjects = objectsCheckbox.isSelected }
        exitsCheckbox.addActionListener { tilemapPanel.showExits = exitsCheckbox.isSelected }

        brushButton.addActionListener { tilemapPanel.currentTool = TilemapPanel.Tool.BRUSH }
        fillButton.addActionListener { tilemapPanel.currentTool = TilemapPanel.Tool.FILL }
        eraserButton.addActionListener { tilemapPanel.currentTool = TilemapPanel.Tool.ERASER }
        selectButton.addActionListener { tilemapPanel.currentTool = TilemapPanel.Tool.SELECT }

        tilesetPanel.onTileSelected = { tileIndex -> tilemapPanel.selectedBrush = tileIndex }

        tilemapPanel.onTilePlaced = { _, _, _ -> updateCode() }
    }

    private fun createNewMap(width: Int, height: Int) {
        val name = mapNameField.text.ifEmpty { "untitled" }
        tilemapPanel.tilemap = TilemapModel.create(name, width, height)
        objects.clear()
        exits.clear()
        tilemapPanel.objects = objects
        tilemapPanel.exits = exits
        updateCode()
    }

    private fun resizeMap() {
        val width = mapWidthField.text.toIntOrNull() ?: return
        val height = mapHeightField.text.toIntOrNull() ?: return
        val oldMap = tilemapPanel.tilemap ?: return

        val newMap = TilemapModel.create(oldMap.name, width, height)

        // Copy existing tiles
        for (y in 0 until minOf(oldMap.height, height)) {
            for (x in 0 until minOf(oldMap.width, width)) {
                newMap.setTile(x, y, oldMap.getTile(x, y))
                newMap.setAttribute(x, y, oldMap.getAttribute(x, y))
            }
        }

        tilemapPanel.tilemap = newMap
        updateCode()
    }

    private fun createSampleTileset() {
        // Create a simple sample tileset with colored tiles
        val tileSize = 8
        val tilesX = 16
        val tilesY = 16
        val image = BufferedImage(tilesX * tileSize, tilesY * tileSize, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        for (y in 0 until tilesY) {
            for (x in 0 until tilesX) {
                val index = y * tilesX + x

                // Create varied colors
                val hue = (index * 5) % 360 / 360f
                val color =
                    Color.getHSBColor(hue, 0.3f + (index % 3) * 0.2f, 0.5f + (index % 4) * 0.15f)

                g.color = color
                g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize)

                // Add some pattern
                if (index > 0) {
                    g.color = Color(color.red / 2, color.green / 2, color.blue / 2)
                    when (index % 4) {
                        0 ->
                            g.drawLine(
                                x * tileSize,
                                y * tileSize,
                                x * tileSize + tileSize,
                                y * tileSize + tileSize,
                            )
                        1 ->
                            g.drawRect(
                                x * tileSize + 1,
                                y * tileSize + 1,
                                tileSize - 3,
                                tileSize - 3,
                            )
                        2 ->
                            g.fillOval(
                                x * tileSize + 2,
                                y * tileSize + 2,
                                tileSize - 4,
                                tileSize - 4,
                            )
                        3 -> {
                            g.drawLine(
                                x * tileSize,
                                y * tileSize + tileSize / 2,
                                x * tileSize + tileSize,
                                y * tileSize + tileSize / 2,
                            )
                            g.drawLine(
                                x * tileSize + tileSize / 2,
                                y * tileSize,
                                x * tileSize + tileSize / 2,
                                y * tileSize + tileSize,
                            )
                        }
                    }
                }
            }
        }

        g.dispose()

        tilesetPanel.tileset = TilesetModel("sample", image)
        tilemapPanel.tileset = tilesetPanel.tileset
    }

    private fun addObject() {
        val type = MapObjectData.ObjectType.entries[objectTypeCombo.selectedIndex]
        val id = objectIdField.text.ifEmpty { "obj_${objects.size}" }
        val x = objectXField.text.toIntOrNull() ?: 0
        val y = objectYField.text.toIntOrNull() ?: 0

        objects.add(MapObjectData(id, type, x, y))
        tilemapPanel.objects = objects.toList()
        updateCode()
    }

    private fun addExit() {
        val id = exitIdField.text.ifEmpty { "exit_${exits.size}" }
        val fromX = exitFromXField.text.toIntOrNull() ?: 0
        val fromY = exitFromYField.text.toIntOrNull() ?: 0
        val toMap = exitToMapField.text.ifEmpty { "target_map" }
        val toX = exitToXField.text.toIntOrNull() ?: 0
        val toY = exitToYField.text.toIntOrNull() ?: 0
        val direction = ExitData.Direction.entries[exitDirectionCombo.selectedIndex]

        exits.add(ExitData(id, fromX, fromY, toMap, toX, toY, direction))
        tilemapPanel.exits = exits.toList()
        updateCode()
    }

    private fun updateCode() {
        val tm = tilemapPanel.tilemap ?: return
        val sb = StringBuilder()

        // Tilemap code
        sb.append(tm.toGbktCode())
        sb.append("\n")

        // Objects code
        if (objects.isNotEmpty()) {
            sb.append("// Map Objects\n")
            for (obj in objects) {
                sb.append("val ${obj.id} by mapObject {\n")
                sb.append("    type = ObjectType.${obj.type.name}\n")
                sb.append("    position(${obj.x}, ${obj.y})\n")
                sb.append("}\n\n")
            }
        }

        // Exits code
        if (exits.isNotEmpty()) {
            sb.append("// Exits\n")
            for (exit in exits) {
                sb.append("exit(\"${exit.id}\") {\n")
                sb.append("    from(${exit.fromX}, ${exit.fromY})\n")
                sb.append("    to(${exit.toMap}, ${exit.toX}, ${exit.toY})\n")
                if (exit.direction != ExitData.Direction.ANY) {
                    sb.append("    direction = Direction.${exit.direction.name}\n")
                }
                sb.append("}\n\n")
            }
        }

        codeArea.text = sb.toString()
    }

    /** Sets the tileset from a BufferedImage. */
    fun setTileset(name: String, image: BufferedImage) {
        val tileset = TilesetModel(name, image)
        tilesetPanel.tileset = tileset
        tilemapPanel.tileset = tileset
    }

    /** Gets the current tilemap model. */
    fun getTilemap(): TilemapModel? = tilemapPanel.tilemap

    /** Sets the tilemap model. */
    fun setTilemap(tilemap: TilemapModel) {
        tilemapPanel.tilemap = tilemap
        mapNameField.text = tilemap.name
        mapWidthField.text = tilemap.width.toString()
        mapHeightField.text = tilemap.height.toString()
        updateCode()
    }
}
