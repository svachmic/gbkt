/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// PHASE 12 PLAN 06 — ZoneBuilder ↔ ZoneIR platformerPhysicsOverride round-trip
//
// Verifies that the opaque per-level physics-override slot (D-12) propagates
// from the internal ZoneBuilder.setPlatformerPhysicsOverride() API into the
// built ZoneIR.platformerPhysicsOverride field intact. Plan 12-07 wires the
// public DSL (a `platformerPhysics { }` block inside `zone { }`) as a
// genre-platformer extension that calls this setter — gbkt-lang stays
// free of compile-time dependency on gbkt-genre-platformer.
//
// `setPlatformerPhysicsOverride` is `internal`, so this test MUST live in
// the same module (gbkt-lang) to compile.
// =============================================================================

class WorldBuilderOverrideTest {

    @Test
    fun `default ZoneBuilder build produces null platformerPhysicsOverride`() {
        val zone = ZoneBuilder("default-zone").build()
        assertNull(
            zone.platformerPhysicsOverride,
            "Without an explicit override, ZoneIR.platformerPhysicsOverride must remain null.",
        )
    }

    @Test
    fun `setPlatformerPhysicsOverride preserves a single-entry map`() {
        val overrides = mapOf<String, Any>("solidThreshold" to 17)
        val zone =
            ZoneBuilder("zone-with-threshold")
                .apply { setPlatformerPhysicsOverride(overrides) }
                .build()

        assertEquals(
            overrides,
            zone.platformerPhysicsOverride,
            "ZoneIR must carry the override map exactly as supplied.",
        )
    }

    @Test
    fun `setPlatformerPhysicsOverride preserves multiple entries`() {
        val overrides =
            mapOf<String, Any>(
                "solidThreshold" to 68,
                "gravity" to 3,
                "jumpForce" to 8,
                "terminalVelocity" to 12,
                "jumpHoldMaxFrames" to 20,
            )
        val zone =
            ZoneBuilder("zone-with-full-override")
                .apply { setPlatformerPhysicsOverride(overrides) }
                .build()

        val actual = zone.platformerPhysicsOverride
        assertEquals(overrides.size, actual?.size)
        assertEquals(overrides, actual, "All override keys/values must round-trip intact.")
    }
}
