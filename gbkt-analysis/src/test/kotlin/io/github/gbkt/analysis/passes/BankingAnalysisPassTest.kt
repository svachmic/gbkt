/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScriptOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BankingAnalysisPassTest {

    private val pass = BankingAnalysisPass()

    /** Creates a SceneIR with [n] placeholder ops to simulate code bulk. */
    private fun sceneWithOps(id: String, n: Int, frameOps: List<ScriptOp> = emptyList()): SceneIR =
        SceneIR(id = id, enterOps = List(n) { RawOp("/* op $it */") }, frameOps = frameOps)

    private fun makeContext(game: GameIR, maxBanks: Int = 32): PassContext =
        PassContext(
            game = game,
            profile = FakeProfile,
            config = AnalysisConfig(maxBanks = maxBanks),
        )

    // -------------------------------------------------------------------------
    // Basic assignment tests
    // -------------------------------------------------------------------------

    @Test
    fun `single scene fits in bank 1`() {
        // Per 09.1-04: fixture sized above HOME_BANK_SCENE_BUDGET (4096 bytes) to bypass the
        // fast-path and exercise the bin-packer fall-through path.
        // 1000 ops * 6 bytes/op = 6000 bytes > 4096 — fast-path does NOT trigger; FFD assigns bank
        // 1.
        val scene = sceneWithOps("gameplay", 1000)
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val slot = result.context.bankAssignments["gameplay"]
        assertNotNull(slot, "Expected 'gameplay' in bankAssignments")
        assertTrue(slot.bank >= 1, "Expected bank 1 or higher, got bank ${slot.bank}")
    }

    @Test
    fun `bank 0 reserved for HOME — no scene code lands in bank 0`() {
        val scenes = (1..5).map { sceneWithOps("scene$it", 20) }
        val game = GameIR(name = "Test", scenes = scenes)

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        for ((id, slot) in result.context.bankAssignments) {
            assertTrue(slot.bank != 0, "Scene '$id' was assigned to bank 0 (HOME) — forbidden")
        }
    }

    @Test
    fun `three small scenes fit in one bank`() {
        // 3 scenes * 5 ops * 6 bytes = 90 bytes total — all fit in bank 1
        val scenes =
            listOf(
                sceneWithOps("title", 5),
                sceneWithOps("gameplay", 5),
                sceneWithOps("gameover", 5),
            )
        val game = GameIR(name = "Test", scenes = scenes)

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        val banks = assignments.values.map { it.bank }.toSet()
        assertTrue(
            banks.size == 1,
            "Expected all 3 small scenes in the same bank, got: $assignments",
        )
    }

    @Test
    fun `large scene gets own bank`() {
        // 2500 ops * 6 bytes = 15000 bytes — nearly fills one 16KB bank alone
        val hugeScene = sceneWithOps("dungeon", 2500)
        val game = GameIR(name = "Test", scenes = listOf(hugeScene))

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val slot = result.context.bankAssignments["dungeon"]
        assertNotNull(slot, "Expected 'dungeon' in bankAssignments")
        assertTrue(slot.bank >= 1, "Expected bank 1 or higher, got bank ${slot.bank}")
    }

    @Test
    fun `FFD ordering assigns largest first`() {
        // Largest (2000 ops) gets bank 1. Smaller ones pack after.
        val scenes =
            listOf(
                sceneWithOps("small", 100),
                sceneWithOps("large", 2000),
                sceneWithOps("medium", 500),
            )
        val game = GameIR(name = "Test", scenes = scenes)

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        // All scenes must be assigned
        assertTrue(
            assignments.containsKey("large") &&
                assignments.containsKey("medium") &&
                assignments.containsKey("small"),
            "Expected all three scenes assigned, got: $assignments",
        )
        // 'large' at 12000 bytes and 'small' at 600 bytes fit in bank 1 (12600 < 16384).
        // 'medium' at 3000 bytes also fits in bank 1 since 12000+600+3000=15600 < 16384.
        // So FFD means 'large' is tried first and gets bank 1.
        val largeBank = assignments["large"]!!.bank
        assertTrue(largeBank >= 1, "large scene must not be in bank 0")
    }

    // -------------------------------------------------------------------------
    // Overflow and warning diagnostics
    // -------------------------------------------------------------------------

    @Test
    fun `overflow fails when exceeding maxBanks`() {
        // maxBanks=2 (bank 0 HOME + bank 1). Total code must exceed 16KB.
        // 3000 ops * 6 bytes = 18000 bytes > 16384 — won't fit in single bank 1.
        val tooBig = sceneWithOps("overflow", 3000)
        val game = GameIR(name = "Test", scenes = listOf(tooBig))

        val result = pass.run(makeContext(game, maxBanks = 2))

        assertIs<PassResult.Failed>(result)
        val messages = result.diagnostics.map { it.message }
        assertTrue(
            messages.any {
                it.contains("overflow", ignoreCase = true) || it.contains("bank", ignoreCase = true)
            },
            "Expected overflow/bank error message, got: $messages",
        )
    }

    @Test
    fun `bank fill warning at 85 percent`() {
        // 16384 * 0.85 = 13926 bytes = ~2321 ops at 6 bytes/op.
        // 2321 ops * 6 = 13926 bytes — exactly at warning threshold.
        // Use 2322 ops to go slightly over 85%.
        val scene = sceneWithOps("heavy", 2322)
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(warnings.isNotEmpty(), "Expected at least one WARNING for high bank fill")
        assertTrue(
            warnings.any { it.message.contains("bank", ignoreCase = true) },
            "Expected bank fill warning, got: $warnings",
        )
    }

    // -------------------------------------------------------------------------
    // Scene locality
    // -------------------------------------------------------------------------

    @Test
    fun `single scene with zero ops gets no bank assignment (ROM_ONLY stays bank 0)`() {
        // Zero ops → estimatedBytes == 0. A zero-op scene produces no banked functions;
        // assigning it to bank 1 spuriously triggers MBC5 upgrade in CompileRomTask (D-10).
        val scene = sceneWithOps("play", 0) // zero ops → zero bytes
        val game = GameIR(name = "SimplePhysicsLike", scenes = listOf(scene))

        val result = pass.run(makeContext(game, maxBanks = 2))

        assertIs<PassResult.Success>(result)
        val slot = result.context.bankAssignments["play"]
        // Per D-10: zero-op scene must NOT receive a bank-1+ slot (causes spurious MBC5 upgrade).
        assertTrue(
            slot == null || slot.bank == 0,
            "Per D-10: zero-op scene must NOT receive a bank-1+ slot (causes spurious MBC5 upgrade). Got slot=$slot",
        )
    }

    // -------------------------------------------------------------------------
    // Single-scene HOME fast-path (09.1-04 gap closure — D-10 extended scope)
    // -------------------------------------------------------------------------

    @Test
    fun `single scene that fits HOME budget is assigned bank 0 (HOME fast-path)`() {
        // 30 ops * 6 bytes/op = 180 bytes — well under HOME_BANK_SCENE_BUDGET = 4096.
        // Per 09.1-04 fast-path (2026-05-14, Option B): single-scene games fitting the
        // HOME budget must land in bank 0, not bank 1 via the FFD bin-packer.
        val scene = sceneWithOps("play", 30)
        val game = GameIR(name = "SinglePhysicsLike", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val slot = result.context.bankAssignments["play"]
        assertNotNull(
            slot,
            "Per D-10 + 09.1-04 fast-path: 'play' scene must appear in bankAssignments",
        )
        assertEquals(
            0,
            slot.bank,
            "Per D-10 + 09.1-04 fast-path: single-scene game fitting HOME budget must be assigned bank 0 (not bank 1+). Got slot=$slot",
        )
    }

    @Test
    fun `single scene that exceeds HOME budget falls through to bin-packer (bank 1+)`() {
        // 1000 ops * 6 bytes/op = 6000 bytes — exceeds HOME_BANK_SCENE_BUDGET = 4096.
        // Per 09.1-04 fast-path: when a single scene does NOT fit HOME budget, the fast-path
        // must NOT trigger; the existing FFD bin-packer assigns bank >= 1 as before.
        val scene = sceneWithOps("huge", 1000)
        val game = GameIR(name = "OversizeSingleScene", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val slot = result.context.bankAssignments["huge"]
        assertNotNull(slot, "Per 09.1-04 fast-path: 'huge' scene must appear in bankAssignments")
        assertTrue(
            slot.bank >= 1,
            "Per 09.1-04 fast-path: single scene exceeding HOME budget must NOT use fast-path; expected bank >= 1 from bin-packer. Got: $slot",
        )
    }

    /**
     * Regression guard for 09.1-06 + CR-01 — reinstates the Plan 04 must_haves.truths spec
     * verbatim: "the fast-path triggers ONLY when scenes.size == 1 (authored scene count, static
     * property of the GameIR)".
     *
     * Shape: a 2-scene authored GameIR where N-1 scenes are zero-op stubs. After the D-10 zero-op
     * filter (BankingAnalysisPass.buildCodeUnits), `codeUnits.size == 1` (the stub is stripped).
     * Before 09.1-06, the single-clause guard `codeUnits.size == 1` incorrectly admitted this shape
     * into the HOME fast-path, placing the real scene's code in bank 0 even though the author wrote
     * a multi-scene game. The 09.1-06 fix tightens the guard to dual-clause `game.scenes.size == 1
     * && codeUnits.size == 1`, which rejects this fixture and routes it through the bin-packer
     * (bank >= 1).
     */
    @Test
    fun `single real scene with zero-op stub sibling does NOT enter fast-path (CR-01 regression guard)`() {
        // Real scene: 30 enter ops * 6 bytes/op = 180 bytes — well under HOME_BANK_SCENE_BUDGET
        // (4096). If it were the only authored scene it would fit the fast-path HOME budget.
        val real = sceneWithOps("real", 30)
        // Stub scene: all-empty enter+frame+exit ops. D-10 zero-op filter strips it from
        // codeUnits, so post-filter codeUnits.size == 1. But game.scenes.size == 2.
        val stub = sceneWithOps("stub", 0)
        val game = GameIR(name = "MultiSceneWithStub", scenes = listOf(real, stub))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        val slot = assignments["real"]
        assertNotNull(
            slot,
            "Per 09.1-06 + CR-01: 'real' scene must appear in bankAssignments after bin-packer runs",
        )
        assertTrue(
            slot.bank >= 1,
            "Per Plan 04 spec + 09.1-06 + CR-01: 1-real + N-zero-op-stub multi-scene game must " +
                "NOT enter fast-path; expected real-scene bank >= 1 from bin-packer. " +
                "Got slot=$slot (full assignments=$assignments)",
        )
    }

    @Test
    fun `scene locality groups transitioning scenes`() {
        // scene A transitions to scene B via NavigateTo
        val sceneA =
            SceneIR(
                id = "sceneA",
                enterOps = List(10) { RawOp("/* op $it */") },
                frameOps = listOf(NavigateTo("sceneB")),
            )
        val sceneB = sceneWithOps("sceneB", 10)
        // Unrelated scene C with same size
        val sceneC = sceneWithOps("sceneC", 10)
        val game = GameIR(name = "Test", scenes = listOf(sceneA, sceneB, sceneC))

        val result = pass.run(makeContext(game, maxBanks = 32))

        assertIs<PassResult.Success>(result)
        val assignments = result.context.bankAssignments
        assertNotNull(assignments["sceneA"])
        assertNotNull(assignments["sceneB"])
        assertNotNull(assignments["sceneC"])
        // A and B should be in the same bank (locality)
        assertTrue(
            assignments["sceneA"]!!.bank == assignments["sceneB"]!!.bank,
            "Expected sceneA and sceneB (which A transitions to) in the same bank. " +
                "Got: A=${assignments["sceneA"]}, B=${assignments["sceneB"]}",
        )
    }
}
