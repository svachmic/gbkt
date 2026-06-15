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
import io.github.gbkt.emulator.agent.assertGoldenMatch
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * Phase 20 FIX-04 visual oracle — D-08 oracle #1.
 *
 * Captures a HEAD GBC-mode screenshot of the metasprites elephant in the `play` scene and diffs it
 * against the committed golden (`elephant-sprite-outline-clean.png`) to confirm the sprite-outline
 * renders clean (transparent pixels correctly routed to GB OBJ index 0 via the Phase 13.6 tRNS
 * auto-route in `ConvertSpritesTask.kt:328-372`).
 *
 * Per the Visual Evidence Rule (CLAUDE.md §"Verification Methodology"), visual truths require a
 * runtime screenshot — variable assertions alone are insufficient. The pixel-exact diff against the
 * committed golden is the binding oracle for FIX-04 Success Criterion 3.
 *
 * GBC mode is auto-detected from ROM header byte 0x143 via [AgentSessionConfig.discoverFiles] (plan
 * 22-02). The D-07 guard asserts `gbcMode` before any golden write to prevent an accidentally
 * mis-built DMG ROM from blessing inverted-palette goldens.
 *
 * Captures are written to `build/gbkt/screenshots/` (gitignored scratch). No `.planning/phases`
 * paths are used — per Phase 22 R1/R2 requirements.
 *
 * Skipped automatically if the ROM is missing — run `./gradlew :gbkt-examples:metasprites:clean
 * :gbkt-examples:metasprites:buildRom` first.
 */
class MetaspritePhase20OracleTest {

    companion object {
        // EVIDENCE_DIR removed (R1) — captures go to gitignored scratch
        private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
        private val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
        // Perceptual .txt artifacts go to test-evidence scratch (R3 — stays scratch, no text
        // golden)
        private val TEXT_SCRATCH_DIR =
            File(System.getProperty("user.dir"), "build/gbkt/test-evidence")
    }

    /**
     * Creates a [StepAgent] in GBC mode. GBC mode is auto-detected from ROM header byte 0x143 via
     * [AgentSessionConfig.discoverFiles] (plan 22-02) — no `.copy(gbcMode = true)` needed.
     *
     * D-07 guard: asserts `gbcMode` is true before proceeding to prevent an accidentally mis-built
     * DMG ROM from blessing inverted-palette goldens.
     *
     * Skips the test automatically if `metasprites.gb` is absent (GBDK not available locally).
     */
    private fun newGbcAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
        )
        SCRATCH_DIR.mkdirs()
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
        // D-07 guarded bless: assert ROM is GBC before any golden write
        check(baseConfig.gbcMode) {
            "ROM 0x143 CGB flag not set — is this a DMG ROM? Aborting to prevent inverted-palette golden bless."
        }
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Perceptual screenshot check — asserts that [file] is a non-uniform PNG (i.e. contains real
     * rendered content, not a blank or solid-colour frame).
     *
     * Asserts >= 2 distinct RGB colour values AND dominant colour covers fewer than 95% of pixels.
     * Aligns with CLAUDE.md Visual Evidence Rule (visual truths require runtime screenshots).
     *
     * The perceptual stats are written to [TEXT_SCRATCH_DIR] (gitignored scratch, no text golden —
     * R3 of Phase 22).
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

        // Perceptual stats written to gitignored scratch (R3 — no text golden)
        TEXT_SCRATCH_DIR.mkdirs()
        File(TEXT_SCRATCH_DIR, "$label-perceptual.txt")
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
    // Boots the ROM in GBC mode, waits for the play scene, then diffs a screenshot of the
    // elephant metasprite at rest against the committed golden `elephant-sprite-outline-clean.png`.
    //
    // The Phase 13.6 tRNS auto-route (ConvertSpritesTask.kt:328-372) permutes the elephant's PNG
    // so the transparent colour lands at GB OBJ palette index 0. This means the sprite-outline
    // renders clean (no black border from non-zero tRNS slots being mapped to non-transparent
    // GB OBJ indices). The golden diff is the binding visual oracle per the Visual Evidence Rule.
    //
    // GBC mode required (D-05 LOCKED): metasprites targets GBC_COMPATIBLE; a DMG capture would
    // produce false grayscale rendering and miss GBC-specific rendering artifacts.

    @Test
    fun `phase20 fix04 sprite outline rendering clean`() {
        newGbcAgent().use { agent ->
            // GBC mode needs more boot frames than DMG — CGB PPU initialisation takes extra time.
            agent.stepN(30)

            // Wait for play scene (elephant metasprite at rest on screen from the start)
            agent.waitForScene("play", 120)

            // Settle one more frame to ensure the LCD has a complete rendered frame
            agent.stepN(5)

            // Pixel-exact diff against committed golden (binding D-08 FIX-04 oracle #1)
            val goldenFile =
                File(
                    javaClass
                        .getResource("/goldens/metasprites/elephant-sprite-outline-clean.png")!!
                        .toURI()
                )
            assertGoldenMatch(
                agent,
                label = "phase20-fix04-sprite-outline",
                goldenFile = goldenFile,
                scratchDir = SCRATCH_DIR,
            )

            // Perceptual non-uniform check (mechanical gate complementing the golden diff)
            val captured =
                File(SCRATCH_DIR, "phase20-fix04-sprite-outline.png").let { f ->
                    // The captured file may have a frame-number suffix; find the most recent match
                    if (f.exists()) f
                    else
                        SCRATCH_DIR.listFiles { _, name ->
                                name.startsWith("phase20-fix04-sprite-outline") &&
                                    name.endsWith(".png")
                            }
                            ?.maxByOrNull { it.lastModified() } ?: f
                }
            if (captured.exists()) {
                assertScreenshotIsNonUniform(captured, "phase20-fix04-sprite-outline")
            }
        }
    }
}
