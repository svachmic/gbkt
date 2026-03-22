/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.dungeon

import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.TriggerSystem
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Dungeon crawler DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (4: title, gameplay, battle, gameover)
 * - Correct number of actors (1: player)
 * - Start scene is "title"
 * - Variables: torchLevel, keys, steps (3 total)
 * - Sound effects: bumpSfx, stepSfx, keySfx, hitSfx (4 total)
 * - ExplorationSystem registered (dungeon crawler preset)
 * - Zone definitions present (floor1)
 * - Flags system present (dungeon_flags)
 * - CombatEngineSystem (simple_battle) registered with id="combat"
 * - Scene IDs include gameplay and battle
 * - Player actor has correct position (64, 64)
 * - All ScriptOp types in IR are core sealed types
 */
class DungeonIRTest {

    private val ir = dungeon.build()

    @Test
    fun `has 4 scenes`() {
        assertEquals(4, ir.scenes.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has 1 actor`() {
        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `has 3 variables`() {
        assertEquals(3, ir.variables.size)
    }

    @Test
    fun `has torchLevel variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "torchLevel" && it.type == VarType.U8 })
    }

    @Test
    fun `has keys variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "keys" && it.type == VarType.U8 })
    }

    @Test
    fun `has steps variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "steps" && it.type == VarType.U8 })
    }

    @Test
    fun `has sound effects`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Expected at least one sound effect")
    }

    @Test
    fun `has 4 named sound effects`() {
        assertEquals(4, ir.soundEffects.size)
    }

    @Test
    fun `has bumpSfx stepSfx keySfx hitSfx sound effects`() {
        assertTrue(ir.soundEffects.any { it.id == "bumpSfx" }, "Expected 'bumpSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "stepSfx" }, "Expected 'stepSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "keySfx" }, "Expected 'keySfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "hitSfx" }, "Expected 'hitSfx' sound effect")
    }

    @Test
    fun `has exploration system`() {
        assertTrue(
            ir.systems.any { it is ExplorationSystem },
            "Expected ExplorationSystem in IR systems for dungeon crawler",
        )
    }

    @Test
    fun `has zone definitions`() {
        assertTrue(ir.zones.isNotEmpty(), "Expected at least one zone definition")
    }

    @Test
    fun `has floor1 zone`() {
        assertTrue(ir.zones.any { it.id == "floor1" }, "Expected zone with id='floor1'")
    }

    @Test
    fun `has flags system`() {
        assertTrue(ir.flags.isNotEmpty(), "Expected at least one GlobalFlagsIR container")
    }

    @Test
    fun `has dungeon_flags flags container`() {
        assertTrue(
            ir.flags.any { it.id == "dungeon_flags" },
            "Expected flags container with id='dungeon_flags'",
        )
    }

    @Test
    fun `has RPG combat CombatEngineSystem`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(
            combatSystem,
            "Expected CombatEngineSystem with id='combat' from simpleBattle builder",
        )
        assertEquals(CombatType.TURN_BASED, combatSystem.combatType)
    }

    @Test
    fun `combat CombatEngineSystem contains party and encounter data`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        val config = combatSystem!!.encounterConfig
        assertNotNull(
            config,
            "CombatEngineSystem.encounterConfig must be set by simpleBattle builder",
        )
        assertTrue(config.containsKey("partyIds"), "encounterConfig must have partyIds")
        assertTrue(config.containsKey("encounterData"), "encounterConfig must have encounterData")
    }

    @Test
    fun `combat CombatEngineSystem partyIds contains adventurer`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        @Suppress("UNCHECKED_CAST")
        val partyIds = combatSystem!!.encounterConfig?.get("partyIds") as? List<String>
        assertNotNull(partyIds, "encounterConfig must have partyIds")
        assertTrue(partyIds.contains("adventurer"), "partyIds must contain 'adventurer'")
    }

    @Test
    fun `scenes include title gameplay battle gameover`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"), "Expected 'title' scene")
        assertTrue(sceneIds.contains("gameplay"), "Expected 'gameplay' scene")
        assertTrue(sceneIds.contains("battle"), "Expected 'battle' scene")
        assertTrue(sceneIds.contains("gameover"), "Expected 'gameover' scene")
    }

    @Test
    fun `player actor has correct initial position`() {
        assertEquals(PositionDef(64, 64), ir.actors.first { it.id == "player" }.position)
    }

    @Test
    fun `player actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "player" }.sprite)
    }

    @Test
    fun `has camera system`() {
        assertTrue(ir.systems.any { it is CameraSystem }, "Expected CameraSystem in IR systems")
    }

    @Test
    fun `has save system`() {
        assertTrue(ir.systems.any { it is SaveSystem }, "Expected SaveSystem in IR systems")
    }

    @Test
    fun `battle scene drives combat via TriggerSystem`() {
        val battleScene = ir.scenes.first { it.id == "battle" }
        val hasTrigger = battleScene.frameOps.any { it is TriggerSystem && it.systemId == "combat" }
        assertTrue(
            hasTrigger,
            "Expected TriggerSystem('combat') in battle scene frameOps from battleUpdate()",
        )
    }

    @Test
    fun `combat CombatEngineSystem has onVictory and onDefeat ops`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        assertTrue(
            combatSystem!!.onVictoryOps.isNotEmpty(),
            "onVictoryOps must contain navigate('gameplay') op",
        )
        assertTrue(
            combatSystem.onDefeatOps.isNotEmpty(),
            "onDefeatOps must contain navigate('gameover') op",
        )
    }

    @Test
    fun `all ScriptOp types in IR are core sealed types`() {
        fun assertCoreOps(ops: List<ScriptOp>) {
            ops.forEach { op ->
                val qualifiedName = op::class.qualifiedName ?: ""
                assertTrue(
                    qualifiedName.startsWith("io.github.gbkt.core.ir"),
                    "Non-core ScriptOp found: $qualifiedName — RPG builders must only produce core IR types",
                )
                when (op) {
                    is IfOp -> {
                        assertCoreOps(op.then)
                        assertCoreOps(op.otherwise)
                    }
                    else -> {
                        /* leaf ops verified by class name check above */
                    }
                }
            }
        }

        ir.scenes.forEach { scene ->
            assertCoreOps(scene.enterOps + scene.frameOps + scene.exitOps)
        }
    }

    @Test
    fun `gameover scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty())
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameplay scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "gameplay" }.frameOps.isNotEmpty())
    }
}
