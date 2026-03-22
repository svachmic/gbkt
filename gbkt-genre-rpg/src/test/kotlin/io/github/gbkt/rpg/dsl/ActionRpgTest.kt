/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.ActionRpgConfig
import io.github.gbkt.rpg.domain.BehaviorPresetType
import io.github.gbkt.rpg.domain.CombatModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// ACTION RPG DSL BUILDER TESTS (Plan 06.8-04 Task 1)
// 5 tests covering ActionRpgBuilder + actionRpg() extension:
//   - ARPG builder produces GenericSystem with correct combat model
//   - Dodge roll config captured correctly
//   - Stamina config captured correctly (bridges to gauge infrastructure)
//   - ATB config captured correctly
//   - Behavior presets composable (multiple presets on same builder)
// =============================================================================

/** Helper to extract ActionRpgConfig from a GenericSystem produced by actionRpg(). */
private fun extractArpgConfig(system: GenericSystem): ActionRpgConfig? =
    system.config["config"] as? ActionRpgConfig

class ActionRpgTest {

    // =========================================================================
    // Test 1: ARPG builder produces GenericSystem with correct combat model
    // =========================================================================

    @Test
    fun `actionRpg produces GenericSystem with arpg_combat type and correct combat model`() {
        var capturedSystem: GenericSystem? = null

        game("ArpgTest") {
                actionRpg("combat") { combatModel(CombatModel.HYBRID_ATB) }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
            }

        assertNotNull(capturedSystem, "Expected GenericSystem with id 'combat'")
        assertEquals("arpg_combat", capturedSystem!!.config["type"])
        val config = extractArpgConfig(capturedSystem!!)
        assertNotNull(config, "Expected ActionRpgConfig in config['config']")
        assertEquals(CombatModel.HYBRID_ATB, config!!.model)
    }

    // =========================================================================
    // Test 2: Default combat model is REALTIME_COOLDOWN
    // =========================================================================

    @Test
    fun `actionRpg defaults to REALTIME_COOLDOWN combat model`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgDefaultTest") {
                actionRpg("combat") {
                    // no combatModel call — should default
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        assertEquals(CombatModel.REALTIME_COOLDOWN, capturedConfig!!.model)
    }

    // =========================================================================
    // Test 3: Dodge roll config captured correctly
    // =========================================================================

    @Test
    fun `actionRpg captures dodge roll config with iFrames and cooldown`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgDodgeTest") {
                actionRpg("combat") {
                    dodgeRoll {
                        iFrames(10)
                        cooldown(24)
                    }
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        val dodgeRoll = capturedConfig!!.dodgeRoll
        assertNotNull(dodgeRoll, "Expected DodgeRollConfig to be set")
        assertEquals(10, dodgeRoll!!.iFrameDuration)
        assertEquals(24, dodgeRoll.cooldownFrames)
    }

    // =========================================================================
    // Test 4: Stamina config bridges to gauge infrastructure
    // =========================================================================

    @Test
    fun `actionRpg captures stamina config that bridges to exploration gauge infrastructure`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgStaminaTest") {
                actionRpg("combat") {
                    stamina {
                        max(120)
                        regen(2)
                        attackCost(25)
                        dodgeCost(40)
                    }
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        val stamina = capturedConfig!!.staminaGauge
        assertNotNull(stamina, "Expected StaminaGaugeConfig to be set")
        // StaminaGaugeConfig bridges to ExplorationGaugeIR(id="stamina") at codegen time
        assertEquals(120, stamina!!.maxStamina, "maxStamina should map to ExplorationGaugeIR.max")
        assertEquals(2, stamina.regenRate)
        assertEquals(25, stamina.attackCost)
        assertEquals(40, stamina.dodgeCost)
    }

    // =========================================================================
    // Test 5: ATB config captured correctly
    // =========================================================================

    @Test
    fun `actionRpg captures ATB config with maxGauge and baseSpeed`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgAtbTest") {
                actionRpg("combat") {
                    combatModel(CombatModel.HYBRID_ATB)
                    atb {
                        maxGauge(200)
                        baseSpeed(3)
                    }
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        val atb = capturedConfig!!.atb
        assertNotNull(atb, "Expected AtbConfig to be set")
        assertEquals(200, atb!!.maxGauge)
        assertEquals(3, atb.baseSpeed)
    }

    // =========================================================================
    // Test 6: Behavior presets are composable (multiple presets on same builder)
    // =========================================================================

    @Test
    fun `actionRpg behavior presets are composable and all captured`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgBehaviorTest") {
                actionRpg("combat") {
                    behaviorPreset(BehaviorPresetType.CHASE, range = 5)
                    behaviorPreset(BehaviorPresetType.ATTACK_WHEN_CLOSE, range = 1)
                    behaviorPreset(BehaviorPresetType.FLEE, threshold = 25)
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        val presets = capturedConfig!!.behaviorPresets
        assertEquals(3, presets.size, "Expected 3 behavior presets")

        val chase = presets.firstOrNull { it.type == BehaviorPresetType.CHASE }
        assertNotNull(chase, "Expected CHASE preset")
        assertEquals(5, chase!!.range)

        val attack = presets.firstOrNull { it.type == BehaviorPresetType.ATTACK_WHEN_CLOSE }
        assertNotNull(attack, "Expected ATTACK_WHEN_CLOSE preset")
        assertEquals(1, attack!!.range)

        val flee = presets.firstOrNull { it.type == BehaviorPresetType.FLEE }
        assertNotNull(flee, "Expected FLEE preset")
        assertEquals(25, flee!!.threshold)
    }

    // =========================================================================
    // Test 7: No dodge roll by default (null)
    // =========================================================================

    @Test
    fun `actionRpg has no dodge roll when not configured`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgNoDodgeTest") {
                actionRpg("combat") {
                    // no dodgeRoll block
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        assertNull(capturedConfig!!.dodgeRoll, "Expected dodgeRoll to be null when not configured")
        assertNull(
            capturedConfig!!.staminaGauge,
            "Expected staminaGauge to be null when not configured",
        )
        assertNull(capturedConfig!!.atb, "Expected atb to be null when not configured")
        assertTrue(capturedConfig!!.behaviorPresets.isEmpty(), "Expected no behavior presets")
    }

    // =========================================================================
    // Test 8: Patrol preset captures waypoint path
    // =========================================================================

    @Test
    fun `actionRpg patrol preset captures waypoint path`() {
        var capturedConfig: ActionRpgConfig? = null

        game("ArpgPatrolTest") {
                actionRpg("combat") {
                    behaviorPreset(
                        BehaviorPresetType.PATROL,
                        path = listOf(Pair(1, 1), Pair(5, 1), Pair(5, 5), Pair(1, 5)),
                    )
                }
                scene("start") { enter {} }
                start = "start"
            }
            .build()
            .also { gameIR ->
                val system =
                    gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull {
                        it.id == "combat"
                    }
                capturedConfig = extractArpgConfig(system!!)
            }

        assertNotNull(capturedConfig)
        val patrol =
            capturedConfig!!.behaviorPresets.firstOrNull { it.type == BehaviorPresetType.PATROL }
        assertNotNull(patrol, "Expected PATROL preset")
        assertEquals(4, patrol!!.path.size, "Expected 4 patrol waypoints")
        assertEquals(Pair(1, 1), patrol.path[0])
        assertEquals(Pair(5, 5), patrol.path[2])
    }
}
