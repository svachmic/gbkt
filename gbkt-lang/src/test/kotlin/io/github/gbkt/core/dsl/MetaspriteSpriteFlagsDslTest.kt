/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.SpriteMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// Plan 12.5-03 Task 2 (TDD RED): MetaspriteSpriteFlagsDslTest — DSL round-trip
// test for MetaspriteBuilder.sprite(AssetRef, block) → MetaspriteIR
// (spriteMode, pivotX, pivotY, frameWidth, frameHeight).
//
// Per D-04, D-04a, D-04b: the typed builders (mode(), pivot(), frameSize())
// inside the sprite() { } block must carry their values through the internal
// SpriteConfigBuilder fields into MetaspriteIR via build().
//
// Per feedback_no_magic_strings.md: mode() takes SpriteMode enum (not String);
// pivot() and frameSize() take Int params (not strings or DSL-internal indexes).
//
// Test 1: full flag set declared → all 5 IR fields carry the declared values.
// Test 2: no config block (migration-window path) → all 5 IR fields stay null.
// Test 3: partial config (pivot only) → pivotX/Y set, others null.
//
// All 3 tests are RED before Task 2 Step 2 (GREEN implementation) lands.
// =============================================================================

class MetaspriteSpriteFlagsDslTest {

    @Test
    fun `mode pivot frameSize are captured in MetaspriteIR`() {
        // Test 1: sprite block with full flag set → all 5 fields carried to IR.
        // D-08 update (Plan 13.3-06): asset-driven path must NOT include frame{} blocks.
        val g =
            game("Test") {
                val hero by metasprite {
                    sprite(asset("graphics/hero.png")) {
                        mode(SpriteMode.SPR8x16)
                        pivot(12, 6)
                        frameSize(24, 32)
                    }
                    // No frame{} — asset-driven path (D-08 exactly-one)
                }
                val playScene = scene("play") { frame { moveMetasprite(hero) } }
                start = playScene
            }
        val ir = g.build()

        val ms =
            ir.metasprites.firstOrNull { it.id == "hero" }
                ?: error("expected metasprite 'hero' in IR")
        assertEquals(
            SpriteMode.SPR8x16,
            ms.spriteMode,
            "mode(SpriteMode.SPR8x16) must reach MetaspriteIR.spriteMode",
        )
        assertEquals(12, ms.pivotX, "pivot(12, 6) x must reach MetaspriteIR.pivotX")
        assertEquals(6, ms.pivotY, "pivot(12, 6) y must reach MetaspriteIR.pivotY")
        assertEquals(24, ms.frameWidth, "frameSize(24, 32) w must reach MetaspriteIR.frameWidth")
        assertEquals(32, ms.frameHeight, "frameSize(24, 32) h must reach MetaspriteIR.frameHeight")
        assertEquals(
            "graphics/hero.png",
            ms.spritePath,
            "sprite(asset(...)) must still capture the asset path",
        )
    }

    @Test
    fun `sprite block with no config leaves flags null`() {
        // Test 2: sprite(asset("foo.png")) with no config block → migration-window path.
        // All 5 new IR fields must stay null (Plan 06 will enforce non-null at codegen time).
        // D-08 update (Plan 13.3-06): asset-driven path must NOT include frame{} blocks.
        val g =
            game("Test") {
                val player by metasprite {
                    sprite(asset("sprites/player.png"))
                    // No frame{} — asset-driven path (D-08 exactly-one)
                }
                val playScene = scene("play") { frame { moveMetasprite(player) } }
                start = playScene
            }
        val ir = g.build()

        val ms =
            ir.metasprites.firstOrNull { it.id == "player" }
                ?: error("expected metasprite 'player' in IR")
        assertNull(ms.spriteMode, "no mode() in sprite block → spriteMode must be null")
        assertNull(ms.pivotX, "no pivot() in sprite block → pivotX must be null")
        assertNull(ms.pivotY, "no pivot() in sprite block → pivotY must be null")
        assertNull(ms.frameWidth, "no frameSize() in sprite block → frameWidth must be null")
        assertNull(ms.frameHeight, "no frameSize() in sprite block → frameHeight must be null")
    }

    @Test
    fun `partial config leaves unset flags null`() {
        // Test 3: only pivot() declared → pivotX/Y set, spriteMode/frameWidth/frameHeight = null.
        // No implicit defaults: Plan 06 catches missing fields at codegen time.
        // D-08 update (Plan 13.3-06): asset-driven path must NOT include frame{} blocks.
        val g =
            game("Test") {
                val npc by metasprite {
                    sprite(asset("sprites/npc.png")) { pivot(12, 6) }
                    // No frame{} — asset-driven path (D-08 exactly-one)
                }
                val playScene = scene("play") { frame { moveMetasprite(npc) } }
                start = playScene
            }
        val ir = g.build()

        val ms =
            ir.metasprites.firstOrNull { it.id == "npc" }
                ?: error("expected metasprite 'npc' in IR")
        assertEquals(12, ms.pivotX, "pivot(12, 6) x must reach MetaspriteIR.pivotX")
        assertEquals(6, ms.pivotY, "pivot(12, 6) y must reach MetaspriteIR.pivotY")
        assertNull(ms.spriteMode, "no mode() declared → spriteMode must be null")
        assertNull(ms.frameWidth, "no frameSize() declared → frameWidth must be null")
        assertNull(ms.frameHeight, "no frameSize() declared → frameHeight must be null")
    }
}
