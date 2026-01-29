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

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Panel for displaying and editing a tilemap.
 *
 * Features:
 * - Renders tilemap using tileset
 * - Tile placement with brush tool
 * - Attribute overlay (collision, etc.)
 * - Object overlay (chests, doors, etc.)
 * - Exit visualization
 * - Grid and selection tools
 */
class TilemapPanel : JPanel() {

    /** The tilemap to display and edit. */
    var tilemap: TilemapModel? = null
        set(value) {
            field = value
            updateDimensions()
            repaint()
        }

    /** The tileset to use for rendering. */
    var tileset: TilesetModel? = null
        set(value) {
            field = value
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

    /** Whether to show attribute overlay. */
    var showAttributes: Boolean = false
        set(value) {
            field = value
            repaint()
        }

    /** Whether to show object overlay. */
    var showObjects: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Whether to show exit connections. */
    var showExits: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Currently selected tile brush (tile index from tileset). */
    var selectedBrush: Int = -1

    /** Current editing tool. */
    var currentTool: Tool = Tool.BRUSH
        set(value) {
            field = value
            repaint()
        }

    /** Map objects to display. */
    var objects: List<MapObjectData> = emptyList()
        set(value) {
            field = value
            repaint()
        }

    /** Exit connections to display. */
    var exits: List<ExitData> = emptyList()
        set(value) {
            field = value
            repaint()
        }

    /** Callback when a tile is placed. */
    var onTilePlaced: ((Int, Int, Int) -> Unit)? = null

    /** Callback when a tile is clicked (for selection/inspection). */
    var onTileClicked: ((Int, Int) -> Unit)? = null

    /** Editing tools. */
    enum class Tool {
        BRUSH,
        FILL,
        ERASER,
        SELECT,
        ATTRIBUTE,
        OBJECT,
    }

    private var isDragging = false
    private var lastDragX = -1
    private var lastDragY = -1

    init {
        background = Color(30, 30, 30)

        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    handleMousePress(e.x, e.y, e.button)
                }

                override fun mouseReleased(e: MouseEvent) {
                    isDragging = false
                    lastDragX = -1
                    lastDragY = -1
                }
            }
        )

        addMouseMotionListener(
            object : MouseAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    handleMouseDrag(e.x, e.y)
                }
            }
        )
    }

    private fun updateDimensions() {
        val tm = tilemap
        if (tm != null) {
            val tileSize = TilemapModel.TILE_SIZE * zoom
            val w = tm.width * tileSize + PADDING * 2
            val h = tm.height * tileSize + PADDING * 2
            preferredSize = Dimension(w, h)
            minimumSize = Dimension(w, h)
        } else {
            preferredSize = Dimension(400, 400)
        }
        revalidate()
    }

    private fun getTileAt(mouseX: Int, mouseY: Int): Pair<Int, Int>? {
        val tm = tilemap ?: return null
        val tileSize = TilemapModel.TILE_SIZE * zoom

        val x = (mouseX - PADDING) / tileSize
        val y = (mouseY - PADDING) / tileSize

        if (x < 0 || x >= tm.width || y < 0 || y >= tm.height) return null
        return Pair(x, y)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleMousePress(mouseX: Int, mouseY: Int, button: Int) {
        val pos = getTileAt(mouseX, mouseY) ?: return
        val (x, y) = pos

        when (currentTool) {
            Tool.BRUSH -> {
                if (selectedBrush >= 0) {
                    placeTile(x, y, selectedBrush)
                    isDragging = true
                    lastDragX = x
                    lastDragY = y
                }
            }
            Tool.ERASER -> {
                placeTile(x, y, 0)
                isDragging = true
                lastDragX = x
                lastDragY = y
            }
            Tool.FILL -> {
                if (selectedBrush >= 0) {
                    floodFill(x, y, selectedBrush)
                }
            }
            Tool.SELECT,
            Tool.ATTRIBUTE,
            Tool.OBJECT -> {
                onTileClicked?.invoke(x, y)
            }
        }
    }

    private fun handleMouseDrag(mouseX: Int, mouseY: Int) {
        if (!isDragging) return

        val pos = getTileAt(mouseX, mouseY) ?: return
        val (x, y) = pos

        if (x != lastDragX || y != lastDragY) {
            when (currentTool) {
                Tool.BRUSH -> {
                    if (selectedBrush >= 0) {
                        placeTile(x, y, selectedBrush)
                    }
                }
                Tool.ERASER -> {
                    placeTile(x, y, 0)
                }
                else -> Unit // Other tools don't handle drag
            }
            lastDragX = x
            lastDragY = y
        }
    }

    private fun placeTile(x: Int, y: Int, tileIndex: Int) {
        val tm = tilemap ?: return
        tm.setTile(x, y, tileIndex)
        onTilePlaced?.invoke(x, y, tileIndex)
        repaint()
    }

    private fun floodFill(startX: Int, startY: Int, newTile: Int) {
        val tm = tilemap ?: return
        val targetTile = tm.getTile(startX, startY)
        if (targetTile == newTile) return

        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            val pos = Pair(x, y)

            // Skip if invalid or already visited
            val isValid =
                x >= 0 &&
                    x < tm.width &&
                    y >= 0 &&
                    y < tm.height &&
                    pos !in visited &&
                    tm.getTile(x, y) == targetTile

            if (isValid) {
                visited.add(pos)
                tm.setTile(x, y, newTile)

                queue.add(Pair(x - 1, y))
                queue.add(Pair(x + 1, y))
                queue.add(Pair(x, y - 1))
                queue.add(Pair(x, y + 1))
            }
        }

        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )

        val tm = tilemap ?: return
        val ts = tileset
        val tileSize = TilemapModel.TILE_SIZE * zoom

        // Draw checkered background
        drawBackground(g2, tm.width * tileSize, tm.height * tileSize)

        // Draw tiles
        if (ts != null) {
            drawTiles(g2, tm, ts, tileSize)
        } else {
            drawPlaceholderTiles(g2, tm, tileSize)
        }

        // Draw grid
        if (showGrid) {
            drawGrid(g2, tm, tileSize)
        }

        // Draw attribute overlay
        if (showAttributes) {
            drawAttributeOverlay(g2, tm, tileSize)
        }

        // Draw objects
        if (showObjects) {
            drawObjects(g2, tileSize)
        }

        // Draw exits
        if (showExits) {
            drawExits(g2, tileSize)
        }
    }

    private fun drawBackground(g2: Graphics2D, w: Int, h: Int) {
        val checkSize = 16
        for (y in 0 until h / checkSize + 1) {
            for (x in 0 until w / checkSize + 1) {
                g2.color = if ((x + y) % 2 == 0) Color(50, 50, 50) else Color(35, 35, 35)
                g2.fillRect(PADDING + x * checkSize, PADDING + y * checkSize, checkSize, checkSize)
            }
        }
    }

    private fun drawTiles(g2: Graphics2D, tm: TilemapModel, ts: TilesetModel, tileSize: Int) {
        for (y in 0 until tm.height) {
            for (x in 0 until tm.width) {
                val tileIndex = tm.getTile(x, y)
                val tileImage = ts.getTileImage(tileIndex)
                g2.drawImage(
                    tileImage,
                    PADDING + x * tileSize,
                    PADDING + y * tileSize,
                    tileSize,
                    tileSize,
                    null,
                )
            }
        }
    }

    private fun drawPlaceholderTiles(g2: Graphics2D, tm: TilemapModel, tileSize: Int) {
        for (y in 0 until tm.height) {
            for (x in 0 until tm.width) {
                val tileIndex = tm.getTile(x, y)
                if (tileIndex > 0) {
                    // Draw placeholder with tile index
                    val px = PADDING + x * tileSize
                    val py = PADDING + y * tileSize

                    g2.color = Color(80, 80, 120)
                    g2.fillRect(px, py, tileSize, tileSize)

                    g2.color = Color.WHITE
                    g2.font = g2.font.deriveFont(8f)
                    g2.drawString(tileIndex.toString(), px + 2, py + tileSize - 2)
                }
            }
        }
    }

    private fun drawGrid(g2: Graphics2D, tm: TilemapModel, tileSize: Int) {
        g2.color = Color(80, 80, 80, 100)

        // Vertical lines
        for (x in 0..tm.width) {
            val px = PADDING + x * tileSize
            g2.drawLine(px, PADDING, px, PADDING + tm.height * tileSize)
        }

        // Horizontal lines
        for (y in 0..tm.height) {
            val py = PADDING + y * tileSize
            g2.drawLine(PADDING, py, PADDING + tm.width * tileSize, py)
        }
    }

    private fun drawAttributeOverlay(g2: Graphics2D, tm: TilemapModel, tileSize: Int) {
        for (y in 0 until tm.height) {
            for (x in 0 until tm.width) {
                val attr = tm.getAttribute(x, y)
                if (attr != 0) {
                    val px = PADDING + x * tileSize
                    val py = PADDING + y * tileSize

                    // Color based on attribute
                    g2.color =
                        when {
                            (attr and TilemapModel.TileAttribute.WALL.flag) != 0 ->
                                Color(255, 0, 0, 100)
                            (attr and TilemapModel.TileAttribute.WATER.flag) != 0 ->
                                Color(0, 100, 255, 100)
                            (attr and TilemapModel.TileAttribute.PIT.flag) != 0 ->
                                Color(0, 0, 0, 150)
                            (attr and TilemapModel.TileAttribute.DAMAGE.flag) != 0 ->
                                Color(255, 100, 0, 100)
                            (attr and TilemapModel.TileAttribute.EXIT.flag) != 0 ->
                                Color(0, 255, 0, 100)
                            else -> Color(255, 255, 0, 80)
                        }

                    g2.fillRect(px, py, tileSize, tileSize)
                }
            }
        }
    }

    private fun drawObjects(g2: Graphics2D, tileSize: Int) {
        for (obj in objects) {
            val px = PADDING + obj.x * tileSize
            val py = PADDING + obj.y * tileSize

            // Draw object marker
            g2.color = obj.type.color
            g2.fillOval(px + tileSize / 4, py + tileSize / 4, tileSize / 2, tileSize / 2)

            // Draw border
            g2.color = Color.WHITE
            g2.drawOval(px + tileSize / 4, py + tileSize / 4, tileSize / 2, tileSize / 2)

            // Draw type indicator
            g2.font = g2.font.deriveFont(9f)
            val label = obj.type.displayName.first().toString()
            g2.drawString(label, px + tileSize / 2 - 3, py + tileSize / 2 + 3)
        }
    }

    private fun drawExits(g2: Graphics2D, tileSize: Int) {
        g2.stroke = BasicStroke(2f)

        for (exit in exits) {
            val px = PADDING + exit.fromX * tileSize + tileSize / 2
            val py = PADDING + exit.fromY * tileSize + tileSize / 2

            // Draw exit marker
            g2.color = Color(0, 255, 100, 200)
            g2.fillRect(
                PADDING + exit.fromX * tileSize + 2,
                PADDING + exit.fromY * tileSize + 2,
                tileSize - 4,
                tileSize - 4,
            )

            // Draw direction arrow
            g2.color = Color.WHITE
            when (exit.direction) {
                ExitData.Direction.UP -> {
                    g2.drawLine(px, py, px, py - tileSize / 3)
                    g2.drawLine(px, py - tileSize / 3, px - 3, py - tileSize / 3 + 3)
                    g2.drawLine(px, py - tileSize / 3, px + 3, py - tileSize / 3 + 3)
                }
                ExitData.Direction.DOWN -> {
                    g2.drawLine(px, py, px, py + tileSize / 3)
                    g2.drawLine(px, py + tileSize / 3, px - 3, py + tileSize / 3 - 3)
                    g2.drawLine(px, py + tileSize / 3, px + 3, py + tileSize / 3 - 3)
                }
                ExitData.Direction.LEFT -> {
                    g2.drawLine(px, py, px - tileSize / 3, py)
                    g2.drawLine(px - tileSize / 3, py, px - tileSize / 3 + 3, py - 3)
                    g2.drawLine(px - tileSize / 3, py, px - tileSize / 3 + 3, py + 3)
                }
                ExitData.Direction.RIGHT -> {
                    g2.drawLine(px, py, px + tileSize / 3, py)
                    g2.drawLine(px + tileSize / 3, py, px + tileSize / 3 - 3, py - 3)
                    g2.drawLine(px + tileSize / 3, py, px + tileSize / 3 - 3, py + 3)
                }
                ExitData.Direction.ANY -> {
                    g2.drawOval(px - 4, py - 4, 8, 8)
                }
            }

            // Draw destination label
            g2.font = g2.font.deriveFont(8f)
            g2.color = Color.YELLOW
            g2.drawString(
                "${exit.toMap}(${exit.toX},${exit.toY})",
                PADDING + exit.fromX * tileSize,
                PADDING + exit.fromY * tileSize - 2,
            )
        }
    }

    companion object {
        const val PADDING = 10
    }
}
