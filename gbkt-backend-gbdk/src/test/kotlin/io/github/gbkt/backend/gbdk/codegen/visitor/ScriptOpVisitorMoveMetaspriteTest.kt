/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.metasprite
import io.github.gbkt.core.dsl.moveMetasprite
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// MOVE METASPRITE WIRING TESTS (Plan 10-07 Task 2)
//
// Verifies that ScriptOpVisitor.visitMoveMetasprite() is wired to delegate to
// MetaspriteVisitor.generateMetaspriteFrameSwitch() — replacing the Plan 04 stub.
//
// End-to-end pipeline: DSL moveMetasprite(elephant) → MoveMetasprite ScriptOp →
//   ScriptOpVisitor.visitMoveMetasprite → MetaspriteVisitor.generateMetaspriteFrameSwitch →
//   emitted C frame-switch block in play_frame function body.
//
// Uses brace-walk extraction on `play_frame` body (CLAUDE.md §"Scope-level grep gates").
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper: extract the body of the first C function matching signature
// (local copy for test isolation — avoids cross-test coupling)
// ---------------------------------------------------------------------------

private fun extractFunctionBody(source: String, signature: String): String? {
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openIdx + 1, i)
            }
        }
        i++
    }
    return null
}

class ScriptOpVisitorMoveMetaspriteTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // TEST 1 (end-to-end): DSL `moveMetasprite(elephant)` in scene frame block
    // produces a play_frame body containing the frame-switch tokens.
    //
    // Confirms: ScriptOpVisitor.visitMoveMetasprite() delegates to
    // MetaspriteVisitor.generateMetaspriteFrameSwitch() — NOT a stub throw.
    // =========================================================================
    @Test
    fun `moveMetasprite DSL in scene frame generates frame-switch in play_frame`() {
        val gameIR =
            game("MoveMetaspriteTest") {
                    val elephant by metasprite {
                        frame {
                            tile(0, 0, 0)
                            tile(8, 0, 1)
                        }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c — brace-walk failed")

        // Verify all frame-switch tokens are present in play_frame body
        assertTrue(
            playFrameBody.contains("hiwater"),
            "Expected 'hiwater' in play_frame body. play_frame body:\n$playFrameBody",
        )
        assertTrue(
            playFrameBody.contains("move_metasprite_ex"),
            "Expected 'move_metasprite_ex' in play_frame body. play_frame body:\n$playFrameBody",
        )
        assertTrue(
            playFrameBody.contains("move_metasprite_flipy"),
            "Expected 'move_metasprite_flipy' in play_frame body. play_frame body:\n$playFrameBody",
        )
        assertTrue(
            playFrameBody.contains("hide_sprites_range"),
            "Expected 'hide_sprites_range' in play_frame body. play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 2 (end-to-end): play_frame body contains _rot & 0x3 switch condition
    // =========================================================================
    @Test
    fun `moveMetasprite DSL produces _rot flip-switch condition in play_frame`() {
        val gameIR =
            game("MoveMetaspriteFlipTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c")

        assertTrue(
            playFrameBody.contains("_rot"),
            "Expected '_rot' in play_frame body. play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 3 (end-to-end): play_frame body contains hide_sprites_range with
    // MAX_HARDWARE_SPRITES (Pitfall 1 mitigation)
    // =========================================================================
    @Test
    fun `moveMetasprite DSL emits hide_sprites_range with MAX_HARDWARE_SPRITES in play_frame`() {
        val gameIR =
            game("HiwaterTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c")

        assertTrue(
            playFrameBody.contains("hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)"),
            "Expected 'hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)' in play_frame body. " +
                "play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 4 (Phase 10.1 Plan 12 — D-Seed005 binder-prefix bug):
    //
    // Binder-supplied var-refs MUST be underscore-prefixed at the C-emission
    // boundary, matching the convention used by every other user-declared global
    // (`var elephantPosX by i16Var(0)` → emitted as `_elephantPosX` in main.c).
    //
    // Pre-Plan-12 the wiring `visitMoveMetasprite` → `op.posXVar` →
    // `MetaspriteVisitor.generateMetaspriteFrameSwitch(posXVar = op.posXVar)`
    // passed the raw `AssignableVar.name` through unchanged, producing
    // `bank1.c` references like `elephantPosX >> 4` (no underscore) while
    // `main.c` declared `INT16 _elephantPosX = 0u;`. SDCC reported
    // `Undefined identifier 'elephantPosX'` at link time.
    //
    // The Seed010NamespaceTest cases pass pre-prefixed names directly to the
    // visitor (`posXVar = "_elephant_posX"`), so they never exercised the
    // wiring layer's prefix obligation. This test plugs that gap by going
    // through the full DSL → IR → visitor → emitted-C pipeline with D-10
    // binders bound (the substrate Plan 03 added).
    //
    // Surfaced by Plan 12's `:metasprites-stress:buildRom` integration-evidence
    // gate (per RESEARCH §Synthetic ROM Verification). Locked here so the gap
    // cannot reopen.
    // =========================================================================
    @Test
    fun `moveMetasprite with D-10 binders underscore-prefixes user var refs in play_frame`() {
        val gameIR =
            game("BinderPrefixTest") {
                    var elephantPosX by io.github.gbkt.core.dsl.i16Var(1280)
                    var elephantPosY by io.github.gbkt.core.dsl.i16Var(1152)
                    var elephantIdx by io.github.gbkt.core.dsl.u8Var(0)
                    var elephantRot by io.github.gbkt.core.dsl.u8Var(0)
                    val elephant by metasprite {
                        posX(elephantPosX)
                        posY(elephantPosY)
                        idx(elephantIdx)
                        rot(elephantRot)
                        frame { tile(0, 0, 0) }
                    }
                    val playScene = scene("play") { frame { moveMetasprite(elephant) } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c")

        // Positive: all four binder-supplied var-refs MUST appear underscore-prefixed
        // (matching the `_elephantPosX` etc. declarations emitted in main.c).
        for (varRef in listOf("_elephantPosX", "_elephantPosY", "_elephantIdx", "_elephantRot")) {
            assertTrue(
                playFrameBody.contains(varRef),
                "Expected '$varRef' (underscore-prefixed binder ref) in play_frame body — " +
                    "the C-emission convention requires user-declared globals to use '_<name>' " +
                    "to match main.c declarations.\nplay_frame body:\n$playFrameBody",
            )
        }

        // Negative: the raw unprefixed binder name must NOT appear as a bare identifier in
        // the emitted C (it is the lexically-distinct undeclared identifier that triggered
        // SDCC `error 20: Undefined identifier 'elephantPosX'`). Use regex word boundaries
        // so the underscore-prefixed form does not match.
        for (rawName in listOf("elephantPosX", "elephantPosY", "elephantIdx", "elephantRot")) {
            val rawIdRegex = Regex("(?<![A-Za-z0-9_])$rawName(?![A-Za-z0-9_])")
            assertTrue(
                !rawIdRegex.containsMatchIn(playFrameBody),
                "Did NOT expect raw unprefixed '$rawName' as a bare identifier in play_frame " +
                    "body (would cause SDCC 'Undefined identifier' link error).\n" +
                    "play_frame body:\n$playFrameBody",
            )
        }
    }
}
