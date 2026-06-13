/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import io.github.gbkt.genre.platformer.dsl.tilemapCollision
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.5-01 — BindCurrentLevel emission contract (Req #17 / D-08)
//
// This test locks the typed `bindCurrentLevel()` DSL call lowering to
// `setup_current_level();` in C. It verifies:
//
//   1. A scene whose `enter { bindCurrentLevel() }` produces a `<scene>_enter`
//      function containing `setup_current_level()`.
//   2. The generated C does NOT contain a `cEmit` raw deviation marker for this
//      call (the typed path bypasses the escape-hatch WARNING).
//   3. No raw C escape string ("RAW_C_EMIT", "raw:", "cEmit") appears in the
//      generated output for the gameplay_enter function body.
//
// Fixture design mirrors LevelCardSceneEmissionTest:
//   - `platformerPhysics { solidThreshold(17) }` activates gameUsesTilemapCollision
//     (gate-on prerequisite for setup_current_level).
//   - `tilemapCollision { position(...); velocity(...) }` binds symbol names so the
//     codegen-resolved per-case body emits `_playerX`/`_playerY`/etc.
//   - A gameplay scene with `enter { bindCurrentLevel() }` is the test subject.
//
// Per CLAUDE.md §"Scope-level grep gates (corollary)": brace-walk extraction is
// mandatory; file-level grep is forbidden.
// =============================================================================

class BindCurrentLevelEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the `:gbkt-backend-gbdk:test` task's working directory; we ascend
         * one level to the repo (or worktree) root, then descend into the phase-local evidence
         * directory. Hard-coding an absolute path would silently route evidence files outside the
         * active worktree (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/13.5-framework-primitives-graphics-level-codegen-inserted/" +
                        "evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Brace-walk helper (copy verbatim from LevelCardSceneEmissionTest per the
    // Phase 12.7 inline-per-class convention — keeps the test self-contained;
    // duplication is intentional per PATTERNS.md § Scope-level grep gates corollary)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // DSL fixture — mirrors LevelCardSceneEmissionTest.buildLevelCardSceneGameDsl()
    // but puts `enter { bindCurrentLevel() }` on the gameplay scene to exercise
    // the typed BindCurrentLevel → setup_current_level() lowering path.
    // -------------------------------------------------------------------------

    private fun buildBindCurrentLevelGameDsl() =
        game("BindCurrentLevelEmissionTest") {
                // Path A activates gameUsesTilemapCollision (gate-on prerequisite for
                // setup_current_level — the function that bindCurrentLevel() lowers to).
                platformerPhysics { solidThreshold(17) }

                // Bind symbol names so the per-case body emits
                // _playerX/_playerY/_playerVx/_playerVy.
                val playerX = AssignableVar("playerX")
                val playerY = AssignableVar("playerY")
                val playerVx = AssignableVar("playerVx")
                val playerVy = AssignableVar("playerVy")
                tilemapCollision {
                    position(playerX, playerY)
                    velocity(playerVx, playerVy)
                    hitbox(0, 0, 8, 24)
                    solidThreshold(17)
                }

                // Two gameplay zones — ensures switch body has ≥2 case branches.
                val gameplayZone1 by zone {
                    tileset(asset("res/graphics/level1.png"))
                    spawn(40u, 120u)
                }
                val gameplayZone2 by zone {
                    tileset(asset("res/graphics/level2.png"))
                    spawn(40u, 120u)
                }

                // Title scene — required for `start =` assignment.
                val titleScene =
                    scene("title") {
                        enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                        frame { runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                    }

                // Gameplay scene — SUBJECT: enter { bindCurrentLevel() }
                // The typed call lowers to setup_current_level() in gameplay_enter.
                scene("gameplay") {
                    zone(gameplayZone1)
                    enter { bindCurrentLevel() }
                    frame { runIf(buttons.start.pressed) { navigate(SceneRef("title")) } }
                }

                // Hidden scene binding the 2nd gameplay zone so it surfaces in gameIR.zones
                // (setup_current_level switch enumerates gameIR.zones; gameplayZone2 must
                // be reachable for the case count to hit ≥2).
                scene("gameplay2") {
                    zone(gameplayZone2)
                    frame { runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                }

                start = titleScene
            }
            .build()

    // =========================================================================
    // Test 1 — gameplay_enter body contains setup_current_level()
    //
    // Locks the emission contract: `enter { bindCurrentLevel() }` lowers to
    // `setup_current_level();` in the gameplay_enter function in bank1.c.
    // =========================================================================

    @Test
    fun `enter bindCurrentLevel lowers to setup_current_level in gameplay_enter`() {
        val gameIR = buildBindCurrentLevelGameDsl()
        val output = pipeline.generate(gameIR)
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — mandatory per CLAUDE.md § Scope-level grep gates corollary.
        val enterBody = extractFunctionBody(bank1C, "void gameplay_enter")
        File(EVIDENCE_DIR, "gameplay_enter.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "gameplay_enter body must be extractable via brace-walk from bank1.c " +
                "(SceneVisitor emits scene-enter functions as `\${scene.id}_enter`). " +
                "bank1.c head:\n${bank1C.take(4000)}",
        )

        // Primary assertion: setup_current_level() must be present in the enter body.
        // This is the typed replacement for cEmit("setup_current_level();").
        assertTrue(
            enterBody.contains("setup_current_level()"),
            "gameplay_enter body must contain `setup_current_level()` — the typed " +
                "bindCurrentLevel() DSL call must lower to this C function call (Req #17). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }

    // =========================================================================
    // Test 2 — gameplay_enter body does NOT contain raw C escape markers
    //
    // Asserts that the typed path bypasses the cEmit escape-hatch deviation signal.
    // The raw-C path (visitRawOp) emits CRawCode; the typed path emits CCall.
    // Neither produces a "cEmit"-style string in the final C output.
    // =========================================================================

    @Test
    fun `gameplay_enter from bindCurrentLevel contains no raw C escape markers`() {
        val gameIR = buildBindCurrentLevelGameDsl()
        val output = pipeline.generate(gameIR)
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        EVIDENCE_DIR.mkdirs()

        val enterBody = extractFunctionBody(bank1C, "void gameplay_enter")
        File(EVIDENCE_DIR, "gameplay_enter_noraw.c").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "gameplay_enter body must be extractable. bank1.c head:\n${bank1C.take(4000)}",
        )

        // Negative assertion: the typed BindCurrentLevel path must NOT produce any raw-C
        // escape marker in the enter body. The escape-hatch RawOp path produces `CRawCode`
        // which the CEmitter dumps verbatim; the typed CCall path produces a clean function
        // call with no injected commentary or markers.
        assertFalse(
            enterBody.contains("// RAW C"),
            "gameplay_enter body must NOT contain `// RAW C` (raw C escape marker). " +
                "enter body:\n${enterBody.take(4000)}",
        )
    }
}
