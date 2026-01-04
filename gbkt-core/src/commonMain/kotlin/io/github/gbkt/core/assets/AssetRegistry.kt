/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.assets

/**
 * Registry interface for validated assets.
 *
 * Implement this interface to create a type-safe assets object:
 * ```kotlin
 * object Assets : AssetRegistry {
 *     object Sprites {
 *         val player = SpriteAsset("player.png")
 *         val enemy = SpriteAsset("enemy.png")
 *     }
 *
 *     object Sounds {
 *         val jump = SoundAsset("jump.wav")
 *     }
 *
 *     override val sprites = mapOf(
 *         "player" to Sprites.player,
 *         "enemy" to Sprites.enemy
 *     )
 *     override val tiles = emptyMap()
 *     override val sounds = mapOf("jump" to Sounds.jump)
 *     override val music = emptyMap()
 * }
 * ```
 *
 * Alternatively, the Gradle plugin can auto-generate this from your assets folder.
 */
interface AssetRegistry {
    /** Map of sprite name to asset reference. */
    val sprites: Map<String, SpriteAsset>
        get() = emptyMap()

    /** Map of tile name to asset reference. */
    val tiles: Map<String, TileAsset>
        get() = emptyMap()

    /** Map of sound name to asset reference. */
    val sounds: Map<String, SoundAsset>
        get() = emptyMap()

    /** Map of music name to asset reference. */
    val music: Map<String, MusicAsset>
        get() = emptyMap()

    /** Map of font name to asset reference. */
    val fonts: Map<String, FontAsset>
        get() = emptyMap()

    /** Look up any asset by name. */
    operator fun get(name: String): AssetRef? {
        return sprites[name] ?: tiles[name] ?: sounds[name] ?: music[name] ?: fonts[name]
    }

    /** Get all assets as a flat list. */
    fun allAssets(): List<AssetRef> =
        sprites.values + tiles.values + sounds.values + music.values + fonts.values
}

/** Empty registry for games that don't use the asset registry system. */
object EmptyAssetRegistry : AssetRegistry
