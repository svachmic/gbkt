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
import io.github.gbkt.emulator.agent.assertGoldenMatch
import java.io.File
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions

/**
 * Phase 20 FIX-04 visual oracle — D-08 oracle #2.
 *
 * Captures a GBC-mode screenshot of the platformer-template player sprite in the `world1Area1`
 * scene, confirming that player transparency is unchanged at HEAD (no tRNS regression).
 *
 * Per the Visual Evidence Rule (CLAUDE.md §"Verification Methodology"), visual truths require a
 * runtime screenshot — variable assertions alone are insufficient. The PNG diff via
 * [assertGoldenMatch] against the committed golden is the binding mechanical gate.
 *
 * Phase 22 (22-07) migration: EVIDENCE_DIR removed (R1). GBC mode is auto-detected via
 * [AgentSessionConfig.discoverFiles] from ROM 0x143 (22-02); the D-07 GBC-header guard asserts the
 * ROM is GBC before any golden write. Screenshots capture to gitignored SCRATCH_DIR under build/;
 * the 1 blessed anchor diffs against src/test/resources/goldens/platformer-template/
 * platformer-player-transparency.png via assertGoldenMatch.
 *
 * Skipped automatically if the ROM is missing — run `./gradlew
 * :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom` first.
 */
class PlatformerTemplatePhase20OracleTest {

    companion object {
        val ROM_FILE = File("build/gbkt/output/platformer-template.gb")
        val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
        // Phase 22 (22-07): capture to gitignored scratch under build/ — no .planning/phases path.
        val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
    }

    /**
     * Creates a [StepAgent] in GBC mode, auto-detected from ROM 0x143 via
     * [AgentSessionConfig.discoverFiles] (22-02). The D-07 guard asserts the ROM is GBC so a
     * mis-built DMG ROM cannot bless an inverted-palette golden.
     *
     * Skips the test automatically if `platformer-template.gb` is absent (GBDK not available
     * locally).
     */
    private fun newGbcAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "platformer-template.gb not found — run buildRom first",
        )
        SCRATCH_DIR.mkdirs()
        // Phase 22 (D-07 guard): discoverFiles() auto-detects gbcMode from ROM 0x143 (22-02).
        // Assert GBC mode is active so a mis-built DMG ROM cannot bless an inverted-palette golden.
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
        check(baseConfig.gbcMode) {
            "ROM 0x143 CGB flag not set — is this a DMG ROM? " +
                "Aborting to prevent inverted-palette golden bless."
        }
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    // ── Phase 20 FIX-04 oracle #2 — platformer player-transparency twin shot ─────────────────
    //
    // Boots the ROM in GBC mode, transitions to world1Area1, navigates the player (RIGHT + periodic
    // A for jumps — held RIGHT alone stalls at the tree obstacle per
    // learning_platformer_traversal_needs_jumps), then captures a screenshot with the player sprite
    // clearly on screen.
    //
    // Phase 22 (22-07): diffs against committed golden
    // src/test/resources/goldens/platformer-template/platformer-player-transparency.png via
    // assertGoldenMatch. D-07 guard asserts ROM is GBC before any golden write.

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
            // Phase 22 (22-07): diff against committed golden via assertGoldenMatch.
            val goldenFile =
                File(
                    javaClass
                        .getResource(
                            "/goldens/platformer-template/platformer-player-transparency.png"
                        )!!
                        .toURI()
                )
            assertGoldenMatch(
                agent,
                "phase20-fix04-platformer-player-transparency",
                goldenFile = goldenFile,
                scratchDir = SCRATCH_DIR,
            )
        }
    }
}
