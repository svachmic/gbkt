/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// GENERIC POOL CODEGEN TEST
// Validates the pool engine contract independent of any specific game.
// Exercises spawn/move/destroy patterns for a minimal GameIR with one pool.
// Covers D-05 from RESEARCH.md: "generic pool codegen test covering
// spawn/move/destroy patterns independent of any specific game".
// =============================================================================

class GenericPoolCodegenTest {

    /**
     * Build a minimal GameIR with:
     * - One actor template `testEntity` (8x8 sprite, position at 0,0)
     * - One pool `testPool` (templateActor=testEntity, maxSize=4)
     * - One non-template actor `player` to verify correct update_sprites exclusion
     * - One scene with a forEachActive body containing a destroy op
     */
    private fun buildTestGameIR(): GameIR {
        val templateActor =
            ActorIR(
                id = "testEntity",
                position = PositionDef(0, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/testEntity.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val playerActor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val pool =
            ActorPoolIR(
                id = "testPool",
                actorTemplateId = "testEntity",
                config = ActorPoolConfig(maxSize = 4),
            )
        val scene =
            SceneIR(
                id = "gameplay",
                frameOps =
                    listOf(
                        PoolForEachActive(
                            poolId = "testPool",
                            maxSize = 4,
                            slotVarName = "i",
                            body =
                                listOf(
                                    PoolDestroyActor(
                                        poolId = "testPool",
                                        slotExpr = VarRef("i"),
                                        deathCallbackOps = emptyList(),
                                    )
                                ),
                        )
                    ),
            )
        return GameIR(
            name = "TestGame",
            config = CartridgeConfig(),
            actors = listOf(templateActor, playerActor),
            actorPools = listOf(pool),
            scenes = listOf(scene),
            startScene = "gameplay",
        )
    }

    // =========================================================================
    // State variable arrays
    // =========================================================================

    @Test
    fun `pool state vars include per-instance x array`() {
        val gameIR = buildTestGameIR()
        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_testPool_x"),
            "Pool state should include _pool_testPool_x[4] per-instance x array, got: $names",
        )
    }

    @Test
    fun `pool state vars include per-instance y array`() {
        val gameIR = buildTestGameIR()
        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_testPool_y"),
            "Pool state should include _pool_testPool_y[4] per-instance y array, got: $names",
        )
    }

    @Test
    fun `pool state vars include per-instance oam array`() {
        val gameIR = buildTestGameIR()
        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertTrue(
            names.contains("_pool_testPool_oam"),
            "Pool state should include _pool_testPool_oam[4] per-instance OAM array, got: $names",
        )
    }

    @Test
    fun `pool state vars do not include oam_base scalar`() {
        val gameIR = buildTestGameIR()
        val vars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)
        val names = vars.map { it.name }

        assertFalse(
            names.contains("_pool_testPool_oam_base"),
            "Pool state should NOT include oam_base scalar (replaced by per-instance array), got: $names",
        )
    }

    // =========================================================================
    // Spawn function — dynamic OAM allocation
    // =========================================================================

    @Test
    fun `pool spawn function uses static oam slot without spawn_actor`() {
        // Static OAM assignment: spawn uses oam[i] pre-initialized in init(), not spawn_actor().
        // This fixes the OAM out-of-bounds write bug (multi-tile sprites writing past
        // shadow_OAM[39]).
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val spawnFn = functions.first { it.name == "pool_testPool_spawn" }
        val emitted =
            spawnFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        assertFalse(
            emitted.contains("spawn_actor"),
            "pool_testPool_spawn should NOT call spawn_actor() (static OAM, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("move_sprite"),
            "pool_testPool_spawn should call move_sprite() to position the sprite, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_testPool_oam["),
            "pool_testPool_spawn should use _pool_testPool_oam[] for the slot, got:\n$emitted",
        )
    }

    @Test
    fun `pool spawn function stores position in per-instance arrays`() {
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val spawnFn = functions.first { it.name == "pool_testPool_spawn" }
        val emitted =
            spawnFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        assertTrue(
            emitted.contains("_pool_testPool_x["),
            "pool_testPool_spawn should store x in _pool_testPool_x[] array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_testPool_y["),
            "pool_testPool_spawn should store y in _pool_testPool_y[] array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_testPool_oam["),
            "pool_testPool_spawn should store OAM slot in _pool_testPool_oam[] array, got:\n$emitted",
        )
    }

    @Test
    fun `pool spawn returns 0xFF when pool is full`() {
        // Static OAM assignment: when all slots are active, spawn returns 0xFF (pool full).
        // No spawn_actor() failure path needed — OAM is always available via static assignment.
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val spawnFn = functions.first { it.name == "pool_testPool_spawn" }
        val emitted =
            spawnFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        // When all slots are active, spawn returns 0xFF (pool-full sentinel)
        assertTrue(
            emitted.contains("0xFF"),
            "pool_testPool_spawn should return 0xFF when pool is full, got:\n$emitted",
        )
    }

    // =========================================================================
    // Destroy function — static OAM hide (move_sprite to 0,0)
    // =========================================================================

    @Test
    fun `pool destroy function hides sprite via move_sprite not destroy_actor`() {
        // Static OAM assignment: destroy calls move_sprite(slot, 0, 0) to hide off-screen.
        // No destroy_actor() call — the OAM slot is permanently assigned (not returned to free
        // list).
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val destroyFn = functions.first { it.name == "pool_testPool_destroy" }
        val emitted =
            destroyFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        assertFalse(
            emitted.contains("destroy_actor"),
            "pool_testPool_destroy should NOT call destroy_actor() (static OAM, no free list), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("move_sprite"),
            "pool_testPool_destroy should call move_sprite(slot, 0, 0) to hide sprite off-screen, got:\n$emitted",
        )
    }

    @Test
    fun `pool destroy function reads oam slot from per-instance array`() {
        // Static OAM: destroy reads oam[i] to hide the sprite, but does NOT reset it to 0xFF
        // (the static slot assignment is permanent — no sentinel needed).
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val destroyFn = functions.first { it.name == "pool_testPool_destroy" }
        val emitted =
            destroyFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        assertTrue(
            emitted.contains("_pool_testPool_oam["),
            "pool_testPool_destroy should read OAM slot from _pool_testPool_oam[] array, got:\n$emitted",
        )
        assertFalse(
            emitted.contains("0xFF"),
            "pool_testPool_destroy should NOT set oam[i] = 0xFF (static OAM is permanent), got:\n$emitted",
        )
    }

    // =========================================================================
    // Init function — zeroes active bitmap and sets oam entries to 0xFF
    // =========================================================================

    @Test
    fun `pool init function pre-initializes oam entries to static slot assignment`() {
        // Static OAM assignment: pool_testPool_init() sets oam[i] = oamBase + i * tilesPerEntity.
        // No 0xFF sentinel — the slot is statically assigned and permanent.
        val gameIR = buildTestGameIR()
        val functions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)
        val initFn = functions.first { it.name == "pool_testPool_init" }
        val emitted =
            initFn.body.joinToString("\n") {
                io.github.gbkt.backend.gbdk.codegen.emit.CEmitter.emitStatement(it)
            }

        assertFalse(
            emitted.contains("0xFF"),
            "pool_testPool_init should NOT set oam entries to 0xFF sentinel (static OAM), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_testPool_oam"),
            "pool_testPool_init should initialize _pool_testPool_oam array, got:\n$emitted",
        )
        // The init loop should set oam[i] = oamBase + i * tilesPerEntity
        assertTrue(
            emitted.contains("_pool_testPool_oam["),
            "pool_testPool_init should assign to _pool_testPool_oam[i], got:\n$emitted",
        )
    }

    // =========================================================================
    // main() integration — pool_init calls before start scene enter
    // =========================================================================

    @Test
    fun `main function calls pool_init for each pool`() {
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pool_testPool_init()"),
            "main() should call pool_testPool_init() before game loop, got relevant section:\n" +
                mainC
                    .lines()
                    .filter { it.contains("pool") || it.contains("init") }
                    .joinToString("\n"),
        )
    }

    // =========================================================================
    // update_sprites exclusion — template actors excluded from per-frame sync
    // =========================================================================

    @Test
    fun `update_sprites does not emit move_sprite for pool template actor`() {
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Extract the update_sprites function body
        val lines = mainC.lines()
        val updateSpritesStart = lines.indexOfFirst {
            it.contains("update_sprites") && it.contains("{")
        }
        val updateSpritesEnd =
            if (updateSpritesStart >= 0) {
                var depth = 0
                var end = updateSpritesStart
                for (i in updateSpritesStart until lines.size) {
                    depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
                    if (i > updateSpritesStart && depth <= 0) {
                        end = i
                        break
                    }
                }
                end
            } else -1

        val updateSpritesBody =
            if (updateSpritesStart >= 0 && updateSpritesEnd >= 0) {
                lines.subList(updateSpritesStart, updateSpritesEnd + 1).joinToString("\n")
            } else ""

        assertFalse(
            updateSpritesBody.contains("_testEntity_x"),
            "update_sprites should NOT emit move_sprite for pool template actor testEntity, got:\n$updateSpritesBody",
        )
    }

    @Test
    fun `update_sprites does emit move_sprite for non-template actor`() {
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_player_x"),
            "update_sprites should emit move_sprite for non-template actor player, main.c should contain _player_x",
        )
    }

    @Test
    fun `set_sprite_data is still emitted for pool template actor`() {
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // set_sprite_data loads VRAM tiles — template actors still need their tile data loaded
        // even though they don't get static OAM slots
        assertTrue(
            mainC.contains("sprites_testEntity_tiles") || mainC.contains("testEntity_tiles"),
            "set_sprite_data should still be called for template actor's VRAM tile data, main.c should reference testEntity tiles",
        )
    }

    @Test
    fun `set_sprite_tile is not emitted for pool template actor`() {
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Extract only the main() function body before the game loop
        // Template actors should NOT get static OAM slot binding
        val mainFnStart = mainC.indexOf("void main(")
        val initSection =
            if (mainFnStart >= 0) {
                mainC.substring(mainFnStart, minOf(mainFnStart + 2000, mainC.length))
            } else mainC

        // Count set_sprite_tile calls - should only be for non-template actors
        // Template actors don't have static OAM slots so should not have set_sprite_tile
        // with a static slot number for the testEntity
        val setSpriteTileCalls = initSection.lines().filter { it.contains("set_sprite_tile") }

        // Player gets a set_sprite_tile call, testEntity template should NOT
        // (since it's excluded from static OAM init)
        assertTrue(
            setSpriteTileCalls.size <= 1,
            "Only non-template actors should get set_sprite_tile calls (player=1, testEntity=excluded), " +
                "but got ${setSpriteTileCalls.size} calls: $setSpriteTileCalls",
        )
    }

    // =========================================================================
    // Bug 7: Pool-pool collision — nested forEachActive loop synthesis
    // =========================================================================

    /**
     * Build a minimal GameIR with two pools (bullet + enemy) for collision tests. Both templates
     * have hitboxes.
     */
    private fun buildTwoPoolGameIR(): GameIR {
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
                position = PositionDef(0, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/enemy.png", AssetType.SPRITE),
                        size = SizeDef(16, 16),
                        hitbox = HitboxDef(0, 0, 16, 16),
                    ),
            )
        val playerActor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 16),
                        hitbox = HitboxDef(0, 0, 8, 16),
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
        // Scene with whenever(bullet.collides(enemy)) — both pool templates
        val bothPoolCollision =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("bullet"), VarRef("enemy"))),
                then = listOf(Literal(1).let { io.github.gbkt.core.ir.Assign("score", it) }),
            )
        // Scene with whenever(enemy.collides(player)) — one pool template, one normal actor
        val onePoolCollision =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("enemy"), VarRef("player"))),
                then = emptyList(),
            )
        // Scene with whenever(player.collides(player)) — no pool templates (regression)
        val noPoolCollision =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("player"), VarRef("player"))),
                then = emptyList(),
            )
        val bothPoolScene = SceneIR(id = "bothPool", frameOps = listOf(bothPoolCollision))
        val onePoolScene = SceneIR(id = "onePool", frameOps = listOf(onePoolCollision))
        val noPoolScene = SceneIR(id = "noPool", frameOps = listOf(noPoolCollision))
        return GameIR(
            name = "CollisionTest",
            config = CartridgeConfig(),
            actors = listOf(bulletTemplate, enemyTemplate, playerActor),
            actorPools = listOf(bulletPool, enemyPool),
            scenes = listOf(bothPoolScene, onePoolScene, noPoolScene),
            startScene = "bothPool",
        )
    }

    @Test
    fun `both-pool-template collision generates nested for-loops`() {
        // whenever(bullet.collides(enemy)) — both are pool templates
        // Should generate nested loops iterating _pool_bulletPool_active and _pool_enemyPool_active
        val gameIR = buildTwoPoolGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)
        val ifOp =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("bullet"), VarRef("enemy"))),
                then = emptyList(),
            )
        val result = ScriptOpVisitor.visit(ifOp, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        // Should have two nested for-loops
        val forCount = emitted.split("for ").size - 1
        assertTrue(
            forCount >= 2,
            "Both-pool-template collision should generate 2 nested for-loops, got $forCount for-loops in:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_active["),
            "Outer loop should iterate _pool_bulletPool_active[], got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_enemyPool_active["),
            "Inner loop should iterate _pool_enemyPool_active[], got:\n$emitted",
        )
    }

    @Test
    fun `both-pool-template collision AABB uses per-instance arrays`() {
        // AABB check should use _pool_bulletPool_x[_pool_bi] vs _pool_enemyPool_x[_pool_ei]
        val gameIR = buildTwoPoolGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)
        val ifOp =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("bullet"), VarRef("enemy"))),
                then = emptyList(),
            )
        val result = ScriptOpVisitor.visit(ifOp, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertFalse(
            emitted.contains("_bullet_x") || emitted.contains("_bullet_y"),
            "Pool-pool AABB should NOT reference template actor globals _bullet_x/_bullet_y, got:\n$emitted",
        )
        assertFalse(
            emitted.contains("_enemy_x") || emitted.contains("_enemy_y"),
            "Pool-pool AABB should NOT reference template actor globals _enemy_x/_enemy_y, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_bulletPool_x["),
            "Pool-pool AABB should use _pool_bulletPool_x[] per-instance array, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_enemyPool_x["),
            "Pool-pool AABB should use _pool_enemyPool_x[] per-instance array, got:\n$emitted",
        )
    }

    @Test
    fun `one-pool-template collision generates single for-loop`() {
        // whenever(enemy.collides(player)) — enemy is pool template, player is not
        // Should generate single loop over _pool_enemyPool_active, AABB against _player_x
        val gameIR = buildTwoPoolGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)
        val ifOp =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("enemy"), VarRef("player"))),
                then = emptyList(),
            )
        val result = ScriptOpVisitor.visit(ifOp, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        val forCount = emitted.split("for ").size - 1
        assertTrue(
            forCount == 1,
            "One-pool-template collision should generate exactly 1 for-loop, got $forCount for-loops in:\n$emitted",
        )
        assertTrue(
            emitted.contains("_pool_enemyPool_active["),
            "Single loop should iterate _pool_enemyPool_active[], got:\n$emitted",
        )
        // Non-pool actor should use its scalar variable
        assertTrue(
            emitted.contains("_player_x"),
            "Non-pool actor should use scalar _player_x in AABB, got:\n$emitted",
        )
    }

    @Test
    fun `no-pool-template collision unchanged single AABB check regression`() {
        // whenever(player.collides(player)) — neither is a pool template
        // Should generate the existing single AABB check (no regression)
        val gameIR = buildTwoPoolGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)
        val ifOp =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("player"), VarRef("player"))),
                then = emptyList(),
            )
        val result = ScriptOpVisitor.visit(ifOp, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        // No for-loops — just a plain if with AABB condition
        val forCount = emitted.split("for ").size - 1
        assertTrue(
            forCount == 0,
            "No-pool-template collision should generate 0 for-loops, got $forCount for-loops in:\n$emitted",
        )
        assertTrue(
            emitted.contains("if"),
            "No-pool collision should still generate if-statement with AABB, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_player_x"),
            "No-pool AABB should use scalar _player_x, got:\n$emitted",
        )
    }

    @Test
    fun `both-pool-template collision body can call destroy on each pool with auto-named slot vars`() {
        // F-A: collision body needs access to outer/inner loop indices to destroy the colliding
        // instances. The DSL's typed `whenever(poolA.collides(poolB)) { idxA, idxB -> ... }`
        // overload emits PoolDestroyActor ops with slotExpr=VarRef("pool_<short>i"), matching the
        // codegen's auto-derived loop variable names. Verify the generated C contains
        //   pool_bulletPool_destroy(_pool_bi);
        //   pool_enemyPool_destroy(_pool_ei);
        // INSIDE the AABB if-block (i.e. nested inside both for-loops), so a hit destroys exactly
        // one bullet + one enemy.
        val gameIR = buildTwoPoolGameIR()
        ScriptOpVisitor.setGameIR(gameIR)
        val exprVisitor = ExprVisitor(gameIR.actors)
        val ifOp =
            IfOp(
                condition = CallExpr("collides", listOf(VarRef("bullet"), VarRef("enemy"))),
                then =
                    listOf(
                        PoolDestroyActor(poolId = "bulletPool", slotExpr = VarRef("pool_bi")),
                        PoolDestroyActor(poolId = "enemyPool", slotExpr = VarRef("pool_ei")),
                    ),
            )
        val result = ScriptOpVisitor.visit(ifOp, exprVisitor)
        val emitted = CEmitter.emitStatement(result)

        assertTrue(
            emitted.contains("pool_bulletPool_destroy(_pool_bi)"),
            "Collision body should emit pool_bulletPool_destroy(_pool_bi), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("pool_enemyPool_destroy(_pool_ei)"),
            "Collision body should emit pool_enemyPool_destroy(_pool_ei), got:\n$emitted",
        )
        // Sanity: both destroy calls must be inside the AABB if-block — verify they appear after
        // the AABB condition opens and before the trailing close braces of both for loops.
        val aabbIdx = emitted.indexOf("if (_pool_bulletPool_x[")
        val destroyBulletIdx = emitted.indexOf("pool_bulletPool_destroy(_pool_bi)")
        val destroyEnemyIdx = emitted.indexOf("pool_enemyPool_destroy(_pool_ei)")
        assertTrue(
            aabbIdx in 0 until destroyBulletIdx,
            "pool_bulletPool_destroy must appear AFTER the AABB if-condition opens; positions: aabb=$aabbIdx destroyBullet=$destroyBulletIdx in:\n$emitted",
        )
        assertTrue(
            aabbIdx in 0 until destroyEnemyIdx,
            "pool_enemyPool_destroy must appear AFTER the AABB if-condition opens; positions: aabb=$aabbIdx destroyEnemy=$destroyEnemyIdx in:\n$emitted",
        )
    }

    @Test
    fun `forEachActive end-to-end generates array accesses and display sync in pipeline output`() {
        // End-to-end: the scene frame function in generated C should contain pool array accesses
        // and display sync when the scene has a forEachActive body op that modifies position
        val gameIR = buildTestGameIR()
        val output = GBDKPipeline().generate(gameIR)
        val bankC =
            output.files["bank1.c"] ?: output.files["main.c"] ?: error("No bank file generated")

        // The gameplay scene has a forEachActive with a PoolDestroyActor body
        // After Plan 02, this scene's frame function should contain pool array accesses
        // and a move_sprite call for display sync
        assertTrue(
            bankC.contains("_pool_testPool_active[") || bankC.contains("_pool_testPool_oam["),
            "Scene C output should reference pool arrays for forEachActive, got pool-related content:\n" +
                bankC.lines().filter { it.contains("pool") }.joinToString("\n"),
        )
    }
}
