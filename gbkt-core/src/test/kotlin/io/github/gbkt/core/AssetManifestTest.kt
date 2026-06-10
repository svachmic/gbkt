/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetManifestTest {

    @Test
    fun `round-trip for sprite entry - fromJson(toJson()) equals original`() {
        val entry =
            AssetManifestEntry.SpriteEntry(
                path = "sprites/ball.png",
                tileCount = 4,
                uniqueTileCount = 3,
                widthInTiles = 2,
                heightInTiles = 2,
                palette = listOf(0, 85, 170, 255),
                frameWidth = 8,
                frameHeight = 8,
                frameCount = 4,
            )
        val manifest = AssetManifest(assets = listOf(entry))
        val roundTripped = AssetManifest.fromJson(manifest.toJson())
        assertEquals(manifest, roundTripped)
    }

    @Test
    fun `round-trip for tilemap entry - fromJson(toJson()) equals original`() {
        val entry =
            AssetManifestEntry.TilemapEntry(
                path = "maps/level1.tmx",
                width = 20,
                height = 18,
                hasCollision = true,
                tilesetPath = "sprites/dungeon.png",
            )
        val manifest = AssetManifest(assets = listOf(entry))
        val roundTripped = AssetManifest.fromJson(manifest.toJson())
        assertEquals(manifest, roundTripped)
    }

    @Test
    fun `JSON output has version=1 at root`() {
        val manifest = AssetManifest(assets = emptyList())
        val json = manifest.toJson()
        assertTrue(json.contains("\"version\""), "JSON should contain version field")
        // Parse it back and check version
        val back = AssetManifest.fromJson(json)
        assertEquals(1, back.version)
    }

    @Test
    fun `SpriteEntry includes frameWidth, frameHeight, frameCount`() {
        val entry =
            AssetManifestEntry.SpriteEntry(
                path = "sprites/player.png",
                tileCount = 8,
                uniqueTileCount = 6,
                widthInTiles = 4,
                heightInTiles = 2,
                palette = listOf(0, 85, 170, 255),
                frameWidth = 16,
                frameHeight = 16,
                frameCount = 2,
            )
        val manifest = AssetManifest(assets = listOf(entry))
        val json = manifest.toJson()
        assertTrue(json.contains("frameWidth"), "JSON should contain frameWidth")
        assertTrue(json.contains("frameHeight"), "JSON should contain frameHeight")
        assertTrue(json.contains("frameCount"), "JSON should contain frameCount")
        val back = AssetManifest.fromJson(json)
        val backEntry = back.assets.first() as AssetManifestEntry.SpriteEntry
        assertEquals(16, backEntry.frameWidth)
        assertEquals(16, backEntry.frameHeight)
        assertEquals(2, backEntry.frameCount)
    }

    @Test
    fun `empty manifest serializes and deserializes correctly`() {
        val manifest = AssetManifest(assets = emptyList())
        val roundTripped = AssetManifest.fromJson(manifest.toJson())
        assertEquals(manifest, roundTripped)
        assertEquals(0, roundTripped.assets.size)
    }

    @Test
    fun `tilemap entry with null tilesetPath round-trips correctly`() {
        val entry =
            AssetManifestEntry.TilemapEntry(
                path = "maps/simple.tmx",
                width = 8,
                height = 8,
                hasCollision = false,
                tilesetPath = null,
            )
        val manifest = AssetManifest(assets = listOf(entry))
        val back = AssetManifest.fromJson(manifest.toJson())
        val backEntry = back.assets.first() as AssetManifestEntry.TilemapEntry
        assertEquals(null, backEntry.tilesetPath)
    }

    @Test
    fun `manifest with mixed entry types round-trips correctly`() {
        val sprite =
            AssetManifestEntry.SpriteEntry(
                path = "sprites/enemy.png",
                tileCount = 2,
                uniqueTileCount = 2,
                widthInTiles = 1,
                heightInTiles = 2,
                palette = listOf(0, 85, 170, 255),
                frameWidth = 8,
                frameHeight = 16,
                frameCount = 1,
            )
        val tilemap =
            AssetManifestEntry.TilemapEntry(
                path = "maps/floor1.tmx",
                width = 32,
                height = 32,
                hasCollision = true,
                tilesetPath = "sprites/dungeon.png",
            )
        val manifest = AssetManifest(assets = listOf(sprite, tilemap))
        val back = AssetManifest.fromJson(manifest.toJson())
        assertEquals(2, back.assets.size)
        assertTrue(back.assets[0] is AssetManifestEntry.SpriteEntry)
        assertTrue(back.assets[1] is AssetManifestEntry.TilemapEntry)
    }

    @Test
    fun `MANIFEST_FILENAME constant is asset-manifest dot json`() {
        assertEquals("asset-manifest.json", AssetManifest.MANIFEST_FILENAME)
    }
}
