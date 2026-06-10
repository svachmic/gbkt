/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

// =============================================================================
// Phase 13.1 Plan 02 — Wave 0 RED TEST
//
// RomBanksValidationTest: Req #11 D-06 hard-error path
//
// This test is INTENTIONALLY RED until Plans 13.1-03 + 13.1-06 land:
//   - Plan 13.1-03: makes CartridgeConfig.romBanks nullable (Int? = null)
//                   and adds Cartridge enum (cartridge: Cartridge = Cartridge.ROM_ONLY)
//   - Plan 13.1-06: adds the derive+validate block in GBDKBackend.generate()
//                   which throws before the pipeline runs when romBanks < derived
//
// What this locks (D-06 contract):
//   When a game author sets an explicit romBanks value that is lower than what
//   BankingAnalysisPass assigns to the game's scenes, the pipeline MUST fail with
//   an actionable error message containing:
//     - "romBanks=" followed by the declared (too-small) count
//     - "too small" to clearly identify the problem
//     - guidance on what to set it to (the derived need)
//
// D-06 contract message shape (exact):
//   "romBanks=$declared too small; banking analysis needs $derived.
//    Set romBanks >= $derived or remove romBanks to auto-derive."
//
// Test approach: build a CartridgeConfig with explicit romBanks=1 (too small for
// a multi-scene game), run the analysis pipeline directly, and assert the D-06
// error is present in the diagnostics. Today the pass emits a generic bank-overflow
// error but NOT the D-06 "romBanks= too small" message shape — the test is RED
// until Plan 13.1-06 adds the pre-pipeline validation check.
//
// NOTE: This test lives in gbkt-analysis (not gbkt-backend-gbdk) because the
// derive+validate logic will be implemented here — in AnalysisConfig or an
// analysis-layer validate() function — to keep GBDKBackend thin. The gbkt-analysis
// module has no dep on gbkt-backend-gbdk (which would be circular).
// =============================================================================

import io.github.gbkt.analysis.DefaultPipeline
import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RED test — Req #11 D-06: explicit romBanks set below derived need → hard-error path.
 *
 * Today (pre-Plan-13.1-06): BankingAnalysisPass emits a generic bank-overflow diagnostic when
 * maxBanks=1 cannot hold the scene code. The diagnostic does NOT contain "romBanks=" or "too small"
 * — it's a generic overflow message.
 *
 * After Plan 13.1-06: the derive+validate block runs BEFORE AnalysisConfig is constructed. If
 * romBanks is non-null and below the derived need, a diagnostic with the D-06 message shape is
 * emitted (or an exception is thrown with that message). This test turns GREEN at that point.
 */
class RomBanksValidationTest {

    /**
     * Creates a SceneIR large enough to exceed the HOME bank budget, forcing BankingAnalysisPass to
     * assign it to a banked slot (bank >= 1).
     *
     * 1000 ops * 6 bytes/op = 6000 bytes > 4096 (HOME_BANK_SCENE_BUDGET).
     */
    private fun largeScene(id: String): SceneIR =
        SceneIR(id = id, enterOps = List(1000) { RawOp("/* op $it */") })

    /**
     * D-06 main contract: when romBanks is set explicitly below the derived need, the error
     * diagnostic MUST contain "romBanks=" and "too small".
     *
     * Fixture: 5 large scenes — BankingAnalysisPass will need multiple banks. CartridgeConfig
     * declares romBanks=1 — clearly below the derived need.
     *
     * RED because today the overflow diagnostic contains "overflow" or "bank" but NOT "romBanks="
     * or "too small". Plan 13.1-06 adds the validate step that emits the D-06 actionable message
     * before the pipeline runs.
     */
    @Test
    fun `explicit romBanks below derived need produces D-06 actionable error message`() {
        val scenes = (1..5).map { largeScene("scene$it") }
        // romBanks=1: explicitly too small for 5 large scenes.
        // After Plan 13.1-03, CartridgeConfig.romBanks will be Int? but the value
        // here is an explicit Int=1 override (not null/auto-derive).
        val game =
            GameIR(
                name = "TooFewBanksGame",
                config =
                    CartridgeConfig(
                        cartridge =
                            Cartridge
                                .MBC5, // MBC5: up to 256 banks; undersizing is an explicit error
                        romBanks = 1, // Explicit override below derived need → D-06 must fire
                    ),
                scenes = scenes,
            )

        // After Plan 13.1-06, the analysis config factory or a pre-pipeline validate()
        // call will embed the D-06 check. For now we invoke the pipeline directly to
        // assert against the diagnostic output shape.
        val analysisConfig = AnalysisConfig.fromCartridgeConfig(game.config)
        val pipeline = DefaultPipeline.create()
        val ctx =
            PassContext(
                game = game,
                profile = FakeProfile,
                config = analysisConfig,
                outputDirectory = null, // suppress BudgetAuditPass file writes (Pitfall 1)
            )
        val result = pipeline.execute(ctx)

        // Collect all diagnostics from the result (may be Failed or Success with errors)
        val allMessages =
            when (result) {
                is PassResult.Failed -> result.diagnostics.map { it.message }
                is PassResult.Success -> result.context.diagnostics.map { it.message }
            }
        val combinedMessages = allMessages.joinToString("\n")

        // D-06 contract: the error message MUST contain "romBanks=" and "too small".
        // RED today: the overflow message says something like "no bin found" or
        // "exceeds maxBanks" — not the D-06 actionable shape.
        assertTrue(
            combinedMessages.contains("romBanks="),
            "D-06: error message must contain 'romBanks=' to identify the parameter. " +
                "Got messages:\n$combinedMessages",
        )
        assertTrue(
            combinedMessages.contains("too small"),
            "D-06: error message must contain 'too small' to describe the problem. " +
                "Got messages:\n$combinedMessages",
        )
    }

    /**
     * D-06 message guidance: the error must tell the author what to do next.
     *
     * Locks the "Set romBanks >= $derived or remove romBanks to auto-derive." part of the D-06
     * message contract. A message without this guidance leaves the author unable to fix the problem
     * without guessing.
     *
     * RED today: same reason as the main D-06 test.
     */
    @Test
    fun `D-06 error message guides author with derived count or auto-derive hint`() {
        val scenes = (1..3).map { largeScene("s$it") }
        val game =
            GameIR(
                name = "UndersizedGame",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.MBC1,
                        romBanks = 1, // undersized explicit override
                    ),
                scenes = scenes,
            )

        val analysisConfig = AnalysisConfig.fromCartridgeConfig(game.config)
        val pipeline = DefaultPipeline.create()
        val ctx =
            PassContext(
                game = game,
                profile = FakeProfile,
                config = analysisConfig,
                outputDirectory = null,
            )
        val result = pipeline.execute(ctx)

        val allMessages =
            when (result) {
                is PassResult.Failed -> result.diagnostics.map { it.message }
                is PassResult.Success -> result.context.diagnostics.map { it.message }
            }
        val combinedMessages = allMessages.joinToString("\n")

        // The D-06 message must contain actionable guidance:
        //   "Set romBanks >= $derived or remove romBanks to auto-derive."
        assertTrue(
            combinedMessages.contains("auto-derive") || combinedMessages.contains("romBanks >="),
            "D-06: error message must guide author with auto-derive hint or derived count. " +
                "Got messages:\n$combinedMessages",
        )
    }

    /**
     * WR-03: when declared romBanks exceeds the cartridge type's cap, emit a distinct diagnostic
     * (not the generic D-06 "too small" message).
     *
     * MBC1 supports at most 32 banks. Declaring romBanks=512 must produce the cartridge-cap error
     * message referencing the cartridge name and its actual limit — not the generic "romBanks= too
     * small" D-06 message.
     */
    @Test
    fun `romBanks exceeding cartridge cap produces cartridge-cap error not generic D-06 message`() {
        val scenes = (1..2).map { largeScene("scene$it") }
        val game =
            GameIR(
                name = "OverCappedGame",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.MBC1,
                        romBanks = 512, // MBC1 max is 32 — 512 far exceeds the cap
                    ),
                scenes = scenes,
            )

        // AnalysisConfig.fromCartridgeConfig clamps to cartridge.maxRomBanks (32),
        // so the effective maxBanks is 32 even though the author wrote 512.
        val analysisConfig = AnalysisConfig.fromCartridgeConfig(game.config)
        val pipeline = DefaultPipeline.create()
        val ctx =
            PassContext(
                game = game,
                profile = FakeProfile,
                config = analysisConfig,
                outputDirectory = null,
            )
        val result = pipeline.execute(ctx)

        val allMessages =
            when (result) {
                is PassResult.Failed -> result.diagnostics.map { it.message }
                is PassResult.Success -> result.context.diagnostics.map { it.message }
            }
        val combinedMessages = allMessages.joinToString("\n")

        // WR-03: must reference the cartridge type name and its cap
        assertTrue(
            combinedMessages.contains("MBC1"),
            "WR-03: cartridge-cap error must name the cartridge type. Got:\n$combinedMessages",
        )
        assertTrue(
            combinedMessages.contains("32"),
            "WR-03: cartridge-cap error must state the cartridge's max banks (32). Got:\n$combinedMessages",
        )
        // Must NOT be the generic D-06 "too small" message
        assertTrue(
            !combinedMessages.contains("too small"),
            "WR-03: over-cap error must NOT be the generic 'too small' D-06 message. Got:\n$combinedMessages",
        )
    }
}
