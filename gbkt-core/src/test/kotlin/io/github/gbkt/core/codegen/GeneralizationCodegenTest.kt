/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.combat.TurnOrderStrategy
import io.github.gbkt.core.combat.activeTimeBattle
import io.github.gbkt.core.combat.realTimeBattle
import io.github.gbkt.core.combat.tacticalBattle
import io.github.gbkt.core.combat.turnBasedBattle
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.movement.freeRoamMovement
import io.github.gbkt.core.movement.gridMovement
import io.github.gbkt.core.movement.physicsMovement
import io.github.gbkt.core.movement.topDownMovement
import io.github.gbkt.core.rpg.StatSchema
import io.github.gbkt.core.rpg.StatStorageType
import io.github.gbkt.core.rpg.statSchema
import io.github.gbkt.core.rpg.useStatSchema
import io.github.gbkt.core.world.ZoneType
import io.github.gbkt.core.world.regionTrigger
import io.github.gbkt.core.world.stepTrigger
import io.github.gbkt.core.world.timeTrigger
import io.github.gbkt.core.world.waveTrigger
import io.github.gbkt.core.world.zone
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for the generalization layer code generation.
 *
 * Verifies that the new pluggable abstractions (StatSchema, Zone) generate correct C code.
 */
class GeneralizationCodegenTest {

    // =========================================================================
    // STAT SCHEMA TESTS
    // =========================================================================

    @Test
    fun `stat schema generates schema constants`() {
        val game =
            gbGame("test") {
                val myStats by statSchema {
                    stat("hp") {
                        display("HP")
                        storage(StatStorageType.UINT16)
                        max(999)
                        defaultValue(100)
                        category("vital")
                    }
                    stat("mp") {
                        display("MP")
                        storage(StatStorageType.UINT8)
                        max(99)
                        defaultValue(50)
                        category("vital")
                    }
                    stat("str") {
                        display("STR")
                        category("offense")
                    }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify schema constants
        assertTrue(code.contains("SCHEMA_MYSTATS"), "Should define schema constant")
        assertTrue(code.contains("SCHEMA_COUNT"), "Should define schema count")

        // Verify stat index constants
        assertTrue(code.contains("MYSTATS_STAT_HP"), "Should define HP stat index")
        assertTrue(code.contains("MYSTATS_STAT_MP"), "Should define MP stat index")
        assertTrue(code.contains("MYSTATS_STAT_STR"), "Should define STR stat index")
        assertTrue(code.contains("MYSTATS_STAT_COUNT"), "Should define stat count")
    }

    @Test
    fun `stat schema generates stat access functions`() {
        val game =
            gbGame("test") {
                val stats by statSchema {
                    stat("hp") {
                        display("HP")
                        storage(StatStorageType.UINT16)
                        max(999)
                    }
                    stat("atk") { display("ATK") }
                    asDefault()
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify getter function
        assertTrue(code.contains("_stats_get_stat"), "Should generate stat getter")

        // Verify setter function
        assertTrue(code.contains("_stats_set_stat"), "Should generate stat setter")

        // Verify modifier function
        assertTrue(code.contains("_stats_modify_stat"), "Should generate stat modifier")
    }

    @Test
    fun `stat schema generates storage type constants`() {
        val game =
            gbGame("test") {
                val stats by statSchema {
                    stat("hp") { storage(StatStorageType.UINT16) }
                    stat("atk") { storage(StatStorageType.UINT8) }
                    stat("mod") { storage(StatStorageType.INT8) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify storage type constants
        assertTrue(code.contains("STAT_STORAGE_UINT8"), "Should define UINT8 storage type")
        assertTrue(code.contains("STAT_STORAGE_UINT16"), "Should define UINT16 storage type")
        assertTrue(code.contains("STAT_STORAGE_INT8"), "Should define INT8 storage type")
    }

    @Test
    fun `stat schema generates metadata tables`() {
        val game =
            gbGame("test") {
                val stats by statSchema {
                    stat("hp") {
                        display("HP")
                        max(999)
                        defaultValue(100)
                    }
                    stat("sp") {
                        display("SP")
                        max(99)
                        defaultValue(50)
                    }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify metadata tables are generated
        assertTrue(code.contains("stat_max"), "Should generate max values table")
        assertTrue(code.contains("stat_default"), "Should generate default values table")
        assertTrue(code.contains("stat_names"), "Should generate display names table")
    }

    @Test
    fun `predefined stat schema generates correct code`() {
        val game =
            gbGame("test") {
                useStatSchema(StatSchema.MINIMALIST)

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify minimalist schema stats
        assertTrue(code.contains("MINIMALIST_STAT_HP"), "Should define HP stat")
        assertTrue(code.contains("MINIMALIST_STAT_ATK"), "Should define ATK stat")
        assertTrue(code.contains("MINIMALIST_STAT_DEF"), "Should define DEF stat")
    }

    @Test
    fun `stat schema with categories generates category support`() {
        val game =
            gbGame("test") {
                val stats by statSchema {
                    stat("hp") { category("vital") }
                    stat("sp") { category("vital") }
                    stat("str") { category("offense") }
                    stat("def") { category("defense") }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify category constants
        assertTrue(
            code.contains("CAT_VITAL") || code.contains("vital"),
            "Should define vital category",
        )
        assertTrue(
            code.contains("CAT_OFFENSE") || code.contains("offense"),
            "Should define offense category",
        )
    }

    // =========================================================================
    // ZONE TESTS
    // =========================================================================

    @Test
    fun `zone generates type constants and config`() {
        val game =
            gbGame("test") {
                val town by zone {
                    type(ZoneType.OVERWORLD)
                    defaultPosition(10, 10)
                    map("town_map") { size(32, 32) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify zone type constants
        assertTrue(code.contains("ZONE_TYPE_OVERWORLD"), "Should define OVERWORLD zone type")

        // Verify zone index
        assertTrue(code.contains("ZONE_TOWN"), "Should define zone constant")
    }

    @Test
    fun `zone generates zone count`() {
        val game =
            gbGame("test") {
                val town by zone {
                    type(ZoneType.OVERWORLD)
                    map("town_map") { size(16, 16) }
                }
                val dungeon by zone {
                    type(ZoneType.DUNGEON)
                    map("dungeon_map") { size(16, 16) }
                }
                val arena by zone {
                    type(ZoneType.ARENA)
                    map("arena_map") { size(16, 16) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify zones defined
        assertTrue(code.contains("ZONE_TOWN"), "Should define town zone")
        assertTrue(code.contains("ZONE_DUNGEON"), "Should define dungeon zone")
        assertTrue(code.contains("ZONE_ARENA"), "Should define arena zone")
        assertTrue(code.contains("ZONE_COUNT"), "Should define zone count")
    }

    @Test
    fun `zone generates default position config`() {
        val game =
            gbGame("test") {
                val area by zone {
                    type(ZoneType.ROOM)
                    defaultPosition(5, 10)
                    map("room_map") { size(8, 8) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify default position is set
        assertTrue(
            code.contains("5") && code.contains("10"),
            "Should include default position values",
        )
    }

    @Test
    fun `multiple zone types generate all constants`() {
        val game =
            gbGame("test") {
                val overworld by zone {
                    type(ZoneType.OVERWORLD)
                    map("m1") { size(8, 8) }
                }
                val dungeon by zone {
                    type(ZoneType.DUNGEON)
                    map("m2") { size(8, 8) }
                }
                val sideScroll by zone {
                    type(ZoneType.SIDE_SCROLLING)
                    map("m3") { size(8, 8) }
                }
                val arena by zone {
                    type(ZoneType.ARENA)
                    map("m4") { size(8, 8) }
                }
                val room by zone {
                    type(ZoneType.ROOM)
                    map("m5") { size(8, 8) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify all zone types
        assertTrue(code.contains("ZONE_TYPE_OVERWORLD"), "Should define OVERWORLD type")
        assertTrue(code.contains("ZONE_TYPE_DUNGEON"), "Should define DUNGEON type")
        assertTrue(code.contains("ZONE_TYPE_SIDE_SCROLLING"), "Should define SIDE_SCROLLING type")
        assertTrue(code.contains("ZONE_TYPE_ARENA"), "Should define ARENA type")
        assertTrue(code.contains("ZONE_TYPE_ROOM"), "Should define ROOM type")
    }

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Test
    fun `stat schema and zone generate without conflicts`() {
        val game =
            gbGame("test") {
                // Stat schema
                val stats by statSchema {
                    stat("hp") {
                        storage(StatStorageType.UINT16)
                        max(999)
                    }
                    stat("atk") {}
                }

                // Zone
                val town by zone {
                    type(ZoneType.OVERWORLD)
                    defaultPosition(0, 0)
                    map("town_map") { size(16, 16) }
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify both systems are present without conflicts
        assertTrue(
            code.contains("STAT") && code.contains("ZONE"),
            "Should generate both stat and zone systems",
        )
    }

    // =========================================================================
    // MOVEMENT CONTROLLER TESTS
    // =========================================================================

    @Test
    fun `grid movement generates constants`() {
        val game =
            gbGame("test") {
                val playerMovement by gridMovement {
                    tileSize(8)
                    speed(4)
                    smoothInterpolation(true)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify movement type constant
        assertTrue(code.contains("MOVE_TYPE_GRID"), "Should define GRID movement type")
        assertTrue(
            code.contains("CTRL_PLAYERMOVEMENT") || code.contains("PLAYERMOVEMENT"),
            "Should define movement controller",
        )
    }

    @Test
    fun `physics movement generates gravity and velocity constants`() {
        val game =
            gbGame("test") {
                val playerPhysics by physicsMovement {
                    tileSize(8)
                    gravity(4)
                    jumpVelocity(-64)
                    maxSpeedX(48)
                    maxSpeedY(80)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify physics constants
        assertTrue(code.contains("MOVE_TYPE_PHYSICS"), "Should define PHYSICS movement type")
    }

    @Test
    fun `multiple movement types generate all constants`() {
        val game =
            gbGame("test") {
                val gridMove by gridMovement { tileSize(8) }
                val physicsMove by physicsMovement { tileSize(8) }
                val freeMove by freeRoamMovement { speed(2) }
                val topDownMove by topDownMovement { tileSize(8) }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify all movement types
        assertTrue(code.contains("MOVE_TYPE_GRID"), "Should define GRID type")
        assertTrue(code.contains("MOVE_TYPE_PHYSICS"), "Should define PHYSICS type")
        assertTrue(code.contains("MOVE_TYPE_FREE_ROAM"), "Should define FREE_ROAM type")
        assertTrue(code.contains("MOVE_TYPE_TOP_DOWN"), "Should define TOP_DOWN type")
        assertTrue(code.contains("CTRL_COUNT"), "Should define controller count")
    }

    // =========================================================================
    // ENCOUNTER TRIGGER TESTS
    // =========================================================================

    @Test
    fun `step trigger generates step-based constants`() {
        val game =
            gbGame("test") {
                val randomEncounters by stepTrigger {
                    safeSteps(10)
                    initialChance(5)
                    incrementPerStep(3)
                    maxChance(100)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify trigger type constant
        assertTrue(
            code.contains("TRIGGER_TYPE_STEP_BASED"),
            "Should define STEP_BASED trigger type",
        )
        assertTrue(
            code.contains("TRIGGER_RANDOMENCOUNTERS") || code.contains("RANDOMENCOUNTERS"),
            "Should define trigger",
        )
    }

    @Test
    fun `time trigger generates time-based constants`() {
        val game =
            gbGame("test") {
                val timedEncounters by timeTrigger {
                    safeFrames(300)
                    checkInterval(60)
                    baseChance(10)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify time-based trigger
        assertTrue(
            code.contains("TRIGGER_TYPE_TIME_BASED"),
            "Should define TIME_BASED trigger type",
        )
    }

    @Test
    fun `region trigger generates zone-based constants`() {
        val game =
            gbGame("test") {
                val zoneEncounters by regionTrigger {
                    dangerZone(x1 = 0, y1 = 0, x2 = 10, y2 = 10, chance = 50)
                    checkInterval(60)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify region-based trigger
        assertTrue(
            code.contains("TRIGGER_TYPE_REGION_BASED"),
            "Should define REGION_BASED trigger type",
        )
    }

    @Test
    fun `wave trigger generates wave-based constants`() {
        val game =
            gbGame("test") {
                val arenaWaves by waveTrigger {
                    wave(1, delay = 0) { monster("goblin") }
                    wave(2, delay = 60) { monster("orc") }
                    loop(scaling = 10)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify wave-based trigger
        assertTrue(
            code.contains("TRIGGER_TYPE_WAVE_BASED"),
            "Should define WAVE_BASED trigger type",
        )
    }

    // =========================================================================
    // BATTLE ENGINE TESTS
    // =========================================================================

    @Test
    fun `turn-based battle generates constants`() {
        val game =
            gbGame("test") {
                val combat by turnBasedBattle {
                    name("Main Combat")
                    maxPartySize(4)
                    maxEnemies(3)
                    turnOrder(TurnOrderStrategy.SPEED_BASED)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify combat type constant
        assertTrue(code.contains("COMBAT_TYPE_TURN_BASED"), "Should define TURN_BASED combat type")
        assertTrue(
            code.contains("BATTLE_COMBAT") || code.contains("ENGINE_COMBAT"),
            "Should define battle engine",
        )
    }

    @Test
    fun `active time battle generates ATB constants`() {
        val game =
            gbGame("test") {
                val combat by activeTimeBattle {
                    name("ATB Combat")
                    baseFillRate(4)
                    speedMultiplier(2)
                    pauseSettings(onMenu = true, onAnimation = false)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify ATB type
        assertTrue(
            code.contains("COMBAT_TYPE_ACTIVE_TIME"),
            "Should define ACTIVE_TIME combat type",
        )
    }

    @Test
    fun `real-time battle generates action constants`() {
        val game =
            gbGame("test") {
                val combat by realTimeBattle {
                    name("Action Combat")
                    hitStun(10)
                    invincibility(60)
                    knockback(8)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify real-time type
        assertTrue(code.contains("COMBAT_TYPE_REAL_TIME"), "Should define REAL_TIME combat type")
    }

    @Test
    fun `tactical battle generates grid constants`() {
        val game =
            gbGame("test") {
                val combat by tacticalBattle {
                    name("Tactical Combat")
                    gridSize(16, 16)
                    baseMoveRange(4)
                    facing(enabled = true, flankBonus = 25)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify tactical type
        assertTrue(code.contains("COMBAT_TYPE_TACTICAL"), "Should define TACTICAL combat type")
    }

    @Test
    fun `multiple battle engines generate all types`() {
        val game =
            gbGame("test") {
                val turnCombat by turnBasedBattle {
                    name("Turn Combat")
                    turnOrder(TurnOrderStrategy.SPEED_BASED)
                }
                val atbCombat by activeTimeBattle { name("ATB Combat") }
                val actionCombat by realTimeBattle { name("Action Combat") }
                val tactCombat by tacticalBattle {
                    name("Tactical Combat")
                    gridSize(8, 8)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify all combat types
        assertTrue(code.contains("COMBAT_TYPE_TURN_BASED"), "Should define TURN_BASED type")
        assertTrue(code.contains("COMBAT_TYPE_ACTIVE_TIME"), "Should define ACTIVE_TIME type")
        assertTrue(code.contains("COMBAT_TYPE_REAL_TIME"), "Should define REAL_TIME type")
        assertTrue(code.contains("COMBAT_TYPE_TACTICAL"), "Should define TACTICAL type")
        assertTrue(code.contains("ENGINE_COUNT"), "Should define engine count")
    }

    // =========================================================================
    // FULL INTEGRATION TESTS
    // =========================================================================

    @Test
    fun `all generalization systems work together`() {
        val game =
            gbGame("test") {
                // Stat schema
                val stats by statSchema {
                    stat("hp") { max(100) }
                    stat("atk") {}
                }

                // Zone
                val town by zone {
                    type(ZoneType.OVERWORLD)
                    map("town") { size(16, 16) }
                }

                // Movement controller
                val movement by gridMovement {
                    tileSize(8)
                    speed(4)
                }

                // Encounter trigger
                val trigger by stepTrigger {
                    safeSteps(10)
                    initialChance(5)
                }

                // Battle engine
                val combat by turnBasedBattle {
                    name("Combat")
                    turnOrder(TurnOrderStrategy.SPEED_BASED)
                }

                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Verify all systems are present
        assertTrue(code.contains("STAT"), "Should generate stat system")
        assertTrue(code.contains("ZONE"), "Should generate zone system")
        assertTrue(code.contains("MOVEMENT"), "Should generate movement system")
        assertTrue(code.contains("TRIGGER"), "Should generate trigger system")
        assertTrue(
            code.contains("COMBAT") || code.contains("BATTLE"),
            "Should generate battle system",
        )
    }
}
