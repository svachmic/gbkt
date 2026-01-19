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
 * Tests for the target selection system.
 *
 * Validates:
 * - Target selection configuration
 * - IR generation
 * - Code generation
 */
class TargetSelectionTest {

    // =========================================================================
    // TARGET SELECTION BUILDER
    // =========================================================================

    @Test
    fun `targetSelection creates system with default values`() {
        val targeting = targetSelection("test") {}

        assertEquals("test", targeting.config.name)
        assertEquals(8, targeting.config.maxTargets)
        assertEquals('>', targeting.config.cursorChar)
        assertTrue(targeting.config.showTargetName)
        assertTrue(targeting.config.showTargetHP)
    }

    @Test
    fun `targetSelection can configure max targets`() {
        val targeting = targetSelection("test") { maxTargets(4) }

        assertEquals(4, targeting.config.maxTargets)
    }

    @Test
    fun `targetSelection can configure cursor`() {
        val targeting = targetSelection("test") { cursor('*') }

        assertEquals('*', targeting.config.cursorChar)
    }

    @Test
    fun `targetSelection can configure show info`() {
        val targeting = targetSelection("test") { showInfo(name = false, hp = false) }

        assertEquals(false, targeting.config.showTargetName)
        assertEquals(false, targeting.config.showTargetHP)
    }

    @Test
    fun `targetSelection can configure multi select`() {
        val targeting = targetSelection("test") { allowMultiSelect(true) }

        assertEquals(true, targeting.config.allowMultiSelect)
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerTargetSelection emits IR`() {
        val game =
            gbGame("RegisterTargetSelectionIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame {}
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasConfigIR = scene.onEnter.any { stmt -> stmt is IRTargetSelectionConfig }
        assertTrue(hasConfigIR, "Should emit IRTargetSelectionConfig")
    }

    @Test
    fun `selectEnemy emits IR`() {
        val game =
            gbGame("SelectEnemyIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame { targeting.selectEnemy() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRStartTargetSelection && stmt.mode == TargetingMode.SINGLE_ENEMY
            }
        assertTrue(hasIR, "Should emit IRStartTargetSelection with SINGLE_ENEMY mode")
    }

    @Test
    fun `selectAllEnemies emits IR`() {
        val game =
            gbGame("SelectAllEnemiesIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame { targeting.selectAllEnemies() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRStartTargetSelection && stmt.mode == TargetingMode.ALL_ENEMIES
            }
        assertTrue(hasIR, "Should emit IRStartTargetSelection with ALL_ENEMIES mode")
    }

    @Test
    fun `selectAlly emits IR`() {
        val game =
            gbGame("SelectAllyIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame { targeting.selectAlly() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR =
            scene.onFrame.any { stmt ->
                stmt is IRStartTargetSelection && stmt.mode == TargetingMode.SINGLE_ALLY
            }
        assertTrue(hasIR, "Should emit IRStartTargetSelection with SINGLE_ALLY mode")
    }

    @Test
    fun `tick emits IR`() {
        val game =
            gbGame("TargetSelectionTickIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame { targeting.tick() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRTargetSelectionTick }
        assertTrue(hasIR, "Should emit IRTargetSelectionTick")
    }

    @Test
    fun `confirm emits IR`() {
        val game =
            gbGame("TargetSelectionConfirmIRTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame { targeting.confirm() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasIR = scene.onFrame.any { stmt -> stmt is IRConfirmTarget }
        assertTrue(hasIR, "Should emit IRConfirmTarget")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `target selection generates mode constants`() {
        val game =
            gbGame("TargetSelectionCodegenConstantsTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("TARGET_MODE_SINGLE_ENEMY"),
            "Should generate SINGLE_ENEMY constant",
        )
        assertTrue(code.contains("TARGET_MODE_ALL_ENEMIES"), "Should generate ALL_ENEMIES constant")
        assertTrue(code.contains("TARGET_MODE_SINGLE_ALLY"), "Should generate SINGLE_ALLY constant")
        assertTrue(code.contains("TARGET_MODE_SELF"), "Should generate SELF constant")
    }

    @Test
    fun `target selection generates state variables`() {
        val game =
            gbGame("TargetSelectionCodegenVarsTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_target_battle_active"), "Should generate active var")
        assertTrue(code.contains("_target_battle_mode"), "Should generate mode var")
        assertTrue(code.contains("_target_battle_cursor"), "Should generate cursor var")
        assertTrue(code.contains("_target_battle_count"), "Should generate count var")
    }

    @Test
    fun `target selection generates helper functions`() {
        val game =
            gbGame("TargetSelectionCodegenFunctionsTest") {
                val targeting = targetSelection("battle") {}

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_target_battle_build_valid"),
            "Should generate build_valid function",
        )
        assertTrue(code.contains("_target_battle_start"), "Should generate start function")
        assertTrue(code.contains("_target_battle_move"), "Should generate move function")
        assertTrue(code.contains("_target_battle_toggle"), "Should generate toggle function")
        assertTrue(code.contains("_target_battle_confirm"), "Should generate confirm function")
        assertTrue(code.contains("_target_battle_tick"), "Should generate tick function")
    }

    @Test
    fun `target selection with max targets generates correct constant`() {
        val game =
            gbGame("TargetSelectionMaxTargetsTest") {
                val targeting = targetSelection("battle") { maxTargets(12) }

                start =
                    scene("main") {
                        enter { registerTargetSelection(targeting) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BATTLE_MAX_TARGETS 12u"),
            "Should have correct max targets constant",
        )
    }

    // =========================================================================
    // SELECTED TARGET DATA CLASS
    // =========================================================================

    @Test
    fun `SelectedTarget stores correct values`() {
        val target = SelectedTarget(index = 2, isEnemy = true, isAlive = true)

        assertEquals(2, target.index)
        assertTrue(target.isEnemy)
        assertTrue(target.isAlive)
    }

    // =========================================================================
    // TARGET SELECTION CONFIG DATA CLASS
    // =========================================================================

    @Test
    fun `TargetSelectionConfig has correct defaults`() {
        val config = TargetSelectionConfig(name = "test")

        assertEquals("test", config.name)
        assertEquals(8, config.maxTargets)
        assertEquals('>', config.cursorChar)
        assertTrue(config.showTargetName)
        assertTrue(config.showTargetHP)
        assertEquals(false, config.allowMultiSelect)
    }
}
