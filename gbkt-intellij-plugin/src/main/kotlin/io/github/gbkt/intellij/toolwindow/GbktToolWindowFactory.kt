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

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.gbkt.intellij.buildtools.AssetPipelineDashboard
import io.github.gbkt.intellij.buildtools.BuildLogPanel
import io.github.gbkt.intellij.buildtools.RomSizeAnalyzer

/**
 * Factory for the gbkt tool window.
 *
 * The tool window provides:
 * - Build output console with error navigation
 * - Asset pipeline dashboard
 * - ROM size analyzer with bank visualization
 * - Quick actions (build, run, clean)
 */
class GbktToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Build Log tab (primary)
        val buildLogPanel = BuildLogPanel(project)
        val buildLogContent =
            ContentFactory.getInstance().createContent(buildLogPanel, "Build Log", false)
        toolWindow.contentManager.addContent(buildLogContent)

        // C Code Preview tab
        // Note: CCodePreviewPanel implements Disposable and registers itself with the project
        val cCodePreview = CCodePreviewPanel(project)
        val cCodeContent =
            ContentFactory.getInstance().createContent(cCodePreview, "C Preview", false)
        toolWindow.contentManager.addContent(cCodeContent)

        // Asset Pipeline tab
        val assetPipeline = AssetPipelineDashboard(project)
        val pipelineContent =
            ContentFactory.getInstance().createContent(assetPipeline, "Assets", false)
        toolWindow.contentManager.addContent(pipelineContent)

        // ROM Analyzer tab
        val romAnalyzer = RomSizeAnalyzer(project)
        val analyzerContent =
            ContentFactory.getInstance().createContent(romAnalyzer, "ROM Analyzer", false)
        toolWindow.contentManager.addContent(analyzerContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Only show for gbkt projects
        return isGbktProject(project)
    }

    private fun isGbktProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val projectDir = java.io.File(basePath)

        // Quick check for gbkt files or build config
        return try {
            projectDir.walkTopDown().take(100).any { file ->
                file.name.endsWith(".gbkt.kts") ||
                    (file.name == "build.gradle.kts" &&
                        runCatching { file.readText().contains("gbkt") }.getOrElse { false })
            }
        } catch (@Suppress("SwallowedException") e: SecurityException) {
            // Security exception means we can't access the project - not a gbkt project
            false
        }
    }
}
