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
package io.github.gbkt.intellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.github.gbkt.intellij.GbktBundle
import io.github.gbkt.intellij.sdk.GbktSdkService
import java.awt.BorderLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Project-level settings configurable for gbkt.
 *
 * Accessible via Settings > Project > gbkt
 *
 * Allows configuration of:
 * - Project-specific GBDK path (overrides global)
 * - Build output directory
 * - ROM name and metadata
 * - Emulator path override
 */
class GbktProjectConfigurable(private val project: Project) : Configurable {

    private var panel: GbktProjectSettingsPanel? = null

    override fun getDisplayName(): String = GbktBundle.message("settings.project.title")

    override fun createComponent(): JComponent {
        panel = GbktProjectSettingsPanel(project)
        return panel!!.mainPanel
    }

    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}

/** UI panel for project settings. */
class GbktProjectSettingsPanel(private val project: Project) {
    val mainPanel: JPanel = JPanel(BorderLayout())

    @Suppress("unused") private val sdkService = GbktSdkService.getInstance(project)

    // SDK Settings
    private val gbdkPathField = TextFieldWithBrowseButton()
    private val overrideGlobalGbdk = JBCheckBox("Override global GBDK path")

    // Build Settings
    private val buildOutputField = TextFieldWithBrowseButton()
    private val romNameField = JBTextField()

    // Emulator Settings
    private val emulatorPathField = TextFieldWithBrowseButton()
    private val overrideGlobalEmulator = JBCheckBox("Override global emulator")

    // Status
    private val sdkStatusLabel = JBLabel()

    init {
        setupUI()
        setupListeners()
        loadSettings()
    }

    private fun setupUI() {
        // Configure file choosers
        gbdkPathField.addBrowseFolderListener(
            "Select GBDK-2020 Directory",
            "Choose the GBDK-2020 installation directory",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )

        buildOutputField.addBrowseFolderListener(
            "Select Build Output Directory",
            "Choose where ROM files will be generated",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )

        emulatorPathField.addBrowseFolderListener(
            "Select Emulator",
            "Choose the emulator executable",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )

        // Build the form
        val formPanel =
            FormBuilder.createFormBuilder()
                // SDK Section
                .addSeparator()
                .addComponent(createSectionLabel("GBDK-2020 SDK"))
                .addComponent(overrideGlobalGbdk)
                .addLabeledComponent("GBDK Path:", gbdkPathField)
                .addComponent(sdkStatusLabel)

                // Build Section
                .addSeparator()
                .addComponent(createSectionLabel("Build Configuration"))
                .addLabeledComponent("Build Output:", buildOutputField)
                .addLabeledComponent("ROM Name:", romNameField)

                // Emulator Section
                .addSeparator()
                .addComponent(createSectionLabel("Emulator"))
                .addComponent(overrideGlobalEmulator)
                .addLabeledComponent("Emulator Path:", emulatorPathField)
                .addComponentFillVertically(JPanel(), 0)
                .panel

        formPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        mainPanel.add(formPanel, BorderLayout.CENTER)

        // Update enabled states
        updateEnabledStates()
    }

    private fun createSectionLabel(text: String): JBLabel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(java.awt.Font.BOLD)
        label.border = JBUI.Borders.emptyBottom(5)
        return label
    }

    private fun setupListeners() {
        overrideGlobalGbdk.addActionListener { updateEnabledStates() }
        overrideGlobalEmulator.addActionListener { updateEnabledStates() }

        gbdkPathField.textField.document.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()

                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()

                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()
            }
        )
    }

    private fun updateEnabledStates() {
        gbdkPathField.isEnabled = overrideGlobalGbdk.isSelected
        emulatorPathField.isEnabled = overrideGlobalEmulator.isSelected
    }

    private fun validateGbdk() {
        val path = gbdkPathField.text
        if (path.isBlank()) {
            sdkStatusLabel.text = "Using global GBDK path"
            sdkStatusLabel.foreground = JBColor.GRAY
            return
        }

        val dir = File(path)
        if (!dir.exists()) {
            sdkStatusLabel.text = "Directory does not exist"
            sdkStatusLabel.foreground = JBColor.RED
        } else if (!File(dir, "bin").exists()) {
            sdkStatusLabel.text = "Not a valid GBDK directory (missing bin folder)"
            sdkStatusLabel.foreground = JBColor.RED
        } else {
            sdkStatusLabel.text = "Valid GBDK installation found"
            sdkStatusLabel.foreground = JBColor(0x00AA00, 0x00CC00)
        }
    }

    fun isModified(): Boolean {
        val settings = GbktProjectSettings.getInstance(project)
        return overrideGlobalGbdk.isSelected != settings.gbdkOverride ||
            gbdkPathField.text != (settings.gbdkPath ?: "") ||
            buildOutputField.text != settings.buildOutputDir ||
            romNameField.text != (settings.romName ?: "") ||
            overrideGlobalEmulator.isSelected != settings.emulatorOverride ||
            emulatorPathField.text != (settings.emulatorPath ?: "")
    }

    fun apply() {
        val settings = GbktProjectSettings.getInstance(project)
        settings.gbdkOverride = overrideGlobalGbdk.isSelected
        settings.gbdkPath = gbdkPathField.text.ifBlank { null }
        settings.buildOutputDir = buildOutputField.text.ifBlank { "build" }
        settings.romName = romNameField.text.ifBlank { null }
        settings.emulatorOverride = overrideGlobalEmulator.isSelected
        settings.emulatorPath = emulatorPathField.text.ifBlank { null }
    }

    fun reset() {
        loadSettings()
    }

    private fun loadSettings() {
        val settings = GbktProjectSettings.getInstance(project)
        overrideGlobalGbdk.isSelected = settings.gbdkOverride
        gbdkPathField.text = settings.gbdkPath ?: ""
        buildOutputField.text = settings.buildOutputDir
        romNameField.text = settings.romName ?: ""
        overrideGlobalEmulator.isSelected = settings.emulatorOverride
        emulatorPathField.text = settings.emulatorPath ?: ""

        updateEnabledStates()
        validateGbdk()
    }
}
