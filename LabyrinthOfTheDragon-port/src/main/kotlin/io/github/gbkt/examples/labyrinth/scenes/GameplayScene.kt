/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.exploration.tryInteractWithObject
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.rpg.initBattleWithMonsters
import io.github.gbkt.core.screen
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.core.world.checkEncounter
import io.github.gbkt.core.world.clear
import io.github.gbkt.core.world.isSet
import io.github.gbkt.core.world.setEncounterTable
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.rpg.Monsters

/**
 * Gameplay Scene
 *
 * Main dungeon exploration mode. Player moves through the labyrinth, encounters monsters, finds
 * treasures, and progresses through floors.
 *
 * Boss encounters are handled via flags set by map object interactions:
 * - When player interacts with dragon throne on Floor 8, "dragonBattleTriggered" flag is set
 * - This scene checks the flag and initiates the boss battle using [initBattleWithMonsters]
 */
fun GameBuilder.initGameplayScene(
    state: GameState,
    pause: SceneRef,
    battle: SceneRef,
    monsters: Monsters,
    gameFlags: GlobalFlags,
): SceneRef =
    scene("gameplay") {
        enter {
            screen.showSprites()
            // Reset player position to floor default
            state.playerX set 5
            state.playerY set 5
            state.moveCooldown set 0
            state.stepCount set 0
            // Initialize encounter table based on current floor
            setEncounterTable(state.currentFloor)
        }

        every.frame {
            // Decrement move cooldown if active
            whenever(state.moveCooldown isAbove 0) { state.moveCooldown -= 1 }

            // D-pad movement (only when cooldown is 0)
            whenever(state.moveCooldown isEqualTo 0) {
                whenever(dpad.up.held) {
                    whenever(state.playerY isAbove 0) {
                        state.playerY -= 1
                        state.stepCount += 1
                        state.moveCooldown set 8
                        // Consume torch fuel on movement
                        whenever(state.torchFuel isAbove 0) { state.torchFuel -= 1 }
                        // Check for random encounter after step
                        checkEncounter("battle")
                    }
                }
                whenever(dpad.down.held) {
                    whenever(state.playerY isBelow 31) {
                        state.playerY += 1
                        state.stepCount += 1
                        state.moveCooldown set 8
                        // Consume torch fuel on movement
                        whenever(state.torchFuel isAbove 0) { state.torchFuel -= 1 }
                        // Check for random encounter after step
                        checkEncounter("battle")
                    }
                }
                whenever(dpad.left.held) {
                    whenever(state.playerX isAbove 0) {
                        state.playerX -= 1
                        state.stepCount += 1
                        state.moveCooldown set 8
                        // Consume torch fuel on movement
                        whenever(state.torchFuel isAbove 0) { state.torchFuel -= 1 }
                        // Check for random encounter after step
                        checkEncounter("battle")
                    }
                }
                whenever(dpad.right.held) {
                    whenever(state.playerX isBelow 31) {
                        state.playerX += 1
                        state.stepCount += 1
                        state.moveCooldown set 8
                        // Consume torch fuel on movement
                        whenever(state.torchFuel isAbove 0) { state.torchFuel -= 1 }
                        // Check for random encounter after step
                        checkEncounter("battle")
                    }
                }
            }

            // Open pause/menu
            whenever(buttons.start.pressed) { scene(pause) }

            // Action button - interact with objects
            whenever(buttons.a.pressed) {
                // Check for interactable objects at player position
                tryInteractWithObject(state.currentFloor, state.playerX, state.playerY)
            }

            // =========================================================
            // BOSS ENCOUNTER CHECKS
            // =========================================================
            // Check if boss encounter was triggered by map object interaction
            val dragonFlag = gameFlags.getFlag("dragonBattleTriggered")
            if (dragonFlag != null) {
                whenever(dragonFlag.isSet()) {
                    // Clear the flag so battle doesn't loop
                    dragonFlag.clear()
                    // Initialize battle with the dragon as enemy
                    initBattleWithMonsters(monsters.dragon)
                    // Transition to battle scene
                    scene(battle)
                }
            }
        }

        exit { screen.hideSprites() }
    }
