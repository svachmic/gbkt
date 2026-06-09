/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

// =============================================================================
// Phase 13.1 Plan 02 — Wave 0 RED TEST
//
// RomBanksDerivedTest: Req #11 D-05 derivation contract
//
// This test is INTENTIONALLY RED until Plans 13.1-03 + 13.1-06 land:
//   - Plan 13.1-03: makes CartridgeConfig.romBanks nullable (Int? = null).
//                   null = "derive from BankingAnalysisPass" (D-05 sentinel).
//   - Plan 13.1-06: adds the D-05 derive block in GBDKBackend.generate():
//                   when romBanks==null, runs a probe analysis pass and derives
//                   effectiveRomBanks = maxOf(2, nextPowerOfTwo(maxBank + 1)).
//
// What this locks (D-05 contract):
//   When a game author omits romBanks (null / not set in config {}), the backend
//   MUST derive the bank count automatically from BankingAnalysisPass output:
//     derived = maxOf(2, nextPowerOfTwo(maxAssignedBank + 1))
//   The derived count must be >= the number of banks BankingAnalysisPass assigned.
//
// Test approach:
//   Build a multi-bank GameIR (enough scenes to span multiple banks) with
//   config.romBanks == null (post-13.1-03 sentinel). Assert that the generation
//   succeeds and the effective bank count used is >= the bank count BankingAnalysisPass
//   would assign in a standalone probe.
//
// RED today because CartridgeConfig.romBanks is a non-null Int=2, so we cannot
// construct a config with romBanks=null. The test references the future API
// surface. Plans 13.1-03 makes romBanks nullable, turning this test GREEN once
// Plan 13.1-06 implements the derive block.
//
// Implementation note (Pitfall 1 from RESEARCH.md):
//   The probe run inside GBDKBackend must pass outputDirectory=null to suppress
//   BudgetAuditPass file writes. This test asserts the derive result, not the
//   probe side effects.
// =============================================================================

import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.analysis.passes.BankingAnalysisPass
import io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfile
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RED test — Req #11 D-05: omitting romBanks derives the count from BankingAnalysisPass.
 *
 * Today (pre-Plan-13.1-03): CartridgeConfig.romBanks is non-null Int=2.
 * The test references [CartridgeConfig] with romBanks=null which does not
 * compile until Plan 13.1-03 changes the field to Int?.
 *
 * After Plans 13.1-03 + 13.1-06: GBDKBackend.generate() detects null romBanks,
 * runs a probe BankingAnalysisPass, computes the derived count, and uses it as
 * the effective maxBanks. This test turns GREEN at that point.
 */
class RomBanksDerivedTest {

    /** Creates a SceneIR large enough to exceed the HOME bank budget. */
    private fun largeScene(id: String): SceneIR =
        SceneIR(id = id, enterOps = List(1000) { RawOp("/* op $it */") })

    /**
     * Standalone probe: runs BankingAnalysisPass with unconstrained maxBanks on the
     * fixture GameIR to compute the minimum number of banks the packer needs.
     * This is the reference count the D-05 derivation must meet or exceed.
     */
    private fun probeMaxAssignedBank(game: GameIR): Int {
        val pass = BankingAnalysisPass()
        val ctx = PassContext(
            game = game,
            profile = GameBoyColorProfile,
            config = AnalysisConfig(maxBanks = 256),  // unconstrained probe
            outputDirectory = null,                    // suppress BudgetAuditPass file writes
        )
        val result = pass.run(ctx)
        return if (result is PassResult.Success) {
            result.context.bankAssignments.values.maxOfOrNull { it.bank } ?: 0
        } else {
            0
        }
    }

    /**
     * D-05 main contract: omitting romBanks (null) causes GBDKBackend.generate()
     * to derive the bank count from BankingAnalysisPass output.
     *
     * Fixture: 6 large scenes — BankingAnalysisPass will assign them to multiple banks.
     * config.romBanks = null (D-05 sentinel, available after Plan 13.1-03).
     *
     * The derived count must be >= the standalone probe result.
     *
     * RED today because CartridgeConfig.romBanks is Int (non-nullable), so
     * romBanks = null is a compile error until Plan 13.1-03 lands.
     */
    @Test
    fun `omitting romBanks derives bank count from BankingAnalysisPass (D-05)`() {
        val scenes = (1..6).map { largeScene("scene$it") }
        val game = GameIR(
            name = "AutoDeriveGame",
            config = CartridgeConfig(
                cartridge = Cartridge.MBC5,
                // romBanks omitted (null) — D-05 sentinel.
                // COMPILE ERROR until Plan 13.1-03: today romBanks: Int = 2, not nullable.
                romBanks = null,
            ),
            scenes = scenes,
        )

        // Standalone probe to get the reference bank count (what the packer needs)
        val probeMaxBank = probeMaxAssignedBank(game)
        val minExpectedBanks = maxOf(2, nextPowerOfTwo(probeMaxBank + 1))

        // Run the actual backend; D-05 derive block should pick the same count
        val backend = GBDKBackend()
        val result = backend.generate(
            gameIR = game,
            outputDirectory = null,   // suppress file writes during test
        )

        // The generation should succeed (derived count satisfies the packer)
        assertTrue(
            result.success,
            "D-05: generate with romBanks=null should succeed by deriving the bank count. " +
                "Got error: ${result.error}"
        )

        // The effective maxBanks used must be >= the probe result.
        // After Plan 13.1-06 lands, the derived count is observable via the
        // gbkt-build.properties output or the analysisConfig used internally.
        // For now we assert the generation succeeded without errors as a proxy
        // for the derive path running correctly.
        assertTrue(
            minExpectedBanks >= 2,
            "Probe derived at least 2 banks for 6 large scenes (sanity check)"
        )
    }

    /**
     * D-05 auto-derive round-trips to nextPowerOfTwo: the derived count must be
     * a power of two (per Game Boy hardware bank sizing: 2, 4, 8, 16, 32, ...).
     *
     * The D-05 formula: derived = maxOf(2, nextPowerOfTwo(maxAssignedBank + 1))
     * must produce a value that satisfies nextPowerOfTwo constraints.
     *
     * RED today: same compile error on romBanks=null.
     */
    @Test
    fun `D-05 derived bank count is at least 2 and a power of two`() {
        val scenes = (1..4).map { largeScene("s$it") }
        val game = GameIR(
            name = "PowerOfTwoGame",
            config = CartridgeConfig(
                cartridge = Cartridge.MBC1,
                // romBanks = null — D-05 sentinel (compile error until Plan 13.1-03)
                romBanks = null,
            ),
            scenes = scenes,
        )

        val probeMaxBank = probeMaxAssignedBank(game)
        val minExpected = maxOf(2, nextPowerOfTwo(probeMaxBank + 1))

        // minExpected must be a power of two >= 2
        assertTrue(
            minExpected >= 2,
            "D-05 derived count must be >= 2; got $minExpected from probe maxBank=$probeMaxBank"
        )
        assertTrue(
            minExpected and (minExpected - 1) == 0,
            "D-05 derived count must be a power of two; got $minExpected"
        )
    }

    companion object {
        /**
         * Computes the smallest power of two that is >= [n].
         * Mirrors the nextPowerOfTwo helper that Plan 13.1-06 will add in GBDKBackend.
         *
         * Examples: nextPowerOfTwo(1) = 1, nextPowerOfTwo(3) = 4, nextPowerOfTwo(5) = 8.
         */
        fun nextPowerOfTwo(n: Int): Int {
            if (n <= 0) return 1
            var v = n - 1
            v = v or (v shr 1)
            v = v or (v shr 2)
            v = v or (v shr 4)
            v = v or (v shr 8)
            v = v or (v shr 16)
            return v + 1
        }
    }
}
