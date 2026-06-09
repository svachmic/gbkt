/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.simple_physics

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simulation logic tests for the three D-01 behaviors.
 *
 * Tests run on JVM via [SimulationContext] — no GBDK toolchain or ROM required.
 *
 * **Tier scope (per CLAUDE.md Visual Evidence Rule):** JVM-tier evidence is necessary but never
 * sufficient for visual truths. Plan 06 captures runtime MCP screenshots that prove the sprite
 * actually moves on-screen.
 *
 * **Input-helper caveat:** `dpad_held` / `button_pressed` are stub-resolved to `0L` by
 * [io.github.gbkt.core.test.ScriptOpInterpreter.evaluateCallExpr] in JVM simulation (input helpers
 * are not part of the interpreter's supported CallExpr surface). The tests below exercise the
 * post-input physics paths by initializing state directly via `setVar` — this validates the
 * integration and decel ladders, which is the JVM-tier floor the GameTest contract covers. Tests of
 * input edges (the held-accel ramp and the A-press impulse) live in `SimplePhysicsEmissionTest`
 * (C-shape oracle) and Plan 06 MCP UAT (runtime).
 *
 * Scenarios:
 * 1. D-01.1 (post-clamp decel arc) — preload spdX past clamp into clamp range, advance one frame
 *    with no held input, observe decel ladder reduce spdX by 1.
 * 2. D-01.2 (jump impulse target value) — preload spdY to -JUMP_ACCELERATION_IN_SUBPIXELS (the
 *    impulse target, post-09.3 D-01 oracle fix), advance one frame, confirm the decel-from-negative
 *    branch nudges spdY toward zero.
 * 3. D-01.3 (decel toward zero) — preload spdX to 10, advance one frame, confirm spdX decreased
 *    (positive-side decel ladder).
 */
class SimplePhysicsGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = simplePhysics.build()
    }

    // =========================================================================
    // Scenario 1: D-01.1 — accel/clamp arc (decel from clamp value)
    // =========================================================================

    @Test
    fun `D-01_1 decel from clamp ceiling - spdX at 64 decreases by 1 with no input`() {
        val sim = SimulationContext(ir)
        sim.enterScene("play")

        // Preload spdX at MAX_X_SPEED clamp value (where the held-accel ramp would
        // settle after enough frames of RIGHT held + clamp firing).
        sim.setVar("spdX", 64)

        // Advance one frame — held-input branch evaluates to 0 (stub), so only the
        // decel ladder fires: spdX isAbove 0 → spdX--
        sim.advanceFrames(1)

        sim.assertVar("spdX", 63)
    }

    // =========================================================================
    // Scenario 2: D-01.2 — jump impulse target value behaves on subsequent frames
    // =========================================================================

    @Test
    fun `D-01_2 jump impulse target - spdY at -JUMP_ACCELERATION_IN_SUBPIXELS decels toward zero by 1 per frame`() {
        val sim = SimulationContext(ir)
        sim.enterScene("play")

        // The A-press impulse sets spdY to -JUMP_ACCELERATION_IN_SUBPIXELS (= -32 per phys.c:83,
        // post-09.3 D-01 oracle fix). Preload that value directly (the press path itself is
        // covered by the C-shape oracle in SimplePhysicsEmissionTest D-11.2 and by Plan 06
        // runtime UAT).
        sim.setVar("spdY", -JUMP_ACCELERATION_IN_SUBPIXELS)

        // Advance one frame — decel ladder: spdY isBelow 0 → spdY++
        sim.advanceFrames(1)

        sim.assertVar("spdY", -JUMP_ACCELERATION_IN_SUBPIXELS + 1)
    }

    // =========================================================================
    // Scenario 3: D-01.3 — decel toward zero (positive side)
    // =========================================================================

    @Test
    fun `D-01_3 decel toward zero - spdX at 10 decreases by 1 with no input`() {
        val sim = SimulationContext(ir)
        sim.enterScene("play")

        sim.setVar("spdX", 10)

        sim.advanceFrames(1)

        val spdXAfter = sim.getVar("spdX")
        assertTrue(
            spdXAfter < 10,
            "spdX should decrease toward zero (expected < 10, got $spdXAfter)",
        )
        assertEquals(9, spdXAfter)
    }

    // =========================================================================
    // Additional invariant: enter resets state to 1024/1024/0/0
    // =========================================================================

    @Test
    fun `enter resets posX posY to 1024 sub-pixels and speeds to 0`() {
        val sim = SimulationContext(ir)
        sim.enterScene("play")

        // Verify enter ops set the initial sub-pixel position (64 px × 16 = 1024)
        // and zero the speeds.
        sim.assertVar("posX", 1024)
        sim.assertVar("posY", 1024)
        sim.assertVar("spdX", 0)
        sim.assertVar("spdY", 0)
    }
}
