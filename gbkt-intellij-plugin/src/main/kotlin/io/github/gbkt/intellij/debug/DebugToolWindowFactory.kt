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
package io.github.gbkt.intellij.debug

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.io.File

/**
 * Factory for the gbkt Debug tool window.
 *
 * Provides debug and preview tools:
 * - Emulator integration with save state management
 * - Live entity preview
 * - Collision visualization
 */
class DebugToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Emulator tab
        val emulatorPanel = EmulatorIntegration(project)
        val emulatorContent =
            ContentFactory.getInstance().createContent(emulatorPanel, "Emulator", false)
        toolWindow.contentManager.addContent(emulatorContent)

        // Entity Preview tab
        val entityPanel = EntityPreviewPanel(project)
        val entityContent =
            ContentFactory.getInstance().createContent(entityPanel, "Entities", false)
        toolWindow.contentManager.addContent(entityContent)

        // Collision Visualization tab
        val collisionPanel = CollisionVisualizationPanel()
        val collisionContent =
            ContentFactory.getInstance().createContent(collisionPanel, "Collision", false)
        toolWindow.contentManager.addContent(collisionContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Only show for gbkt projects
        return isGbktProject(project)
    }

    private fun isGbktProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val projectDir = File(basePath)

        // Quick check for gbkt files or build config
        return projectDir.walkTopDown().take(100).any { file ->
            file.name.endsWith(".gbkt.kts") ||
                (file.name == "build.gradle.kts" && file.readText().contains("gbkt"))
        }
    }
}
