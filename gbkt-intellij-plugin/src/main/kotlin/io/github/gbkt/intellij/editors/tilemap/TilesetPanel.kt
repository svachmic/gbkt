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

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Panel for displaying a tileset and selecting tiles.
 *
 * Features:
 * - Displays tileset image with grid overlay
 * - Click to select individual tiles
 * - Drag to select rectangular regions
 * - Zoom support
 * - Hover preview
 */
class TilesetPanel : JPanel() {

    /** The tileset to display. */
    var tileset: TilesetModel? = null
        set(value) {
            field = value
            updateDimensions()
            repaint()
        }

    /** Current zoom level (1-4). */
    var zoom: Int = 2
        set(value) {
            field = value.coerceIn(1, 4)
            updateDimensions()
            repaint()
        }

    /** Whether to show the grid overlay. */
    var showGrid: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Currently selected tile index, or -1 if none. */
    var selectedTile: Int = -1
        private set

    /** Selection start for multi-tile selection. */
    private var selectionStart: Int = -1

    /** Selection end for multi-tile selection. */
    private var selectionEnd: Int = -1

    /** Hovered tile index, or -1 if none. */
    private var hoveredTile: Int = -1

    /** Callback when a tile is selected. */
    var onTileSelected: ((Int) -> Unit)? = null

    /** Callback when a tile region is selected. */
    var onRegionSelected: ((List<Int>) -> Unit)? = null

    init {
        background = Color(45, 45, 45)

        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    handleMousePress(e.x, e.y)
                }

                override fun mouseReleased(e: MouseEvent) {
                    handleMouseRelease(e.x, e.y)
                }
            }
        )

        addMouseMotionListener(
            object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    handleMouseMove(e.x, e.y)
                }

                override fun mouseDragged(e: MouseEvent) {
                    handleMouseDrag(e.x, e.y)
                }
            }
        )
    }

    private fun updateDimensions() {
        val ts = tileset
        if (ts != null) {
            val w = ts.image.width * zoom + PADDING * 2
            val h = ts.image.height * zoom + PADDING * 2
            preferredSize = Dimension(w, h)
            minimumSize = Dimension(w, h)
        } else {
            preferredSize = Dimension(200, 200)
        }
        revalidate()
    }

    private fun getTileAt(mouseX: Int, mouseY: Int): Int {
        val ts = tileset ?: return -1
        val tileSize = ts.tileSize * zoom

        val x = (mouseX - PADDING) / tileSize
        val y = (mouseY - PADDING) / tileSize

        if (x < 0 || x >= ts.tilesX || y < 0 || y >= ts.tilesY) return -1
        return y * ts.tilesX + x
    }

    private fun handleMousePress(mouseX: Int, mouseY: Int) {
        val tile = getTileAt(mouseX, mouseY)
        selectionStart = tile
        selectionEnd = tile
        selectedTile = tile
        repaint()
    }

    private fun handleMouseRelease(mouseX: Int, mouseY: Int) {
        val tile = getTileAt(mouseX, mouseY)
        selectionEnd = tile

        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            // Multi-tile selection
            val selectedTiles = getSelectedTiles()
            onRegionSelected?.invoke(selectedTiles)
        } else if (selectedTile >= 0) {
            onTileSelected?.invoke(selectedTile)
        }
    }

    private fun handleMouseMove(mouseX: Int, mouseY: Int) {
        val newHover = getTileAt(mouseX, mouseY)
        if (newHover != hoveredTile) {
            hoveredTile = newHover
            repaint()
        }
    }

    private fun handleMouseDrag(mouseX: Int, mouseY: Int) {
        val tile = getTileAt(mouseX, mouseY)
        if (tile != selectionEnd) {
            selectionEnd = tile
            repaint()
        }
    }

    private fun getSelectedTiles(): List<Int> {
        val ts = tileset ?: return emptyList()
        if (selectionStart < 0 || selectionEnd < 0) return emptyList()

        val startX = selectionStart % ts.tilesX
        val startY = selectionStart / ts.tilesX
        val endX = selectionEnd % ts.tilesX
        val endY = selectionEnd / ts.tilesX

        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)
        val minY = minOf(startY, endY)
        val maxY = maxOf(startY, endY)

        val tiles = mutableListOf<Int>()
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                tiles.add(y * ts.tilesX + x)
            }
        }
        return tiles
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )

        val ts = tileset ?: return
        val tileSize = ts.tileSize * zoom

        // Draw checkered background
        drawTransparencyBackground(g2, ts.image.width * zoom, ts.image.height * zoom)

        // Draw tileset image
        g2.drawImage(
            ts.image,
            PADDING,
            PADDING,
            ts.image.width * zoom,
            ts.image.height * zoom,
            null,
        )

        // Draw grid
        if (showGrid) {
            drawGrid(g2, ts, tileSize)
        }

        // Draw selection
        drawSelection(g2, ts, tileSize)

        // Draw hover highlight
        if (hoveredTile >= 0) {
            drawHoverHighlight(g2, ts, tileSize)
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

    private fun drawGrid(g2: Graphics2D, ts: TilesetModel, tileSize: Int) {
        g2.color = Color(100, 100, 100, 150)

        // Vertical lines
        for (x in 0..ts.tilesX) {
            val px = PADDING + x * tileSize
            g2.drawLine(px, PADDING, px, PADDING + ts.tilesY * tileSize)
        }

        // Horizontal lines
        for (y in 0..ts.tilesY) {
            val py = PADDING + y * tileSize
            g2.drawLine(PADDING, py, PADDING + ts.tilesX * tileSize, py)
        }
    }

    private fun drawSelection(g2: Graphics2D, ts: TilesetModel, tileSize: Int) {
        if (selectionStart < 0) return

        val startX = selectionStart % ts.tilesX
        val startY = selectionStart / ts.tilesX
        val endX = (selectionEnd.takeIf { it >= 0 } ?: selectionStart) % ts.tilesX
        val endY = (selectionEnd.takeIf { it >= 0 } ?: selectionStart) / ts.tilesX

        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)
        val minY = minOf(startY, endY)
        val maxY = maxOf(startY, endY)

        val x = PADDING + minX * tileSize
        val y = PADDING + minY * tileSize
        val w = (maxX - minX + 1) * tileSize
        val h = (maxY - minY + 1) * tileSize

        // Fill
        g2.color = Color(255, 200, 0, 50)
        g2.fillRect(x, y, w, h)

        // Border
        g2.color = Color(255, 200, 0)
        g2.drawRect(x, y, w, h)
    }

    private fun drawHoverHighlight(g2: Graphics2D, ts: TilesetModel, tileSize: Int) {
        val x = PADDING + (hoveredTile % ts.tilesX) * tileSize
        val y = PADDING + (hoveredTile / ts.tilesX) * tileSize

        g2.color = Color(255, 255, 255, 80)
        g2.fillRect(x, y, tileSize, tileSize)
    }

    /** Clears the current selection. */
    fun clearSelection() {
        selectedTile = -1
        selectionStart = -1
        selectionEnd = -1
        repaint()
    }

    companion object {
        const val PADDING = 5
    }
}
