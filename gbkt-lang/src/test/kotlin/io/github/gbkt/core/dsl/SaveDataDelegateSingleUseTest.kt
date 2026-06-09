/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertFailsWith

// =============================================================================
// SAVE DATA DELEGATE SINGLE-USE TESTS  (Wave 0 RED — Plan 13.2-01 Task 1)
//
// Pins WR-06: `SaveDataDelegate` second `provideDelegate` (instance reuse)
// must throw `IllegalStateException`.
//
// Root cause (per 13.2-RESEARCH.md §"WR-06"): `SaveDataDelegate` stores
// `ref: SaveDataRef? = null`. A second `provideDelegate` call on the same
// instance silently overwrites `ref`, so the first `val a` now returns
// the second name's `SaveDataRef`. The first registration survives in
// `GameBuilder` under the FIRST name but the value object is wrong.
//
// This test is RED today because the `delegateUsed` guard does not yet exist
// in `SaveDataDelegate.provideDelegate`. Plan 13.2-02 adds the guard,
// turning this RED test GREEN.
//
// Reference decision: WR-06, D-04 (uniform single-use guard).
// =============================================================================

class SaveDataDelegateSingleUseTest {

    // =========================================================================
    // Behavior: second provideDelegate on same SaveDataDelegate instance throws
    // =========================================================================

    @Test
    fun `SaveDataDelegate provideDelegate on second call throws IllegalStateException`() {
        val d = saveData { slots(2) }
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
}
