/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.i16
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8
import io.github.gbkt.core.ir.x
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

// =============================================================================
// GAME BOY HARDWARE CONSTANTS
// =============================================================================

/** Game Boy hardware constraints for realistic test value generation. */
object GBConstants {
    /** GB screen width in pixels. */
    const val SCREEN_WIDTH = 160

    /** GB screen height in pixels. */
    const val SCREEN_HEIGHT = 144

    /** Maximum OAM (sprite) slots available on GB hardware. */
    const val MAX_OAM_SLOTS = 40

    /** Maximum palette slots for GBC. */
    const val MAX_PALETTES = 8

    /** Valid sprite sizes in pixels (GB hardware constraint). */
    val SPRITE_SIZES = listOf(8, 16, 32)

    /** Maximum tiles in VRAM. */
    const val MAX_TILES = 256
}

// =============================================================================
// TIER 1: DOMAIN-SPECIFIC GENERATORS (Realistic Values)
// =============================================================================

/** Screen X coordinate (0-159). */
fun Arb.Companion.screenX(): Arb<u8> = int(0, GBConstants.SCREEN_WIDTH - 1).map { u8(it) }

/** Screen Y coordinate (0-143). */
fun Arb.Companion.screenY(): Arb<u8> = int(0, GBConstants.SCREEN_HEIGHT - 1).map { u8(it) }

/** Typical walking speed (1-4 pixels per frame). */
fun Arb.Companion.walkSpeed(): Arb<Int> = int(1, 4)

/** Entity velocity (-8 to 8 pixels per frame). */
fun Arb.Companion.velocity(): Arb<i8> = int(-8, 8).map { i8(it) }

/** Jump velocity (negative for upward movement, -16 to -4). */
fun Arb.Companion.jumpVelocity(): Arb<i8> = int(-16, -4).map { i8(it) }

/** Valid sprite dimensions (8x8, 8x16, 16x16, etc.). */
fun Arb.Companion.spriteDimensions(): Arb<Dimensions> =
    bind(element(8, 16, 32), element(8, 16)) { w, h -> w x h }

/** OAM slot index (0-39). */
fun Arb.Companion.oamSlot(): Arb<Int> = int(0, GBConstants.MAX_OAM_SLOTS - 1)

/** Palette slot index (0-7). */
fun Arb.Companion.paletteSlot(): Arb<Int> = int(0, GBConstants.MAX_PALETTES - 1)

/** Animation frame count (1-16 typical). */
fun Arb.Companion.animationFrameCount(): Arb<Int> = int(1, 16)

/** Tile index (0-255). */
fun Arb.Companion.tileIndex(): Arb<u8> = int(0, 255).map { u8(it) }

/** Gravity value (0.0-1.0 typical). */
fun Arb.Companion.gravity(): Arb<Float> = float(0f, 1f)

/** Friction coefficient (0.8-0.99 realistic). */
fun Arb.Companion.friction(): Arb<Float> = float(0.8f, 0.99f)

/** Bounce coefficient (0.0-0.8 realistic). */
fun Arb.Companion.bounce(): Arb<Float> = float(0f, 0.8f)

// =============================================================================
// TIER 2: EDGE CASE GENERATORS (Boundary Testing)
// =============================================================================

/** u8 boundary values for overflow/underflow testing. */
fun Arb.Companion.u8Edge(): Arb<u8> = element(u8(0), u8(1), u8(127), u8(128), u8(254), u8(255))

/** u8 values near overflow (250-255). */
fun Arb.Companion.u8Overflow(): Arb<u8> = int(250, 255).map { u8(it) }

/** u16 boundary values. */
fun Arb.Companion.u16Edge(): Arb<u16> =
    element(u16(0), u16(1), u16(255), u16(256), u16(32767), u16(32768), u16(65534), u16(65535))

/** i8 boundary values including sign change. */
fun Arb.Companion.i8Edge(): Arb<i8> = element(i8(-128), i8(-1), i8(0), i8(1), i8(127))

/** i16 boundary values. */
fun Arb.Companion.i16Edge(): Arb<i16> = element(i16(-32768), i16(-1), i16(0), i16(1), i16(32767))

/** Screen edge positions. */
fun Arb.Companion.screenEdgeX(): Arb<u8> = element(u8(0), u8(GBConstants.SCREEN_WIDTH - 1))

fun Arb.Companion.screenEdgeY(): Arb<u8> = element(u8(0), u8(GBConstants.SCREEN_HEIGHT - 1))

// =============================================================================
// TIER 3: COMPOSITE GENERATORS (Game Scenarios)
// =============================================================================

/** Test entity with realistic game values. */
data class TestEntity(val x: u8, val y: u8, val vx: i8, val vy: i8, val width: Int, val height: Int)

/** Generate a realistic game entity. */
fun Arb.Companion.entity(): Arb<TestEntity> =
    bind(screenX(), screenY(), velocity(), velocity(), element(8, 16), element(8, 16)) {
        x,
        y,
        vx,
        vy,
        w,
        h ->
        TestEntity(x, y, vx, vy, w, h)
    }

/** Generate a static entity (no velocity). */
fun Arb.Companion.staticEntity(): Arb<TestEntity> =
    bind(screenX(), screenY(), element(8, 16), element(8, 16)) { x, y, w, h ->
        TestEntity(x, y, i8(0), i8(0), w, h)
    }

/** Generate two entities guaranteed to collide (overlapping). */
fun Arb.Companion.collidingPair(): Arb<Pair<TestEntity, TestEntity>> =
    bind(
        int(20, GBConstants.SCREEN_WIDTH - 40),
        int(20, GBConstants.SCREEN_HEIGHT - 40),
        element(8, 16),
    ) { x, y, size ->
        val e1 = TestEntity(u8(x), u8(y), i8(0), i8(0), size, size)
        // Second entity overlaps by placing it within the first entity's bounds
        val e2 = TestEntity(u8(x + size / 2), u8(y + size / 2), i8(0), i8(0), size, size)
        e1 to e2
    }

/** Generate two entities guaranteed NOT to collide (separated). */
fun Arb.Companion.separatedPair(): Arb<Pair<TestEntity, TestEntity>> =
    bind(int(0, 60), int(0, 50), element(8, 16)) { x, y, size ->
        val e1 = TestEntity(u8(x), u8(y), i8(0), i8(0), size, size)
        // Second entity is far away (at least 50 pixels gap)
        val e2 = TestEntity(u8(x + size + 50), u8(y + size + 50), i8(0), i8(0), size, size)
        e1 to e2
    }

/** Generate a realistic GBC palette with ascending brightness (dark → light). */
fun Arb.Companion.gbcPalette(): Arb<List<GBCColor>> =
    bind(int(0, 7), int(8, 15), int(16, 23), int(24, 31)) { d, m1, m2, l ->
        listOf(
            GBCColor.fromRGB888(d * 8, d * 8, d * 8),
            GBCColor.fromRGB888(m1 * 8, m1 * 8, m1 * 8),
            GBCColor.fromRGB888(m2 * 8, m2 * 8, m2 * 8),
            GBCColor.fromRGB888(l * 8, l * 8, l * 8),
        )
    }

// =============================================================================
// TIER 4: FULL-RANGE GENERATORS (Exhaustive Testing)
// =============================================================================

/** Full u8 range (0-255). */
fun Arb.Companion.u8Full(): Arb<u8> = int(0, 255).map { u8(it) }

/** Full u16 range (0-65535). */
fun Arb.Companion.u16Full(): Arb<u16> = int(0, 65535).map { u16(it) }

/** Full i8 range (-128 to 127). */
fun Arb.Companion.i8Full(): Arb<i8> = int(-128, 127).map { i8(it) }

/** Full i16 range (-32768 to 32767). */
fun Arb.Companion.i16Full(): Arb<i16> = int(-32768, 32767).map { i16(it) }

/** Full GBC color range (0-32767 RGB555). */
fun Arb.Companion.gbcColorFull(): Arb<GBCColor> = int(0, 0x7FFF).map { GBCColor(it) }

/** Full RGB888 component (0-255). */
fun Arb.Companion.rgb888Component(): Arb<Int> = int(0, 255)
