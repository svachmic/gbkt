/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolDestroyAll
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PoolGetActiveCount
import io.github.gbkt.core.ir.PoolInstanceProperty
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// ACTOR POOL OPERATIONS TESTS
// Verifies: forEachActive, activeCount, destroyAll, per-instance properties,
// death callbacks, and pool_id_active_count() helper function codegen.
// =============================================================================

class ActorPoolOperationsTest {

    // =========================================================================
    // forEachActive — for-loop with active bitmap guard
    // =========================================================================

    @Test
    fun `visitPoolForEachActive generates for-loop with active guard`() {
        val op =
            PoolForEachActive(
                poolId = "bullets",
                maxSize = 8,
                slotVarName = "slot",
                body = emptyList(),
            )

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("_pool_bullets_active"),
            "forEachActive should reference active bitmap, got:\n$emitted",
        )
        assertTrue(emitted.contains("for"), "forEachActive should emit a for-loop, got:\n$emitted")
        assertTrue(emitted.contains("if"), "forEachActive should emit an if-guard, got:\n$emitted")
    }

    @Test
    fun `visitPoolForEachActive uses correct slot variable name`() {
        val op =
            PoolForEachActive(
                poolId = "bricks",
                maxSize = 30,
                slotVarName = "i",
                body = emptyList(),
            )

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains(" _i;") || emitted.contains(" _i ="),
            "forEachActive should declare sanitized slot variable '_i', got:\n$emitted",
        )
        assertTrue(
            emitted.contains("< 30"),
            "forEachActive should use maxSize (30) as loop upper bound, got:\n$emitted",
        )
    }

    @Test
    fun `visitPoolForEachActive iterates up to pool maxSize`() {
        val op =
            PoolForEachActive(
                poolId = "sparks",
                maxSize = 16,
                slotVarName = "slot",
                body = emptyList(),
            )

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("< 16"),
            "forEachActive loop bound should equal maxSize (16), got:\n$emitted",
        )
    }

    // =========================================================================
    // activeCount — pool_id_active_count() expression
    // =========================================================================

    @Test
    fun `PoolGetActiveCount emits pool active_count function call`() {
        // Use Assign op to get the expression emitted in a statement context
        val op = Assign("remainingCount", PoolGetActiveCount("bullets"), AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("pool_bullets_active_count()"),
            "PoolGetActiveCount should emit pool_bullets_active_count() function call, got:\n$emitted",
        )
    }

    @Test
    fun `buildActorPoolFunctions generates pool active_count function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val names = functions.map { it.name }

        assertTrue(
            names.contains("pool_bullets_active_count"),
            "buildActorPoolFunctions should generate pool_bullets_active_count, got: $names",
        )
    }

    @Test
    fun `pool active_count function counts active slots`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 4),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val countFn = functions.first { it.name == "pool_bullets_active_count" }
        val emitted = countFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertTrue(
            emitted.contains("_pool_bullets_active"),
            "active_count function should reference active bitmap, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("count"),
            "active_count function should use a count variable, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("return"),
            "active_count function should return the count, got:\n$emitted",
        )
    }

    // =========================================================================
    // destroyAll — bulk-clear all active pool slots
    // =========================================================================

    @Test
    fun `visitPoolDestroyAll generates for-loop clearing active bitmap`() {
        val op = PoolDestroyAll(poolId = "bullets", maxSize = 8)

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("_pool_bullets_active"),
            "destroyAll should reference active bitmap, got:\n$emitted",
        )
        assertTrue(emitted.contains("for"), "destroyAll should emit a for-loop, got:\n$emitted")
        assertTrue(emitted.contains("= 0"), "destroyAll should zero active entries, got:\n$emitted")
    }

    @Test
    fun `visitPoolDestroyAll hides all sprites via move_sprite`() {
        val op = PoolDestroyAll(poolId = "sparks", maxSize = 16)

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("move_sprite"),
            "destroyAll should call move_sprite to hide sprites, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_sparks_oam_base"),
            "destroyAll should use oam_base for sprite index, got:\n$emitted",
        )
    }

    @Test
    fun `visitPoolDestroyAll uses correct maxSize as loop bound`() {
        val op = PoolDestroyAll(poolId = "enemies", maxSize = 6)

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("< 6"),
            "destroyAll loop bound should equal maxSize (6), got:\n$emitted",
        )
    }

    // =========================================================================
    // Per-instance properties — parallel arrays in state vars
    // =========================================================================

    @Test
    fun `buildActorPoolStateVars generates U8 parallel array for u8 instance property`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                            instanceProperties =
                                listOf(PoolInstanceProperty(name = "hp", type = VarType.U8)),
                        )
                    ),
            )

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_bullets_hp"),
            "Per-instance U8 property should generate _pool_bullets_hp array, got: $names",
        )
    }

    @Test
    fun `buildActorPoolStateVars generates I8 parallel array for i8 instance property`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "enemies",
                            actorTemplateId = "enemy",
                            config = ActorPoolConfig(maxSize = 4),
                            instanceProperties =
                                listOf(PoolInstanceProperty(name = "dx", type = VarType.I8)),
                        )
                    ),
            )

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_enemies_dx"),
            "Per-instance I8 property should generate _pool_enemies_dx array, got: $names",
        )
    }

    @Test
    fun `buildActorPoolStateVars generates arrays for multiple instance properties`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bricks",
                            actorTemplateId = "brick",
                            config = ActorPoolConfig(maxSize = 30),
                            instanceProperties =
                                listOf(
                                    PoolInstanceProperty(name = "hp", type = VarType.U8),
                                    PoolInstanceProperty(name = "type", type = VarType.U8),
                                ),
                        )
                    ),
            )

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_bricks_hp"),
            "Should generate _pool_bricks_hp, got: $names",
        )
        assertTrue(
            names.contains("_pool_bricks_type"),
            "Should generate _pool_bricks_type, got: $names",
        )
        // Also still has active bitmap and oam_base
        assertTrue(names.contains("_pool_bricks_active"))
        assertTrue(names.contains("_pool_bricks_oam_base"))
    }

    @Test
    fun `buildActorPoolStateVars with no instance properties generates only active and oam_base`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "sparks",
                            actorTemplateId = "spark",
                            config = ActorPoolConfig(maxSize = 8),
                            instanceProperties = emptyList(),
                        )
                    ),
            )

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)

        assertEquals(2, vars.size, "No instance properties should produce only active + oam_base")
    }

    // =========================================================================
    // Death callbacks — emitted before slot release in destroy
    // =========================================================================

    @Test
    fun `visitPoolDestroyActor with deathCallbackOps emits callback before destroy call`() {
        val callbackOp = PlaySound("boom")
        val op =
            PoolDestroyActor(
                poolId = "bullets",
                slotExpr = VarRef("slot"),
                deathCallbackOps = listOf(callbackOp),
            )

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("play_sound"),
            "Death callback should emit play_sound before destroy, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("pool_bullets_destroy"),
            "Destroy call should still appear after callback, got:\n$emitted",
        )

        // Verify callback appears BEFORE destroy call in the output
        val soundIdx = emitted.indexOf("play_sound")
        val destroyIdx = emitted.indexOf("pool_bullets_destroy")
        assertTrue(
            soundIdx < destroyIdx,
            "Death callback (play_sound) should appear before pool_bullets_destroy, got:\n$emitted",
        )
    }

    @Test
    fun `visitPoolDestroyActor without deathCallbackOps emits simple destroy call`() {
        val op =
            PoolDestroyActor(
                poolId = "sparks",
                slotExpr = VarRef("i"),
                deathCallbackOps = emptyList(),
            )

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertEquals(
            "pool_sparks_destroy(_i);",
            emitted.trim(),
            "No death callback should produce simple destroy call",
        )
    }

    @Test
    fun `buildActorPoolFunctions generates death callback in pool destroy function`() {
        val callbackOp = PlaySound("boom")
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                actorPools =
                    listOf(
                        ActorPoolIR(
                            id = "bullets",
                            actorTemplateId = "bullet",
                            config = ActorPoolConfig(maxSize = 8),
                            deathCallback = listOf(callbackOp),
                        )
                    ),
            )

        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val destroyFn = functions.first { it.name == "pool_bullets_destroy" }
        val emitted = destroyFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertTrue(
            emitted.contains("play_sound"),
            "Destroy function with deathCallback should emit play_sound, got:\n$emitted",
        )

        // Callback appears before active flag clear
        val soundIdx = emitted.indexOf("play_sound")
        val activeIdx = emitted.indexOf("_pool_bullets_active")
        assertTrue(
            soundIdx < activeIdx,
            "Death callback should appear before active flag clear, got:\n$emitted",
        )
    }
}
