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
package io.github.gbkt.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import io.github.gbkt.intellij.buildtools.GradleRunner

/**
 * Action to build a gbkt project to a Game Boy ROM.
 *
 * Executes the Gradle buildRom task and displays output in the tool window.
 */
class BuildRomAction : AnAction() {

    private val logger = Logger.getInstance(BuildRomAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get the build log panel for output
        val buildLogPanel = GradleRunner.getBuildLogPanel(project)

        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Building ROM", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        indicator.text = "Building ROM..."

                        logger.info("Building ROM for project: ${project.name}")

                        // Clear previous output
                        ApplicationManager.getApplication().invokeLater {
                            buildLogPanel?.clear()
                            buildLogPanel?.setStatus("Building...")
                        }

                        GradleRunner.runTask(
                            project = project,
                            task = "buildRom",
                            onOutput = { line ->
                                ApplicationManager.getApplication().invokeLater {
                                    buildLogPanel?.appendLine(line)
                                }
                            },
                            onComplete = { success, _ ->
                                ApplicationManager.getApplication().invokeLater {
                                    if (success) {
                                        buildLogPanel?.setStatus("Build successful")
                                    } else {
                                        buildLogPanel?.setStatus("Build failed")
                                    }
                                }
                            },
                        )
                    }

                    override fun onSuccess() {
                        logger.info("ROM build task completed")
                    }

                    override fun onThrowable(error: Throwable) {
                        logger.error("ROM build failed", error)
                        ApplicationManager.getApplication().invokeLater {
                            buildLogPanel?.appendLine("Error: ${error.message}\n")
                            buildLogPanel?.setStatus("Build failed with exception")
                        }
                    }
                }
            )
    }

    override fun update(e: AnActionEvent) {
        // Only enable for gbkt projects
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}
