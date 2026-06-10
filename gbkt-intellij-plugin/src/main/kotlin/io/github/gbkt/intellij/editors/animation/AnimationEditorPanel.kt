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
package io.github.gbkt.intellij.editors.animation

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants

/**
 * Panel for creating and previewing sprite animations.
 *
 * Features:
 * - Animation preview with play/pause controls
 * - Frame rate control
 * - Frame list editing
 * - Code generation for gbkt
 */
class AnimationEditorPanel : JPanel(BorderLayout()) {

    private val previewPanel = AnimationPreviewPanel()
    private val frameListField = JBTextField(20)
    private val frameRateSlider = JSlider(SwingConstants.HORIZONTAL, 1, 30, 10)
    private val frameRateLabel = JBLabel("10 FPS")
    private val gbPreviewCheckbox = JBCheckBox("GB Preview", false)
    private val codeArea = JBTextArea(3, 30)

    private val playButton = JButton("▶ Play")
    private val stopButton = JButton("■ Stop")

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        setupPreviewSection()
        setupControlsSection()
        setupCodeSection()
        setupListeners()
    }

    private fun setupPreviewSection() {
        val previewSection = JPanel(BorderLayout())
        previewSection.border = BorderFactory.createTitledBorder("Animation Preview")

        previewSection.add(previewPanel, BorderLayout.CENTER)

        // Playback controls
        val controlPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        controlPanel.add(playButton)
        controlPanel.add(stopButton)
        previewSection.add(controlPanel, BorderLayout.SOUTH)

        add(previewSection, BorderLayout.WEST)
    }

    private fun setupControlsSection() {
        val controlsSection = JPanel(GridBagLayout())
        controlsSection.border = BorderFactory.createTitledBorder("Animation Settings")

        val gbc =
            GridBagConstraints().apply {
                insets = JBUI.insets(5)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
            }

        // Frame list
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        controlsSection.add(JBLabel("Frames:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.gridwidth = 2
        frameListField.toolTipText = "Comma-separated tile indices (e.g., 0,1,2,3)"
        controlsSection.add(frameListField, gbc)

        // Frame rate
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        controlsSection.add(JBLabel("Speed:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        frameRateSlider.majorTickSpacing = 5
        frameRateSlider.paintTicks = true
        controlsSection.add(frameRateSlider, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        controlsSection.add(frameRateLabel, gbc)

        // GB Preview
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 3
        controlsSection.add(gbPreviewCheckbox, gbc)

        // Help text
        gbc.gridy = 3
        val helpLabel =
            JBLabel(
                "<html><i>Enter tile indices from the sprite sheet.<br>" +
                    "Click tiles in Sprite Editor to get indices.</i></html>"
            )
        helpLabel.foreground = JBColor.GRAY
        controlsSection.add(helpLabel, gbc)

        add(controlsSection, BorderLayout.CENTER)
    }

    private fun setupCodeSection() {
        val codeSection = JPanel(BorderLayout())
        codeSection.border = BorderFactory.createTitledBorder("Generated Code")

        codeArea.isEditable = false
        codeArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
        codeArea.background = JBColor.background()

        val scrollPane = JBScrollPane(codeArea)
        codeSection.add(scrollPane, BorderLayout.CENTER)

        val copyButton = JButton("Copy")
        copyButton.addActionListener {
            val selection = StringSelection(codeArea.text)
            CopyPasteManager.getInstance().setContents(selection)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(copyButton)
        codeSection.add(buttonPanel, BorderLayout.SOUTH)

        add(codeSection, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        playButton.addActionListener {
            previewPanel.play()
            updatePlayButtonState()
        }

        stopButton.addActionListener {
            previewPanel.stop()
            updatePlayButtonState()
        }

        frameListField.addActionListener { parseFrameList() }

        frameRateSlider.addChangeListener {
            val fps = frameRateSlider.value
            frameRateLabel.text = "$fps FPS"
            previewPanel.frameDuration = 1000 / fps
            updateCode()
        }

        gbPreviewCheckbox.addActionListener {
            previewPanel.gbPreviewMode = gbPreviewCheckbox.isSelected
        }
    }

    private fun parseFrameList() {
        val text = frameListField.text.trim()
        if (text.isEmpty()) {
            previewPanel.frameIndices = emptyList()
            return
        }

        val indices = text.split(",", " ", "-").mapNotNull { it.trim().toIntOrNull() }

        previewPanel.frameIndices = indices
        updateCode()
    }

    private fun updatePlayButtonState() {
        playButton.text = if (previewPanel.isPlaying) "❚❚ Pause" else "▶ Play"
    }

    private fun updateCode() {
        val frames = previewPanel.frameIndices
        if (frames.isEmpty()) {
            codeArea.text = "// No frames defined"
            return
        }

        val fps = frameRateSlider.value
        val frameDelay = 60 / fps // Convert to GB frames (60 fps)

        codeArea.text =
            """
animations {
    plays(${frames.joinToString(", ")})
    every($frameDelay.frames)
}
        """
                .trimIndent()
    }

    /** Sets the sprite sheet to use for animation frames. */
    fun setSpriteSheet(image: BufferedImage) {
        previewPanel.spriteSheet = image
    }

    /** Sets the frame indices for the animation. */
    fun setFrameIndices(indices: List<Int>) {
        frameListField.text = indices.joinToString(", ")
        previewPanel.frameIndices = indices
        updateCode()
    }
}
