/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.analysis.passes.BankingAnalysisPass
import io.github.gbkt.backend.gbdk.profiles.GameBoyProfile
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// MBC TYPE PROBE TESTS (D-12 regression contract)
//
// Per-example MBC-type assertion probe that runs BankingAnalysisPass directly
// (no GenerateCTask invocation, no actual Gradle build) and asserts the
// expected bank-assignment shape for each example.
//
// Contract per D-12 (revised after 09.1-04 gap-closure 2026-05-14, Option B):
//   - Single-scene games with NON-TRIVIAL but HOME-fitting op counts
//     (simple-physics ~30 ops = 180 bytes, pong ~50 ops = 300 bytes) are now
//     assigned bank 0 (HOME) by the 09.1-04 fast-path, NOT bank 1. This
//     prevents spurious MBC5 upgrade when the user's DSL says ROM_ONLY.
//   - Multi-scene banked games (rpg-lite, breakout) bypass the fast-path
//     (scenes.size > 1 static guard) and continue to get bank >= 1 via FFD.
//   - The D-10 zero-op filter only excludes scenes whose enterOps + frameOps
//     + exitOps are ALL empty. Scenes with even one op survive the filter.
//
// Fixtures mirror the GameIR *shape* of each example (scene count, op
// density) with realistic op counts so the assertions have real weight.
// Importing the actual example modules would create a circular test
// dependency (`gbkt-backend-gbdk` test classpath depending on
// `gbkt-examples/*`), so the fixtures are sized to match the source-of-truth
// op counts visible in each example's DSL file.
// =============================================================================

class MbcTypeProbeTest {

    private val pass = BankingAnalysisPass()

    /** Creates a SceneIR with [enterN] enter ops and [frameN] frame ops to simulate code bulk. */
    private fun sceneWithOps(id: String, enterN: Int, frameN: Int = 0, exitN: Int = 0): SceneIR =
        SceneIR(
            id = id,
            enterOps = List(enterN) { RawOp("/* enter $it */") },
            frameOps = List(frameN) { RawOp("/* frame $it */") },
            exitOps = List(exitN) { RawOp("/* exit $it */") },
        )

    private fun makeContext(game: GameIR, maxBanks: Int = 32): PassContext =
        PassContext(
            game = game,
            profile = GameBoyProfile,
            config = AnalysisConfig(maxBanks = maxBanks),
        )

    // -------------------------------------------------------------------------
    // simple-physics: single scene, NON-TRIVIAL op count
    // -------------------------------------------------------------------------

    /**
     * simple-physics shape: single scene "play" with realistic op counts. The actual DSL has:
     * - enter: 5 ops (showSprites + 4 var initializations)
     * - frame: ~25 ops (8 `whenever` blocks each lowering to >= 1 op, plus 4 increments and a
     *   moveTo) — see `SimplePhysics.kt` and the IR-level test `SimplePhysicsIRTest.kt:71-77` which
     *   only asserts non-empty (does not count). We use conservative lower bounds: 5 enter + 25
     *   frame = 30 ops.
     *
     * Per 09.1-04 (gap-closure 2026-05-14, Option B): 30 ops * 6 bytes/op = 180 bytes, well under
     * HOME_BANK_SCENE_BUDGET = 4096. The 09.1-04 fast-path assigns this single scene to bank 0
     * (HOME) instead of bank 1. This prevents the spurious MBC5 upgrade that occurred when
     * simple-physics's `cartridge = Cartridge.ROM_ONLY` DSL declaration was silently overridden at
     * build time by the CompileRomTask `readMbcType` path.
     */
    private fun simplePhysicsLikeGameIR(): GameIR =
        GameIR(
            name = "simple-physics-like",
            scenes = listOf(sceneWithOps("play", enterN = 5, frameN = 25)),
            startScene = "play",
        )

    @Test
    fun `simple-physics-like game stays in bank 0 (HOME fast-path per plan 09_1-04)`() {
        val game = simplePhysicsLikeGameIR()
        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        assertEquals(
            1,
            assignments.size,
            "Per D-12 (simple-physics) + 09.1-04 fast-path: single-scene game must produce " +
                "exactly one bank assignment. Got: $assignments",
        )
        val playSlot = assignments["play"]
        assertEquals(
            0,
            playSlot?.bank,
            "Per D-12 (simple-physics) + 09.1-04 fast-path: single-scene game fitting HOME " +
                "budget must be assigned bank 0 (HOME). Got: $assignments",
        )
    }

    // -------------------------------------------------------------------------
    // pong: single scene with ~50 ops — single bank assignment
    // -------------------------------------------------------------------------

    /**
     * pong shape: single "gameplay" scene with ~50 ops (ball physics, paddle input, score, scene
     * transitions). Pong.kt is 268 lines of DSL — well above the 10-op floor. We use 50 ops as a
     * conservative lower bound.
     *
     * Per 09.1-04 (gap-closure 2026-05-14, Option B): 50 ops * 6 bytes/op = 300 bytes, well under
     * HOME_BANK_SCENE_BUDGET = 4096. The 09.1-04 fast-path assigns this single scene to bank 0
     * (HOME). Per 09.1-04: multi-scene fixtures bypass the single-scene fast-path via the
     * scenes.size == 1 static guard; bin-packer path locks bank >= 1.
     */
    private fun pongLikeGameIR(): GameIR =
        GameIR(
            name = "pong-like",
            scenes = listOf(sceneWithOps("gameplay", enterN = 10, frameN = 40)),
            startScene = "gameplay",
        )

    @Test
    fun `pong-like game stays in bank 0 (HOME fast-path per plan 09_1-04)`() {
        val game = pongLikeGameIR()
        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        assertEquals(
            1,
            assignments.size,
            "Per D-12 (pong) + 09.1-04 fast-path: single-scene game must produce exactly one " +
                "bank assignment. Got: $assignments",
        )
        val gameplaySlot = assignments["gameplay"]
        assertEquals(
            0,
            gameplaySlot?.bank,
            "Per D-12 (pong) + 09.1-04 fast-path: single-scene game fitting HOME budget must " +
                "be assigned bank 0 (HOME). Got: $assignments",
        )
    }

    // -------------------------------------------------------------------------
    // rpg-lite: multi-scene with high op count — requires banking (MBC5)
    // -------------------------------------------------------------------------

    /**
     * rpg-lite shape: multiple scenes with substantial op counts. title (50) + heroSelect (100) +
     * gameplay (500) + battle (800) + gameover (50) = 1500 ops At 6 bytes/op: 1500 * 6 = 9000 bytes
     * → packed into bank 1 with locality. rpg-lite also has dialog/menu/inventory scenes adding to
     * the bank pressure.
     */
    private fun rpgLiteLikeGameIR(): GameIR =
        GameIR(
            name = "rpg-lite-like",
            scenes =
                listOf(
                    sceneWithOps("title", enterN = 50),
                    sceneWithOps("hero-select", enterN = 100),
                    sceneWithOps("gameplay", enterN = 500),
                    sceneWithOps("battle", enterN = 800),
                    sceneWithOps("inventory", enterN = 200),
                    sceneWithOps("gameover", enterN = 50),
                    sceneWithOps("victory", enterN = 50),
                ),
            startScene = "title",
        )

    @Test
    fun `rpg-lite-like game requires banking (MBC5)`() {
        // Per 09.1-04: multi-scene fixtures bypass the single-scene fast-path via the
        // scenes.size == 1 static guard; bin-packer path locks bank >= 1.
        val game = rpgLiteLikeGameIR()
        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        assertTrue(
            assignments.isNotEmpty(),
            "Per D-12 (rpg-lite): multi-scene banked game must require MBC5. Got: $assignments",
        )
        assertTrue(
            assignments.values.any { it.bank >= 1 },
            "Per D-12 (rpg-lite): at least one scene must be in bank >= 1 (MBC5 path). Got: $assignments",
        )
    }

    // -------------------------------------------------------------------------
    // breakout: multi-scene with medium op count — requires banking (MBC5)
    // -------------------------------------------------------------------------

    /**
     * breakout shape: multiple scenes with medium op counts. title (30) + gameplay (600) + gameover
     * (30) = 660 ops gameplay at 600 ops * 6 bytes/op = 3600 bytes → fits bank 1, non-empty
     * assignments.
     */
    private fun breakoutLikeGameIR(): GameIR =
        GameIR(
            name = "breakout-like",
            scenes =
                listOf(
                    sceneWithOps("title", enterN = 30),
                    sceneWithOps("gameplay", enterN = 600),
                    sceneWithOps("gameover", enterN = 30),
                ),
            startScene = "title",
        )

    @Test
    fun `breakout-like game requires banking (MBC5)`() {
        // Per 09.1-04: multi-scene fixtures bypass the single-scene fast-path via the
        // scenes.size == 1 static guard; bin-packer path locks bank >= 1.
        val game = breakoutLikeGameIR()
        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        assertTrue(
            assignments.isNotEmpty(),
            "Per D-12 (breakout): multi-scene banked game must require MBC5. Got: $assignments",
        )
        assertTrue(
            assignments.values.any { it.bank >= 1 },
            "Per D-12 (breakout): at least one scene must be in bank >= 1 (MBC5 path). Got: $assignments",
        )
    }

    // =============================================================================
    // Pipeline-output probe tests (Plan 05) — assert GBDKPipeline.generate
    // honors the bank-0 fast-path by omitting bank1.c emission for
    // single-scene-fits-HOME games (D-10 / D-03 / D-12 extended scope).
    // =============================================================================

    /**
     * Mirrors GBDKBackend.applyAnnotations:177-183 — annotates scene bankSlots from [assignments].
     *
     * GBDKBackend.applyAnnotations is private; the pipeline-output tests inline this annotation
     * step rather than relaxing visibility. The helper is intentionally kept minimal (scene branch
     * only — actors not needed for these tests).
     */
    private fun annotateWithBanks(game: GameIR, assignments: Map<String, BankSlot>): GameIR =
        game.copy(
            scenes =
                game.scenes.map { scene ->
                    scene.copy(bankSlot = assignments[scene.id] ?: scene.bankSlot)
                }
        )

    @Test
    fun `simple-physics-like pipeline output omits bank1c (Plan 05 + 09_1-04 fast-path)`() {
        val game = simplePhysicsLikeGameIR()
        val passResult = pass.run(makeContext(game, maxBanks = 32))
        assertIs<PassResult.Success>(passResult)

        val annotated = annotateWithBanks(game, passResult.context.bankAssignments)
        val output = GBDKPipeline().generate(annotated).files

        // The contract: bank1.c must NOT be in the output map for single-scene-fits-HOME games.
        // The scene functions must be folded into main.c (HOME) as non-BANKED definitions.
        val bank1Body = output["bank1.c"]
        assertTrue(
            bank1Body == null,
            "Per D-10 + 09.1-04 fast-path + Plan 05: single-scene-fits-HOME game must NOT emit " +
                "bank1.c at all — the file should be absent from the output map. " +
                "Got bank1.c lines(0..4)=${bank1Body?.lines()?.take(5)}",
        )

        val mainC = output["main.c"]
        assertNotNull(mainC, "Per D-10 + Plan 05: main.c must be present")
        // Scene function DEFINITION (not just a call) must be in main.c — proves folding direction
        assertTrue(
            mainC.contains("void play_enter"),
            "Per D-10 + 09.1-04 fast-path + Plan 05: scene function definition 'void play_enter' " +
                "must be folded into main.c (HOME) when all scenes are bank 0. " +
                "main.c does not contain 'void play_enter'.",
        )
    }

    @Test
    fun `rpg-lite-like pipeline output still emits bank1c (09_1-04 fast-path negative control)`() {
        val game = rpgLiteLikeGameIR()
        val passResult = pass.run(makeContext(game, maxBanks = 32))
        assertIs<PassResult.Success>(passResult)

        val annotated = annotateWithBanks(game, passResult.context.bankAssignments)
        val output = GBDKPipeline().generate(annotated).files

        val bank1Body = output["bank1.c"]
        assertNotNull(
            bank1Body,
            "Per D-04 + 09.1-04 fast-path negative control: multi-scene banked game must still " +
                "emit bank1.c. Got: ${output.keys}",
        )
        assertTrue(
            bank1Body.contains("#pragma bank 1"),
            "Per D-04 + 09.1-04 fast-path negative control: bank1.c must contain '#pragma bank 1' " +
                "for multi-scene banked games. Got bank1.c lines(0..4)=${bank1Body.lines().take(5)}",
        )
    }

    @Test
    fun `breakout-like pipeline output still emits bank1c (09_1-04 fast-path negative control)`() {
        val game = breakoutLikeGameIR()
        val passResult = pass.run(makeContext(game, maxBanks = 32))
        assertIs<PassResult.Success>(passResult)

        val annotated = annotateWithBanks(game, passResult.context.bankAssignments)
        val output = GBDKPipeline().generate(annotated).files

        val bank1Body = output["bank1.c"]
        assertNotNull(
            bank1Body,
            "Per D-04 + 09.1-04 fast-path negative control: multi-scene banked game must still " +
                "emit bank1.c. Got: ${output.keys}",
        )
        assertTrue(
            bank1Body.contains("#pragma bank 1"),
            "Per D-04 + 09.1-04 fast-path negative control: bank1.c must contain '#pragma bank 1' " +
                "for multi-scene banked games (breakout). Got bank1.c lines(0..4)=${bank1Body.lines().take(5)}",
        )
    }

    // -------------------------------------------------------------------------
    // Zero-op filter coverage — locks the D-10 filter semantics
    // -------------------------------------------------------------------------

    /**
     * A scene with ALL three op lists empty (enterOps, frameOps, exitOps) must be filtered out by
     * D-10 — it produces no banked C and so needs no bank slot. This is the only shape the filter
     * affects; any non-empty op list survives.
     */
    @Test
    fun `D-10 filter skips all-empty scenes`() {
        val game =
            GameIR(
                name = "all-empty",
                scenes = listOf(sceneWithOps("dead", enterN = 0, frameN = 0, exitN = 0)),
                startScene = "dead",
            )
        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        assertTrue(
            result.context.bankAssignments.isEmpty(),
            "Per D-10: scenes with zero enter+frame+exit ops must be filtered out. Got: " +
                "${result.context.bankAssignments}",
        )
    }
}
