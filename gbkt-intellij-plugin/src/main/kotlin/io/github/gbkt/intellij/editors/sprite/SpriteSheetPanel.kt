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

import io.github.gbkt.intellij.editors.GbColors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel

/**
 * Panel that displays a sprite sheet with 8x8 tile grid overlay.
 *
 * Features:
 * - Zoom support (1x-8x)
 * - 8x8 tile grid overlay
 * - Tile selection
 * - Color validation indicators
 * - GB preview mode
 */
class SpriteSheetPanel : JPanel() {

    /** The source image to display. */
    var image: BufferedImage? = null
        set(value) {
            field = value
            updateDimensions()
            analyzeColors()
            repaint()
        }

    /** Current zoom level (1-8). */
    var zoom: Int = 2
        set(value) {
            field = value.coerceIn(1, 8)
            updateDimensions()
            repaint()
        }

    /** Whether to show the 8x8 grid overlay. */
    var showGrid: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Whether to show in GB preview mode (apply DMG palette). */
    var gbPreviewMode: Boolean = false
        set(value) {
            field = value
            repaint()
        }

    /** Currently selected tile index, or -1 if none. */
    var selectedTile: Int = -1
        private set

    /** Listener for tile selection changes. */
    var onTileSelected: ((Int) -> Unit)? = null

    /** Colors found in the image. */
    private var imageColors: Set<Color> = emptySet()

    /** Invalid colors (not valid 2BPP). */
    private var invalidColors: List<Color> = emptyList()

    /** Number of tiles in X direction. */
    val tilesX: Int
        get() = (image?.width ?: 0) / TILE_SIZE

    /** Number of tiles in Y direction. */
    val tilesY: Int
        get() = (image?.height ?: 0) / TILE_SIZE

    /** Total number of tiles. */
    val tileCount: Int
        get() = tilesX * tilesY

    /** Whether the image has valid dimensions (multiple of 8). */
    val hasValidDimensions: Boolean
        get() {
            val img = image ?: return true
            return img.width % TILE_SIZE == 0 && img.height % TILE_SIZE == 0
        }

    /** Whether the image uses only valid 2BPP colors. */
    val hasValid2bppColors: Boolean
        get() = invalidColors.isEmpty()

    init {
        background = Color(45, 45, 45) // Dark background for transparency visibility

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    handleClick(e.x, e.y)
                }
            }
        )
    }

    private fun updateDimensions() {
        val img = image
        if (img != null) {
            val w = img.width * zoom + PADDING * 2
            val h = img.height * zoom + PADDING * 2
            preferredSize = Dimension(w, h)
            minimumSize = Dimension(w, h)
        } else {
            preferredSize = Dimension(200, 200)
        }
        revalidate()
    }

    private fun analyzeColors() {
        val img = image ?: return
        val colors = mutableSetOf<Color>()

        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                if (alpha > 0) { // Skip fully transparent pixels
                    colors.add(Color(rgb, true))
                }
            }
        }

        imageColors = colors
        invalidColors = GbColors.validateImage2bpp(colors)
    }

    private fun handleClick(mouseX: Int, mouseY: Int) {
        val img = image ?: return

        val imgX = (mouseX - PADDING) / zoom
        val imgY = (mouseY - PADDING) / zoom

        if (imgX < 0 || imgX >= img.width || imgY < 0 || imgY >= img.height) {
            selectedTile = -1
        } else {
            val tileX = imgX / TILE_SIZE
            val tileY = imgY / TILE_SIZE
            selectedTile = tileY * tilesX + tileX
        }

        onTileSelected?.invoke(selectedTile)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D

        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )

        val img = image ?: return

        // Draw checkered background for transparency
        drawTransparencyBackground(g2, img.width * zoom, img.height * zoom)

        // Draw the image
        if (gbPreviewMode) {
            drawGbPreview(g2, img)
        } else {
            g2.drawImage(img, PADDING, PADDING, img.width * zoom, img.height * zoom, null)
        }

        // Draw grid overlay
        if (showGrid) {
            drawGrid(g2, img.width, img.height)
        }

        // Draw tile selection
        if (selectedTile >= 0) {
            drawSelection(g2)
        }

        // Draw dimension warning
        if (!hasValidDimensions) {
            drawDimensionWarning(g2)
        }
    }

    private fun drawTransparencyBackground(g2: Graphics2D, w: Int, h: Int) {
        val checkSize = 8
        for (y in 0 until h / checkSize + 1) {
            for (x in 0 until w / checkSize + 1) {
                g2.color = if ((x + y) % 2 == 0) Color(60, 60, 60) else Color(40, 40, 40)
                g2.fillRect(PADDING + x * checkSize, PADDING + y * checkSize, checkSize, checkSize)
            }
        }
    }

    private fun drawGbPreview(g2: Graphics2D, img: BufferedImage) {
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF

                if (alpha > 0) {
                    val color = Color(rgb, true)
                    val index = GbColors.findClosest2bppIndex(color)
                    val gbColor = GbColors.DMG_PALETTE[index]

                    g2.color = gbColor
                    g2.fillRect(PADDING + x * zoom, PADDING + y * zoom, zoom, zoom)
                }
            }
        }
    }

    private fun drawGrid(g2: Graphics2D, imgWidth: Int, imgHeight: Int) {
        g2.color = Color(100, 100, 100, 150)
        g2.stroke = BasicStroke(1f)

        // Vertical lines
        for (x in 0..imgWidth / TILE_SIZE) {
            val px = PADDING + x * TILE_SIZE * zoom
            g2.drawLine(px, PADDING, px, PADDING + imgHeight * zoom)
        }

        // Horizontal lines
        for (y in 0..imgHeight / TILE_SIZE) {
            val py = PADDING + y * TILE_SIZE * zoom
            g2.drawLine(PADDING, py, PADDING + imgWidth * zoom, py)
        }
    }

    private fun drawSelection(g2: Graphics2D) {
        if (selectedTile < 0) return

        val tileX = selectedTile % tilesX
        val tileY = selectedTile / tilesX

        val x = PADDING + tileX * TILE_SIZE * zoom
        val y = PADDING + tileY * TILE_SIZE * zoom
        val size = TILE_SIZE * zoom

        g2.color = Color(255, 200, 0, 200)
        g2.stroke = BasicStroke(2f)
        g2.drawRect(x, y, size, size)
    }

    private fun drawDimensionWarning(g2: Graphics2D) {
        g2.color = Color(255, 100, 100)
        g2.drawString("⚠ Dimensions not multiple of 8", PADDING, height - 10)
    }

    companion object {
        /** Game Boy tile size in pixels. */
        const val TILE_SIZE = 8

        /** Padding around the image. */
        const val PADDING = 10
    }
}
