/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.emulator.agent.VisualDiff
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task that performs a pixel-level screenshot comparison between two PNG files.
 *
 * Compares [expectedFile] and [actualFile] pixel-by-pixel. Mismatched pixels beyond the configured
 * [tolerance] fraction cause the task to throw a [GradleException] (non-zero Gradle exit code).
 * When images differ, a diff PNG highlighting mismatched pixels in red is written next to
 * [actualFile].
 *
 * This task does NOT depend on `buildRom` or run the emulator — it is a pure file comparison. Use
 * it in combination with [CaptureScreenshotTask] to implement visual regression tests.
 *
 * Usage:
 * ```
 * ./gradlew diffScreenshots --expected=ref.png --actual=current.png
 * ./gradlew diffScreenshots --expected=ref.png --actual=current.png --tolerance=0.05
 * ```
 *
 * A tolerance of 0.05 allows up to 5% of pixels to differ (useful for anti-aliasing or minor
 * rendering variation). Default tolerance is 0.0 (pixel-perfect match required).
 */
@DisableCachingByDefault(
    because =
        "Screenshot diff compares live files — caching the comparison result would hide regressions"
)
abstract class DiffScreenshotsTask : DefaultTask() {

    /** Reference (expected) PNG file to compare against. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expectedFile: RegularFileProperty

    /** Actual PNG file captured from the emulator. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val actualFile: RegularFileProperty

    /**
     * Fraction of pixels allowed to differ (0.0 = pixel-perfect, 0.05 = 5%). Default: 0.0
     * (pixel-perfect).
     */
    @get:Input abstract val tolerance: Property<Double>

    init {
        group = "gbkt-agent"
        description = "Compare two screenshots pixel-by-pixel (agent-callable, fails on mismatch)"
        tolerance.convention(0.0)
    }

    @TaskAction
    fun run() {
        val expected = expectedFile.get().asFile
        val actual = actualFile.get().asFile
        val tol = tolerance.get()

        if (!expected.exists()) {
            throw GradleException("Expected screenshot not found: ${expected.absolutePath}")
        }
        if (!actual.exists()) {
            throw GradleException("Actual screenshot not found: ${actual.absolutePath}")
        }

        logger.lifecycle(
            "diffScreenshots: comparing ${expected.name} vs ${actual.name} (tolerance=$tol)..."
        )

        val diffOutputDir = actual.parentFile
        val result = VisualDiff.compare(expected, actual, tol, diffOutputDir)

        if (result.match) {
            logger.lifecycle(
                "diffScreenshots: MATCH — ${result.diffCount} differing pixels out of ${result.totalPixels} " +
                    "(${String.format("%.2f", result.diffCount.toDouble() / result.totalPixels * 100)}%)"
            )
        } else {
            val diffMsg =
                "${result.diffCount} pixels differ out of ${result.totalPixels} " +
                    "(${String.format("%.2f", result.diffCount.toDouble() / result.totalPixels * 100)}%)" +
                    (result.diffImage?.let { " — diff image: ${it.absolutePath}" } ?: "")
            logger.lifecycle("diffScreenshots: MISMATCH — $diffMsg")
            throw GradleException("Screenshot mismatch: $diffMsg")
        }
    }
}
