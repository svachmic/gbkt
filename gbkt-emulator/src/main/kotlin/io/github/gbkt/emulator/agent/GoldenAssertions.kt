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
 * - Creates parent directories of [goldenFile] if needed.
 * - Raw-copies [capturedFile] to [goldenFile] via [File.copyTo] (NEVER re-encodes through ImageIO —
 *   raw copy preserves byte identity, which is the only correct bless strategy; see Phase 22
 *   research Pitfall 2).
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
        goldenFile.parentFile?.mkdirs()
        capturedFile.copyTo(goldenFile, overwrite = true)
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
