/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.explorer

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.MenuLayout
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.rpg.dsl.battleUpdate
import io.github.gbkt.rpg.dsl.character
import io.github.gbkt.rpg.dsl.monster
import io.github.gbkt.rpg.dsl.simpleBattle

/**
 * Explorer game defined using the new v2 DSL.
 *
 * Demonstrates:
 * - 5 scenes: title, gameplay, pause, combat_scene, gameover
 * - 1 actor: player (8x16 sprite) — using `val x by actor { }` name inference with entityCollision
 * - Type-safe input: `dpad.up.held`, `buttons.start.pressed`, `buttons.b.pressed`, `dpad.any`
 * - Scene references for navigation where cycle permits
 * - Variables: hp, stepCount, keys, torchLevel
 * - Sound effects on step, hit, save
 * - RPG combat system (text-based auto-battle via DSL ops)
 * - Pause menu using new `menu()` DSL builder — demonstrates MenuHandle.show()
 * - Gameplay HUD using new `hud()` DSL builder — bar/number/icons elements
 * - Dialog via new `dialog()` DSL builder — torch-out warning message
 * - World DSL features: zone(), flags(), exploration() with gauge/keys/callbacks (Phase 06.3)
 *
 * This proves the BOM genre package pattern:
 * - Explorer depends on both gbkt-core AND gbkt-rpg
 * - RPG builders produce GenericSystem (core IR) — no new sealed subtypes
 *
 * Scene ordering: gameover → combat_scene → pause → gameplay → title (each scene references only
 * earlier-defined scenes in navigate calls). Forward-declared SceneRefs (titleRef, gameplayRef,
 * gameoverRef) break circular navigation cycles — all navigate() calls use typed SceneRef (zero
 * string-navigate calls).
 */
@Suppress("LongMethod")
val explorerV2 =
    game("Explorer") {
        config {
            cartridge = "MBC5_RAM_BATTERY"
            romBanks = 4
            ramBanks = 1
        }

        // Forward-declare scene refs for circular navigation (scenes defined below)
        val titleRef = sceneRef("title")
        val gameplayRef = sceneRef("gameplay")
        val gameoverRef = sceneRef("gameover")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var hp by u8Var(20)
        var stepCount by u8Var(0)
        var keys by u8Var(0)
        var torchLevel by u8Var(100)
        var level by u8Var(1)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val step by soundEffect { preset(SoundPreset.BEEP) }
        val door by soundEffect { preset(SoundPreset.COIN) }
        val save by soundEffect { preset(SoundPreset.POWERUP) }
        val hit by soundEffect { preset(SoundPreset.HIT) }

        // -------------------------------------------------------------------------
        // Actors — name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val player by actor {
            position(80, 72)
            sprite(asset("sprites/player.png")) {
                size(8, 16)
                hitbox(0, 8, 8, 8)
            }
            // Entity collision: block-and-trigger for NPC/door interaction during exploration
            entityCollision {
                mode(EntityCollisionMode.BLOCK_AND_TRIGGER)
                onBlocked { navigate(gameplayRef) }
            }
        }

        // -------------------------------------------------------------------------
        // World: zones, global flags (Phase 06.3)
        // -------------------------------------------------------------------------

        // Dungeon floor 1 — encounters with goblins, transition east to floor 2
        val floor1 =
            zone("floor1") {
                name("Dungeon Level 1")
                size(32, 32)
                encounters {
                    safeSteps(10)
                    entry("combat", weight = 30)
                }
                transition {
                    to("floor2")
                    edge(TransitionEdge.EAST)
                    entryX(0)
                    entryY(15)
                }
                onEnter { navigate(gameplayRef) }
            }

        // Dungeon floor 2 — safe zone (boss room)
        zone("floor2") {
            name("Boss Chamber")
            size(16, 16)
            safeZone()
            transition {
                to("floor1")
                edge(TransitionEdge.WEST)
                entryX(31)
                entryY(15)
            }
        }

        // Global story flags for tracking game progression
        flags {
            page("story") {
                flag("metElder")
                flag("hasKey")
                flag("defeatedBoss")
            }
            page("exploration") {
                flag("visitedFloor1")
                flag("visitedFloor2")
            }
        }

        // -------------------------------------------------------------------------
        // Systems
        // -------------------------------------------------------------------------

        camera { smoothing = 0.2f }

        saveData("explorer_save") { slots(1) }

        // Exploration system with torch gauge, key counter, and step/blocked callbacks
        exploration {
            preset(ExplorationPreset.DUNGEON_CRAWLER)
            startZone(floor1)
            gauge("torch") {
                max(255)
                initial(100)
                decrementPerStep(1)
                onLow(50) { navigate(gameplayRef) }
                onDepleted { navigate(gameoverRef) }
            }
            keys("magic_key") {
                max(99)
                initial(0)
            }
            onStep { navigate(gameplayRef) }
            onBlocked { navigate(gameplayRef) }
        }

        // -------------------------------------------------------------------------
        // UI definitions — dialog, menu, HUD (new UI DSL builders from Phase 06.2)
        // -------------------------------------------------------------------------

        // Dialog for torch-out warning — renders via window layer
        val torchWarning =
            dialog("torch_warning") {
                border(BorderStyle.SINGLE)
                textSpeed(2)
                box(x = 0, y = 12, width = 20, height = 4)
            }

        // Pause menu — vertical layout, two items, SFX hooks
        val pauseMenu =
            menu("pause") {
                layout(MenuLayout.VERTICAL)
                position(x = 4, y = 4, width = 12, height = 10)
                sfx(onMove = step, onSelect = door)
                item("Resume") { navigate(gameplayRef) }
                item("Quit to Title") { navigate(titleRef) }
            }

        // Gameplay HUD — shows HP bar, torch level number, keys icon counter
        val gameHud =
            hud("game_hud") {
                anchor(Anchor.TOP_LEFT)
                bar("hp") {
                    variable(hp)
                    max(20)
                    width(5)
                    fillTile(0x01)
                    emptyTile(0x00)
                }
                number("torch") {
                    variable(torchLevel)
                    label("T:")
                    format("%d")
                }
                icons("keys") {
                    variable(keys)
                    max(5)
                    fullTile(0x10)
                    emptyTile(0x11)
                    displayMode(IconDisplayMode.FULL_AND_EMPTY)
                }
            }

        // -------------------------------------------------------------------------
        // RPG definitions — using gbkt-rpg DSL builders
        // -------------------------------------------------------------------------

        val hero =
            character("hero") {
                name("Hero")
                stats {
                    hp(20)
                    atk(5)
                    def(3)
                }
            }

        val goblin =
            monster("goblin") {
                name("Goblin")
                stats {
                    hp(10)
                    atk(3)
                    def(1)
                }
                exp(5)
            }

        val rat =
            monster("rat") {
                name("Rat")
                stats {
                    hp(5)
                    atk(2)
                    def(0)
                }
                exp(2)
            }

        val combat =
            simpleBattle("combat") {
                party(hero)
                encounter { +goblin }
                encounter { +rat }
                onVictory { navigate(gameplayRef) }
                onDefeat { navigate(gameoverRef) }
            }

        // -------------------------------------------------------------------------
        // Scene ordering: gameover → combat_scene → pause → gameplay → title
        // Each scene navigates only to earlier-defined scenes (except cycle-breaker strings)
        // -------------------------------------------------------------------------

        // Game-over scene — defined first (navigates back to title: cycle-break via string)
        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    // "GAME OVER" = 9 → col (20-9)/2 = 5
                    print("GAME OVER", position = PositionDef(6, 6))
                    print("LEVEL: %d", level.toExpr(), position = PositionDef(5, 9))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                // Navigate back to title — uses forward-declared titleRef (SceneRef)
                frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
            }

        // Combat scene — uses gameoverScene ref
        val combatScene =
            scene("combat_scene") {
                enter {
                    hideSprites()
                    clear()
                    // Auto-battle: show encounter, resolve, return
                    // Combat text fits 20-col screen:
                    // "GOBLIN APPEARS!" = 15 @ col 3 → ends 18 ✓
                    // "You attack!  5dmg" = 17 @ col 2 → ends 19 ✓
                    // "Goblin hits! 3dmg" = 17 @ col 2 → ends 19 ✓
                    // "Victory! +5 exp" = 15 @ col 3 → ends 18 ✓
                    print("GOBLIN APPEARS!", position = PositionDef(3, 3))
                    delay(60)
                    print("You attack!  5dmg", position = PositionDef(2, 6))
                    delay(40)
                    print("Goblin hits! 3dmg", position = PositionDef(2, 8))
                    ifOp(hp isAbove 3) { hp -= 3 }
                    elseOp { hp set 0 }
                    delay(40)
                    print("Victory! +5 exp", position = PositionDef(3, 11))
                    delay(60)
                    // After the battle animation, check HP — navigate to gameover via SceneRef
                    whenever(hp isEqualTo 0) { navigate(gameoverScene) }
                }
                frame {
                    // Drive the combat system trigger (currently a stub)
                    battleUpdate(combat)
                }
            }

        // Pause scene — uses forward-declared gameplayRef and titleRef; shows pauseMenu via
        // MenuHandle.show() (demonstrates new menu DSL from Phase 06.2)
        val pauseScene =
            scene("pause") {
                enter {
                    hideSprites()
                    clear()
                    // Show the pause menu using the new MenuHandle API
                    pauseMenu.show()
                }
                frame {
                    whenever(buttons.start.pressed) { navigate(gameplayRef) }
                    whenever(buttons.b.pressed) { navigate(titleRef) }
                }
            }

        // Gameplay scene — uses combatScene ref (defined above)
        val gameplayScene =
            scene("gameplay") {
                enter {
                    showSprites()
                    clear()
                    setPosition(player.id, 80, 72)
                    // Show the HUD via HudPanel.show() (demonstrates new hud DSL from Phase 06.2)
                    gameHud.show()
                }
                frame {
                    // 4-directional movement (2px/frame for responsiveness) — type-safe d-pad API
                    whenever(dpad.up.held) {
                        whenever(player.y isAbove 16) { moveBy(player, 0, -2) }
                    }
                    whenever(dpad.down.held) {
                        whenever(player.y isBelow 136) { moveBy(player, 0, 2) }
                    }
                    whenever(dpad.left.held) {
                        whenever(player.x isAbove 8) { moveBy(player, -2, 0) }
                    }
                    whenever(dpad.right.held) {
                        whenever(player.x isBelow 152) { moveBy(player, 2, 0) }
                    }

                    // Step counter — increment when any d-pad direction is held
                    whenever(dpad.any) { stepCount += 1 }

                    // Torch depletes slowly (every 4 steps via stepCount modulo)
                    whenever(
                        (stepCount isAbove 0) logicalAnd
                            ((stepCount and 3) isEqualTo 0) logicalAnd
                            (torchLevel isAbove 0)
                    ) {
                        torchLevel -= 1
                    }

                    // Pause — navigate to pauseScene via SceneRef
                    whenever(buttons.start.pressed) { navigate(pauseScene) }

                    // Random encounter every 120 steps (~2s of walking) — navigate to combatScene
                    // via SceneRef
                    whenever(stepCount isAtLeast 120) {
                        stepCount set 0
                        navigate(combatScene)
                    }

                    // Torch out — show warning dialog then navigate to gameover
                    whenever(torchLevel isEqualTo 0) {
                        hideSprites()
                        // Use new dialog DSL: torchWarning.say() emits DialogSay op
                        torchWarning.say("Your torch burns out...")
                        delay(60)
                        navigate(gameoverScene)
                    }
                }
                exit {
                    // Hide the HUD when leaving gameplay
                    gameHud.hide()
                }
            }

        // Title scene — uses gameplayScene ref (defined above)
        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    // "EXPLORER" = 8 chars → col (20-8)/2 = 6
                    print("EXPLORER", position = PositionDef(6, 5))
                    print("A DUNGEON CRAWL", position = PositionDef(3, 8))
                    print("PRESS START", position = PositionDef(5, 12))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        // Reset all state for new game (only from title, not combat/pause)
                        hp set 20
                        torchLevel set 100
                        stepCount set 0
                        keys set 0
                        level set 1
                        navigate(gameplayScene)
                    }
                }
            }

        start = titleScene.id
    }
