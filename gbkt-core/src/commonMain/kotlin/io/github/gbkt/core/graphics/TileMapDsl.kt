/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRVar

/**
 * Background control helpers for tilemap scrolling.
 *
 * Usage: every.frame { background.scroll(1, 0) // Auto-scroll right }
 */
object background {

    /**
     * Scroll background by delta amounts. Uses GBDK's scroll_bkg function for relative movement.
     */
    fun scroll(dx: Expr, dy: Expr) {
        RecordingContext.require().emit(IRCall("scroll_bkg", listOf(dx.ir, dy.ir)))
    }

    fun scroll(dx: Int, dy: Int) {
        scroll(Expr(IRLiteral(dx)), Expr(IRLiteral(dy)))
    }

    /** Set absolute scroll position. Uses GBDK's move_bkg function. */
    fun moveTo(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRCall("move_bkg", listOf(x.ir, y.ir)))
    }

    fun moveTo(x: Int, y: Int) {
        moveTo(Expr(IRLiteral(x)), Expr(IRLiteral(y)))
    }

    /** Show the background layer. */
    fun show() {
        RecordingContext.require().emit(IRCall("SHOW_BKG", emptyList()))
    }

    /** Hide the background layer. */
    fun hide() {
        RecordingContext.require().emit(IRCall("HIDE_BKG", emptyList()))
    }
}

/**
 * Camera helper for following a sprite with the background.
 *
 * Centers the camera on the sprite position, clamping to map bounds.
 *
 * Usage: every.frame { level1.followSprite(playerX, playerY) }
 */
fun TileMap.followSprite(spriteX: Expr, spriteY: Expr) {
    // Game Boy screen is 160x144 pixels (20x18 tiles)
    // Center point is 80x72
    // Camera position = sprite position - screen center
    val cameraX = spriteX - 80
    val cameraY = spriteY - 72

    scrollTo(cameraX, cameraY)
}

fun <T : Any, U : Any> TileMap.followSprite(spriteX: GBVar<T>, spriteY: GBVar<U>) {
    followSprite(Expr(IRVar(spriteX.name)), Expr(IRVar(spriteY.name)))
}

/**
 * Follow a sprite with bounds checking.
 *
 * @param spriteX Sprite X position
 * @param spriteY Sprite Y position
 * @param minX Minimum camera X (usually 0)
 * @param minY Minimum camera Y (usually 0)
 * @param maxX Maximum camera X (map width - screen width)
 * @param maxY Maximum camera Y (map height - screen height)
 */
fun TileMap.followSpriteWithBounds(
    spriteX: Expr,
    spriteY: Expr,
    minX: Int = 0,
    minY: Int = 0,
    maxX: Int = 96, // 32*8 - 160 = 96 for 32-tile wide map
    maxY: Int = 112, // 32*8 - 144 = 112 for 32-tile tall map
) {
    // Calculate desired camera position
    val desiredX = spriteX - 80
    val desiredY = spriteY - 72

    // Clamp to bounds using ternary expressions
    val clampedX =
        Expr(
            IRTernary(
                IRBinary(desiredX.ir, BinaryOp.LT, IRLiteral(minX)),
                IRLiteral(minX),
                IRTernary(
                    IRBinary(desiredX.ir, BinaryOp.GT, IRLiteral(maxX)),
                    IRLiteral(maxX),
                    desiredX.ir,
                ),
            )
        )

    val clampedY =
        Expr(
            IRTernary(
                IRBinary(desiredY.ir, BinaryOp.LT, IRLiteral(minY)),
                IRLiteral(minY),
                IRTernary(
                    IRBinary(desiredY.ir, BinaryOp.GT, IRLiteral(maxY)),
                    IRLiteral(maxY),
                    desiredY.ir,
                ),
            )
        )

    scrollTo(clampedX, clampedY)
}

/**
 * Parallax scrolling helper.
 *
 * Scrolls the background at a fraction of the sprite movement. Use values < 1.0 for distant
 * backgrounds, > 1.0 for foreground effects.
 *
 * Usage: // Scroll at half speed for parallax effect background.parallax(playerX, playerY, 0.5)
 */
fun parallaxScroll(spriteX: Expr, spriteY: Expr, factor: Double) {
    // Note: Game Boy has no floating point, so we use integer approximation
    // factor of 0.5 means shift right by 1
    // factor of 0.25 means shift right by 2
    val shiftAmount =
        when {
            factor <= 0.125 -> 3
            factor <= 0.25 -> 2
            factor <= 0.5 -> 1
            else -> 0
        }

    if (shiftAmount > 0) {
        val scrollX = Expr(IRBinary(spriteX.ir, BinaryOp.SHR, IRLiteral(shiftAmount)))
        val scrollY = Expr(IRBinary(spriteY.ir, BinaryOp.SHR, IRLiteral(shiftAmount)))
        background.moveTo(scrollX, scrollY)
    } else {
        background.moveTo(spriteX, spriteY)
    }
}

/**
 * Set a single tile in the background map at runtime.
 *
 * Useful for dynamic map changes like breaking blocks or opening doors.
 *
 * @param x Tile X position (0-31)
 * @param y Tile Y position (0-31)
 * @param tileIndex Index of the tile in the tileset (0-255)
 */
fun setBackgroundTile(x: Expr, y: Expr, tileIndex: Expr) {
    // Use a temporary variable to hold the tile index
    // This generates: unsigned char _temp_tile = tileIndex; set_bkg_tiles(x, y, 1, 1, &_temp_tile);
    // For simplicity, we use GBDK's set_tile_xy which is more direct
    RecordingContext.require().emit(IRRaw("set_bkg_tile_xy(${x.ir}, ${y.ir}, ${tileIndex.ir});"))
}

fun setBackgroundTile(x: Int, y: Int, tileIndex: Int) {
    RecordingContext.require().emit(IRRaw("set_bkg_tile_xy($x, $y, $tileIndex);"))
}

/**
 * Get a tile from the background map at runtime.
 *
 * @param x Tile X position
 * @param y Tile Y position
 * @return Expression representing the tile index
 */
fun getBackgroundTile(x: Int, y: Int): Expr {
    return Expr(IRCallExpr("get_bkg_tile_xy", listOf(IRLiteral(x), IRLiteral(y))))
}
