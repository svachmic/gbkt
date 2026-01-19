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
 * Tests for the turn order/initiative system.
 *
 * Validates:
 * - Initiative method definitions
 * - Speed tier definitions
 * - Turn order configuration
 * - IR generation
 * - Code generation
 */
class TurnOrderTest {

    // =========================================================================
    // INITIATIVE METHOD DEFINITIONS
    // =========================================================================

    @Test
    fun `initiative methods are defined`() {
        val methods = InitiativeMethod.entries
        assertTrue(methods.contains(InitiativeMethod.AGILITY_ONLY))
        assertTrue(methods.contains(InitiativeMethod.AGILITY_PLUS_RANDOM))
        assertTrue(methods.contains(InitiativeMethod.SPEED_TIERS))
        assertTrue(methods.contains(InitiativeMethod.PARTY_FIRST))
        assertTrue(methods.contains(InitiativeMethod.ENEMIES_FIRST))
        assertTrue(methods.contains(InitiativeMethod.ALTERNATING))
        assertEquals(6, methods.size)
    }

    // =========================================================================
    // SPEED TIER DEFINITIONS
    // =========================================================================

    @Test
    fun `speed tiers have correct priorities`() {
        assertEquals(0, SpeedTier.INSTANT.priority)
        assertEquals(1, SpeedTier.FAST.priority)
        assertEquals(2, SpeedTier.NORMAL.priority)
        assertEquals(3, SpeedTier.SLOW.priority)
    }

    @Test
    fun `all 4 speed tiers are defined`() {
        assertEquals(4, SpeedTier.entries.size)
    }

    // =========================================================================
    // TURN ORDER BUILDER
    // =========================================================================

    @Test
    fun `turnOrder creates system with default values`() {
        val system = turnOrder {}

        assertEquals(InitiativeMethod.AGILITY_PLUS_RANDOM, system.method)
        assertEquals(10, system.randomVariance)
        assertEquals(8, system.maxCombatants)
    }

    @Test
    fun `turnOrder can configure method`() {
        val system = turnOrder { method(InitiativeMethod.PARTY_FIRST) }

        assertEquals(InitiativeMethod.PARTY_FIRST, system.method)
    }

    @Test
    fun `turnOrder can configure random variance`() {
        val system = turnOrder { randomVariance(25) }

        assertEquals(25, system.randomVariance)
    }

    @Test
    fun `turnOrder can configure max combatants`() {
        val system = turnOrder { maxCombatants(12) }

        assertEquals(12, system.maxCombatants)
    }

    @Test
    fun `turnOrder with all options configured`() {
        val system = turnOrder {
            method(InitiativeMethod.ALTERNATING)
            randomVariance(0)
            maxCombatants(6)
        }

        assertEquals(InitiativeMethod.ALTERNATING, system.method)
        assertEquals(0, system.randomVariance)
        assertEquals(6, system.maxCombatants)
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerTurnOrder emits IR`() {
        val game =
            gbGame("RegisterTurnOrderIRTest") {
                val system = turnOrder {}

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasConfigIR = scene.onEnter.any { stmt -> stmt is IRTurnOrderConfig }
        assertTrue(hasConfigIR, "Should emit IRTurnOrderConfig")
    }

    @Test
    fun `calculateTurnOrder emits IR`() {
        val game =
            gbGame("CalculateTurnOrderIRTest") {
                start = scene("main") { every.frame { calculateTurnOrder() } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRCalculateInitiative }
        assertTrue(hasIR, "Should emit IRCalculateInitiative")
    }

    @Test
    fun `sortTurnOrder emits IR`() {
        val game =
            gbGame("SortTurnOrderIRTest") {
                start = scene("main") { every.frame { sortTurnOrder() } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRSortTurnOrder }
        assertTrue(hasIR, "Should emit IRSortTurnOrder")
    }

    @Test
    fun `resetTurnOrder emits IR`() {
        val game =
            gbGame("ResetTurnOrderIRTest") {
                start = scene("main") { every.frame { resetTurnOrder() } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRResetTurnOrder }
        assertTrue(hasIR, "Should emit IRResetTurnOrder")
    }

    @Test
    fun `nextTurn emits IR`() {
        val game = gbGame("NextTurnIRTest") { start = scene("main") { every.frame { nextTurn() } } }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRNextTurn }
        assertTrue(hasIR, "Should emit IRNextTurn")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `turn order system generates method constant`() {
        val game =
            gbGame("TurnOrderCodegenMethodTest") {
                val system = turnOrder { method(InitiativeMethod.AGILITY_PLUS_RANDOM) }

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("TURN_METHOD_AGILITY_PLUS_RANDOM"),
            "Should generate method constant",
        )
        assertTrue(code.contains("_turn_method"), "Should generate method variable")
    }

    @Test
    fun `turn order system generates variables`() {
        val game =
            gbGame("TurnOrderCodegenVarsTest") {
                val system = turnOrder {}

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_turn_order["), "Should generate turn order array")
        assertTrue(code.contains("_turn_initiative["), "Should generate initiative array")
        assertTrue(code.contains("_turn_is_party["), "Should generate is_party array")
        assertTrue(code.contains("_turn_order_count"), "Should generate order count")
        assertTrue(code.contains("_turn_current_index"), "Should generate current index")
        assertTrue(code.contains("_turn_count"), "Should generate turn count")
        assertTrue(code.contains("_round_number"), "Should generate round number")
    }

    @Test
    fun `turn order system generates calculate initiative function`() {
        val game =
            gbGame("TurnOrderCodegenCalcTest") {
                val system = turnOrder {}

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_turn_calculate_initiative"),
            "Should generate calculate function",
        )
    }

    @Test
    fun `turn order system generates sort function`() {
        val game =
            gbGame("TurnOrderCodegenSortTest") {
                val system = turnOrder {}

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_turn_sort_order"), "Should generate sort function")
    }

    @Test
    fun `turn order with party first method generates correct code`() {
        val game =
            gbGame("TurnOrderPartyFirstTest") {
                val system = turnOrder { method(InitiativeMethod.PARTY_FIRST) }

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("TURN_METHOD_PARTY_FIRST"), "Should have PARTY_FIRST constant")
        assertTrue(
            code.contains("_turn_is_party[i] ? 200u : 100u"),
            "Should set party initiative to 200",
        )
    }

    @Test
    fun `turn order with alternating method generates interleave code`() {
        val game =
            gbGame("TurnOrderAlternatingTest") {
                val system = turnOrder { method(InitiativeMethod.ALTERNATING) }

                start =
                    scene("main") {
                        enter { registerTurnOrder(system) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("TURN_METHOD_ALTERNATING"), "Should have ALTERNATING constant")
        assertTrue(code.contains("interleave party and enemies"), "Should have alternating comment")
    }

    // =========================================================================
    // COMBATANT DATA CLASS
    // =========================================================================

    @Test
    fun `Combatant data class stores correct values`() {
        val combatant = Combatant(name = "hero", isPartyMember = true, index = 0)

        assertEquals("hero", combatant.name)
        assertTrue(combatant.isPartyMember)
        assertEquals(0, combatant.index)
    }

    @Test
    fun `Combatant can represent enemy`() {
        val enemy = Combatant(name = "goblin", isPartyMember = false, index = 4)

        assertEquals("goblin", enemy.name)
        assertEquals(false, enemy.isPartyMember)
        assertEquals(4, enemy.index)
    }

    // =========================================================================
    // INITIATIVE MODIFIER
    // =========================================================================

    @Test
    fun `InitiativeModifier has correct defaults`() {
        val modifier = InitiativeModifier(targetName = "hero", modifier = 10)

        assertEquals("hero", modifier.targetName)
        assertEquals(10, modifier.modifier)
        assertEquals(1, modifier.duration)
    }

    @Test
    fun `InitiativeModifier can set custom duration`() {
        val modifier = InitiativeModifier(targetName = "hero", modifier = -20, duration = 3)

        assertEquals(-20, modifier.modifier)
        assertEquals(3, modifier.duration)
    }

    // =========================================================================
    // INITIATIVE BONUS CONSTANTS
    // =========================================================================

    @Test
    fun `InitiativeBonus constants are defined`() {
        assertEquals(50, InitiativeBonus.HASTE)
        assertEquals(-50, InitiativeBonus.SLOW)
        assertEquals(100, InitiativeBonus.SURPRISE)
        assertEquals(-100, InitiativeBonus.AMBUSHED)
        assertEquals(-10, InitiativeBonus.HEAVY_ARMOR)
        assertEquals(5, InitiativeBonus.LIGHT_ARMOR)
    }
}
