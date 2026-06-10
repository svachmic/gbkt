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

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.github.gbkt.intellij.editors.GbColors
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Complete palette editor panel.
 *
 * Features:
 * - 4-color palette display
 * - GBC color picker for each slot
 * - Preset palettes (DMG, grayscale)
 * - Code generation for gbkt
 * - Copy to clipboard
 */
class PaletteEditorPanel : JPanel(BorderLayout()) {

    private val palettePanel = PalettePanel()
    private val colorPicker = GbcColorPicker()
    private val codeArea = JBTextArea(5, 40)
    private val selectedColorLabel = JBLabel("Select a color to edit")

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        setupPaletteSection()
        setupColorPickerSection()
        setupCodeSection()
        setupListeners()

        updateCode()
    }

    private fun setupPaletteSection() {
        val paletteSection = JPanel(BorderLayout())
        paletteSection.border = BorderFactory.createTitledBorder("Palette")

        // Palette display
        val paletteContainer = JPanel(FlowLayout(FlowLayout.LEFT))
        paletteContainer.add(palettePanel)
        paletteSection.add(paletteContainer, BorderLayout.CENTER)

        // Preset buttons
        val presetPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        presetPanel.add(JBLabel("Presets:"))

        val dmgButton = JButton("DMG Green")
        dmgButton.addActionListener {
            palettePanel.setColors(GbColors.DMG_PALETTE)
            updateCode()
        }
        presetPanel.add(dmgButton)

        val grayscaleButton = JButton("Grayscale")
        grayscaleButton.addActionListener {
            palettePanel.setColors(GbColors.GRAYSCALE_PALETTE)
            updateCode()
        }
        presetPanel.add(grayscaleButton)

        val invertButton = JButton("Invert")
        invertButton.addActionListener {
            palettePanel.setColors(palettePanel.getColors().reversed())
            updateCode()
        }
        presetPanel.add(invertButton)

        paletteSection.add(presetPanel, BorderLayout.SOUTH)

        add(paletteSection, BorderLayout.NORTH)
    }

    private fun setupColorPickerSection() {
        val pickerSection = JPanel(BorderLayout())
        pickerSection.border = BorderFactory.createTitledBorder("Color Editor")

        val infoPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                insets = JBUI.insets(5)
                anchor = GridBagConstraints.WEST
            }
        gbc.gridx = 0
        gbc.gridy = 0
        infoPanel.add(selectedColorLabel, gbc)

        pickerSection.add(infoPanel, BorderLayout.NORTH)
        pickerSection.add(colorPicker, BorderLayout.CENTER)

        add(pickerSection, BorderLayout.CENTER)
    }

    private fun setupCodeSection() {
        val codeSection = JPanel(BorderLayout())
        codeSection.border = BorderFactory.createTitledBorder("Generated Code")

        codeArea.isEditable = false
        codeArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
        codeArea.background = JBColor.background()

        val scrollPane = JBScrollPane(codeArea)
        codeSection.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val copyButton = JButton("Copy to Clipboard")
        copyButton.addActionListener {
            val selection = StringSelection(codeArea.text)
            CopyPasteManager.getInstance().setContents(selection)
        }
        buttonPanel.add(copyButton)

        codeSection.add(buttonPanel, BorderLayout.SOUTH)

        add(codeSection, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        palettePanel.onColorSelected = { index, color ->
            selectedColorLabel.text = "Editing Color $index"
            colorPicker.setColor(color)
        }

        colorPicker.onColorChanged = { color ->
            val index = palettePanel.selectedIndex
            if (index >= 0) {
                palettePanel.setColor(index, color)
                updateCode()
            }
        }
    }

    private fun updateCode() {
        codeArea.text = palettePanel.toGbktCode()
    }

    /** Sets the palette colors. */
    fun setColors(colors: List<Color>) {
        palettePanel.setColors(colors)
        updateCode()
    }

    /** Gets the current palette colors. */
    fun getColors(): List<Color> = palettePanel.getColors()
}
