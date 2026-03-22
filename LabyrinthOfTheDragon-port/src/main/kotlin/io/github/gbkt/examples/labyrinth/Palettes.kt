/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.GbcColor
import io.github.gbkt.core.dsl.gbc
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType

/**
 * Labyrinth of the Dragon — GBC palette definitions.
 *
 * Ports all GBC palette data from the original C implementation. The original uses
 * `update_bg_palettes()` and `update_sprite_palettes()` to set palette data dynamically at runtime.
 * In the V2 port, palettes are declared as named [GBCPalette] objects and referenced by scene enter
 * blocks.
 *
 * ## Original C Reference
 * - `palette.h` — `update_bg_palettes()`, `update_sprite_palettes()`, fade system
 * - Each floor's `.c` file sets per-floor palette data in scene enter blocks
 *
 * ## Palette Categories
 * | Category      | Palettes                       | Usage                                |
 * |---------------|--------------------------------|--------------------------------------|
 * | Floor 1-2     | `floor1Palette0-2`             | Stone tiles, chests, special objects |
 * | Floor 3-4     | `floor3Palette0-2`             | Cave tiles, walls, chests            |
 * | Floor 5-6     | `floor5Palette0-2`             | Crystal/magic tiles                  |
 * | Floor 7-8     | `floor7Palette0-2`             | Volcanic tiles, final areas          |
 * | Battle        | `battleBg0`, `battleHpNormal`  | Background, HP bar normal/critical   |
 * | Battle status | `battleBuff`, `battleDebuff`   | Palette 6 (buff), palette 7 (debuff) |
 * | Death         | `deathFade0`-`deathFade5`      | 6-step fade to white                 |
 * | Title screen  | `titleDragonFace`, `titleFire` | Dragon face/body, fire, smoke, text  |
 *
 * ## How to Use
 *
 * Palettes are declared as standalone [GBCPalette] instances and referenced directly by scenes:
 * ```kotlin
 * val palettes = Palettes.register(this)
 * // Scenes reference palettes via Palettes.floor1Palette0, etc.
 * ```
 */
@Suppress("LongParameterList")
object Palettes {

    // =========================================================================
    // Floor 1-2 palettes — stone dungeon (entrance floors)
    // @source floor1.c, floor2.c — set_bg_palette calls in floor load
    // =========================================================================

    /**
     * Floor 1-2 background palette 0 — stone tile base.
     *
     * Light gray stone to dark dungeon shadow. Used for stone floor and wall tiles.
     *
     * @source floor1.c — primary tile palette (palette index 0)
     */
    val floor1Palette0 =
        GBCPalette(
            "floor1Palette0",
            listOf(
                gbc(31, 30, 28), // near-white highlight
                gbc(18, 16, 14), // mid warm stone
                gbc(10, 8, 7), // dark stone gray
                gbc(3, 2, 2), // near-black shadow
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 1-2 background palette 1 — chest / object palette.
     *
     * Brown-gold tones for treasure chests and interactive objects.
     *
     * @source floor1.c — chest tile palette (palette index 1)
     */
    val floor1Palette1 =
        GBCPalette(
            "floor1Palette1",
            listOf(
                gbc(31, 28, 16), // bright gold highlight
                gbc(22, 18, 8), // mid gold-brown
                gbc(14, 10, 4), // dark brown
                gbc(4, 3, 1), // near-black shadow
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 1-2 background palette 2 — special tiles (doors, levers, sconces).
     *
     * Warm amber tones for lit torches and special interactive elements.
     *
     * @source floor1.c — special object palette (palette index 2)
     */
    val floor1Palette2 =
        GBCPalette(
            "floor1Palette2",
            listOf(
                gbc(31, 24, 8), // bright torch orange
                gbc(28, 14, 2), // deep orange
                gbc(16, 6, 0), // dark red-orange
                gbc(4, 1, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Floor 2 palettes — goblin warrens (earth tones)
    // @source floor2.c — earth/organic palette variant
    // =========================================================================

    /**
     * Floor 2 background palette 0 — earth tile base.
     *
     * Warm earth browns for the goblin warren floor and wall tiles.
     *
     * @source floor2.c — primary tile palette (palette index 0)
     */
    val floor2Palette0 =
        GBCPalette(
            "floor2Palette0",
            listOf(
                gbc(28, 24, 18), // pale earth
                gbc(18, 14, 8), // mid earth brown
                gbc(10, 7, 4), // dark soil
                gbc(3, 2, 1), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 2 background palette 1 — chest / object palette (earth variant).
     *
     * @source floor2.c — chest tile palette (palette index 1)
     */
    val floor2Palette1 =
        GBCPalette(
            "floor2Palette1",
            listOf(
                gbc(31, 27, 14), // bright gold
                gbc(20, 16, 6), // mid amber
                gbc(12, 9, 2), // dark tan
                gbc(3, 2, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 2 background palette 2 — special tiles (earth variant).
     *
     * Lime green tones for goblin-themed special objects.
     *
     * @source floor2.c — special object palette (palette index 2)
     */
    val floor2Palette2 =
        GBCPalette(
            "floor2Palette2",
            listOf(
                gbc(24, 31, 12), // bright lime (goblin green)
                gbc(14, 22, 4), // mid green
                gbc(6, 12, 1), // dark green
                gbc(1, 3, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Floor 3-4 palettes — deeper dungeons (cave / purple stone)
    // =========================================================================

    /**
     * Floor 3-4 background palette 0 — cave stone base.
     *
     * Cool purple-gray cave stone tones for deeper dungeon floors.
     *
     * @source floor3.c — primary tile palette (palette index 0)
     */
    val floor3Palette0 =
        GBCPalette(
            "floor3Palette0",
            listOf(
                gbc(24, 22, 26), // pale lavender gray
                gbc(14, 12, 18), // mid purple-gray
                gbc(8, 6, 12), // dark cave purple
                gbc(2, 1, 4), // near-black abyss
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 3-4 background palette 1 — chest / object palette (cave variant).
     *
     * @source floor3.c — chest palette (palette index 1)
     */
    val floor3Palette1 =
        GBCPalette(
            "floor3Palette1",
            listOf(
                gbc(28, 26, 10), // bright yellow-gold
                gbc(18, 16, 4), // mid gold
                gbc(10, 8, 1), // dark gold-brown
                gbc(3, 2, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 3-4 background palette 2 — special tiles (cave variant).
     *
     * Purple magic tones for arcane cave objects and spell circles.
     *
     * @source floor3.c — special object palette (palette index 2)
     */
    val floor3Palette2 =
        GBCPalette(
            "floor3Palette2",
            listOf(
                gbc(20, 12, 31), // bright purple magic
                gbc(14, 6, 22), // mid purple
                gbc(8, 2, 14), // dark purple
                gbc(2, 0, 4), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Floor 5-6 palettes — crystal / arcane halls
    // =========================================================================

    /**
     * Floor 5-6 background palette 0 — crystal tile base.
     *
     * Icy blue-white crystal stone for the arcane dungeon halls.
     *
     * @source floor5.c — primary tile palette (palette index 0)
     */
    val floor5Palette0 =
        GBCPalette(
            "floor5Palette0",
            listOf(
                gbc(28, 30, 31), // near-white crystal
                gbc(16, 20, 28), // pale crystal blue
                gbc(8, 10, 20), // deep crystal blue
                gbc(2, 2, 8), // near-black deep shadow
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 5-6 background palette 1 — chest / object palette (crystal variant).
     *
     * @source floor5.c — chest palette (palette index 1)
     */
    val floor5Palette1 =
        GBCPalette(
            "floor5Palette1",
            listOf(
                gbc(31, 28, 20), // bright warm gold
                gbc(22, 18, 10), // mid gold
                gbc(12, 10, 4), // dark gold
                gbc(3, 2, 1), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 5-6 background palette 2 — special tiles (arcane variant).
     *
     * Teal magic tones for arcane portal and crystal resonance objects.
     *
     * @source floor5.c — special object palette (palette index 2)
     */
    val floor5Palette2 =
        GBCPalette(
            "floor5Palette2",
            listOf(
                gbc(24, 31, 28), // bright teal magic
                gbc(12, 22, 18), // mid teal
                gbc(4, 14, 10), // dark teal
                gbc(0, 4, 2), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Floor 7-8 palettes — volcanic / dragon's lair
    // =========================================================================

    /**
     * Floor 7-8 background palette 0 — volcanic stone base.
     *
     * Dark volcanic rock with red-hot fissures for the dragon's domain.
     *
     * @source floor7.c — primary tile palette (palette index 0)
     */
    val floor7Palette0 =
        GBCPalette(
            "floor7Palette0",
            listOf(
                gbc(22, 10, 6), // volcanic red highlight
                gbc(14, 4, 2), // mid dark red
                gbc(7, 1, 0), // very dark red
                gbc(2, 0, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 7-8 background palette 1 — chest / object palette (volcanic variant).
     *
     * Dragon gold tones for treasure chests in the final dungeon area.
     *
     * @source floor7.c — chest palette (palette index 1)
     */
    val floor7Palette1 =
        GBCPalette(
            "floor7Palette1",
            listOf(
                gbc(31, 26, 4), // bright dragon gold
                gbc(24, 18, 0), // mid gold
                gbc(14, 10, 0), // dark gold-brown
                gbc(4, 3, 0), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Floor 7-8 background palette 2 — special tiles (volcanic variant).
     *
     * Lava orange tones for glowing volcanic features and boss portals.
     *
     * @source floor7.c — special object palette (palette index 2)
     */
    val floor7Palette2 =
        GBCPalette(
            "floor7Palette2",
            listOf(
                gbc(31, 20, 0), // bright lava orange
                gbc(28, 10, 0), // hot lava
                gbc(20, 4, 0), // dark lava red
                gbc(6, 0, 0), // near-black volcanic
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Battle palettes — combat screen backgrounds and UI
    // @source Original battle.c — combat palette setup
    // =========================================================================

    /**
     * Battle background palette 0 — main battle screen background.
     *
     * Dark blue-gray tones for the battle arena background tiles.
     *
     * @source battle.c — battle background palette (BG palette 0)
     */
    val battleBg0 =
        GBCPalette(
            "battleBg0",
            listOf(
                gbc(20, 22, 28), // pale battle blue
                gbc(10, 12, 18), // mid battle blue
                gbc(4, 6, 12), // dark battle blue
                gbc(0, 1, 4), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle monster palette 1 — monster display area background.
     *
     * Warm gray tones for the monster sprite backing area.
     *
     * @source battle.c — monster background palette (BG palette 1)
     */
    val battleMonster1 =
        GBCPalette(
            "battleMonster1",
            listOf(
                gbc(28, 26, 24), // light warm stone
                gbc(18, 16, 14), // mid stone
                gbc(10, 8, 7), // dark stone shadow
                gbc(2, 2, 2), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle HP bar palette — normal HP state (green).
     *
     * Green HP bar used when player HP is above 33% of maximum.
     *
     * @source battle.c — HP bar palette (BG palette 2 for normal HP)
     */
    val battleHpNormal =
        GBCPalette(
            "battleHpNormal",
            listOf(
                gbc(31, 31, 31), // white background
                gbc(8, 28, 8), // bright green HP fill
                gbc(2, 16, 2), // dark green HP border
                gbc(0, 0, 0), // black text
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle HP bar palette — critical HP state (red).
     *
     * Red HP bar used when player HP drops to or below 33% of maximum.
     *
     * @source battle.c — HP bar palette (BG palette 3 for critical HP ≤33%)
     */
    val battleHpCritical =
        GBCPalette(
            "battleHpCritical",
            listOf(
                gbc(31, 31, 31), // white background
                gbc(28, 6, 4), // bright red HP fill
                gbc(16, 2, 1), // dark red HP border
                gbc(0, 0, 0), // black text
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle SP bar palette — mana/spirit points display.
     *
     * Blue SP bar for displaying the player's current spirit points.
     *
     * @source battle.c — SP bar palette (BG palette 4)
     */
    val battleSpBar =
        GBCPalette(
            "battleSpBar",
            listOf(
                gbc(31, 31, 31), // white background
                gbc(6, 16, 28), // bright blue SP fill
                gbc(2, 8, 18), // dark blue SP border
                gbc(0, 0, 0), // black text
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle UI text palette — menus and dialog text in battle.
     *
     * High-contrast light palette for battle menu text rendering.
     *
     * @source battle.c — battle UI palette (BG palette 5)
     */
    val battleUi =
        GBCPalette(
            "battleUi",
            listOf(
                gbc(31, 31, 31), // white panel
                gbc(20, 20, 22), // light gray accents
                gbc(8, 8, 10), // dark border
                gbc(0, 0, 0), // black text
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Battle buff indicator palette — palette 6, used for active positive status effects.
     *
     * Green-tinted sprite palette for showing buff status icons (ATK Up, DEF Up, Haste, Regen).
     *
     * @source battle.c — buff sprite palette (sprite palette 6)
     */
    val battleBuff =
        GBCPalette(
            "battleBuff",
            listOf(
                gbc(28, 31, 28), // bright buff green
                gbc(12, 24, 12), // mid buff green
                gbc(4, 14, 4), // dark buff green
                gbc(0, 2, 0), // near-black
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Battle debuff indicator palette — palette 7, used for active negative status effects.
     *
     * Red-tinted sprite palette for showing debuff status icons (Poison, Bleed, Silence, etc.).
     *
     * @source battle.c — debuff sprite palette (sprite palette 7)
     */
    val battleDebuff =
        GBCPalette(
            "battleDebuff",
            listOf(
                gbc(31, 26, 26), // bright debuff red
                gbc(24, 10, 10), // mid debuff red
                gbc(14, 3, 3), // dark debuff red
                gbc(2, 0, 0), // near-black
            ),
            type = PaletteType.SPRITE,
        )

    // =========================================================================
    // Monster death fade palettes — 6-step fade to white
    // @source palette.c — fade_out() / update_fade_out() increments RGB toward white
    // =========================================================================

    /**
     * Death fade step 0 — monster sprite at full color (no fade).
     *
     * Starting state before the death fade animation begins.
     *
     * @source palette.c — fade_out() initial state; fade_step = 16
     */
    val deathFade0 =
        GBCPalette(
            "deathFade0",
            listOf(
                gbc(28, 26, 24), // stone highlight
                gbc(18, 16, 14), // mid stone
                gbc(10, 8, 7), // dark stone
                gbc(2, 2, 2), // near-black
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Death fade step 1 — approximately 20% fade toward white.
     *
     * @source palette.c — update_fade_out() increments r/g/b by 2 per step toward RGB_WHITE
     */
    val deathFade1 =
        GBCPalette(
            "deathFade1",
            listOf(
                gbc(30, 28, 27), // brighter highlight
                gbc(22, 20, 18), // lighter mid
                gbc(14, 12, 11), // lighter dark
                gbc(8, 8, 8), // lightened shadow
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Death fade step 2 — approximately 40% fade toward white.
     *
     * @source palette.c — update_fade_out() cumulative fade progression
     */
    val deathFade2 =
        GBCPalette(
            "deathFade2",
            listOf(
                gbc(31, 30, 29), // near-white highlight
                gbc(26, 24, 22), // light gray mid
                gbc(18, 16, 15), // medium gray dark
                gbc(14, 14, 14), // medium gray shadow
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Death fade step 3 — approximately 60% fade toward white.
     *
     * @source palette.c — update_fade_out() cumulative fade progression
     */
    val deathFade3 =
        GBCPalette(
            "deathFade3",
            listOf(
                gbc(31, 31, 31), // white (fully faded)
                gbc(29, 28, 27), // pale gray mid
                gbc(22, 22, 21), // light gray dark
                gbc(20, 20, 20), // light gray shadow
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Death fade step 4 — approximately 80% fade toward white.
     *
     * @source palette.c — update_fade_out() cumulative fade progression
     */
    val deathFade4 =
        GBCPalette(
            "deathFade4",
            listOf(
                gbc(31, 31, 31), // white
                gbc(31, 31, 31), // white (faded)
                gbc(28, 28, 28), // near-white
                gbc(26, 26, 26), // near-white shadow
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Death fade step 5 — fully white (fade complete).
     *
     * All colors are RGB_WHITE. Monster is fully invisible against a white background.
     *
     * @source palette.c — fade_type == FADE_STOPPED after fade_step reaches 0
     */
    val deathFade5 =
        GBCPalette(
            "deathFade5",
            listOf(
                GbcColor.WHITE, // fully white
                GbcColor.WHITE, // fully white
                GbcColor.WHITE, // fully white
                GbcColor.WHITE, // fully white
            ),
            type = PaletteType.SPRITE,
        )

    // =========================================================================
    // Title screen palettes — dragon face, body, fire, smoke, press start
    // @source title.c — title screen palette setup in title scene enter
    // =========================================================================

    /**
     * Title screen — dragon face sprite palette.
     *
     * Red-orange tones for the dragon's face on the title screen.
     *
     * @source title.c — dragon face sprite palette (sprite palette 0)
     */
    val titleDragonFace =
        GBCPalette(
            "titleDragonFace",
            listOf(
                gbc(31, 20, 16), // bright orange-red face highlight
                gbc(28, 10, 6), // mid orange-red
                gbc(20, 4, 2), // dark red scale
                gbc(6, 0, 0), // near-black outline
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Title screen — dragon body sprite palette.
     *
     * Deeper red-black tones for the dragon's body on the title screen.
     *
     * @source title.c — dragon body sprite palette (sprite palette 1)
     */
    val titleDragonBody =
        GBCPalette(
            "titleDragonBody",
            listOf(
                gbc(22, 8, 4), // dark orange-red body highlight
                gbc(16, 4, 2), // mid dark red
                gbc(10, 1, 0), // very dark red
                gbc(4, 0, 0), // near-black outline
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Title screen — fire animation sprite palette.
     *
     * Bright flame colors for the animated fire effect on the title screen. The title fire cycles
     * through 18 frames at 6 frames per step.
     *
     * @source title.c — fire sprite palette (sprite palette 2) TitleAnimationConfig:
     *   FIRE_FRAME_DELAY = 6, FIRE_FRAMES = [0,1,2,3,4,2,3,4,2,3,4,2,3,4,3,2,1,0]
     */
    val titleFire =
        GBCPalette(
            "titleFire",
            listOf(
                gbc(31, 31, 16), // bright yellow flame tip
                gbc(31, 20, 4), // orange flame
                gbc(24, 8, 0), // deep orange-red
                gbc(8, 0, 0), // dark red base
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Title screen — smoke animation sprite palette.
     *
     * Dark gray-brown smoke puffs rising from the dragon's fire.
     *
     * @source title.c — smoke sprite palette (sprite palette 3) TitleAnimationConfig:
     *   SMOKE_FRAME_DELAY = 6
     */
    val titleSmoke =
        GBCPalette(
            "titleSmoke",
            listOf(
                gbc(24, 22, 20), // pale smoke
                gbc(16, 14, 12), // mid smoke
                gbc(8, 7, 6), // dark smoke
                gbc(2, 1, 1), // near-black smoke base
            ),
            type = PaletteType.SPRITE,
        )

    /**
     * Title screen — background tile palette.
     *
     * Dark atmospheric purple background for the title screen.
     *
     * @source title.c — title BG palette (BG palette 0)
     */
    val titleBg =
        GBCPalette(
            "titleBg",
            listOf(
                gbc(6, 4, 10), // dark purple-gray atmosphere
                gbc(4, 2, 8), // deeper purple
                gbc(2, 1, 5), // very dark purple
                gbc(0, 0, 2), // near-black
            ),
            type = PaletteType.BACKGROUND,
        )

    /**
     * Title screen — "PRESS START" text background palette.
     *
     * High-contrast white-on-dark for the blinking press start prompt.
     *
     * @source title.c — press start text palette (BG palette 7)
     */
    val titlePressStart =
        GBCPalette(
            "titlePressStart",
            listOf(
                gbc(31, 31, 31), // bright white text
                gbc(20, 20, 20), // gray mid
                gbc(8, 8, 8), // dark gray
                gbc(0, 0, 0), // black background
            ),
            type = PaletteType.BACKGROUND,
        )

    // =========================================================================
    // Registration helper
    // =========================================================================

    /**
     * Registers the palette system into the [GameBuilder] scope.
     *
     * Called inside the `game { }` DSL block. Each [GBCPalette] is registered with the builder so
     * that the codegen emits `const palette_color_t {name}_pal[4]` data arrays in main.c. Without
     * registration, `set_bkg_palette()` and `set_sprite_palette()` calls in scene enter blocks
     * reference undefined identifiers.
     *
     * Returns this [Palettes] object for caller convenience.
     *
     * @source palette.c — `update_bg_palettes()` and `update_sprite_palettes()` are called from
     *   scene enter blocks in the original
     */
    @Suppress("LongMethod")
    fun register(builder: GameBuilder): Palettes {
        // Floor palettes
        builder.registerPalette(floor1Palette0)
        builder.registerPalette(floor1Palette1)
        builder.registerPalette(floor1Palette2)
        builder.registerPalette(floor2Palette0)
        builder.registerPalette(floor2Palette1)
        builder.registerPalette(floor2Palette2)
        builder.registerPalette(floor3Palette0)
        builder.registerPalette(floor3Palette1)
        builder.registerPalette(floor3Palette2)
        builder.registerPalette(floor5Palette0)
        builder.registerPalette(floor5Palette1)
        builder.registerPalette(floor5Palette2)
        builder.registerPalette(floor7Palette0)
        builder.registerPalette(floor7Palette1)
        builder.registerPalette(floor7Palette2)
        // Battle palettes
        builder.registerPalette(battleBg0)
        builder.registerPalette(battleMonster1)
        builder.registerPalette(battleHpNormal)
        builder.registerPalette(battleHpCritical)
        builder.registerPalette(battleSpBar)
        builder.registerPalette(battleUi)
        builder.registerPalette(battleBuff)
        builder.registerPalette(battleDebuff)
        // Death fade palettes
        builder.registerPalette(deathFade0)
        builder.registerPalette(deathFade1)
        builder.registerPalette(deathFade2)
        builder.registerPalette(deathFade3)
        builder.registerPalette(deathFade4)
        builder.registerPalette(deathFade5)
        // Title screen palettes
        builder.registerPalette(titleDragonFace)
        builder.registerPalette(titleDragonBody)
        builder.registerPalette(titleFire)
        builder.registerPalette(titleSmoke)
        builder.registerPalette(titleBg)
        builder.registerPalette(titlePressStart)
        return this
    }
}
