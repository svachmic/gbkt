/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.exploration.*
import io.github.gbkt.core.ir.u8Var
import kotlin.test.*

/**
 * Tests for the exploration system.
 *
 * Validates:
 * - Exploration system creation
 * - Movement styles (GRID, SMOOTH)
 * - Tile size and movement speed configuration
 * - Gauge creation and depletion
 * - Key counter management
 * - Callback registration
 */
class ExplorationTest {

    // =========================================================================
    // EXPLORATION SYSTEM CREATION
    // =========================================================================

    @Test
    fun `exploration system is created with correct id`() {
        val game =
            gbGame("ExplorationCreationTest") {
                val dungeon by exploration {
                    tileSize(8)
                    movementSpeed(4)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.explorations.size, "Should have 1 exploration system")
        assertEquals("dungeon", game.explorations[0].id, "Exploration ID should be 'dungeon'")
    }

    @Test
    fun `multiple exploration systems can be created`() {
        val game =
            gbGame("MultiExplorationTest") {
                val overworld by exploration {
                    tileSize(16)
                    movementStyle(MovementStyle.SMOOTH)
                }

                val dungeon by exploration {
                    tileSize(8)
                    movementStyle(MovementStyle.GRID)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(2, game.explorations.size, "Should have 2 exploration systems")
        assertTrue(
            game.explorations.any { it.id == "overworld" },
            "Should have 'overworld' exploration",
        )
        assertTrue(
            game.explorations.any { it.id == "dungeon" },
            "Should have 'dungeon' exploration",
        )
    }

    // =========================================================================
    // MOVEMENT STYLE CONFIGURATION
    // =========================================================================

    @Test
    fun `exploration defaults to grid movement`() {
        val game =
            gbGame("DefaultMovementTest") {
                val dungeon by exploration { tileSize(8) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(
            MovementStyle.GRID,
            game.explorations[0].movementStyle,
            "Default movement style should be GRID",
        )
    }

    @Test
    fun `exploration with smooth movement is configured correctly`() {
        val game =
            gbGame("SmoothMovementTest") {
                val overworld by exploration {
                    tileSize(8)
                    movementStyle(MovementStyle.SMOOTH)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(
            MovementStyle.SMOOTH,
            game.explorations[0].movementStyle,
            "Movement style should be SMOOTH",
        )
    }

    @Test
    fun `exploration with grid movement is configured correctly`() {
        val game =
            gbGame("GridMovementTest") {
                val dungeon by exploration {
                    tileSize(8)
                    movementStyle(MovementStyle.GRID)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(
            MovementStyle.GRID,
            game.explorations[0].movementStyle,
            "Movement style should be GRID",
        )
    }

    // =========================================================================
    // TILE SIZE CONFIGURATION
    // =========================================================================

    @Test
    fun `exploration tile size is configured correctly`() {
        val game =
            gbGame("TileSizeTest") {
                val dungeon by exploration { tileSize(16) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(16, game.explorations[0].tileSize, "Tile size should be 16")
    }

    @Test
    fun `exploration tile size defaults to 8`() {
        val game =
            gbGame("DefaultTileSizeTest") {
                val dungeon by exploration {
                    // No tile size specified
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(8, game.explorations[0].tileSize, "Default tile size should be 8")
    }

    @Test
    fun `exploration tile size below 1 throws error`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidTileSizeTest") {
                val dungeon by exploration { tileSize(0) }

                start = scene("main") { every.frame {} }
            }
        }
    }

    @Test
    fun `exploration tile size above 32 throws error`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidLargeTileSizeTest") {
                val dungeon by exploration { tileSize(64) }

                start = scene("main") { every.frame {} }
            }
        }
    }

    // =========================================================================
    // MOVEMENT SPEED CONFIGURATION
    // =========================================================================

    @Test
    fun `exploration movement speed is configured correctly`() {
        val game =
            gbGame("MovementSpeedTest") {
                val dungeon by exploration {
                    tileSize(8)
                    movementSpeed(8)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(8, game.explorations[0].movementSpeed, "Movement speed should be 8")
    }

    @Test
    fun `exploration movement speed defaults to 4`() {
        val game =
            gbGame("DefaultMovementSpeedTest") {
                val dungeon by exploration {
                    tileSize(8)
                    // No movement speed specified
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(4, game.explorations[0].movementSpeed, "Default movement speed should be 4")
    }

    @Test
    fun `exploration movement speed must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("ZeroMovementSpeedTest") {
                val dungeon by exploration {
                    tileSize(8)
                    movementSpeed(0)
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    @Test
    fun `exploration movement speed must be positive not negative`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("NegativeMovementSpeedTest") {
                val dungeon by exploration {
                    tileSize(8)
                    movementSpeed(-1)
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    // =========================================================================
    // GAUGE CREATION
    // =========================================================================

    @Test
    fun `exploration gauge is created with correct values`() {
        val game =
            gbGame("GaugeCreationTest") {
                val dungeon by exploration {
                    tileSize(8)
                    gauge("torch") {
                        max(255)
                        initial(200)
                        decrementPerStep(1)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertEquals(1, exploration.gauges.size, "Should have 1 gauge")

        val gauge = exploration.gauges[0]
        assertEquals("torch", gauge.id, "Gauge ID should be 'torch'")
        assertEquals(255, gauge.maxValue, "Max value should be 255")
        assertEquals(200, gauge.initialValue, "Initial value should be 200")
        assertEquals(1, gauge.decrementPerStep, "Decrement per step should be 1")
    }

    @Test
    fun `exploration gauge with frame decrement`() {
        val game =
            gbGame("GaugeFrameDecrementTest") {
                val dungeon by exploration {
                    tileSize(8)
                    gauge("stamina") {
                        max(100)
                        initial(100)
                        decrementPerFrame(1)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val gauge = game.explorations[0].gauges[0]
        assertEquals(1, gauge.decrementPerFrame, "Decrement per frame should be 1")
        assertEquals(0, gauge.decrementPerStep, "Decrement per step should be 0 (default)")
    }

    @Test
    fun `exploration gauge with low threshold callback`() {
        var called = false
        val game =
            gbGame("GaugeLowThresholdTest") {
                var warning by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    gauge("torch") {
                        max(255)
                        initial(255)
                        decrementPerStep(1)
                        onLow(50) { warning set 1 }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val gauge = game.explorations[0].gauges[0]
        assertEquals(50, gauge.lowThreshold, "Low threshold should be 50")
        assertTrue(gauge.onLowStatements.isNotEmpty(), "Should have onLow statements")
    }

    @Test
    fun `exploration gauge with depleted callback`() {
        val game =
            gbGame("GaugeDepletedTest") {
                var torchOut by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    gauge("torch") {
                        max(255)
                        initial(255)
                        decrementPerStep(1)
                        onDepleted { torchOut set 1 }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val gauge = game.explorations[0].gauges[0]
        assertTrue(gauge.onDepletedStatements.isNotEmpty(), "Should have onDepleted statements")
    }

    @Test
    fun `exploration multiple gauges`() {
        val game =
            gbGame("MultiGaugeTest") {
                val dungeon by exploration {
                    tileSize(8)
                    gauge("torch") {
                        max(255)
                        initial(255)
                    }
                    gauge("stamina") {
                        max(100)
                        initial(100)
                    }
                    gauge("hunger") {
                        max(100)
                        initial(100)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertEquals(3, exploration.gauges.size, "Should have 3 gauges")
        assertTrue(exploration.gauges.any { it.id == "torch" }, "Should have 'torch' gauge")
        assertTrue(exploration.gauges.any { it.id == "stamina" }, "Should have 'stamina' gauge")
        assertTrue(exploration.gauges.any { it.id == "hunger" }, "Should have 'hunger' gauge")
    }

    @Test
    fun `exploration gauge max value must be in valid range`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidGaugeMaxTest") {
                val dungeon by exploration {
                    tileSize(8)
                    gauge("invalid") { max(0) }
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    @Test
    fun `exploration gauge initial value must be non-negative`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidGaugeInitialTest") {
                val dungeon by exploration {
                    tileSize(8)
                    gauge("invalid") {
                        max(100)
                        initial(-1)
                    }
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    // =========================================================================
    // KEY COUNTER MANAGEMENT
    // =========================================================================

    @Test
    fun `exploration key is created with correct values`() {
        val game =
            gbGame("KeyCreationTest") {
                val dungeon by exploration {
                    tileSize(8)
                    keys("magic_key") {
                        max(99)
                        initial(0)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertEquals(1, exploration.keys.size, "Should have 1 key type")

        val key = exploration.keys[0]
        assertEquals("magic_key", key.id, "Key ID should be 'magic_key'")
        assertEquals(99, key.maxCount, "Max count should be 99")
        assertEquals(0, key.initialCount, "Initial count should be 0")
    }

    @Test
    fun `exploration key with starting count`() {
        val game =
            gbGame("KeyWithInitialTest") {
                val dungeon by exploration {
                    tileSize(8)
                    keys("skeleton_key") {
                        max(10)
                        initial(3)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val key = game.explorations[0].keys[0]
        assertEquals(3, key.initialCount, "Initial count should be 3")
    }

    @Test
    fun `exploration multiple key types`() {
        val game =
            gbGame("MultiKeyTest") {
                val dungeon by exploration {
                    tileSize(8)
                    keys("gold_key") { max(99) }
                    keys("silver_key") { max(99) }
                    keys("bronze_key") { max(99) }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertEquals(3, exploration.keys.size, "Should have 3 key types")
        assertTrue(exploration.keys.any { it.id == "gold_key" }, "Should have 'gold_key'")
        assertTrue(exploration.keys.any { it.id == "silver_key" }, "Should have 'silver_key'")
        assertTrue(exploration.keys.any { it.id == "bronze_key" }, "Should have 'bronze_key'")
    }

    @Test
    fun `exploration key max count must be in valid range`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidKeyMaxTest") {
                val dungeon by exploration {
                    tileSize(8)
                    keys("invalid") { max(0) }
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    @Test
    fun `exploration key initial count must be non-negative`() {
        assertFailsWith<IllegalArgumentException> {
            gbGame("InvalidKeyInitialTest") {
                val dungeon by exploration {
                    tileSize(8)
                    keys("invalid") {
                        max(99)
                        initial(-1)
                    }
                }

                start = scene("main") { every.frame {} }
            }
        }
    }

    // =========================================================================
    // CALLBACK REGISTRATION
    // =========================================================================

    @Test
    fun `exploration onStep callback is registered`() {
        val game =
            gbGame("OnStepTest") {
                var steps by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    onStep { steps += 1 }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertTrue(exploration.onStepStatements.isNotEmpty(), "Should have onStep statements")
    }

    @Test
    fun `exploration onBlocked callback is registered`() {
        val game =
            gbGame("OnBlockedTest") {
                var blocked by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    onBlocked { blocked set 1 }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertTrue(exploration.onBlockedStatements.isNotEmpty(), "Should have onBlocked statements")
    }

    @Test
    fun `exploration onInteract callback is registered`() {
        val game =
            gbGame("OnInteractTest") {
                var interacting by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    onInteract { interacting set 1 }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertTrue(
            exploration.onInteractStatements.isNotEmpty(),
            "Should have onInteract statements",
        )
    }

    @Test
    fun `exploration onWater callback is registered`() {
        val game =
            gbGame("OnWaterTest") {
                var inWater by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    onWater { inWater set 1 }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertTrue(exploration.onWaterStatements.isNotEmpty(), "Should have onWater statements")
    }

    @Test
    fun `exploration onPit callback is registered`() {
        val game =
            gbGame("OnPitTest") {
                var fellInPit by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    onPit { fellInPit set 1 }
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertTrue(exploration.onPitStatements.isNotEmpty(), "Should have onPit statements")
    }

    // =========================================================================
    // COLLISION AND TERRAIN SETTINGS
    // =========================================================================

    @Test
    fun `exploration wall collision defaults to true`() {
        val game =
            gbGame("DefaultWallCollisionTest") {
                val dungeon by exploration { tileSize(8) }

                start = scene("main") { every.frame {} }
            }

        assertTrue(
            game.explorations[0].wallCollisionEnabled,
            "Wall collision should be enabled by default",
        )
    }

    @Test
    fun `exploration wall collision can be disabled`() {
        val game =
            gbGame("DisableWallCollisionTest") {
                val dungeon by exploration {
                    tileSize(8)
                    wallCollision(false)
                }

                start = scene("main") { every.frame {} }
            }

        assertFalse(game.explorations[0].wallCollisionEnabled, "Wall collision should be disabled")
    }

    @Test
    fun `exploration water blocks defaults to true`() {
        val game =
            gbGame("DefaultWaterBlocksTest") {
                val dungeon by exploration { tileSize(8) }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.explorations[0].waterBlocks, "Water should block movement by default")
    }

    @Test
    fun `exploration water blocks can be disabled`() {
        val game =
            gbGame("DisableWaterBlocksTest") {
                val dungeon by exploration {
                    tileSize(8)
                    waterBlocks(false)
                }

                start = scene("main") { every.frame {} }
            }

        assertFalse(game.explorations[0].waterBlocks, "Water should not block movement")
    }

    @Test
    fun `exploration pit damage defaults to 10`() {
        val game =
            gbGame("DefaultPitDamageTest") {
                val dungeon by exploration { tileSize(8) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(10, game.explorations[0].pitDamage, "Default pit damage should be 10")
    }

    @Test
    fun `exploration pit damage can be configured`() {
        val game =
            gbGame("CustomPitDamageTest") {
                val dungeon by exploration {
                    tileSize(8)
                    pitDamage(25)
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(25, game.explorations[0].pitDamage, "Pit damage should be 25")
    }

    // =========================================================================
    // COMPLETE EXPLORATION CONFIGURATION
    // =========================================================================

    @Test
    fun `exploration with full configuration`() {
        val game =
            gbGame("FullExplorationTest") {
                var torchOut by u8Var(0)
                var steps by u8Var(0)

                val dungeon by exploration {
                    tileSize(8)
                    movementSpeed(8)
                    movementStyle(MovementStyle.GRID)
                    wallCollision(true)
                    waterBlocks(true)
                    pitDamage(15)

                    gauge("torch") {
                        max(255)
                        initial(255)
                        decrementPerStep(1)
                        onLow(50) { /* warning */ }
                        onDepleted { torchOut set 1 }
                    }

                    keys("magic_key") {
                        max(99)
                        initial(0)
                    }

                    onStep { steps += 1 }
                    onBlocked { torchOut set 0 } // placeholder action
                    onInteract { steps set 0 } // placeholder action
                }

                start = scene("main") { every.frame {} }
            }

        val exploration = game.explorations[0]
        assertEquals("dungeon", exploration.id)
        assertEquals(8, exploration.tileSize)
        assertEquals(8, exploration.movementSpeed)
        assertEquals(MovementStyle.GRID, exploration.movementStyle)
        assertTrue(exploration.wallCollisionEnabled)
        assertTrue(exploration.waterBlocks)
        assertEquals(15, exploration.pitDamage)
        assertEquals(1, exploration.gauges.size)
        assertEquals(1, exploration.keys.size)
        assertTrue(exploration.onStepStatements.isNotEmpty())
        assertTrue(exploration.onBlockedStatements.isNotEmpty())
        assertTrue(exploration.onInteractStatements.isNotEmpty())
    }
}
