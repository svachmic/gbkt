/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =============================================================================
// ZONE DELEGATE TESTS  (Plan 13.4-01 Task 2)
//
// Covers REQ-10 / D-10: the `by zone { }` property delegate infers the zone ID
// from the Kotlin property name, mirroring MetaspriteDelegate exactly.
//
// Three behaviors locked here:
//   1. id-from-property-name: `val playZone by zone { }` produces ZoneRef.id == "playZone"
//   2. reuse guard: binding one ZoneDelegate instance to two `val` properties throws
//   3. outside-game guard: `val z by zone { }` outside `game { }` throws with
//      "must be called inside a game" in the message
//
// The string form `zone(id, block)` is NOT tested here (it already has coverage
// in existing WorldBuildersTest); this test class is strictly ZoneDelegate scope.
// =============================================================================

class ZoneDelegateTest {

    // =========================================================================
    // Behavior 1: zone ID is inferred from the Kotlin property name
    // =========================================================================

    @Test
    fun `by zone registers zone whose ZoneRef id equals the Kotlin property name`() {
        val ir =
            game("TestGame") {
                    val playZone by zone { tileset(asset("tiles/checker.png")) }
                    // Reference the ZoneRef to suppress unused-variable warning
                    @Suppress("UNUSED_VARIABLE") val _ref = playZone

                    val mainScene = scene("main") {}
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.zones.size, "expected exactly one zone in IR")
        assertEquals("playZone", ir.zones.single().id, "zone ID must equal the property name")
    }

    // =========================================================================
    // Behavior 2: reusing a single ZoneDelegate instance throws
    // =========================================================================

    @Test
    fun `ZoneDelegate reuse on second by binding throws IllegalStateException`() {
        val d = zone {}
        assertFailsWith<IllegalStateException> {
            game("Test") {
                    val a by d // first provideDelegate — OK
                    val b by d // second provideDelegate on SAME instance — must throw
                    val sScene = scene("s") {}
                    start = sScene
                }
                .build()
        }
    }

    // =========================================================================
    // Behavior 3: `by zone { }` outside a `game { }` block throws
    // =========================================================================

    @Test
    fun `by zone outside game block throws IllegalStateException with expected message`() {
        val ex =
            assertFailsWith<IllegalStateException> {
                val z by zone {}
                // Touch the reference to ensure provideDelegate actually fires
                @Suppress("UNUSED_VARIABLE") val _touch = z
            }
        assertContains(
            ex.message ?: "",
            "must be called inside a game",
            message = "exception message must mention 'must be called inside a game'",
        )
    }
}
