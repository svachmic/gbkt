/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.dungeon

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.rpg.dsl.battleUpdate
import io.github.gbkt.rpg.dsl.character
import io.github.gbkt.rpg.dsl.monster
import io.github.gbkt.rpg.dsl.simpleBattle

/**
 * Dungeon crawler game defined using the v2 DSL.
 *
 * Demonstrates:
 * - Grid-based dungeon exploration via exploration() with preset(DUNGEON_CRAWLER)
 * - Torch gauge that depletes every step — game over when it reaches 0
 * - Key counter for locked doors (room_key, max 9)
 * - Random encounters via zone encounter table
 * - RPG combat: adventurer vs bat/skeleton using simpleBattle
 * - 4 scenes: title, gameplay, battle, gameover
 * - Variables: torchLevel, keys, steps
 * - Sound effects: bump, step, key, hit
 * - World DSL: zone(), flags(), exploration() (Phase 06.3)
 *
 * Navigation cycle: title → gameplay ↔ battle → gameover → title. Forward-declared
 * titleRef/gameoverRef break circular navigation cycles. All navigate() calls use typed SceneRef
 * (zero string-navigate calls).
 */
@Suppress("LongMethod")
val dungeon =
    game("Dungeon") {
        config {
            cartridge = "MBC5_RAM_BATTERY"
            romBanks = 4
            ramBanks = 1
        }

        // Forward-declare scene refs for circular navigation (scenes defined below)
        val titleRef = sceneRef("title")
        val gameoverRef = sceneRef("gameover")
        val gameplayRef = sceneRef("gameplay")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var torchLevel by u8Var(255)
        var keys by u8Var(0)
        var steps by u8Var(0)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val bumpSfx by soundEffect { preset(SoundPreset.BUMP) }
        val stepSfx by soundEffect { preset(SoundPreset.BEEP) }
        val keySfx by soundEffect { preset(SoundPreset.COIN) }
        val hitSfx by soundEffect { preset(SoundPreset.HIT) }

        // -------------------------------------------------------------------------
        // Actors — name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val player by actor {
            position(64, 64)
            sprite(asset("sprites/player.png")) {
                size(8, 16)
                hitbox(0, 8, 8, 8)
            }
        }

        // -------------------------------------------------------------------------
        // World: zones, global flags (Phase 06.3)
        // -------------------------------------------------------------------------

        // Dungeon floor 1 — random encounters with bats and skeletons
        val floor1 =
            zone("floor1") {
                name("Dungeon Level 1")
                size(16, 16)
                encounters {
                    safeSteps(10)
                    entry("combat", weight = 30)
                }
            }

        // Global dungeon flags for tracking story progression
        flags("dungeon_flags") {
            page("dungeon") {
                flag("bossDefeated")
                flag("gotTreasure")
                flag("foundKey")
            }
        }

        // -------------------------------------------------------------------------
        // Systems
        // -------------------------------------------------------------------------

        camera { smoothing = 0.2f }

        saveData("dungeon_save") { slots(1) }

        // Exploration system: DUNGEON_CRAWLER preset (tileSize=8, GRID, movementSpeed=8, torch
        // gauge). Customize torch with onDepleted callback + key counter for locked doors.
        exploration {
            preset(ExplorationPreset.DUNGEON_CRAWLER)
            startZone(floor1)
            gauge("torch") {
                max(255)
                initial(255)
                decrementPerStep(1)
                onLow(50) { navigate(gameoverRef) }
                onDepleted { navigate(gameoverRef) }
            }
            keys("room_key") {
                max(9)
                initial(0)
            }
            onStep { playSound(stepSfx) }
            onBlocked { playSound(bumpSfx) }
        }

        // -------------------------------------------------------------------------
        // UI definitions — dialog and HUD
        // -------------------------------------------------------------------------

        // Torch-out warning dialog
        val torchWarning =
            dialog("torch_warning") {
                border(BorderStyle.SINGLE)
                textSpeed(2)
                box(x = 0, y = 12, width = 20, height = 4)
            }

        // Gameplay HUD — shows torch level, key count
        val gameHud =
            hud("game_hud") {
                anchor(Anchor.TOP_LEFT)
                number("torch") {
                    variable(torchLevel)
                    label("T:")
                    format("%d")
                }
                icons("keys") {
                    variable(keys)
                    max(9)
                    fullTile(0x10)
                    emptyTile(0x11)
                    displayMode(IconDisplayMode.FULL_AND_EMPTY)
                }
            }

        // -------------------------------------------------------------------------
        // RPG definitions — using gbkt-genre-rpg DSL builders
        // -------------------------------------------------------------------------

        val adventurer =
            character("adventurer") {
                name("Adventurer")
                stats {
                    hp(25)
                    atk(7)
                    def(4)
                    agl(8)
                }
            }

        val bat =
            monster("bat") {
                name("Bat")
                stats {
                    hp(8)
                    atk(5)
                    def(1)
                    agl(15)
                }
                exp(3)
            }

        val skeleton =
            monster("skeleton") {
                name("Skeleton")
                stats {
                    hp(15)
                    atk(6)
                    def(3)
                    agl(5)
                }
                exp(8)
            }

        // simpleBattle — defined before scenes so BattleRef `combat` is in scope
        val combat =
            simpleBattle("combat") {
                party(adventurer)
                encounter { +bat }
                encounter {
                    +bat
                    +skeleton
                }
                onVictory { navigate(gameplayRef) }
                onDefeat { navigate(gameoverRef) }
            }

        // -------------------------------------------------------------------------
        // Scene ordering: gameover → battle → gameplay → title
        // Each scene navigates only to earlier-defined scenes (except cycle-breakers)
        // -------------------------------------------------------------------------

        // Game-over scene — defined first (navigates back to title via forward-declared titleRef)
        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    // "GAME OVER" = 9 chars → col (20-9)/2 = 5
                    print("GAME OVER", position = PositionDef(5, 6))
                    // "TORCH EXPIRED" = 13 chars → col (20-13)/2 = 3
                    print("TORCH EXPIRED", position = PositionDef(3, 9))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        // Reset state for new game
                        torchLevel set 255
                        keys set 0
                        steps set 0
                        navigate(titleRef)
                    }
                }
            }

        // Battle scene — drives simpleBattle state machine each frame
        val battleScene =
            scene("battle") {
                enter {
                    hideSprites()
                    clear()
                    // "ENCOUNTER!" = 10 chars → col (20-10)/2 = 5
                    print("ENCOUNTER!", position = PositionDef(5, 4))
                    playSound(hitSfx)
                    delay(30)
                }
                frame {
                    // Drive the combat state machine (simpleBattle system)
                    battleUpdate(combat)
                }
            }

        // Gameplay scene — grid movement, torch depletion, encounter checks
        val gameplayScene =
            scene("gameplay") {
                enter {
                    showSprites()
                    clear()
                    setPosition(player.id, 64, 64)
                    gameHud.show()
                }
                frame {
                    // Grid-based movement using type-safe d-pad API (8px per step)
                    whenever(dpad.up.held) {
                        whenever(player.y isAbove 16) { moveBy(player, 0, -8) }
                    }
                    whenever(dpad.down.held) {
                        whenever(player.y isBelow 128) { moveBy(player, 0, 8) }
                    }
                    whenever(dpad.left.held) {
                        whenever(player.x isAbove 8) { moveBy(player, -8, 0) }
                    }
                    whenever(dpad.right.held) {
                        whenever(player.x isBelow 152) { moveBy(player, 8, 0) }
                    }

                    // Step counter — increment when any d-pad direction pressed
                    whenever(dpad.any) { steps += 1 }

                    // Torch depletion — every 4 steps decrement torchLevel
                    whenever(
                        (steps isAbove 0) logicalAnd
                            ((steps and 3) isEqualTo 0) logicalAnd
                            (torchLevel isAbove 0)
                    ) {
                        torchLevel -= 1
                    }

                    // Torch low warning at 50 — show dialog then continue
                    whenever(torchLevel isEqualTo 50) { torchWarning.say("Torch dimming...") }

                    // Torch out — navigate to gameover via SceneRef
                    whenever(torchLevel isEqualTo 0) {
                        hideSprites()
                        torchWarning.say("Your torch burns out!")
                        delay(60)
                        navigate(gameoverScene)
                    }

                    // Random encounter every 120 steps — navigate to battle via SceneRef
                    whenever(steps isAtLeast 120) {
                        steps set 0
                        navigate(battleScene)
                    }
                }
                exit { gameHud.hide() }
            }

        // Title scene — uses gameplayScene ref (defined above)
        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    // "DUNGEON" = 7 chars → col (20-7)/2 = 6
                    print("DUNGEON", position = PositionDef(6, 4))
                    print("A TORCH CRAWLER", position = PositionDef(2, 7))
                    print("PRESS START", position = PositionDef(5, 12))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        // Reset all state for a new run
                        torchLevel set 255
                        keys set 0
                        steps set 0
                        navigate(gameplayScene)
                    }
                }
            }

        start = titleScene.id
    }
