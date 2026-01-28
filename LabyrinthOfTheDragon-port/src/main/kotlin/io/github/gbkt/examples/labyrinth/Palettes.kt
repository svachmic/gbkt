/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.graphics.PaletteBuilder

/**
 * Palette definitions for Labyrinth of the Dragon.
 *
 * Ported from original floor*.c and bank03.c palette data. Each floor has 7 background palettes
 * used for dungeon tiles, chests, and UI elements. Battle palettes include monster slots, HP bars,
 * and status effect indicators.
 *
 * Palette assignments:
 * - BG Palette 0-3: Floor tiles (varies per floor)
 * - BG Palette 4-6: UI elements (torch gauge, keys, textbox)
 * - BG Palette 6 (Battle): Buff status effects (green)
 * - BG Palette 7 (Battle): Debuff status effects (red)
 * - BG Palette 4 (Battle): HP bar normal (green)
 * - BG Palette 5 (Battle): HP bar critical (red)
 */
object Palettes {

    // =========================================================================
    // FLOOR 1 - Dungeon Entrance (Gray stone)
    // =========================================================================

    /** Floor 1 core background tiles - gray stone walls */
    val floor1Palette0 =
        palette("floor1_bg0") {
            color(0, 190, 200, 190) // Light stone
            color(1, 100, 100, 140) // Medium shadow
            color(2, 40, 60, 40) // Dark crevice
            color(3, 24, 0, 0) // Black
            forBackground()
        }

    /** Floor 1 item chests */
    val floor1Palette1 =
        palette("floor1_chest") {
            color(0, 192, 138, 40) // Gold highlight
            color(1, 100, 100, 140) // Medium shadow
            color(2, 40, 60, 40) // Dark
            color(3, 24, 0, 0) // Black
            forBackground()
        }

    /** Floor 1 special chests */
    val floor1Palette2 =
        palette("floor1_special") {
            color(0, 195, 222, 180) // Light green
            color(1, 100, 100, 140)
            color(2, 40, 60, 40)
            color(3, 24, 0, 0)
            forBackground()
        }

    // =========================================================================
    // FLOOR 2 - Goblin Warrens (Brown earth)
    // =========================================================================

    /** Floor 2 floor tiles - brown earth */
    val floor2Palette0 =
        palette("floor2_bg0") {
            color(0, 194, 192, 190) // Light earth
            color(1, 131, 100, 59) // Brown mid
            color(2, 55, 56, 0) // Dark earth
            color(3, 0, 29, 49) // Deep shadow
            forBackground()
        }

    /** Floor 2 wall tiles */
    val floor2Palette1 =
        palette("floor2_walls") {
            color(0, 212, 222, 207) // Light
            color(1, 163, 139, 54) // Tan
            color(2, 110, 56, 46) // Brown
            color(3, 8, 12, 50) // Dark blue-black
            forBackground()
        }

    /** Floor 2 treasure chests */
    val floor2Palette2 =
        palette("floor2_chest") {
            color(0, 219, 191, 34) // Gold
            color(1, 163, 139, 54) // Tan
            color(2, 110, 56, 46) // Brown
            color(3, 54, 55, 0) // Dark
            forBackground()
        }

    // =========================================================================
    // BATTLE SCREEN PALETTES
    // =========================================================================

    /** Battle background/textbox */
    val battleBg0 =
        palette("battle_bg0") {
            color(0, 255, 255, 255) // White
            color(1, 81, 108, 186) // Blue mid
            color(2, 3, 37, 135) // Dark blue
            color(3, 22, 6, 4) // Near black
            forBackground()
        }

    /** Battle monster slot 1 (placeholder - actual colors come from monster) */
    val battleMonster1 =
        palette("battle_monster1") {
            color(0, 255, 255, 255)
            color(1, 209, 206, 107)
            color(2, 126, 73, 73)
            color(3, 0, 40, 51)
            forBackground()
        }

    /** HP bar normal (green) - BG palette 4 in battle */
    val battleHpNormal =
        palette("battle_hp_normal") {
            color(0, 255, 255, 255) // White
            color(1, 150, 200, 150) // Light green
            color(2, 80, 120, 80) // Medium green
            color(3, 0, 32, 0) // Dark green
            forBackground()
        }

    /** HP bar critical (red) - BG palette 5 in battle */
    val battleHpCritical =
        palette("battle_hp_critical") {
            color(0, 255, 255, 255) // White
            color(1, 200, 150, 150) // Light red
            color(2, 120, 80, 80) // Medium red
            color(3, 32, 0, 0) // Dark red
            forBackground()
        }

    /** Buff status effects (green) - BG palette 6 in battle */
    val battleBuff =
        palette("battle_buff") {
            color(0, 40, 150, 40) // Green accent
            color(1, 255, 255, 255) // White
            color(2, 120, 120, 120) // Gray
            color(3, 0, 0, 0) // Black
            forBackground()
        }

    /** Debuff status effects (red) - BG palette 7 in battle */
    val battleDebuff =
        palette("battle_debuff") {
            color(0, 150, 40, 40) // Red accent
            color(1, 255, 255, 255) // White
            color(2, 120, 120, 120) // Gray
            color(3, 0, 0, 0) // Black
            forBackground()
        }

    // =========================================================================
    // MONSTER DEATH FADE PALETTES (6 steps toward white)
    // =========================================================================

    /** Death fade step 0 */
    val deathFade0 =
        palette("death_fade0") {
            color(0, 255, 255, 255)
            color(1, 207, 66, 66)
            color(2, 128, 41, 92)
            color(3, 42, 9, 69)
            forBackground()
        }

    /** Death fade step 1 */
    val deathFade1 =
        palette("death_fade1") {
            color(0, 255, 255, 255)
            color(1, 207, 134, 134)
            color(2, 128, 41, 92)
            color(3, 42, 9, 69)
            forBackground()
        }

    /** Death fade step 2 */
    val deathFade2 =
        palette("death_fade2") {
            color(0, 255, 255, 255)
            color(1, 207, 179, 179)
            color(2, 154, 95, 130)
            color(3, 42, 9, 69)
            forBackground()
        }

    /** Death fade step 3 */
    val deathFade3 =
        palette("death_fade3") {
            color(0, 255, 255, 255)
            color(1, 255, 255, 255)
            color(2, 190, 144, 171)
            color(3, 82, 38, 119)
            forBackground()
        }

    /** Death fade step 4 */
    val deathFade4 =
        palette("death_fade4") {
            color(0, 255, 255, 255)
            color(1, 255, 255, 255)
            color(2, 255, 255, 255)
            color(3, 156, 123, 183)
            forBackground()
        }

    /** Death fade step 5 (fully white) */
    val deathFade5 =
        palette("death_fade5") {
            color(0, 255, 255, 255)
            color(1, 255, 255, 255)
            color(2, 255, 255, 255)
            color(3, 255, 255, 255)
            forBackground()
        }

    /** List of death fade palettes for animation */
    val deathFadePalettes =
        listOf(deathFade0, deathFade1, deathFade2, deathFade3, deathFade4, deathFade5)

    // =========================================================================
    // TITLE SCREEN PALETTES
    // =========================================================================

    /** Title screen dragon face */
    val titleDragonFace =
        palette("title_face") {
            color(0, 252, 216, 0) // Gold eyes
            color(1, 154, 32, 24) // Red scales
            color(2, 54, 11, 13) // Dark red
            color(3, 25, 8, 10) // Near black
            forBackground()
        }

    /** Title screen dragon body */
    val titleDragonBody =
        palette("title_body") {
            color(0, 54, 11, 13) // Dark red
            color(1, 25, 8, 10) // Darker
            color(2, 9, 4, 4) // Very dark
            color(3, 0, 0, 0) // Black
            forBackground()
        }

    /** Title "PRESS START" text */
    val titlePressStart =
        palette("title_start") {
            color(0, 251, 242, 54) // Yellow
            color(1, 0, 0, 0)
            color(2, 0, 0, 0)
            color(3, 0, 0, 0)
            forBackground()
        }

    /** Fire animation sprite palette */
    val titleFire =
        palette("title_fire") {
            color(0, 0, 0, 0) // Transparent
            color(1, 252, 216, 0) // Yellow
            color(2, 252, 108, 0) // Orange
            color(3, 255, 255, 255) // White core
            forSprites()
        }

    /** Smoke animation sprite palette */
    val titleSmoke =
        palette("title_smoke") {
            color(0, 0, 0, 0) // Transparent
            color(1, 91, 91, 91) // Light gray
            color(2, 156, 156, 156) // Medium gray
            color(3, 255, 255, 255) // White
            forSprites()
        }

    // =========================================================================
    // HELPER FUNCTION
    // =========================================================================

    /** Helper to create a palette with RGB888 colors */
    private fun palette(name: String, init: PaletteBuilder.() -> Unit): PaletteBuilder {
        return PaletteBuilder(name).apply(init)
    }
}

/**
 * Title fire animation timing constants.
 *
 * Ported from title_screen.c:
 * - Fire animation uses 6 frames per step
 * - Fire sequence: 0, 1, 2, 3, 4, 2, 3, 4, 2, 3, 4, 2, 3, 4, 3, 2, 1, 0, END
 * - Smoke animation follows fire completion
 * - Dragon palette flickers between 2 states every 3 frames
 */
object TitleAnimationConfig {
    /** Frames per fire animation step */
    const val FIRE_FRAME_DELAY = 6

    /** Frames per dragon palette flicker */
    const val DRAGON_PALETTE_DELAY = 3

    /** Frames per smoke animation step */
    const val SMOKE_FRAME_DELAY = 6

    /** Pause between smoke animation loops (in frames) */
    const val SMOKE_PAUSE_FRAMES = 100

    /** Fire animation frame sequence */
    val FIRE_FRAMES = intArrayOf(0, 1, 2, 3, 4, 2, 3, 4, 2, 3, 4, 2, 3, 4, 3, 2, 1, 0)
}
