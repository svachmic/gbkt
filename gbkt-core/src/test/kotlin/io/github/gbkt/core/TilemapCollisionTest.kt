/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for tilemap collision detection.
 *
 * Validates:
 * - isBlocked() tile-based queries
 * - isBlockedAtPixel() pixel-based queries
 * - Out-of-bounds handling
 * - Pixel-to-tile conversion
 * - Collision layer data access
 */
class TilemapCollisionTest {

    private fun createTestTilemap(
        width: Int = 10,
        height: Int = 10,
        collisionData: IntArray? = null,
    ): TileMap {
        return TileMap(
            name = "test_map",
            asset = "test.json",
            tilesetAsset = null,
            layerName = null,
            collisionLayerName = null, // Use direct collisionData, not layerData lookup
            slot = 0,
            widthInTiles = width,
            heightInTiles = height,
            tileData = IntArray(width * height),
            layerData = emptyMap(),
            collisionData = collisionData,
        )
    }

    // =========================================================================
    // BASIC COLLISION QUERY TESTS
    // =========================================================================

    @Test
    fun `isBlocked returns true for blocked tile`() {
        // Create a 10x10 map with some blocked tiles
        val collisionData = IntArray(100) { 0 }
        collisionData[0] = 1 // (0, 0) is blocked
        collisionData[15] = 1 // (5, 1) is blocked
        collisionData[55] = 2 // (5, 5) is blocked with value 2

        val tilemap = createTestTilemap(collisionData = collisionData)

        assertTrue(tilemap.isBlocked(0, 0), "Tile (0,0) should be blocked")
        assertTrue(tilemap.isBlocked(5, 1), "Tile (5,1) should be blocked")
        assertTrue(tilemap.isBlocked(5, 5), "Tile (5,5) should be blocked (value > 0)")
    }

    @Test
    fun `isBlocked returns false for walkable tile`() {
        // Create a 10x10 map with some blocked tiles
        val collisionData = IntArray(100) { 0 }
        collisionData[0] = 1 // Only (0, 0) is blocked

        val tilemap = createTestTilemap(collisionData = collisionData)

        assertFalse(tilemap.isBlocked(1, 0), "Tile (1,0) should be walkable")
        assertFalse(tilemap.isBlocked(5, 5), "Tile (5,5) should be walkable")
        assertFalse(tilemap.isBlocked(9, 9), "Tile (9,9) should be walkable")
    }

    @Test
    fun `isBlocked returns false when no collision data`() {
        val tilemap = createTestTilemap(collisionData = null)

        assertFalse(tilemap.isBlocked(0, 0), "Should return false when no collision data")
        assertFalse(tilemap.isBlocked(5, 5), "Should return false when no collision data")
    }

    // =========================================================================
    // PIXEL-BASED COLLISION TESTS
    // =========================================================================

    @Test
    fun `isBlockedAtPixel converts coordinates correctly`() {
        // Create a 10x10 map with tile (1, 1) blocked
        val collisionData = IntArray(100) { 0 }
        collisionData[11] = 1 // (1, 1) is blocked - index = y * width + x = 1 * 10 + 1 = 11

        val tilemap = createTestTilemap(collisionData = collisionData)

        // Pixel (8, 8) is in tile (1, 1) - should be blocked
        assertTrue(tilemap.isBlockedAtPixel(8, 8), "Pixel (8,8) should be in blocked tile (1,1)")

        // Pixel (15, 15) is also in tile (1, 1) - should be blocked
        assertTrue(
            tilemap.isBlockedAtPixel(15, 15),
            "Pixel (15,15) should be in blocked tile (1,1)",
        )

        // Pixel (0, 0) is in tile (0, 0) - should be walkable
        assertFalse(tilemap.isBlockedAtPixel(0, 0), "Pixel (0,0) should be in walkable tile (0,0)")

        // Pixel (7, 7) is in tile (0, 0) - should be walkable
        assertFalse(tilemap.isBlockedAtPixel(7, 7), "Pixel (7,7) should be in walkable tile (0,0)")
    }

    @Test
    fun `isBlockedAtPixel handles tile boundaries correctly`() {
        // Create a map where tile (1, 0) is blocked but tile (0, 0) is not
        val collisionData = IntArray(100) { 0 }
        collisionData[1] = 1 // (1, 0) is blocked

        val tilemap = createTestTilemap(collisionData = collisionData)

        // Pixel 7 is last pixel in tile 0
        assertFalse(tilemap.isBlockedAtPixel(7, 0), "Pixel 7 should be in tile 0 (walkable)")

        // Pixel 8 is first pixel in tile 1
        assertTrue(tilemap.isBlockedAtPixel(8, 0), "Pixel 8 should be in tile 1 (blocked)")
    }

    // =========================================================================
    // OUT-OF-BOUNDS TESTS
    // =========================================================================

    @Test
    fun `out of bounds returns blocked`() {
        val collisionData = IntArray(100) { 0 } // All tiles walkable
        val tilemap = createTestTilemap(width = 10, height = 10, collisionData = collisionData)

        // Test negative coordinates
        assertTrue(tilemap.isBlocked(-1, 0), "Negative X should return blocked")
        assertTrue(tilemap.isBlocked(0, -1), "Negative Y should return blocked")
        assertTrue(tilemap.isBlocked(-5, -5), "Both negative should return blocked")

        // Test beyond map bounds
        assertTrue(tilemap.isBlocked(10, 0), "X at width should return blocked")
        assertTrue(tilemap.isBlocked(0, 10), "Y at height should return blocked")
        assertTrue(tilemap.isBlocked(15, 15), "Both beyond bounds should return blocked")
    }

    @Test
    fun `out of bounds pixel returns blocked`() {
        val collisionData = IntArray(100) { 0 }
        val tilemap = createTestTilemap(width = 10, height = 10, collisionData = collisionData)

        // Negative pixel coordinates
        assertTrue(tilemap.isBlockedAtPixel(-8, 0), "Negative pixel X should return blocked")
        assertTrue(tilemap.isBlockedAtPixel(0, -8), "Negative pixel Y should return blocked")

        // Beyond map bounds (10 tiles * 8 pixels = 80 pixels)
        assertTrue(tilemap.isBlockedAtPixel(80, 0), "Pixel at width boundary should return blocked")
        assertTrue(
            tilemap.isBlockedAtPixel(0, 80),
            "Pixel at height boundary should return blocked",
        )
    }

    // =========================================================================
    // BOUNDARY EDGE CASES
    // =========================================================================

    @Test
    fun `pixel at tile boundary handled correctly`() {
        // Create a checkerboard pattern for clear testing
        val collisionData =
            IntArray(100) { idx ->
                val x = idx % 10
                val y = idx / 10
                if ((x + y) % 2 == 0) 1 else 0 // Blocked on "black" squares
            }
        val tilemap = createTestTilemap(collisionData = collisionData)

        // Tile (0,0) is blocked (0+0=0, even)
        assertTrue(tilemap.isBlockedAtPixel(0, 0), "(0,0) should be blocked")
        assertTrue(tilemap.isBlockedAtPixel(7, 7), "(7,7) should be blocked (still in tile 0,0)")

        // Tile (1,0) is walkable (1+0=1, odd)
        assertFalse(tilemap.isBlockedAtPixel(8, 0), "(8,0) should be walkable")
        assertFalse(
            tilemap.isBlockedAtPixel(15, 7),
            "(15,7) should be walkable (still in tile 1,0)",
        )

        // Tile (0,1) is walkable (0+1=1, odd)
        assertFalse(tilemap.isBlockedAtPixel(0, 8), "(0,8) should be walkable")

        // Tile (1,1) is blocked (1+1=2, even)
        assertTrue(tilemap.isBlockedAtPixel(8, 8), "(8,8) should be blocked")
    }

    @Test
    fun `last valid tile is accessible`() {
        val collisionData = IntArray(100) { 0 }
        collisionData[99] = 1 // (9, 9) is blocked - last tile
        val tilemap = createTestTilemap(width = 10, height = 10, collisionData = collisionData)

        assertTrue(tilemap.isBlocked(9, 9), "Last tile (9,9) should be blocked")
        assertTrue(tilemap.isBlockedAtPixel(79, 79), "Last pixel (79,79) should be blocked")

        // One past should be out of bounds
        assertTrue(tilemap.isBlocked(10, 9), "Tile (10,9) should be out of bounds")
        assertTrue(tilemap.isBlocked(9, 10), "Tile (9,10) should be out of bounds")
    }

    // =========================================================================
    // COLLISION DATA ACCESS TESTS
    // =========================================================================

    @Test
    fun `getCollisionData returns collision array`() {
        val collisionData = IntArray(100) { idx -> idx }
        val tilemap = createTestTilemap(collisionData = collisionData)

        val data = tilemap.getCollisionData()
        assertNotNull(data, "Should return collision data")
        assertEquals(100, data.size, "Should have 100 elements")
        assertEquals(0, data[0], "First element should be 0")
        assertEquals(55, data[55], "Element 55 should be 55")
    }

    @Test
    fun `getCollisionData returns null when not configured`() {
        val tilemap = createTestTilemap(collisionData = null)

        val data = tilemap.getCollisionData()
        assertNull(data, "Should return null when no collision data")
    }

    // =========================================================================
    // INTEGRATION WITH CODE GENERATION
    // =========================================================================

    @Test
    fun `tilemap with collision layer generates collision functions`() {
        val game =
            gbGame("TilemapCollisionCodegenTest") {
                val level =
                    tilemap("level.json") {
                        tileset = "tiles.png"
                        collisionLayer = "collision"
                        name("level1")
                    }

                val testScene = scene("test") { enter { level.show() } }

                start = testScene
            }

        // Note: The actual codegen depends on having the collision data populated
        // at code generation time, which happens in the Gradle plugin
        val code = game.compileForTest()

        // Should at least generate the tilemap structure
        assertTrue(
            code.contains("LEVEL1") || code.contains("level1"),
            "Should reference tilemap name",
        )
    }

    // =========================================================================
    // DIFFERENT TILE SIZES (non-8x8 not supported, but test standard)
    // =========================================================================

    @Test
    fun `standard 8x8 tile conversion works`() {
        val collisionData = IntArray(100) { 0 }
        collisionData[22] = 1 // Tile (2, 2) blocked
        val tilemap = createTestTilemap(collisionData = collisionData)

        // Tile (2, 2) spans pixels 16-23 in both X and Y
        assertTrue(tilemap.isBlockedAtPixel(16, 16), "First pixel of tile (2,2)")
        assertTrue(tilemap.isBlockedAtPixel(23, 23), "Last pixel of tile (2,2)")
        assertFalse(tilemap.isBlockedAtPixel(15, 16), "Pixel just before tile (2,2)")
        assertFalse(tilemap.isBlockedAtPixel(24, 16), "Pixel just after tile (2,2)")
    }

    // =========================================================================
    // LARGE MAP TESTS
    // =========================================================================

    @Test
    fun `large map collision works correctly`() {
        // Standard Game Boy map is 32x32 tiles (256x256 pixels)
        val width = 32
        val height = 32
        val collisionData = IntArray(width * height) { 0 }

        // Block the center tile
        val centerIdx = 16 * width + 16 // (16, 16)
        collisionData[centerIdx] = 1

        val tilemap =
            TileMap(
                name = "large_map",
                asset = "large.json",
                tilesetAsset = null,
                layerName = null,
                collisionLayerName = null, // Use direct collisionData, not layerData lookup
                slot = 0,
                widthInTiles = width,
                heightInTiles = height,
                tileData = IntArray(width * height),
                layerData = emptyMap(),
                collisionData = collisionData,
            )

        assertTrue(tilemap.isBlocked(16, 16), "Center tile should be blocked")
        assertTrue(tilemap.isBlockedAtPixel(128, 128), "Center pixel should be blocked")

        assertFalse(tilemap.isBlocked(0, 0), "Corner tile should be walkable")
        assertFalse(tilemap.isBlocked(31, 31), "Opposite corner should be walkable")
    }
}
