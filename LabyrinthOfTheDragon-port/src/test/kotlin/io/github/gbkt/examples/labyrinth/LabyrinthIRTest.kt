/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR structure tests for the complete Labyrinth of the Dragon V2 port.
 *
 * Validates the fully-wired game IR produced by [LabyrinthOfTheDragon.create]:
 * - Correct game name and start scene
 * - Cartridge configuration (MBC5, ROM banks, RAM banks)
 * - State variables declared with correct types
 * - Sound effects registered
 * - Save system with 3 slots
 * - All 7 scenes registered
 * - 12 monsters (kobold through dragon)
 * - 5 character classes (druid, fighter, monk, sorcerer, test)
 * - 8 dungeon floor zones
 * - 24 class abilities (6 per class, 4 classes)
 * - 8 consumable items
 * - 13 status effects
 * - 3 flag pages (chests_1_4, chests_5_8, world)
 * - ExplorationSystem with torch gauge
 * - CombatEngineSystem (turn-based) registered
 */
class LabyrinthIRTest {

    private val ir = LabyrinthOfTheDragon.create().build()

    // -------------------------------------------------------------------------
    // Core identity
    // -------------------------------------------------------------------------

    @Test
    fun `game has correct name`() {
        assertEquals("LabyrinthDragon", ir.name)
    }

    @Test
    fun `game start scene is title`() {
        assertEquals("title", ir.startScene)
        assertNotNull(ir.startScene)
    }

    // -------------------------------------------------------------------------
    // Cartridge configuration
    // -------------------------------------------------------------------------

    @Test
    fun `config has MBC5 with RAM and battery`() {
        assertEquals("MBC5_RAM_BATTERY", ir.config.cartridge)
    }

    @Test
    fun `config has 32 ROM banks`() {
        assertEquals(32, ir.config.romBanks)
    }

    @Test
    fun `config has 4 RAM banks`() {
        assertEquals(4, ir.config.ramBanks)
    }

    // -------------------------------------------------------------------------
    // State variables
    // -------------------------------------------------------------------------

    @Test
    fun `has currentFloor variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "currentFloor" && it.type == VarType.U8 },
            "Expected 'currentFloor' U8 variable",
        )
    }

    @Test
    fun `has torchLevel variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "torchLevel" && it.type == VarType.U8 },
            "Expected 'torchLevel' U8 variable",
        )
    }

    @Test
    fun `has torchLevel initial value 255`() {
        val v = ir.variables.first { it.name == "torchLevel" }
        assertEquals(255, v.initialValue)
    }

    @Test
    fun `has magicKeys variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "magicKeys" && it.type == VarType.U8 },
            "Expected 'magicKeys' U8 variable",
        )
    }

    @Test
    fun `has stepCount variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "stepCount" && it.type == VarType.U8 },
            "Expected 'stepCount' U8 variable",
        )
    }

    @Test
    fun `has selectedClass variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "selectedClass" && it.type == VarType.U8 },
            "Expected 'selectedClass' U8 variable",
        )
    }

    @Test
    fun `has selectedSaveSlot variable of type U8`() {
        assertTrue(
            ir.variables.any { it.name == "selectedSaveSlot" && it.type == VarType.U8 },
            "Expected 'selectedSaveSlot' U8 variable",
        )
    }

    @Test
    fun `has at least 10 state variables declared`() {
        assertTrue(
            ir.variables.size >= 10,
            "Expected at least 10 variables, found: ${ir.variables.size}",
        )
    }

    // -------------------------------------------------------------------------
    // Sound effects
    // -------------------------------------------------------------------------

    @Test
    fun `has sound effects registered`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Expected at least one sound effect")
    }

    @Test
    fun `has at least 20 sound effects`() {
        // Original has 31 SFX; port declares them all in Sounds.kt
        assertTrue(
            ir.soundEffects.size >= 20,
            "Expected at least 20 sound effects, found: ${ir.soundEffects.size}",
        )
    }

    // -------------------------------------------------------------------------
    // Save system
    // -------------------------------------------------------------------------

    @Test
    fun `has save system`() {
        assertTrue(ir.systems.any { it is SaveSystem }, "Expected SaveSystem registered")
    }

    @Test
    fun `save system has 3 slots`() {
        val saveSystem = ir.systems.filterIsInstance<SaveSystem>().first()
        assertEquals(
            GameConfig.SAVE_SLOTS,
            saveSystem.slots,
            "SaveSystem must have ${GameConfig.SAVE_SLOTS} slots",
        )
    }

    // -------------------------------------------------------------------------
    // Scenes — all 7 scenes wired
    // -------------------------------------------------------------------------

    @Test
    fun `has 7 scenes`() {
        // title, hero_select, gameplay, battle, pause, gameover, victory
        assertEquals(7, ir.scenes.size, "Expected 7 scenes: ${ir.scenes.map { it.id }}")
    }

    @Test
    fun `has title scene`() {
        assertTrue(ir.scenes.any { it.id == "title" }, "Expected 'title' scene")
    }

    @Test
    fun `has hero_select scene`() {
        assertTrue(ir.scenes.any { it.id == "hero_select" }, "Expected 'hero_select' scene")
    }

    @Test
    fun `has gameplay scene`() {
        assertTrue(ir.scenes.any { it.id == "gameplay" }, "Expected 'gameplay' scene")
    }

    @Test
    fun `has battle scene`() {
        assertTrue(ir.scenes.any { it.id == "battle" }, "Expected 'battle' scene")
    }

    @Test
    fun `has pause scene`() {
        assertTrue(ir.scenes.any { it.id == "pause" }, "Expected 'pause' scene")
    }

    @Test
    fun `has gameover scene`() {
        assertTrue(ir.scenes.any { it.id == "gameover" }, "Expected 'gameover' scene")
    }

    @Test
    fun `has victory scene`() {
        assertTrue(ir.scenes.any { it.id == "victory" }, "Expected 'victory' scene")
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(
            ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty(),
            "Title scene must have enter ops",
        )
    }

    @Test
    fun `gameover scene has enter ops`() {
        assertTrue(
            ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty(),
            "Gameover scene must have enter ops",
        )
    }

    // -------------------------------------------------------------------------
    // Monsters — 12 monsters (kobold through dragon)
    // -------------------------------------------------------------------------

    @Test
    fun `has 12 monsters`() {
        val monsterSystems =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "rpg_monster"
            }
        assertEquals(
            12,
            monsterSystems.size,
            "Expected 12 monsters: ${monsterSystems.map { it.id }}",
        )
    }

    @Test
    fun `has kobold monster`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_monster" && it.id == "kobold"
            },
            "Expected 'kobold' monster",
        )
    }

    @Test
    fun `has dragon monster`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_monster" && it.id == "dragon"
            },
            "Expected 'dragon' monster",
        )
    }

    // -------------------------------------------------------------------------
    // Character classes — 4 playable + 1 debug (5 total)
    // -------------------------------------------------------------------------

    @Test
    fun `has 5 character class systems`() {
        // druid, fighter, monk, sorcerer + test (debug class)
        val characterSystems =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "rpg_character_system"
            }
        assertEquals(
            5,
            characterSystems.size,
            "Expected 5 character systems: ${characterSystems.map { it.id }}",
        )
    }

    @Test
    fun `has druid character`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_character_system" && it.id == "druid"
            },
            "Expected 'druid' character system",
        )
    }

    @Test
    fun `has fighter character`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_character_system" && it.id == "fighter"
            },
            "Expected 'fighter' character system",
        )
    }

    @Test
    fun `has monk character`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_character_system" && it.id == "monk"
            },
            "Expected 'monk' character system",
        )
    }

    @Test
    fun `has sorcerer character`() {
        assertTrue(
            ir.systems.filterIsInstance<GenericSystem>().any {
                it.config["type"] == "rpg_character_system" && it.id == "sorcerer"
            },
            "Expected 'sorcerer' character system",
        )
    }

    // -------------------------------------------------------------------------
    // Dungeon floors — 8 zones
    // -------------------------------------------------------------------------

    @Test
    fun `has 8 dungeon floor zones`() {
        assertTrue(
            ir.zones.size >= 8,
            "Expected at least 8 zones (one per floor), found: ${ir.zones.size}",
        )
    }

    @Test
    fun `has floor1_entrance zone`() {
        assertTrue(
            ir.zones.any { it.id.contains("floor1") || it.id == "floor1_entrance" },
            "Expected floor1 zone, found: ${ir.zones.map { it.id }}",
        )
    }

    @Test
    fun `has floor8_dragon_lair zone`() {
        assertTrue(
            ir.zones.any { it.id.contains("floor8") },
            "Expected floor8 zone, found: ${ir.zones.map { it.id }}",
        )
    }

    // -------------------------------------------------------------------------
    // Abilities — 24 class abilities (6 per class x 4 classes)
    // -------------------------------------------------------------------------

    @Test
    fun `has 24 abilities`() {
        val abilitySystems =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "rpg_ability"
            }
        assertEquals(
            24,
            abilitySystems.size,
            "Expected 24 abilities (6 per class x 4 classes): ${abilitySystems.map { it.id }}",
        )
    }

    // -------------------------------------------------------------------------
    // Items — 8 consumables
    // -------------------------------------------------------------------------

    @Test
    fun `has 8 items`() {
        assertEquals(
            8,
            ir.items.size,
            "Expected 8 items (potion through haste): ${ir.items.map { it.id }}",
        )
    }

    @Test
    fun `has potion item`() {
        assertTrue(ir.items.any { it.id == "potion" }, "Expected 'potion' item")
    }

    @Test
    fun `has elixir item`() {
        assertTrue(ir.items.any { it.id == "elixir" }, "Expected 'elixir' item")
    }

    // -------------------------------------------------------------------------
    // Status effects — 13 active effects
    // -------------------------------------------------------------------------

    @Test
    fun `has 13 status effects`() {
        // 8 debuffs (blind, scared, paralyzed, poisoned, confused, aglDown, atkDown, defDown)
        // 5 buffs (haste, regen, aglUp, atkUp, defUp)
        val effectSystems =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "rpg_status_effect"
            }
        assertEquals(
            13,
            effectSystems.size,
            "Expected 13 status effects: ${effectSystems.map { it.id }}",
        )
    }

    // -------------------------------------------------------------------------
    // Flag pages — 3 flag pages (chests_1_4, chests_5_8, world)
    // -------------------------------------------------------------------------

    @Test
    fun `has flag pages`() {
        assertTrue(ir.flags.isNotEmpty(), "Expected at least one GlobalFlagsIR container")
    }

    @Test
    fun `has 3 flag pages`() {
        val pages = ir.flags.flatMap { it.pages }
        assertEquals(3, pages.size, "Expected 3 flag pages: chests_1_4, chests_5_8, world")
    }

    @Test
    fun `has chests_1_4 flag page`() {
        val pages = ir.flags.flatMap { it.pages }
        assertTrue(pages.any { it.name == "chests_1_4" }, "Expected 'chests_1_4' flag page")
    }

    @Test
    fun `has chests_5_8 flag page`() {
        val pages = ir.flags.flatMap { it.pages }
        assertTrue(pages.any { it.name == "chests_5_8" }, "Expected 'chests_5_8' flag page")
    }

    @Test
    fun `has world flag page`() {
        val pages = ir.flags.flatMap { it.pages }
        assertTrue(pages.any { it.name == "world" }, "Expected 'world' flag page")
    }

    // -------------------------------------------------------------------------
    // Exploration system
    // -------------------------------------------------------------------------

    @Test
    fun `has exploration system`() {
        assertTrue(
            ir.systems.any { it is ExplorationSystem },
            "Expected ExplorationSystem registered (dungeon crawler preset)",
        )
    }

    @Test
    fun `exploration system has torch gauge`() {
        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertTrue(
            exploration.gauges.any { it.id == "torch" },
            "Expected 'torch' gauge in exploration system, found: ${exploration.gauges.map { it.id }}",
        )
    }

    @Test
    fun `exploration system has magic_key counter`() {
        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertTrue(
            exploration.keys.any { it.id == "magic_key" },
            "Expected 'magic_key' counter in exploration system, found: ${exploration.keys.map { it.id }}",
        )
    }

    // -------------------------------------------------------------------------
    // Combat system — CombatEngineSystem (turn-based)
    // -------------------------------------------------------------------------

    @Test
    fun `has CombatEngineSystem`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
    }

    @Test
    fun `combat system is turn-based`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        assertEquals(CombatType.TURN_BASED, combatSystem!!.combatType)
    }

    @Test
    fun `combat system has party and encounter data`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        val config = combatSystem!!.encounterConfig
        assertNotNull(config, "CombatEngineSystem.encounterConfig must be set")
        assertTrue(config.containsKey("partyIds"), "encounterConfig must have partyIds")
        assertTrue(config.containsKey("encounterData"), "encounterConfig must have encounterData")
    }
}
