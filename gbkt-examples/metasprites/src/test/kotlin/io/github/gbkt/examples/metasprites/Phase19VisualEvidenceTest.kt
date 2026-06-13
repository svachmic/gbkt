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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions

/**
 * Phase 19 visual evidence capture — fresh HEAD GBC-mode screenshots for FIX-01 seeds
 * (SEED-004/005/006/013) and the Req-3 ROM-smoke shot, all captured against a clean-built ROM.
 *
 * Visual truths require runtime screenshot evidence (Visual Evidence Rule, CLAUDE.md §
 * "Verification Methodology"); variable assertions are insufficient. Every capture uses
 * [newGbcAgent] (`gbcMode=true`) because the metasprites example targets `GbcTarget.GBC_COMPATIBLE`
 * — a DMG-mode capture produces false grayscale rendering (MEMORY:
 * `learning_platformer_mcp_needs_gbc_mode`).
 *
 * Evidence outputs (under `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`):
 * - `SEED-004/screenshot.png` — elephant tiles uncorrupted (boot frame)
 * - `SEED-005/screenshot.png` — BG checkerboard, not diagonal (boot frame)
 * - `SEED-006/screenshot.png` — elephant sub-palette assigned correctly (rot=8, cyan)
 * - `SEED-013/screenshot.png` — correct GBC sub-palette colors (rot=8, cyan)
 * - `ROM-smoke/screenshot.png` — metasprites ROM renders correctly at HEAD (Req 3)
 *
 * Skipped automatically if the ROM is missing — run `./gradlew :gbkt-examples:metasprites:clean
 * :gbkt-examples:metasprites:buildRom` first.
 */
class Phase19VisualEvidenceTest {

    companion object {
        // Evidence directory — user.dir resolves to gbkt-examples/metasprites/ at test runtime;
        // ../../ walks up to the repo root.
        private val EVIDENCE_DIR =
            File("../../.planning/phases/" + "19-codegen-fixes-metasprite-cluster/evidence")
        private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    }

    /**
     * Creates a [StepAgent] in GBC mode (`gbcMode=true`). ALL Phase 19 captures use this helper —
     * the metasprites example targets `GbcTarget.GBC_COMPATIBLE` and a DMG capture would produce
     * false grayscale rendering, making it inadequate evidence for sub-palette seed coverage.
     *
     * The `.noi` symFile is auto-discovered from `build/gbkt/output/metasprites.noi` via
     * [AgentSessionConfig.discoverFiles] — no manual path required.
     */
    private fun newGbcAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
        )
        EVIDENCE_DIR.mkdirs()
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
     * Captures a screenshot via [StepAgent.captureScreenshot] and renames it to the per-seed target
     * path inside [EVIDENCE_DIR]. The per-seed subdirectory is created before rename so paths like
     * `SEED-004/screenshot.png` resolve correctly. JSON sidecar is also renamed in lock-step
     * (best-effort).
     */
    private fun captureAndRename(agent: StepAgent, label: String, targetName: String): File {
        val captured = agent.captureScreenshot(label)
        val target = File(EVIDENCE_DIR, targetName)
        target.parentFile.mkdirs()
        if (target.exists()) target.delete()
        check(captured.renameTo(target)) {
            "Failed to rename ${captured.absolutePath} -> ${target.absolutePath}"
        }
        // Sidecar JSON: rename in lock-step (best-effort; not required by plan).
        val sidecar = File(captured.parentFile, captured.nameWithoutExtension + ".json")
        if (sidecar.exists()) {
            val targetJson = File(target.parentFile, target.nameWithoutExtension + ".json")
            if (targetJson.exists()) targetJson.delete()
            sidecar.renameTo(targetJson)
        }
        return target
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
    // rendering path. Captured separately as ROM-smoke/screenshot.png for traceability.

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
            val seed004 = captureAndRename(agent, "seed004-boot", "SEED-004/screenshot.png")
            assertTrue(
                seed004.length() > 0,
                "Phase 19 SEED-004 screenshot must be non-empty: ${seed004.absolutePath}",
            )

            // SEED-005: same boot frame — BG checkerboard visible
            val seed005 = captureAndRename(agent, "seed005-boot", "SEED-005/screenshot.png")
            assertTrue(
                seed005.length() > 0,
                "Phase 19 SEED-005 screenshot must be non-empty: ${seed005.absolutePath}",
            )

            // ROM-smoke: same boot frame proves the ROM renders correctly at HEAD (Req 3)
            val romSmoke = captureAndRename(agent, "rom-smoke", "ROM-smoke/screenshot.png")
            assertTrue(
                romSmoke.length() > 0,
                "Phase 19 ROM-smoke screenshot must be non-empty: ${romSmoke.absolutePath}",
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
            val seed006 = captureAndRename(agent, "seed006-subpalette", "SEED-006/screenshot.png")
            assertTrue(
                seed006.length() > 0,
                "Phase 19 SEED-006 screenshot must be non-empty (should show cyan, not grayscale): " +
                    seed006.absolutePath,
            )

            // SEED-013: correct GBC sub-palette colors — same climax frame
            val seed013 = captureAndRename(agent, "seed013-gbccolors", "SEED-013/screenshot.png")
            assertTrue(
                seed013.length() > 0,
                "Phase 19 SEED-013 screenshot must be non-empty (should show cyan, not grayscale): " +
                    seed013.absolutePath,
            )
        }
    }
}
