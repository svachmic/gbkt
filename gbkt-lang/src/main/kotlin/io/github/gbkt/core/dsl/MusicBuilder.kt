/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.MusicDef
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// MUSIC DSL
// Provides the `music(asset("music/theme.uge"))` delegate syntax for declaring
// music tracks, and `play()` / `stopMusic()` script builder functions.
// =============================================================================

/**
 * Typed reference to a declared music track.
 *
 * Returned by the `music()` delegate when the game DSL property is initialized:
 * ```kotlin
 * val theme by music(asset("music/dungeon.uge"))
 * ```
 *
 * Use a [MusicRef] in [SceneBuilder.music] to auto-play on scene enter/exit, or pass it to
 * [ScriptBuilder.play] and [ScriptBuilder.stopMusic] for explicit control.
 */
data class MusicRef(val id: String)

/**
 * Property delegate that registers a [MusicDef] in the current [GameBuilder] and provides a
 * [MusicRef] to the property.
 *
 * Created by the top-level [music] function:
 * ```kotlin
 * val theme by music(asset("music/dungeon.uge"))
 * val boss   by music(asset("music/boss.uge"))
 * ```
 *
 * The property name is captured via [provideDelegate] and used as the music track ID.
 */
class MusicDelegate(private val assetRef: AssetRef) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [MusicDef] with the active [GameBuilder], and returns a [ReadOnlyProperty] that yields
     * [MusicRef].
     *
     * @throws IllegalStateException if called outside a `game {}` block.
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, MusicRef> {
        val id = property.name
        GameBuilderContext.current?.registerMusic(MusicDef(id, assetRef))
            ?: error("music() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> MusicRef(id) }
    }
}

/**
 * Declares a music track from an asset reference.
 *
 * The property name is used as the track ID. Must be called inside a `game {}` block.
 *
 * Usage:
 * ```kotlin
 * val theme  by music(asset("music/dungeon.uge"))
 * val battle by music(asset("music/boss.uge"))
 * ```
 *
 * @param assetRef Reference to a .uge tracker music file.
 * @return [MusicDelegate] for property delegation.
 */
fun music(assetRef: AssetRef): MusicDelegate = MusicDelegate(assetRef)
