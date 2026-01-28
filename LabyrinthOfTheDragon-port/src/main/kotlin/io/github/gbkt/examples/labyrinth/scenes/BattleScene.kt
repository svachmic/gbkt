/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.ShakeDecay
import io.github.gbkt.core.print
import io.github.gbkt.core.rpg.BattleSystem
import io.github.gbkt.core.rpg.CombatFormulas
import io.github.gbkt.core.rpg.battleUpdate
import io.github.gbkt.core.rpg.combatIsInState
import io.github.gbkt.core.rpg.combatPartyCount
import io.github.gbkt.core.rpg.confirmCombatTarget
import io.github.gbkt.core.rpg.initBattleFromEncounter
import io.github.gbkt.core.rpg.initPartyFromClass
import io.github.gbkt.core.rpg.selectCombatItem
import io.github.gbkt.core.rpg.transitionToCombatState
import io.github.gbkt.core.screen
import io.github.gbkt.core.ui.StatusBarHandle
import io.github.gbkt.examples.labyrinth.GameConfig
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.Sounds
import io.github.gbkt.examples.labyrinth.StatusIcons

/**
 * Battle Scene
 *
 * Turn-based combat mode. Player selects actions (Attack, Ability, Item, Flee) and battles against
 * monsters.
 *
 * Architecture:
 * - **Framework (BattleSystem)**: Handles combat mechanics - damage calculation, turn order, status
 *   effects, critical hits, and hit/miss via registered CombatFormulas.
 * - **Scene (this code)**: Handles UI layer - menu rendering, cursor navigation, user input, and
 *   screen presentation.
 *
 * Battle Menu States (battleState.menuState):
 * - 0 = Main menu (Attack/Ability/Item/Flee)
 * - 1 = Target selection
 * - 2 = Ability selection
 * - 3 = Item selection
 * - 4 = Action executing (framework handles combat)
 * - 5 = Enemy turn (framework handles AI)
 * - 6 = Result display
 */
@Suppress("LongMethod", "LongParameterList", "UnusedParameter")
fun GameBuilder.initBattleScene(
    state: GameState,
    battleState: BattleSceneState,
    combatSystem: BattleSystem,
    combatFormulas: CombatFormulas,
    sounds: Sounds,
    gameplay: SceneRef,
    camera: Camera,
    monsterPalette: Palette,
    monster1HpBar: StatusBarHandle,
    monster2HpBar: StatusBarHandle,
    monster3HpBar: StatusBarHandle,
    statusIcons: StatusIcons,
): SceneRef =
    scene("battle") {
        enter {
            // Note: Battle system is registered at game scope, not here
            // (registerBattleSystem is called in LabyrinthOfTheDragon.kt)

            // Initialize party from selected character class
            initPartyFromClass(state.selectedClass)
            // Initialize enemies from pending encounter (set by exploration system)
            initBattleFromEncounter()

            screen.clear()
            // Initialize battle state
            battleState.menuState set 0 // Main menu
            battleState.menuCursor set 0 // First option
            battleState.targetCursor set 0
            battleState.turnPhase set 0 // Player turn

            // Initialize animation state
            battleState.monster1DisplayHP set 100 // Will be updated with actual HP
            battleState.monster1TargetHP set 100
            battleState.monster2DisplayHP set 100
            battleState.monster2TargetHP set 100
            battleState.monster3DisplayHP set 100
            battleState.monster3TargetHP set 100
            battleState.deathAnimState set 0
            battleState.deathAnimStep set 0
            battleState.deathAnimTimer set 0
            battleState.shakeStep set 0
            battleState.shakeTimer set 0
            battleState.lastActionResult set 0
            battleState.messageTimer set 0

            // Draw battle UI
            print("MONSTER ATTACK!") at (2 to 1)
            print("") at (0 to 4)

            // Initialize HP bars using tile-based StatusBar system
            // Each monster HP bar is shown and initialized with full health
            monster1HpBar.show()
            monster1HpBar.setValue(100, 100) // Will be set to actual HP when enemy data loads
            monster2HpBar.show()
            monster2HpBar.setValue(100, 100)
            monster3HpBar.show()
            monster3HpBar.setValue(100, 100)

            // Initialize status effect icons (all hidden at start)
            // Icons are shown when status effects become active during battle
            statusIcons.allIcons.forEach { it.hide() }

            // Initialize status effect bitmasks to 0 (no effects active)
            battleState.playerStatusEffects set 0
            battleState.monster1StatusEffects set 0
            battleState.monster2StatusEffects set 0
            battleState.monster3StatusEffects set 0

            // Draw main menu
            print(">ATTACK") at (1 to 13)
            print(" ABILITY") at (1 to 14)
            print(" ITEM") at (11 to 13)
            print(" FLEE") at (11 to 14)
        }

        every.frame {
            // =========================================================
            // FRAMEWORK: Update battle state machine
            // =========================================================
            // This drives the combat mechanics - damage calculation, turn order,
            // status effects, AI decisions, and victory/defeat checking.
            battleUpdate(combatSystem)

            // =========================================================
            // HP BAR ANIMATION
            // =========================================================
            // StatusBar handles smooth animation internally via tick()
            // Update HP bars with target values each frame - the StatusBar
            // system handles tile-based rendering and animation smoothing
            //
            // Note: In a full implementation, these would be connected to the
            // actual combatant HP values from the battle system. For now we
            // use the display/target state variables for animation.
            monster1HpBar.setValue(
                Expr(battleState.monster1DisplayHP.ir),
                Expr(battleState.monster1TargetHP.ir),
            )
            monster2HpBar.setValue(
                Expr(battleState.monster2DisplayHP.ir),
                Expr(battleState.monster2TargetHP.ir),
            )
            monster3HpBar.setValue(
                Expr(battleState.monster3DisplayHP.ir),
                Expr(battleState.monster3TargetHP.ir),
            )

            // Tick the HP bars to update animation each frame
            monster1HpBar.tick()
            monster2HpBar.tick()
            monster3HpBar.tick()

            // =========================================================
            // STATUS EFFECT ICON DISPLAY
            // =========================================================
            // Display up to 4 status effect icons per combatant based on
            // the status effect bitmask. Each bit represents one effect type.
            // Icons use tile indices 0x60-0x72 (TILE_STATUS_BASE + effect index).
            //
            // The bitmask is organized as:
            // Bit 0: Regen, Bit 1: Poison, Bit 2: Burn, etc.
            // We check each bit and show/hide the corresponding icon slot.

            // Player status icons (check bits 0-3 for first 4 effects)
            // Each bit represents: 0=Regen, 1=Poison, 2=Burn, 3=ATK Up
            whenever((battleState.playerStatusEffects and 0x01) isAbove 0) {
                statusIcons.playerIcon1.sprite?.tile(GameConfig.TILE_STATUS_BASE + 0)
                statusIcons.playerIcon1.show()
            }
            whenever((battleState.playerStatusEffects and 0x01) isEqualTo 0) {
                statusIcons.playerIcon1.hide()
            }
            whenever((battleState.playerStatusEffects and 0x02) isAbove 0) {
                statusIcons.playerIcon2.sprite?.tile(GameConfig.TILE_STATUS_BASE + 1)
                statusIcons.playerIcon2.show()
            }
            whenever((battleState.playerStatusEffects and 0x02) isEqualTo 0) {
                statusIcons.playerIcon2.hide()
            }
            whenever((battleState.playerStatusEffects and 0x04) isAbove 0) {
                statusIcons.playerIcon3.sprite?.tile(GameConfig.TILE_STATUS_BASE + 2)
                statusIcons.playerIcon3.show()
            }
            whenever((battleState.playerStatusEffects and 0x04) isEqualTo 0) {
                statusIcons.playerIcon3.hide()
            }
            whenever((battleState.playerStatusEffects and 0x08) isAbove 0) {
                statusIcons.playerIcon4.sprite?.tile(GameConfig.TILE_STATUS_BASE + 3)
                statusIcons.playerIcon4.show()
            }
            whenever((battleState.playerStatusEffects and 0x08) isEqualTo 0) {
                statusIcons.playerIcon4.hide()
            }

            // Monster 1 status icons
            whenever((battleState.monster1StatusEffects and 0x01) isAbove 0) {
                statusIcons.monster1Icon1.sprite?.tile(GameConfig.TILE_STATUS_BASE + 0)
                statusIcons.monster1Icon1.show()
            }
            whenever((battleState.monster1StatusEffects and 0x01) isEqualTo 0) {
                statusIcons.monster1Icon1.hide()
            }
            whenever((battleState.monster1StatusEffects and 0x02) isAbove 0) {
                statusIcons.monster1Icon2.sprite?.tile(GameConfig.TILE_STATUS_BASE + 1)
                statusIcons.monster1Icon2.show()
            }
            whenever((battleState.monster1StatusEffects and 0x02) isEqualTo 0) {
                statusIcons.monster1Icon2.hide()
            }
            whenever((battleState.monster1StatusEffects and 0x04) isAbove 0) {
                statusIcons.monster1Icon3.sprite?.tile(GameConfig.TILE_STATUS_BASE + 2)
                statusIcons.monster1Icon3.show()
            }
            whenever((battleState.monster1StatusEffects and 0x04) isEqualTo 0) {
                statusIcons.monster1Icon3.hide()
            }
            whenever((battleState.monster1StatusEffects and 0x08) isAbove 0) {
                statusIcons.monster1Icon4.sprite?.tile(GameConfig.TILE_STATUS_BASE + 3)
                statusIcons.monster1Icon4.show()
            }
            whenever((battleState.monster1StatusEffects and 0x08) isEqualTo 0) {
                statusIcons.monster1Icon4.hide()
            }

            // Monster 2 status icons
            whenever((battleState.monster2StatusEffects and 0x01) isAbove 0) {
                statusIcons.monster2Icon1.sprite?.tile(GameConfig.TILE_STATUS_BASE + 0)
                statusIcons.monster2Icon1.show()
            }
            whenever((battleState.monster2StatusEffects and 0x01) isEqualTo 0) {
                statusIcons.monster2Icon1.hide()
            }
            whenever((battleState.monster2StatusEffects and 0x02) isAbove 0) {
                statusIcons.monster2Icon2.sprite?.tile(GameConfig.TILE_STATUS_BASE + 1)
                statusIcons.monster2Icon2.show()
            }
            whenever((battleState.monster2StatusEffects and 0x02) isEqualTo 0) {
                statusIcons.monster2Icon2.hide()
            }
            whenever((battleState.monster2StatusEffects and 0x04) isAbove 0) {
                statusIcons.monster2Icon3.sprite?.tile(GameConfig.TILE_STATUS_BASE + 2)
                statusIcons.monster2Icon3.show()
            }
            whenever((battleState.monster2StatusEffects and 0x04) isEqualTo 0) {
                statusIcons.monster2Icon3.hide()
            }
            whenever((battleState.monster2StatusEffects and 0x08) isAbove 0) {
                statusIcons.monster2Icon4.sprite?.tile(GameConfig.TILE_STATUS_BASE + 3)
                statusIcons.monster2Icon4.show()
            }
            whenever((battleState.monster2StatusEffects and 0x08) isEqualTo 0) {
                statusIcons.monster2Icon4.hide()
            }

            // Monster 3 status icons
            whenever((battleState.monster3StatusEffects and 0x01) isAbove 0) {
                statusIcons.monster3Icon1.sprite?.tile(GameConfig.TILE_STATUS_BASE + 0)
                statusIcons.monster3Icon1.show()
            }
            whenever((battleState.monster3StatusEffects and 0x01) isEqualTo 0) {
                statusIcons.monster3Icon1.hide()
            }
            whenever((battleState.monster3StatusEffects and 0x02) isAbove 0) {
                statusIcons.monster3Icon2.sprite?.tile(GameConfig.TILE_STATUS_BASE + 1)
                statusIcons.monster3Icon2.show()
            }
            whenever((battleState.monster3StatusEffects and 0x02) isEqualTo 0) {
                statusIcons.monster3Icon2.hide()
            }
            whenever((battleState.monster3StatusEffects and 0x04) isAbove 0) {
                statusIcons.monster3Icon3.sprite?.tile(GameConfig.TILE_STATUS_BASE + 2)
                statusIcons.monster3Icon3.show()
            }
            whenever((battleState.monster3StatusEffects and 0x04) isEqualTo 0) {
                statusIcons.monster3Icon3.hide()
            }
            whenever((battleState.monster3StatusEffects and 0x08) isAbove 0) {
                statusIcons.monster3Icon4.sprite?.tile(GameConfig.TILE_STATUS_BASE + 3)
                statusIcons.monster3Icon4.show()
            }
            whenever((battleState.monster3StatusEffects and 0x08) isEqualTo 0) {
                statusIcons.monster3Icon4.hide()
            }

            // =========================================================
            // DEATH ANIMATION (state 1=delay, 2=fading)
            // Original: 23-frame initial delay, then 6-step palette fade (5 frames each)
            // Uses palette fadeTo() to fade monster colors to white
            // =========================================================
            // State 1: Initial delay (23 frames)
            whenever(battleState.deathAnimState isEqualTo 1) {
                battleState.deathAnimTimer += 1
                whenever(battleState.deathAnimTimer isAtLeast 23) {
                    battleState.deathAnimState set 2 // Start fading
                    battleState.deathAnimTimer set 0
                    battleState.deathAnimStep set 0
                    sounds.defeat.play()
                }
            }
            // State 2: Palette fade (6 steps, 5 frames each → 30 frames total)
            // Uses native gbkt palette fade toward white
            // Progress: step * 51 gives 0, 51, 102, 153, 204, 255
            whenever(battleState.deathAnimState isEqualTo 2) {
                battleState.deathAnimTimer += 1
                whenever(battleState.deathAnimTimer isAtLeast 5) {
                    battleState.deathAnimStep += 1
                    battleState.deathAnimTimer set 0

                    // Apply death fade using palette.fadeTo() with step-based progress
                    // Progress calculation: step * 51 (0→255 over 5 steps)
                    // Target: all white (0xFFFFFF for each color)
                    monsterPalette.fadeTo(
                        listOf(0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF),
                        Expr(battleState.deathAnimStep.ir) * 51,
                    )

                    // After 6 steps, animation complete
                    whenever(battleState.deathAnimStep isAtLeast 6) {
                        battleState.deathAnimState set 0 // Done
                        // Restore original palette
                        monsterPalette.apply()
                    }
                }
            }

            // =========================================================
            // SCREEN SHAKE
            // Uses native Camera.shake() for impact effect
            // Original: 5 steps × 3 frames = 15 frames total, intensity 6→0
            // =========================================================
            // Camera shake is now triggered via camera.shake() when action executes
            // The camera system handles the shake internally, so no manual
            // SCX_REG manipulation needed. We just need to call camera.update()
            // to process any active shake.
            camera.update()

            // =========================================================
            // ACTION RESULT MESSAGE DISPLAY (with damage numbers)
            // =========================================================
            // Messages now include actual damage values using lastDamage variable.
            // Format matches original: "Hit for X!", "CRITICAL! X!", "Healed X HP!"
            whenever(battleState.messageTimer isAbove 0) {
                battleState.messageTimer -= 1
                // Display message based on last action result with damage number
                whenever(battleState.lastActionResult isEqualTo 1) {
                    // Hit message with damage value
                    print("Hit for ", Expr(battleState.lastDamage.ir), "!") at (3 to 10)
                }
                whenever(battleState.lastActionResult isEqualTo 2) {
                    // Critical hit message with damage value
                    print("CRIT! ", Expr(battleState.lastDamage.ir), " dmg!") at (3 to 10)
                }
                whenever(battleState.lastActionResult isEqualTo 3) {
                    // Miss message (no damage to show)
                    print("Miss!") at (7 to 10)
                }
                whenever(battleState.lastActionResult isEqualTo 4) {
                    // Heal message with heal amount
                    print("Healed ", Expr(battleState.lastDamage.ir), " HP!") at (3 to 10)
                }
                // Clear message when timer expires
                whenever(battleState.messageTimer isEqualTo 0) {
                    battleState.lastActionResult set 0
                    print("                ") at (2 to 10) // Clear message area (wider now)
                }
            }

            // =========================================================
            // MAIN MENU STATE (menuState == 0)
            // =========================================================
            // Menu layout (2x2 grid):
            //   0=ATTACK  2=ITEM
            //   1=ABILITY 3=FLEE
            whenever(battleState.menuState isEqualTo 0) {
                // D-pad navigation in main menu with cursor display update
                whenever(dpad.up.pressed) {
                    whenever(battleState.menuCursor isAbove 0) {
                        battleState.menuCursor -= 1
                        sounds.menuMove.play()
                        // Redraw menu with updated cursor
                        whenever(battleState.menuCursor isEqualTo 0) {
                            print(">ATTACK") at (1 to 13)
                            print(" ABILITY") at (1 to 14)
                        }
                        whenever(battleState.menuCursor isEqualTo 2) {
                            print(">ITEM") at (11 to 13)
                            print(" FLEE") at (11 to 14)
                        }
                    }
                }
                whenever(dpad.down.pressed) {
                    whenever(battleState.menuCursor isBelow 1) {
                        battleState.menuCursor += 1
                        sounds.menuMove.play()
                        // Redraw menu with updated cursor
                        whenever(battleState.menuCursor isEqualTo 1) {
                            print(" ATTACK") at (1 to 13)
                            print(">ABILITY") at (1 to 14)
                        }
                        whenever(battleState.menuCursor isEqualTo 3) {
                            print(" ITEM") at (11 to 13)
                            print(">FLEE") at (11 to 14)
                        }
                    }
                }
                whenever(dpad.left.pressed) {
                    whenever(battleState.menuCursor isAbove 1) {
                        battleState.menuCursor -= 2
                        sounds.menuMove.play()
                        // Moving from right column to left column
                        whenever(battleState.menuCursor isEqualTo 0) {
                            print(">ATTACK") at (1 to 13)
                            print(" ITEM") at (11 to 13)
                        }
                        whenever(battleState.menuCursor isEqualTo 1) {
                            print(">ABILITY") at (1 to 14)
                            print(" FLEE") at (11 to 14)
                        }
                    }
                }
                whenever(dpad.right.pressed) {
                    whenever(battleState.menuCursor isBelow 2) {
                        battleState.menuCursor += 2
                        sounds.menuMove.play()
                        // Moving from left column to right column
                        whenever(battleState.menuCursor isEqualTo 2) {
                            print(" ATTACK") at (1 to 13)
                            print(">ITEM") at (11 to 13)
                        }
                        whenever(battleState.menuCursor isEqualTo 3) {
                            print(" ABILITY") at (1 to 14)
                            print(">FLEE") at (11 to 14)
                        }
                    }
                }

                // A button selects current option
                whenever(buttons.a.pressed) {
                    sounds.menuSelect.play()
                    // Option 0: Attack -> go to target select
                    whenever(battleState.menuCursor isEqualTo 0) {
                        battleState.menuState set 1 // Target select
                        battleState.targetCursor set 0
                        // Draw target selection prompt
                        print("SELECT TARGET") at (3 to 11)
                    }
                    // Option 1: Ability -> go to ability menu
                    whenever(battleState.menuCursor isEqualTo 1) {
                        battleState.menuState set 2 // Ability select
                        battleState.abilityCursor set 0
                        // Draw ability menu header
                        print("ABILITIES") at (5 to 1)
                    }
                    // Option 2: Item -> go to item menu
                    whenever(battleState.menuCursor isEqualTo 2) {
                        battleState.menuState set 3 // Item select
                        battleState.itemCursor set 0
                        // Draw item menu header
                        print("ITEMS") at (7 to 1)
                    }
                    // Option 3: Flee -> attempt to flee
                    whenever(battleState.menuCursor isEqualTo 3) {
                        sounds.flee.play()
                        scene(gameplay)
                    }
                }
            }

            // =========================================================
            // TARGET SELECT STATE (menuState == 1)
            // =========================================================
            whenever(battleState.menuState isEqualTo 1) {
                // Navigate between targets
                whenever(dpad.left.pressed) {
                    whenever(battleState.targetCursor isAbove 0) {
                        battleState.targetCursor -= 1
                        sounds.menuMove.play()
                    }
                }
                whenever(dpad.right.pressed) {
                    whenever(battleState.targetCursor isBelow 2) {
                        battleState.targetCursor += 1
                        sounds.menuMove.play()
                    }
                }

                // A to confirm attack
                whenever(buttons.a.pressed) {
                    sounds.menuSelect.play()
                    battleState.menuState set 4 // Execute action
                    // Confirm target selection - framework handles damage calculation
                    // Target index is enemy position (offset by party count in combatant array)
                    confirmCombatTarget(combatPartyCount + battleState.targetCursor)
                }

                // B to go back to main menu
                whenever(buttons.b.pressed) {
                    sounds.menuCancel.play()
                    battleState.menuState set 0
                    // Redraw main menu with cursor on Attack
                    print("MONSTER ATTACK!") at (2 to 1)
                    print(">ATTACK") at (1 to 13)
                    print(" ABILITY") at (1 to 14)
                    print(" ITEM") at (11 to 13)
                    print(" FLEE") at (11 to 14)
                    battleState.menuCursor set 0
                }
            }

            // =========================================================
            // ABILITY SELECT STATE (menuState == 2)
            // =========================================================
            whenever(battleState.menuState isEqualTo 2) {
                // Navigate abilities
                whenever(dpad.up.pressed) {
                    whenever(battleState.abilityCursor isAbove 0) {
                        battleState.abilityCursor -= 1
                        sounds.menuMove.play()
                    }
                }
                whenever(dpad.down.pressed) {
                    whenever(battleState.abilityCursor isBelow 5) {
                        battleState.abilityCursor += 1
                        sounds.menuMove.play()
                    }
                }

                // A to select ability
                whenever(buttons.a.pressed) {
                    sounds.menuSelect.play()
                    // Go to target select for ability
                    battleState.menuState set 1
                    battleState.targetCursor set 0
                    print("SELECT TARGET") at (3 to 11)
                }

                // B to go back
                whenever(buttons.b.pressed) {
                    sounds.menuCancel.play()
                    battleState.menuState set 0
                    // Redraw main menu
                    screen.clear()
                    print("MONSTER ATTACK!") at (2 to 1)
                    print(">ATTACK") at (1 to 13)
                    print(" ABILITY") at (1 to 14)
                    print(" ITEM") at (11 to 13)
                    print(" FLEE") at (11 to 14)
                    battleState.menuCursor set 0
                }
            }

            // =========================================================
            // ITEM SELECT STATE (menuState == 3)
            // =========================================================
            whenever(battleState.menuState isEqualTo 3) {
                // Navigate items
                whenever(dpad.up.pressed) {
                    whenever(battleState.itemCursor isAbove 0) {
                        battleState.itemCursor -= 1
                        sounds.menuMove.play()
                    }
                }
                whenever(dpad.down.pressed) {
                    whenever(battleState.itemCursor isBelow 7) {
                        battleState.itemCursor += 1
                        sounds.menuMove.play()
                    }
                }

                // A to use item
                whenever(buttons.a.pressed) {
                    sounds.menuSelect.play()
                    battleState.menuState set 4 // Execute
                    // Select item and let framework handle usage
                    selectCombatItem(battleState.itemCursor)
                }

                // B to go back
                whenever(buttons.b.pressed) {
                    sounds.menuCancel.play()
                    battleState.menuState set 0
                    // Redraw main menu
                    screen.clear()
                    print("MONSTER ATTACK!") at (2 to 1)
                    print(">ATTACK") at (1 to 13)
                    print(" ABILITY") at (1 to 14)
                    print(" ITEM") at (11 to 13)
                    print(" FLEE") at (11 to 14)
                    battleState.menuCursor set 0
                }
            }

            // =========================================================
            // ACTION EXECUTE STATE (menuState == 4)
            // =========================================================
            whenever(battleState.menuState isEqualTo 4) {
                // Action is executing - show result message and wait for animation
                // Set action result message (1=hit by default)
                battleState.lastActionResult set 1
                battleState.messageTimer set 60 // Show for ~1 second

                // Play attack sound
                sounds.attack.play()

                // Trigger screen shake when player attacks (15 frames, intensity 6, linear decay)
                // Original pattern: +6, -6, +4, -4, 0 over 15 frames
                camera.shake {
                    intensity = 6
                    duration = 15 // 15 frames total
                    decay = ShakeDecay.LINEAR
                }

                // Wait for message to display, then go to enemy turn
                // For now, immediately transition
                battleState.menuState set 5
            }

            // =========================================================
            // ENEMY TURN STATE (menuState == 5)
            // =========================================================
            whenever(battleState.menuState isEqualTo 5) {
                // Enemy AI acts - framework's state machine handles all enemy turns
                // Transition to ENEMY_THINK state, framework handles AI decision and execution
                transitionToCombatState("COMBAT_STATE_ENEMY_THINK")
                // The framework state machine will:
                // 1. ENEMY_THINK: Call _call_monster_ai() for current enemy
                // 2. ENEMY_DECIDE: Queue the enemy's action
                // 3. ACTION_EXECUTE: Execute the action
                // 4. NEXT_TURN: Advance to next combatant

                // After framework processes, check battle outcome and return to player menu
                whenever(combatIsInState("COMBAT_STATE_VICTORY")) {
                    battleState.menuState set 6 // Result display
                }
                whenever(combatIsInState("COMBAT_STATE_DEFEAT")) {
                    battleState.menuState set 6 // Result display
                }
                whenever(combatIsInState("COMBAT_STATE_PLAYER_MENU")) {
                    battleState.menuState set 0 // Back to player menu
                }
            }

            // =========================================================
            // DEBUG: SELECT to return to gameplay (dev mode)
            // =========================================================
            whenever(buttons.select.pressed) { scene(gameplay) }
        }

        exit {
            // Clean up battle state
        }
    }
