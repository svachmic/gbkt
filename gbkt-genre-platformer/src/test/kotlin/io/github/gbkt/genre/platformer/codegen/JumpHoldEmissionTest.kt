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
// D-14 INVARIANT — jumpHold gravity-suppression branch shape lock
//
// Plan 12-13 emits a section-5b block inside `buildTilemapPhysicsUpdateFunction`
// (PlatformerVisitor.kt) — gated on `cfg.jumpHoldMaxFrames > 0` — that mirrors
// the reference `platformer_template/src/player.c` lines 297-317 variable-height
// jump body:
//
//   if (_grounded == 0) {
//       if (_jump_increase_timer > 0u) {
//           --_jump_increase_timer;
//       }
//       if (!(button_held(J_A) || button_held(J_UP)) || _jump_increase_timer == 0u) {
//           _player_vy += <gravity_scaled>;
//           _jump_increase_timer = 0u;
//       }
//   }
//
// The behavioural contract: while airborne AND A/Up is held AND the timer is
// still positive, gravity is SUPPRESSED (the rising-window of a variable-height
// jump). On button-release OR timer-expiry, gravity resumes and the timer is
// zeroed so a stray re-press cannot re-enter the suppression window.
//
// VALIDATION.md §Per-Anchor Verification Map (D-14 is not one of the 5 anchor
// rows, but its emission shape participates in Anchor 2's variable-evidence
// chain — `_player_vy` transitions through 0 → -550 → 0 over the jump cycle.
// If the gravity-suppression branch drifts, the jump-cycle variable evidence
// passes while the visual feel degrades silently). The awk pattern below is
// the local counterpart of VALIDATION.md rows 2-3:
//
//   awk '/^void platformer_physics_update/{p=1;d=0} p{d+=gsub(/{/,"");
//        d-=gsub(/}/,""); if(d<0)exit} p' main.c
//
// Note: `platformer_physics_update` is emitted as `CFunction(isBanked = false)`
// (PlatformerVisitor.kt line 807-810 — no isBanked=true), so it lands in
// `main.c` (HOME bank) — same convention as `platformer_camera_update`
// (HorizontalScrollEmissionTest extracts from main.c for the same reason).
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level `mainC.contains(...)`
// here because `_player_vy +=`, `button_held(...)`, and `_jump_increase_timer`
// also appear in other functions (the jump-initiation site at section 5 sets
// `_jump_increase_timer = cfg.jumpHoldMaxFrames`, and downstream waves may
// emit `button_held` calls in scene handlers). The brace-walk confines the
// substring checks to the `platformer_physics_update` body only.
//
// Decrement form (Plan 12-13 §Deviations): the timer decrement is emitted
// PREFIX (`--_jump_increase_timer`), NOT postfix (`_jump_increase_timer--`),
// because `CEmitter.emitExpr` (line 426) emits `CUnaryExpr` as
// `${op}${operand}` — prefix-only — and every existing visitor follows the
// same convention. Functionally identical at this position (the decrement's
// value is never consumed). This grep MUST accept either form so a future
// codegen evolution to a CPostfixUnaryExpr AST does not trip the test.
// =============================================================================

class JumpHoldEmissionTest {

    companion object {
        /**
         * Evidence is written to the module's gitignored build/ scratch directory (R1 + R3).
         *
         * `user.dir` at `:gbkt-genre-platformer:test` runtime resolves to the
         * `gbkt-genre-platformer` module root, so `build/gbkt/test-evidence` is the module's own
         * gitignored build directory — no `../` ascent needed (22-PATTERNS Pitfall 5). In-test C
         * assertions remain the gate; the txt dumps are for post-failure review only.
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence").normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching closing
     * brace at depth zero.
     *
     * Mirror of the helper in `TilemapCollisionEmissionTest.kt` (Plan 12-09) and
     * `HorizontalScrollEmissionTest.kt` (Plan 12-12). The returned blob includes the signature line
     * and the closing brace, so downstream `.contains()` checks operate ONLY on tokens that live
     * inside the named function — never on tokens from unrelated functions in the same file (per
     * CLAUDE.md §"Scope-level grep gates").
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
     * Build a minimal GameIR carrying a single `platformer_physics` GenericSystem with both the
     * tilemap-collision gate ([solidThreshold] non-null) AND the jumpHold gate
     * ([jumpHoldMaxFrames] > 0) set.
     *
     * Both gates must fire for section 5b to emit:
     * - `gameUsesTilemapCollision()` returns true (via `solidThreshold` set) → the
     *   `buildTilemapPhysicsUpdateFunction` branch is taken (Plan 12-11).
     * - `cfg.jumpHoldMaxFrames > 0` → the section-5b block inside that branch emits (Plan 12-13).
     *
     * The positive test sets both; the negative test sets `solidThreshold` (so the tilemap- physics
     * branch still runs and `platformer_physics_update` is still emitted) but leaves
     * `jumpHoldMaxFrames` at its default of 0, so section 5b is omitted.
     */
    private fun buildPlatformerGameIR(
        solidThreshold: Int? = 17,
        jumpHoldMaxFrames: Int = 0,
        id: String = "plat",
    ): GameIR {
        val config =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = solidThreshold,
                jumpHoldMaxFrames = jumpHoldMaxFrames,
            )
        val system =
            GenericSystem(
                id = id,
                config = mapOf("type" to "platformer_physics", "physicsConfig" to config),
            )
        return GameIR(
            name = "TestJumpHoldGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(system),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — section 5b emits when jumpHoldMaxFrames > 0 AND tilemap-collision on.
    //
    // Production mechanism (Plan 12-13 — PlatformerVisitor.buildTilemapPhysicsUpdate
    // Function section 5b, gated on `cfg.jumpHoldMaxFrames > 0` inside the tilemap-
    // physics branch): when both gates fire, the section emits the 3-statement
    // reference shape — timer decrement, button/timer guard, gravity application
    // + timer reset.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): a
    // file-level `mainC.contains("_jump_increase_timer")` would false-positive on
    // the jump-initiation site (Plan 12-11 §section 5 sets the timer to
    // `cfg.jumpHoldMaxFrames` at jump time), and would also false-positive on
    // the WRAM global declaration at the top of main.c. The brace-walk extracts
    // the `platformer_physics_update` body so the substring checks fire ONLY
    // against tokens inside the physics function — and the multiplicity checks
    // (≥ 2 timer references) lock that section 5b's body emitted in addition to
    // section 5's jump-initiation site.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update emits jumpHold gravity-suppression body when jumpHoldMaxFrames is positive`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17, jumpHoldMaxFrames = 20)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Signature anchor — function declaration starts at column 0 of main.c with the literal
        // text `void platformer_physics_update` per Plan 12-11's emission contract
        // (CFunction(isBanked = false) → HOME bank → main.c). This is the awk
        // `/^void platformer_physics_update/` anchor expressed in Kotlin.
        val signatureRegex = Regex("^void platformer_physics_update", RegexOption.MULTILINE)
        val signatureFound = signatureRegex.containsMatchIn(mainC)

        // Evidence-before-assert: extract and persist the physics body BEFORE any assertion
        // fires so a RED run still produces a reviewable artifact on disk (per the
        // evidence-before-assert pattern from Plan 12-09 TilemapCollisionEmissionTest).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_jumpHold.c").writeText(physicsBody)

        assertTrue(
            signatureFound,
            "platformer_physics_update declaration must start with " +
                "'void platformer_physics_update' at column 0 of main.c " +
                "(Plan 12-11 awk-brace-walk extraction contract). main.c head:\n" +
                mainC.take(2000),
        )
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // _jump_increase_timer references — section 5b references the timer in 4 distinct spots
        // (decrement, `> 0u` guard, `== 0u` guard, `= 0u` reset). The jump-initiation site at
        // section 5 references it once more (assignment to `cfg.jumpHoldMaxFrames` at jump
        // time). So the function-scope count is at least 5; we assert ≥ 2 to lock that section
        // 5b's emission happened in addition to section 5's initiation — and to leave headroom
        // for benign reformat / extra clauses without re-baselining the test. Plan 12-13
        // SUMMARY §"Plan 12-14 readiness" documents the 4+1 expected occurrences.
        val timerCount = physicsBody.split("_jump_increase_timer").size - 1
        assertTrue(
            timerCount >= 2,
            "platformer_physics_update body must reference _jump_increase_timer at least 2× " +
                "(jump-initiation + at least one section-5b use). Found $timerCount. " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // Decrement form — Plan 12-13 emits PREFIX (`--_jump_increase_timer`); accept both
        // prefix and postfix so the test does not break if a future CPostfixUnaryExpr AST
        // node lets the visitor emit `_jump_increase_timer--` instead. This is the explicit
        // contract documented in Plan 12-13 SUMMARY §Deviations rule 1.
        val prefixDecrement = physicsBody.contains("--_jump_increase_timer")
        val postfixDecrement = physicsBody.contains("_jump_increase_timer--")
        assertTrue(
            prefixDecrement || postfixDecrement,
            "platformer_physics_update body must decrement _jump_increase_timer " +
                "(prefix `--_jump_increase_timer` or postfix `_jump_increase_timer--`). " +
                "Plan 12-13 emits prefix; this assertion accepts both for forward " +
                "compatibility with a future CPostfixUnaryExpr AST. physics body:\n" +
                physicsBody.take(8000),
        )

        // button_held(J_A) — section 5b's CRawExpr emits the literal token
        // `!(button_held(J_A) || button_held(J_UP))`. The presence of `button_held(J_A)`
        // inside the function scope locks the A-button half of the variable-height-jump
        // guard. A regression that, e.g., De-Morgan-rewrites the guard to
        // `!button_held(J_A) && !button_held(J_UP)` would still satisfy this assertion
        // (correctly — the token `button_held(J_A)` survives the rewrite). A regression that
        // drops the A-button check entirely (e.g. only Up triggers variable-height) would
        // fail here.
        assertTrue(
            physicsBody.contains("button_held(J_A)"),
            "platformer_physics_update body must reference button_held(J_A) — the A-button " +
                "half of the variable-height-jump guard (Plan 12-13 §section 5b CRawExpr). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // button_held(J_UP) — the Up-button half of the same guard. The reference player.c
        // uses both A and Up as variable-height triggers (lines 304-305). Asserting both halves
        // separately locks the dual-input contract: a regression that accidentally drops one
        // would fail here even if the other survived.
        assertTrue(
            physicsBody.contains("button_held(J_UP)"),
            "platformer_physics_update body must reference button_held(J_UP) — the Up-button " +
                "half of the variable-height-jump guard (Plan 12-13 §section 5b CRawExpr). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // _player_vy += <gravity_scaled> — the gravity-application statement inside section
        // 5b's thenBody. With cfg.gravity=2 and the *16 scale chosen by Plan 12-13, this emits
        // as `_player_vy += 32u;` (or similar — we only lock the operator + LHS shape, not
        // the literal value, so future tuning of the scale factor does not break this test).
        // The token `_player_vy += ` is the binding shape — a regression that changed it to
        // `=` (absolute) or `-=` (anti-gravity) would fail here.
        assertTrue(
            physicsBody.contains("_player_vy +="),
            "platformer_physics_update body must contain `_player_vy +=` — gravity application " +
                "inside section 5b's thenBody (Plan 12-13). A change to `=` or `-=` would " +
                "break the variable-height-jump behaviour. physics body:\n" +
                physicsBody.take(8000),
        )

        // _grounded == 0 (or 0u) — the airborne guard at the top of section 5b. Locks that
        // the suppression block runs ONLY while airborne. A regression that drops the guard
        // would suppress gravity on the ground too, breaking jump-on-platform behaviour
        // entirely. Plan 12-13 emits `_grounded == 0` via CIntLiteral(0); accept the `0u`
        // form too (CLiteral) in case a future plan re-aligns the literal flavour with the
        // surrounding UINT8 context.
        val groundedGuardSigned = physicsBody.contains("_grounded == 0")
        // The signed form is a substring of the unsigned form (`_grounded == 0u`), so the
        // signed check above already covers both. Asserting once is sufficient — the message
        // documents both accepted forms.
        assertTrue(
            groundedGuardSigned,
            "platformer_physics_update body must contain `_grounded == 0` (or `_grounded == 0u`) " +
                "— the airborne guard wrapping section 5b's suppression branch (Plan 12-13). " +
                "physics body:\n${physicsBody.take(8000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — gate verification. When jumpHoldMaxFrames == 0 (default), the
    // section-5b gravity-suppression block is omitted from platformer_physics_update.
    //
    // Production mechanism (Plan 12-13 — `cfg.jumpHoldMaxFrames > 0` gate fails):
    // section 5b is omitted entirely. The function `platformer_physics_update` is
    // still emitted (the tilemap-physics branch runs because `solidThreshold` is
    // set), AND Plan 12-11's section-5 jump-initiation site STILL emits the
    // harmless `_jump_increase_timer = cfg.jumpHoldMaxFrames` assignment
    // (PlatformerVisitor.kt line 627). With cfg.jumpHoldMaxFrames=0, that emits
    // as `_jump_increase_timer = 0u;` — a no-op that pre-dates Plan 12-13 and
    // belongs to the 12-11 baseline. Plan 12-13's deviation note (SUMMARY §5b
    // codegen-time gate comment lines 647-649) acknowledges this: "When 0, the
    // 12-11 baseline is preserved byte-identical … the jump-initiation site
    // still emits the harmless `_jump_increase_timer = 0u;` assignment per Plan
    // 12-11 §decision #4."
    //
    // So the binding contract this negative test locks is NOT "the token
    // `_jump_increase_timer` never appears" (Plan 12-11 emits one reference at
    // section 5 unconditionally), but rather "the THREE distinctive section-5b
    // tokens never appear":
    //
    //   1. `--_jump_increase_timer` or `_jump_increase_timer--`  — section 5b's decrement
    //   2. `button_held(J_A)` / `button_held(J_UP)`              — section 5b's CRawExpr
    //   3. `_player_vy += `                                       — section 5b's gravity-apply
    //
    // The rest of the function uses `_player_vy = 0u;` (AABB-probe vertical
    // collision) and `_player_vy = -jumpVelocity` (jump-init) — never the `+=`
    // compound operator. So `_player_vy +=` is a unique discriminator for
    // section 5b's emission.
    //
    // Multiplicity also matters: when section 5b is off, `_jump_increase_timer`
    // appears EXACTLY ONCE in the function body (the section-5 init at line
    // 627). Asserting `count == 1` catches a regression that re-fires section
    // 5b silently — section 5b adds 4 more references (decrement, `> 0u`,
    // `== 0u`, `= 0u` reset) → count of 5 if it leaked through.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update omits jumpHold body when jumpHoldMaxFrames is zero`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17, jumpHoldMaxFrames = 0)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The function MUST still exist (tilemap-physics branch emits it because
        // `solidThreshold` is set). Asserting presence rules out a regression that
        // accidentally drops the physics function entirely when the jumpHold gate is off.
        val signatureRegex = Regex("^void platformer_physics_update", RegexOption.MULTILINE)
        assertTrue(
            signatureRegex.containsMatchIn(mainC),
            "platformer_physics_update must still be emitted when jumpHoldMaxFrames is 0 — the " +
                "tilemap-physics branch runs whenever solidThreshold is set. main.c head:\n" +
                mainC.take(2000),
        )

        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c " +
                "even when jumpHold gate is off. main.c head:\n${mainC.take(2000)}",
        )

        // Multiplicity discriminator — when section 5b is gated off, the ONLY reference to
        // `_jump_increase_timer` inside the function body is the section-5 jump-initiation
        // init (Plan 12-11 line 627, emitted unconditionally as part of the 12-11 baseline).
        // Section 5b would add 4 more references (decrement + `> 0u` guard + `== 0u` guard +
        // `= 0u` reset). Asserting exactly 1 catches a regression that re-fires section 5b
        // silently — the count of 5 would fail here even though a simple `contains` check
        // would not, because the section-5 baseline reference is permitted.
        val timerCount = physicsBody.split("_jump_increase_timer").size - 1
        assertTrue(
            timerCount == 1,
            "platformer_physics_update body must contain EXACTLY 1 reference to " +
                "_jump_increase_timer when jumpHoldMaxFrames is 0 — only the section-5 " +
                "jump-initiation init (Plan 12-11 baseline). Section 5b would add 4 more " +
                "references; a count >1 means section 5b leaked through. Found $timerCount. " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // Section 5b's distinctive decrement — accept both prefix and postfix forms so a
        // future CPostfixUnaryExpr AST refactor does not break this negative-case lock.
        // Neither form may appear in the function body when section 5b is gated off.
        assertFalse(
            physicsBody.contains("--_jump_increase_timer") ||
                physicsBody.contains("_jump_increase_timer--"),
            "platformer_physics_update body must NOT contain the section-5b decrement " +
                "(`--_jump_increase_timer` or `_jump_increase_timer--`) when jumpHoldMaxFrames " +
                "is 0. physics body:\n${physicsBody.take(8000)}",
        )

        // Section 5b's CRawExpr emits `button_held(J_A)` and `button_held(J_UP)` inside the
        // gravity-suppression guard. When the gate is off, neither token should appear inside
        // the function scope. The section-5 jump-initiation site uses `button_pressed(J_A)` /
        // `button_pressed(J_UP)` (line 601-603) — different token — so this check robustly
        // discriminates section 5b's emission from section 5's.
        assertFalse(
            physicsBody.contains("button_held(J_A)"),
            "platformer_physics_update body must NOT reference button_held(J_A) when " +
                "jumpHoldMaxFrames is 0 — section 5b's CRawExpr is gated off. (Section 5's " +
                "jump-initiation site uses button_pressed, not button_held, so this is a clean " +
                "section-5b-only discriminator.) physics body:\n${physicsBody.take(8000)}",
        )
        assertFalse(
            physicsBody.contains("button_held(J_UP)"),
            "platformer_physics_update body must NOT reference button_held(J_UP) when " +
                "jumpHoldMaxFrames is 0 — section 5b's CRawExpr is gated off. physics body:\n" +
                physicsBody.take(8000),
        )

        // _player_vy += — section 5b's gravity-application statement. The rest of the
        // function body uses `_player_vy = 0u;` (AABB vertical collision response) and
        // `_player_vy = -<jumpVelocity>;` (jump-init), never the `+=` compound operator. So
        // `_player_vy +=` is a unique discriminator for section 5b's emission. A regression
        // that accidentally fires section 5b would leak this token here and fail RED.
        assertFalse(
            physicsBody.contains("_player_vy +="),
            "platformer_physics_update body must NOT contain `_player_vy +=` when " +
                "jumpHoldMaxFrames is 0 — that is section 5b's gravity-application token " +
                "(the rest of the function uses `=` not `+=`). physics body:\n" +
                physicsBody.take(8000),
        )
    }
}
