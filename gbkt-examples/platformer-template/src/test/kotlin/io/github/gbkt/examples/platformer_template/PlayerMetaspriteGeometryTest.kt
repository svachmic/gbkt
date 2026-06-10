/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer_template

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions

// =============================================================================
// PLAYER METASPRITE GEOMETRY TEST — Phase 12.5 Plan 07 (D-08a, REQ-2 acceptance)
// + Debug E-03 (post-fix tightened geometry assertions)
//
// Asserts the JVM-tier geometry contract for the player metasprite in the
// platformer-template example. The test runs the full GBDK pipeline on the
// PlatformerTemplate DSL, extracts the `sprite_player_frame_0[]` array body
// from the generated `main.c`, parses the cumulative dx/dy values, and asserts
// the expected 3col × 2row grid layout.
//
// REQ-2 acceptance: the 3col×2row 24×32 player metasprite layout is guarded
// structurally by this JVM-tier test. Future regressions in ConvertSpritesTask,
// GBDKPipeline, or DSL tile() coordinates would fail this test.
//
// CRITICAL GEOMETRY (post-E-03 fix, 2026-05-24):
// Prior to the E-03 fix, the platformer-template DSL had x/y arguments swapped
// in every tile() call — author transcribed reference METASPR_ITEM(dy,dx,...)
// arguments directly, but the DSL signature is tile(x=dx, y=dy, id). That
// produced a vertical column layout (2 distinct x-columns, 3 y-rows) instead
// of the correct horizontal grid. The pre-fix assertion (2 x-clusters at
// {-6, 10}) was LOCKING IN the bug — passing tests despite a malformed
// runtime visual (the "duck blob").
//
// Reference png2asset output for player-character-gbapduck-sprites.png with
// flags -spr8x16 -px 12 -py 6 -sw 24 -sh 32:
//   METASPR_ITEM(-6, -12, 0, ...)   // {dy=-6, dx=-12}  → top-left tile
//   METASPR_ITEM(0,  8,  2, ...)    // {dy=0,  dx=8}    → top-middle (8px right)
//   METASPR_ITEM(0,  8,  4, ...)    // {dy=0,  dx=8}    → top-right (8px right)
//   METASPR_ITEM(16, -16, 6, ...)   // {dy=16, dx=-16}  → mid-left (16px down, 16px left)
//   METASPR_ITEM(0,  8,  8, ...)    // {dy=0,  dx=8}    → mid-middle
//   METASPR_ITEM(0,  8,  10, ...)   // {dy=0,  dx=8}    → mid-right
//
// Post-fix cumulative absolute positions (anchor 0):
//   x: -12, -4, 4, -12, -4, 4    → 3 distinct x-columns at {-12, -4, 4}
//   y: -6,  -6, -6, 10, 10, 10   → 2 distinct y-rows at {-6, 10}
//
// This forms the correct 3col × 2row 24×32 SPR8x16 grid (columns 8px apart,
// rows 16px apart = 8x16 tile pair height).
//
// Scope-level grep gate (per CLAUDE.md §"Scope-level grep gates"):
// assertions run against the `sprite_player_frame_0` ARRAY BODY (brace-walk
// extracted), not the full file, to avoid false positives from other arrays
// in main.c.
//
// Evidence: extracted array body is written to
// .planning/phases/12.5-.../evidence/tier1-geometry/ before assertions fire,
// so the C output shape is reviewable from disk even when a test is RED.
// =============================================================================

class PlayerMetaspriteGeometryTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which inside a Claude Code
         * worktree is the worktree root — not the main repository. Hard-coding the main-repo
         * absolute path would silently route evidence files outside the active checkout and miss
         * the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/12.5-png2asset-metasprite-layout-fix-and-phase-12-3-closure/evidence/tier1-geometry"
                )
                .normalize()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C array body by brace-walking from the first line containing `arrayName[` until
     * the matching closing brace + semicolon at depth zero.
     *
     * Works for both `const type array[] = { ... };` declarations and `void functionName(...) { ...
     * }` functions (per CLAUDE.md §"Scope-level grep gates" approved brace-walk pattern from
     * MetaspriteEmissionTest.kt).
     *
     * The returned blob includes the declaration/signature line and the closing brace, so
     * downstream `.contains()` checks operate ONLY on tokens that live inside the named array —
     * never on tokens from unrelated declarations in the same C file.
     */
    private fun extractArrayBody(cSource: String, arrayName: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.contains("$arrayName[]") }
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

    /**
     * Reads the png2asset-native generated player sprite C and returns its source.
     *
     * Phase 15 F3/F4 (research Pitfall 1): the player metasprite is NO LONGER hand-emitted into
     * `main.c` as `sprite_player_frame_0[]`. The platformer-template player uses Path A
     * png2asset-native metasprites — the per-frame arrays live in
     * `build/gbkt/generated/sprites/player.c` as `const metasprite_t player_metasprite0[] = {
     * METASPR_ITEM(...) }`, produced by the Gradle `convertSprites` (png2asset) task, NOT by
     * `GBDKPipeline.generate()`. So this test reads the on-disk asset (build.gradle.kts wires
     * `tasks.test` to depend on `convertSprites` so the file is fresh before `:test`).
     *
     * If the file is genuinely absent (no GBDK/png2asset on host), the test SKIPS via
     * `Assumptions.assumeTrue` — a genuine missing-prerequisite skip is not a failure. The skip is
     * NOT used to mask a present-but-wrong asset: when present, the geometry assertions run for
     * real.
     */
    private fun playerSpriteC(): String {
        val file =
            File(System.getProperty("user.dir"))
                .resolve("build/gbkt/generated/sprites/player.c")
                .normalize()
        Assumptions.assumeTrue(
            file.exists(),
            "sprites/player.c not generated — run :gbkt-examples:platformer-template:convertSprites " +
                "first (needs GBDK/png2asset). Expected at ${file.absolutePath}",
        )
        return file.readText()
    }

    /**
     * Parses all `METASPR_ITEM(dy, dx, tileId, ...)` macro entries from a png2asset metasprite
     * frame array body and returns them as a list of (dy, dx, tileId) triples. The trailing macro
     * props (e.g. `S_PAL(0)`) are ignored; the array terminator `METASPR_TERM` does not match and
     * is naturally excluded.
     */
    private fun parseFrameEntries(arrayBody: String): List<Triple<Int, Int, Int>> {
        val entryRegex = Regex("""METASPR_ITEM\(\s*(-?\d+),\s*(-?\d+),\s*(\d+)\s*,""")
        return entryRegex
            .findAll(arrayBody)
            .map { match ->
                Triple(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }
            .toList()
    }

    /** Clusters a sorted list of integers within ±[tolerance], returning cluster centers. */
    private fun clusterPositions(positions: List<Int>, tolerance: Int = 4): List<Int> {
        val sorted = positions.sorted()
        val clusters = mutableListOf<Int>()
        var currentClusterCenter: Int? = null
        for (x in sorted) {
            if (
                currentClusterCenter == null ||
                    kotlin.math.abs(x - currentClusterCenter) > tolerance
            ) {
                clusters.add(x)
                currentClusterCenter = x
            }
        }
        return clusters
    }

    // -------------------------------------------------------------------------
    // Sanity check: array exists
    //
    // Asserts that the generated main.c contains the `sprite_player_frame_0[]`
    // array, guarding against catastrophic regressions where the player
    // metasprite is entirely absent from generated output.
    // -------------------------------------------------------------------------

    @Test
    fun `player_metasprite_array_exists`() {
        EVIDENCE_DIR.mkdirs()
        val cSource = playerSpriteC()
        val arrayBody = extractArrayBody(cSource, "player_metasprite0")
        File(EVIDENCE_DIR, "player_frame_0_layout.txt")
            .writeText(
                "=== player_metasprite0[] extracted body (sprites/player.c) ===\n" +
                    arrayBody +
                    "\n=== sprites/player.c (first 200 lines) ===\n" +
                    cSource.lines().take(200).joinToString("\n")
            )

        assertTrue(
            arrayBody.isNotEmpty(),
            "player_metasprite_array_exists: player_metasprite0[] not found in sprites/player.c. " +
                "This indicates the png2asset-native player metasprite was not emitted into the " +
                "generated sprite C output. sprites/player.c first 200 lines:\n" +
                cSource.lines().take(200).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // E-03 geometry gate: 3 distinct x-columns × 2 distinct y-rows (post-fix)
    //
    // Asserts the corrected 3col × 2row 24×32 SPR8x16 layout, which guards
    // against the x/y swap regression (debug session
    // platformer-duck-malformed-blob, E-03).
    //
    // Cluster positions are tested ±4px tolerance from the reference png2asset
    // output (-spr8x16 -px 12 -py 6 -sw 24 -sh 32):
    //   x-columns at {-12, -4, 4}    (3 columns 8px apart, total 24px wide)
    //   y-rows    at {-6, 10}        (2 rows 16px apart, total 32px tall)
    //
    // PRE-FIX (locked the bug): asserted 2 x-clusters at {-6, 10}. That was the
    // SWAPPED geometry where the duck rendered as a vertical column instead of
    // a horizontal grid — the "blob" runtime symptom.
    // -------------------------------------------------------------------------

    @Test
    fun `player_frame_0 has 3 x-columns and 2 y-rows (3col x 2row 24x32 SPR8x16 layout)`() {
        EVIDENCE_DIR.mkdirs()
        val cSource = playerSpriteC()
        val arrayBody = extractArrayBody(cSource, "player_metasprite0")

        File(EVIDENCE_DIR, "player_frame_0_array.txt").writeText(arrayBody)

        assertTrue(
            arrayBody.isNotEmpty(),
            "player_metasprite0[] not found in sprites/player.c — cannot assert geometry",
        )

        // Parse {dy, dx, tileId} struct entries from the array body.
        // `{metasprite_end}` terminates the array; this entry is excluded from parsing.
        val entries = parseFrameEntries(arrayBody)

        assertTrue(
            entries.isNotEmpty(),
            "No {dy, dx, tileId} entries found in sprite_player_frame_0[] body. " +
                "Array body:\n$arrayBody",
        )

        // Accumulate dx AND dy values to compute absolute (x, y) positions from anchor (0,0).
        // The first entry is relative to the anchor; subsequent entries accumulate from the
        // previous.
        var cumulativeX = 0
        var cumulativeY = 0
        val absoluteXPositions = mutableListOf<Int>()
        val absoluteYPositions = mutableListOf<Int>()
        for ((dy, dx, _) in entries) {
            cumulativeX += dx
            cumulativeY += dy
            absoluteXPositions.add(cumulativeX)
            absoluteYPositions.add(cumulativeY)
        }

        File(EVIDENCE_DIR, "player_frame_0_x_positions.txt")
            .writeText(
                "Parsed {dy, dx, tileId} entries:\n" +
                    entries
                        .mapIndexed { i, (dy, dx, t) -> "  Entry $i: dy=$dy, dx=$dx, tileId=$t" }
                        .joinToString("\n") +
                    "\n\nAbsolute x positions (cumulative dx from anchor 0):\n" +
                    absoluteXPositions
                        .mapIndexed { i, x -> "  Entry $i: x=$x" }
                        .joinToString("\n") +
                    "\n\nAbsolute y positions (cumulative dy from anchor 0):\n" +
                    absoluteYPositions.mapIndexed { i, y -> "  Entry $i: y=$y" }.joinToString("\n")
            )

        val xClusters = clusterPositions(absoluteXPositions)
        val yClusters = clusterPositions(absoluteYPositions)

        // Assert 6 tile entries (3col × 2row = 6 8x16 sprite pairs forming 24×32).
        assertEquals(
            6,
            entries.size,
            "player_frame_0 geometry: expected exactly 6 tile entries (3col × 2row = 6 tiles). " +
                "Actual entry count: ${entries.size}. " +
                "Array body:\n$arrayBody",
        )

        // Assert 3 distinct x-column clusters (E-03 fix: post-swap horizontal grid).
        assertEquals(
            3,
            xClusters.size,
            "player_frame_0 geometry (E-03): expected exactly 3 distinct x-column clusters " +
                "(3col × 2row 24×32 SPR8x16 layout). " +
                "Pre-fix bug produced 2 x-clusters (vertical column layout — the duck blob). " +
                "Actual absolute x positions: $absoluteXPositions, " +
                "detected x-clusters: $xClusters. " +
                "Array body:\n$arrayBody",
        )

        // Assert 2 distinct y-row clusters (8x16 pair rows = 16px apart).
        assertEquals(
            2,
            yClusters.size,
            "player_frame_0 geometry (E-03): expected exactly 2 distinct y-row clusters " +
                "(SPR8x16 pair rows 16px apart). " +
                "Actual absolute y positions: $absoluteYPositions, " +
                "detected y-clusters: $yClusters. " +
                "Array body:\n$arrayBody",
        )

        // Assert the three expected x-column centers (±4px tolerance).
        assertTrue(
            kotlin.math.abs(xClusters[0] - (-12)) <= 4,
            "player_frame_0 geometry: leftmost x-column expected near -12, got ${xClusters[0]} " +
                "(x positions: $absoluteXPositions)",
        )
        assertTrue(
            kotlin.math.abs(xClusters[1] - (-4)) <= 4,
            "player_frame_0 geometry: middle x-column expected near -4, got ${xClusters[1]} " +
                "(x positions: $absoluteXPositions)",
        )
        assertTrue(
            kotlin.math.abs(xClusters[2] - 4) <= 4,
            "player_frame_0 geometry: rightmost x-column expected near 4, got ${xClusters[2]} " +
                "(x positions: $absoluteXPositions)",
        )

        // Assert the two expected y-row centers (±4px tolerance).
        assertTrue(
            kotlin.math.abs(yClusters[0] - (-6)) <= 4,
            "player_frame_0 geometry: top y-row expected near -6, got ${yClusters[0]} " +
                "(y positions: $absoluteYPositions)",
        )
        assertTrue(
            kotlin.math.abs(yClusters[1] - 10) <= 4,
            "player_frame_0 geometry: bottom y-row expected near 10, got ${yClusters[1]} " +
                "(y positions: $absoluteYPositions)",
        )
    }
}
