/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.ExplorationPreset
import io.github.gbkt.core.dsl.game
import io.github.gbkt.examples.labyrinth.rpg.defineAbilities
import io.github.gbkt.examples.labyrinth.rpg.defineCharacters
import io.github.gbkt.examples.labyrinth.rpg.defineItems
import io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects
import io.github.gbkt.examples.labyrinth.rpg.registerCombat
import io.github.gbkt.examples.labyrinth.rpg.registerMonsters
import io.github.gbkt.examples.labyrinth.scenes.Scenes
import io.github.gbkt.examples.labyrinth.world.registerFloors

/**
 * Labyrinth of the Dragon — gbkt V2 port entry point.
 *
 * This object is the root of the game definition. [create] returns the fully-built [GameIR] that
 * the gbkt Gradle plugin compiles to GBDK-compatible C.
 *
 * Architecture:
 * - [GameConfig] provides all gameplay constants (ported from core.h, player.h, map.h)
 * - [GameState] declares all runtime variables (V2 delegates)
 * - All subsystems are wired in [create] via their register/define extension functions
 *
 * Cartridge configuration matches the original C game:
 * - MBC5 with RAM+Battery (SRAM save support)
 * - 32 ROM banks (original uses 19+ banks; 32 gives headroom)
 * - 4 RAM banks (32KB SRAM for 3 save slots)
 *
 * Scene overview:
 * - `title` — title screen with class selection and load prompt
 * - `heroSelect` — character class and name selection
 * - `gameplay` — grid-based dungeon exploration
 * - `battle` — turn-based combat state machine
 * - `pause` — pause/save/stats overlay
 * - `gameover` — death / game over screen
 * - `victory` — end-game credits roll
 *
 * Original reference: `LabyrinthOfTheDragon/src/main.c`
 */
/**
 * Top-level entry point for the gbkt Gradle plugin (GenerateCTask reflection).
 *
 * The plugin discovers games via `getLabyrinthOfTheDragon()` — the JVM getter for this val. This
 * mirrors the pattern used by all other gbkt examples (pongV2, breakoutV2, etc.).
 */
val labyrinthOfTheDragon = LabyrinthOfTheDragon.create()

@Suppress("LongMethod")
object LabyrinthOfTheDragon {

    /**
     * Builds and returns the complete game IR for Labyrinth of the Dragon.
     *
     * Registration order follows the dependency graph:
     * 1. Config — cartridge parameters
     * 2. State variables — runtime globals
     * 3. Sounds — SFX refs needed by scenes
     * 4. Palettes — GBC palette objects (referenced by scenes)
     * 5. StatusIcons — sprite slot allocations
     * 6. Status effects — combat effect definitions (needed by abilities)
     * 7. Items — consumable item definitions
     * 8. Characters — playable class stats and level curves
     * 9. Monsters — monster definitions (needed by encounter tables)
     * 10. Abilities — class abilities (depend on status effects)
     * 11. Combat system — battle configuration (depends on characters + monsters)
     * 12. Save system — SRAM layout (after state variables are declared)
     * 13. Floors — 8 dungeon floor zones with encounters, chests, NPCs
     * 14. Exploration — gauge, keys, step callbacks (depend on floors)
     * 15. Scenes — all 7 scenes (registered last; reference everything above)
     *
     * @return A [GameBuilder] lambda result that can be compiled to C.
     */
    fun create() =
        game("LabyrinthDragon") {

            // -------------------------------------------------------------------------
            // 1. Cartridge configuration
            // Matches the original game: MBC5 + RAM + Battery, 32 ROM banks, 4 RAM banks.
            // @source main.c — ENABLE_RAM at startup; original uses MBC5 with battery
            // -------------------------------------------------------------------------
            config {
                cartridge = "MBC5_RAM_BATTERY"
                romBanks = 32 // Original uses 19+ banks; 32 gives expansion headroom
                ramBanks = 4 // 4 x 8KB = 32KB SRAM — holds 3 save slots (GameConfig.SAVE_SLOTS)
            }

            // -------------------------------------------------------------------------
            // 2. Runtime state variables
            // All variables declared here become global C variables in generated output.
            // @see GameState for variable documentation
            // @source main.c, map.h, player.h, battle.h
            // -------------------------------------------------------------------------
            val state = GameState.register(this)

            // -------------------------------------------------------------------------
            // 3. Sound effects
            // SFX-only audio — no background music in original.
            // @source sound.h — 31 sfx_* function declarations
            // -------------------------------------------------------------------------
            val sounds = defineSounds()

            // -------------------------------------------------------------------------
            // 4. GBC palettes
            // Named palette objects; referenced by scene enter blocks.
            // @source palette.h — update_bg_palettes(), update_sprite_palettes()
            // -------------------------------------------------------------------------
            Palettes.register(this)

            // -------------------------------------------------------------------------
            // 5. Status effect icon sprites
            // 16 OAM slots for buff/debuff status display during battle.
            // @source battle.c — sprite palette slots 6 (buff) and 7 (debuff)
            // -------------------------------------------------------------------------
            StatusIcons.register(this)

            // -------------------------------------------------------------------------
            // 6. Status effects (must precede abilities — abilities reference effect refs)
            // 13 active effects ported from stats.h StatusEffect enum.
            // @source stats.h — DEBUFF_BLIND through BUFF_DEF_UP
            // -------------------------------------------------------------------------
            val statusEffects = defineStatusEffects()

            // -------------------------------------------------------------------------
            // 7. Items (consumables)
            // 8 items ported from item.h ItemId enum.
            // @source item.h — ITEM_POTION through ITEM_HASTE
            // -------------------------------------------------------------------------
            val items = defineItems()

            // -------------------------------------------------------------------------
            // 8. Playable character classes
            // 4 classes + debug Test class ported from player.c stat tables.
            // @source player.h — PlayerClass enum; player.c *_update_stats() functions
            // -------------------------------------------------------------------------
            val characters = defineCharacters()

            // -------------------------------------------------------------------------
            // 9. Monster definitions
            // 12 monsters ported from monsters.bank6.c and monsters.bank7.c.
            // @source monster.h — MonsterType enum (kobold=0 through dragon=11)
            // -------------------------------------------------------------------------
            val monsters = registerMonsters()

            // -------------------------------------------------------------------------
            // 10. Class abilities
            // 24 abilities (6 per class) ported from player.data.c.
            // Depends on statusEffects for effect application in ability execution blocks.
            // @source player.data.c — druid0..druid5, fighter0..fighter5, monk0..monk5,
            // sorcerer0..sorcerer5
            // -------------------------------------------------------------------------
            val abilities = defineAbilities(statusEffects)

            // -------------------------------------------------------------------------
            // 11. Combat system
            // Turn-based battle with AGL-based initiative, damage formulas, status ticks.
            // @source battle.c/h — BattleState enum, party config, encounter groups
            // -------------------------------------------------------------------------
            val combatSystem = registerCombat(characters, monsters)

            // -------------------------------------------------------------------------
            // 12. Save system (after all state variables are declared)
            // 3 SRAM slots with checksum. @source save.c — SAVE_SLOTS = 3
            // -------------------------------------------------------------------------
            SaveSystem.register(this)

            // -------------------------------------------------------------------------
            // 13. Dungeon floors (8 floors with encounter tables, chests, puzzles)
            // Registers flag pages (chests_1_4, chests_5_8, world) and all 8 zones.
            // @source floor1.c through floor8.c, floor_common.c
            // -------------------------------------------------------------------------
            val floors = registerFloors()

            // -------------------------------------------------------------------------
            // 14. Exploration system
            // Grid-based dungeon exploration with torch gauge, magic keys, and encounters.
            // Depends on floors for startZone; depends on Scenes.gameOverRef for torch depletion.
            // @source map.h — MapState, exploration callbacks
            // -------------------------------------------------------------------------
            exploration {
                preset(ExplorationPreset.DUNGEON_CRAWLER)
                startZone(floors.floor1)
                gauge("torch") {
                    max(GameConfig.TORCH_MAX)
                    initial(GameConfig.TORCH_MAX)
                    decrementPerStep(1)
                    onLow(GameConfig.TORCH_LOW_THRESHOLD) { print("Torch dimming...") }
                    onDepleted { navigate(Scenes.gameOverRef) }
                }
                keys("magic_key") {
                    max(99)
                    initial(0)
                }
                onStep { /* encounter check is handled by zone encounter tables */ }
                onInteract { /* object interaction is handled in gameplay scene frame via A button */
                }
            }

            // -------------------------------------------------------------------------
            // 15. Scenes (registered last — they reference all subsystems above)
            // Order: gameover/victory → battle → pause → gameplay → heroSelect → title
            // @source main.c — GameState enum and scene transition logic
            // -------------------------------------------------------------------------
            Scenes.register(this, sounds, combatSystem, state)

            // -------------------------------------------------------------------------
            // Starting scene
            // -------------------------------------------------------------------------
            start = Scenes.titleRef.id
        }
}
