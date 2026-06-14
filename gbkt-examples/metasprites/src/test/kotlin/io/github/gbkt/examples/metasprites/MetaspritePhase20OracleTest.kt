/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * Phase 20 FIX-04 visual oracle — D-08 oracle #1.
 *
 * Captures a HEAD GBC-mode screenshot of the metasprites elephant in the `play` scene to confirm
 * the sprite-outline renders clean (transparent pixels correctly routed to GB OBJ index 0 via the
 * Phase 13.6 tRNS auto-route in `ConvertSpritesTask.kt:328-372`).
 *
 * Per the Visual Evidence Rule (CLAUDE.md §"Verification Methodology"), visual truths require a
 * runtime screenshot — variable assertions alone are insufficient. The PNG captured here is the
 * binding visual oracle for FIX-04 Success Criterion 3. Human visual sign-off happens at phase
 * verification; this test provides the mechanical non-blank gate.
 *
 * GBC mode is required: the metasprites example targets `GbcTarget.GBC_COMPATIBLE`. A DMG capture
 * produces false grayscale rendering (MEMORY: `learning_platformer_mcp_needs_gbc_mode`).
 *
 * Evidence output:
 * - `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png`
 *
 * Skipped automatically if the ROM is missing — run `./gradlew :gbkt-examples:metasprites:clean
 * :gbkt-examples:metasprites:buildRom` first.
 */
class MetaspritePhase20OracleTest {

    companion object {
        // Phase 20 evidence dir — resolves from gbkt-examples/metasprites/ (user.dir at test time)
        // ../../ walks up to the repo root.
        private val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/" +
                        "20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04"
                )
                .normalize()
        private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    }

    /**
     * Creates a [StepAgent] in GBC mode (`gbcMode=true`). The metasprites example targets
     * `GbcTarget.GBC_COMPATIBLE` — a DMG capture would produce false grayscale rendering, making it
     * inadequate evidence for the tRNS sprite-outline oracle (D-05).
     *
     * The `.noi` symFile is auto-discovered from `build/gbkt/output/metasprites.noi` via
     * [AgentSessionConfig.discoverFiles] — no manual path required.
     *
     * Skips the test automatically if `metasprites.gb` is absent (GBDK not available locally).
     */
    private fun newGbcAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
        )
        EVIDENCE_DIR.mkdirs()
        val baseConfig =
            AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
                .copy(gbcMode = true) // GBC_COMPATIBLE target — D-05 requires authentic mode
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Captures a screenshot via [StepAgent.captureScreenshot] and renames the produced file to the
     * Phase 20 target path inside [EVIDENCE_DIR]. JSON sidecar is also renamed in lock-step
     * (best-effort).
     */
    private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
        val captured = agent.captureScreenshot(label)
        val target = File(EVIDENCE_DIR, targetName)
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        // Sidecar JSON: rename in lock-step (best-effort; not required by plan).
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(EVIDENCE_DIR, target.nameWithoutExtension + ".json")
            if (targetJson.exists()) targetJson.delete()
            sidecar.renameTo(targetJson)
        }
        return target
    }

    /**
     * Perceptual screenshot check — asserts that [file] is a non-uniform PNG (i.e. contains real
     * rendered content, not a blank or solid-colour frame).
     *
     * Asserts >= 2 distinct RGB colour values AND dominant colour covers fewer than 95% of pixels.
     * Aligns with CLAUDE.md Visual Evidence Rule (visual truths require runtime screenshots).
     *
     * Copied verbatim from [PlatformerTemplate128UatTest.assertScreenshotIsNonUniform].
     */
    private fun assertScreenshotIsNonUniform(file: File, label: String) {
        val img =
            ImageIO.read(file) ?: fail("$label: file is not a valid PNG: ${file.absolutePath}")

        val colours = mutableSetOf<Int>()
        val histogram = mutableMapOf<Int, Int>()
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y) and 0xFFFFFF
                colours.add(rgb)
                histogram[rgb] = (histogram[rgb] ?: 0) + 1
            }
        }

        assertTrue(
            colours.size >= 2,
            "$label: screenshot must contain >= 2 distinct RGB colours " +
                "(a blank frame has 1; any real tile content uses >= 2 shades). " +
                "Found ${colours.size} distinct colour(s). " +
                "File: ${file.absolutePath}",
        )

        val totalPixels = img.width * img.height
        val dominantCount = histogram.values.max()
        val dominantRatio = dominantCount.toDouble() / totalPixels

        assertTrue(
            dominantRatio < 0.95,
            "$label: dominant colour must cover < 95% of pixels " +
                "(guards against near-blank frames with only a pixel border of content). " +
                "Dominant-colour ratio: ${"%.3f".format(dominantRatio)} " +
                "(${dominantCount}/${totalPixels} pixels). " +
                "File: ${file.absolutePath}",
        )

        File(EVIDENCE_DIR, "$label-perceptual.txt")
            .writeText(
                "file: ${file.absolutePath}\n" +
                    "dimensions: ${img.width}x${img.height}\n" +
                    "distinct_colours: ${colours.size}\n" +
                    "dominant_ratio: ${"%.4f".format(dominantRatio)}\n" +
                    "dominant_count: $dominantCount\n" +
                    "total_pixels: $totalPixels\n"
            )
    }

    // ── Phase 20 FIX-04 oracle #1 — metasprites elephant sprite-outline clean ─────────────────
    //
    // Boots the ROM in GBC mode, waits for the play scene, then captures a screenshot with the
    // elephant metasprite at rest on screen.
    //
    // The Phase 13.6 tRNS auto-route (ConvertSpritesTask.kt:328-372) permutes the elephant's PNG
    // so the transparent colour lands at GB OBJ palette index 0. This means the sprite-outline
    // renders clean (no black border from non-zero tRNS slots being mapped to non-transparent
    // GB OBJ indices). The PNG is the binding visual oracle per the Visual Evidence Rule.
    //
    // GBC mode required (D-05 LOCKED): metasprites targets GBC_COMPATIBLE; a DMG capture would
    // produce false grayscale rendering and miss GBC-specific rendering artifacts.
    //
    // Variable evidence alone is insufficient per the Visual Evidence Rule — the PNG artifact
    // is the binding D-08 oracle. Human visual sign-off happens at phase verification.

    @Test
    fun `phase20 fix04 sprite outline rendering clean`() {
        newGbcAgent().use { agent ->
            // GBC mode needs more boot frames than DMG — CGB PPU initialisation takes extra time.
            agent.stepN(30)

            // Wait for play scene (elephant metasprite at rest on screen from the start)
            agent.waitForScene("play", 120)

            // Settle one more frame to ensure the LCD has a complete rendered frame
            agent.stepN(5)

            // Capture the elephant at rest — transparent pixels at OBJ index 0 (tRNS auto-route)
            // means no black outline. This PNG is the D-08 FIX-04 visual oracle #1.
            val png =
                captureAndRename(
                    agent,
                    "phase20-fix04-sprite-outline",
                    "metasprites-sprite-outline.png",
                )

            // Mechanical non-blank gate — must pass before human visual sign-off
            assertScreenshotIsNonUniform(png, "phase20-fix04-sprite-outline")

            assertTrue(
                png.exists(),
                "FIX-04 oracle #1 PNG must exist: ${png.absolutePath}",
            )
        }
    }
}
