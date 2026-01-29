/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.assets
import io.github.gbkt.core.builder.camera
import io.github.gbkt.core.builder.palette
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.exploration.exploration
import io.github.gbkt.core.flow.gameFlow
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.rpg.StatSchema
import io.github.gbkt.core.rpg.registerBattleSystem
import io.github.gbkt.core.rpg.registerCombatFormulas
import io.github.gbkt.core.rpg.useStatSchema
import io.github.gbkt.core.ui.BarOrientation
import io.github.gbkt.core.ui.StatusBarStyle
import io.github.gbkt.core.ui.statusBar
import io.github.gbkt.core.world.flags
import io.github.gbkt.examples.labyrinth.rpg.Abilities
import io.github.gbkt.examples.labyrinth.rpg.Characters
import io.github.gbkt.examples.labyrinth.rpg.Items
import io.github.gbkt.examples.labyrinth.rpg.MonsterAbilities
import io.github.gbkt.examples.labyrinth.rpg.Monsters
import io.github.gbkt.examples.labyrinth.rpg.StatusEffects
import io.github.gbkt.examples.labyrinth.rpg.createCombatFormulas
import io.github.gbkt.examples.labyrinth.rpg.createCombatSystem
import io.github.gbkt.examples.labyrinth.scenes.BattleSceneState
import io.github.gbkt.examples.labyrinth.scenes.CreditsSceneState
import io.github.gbkt.examples.labyrinth.scenes.Scenes
import io.github.gbkt.examples.labyrinth.scenes.SettingsSceneState
import io.github.gbkt.examples.labyrinth.world.Floors

// Asset path constants
private const val STATUS_ICONS_SPRITE_PATH = "sprites/status_icons.png"

/**
 * Labyrinth of the Dragon - gbkt Port
 *
 * A D&D-style dungeon crawler RPG ported to the gbkt Kotlin DSL. Original game:
 * https://github.com/NESHacker/LabyrinthOfTheDragon
 *
 * Game Features:
 * - 4 playable character classes (Druid, Fighter, Monk, Sorcerer)
 * - 12 unique monsters across 8 dungeon floors
 * - 24 class-specific abilities
 * - Turn-based combat with elemental weaknesses
 * - Dungeon exploration with random encounters
 *
 * File Structure:
 * - LabyrinthOfTheDragon.kt - Main entry point (this file)
 * - rpg/Abilities.kt - All 24 ability definitions
 * - rpg/Characters.kt - The 4 playable classes
 * - rpg/Monsters.kt - All 12 monster definitions
 * - scenes/Scenes.kt - Game scenes (title, gameplay, battle, etc.)
 */
val labyrinthOfTheDragon =
    gbGame("LabyrinthDragon") {
        // =========================================================================
        // HARDWARE CONFIGURATION
        // =========================================================================

        // Match original LabyrinthOfTheDragon ROM configuration
        config {
            cartridge = io.github.gbkt.core.Cartridge.MBC5_RAM_BATTERY
            romBanks = 32 // 512KB ROM
            ramBanks = 4 // 32KB SRAM (4 banks)
            gbcSupport = true

            // Banking layout matches original game's ~60 C files with pragma bank directives
            banking {
                menuBank = 1 // UI, title, menus
                explorationBank = 2 // Map, exploration
                battleBank = 3 // Battle system, items
                playerBank = 4 // Player management
                statsBank = 5 // Stats, leveling
                monsterBank1 = 6 // Monster AI (first half: Kobold-GelatinousCube)
                monsterBank2 = 7 // Monster AI (second half: DisplacerBeast-Dragon)
                floorDataBank = 8 // Floor definitions
                sceneBank = 10 // Scene handlers
                soundBank = 30 // Sound effects
            }
        }

        // Asset directory relative to the port project root
        assets { directory = "res" }

        // =========================================================================
        // STAT SCHEMA DEFINITION
        // =========================================================================

        // Use the standard JRPG stat schema (HP, SP, ATK, DEF, MATK, MDEF, AGL, Level, EXP)
        useStatSchema(StatSchema.STANDARD_JRPG)

        // =========================================================================
        // RPG DEFINITIONS
        // =========================================================================

        // Initialize status effects (must come first - abilities and items reference them)
        val statusEffects = StatusEffects(this)

        // Initialize all abilities (Druid, Fighter, Monk, Sorcerer skills)
        val abilities = Abilities(this, statusEffects)

        // Initialize monster abilities (used by monster AI - requires status effects)
        val monsterAbilities = MonsterAbilities(this, statusEffects)

        // Initialize character classes (requires abilities for level-based unlocks)
        val characters = Characters(this, abilities)

        // Initialize items (consumables, monster drops - needed before Monsters for drop
        // definitions)
        val items = Items(this, statusEffects)

        // Initialize monster definitions (requires items for drop tables, monster abilities for AI)
        val monsters = Monsters(this, items, monsterAbilities)

        // Initialize sound effects
        val sounds = Sounds(this)

        // =========================================================================
        // CAMERA SYSTEM
        // =========================================================================

        // Main camera for screen effects (shake, fades, transitions)
        val gameCamera = camera {
            smoothing = 0.15f
            offset(0, 0)
        }

        // =========================================================================
        // MONSTER PALETTES (for death animation)
        // =========================================================================

        // Monster palette slot 1 (used for monsters in battle)
        val monsterPalette =
            palette("monster_pal") {
                // Default monster colors (red/purple theme from original)
                colors(0xFFFFFF, 0xCF4242, 0x80295C, 0x2A0945)
                slot = 1
                type = PaletteType.BACKGROUND
                forBackground()
            }

        // =========================================================================
        // HP BAR STATUS BARS (Tile-based rendering)
        // =========================================================================

        // Monster HP bars using tile-based PIPS style
        // Original uses tiles 0x50 (empty) through 0x58 (full) with 8 partial states
        // Position: 5 tiles wide at calculated X based on monster position
        // Critical HP (≤33%): Palette swap triggers warning callback

        /** Monster 1 HP bar (leftmost position) */
        val monster1HpBar by statusBar {
            position(GameConfig.HP_BAR_X1, GameConfig.HP_BAR_Y)
            size(GameConfig.HP_BAR_WIDTH, GameConfig.HP_BAR_HEIGHT)
            style(StatusBarStyle.PIPS)
            orientation(BarOrientation.HORIZONTAL)
            pips(GameConfig.HP_BAR_PIPS) // 5 pip segments
            animationSpeed(2) // Smooth animation (2 units per frame)
            tiles {
                empty(GameConfig.TILE_HP_EMPTY) // 0x50
                filled(GameConfig.TILE_HP_FULL) // 0x58
                partial(GameConfig.TILE_HP_PARTIAL) // 0x51-0x57 for partial fill
            }
            // Critical HP threshold at 33% - triggers warning palette
            criticalThreshold(33)
        }

        /** Monster 2 HP bar (center position) */
        val monster2HpBar by statusBar {
            position(GameConfig.HP_BAR_X2, GameConfig.HP_BAR_Y)
            size(GameConfig.HP_BAR_WIDTH, GameConfig.HP_BAR_HEIGHT)
            style(StatusBarStyle.PIPS)
            orientation(BarOrientation.HORIZONTAL)
            pips(GameConfig.HP_BAR_PIPS)
            animationSpeed(2)
            tiles {
                empty(GameConfig.TILE_HP_EMPTY)
                filled(GameConfig.TILE_HP_FULL)
                partial(GameConfig.TILE_HP_PARTIAL)
            }
            criticalThreshold(33)
        }

        /** Monster 3 HP bar (rightmost position) */
        val monster3HpBar by statusBar {
            position(GameConfig.HP_BAR_X3, GameConfig.HP_BAR_Y)
            size(GameConfig.HP_BAR_WIDTH, GameConfig.HP_BAR_HEIGHT)
            style(StatusBarStyle.PIPS)
            orientation(BarOrientation.HORIZONTAL)
            pips(GameConfig.HP_BAR_PIPS)
            animationSpeed(2)
            tiles {
                empty(GameConfig.TILE_HP_EMPTY)
                filled(GameConfig.TILE_HP_FULL)
                partial(GameConfig.TILE_HP_PARTIAL)
            }
            criticalThreshold(33)
        }

        // =========================================================================
        // STATUS EFFECT ICON SPRITES
        // =========================================================================

        // Status effect icons displayed during battle. Each combatant (player + 3 monsters)
        // can display up to 4 status effect icons. Sprites use tile-based rendering where
        // the tile index determines which status icon is shown (tiles 0x60-0x72).
        //
        // Original implementation:
        // - Tiles 0x60-0x6F for 16 status types
        // - Debuffs use palette 7 (red)
        // - Buffs use palette 6 (blue)
        //
        // Each sprite starts hidden and is shown when a status effect is active.

        // Player status icon sprites (4 slots at bottom of screen)
        val playerStatusIcon1 by entity {
            position(GameConfig.STATUS_ICON_PLAYER_X, GameConfig.STATUS_ICON_PLAYER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val playerStatusIcon2 by entity {
            position(GameConfig.STATUS_ICON_PLAYER_X + 8, GameConfig.STATUS_ICON_PLAYER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val playerStatusIcon3 by entity {
            position(GameConfig.STATUS_ICON_PLAYER_X + 16, GameConfig.STATUS_ICON_PLAYER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val playerStatusIcon4 by entity {
            position(GameConfig.STATUS_ICON_PLAYER_X + 24, GameConfig.STATUS_ICON_PLAYER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }

        // Monster 1 status icon sprites (4 slots near monster 1 HP bar)
        val monster1StatusIcon1 by entity {
            position(GameConfig.STATUS_ICON_M1_X, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster1StatusIcon2 by entity {
            position(GameConfig.STATUS_ICON_M1_X + 8, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster1StatusIcon3 by entity {
            position(GameConfig.STATUS_ICON_M1_X + 16, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster1StatusIcon4 by entity {
            position(GameConfig.STATUS_ICON_M1_X + 24, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }

        // Monster 2 status icon sprites (4 slots near monster 2 HP bar)
        val monster2StatusIcon1 by entity {
            position(GameConfig.STATUS_ICON_M2_X, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster2StatusIcon2 by entity {
            position(GameConfig.STATUS_ICON_M2_X + 8, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster2StatusIcon3 by entity {
            position(GameConfig.STATUS_ICON_M2_X + 16, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster2StatusIcon4 by entity {
            position(GameConfig.STATUS_ICON_M2_X + 24, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }

        // Monster 3 status icon sprites (4 slots near monster 3 HP bar)
        val monster3StatusIcon1 by entity {
            position(GameConfig.STATUS_ICON_M3_X, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster3StatusIcon2 by entity {
            position(GameConfig.STATUS_ICON_M3_X + 8, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster3StatusIcon3 by entity {
            position(GameConfig.STATUS_ICON_M3_X + 16, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }
        val monster3StatusIcon4 by entity {
            position(GameConfig.STATUS_ICON_M3_X + 24, GameConfig.STATUS_ICON_MONSTER_Y)
            sprite(SpriteAsset(STATUS_ICONS_SPRITE_PATH)) { size = 8 x 8 }
        }

        // =========================================================================
        // PLAYER SPRITE
        // =========================================================================

        // Player sprite for exploration movement (sprite position updated during movement)
        // Uses entity DSL to create sprite with OAM slot allocation
        val playerEntity by entity {
            // Initial position will be set by exploration system
            position(
                GameConfig.PLAYER_START_X * GameConfig.TILE_SIZE,
                GameConfig.PLAYER_START_Y * GameConfig.TILE_SIZE,
            )
            sprite(SpriteAsset("sprites/hero.png")) {
                size = 8 x 16 // Standard 8x16 Game Boy sprite
            }
        }

        // =========================================================================
        // GAME FLAGS
        // =========================================================================

        // Flags for tracking chest/door states and story progression
        // Organized into 3 pages to fit within 8-page limit (256 flags total)
        // NOTE: Must be initialized before Floors so NPC interactions can reference flags
        val gameFlags = flags {
            // Page 0: Floors 1-4 chest flags (32 flags = 4 floors × 8 chests)
            page("chests_1_4") {
                // Floor 1 chests
                flag("f1_chest1")
                flag("f1_chest2")
                flag("f1_chest3")
                flag("f1_chest4")
                flag("f1_chest5")
                flag("f1_chest6")
                flag("f1_chest7")
                flag("f1_chest8")
                // Floor 2 chests
                flag("f2_chest1")
                flag("f2_chest2")
                flag("f2_chest3")
                flag("f2_chest4")
                flag("f2_chest5")
                flag("f2_chest6")
                flag("f2_chest7")
                flag("f2_chest8")
                // Floor 3 chests
                flag("f3_chest1")
                flag("f3_chest2")
                flag("f3_chest3")
                flag("f3_chest4")
                flag("f3_chest5")
                flag("f3_chest6")
                flag("f3_chest7")
                flag("f3_chest8")
                // Floor 4 chests
                flag("f4_chest1")
                flag("f4_chest2")
                flag("f4_chest3")
                flag("f4_chest4")
                flag("f4_chest5")
                flag("f4_chest6")
                flag("f4_chest7")
                flag("f4_chest8")
            }

            // Page 1: Floors 5-8 chest flags (32 flags = 4 floors × 8 chests)
            page("chests_5_8") {
                // Floor 5 chests
                flag("f5_chest1")
                flag("f5_chest2")
                flag("f5_chest3")
                flag("f5_chest4")
                flag("f5_chest5")
                flag("f5_chest6")
                flag("f5_chest7")
                flag("f5_chest8")
                // Floor 6 chests
                flag("f6_chest1")
                flag("f6_chest2")
                flag("f6_chest3")
                flag("f6_chest4")
                flag("f6_chest5")
                flag("f6_chest6")
                flag("f6_chest7")
                flag("f6_chest8")
                // Floor 7 chests
                flag("f7_chest1")
                flag("f7_chest2")
                flag("f7_chest3")
                flag("f7_chest4")
                flag("f7_chest5")
                flag("f7_chest6")
                flag("f7_chest7")
                flag("f7_chest8")
                // Floor 8 chests
                flag("f8_chest1")
                flag("f8_chest2")
                flag("f8_chest3")
                flag("f8_chest4")
                flag("f8_chest5")
                flag("f8_chest6")
                flag("f8_chest7")
                flag("f8_chest8")
            }

            // Page 2: Doors and story progression (12 flags)
            page("world") {
                // Door states
                flag("door1")
                flag("door2")
                flag("door3")
                flag("door4")
                flag("door5")
                flag("door6")
                flag("door7")
                flag("door8")
                // Story progression
                flag("hasTorch")
                flag("gotMagicKey")
                flag("metElder")
                flag("defeatedDragon")
                // Boss encounter triggers
                flag("dragonBattleTriggered")
            }
        }

        // Initialize dungeon floors (8 floors with encounters and map objects)
        val floors = Floors(this, monsters, items, gameFlags)

        // =========================================================================
        // EXPLORATION SYSTEM
        // =========================================================================

        // Unified exploration controller for dungeon crawling
        val dungeonExploration by exploration {
            // 8x8 pixel tiles for Game Boy
            tileSize(GameConfig.TILE_SIZE)
            // Grid-based movement (classic dungeon crawler style)
            movementStyle(GameConfig.MOVEMENT_STYLE)
            // 8 frames per tile (4 pixels per frame at 60fps)
            movementSpeed(GameConfig.MOVEMENT_SPEED)

            // Player sprite to update during movement interpolation
            playerEntity.sprite?.let { playerSprite(it) }

            // Torch gauge - depletes as you explore
            gauge("torch") {
                max(GameConfig.TORCH_MAX)
                initial(GameConfig.TORCH_INITIAL)
                decrementPerStep(GameConfig.TORCH_DECREMENT)
                onLow(GameConfig.TORCH_LOW_THRESHOLD) {
                    // Torch getting dim warning (will trigger message)
                }
                onDepleted {
                    // Darkness penalty - increased encounter rate
                }
            }

            // Magic keys for locked doors and chests
            keys("magic_key") {
                max(GameConfig.KEYS_MAX)
                initial(GameConfig.KEYS_INITIAL)
            }

            // Start on floor 1
            startZone(floors.floor1)

            // On each step: check for random encounters
            onStep {
                // Encounter checking is handled by the floor's encounter table
            }

            // On interact: check for interactable objects (chests, NPCs, etc.)
            onInteract {
                // Map object interaction will be handled by the MapObject system
            }

            // On blocked movement: play bump sound
            onBlocked {
                // Play wall bump sound effect
            }
        }

        // =========================================================================
        // PLAYER STATE VARIABLES
        // =========================================================================

        // Player position (tile coordinates)
        var playerX by u8Var(GameConfig.PLAYER_START_X)
        var playerY by u8Var(GameConfig.PLAYER_START_Y)

        // Current floor (0-7, corresponding to floors 1-8)
        var currentFloor by u8Var(GameConfig.START_FLOOR)

        // Movement cooldown (prevents too-fast movement)
        var moveCooldown by u8Var(0)

        // Step counter for random encounters
        var stepCount by u8Var(0)

        // Torch fuel (starts at TORCH_INITIAL, decreases each step, refilled at sconces)
        var torchFuel by u8Var(GameConfig.TORCH_INITIAL)

        // Magic key count (used to unlock doors/chests)
        var keyCount by u8Var(0)

        // Selected character class (0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer)
        var selectedClass by u8Var(0)

        // Title menu cursor position (0=New Game, 1=Continue)
        var titleMenuCursor by u8Var(0)

        // Hero selection cursor position (0-3)
        var heroSelectCursor by u8Var(0)

        // Pause menu cursor position (0=Resume, 1=Save, 2=Load, 3=Quit)
        var pauseMenuCursor by u8Var(0)

        // Save/load slot selection cursor (0-2)
        var saveSlotCursor by u8Var(0)

        // Pause menu sub-state (0=main, 1=save slots, 2=load slots, 3=confirm)
        var pauseMenuState by u8Var(0)

        // Create game state holder for scene access
        val gameState =
            GameState(
                playerX = playerX,
                playerY = playerY,
                currentFloor = currentFloor,
                moveCooldown = moveCooldown,
                stepCount = stepCount,
                torchFuel = torchFuel,
                keyCount = keyCount,
                selectedClass = selectedClass,
                titleMenuCursor = titleMenuCursor,
                heroSelectCursor = heroSelectCursor,
                pauseMenuCursor = pauseMenuCursor,
                saveSlotCursor = saveSlotCursor,
                pauseMenuState = pauseMenuState,
            )

        // =========================================================================
        // BATTLE STATE VARIABLES
        // =========================================================================

        // Battle menu state (0=main, 1=target, 2=ability, 3=item, 4=execute, 5=enemy, 6=result)
        var battleMenuState by u8Var(0)
        var battleMenuCursor by u8Var(0)
        var battleTargetCursor by u8Var(0)
        var battleAbilityCursor by u8Var(0)
        var battleItemCursor by u8Var(0)
        var battleTurnPhase by u8Var(0)
        var battleAnimTimer by u8Var(0)

        // HP bar animation state (for up to 3 monsters)
        var monster1DisplayHP by u8Var(0)
        var monster1TargetHP by u8Var(0)
        var monster2DisplayHP by u8Var(0)
        var monster2TargetHP by u8Var(0)
        var monster3DisplayHP by u8Var(0)
        var monster3TargetHP by u8Var(0)

        // Death animation state
        var deathAnimState by u8Var(0)
        var deathAnimMonster by u8Var(0)
        var deathAnimStep by u8Var(0)
        var deathAnimTimer by u8Var(0)

        // Screen shake state
        var shakeStep by u8Var(0)
        var shakeTimer by u8Var(0)

        // Action result message state
        var lastDamage by u8Var(0)
        var lastActionResult by u8Var(0)
        var messageTimer by u8Var(0)

        // Status effect tracking (bitmask for active effects per combatant)
        var playerStatusEffects by u8Var(0)
        var monster1StatusEffects by u8Var(0)
        var monster2StatusEffects by u8Var(0)
        var monster3StatusEffects by u8Var(0)

        // Create battle scene state holder
        val battleSceneState =
            BattleSceneState(
                menuState = battleMenuState,
                menuCursor = battleMenuCursor,
                targetCursor = battleTargetCursor,
                abilityCursor = battleAbilityCursor,
                itemCursor = battleItemCursor,
                turnPhase = battleTurnPhase,
                animTimer = battleAnimTimer,
                // HP bar animation
                monster1DisplayHP = monster1DisplayHP,
                monster1TargetHP = monster1TargetHP,
                monster2DisplayHP = monster2DisplayHP,
                monster2TargetHP = monster2TargetHP,
                monster3DisplayHP = monster3DisplayHP,
                monster3TargetHP = monster3TargetHP,
                // Death animation
                deathAnimState = deathAnimState,
                deathAnimMonster = deathAnimMonster,
                deathAnimStep = deathAnimStep,
                deathAnimTimer = deathAnimTimer,
                // Screen shake
                shakeStep = shakeStep,
                shakeTimer = shakeTimer,
                // Action result
                lastDamage = lastDamage,
                lastActionResult = lastActionResult,
                messageTimer = messageTimer,
                // Status effect icons
                playerStatusEffects = playerStatusEffects,
                monster1StatusEffects = monster1StatusEffects,
                monster2StatusEffects = monster2StatusEffects,
                monster3StatusEffects = monster3StatusEffects,
            )

        // =========================================================================
        // CREDITS STATE VARIABLES
        // =========================================================================

        // Credits scene state machine
        var creditsStateVar by u8Var(0) // 0=FADE_IN, 1=HOLD, 2=FADE_OUT
        var creditsPageIndex by u8Var(0) // Current page (0-7)
        var creditsFrameCounter by u8Var(0) // Frame timing

        // Create credits scene state holder
        val creditsSceneState =
            CreditsSceneState(
                creditsState = creditsStateVar,
                pageIndex = creditsPageIndex,
                frameCounter = creditsFrameCounter,
            )

        // =========================================================================
        // SETTINGS STATE VARIABLES
        // =========================================================================

        // Settings/audio configuration
        var masterVolume by u8Var(7) // 0-7, default max
        var sfxEnabled by u8Var(1) // 0=off, 1=on
        var settingsCursor by u8Var(0) // Menu position

        // Create settings scene state holder
        val settingsSceneState =
            SettingsSceneState(
                masterVolume = masterVolume,
                sfxEnabled = sfxEnabled,
                settingsCursor = settingsCursor,
            )

        // =========================================================================
        // SAVE SYSTEM
        // =========================================================================

        // Create save data structure for 3 save slots
        val save = createSaveData()

        // =========================================================================
        // COMBAT SYSTEM
        // =========================================================================

        // Create combat system for turn-based battles (with sound effects)
        val combatSystem = createCombatSystem(sounds)

        // Create combat formulas (hit chance, crits, damage variance)
        val combatFormulas = createCombatFormulas()

        // Register battle system at game scope (generates C code at file scope, not inside
        // functions)
        registerBattleSystem(combatSystem)
        registerCombatFormulas(combatFormulas)

        // =========================================================================
        // STATUS ICONS HOLDER
        // =========================================================================

        // Create status icons container for all 16 icon sprites (4 per combatant)
        val statusIcons =
            StatusIcons(
                playerIcon1 = playerStatusIcon1,
                playerIcon2 = playerStatusIcon2,
                playerIcon3 = playerStatusIcon3,
                playerIcon4 = playerStatusIcon4,
                monster1Icon1 = monster1StatusIcon1,
                monster1Icon2 = monster1StatusIcon2,
                monster1Icon3 = monster1StatusIcon3,
                monster1Icon4 = monster1StatusIcon4,
                monster2Icon1 = monster2StatusIcon1,
                monster2Icon2 = monster2StatusIcon2,
                monster2Icon3 = monster2StatusIcon3,
                monster2Icon4 = monster2StatusIcon4,
                monster3Icon1 = monster3StatusIcon1,
                monster3Icon2 = monster3StatusIcon2,
                monster3Icon3 = monster3StatusIcon3,
                monster3Icon4 = monster3StatusIcon4,
            )

        // =========================================================================
        // SCENES
        // =========================================================================

        // Initialize all game scenes (pass combat system, formulas, save data, monsters, flags,
        // sounds, camera, palette, HP bars, and status icons)
        val scenes =
            Scenes(
                this,
                gameState,
                battleSceneState,
                creditsSceneState,
                settingsSceneState,
                combatSystem,
                combatFormulas,
                save,
                monsters,
                gameFlags,
                sounds,
                gameCamera,
                monsterPalette,
                monster1HpBar,
                monster2HpBar,
                monster3HpBar,
                statusIcons,
            )
        scenes.initAll()

        // =========================================================================
        // GAME FLOW
        // =========================================================================

        val flow = gameFlow {
            titleScreen(scenes.title)
            gameplay(scenes.gameplay)
            battle(scenes.battle)
            gameOver(scenes.gameOver)
            victory(scenes.victory)
        }

        // =========================================================================
        // GAME START
        // =========================================================================

        start = flow.getStartScene() ?: scenes.title

        // =========================================================================
        // SUPPRESS UNUSED WARNINGS (all content is registered via delegates)
        // =========================================================================

        // Abilities are registered but referenced here to suppress warnings
        @Suppress("UNUSED_VARIABLE")
        val registeredContent =
            listOf(
                // Druid
                abilities.cureWounds,
                abilities.barkSkin,
                abilities.lightning,
                abilities.majorHeal,
                abilities.insectPlague,
                abilities.regenerate,
                // Fighter
                abilities.secondWind,
                abilities.actionSurge,
                abilities.cleave,
                abilities.tripAttack,
                abilities.menace,
                abilities.indomitable,
                // Monk
                abilities.evasion,
                abilities.openPalm,
                abilities.stillMind,
                abilities.flurry,
                abilities.diamondBody,
                abilities.quiveringPalm,
                // Sorcerer
                abilities.darkness,
                abilities.fireball,
                abilities.haste,
                abilities.sleetStorm,
                abilities.disintegrate,
                abilities.wildMagic,
                // Characters
                characters.druid,
                characters.fighter,
                characters.monk,
                characters.sorcerer,
                // Monsters
                monsters.kobold,
                monsters.goblin,
                monsters.zombie,
                monsters.bugbear,
                monsters.owlbear,
                monsters.gelatinousCube,
                monsters.displacerBeast,
                monsters.willOWisp,
                monsters.deathknight,
                monsters.mindflayer,
                monsters.beholder,
                monsters.dragon,
                // Items
                items.potion,
                items.ether,
                items.elixir,
                items.remedy,
                items.atkUp,
                items.defUp,
                items.regen,
                items.haste,
                // Status Effects
                statusEffects.regen,
                statusEffects.poison,
                statusEffects.burn,
                statusEffects.atkUp,
                statusEffects.defUp,
                statusEffects.haste,
                statusEffects.evasion,
                statusEffects.barkskin,
                statusEffects.diamondBody,
                statusEffects.atkDown,
                statusEffects.defDown,
                statusEffects.slow,
                statusEffects.blind,
                statusEffects.scared,
                statusEffects.stun,
                statusEffects.sleep,
                statusEffects.paralysis,
                statusEffects.confusion,
                // Monster Abilities
                monsterAbilities.fireLoogie,
                monsterAbilities.poisonBite,
                monsterAbilities.hellfireOrb,
                monsterAbilities.mindBlast,
                monsterAbilities.extractBrain,
                monsterAbilities.disintegrationRay,
                monsterAbilities.fearRay,
                monsterAbilities.flameBreath,
                monsterAbilities.rage,
                // Floors
                floors.floor1,
                floors.floor2,
                floors.floor3,
                floors.floor4,
                floors.floor5,
                floors.floor6,
                floors.floor7,
                floors.floor8,
                // Save System
                save,
                // Exploration System
                dungeonExploration,
                // Sound Effects
                sounds.menuMove,
                sounds.menuSelect,
                sounds.attack,
                sounds.heal,
                sounds.defeat,
                sounds.victory,
                sounds.encounter,
                sounds.evade,
                sounds.poisonSpray,
                sounds.monkStrike,
                sounds.actionSurge,
                sounds.stairs,
                sounds.falling,
                // Credits state
                creditsStateVar,
                creditsPageIndex,
                creditsFrameCounter,
                // Settings state
                masterVolume,
                sfxEnabled,
                settingsCursor,
                // Battle animation state
                monster1DisplayHP,
                monster1TargetHP,
                monster2DisplayHP,
                monster2TargetHP,
                monster3DisplayHP,
                monster3TargetHP,
                deathAnimState,
                deathAnimMonster,
                deathAnimStep,
                deathAnimTimer,
                shakeStep,
                shakeTimer,
                lastDamage,
                lastActionResult,
                messageTimer,
                // HP bar status bars
                monster1HpBar,
                monster2HpBar,
                monster3HpBar,
                // Status effect icon sprites
                playerStatusIcon1,
                playerStatusIcon2,
                playerStatusIcon3,
                playerStatusIcon4,
                monster1StatusIcon1,
                monster1StatusIcon2,
                monster1StatusIcon3,
                monster1StatusIcon4,
                monster2StatusIcon1,
                monster2StatusIcon2,
                monster2StatusIcon3,
                monster2StatusIcon4,
                monster3StatusIcon1,
                monster3StatusIcon2,
                monster3StatusIcon3,
                monster3StatusIcon4,
                // Status icons holder
                statusIcons,
            )
    }

/** Main entry point for standalone execution. */
fun main() {
    println("======================================")
    println("  Labyrinth of the Dragon - gbkt Port")
    println("======================================")
    println()
    println("Characters:")
    println("  - Druid (Healer / Nature Magic)")
    println("  - Fighter (Tank / Physical DPS)")
    println("  - Monk (Speed / Martial Arts)")
    println("  - Sorcerer (Glass Cannon / Arcane)")
    println()
    println("Monsters: 12 unique creatures")
    println("  Floors 1-2: Kobold, Goblin, Zombie")
    println("  Floors 3-4: Bugbear, Owlbear, Gelatinous Cube")
    println("  Floors 5-6: Displacer Beast, Will-o'-Wisp")
    println("  Floors 7-8: Death Knight, Mind Flayer, Beholder")
    println("  Final Boss: DRAGON")
    println()
    println("Code generation complete!")
}
