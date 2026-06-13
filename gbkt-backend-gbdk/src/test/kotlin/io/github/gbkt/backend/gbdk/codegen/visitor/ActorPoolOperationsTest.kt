/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolDestroyAll
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PoolGetActiveCount
import io.github.gbkt.core.ir.PoolInstanceProperty
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PropertyAccessExpr
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `visitPoolDestroyAll hides sprites via move_sprite not destroy_actor`() {
        // Static OAM assignment: destroyAll should call move_sprite(oam[i], 0, 0) to hide sprites,
        // NOT call destroy_actor (which was part of the dynamic free-list approach).
        // The OAM slot is permanent — no need to free it back to any free list.
        val op = PoolDestroyAll(poolId = "sparks", maxSize = 16)

        val result = ScriptOpVisitor.visit(op)
        val emitted = CEmitter.emitStatement(result)

        assertFalse(
            emitted.contains("destroy_actor"),
            "destroyAll should NOT call destroy_actor (static OAM, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("move_sprite"),
            "destroyAll should call move_sprite(oam[i], 0, 0) to hide sprites, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_sparks_oam"),
            "destroyAll should use _pool_sparks_oam[] per-instance array, got:\n$emitted",
        )
        assertFalse(
            emitted.contains("_pool_sparks_oam_base"),
            "destroyAll should NOT reference oam_base scalar (removed), got:\n$emitted",
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
        // Also still has active bitmap and per-instance OAM/position arrays
        assertTrue(names.contains("_pool_bricks_active"))
        assertTrue(names.contains("_pool_bricks_oam"))
        assertFalse(
            names.contains("_pool_bricks_oam_base"),
            "Should NOT generate oam_base scalar (replaced by per-instance oam array), got: $names",
        )
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

        assertEquals(
            4,
            vars.size,
            "No instance properties should produce active + x + y + oam arrays (4 total)",
        )
    }

    // =========================================================================
    // Bug 1: Per-instance position and OAM arrays (replacing oam_base scalar)
    // =========================================================================

    @Test
    fun `buildActorPoolStateVars generates per-instance x array`() {
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

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_bullets_x"),
            "Should generate per-instance _pool_bullets_x array, got: $names",
        )
    }

    @Test
    fun `buildActorPoolStateVars generates per-instance y array`() {
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

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_bullets_y"),
            "Should generate per-instance _pool_bullets_y array, got: $names",
        )
    }

    @Test
    fun `buildActorPoolStateVars generates per-instance oam array`() {
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

        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_bullets_oam"),
            "Should generate per-instance _pool_bullets_oam array (not oam_base scalar), got: $names",
        )
        assertTrue(
            !names.contains("_pool_bullets_oam_base"),
            "Should NOT generate _pool_bullets_oam_base scalar (replaced by per-instance array), got: $names",
        )
    }

    // =========================================================================
    // Static OAM assignment in spawn/destroy
    // (replaces former dynamic spawn_actor()/destroy_actor() free-list approach
    //  which caused OAM out-of-bounds writes for multi-tile sprites)
    // =========================================================================

    @Test
    fun `pool spawn function uses static oam slot from pool oam array`() {
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
        val spawnFn = functions.first { it.name == "pool_bullets_spawn" }
        val emitted = spawnFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertFalse(
            emitted.contains("spawn_actor"),
            "Pool spawn should NOT call spawn_actor (static OAM assignment, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bullets_oam["),
            "Pool spawn should use pre-initialized _pool_bullets_oam[] array for the slot, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bullets_x["),
            "Pool spawn should store x in _pool_bullets_x[] array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bullets_y["),
            "Pool spawn should store y in _pool_bullets_y[] array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("move_sprite"),
            "Pool spawn should call move_sprite to position the sprite on OAM hardware, got:\n$emitted",
        )
        assertFalse(
            emitted.contains("_pool_bullets_oam_base"),
            "Pool spawn should NOT use oam_base scalar, got:\n$emitted",
        )
    }

    @Test
    fun `pool destroy function hides sprite via move_sprite not destroy_actor`() {
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
        val destroyFn = functions.first { it.name == "pool_bullets_destroy" }
        val emitted = destroyFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertFalse(
            emitted.contains("destroy_actor"),
            "Pool destroy should NOT call destroy_actor (static OAM assignment, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("move_sprite"),
            "Pool destroy should call move_sprite(slot, 0, 0) to hide sprite off-screen, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bullets_oam["),
            "Pool destroy should read OAM slot from _pool_bullets_oam[] array, got:\n$emitted",
        )
        assertFalse(
            emitted.contains("0xFF"),
            "Pool destroy should NOT reset oam[i] to 0xFF (static OAM is permanent), got:\n$emitted",
        )
    }

    // =========================================================================
    // pool_init() pre-initializes OAM entries to static slot (not 0xFF sentinel)
    // (Static OAM assignment: oam[i] = oamBase + i * tilesPerEntity at init time)
    // =========================================================================

    @Test
    fun `pool init function sets oam entries to static slot assignment`() {
        // For a pool with no actors before it, oamBase = 0. With 1 tile per entity:
        // oam[0] = 0, oam[1] = 1, ..., oam[7] = 7.
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
        val initFn = functions.first { it.name == "pool_bullets_init" }
        val emitted = initFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        assertFalse(
            emitted.contains("0xFF"),
            "pool_bullets_init should NOT set OAM entries to 0xFF sentinel (static assignment), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bullets_oam"),
            "pool_bullets_init should initialize _pool_bullets_oam array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("* 1") ||
                emitted.contains("*1") ||
                emitted.contains("+ 0") ||
                emitted.contains("+0"),
            "pool_bullets_init should compute oamBase + i * tilesPerEntity, got:\n$emitted",
        )
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

    // =========================================================================
    // Bug 4: forEachActive body — actor property reads redirect to per-instance arrays
    // =========================================================================

    private fun buildBulletGameIR(): GameIR {
        val bulletTemplate =
            ActorIR(
                id = "bullet",
                position = PositionDef(0, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/bullet.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                        hitbox = HitboxDef(0, 0, 8, 8),
                    ),
            )
        val pool =
            ActorPoolIR(
                id = "bulletPool",
                actorTemplateId = "bullet",
                config = ActorPoolConfig(maxSize = 8),
            )
        return GameIR(
            name = "BulletTest",
            config = CartridgeConfig(),
            actors = listOf(bulletTemplate),
            actorPools = listOf(pool),
        )
    }

    @Test
    fun `forEachActive body Assign on template actor redirects to per-instance array`() {
        // bullet.y -= 4 inside forEachActive should emit _pool_bulletPool_y[_bi] -= 4u
        // (not _bullet_y -= 4u which references the template actor global)
        val gameIR = buildBulletGameIR()
        val bodyOp = Assign(target = "bullet.y", value = Literal(4), op = AssignOp.SUB)
        val op =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = listOf(bodyOp),
            )
        val exprVisitor = ExprVisitor(gameIR.actors)
        ScriptOpVisitor.setGameIR(gameIR)
        val result = ScriptOpVisitor.visit(op, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertFalse(
            emitted.contains("_bullet_y"),
            "forEachActive body should NOT reference template actor global _bullet_y, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_y["),
            "forEachActive body Assign should redirect to _pool_bulletPool_y[_bi], got:\n$emitted",
        )
    }

    @Test
    fun `forEachActive body condition on template actor property redirects to per-instance array`() {
        // runIf(bullet.y isBelow 4) inside forEachActive should emit _pool_bulletPool_y[_bi] <
        // 4u
        // (not _bullet_y < 4u)
        val gameIR = buildBulletGameIR()
        val condExpr =
            io.github.gbkt.core.ir.BinaryExpr(
                PropertyAccessExpr("bullet", "y"),
                io.github.gbkt.core.ir.BinaryOp.LT,
                Literal(4),
            )
        val ifOp = IfOp(condition = condExpr, then = emptyList())
        val op =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = listOf(ifOp),
            )
        val exprVisitor = ExprVisitor(gameIR.actors)
        ScriptOpVisitor.setGameIR(gameIR)
        val result = ScriptOpVisitor.visit(op, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertFalse(
            emitted.contains("_bullet_y"),
            "forEachActive condition should NOT reference template actor global _bullet_y, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_y["),
            "forEachActive condition should redirect to _pool_bulletPool_y[_bi], got:\n$emitted",
        )
    }

    @Test
    fun `forEachActive body property read on template actor redirects to per-instance array`() {
        // Reading bullet.x should emit _pool_bulletPool_x[_bi]
        val gameIR = buildBulletGameIR()
        // Use Assign: someVar = bullet.x → should produce someVar = _pool_bulletPool_x[_bi]
        val bodyOp =
            Assign(target = "temp", value = PropertyAccessExpr("bullet", "x"), op = AssignOp.SET)
        val op =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = listOf(bodyOp),
            )
        val exprVisitor = ExprVisitor(gameIR.actors)
        ScriptOpVisitor.setGameIR(gameIR)
        val result = ScriptOpVisitor.visit(op, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertFalse(
            emitted.contains("_bullet_x"),
            "forEachActive body should NOT reference template actor global _bullet_x, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_x["),
            "forEachActive body property read should redirect to _pool_bulletPool_x[_bi], got:\n$emitted",
        )
    }

    // =========================================================================
    // Bug 6: forEachActive display sync — move_sprite after body ops
    // =========================================================================

    @Test
    fun `forEachActive emits move_sprite display sync after body ops`() {
        // After body executes, move_sprite should be called to sync OAM position
        val gameIR = buildBulletGameIR()
        val op =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = emptyList(),
            )
        val exprVisitor = ExprVisitor(gameIR.actors)
        ScriptOpVisitor.setGameIR(gameIR)
        val result = ScriptOpVisitor.visit(op, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("move_sprite"),
            "forEachActive should emit move_sprite display sync after body ops, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_oam["),
            "move_sprite call should reference _pool_bulletPool_oam[_bi] array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_x["),
            "move_sprite call should use _pool_bulletPool_x[_bi] for x position, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_y["),
            "move_sprite call should use _pool_bulletPool_y[_bi] for y position, got:\n$emitted",
        )
    }

    @Test
    fun `pool context is cleared after forEachActive body compilation`() {
        // After forEachActive, subsequent ops should use normal template actor global vars
        val gameIR = buildBulletGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)

        // First compile a forEachActive op to set pool context
        val forEachOp =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = emptyList(),
            )
        ScriptOpVisitor.visit(forEachOp, exprVisitor)

        // Now compile a normal Assign — should use template actor globals, NOT pool arrays
        val normalOp = Assign(target = "bullet.y", value = Literal(10), op = AssignOp.SET)
        val normalResult = ScriptOpVisitor.visit(normalOp, exprVisitor)
        val emitted = CEmitter.emitStatement(normalResult)

        // Outside forEachActive, bullet.y should use the global (not pool array)
        // Because the pool context was cleared after forEachActive body compilation
        assertFalse(
            emitted.contains("_pool_bulletPool_y["),
            "After forEachActive, pool context should be cleared — normal ops use template globals, got:\n$emitted",
        )
    }

    // =========================================================================
    // Regression: OAM out-of-bounds write fix (shmup-073-ram-corruption)
    // Multi-tile pool sprites must not write past shadow_OAM[39]
    // =========================================================================

    private fun buildShmupLikeGameIR(): GameIR {
        // Mirrors the shmup: bullet (8x8=1 tile), enemy (16x16=4 tiles), player (16x16=4 tiles)
        val bulletTemplate =
            ActorIR(
                id = "bullet",
                position = PositionDef(0, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/bullet.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                        hitbox = HitboxDef(0, 0, 8, 8),
                    ),
            )
        val enemyTemplate =
            ActorIR(
                id = "enemy",
                position = PositionDef(80, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/enemy.png", AssetType.SPRITE),
                        size = SizeDef(16, 16),
                        hitbox = HitboxDef(0, 0, 16, 16),
                    ),
            )
        val player =
            ActorIR(
                id = "player",
                position = PositionDef(80, 120),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(16, 16),
                        hitbox = HitboxDef(0, 0, 16, 16),
                    ),
            )
        val bulletPool =
            ActorPoolIR(
                id = "bulletPool",
                actorTemplateId = "bullet",
                config = ActorPoolConfig(maxSize = 8),
            )
        val enemyPool =
            ActorPoolIR(
                id = "enemyPool",
                actorTemplateId = "enemy",
                config = ActorPoolConfig(maxSize = 4),
            )
        return GameIR(
            name = "Shmup",
            config = CartridgeConfig(),
            actors = listOf(bulletTemplate, enemyTemplate, player),
            actorPools = listOf(bulletPool, enemyPool),
        )
    }

    @Test
    fun `bullet pool oamBase starts after all actor tiles`() {
        // Actors: bullet (8x8=1 tile), enemy (16x16=4 tiles), player (16x16=4 tiles)
        // Static OAM layout: slots 0-0 reserved for bullet template, 1-4 for enemy template,
        // 5-8 for player. First pool (bulletPool) oamBase = 9.
        val gameIR = buildShmupLikeGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val initFn = functions.first { it.name == "pool_bulletPool_init" }
        val emitted = initFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        // oam[i] = 9 + i * 1  (oamBase=9, tilesPerEntity=1)
        // The emitted code should contain the literal 9 as the base
        assertTrue(
            emitted.contains("9"),
            "bulletPool init should use oamBase=9 (after 1+4+4 actor tiles), got:\n$emitted",
        )
    }

    @Test
    fun `enemy pool oamBase follows bullet pool without overlap`() {
        // bulletPool: max=8, 1 tile each → slots 9-16
        // enemyPool: max=4, 4 tiles each → starts at slot 17, uses slots 17-32
        // No OAM slot in this range exceeds 39, so no shadow_OAM out-of-bounds write.
        val gameIR = buildShmupLikeGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val initFn = functions.first { it.name == "pool_enemyPool_init" }
        val emitted = initFn.body.map { CEmitter.emitStatement(it) }.joinToString("\n")

        // oam[i] = 17 + i * 4  (oamBase=17, tilesPerEntity=4)
        assertTrue(
            emitted.contains("17"),
            "enemyPool init should use oamBase=17 (after bullet template + player + bulletPool), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("4"),
            "enemyPool init should use tilesPerEntity=4 (16x16 sprite = 2x2 tiles), got:\n$emitted",
        )
    }

    @Test
    fun `enemy pool oam slots stay within shadow_OAM bounds`() {
        // Maximum OAM slot used: oamBase + (maxSize-1) * tilesPerEntity + (tilesPerEntity-1)
        // = 17 + 3*4 + 3 = 17 + 12 + 3 = 32. Shadow_OAM has 40 entries (0-39). 32 <= 39. PASS.
        val gameIR = buildShmupLikeGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)

        // Compute expected OAM layout
        var poolOamBase = 0
        for (actor in gameIR.actors) {
            val sprite = actor.sprite ?: continue
            val tw = (sprite.size.width + 7) / 8
            val th = (sprite.size.height + 7) / 8
            poolOamBase += tw * th
        }
        // poolOamBase should now be 9 (1 + 4 + 4)
        assertEquals(
            9,
            poolOamBase,
            "Static actor OAM total should be 9 (bullet+enemy+player templates)",
        )

        var currentBase = poolOamBase
        for (pool in gameIR.actorPools) {
            val templateActor = gameIR.actors.find { it.id == pool.actorTemplateId }
            val tilesWide = templateActor?.sprite?.size?.let { (it.width + 7) / 8 } ?: 1
            val tilesHigh = templateActor?.sprite?.size?.let { (it.height + 7) / 8 } ?: 1
            val tilesPerEntity = tilesWide * tilesHigh
            val maxSlot =
                currentBase + (pool.config.maxSize - 1) * tilesPerEntity + (tilesPerEntity - 1)
            assertTrue(
                maxSlot <= 39,
                "Pool '${pool.id}' max OAM slot $maxSlot must be <= 39 (shadow_OAM has 40 entries), " +
                    "got: oamBase=$currentBase, maxSize=${pool.config.maxSize}, tilesPerEntity=$tilesPerEntity",
            )
            currentBase += pool.config.maxSize * tilesPerEntity
        }
    }

    @Test
    fun `forEachActive does not call move_sprite when body destroys the slot`() {
        // Regression test: before fix, move_sprite was called after destroy with oam[i]=0xFF,
        // writing to shadow_OAM[255] which corrupts GBDK internal variables at 0xC3FC-0xC3FD.
        // After fix: displaySyncStmts are wrapped in a re-check of the active flag.
        val gameIR = buildBulletGameIR()
        val destroyOp =
            PoolDestroyActor(
                poolId = "bulletPool",
                slotExpr = VarRef("bi"),
                deathCallbackOps = emptyList(),
            )
        val op =
            PoolForEachActive(
                poolId = "bulletPool",
                maxSize = 8,
                slotVarName = "bi",
                body = listOf(destroyOp),
            )
        val exprVisitor = ExprVisitor(gameIR.actors)
        ScriptOpVisitor.setGameIR(gameIR)
        val result = ScriptOpVisitor.visit(op, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        // The move_sprite display sync must be inside a re-check of the active flag.
        // After destroy, _pool_bulletPool_active[_bi] = 0, so the re-check skips move_sprite.
        // Verify: move_sprite appears AFTER a second active-check (two if statements in the body).
        val firstIfIdx = emitted.indexOf("if (")
        val secondIfIdx = emitted.indexOf("if (", firstIfIdx + 1)
        val moveSpriteIdx = emitted.indexOf("move_sprite(")
        assertTrue(
            secondIfIdx != -1,
            "forEachActive with destroy body should emit a re-check of active flag before move_sprite, got:\n$emitted",
        )
        assertTrue(
            moveSpriteIdx > secondIfIdx,
            "move_sprite display sync should appear after the re-check if (active), got:\n$emitted",
        )
    }
}
