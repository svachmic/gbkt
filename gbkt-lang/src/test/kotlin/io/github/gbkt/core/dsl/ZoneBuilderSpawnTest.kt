/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// ZONE BUILDER SPAWN TEST (Phase 12.6 Plan 03, Task 2)
//
// Locks the DSL → IR contract for the new `spawn(x, y)` setter on
// ZoneBuilder. Plan 12.6-05 codegen consumes `ZoneIR.spawnX` /
// `ZoneIR.spawnY` to emit per-level spawn tables.
//
// Round-trip checks:
//   - `val xZone by zone { spawn(40u, 120u) }` → spawnX=40u, spawnY=120u
//   - `val yZone by zone { }` (no spawn call)  → spawnX=null, spawnY=null
//   - `val zZone by zone { spawn(0u, 255u) }`  → edge values (sanity)
//
// Mirrors the buildZone() fixture pattern from ZoneBuilderTilemapTest.
// Closes DEFECT-2 per CONTEXT D-06 / D-07.
// =============================================================================

class ZoneBuilderSpawnTest {

    /** Minimal DSL helper — same pattern as ZoneBuilderTilemapTest.buildZone(). */
    private fun buildZone(id: String, block: ZoneBuilder.() -> Unit): ZoneIR =
        ZoneBuilder(id).apply(block).build()

    @Test
    fun `spawn sets ZoneIR spawnX and spawnY to the declared UByte values`() {
        val zone = buildZone("z") { spawn(40u, 120u) }
        assertEquals(40u.toUByte(), zone.spawnX, "spawnX must reflect declared DSL value")
        assertEquals(120u.toUByte(), zone.spawnY, "spawnY must reflect declared DSL value")
    }

    @Test
    fun `omitted spawn leaves spawnX and spawnY null`() {
        val zone = buildZone("z") { }
        assertNull(zone.spawnX, "spawnX must be null when spawn() is not called")
        assertNull(zone.spawnY, "spawnY must be null when spawn() is not called")
    }

    @Test
    fun `spawn accepts edge values 0u and 255u without overflow`() {
        val zone = buildZone("z") { spawn(0u, 255u) }
        assertEquals(0u.toUByte(), zone.spawnX, "spawnX must accept 0u")
        assertEquals(255u.toUByte(), zone.spawnY, "spawnY must accept 255u")
    }
}
