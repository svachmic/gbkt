/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.io.File

/**
 * JVM system-property key used to activate golden re-baseline mode.
 *
 * When this property is set (any value), [assertGoldenMatch] and [compareOrBless] write the
 * captured PNG as the new golden instead of diffing against it.
 *
 * Named constant per Project Rule #1 — no magic strings. Callers must reference this constant
 * rather than inlining the literal `"gbkt.updateGoldens"`.
 *
 * Activation (Gradle CLI):
 * ```
 * ./gradlew test -Pgbkt.updateGoldens
 * ```
 */
const val GBKT_UPDATE_GOLDENS_PROP = "gbkt.updateGoldens"

/**
 * Asserts that the current LCD frame captured by [agent] is pixel-identical to [goldenFile].
 *
 * Captures the frame via [StepAgent.captureScreenshot] (which writes to the agent's configured
 * `screenshotDir`), then delegates to [compareOrBless] for the diff/update logic.
 *
 * **GBC-header guard (D-07):** This function does NOT read the ROM header. GBC-target callers are
 * responsible for asserting `config.gbcMode == true` before calling, so a mis-built DMG ROM cannot
 * bless an inverted-palette golden. See `AgentSessionConfig.discoverFiles` for auto-detection.
 *
 * @param agent The [StepAgent] whose current frame buffer will be captured.
 * @param label Human-readable label for the screenshot file name prefix.
 * @param goldenFile The committed golden PNG to diff against.
 * @param scratchDir Directory used for captured PNGs and diff images. Should be gitignored.
 * @throws AssertionError if golden is missing and update-mode is off, or if captured PNG differs by
 *   ≥ 1 pixel from [goldenFile].
 */
fun assertGoldenMatch(
    agent: StepAgent,
    label: String,
    goldenFile: File,
    scratchDir: File,
) {
    val captured = agent.captureScreenshot(label)
    compareOrBless(goldenFile, captured, scratchDir)
}

/**
 * Internal diff/bless delegate — unit-testable without a live emulator.
 *
 * In **diff mode** (default — system property absent):
 * - If [goldenFile] does not exist: throws [AssertionError] whose message names [goldenFile] and
 *   the [GBKT_UPDATE_GOLDENS_PROP] re-baseline hint (D-05).
 * - If [goldenFile] exists: calls [VisualDiff.compare] at `tolerance = 0.0` (exact match, D-04). On
 *   mismatch: throws [AssertionError] reporting `diffCount/totalPixels` and the diff image path. On
 *   match: returns normally (pass).
 *
 * In **update mode** ([GBKT_UPDATE_GOLDENS_PROP] system property set):
 * - Resolves the **source-tree** golden destination via [sourceGoldenDestination]. Real callers
 *   resolve `goldenFile` from the classpath (`javaClass.getResource("/goldens/…")`), which points
 *   at the gitignored `build/resources/test/…` copy that `processTestResources` regenerates from
 *   `src/`. Writing the blessed PNG there silently no-ops (the next `clean`/`build` wipes it and
 *   the committed golden never changes). To make `-Pgbkt.updateGoldens` actually persist, any
 *   `goldenFile` path under the standard Gradle `build/resources/test` segment is remapped to the
 *   matching `src/test/resources` path before writing. Non-build paths (e.g. unit tests passing a
 *   plain `File`) are written as-is.
 * - Creates parent directories of the resolved destination if needed.
 * - Raw-copies [capturedFile] to the destination via [File.copyTo] (NEVER re-encodes through
 *   ImageIO — raw copy preserves byte identity, which is the only correct bless strategy; see Phase
 *   22 research Pitfall 2).
 * - Returns normally (pass) — no diff is performed.
 *
 * @param goldenFile Committed golden PNG (read in diff mode, written in update mode).
 * @param capturedFile Scratch PNG captured by the current test run.
 * @param scratchDir Directory for diff images on mismatch. Passed as [VisualDiff.compare]'s
 *   `diffOutputDir`.
 */
fun compareOrBless(
    goldenFile: File,
    capturedFile: File,
    scratchDir: File,
) {
    val updateMode = System.getProperty(GBKT_UPDATE_GOLDENS_PROP) != null

    if (updateMode) {
        val destination = sourceGoldenDestination(goldenFile)
        destination.parentFile?.mkdirs()
        capturedFile.copyTo(destination, overwrite = true)
        return
    }

    if (!goldenFile.exists()) {
        throw AssertionError(
            "GOLDEN MISSING: ${goldenFile.absolutePath} — run with " +
                "-P$GBKT_UPDATE_GOLDENS_PROP to bless the current capture as the golden"
        )
    }

    val result =
        VisualDiff.compare(
            expected = goldenFile,
            actual = capturedFile,
            tolerance = 0.0,
            diffOutputDir = scratchDir,
        )

    if (!result.match) {
        val diffPath = result.diffImage?.absolutePath ?: "<no diff image>"
        throw AssertionError(
            "Golden mismatch for ${goldenFile.name}: " +
                "${result.diffCount}/${result.totalPixels} pixels differ. " +
                "Expected: ${goldenFile.absolutePath} " +
                "Actual:   ${capturedFile.absolutePath} " +
                "Diff:     $diffPath — " +
                "run with -P$GBKT_UPDATE_GOLDENS_PROP to update the golden"
        )
    }
}

/** Gradle's processed test-resources segment; classpath URLs resolve goldens under here. */
private const val BUILD_TEST_RESOURCES_SEGMENT = "build/resources/test"

/** Source-tree test-resources segment that `processTestResources` copies into the build dir. */
private const val SRC_TEST_RESOURCES_SEGMENT = "src/test/resources"

/**
 * Resolves the **source-tree** destination a blessed golden must be written to.
 *
 * Real callers obtain [goldenFile] via `javaClass.getResource("/goldens/…")`, which resolves to the
 * gitignored `build/resources/test/goldens/…` copy that Gradle's `processTestResources` regenerates
 * from `src/`. Writing a blessed PNG there silently no-ops (it is wiped on the next
 * `clean`/`build`).
 *
 * This maps any path containing the standard `build/resources/test` segment to the matching
 * `src/test/resources` path so `-Pgbkt.updateGoldens` updates the committed golden. The check is
 * OS-invariant ([File.invariantSeparatorsPath] normalizes `\` to `/`). Paths that do not contain
 * the build segment (e.g. unit tests passing a plain `File`) are returned unchanged.
 */
internal fun sourceGoldenDestination(goldenFile: File): File {
    val invariant = goldenFile.invariantSeparatorsPath
    if (!invariant.contains(BUILD_TEST_RESOURCES_SEGMENT)) return goldenFile
    val remapped = invariant.replace(BUILD_TEST_RESOURCES_SEGMENT, SRC_TEST_RESOURCES_SEGMENT)
    return File(remapped)
}
