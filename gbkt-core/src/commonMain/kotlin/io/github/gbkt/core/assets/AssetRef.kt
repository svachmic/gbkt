/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.assets

/**
 * Type-safe reference to an asset file.
 *
 * Asset references provide compile-time safety for asset paths, eliminating string typos that would
 * only be caught at build time.
 *
 * ## Usage
 *
 * Define your assets as type-safe references:
 * ```kotlin
 * object Assets {
 *     object Sprites {
 *         val player = SpriteAsset("player.png")
 *         val enemy = SpriteAsset("sprites/enemy.png")
 *     }
 *     object Sounds {
 *         val jump = SoundAsset("jump.wav")
 *     }
 * }
 * ```
 *
 * Then use them in your game:
 * ```kotlin
 * sprite(Assets.Sprites.player) { size = 8 x 16 }
 * ```
 *
 * The Gradle plugin can optionally generate these from your assets folder.
 */
sealed class AssetRef(val path: String) {
    /** The type of this asset. */
    abstract val type: AssetType

    override fun toString(): String = path

    override fun equals(other: Any?): Boolean =
        other != null && this::class == other::class && other is AssetRef && path == other.path

    override fun hashCode(): Int = 31 * type.hashCode() + path.hashCode()
}

/**
 * Reference to a sprite/image asset (.png, .bmp).
 *
 * ## Example
 *
 * ```kotlin
 * val playerSprite = SpriteAsset("player.png")
 * val enemySprite = SpriteAsset("sprites/enemy.png")
 *
 * // Use in entity definitions
 * val player by entity {
 *     position(80, 72)
 *     sprite(playerSprite) { size = 8 x 16 }
 * }
 * ```
 */
class SpriteAsset(path: String) : AssetRef(path) {
    override val type: AssetType = AssetType.SPRITE

    companion object {
        /**
         * Create from string path.
         *
         * Prefer using typed assets from an Assets object, but this is available for dynamic use
         * cases.
         */
        fun fromPath(path: String): SpriteAsset = SpriteAsset(path)
    }
}

/**
 * Reference to a tileset asset (.png, .bmp).
 *
 * Tilesets are image files containing multiple tiles arranged in a grid, used for backgrounds and
 * tile-based maps.
 */
class TileAsset(path: String) : AssetRef(path) {
    override val type: AssetType = AssetType.TILE

    companion object {
        fun fromPath(path: String): TileAsset = TileAsset(path)
    }
}

/**
 * Reference to a sound effect asset (.wav, .raw).
 *
 * Sound effects are short audio clips played during gameplay.
 */
class SoundAsset(path: String) : AssetRef(path) {
    override val type: AssetType = AssetType.SOUND

    companion object {
        fun fromPath(path: String): SoundAsset = SoundAsset(path)
    }
}

/**
 * Reference to a music asset (.mod, .s3m, .gbs).
 *
 * Music tracks are longer audio files that loop during gameplay.
 */
class MusicAsset(path: String) : AssetRef(path) {
    override val type: AssetType = AssetType.MUSIC

    companion object {
        fun fromPath(path: String): MusicAsset = MusicAsset(path)
    }
}

/**
 * Reference to a font asset (.png, .bmp).
 *
 * Fonts are image files containing character glyphs for text rendering.
 */
class FontAsset(path: String) : AssetRef(path) {
    override val type: AssetType = AssetType.FONT

    companion object {
        fun fromPath(path: String): FontAsset = FontAsset(path)
    }
}

/** The type category of an asset. */
enum class AssetType {
    SPRITE,
    TILE,
    SOUND,
    MUSIC,
    FONT
}
