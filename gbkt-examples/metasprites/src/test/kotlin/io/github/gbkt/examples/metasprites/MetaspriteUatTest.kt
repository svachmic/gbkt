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
 * Plan 10-17 UAT — runtime verification of D-01 behaviors 1+2 (DMG mode) against the built
 * `metasprites.gb` ROM, with `captureAndRename()` PNGs captured at the climax frame of each
 * behavior (Visual Evidence Rule per `CLAUDE.md`).
 *
 * MCP-equivalent harness: the MCP `gbkt-emulator` server wraps this same [StepAgent] API, so
 * JVM-tier results are deterministically the same as MCP-tier results.
 *
 * Behavior 3 (GBC sub-palette cycling) is owned by Plan 10-18 (UAT-GBC); the [newGbcAgent]
 * placeholder helper is scaffolded here so Plan 18 can add the third test method without
 * restructuring this file.
 *
 * Skipped automatically if the ROM is missing — run `./gradlew :gbkt-examples:metasprites:buildRom`
 * first.
 *
 * Outputs:
 * - `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png`
 * - `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png`
 */
class MetaspriteUatTest {

    companion object {
        // Evidence directory (worktree-safe: user.dir resolves to gbkt-examples/metasprites/
        // when Gradle runs tests, so ../../ walks up to the worktree root).
        private val EVIDENCE_DIR =
            File(
                "../../.planning/phases/" +
                    "10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots"
            )
        private val ROM_FILE = File("build/gbkt/output/metasprites.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    }

    /**
     * Creates a [StepAgent] in DMG mode (default). Both behavior 1 (B-press animation advance) and
     * behavior 2 (A-press flip cycle) are observable in DMG mode — no GBC palette required.
     */
    private fun newAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "metasprites.gb not found — run :gbkt-examples:metasprites:buildRom first",
        )
        EVIDENCE_DIR.mkdirs()
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Scaffold for Plan 18 (UAT-GBC, behavior 3 — sub-palette cycling). Plan 18 will implement this
     * helper with `AgentSessionConfig(gbcMode = true)`.
     */
    @Suppress("UnusedPrivateMember")
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
     * Captures a screenshot via [StepAgent.captureScreenshot] and renames the produced file to the
     * plan's exact target path inside [EVIDENCE_DIR]. JSON sidecar is also renamed/removed to keep
     * the evidence dir tidy.
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

    // ── Behavior 1 — B pressed (edge) → animation index advances (D-01.1) ────
    //
    // mcp_script from 10-UAT.md:
    //   emulator_start → wait_for_scene("play") → read _idx = 0
    //   → step 1 frame B pressed → read _idx = 1
    //   → step 1 more frame B pressed → read _idx = 2
    //   → screenshot → assert _idx == 2
    //
    // Climax frame: post-second-B-press (two visible frame changes from boot; _idx == 2).
    //
    // Edge-detection note: the generated C uses `button_pressed()` which checks the rising
    // edge via `joypad & mask & ~(joypad_prev & mask)`. Each button press therefore requires
    // a release frame (no button held) between successive edge events. This is why a single
    // held press does NOT advance idx twice — the second frame sees B held (not pressed).
    //
    // Variable evidence (_idx) is NECESSARY but never SUFFICIENT per the Visual Evidence
    // Rule (CLAUDE.md §"Verification Methodology"). The PNG at _idx=2 is the binding
    // artifact proving the sprite frame changed visually.

    @Test
    fun `behavior1 b press advances animation index`() {
        newAgent().use { agent ->
            agent.stepN(10) // boot

            agent.waitForScene("play", 120)

            val idxInitial = agent.readVariable("idx")
            assertEquals(0, idxInitial, "_idx should be 0 at boot before any B press")

            // First B press (edge-triggered): idx advances 0 → 1
            agent.step(setOf(Button.B))
            val idxAfterFirst = agent.readVariable("idx")
            assertEquals(1, idxAfterFirst, "_idx should be 1 after first B press")

            // Release frame required for edge detection (button_pressed fires on rising edge only)
            agent.step(emptySet())

            // Second B press: idx advances 1 → 2
            agent.step(setOf(Button.B))
            val idxAfterSecond = agent.readVariable("idx")

            // Climax-frame screenshot (binding visual evidence per Visual Evidence Rule)
            val png = captureAndRename(agent, "behavior1", "behavior1-animation-advance.png")

            assertEquals(2, idxAfterSecond, "_idx should be 2 after second B press")
            assertTrue(
                png.length() > 0,
                "Behavior 1 screenshot must be non-empty: ${png.absolutePath}",
            )
        }
    }

    // ── Behavior 2 — A pressed (edge) → cycles 4 flip states via OAM attr (D-01.2) ─
    //
    // mcp_script from 10-UAT.md:
    //   boot → read _rot = 0 (Normal)
    //   → 1st A press → _rot = 1 (Flip-Y)
    //   → 2nd A press → _rot = 2 (Flip-XY)  ← screenshot here (most visually distinctive)
    //   → assert _rot == 2
    //   → 3rd A press → _rot = 3 (Flip-X)
    //   → 4th A press → _rot = 4 (Normal again; subpal slot advances to 1 on GBC)
    //
    // Climax frame: post-second-A-press (_rot == 2, Flip-XY; most visually distinctive).
    // DMG screenshots are sufficient for this behavior (no palette cycle involved).
    //
    // Edge-detection note: same as behavior 1 — release frames required between edge-triggered
    // A presses so button_pressed() fires on each rising edge separately.

    @Test
    fun `behavior2 a press cycles flip states`() {
        newAgent().use { agent ->
            agent.stepN(10) // boot

            agent.waitForScene("play", 120)

            val rotInitial = agent.readVariable("rot")
            assertEquals(0, rotInitial, "_rot should be 0 at boot (Normal orientation)")

            // 1st A press: rot 0 → 1 (Flip-Y)
            agent.step(setOf(Button.A))
            val rotAfterFirst = agent.readVariable("rot")
            assertEquals(1, rotAfterFirst, "_rot should be 1 after 1st A press (Flip-Y)")

            // Release frame so next press registers as a fresh rising edge
            agent.step(emptySet())

            // 2nd A press: rot 1 → 2 (Flip-XY) — climax frame
            agent.step(setOf(Button.A))
            val rotAfterSecond = agent.readVariable("rot")

            // Climax-frame screenshot (binding visual evidence per Visual Evidence Rule)
            val png = captureAndRename(agent, "behavior2", "behavior2-flip-cycle.png")

            assertEquals(2, rotAfterSecond, "_rot should be 2 after 2nd A press (Flip-XY)")
            assertTrue(
                png.length() > 0,
                "Behavior 2 screenshot must be non-empty: ${png.absolutePath}",
            )

            // Continue cycling to verify the full 4-step sequence
            // 3rd A press: rot 2 → 3 (Flip-X)
            agent.step(emptySet()) // release
            agent.step(setOf(Button.A))
            val rotAfterThird = agent.readVariable("rot")
            assertEquals(3, rotAfterThird, "_rot should be 3 after 3rd A press (Flip-X)")

            // 4th A press: rot 3 → 4 (Normal again; rot & 0x3 == 0; subpal advances on GBC)
            agent.step(emptySet()) // release
            agent.step(setOf(Button.A))
            val rotAfterFourth = agent.readVariable("rot")
            assertEquals(
                4,
                rotAfterFourth,
                "_rot should be 4 after 4th A press (Normal+subpal advance)",
            )
        }
    }

    // ── Behavior 3 — A pressed (after flip cycle wraps) → cycles sub-palettes in GBC (D-01.3) ─
    //
    // Based on mcp_script from 10-UAT.md (adapted with release frames per Plan 17 edge-detection
    // fix — button_pressed() detects rising edges, so consecutive step() calls with same button
    // held register only one press; step(emptySet()) release frames are required).
    //
    // Flow:
    //   newGbcAgent() → wait_for_scene("play") → read _rot = 0 (subpal=0 gray)
    //   → 4 A presses (with release frames) → _rot = 4 (subpal = rot>>2 = 1 pink)
    //   → 4 more A presses (with release frames) → _rot = 8 (subpal = rot>>2 = 2 cyan)
    //   → screenshot (GBC mode — cyan palette visible) → assert _rot == 8
    //
    // Climax frame: _rot == 8 (subpal==2, cyan palette visible on GBC display).
    //
    // GBC MODE REQUIRED (CLAUDE.md Visual Evidence Rule + RESEARCH Pitfall 7):
    // Sub-palette bits in the OAM attribute byte are hardware-ignored on DMG. On GBC,
    // `rot >> 2` selects palette slot 0..3 (gray / pink / cyan / green) via the
    // `moveMetasprite()` generated C. A DMG screenshot would appear identical regardless
    // of sub-palette value — making it inadequate evidence for this behavior.
    // AgentSessionConfig(...).copy(gbcMode = true) MUST be set via [newGbcAgent].
    //
    // Sub-palette mechanism (D-08):
    //   rot 0-3  → subpal 0 (gray)
    //   rot 4-7  → subpal 1 (pink)
    //   rot 8-11 → subpal 2 (cyan)
    //   rot 12-15 → subpal 3 (green)
    //
    // Variable evidence (_rot) is NECESSARY but never SUFFICIENT per the Visual Evidence Rule.
    // The GBC-mode PNG at rot=8 is the binding artifact proving the color shift is visible.

    @Test
    fun `behavior3 a press cycles sub palettes in gbc mode`() {
        newGbcAgent().use { agent ->
            // GBC mode needs more boot frames than DMG — CGB PPU initialization takes
            // additional frames before the LCD outputs visible content.
            agent.stepN(30) // boot (30 frames instead of 10 for GBC LCD init)

            agent.waitForScene("play", 120)

            val rotInitial = agent.readVariable("rot")
            assertEquals(0, rotInitial, "_rot should be 0 at boot (subpal=0 gray)")

            // First cycle: 4 A presses → rot=4 (subpal=1 pink)
            // Press 1: rot 0 → 1
            agent.step(setOf(Button.A))
            val rot1 = agent.readVariable("rot")
            assertEquals(1, rot1, "_rot should be 1 after 1st A press")

            agent.step(emptySet()) // release frame

            // Press 2: rot 1 → 2
            agent.step(setOf(Button.A))
            val rot2 = agent.readVariable("rot")
            assertEquals(2, rot2, "_rot should be 2 after 2nd A press")

            agent.step(emptySet()) // release frame

            // Press 3: rot 2 → 3
            agent.step(setOf(Button.A))
            val rot3 = agent.readVariable("rot")
            assertEquals(3, rot3, "_rot should be 3 after 3rd A press")

            agent.step(emptySet()) // release frame

            // Press 4: rot 3 → 4 (sub-palette advances to 1 pink)
            agent.step(setOf(Button.A))
            val rot4 = agent.readVariable("rot")
            assertEquals(4, rot4, "_rot should be 4 after 4th A press (subpal=rot>>2=1 pink)")

            // Second cycle: 4 more A presses → rot=8 (subpal=2 cyan — climax frame)
            agent.step(emptySet()) // release frame

            // Press 5: rot 4 → 5
            agent.step(setOf(Button.A))
            val rot5 = agent.readVariable("rot")
            assertEquals(5, rot5, "_rot should be 5 after 5th A press")

            agent.step(emptySet()) // release frame

            // Press 6: rot 5 → 6
            agent.step(setOf(Button.A))
            val rot6 = agent.readVariable("rot")
            assertEquals(6, rot6, "_rot should be 6 after 6th A press")

            agent.step(emptySet()) // release frame

            // Press 7: rot 6 → 7
            agent.step(setOf(Button.A))
            val rot7 = agent.readVariable("rot")
            assertEquals(7, rot7, "_rot should be 7 after 7th A press")

            agent.step(emptySet()) // release frame

            // Press 8: rot 7 → 8 (sub-palette advances to 2 cyan — climax frame)
            agent.step(setOf(Button.A))
            val rot8 = agent.readVariable("rot")

            // Wait 2 more frames for GBC PPU to flush the new palette to screen
            agent.step(emptySet())
            agent.step(emptySet())

            // Climax-frame screenshot in GBC mode (binding visual evidence per Visual Evidence
            // Rule)
            // This screenshot MUST show color (cyan palette) — NOT grayscale.
            // If it appears monochrome, gbcMode=true did not apply correctly.
            val png = captureAndRename(agent, "behavior3", "behavior3-subpalette-cycle-gbc.png")

            assertEquals(8, rot8, "_rot should be 8 after 8th A press (subpal=rot>>2=2 cyan)")
            assertTrue(
                png.length() > 0,
                "Behavior 3 GBC screenshot must be non-empty: ${png.absolutePath}",
            )
        }
    }
}
