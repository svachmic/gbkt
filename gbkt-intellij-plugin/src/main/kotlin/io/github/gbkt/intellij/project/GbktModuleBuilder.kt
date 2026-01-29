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

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.gbkt.intellij.GbktIcons
import io.github.gbkt.intellij.project.templates.GameTemplate
import io.github.gbkt.intellij.project.templates.MinimalTemplate
import java.io.File
import javax.swing.Icon

/**
 * Module builder for gbkt game projects.
 *
 * Creates a complete project structure with:
 * - Gradle build files (build.gradle.kts, settings.gradle.kts)
 * - Gradle wrapper
 * - Source directory structure
 * - Resource/asset folders
 * - Starter game code from selected template
 */
class GbktModuleBuilder : ModuleBuilder() {

    /** Selected game template */
    var template: GameTemplate = MinimalTemplate

    /** Game name (used in code generation) */
    var gameName: String = "MyGame"

    /** Base package for generated code */
    var packageName: String = "com.example.mygame"

    /** Whether to include sample assets */
    var includeSampleAssets: Boolean = true

    /** Target platform (GB or GBC) */
    var targetPlatform: TargetPlatform = TargetPlatform.GBC

    override fun getModuleType(): ModuleType<*> = GbktModuleType.INSTANCE

    override fun getNodeIcon(): Icon = GbktIcons.FILE

    override fun getPresentableName(): String = "gbkt Game"

    override fun getDescription(): String =
        "Create a Game Boy Color game using the gbkt Kotlin DSL framework"

    override fun getGroupName(): String = "gbkt"

    override fun getBuilderId(): String = "gbkt.module.builder"

    override fun getCustomOptionsStep(
        context: WizardContext,
        parentDisposable: Disposable,
    ): ModuleWizardStep {
        return GbktProjectConfigStep(this)
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val project = modifiableRootModel.project
        val contentEntryPath = contentEntryPath ?: return

        // Create project directory
        val projectDir = File(contentEntryPath)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        // Generate all project files
        generateProjectFiles(projectDir, project)

        // Set up content root
        val localFileSystem = LocalFileSystem.getInstance()
        val contentRoot =
            localFileSystem.refreshAndFindFileByPath(
                FileUtil.toSystemIndependentName(contentEntryPath)
            )
        if (contentRoot != null) {
            modifiableRootModel.addContentEntry(contentRoot)
        }
    }

    /** Generate all project files from template. */
    private fun generateProjectFiles(projectDir: File, @Suppress("unused") project: Project) {
        val generator =
            ProjectFileGenerator(
                projectDir = projectDir,
                gameName = gameName,
                packageName = packageName,
                template = template,
                includeSampleAssets = includeSampleAssets,
                targetPlatform = targetPlatform,
            )
        generator.generate()
    }

    /** Available target platforms. */
    enum class TargetPlatform(val displayName: String) {
        GB("Game Boy (DMG)"),
        GBC("Game Boy Color"),
    }
}
