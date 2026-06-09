/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// =============================================================================
// PHASE 12 PLAN 07 — End-to-end per-zone platformerPhysics override (D-12)
//
// Locks the contract that the zone delegate with `platformerPhysics { ... }` populates
// ZoneIR.platformerPhysicsOverride with ONLY the explicitly-set fields, using
// the documented PlatformerPhysicsConfig field names verbatim as keys:
//   "gravity", "jumpForce", "terminalVelocity", "solidThreshold",
//   "jumpHoldMaxFrames"
//
// Plan 12-08 (PlatformerVisitor) reads these keys back via an `Int` cast at
// codegen time, so the key contract MUST stay in sync. The string literals
// below are the lock — DO NOT rename them without updating PlatformerVisitor.
//
// Implementation note: `ZoneBuilder.build()` is `internal` to gbkt-lang. We
// route through the `by zone {}` delegate inside a `game {}` block (which
// internally calls `ZoneBuilder.build()` and appends to the GameBuilder's zone
// list) and read the built `ZoneIR` back via `ir.zones` after `.build()`.
// This is also a stronger end-to-end test: it exercises exactly the path the
// end-user DSL takes.
//
// D-12 (Phase 13.4): The original 4 explicit-receiver zone calls
// (which used a bare GameBuilder instance outside any `game {}` context)
// have been restructured into `game("test") { val z by zone {} }` blocks.
// Zones are now read back via the built `ir.zones`, not `gb.currentZones()`.
// =============================================================================

class ZonePlatformerPhysicsTest {

    /**
     * Test 1 — explicit fields land in the override map with the documented keys.
     *
     * Locks the PlatformerVisitor (Plan 12-08) contract: keys must be the
     * PlatformerPhysicsConfig field names verbatim (`solidThreshold`, `gravity`).
     */
    @Test
    fun `platformerPhysics with two set fields populates exactly those two keys`() {
        val ir =
            game("test1") {
                val z1 by zone {
                    platformerPhysics {
                        solidThreshold(68)
                        gravity(3)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = z1
                val s = scene("s") { enter {} }
                start = s
            }.build()

        val zone = ir.zones.single()
        val override = zone.platformerPhysicsOverride
        assertNotNull(override, "platformerPhysicsOverride must be non-null after block call")
        assertEquals(
            mapOf<String, Any>("solidThreshold" to 68, "gravity" to 3),
            override,
            "Only the SET fields must appear in the override map, with PlatformerPhysicsConfig key names.",
        )
    }

    /**
     * Test 2 — calling the block with no field setters records an EMPTY map (NOT null).
     *
     * Distinguishes "block called but no overrides" from "block never called".
     */
    @Test
    fun `platformerPhysics with empty block records an empty override map (not null)`() {
        val ir =
            game("test2") {
                val z2 by zone {
                    platformerPhysics {
                        // intentionally empty — locks the empty-block contract
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = z2
                val s = scene("s") { enter {} }
                start = s
            }.build()

        val zone = ir.zones.single()
        assertEquals(
            emptyMap<String, Any>(),
            zone.platformerPhysicsOverride,
            "Empty block must produce an empty Map, not null — locks the 'block-called' marker.",
        )
    }

    /**
     * Test 3 — when the block is NEVER called the override field stays null.
     *
     * Locks the default ZoneIR shape (also verified at gbkt-lang tier by
     * WorldBuilderOverrideTest from Plan 12-06).
     */
    @Test
    fun `zone without platformerPhysics block leaves override null`() {
        val ir =
            game("test3") {
                val z3 by zone {
                    // no platformerPhysics block — override stays null
                }
                @Suppress("UNUSED_VARIABLE") val _unused = z3
                val s = scene("s") { enter {} }
                start = s
            }.build()

        val zone = ir.zones.single()
        assertNull(
            zone.platformerPhysicsOverride,
            "Without the block, ZoneIR.platformerPhysicsOverride must remain null.",
        )
    }

    /**
     * Test 4 — calling the block TWICE on the same zone REPLACES (does not merge) the
     * previous override map; the second call wins.
     *
     * Locks the "last-writer-wins" semantic so per-zone authoring stays predictable.
     * Note: each `platformerPhysics { }` invocation builds a fresh OverrideTrackingPhysicsBuilder
     * and overwrites the slot via setPlatformerPhysicsOverride(...).
     */
    @Test
    fun `calling platformerPhysics twice on the same zone replaces, not merges`() {
        val ir =
            game("test4") {
                val z4 by zone {
                    platformerPhysics {
                        gravity(2)
                        jumpForce(8)
                    }
                    platformerPhysics {
                        // second block — sets ONLY solidThreshold; previous gravity/jumpForce dropped
                        solidThreshold(68)
                        jumpHold(12)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val _unused = z4
                val s = scene("s") { enter {} }
                start = s
            }.build()

        val zone = ir.zones.single()
        val override = zone.platformerPhysicsOverride
        assertNotNull(override, "Second platformerPhysics call must still produce a map")
        assertEquals(
            mapOf<String, Any>("solidThreshold" to 68, "jumpHoldMaxFrames" to 12),
            override,
            "Second platformerPhysics block must REPLACE the first; gravity/jumpForce must be absent.",
        )
    }
}
