/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// ZONE IR SPAWN FIELDS TEST (Phase 12.6 Plan 03, Task 1)
//
// Locks the additive-non-breaking contract for the new per-zone player spawn
// position fields (D-06 / D-07):
//   - `spawnX: UByte? = null`
//   - `spawnY: UByte? = null`
//
// These fields are nullable so existing ZoneIR(...) call sites continue to
// compile unchanged. Plan 12.6-05 codegen will consume the fields to emit
// per-level spawn tables in HOME bank.
// =============================================================================

class ZoneIRSpawnTest {

    @Test
    fun `ZoneIR default-constructed has null spawnX and null spawnY`() {
        val zone = ZoneIR(id = "z1", name = "Zone 1")
        assertNull(zone.spawnX, "spawnX must default to null (additive non-breaking)")
        assertNull(zone.spawnY, "spawnY must default to null (additive non-breaking)")
    }

    @Test
    fun `ZoneIR explicit spawnX and spawnY values round-trip through data class`() {
        val zone =
            ZoneIR(id = "z2", name = "Zone 2", spawnX = 40u.toUByte(), spawnY = 120u.toUByte())
        assertEquals(40u.toUByte(), zone.spawnX)
        assertEquals(120u.toUByte(), zone.spawnY)
    }

    @Test
    fun `ZoneIR accepts edge UByte values 0u and 255u without overflow`() {
        val zone =
            ZoneIR(id = "z3", name = "Zone 3", spawnX = 0u.toUByte(), spawnY = 255u.toUByte())
        assertEquals(0u.toUByte(), zone.spawnX)
        assertEquals(255u.toUByte(), zone.spawnY)
    }
}
