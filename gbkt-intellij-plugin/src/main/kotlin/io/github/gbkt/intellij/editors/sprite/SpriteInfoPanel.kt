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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JPanel

/** Panel displaying sprite sheet information and validation status. */
class SpriteInfoPanel : JPanel(BorderLayout()) {

    private val dimensionsLabel = JBLabel()
    private val tileSizeLabel = JBLabel()
    private val tileCountLabel = JBLabel()
    private val colorCountLabel = JBLabel()
    private val validationLabel = JBLabel()
    private val selectedTileLabel = JBLabel()

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val infoPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(2)
            }

        var row = 0

        // Dimensions
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Dimensions:"), gbc)
        gbc.gridx = 1
        infoPanel.add(dimensionsLabel, gbc)
        row++

        // Tile size
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Tile size:"), gbc)
        gbc.gridx = 1
        infoPanel.add(tileSizeLabel, gbc)
        row++

        // Tile count
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Tile count:"), gbc)
        gbc.gridx = 1
        infoPanel.add(tileCountLabel, gbc)
        row++

        // Color count
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Colors:"), gbc)
        gbc.gridx = 1
        infoPanel.add(colorCountLabel, gbc)
        row++

        // Validation status
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1
        infoPanel.add(validationLabel, gbc)
        row++

        // Selected tile
        gbc.gridx = 0
        gbc.gridy = row
        infoPanel.add(JBLabel("Selected:"), gbc)
        gbc.gridx = 1
        infoPanel.add(selectedTileLabel, gbc)

        add(infoPanel, BorderLayout.NORTH)

        // Initialize with empty state
        clear()
    }

    fun clear() {
        dimensionsLabel.text = "-"
        tileSizeLabel.text = "8x8"
        tileCountLabel.text = "-"
        colorCountLabel.text = "-"
        validationLabel.text = "-"
        selectedTileLabel.text = "None"
    }

    fun update(spritePanel: SpriteSheetPanel) {
        val img = spritePanel.image
        if (img == null) {
            clear()
            return
        }

        dimensionsLabel.text = "${img.width} x ${img.height} px"
        tileSizeLabel.text = "8x8"
        tileCountLabel.text =
            "${spritePanel.tileCount} (${spritePanel.tilesX} x ${spritePanel.tilesY})"

        // Color validation
        updateColorValidation(img, spritePanel)

        // Overall validation
        updateValidationStatus(spritePanel)

        // Selected tile
        updateSelectedTile(spritePanel)
    }

    private fun updateColorValidation(img: BufferedImage, spritePanel: SpriteSheetPanel) {
        val colors = countColors(img)
        if (spritePanel.hasValid2bppColors) {
            colorCountLabel.text = "$colors (valid 2BPP)"
            colorCountLabel.foreground = JBColor.GREEN
        } else {
            colorCountLabel.text = "$colors (invalid - needs 4 or fewer)"
            colorCountLabel.foreground = JBColor.RED
        }
    }

    private fun updateValidationStatus(spritePanel: SpriteSheetPanel) {
        val issues = mutableListOf<String>()

        if (!spritePanel.hasValidDimensions) {
            issues.add("dimensions not 8x8 multiple")
        }
        if (!spritePanel.hasValid2bppColors) {
            issues.add("invalid colors")
        }

        if (issues.isEmpty()) {
            validationLabel.text = "✓ Valid"
            validationLabel.foreground = JBColor.GREEN
        } else {
            validationLabel.text = "✗ ${issues.joinToString(", ")}"
            validationLabel.foreground = JBColor.RED
        }
    }

    private fun updateSelectedTile(spritePanel: SpriteSheetPanel) {
        val tile = spritePanel.selectedTile
        if (tile < 0) {
            selectedTileLabel.text = "None"
        } else {
            val tileX = tile % spritePanel.tilesX
            val tileY = tile / spritePanel.tilesX
            selectedTileLabel.text = "Tile $tile ($tileX, $tileY)"
        }
    }

    private fun countColors(img: BufferedImage): Int {
        val colors = mutableSetOf<Int>()
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                if (alpha > 0) {
                    colors.add(rgb or 0xFF000000.toInt()) // Normalize alpha
                }
            }
        }
        return colors.size
    }
}
