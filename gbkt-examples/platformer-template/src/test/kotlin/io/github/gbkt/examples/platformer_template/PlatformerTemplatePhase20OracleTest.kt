/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions

/**
 * Phase 20 FIX-04 visual oracle — D-08 oracle #2.
 *
 * Clone-and-retarget of [PlatformerTemplate128UatTest] (Phase 12.8 precedent). This class owns a
 * Phase-20-specific [EVIDENCE_DIR] pointing to the Phase 20 `evidence/fix-04/` directory so the
 * player-transparency twin shot lands in the correct evidence bucket.
 *
 * Captures a GBC-mode screenshot of the platformer-template player sprite in the `world1Area1`
 * scene, confirming that player transparency is unchanged at HEAD (no tRNS regression).
 *
 * Per the Visual Evidence Rule (CLAUDE.md §"Verification Methodology"), visual truths require a
 * runtime screenshot — variable assertions alone are insufficient. The PNG captured here is the
 * binding visual oracle for FIX-04 Success Criterion 4. Human visual sign-off happens at phase
 * verification; this test provides the mechanical non-blank gate.
 *
 * D-05 LOCKED: platformer-template targets `GbcTarget.GBC_COMPATIBLE`. A DMG-mode capture looks
 * green-tinted and falsely reads as a palette regression versus the approved GBC baseline (MEMORY:
 * `learning_platformer_mcp_needs_gbc_mode`). `gbcMode=true` is mandatory. The `.noi` symFile is
 * auto-discovered by [AgentSessionConfig.discoverFiles] — no explicit path needed.
 *
 * Evidence output:
 * - `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png`
 *
 * Skipped automatically if the ROM is missing — run `./gradlew
 * :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom` first.
 */
class PlatformerTemplatePhase20OracleTest {

    companion object {
        val ROM_FILE = File("build/gbkt/output/platformer-template.gb")
        val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")

        // Phase 20 evidence dir — resolves from gbkt-examples/platformer-template/ (user.dir at
        // test time); ../../ walks up to the repo root.
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/" +
                        "20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04"
                )
                .normalize()
    }

    /**
     * Creates a [StepAgent] in GBC mode (`gbcMode=true`).
     *
     * D-05 LOCKED: platformer-template targets GBC_COMPATIBLE. A DMG-mode capture looks
     * green-tinted and falsely reads as a palette regression versus the approved GBC baseline
     * (learning_platformer_mcp_needs_gbc_mode). Always use GBC mode for this example.
     *
     * The `.noi` symFile is auto-discovered from `build/gbkt/output/platformer-template.noi` by
     * [AgentSessionConfig.discoverFiles] — no explicit path required.
     *
     * Skips the test automatically if `platformer-template.gb` is absent (GBDK not available
     * locally).
     */
    private fun newGbcAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run buildRom first",
        )
        EVIDENCE_DIR.mkdirs()
        // D-05 LOCKED: platformer-template targets GBC_COMPATIBLE; DMG-mode captures look
        // green-tinted and count as palette regressions (learning_platformer_mcp_needs_gbc_mode).
        val baseConfig =
            AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
                .copy(gbcMode = true)
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Captures a screenshot via [StepAgent.captureScreenshot] and renames it to the plan's exact
     * target path under the given anchor subdirectory. JSON sidecar is renamed in lock-step.
     *
     * Mirrors the helper in [PlatformerTemplate128UatTest.captureAndRename].
     */
    private fun captureAndRename(
        agent: StepAgent,
        label: String,
        anchorDir: File,
        targetName: String,
    ): File {
        val captured = agent.captureScreenshot(label)
        val target = File(anchorDir, targetName)
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(anchorDir, target.nameWithoutExtension + ".json")
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

    // ── Phase 20 FIX-04 oracle #2 — platformer player-transparency twin shot ─────────────────
    //
    // Boots the ROM in GBC mode, transitions to world1Area1, navigates the player (RIGHT + periodic
    // A
    // for jumps — held RIGHT alone stalls at the tree obstacle per
    // learning_platformer_traversal_needs_jumps), then captures a screenshot with the player sprite
    // clearly on screen.
    //
    // This capture confirms that the platformer-template player transparency is unchanged at HEAD
    // (no tRNS regression from any Phase 20 changes — D-08 oracle #2).
    //
    // D-05 LOCKED: gbcMode=true is mandatory. DMG captures look green-tinted and falsely flag a
    // palette regression. The .noi symFile is auto-discovered by discoverFiles().
    //
    // Per the Visual Evidence Rule: the PNG is the binding artifact (variable assertions alone
    // are insufficient). Human no-regression sign-off happens at phase verification.

    @Test
    fun `phase20 fix04 platformer player transparency no regression`() {
        newGbcAgent().use { agent ->
            // Boot lead-in — transition to gameplay scene via START press.
            // Use 120 frames to allow banked tileset + tilemap writes to complete.
            agent.stepN(120)

            agent.step(setOf(Button.START))
            agent.step() // release START — neutral input

            // Wait for world1Area1/gameplay scene
            agent.waitForScene("gameplay", maxFrames = 60)
            agent.stepN(30) // setup_current_level + tilemap load settle

            // Navigate the player so the player sprite is clearly visible on screen.
            // RIGHT + periodic A (jumps required) — held RIGHT alone stalls at designed tree
            // obstacle (learning_platformer_traversal_needs_jumps).
            // Navigate for 120 frames — enough to get the player off spawn and clearly visible
            // but not near the level-end trigger (trigger at player_real_x > 448 px; spawn ~80 px).
            repeat(120) { frame ->
                val buttons =
                    if ((frame / 8) % 3 == 0) setOf(Button.RIGHT, Button.A) else setOf(Button.RIGHT)
                agent.step(buttons)
            }

            // Settle one more frame with neutral input
            agent.stepN(5)

            // Capture the player sprite clearly on screen in GBC mode.
            // This PNG is the D-08 FIX-04 visual oracle #2 (no regression gate).
            val png =
                captureAndRename(
                    agent,
                    "phase20-fix04-platformer-player-transparency",
                    EVIDENCE_DIR,
                    "platformer-player-transparency.png",
                )

            // Mechanical non-blank gate — must pass before human visual sign-off
            assertScreenshotIsNonUniform(png, "phase20-fix04-platformer-player-transparency")

            assertTrue(
                png.exists(),
                "FIX-04 oracle #2 PNG must exist: ${png.absolutePath}",
            )
        }
    }
}
