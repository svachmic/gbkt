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

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.gbkt.intellij.build.GradleRunner
import io.github.gbkt.intellij.sdk.EmulatorType
import io.github.gbkt.intellij.sdk.GbktSdkService
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Action to build and run a gbkt ROM in the configured emulator.
 *
 * Performs:
 * 1. Build the ROM (if not up to date)
 * 2. Launch the configured emulator with the ROM
 */
class RunInEmulatorAction : AnAction() {

    private val logger = Logger.getInstance(RunInEmulatorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sdkService = GbktSdkService.getInstance(project)

        if (sdkService.emulatorPath == null) {
            logger.warn("No emulator configured")
            showNotification(
                project,
                "Emulator Not Configured",
                "Please configure an emulator in Settings → Tools → gbkt to run ROMs.",
                NotificationType.WARNING,
            )
            return
        }

        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Running in Emulator", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.text = "Building ROM..."
                        indicator.fraction = 0.2

                        // Build ROM first
                        val buildSuccess = buildRom(project, indicator)
                        if (!buildSuccess) {
                            showNotification(
                                project,
                                "Build Failed",
                                "ROM build failed. Check the build output for details.",
                                NotificationType.ERROR,
                            )
                            return
                        }

                        indicator.text = "Launching emulator..."
                        indicator.fraction = 0.8

                        launchEmulator(sdkService)

                        indicator.text = "Emulator launched"
                        indicator.fraction = 1.0
                    }
                }
            )
    }

    private fun buildRom(project: Project, indicator: ProgressIndicator): Boolean {
        val buildSuccess = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        GradleRunner.runTask(
            project = project,
            task = "buildRom",
            onOutput = { line -> indicator.text2 = line.take(80) },
            onComplete = { success, _ ->
                buildSuccess.set(success)
                latch.countDown()
            },
        )

        // Wait for build to complete
        latch.await()
        return buildSuccess.get()
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("gbkt.notifications")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun launchEmulator(sdkService: GbktSdkService) {
        val emulatorPath = sdkService.emulatorPath ?: return
        val romPath = findRomFile(sdkService) ?: return

        logger.info("Launching ${sdkService.emulatorType} with ROM: $romPath")

        val processBuilder =
            when (sdkService.emulatorType) {
                EmulatorType.BGB -> ProcessBuilder(emulatorPath.toString(), romPath)
                EmulatorType.SAMEBOY -> ProcessBuilder(emulatorPath.toString(), romPath)
                EmulatorType.MGBA -> ProcessBuilder(emulatorPath.toString(), romPath)
                else -> ProcessBuilder(emulatorPath.toString(), romPath)
            }

        try {
            processBuilder.start()
        } catch (ex: IOException) {
            logger.error("Failed to launch emulator", ex)
        }
    }

    private fun findRomFile(sdkService: GbktSdkService): String? {
        // Look for ROM file in standard build output locations
        val gbdkPath = sdkService.gbdkPath ?: return null
        val buildDir = gbdkPath.parent?.resolve("build")
        val romFiles =
            buildDir?.toFile()?.listFiles { file -> file.extension in listOf("gb", "gbc") }
        return romFiles?.maxByOrNull { it.lastModified() }?.absolutePath
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val sdkService = GbktSdkService.getInstance(project)
        e.presentation.isEnabled = sdkService.emulatorPath != null
    }
}
