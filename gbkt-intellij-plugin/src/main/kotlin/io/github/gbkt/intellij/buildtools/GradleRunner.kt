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
package io.github.gbkt.intellij.buildtools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Utility for running Gradle tasks and streaming output to the tool window. */
object GradleRunner {

    private val logger = Logger.getInstance(GradleRunner::class.java)

    /**
     * Runs a Gradle task in the project directory.
     *
     * @param project The IntelliJ project
     * @param task The Gradle task to run (e.g., "buildRom", "generateC")
     * @param onOutput Callback for each line of output
     * @param onComplete Callback when task completes with success/failure status
     */
    fun runTask(
        project: Project,
        task: String,
        onOutput: (String) -> Unit,
        onComplete: (success: Boolean, exitCode: Int) -> Unit,
    ) {
        val basePath = project.basePath
        if (basePath == null) {
            onOutput("Error: Project base path not found\n")
            onComplete(false, -1)
            return
        }

        val projectDir = File(basePath)
        val gradleWrapper = findGradleWrapper(projectDir)

        if (gradleWrapper == null) {
            onOutput("Error: Gradle wrapper not found in project\n")
            onOutput("Please ensure gradlew (or gradlew.bat on Windows) exists in project root.\n")
            onComplete(false, -1)
            return
        }

        val command = listOf(gradleWrapper.absolutePath, task, "--console=plain", "--no-daemon")

        onOutput("Running: ${command.joinToString(" ")}\n")
        onOutput("Working directory: $basePath\n")
        onOutput("-".repeat(60) + "\n")

        try {
            val processBuilder =
                ProcessBuilder(command).directory(projectDir).redirectErrorStream(true)

            val process = processBuilder.start()

            // Read output in a separate thread to avoid blocking
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onOutput(line + "\n")
            }

            // Wait for process to complete (with timeout)
            val completed = process.waitFor(10, TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                onOutput("\nError: Gradle task timed out after 10 minutes\n")
                onComplete(false, -1)
                return
            }

            val exitCode = process.exitValue()
            onOutput("-".repeat(60) + "\n")

            if (exitCode == 0) {
                onOutput("BUILD SUCCESSFUL\n")
            } else {
                onOutput("BUILD FAILED (exit code: $exitCode)\n")
            }

            onComplete(exitCode == 0, exitCode)
        } catch (e: java.io.IOException) {
            logger.error("Failed to run Gradle task: $task", e)
            onOutput("Error: ${e.message}\n")
            onComplete(false, -1)
        } catch (e: InterruptedException) {
            logger.error("Gradle task interrupted: $task", e)
            onOutput("Error: Task interrupted - ${e.message}\n")
            onComplete(false, -1)
            Thread.currentThread().interrupt()
        }
    }

    /** Finds the Gradle wrapper script in the project directory. */
    private fun findGradleWrapper(projectDir: File): File? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"

        val wrapper = File(projectDir, wrapperName)
        if (wrapper.exists() && wrapper.canExecute()) {
            return wrapper
        }

        // On Unix, try to make it executable if it exists but isn't executable
        if (!isWindows && wrapper.exists()) {
            wrapper.setExecutable(true)
            if (wrapper.canExecute()) {
                return wrapper
            }
        }

        return null
    }

    /** Gets the BuildLogPanel from the gbkt tool window. */
    fun getBuildLogPanel(project: Project): BuildLogPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("gbkt")
        if (toolWindow == null) {
            logger.warn("gbkt tool window not found")
            return null
        }

        // Activate the tool window on EDT
        ApplicationManager.getApplication().invokeLater { toolWindow.show() }

        // Get the BuildLogPanel from the first content (Build Log tab)
        val content = toolWindow.contentManager.getContent(0)
        return content?.component as? BuildLogPanel
    }
}
