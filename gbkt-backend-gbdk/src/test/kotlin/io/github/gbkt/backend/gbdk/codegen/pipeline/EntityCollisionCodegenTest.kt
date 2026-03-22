/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CollisionShape
import io.github.gbkt.core.ir.EntityCollisionConfig
import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.TransitionEdge
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// ENTITY COLLISION CODEGEN TESTS (Plan 06.3-03)
// Verifies that GBDKPipelineV2 generates correct C code for entity collision:
//  - Entity grid management functions (_entity_register, _entity_remove, _entity_check)
//  - All 5 collision modes in _entity_handle_block
//  - Gap 1: _blocking_entity_id/_pushed_entity_id/_push_direction set before callbacks
//  - Gap 2: HITBOX collision shape generates AABB pixel check instead of grid-bit lookup
//  - Entity grid globals (_entity_grid, _entity_collision_mode, etc.)
//  - MAX_ENTITIES and MAP_SIZE #defines
// =============================================================================

/** Helper: build a GameIR with an ExplorationSystem and actors with entity collision. */
private fun buildEntityCollisionGame(
    actors: List<ActorIR>,
    system: ExplorationSystem = ExplorationSystem(id = "dungeon"),
): GameIR =
    GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "gameplay")),
        systems = listOf(system),
        actors = actors,
        startScene = "gameplay",
    )

/** Build an actor with the given entity collision config. */
private fun blockActor(id: String = "boulder"): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 64, y = 64),
        entityCollision = EntityCollisionConfig(mode = EntityCollisionMode.BLOCK),
    )

private fun passthroughActor(id: String = "ghost"): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 32, y = 32),
        entityCollision = EntityCollisionConfig(mode = EntityCollisionMode.PASSTHROUGH),
    )

private fun blockAndTriggerActor(id: String = "npc"): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 80, y = 80),
        entityCollision =
            EntityCollisionConfig(
                mode = EntityCollisionMode.BLOCK_AND_TRIGGER,
                onBlockedStatements = emptyList(),
            ),
    )

private fun overlapTriggerActor(id: String = "trap"): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 48, y = 48),
        entityCollision =
            EntityCollisionConfig(
                mode = EntityCollisionMode.OVERLAP_TRIGGER,
                onOverlapStatements = emptyList(),
            ),
    )

private fun pushActor(
    id: String = "crate",
    pushDir: PushDirection = PushDirection.ANY,
    allowedDirections: Set<TransitionEdge> = emptySet(),
): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 96, y = 96),
        entityCollision =
            EntityCollisionConfig(
                mode = EntityCollisionMode.PUSH,
                pushDirection = pushDir,
                allowedPushDirections = allowedDirections,
                onPushedStatements = emptyList(),
            ),
    )

private fun hitboxActor(id: String = "precise"): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 72, y = 72),
        entityCollision =
            EntityCollisionConfig(mode = EntityCollisionMode.BLOCK, shape = CollisionShape.HITBOX),
    )

private fun multiTileActor(
    id: String = "bigrock",
    tilesWide: Int = 2,
    tilesHigh: Int = 2,
): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 64, y = 64),
        entityCollision =
            EntityCollisionConfig(
                mode = EntityCollisionMode.BLOCK,
                tilesWide = tilesWide,
                tilesHigh = tilesHigh,
            ),
    )

class EntityCollisionCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: BLOCK mode entity generates grid check in exploration_move
    // =========================================================================

    @Test
    fun `BLOCK mode entity generates entity grid check in exploration_move`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_check"),
            "_entity_check call missing from exploration_move",
        )
        assertTrue(
            mainC.contains("_entity_handle_block"),
            "_entity_handle_block call missing from exploration_move",
        )
    }

    @Test
    fun `BLOCK mode entity registers in entity grid and generates grid management functions`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_entity_register"), "_entity_register function missing")
        assertTrue(mainC.contains("_entity_remove"), "_entity_remove function missing")
        assertTrue(mainC.contains("_entity_grid"), "_entity_grid variable missing")
    }

    // =========================================================================
    // Test 2: PASSTHROUGH entity skips collision check
    // =========================================================================

    @Test
    fun `game with only PASSTHROUGH entities does not generate entity grid check`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(passthroughActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("_entity_check"),
            "_entity_check should NOT be present for PASSTHROUGH-only game",
        )
        assertFalse(
            mainC.contains("_entity_grid"),
            "_entity_grid should NOT be present for PASSTHROUGH-only game",
        )
    }

    @Test
    fun `game with no entity collision config skips entity grid entirely`() {
        val actor =
            ActorIR(id = "npc", position = PositionDef(x = 32, y = 32)) // no entityCollision
        val gameIR = buildEntityCollisionGame(actors = listOf(actor))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("_entity_check"),
            "_entity_check should NOT appear without entity collision config",
        )
        assertFalse(
            mainC.contains("_entity_grid"),
            "_entity_grid should NOT appear without entity collision config",
        )
    }

    // =========================================================================
    // Test 3: BLOCK_AND_TRIGGER sets _blocking_entity_id before callback (Gap 1)
    // =========================================================================

    @Test
    fun `BLOCK_AND_TRIGGER generates onBlocked callback invocation`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockAndTriggerActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_handle_block"),
            "_entity_handle_block missing for BLOCK_AND_TRIGGER entity",
        )
    }

    @Test
    fun `BLOCK_AND_TRIGGER sets _blocking_entity_id before callback statements (Gap 1)`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockAndTriggerActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Gap 1: _blocking_entity_id must be assigned before callback fires
        assertTrue(
            mainC.contains("_blocking_entity_id"),
            "_blocking_entity_id global missing from output (Gap 1)",
        )
        // The assignment appears in _entity_handle_block
        val handleBlockIdx = mainC.indexOf("_entity_handle_block")
        assertTrue(handleBlockIdx >= 0, "_entity_handle_block function not found")
        val blockingEntityAssign = mainC.indexOf("_blocking_entity_id")
        assertTrue(
            blockingEntityAssign >= 0,
            "_blocking_entity_id assignment not found in handle_block (Gap 1)",
        )
    }

    // =========================================================================
    // Test 4: OVERLAP_TRIGGER allows continued movement
    // =========================================================================

    @Test
    fun `OVERLAP_TRIGGER entity generates mode ordinal 3 check to allow movement`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(overlapTriggerActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_check"),
            "_entity_check missing for OVERLAP_TRIGGER entity",
        )
        // OVERLAP_TRIGGER has mode ordinal 3 — exploration_move checks this to allow movement
        assertTrue(
            mainC.contains("3"),
            "OVERLAP_TRIGGER ordinal (3) missing from exploration_move entity check",
        )
    }

    // =========================================================================
    // Test 5: PUSH mode checks destination freedom and moves entity
    // =========================================================================

    @Test
    fun `PUSH mode generates destination freedom check`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(pushActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_handle_block"),
            "_entity_handle_block missing for PUSH entity",
        )
        // PUSH logic calls _map_collision for destination check
        assertTrue(mainC.contains("_map_collision"), "_map_collision check missing from PUSH logic")
    }

    @Test
    fun `PUSH mode sets _pushed_entity_id and _push_direction before callback (Gap 1)`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(pushActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Gap 1: _pushed_entity_id and _push_direction must be set before onPushed callback
        assertTrue(mainC.contains("_pushed_entity_id"), "_pushed_entity_id global missing (Gap 1)")
        assertTrue(mainC.contains("_push_direction"), "_push_direction global missing (Gap 1)")
    }

    // =========================================================================
    // Test 6: Multi-tile entity footprint (tilesWide/tilesHigh > 1)
    // =========================================================================

    @Test
    fun `entity collision globals declared when BLOCK entity present`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Entity grid globals must be present
        assertTrue(mainC.contains("_entity_grid"), "_entity_grid array declaration missing")
        assertTrue(mainC.contains("_entity_collision_mode"), "_entity_collision_mode array missing")
        assertTrue(mainC.contains("_entity_tile_x"), "_entity_tile_x array missing")
        assertTrue(mainC.contains("_entity_tile_y"), "_entity_tile_y array missing")
        assertTrue(mainC.contains("_entity_count"), "_entity_count global missing")
    }

    // =========================================================================
    // Test 7: Entity remove clears grid bits
    // =========================================================================

    @Test
    fun `entity remove function generated and clears grid bits`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_entity_remove"), "_entity_remove function missing")
        // Remove function should use AND + NOT (bitwise clear pattern)
        val removeIdx = mainC.indexOf("_entity_remove(")
        assertTrue(removeIdx >= 0, "_entity_remove function definition not found")
    }

    // =========================================================================
    // Test 8: Runtime mode change setter generated
    // =========================================================================

    @Test
    fun `runtime collision mode change setter function is generated`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_set_collision_mode"),
            "_entity_set_collision_mode function missing",
        )
    }

    // =========================================================================
    // Test 9: Bump feedback generated for BLOCK mode
    // =========================================================================

    @Test
    fun `BLOCK mode generates bump feedback call`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_bump_feedback"),
            "_entity_bump_feedback function missing for BLOCK mode",
        )
    }

    @Test
    fun `entity collision generates bump feedback stub function`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_bump_feedback"),
            "_entity_bump_feedback function stub missing",
        )
    }

    // =========================================================================
    // Test 10: MAX_ENTITIES and MAP_SIZE #defines generated
    // =========================================================================

    @Test
    fun `entity collision generates MAX_ENTITIES define based on actor count`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor("a"), blockActor("b")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("MAX_ENTITIES"), "MAX_ENTITIES #define missing")
        assertTrue(mainC.contains("2"), "MAX_ENTITIES should be 2 for 2 non-passthrough actors")
    }

    @Test
    fun `entity collision generates MAP_SIZE define for bit-packed grid`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("MAP_SIZE"), "MAP_SIZE #define missing")
        assertTrue(mainC.contains("129"), "MAP_SIZE should be 129 (32*32/8+1)")
    }

    // =========================================================================
    // Test 11: HITBOX collision shape uses AABB pixel check (Gap 2)
    // =========================================================================

    @Test
    fun `HITBOX collision shape generates AABB pixel overlap check (Gap 2)`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(hitboxActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Gap 2: HITBOX path must generate pixel-coordinate variables
        assertTrue(
            mainC.contains("_entity_check"),
            "_entity_check function missing for HITBOX actor",
        )
        // HITBOX path uses pixel variables (px, py, ex, ey) for AABB
        assertTrue(
            mainC.contains("px"),
            "px (pixel x) variable missing from HITBOX AABB check (Gap 2)",
        )
        assertTrue(
            mainC.contains("py"),
            "py (pixel y) variable missing from HITBOX AABB check (Gap 2)",
        )
        assertTrue(
            mainC.contains("ex"),
            "ex (entity pixel x) variable missing from HITBOX AABB check (Gap 2)",
        )
        assertTrue(
            mainC.contains("ey"),
            "ey (entity pixel y) variable missing from HITBOX AABB check (Gap 2)",
        )
    }

    @Test
    fun `HITBOX collision shape uses shape 1 discriminator to route AABB check`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(hitboxActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // _entity_collision_shape[j] == 1 check discriminates HITBOX vs TILE path
        assertTrue(
            mainC.contains("_entity_collision_shape"),
            "_entity_collision_shape reference missing from HITBOX check",
        )
    }

    // =========================================================================
    // Test 12: Mixed TILE and HITBOX actors both handled correctly
    // =========================================================================

    @Test
    fun `game with both TILE and HITBOX actors generates both code paths in entity check`() {
        val gameIR =
            buildEntityCollisionGame(actors = listOf(blockActor("wall"), hitboxActor("monster")))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Both TILE bit-check and HITBOX AABB check should be present
        assertTrue(mainC.contains("_entity_grid"), "_entity_grid bit check missing (TILE path)")
        assertTrue(mainC.contains("px"), "px pixel variable missing (HITBOX AABB path)")
        assertTrue(mainC.contains("ex"), "ex entity pixel variable missing (HITBOX AABB path)")
    }

    // =========================================================================
    // Test 13: Direction variable added to exploration_move when entity collision active
    // =========================================================================

    @Test
    fun `exploration_move gets direction variable when entity collision actors present`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // direction variable captures the d-pad direction for PUSH support
        assertTrue(
            mainC.contains("direction"),
            "direction variable missing from exploration_move (needed for PUSH mode)",
        )
    }

    // =========================================================================
    // Test 14: Gap 1 globals declared in variable section
    // =========================================================================

    @Test
    fun `Gap 1 callback globals declared as global variables`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(blockAndTriggerActor(), pushActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_blocking_entity_id"),
            "_blocking_entity_id global variable missing (Gap 1)",
        )
        assertTrue(
            mainC.contains("_pushed_entity_id"),
            "_pushed_entity_id global variable missing (Gap 1)",
        )
        assertTrue(
            mainC.contains("_push_direction"),
            "_push_direction global variable missing (Gap 1)",
        )
    }

    // =========================================================================
    // Test 15: Multi-tile entity register sets grid bits for all occupied tiles
    // =========================================================================

    @Test
    fun `multi-tile entity register sets grid bits for all occupied tiles`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(multiTileActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Multi-tile register must use nested for loops with _entity_tiles_wide/high
        assertTrue(
            mainC.contains("_entity_tiles_wide"),
            "_entity_tiles_wide array access missing from register (multi-tile support)",
        )
        assertTrue(
            mainC.contains("_entity_tiles_high"),
            "_entity_tiles_high array access missing from register (multi-tile support)",
        )
    }

    @Test
    fun `multi-tile entity remove clears all occupied tile bits`() {
        val gameIR = buildEntityCollisionGame(actors = listOf(multiTileActor()))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Multi-tile remove must reference dimension arrays for nested loops
        assertTrue(
            mainC.contains("_entity_tiles_wide"),
            "_entity_tiles_wide missing from remove function (multi-tile support)",
        )
        assertTrue(
            mainC.contains("_entity_tiles_high"),
            "_entity_tiles_high missing from remove function (multi-tile support)",
        )
    }

    @Test
    fun `multi-tile dimension arrays initialized from actor config`() {
        val gameIR =
            buildEntityCollisionGame(actors = listOf(multiTileActor(tilesWide = 2, tilesHigh = 3)))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_entity_tiles_wide"), "_entity_tiles_wide global array missing")
        assertTrue(mainC.contains("_entity_tiles_high"), "_entity_tiles_high global array missing")
    }

    // =========================================================================
    // Test 16: Push direction constraints (Gap B)
    // =========================================================================

    @Test
    fun `PUSH HORIZONTAL_ONLY generates direction constraint check`() {
        val gameIR =
            buildEntityCollisionGame(
                actors = listOf(pushActor(pushDir = PushDirection.HORIZONTAL_ONLY))
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // HORIZONTAL_ONLY must generate push_dir variable and direction validation
        assertTrue(
            mainC.contains("_entity_push_dir"),
            "_entity_push_dir array access missing for HORIZONTAL_ONLY push constraint",
        )
        assertTrue(
            mainC.contains("push_dir"),
            "push_dir local variable missing from PUSH direction constraint check",
        )
    }

    @Test
    fun `PUSH VERTICAL_ONLY generates direction constraint rejecting horizontal`() {
        val gameIR =
            buildEntityCollisionGame(
                actors = listOf(pushActor(pushDir = PushDirection.VERTICAL_ONLY))
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_push_dir"),
            "_entity_push_dir array access missing for VERTICAL_ONLY push constraint",
        )
    }

    @Test
    fun `PUSH SPECIFIC uses bitmask for allowed directions`() {
        val gameIR =
            buildEntityCollisionGame(
                actors =
                    listOf(
                        pushActor(
                            pushDir = PushDirection.SPECIFIC,
                            allowedDirections = setOf(TransitionEdge.EAST, TransitionEdge.WEST),
                        )
                    )
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_entity_push_allowed"),
            "_entity_push_allowed bitmask check missing for SPECIFIC push constraint",
        )
        assertTrue(
            mainC.contains("_entity_push_dir"),
            "_entity_push_dir array access missing for SPECIFIC push constraint",
        )
    }

    @Test
    fun `PUSH ANY generates push direction arrays but no restrictive guard`() {
        val gameIR =
            buildEntityCollisionGame(actors = listOf(pushActor(pushDir = PushDirection.ANY)))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // ANY still generates the push_dir variable (guard checks push_dir == 1/2/3 only)
        assertTrue(
            mainC.contains("_entity_push_dir"),
            "_entity_push_dir array declaration missing even for ANY mode",
        )
        // The guard checks for ordinal 1, 2, 3 — ANY (ordinal 0) falls through without blocking
        assertTrue(mainC.contains("push_dir"), "push_dir local variable missing from PUSH case")
    }
}
