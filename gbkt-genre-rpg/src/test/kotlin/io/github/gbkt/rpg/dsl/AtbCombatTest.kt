/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.AtbGaugeModel
import io.github.gbkt.core.ir.AtbWaitMode
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.TurnOrderStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// ATB COMBAT DSL BUILDER TESTS (Plan 06.5-05 success criterion SC-6, SC-9)
// 4 tests covering AtbCombatBuilder + atbCombat() extension:
//   - atbCombat produces CombatEngineSystem with ATB type
//   - ATB builder sets gauge model and wait mode
//   - ATB builder configures player toggle option
//   - ATB combat inherits all CombatEngineSystem fields (combatants, victory, defeat)
// =============================================================================

class AtbCombatTest {

    // =========================================================================
    // Test 1: atbCombat produces CombatEngineSystem with ATB type
    // =========================================================================

    @Test
    fun `atbCombat produces CombatEngineSystem with ATB type`() {
        var capturedSystem: CombatEngineSystem? = null

        game("AtbTest") {
                atbCombat("mybattle") {
                    // minimal config
                }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<CombatEngineSystem>().firstOrNull {
                        it.id == "mybattle"
                    }
            }

        assertNotNull(capturedSystem, "Expected CombatEngineSystem with id 'mybattle'")
        assertEquals(CombatType.ATB, capturedSystem!!.combatType)
    }

    // =========================================================================
    // Test 2: ATB builder sets gauge model and wait mode correctly
    // =========================================================================

    @Test
    fun `ATB builder sets gauge model and wait mode`() {
        var capturedSystem: CombatEngineSystem? = null

        game("AtbTest") {
                atbCombat("atb1") {
                    gaugeModel(AtbGaugeModel.CHARGE)
                    waitMode(AtbWaitMode.ACTIVE)
                    fillRate(6)
                    maxGauge(200)
                }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<CombatEngineSystem>().firstOrNull {
                        it.id == "atb1"
                    }
            }

        assertNotNull(capturedSystem)
        val atbConfig = capturedSystem!!.atbConfig
        assertNotNull(atbConfig, "Expected atbConfig to be non-null for ATB system")
        assertEquals(AtbGaugeModel.CHARGE, atbConfig!!.gaugeModel)
        assertEquals(AtbWaitMode.ACTIVE, atbConfig.waitMode)
        assertEquals(6, atbConfig.baseGaugeFillRate)
        assertEquals(200, atbConfig.maxGauge)
    }

    // =========================================================================
    // Test 3: ATB builder configures player toggle option
    // =========================================================================

    @Test
    fun `ATB builder configures player toggle option`() {
        var capturedSystem: CombatEngineSystem? = null

        game("AtbTest") {
                atbCombat("atb2") { allowPlayerToggle() }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<CombatEngineSystem>().firstOrNull {
                        it.id == "atb2"
                    }
            }

        assertNotNull(capturedSystem)
        val atbConfig = capturedSystem!!.atbConfig
        assertNotNull(atbConfig)
        assertTrue(atbConfig!!.allowPlayerToggle, "Expected allowPlayerToggle to be true")
    }

    // =========================================================================
    // Test 4: ATB combat inherits CombatEngineSystem fields
    // =========================================================================

    @Test
    fun `ATB combat inherits CombatEngineSystem fields`() {
        var capturedSystem: CombatEngineSystem? = null

        game("AtbTest") {
                atbCombat("atb3") {
                    turnOrder(TurnOrderStrategy.SPEED_BASED)
                    maxCombatants(6)
                    onVictory { navigate(SceneRef("win")) }
                    onDefeat { navigate(SceneRef("gameover")) }
                }
                val startSceneRef = scene("start") { enter {} }
                scene("win") { enter {} }
                scene("gameover") { enter {} }
                start = startSceneRef
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<CombatEngineSystem>().firstOrNull {
                        it.id == "atb3"
                    }
            }

        assertNotNull(capturedSystem)
        assertEquals(TurnOrderStrategy.SPEED_BASED, capturedSystem!!.turnOrderStrategy)
        assertEquals(6, capturedSystem!!.maxCombatants)
        assertTrue(
            capturedSystem!!.onVictoryOps.isNotEmpty(),
            "Expected onVictoryOps to be non-empty",
        )
        assertTrue(
            capturedSystem!!.onDefeatOps.isNotEmpty(),
            "Expected onDefeatOps to be non-empty",
        )
    }

    // =========================================================================
    // Test 5: ATB builder activeMode() shortcut sets ACTIVE wait mode
    // =========================================================================

    @Test
    fun `ATB builder activeMode shortcut sets ACTIVE wait mode`() {
        var capturedSystem: CombatEngineSystem? = null

        game("AtbTest") {
                atbCombat("atb4") { activeMode() }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()
            .also { gameIR ->
                capturedSystem =
                    gameIR.systems.filterIsInstance<CombatEngineSystem>().firstOrNull {
                        it.id == "atb4"
                    }
            }

        assertNotNull(capturedSystem)
        val atbConfig = capturedSystem!!.atbConfig
        assertNotNull(atbConfig)
        assertEquals(
            AtbWaitMode.ACTIVE,
            atbConfig!!.waitMode,
            "Expected activeMode() to set AtbWaitMode.ACTIVE",
        )
    }
}
