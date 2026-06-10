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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.gbkt.intellij.editors.GbColors
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Color picker constrained to GBC 15-bit color space.
 *
 * Each RGB channel has 5 bits (0-31), giving 32 possible values per channel.
 */
class GbcColorPicker : JPanel(BorderLayout()) {

    private val redSlider = createChannelSlider()
    private val greenSlider = createChannelSlider()
    private val blueSlider = createChannelSlider()

    private val redValue = JTextField(3)
    private val greenValue = JTextField(3)
    private val blueValue = JTextField(3)

    private val previewPanel = ColorPreviewPanel()
    private val gbcHexLabel = JBLabel()
    private val rgb8Label = JBLabel()

    /** Current color. */
    var color: Color = Color.WHITE
        private set

    /** Listener for color changes. */
    var onColorChanged: ((Color) -> Unit)? = null

    private var isUpdating = false

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        setupSliderPanel()
        setupPreviewPanel()
        setupListeners()

        updateFromColor(Color.WHITE)
    }

    private fun createChannelSlider(): JSlider {
        return JSlider(SwingConstants.HORIZONTAL, 0, 31, 31).apply {
            majorTickSpacing = 8
            minorTickSpacing = 1
            paintTicks = true
            paintLabels = true
        }
    }

    private fun setupSliderPanel() {
        val sliderPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                insets = JBUI.insets(5)
                fill = GridBagConstraints.HORIZONTAL
            }

        // Red
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        sliderPanel.add(JBLabel("R:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        sliderPanel.add(redSlider, gbc)
        gbc.gridx = 2
        gbc.weightx = 0.0
        sliderPanel.add(redValue, gbc)

        // Green
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        sliderPanel.add(JBLabel("G:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        sliderPanel.add(greenSlider, gbc)
        gbc.gridx = 2
        gbc.weightx = 0.0
        sliderPanel.add(greenValue, gbc)

        // Blue
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        sliderPanel.add(JBLabel("B:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        sliderPanel.add(blueSlider, gbc)
        gbc.gridx = 2
        gbc.weightx = 0.0
        sliderPanel.add(blueValue, gbc)

        add(sliderPanel, BorderLayout.CENTER)
    }

    private fun setupPreviewPanel() {
        val previewContainer = JPanel(GridBagLayout())
        previewContainer.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)

        val gbc = GridBagConstraints().apply { insets = JBUI.insets(5) }

        gbc.gridx = 0
        gbc.gridy = 0
        previewContainer.add(previewPanel, gbc)

        val infoPanel = JPanel(GridBagLayout())
        val igbc =
            GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(2)
            }

        igbc.gridx = 0
        igbc.gridy = 0
        infoPanel.add(JBLabel("GBC:"), igbc)
        igbc.gridx = 1
        infoPanel.add(gbcHexLabel, igbc)

        igbc.gridx = 0
        igbc.gridy = 1
        infoPanel.add(JBLabel("RGB8:"), igbc)
        igbc.gridx = 1
        infoPanel.add(rgb8Label, igbc)

        gbc.gridx = 1
        gbc.gridy = 0
        previewContainer.add(infoPanel, gbc)

        add(previewContainer, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        redSlider.addChangeListener { if (!isUpdating) updateFromSliders() }
        greenSlider.addChangeListener { if (!isUpdating) updateFromSliders() }
        blueSlider.addChangeListener { if (!isUpdating) updateFromSliders() }

        val docListener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateFromTextFields()

                override fun removeUpdate(e: DocumentEvent) = updateFromTextFields()

                override fun changedUpdate(e: DocumentEvent) = updateFromTextFields()
            }

        redValue.document.addDocumentListener(docListener)
        greenValue.document.addDocumentListener(docListener)
        blueValue.document.addDocumentListener(docListener)
    }

    private fun updateFromSliders() {
        val r5 = redSlider.value
        val g5 = greenSlider.value
        val b5 = blueSlider.value

        // Convert 5-bit to 8-bit
        val r8 = r5 * 255 / 31
        val g8 = g5 * 255 / 31
        val b8 = b5 * 255 / 31

        isUpdating = true
        redValue.text = r5.toString()
        greenValue.text = g5.toString()
        blueValue.text = b5.toString()
        isUpdating = false

        updateColor(Color(r8, g8, b8))
    }

    private fun updateFromTextFields() {
        if (isUpdating) return

        try {
            val r5 = redValue.text.toIntOrNull()?.coerceIn(0, 31) ?: return
            val g5 = greenValue.text.toIntOrNull()?.coerceIn(0, 31) ?: return
            val b5 = blueValue.text.toIntOrNull()?.coerceIn(0, 31) ?: return

            isUpdating = true
            redSlider.value = r5
            greenSlider.value = g5
            blueSlider.value = b5
            isUpdating = false

            val r8 = r5 * 255 / 31
            val g8 = g5 * 255 / 31
            val b8 = b5 * 255 / 31

            updateColor(Color(r8, g8, b8))
        } catch (e: NumberFormatException) {
            // Ignore invalid input
        }
    }

    private fun updateColor(newColor: Color) {
        color = newColor
        previewPanel.color = newColor

        val gbc = GbColors.rgbToGbc(newColor)
        gbcHexLabel.text = GbColors.formatGbcHex(gbc)
        rgb8Label.text = GbColors.formatRgb8(newColor)

        onColorChanged?.invoke(newColor)
    }

    fun setColor(newColor: Color) {
        updateFromColor(newColor)
    }

    private fun updateFromColor(newColor: Color) {
        isUpdating = true

        val r5 = newColor.red * 31 / 255
        val g5 = newColor.green * 31 / 255
        val b5 = newColor.blue * 31 / 255

        redSlider.value = r5
        greenSlider.value = g5
        blueSlider.value = b5

        redValue.text = r5.toString()
        greenValue.text = g5.toString()
        blueValue.text = b5.toString()

        isUpdating = false

        updateColor(newColor)
    }

    /** Preview panel showing the selected color. */
    private inner class ColorPreviewPanel : JPanel() {
        var color: Color = Color.WHITE
            set(value) {
                field = value
                repaint()
            }

        init {
            preferredSize = Dimension(60, 60)
            border = BorderFactory.createLineBorder(JBColor.border())
        }

        override fun paintComponent(g: java.awt.Graphics) {
            super.paintComponent(g)
            g.color = color
            g.fillRect(0, 0, width, height)
        }
    }
}
