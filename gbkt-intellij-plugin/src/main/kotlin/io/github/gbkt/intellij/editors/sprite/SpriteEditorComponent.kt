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
package io.github.gbkt.intellij.editors.sprite

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.SwingConstants

/**
 * Main sprite editor component.
 *
 * Provides a complete sprite editing experience with:
 * - Sprite sheet viewer with 8x8 grid
 * - Zoom controls
 * - GB preview mode
 * - Validation info panel
 * - Tile selection
 */
class SpriteEditorComponent(private val file: VirtualFile) : JPanel(BorderLayout()) {

    private val logger = Logger.getInstance(SpriteEditorComponent::class.java)

    private val spritePanel = SpriteSheetPanel()
    private val infoPanel = SpriteInfoPanel()

    private val zoomSlider = JSlider(SwingConstants.HORIZONTAL, 1, 8, 2)
    private val zoomLabel = JBLabel("Zoom: 2x")
    private val gridCheckbox = JBCheckBox("Show Grid", true)
    private val gbPreviewCheckbox = JBCheckBox("GB Preview", false)

    init {
        setupToolbar()
        setupMainContent()
        setupListeners()

        loadImage()
    }

    private fun setupToolbar() {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())

        // Zoom controls
        toolbar.add(JBLabel("Zoom:"))
        zoomSlider.majorTickSpacing = 1
        zoomSlider.paintTicks = true
        zoomSlider.snapToTicks = true
        toolbar.add(zoomSlider)
        toolbar.add(zoomLabel)

        toolbar.add(javax.swing.Box.createHorizontalStrut(20))

        // View options
        toolbar.add(gridCheckbox)
        toolbar.add(gbPreviewCheckbox)

        add(toolbar, BorderLayout.NORTH)
    }

    private fun setupMainContent() {
        val scrollPane = JBScrollPane(spritePanel)
        scrollPane.border = BorderFactory.createEmptyBorder()

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, infoPanel)
        splitPane.resizeWeight = 1.0
        splitPane.dividerLocation = 600

        add(splitPane, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        zoomSlider.addChangeListener {
            val zoom = zoomSlider.value
            spritePanel.zoom = zoom
            zoomLabel.text = "Zoom: ${zoom}x"
        }

        gridCheckbox.addActionListener { spritePanel.showGrid = gridCheckbox.isSelected }

        gbPreviewCheckbox.addActionListener {
            spritePanel.gbPreviewMode = gbPreviewCheckbox.isSelected
        }

        spritePanel.onTileSelected = { infoPanel.update(spritePanel) }
    }

    private fun loadImage() {
        try {
            val inputStream = file.inputStream
            val image: BufferedImage? = ImageIO.read(inputStream)
            inputStream.close()

            if (image != null) {
                spritePanel.image = image
                infoPanel.update(spritePanel)
                logger.info("Loaded sprite: ${file.name} (${image.width}x${image.height})")
            } else {
                logger.warn("Failed to decode image: ${file.name}")
            }
        } catch (e: IOException) {
            logger.error("Error loading sprite: ${file.name}", e)
        }
    }

    /** Refreshes the sprite from disk. */
    fun refresh() {
        loadImage()
    }
}
