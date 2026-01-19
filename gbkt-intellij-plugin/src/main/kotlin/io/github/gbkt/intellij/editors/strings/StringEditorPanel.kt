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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Panel for editing game strings with validation and preview.
 *
 * Features:
 * - Text editor with line/character count
 * - Character limit warnings (18 per line, 90 total)
 * - Parameter validation (%name, %damage, etc.)
 * - Live GB font preview
 * - Code generation for gbkt DSL
 */
class StringEditorPanel : JPanel(BorderLayout()) {

    private val textArea = JBTextArea(5, 40)
    private val previewPanel = StringPreviewPanel()
    private val codeArea = JBTextArea(3, 40)

    private val charCountLabel = JBLabel("Characters: 0 / ${GbFontRenderer.MAX_TOTAL_CHARS}")
    private val lineCountLabel = JBLabel("Lines: 0 / ${GbFontRenderer.MAX_LINES}")
    private val statusLabel = JBLabel("Status: OK")
    private val parametersLabel = JBLabel("Parameters: none")

    /** Current validation status. */
    var hasValidInput: Boolean = true
        private set

    /** Listener for text changes. */
    var onTextChanged: ((String) -> Unit)? = null

    init {
        setupUI()
        setupListeners()
        updateValidation()
    }

    private fun setupUI() {
        // Editor panel (left side)
        val editorPanel = JPanel(BorderLayout())
        editorPanel.border = BorderFactory.createTitledBorder("Text Editor")

        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        val textScroll = JBScrollPane(textArea)
        editorPanel.add(textScroll, BorderLayout.CENTER)

        // Validation info panel
        val validationPanel = JPanel(GridBagLayout())
        validationPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        val gbc =
            GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(2)
                gridx = 0
            }

        gbc.gridy = 0
        validationPanel.add(charCountLabel, gbc)
        gbc.gridy = 1
        validationPanel.add(lineCountLabel, gbc)
        gbc.gridy = 2
        validationPanel.add(parametersLabel, gbc)
        gbc.gridy = 3
        validationPanel.add(statusLabel, gbc)

        editorPanel.add(validationPanel, BorderLayout.SOUTH)

        // Preview panel (right side)
        val previewContainer = JPanel(BorderLayout())
        previewContainer.border = BorderFactory.createTitledBorder("GB Preview")
        previewContainer.add(previewPanel, BorderLayout.CENTER)

        // Code output panel (bottom)
        val codePanel = JPanel(BorderLayout())
        codePanel.border = BorderFactory.createTitledBorder("Generated Code")
        codeArea.isEditable = false
        codeArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
        codePanel.add(JBScrollPane(codeArea), BorderLayout.CENTER)

        // Main split pane
        val topSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, previewContainer)
        topSplit.resizeWeight = 0.5
        topSplit.dividerLocation = 400

        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, codePanel)
        mainSplit.resizeWeight = 0.7
        mainSplit.dividerLocation = 300

        add(mainSplit, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        textArea.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = onTextChange()

                override fun removeUpdate(e: DocumentEvent) = onTextChange()

                override fun changedUpdate(e: DocumentEvent) = onTextChange()
            }
        )
    }

    private fun onTextChange() {
        val text = textArea.text
        previewPanel.text = text
        updateValidation()
        updateCode()
        onTextChanged?.invoke(text)
    }

    private fun updateValidation() {
        val text = textArea.text
        val lines = text.split("\n")
        val totalChars = text.replace("\n", "").length
        val issues = mutableListOf<String>()

        // Character count
        charCountLabel.text = "Characters: $totalChars / ${GbFontRenderer.MAX_TOTAL_CHARS}"
        if (totalChars > GbFontRenderer.MAX_TOTAL_CHARS) {
            charCountLabel.foreground = JBColor.RED
            issues.add("Too many characters")
        } else if (totalChars > GbFontRenderer.MAX_TOTAL_CHARS * 0.9) {
            charCountLabel.foreground = JBColor.ORANGE
        } else {
            charCountLabel.foreground = JBColor.foreground()
        }

        // Line count
        lineCountLabel.text = "Lines: ${lines.size} / ${GbFontRenderer.MAX_LINES}"
        if (lines.size > GbFontRenderer.MAX_LINES) {
            lineCountLabel.foreground = JBColor.RED
            issues.add("Too many lines")
        } else {
            lineCountLabel.foreground = JBColor.foreground()
        }

        // Check line lengths
        val longLines = lines.filter { it.length > GbFontRenderer.MAX_CHARS_PER_LINE }
        if (longLines.isNotEmpty()) {
            issues.add(
                "${longLines.size} line(s) exceed ${GbFontRenderer.MAX_CHARS_PER_LINE} chars"
            )
        }

        // Detect parameters
        val parameters = detectParameters(text)
        parametersLabel.text =
            if (parameters.isEmpty()) {
                "Parameters: none"
            } else {
                "Parameters: ${parameters.joinToString(", ")}"
            }

        // Validate parameters
        val invalidParams = validateParameters(parameters)
        if (invalidParams.isNotEmpty()) {
            issues.add("Unknown parameters: ${invalidParams.joinToString(", ")}")
        }

        // Update status
        hasValidInput = issues.isEmpty()
        if (hasValidInput) {
            statusLabel.text = "Status: OK"
            statusLabel.foreground = JBColor.GREEN.darker()
        } else {
            statusLabel.text = "Status: ${issues.joinToString("; ")}"
            statusLabel.foreground = JBColor.RED
        }
    }

    private fun detectParameters(text: String): List<String> {
        val pattern = Regex("%([a-zA-Z_][a-zA-Z0-9_]*)")
        return pattern.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }

    private fun validateParameters(parameters: List<String>): List<String> {
        val knownParameters =
            setOf(
                "name",
                "player",
                "monster",
                "damage",
                "hp",
                "sp",
                "mp",
                "exp",
                "gold",
                "item",
                "amount",
                "level",
                "target",
                "source",
                "value",
                "count",
            )
        return parameters.filter { it !in knownParameters }
    }

    private fun updateCode() {
        val text = textArea.text
        val escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        val parameters = detectParameters(text)

        codeArea.text =
            if (parameters.isEmpty()) {
                "dialog {\n    text(\"$escapedText\")\n}"
            } else {
                val paramList = parameters.joinToString(", ") { "$it: String" }
                "fun showMessage($paramList) {\n    dialog {\n        text(\"$escapedText\")\n    }\n}"
            }
    }

    /** Gets the current text. */
    fun getText(): String = textArea.text

    /** Sets the text. */
    fun setText(text: String) {
        textArea.text = text
    }

    /** Clears the text. */
    fun clear() {
        textArea.text = ""
    }
}
