/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.dsl.TilemapCollisionBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 12.1 Plan 07 — Defect 4 emission-invariant lock + Plan 12.1-05 builder
// API contract lock (consolidated under the codegen test package).
//
// LOCKED INVARIANTS (Plan 12.1-07 §must_haves):
//
//   1. (POSITIVE — bound case)
//      When a `tilemap_collision` GenericSystem is present with all 5 `*Var`
//      bindings, `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` MUST
//      emit `_playerX / _playerY / _playerVx / _playerVy` inside the
//      `platformer_physics_update` function body, and MUST NOT emit the
//      legacy `_player_x / _player_y / _player_vx / _player_vy`.
//
//   2. (NEGATIVE — fallback case, with symmetric negatives — checker W3)
//      When the `tilemap_collision` system is absent, the visitor MUST fall
//      back to legacy `_player_x / _player_y / _player_vx / _player_vy`, AND
//      MUST NOT emit user-named `_playerX / _playerY / _playerVx / _playerVy`.
//      The symmetric negatives close a class of regression where the visitor
//      emits BOTH forms simultaneously — the W3 vector.
//
//   3. (BUILDER API CONTRACT — checker W2)
//      `TilemapCollisionBuilder` MUST store the BARE Kotlin property names
//      (`"playerVx"`, NOT `"_playerVx"`) under `config["vxVar"]` etc. The C
//      `_` prefix is applied at codegen by the visitor's resolution block,
//      not at builder time. A regression that writes the already-prefixed
//      form would silently double-prefix at codegen (`__playerVx`).
//
// CLAUDE.md §"Scope-level grep gates" — all C-shape assertions extract the
// `platformer_physics_update` function body via brace-walk so substring
// checks fire ONLY against tokens inside that function. A file-level
// `mainC.contains("_player_x")` would false-positive on any unrelated
// top-of-file extern or symbol that happens to share the prefix.
//
// Relation to sibling tests (see SUMMARY 12.1-07 §"Coverage overlap"):
//
//   - 12.1-06's `Defect4SymbolRewriteEmissionTest` already covers Tests 1+2
//     (with the W3 symmetric negatives) — this file consolidates the same
//     contracts plus Test 3 under the file path mandated by Plan 12.1-07
//     §<output> so the codegen package carries a single canonical lock for
//     all three checker dimensions of the Defect-4 closure.
//   - 12.1-05's `TilemapCollisionBuilderTest` (under `dsl/`) already covers
//     Test 3's storage-key assertions — this file re-asserts them under
//     `codegen/` so the per-symbol contract review can happen on one page.
// =============================================================================

class TilemapPhysicsPlayerSymbolEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which for the
         * `:gbkt-genre-platformer:test` task is `<repo>/gbkt-genre-platformer`. From there we
         * ascend one level (`..`) to reach the repo (or worktree) root, then descend into the
         * phase evidence directory. Hard-coding an absolute path would silently route evidence
         * files outside the active worktree and miss the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/" +
                        "12.1-platformer-template-codegen-contract-reconciliation/evidence/" +
                        "tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching
     * closing brace at depth zero.
     *
     * Mirror of the same helper in `JumpHoldEmissionTest.kt`,
     * `TilemapCollisionEmissionTest.kt`, `HorizontalScrollEmissionTest.kt`, and
     * `Defect4SymbolRewriteEmissionTest.kt`. Per RESEARCH §D-claude-5, the helper is INLINED
     * per-test rather than factored out — the convention matches awk's `/^prefix/{p=1; d=0}
     * p{d += gsub(/{/,""); d -= gsub(/}/,""); if(d<0) exit} p` pattern that CLAUDE.md
     * §"Scope-level grep gates (corollary)" mandates for per-function invariants.
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0). This
     * is the Kotlin counterpart of awk's `/^prefix/` anchor. It prevents false matches against
     * the same prefix appearing inside a string literal, comment, or argument list of an
     * unrelated function.
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
     * Build a minimal GameIR carrying a `platformer_physics` GenericSystem (to fire Path A of
     * `gameUsesTilemapCollision`, causing the pipeline to emit `platformer_physics_update`)
     * PLUS a `tilemap_collision` GenericSystem (Path C — the Plan 12.1-05 substrate) whose
     * config map carries user-DSL property names.
     *
     * Mirrors the `bindings` parameter of `Defect4SymbolRewriteEmissionTest.buildGameIR` and
     * the `buildPlatformerGameIR` shape in `TilemapCollisionEmissionTest`. See PATTERNS.md
     * §"Builder helper pattern" for the canonical recipe.
     */
    private fun buildGameWithTilemapCollision(
        posXVar: String = "playerX",
        posYVar: String = "playerY",
        vxVar: String = "playerVx",
        vyVar: String = "playerVy",
        groundedVar: String = "grounded",
        solidThreshold: Int = 17,
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
        val tilemapCollisionSystem =
            GenericSystem(
                id = "tilemap_collision",
                config =
                    mapOf(
                        "type" to "tilemap_collision",
                        "posXVar" to posXVar,
                        "posYVar" to posYVar,
                        "vxVar" to vxVar,
                        "vyVar" to vyVar,
                        "groundedVar" to groundedVar,
                        "hitboxX" to 0,
                        "hitboxY" to 0,
                        "hitboxW" to 8,
                        "hitboxH" to 24,
                        "solidThreshold" to solidThreshold,
                    ),
            )
        return GameIR(
            name = "TestTilemapPhysicsBound",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, tilemapCollisionSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Build a minimal GameIR carrying ONLY the `platformer_physics` system — no
     * `tilemap_collision`. Path A fires; Path C does not. The visitor's resolution block must
     * fall back to the legacy `_player_x / _player_y / _player_vx / _player_vy / _grounded`
     * symbol shape.
     *
     * This exercises the same back-compat path that the 4 existing genre-platformer
     * EmissionTests (JumpHold, HorizontalScroll, TilemapCollision, PlatformerCodegen) rely
     * on.
     */
    private fun buildGameWithoutTilemapCollision(solidThreshold: Int = 17): GameIR {
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
        return GameIR(
            name = "TestTilemapPhysicsUnbound",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 1 — Positive (bound). With `tilemap_collision` system present and
    // all 5 var bindings populated, the visitor MUST emit the user-named
    // `_playerX / _playerY / _playerVx / _playerVy` symbols inside the
    // `platformer_physics_update` function body — and MUST NOT emit the
    // legacy `_player_x / _player_y / _player_vx / _player_vy`.
    //
    // Plan 12.1-06 GREEN commit `7f2734eb` rewired the visitor to read its
    // 5 player-state symbol names from the `tilemap_collision` system's
    // `config["posXVar"|"posYVar"|"vxVar"|"vyVar"|"groundedVar"]` keys. This
    // test locks that contract via brace-walk-scoped substring assertions.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update emits user-named player symbols when tilemap_collision system is present`() {
        val gameIR = buildGameWithTilemapCollision()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert: persist the extracted body BEFORE any assertion so a RED
        // run still leaves a reviewable artifact on disk.
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_bound.c").writeText(physicsBody)

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c " +
                "(Plan 12.1-07 contract). main.c head:\n${mainC.take(2000)}",
        )

        // Positive: all four user-named symbols must appear inside the function body.
        assertTrue(
            physicsBody.contains("_playerX"),
            "physics body must reference user-DSL-named _playerX (Plan 12.1-06 GREEN " +
                "commit 7f2734eb). physics body:\n${physicsBody.take(4000)}",
        )
        assertTrue(
            physicsBody.contains("_playerY"),
            "physics body must reference user-DSL-named _playerY. physics body:\n" +
                physicsBody.take(4000),
        )
        assertTrue(
            physicsBody.contains("_playerVx"),
            "physics body must reference user-DSL-named _playerVx. physics body:\n" +
                physicsBody.take(4000),
        )
        assertTrue(
            physicsBody.contains("_playerVy"),
            "physics body must reference user-DSL-named _playerVy. physics body:\n" +
                physicsBody.take(4000),
        )

        // Negative: none of the four legacy snake_case symbols may appear inside the function
        // body when the bindings are present.
        assertFalse(
            physicsBody.contains("_player_x"),
            "physics body must NOT reference legacy _player_x when posXVar is bound to " +
                "playerX (Plan 12.1-06 §must_haves). physics body:\n${physicsBody.take(4000)}",
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
    // TEST 2 — Negative (legacy fallback) with SYMMETRIC negatives — closes
    // checker W3. With NO `tilemap_collision` system, the visitor's resolution
    // block defaults to the legacy `_player_x / _player_y / _player_vx /
    // _player_vy / _grounded` shape. This preserves byte-identical emission
    // for the 4 pre-existing EmissionTests (JumpHold / HorizontalScroll /
    // TilemapCollision / PlatformerCodegen) which do not declare the new
    // system.
    //
    // The 4 SYMMETRIC negative assertions (`!body.contains("_playerX")` etc.)
    // are the W3 closure — without them, a regression that emits BOTH symbol
    // forms simultaneously (e.g. a future bug that resolves both branches and
    // leaks both into the same function body) would pass the positive
    // assertions but break compilation downstream.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update falls back to legacy _player_x when tilemap_collision system absent`() {
        val gameIR = buildGameWithoutTilemapCollision()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_unbound.c").writeText(physicsBody)

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable; main.c head:\n" +
                mainC.take(2000),
        )

        // Positive: legacy symbols fire when the new system is absent.
        assertTrue(
            physicsBody.contains("_player_x"),
            "physics body MUST reference legacy _player_x when tilemap_collision system is " +
                "absent (back-compat for the 4 existing EmissionTests). physics body:\n" +
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

        // SYMMETRIC negatives (checker W3 closure) — user-named camelCase symbols must NOT
        // appear when nothing is bound. A regression that emits BOTH forms simultaneously
        // would fail these assertions even though the positive ones would still pass.
        assertFalse(
            physicsBody.contains("_playerX"),
            "unbound case must not also emit user-named _playerX (W3 symmetric negative); " +
                "physics body:\n${physicsBody.take(2000)}",
        )
        assertFalse(
            physicsBody.contains("_playerY"),
            "unbound case must not also emit user-named _playerY (W3 symmetric negative); " +
                "physics body:\n${physicsBody.take(2000)}",
        )
        assertFalse(
            physicsBody.contains("_playerVx"),
            "unbound case must not also emit user-named _playerVx (W3 symmetric negative); " +
                "physics body:\n${physicsBody.take(2000)}",
        )
        assertFalse(
            physicsBody.contains("_playerVy"),
            "unbound case must not also emit user-named _playerVy (W3 symmetric negative); " +
                "physics body:\n${physicsBody.take(2000)}",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 3 — Builder API contract — closes checker W2.
    //
    // Plan 12.1-05's `TilemapCollisionBuilder.velocity()` setter MUST store
    // the BARE Kotlin property names (`"playerVx"`, NOT `"_playerVx"`) into
    // `config["vxVar"]`. The C `_` prefix is applied at codegen by the
    // visitor's resolution block (Plan 12.1-06's `"_" + (config[...] as?
    // String)` formula), NOT at builder time.
    //
    // This test is at the builder/IR boundary — it does NOT invoke
    // `GBDKPipeline.generate()`. A regression where the builder writes
    // `"_playerVx"` (already-prefixed) would silently double-prefix at
    // codegen (`"_" + "_playerVx" = "__playerVx"`) and fail compilation
    // downstream at the linker, far from the bug's origin. Locking the
    // contract here surfaces the bug at its source.
    //
    // Sibling: `TilemapCollisionBuilderTest` (under `dsl/`) covers the same
    // contract from the DSL package vantage. This duplicate lock under
    // `codegen/` keeps the contract review for Defect 4 on a single page —
    // the test that drives the visitor's read (Tests 1+2) and the test that
    // locks the builder's write (Test 3) live alongside each other.
    // -------------------------------------------------------------------------

    @Test
    fun `tilemapCollision velocity setter stores bare property names in config map`() {
        // Construct AssignableVar refs whose names are the bare Kotlin property names — same
        // shape as `TilemapCollisionBuilderTest.Test 2` and the path that
        // `VariableBuilders.kt:357 provideDelegate` produces at real-DSL time.
        val playerX = AssignableVar("playerX")
        val playerY = AssignableVar("playerY")
        val playerVx = AssignableVar("playerVx")
        val playerVy = AssignableVar("playerVy")
        val groundedRef = AssignableVar("grounded")

        // Build a TilemapCollisionBuilder directly (no GameBuilder context needed — the
        // builder's setters and `build()` are pure functions on the captured names).
        val builder = TilemapCollisionBuilder("tilemap_collision")
        builder.position(playerX, playerY)
        builder.velocity(playerVx, playerVy)
        builder.grounded(groundedRef)
        builder.hitbox(0, 0, 8, 24)
        builder.solidThreshold(17)
        val tcSystem = builder.build()

        // Verify the velocity setter wrote bare names (no `_` prefix).
        assertEquals(
            "playerVx",
            tcSystem.config["vxVar"] as? String,
            "vxVar must hold the bare Kotlin property name 'playerVx', NOT the prefixed " +
                "'_playerVx'. The visitor's resolution block applies the '_' prefix at " +
                "codegen — writing it here would double-prefix to '__playerVx' (W2 closure).",
        )
        assertEquals(
            "playerVy",
            tcSystem.config["vyVar"] as? String,
            "vyVar must hold the bare Kotlin property name 'playerVy', NOT '_playerVy' " +
                "(W2 closure).",
        )

        // Sibling assertions for position — same contract, different setter. If `position()`
        // ever drifts apart from `velocity()` in storage convention, this catches it.
        assertEquals(
            "playerX",
            tcSystem.config["posXVar"] as? String,
            "posXVar must hold the bare Kotlin property name 'playerX', NOT '_playerX'.",
        )
        assertEquals(
            "playerY",
            tcSystem.config["posYVar"] as? String,
            "posYVar must hold the bare Kotlin property name 'playerY', NOT '_playerY'.",
        )

        // Sanity: the resulting system has the expected `type` discriminator that Plan
        // 12.1-05's Path C predicate looks up in `GBDKPipeline.gameUsesTilemapCollision`.
        assertEquals(
            "tilemap_collision",
            tcSystem.config["type"] as? String,
            "tilemapCollision builder must register the system under " +
                "config[\"type\"] == \"tilemap_collision\" so Path C fires.",
        )
    }
}
