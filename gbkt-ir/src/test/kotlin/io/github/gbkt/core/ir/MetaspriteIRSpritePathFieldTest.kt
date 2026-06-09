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
// Plan 12.4-01 Task 1: MetaspriteIR.spritePath field (additive nullable field
// for DSL-bound PNG asset path — foundation for Phase 12.4 png2asset pipeline).
//
// REQ-3 acceptance gate (IR level): the field exists, defaults to null (migration
// window per D-01b), and participates in data class equality/copy semantics.
//
// The DSL-propagation test (`MetaspriteBuilder.sprite(AssetRef)` → MetaspriteIR)
// lives in :gbkt-lang (MetaspriteSpriteDslTest) per the leaf-module boundary
// (gbkt-ir cannot reach the DSL builders — see Plan 10.1-03 split).
// =============================================================================

class MetaspriteIRSpritePathFieldTest {

    @Test
    fun `spritePath_defaults_to_null_without_explicit_argument`() {
        // Migration window (D-01b): existing metasprites that do NOT call sprite()
        // must continue to compile and resolve spritePath = null.
        val ms = MetaspriteIR(id = "hero", frames = emptyList())
        assertNull(
            ms.spritePath,
            "spritePath must default to null for back-compat — metasprites that do not call " +
                "sprite(asset(...)) must continue to type-check without modification",
        )
    }

    @Test
    fun `spritePath_captures_explicit_value_when_provided`() {
        val ms = MetaspriteIR(id = "hero", frames = emptyList(), spritePath = "sprites/hero.png")
        assertEquals(
            "sprites/hero.png",
            ms.spritePath,
            "spritePath must carry the explicit path string through the data class",
        )
    }

    @Test
    fun `spritePath_participates_in_data_class_copy_semantics`() {
        // data class copy() must include the new field so downstream plans
        // (sidecar emitter, codegen pipeline) can transform the IR without
        // losing other fields.
        val original = MetaspriteIR(id = "foo", frames = emptyList())
        val withPath = original.copy(spritePath = "sprites/foo.png")
        assertEquals(
            "sprites/foo.png",
            withPath.spritePath,
            "copy(spritePath=...) must update the field",
        )
        assertEquals("foo", withPath.id, "copy() must preserve the id field")
        // Reset path to null via copy
        val cleared = withPath.copy(spritePath = null)
        assertNull(cleared.spritePath, "copy(spritePath = null) must clear the field back to null")
    }

    @Test
    fun `spritePath_does_not_perturb_other_fields`() {
        // Regression guard: adding spritePath must not shift any existing
        // named-arg field. Construct with the existing pattern and assert all
        // other fields stay at their defaults.
        val ms =
            MetaspriteIR(
                id = "x",
                frames = listOf(MetaspriteFrame(tiles = listOf(MetaspriteTile(0, 0, 0)))),
                sourceLocation = SourceLocation(file = "f", line = 1, col = 1),
            )
        assertEquals("x", ms.id)
        assertEquals(1, ms.frames.size)
        assertNull(ms.spritePath, "spritePath must default to null")
        assertNull(ms.posXVarName, "posXVarName must stay null")
        assertNull(ms.posYVarName, "posYVarName must stay null")
        assertNull(ms.idxVarName, "idxVarName must stay null")
        assertNull(ms.rotVarName, "rotVarName must stay null")
        assertEquals(false, ms.mirrorDedup, "mirrorDedup must stay false")
        assertEquals("f", ms.sourceLocation?.file)
    }
}
