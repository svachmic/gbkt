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
package io.github.gbkt.intellij.editors.strings

import io.github.gbkt.intellij.editors.GbColors
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

/**
 * Panel that previews how text will appear on the Game Boy screen.
 *
 * Shows a dialog box mockup with the GB font rendering.
 */
class StringPreviewPanel : JPanel() {

    /** The text to preview. */
    var text: String = ""
        set(value) {
            field = value
            repaint()
        }

    /** Whether to show the dialog box frame. */
    var showDialogBox: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /** Scale factor for the preview. */
    var scale: Int = 2
        set(value) {
            field = value.coerceIn(1, 4)
            updateDimensions()
            repaint()
        }

    /** Palette to use for rendering. */
    var palette: List<Color> = GbColors.DMG_PALETTE
        set(value) {
            field = value
            repaint()
        }

    init {
        updateDimensions()
    }

    private fun updateDimensions() {
        // Game Boy screen is 160x144 pixels
        val width = 160 * scale + PADDING * 2
        val height = 144 * scale + PADDING * 2
        preferredSize = Dimension(width, height)
        minimumSize = Dimension(width, height)
        revalidate()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )

        // Draw GB screen background
        g2.color = palette[0]
        g2.fillRect(PADDING, PADDING, 160 * scale, 144 * scale)

        if (showDialogBox) {
            drawDialogBox(g2)
        }

        // Draw the text
        val textX = PADDING + (if (showDialogBox) DIALOG_PADDING else 0) * scale
        val textY = PADDING + (if (showDialogBox) DIALOG_Y + DIALOG_PADDING else 0) * scale
        GbFontRenderer.renderText(g2, text, textX, textY, scale, palette[3])
    }

    private fun drawDialogBox(g2: Graphics2D) {
        val boxX = PADDING
        val boxY = PADDING + DIALOG_Y * scale
        val boxWidth = 160 * scale
        val boxHeight = DIALOG_HEIGHT * scale

        // Dialog box background
        g2.color = palette[0]
        g2.fillRect(boxX, boxY, boxWidth, boxHeight)

        // Dialog box border (2 pixel border in dark color)
        g2.color = palette[3]
        val borderWidth = 2 * scale

        // Top border
        g2.fillRect(boxX, boxY, boxWidth, borderWidth)
        // Bottom border
        g2.fillRect(boxX, boxY + boxHeight - borderWidth, boxWidth, borderWidth)
        // Left border
        g2.fillRect(boxX, boxY, borderWidth, boxHeight)
        // Right border
        g2.fillRect(boxX + boxWidth - borderWidth, boxY, borderWidth, boxHeight)

        // Inner lighter border
        g2.color = palette[2]
        val innerBorder = 1 * scale
        val innerX = boxX + borderWidth
        val innerY = boxY + borderWidth
        val innerWidth = boxWidth - borderWidth * 2
        val innerHeight = boxHeight - borderWidth * 2

        g2.drawRect(innerX, innerY, innerWidth - innerBorder, innerHeight - innerBorder)
    }

    companion object {
        /** Padding around the preview. */
        const val PADDING = 10

        /** Y position of dialog box (bottom third of screen). */
        const val DIALOG_Y = 96

        /** Height of dialog box. */
        const val DIALOG_HEIGHT = 48

        /** Padding inside dialog box. */
        const val DIALOG_PADDING = 8
    }
}
