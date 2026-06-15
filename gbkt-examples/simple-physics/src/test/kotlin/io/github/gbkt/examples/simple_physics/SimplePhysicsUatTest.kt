/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.simple_physics

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
 * Plan 09-06 UAT — runtime verification of the three D-01 behaviors against the built
 * `simple-physics.gb` ROM, with `emulator_screenshot` PNGs captured at the climax frame of each
 * behavior (Visual Evidence Rule per `CLAUDE.md`).
 *
 * MCP-equivalent harness: the MCP `gbkt-emulator` server wraps this same [StepAgent] API, so
 * JVM-tier results are deterministically the same as MCP-tier results (no emulator divergence). PNG
 * output paths match the plan contract exactly via post-capture rename.
 *
 * Skipped automatically if the ROM is missing — run `./gradlew
 * :gbkt-examples:simple-physics:buildRom` first.
 *
 * Captures land under gitignored scratch (`build/gbkt/screenshots/`). These PNGs are smoke-only
 * (length > 0 gate) and carry no golden baseline.
 */
class SimplePhysicsUatTest {

    companion object {
        // Gitignored scratch directory for smoke captures.
        private val SCRATCH_DIR = File(System.getProperty("user.dir"), "build/gbkt/screenshots")
        private val ROM_FILE = File("build/gbkt/output/simple-physics.gb")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
    }

    private fun newAgent(): StepAgent {
        Assumptions.assumeTrue(
            ROM_FILE.exists(),
            "simple-physics.gb not found — run buildRom first",
        )
        SCRATCH_DIR.mkdirs()
        val baseConfig = AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = SCRATCH_DIR)
        val metadata =
            if (METADATA_FILE.exists()) GameMetadata.fromJsonFile(METADATA_FILE) else null
        val agent = StepAgent(baseConfig, metadata)
        agent.start()
        return agent
    }

    /**
     * Resolves the WRAM address of an INT16 DSL variable by parsing the `.noi` symbol file.
     *
     * The default [StepAgent.readVariable] returns only the low byte for INT16 variables because
     * [io.github.gbkt.emulator.agent.VariableInspector.inferVariableType] uses a name-based
     * heuristic ("16"/"addr"/"ptr" → UINT16; otherwise UINT8) — `spdX` / `spdY` / `posX` / `posY`
     * fall through to UINT8. We bypass this by reading two bytes directly from the memory bus.
     */
    private fun resolveI16Address(name: String): Int {
        val noi = File(ROM_FILE.parentFile, ROM_FILE.nameWithoutExtension + ".noi")
        check(noi.exists()) { ".noi symbol file not found at ${noi.absolutePath}" }
        // GBDK .noi uses double-underscore prefix: `DEF __spdX 0xC0B7`.
        val pattern = Regex("""^DEF\s+__${Regex.escape(name)}\s+0x([0-9A-Fa-f]+)\s*$""")
        for (line in noi.readLines()) {
            val m = pattern.matchEntire(line.trim()) ?: continue
            return m.groupValues[1].toInt(16)
        }
        error("Symbol '$name' not found in ${noi.absolutePath}")
    }

    /** Reads an INT16 variable as a signed value by combining two memory bytes. */
    private fun readI16(agent: StepAgent, name: String): Int {
        val addr = resolveI16Address(name)
        val lo = agent.readMemory(addr) and 0xFF
        val hi = agent.readMemory(addr + 1) and 0xFF
        val raw = (hi shl 8) or lo
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    // ── Behavior 1 — D-pad held → accel + (eventually) clamp at +64 (D-01.1) ─────
    //
    // Frame-loop trace (see SimplePhysics.kt for source — the decel ladder ALWAYS runs
    // at the end of each frame, so a held-RIGHT frame's NET delta is +2 (accel) - 1
    // (decel) = +1 sub-pixel/frame, NOT +2 as the plan's 30-frame expectation implies):
    //
    //   frame N (held RIGHT): spdX += 2 → clamp(no, since N+2 ≤ 64 for N ≤ 62) →
    //   posX += spdX → ball.x sync → decel `spdX > 0` → spdX-- = N+1
    //   End-of-frame spdX = N+1 (net +1).
    //
    // The clamp at +64 first fires at frame 64 (when start-of-frame spdX = 63, accel
    // → 65, clamp resets to 64, decel → 63). Once clamped, steady-state end-of-frame
    // spdX = 63. This test exercises BOTH regimes:
    //   - 30-frame hold (plan's mcp_script choice): screenshot evidence + spdX = 30
    //     (proves accel ramp; clamp not yet reached because plan miscounted net delta)
    //   - 64-frame hold (clamp verification): final spdX = 63 (clamp-steady-state value)
    //
    // The 30-frame PNG is the binding visual evidence (sprite displaced rightward from
    // initial center); the 64-frame variable read is the clamp signature. Holding longer
    // (e.g. 80 frames) would push posX past the 8-bit sprite-coordinate range and the
    // ball would wrap to the left edge — which is visually misleading even though the
    // physics is correct.

    @Test
    fun `behavior 1 - D-pad held right accelerates and (extended hold) clamps spdX at +64`() {
        newAgent().use { agent ->
            agent.stepN(10) // boot
            val initial = readI16(agent, "spdX")
            assertEquals(0, initial, "spdX initial value should be 0 before any input")

            // Plan's mcp_script: 30 frames of held RIGHT, capture screenshot.
            agent.stepN(30, buttons = setOf(Button.RIGHT))
            val spdXAt30 = readI16(agent, "spdX")
            val posXAt30 = readI16(agent, "posX")
            val png = agent.captureScreenshot("behavior1_clamp_right")

            // Net +1 sub-pixel/frame → spdX = 30 after 30 frames. Plan-06 anticipated 64
            // because it counted +2/frame (forgetting the decel ladder runs every frame).
            // The clamp does NOT fire at this point — see the extension below for proof.
            assertEquals(
                30,
                spdXAt30,
                "spdX after 30 frames of held RIGHT = 30 (net +1/frame after decel ladder)",
            )
            // Per-frame: start spdX=N-1, accel +2 → N+1, posX += (N+1), decel -1 → end
            // spdX=N. So posX accumulates 2+3+...+31 = (sum 1..31) - 1 = 496 − 1 = 495
            // (sum of post-accel pre-decel speeds). End posX = 1024 + 495 = 1519.
            // ball_x = posX >> 4 = 94 → sprite visibly ~30 px right of initial center (64).
            assertEquals(1519, posXAt30, "posX after 30 frames should be 1519 sub-pixels")
            assertTrue(png.length() > 0, "PNG was empty: ${png.absolutePath}")

            // Extension — continue holding to verify the clamp eventually fires.
            // 34 more frames of held RIGHT (total 64) gets spdX through the clamp.
            agent.stepN(34, buttons = setOf(Button.RIGHT))
            val spdXAt64 = readI16(agent, "spdX")
            assertEquals(
                63,
                spdXAt64,
                "spdX should sit at 63 (clamp 64 − decel 1) once the clamp at frame 64 fires; " +
                    "without the clamp, 64 frames of net +1 would give spdX = 64 (oscillating " +
                    "+1 above 63), and longer holds would push it past 64. Steady-state 63 " +
                    "is the clamp signature.",
            )
        }
    }

    // ── Behavior 2 — A pressed (edge) → jump impulse (D-01.2) ────────────────────
    //
    // Frame loop on the A-press frame (post-09.3 D-01 oracle fix):
    //   buttons.a.pressed → spdY set -JUMP_ACCELERATION_IN_SUBPIXELS → posY += spdY →
    //   ball.y sync → decel `spdY isBelow 0` → spdY++ → spdY = -JUMP_ACCELERATION_IN_SUBPIXELS + 1.
    //
    // Post-09.3 D-01 expected: spdY == -JUMP_ACCELERATION_IN_SUBPIXELS after the single A
    // frame (impulse), then one decel step yields -JUMP_ACCELERATION_IN_SUBPIXELS + 1 at the
    // post-frame read site. The impulse magnitude is unambiguous (jumps from 0 to a clearly
    // negative value) — this is the binding signal. The "off-by-one" relative to the impulse
    // value reflects the per-frame decel ladder, not a physics defect; it matches the
    // reference's `if (SpdY < 0) SpdY++` decel in phys.c L93.

    @Test
    fun `behavior 2 - A pressed once sets spdY to large negative (jump impulse fired)`() {
        newAgent().use { agent ->
            agent.stepN(10)
            val initialSpdY = readI16(agent, "spdY")
            assertEquals(0, initialSpdY, "spdY should be 0 before A press")

            agent.step(setOf(Button.A)) // single-frame edge-triggered press

            val spdY = readI16(agent, "spdY")
            val png = agent.captureScreenshot("behavior2_jump")

            assertEquals(
                -JUMP_ACCELERATION_IN_SUBPIXELS + 1,
                spdY,
                "spdY should be ${-JUMP_ACCELERATION_IN_SUBPIXELS + 1} " +
                    "(= -$JUMP_ACCELERATION_IN_SUBPIXELS impulse + 1 decel step in same frame); " +
                    "a clearly negative value proves the A-press jump impulse fired",
            )
            assertTrue(png.length() > 0, "PNG was empty: ${png.absolutePath}")
        }
    }

    // ── Behavior 3 — D-pad released → spdX decelerates to 0 (D-01.3) ─────────────
    //
    // Build up speed (~20 frames RIGHT → spdX ≈ 20 net), then release. Decel ladder
    // fires once/frame while spdX > 0. spdX reaches 0 in ≤20 frames after release; we
    // give 60 frames of slack.

    @Test
    fun `behavior 3 - release after acceleration decelerates spdX to 0`() {
        newAgent().use { agent ->
            agent.stepN(10)

            agent.stepN(20, buttons = setOf(Button.RIGHT))
            val builtUp = readI16(agent, "spdX")
            assertTrue(builtUp > 0, "spdX should be > 0 after build-up; was $builtUp")

            agent.stepN(60) // release

            val rest = readI16(agent, "spdX")
            val png = agent.captureScreenshot("behavior3_decel")

            assertEquals(0, rest, "spdX should decel to 0 after 60 frames of no input")
            assertTrue(png.length() > 0, "PNG was empty: ${png.absolutePath}")
        }
    }
}
