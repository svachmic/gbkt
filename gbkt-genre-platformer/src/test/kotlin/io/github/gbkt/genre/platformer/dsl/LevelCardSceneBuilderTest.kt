/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.BindCurrentLevel
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.NavigateTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plan 12.6-04 Task 2 — locks the new `levelCardScene { }` delegate-pattern DSL surface introduced
 * in `PlatformerExtensions.kt`.
 *
 * The helper closes Phase 12.6 DEFECT-1 at the DSL tier:
 * - Property name is captured via `provideDelegate` (Project Rule #1 — no magic strings).
 * - The lowered scene's frame handler emits [BindCurrentLevel] (Phase 13.5 Req #17 — typed IR node
 *   replacing the old `cEmit` → `RawOp` approach) BEFORE `navigate_to_scene(<gameplay>)` (via
 *   `navigate` → `NavigateTo`), all inside a `whenever(buttons.start.pressed) { ... }` guard
 *   (lowered to an [IfOp]).
 *
 * Round-trip contract (PSEUDO-04 in 12.6-RESEARCH § Validation Architecture, updated for Phase 13.5
 * Plan 06): `val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }` → GameIR
 * contains a scene with id == "nextLevelScene" whose frame body contains an IfOp whose then-branch
 * contains a [BindCurrentLevel] followed by NavigateTo(gameplayScene.id).
 */
class LevelCardSceneBuilderTest {

    // =========================================================================
    // Test 1 — property name capture (Project Rule #1)
    // =========================================================================

    @Test
    fun `levelCardScene delegate captures property name as scene id`() {
        val ir =
            game("test_levelcard_capture") {
                    val gameplayScene = scene("gameplay") {}
                    val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }
                    @Suppress("UNUSED_VARIABLE") val _unused = nextLevelScene

                    start = gameplayScene
                }
                .build()

        val nextLevel = ir.scenes.find { it.id == "nextLevelScene" }
        assertNotNull(
            nextLevel,
            "Expected a scene registered with id == property name 'nextLevelScene'",
        )
    }

    // =========================================================================
    // Test 2 — Start-press path emits setup_current_level then navigate
    // =========================================================================

    @Test
    fun `levelCardScene frame emits bindCurrentLevel then navigate to gameplay (Phase 13_5 Req17)`() {
        val ir =
            game("test_levelcard_emission") {
                    val gameplayScene = scene("gameplay") {}
                    val nextLevelScene by levelCardScene { onStartPress(gameplayScene) }
                    @Suppress("UNUSED_VARIABLE") val _unused = nextLevelScene

                    start = gameplayScene
                }
                .build()

        val nextLevel =
            ir.scenes.find { it.id == "nextLevelScene" }
                ?: fail("Expected a scene registered with id 'nextLevelScene'")

        // Locate the IfOp whose then-branch carries the bindCurrentLevel + navigate sequence.
        // Phase 13.5 Plan 06 (Req #17): BindCurrentLevel typed IR node replaces the old RawOp.
        val ifOp =
            nextLevel.frameOps.filterIsInstance<IfOp>().firstOrNull {
                it.then.any { op -> op is BindCurrentLevel }
            }
                ?: fail(
                    "Expected an IfOp (lowered from whenever(buttons.start.pressed)) in frame ops " +
                        "whose then-branch contains BindCurrentLevel (Phase 13.5 Req #17 typed IR node). " +
                        "frameOps: ${nextLevel.frameOps}"
                )

        val bindOp =
            ifOp.then.filterIsInstance<BindCurrentLevel>().firstOrNull()
                ?: fail(
                    "Expected a BindCurrentLevel inside the IfOp's then-branch (typed IR for setup_current_level)"
                )

        // Presence of BindCurrentLevel is the typed binding gate; no code string to assert.
        assertNotNull(bindOp, "BindCurrentLevel IR node must be present in then-branch (Req #17)")

        val nav =
            ifOp.then.filterIsInstance<NavigateTo>().firstOrNull()
                ?: fail(
                    "Expected a NavigateTo inside the IfOp's then-branch (lowered from navigate)"
                )
        assertEquals(
            "gameplay",
            nav.sceneId,
            "NavigateTo must point at the gameplay scene id captured by onStartPress(...)",
        )

        // Order invariant: BindCurrentLevel MUST come BEFORE navigate (so the level data is
        // initialized before the gameplay scene's enter runs — DEFECT-1 fix contract preserved).
        val bindIdx = ifOp.then.indexOfFirst { it is BindCurrentLevel }
        val navIdx = ifOp.then.indexOfFirst { it is NavigateTo }
        assertTrue(
            bindIdx in 0 until navIdx,
            "BindCurrentLevel must precede NavigateTo (got bindIdx=$bindIdx, navIdx=$navIdx)",
        )
    }

    // =========================================================================
    // Test 3 — omitting onStartPress raises a helpful error
    // =========================================================================

    @Test
    fun `levelCardScene without onStartPress throws helpful error`() {
        // materialize() raises via error(...) when onStartPress is omitted — assertFailsWith
        // pins the exact type AND returns the exception so the message can be matched too.
        val thrown =
            assertFailsWith<IllegalStateException>(
                "Expected an error when onStartPress(...) is omitted"
            ) {
                game("test_levelcard_missing_target") {
                        val nextLevelScene by levelCardScene {
                            // Intentionally no onStartPress(...) — must error at delegate-provision
                            // time.
                        }
                        @Suppress("UNUSED_VARIABLE") val _unused = nextLevelScene
                        start = nextLevelScene
                    }
                    .build()
            }
        val msg = thrown.message.orEmpty()
        assertTrue(
            msg.contains("must call onStartPress"),
            "Error message must mention 'must call onStartPress' (got: '$msg')",
        )
    }

    // =========================================================================
    // (Optional Test 4 from PLAN — calling outside a game { } block — is
    // impractical because `fun GameBuilder.levelCardScene(...)` is a typed
    // extension on [GameBuilder]. Without a `GameBuilder` receiver, the
    // factory cannot be referenced at all — the receiver-mismatch error is
    // raised by the Kotlin COMPILER, which is a STRONGER guarantee than a
    // runtime error. The PLAN acceptance criteria explicitly accept 3 tests
    // when Test 4 is impractical: "3 is acceptable if Test 4 is impractical
    // due to GameBuilderContext threading." (12.6-04-PLAN.md line 171.)
    //
    // The GameBuilderContext-null guard inside provideDelegate is still
    // exercised structurally — it gates the `error("levelCardScene {} must be
    // called inside a game {} block")` message — but is unreachable from
    // user code through this DSL surface.
    // =========================================================================
}
