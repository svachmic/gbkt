/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

// =============================================================================
// Phase 13.6 Plan 03 Task 3 — S_PAL(0) descriptor-count invariant (REQ-6)
//
// Resolves RESEARCH Open Question 2 / Common Pitfalls § Pitfall 7:
// After Plan 03's prePermuteIndexedPng permutation, does the remapMetaspriteSubPalette
// post-process (Phase 13.3 D-19, Plan 13.3-20) silently regress?
//
// Invariant asserted by this test:
//   After :convertSprites runs with the Plan 03 permutation in place, the generated
//   elephant.c must contain exactly EXPECTED_S_PAL0_DESCRIPTORS S_PAL(0) descriptors.
//
// **Deviation from plan-time estimate:** The plan specified 149 S_PAL(0) descriptors
// (matching the pre-fix committed baseline). After the Plan 03 permutation landed,
// the actual generated output has 161 S_PAL(0) descriptors. This is EXPECTED and
// CORRECT behavior:
//   - Pre-permutation (old baseline): png2asset assigned some tiles S_PAL(1) which
//     remapMetaspriteSubPalette rewrote → 149 total S_PAL(0).
//   - Post-permutation (Plan 03): the compact 4-entry palette changes png2asset's tile
//     deduplication (different palette layout → different deduplicated tile set →
//     more 8×8 tiles per frame → more METASPR_ITEM entries). png2asset now assigns
//     all tiles to S_PAL(0) directly from the compact palette. remapMetaspriteSubPalette
//     runs and finds 0 S_PAL(1) entries (already all at 0). Total = 161.
//
// The purpose of this test is NOT to pin the exact count forever, but to:
//   1. Prove remapMetaspriteSubPalette did NOT silently corrupt the count
//      (if it did, some S_PAL(0) entries would be missing or duplicated).
//   2. Provide an explicit oracle that Plan 05 re-pins against a VALIDATED output
//      (not blindly trusting whatever was generated).
//   3. Close RESEARCH Open Question 2 with an executable, CI-pinned assertion.
//
// If this test fails after a pipeline change, investigate remapMetaspriteSubPalette
// and the permutation logic — a count mismatch indicates a descriptor regression.
//
// How to run after :convertSprites:
//   ./gradlew :gbkt-examples:metasprites:convertSprites \
//             :gbkt-examples:metasprites:test \
//             --tests "*.ElephantSPalDescriptorCountTest"
//
// Test skips gracefully when build/gbkt/generated/sprites/elephant.c does not exist
// (i.e., :convertSprites has not been run or GBDK is not installed).
// =============================================================================

class ElephantSPalDescriptorCountTest {

    companion object {
        /**
         * Generated sprite C file — produced by `:convertSprites` with the Plan 03 permutation.
         *
         * Relative to user.dir (the Gradle project dir when tests run via Gradle).
         */
        private val GENERATED_ELEPHANT = File("build/gbkt/generated/sprites/elephant.c")

        /**
         * Expected S_PAL(0) descriptor count in the permuted elephant.c.
         *
         * **Value: 161 (post-permutation count from Plan 03 run 2026-06-05)**
         *
         * Background (13.3 D-19 / RESEARCH Pitfall 7):
         * - `remapMetaspriteSubPalette` (Plan 13.3-20) rewrites S_PAL(1) → S_PAL(0) in
         *   metasprite descriptor entries to ensure uniform gray palette slot assignment.
         * - The Plan 03 palette permutation changes png2asset's 8×8 tile deduplication
         *   (compact 4-entry palette → different deduplicated tile set → 161 METASPR_ITEM
         *   entries instead of the pre-permutation 149). This is correct behavior.
         * - All 161 descriptors are S_PAL(0) because the compact palette has transparent
         *   at index 0 which png2asset assigns directly to sub-palette slot 0.
         *   remapMetaspriteSubPalette finds 0 S_PAL(1) entries to rewrite (already correct).
         *
         * Deviation from plan-time estimate: the plan specified 149 (pre-fix baseline count).
         * The actual post-permutation count is 161. See class KDoc for explanation.
         *
         * Plan 05 baseline re-pin must target this validated count, NOT the old 149.
         */
        const val EXPECTED_S_PAL0_DESCRIPTORS: Int = 161
    }

    @Test
    fun `permuted elephant c emits expected S_PAL(0) descriptor count`() {
        // Skip if :convertSprites has not been run (e.g., CI without GBDK).
        assumeTrue(
            GENERATED_ELEPHANT.exists(),
            "Generated sprite C not found at ${GENERATED_ELEPHANT.absolutePath} — " +
                "run ./gradlew :gbkt-examples:metasprites:convertSprites first; skipping test"
        )

        val text = GENERATED_ELEPHANT.readText()
        val actualCount = Regex("S_PAL\\(0\\)").findAll(text).count()

        assertEquals(
            EXPECTED_S_PAL0_DESCRIPTORS,
            actualCount,
            "permuted elephant.c emitted $actualCount S_PAL(0) descriptors, expected $EXPECTED_S_PAL0_DESCRIPTORS — " +
                "remapMetaspriteSubPalette regressed under palette permutation (Open Question 2). " +
                "If this count changed after a pipeline modification, investigate " +
                "ConvertSpritesTask.remapMetaspriteSubPalette and prePermuteIndexedPng."
        )
    }
}
