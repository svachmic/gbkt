/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 12.3 Plan 03 (R2 acceptance) — per-function awk brace-walk emission
// test that LOCKS Plan 12.3-02's auto-emitted dpad → playerVx input wiring +
// friction-on-release branch inside `void platformer_physics_update`.
//
// Plan 12.3-02 added a section-0 emission block (PlatformerVisitor.kt §line 597+)
// gated on `gameUsesPlatformerInput(gameIR) == true`. When the gate fires, the
// emitted physics function body begins (BEFORE the existing sub-pixel
// integration, AABB probes, and jumpHold body) with the reference player.c
// shape from CONTEXT.md §D-04:
//
//   if (button_held(J_RIGHT)) {
//       _player_vx = 128;             // walkSpeed — CIntLiteral (signed RHS)
//   } else if (button_held(J_LEFT)) {
//       _player_vx = -128;            // CIntLiteral (signed RHS)
//   } else {
//       UINT8 f = _grounded ? 8u : 0u;                      // friction
//       if (_player_vx > 0) {                               // CIntLiteral(0)
//           _player_vx = (_player_vx > f) ? (_player_vx - f) : 0;
//       } else if (_player_vx < 0) {                        // CIntLiteral(0)
//           _player_vx = (_player_vx < -((INT16)f)) ? (_player_vx + f) : 0;
//       }
//   }
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level
// `mainC.contains("button_held(J_RIGHT)")` here because `button_held(J_RIGHT)`
// also lands inside the gameplay scene's `runIf(dpad.right.held) { ... }`
// frame-body emission in any fixture that registers dpad input ops. The
// brace-walk extracts the `platformer_physics_update` body so the substring
// checks fire ONLY against tokens inside the physics function (this test's
// fixture is minimal — no dpad ops in any scene — but the per-function
// discipline guarantees the same shape lock holds for richer fixtures
// downstream).
//
// Pitfall 1 regression guard (RESEARCH §Pitfalls / L-5.1): the test asserts
// the body does NOT contain a `> 0u` literal adjacent to `_player_vx`.
// CLiteral(0) instead of CIntLiteral(0) would emit `_player_vx > 0u` and
// SDCC's usual arithmetic conversion (C11 §6.3.1.8) would promote the
// signed LHS to unsigned — making the comparison never fire for negative
// `_player_vx` (the camera-never-advances bug class codified after Phase 07.4).
// This forbidden-token assertion structurally guards against that regression.
//
// Evidence-before-assert (Pattern 5 / L-10.2): each test writes the extracted
// physics body to `evidence/tier1-shape/platformer_physics_update_input_*.c`
// BEFORE any assertion fires, so a RED run still produces a reviewable artifact
// on disk.
// =============================================================================

class PlatformerInputEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory; for the
         * `:gbkt-genre-platformer:test` task this is `<repo>/gbkt-genre-platformer`. We ascend one
         * level (`..`) to reach the worktree root, then descend into the Phase 12.3 evidence
         * directory (Pattern 5 / RESEARCH §"EVIDENCE_DIR convention — worktree-safe"). Hard-coding
         * an absolute path would silently route evidence files outside the active worktree and miss
         * the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers — extractFunctionBody copied verbatim from JumpHoldEmissionTest.kt
    // (CONTEXT.md §L-12.3 — duplicate is acceptable / preferred per sibling-tests
    // convention).
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching closing
     * brace at depth zero.
     *
     * Mirror of the helper in `TilemapCollisionEmissionTest.kt` (Plan 12-09),
     * `JumpHoldEmissionTest.kt` (Plan 12-13), and `HorizontalScrollEmissionTest.kt` (Plan 12-12).
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same file (per CLAUDE.md §"Scope-level grep gates").
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different function
     * cannot false-match. This is the literal counterpart of awk's `/^prefix/` anchor.
     */
    private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
        if (startIdx == -1) return ""
        val body = StringBuilder()
        var depth = 0
        var started = false
        for (i in startIdx until lines.size) {
            val line = lines[i]
            body.appendLine(line)
            for (ch in line) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (ch == '}') depth--
            }
            if (started && depth == 0) break
        }
        return body.toString()
    }

    /**
     * Build a minimal GameIR carrying a `platformer_physics` GenericSystem (with the
     * tilemap-collision gate `solidThreshold` set) PLUS a `platformer_input` GenericSystem (with
     * the Plan 12.3-02 numeric tuning keys: walkSpeed=128, friction=8, airFriction=0).
     *
     * Both gates must fire for section 0 (input emission) to land inside
     * `platformer_physics_update`:
     * - `gameUsesTilemapCollision(gameIR)` returns true (via `solidThreshold` set) → the
     *   `buildTilemapPhysicsUpdateFunction` branch is taken (Plan 12-11).
     * - `gameUsesPlatformerInput(gameIR)` returns true (via the `platformer_input` system) → the
     *   section-0 input emission fires INSIDE that branch (Plan 12.3-02).
     *
     * The default `vxSym` resolution in `buildTilemapPhysicsUpdateFunction` (PlatformerVisitor.kt
     * line 555) is `"_" + ((tcSystem?.config?.get("vxVar") as? String) ?: "player_vx")`. This
     * fixture does NOT register a `tilemap_collision` system (only `platformer_physics`), so the
     * fallback `"player_vx"` applies → emitted symbol is `_player_vx`. Identical to the symbol the
     * existing JumpHoldEmissionTest evidence exhibits.
     */
    private fun buildPositiveGameIR(): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = 17,
                jumpHoldMaxFrames = 0,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        val inputSystem =
            GenericSystem(
                id = "input",
                config =
                    mapOf(
                        "type" to "platformer_input",
                        "walkSpeed" to 128,
                        "friction" to 8,
                        "airFriction" to 0,
                        "walkFrameCount" to 3,
                        "cyclePeriod" to 6,
                    ),
            )
        return GameIR(
            name = "TestPlatformerInputPositive",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, inputSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Negative back-compat fixture — same `platformer_physics` system, NO `platformer_input`
     * system. `gameUsesPlatformerInput(gameIR)` returns false; the section-0 emission block is
     * skipped entirely. This locks the back-compat invariant: existing platformer fixtures that
     * never registered `platformer_input` (the JumpHold / TilemapCollision / HorizontalScroll test
     * fixtures + every pre-Plan 12.3-02 game) keep their physics function byte-identical relative
     * to the Plan 12.1-06 baseline.
     */
    private fun buildNegativeGameIR(): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = 17,
                jumpHoldMaxFrames = 0,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        return GameIR(
            name = "TestPlatformerInputNegative",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — section 0 input emission fires when platformer_input system is
    // present AND tilemap-collision gate (solidThreshold) is on.
    //
    // Locks the Plan 12.3-02 emission shape inside `platformer_physics_update`:
    //   - Two `button_held(...)` calls (J_RIGHT, J_LEFT) inside the function scope.
    //   - The walkSpeed integer literal `128` assigned to `_player_vx` (positive branch).
    //   - The negated walkSpeed `-128` assigned to `_player_vx` (left branch).
    //   - Signed-RHS comparison markers `> 0` and `< 0` on `_player_vx` (friction branch
    //     uses CIntLiteral(0) per Plan 12.3-02 §Pitfall 1 audit).
    //
    // Pitfall 1 forbidden-token guard: `_player_vx > 0u` MUST NOT appear in the body. If a
    // future visitor regression switches `CIntLiteral(0)` → `CLiteral(0)` on the friction
    // branch, this assertion fails RED and surfaces the SDCC signed/unsigned promotion bug
    // BEFORE it reaches the ROM (which would manifest as "right-walk friction never fires
    // for negative velocity" — the camera-never-advances class).
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update emits input wiring inside function body when platformer_input system is present`() {
        val gameIR = buildPositiveGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert: extract and persist the physics body BEFORE any assertion
        // fires so a RED run still produces a reviewable artifact on disk (Pattern 5 / L-10.2).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_input_positive.c").writeText(physicsBody)

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // ---- Required tokens (Plan 12.3-02 emission shape, gated by gameUsesPlatformerInput) ----

        // button_held(J_RIGHT) — section 0's outer `CIf(condition = CCall("button_held",
        // listOf(CVar("J_RIGHT"))), ...)`. A regression that drops the right-input gate would
        // fail here. Scope-confined via the brace-walk — even if `runIf(dpad.right.held)`
        // lowered to `button_held(J_RIGHT)` in another scene's frame body, this assertion would
        // be unaffected.
        assertTrue(
            physicsBody.contains("button_held(J_RIGHT)"),
            "platformer_physics_update body must reference button_held(J_RIGHT) — Plan 12.3-02 " +
                "section-0 input gate (CIf condition). physics body:\n${physicsBody.take(8000)}",
        )

        // button_held(J_LEFT) — section 0's nested `else if` clause `CIf(condition = CCall(
        // "button_held", listOf(CVar("J_LEFT"))), ...)`. A regression that drops the left-input
        // gate would fail here even if right-input survived.
        assertTrue(
            physicsBody.contains("button_held(J_LEFT)"),
            "platformer_physics_update body must reference button_held(J_LEFT) — Plan 12.3-02 " +
                "section-0 left-input gate (nested CIf condition). physics body:\n" +
                physicsBody.take(8000),
        )

        // _player_vx = 128 — walkSpeed (CIntLiteral) assigned in the right-input branch's
        // thenBody. Accept both `= 128;` and `= 128)` forms (the CEmitter may end the
        // statement with a semicolon OR the literal may appear inside an enclosing
        // expression). A regression that changed walkSpeed to a non-128 default or
        // forgot the assignment entirely would fail here. The token is uniquely
        // discriminating because `128` does NOT appear elsewhere in the baseline
        // physics body (Plan 12-11 / 12-13 emission shapes have no 128 literal).
        assertTrue(
            physicsBody.contains("_player_vx = 128"),
            "platformer_physics_update body must contain `_player_vx = 128` — section-0 walkSpeed " +
                "assignment (CIntLiteral(128) → CEmitter). physics body:\n" +
                physicsBody.take(8000),
        )

        // _player_vx = -128 — negated walkSpeed (CIntLiteral(-walkSpeed)) assigned in the
        // left-input branch's thenBody. A regression that flipped the sign or dropped the
        // assignment would fail here. The `-128` token is uniquely discriminating
        // for the same reason as `128` above.
        assertTrue(
            physicsBody.contains("_player_vx = -128"),
            "platformer_physics_update body must contain `_player_vx = -128` — section-0 " +
                "negative walkSpeed assignment for J_LEFT branch (CIntLiteral(-128)). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // Signed-RHS literal discipline (Phase 07.9 / L-5.1) — the friction branch emits
        // `_player_vx > 0` and `_player_vx < 0` via CIntLiteral(0). Accept any trailing
        // delimiter (paren, semicolon, space) so a future CEmitter reformat doesn't break the
        // shape lock. The CRITICAL property is the `0` literal appears WITHOUT a `u` suffix —
        // i.e. as a signed-context literal. The forbidden-token guard below enforces the
        // negative half of this contract.
        val gtZeroSignedFound =
            physicsBody.contains("_player_vx > 0)") ||
                physicsBody.contains("_player_vx > 0 ") ||
                physicsBody.contains("_player_vx > 0\n")
        assertTrue(
            gtZeroSignedFound,
            "platformer_physics_update body must contain `_player_vx > 0` (signed CIntLiteral(0), " +
                "WITHOUT `u` suffix) — Plan 12.3-02 friction branch (Phase 07.9 §Literal Emission " +
                "Convention). physics body:\n${physicsBody.take(8000)}",
        )

        val ltZeroSignedFound =
            physicsBody.contains("_player_vx < 0)") ||
                physicsBody.contains("_player_vx < 0 ") ||
                physicsBody.contains("_player_vx < 0\n")
        assertTrue(
            ltZeroSignedFound,
            "platformer_physics_update body must contain `_player_vx < 0` (signed CIntLiteral(0), " +
                "WITHOUT `u` suffix) — Plan 12.3-02 negative-velocity friction branch. " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // ---- Forbidden token (Pitfall 1 / L-5.1 regression guard) ----

        // `_player_vx > 0u` MUST NOT appear anywhere in the body. The unsigned literal `0u`
        // adjacent to a signed-context LHS would trigger SDCC's usual arithmetic conversion
        // (C11 §6.3.1.8), promoting `_player_vx` to unsigned and breaking the comparison for
        // negative velocities (Phase 07.9 SIGNED bug class). This is the structural guard for
        // the regression Plan 12.3-02 §Pitfall 1 audited and that the rest of this test
        // affirmatively locks.
        assertFalse(
            physicsBody.contains("_player_vx > 0u"),
            "platformer_physics_update body MUST NOT contain `_player_vx > 0u` — that is the " +
                "CLiteral-on-signed-RHS regression Phase 07.9 §Literal Emission Convention " +
                "forbids. Plan 12.3-02 emits CIntLiteral(0) on this comparison. physics body:\n" +
                physicsBody.take(8000),
        )

        // Symmetric forbidden-token for the negative-velocity friction branch — same
        // reasoning. If the comparison emitted as `_player_vx < 0u`, it would always fire
        // (any unsigned value < 0u is false → the negative-velocity friction never runs).
        assertFalse(
            physicsBody.contains("_player_vx < 0u"),
            "platformer_physics_update body MUST NOT contain `_player_vx < 0u` — same Pitfall 1 " +
                "signed/unsigned regression class. Plan 12.3-02 emits CIntLiteral(0) here. " +
                "physics body:\n${physicsBody.take(8000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — back-compat. When `platformer_input` is NOT registered, the
    // section-0 emission block is skipped entirely. Existing JumpHold /
    // TilemapCollision / HorizontalScroll fixtures (which do NOT register
    // `platformer_input`) keep their physics function body byte-identical
    // relative to the Plan 12.1-06 baseline.
    //
    // Discriminator tokens — these MUST NOT appear in the function body when
    // the input system is absent:
    //   - `button_held(J_RIGHT)` — section-0's outer CIf condition.
    //   - `button_held(J_LEFT)` — section-0's nested CIf condition.
    //
    // The rest of the body (existing physics integration, AABB probes,
    // jump-initiation site) uses `button_pressed(J_A)` / `button_pressed(J_UP)`
    // — NEVER `button_held` — so these tokens are clean section-0-only
    // discriminators.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update omits input emission when platformer_input system is absent`() {
        val gameIR = buildNegativeGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert: extract and persist the physics body BEFORE any assertion
        // fires (Pattern 5 / L-10.2).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_input_negative.c").writeText(physicsBody)

        // The function MUST still exist — the tilemap-physics branch runs whenever
        // `solidThreshold` is set (Plan 12-11), independent of the input gate. Asserting
        // presence rules out a regression that accidentally drops the physics function
        // entirely when the input gate is off.
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c even " +
                "when platformer_input is absent (tilemap-physics branch still runs). " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Section-0 input emission MUST be omitted entirely. The discriminator is
        // `button_held(J_RIGHT)` / `button_held(J_LEFT)` — these tokens are produced ONLY by
        // the Plan 12.3-02 section-0 emission. A regression that fires section 0 when the
        // gate is off would leak these tokens here and fail RED.
        assertFalse(
            physicsBody.contains("button_held(J_RIGHT)"),
            "platformer_physics_update body MUST NOT reference button_held(J_RIGHT) when " +
                "platformer_input system is absent — Plan 12.3-02 section-0 emission must be " +
                "gated off (back-compat invariant). physics body:\n${physicsBody.take(8000)}",
        )
        assertFalse(
            physicsBody.contains("button_held(J_LEFT)"),
            "platformer_physics_update body MUST NOT reference button_held(J_LEFT) when " +
                "platformer_input system is absent — Plan 12.3-02 section-0 emission must be " +
                "gated off (back-compat invariant). physics body:\n${physicsBody.take(8000)}",
        )
    }
}
