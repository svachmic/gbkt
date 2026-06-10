/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertNull

// =============================================================================
// Plan 10.1-03: MetaspriteIR + MoveMetasprite var-ref name fields (substrate
// for CR-03 / WR-01).
//
// Asserts the 4 nullable String? var-ref-name fields default to null on both
// MetaspriteIR and MoveMetasprite — back-compat for the Phase 10 port (which
// does NOT call the new DSL binders and must continue to type-check).
//
// The DSL-level propagation test (build a `game { val s by metasprite { posX(myX) ... } }`
// and assert the resolved MetaspriteIR + MoveMetasprite carry the captured names)
// lives in :gbkt-lang (MetaspriteBuilderVarRefFieldsTest) — the IR module is the
// leaf and cannot reach the DSL builders by design. The plan explicitly
// anticipated this split (acceptance line: "2 in IR module + 1 in lang module").
// =============================================================================

class MetaspriteIRVarRefFieldsTest {

    @Test
    fun `metaspriteIR_var_ref_name_fields_default_null`() {
        val ms = MetaspriteIR(id = "x", frames = emptyList())
        assertNull(ms.posXVarName, "posXVarName must default to null for Phase 10 back-compat")
        assertNull(ms.posYVarName, "posYVarName must default to null for Phase 10 back-compat")
        assertNull(ms.idxVarName, "idxVarName must default to null for Phase 10 back-compat")
        assertNull(ms.rotVarName, "rotVarName must default to null for Phase 10 back-compat")
    }

    @Test
    fun `moveMetasprite_var_ref_fields_default_null`() {
        val op = MoveMetasprite(metaspriteId = "x")
        assertNull(op.posXVar, "posXVar must default to null for Phase 10 back-compat")
        assertNull(op.posYVar, "posYVar must default to null for Phase 10 back-compat")
        assertNull(op.idxVar, "idxVar must default to null for Phase 10 back-compat")
        assertNull(op.rotVar, "rotVar must default to null for Phase 10 back-compat")
    }
}
