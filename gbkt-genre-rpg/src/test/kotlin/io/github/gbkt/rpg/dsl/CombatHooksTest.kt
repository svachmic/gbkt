/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatHookPoint
import io.github.gbkt.core.ir.NavigateTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// COMBAT HOOKS TESTS (Plan 06.5-10 Task 1 success criterion)
// 5 tests covering DSL builder and IR wiring:
//   - CombatHookBuilder records ops per hook point
//   - beforeAction hook stores ops under BEFORE_ACTION key
//   - Multiple hooks on same point are concatenated
//   - Empty hooks produce empty map (backward-compatible)
//   - hooks() DSL extension sets combatHooks on CombatEngineSystem
// =============================================================================

class CombatHooksTest {

    // =========================================================================
    // Test 1: CombatHookBuilder records ops per hook point
    // =========================================================================

    @Test
    fun `CombatHookBuilder records ops per hook point`() {
        val builder = CombatHookBuilder()
        builder.beforeAction { navigate("flash") }
        builder.afterDamage { navigate("damage") }
        builder.onVictory { navigate("win") }

        val hooks = builder.build()
        assertTrue(
            hooks.containsKey(CombatHookPoint.BEFORE_ACTION),
            "BEFORE_ACTION must be present",
        )
        assertTrue(hooks.containsKey(CombatHookPoint.AFTER_DAMAGE), "AFTER_DAMAGE must be present")
        assertTrue(hooks.containsKey(CombatHookPoint.ON_VICTORY), "ON_VICTORY must be present")
    }

    // =========================================================================
    // Test 2: beforeAction hook stores ops under BEFORE_ACTION key
    // =========================================================================

    @Test
    fun `beforeAction hook stores ops under BEFORE_ACTION key`() {
        val builder = CombatHookBuilder()
        builder.beforeAction { navigate("before_action_scene") }

        val hooks = builder.build()
        val ops = hooks[CombatHookPoint.BEFORE_ACTION]
        assertNotNull(ops, "BEFORE_ACTION key must be present in hooks map")
        assertTrue(ops.isNotEmpty(), "BEFORE_ACTION ops must be non-empty")
        assertTrue(ops.any { it is NavigateTo }, "navigate() must produce NavigateTo op")
        assertEquals("before_action_scene", (ops.first() as NavigateTo).sceneId)
    }

    // =========================================================================
    // Test 3: Multiple hooks on same point are concatenated
    // =========================================================================

    @Test
    fun `multiple hooks on same point are concatenated`() {
        val builder = CombatHookBuilder()
        builder.beforeAction { navigate("scene1") }
        builder.beforeAction { navigate("scene2") }

        val hooks = builder.build()
        val ops = hooks[CombatHookPoint.BEFORE_ACTION]
        assertNotNull(ops)
        assertEquals(2, ops.size, "Two beforeAction calls must produce 2 ops (concatenated)")
        val targets = ops.filterIsInstance<NavigateTo>().map { it.sceneId }
        assertTrue(targets.contains("scene1"), "First navigate target must be in ops")
        assertTrue(targets.contains("scene2"), "Second navigate target must be in ops")
    }

    // =========================================================================
    // Test 4: Empty hooks produce empty map (backward-compatible)
    // =========================================================================

    @Test
    fun `empty hooks produce empty map`() {
        val builder = CombatHookBuilder()
        val hooks = builder.build()
        assertTrue(hooks.isEmpty(), "Empty CombatHookBuilder must produce empty map")
    }

    // =========================================================================
    // Test 5: hooks() DSL extension sets combatHooks on CombatEngineSystem
    // =========================================================================

    @Test
    fun `hooks DSL extension sets combatHooks on CombatEngineSystem`() {
        val ir =
            game("HooksTest") {
                    combatEngine("combat") {
                        hooks {
                            beforeAction { navigate("pre_action") }
                            afterTurn { navigate("post_turn") }
                            onVictory { navigate("extra_victory") }
                        }
                    }
                    scene("pre_action") { enter {} }
                    scene("post_turn") { enter {} }
                    scene("extra_victory") { enter {} }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.filterIsInstance<CombatEngineSystem>().firstOrNull()
        assertNotNull(system, "CombatEngineSystem must be registered")
        assertEquals("combat", system.id)

        val hooks = system.combatHooks
        assertTrue(hooks.containsKey(CombatHookPoint.BEFORE_ACTION), "BEFORE_ACTION must be set")
        assertTrue(hooks.containsKey(CombatHookPoint.AFTER_TURN), "AFTER_TURN must be set")
        assertTrue(hooks.containsKey(CombatHookPoint.ON_VICTORY), "ON_VICTORY must be set")

        val beforeActionOps = hooks[CombatHookPoint.BEFORE_ACTION]
        assertNotNull(beforeActionOps)
        assertTrue(beforeActionOps.isNotEmpty(), "BEFORE_ACTION must have ops")
    }

    // =========================================================================
    // Test 6: All 7 hook points can be registered
    // =========================================================================

    @Test
    fun `all 7 hook points can be registered`() {
        val builder = CombatHookBuilder()
        builder.beforeAction { navigate("a") }
        builder.afterAction { navigate("b") }
        builder.afterDamage { navigate("c") }
        builder.beforeTurn { navigate("d") }
        builder.afterTurn { navigate("e") }
        builder.onVictory { navigate("f") }
        builder.onDefeat { navigate("g") }

        val hooks = builder.build()
        assertEquals(7, hooks.size, "All 7 CombatHookPoints must be registered")
        for (point in CombatHookPoint.entries) {
            assertTrue(hooks.containsKey(point), "Hook point $point must be in map")
        }
    }
}
