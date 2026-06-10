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
package io.github.gbkt.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Main tool window panel for gbkt build output.
 *
 * Displays:
 * - Build progress and output
 * - Compilation errors with clickable links
 * - ROM generation status
 */
class GbktToolWindowPanel(private val project: Project) {

    val mainPanel: JPanel = JPanel(BorderLayout())
    private val outputArea: JTextArea = JTextArea()

    init {
        outputArea.isEditable = false
        outputArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)

        mainPanel.add(JBScrollPane(outputArea), BorderLayout.CENTER)

        appendOutput("gbkt Build Console\n")
        appendOutput("==================\n\n")
        appendOutput("Ready to build. Use Build > gbkt > Build ROM or press Ctrl+Shift+B.\n")
    }

    /** Appends text to the output area. */
    fun appendOutput(text: String) {
        outputArea.append(text)
        outputArea.caretPosition = outputArea.document.length
    }

    /** Clears the output area. */
    fun clearOutput() {
        outputArea.text = ""
    }
}
