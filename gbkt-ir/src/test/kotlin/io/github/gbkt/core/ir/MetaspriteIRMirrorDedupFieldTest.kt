/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Plan 10.1-16 Task 2: MetaspriteIR.mirrorDedup field (per-metasprite DSL opt-in
// to allow png2asset's mirror-pair deduplication, i.e. SKIP the -noflip arg).
//
// Default behavior (mirrorDedup = false): Plan 10.1-16 Task 1's
// ConvertSpritesTask edit forces `-noflip` for all metasprite-bound PNGs --
// this preserves DSL faithfulness to the reference's -noflip id space and
// fixes DEF-10.1-13-A (garbled elephant pixels).
//
// Opt-in behavior (mirrorDedup = true): Task 4's ConvertSpritesTask edit reads
// this flag and OMITS `-noflip` for the metasprite, allowing png2asset to dedup
// mirror-pair tiles. Games that author metasprites from-scratch (not transcribed
// from a reference's -noflip output) may legitimately want this to save ROM.
//
// This IR-level test locks the field's default + override roundtrip. The DSL
// propagation test (`MetaspriteBuilder.mirrorDedup()` -> `MetaspriteIR.mirrorDedup`)
// lives in `:gbkt-lang/MetaspriteBuilderMirrorDedupTest.kt` per the leaf-module
// boundary (gbkt-ir cannot reach the DSL builders -- see Plan 10.1-03 split).
// =============================================================================

class MetaspriteIRMirrorDedupFieldTest {

    @Test
    fun `metaspriteIR_mirrorDedup_defaults_to_false`() {
        val ms = MetaspriteIR(id = "x", frames = emptyList())
        assertFalse(
            ms.mirrorDedup,
            "mirrorDedup must default to false so existing metasprites (which never " +
                "opted in via the DSL builder) continue to get the -noflip arg from " +
                "ConvertSpritesTask -- preserving the DEF-10.1-13-A fix unconditionally.",
        )
    }

    @Test
    fun `metaspriteIR_mirrorDedup_can_be_overridden_to_true`() {
        val ms = MetaspriteIR(id = "x", frames = emptyList(), mirrorDedup = true)
        assertTrue(
            ms.mirrorDedup,
            "mirrorDedup must be overridable to true via the constructor so the DSL " +
                "builder (`metasprite { mirrorDedup() }`) can propagate user intent to " +
                "the IR layer. Task 4 reads this flag at codegen time to gate -noflip.",
        )
    }

    @Test
    fun `metaspriteIR_mirrorDedup_does_not_affect_other_fields`() {
        // Regression guard: adding mirrorDedup must not silently shift any
        // existing positional/default field. Construct with the existing field
        // pattern (id, frames, sourceLocation = ...) and assert all 4 var-ref
        // fields stay null and mirrorDedup stays at its default.
        val ms =
            MetaspriteIR(
                id = "x",
                frames = listOf(MetaspriteFrame(tiles = listOf(MetaspriteTile(0, 0, 0)))),
                sourceLocation = SourceLocation(file = "f", line = 1, col = 1),
            )
        assertEquals("x", ms.id)
        assertEquals(1, ms.frames.size)
        assertEquals(
            false,
            ms.mirrorDedup,
            "named-arg sourceLocation must not perturb mirrorDedup default",
        )
        assertEquals(null, ms.posXVarName, "var-ref fields must stay at null default")
        assertEquals(null, ms.posYVarName)
        assertEquals(null, ms.idxVarName)
        assertEquals(null, ms.rotVarName)
        assertEquals("f", ms.sourceLocation?.file)
    }
}
