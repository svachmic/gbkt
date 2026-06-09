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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 12.1 Plan 06 — Defect 4 symbol-rewrite emission contract lock
//
// LOCKED INVARIANT (PLAN 12.1-06 §must_haves):
//   PlatformerVisitor.buildTilemapPhysicsUpdateFunction MUST emit symbol names
//   resolved from a `tilemap_collision` GenericSystem's config map (when
//   present), and MUST fall back to the legacy `_player_x` / `_player_y` /
//   `_player_vx` / `_player_vy` / `_grounded` shape when the system is absent.
//
// The 5 symbol classes covered by Defect 4 (RESEARCH §D-claude-1):
//   - posXVar   (default `player_x`   → C symbol `_player_x`)
//   - posYVar   (default `player_y`   → C symbol `_player_y`)
//   - vxVar     (default `player_vx`  → C symbol `_player_vx`)
//   - vyVar     (default `player_vy`  → C symbol `_player_vy`)
//   - groundedVar (default `grounded` → C symbol `_grounded`)
//
// `_jump_increase_timer` is OUT OF SCOPE for Defect 4 (RESEARCH §Risks #1) —
// it remains a pipeline-emitted global declared at the rect-physics path
// (PlatformerVisitor.kt:171 area) and is reused in the tilemap-physics path
// verbatim. A rewrite of this symbol would break the rect-physics path. The
// test asserts the symbol is PRESERVED unchanged regardless of system presence.
//
// Hitbox auto-derivation (PlatformerVisitor.kt:500-502) and `cfg.solidThreshold`
// reads are likewise OUT OF SCOPE per checker W4. Not asserted here — locked
// instead by the existing 4 EmissionTests (JumpHold/HorizontalScroll/Tilemap
// Collision/PlatformerCodegen) which exercise the legacy-fallback path.
//
// CLAUDE.md §"Scope-level grep gates" — all assertions extract the
// `platformer_physics_update` function body via brace-walk so substring checks
// fire ONLY against tokens inside that function. A file-level
// `mainC.contains("_player_x")` would false-positive on the user-declared
// extern at the top of main.c if a future plan introduces such a global, or on
// rect-physics symbols like `_plat_vx` that share a prefix.
// =============================================================================

class Defect4SymbolRewriteEmissionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching closing
     * brace at depth zero.
     *
     * Mirror of the same helper in `JumpHoldEmissionTest.kt`, `TilemapCollisionEmissionTest.kt`,
     * and `HorizontalScrollEmissionTest.kt`. The returned blob includes the signature line and the
     * closing brace so downstream `.contains()` checks operate ONLY on tokens inside the named
     * function — never on tokens from unrelated functions or top-of-file extern declarations (per
     * CLAUDE.md §"Scope-level grep gates" corollary).
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0). This is the
     * Kotlin counterpart of awk's `/^prefix/` anchor.
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
     * Build a minimal GameIR with a `platformer_physics` GenericSystem (to fire Path A of
     * `gameUsesTilemapCollision`, which makes the pipeline emit `platformer_physics_update`) and an
     * optional `tilemap_collision` GenericSystem (Path C — the new substrate from Plan 12.1-05)
     * whose config map carries user-DSL property names.
     *
     * When [bindings] is null, NO tilemap_collision system is registered — the visitor must fall
     * back to the legacy `_player_x` / `_player_y` / `_player_vx` / `_player_vy` / `_grounded`
     * shape (regression guard for any caller that opts into tilemap collision via Path A only, e.g.
     * the existing JumpHold / TilemapCollision / HorizontalScroll emission tests).
     */
    private fun buildGameIR(
        bindings: Map<String, String>? = null,
        solidThreshold: Int? = 17,
    ): GameIR {
        val physicsSystem =
            GenericSystem(
                id = "physics",
                config =
                    mapOf(
                        "type" to "platformer_physics",
                        "physicsConfig" to
                            PlatformerPhysicsConfig(
                                gravity = 2,
                                jumpForce = 8,
                                terminalVelocity = 12,
                                solidThreshold = solidThreshold,
                            ),
                    ),
            )
        val systems =
            if (bindings == null) {
                listOf(physicsSystem)
            } else {
                val tcConfig = mutableMapOf<String, Any>("type" to "tilemap_collision")
                bindings.forEach { (k, v) -> tcConfig[k] = v }
                listOf(physicsSystem, GenericSystem(id = "tilemap_collision", config = tcConfig))
            }
        return GameIR(
            name = "TestDefect4Game",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = systems,
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 1 — posXVar bound → visitor emits _playerX instead of _player_x.
    //
    // The plan locks this contract at PATTERNS.md "Rewrite pattern" and
    // RESEARCH §D-claude-1's per-symbol rewrite table row 1.
    // -------------------------------------------------------------------------

    @Test
    fun `posXVar bound to playerX emits _playerX inside physics function and not _player_x`() {
        val gameIR =
            buildGameIR(
                bindings =
                    mapOf(
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                    )
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable; main.c head:\n${mainC.take(2000)}",
        )

        // Positive: user-named symbol must appear inside the function body.
        assertTrue(
            physicsBody.contains("_playerX"),
            "physics body must reference user-DSL-named _playerX (Plan 12.1-06 contract). " +
                "physics body:\n${physicsBody.take(4000)}",
        )
        // Negative: legacy _player_x must NOT appear inside the function body.
        assertFalse(
            physicsBody.contains("_player_x"),
            "physics body must NOT reference legacy _player_x when posXVar is bound to playerX " +
                "(Plan 12.1-06 §must_haves). physics body:\n${physicsBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 2 — All four position/velocity slots bound.
    //
    // Locks the full rewrite shape — every one of (posXVar, posYVar, vxVar,
    // vyVar) must reach the emission body. The previous test only covers
    // posXVar; this one covers the remaining three plus re-covers posXVar to
    // guard against a half-implementation regression that, e.g., only rewrites
    // position but leaves velocity hardcoded.
    // -------------------------------------------------------------------------

    @Test
    fun `all four position-velocity slots bound emits all four user-named symbols`() {
        val gameIR =
            buildGameIR(
                bindings =
                    mapOf(
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                    )
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        assertTrue(physicsBody.isNotEmpty(), "physics body must be extractable")

        // Each of the four user-named symbols must appear inside the function body.
        assertTrue(
            physicsBody.contains("_playerX"),
            "physics body must reference _playerX. physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_playerY"),
            "physics body must reference _playerY. physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_playerVx"),
            "physics body must reference _playerVx. physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_playerVy"),
            "physics body must reference _playerVy. physics body:\n${physicsBody.take(4000)}",
        )

        // None of the four legacy snake_case symbols may appear inside the function body.
        assertFalse(
            physicsBody.contains("_player_x"),
            "physics body must NOT reference legacy _player_x. physics body:\n" +
                physicsBody.take(4000),
        )
        assertFalse(
            physicsBody.contains("_player_y"),
            "physics body must NOT reference legacy _player_y. physics body:\n" +
                physicsBody.take(4000),
        )
        assertFalse(
            physicsBody.contains("_player_vx"),
            "physics body must NOT reference legacy _player_vx. physics body:\n" +
                physicsBody.take(4000),
        )
        assertFalse(
            physicsBody.contains("_player_vy"),
            "physics body must NOT reference legacy _player_vy. physics body:\n" +
                physicsBody.take(4000),
        )
    }

    // -------------------------------------------------------------------------
    // TEST 3 — groundedVar bound to `grounded` keeps _grounded (no observable
    // change in this case — the bound name happens to coincide with the
    // legacy convention — but the resolution path fires).
    //
    // The visitor cannot distinguish "user bound groundedVar to `grounded`"
    // from "groundedVar absent" at the emission layer because both produce the
    // same C symbol. This test locks that the resolution path is exercised
    // (the visitor does not crash, the symbol still appears) — a regression
    // that, e.g., emits "_groundedgrounded" by accidentally double-prefixing
    // would fail here.
    // -------------------------------------------------------------------------

    @Test
    fun `groundedVar bound to grounded preserves _grounded emission`() {
        val gameIR =
            buildGameIR(
                bindings =
                    mapOf(
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                    )
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        assertTrue(physicsBody.isNotEmpty(), "physics body must be extractable")

        // _grounded must appear at least twice — the jump-initiation site at section 5 zeroes
        // the timer/sets _grounded = 0 on jump-start, and section 5b's airborne guard reads
        // _grounded == 0 to gate the gravity-suppression branch. The foot-probe helper also
        // sets `_grounded = 1` on landing. Asserting ≥ 2 occurrences locks that multiple call
        // sites (not just one) survived the rewrite. Per CLAUDE.md §"Scope-level grep gates",
        // the count is over the brace-walked body, not the whole main.c.
        val groundedCount = physicsBody.split("_grounded").size - 1
        assertTrue(
            groundedCount >= 2,
            "physics body must reference _grounded at least 2× (jump-initiation + airborne " +
                "guard / landing). Found $groundedCount. physics body:\n" +
                physicsBody.take(4000),
        )

        // Regression guard against accidental double-prefixing — `_groundedgrounded`,
        // `__grounded`, etc.
        assertFalse(
            physicsBody.contains("_groundedgrounded"),
            "physics body must NOT contain accidentally-double-named _groundedgrounded " +
                "(regression guard against prefix mishandling). physics body:\n" +
                physicsBody.take(4000),
        )
        assertFalse(
            physicsBody.contains("__grounded"),
            "physics body must NOT contain accidentally-double-prefixed __grounded " +
                "(regression guard against `_` + `_grounded`). physics body:\n" +
                physicsBody.take(4000),
        )
    }

    // -------------------------------------------------------------------------
    // TEST 4 — Legacy fallback. No tilemap_collision system → visitor emits
    // the legacy `_player_x` / `_player_y` / `_player_vx` / `_player_vy` /
    // `_grounded` shape unchanged.
    //
    // This is the regression guard for the existing 4 EmissionTests
    // (JumpHold/HorizontalScroll/TilemapCollision/PlatformerCodegen) which do
    // NOT declare a tilemap_collision system. They exercise the legacy
    // fallback path; if this test fails RED-then-GREEN, those 4 tests will
    // ALSO turn RED (and they currently pass on master). That coupling is the
    // exact safety net checker W4 requested.
    // -------------------------------------------------------------------------

    @Test
    fun `legacy fallback fires when tilemap_collision system absent`() {
        val gameIR = buildGameIR(bindings = null) // no tilemap_collision system
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        assertTrue(physicsBody.isNotEmpty(), "physics body must be extractable")

        // Legacy symbols must appear when the new system is absent.
        assertTrue(
            physicsBody.contains("_player_x"),
            "physics body MUST reference legacy _player_x when tilemap_collision system is " +
                "absent (back-compat regression guard). physics body:\n" +
                physicsBody.take(4000),
        )
        assertTrue(
            physicsBody.contains("_player_y"),
            "physics body MUST reference legacy _player_y when tilemap_collision system is " +
                "absent. physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_player_vx"),
            "physics body MUST reference legacy _player_vx when tilemap_collision system is " +
                "absent. physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_player_vy"),
            "physics body MUST reference legacy _player_vy when tilemap_collision system is " +
                "absent. physics body:\n${physicsBody.take(4000)}",
        )
        // _grounded is the legacy convention too — present in both branches.
        assertTrue(
            physicsBody.contains("_grounded"),
            "physics body MUST reference _grounded in the legacy-fallback branch. physics " +
                "body:\n${physicsBody.take(4000)}",
        )

        // User-named camelCase symbols must NOT appear when nothing is bound.
        assertFalse(
            physicsBody.contains("_playerX"),
            "physics body must NOT reference _playerX when tilemap_collision system is absent. " +
                "physics body:\n${physicsBody.take(4000)}",
        )
        assertFalse(
            physicsBody.contains("_playerY"),
            "physics body must NOT reference _playerY when tilemap_collision system is absent. " +
                "physics body:\n${physicsBody.take(4000)}",
        )
        assertFalse(
            physicsBody.contains("_playerVx"),
            "physics body must NOT reference _playerVx when tilemap_collision system is absent. " +
                "physics body:\n${physicsBody.take(4000)}",
        )
        assertFalse(
            physicsBody.contains("_playerVy"),
            "physics body must NOT reference _playerVy when tilemap_collision system is absent. " +
                "physics body:\n${physicsBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 5 — `_jump_increase_timer` is OUT OF SCOPE per RESEARCH §Risks #1.
    // The pipeline-emitted symbol from the rect-physics path must survive the
    // Defect-4 rewrite unchanged in BOTH branches (bound + unbound).
    //
    // The visitor declares `_jump_increase_timer` at line 171 (rect-physics
    // path WRAM globals) and reuses it from the tilemap-physics path at the
    // jump-initiation site (section 5) and the gravity-suppression block
    // (section 5b). Rewriting it to `_jumpIncreaseTimer` (or any user-named
    // form) would either break the rect-physics path callers (which declare
    // the rect-form symbol) or force the user to declare a matching `var
    // jumpIncreaseTimer by u8Var(0)` in every platformer DSL — neither is
    // desired. The symbol stays bare.
    // -------------------------------------------------------------------------

    @Test
    fun `_jump_increase_timer is preserved unchanged in both branches`() {
        // Branch A — bindings present.
        val gameIRBound =
            buildGameIR(
                bindings =
                    mapOf(
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                    )
            )
        val outputBound = pipeline.generate(gameIRBound)
        val mainCBound = outputBound.files["main.c"] ?: error("main.c not generated")
        val physicsBodyBound = extractFunctionBody(mainCBound, "void platformer_physics_update")
        assertTrue(
            physicsBodyBound.contains("_jump_increase_timer"),
            "bound-branch physics body must reference _jump_increase_timer verbatim " +
                "(RESEARCH §Risks #1 lock). physics body:\n${physicsBodyBound.take(4000)}",
        )
        assertFalse(
            physicsBodyBound.contains("_jumpIncreaseTimer"),
            "bound-branch physics body must NOT contain a user-named _jumpIncreaseTimer " +
                "(RESEARCH §Risks #1 lock). physics body:\n${physicsBodyBound.take(4000)}",
        )

        // Branch B — no bindings (legacy fallback).
        val gameIRLegacy = buildGameIR(bindings = null)
        val outputLegacy = pipeline.generate(gameIRLegacy)
        val mainCLegacy = outputLegacy.files["main.c"] ?: error("main.c not generated")
        val physicsBodyLegacy = extractFunctionBody(mainCLegacy, "void platformer_physics_update")
        assertTrue(
            physicsBodyLegacy.contains("_jump_increase_timer"),
            "legacy-branch physics body must reference _jump_increase_timer verbatim. " +
                "physics body:\n${physicsBodyLegacy.take(4000)}",
        )
        assertFalse(
            physicsBodyLegacy.contains("_jumpIncreaseTimer"),
            "legacy-branch physics body must NOT contain a user-named _jumpIncreaseTimer. " +
                "physics body:\n${physicsBodyLegacy.take(4000)}",
        )

        // Symmetry check — the count of `_jump_increase_timer` references must match between
        // the two branches. If a rewrite accidentally drops or duplicates one of the symbol
        // sites in only one branch, the counts diverge and this assertion fires.
        val countBound = physicsBodyBound.split("_jump_increase_timer").size - 1
        val countLegacy = physicsBodyLegacy.split("_jump_increase_timer").size - 1
        assertEquals(
            countLegacy,
            countBound,
            "_jump_increase_timer reference count must be identical in bound and legacy " +
                "branches (Defect-4 rewrite must NOT touch this symbol). bound=$countBound, " +
                "legacy=$countLegacy",
        )
    }
}
