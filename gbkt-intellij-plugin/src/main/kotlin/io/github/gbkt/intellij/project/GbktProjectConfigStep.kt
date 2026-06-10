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
package io.github.gbkt.intellij.project

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.github.gbkt.intellij.project.templates.GameTemplate
import io.github.gbkt.intellij.project.templates.MinimalTemplate
import io.github.gbkt.intellij.project.templates.PlatformerTemplate
import io.github.gbkt.intellij.project.templates.RpgTemplate
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/** Wizard step for configuring gbkt project settings. */
class GbktProjectConfigStep(private val builder: GbktModuleBuilder) : ModuleWizardStep() {

    private val gameNameField = JBTextField(builder.gameName)
    private val packageField = JBTextField(builder.packageName)
    private val templateCombo = JComboBox(DefaultComboBoxModel(TEMPLATES.toTypedArray()))
    private val platformCombo =
        JComboBox(DefaultComboBoxModel(GbktModuleBuilder.TargetPlatform.entries.toTypedArray()))
    private val includeSampleAssetsCheck =
        JBCheckBox("Include sample assets", builder.includeSampleAssets)

    private val panel: JPanel = panel {
        group("Game Settings") {
            row("Game Name:") {
                cell(gameNameField)
                    .align(AlignX.FILL)
                    .comment("Name of your game (used in ROM and code)")
            }
            row("Package:") {
                cell(packageField).align(AlignX.FILL).comment("Kotlin package for generated code")
            }
        }

        group("Project Type") {
            row("Template:") {
                cell(templateCombo).align(AlignX.FILL).comment("Starting point for your game")
            }
            row("Target:") {
                cell(platformCombo).align(AlignX.FILL).comment("Target Game Boy platform")
            }
        }

        group("Assets") {
            row {
                cell(includeSampleAssetsCheck).comment("Include placeholder sprites and tilemaps")
            }
        }

        row {
            comment(
                """
                <html>
                <p>This will create a complete gbkt project with:</p>
                <ul>
                <li>Gradle build configuration</li>
                <li>Source code from selected template</li>
                <li>Asset folder structure (res/)</li>
                <li>Ready to build with <code>./gradlew buildRom</code></li>
                </ul>
                </html>
                """
                    .trimIndent()
            )
        }
    }

    override fun getComponent(): JComponent = panel

    override fun updateDataModel() {
        builder.gameName = gameNameField.text.trim().ifEmpty { "MyGame" }
        builder.packageName = packageField.text.trim().ifEmpty { "com.example.mygame" }
        builder.template =
            (templateCombo.selectedItem as? TemplateItem)?.template ?: MinimalTemplate
        builder.targetPlatform =
            platformCombo.selectedItem as? GbktModuleBuilder.TargetPlatform
                ?: GbktModuleBuilder.TargetPlatform.GBC
        builder.includeSampleAssets = includeSampleAssetsCheck.isSelected
    }

    override fun validate(): Boolean {
        val gameName = gameNameField.text.trim()
        if (gameName.isEmpty()) {
            throw com.intellij.openapi.options.ConfigurationException("Game name cannot be empty")
        }
        if (!gameName.matches(Regex("^[A-Za-z][A-Za-z0-9_]*$"))) {
            throw com.intellij.openapi.options.ConfigurationException(
                "Game name must start with a letter and contain only letters, numbers, and underscores"
            )
        }

        val packageName = packageField.text.trim()
        if (packageName.isEmpty()) {
            throw com.intellij.openapi.options.ConfigurationException(
                "Package name cannot be empty"
            )
        }
        if (!packageName.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$"))) {
            throw com.intellij.openapi.options.ConfigurationException(
                "Package name must be a valid Java/Kotlin package (e.g., com.example.mygame)"
            )
        }

        return true
    }

    /** Wrapper for template display in combo box. */
    data class TemplateItem(val template: GameTemplate) {
        override fun toString(): String = template.displayName
    }

    companion object {
        private val TEMPLATES =
            listOf(
                TemplateItem(MinimalTemplate),
                TemplateItem(RpgTemplate),
                TemplateItem(PlatformerTemplate),
            )
    }
}
