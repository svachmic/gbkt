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
package io.github.gbkt.intellij.editors.palette

import io.github.gbkt.intellij.editors.GbColors
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Panel for displaying and editing a 4-color GBC palette.
 *
 * GBC palettes have 4 colors, each using 15-bit BGR555 format.
 */
class PalettePanel : JPanel() {

    /** The 4 colors in this palette. */
    private val colors =
        mutableListOf(Color(255, 255, 255), Color(170, 170, 170), Color(85, 85, 85), Color(0, 0, 0))

    /** Currently selected color index (0-3), or -1 if none. */
    var selectedIndex: Int = -1
        private set

    /** Listener for color selection changes. */
    var onColorSelected: ((Int, Color) -> Unit)? = null

    /** Listener for color changes. */
    var onColorChanged: ((Int, Color) -> Unit)? = null

    init {
        preferredSize = Dimension(COLOR_SIZE * 4 + SPACING * 5, COLOR_SIZE + SPACING * 2)
        minimumSize = preferredSize

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    handleClick(e.x)
                }
            }
        )
    }

    private fun handleClick(mouseX: Int) {
        for (i in 0..3) {
            val x = SPACING + i * (COLOR_SIZE + SPACING)
            if (mouseX >= x && mouseX < x + COLOR_SIZE) {
                selectedIndex = i
                onColorSelected?.invoke(i, colors[i])
                repaint()
                return
            }
        }
        selectedIndex = -1
        repaint()
    }

    fun getColor(index: Int): Color {
        return colors.getOrElse(index) { Color.BLACK }
    }

    fun setColor(index: Int, color: Color) {
        if (index in 0..3) {
            colors[index] = color
            onColorChanged?.invoke(index, color)
            repaint()
        }
    }

    fun setColors(newColors: List<Color>) {
        for (i in 0 until minOf(4, newColors.size)) {
            colors[i] = newColors[i]
        }
        repaint()
    }

    fun getColors(): List<Color> = colors.toList()

    /** Generates gbkt code for this palette. */
    fun toGbktCode(): String {
        val lines =
            colors.mapIndexed { i, color ->
                val gbc = GbColors.rgbToGbc(color)
                "    color$i = ${GbColors.formatRgb8(color)} // GBC: ${GbColors.formatGbcHex(gbc)}"
            }
        return "palette {\n${lines.joinToString("\n")}\n}"
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (i in 0..3) {
            val x = SPACING + i * (COLOR_SIZE + SPACING)
            val y = SPACING

            // Draw color swatch
            g2.color = colors[i]
            g2.fillRect(x, y, COLOR_SIZE, COLOR_SIZE)

            // Draw border
            g2.color = if (i == selectedIndex) Color(255, 200, 0) else Color.GRAY
            g2.drawRect(x, y, COLOR_SIZE, COLOR_SIZE)

            // Draw selection highlight
            if (i == selectedIndex) {
                g2.color = Color(255, 200, 0)
                g2.drawRect(x - 2, y - 2, COLOR_SIZE + 4, COLOR_SIZE + 4)
            }

            // Draw color index
            g2.color = if (isLightColor(colors[i])) Color.BLACK else Color.WHITE
            g2.drawString(i.toString(), x + COLOR_SIZE / 2 - 3, y + COLOR_SIZE / 2 + 5)
        }
    }

    private fun isLightColor(color: Color): Boolean {
        return (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 128
    }

    companion object {
        const val COLOR_SIZE = 40
        const val SPACING = 5
    }
}
