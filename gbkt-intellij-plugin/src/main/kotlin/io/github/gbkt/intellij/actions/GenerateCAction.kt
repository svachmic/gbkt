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
 * Action to generate C code from gbkt DSL without compiling to ROM.
 *
 * Useful for:
 * - Inspecting generated C code
 * - Debugging codegen issues
 * - Faster iteration when ROM compilation is slow
 */
class GenerateCAction : AnAction() {

    private val logger = Logger.getInstance(GenerateCAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get the build log panel for output
        val buildLogPanel = GradleRunner.getBuildLogPanel(project)

        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Generating C Code", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        indicator.text = "Generating C code..."

                        logger.info("Generating C code for project: ${project.name}")

                        // Clear previous output
                        ApplicationManager.getApplication().invokeLater {
                            buildLogPanel?.clear()
                            buildLogPanel?.setStatus("Generating C code...")
                        }

                        GradleRunner.runTask(
                            project = project,
                            task = "generateC",
                            onOutput = { line ->
                                ApplicationManager.getApplication().invokeLater {
                                    buildLogPanel?.appendLine(line)
                                }
                            },
                            onComplete = { success, _ ->
                                ApplicationManager.getApplication().invokeLater {
                                    if (success) {
                                        buildLogPanel?.setStatus("C code generation successful")
                                    } else {
                                        buildLogPanel?.setStatus("C code generation failed")
                                    }
                                }
                            },
                        )
                    }

                    override fun onSuccess() {
                        logger.info("C code generation task completed")
                    }

                    override fun onThrowable(error: Throwable) {
                        logger.error("C code generation failed", error)
                        ApplicationManager.getApplication().invokeLater {
                            buildLogPanel?.appendLine("Error: ${error.message}\n")
                            buildLogPanel?.setStatus("Generation failed with exception")
                        }
                    }
                }
            )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}
