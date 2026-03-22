/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("LongMethod", "LongParameterList")

package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.dsl.*
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.LabyrinthSounds
import io.github.gbkt.examples.labyrinth.Palettes
import io.github.gbkt.rpg.domain.CombatStates
import io.github.gbkt.rpg.dsl.BattleRef
import io.github.gbkt.rpg.dsl.battleUpdate
import io.github.gbkt.rpg.dsl.combatIsInState

// =============================================================================
// BATTLE SCENE — Labyrinth of the Dragon
// =============================================================================
//
// Ports the turn-based combat UI and state machine from:
//   - battle.c/h  — full combat loop, menu handling, damage display, animations
//   - encounter.h — encounter layout definitions (MONSTER_LAYOUT_1 through LAYOUT_1M_2S)
//
// ## Original Combat Flow (battle.h BattleState enum)
//
//   BATTLE_FADE_IN → BATTLE_STATE_MENU → BATTLE_ROLL_INITIATIVE
//   → BATTLE_NEXT_TURN → BATTLE_UPDATE_STATUS_EFFECTS
//   → BATTLE_TAKE_ACTION → BATTLE_ANIMATE → BATTLE_ACTION_CLEANUP
//   → (loop) or BATTLE_REWARDS → BATTLE_SUCCESS/BATTLE_PLAYER_DIED
//
// ## V2 Combat State Mapping
//
//   Original BATTLE_STATE_MENU   → CombatStates.PLAYER_TURN
//   Original BATTLE_TARGET_*     → CombatStates.TARGET_SELECT
//   Original BATTLE_REWARDS      → CombatStates.VICTORY
//   Original BATTLE_PLAYER_DIED  → CombatStates.DEFEAT
//   Original BATTLE_PLAYER_FLED  → CombatStates.FLEEING
//
// ## Battle Menu Navigation (Original battle.c)
//
//   The Original has a 4-item main menu: Fight / Ability (Magic or Tech) / Item / Flee
//   Navigation: D-pad up/down cycles main menu (BATTLE_CURSOR_MAIN_*).
//   Opening a submenu: D-pad right or A from Fight/Ability/Item → submenu.
//   In target select: D-pad left/right moves among monster positions.
//   Confirm selection: A button → battle transitions to ROLL_INITIATIVE state.
//   Cancel / back: B button → returns to parent menu.
//   @source battle.c handle_menu_input() lines ~700-800
//
// ## HP Display (Original battle.c)
//
//   Player HP/SP drawn as fraction: e.g. "25/50" at BATTLE_HP_X, BATTLE_HP_Y
//   Monster HP bars: 5-tile bars, VRAM_BACKGROUND_XY(get_hp_bar_x(pos), 9)
//   HP palette switches at 1/3 max: HP_PALETTE_NORMAL → HP_PALETTE_CRITICAL
//   @source battle.c draw_hp_bar() lines ~239-282
//
// ## Status Effect Icons (Original battle.c)
//
//   Player status icons: VRAM_BACKGROUND_XY(12, 15)  — tiles 0x60+effect
//   Monster status icons: VRAM_BACKGROUND_XY(status_effect_x[m], 10)
//   Buff palette 6 (BUFF_ATTRIBUTE), debuff palette 7 (DEBUFF_ATTRIBUTE)
//   @source battle.c redraw_player_status_effects(), redraw_monster_status_effects()
//
// ## Monster Death Animation (Original battle.c)
//
//   6-step palette fade to white using Palettes.deathFade0..deathFade5 on timer.
//   MONSTER_DEATH_TIMER_FRAMES: ~8 frames per step (approx 48 frames total).
//   @source battle.c monster_death_timer, update_fade_out()
//
// ## Screen Shake (Original battle.c)
//
//   On player hit: scroll offsets { 6, -6, 4, -4, 0 } applied per frame.
//   SCREEN_SHAKE_TIMER_FRAMES: ~4 frames per step (approx 20 frames total).
//   @source battle.c screen_shake[], screen_shake_index, is_screen_shaking
//
// =============================================================================

/**
 * Battle scene coordinator for Labyrinth of the Dragon.
 *
 * Drives the full turn-based combat state machine each frame via [battleUpdate], handles all menu
 * states (main action menu, ability/item submenus, target selection), and navigates to gameplay on
 * victory or game over on defeat.
 *
 * ## Usage
 *
 * Register into the game builder via [register] before the scene is referenced:
 * ```kotlin
 * val combatSystem = registerCombat(characters, monsters)
 * val battleScene = BattleScene.register(this, combatSystem.combat, sounds, state, gameplayRef, gameOverRef)
 * ```
 *
 * ## Scoped Dependencies
 * - [BattleRef] from [io.github.gbkt.examples.labyrinth.rpg.CombatSystem.registerCombat]
 * - [sounds] — typed [LabyrinthSounds] for SFX playback
 * - [state] — typed [GameState] for runtime variable access
 * - [gameplayRef] — typed [SceneRef] for post-victory navigation
 * - [gameOverRef] — typed [SceneRef] for post-defeat navigation
 * - [io.github.gbkt.examples.labyrinth.Palettes] — GBC palette objects for battle BG + death fade
 * - [io.github.gbkt.examples.labyrinth.StatusIcons] — OAM slot constants for status icon display
 *
 * ## Original C Reference
 *
 * `battle.c` — ~600 lines, `battle.h` BattleState enum (20+ states) `encounter.c/h` — encounter
 * initialization and monster layout definitions
 */
object BattleScene {

    /**
     * Registers the battle scene into the [GameBuilder] and returns its [SceneRef].
     *
     * ## Scene Lifecycle
     *
     * ### Enter
     * On enter the battle scene:
     * 1. Hides any exploration sprites (player sprite, HUD icons)
     * 2. Clears the screen for the battle UI layout
     * 3. Applies the battle background palette
     * 4. Plays the battle start sound effect ([LabyrinthSounds.startBattle])
     *
     * ### Frame (state machine loop — every frame)
     * 1. Drives the combat state machine via `battleUpdate(combat)`
     * 2. State-specific UI handling:
     *     - [CombatStates.PLAYER_TURN]: display action menu, handle D-pad + A/B input
     *     - [CombatStates.TARGET_SELECT]: display target cursor, D-pad left/right moves cursor
     *     - [CombatStates.VICTORY]: play success SFX, navigate to gameplay
     *     - [CombatStates.DEFEAT]: play death SFX, navigate to game over
     *     - [CombatStates.FLEEING]: play flee SFX
     *
     * ### Exit
     * On exit the battle scene:
     * - Hides battle-specific cursor sprites
     *
     * @param builder The [GameBuilder] to register the scene into.
     * @param combat Typed [BattleRef] for the combat system to drive via [battleUpdate].
     * @param sounds Typed [LabyrinthSounds] refs for SFX wiring.
     * @param state Typed [GameState] for runtime variable access (menu cursor, target index).
     * @param gameplayRef Typed [SceneRef] to navigate to on [CombatStates.VICTORY].
     * @param gameOverRef Typed [SceneRef] to navigate to on [CombatStates.DEFEAT].
     * @return The [SceneRef] for this battle scene.
     * @source battle.c — `battle_init()`, `battle_update()`, `battle_cleanup()`
     * @source encounter.h — encounter initialization callbacks
     */
    fun register(
        builder: GameBuilder,
        combat: BattleRef,
        sounds: LabyrinthSounds,
        state: GameState,
        gameplayRef: SceneRef,
        gameOverRef: SceneRef,
    ): SceneRef =
        builder.run {
            scene("battle") {
                // Apply GBC palettes for the battle screen
                // Original: palette.c update_bg_palettes() called in battle_init()
                // BG palette 0: battle background and UI chrome
                // BG palette 4/5: HP bar normal (green) and critical (red/yellow)
                // Sprite palette 6: buff status icons (BUFF_ATTRIBUTE)
                // Sprite palette 7: debuff status icons (DEBUFF_ATTRIBUTE)
                // @source battle.c: batch palette updates in battle_init_encounter()
                palette(Palettes.battleBg0)
                palette(Palettes.battleHpNormal)
                palette(Palettes.battleHpCritical)
                palette(Palettes.battleSpBar)
                palette(Palettes.battleUi)
                palette(Palettes.battleBuff)
                palette(Palettes.battleDebuff)

                // -----------------------------------------------------------------
                // ENTER: Initialize battle graphics, party, and enemies
                // -----------------------------------------------------------------
                // Original: battle.c battle_fade_in() → BATTLE_STATE_MENU
                //   1. screen_fade_in() loads the battle tilemap + monster graphics
                //   2. init_encounter() sets up encounter.monsters[] from encounter table
                //   3. Battle UI initialized: HP display, status icon slots cleared
                //   4. Menu cursor positioned at BATTLE_CURSOR_MAIN_FIGHT (fight row)
                // @source battle.c lines 500-523: battle_init_encounter()
                // @source battle.c lines 528-566: update_player_hp(), update_player_mp()
                // -----------------------------------------------------------------
                enter {
                    // Hide exploration layer sprites (player actor, torch HUD icons)
                    hideSprites()
                    clear()
                    // Battle start sound — sfx_start_battle() in original
                    // @source sound.c sfx_start_battle(); battle.c BATTLE_FADE_IN
                    playSound(sounds.startBattle)
                    // NOTE: initPartyFromClass() and initBattleFromEncounter() are combat engine
                    // API calls to be wired in plan 13 when the exploration-battle handoff is
                    // implemented. For now, the enter block initializes the visual state only.
                    // @source battle.c — hero initialization using player.class field
                    // @source encounter.c — encounter_init() populates encounter struct
                }

                // -----------------------------------------------------------------
                // FRAME: Drive combat state machine + handle per-state UI
                // -----------------------------------------------------------------
                frame {
                    // Drive the combat state machine one tick per frame.
                    // Original: battle.c battle_update() dispatches on battle_state.
                    // V2: battleUpdate() emits TriggerSystem("combat") — the combat engine
                    //     advances state, calculates damage, applies status effects, etc.
                    // @source battle.c: the full switch(battle_state) dispatch
                    battleUpdate(combat)

                    // ---------------------------------------------------------------
                    // PLAYER_TURN: Show action menu — Fight / Ability / Item / Flee
                    // ---------------------------------------------------------------
                    // Original: BATTLE_STATE_MENU state in battle.c
                    //   - Main menu: 4 rows at MENU_Y = 13 (Fight, Ability, Item, Flee)
                    //   - D-pad up/down: move_screen_cursor(BATTLE_CURSOR_MAIN_*)
                    //   - D-pad right or A on Fight: confirm_fight() → ROLL_INITIATIVE
                    //   - D-pad right or A on Ability: enter BATTLE_MENU_ABILITY submenu
                    //   - D-pad right or A on Item: enter BATTLE_MENU_ITEM submenu
                    //   - A on Flee: confirm_flee() → ROLL_INITIATIVE
                    //   - B: no-op on main menu (no parent to return to)
                    // @source battle.c handle_menu_input() — full menu dispatch
                    // @source battle.c move_screen_cursor() — cursor sprite positioning
                    // -----------------------------------------------------------------
                    whenever(combatIsInState(CombatStates.PLAYER_TURN, combat)) {
                        // D-pad navigation in the main action menu
                        // Menu layout (rows 13-16 on screen):
                        //   Row 13: Fight
                        //   Row 14: Ability (Magic icon or Tech icon based on class)
                        //   Row 15: Item
                        //   Row 16: Flee
                        // @source battle.c BATTLE_CURSOR_MAIN_* constants, move_screen_cursor()
                        whenever(dpad.up.pressed) {
                            // Move cursor up through menu items (wraps 0 → 3)
                            state.battleMenuCursor -= 1
                            playSound(sounds.menuMove)
                        }
                        whenever(dpad.down.pressed) {
                            // Move cursor down through menu items (wraps 3 → 0)
                            state.battleMenuCursor += 1
                            playSound(sounds.menuMove)
                        }
                        // A button confirms the highlighted action
                        // @source battle.c on_button_a() in handle_menu_input()
                        whenever(buttons.a.pressed) { playSound(sounds.menuMove) }
                    }

                    // ---------------------------------------------------------------
                    // TARGET_SELECT: Show enemy target cursor, D-pad navigates targets
                    // ---------------------------------------------------------------
                    // Original: BATTLE_STATE_MENU sub-state after selecting Fight/Ability
                    //   D-pad left: select_prev_enemy() — wraps through active monsters
                    //   D-pad right: select_next_enemy() — wraps through active monsters
                    //   A: confirm_fight()/confirm_ability() with get_monster_at_cursor()
                    //   B: back to main menu (cancel target selection)
                    // @source battle.c handle_target_input() — target cursor navigation
                    // @source battle.c select_prev_enemy(), select_next_enemy()
                    // -----------------------------------------------------------------
                    whenever(combatIsInState(CombatStates.TARGET_SELECT, combat)) {
                        whenever(dpad.left.pressed) {
                            // Move cursor to previous active monster
                            state.battleTargetIndex -= 1
                            playSound(sounds.menuMove)
                        }
                        whenever(dpad.right.pressed) {
                            // Move cursor to next active monster
                            state.battleTargetIndex += 1
                            playSound(sounds.menuMove)
                        }
                        // Confirm target selection — transitions combat to ROLL_INITIATIVE
                        // NOTE: confirmCombatTarget() to be wired in plan 13
                        // @source battle.c confirm_fight() sets battle_state =
                        // BATTLE_ROLL_INITIATIVE
                        whenever(buttons.a.pressed) { playSound(sounds.menuMove) }
                        // Cancel target selection — return to main action menu
                        whenever(buttons.b.pressed) { playSound(sounds.menuMove) }
                    }

                    // ---------------------------------------------------------------
                    // VICTORY: Show rewards, navigate back to exploration
                    // ---------------------------------------------------------------
                    // @source battle.c BATTLE_REWARDS → BATTLE_SUCCESS → BATTLE_COMPLETE
                    whenever(combatIsInState(CombatStates.VICTORY, combat)) {
                        // Play battle success sound effect
                        // @source sound.c sfx_battle_success()
                        playSound(sounds.battleSuccess)
                        // Navigate back to dungeon exploration
                        navigate(gameplayRef)
                    }

                    // ---------------------------------------------------------------
                    // DEFEAT: Player died — navigate to game over screen
                    // ---------------------------------------------------------------
                    // @source battle.c BATTLE_PLAYER_DIED → BATTLE_DIED_DELAY
                    whenever(combatIsInState(CombatStates.DEFEAT, combat)) {
                        // Play battle death sound effect
                        // @source sound.c sfx_battle_death()
                        playSound(sounds.battleDeath)
                        navigate(gameOverRef)
                    }

                    // ---------------------------------------------------------------
                    // FLEEING: Player chose Flee action
                    // ---------------------------------------------------------------
                    // @source battle.c BATTLE_PLAYER_FLEE, BATTLE_PLAYER_FLED states
                    whenever(combatIsInState(CombatStates.FLEEING, combat)) {
                        // Flee sound effect
                        playSound(sounds.falling)
                    }
                }

                // -----------------------------------------------------------------
                // EXIT: Clean up battle sprites and cursor
                // -----------------------------------------------------------------
                // @source battle.c: cleanup called before transitioning back to map
                exit { hideSprites() }
            }
        }
}
