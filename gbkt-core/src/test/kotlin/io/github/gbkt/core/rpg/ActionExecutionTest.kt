/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the action execution pipeline system.
 *
 * Validates:
 * - Action pipeline configuration
 * - Action queuing
 * - IR generation
 * - Code generation
 */
class ActionExecutionTest {

    // =========================================================================
    // ACTION PIPELINE BUILDER
    // =========================================================================

    @Test
    fun `actionPipeline creates pipeline with default values`() {
        val pipeline = actionPipeline("test") {}

        assertEquals("test", pipeline.config.name)
        assertEquals(16, pipeline.config.maxQueueSize)
        assertTrue(pipeline.config.onActionStart.isEmpty())
        assertTrue(pipeline.config.onActionComplete.isEmpty())
    }

    @Test
    fun `actionPipeline can configure max queue size`() {
        val pipeline = actionPipeline("test") { maxQueueSize(8) }

        assertEquals(8, pipeline.config.maxQueueSize)
    }

    @Test
    fun `actionPipeline validates max queue size range`() {
        val exception =
            try {
                actionPipeline("test") {
                    maxQueueSize(64) // Invalid - max is 32
                }
                null
            } catch (e: IllegalArgumentException) {
                e
            }

        assertNotNull(exception)
        assertTrue(exception.message?.contains("1-32") == true)
    }

    // =========================================================================
    // ACTION TYPES
    // =========================================================================

    @Test
    fun `BattleActionType has all expected values`() {
        val types = BattleActionType.entries

        assertTrue(types.contains(BattleActionType.ATTACK))
        assertTrue(types.contains(BattleActionType.ABILITY))
        assertTrue(types.contains(BattleActionType.ITEM))
        assertTrue(types.contains(BattleActionType.DEFEND))
        assertTrue(types.contains(BattleActionType.FLEE))
        assertTrue(types.contains(BattleActionType.WAIT))
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerActionPipeline emits IR`() {
        val game =
            gbGame("RegisterActionPipelineIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasConfigIR = scene.onEnter.any { stmt -> stmt is IRActionPipelineConfig }
        assertTrue(hasConfigIR, "Should emit IRActionPipelineConfig")
    }

    @Test
    fun `queueAttack emits IR`() {
        val game =
            gbGame("QueueAttackIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueAttack(0, 1) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd && stmt.actionType == BattleActionType.ATTACK
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with ATTACK type")
    }

    @Test
    fun `queueAbility emits IR`() {
        val game =
            gbGame("QueueAbilityIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueAbility(0, 5, listOf(1, 2)) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd &&
                    stmt.actionType == BattleActionType.ABILITY &&
                    stmt.abilityId == 5 &&
                    stmt.targetIndices == listOf(1, 2)
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with ABILITY type")
    }

    @Test
    fun `queueItem emits IR`() {
        val game =
            gbGame("QueueItemIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueItem(0, 3, 2) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd &&
                    stmt.actionType == BattleActionType.ITEM &&
                    stmt.itemId == 3
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with ITEM type")
    }

    @Test
    fun `queueDefend emits IR`() {
        val game =
            gbGame("QueueDefendIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueDefend(0) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd && stmt.actionType == BattleActionType.DEFEND
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with DEFEND type")
    }

    @Test
    fun `queueFlee emits IR`() {
        val game =
            gbGame("QueueFleeIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueFlee(0) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd && stmt.actionType == BattleActionType.FLEE
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with FLEE type")
    }

    @Test
    fun `queueWait emits IR`() {
        val game =
            gbGame("QueueWaitIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.queueWait(0) }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRActionQueueAdd && stmt.actionType == BattleActionType.WAIT
            }
        assertTrue(hasIR, "Should emit IRActionQueueAdd with WAIT type")
    }

    @Test
    fun `executeNext emits IR`() {
        val game =
            gbGame("ExecuteNextIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.executeNext() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRActionExecute }
        assertTrue(hasIR, "Should emit IRActionExecute")
    }

    @Test
    fun `skipToNext emits IR`() {
        val game =
            gbGame("SkipToNextIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.skipToNext() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRNextAction }
        assertTrue(hasIR, "Should emit IRNextAction")
    }

    @Test
    fun `clearQueue emits IR`() {
        val game =
            gbGame("ClearQueueIRTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame { pipeline.clearQueue() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRActionQueueClear }
        assertTrue(hasIR, "Should emit IRActionQueueClear")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `action pipeline generates type constants`() {
        val game =
            gbGame("ActionPipelineCodegenConstantsTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("ACTION_TYPE_ATTACK"), "Should generate ATTACK constant")
        assertTrue(code.contains("ACTION_TYPE_ABILITY"), "Should generate ABILITY constant")
        assertTrue(code.contains("ACTION_TYPE_ITEM"), "Should generate ITEM constant")
        assertTrue(code.contains("ACTION_TYPE_DEFEND"), "Should generate DEFEND constant")
        assertTrue(code.contains("ACTION_TYPE_FLEE"), "Should generate FLEE constant")
        assertTrue(code.contains("ACTION_TYPE_WAIT"), "Should generate WAIT constant")
    }

    @Test
    fun `action pipeline generates state variables`() {
        val game =
            gbGame("ActionPipelineCodegenVarsTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_action_battle_queue"), "Should generate queue array")
        assertTrue(code.contains("_action_battle_count"), "Should generate count var")
        assertTrue(code.contains("_action_battle_current"), "Should generate current var")
        assertTrue(code.contains("_action_battle_current_type"), "Should generate current_type var")
        assertTrue(
            code.contains("_action_battle_current_actor"),
            "Should generate current_actor var",
        )
    }

    @Test
    fun `action pipeline generates helper functions`() {
        val game =
            gbGame("ActionPipelineCodegenFunctionsTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_action_battle_add"), "Should generate add function")
        assertTrue(code.contains("_action_battle_execute"), "Should generate execute function")
        assertTrue(code.contains("_action_battle_next"), "Should generate next function")
        assertTrue(code.contains("_action_battle_clear"), "Should generate clear function")
    }

    @Test
    fun `action pipeline with custom queue size generates correct constant`() {
        val game =
            gbGame("ActionPipelineQueueSizeTest") {
                val pipeline = actionPipeline("battle") { maxQueueSize(24) }

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("BATTLE_MAX_QUEUE 24u"), "Should have correct max queue constant")
    }

    @Test
    fun `action pipeline generates queue structure`() {
        val game =
            gbGame("ActionPipelineQueueStructTest") {
                val pipeline = actionPipeline("battle") {}

                start =
                    scene("main") {
                        enter { registerActionPipeline(pipeline) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("typedef struct"), "Should generate struct")
        assertTrue(code.contains("UINT8 type;"), "Should have type field")
        assertTrue(code.contains("UINT8 actor_index;"), "Should have actor_index field")
        assertTrue(code.contains("UINT8 target_count;"), "Should have target_count field")
        assertTrue(code.contains("UINT8 targets["), "Should have targets array")
        assertTrue(code.contains("UINT8 ability_id;"), "Should have ability_id field")
        assertTrue(code.contains("UINT8 item_id;"), "Should have item_id field")
        assertTrue(code.contains("battle_action_t;"), "Should generate action_t type")
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    @Test
    fun `QueuedAction stores correct values`() {
        val action =
            QueuedAction(
                actorIndex = 0,
                type = BattleActionType.ATTACK,
                targetIndices = listOf(1, 2),
                abilityId = null,
                itemId = null,
            )

        assertEquals(0, action.actorIndex)
        assertEquals(BattleActionType.ATTACK, action.type)
        assertEquals(listOf(1, 2), action.targetIndices)
        assertEquals(null, action.abilityId)
        assertEquals(null, action.itemId)
    }

    @Test
    fun `ActionResult stores correct values`() {
        val result =
            ActionResult(
                success = true,
                damage = 25,
                healing = 0,
                statusApplied = "poison",
                targetDefeated = true,
                actorDefeated = false,
            )

        assertTrue(result.success)
        assertEquals(25, result.damage)
        assertEquals(0, result.healing)
        assertEquals("poison", result.statusApplied)
        assertTrue(result.targetDefeated)
        assertEquals(false, result.actorDefeated)
    }

    @Test
    fun `ActionPipelineConfig has correct defaults`() {
        val config = ActionPipelineConfig(name = "test")

        assertEquals("test", config.name)
        assertEquals(16, config.maxQueueSize)
        assertTrue(config.onActionStart.isEmpty())
        assertTrue(config.onActionComplete.isEmpty())
        assertTrue(config.onActionFailed.isEmpty())
    }
}
