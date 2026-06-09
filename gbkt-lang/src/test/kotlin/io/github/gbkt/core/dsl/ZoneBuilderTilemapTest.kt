/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// ZONE BUILDER TILEMAP TEST (REQ-1)
//
// Phase 12.2 Plan 01 — verifies the new `tilemap(ref: AssetRef)` DSL method
// on ZoneBuilder propagates to `ZoneIR.tilemapPath`, and that absence of
// the call leaves `tilemapPath == null` for the one-invocation
// (tileset-doubles-as-tilemap) code path.
//
// AssetRef is constructed directly here — the `asset()` factory lives in
// gbkt-core which is downstream from gbkt-lang and unavailable in this
// test scope.
// =============================================================================

class ZoneBuilderTilemapTest {

    /** Minimal DSL helper — same pattern as WorldBuildersTest.buildZone(). */
    private fun buildZone(id: String, block: ZoneBuilder.() -> Unit): ZoneIR =
        ZoneBuilder(id).apply(block).build()

    @Test
    fun `tilemap(asset) sets tilemapPath on ZoneIR`() {
        val zone =
            buildZone("world1") {
                tileset(AssetRef("graphics/world1-tileset.png"))
                tilemap(AssetRef("graphics/world1-area1.png"))
            }
        assertEquals("graphics/world1-area1.png", zone.tilemapPath)
        assertEquals("graphics/world1-tileset.png", zone.tilesetPath)
    }

    @Test
    fun `zone without tilemap() has tilemapPath null`() {
        val zone = buildZone("banks_zone") { tileset(AssetRef("tiles/checker.png")) }
        assertNull(zone.tilemapPath, "tilemapPath must be null when tilemap() is not called")
        assertEquals("tiles/checker.png", zone.tilesetPath)
    }
}
