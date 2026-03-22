/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.explorer

import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.TriggerSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Explorer v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (5: title, gameplay, pause, combat_scene, gameover)
 * - Correct number of actors (1: player)
 * - Start scene is "title"
 * - Camera system registered
 * - Save system registered
 * - CombatEngineSystem(TURN_BASED) registered by simpleBattle {} builder (Plan 06.5-08 migration)
 * - Combat scene uses battleUpdate() which produces TriggerSystem("combat")
 * - All ScriptOp types in IR are core sealed types (no RPG-specific sealed subtypes)
 */
class ExplorerIRTest {

    private val ir = explorerV2.build()

    @Test
    fun `has 5 scenes`() {
        assertEquals(5, ir.scenes.size)
    }

    @Test
    fun `has 1 actor`() {
        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has torch level variable`() {
        assertTrue(ir.variables.any { it.name == "torchLevel" })
    }

    @Test
    fun `has step count variable`() {
        assertTrue(ir.variables.any { it.name == "stepCount" })
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
    fun `has combat CombatEngineSystem from simpleBattle builder`() {
        // simpleBattle() now produces CombatEngineSystem(TURN_BASED) — Plan 06.5-08 migration
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(
            combatSystem,
            "Expected CombatEngineSystem with id='combat' from simpleBattle builder (Plan 06.5-08)",
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
    fun `combat CombatEngineSystem partyIds contains hero`() {
        val combatSystem =
            ir.systems.filterIsInstance<CombatEngineSystem>().find { it.id == "combat" }
        assertNotNull(combatSystem, "Expected CombatEngineSystem with id='combat'")
        @Suppress("UNCHECKED_CAST")
        val partyIds = combatSystem!!.encounterConfig?.get("partyIds") as? List<String>
        assertNotNull(partyIds, "encounterConfig must have partyIds")
        assertTrue(partyIds.contains("hero"), "partyIds must contain 'hero'")
    }

    @Test
    fun `combat scene uses battleUpdate producing TriggerSystem`() {
        val combatScene = ir.scenes.first { it.id == "combat_scene" }
        val hasTrigger = combatScene.frameOps.any { it is TriggerSystem && it.systemId == "combat" }
        assertTrue(
            hasTrigger,
            "Expected TriggerSystem('combat') in combat_scene frameOps from battleUpdate()",
        )
    }

    @Test
    fun `gameplay scene has movement ops`() {
        val gameplay = ir.scenes.first { it.id == "gameplay" }
        assertTrue(
            gameplay.frameOps.isNotEmpty(),
            "gameplay scene must have frame ops for movement",
        )
    }

    @Test
    fun `player actor has correct initial position`() {
        val player = ir.actors.first { it.id == "player" }
        assertEquals(io.github.gbkt.core.ir.PositionDef(80, 72), player.position)
    }

    @Test
    fun `player actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "player" }.sprite)
    }

    @Test
    fun `scenes include all 5 expected scenes`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("gameplay"))
        assertTrue(sceneIds.contains("pause"))
        assertTrue(sceneIds.contains("combat_scene"))
        assertTrue(sceneIds.contains("gameover"))
    }

    @Test
    fun `all ScriptOp types in IR are core sealed types`() {
        // Verify that RPG builders produced only core IR types — no custom sealed subtypes
        // The BOM constraint: gbkt-rpg does NOT add new sealed ScriptOp or SystemIR subtypes
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
}
