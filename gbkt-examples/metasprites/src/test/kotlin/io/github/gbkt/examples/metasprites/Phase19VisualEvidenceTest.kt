/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.StepAgent
import io.github.gbkt.emulator.agent.assertGoldenMatch
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions

/**
 * Phase 19 visual evidence capture — fresh HEAD GBC-mode screenshots for FIX-01 seeds
 * (SEED-004/005/006/013) and the Req-3 ROM-smoke shot, all diffed against committed golden PNGs.
 *
 * Visual truths require runtime screenshot evidence (Visual Evidence Rule, CLAUDE.md §
 * "Verification Methodology"); variable assertions are insufficient. Every capture uses
 * [newGbcAgent] (GBC mode auto-detected from ROM 0x143 via `AgentSessionConfig.discoverFiles`)
 * because the metasprites example targets `GbcTarget.GBC_COMPATIBLE` — a DMG-mode capture produces
 * false grayscale rendering (MEMORY: `learning_platformer_mcp_needs_gbc_mode`).
 *
 * Diffs performed via [assertGoldenMatch] against committed goldens under
 * `src/test/resources/goldens/metasprites/` (migrated in Plan 22-04, byte-identical).
 *
 * Captures are written to `build/gbkt/screenshots/` (gitignored scratch). No `.planning/phases`
 * paths are used — per Phase 22 R1/R2 requirements.
 *
 * Skipped automatically if the ROM is missing — run `./gradlew :gbkt-examples:metasprites:clean
 * :gbkt-examples:metasprites:buildRom` first.
 */
class Phase19VisualEvidenceTest {

    companion object {
        // EVIDENCE_DIR removed (R1) — captures go to gitignored scratch
        private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
        private val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
    }

    /**
     * Creates a [StepAgent] in GBC mode. GBC mode is auto-detected from ROM header byte 0x143 via
     * [AgentSessionConfig.discoverFiles] (plan 22-02) — no `.copy(gbcMode = true)` needed.
     *
     * D-07 guard: asserts `gbcMode` is true before proceeding to prevent an accidentally mis-built
     * DMG ROM from blessing inverted-palette goldens.
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

    // ── Boot frame — SEED-004 (elephant tiles uncorrupted) + SEED-005 (BG checkerboard) ────
    //
    // A single boot/play frame covers both seeds:
    //   SEED-004: elephant metasprite tiles render uncorrupted (no VRAM collision from SEED-008
    // fix)
    //   SEED-005: background fills with a checkerboard pattern, not a diagonal ramp
    //
    // GBC mode is required even for these boot-frame seeds: the metasprites example targets
    // GBC_COMPATIBLE and the sub-palette fix in earlier phases only applies under GBC PPU.
    // A DMG capture would omit any GBC-specific rendering artifacts that might indicate regression.
    //
    // ROM-smoke (Req 3) reuses this test method's boot frame — same ROM, same scene entry, same
    // rendering path. Captured separately as a named golden for traceability.

    @Test
    fun `bootFrame capturesSeed004And005`() {
        newGbcAgent().use { agent ->
            // GBC mode needs 30 boot frames (not 10) — CGB PPU init takes extra time.
            agent.stepN(30)

            val playObs = agent.waitForScene("play", 120)
            assertTrue(
                playObs.scene == "play",
                "Timed out waiting for play scene after stepN(30) — got: ${playObs.scene}",
            )

            // SEED-004: elephant tiles uncorrupted on a clean boot frame
            assertGoldenMatch(
                agent,
                label = "seed004-boot",
                goldenFile =
                    File(
                        javaClass
                            .getResource("/goldens/metasprites/elephant-boot-seed004.png")!!
                            .toURI()
                    ),
                scratchDir = SCRATCH_DIR,
            )

            // SEED-005: same boot frame — BG checkerboard visible
            assertGoldenMatch(
                agent,
                label = "seed005-boot",
                goldenFile =
                    File(
                        javaClass
                            .getResource(
                                "/goldens/metasprites/elephant-boot-seed005-checkerboard.png"
                            )!!
                            .toURI()
                    ),
                scratchDir = SCRATCH_DIR,
            )

            // ROM-smoke: same boot frame proves the ROM renders correctly at HEAD (Req 3)
            assertGoldenMatch(
                agent,
                label = "rom-smoke",
                goldenFile =
                    File(
                        javaClass.getResource("/goldens/metasprites/rom-smoke-boot.png")!!.toURI()
                    ),
                scratchDir = SCRATCH_DIR,
            )
        }
    }

    // ── Sub-palette climax — SEED-006 (subPalette assigned) + SEED-013 (GBC colors correct) ───
    //
    // Drive 8 A-press/release cycles to reach rot=8, subpal=rot>>2=2 (cyan palette).
    // Sub-palette mechanism:
    //   rot 0-3  → subpal 0 (gray)
    //   rot 4-7  → subpal 1 (pink)
    //   rot 8-11 → subpal 2 (cyan)
    //   rot 12-15 → subpal 3 (green)
    //
    // SEED-006: proves the subPalette global is set before moveMetasprite() executes
    // SEED-013: proves the correct GBC OBJ palette colors are rendered on-screen
    //
    // GBC MODE IS MANDATORY for these seeds — sub-palette bits in the OAM attribute byte
    // are hardware-ignored on DMG. Grayscale output = accidental DMG capture = FAIL.
    // Wait 2 extra frames after reaching rot=8 for GBC PPU palette flush before capture.

    @Test
    fun `subPaletteClimax capturesSeed006And013`() {
        newGbcAgent().use { agent ->
            // GBC mode needs 30 boot frames — CGB PPU init takes extra time.
            agent.stepN(30)

            val playObs = agent.waitForScene("play", 120)
            assertTrue(
                playObs.scene == "play",
                "Timed out waiting for play scene after stepN(30) — got: ${playObs.scene}",
            )

            // First cycle: 4 A presses → rot=4 (subpal=1 pink)
            // Press 1: rot 0 → 1
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 2: rot 1 → 2
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 3: rot 2 → 3
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 4: rot 3 → 4 (subpal=1 pink)
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Second cycle: 4 more A presses → rot=8 (subpal=2 cyan — climax frame)
            // Press 5: rot 4 → 5
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 6: rot 5 → 6
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 7: rot 6 → 7
            agent.step(setOf(Button.A))
            agent.step(emptySet()) // release frame

            // Press 8: rot 7 → 8 (subpal=rot>>2=2 cyan — climax frame)
            agent.step(setOf(Button.A))

            // Wait 2 extra frames for GBC PPU to flush the new palette to screen
            agent.step(emptySet())
            agent.step(emptySet())

            // Confirm rot==8 before capture — if any A-press was missed, fail here rather than
            // silently capturing a wrong-state frame (mirrors MetaspriteUatTest.behavior3 idiom).
            val rot = agent.readVariable("rot")
            assertEquals(
                8,
                rot,
                "rot must be 8 at SEED-006/013 capture (subpal=cyan); got: $rot — " +
                    "check edge-detection release frames",
            )

            // SEED-006: subPalette global correctly set before moveMetasprite() — cyan visible
            assertGoldenMatch(
                agent,
                label = "seed006-subpalette",
                goldenFile =
                    File(
                        javaClass
                            .getResource("/goldens/metasprites/elephant-cyan-subpalette.png")!!
                            .toURI()
                    ),
                scratchDir = SCRATCH_DIR,
            )

            // SEED-013: correct GBC sub-palette colors — same climax frame
            assertGoldenMatch(
                agent,
                label = "seed013-gbccolors",
                goldenFile =
                    File(
                        javaClass
                            .getResource("/goldens/metasprites/elephant-gbc-colors.png")!!
                            .toURI()
                    ),
                scratchDir = SCRATCH_DIR,
            )
        }
    }
}
