/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.rpglite

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.rpg.domain.Aspect
import io.github.gbkt.rpg.domain.TargetingMode
import io.github.gbkt.rpg.dsl.ability
import io.github.gbkt.rpg.dsl.battleUpdate
import io.github.gbkt.rpg.dsl.character
import io.github.gbkt.rpg.dsl.monster
import io.github.gbkt.rpg.dsl.simpleBattle

/**
 * RPG Lite — mini-RPG example defined using the v2 DSL.
 *
 * Demonstrates:
 * - RPG genre package: character(), monster(), simpleBattle() from gbkt-genre-rpg
 * - 3 variables: hp, gold, dungeonLevel
 * - 4 sound effects: hitSfx, coinSfx, winSfx, loseSfx
 * - 1 actor: heroActor (8x16 sprite)
 * - 4 scenes: title, town, dungeon, gameover
 * - Forward-declared SceneRefs (titleRef, gameoverRef) for circular navigation
 * - battleUpdate() every frame in dungeon scene to drive combat state machine
 * - Step-based random encounters in the dungeon
 *
 * Scene ordering: gameover → dungeon → town → title Navigation cycle: title -> town -> dungeon ->
 * (victory: dungeon, defeat: gameover) -> title
 */
@Suppress("LongMethod")
val rpgLite =
    game("RPG Lite") {
        config {
            cartridge = "MBC5_RAM_BATTERY"
            romBanks = 4
            ramBanks = 1
        }

        // Forward-declare refs for circular navigation (title and gameover defined last)
        val titleRef = sceneRef("title")
        val gameoverRef = sceneRef("gameover")
        val dungeonRef = sceneRef("dungeon")
        val townRef = sceneRef("town")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var hp by u8Var(30)
        var gold by u8Var(0)
        var dungeonLevel by u8Var(1)
        var stepCount by u8Var(0)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val hitSfx by soundEffect { preset(SoundPreset.HIT) }
        val coinSfx by soundEffect { preset(SoundPreset.COIN) }
        val winSfx by soundEffect { preset(SoundPreset.WIN) }
        val loseSfx by soundEffect { preset(SoundPreset.EXPLODE) }

        // -------------------------------------------------------------------------
        // Actors — name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val heroActor by actor {
            position(80, 72)
            sprite(asset("sprites/hero.png")) {
                size(8, 16)
                hitbox(0, 8, 8, 8)
            }
        }

        // -------------------------------------------------------------------------
        // RPG definitions — characters, monsters, and battle system
        // -------------------------------------------------------------------------

        val hero =
            character("hero") {
                name("Hero")
                stats {
                    hp(30)
                    atk(8)
                    def(5)
                    agl(10)
                }
            }

        val slime =
            monster("slime") {
                name("Slime")
                stats {
                    hp(12)
                    atk(4)
                    def(2)
                    agl(8)
                }
                exp(5)
            }

        val bat =
            monster("bat") {
                name("Bat")
                stats {
                    hp(8)
                    atk(6)
                    def(1)
                    agl(15)
                }
                exp(3)
            }

        // Ability definition — demonstrates ability() DSL from gbkt-genre-rpg
        val fireball by ability {
            name("Fireball")
            cost(sp = 8)
            targeting(TargetingMode.SINGLE_ENEMY)
            aspect(Aspect.FIRE)
            power(15)
        }

        val combat =
            simpleBattle("combat") {
                party(hero)
                encounter { +slime }
                encounter { +bat }
                onVictory {
                    playSound(winSfx)
                    gold += 5
                    navigate(dungeonRef)
                }
                onDefeat {
                    playSound(loseSfx)
                    navigate(gameoverRef)
                }
            }

        // -------------------------------------------------------------------------
        // Scene ordering: gameover → dungeon → town → title
        // Each scene navigates only to previously defined scenes (except cycle-break refs)
        // -------------------------------------------------------------------------

        // Game-over scene — defined first, navigates back to title via forward-declared ref
        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    // "GAME OVER" = 9 chars → col (20-9)/2 = 5
                    print("GAME OVER", position = PositionDef(5, 6))
                    print("HP: 0  GOLD: %d", gold.toExpr(), position = PositionDef(3, 9))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
            }

        // Dungeon scene — random encounters; battleUpdate() drives combat each frame
        val dungeonScene =
            scene("dungeon") {
                enter {
                    showSprites()
                    clear()
                    // "DUNGEON LV:%d" up to 13 chars → col 3
                    print("DUNGEON LV:%d", dungeonLevel.toExpr(), position = PositionDef(3, 1))
                    print(
                        "HP:%d  GOLD:%d",
                        hp.toExpr(),
                        gold.toExpr(),
                        position = PositionDef(2, 3),
                    )
                    stepCount set 0
                }
                frame {
                    // 4-directional movement — type-safe d-pad API
                    whenever(dpad.up.held) {
                        whenever(heroActor.y isAbove 16) { moveBy(heroActor, 0, -2) }
                    }
                    whenever(dpad.down.held) {
                        whenever(heroActor.y isBelow 128) { moveBy(heroActor, 0, 2) }
                    }
                    whenever(dpad.left.held) {
                        whenever(heroActor.x isAbove 8) { moveBy(heroActor, -2, 0) }
                    }
                    whenever(dpad.right.held) {
                        whenever(heroActor.x isBelow 152) { moveBy(heroActor, 2, 0) }
                    }

                    // Increment step counter on any movement
                    whenever(dpad.any) { stepCount += 1 }

                    // Random encounter every 60 steps (~1s of walking)
                    whenever(stepCount isAtLeast 60) {
                        stepCount set 0
                        playSound(hitSfx)
                        battleUpdate(combat)
                    }

                    // Reach dungeon exit: go deeper (level up dungeon) — right edge
                    whenever(heroActor.x isAtLeast 152) {
                        dungeonLevel += 1
                        gold += 3
                        playSound(coinSfx)
                        heroActor.moveTo(8, 72)
                    }

                    // Return to town — press START
                    whenever(buttons.start.pressed) { navigate(townRef) }

                    // Game over if HP depleted
                    whenever(hp isEqualTo 0) {
                        playSound(loseSfx)
                        navigate(gameoverScene)
                    }
                }
            }

        // Town scene — safe zone, prepare for dungeon
        val townScene =
            scene("town") {
                enter {
                    showSprites()
                    clear()
                    // "TOWN" = 4 chars → col (20-4)/2 = 8
                    print("TOWN", position = PositionDef(8, 2))
                    print(
                        "HP:%d  GOLD:%d",
                        hp.toExpr(),
                        gold.toExpr(),
                        position = PositionDef(2, 4),
                    )
                    print("A: ENTER DUNGEON", position = PositionDef(2, 8))
                    print("START: HEAL (5G)", position = PositionDef(2, 10))
                    heroActor.moveTo(80, 72)
                }
                frame {
                    // Heal using gold (costs 5 gold, restores 10 HP)
                    whenever(buttons.start.pressed) {
                        whenever(gold isAtLeast 5) {
                            whenever(hp isBelow 30) {
                                gold -= 5
                                hp += 10
                                playSound(coinSfx)
                            }
                        }
                    }

                    // Enter dungeon with A button
                    whenever(buttons.a.pressed) { navigate(dungeonScene) }
                }
            }

        // Title scene — entry point, navigate to town on START
        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    // "RPG LITE" = 8 chars → col (20-8)/2 = 6
                    print("RPG LITE", position = PositionDef(6, 5))
                    print("A MINI ADVENTURE", position = PositionDef(2, 8))
                    print("PRESS START", position = PositionDef(5, 12))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        // Reset state for new game
                        hp set 30
                        gold set 0
                        dungeonLevel set 1
                        stepCount set 0
                        navigate(townScene)
                    }
                }
            }

        start = titleScene.id
    }
