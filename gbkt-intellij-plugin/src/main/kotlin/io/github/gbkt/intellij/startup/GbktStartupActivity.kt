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
package io.github.gbkt.intellij.startup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.github.gbkt.intellij.sdk.GbktSdkService
import io.github.gbkt.intellij.sdk.SdkStatus

/**
 * Startup activity for gbkt projects.
 *
 * Runs when a project is opened and performs:
 * - Detection of gbkt project (looks for .gbkt.kts files or build.gradle.kts with gbkt plugin)
 * - SDK validation and auto-detection
 * - Notification if SDK is not configured
 * - Emulator detection
 */
class GbktStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(GbktStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("gbkt startup activity running for project: ${project.name}")

        // Check if this is a gbkt project
        if (!isGbktProject(project)) {
            logger.info("Not a gbkt project, skipping initialization")
            return
        }

        logger.info("Detected gbkt project: ${project.name}")

        // Initialize SDK service (triggers auto-detection)
        val sdkService = GbktSdkService.getInstance(project)

        // Show notification based on SDK status
        when (sdkService.sdkStatus) {
            SdkStatus.NOT_CONFIGURED,
            SdkStatus.NOT_FOUND -> {
                showSdkNotFoundNotification(project)
            }
            SdkStatus.INVALID -> {
                showSdkInvalidNotification(project)
            }
            SdkStatus.CONFIGURED -> {
                logger.info("GBDK-2020 configured at: ${sdkService.gbdkPath}")
                // Optionally show success notification on first setup
            }
        }

        // Log emulator status
        if (sdkService.emulatorPath != null) {
            logger.info(
                "Emulator configured: ${sdkService.emulatorType} at ${sdkService.emulatorPath}"
            )
        } else {
            logger.info("No emulator configured")
        }
    }

    /**
     * Checks if the project is a gbkt project.
     *
     * Looks for:
     * - .gbkt.kts files in the project
     * - gbkt plugin in build.gradle.kts
     * - gbkt-core dependency
     */
    private fun isGbktProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val projectDir = java.io.File(basePath)

        return hasGbktFiles(projectDir) ||
            fileContainsGbktReference(java.io.File(projectDir, "build.gradle.kts")) ||
            fileContainsGbktReference(java.io.File(projectDir, "settings.gradle.kts"))
    }

    /** Check if directory contains any .gbkt.kts files. */
    private fun hasGbktFiles(projectDir: java.io.File): Boolean {
        return try {
            projectDir.walkTopDown().any { it.name.endsWith(".gbkt.kts") }
        } catch (e: SecurityException) {
            logger.warn("Security exception while scanning for .gbkt.kts files", e)
            false
        }
    }

    /** Check if a file exists and contains gbkt references. */
    private fun fileContainsGbktReference(file: java.io.File): Boolean {
        if (!file.exists()) return false

        return try {
            val content = file.readText()
            content.contains("gbkt") || content.contains("io.github.gbkt")
        } catch (e: java.io.IOException) {
            logger.warn("Failed to read ${file.name}", e)
            false
        } catch (e: SecurityException) {
            logger.warn("Security exception reading ${file.name}", e)
            false
        }
    }

    private fun showSdkNotFoundNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("gbkt.notifications")
            ?.createNotification(
                "GBDK-2020 Not Found",
                "gbkt requires GBDK-2020 to compile ROMs. " +
                    "<a href='https://github.com/gbdk-2020/gbdk-2020'>Download GBDK-2020</a> " +
                    "and configure the path in Settings > gbkt.",
                NotificationType.WARNING,
            )
            ?.notify(project)
            ?: logger.warn(
                "Could not show SDK not found notification - notification group not found"
            )
    }

    private fun showSdkInvalidNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("gbkt.notifications")
            ?.createNotification(
                "GBDK-2020 Invalid",
                "The configured GBDK-2020 path is invalid. " +
                    "Please check your configuration in Settings > gbkt.",
                NotificationType.ERROR,
            )
            ?.notify(project)
            ?: logger.warn("Could not show SDK invalid notification - notification group not found")
    }
}
