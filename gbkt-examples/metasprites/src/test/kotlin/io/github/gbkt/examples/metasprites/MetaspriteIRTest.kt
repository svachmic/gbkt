/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.SpriteMode
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IR validation tests for the Metasprites DSL definition — re-pinned for the asset-driven elephant
 * (Plan 13.3-09, D-03 Pitfall 4).
 *
 * The elephant is now declared with `sprite(asset("sprites/elephant.png")) { mode/pivot/frameSize
 * }`
 * + `frames(5)`. The asset-driven path produces:
 * - `frames.isEmpty()` — no procedural tile transcription (D-08 mutual exclusion)
 * - `spritePath == "sprites/elephant.png"` — asset path captured
 * - `frameCount == 5` — author-declared count for build-time cross-validation
 * - cutting flags: `spriteMode == SPR8x8`, `frameWidth == 64`, `frameHeight == 48`, `pivot ==
 *   (32,24)`
 *
 * All non-frame assertions from the previous test (palettes, variables, scene, GBC target) are
 * preserved — they remain valid after the migration.
 */
class MetaspriteIRTest {

    private val ir = metasprites.build()

    @Test
    fun `has 1 metasprite`() {
        assertEquals(1, ir.metasprites.size)
    }

    @Test
    fun `elephant frames are empty (asset-driven path, no tile transcription)`() {
        assertTrue(ir.metasprites.first { it.id == "elephant" }.frames.isEmpty())
    }

    @Test
    fun `elephant sprite path is elephant png`() {
        assertEquals(
            "sprites/elephant.png",
            ir.metasprites.first { it.id == "elephant" }.spritePath,
        )
    }

    @Test
    fun `elephant frame count is 5`() {
        assertEquals(5, ir.metasprites.first { it.id == "elephant" }.frameCount)
    }

    @Test
    fun `elephant sprite mode is SPR8x8`() {
        assertEquals(SpriteMode.SPR8x8, ir.metasprites.first { it.id == "elephant" }.spriteMode)
    }

    @Test
    fun `elephant frame width is 64`() {
        assertEquals(64, ir.metasprites.first { it.id == "elephant" }.frameWidth)
    }

    @Test
    fun `elephant frame height is 48`() {
        assertEquals(48, ir.metasprites.first { it.id == "elephant" }.frameHeight)
    }

    @Test
    fun `elephant pivot is 32 24`() {
        val elephant = ir.metasprites.first { it.id == "elephant" }
        assertEquals(32, elephant.pivotX)
        assertEquals(24, elephant.pivotY)
    }

    @Test
    fun `has 4 palettes all of type SPRITE`() {
        assertEquals(4, ir.palettes.size)
        assertTrue(ir.palettes.all { it.type == PaletteType.SPRITE })
    }

    @Test
    fun `has idx variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "idx" && it.type == VarType.U8 })
    }

    @Test
    fun `has rot variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "rot" && it.type == VarType.U8 })
    }

    @Test
    fun `has posX variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "posX" && it.type == VarType.I16 })
    }

    @Test
    fun `has posY variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "posY" && it.type == VarType.I16 })
    }

    @Test
    fun `has 1 scene`() {
        assertEquals(1, ir.scenes.size)
    }

    @Test
    fun `start scene is play`() {
        assertEquals("play", ir.startScene)
    }

    @Test
    fun `gbc target is GBC_COMPATIBLE`() {
        assertEquals(GbcTarget.GBC_COMPATIBLE, ir.config.gbcTarget)
    }

    @Test
    fun `play scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "play" }.enterOps.isNotEmpty())
    }

    @Test
    fun `play scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "play" }.frameOps.isNotEmpty())
    }
}
