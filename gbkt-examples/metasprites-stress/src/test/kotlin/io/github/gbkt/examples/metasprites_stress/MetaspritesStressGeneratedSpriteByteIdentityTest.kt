/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites_stress

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

// =============================================================================
// PHASE 12.4 PLAN 07 — BYTE-IDENTITY REGRESSION GUARD (metasprites-stress)
//
// Locks the SPEC § Constraints back-compat invariant: png2asset output for
// `elephant.png` and `tiger.png` (both mirrorDedup = false → -noflip ON) MUST
// be byte-identical before and after Phase 12.4's sidecar refactor.
//
// How to capture new baselines (run once after PNG or flag changes):
//   ./gradlew :gbkt-examples:metasprites-stress:convertSprites
//   cp build/gbkt/generated/sprites/elephant.c \
//      src/test/resources/baseline/elephant.c.baseline
//   cp build/gbkt/generated/sprites/tiger.c \
//      src/test/resources/baseline/tiger.c.baseline
//   git add src/test/resources/baseline/ && git commit
//
// Baselines re-pinned on 2026-06-05 after Plan 13.6-07 made the permuted-PNG temp filename
// deterministic (gbkt_permuted_<base>.png in a stable build-temp directory) so the
// png2asset "Conversion args" comment is reproducible across rebuilds (REQ-6).
// Determinism proven by two consecutive clean :convertSprites runs producing byte-identical C.
// Prior baselines (Plan 13.6-05) used a random temp name → non-deterministic C output.
// SHA-256 elephant: 6be5d78ff15572986fb3b8cdd7cb5e4325fb339b3ddeaa77cc026ecc8a235a60
// SHA-256 tiger:    b09fe25d815971d41b448d232eb8ededc2544d344de2f2f77fc5c8482996cd85
//
// Tests skip gracefully when build/gbkt/generated/sprites/<id>.c does not exist
// (i.e., :convertSprites has not been run or GBDK is not installed).
// =============================================================================

class MetaspritesStressGeneratedSpriteByteIdentityTest {

    companion object {
        private val GENERATED_ELEPHANT = File("build/gbkt/generated/sprites/elephant.c")
        private val GENERATED_TIGER = File("build/gbkt/generated/sprites/tiger.c")

        /**
         * Committed baseline file — captured once, committed to git as the contract.
         *
         * Loaded via classloader to work from both Gradle test runner and IDE.
         */
        private fun loadBaseline(name: String): ByteArray {
            val stream =
                MetaspritesStressGeneratedSpriteByteIdentityTest::class
                    .java
                    .classLoader
                    .getResourceAsStream("baseline/$name")
                    ?: error(
                        "Baseline file not found in test resources: baseline/$name\n" +
                            "Ensure src/test/resources/baseline/$name is committed to git."
                    )
            return stream.use { it.readBytes() }
        }

        /** Compares actual vs baseline bytes, producing a clear diff message on failure. */
        private fun assertByteIdentical(
            actual: ByteArray,
            baseline: ByteArray,
            spriteId: String,
            baselineSha256Prefix: String,
        ) {
            if (!actual.contentEquals(baseline)) {
                val diffOffsets = mutableListOf<String>()
                val limit = minOf(actual.size, baseline.size)
                var diffCount = 0
                for (i in 0 until limit) {
                    if (actual[i] != baseline[i]) {
                        diffOffsets.add(
                            "offset $i: actual=0x%02X baseline=0x%02X"
                                .format(actual[i].toInt() and 0xFF, baseline[i].toInt() and 0xFF)
                        )
                        diffCount++
                        if (diffCount >= 5) break
                    }
                }
                val sizeDiff =
                    if (actual.size != baseline.size)
                        "\nSize mismatch: actual=${actual.size} baseline=${baseline.size}"
                    else ""
                error(
                    "$spriteId.c byte sequence differs from pre-12.4 baseline!\n" +
                        "This means png2asset output has changed — a -noflip flag may have been dropped,\n" +
                        "a sidecar schema change altered the invocation, or the PNG itself changed.\n$sizeDiff\n" +
                        "First differing offsets (up to 5):\n${diffOffsets.joinToString("\n")}"
                )
            }
            assertContentEquals(
                baseline,
                actual,
                "$spriteId.c must be byte-identical to the pre-12.4 baseline " +
                    "(baseline SHA-256: $baselineSha256Prefix...)",
            )
        }
    }

    @Test
    fun `generated elephant_tiles c file is byte-identical to pre-12-4 baseline`() {
        // Skip if :convertSprites has not been run (e.g., CI without GBDK).
        assumeTrue(
            GENERATED_ELEPHANT.exists(),
            "Generated sprite C not found at ${GENERATED_ELEPHANT.absolutePath} — " +
                "run ./gradlew :gbkt-examples:metasprites-stress:convertSprites first; skipping test",
        )

        assertByteIdentical(
            actual = GENERATED_ELEPHANT.readBytes(),
            baseline = loadBaseline("elephant.c.baseline"),
            spriteId = "elephant",
            baselineSha256Prefix = "6be5d78f",
        )
    }

    @Test
    fun `generated tiger_tiles c file is byte-identical to pre-12-4 baseline`() {
        // Skip if :convertSprites has not been run (e.g., CI without GBDK).
        assumeTrue(
            GENERATED_TIGER.exists(),
            "Generated sprite C not found at ${GENERATED_TIGER.absolutePath} — " +
                "run ./gradlew :gbkt-examples:metasprites-stress:convertSprites first; skipping test",
        )

        assertByteIdentical(
            actual = GENERATED_TIGER.readBytes(),
            baseline = loadBaseline("tiger.c.baseline"),
            spriteId = "tiger",
            baselineSha256Prefix = "b09fe25d",
        )
    }
}
