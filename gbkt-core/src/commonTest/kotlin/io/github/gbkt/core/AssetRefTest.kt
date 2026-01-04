/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.assets.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

// Test assets defined at file level (not local)
private object TestAssets {
    val player = SpriteAsset("player.png")
    val hero = SpriteAsset("hero.png")
    val bullet = SpriteAsset("bullet.png")
}

// Assets pattern for testing
private object Assets {
    object Sprites {
        val player = SpriteAsset("player.png")
        val enemy = SpriteAsset("enemy.png")
        val bullet = SpriteAsset("bullet.png")
    }

    object Sounds {
        val jump = SoundAsset("jump.wav")
        val hit = SoundAsset("hit.wav")
    }

    object Music {
        val title = MusicAsset("title.mod")
        val gameplay = MusicAsset("gameplay.mod")
    }
}

/**
 * Tests for type-safe asset references.
 *
 * Validates:
 * - SpriteAsset holds path correctly
 * - AssetType enum values are correct
 * - Asset equality and hashCode work
 * - toString returns the path
 * - sprite() functions accept SpriteAsset
 * - Other asset types (Tile, Sound, Music, Font) work correctly
 */
class AssetRefTest {

    // =========================================================================
    // SPRITE ASSET
    // =========================================================================

    @Test
    fun `SpriteAsset holds path correctly`() {
        val asset = SpriteAsset("player.png")
        assertEquals("player.png", asset.path, "Path should be 'player.png'")
    }

    @Test
    fun `SpriteAsset has correct type`() {
        val asset = SpriteAsset("sprite.png")
        assertEquals(AssetType.SPRITE, asset.type, "Type should be SPRITE")
    }

    @Test
    fun `SpriteAsset fromPath factory works`() {
        val asset = SpriteAsset.fromPath("assets/hero.png")
        assertEquals("assets/hero.png", asset.path)
        assertEquals(AssetType.SPRITE, asset.type)
    }

    @Test
    fun `SpriteAsset toString returns path`() {
        val asset = SpriteAsset("my_sprite.png")
        assertEquals("my_sprite.png", asset.toString())
    }

    @Test
    fun `SpriteAsset equality works`() {
        val asset1 = SpriteAsset("player.png")
        val asset2 = SpriteAsset("player.png")
        val asset3 = SpriteAsset("enemy.png")

        assertEquals(asset1, asset2, "Same path should be equal")
        assertNotEquals(asset1, asset3, "Different path should not be equal")
    }

    @Test
    fun `SpriteAsset hashCode works`() {
        val asset1 = SpriteAsset("player.png")
        val asset2 = SpriteAsset("player.png")

        assertEquals(asset1.hashCode(), asset2.hashCode(), "Same path should have same hashCode")
    }

    // =========================================================================
    // OTHER ASSET TYPES
    // =========================================================================

    @Test
    fun `TileAsset has correct type`() {
        val asset = TileAsset("tileset.png")
        assertEquals(AssetType.TILE, asset.type)
        assertEquals("tileset.png", asset.path)
    }

    @Test
    fun `SoundAsset has correct type`() {
        val asset = SoundAsset("jump.wav")
        assertEquals(AssetType.SOUND, asset.type)
        assertEquals("jump.wav", asset.path)
    }

    @Test
    fun `MusicAsset has correct type`() {
        val asset = MusicAsset("bgm.mod")
        assertEquals(AssetType.MUSIC, asset.type)
        assertEquals("bgm.mod", asset.path)
    }

    @Test
    fun `FontAsset has correct type`() {
        val asset = FontAsset("font.png")
        assertEquals(AssetType.FONT, asset.type)
        assertEquals("font.png", asset.path)
    }

    @Test
    fun `all asset types have fromPath factory`() {
        assertEquals("a.png", TileAsset.fromPath("a.png").path)
        assertEquals("b.wav", SoundAsset.fromPath("b.wav").path)
        assertEquals("c.mod", MusicAsset.fromPath("c.mod").path)
        assertEquals("d.png", FontAsset.fromPath("d.png").path)
    }

    // =========================================================================
    // INTEGRATION WITH SPRITE DSL
    // =========================================================================

    @Test
    @Suppress("DEPRECATION")
    fun `sprite function accepts SpriteAsset`() {
        val game =
            gbGame("SpriteAssetIntegrationTest") {
                // Use SpriteAsset with the sprite function
                val playerSprite = sprite(TestAssets.player) { size = 8 x 16 }

                assertTrue(playerSprite.asset == "player.png", "Sprite should use asset path")

                start = scene("main") { every.frame {} }
            }

        // Verify game builds with sprite
        assertEquals(1, game.sprites.size, "Should have 1 sprite")
        assertEquals("player.png", game.sprites[0].asset, "Sprite asset should be player.png")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `entity sprite function accepts SpriteAsset`() {
        val game =
            gbGame("EntitySpriteAssetTest") {
                val player by entity {
                    position(80, 72)
                    sprite(TestAssets.hero) { size = 8 x 16 }
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities.find { it.name == "player" }
        assertTrue(entity?.hasSprite == true, "Entity should have sprite")
        assertEquals("hero.png", entity?.sprite?.asset, "Entity sprite should use hero.png")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `pool sprite function accepts SpriteAsset`() {
        val game =
            gbGame("PoolSpriteAssetTest") {
                val bullets =
                    pool("bullets", 8) {
                        position(0, 0)
                        sprite(TestAssets.bullet) { size = 4 x 4 }
                    }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.pools.size, "Should have 1 pool")
        assertEquals("bullet.png", game.pools[0].spriteAsset, "Pool should use bullet.png sprite")
    }

    // =========================================================================
    // ASSETS OBJECT PATTERN
    // =========================================================================

    @Test
    fun `assets object pattern works correctly`() {
        // Verify all assets are accessible
        assertEquals("player.png", Assets.Sprites.player.path)
        assertEquals("enemy.png", Assets.Sprites.enemy.path)
        assertEquals("jump.wav", Assets.Sounds.jump.path)
        assertEquals("title.mod", Assets.Music.title.path)

        // Verify types
        assertEquals(AssetType.SPRITE, Assets.Sprites.player.type)
        assertEquals(AssetType.SOUND, Assets.Sounds.jump.type)
        assertEquals(AssetType.MUSIC, Assets.Music.title.type)
    }

    // =========================================================================
    // PATH FORMATS
    // =========================================================================

    @Test
    fun `asset paths support subdirectories`() {
        val asset = SpriteAsset("sprites/characters/player.png")
        assertEquals("sprites/characters/player.png", asset.path)
    }

    @Test
    fun `asset paths support various extensions`() {
        val png = SpriteAsset("sprite.png")
        val bmp = SpriteAsset("sprite.bmp")
        val wav = SoundAsset("sound.wav")
        val raw = SoundAsset("sound.raw")
        val mod = MusicAsset("music.mod")
        val s3m = MusicAsset("music.s3m")

        assertEquals("sprite.png", png.path)
        assertEquals("sprite.bmp", bmp.path)
        assertEquals("sound.wav", wav.path)
        assertEquals("sound.raw", raw.path)
        assertEquals("music.mod", mod.path)
        assertEquals("music.s3m", s3m.path)
    }
}
