/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =============================================================================
// VRAM ALLOCATOR UNIT TESTS — Phase 10.1 Plan 08 / CR-01 (SEED-008)
//
// Asserts the minimal contract of [VramAllocator] per RESEARCH §Pattern 1
// (lines 333-348):
//   - reserve(N) returns the current cursor, then advances by N
//   - reserve(0) is a no-op cursor read
//   - reserve(negative) throws IllegalArgumentException
//
// The allocator REPLACES two parallel `var nextTile = 0` counters that
// previously lived in GBDKPipeline.buildSpriteDataLoadStatements and
// buildMetaspriteTileDataLoadStatements. Those counters provably collided
// when both actors and metasprites coexisted in the same game (SEED-008 /
// D-08 / CR-01).
//
// D-13b: VramAllocator is `internal` codegen-only — no DSL exposure.
// =============================================================================

class VramAllocatorTest {

    // -------------------------------------------------------------------------
    // Test 1: monotonic start-index handout (the core contract)
    // -------------------------------------------------------------------------
    @Test
    fun reserve_returns_monotonically_increasing_start_indices() {
        val allocator = VramAllocator()

        // Actor with sprite 8x16 = 2 tiles
        val actorStart = allocator.reserve(2)
        assertEquals(0, actorStart, "First reserve should return 0 (initial cursor)")

        // Metasprite with maxTileId 47 → 48 tiles
        val metaStart = allocator.reserve(48)
        assertEquals(
            2,
            metaStart,
            "Second reserve should start at 2 (immediately after actor's 2 tiles) — " +
                "this is the CR-01 fix: metasprite must NOT collide back to 0",
        )

        // Third actor with 1 tile
        val extraStart = allocator.reserve(1)
        assertEquals(50, extraStart, "Third reserve should start at 50 (2 + 48)")

        assertEquals(51, allocator.tilesUsed, "tilesUsed should reflect cumulative reserved tiles")
    }

    // -------------------------------------------------------------------------
    // Test 2: zero-tile reserve is a cursor-read, not an advance
    // -------------------------------------------------------------------------
    @Test
    fun reserve_zero_returns_current_index_and_does_not_advance() {
        val allocator = VramAllocator()

        val firstStart = allocator.reserve(2)
        assertEquals(0, firstStart)

        val zeroStart = allocator.reserve(0)
        assertEquals(2, zeroStart, "reserve(0) should return the CURRENT cursor without advancing")

        assertEquals(2, allocator.tilesUsed, "tilesUsed should NOT advance for reserve(0)")
    }

    // -------------------------------------------------------------------------
    // Test 3: negative input rejected via require()
    // -------------------------------------------------------------------------
    @Test
    fun reserve_negative_throws() {
        val allocator = VramAllocator()

        assertFailsWith<IllegalArgumentException>(
            "reserve(negative) must reject via require() — a negative tile count " +
                "would advance the cursor backwards and silently corrupt VRAM layout"
        ) {
            allocator.reserve(-1)
        }
    }
}
