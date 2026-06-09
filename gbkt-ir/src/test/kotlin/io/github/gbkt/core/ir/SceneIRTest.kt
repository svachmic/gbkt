/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

// =============================================================================
// SCENE IR EQUALITY TESTS
// Verifies that SceneIR.equals() and hashCode() discriminate by zoneRefs.
// Guards against silent IR-equality bugs per RESEARCH §Pitfall 1 + §R6
// (manual equals/hashCode overrides required because collisionData: ByteArray?
// demands contentEquals — adding a field without updating both overrides is a
// silent IR-equality bug; this test catches it).
// Phase 11.1 Plan 04.
// =============================================================================

class SceneIRTest {

    @Test
    fun `SceneIRs with differing zoneRefs are NOT equal`() {
        val a = SceneIR(id = "test", zoneRefs = emptyList())
        val b = SceneIR(id = "test", zoneRefs = listOf("zone1"))
        assertFalse(a == b, "SceneIR equality must discriminate by zoneRefs")
        assertNotEquals(
            a.hashCode(),
            b.hashCode(),
            "SceneIR hashCodes should differ when zoneRefs differ",
        )
    }

    // =========================================================================
    // Phase 13.8 Plan 06 — allocatedZoneBank field equality guard (Req 6, D-01)
    //
    // Pitfall-1 mitigation: SceneIR has a handwritten equals/hashCode (required
    // because collisionData: ByteArray? needs contentEquals). The new
    // allocatedZoneBank field MUST be added to BOTH methods manually; the
    // generated data-class equals is overridden and does NOT auto-include it.
    //
    // This test catches a missed update: two SceneIRs differing ONLY in
    // allocatedZoneBank must NOT compare equal.
    // =========================================================================
    @Test
    fun `SceneIRs with differing allocatedZoneBank are NOT equal`() {
        val a = SceneIR(id = "gameplay", allocatedZoneBank = 2)
        val b = SceneIR(id = "gameplay", allocatedZoneBank = 3)
        assertFalse(a == b, "SceneIR equality must discriminate by allocatedZoneBank")
        assertNotEquals(
            a.hashCode(),
            b.hashCode(),
            "SceneIR hashCodes should differ when allocatedZoneBank differs",
        )
    }

    @Test
    fun `SceneIR with allocatedZoneBank null compiles unchanged - default is null`() {
        // All existing call sites compile unchanged: SceneIR(id = "x") has allocatedZoneBank = null
        val scene = SceneIR(id = "any-scene")
        assertFalse(
            scene.allocatedZoneBank != null,
            "Default allocatedZoneBank must be null (zero ripple to existing call sites)",
        )
    }
}
