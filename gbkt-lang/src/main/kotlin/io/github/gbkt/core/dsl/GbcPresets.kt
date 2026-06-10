/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCPalette

// =============================================================================
// GBC PALETTE PRESETS
// =============================================================================

/**
 * Curated collection of 16 ready-to-use GBC palette presets.
 *
 * Each preset is a 4-color [GBCPalette] in RGB555 format, ordered from lightest to darkest. Presets
 * can be used directly with [PaletteBuilder.copy] or passed to [palette { copy(...) }].
 *
 * Usage:
 * ```kotlin
 * val forest by palette { copy(GbcPresets.NATURE) }
 * val ui by palette { copy(GbcPresets.UI_LIGHT) }
 *
 * // Override a single shade after copying:
 * val customDungeon by palette {
 *     copy(GbcPresets.DUNGEON)
 *     color0(Color.WHITE)  // brighten the highlight
 * }
 * ```
 *
 * Presets are organized by theme:
 * - **Nature:** CLASSIC_GREEN, NATURE, FIRE, ICE, OCEAN
 * - **Environment:** DUNGEON, CAVERN, SUNSET, NIGHT
 * - **Style:** PASTEL, SEPIA, NEON, MONOCHROME_BLUE, WARM_GRAY
 * - **UI:** UI_LIGHT, UI_DARK
 */
object GbcPresets {

    // =========================================================================
    // Nature themes
    // =========================================================================

    /**
     * Classic Game Boy green monochrome palette.
     *
     * Evokes the original DMG hardware aesthetic with warm green tones. Colors: near-white → bright
     * green → mid green → deep forest.
     */
    val CLASSIC_GREEN =
        GBCPalette(
            "classic_green",
            listOf(
                Color.rgb555(31, 31, 24), // near-white with warm tint
                Color.rgb555(16, 24, 8), // bright lime green
                Color.rgb555(8, 16, 4), // mid forest green
                Color.rgb555(0, 4, 0), // deep dark green
            ),
        )

    /**
     * Soft natural forest palette.
     *
     * Lighter and more colorful than CLASSIC_GREEN, suitable for outdoor overworld maps.
     */
    val NATURE =
        GBCPalette(
            "nature",
            listOf(
                Color.rgb555(28, 31, 20), // light spring green
                Color.rgb555(12, 24, 8), // medium leafy green
                Color.rgb555(6, 16, 4), // deep foliage
                Color.rgb555(2, 6, 2), // dark undergrowth
            ),
        )

    /**
     * Fire and lava palette.
     *
     * Hot yellows fading to deep red — ideal for fire traps, lava zones, and flame effects.
     */
    val FIRE =
        GBCPalette(
            "fire",
            listOf(
                Color.rgb555(31, 31, 16), // bright yellow-white
                Color.rgb555(31, 20, 4), // orange-yellow flame
                Color.rgb555(24, 8, 0), // deep orange-red
                Color.rgb555(8, 0, 0), // near-black dark red
            ),
        )

    /**
     * Ice and frost palette.
     *
     * Cool blue-whites for ice dungeons, frozen tundra, and blizzard effects.
     */
    val ICE =
        GBCPalette(
            "ice",
            listOf(
                Color.rgb555(31, 31, 31), // pure white ice
                Color.rgb555(20, 26, 31), // pale ice blue
                Color.rgb555(10, 16, 28), // mid cool blue
                Color.rgb555(2, 4, 16), // deep glacial blue
            ),
        )

    /**
     * Ocean and deep sea palette.
     *
     * Aquamarine to deep ocean blue — suitable for underwater areas and water tiles.
     */
    val OCEAN =
        GBCPalette(
            "ocean",
            listOf(
                Color.rgb555(24, 31, 31), // bright aquamarine
                Color.rgb555(12, 20, 28), // sea blue
                Color.rgb555(4, 10, 20), // deep ocean
                Color.rgb555(0, 2, 8), // abyssal dark
            ),
        )

    // =========================================================================
    // Environment themes
    // =========================================================================

    /**
     * Classic dungeon stone palette.
     *
     * Warm gray tones for stone walls, rocky floors, and dark castle interiors.
     */
    val DUNGEON =
        GBCPalette(
            "dungeon",
            listOf(
                Color.rgb555(24, 22, 20), // light warm stone
                Color.rgb555(16, 14, 12), // mid stone gray
                Color.rgb555(10, 8, 8), // dark stone
                Color.rgb555(4, 2, 2), // near-black shadow
            ),
        )

    /**
     * Underground cavern palette.
     *
     * Cool purple-grays for caves, mines, and underground passages.
     */
    val CAVERN =
        GBCPalette(
            "cavern",
            listOf(
                Color.rgb555(22, 20, 24), // pale lavender gray
                Color.rgb555(14, 12, 18), // mid purple-gray
                Color.rgb555(8, 6, 12), // dark cave purple
                Color.rgb555(2, 2, 4), // near-black abyss
            ),
        )

    /**
     * Sunset sky palette.
     *
     * Warm golden yellows fading to deep purple-red — title screens and dusk scenes.
     */
    val SUNSET =
        GBCPalette(
            "sunset",
            listOf(
                Color.rgb555(31, 28, 16), // bright golden sky
                Color.rgb555(31, 16, 8), // warm orange horizon
                Color.rgb555(20, 8, 12), // rose-red transition
                Color.rgb555(8, 2, 8), // deep violet dusk
            ),
        )

    /**
     * Night sky palette.
     *
     * Deep cool blues for nighttime outdoor scenes, starry skies, and nocturnal maps.
     */
    val NIGHT =
        GBCPalette(
            "night",
            listOf(
                Color.rgb555(12, 14, 20), // moonlit light blue
                Color.rgb555(6, 8, 16), // deep twilight
                Color.rgb555(3, 4, 10), // dark night blue
                Color.rgb555(0, 0, 4), // near-black midnight
            ),
        )

    // =========================================================================
    // Style themes
    // =========================================================================

    /**
     * Soft pastel palette.
     *
     * Gentle pinks, lavenders, and mint tones — suitable for shops, towns, and light scenes.
     */
    val PASTEL =
        GBCPalette(
            "pastel",
            listOf(
                Color.rgb555(31, 28, 28), // light rose white
                Color.rgb555(28, 24, 31), // soft lavender
                Color.rgb555(24, 31, 28), // mint green
                Color.rgb555(31, 31, 24), // pale lemon
            ),
        )

    /**
     * Sepia-toned palette.
     *
     * Warm brown-yellows evoking aged parchment — perfect for maps, documents, and flashbacks.
     */
    val SEPIA =
        GBCPalette(
            "sepia",
            listOf(
                Color.rgb555(31, 28, 22), // pale parchment
                Color.rgb555(24, 20, 14), // warm tan
                Color.rgb555(16, 12, 8), // medium brown
                Color.rgb555(8, 4, 2), // dark sepia shadow
            ),
        )

    /**
     * Neon cyberpunk palette.
     *
     * Vivid greens, magentas, and purples on black — sci-fi, hacking, and futuristic themes.
     */
    val NEON =
        GBCPalette(
            "neon",
            listOf(
                Color.rgb555(0, 31, 16), // neon green
                Color.rgb555(31, 0, 16), // hot pink/magenta
                Color.rgb555(16, 0, 31), // electric purple
                Color.rgb555(0, 0, 0), // black background
            ),
        )

    /**
     * Monochrome blue palette.
     *
     * Clean blue-gray shades for modern UI panels, sky backgrounds, and calm scenes.
     */
    val MONOCHROME_BLUE =
        GBCPalette(
            "monochrome_blue",
            listOf(
                Color.rgb555(24, 28, 31), // light sky blue
                Color.rgb555(12, 18, 24), // medium steel blue
                Color.rgb555(4, 8, 16), // deep slate blue
                Color.rgb555(0, 2, 8), // near-black navy
            ),
        )

    /**
     * Warm gray palette.
     *
     * Subtle warm-tinted neutrals — versatile for menus, text backgrounds, and cutscene overlays.
     */
    val WARM_GRAY =
        GBCPalette(
            "warm_gray",
            listOf(
                Color.rgb555(28, 26, 24), // near-white warm gray
                Color.rgb555(20, 18, 16), // light warm gray
                Color.rgb555(12, 10, 10), // medium warm gray
                Color.rgb555(4, 4, 4), // near-black warm gray
            ),
        )

    // =========================================================================
    // UI themes
    // =========================================================================

    /**
     * Light UI palette.
     *
     * High-contrast light theme for dialog boxes, menus, and HUD overlays in bright games.
     */
    val UI_LIGHT =
        GBCPalette(
            "ui_light",
            listOf(
                Color.rgb555(31, 31, 31), // pure white panel background
                Color.rgb555(20, 20, 22), // light cool gray for accents
                Color.rgb555(10, 10, 14), // mid dark for borders
                Color.rgb555(0, 0, 0), // pure black for text
            ),
        )

    /**
     * Dark UI palette.
     *
     * Slightly muted dark theme for dialog boxes and menus in darker-toned games.
     */
    val UI_DARK =
        GBCPalette(
            "ui_dark",
            listOf(
                Color.rgb555(24, 24, 26), // light panel background
                Color.rgb555(16, 16, 18), // mid gray accent
                Color.rgb555(8, 8, 10), // dark border
                Color.rgb555(0, 0, 2), // near-black deep shadow
            ),
        )
}
