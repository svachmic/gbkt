/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the turn-based battle system.
 *
 * Validates:
 * - Battle state definitions
 * - Battle system configuration
 * - Action types and targeting
 * - IR generation
 * - Code generation
 */
class BattleTest {

    // =========================================================================
    // BATTLE STATE DEFINITIONS
    // =========================================================================

    @Test
    fun `battle states have correct ids`() {
        assertEquals(0, BattleState.INIT.id)
        assertEquals(1, BattleState.INTRO.id)
        assertEquals(2, BattleState.TURN_START.id)
        assertEquals(4, BattleState.PLAYER_MENU.id)
        assertEquals(16, BattleState.VICTORY.id)
        assertEquals(17, BattleState.DEFEAT.id)
        assertEquals(18, BattleState.FLED.id)
    }

    @Test
    fun `all 19 built-in battle states are defined`() {
        assertEquals(19, BattleState.BUILT_IN_STATES.size)
    }

    @Test
    fun `battle states have correct names`() {
        assertEquals("INIT", BattleState.INIT.name)
        assertEquals("INTRO", BattleState.INTRO.name)
        assertEquals("VICTORY", BattleState.VICTORY.name)
        assertEquals("DEFEAT", BattleState.DEFEAT.name)
        assertEquals("FLED", BattleState.FLED.name)
    }

    @Test
    fun `built-in battle states are marked as built-in`() {
        assertTrue(BattleState.INIT.isBuiltIn)
        assertTrue(BattleState.VICTORY.isBuiltIn)
        assertTrue(BattleState.PLAYER_MENU.isBuiltIn)
    }

    @Test
    fun `custom battle states can be created`() {
        BattleState.resetCustomIdCounter()
        val cutscene = BattleState.createCustom("Cutscene")
        val animation = BattleState.createCustom("Animation")

        assertEquals(19, cutscene.id)
        assertEquals("Cutscene", cutscene.name)
        assertEquals(false, cutscene.isBuiltIn)

        assertEquals(20, animation.id)
        assertEquals("Animation", animation.name)

        // Reset for other tests
        BattleState.resetCustomIdCounter()
    }

    // =========================================================================
    // BATTLE ACTION TYPES
    // =========================================================================

    @Test
    fun `battle action types are defined`() {
        val actionTypes = BattleActionType.entries
        assertTrue(actionTypes.contains(BattleActionType.ATTACK))
        assertTrue(actionTypes.contains(BattleActionType.ABILITY))
        assertTrue(actionTypes.contains(BattleActionType.ITEM))
        assertTrue(actionTypes.contains(BattleActionType.DEFEND))
        assertTrue(actionTypes.contains(BattleActionType.FLEE))
        assertTrue(actionTypes.contains(BattleActionType.WAIT))
        assertEquals(6, actionTypes.size)
    }

    // =========================================================================
    // TARGETING MODES
    // =========================================================================

    @Test
    fun `targeting modes are defined`() {
        val modes = TargetingMode.entries
        assertTrue(modes.contains(TargetingMode.SINGLE_ENEMY))
        assertTrue(modes.contains(TargetingMode.ALL_ENEMIES))
        assertTrue(modes.contains(TargetingMode.SINGLE_ALLY))
        assertTrue(modes.contains(TargetingMode.ALL_ALLIES))
        assertTrue(modes.contains(TargetingMode.SELF))
        assertTrue(modes.contains(TargetingMode.NONE))
        assertEquals(6, modes.size)
    }

    // =========================================================================
    // BATTLE LAYOUT
    // =========================================================================

    @Test
    fun `battle layouts are defined`() {
        val layouts = BattleLayout.entries
        assertTrue(layouts.contains(BattleLayout.SINGLE_LARGE))
        assertTrue(layouts.contains(BattleLayout.TWO_MEDIUM))
        assertTrue(layouts.contains(BattleLayout.THREE_SMALL))
        assertTrue(layouts.contains(BattleLayout.FOUR_SMALL))
        assertEquals(6, layouts.size)
    }

    // =========================================================================
    // BATTLE SYSTEM BUILDER
    // =========================================================================

    @Test
    fun `battleSystem creates system with name`() {
        val system = battleSystem("main") {}

        assertEquals("main", system.name)
    }

    @Test
    fun `battleSystem has default party and enemy sizes`() {
        val system = battleSystem("test") {}

        assertEquals(4, system.maxPartySize)
        assertEquals(4, system.maxEnemies)
    }

    @Test
    fun `battleSystem can configure party size`() {
        val system = battleSystem("test") { maxPartySize(3) }

        assertEquals(3, system.maxPartySize)
    }

    @Test
    fun `battleSystem can configure enemy count`() {
        val system = battleSystem("test") { maxEnemies(2) }

        assertEquals(2, system.maxEnemies)
    }

    @Test
    fun `battleSystem can configure flee mechanics`() {
        val system = battleSystem("test") { fleeMechanics(baseChance = 30, perAgility = 5) }

        assertEquals(30, system.fleeChanceBase)
        assertEquals(5, system.fleeChancePerAgility)
    }

    // =========================================================================
    // STATE CALLBACKS
    // =========================================================================

    @Test
    fun `battleSystem records onInit callback`() {
        var system: BattleSystem? = null

        val game =
            gbGame("BattleInitCallbackTest") {
                system = battleSystem("battle") { onInit { /* init code */ } }

                start = scene("main") { every.frame {} }
            }

        assertNotNull(system?.stateCallbacks?.get(BattleState.INIT))
    }

    @Test
    fun `battleSystem records onVictory callback`() {
        var system: BattleSystem? = null

        val game =
            gbGame("BattleVictoryCallbackTest") {
                system = battleSystem("battle") { onVictory { /* victory code */ } }

                start = scene("main") { every.frame {} }
            }

        assertNotNull(system?.onVictory, "onVictory callback should be captured")
    }

    @Test
    fun `battleSystem records onDefeat callback`() {
        var system: BattleSystem? = null

        val game =
            gbGame("BattleDefeatCallbackTest") {
                system = battleSystem("battle") { onDefeat { /* defeat code */ } }

                start = scene("main") { every.frame {} }
            }

        assertNotNull(system?.onDefeat)
    }

    // =========================================================================
    // BATTLE ACTIONS
    // =========================================================================

    @Test
    fun `BattleAction can be created for attack`() {
        val action =
            BattleAction(
                type = BattleActionType.ATTACK,
                actorName = "hero",
                targetNames = listOf("goblin"),
            )

        assertEquals(BattleActionType.ATTACK, action.type)
        assertEquals("hero", action.actorName)
        assertEquals(listOf("goblin"), action.targetNames)
    }

    @Test
    fun `BattleAction can be created for ability`() {
        val action =
            BattleAction(
                type = BattleActionType.ABILITY,
                actorName = "hero",
                targetNames = listOf("goblin", "orc"),
                abilityId = 5,
            )

        assertEquals(BattleActionType.ABILITY, action.type)
        assertEquals(5, action.abilityId)
    }

    @Test
    fun `BattleAction can be created for item`() {
        val action =
            BattleAction(
                type = BattleActionType.ITEM,
                actorName = "hero",
                targetNames = listOf("hero"),
                itemId = 1,
            )

        assertEquals(BattleActionType.ITEM, action.type)
        assertEquals(1, action.itemId)
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerBattleSystem emits IR`() {
        val game =
            gbGame("RegisterBattleSystemIRTest") {
                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        // registerBattleSystem adds to game.battleSystems, which triggers codegen
        assertTrue(game.battleSystems.isNotEmpty(), "Should register battle system")
    }

    @Test
    fun `startBattle emits IR`() {
        val game =
            gbGame("StartBattleIRTest") {
                val system = battleSystem("battle") {}
                val goblin by character { stats { hp(30) } }

                start = scene("main") { every.frame { startBattle(system, goblin) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasStartIR = scene.onFrame.any { stmt -> stmt is IRBattleStart }
        assertTrue(hasStartIR, "Should emit IRBattleStart")
    }

    @Test
    fun `battleTransition emits IR`() {
        val game =
            gbGame("BattleTransitionIRTest") {
                start = scene("main") { every.frame { battleTransition(BattleState.PLAYER_MENU) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasTransitionIR = scene.onFrame.any { stmt -> stmt is IRBattleStateTransition }
        assertTrue(hasTransitionIR, "Should emit IRBattleStateTransition")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `battle system generates state constants`() {
        val game =
            gbGame("BattleCodegenConstantsTest") {
                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("BATTLE_STATE_INIT"), "Should generate INIT constant")
        assertTrue(
            code.contains("BATTLE_STATE_PLAYER_MENU"),
            "Should generate PLAYER_MENU constant",
        )
        assertTrue(code.contains("BATTLE_STATE_VICTORY"), "Should generate VICTORY constant")
    }

    @Test
    fun `battle system generates variables`() {
        val game =
            gbGame("BattleCodegenVarsTest") {
                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_battle_battle_active"), "Should generate battle active var")
        assertTrue(code.contains("_battle_battle_state"), "Should generate battle state var")
        assertTrue(code.contains("_battle_turn_number"), "Should generate turn number var")
    }

    @Test
    fun `battle system generates update function`() {
        val game =
            gbGame("BattleCodegenUpdateTest") {
                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_battle_battle_update"), "Should generate update function")
    }

    @Test
    fun `battle system generates transition function`() {
        val game =
            gbGame("BattleCodegenTransitionTest") {
                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_battle_battle_transition"),
            "Should generate transition function",
        )
    }

    // =========================================================================
    // CUSTOM BATTLE STATE TESTS
    // =========================================================================

    @Test
    fun `custom battle states can be defined via DSL`() {
        BattleState.resetCustomIdCounter()
        val game =
            gbGame("CustomBattleStateTest") {
                val cutsceneState by battleState("Cutscene")
                val animationState by battleState("Animation")

                start = scene("test") {}
            }

        // Verify custom states are registered
        assertEquals(2, game.customBattleStates.size)
        assertEquals("Cutscene", game.customBattleStates[0].name)
        assertEquals("Animation", game.customBattleStates[1].name)
        assertEquals(19, game.customBattleStates[0].id) // After built-in states
        assertEquals(20, game.customBattleStates[1].id)

        // Reset for other tests
        BattleState.resetCustomIdCounter()
    }

    @Test
    fun `custom battle states can be used in battle system callbacks`() {
        BattleState.resetCustomIdCounter()
        var callbackRegistered = false

        val game =
            gbGame("CustomStateBattleSystemTest") {
                val cutsceneState by battleState("Cutscene")

                val system =
                    battleSystem("battle") { onState(cutsceneState) { callbackRegistered = true } }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        // Callback should be registered (will be invoked during build)
        assertTrue(callbackRegistered, "Custom state callback should be registered")

        // Reset for other tests
        BattleState.resetCustomIdCounter()
    }

    @Test
    fun `codegen generates custom battle state constants`() {
        BattleState.resetCustomIdCounter()
        val game =
            gbGame("CustomStateCodegenTest") {
                val cutsceneState by battleState("Cutscene")
                val specialAnimState by battleState("Special Animation")

                val system = battleSystem("battle") {}
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate constants for custom states
        assertTrue(
            code.contains("BATTLE_STATE_CUTSCENE"),
            "Should generate CUTSCENE state constant",
        )
        assertTrue(
            code.contains("BATTLE_STATE_SPECIAL_ANIMATION"),
            "Should generate SPECIAL_ANIMATION state constant",
        )

        // State count should include both built-in and custom states
        assertTrue(
            code.contains("BATTLE_STATE_COUNT 21u"), // 19 built-in + 2 custom
            "Should have correct total state count",
        )

        // Reset for other tests
        BattleState.resetCustomIdCounter()
    }

    @Test
    fun `custom states can have callbacks generated`() {
        BattleState.resetCustomIdCounter()
        var callbackInvoked = false
        val game =
            gbGame("CustomStateCallbackCodegenTest") {
                val cutsceneState by battleState("Cutscene")

                val system =
                    battleSystem("battle") {
                        onState(cutsceneState) {
                            callbackInvoked = true
                            // Callback body - cutscene logic would go here
                        }
                    }
                registerBattleSystem(system)

                start = scene("main") { every.frame {} }
            }

        // Verify callback was invoked during build
        assertTrue(callbackInvoked, "Callback should be invoked during build")

        val code = game.compileForTest()

        // Should generate update function that handles custom state
        assertTrue(
            code.contains("BATTLE_STATE_CUTSCENE"),
            "Should reference CUTSCENE state in generated code",
        )

        // Reset for other tests
        BattleState.resetCustomIdCounter()
    }
}
