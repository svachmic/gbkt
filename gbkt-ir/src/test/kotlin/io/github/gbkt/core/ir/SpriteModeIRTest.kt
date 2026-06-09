/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// =============================================================================
// Plan 12.5-02 Task 1 RED: SpriteMode enum in gbkt-ir leaf module.
//
// D-15 (CONTEXT.md): SpriteMode must live in gbkt-ir as the single source of
// truth so MetaspriteIR (in gbkt-ir) can reference it without layer violations.
// ConvertSpritesTask imports it from io.github.gbkt.core.ir; the prior
// `internal enum class SpriteMode` in ConvertSpritesTask is deleted.
//
// Behavior contracts:
//   1. io.github.gbkt.core.ir.SpriteMode exists with exactly two values:
//      SPR8x8 and SPR8x16. The enum is public (not internal).
//   2. Both values are non-null and have the expected names.
//   3. The enum participates in standard Kotlin enum semantics
//      (values(), valueOf(), ordinal, name).
// =============================================================================

class SpriteModeIRTest {

    @Test
    fun `SpriteMode has exactly two values SPR8x8 and SPR8x16`() {
        val values = SpriteMode.entries
        assertEquals(2, values.size, "SpriteMode must have exactly 2 entries: SPR8x8 and SPR8x16")
    }

    @Test
    fun `SpriteMode SPR8x8 is the first entry`() {
        val spr8x8 = SpriteMode.SPR8x8
        assertNotNull(spr8x8, "SpriteMode.SPR8x8 must be non-null")
        assertEquals("SPR8x8", spr8x8.name, "First entry must be named SPR8x8")
        assertEquals(0, spr8x8.ordinal, "SPR8x8 must have ordinal 0")
    }

    @Test
    fun `SpriteMode SPR8x16 is the second entry`() {
        val spr8x16 = SpriteMode.SPR8x16
        assertNotNull(spr8x16, "SpriteMode.SPR8x16 must be non-null")
        assertEquals("SPR8x16", spr8x16.name, "Second entry must be named SPR8x16")
        assertEquals(1, spr8x16.ordinal, "SPR8x16 must have ordinal 1")
    }

    @Test
    fun `SpriteMode valueOf works for both entries`() {
        assertEquals(SpriteMode.SPR8x8, SpriteMode.valueOf("SPR8x8"))
        assertEquals(SpriteMode.SPR8x16, SpriteMode.valueOf("SPR8x16"))
    }

    @Test
    fun `SpriteMode is usable as MetaspriteIR spriteMode field type`() {
        // Regression guard: SpriteMode must be in the same package as MetaspriteIR
        // so the two IR types are co-located and no layer violation occurs.
        // Both types exist in io.github.gbkt.core.ir — confirm round-trip.
        val ms = MetaspriteIR(id = "test", frames = emptyList())
        // spriteMode defaults to null (migration window per D-04b)
        assertEquals(null, ms.spriteMode, "spriteMode must default to null for back-compat")
        // copy() with explicit SpriteMode
        val withMode = ms.copy(spriteMode = SpriteMode.SPR8x16)
        assertEquals(
            SpriteMode.SPR8x16,
            withMode.spriteMode,
            "copy(spriteMode = SPR8x16) must update the field",
        )
    }
}
