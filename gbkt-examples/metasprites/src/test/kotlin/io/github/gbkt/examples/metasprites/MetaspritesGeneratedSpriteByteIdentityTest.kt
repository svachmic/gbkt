/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

// =============================================================================
// PHASE 12.4 PLAN 07 — BYTE-IDENTITY REGRESSION GUARD (metasprites / elephant)
//
// Locks the SPEC § Constraints back-compat invariant: png2asset output for
// `elephant.png` (mirrorDedup = false → -noflip ON) MUST be byte-identical
// before and after Phase 12.4's sidecar refactor.
//
// How to capture a new baseline (run once after PNG or flag changes):
//   ./gradlew :gbkt-examples:metasprites:convertSprites
//   cp build/gbkt/generated/sprites/elephant.c \
//      src/test/resources/baseline/elephant.c.baseline
//   git add src/test/resources/baseline/elephant.c.baseline && git commit
//
// The baseline was re-pinned on 2026-06-05 after Plan 13.6-07 made the permuted-PNG
// temp filename deterministic (gbkt_permuted_<base>.png in a stable build-temp directory)
// so the png2asset "Conversion args" comment is reproducible across rebuilds (REQ-6).
// Determinism proven by two consecutive clean :convertSprites runs producing byte-identical C.
// Prior baseline (Plan 13.6-05, SHA-256: ef8f44c4...) used a random temp name → non-deterministic.
// SHA-256: 0296ec36048c72de6a7d9ab16f8843e663bdca37f6718787219caa293767ab99
//
// Test skips gracefully when build/gbkt/generated/sprites/elephant.c does
// not exist (i.e., :convertSprites has not been run or GBDK is not installed).
// CI environments without GBDK: run this test only when GBDK is available
// (e.g., ./gradlew :gbkt-examples:metasprites:convertSprites test --tests "*ByteIdentity*").
// =============================================================================

class MetaspritesGeneratedSpriteByteIdentityTest {

    companion object {
        /**
         * Generated sprite C file — produced by `:convertSprites`.
         *
         * Relative to user.dir (the Gradle project dir when tests run via Gradle).
         */
        private val GENERATED_ELEPHANT = File("build/gbkt/generated/sprites/elephant.c")

        /**
         * Committed baseline file — captured once, committed to git as the contract.
         *
         * Loaded via classloader to work from both Gradle test runner and IDE.
         */
        private fun loadBaseline(name: String): ByteArray {
            val stream =
                MetaspritesGeneratedSpriteByteIdentityTest::class
                    .java
                    .classLoader
                    .getResourceAsStream("baseline/$name")
                    ?: error(
                        "Baseline file not found in test resources: baseline/$name\n" +
                            "Ensure src/test/resources/baseline/$name is committed to git."
                    )
            return stream.use { it.readBytes() }
        }
    }

    @Test
    fun `generated elephant_tiles c file is byte-identical to pre-12-4 baseline`() {
        // Skip if :convertSprites has not been run (e.g., CI without GBDK).
        assumeTrue(
            GENERATED_ELEPHANT.exists(),
            "Generated sprite C not found at ${GENERATED_ELEPHANT.absolutePath} — " +
                "run ./gradlew :gbkt-examples:metasprites:convertSprites first; skipping test",
        )

        val actualBytes = GENERATED_ELEPHANT.readBytes()
        val baselineBytes = loadBaseline("elephant.c.baseline")

        // Produce a diff-friendly failure message showing the first differing offsets.
        if (!actualBytes.contentEquals(baselineBytes)) {
            val diffOffsets = mutableListOf<String>()
            val limit = minOf(actualBytes.size, baselineBytes.size)
            var diffCount = 0
            for (i in 0 until limit) {
                if (actualBytes[i] != baselineBytes[i]) {
                    diffOffsets.add(
                        "offset $i: actual=0x%02X baseline=0x%02X"
                            .format(
                                actualBytes[i].toInt() and 0xFF,
                                baselineBytes[i].toInt() and 0xFF,
                            )
                    )
                    diffCount++
                    if (diffCount >= 5) break
                }
            }
            val sizeDiff =
                if (actualBytes.size != baselineBytes.size)
                    "\nSize mismatch: actual=${actualBytes.size} baseline=${baselineBytes.size}"
                else ""
            error(
                "elephant.c byte sequence differs from pre-12.4 baseline!\n" +
                    "This means png2asset output has changed — a -noflip flag may have been dropped,\n" +
                    "a sidecar schema change altered the invocation, or the PNG itself changed.\n$sizeDiff\n" +
                    "First differing offsets (up to 5):\n${diffOffsets.joinToString("\n")}"
            )
        }

        assertContentEquals(
            baselineBytes,
            actualBytes,
            "elephant.c must be byte-identical to the 13.6-07 deterministic-name baseline " +
                "(baseline SHA-256: 0296ec36...)",
        )
    }
}
