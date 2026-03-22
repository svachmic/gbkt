/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameBuilderTest {

    @Test
    fun `game builder produces GameIR with correct name`() {
        val ir =
            game("TestGame") {
                    scene("title") {
                        enter { hideSprites() }
                        frame {}
                    }
                    start = "title"
                }
                .build()

        assertEquals("TestGame", ir.name)
    }

    @Test
    fun `game builder with two scenes produces GameIR with two scenes`() {
        val ir =
            game("TestGame") {
                    scene("title") { enter { hideSprites() } }
                    scene("game") { enter { showSprites() } }
                    start = "title"
                }
                .build()

        assertEquals(2, ir.scenes.size)
    }

    @Test
    fun `game builder with actor produces GameIR with one actor`() {
        val ir =
            game("TestGame") {
                    actor("player") {
                        position(80, 72)
                        sprite(asset("sprites/player.png")) { size(8, 16) }
                    }
                    scene("game") { enter { showSprites() } }
                    start = "game"
                }
                .build()

        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `game builder sets start scene correctly`() {
        val ir =
            game("TestGame") {
                    scene("title") { enter { hideSprites() } }
                    start = "title"
                }
                .build()

        assertEquals("title", ir.startScene)
    }

    @Test
    fun `game builder scene enter block produces non-empty enterOps`() {
        val ir =
            game("TestGame") {
                    scene("title") {
                        enter {
                            hideSprites()
                            clear()
                        }
                    }
                    start = "title"
                }
                .build()

        val titleScene = ir.scenes.first { it.id == "title" }
        assertTrue(titleScene.enterOps.isNotEmpty())
    }

    @Test
    fun `game builder scene frame block produces non-empty frameOps`() {
        val ir =
            game("TestGame") {
                    scene("title") {
                        frame { whenever(buttons.start.pressed) { navigate("title") } }
                    }
                    start = "title"
                }
                .build()

        val titleScene = ir.scenes.first { it.id == "title" }
        assertTrue(titleScene.frameOps.isNotEmpty())
    }

    @Test
    fun `game builder actor has correct position`() {
        val ir =
            game("TestGame") {
                    actor("player") {
                        position(80, 72)
                        sprite(asset("sprites/player.png")) { size(8, 16) }
                    }
                    scene("game") { enter { showSprites() } }
                    start = "game"
                }
                .build()

        val player = ir.actors.first { it.id == "player" }
        assertEquals(PositionDef(80, 72), player.position)
    }

    @Test
    fun `game builder throws DSLValidationError for unresolved start scene`() {
        val exception =
            assertFailsWith<DSLValidationError> {
                game("TestGame") { start = "nonexistent" }.build()
            }
        assertTrue(exception.message!!.contains("Unresolved"))
    }

    @Test
    fun `game builder throws DSLValidationError with Did you mean suggestion`() {
        val exception =
            assertFailsWith<DSLValidationError> {
                game("TestGame") {
                        scene("gameplay") { enter {} }
                        start = "gamepaly"
                    }
                    .build()
            }
        assertTrue(exception.message!!.contains("gameplay"))
    }

    // --- Variable delegate tests ---

    @Test
    fun `u8Var delegate registers VariableDef in GameIR`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") var score by u8Var(0)
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val scoreDef = ir.variables.find { it.name == "score" }
        assertNotNull(scoreDef)
        assertEquals(VarType.U8, scoreDef.type)
        assertEquals(0, scoreDef.initialValue)
    }

    @Test
    fun `i8Var delegate registers VariableDef with correct type and initial value`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") var speed by i8Var(1)
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val speedDef = ir.variables.find { it.name == "speed" }
        assertNotNull(speedDef)
        assertEquals(VarType.I8, speedDef.type)
        assertEquals(1, speedDef.initialValue)
    }

    @Test
    fun `u16Var delegate registers VariableDef with U16 type`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") var highScore by u16Var(0)
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val def = ir.variables.find { it.name == "highScore" }
        assertNotNull(def)
        assertEquals(VarType.U16, def.type)
    }

    @Test
    fun `i16Var delegate registers VariableDef with I16 type`() {
        val ir =
            game("TestGame") {
                    @Suppress("UNUSED_VARIABLE") var bigValue by i16Var(-100)
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val def = ir.variables.find { it.name == "bigValue" }
        assertNotNull(def)
        assertEquals(VarType.I16, def.type)
        assertEquals(-100, def.initialValue)
    }

    // --- System builder tests ---

    @Test
    fun `camera builder registers CameraSystem in GameIR`() {
        val ir =
            game("TestGame") {
                    camera { smoothing = 0.2f }
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val cameraSystem = ir.systems.filterIsInstance<io.github.gbkt.core.ir.CameraSystem>()
        assertTrue(cameraSystem.isNotEmpty())
    }

    @Test
    fun `saveData builder registers SaveSystem in GameIR`() {
        val ir =
            game("TestGame") {
                    saveData("save") { slots(1) }
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val saveSystem = ir.systems.filterIsInstance<io.github.gbkt.core.ir.SaveSystem>()
        assertTrue(saveSystem.isNotEmpty())
    }

    @Test
    fun `asset function creates AssetRef accessible in builder output`() {
        val ir =
            game("TestGame") {
                    actor("player") {
                        position(0, 0)
                        sprite(asset("sprites/player.png")) { size(8, 16) }
                    }
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        val player = ir.actors.first { it.id == "player" }
        assertNotNull(player.sprite)
        assertEquals("sprites/player.png", player.sprite!!.assetRef.path)
    }

    @Test
    fun `config block sets cartridge config`() {
        val ir =
            game("TestGame") {
                    config {
                        cartridge = "ROM_ONLY"
                        romBanks = 2
                    }
                    scene("game") { enter {} }
                    start = "game"
                }
                .build()

        assertEquals("ROM_ONLY", ir.config.cartridge)
        assertEquals(2, ir.config.romBanks)
    }

    @Test
    fun `full game builder produces valid GameIR`() {
        val ir =
            game("TestGame") {
                    val player =
                        actor("player") {
                            position(80, 72)
                            sprite(asset("sprites/player.png")) { size(8, 16) }
                        }

                    @Suppress("UNUSED_VARIABLE")
                    val titleScene =
                        scene("title") {
                            enter {
                                hideSprites()
                                clear()
                            }
                            frame { whenever(buttons.start.pressed) { navigate("game") } }
                        }

                    @Suppress("UNUSED_VARIABLE")
                    val gameScene =
                        scene("game") {
                            enter { showSprites() }
                            frame {
                                whenever(dpad.up.held) { moveBy(player, 0, -2) }
                                whenever(dpad.down.held) { moveBy(player, 0, 2) }
                            }
                        }

                    start = "title"
                }
                .build()

        assertEquals("TestGame", ir.name)
        assertEquals(2, ir.scenes.size)
        assertEquals(1, ir.actors.size)
        assertEquals("title", ir.startScene)
    }
}
