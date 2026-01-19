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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.github.gbkt.intellij.GbktBundle
import java.awt.BorderLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Application-level settings configurable for gbkt.
 *
 * Accessible via Settings > gbkt
 *
 * Allows configuration of:
 * - Global GBDK-2020 installation path
 * - Default emulator and arguments
 * - Plugin behavior preferences
 */
class GbktApplicationConfigurable : Configurable {

    private var panel: GbktApplicationSettingsPanel? = null

    override fun getDisplayName(): String = GbktBundle.message("settings.app.title")

    override fun createComponent(): JComponent {
        panel = GbktApplicationSettingsPanel()
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

/** UI panel for application settings. */
class GbktApplicationSettingsPanel {
    val mainPanel: JPanel = JPanel(BorderLayout())

    // SDK Settings
    private val gbdkPathField = TextFieldWithBrowseButton()
    private val gbdkStatusLabel = JBLabel()

    // Emulator Settings
    private val emulatorPathField = TextFieldWithBrowseButton()
    private val emulatorTypeCombo = ComboBox(DefaultComboBoxModel(EMULATOR_TYPES))
    private val emulatorArgsField = JBTextField()

    // Behavior Settings
    private val autoBuildOnSave = JBCheckBox("Automatically build on save")
    private val showSdkStatus = JBCheckBox("Show SDK status in status bar")
    private val showRomSizeWarnings = JBCheckBox("Show ROM size warnings")
    private val enableAutoDetection = JBCheckBox("Enable auto-detection of GBDK and emulators")

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
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )

        emulatorPathField.addBrowseFolderListener(
            "Select Emulator",
            "Choose the emulator executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )

        // Build the form
        val formPanel =
            FormBuilder.createFormBuilder()
                // SDK Section
                .addSeparator()
                .addComponent(createSectionLabel("GBDK-2020 SDK"))
                .addLabeledComponent("GBDK Path:", gbdkPathField)
                .addComponent(gbdkStatusLabel)
                .addComponent(enableAutoDetection)

                // Emulator Section
                .addSeparator()
                .addComponent(createSectionLabel("Default Emulator"))
                .addLabeledComponent("Emulator Type:", emulatorTypeCombo)
                .addLabeledComponent("Emulator Path:", emulatorPathField)
                .addLabeledComponent("Arguments:", emulatorArgsField)

                // Behavior Section
                .addSeparator()
                .addComponent(createSectionLabel("Plugin Behavior"))
                .addComponent(autoBuildOnSave)
                .addComponent(showSdkStatus)
                .addComponent(showRomSizeWarnings)
                .addComponentFillVertically(JPanel(), 0)
                .panel

        formPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        mainPanel.add(formPanel, BorderLayout.CENTER)
    }

    private fun createSectionLabel(text: String): JBLabel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(java.awt.Font.BOLD)
        label.border = JBUI.Borders.emptyBottom(5)
        return label
    }

    private fun setupListeners() {
        gbdkPathField.textField.document.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()

                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()

                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = validateGbdk()
            }
        )

        emulatorTypeCombo.addActionListener {
            val selected = emulatorTypeCombo.selectedItem as? String
            emulatorPathField.isEnabled = selected != "NONE"
            emulatorArgsField.isEnabled = selected != "NONE"
        }
    }

    private fun validateGbdk() {
        val path = gbdkPathField.text
        if (path.isBlank()) {
            gbdkStatusLabel.text = "No GBDK path configured"
            gbdkStatusLabel.foreground = JBColor.GRAY
            return
        }

        val dir = File(path)
        if (!dir.exists()) {
            gbdkStatusLabel.text = "Directory does not exist"
            gbdkStatusLabel.foreground = JBColor.RED
        } else if (!File(dir, "bin").exists()) {
            gbdkStatusLabel.text = "Not a valid GBDK directory (missing bin folder)"
            gbdkStatusLabel.foreground = JBColor.RED
        } else {
            // Try to detect version
            val lcc = File(dir, "bin/lcc")
            val lccExe = File(dir, "bin/lcc.exe")
            if (lcc.exists() || lccExe.exists()) {
                gbdkStatusLabel.text = "Valid GBDK-2020 installation found"
                gbdkStatusLabel.foreground = JBColor(0x00AA00, 0x00CC00)
            } else {
                gbdkStatusLabel.text = "GBDK directory found but lcc compiler not found"
                gbdkStatusLabel.foreground = JBColor.ORANGE
            }
        }
    }

    fun isModified(): Boolean {
        val settings = GbktSettings.getInstance()
        return gbdkPathField.text != (settings.gbdkPath ?: "") ||
            emulatorPathField.text != (settings.emulatorPath ?: "") ||
            emulatorTypeCombo.selectedItem != settings.emulatorType ||
            emulatorArgsField.text != settings.emulatorArgs ||
            autoBuildOnSave.isSelected != settings.autoBuildOnSave ||
            showSdkStatus.isSelected != settings.showSdkStatus ||
            showRomSizeWarnings.isSelected != settings.showRomSizeWarnings ||
            enableAutoDetection.isSelected != settings.enableAutoDetection
    }

    fun apply() {
        val settings = GbktSettings.getInstance()
        settings.gbdkPath = gbdkPathField.text.ifBlank { null }
        settings.emulatorPath = emulatorPathField.text.ifBlank { null }
        settings.emulatorType = emulatorTypeCombo.selectedItem as? String ?: "NONE"
        settings.emulatorArgs = emulatorArgsField.text
        settings.autoBuildOnSave = autoBuildOnSave.isSelected
        settings.showSdkStatus = showSdkStatus.isSelected
        settings.showRomSizeWarnings = showRomSizeWarnings.isSelected
        settings.enableAutoDetection = enableAutoDetection.isSelected
    }

    fun reset() {
        loadSettings()
    }

    private fun loadSettings() {
        val settings = GbktSettings.getInstance()
        gbdkPathField.text = settings.gbdkPath ?: ""
        emulatorPathField.text = settings.emulatorPath ?: ""
        emulatorTypeCombo.selectedItem = settings.emulatorType
        emulatorArgsField.text = settings.emulatorArgs
        autoBuildOnSave.isSelected = settings.autoBuildOnSave
        showSdkStatus.isSelected = settings.showSdkStatus
        showRomSizeWarnings.isSelected = settings.showRomSizeWarnings
        enableAutoDetection.isSelected = settings.enableAutoDetection

        // Update enabled states
        val emulatorEnabled = settings.emulatorType != "NONE"
        emulatorPathField.isEnabled = emulatorEnabled
        emulatorArgsField.isEnabled = emulatorEnabled

        validateGbdk()
    }

    companion object {
        private val EMULATOR_TYPES =
            arrayOf("NONE", "BGB", "SameBoy", "mGBA", "Emulicious", "Custom")
    }
}
