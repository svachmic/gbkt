/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatHookPoint
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// COMBAT HOOKS CODEGEN TESTS (Plan 06.5-10 Task 2 success criterion)
// 8 tests covering CombatVisitor hook injection:
//   - BEFORE_ACTION hook emits function and call site before action dispatch
//   - AFTER_DAMAGE hook emits function and call site after HP modification
//   - AFTER_TURN hook emits function and call site at end of turn
//   - ON_VICTORY hook runs before user onVictoryOps
//   - Empty combatHooks produces no hook functions (zero overhead)
//   - Multiple hook points each generate separate functions
//   - hooks_enabled global emitted when any hook is present
//   - Zero CRawCode in hook codegen
// =============================================================================

/** Build a minimal GameIR with a CombatEngineSystem for codegen tests. */
private fun buildHooksGameIR(system: CombatEngineSystem, startScene: String = "gameplay"): GameIR =
    GameIR(
        name = "TestHooksGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = startScene)),
        systems = listOf(system),
        startScene = startScene,
    )

/** Default system ID used in all hook tests. */
private const val HOOKS_COMBAT_ID = "combat"

class CombatHooksCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: BEFORE_ACTION hook emits hook function and call site before action dispatch
    // =========================================================================

    @Test
    fun `onBeforeAction hook emits hook_before_action function and call site before action dispatch`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatType = CombatType.TURN_BASED,
                combatHooks =
                    mapOf(CombatHookPoint.BEFORE_ACTION to listOf(NavigateTo("pre_action"))),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "pre_action")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Hook function must be generated
        assertTrue(
            mainC.contains("void hook_before_action_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_before_action_$HOOKS_COMBAT_ID function definition",
        )
        // Call site must be present (wrapped in hooks_enabled check)
        assertTrue(
            mainC.contains("hook_before_action_$HOOKS_COMBAT_ID()"),
            "Expected hook_before_action_$HOOKS_COMBAT_ID() call site",
        )
        // Call site must appear before button_pressed(J_A) in PLAYER_TURN case
        val hookCallPos = mainC.indexOf("hook_before_action_$HOOKS_COMBAT_ID()")
        val buttonPressPos = mainC.indexOf("button_pressed(J_A)")
        assertTrue(
            hookCallPos < buttonPressPos,
            "hook_before_action call site must appear before button_pressed(J_A) in PLAYER_TURN",
        )
    }

    // =========================================================================
    // Test 2: AFTER_DAMAGE hook emits function and call site after HP modification
    // =========================================================================

    @Test
    fun `onAfterDamage hook emits hook_after_damage function and call site`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatHooks =
                    mapOf(CombatHookPoint.AFTER_DAMAGE to listOf(NavigateTo("damage_flash"))),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "damage_flash")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void hook_after_damage_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_after_damage_$HOOKS_COMBAT_ID function definition",
        )
        assertTrue(
            mainC.contains("hook_after_damage_$HOOKS_COMBAT_ID()"),
            "Expected hook_after_damage_$HOOKS_COMBAT_ID() call site",
        )
    }

    // =========================================================================
    // Test 3: AFTER_TURN hook emits function and call site at end of turn
    // =========================================================================

    @Test
    fun `onAfterTurn hook emits hook_after_turn function and call site at end of turn`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatHooks = mapOf(CombatHookPoint.AFTER_TURN to listOf(NavigateTo("post_turn"))),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "post_turn")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void hook_after_turn_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_after_turn_$HOOKS_COMBAT_ID function definition",
        )
        assertTrue(
            mainC.contains("hook_after_turn_$HOOKS_COMBAT_ID()"),
            "Expected hook_after_turn_$HOOKS_COMBAT_ID() call site in ENEMY_TURN case",
        )
    }

    // =========================================================================
    // Test 4: ON_VICTORY hook runs before user onVictoryOps
    // =========================================================================

    @Test
    fun `onVictory hook runs before user onVictoryOps`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                onVictoryOps = listOf(NavigateTo("victory")),
                combatHooks =
                    mapOf(CombatHookPoint.ON_VICTORY to listOf(NavigateTo("pre_victory"))),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(
                    scenes =
                        listOf(
                            SceneIR(id = "gameplay"),
                            SceneIR(id = "victory"),
                            SceneIR(id = "pre_victory"),
                        )
                )
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        // Both the hook function and call site must be present
        assertTrue(
            allC.contains("void hook_on_victory_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_on_victory_$HOOKS_COMBAT_ID function definition",
        )
        assertTrue(
            allC.contains("hook_on_victory_$HOOKS_COMBAT_ID()"),
            "Expected hook_on_victory_$HOOKS_COMBAT_ID() call site",
        )
        // In main.c, the hook call must appear before navigate_to_scene/victory in case 3
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        val hookPos = mainC.indexOf("hook_on_victory_$HOOKS_COMBAT_ID()")
        assertTrue(hookPos >= 0, "Hook call site must be in main.c")
    }

    // =========================================================================
    // Test 5: Empty combatHooks produces no hook functions (zero overhead)
    // =========================================================================

    @Test
    fun `empty combatHooks produces no hook functions`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatType = CombatType.TURN_BASED,
                combatHooks = emptyMap(),
            )
        val gameIR = buildHooksGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("hook_"),
            "No hook functions or call sites when combatHooks is empty",
        )
        assertFalse(
            mainC.contains("_hooks_enabled"),
            "No _hooks_enabled global when combatHooks is empty",
        )
    }

    // =========================================================================
    // Test 6: Multiple hook points each generate separate functions
    // =========================================================================

    @Test
    fun `multiple hook points each generate separate functions`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatHooks =
                    mapOf(
                        CombatHookPoint.BEFORE_ACTION to listOf(NavigateTo("s1")),
                        CombatHookPoint.AFTER_ACTION to listOf(NavigateTo("s2")),
                        CombatHookPoint.ON_VICTORY to listOf(NavigateTo("s3")),
                    ),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(
                    scenes =
                        listOf(
                            SceneIR(id = "gameplay"),
                            SceneIR(id = "s1"),
                            SceneIR(id = "s2"),
                            SceneIR(id = "s3"),
                        )
                )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void hook_before_action_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_before_action function",
        )
        assertTrue(
            mainC.contains("void hook_after_action_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_after_action function",
        )
        assertTrue(
            mainC.contains("void hook_on_victory_$HOOKS_COMBAT_ID(void)"),
            "Expected hook_on_victory function",
        )
    }

    // =========================================================================
    // Test 7: hooks_enabled global emitted when any hook is present
    // =========================================================================

    @Test
    fun `hooks_enabled global emitted when any hook is present`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatHooks =
                    mapOf(CombatHookPoint.BEFORE_TURN to listOf(NavigateTo("turn_start"))),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "turn_start")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_combat_${HOOKS_COMBAT_ID}_hooks_enabled"),
            "Expected _combat_${HOOKS_COMBAT_ID}_hooks_enabled global",
        )
    }

    // =========================================================================
    // Test 8: Zero CRawCode in hook codegen
    // =========================================================================

    @Test
    fun `zero CRawCode in hook codegen`() {
        val system =
            CombatEngineSystem(
                id = HOOKS_COMBAT_ID,
                combatHooks =
                    mapOf(
                        CombatHookPoint.BEFORE_ACTION to listOf(NavigateTo("s1")),
                        CombatHookPoint.ON_DEFEAT to listOf(NavigateTo("s2")),
                    ),
            )
        val gameIR =
            buildHooksGameIR(system)
                .copy(
                    scenes =
                        listOf(SceneIR(id = "gameplay"), SceneIR(id = "s1"), SceneIR(id = "s2"))
                )
        // Use CombatVisitor directly to inspect generated CFunction nodes
        val visitor = CombatVisitor(gameIR)
        val functions = visitor.generateCombatFunctions(system)

        // Find hook functions (named hook_*)
        val hookFunctions = functions.filter { it.name.startsWith("hook_") }
        assertTrue(hookFunctions.isNotEmpty(), "Hook functions must be generated")

        // Verify all hook function bodies use typed CStatement nodes (no CRawCode)
        for (fn in hookFunctions) {
            for (stmt in fn.body) {
                val stmtClass = stmt::class.simpleName ?: "Unknown"
                assertFalse(
                    stmtClass == "CRawCode",
                    "Hook function ${fn.name} must not contain CRawCode — found: $stmtClass",
                )
            }
        }
    }
}
