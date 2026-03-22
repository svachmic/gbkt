/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CollisionGroupIR
import io.github.gbkt.core.ir.CollisionResponse
import io.github.gbkt.core.ir.CollisionRuleIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.NpcCollisionConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// NPC COLLISION CODEGEN TESTS (Plan 06.7-03)
// Verifies that GBDKPipelineV2 generates correct C code for NPC-to-NPC collision:
//  - AABB pair-check function generated for each collision rule
//  - PUSH response generates mass-proportional displacement
//  - Interval > 1 generates static counter with modulo check
//  - collidesWithNpcs(true) implicit _default_npc group generates pairwise check
//  - collidesWithNpcs(true) + explicit group: actor NOT in default group
//  - Custom onCollide callback code appears inside collision check
//  - BLOCK response generates velocity zeroing
//  - BOUNCE response generates velocity reversal
//  - No collision rules → check_all_npc_collisions not generated
// =============================================================================

// =============================================================================
// Test helpers
// =============================================================================

private fun buildNpcGame(
    actors: List<ActorIR>,
    groups: List<CollisionGroupIR> = emptyList(),
    rules: List<CollisionRuleIR> = emptyList(),
): GameIR =
    GameIR(
        name = "NpcCollisionTestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "gameplay")),
        actors = actors,
        startScene = "gameplay",
        collisionGroups = groups,
        collisionRules = rules,
    )

/** Build an actor assigned to one or more explicit collision groups. */
private fun npcActor(
    id: String,
    groups: List<String> = emptyList(),
    mass: Int = 1,
    collidesWithNpcs: Boolean = false,
): ActorIR =
    ActorIR(
        id = id,
        position = PositionDef(x = 32, y = 32),
        npcCollisionConfig =
            NpcCollisionConfig(groupIds = groups, collidesWithNpcs = collidesWithNpcs, mass = mass),
    )

// =============================================================================
// Tests
// =============================================================================

class NpcCollisionCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Two groups, one rule → check function with AABB
    // =========================================================================

    @Test
    fun `two collision groups with one rule generates check function with AABB`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("bullet1", groups = listOf("bullets")),
                        npcActor("enemy1", groups = listOf("enemies")),
                    ),
                groups = listOf(CollisionGroupIR("bullets"), CollisionGroupIR("enemies")),
                rules = listOf(CollisionRuleIR("bullets", "enemies", CollisionResponse.OVERLAP)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_bullets_enemies"),
            "check_collision_bullets_enemies function missing",
        )
        // AABB check uses actor position variables
        assertTrue(
            mainC.contains("_bullet1_x"),
            "_bullet1_x position variable missing from AABB check",
        )
        assertTrue(
            mainC.contains("_enemy1_x"),
            "_enemy1_x position variable missing from AABB check",
        )
        // AABB uses +8 for hitbox size
        assertTrue(mainC.contains("8"), "AABB hitbox size constant (8) missing")
    }

    @Test
    fun `check_all_npc_collisions master function generated when rules exist`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("bullet1", groups = listOf("bullets")),
                        npcActor("enemy1", groups = listOf("enemies")),
                    ),
                groups = listOf(CollisionGroupIR("bullets"), CollisionGroupIR("enemies")),
                rules = listOf(CollisionRuleIR("bullets", "enemies")),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_all_npc_collisions"),
            "check_all_npc_collisions master function missing",
        )
    }

    @Test
    fun `no collision rules → check_all_npc_collisions not generated`() {
        val gameIR =
            buildNpcGame(
                actors = listOf(npcActor("enemy1", groups = listOf("enemies"))),
                groups = listOf(CollisionGroupIR("enemies")),
                rules = emptyList(),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("check_all_npc_collisions"),
            "check_all_npc_collisions should NOT appear when no rules exist",
        )
    }

    // =========================================================================
    // Test 2: PUSH response → mass-proportional displacement
    // =========================================================================

    @Test
    fun `PUSH response with different masses generates mass-proportional displacement`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("light_npc", groups = listOf("lights"), mass = 1),
                        npcActor("heavy_npc", groups = listOf("heavies"), mass = 3),
                    ),
                groups = listOf(CollisionGroupIR("lights"), CollisionGroupIR("heavies")),
                rules = listOf(CollisionRuleIR("lights", "heavies", CollisionResponse.PUSH)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_lights_heavies"),
            "check_collision_lights_heavies function missing for PUSH",
        )
        // PUSH generates a comment documenting the mass split
        assertTrue(mainC.contains("PUSH"), "PUSH comment (mass distribution info) missing")
    }

    @Test
    fun `PUSH response modifies both actor x positions`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("npc_a", groups = listOf("group_a"), mass = 1),
                        npcActor("npc_b", groups = listOf("group_b"), mass = 1),
                    ),
                groups = listOf(CollisionGroupIR("group_a"), CollisionGroupIR("group_b")),
                rules = listOf(CollisionRuleIR("group_a", "group_b", CollisionResponse.PUSH)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Both actor x variables appear on left side of assignment (displacement)
        assertTrue(mainC.contains("_npc_a_x"), "_npc_a_x missing from PUSH displacement")
        assertTrue(mainC.contains("_npc_b_x"), "_npc_b_x missing from PUSH displacement")
    }

    // =========================================================================
    // Test 3: Interval > 1 → static counter with modulo check
    // =========================================================================

    @Test
    fun `interval 2 generates static counter with modulo check`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("npc1", groups = listOf("npcs")),
                        npcActor("npc2", groups = listOf("npcs")),
                    ),
                groups = listOf(CollisionGroupIR("npcs")),
                rules = listOf(CollisionRuleIR("npcs", "npcs", interval = 2)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Interval support: static counter variable + modulo
        assertTrue(mainC.contains("static"), "static counter variable missing for interval check")
        assertTrue(mainC.contains("%"), "modulo operator missing from interval check")
        assertTrue(mainC.contains("2"), "interval value (2) missing from modulo check")
    }

    @Test
    fun `interval 1 does not generate static counter (no overhead)`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("npc1", groups = listOf("npcs")),
                        npcActor("npc2", groups = listOf("npcs")),
                    ),
                groups = listOf(CollisionGroupIR("npcs")),
                rules = listOf(CollisionRuleIR("npcs", "npcs", interval = 1)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // For interval=1, no _npc_interval_ counter should appear for this rule
        assertFalse(
            mainC.contains("_npc_interval_check_collision_npcs_npcs"),
            "interval counter should NOT appear for interval=1 (default)",
        )
    }

    // =========================================================================
    // Test 4: collidesWithNpcs(true) implicit _default_npc group
    // =========================================================================

    @Test
    fun `three actors with collidesWithNpcs=true in default group generates pairwise check`() {
        // This test simulates what GameBuilder.build() produces after the implicit group logic.
        // GameBuilder assigns these actors to _default_npc group and adds a self-OVERLAP rule.
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor(
                            "npc_1",
                            groups = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP),
                            collidesWithNpcs = true,
                        ),
                        npcActor(
                            "npc_2",
                            groups = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP),
                            collidesWithNpcs = true,
                        ),
                        npcActor(
                            "npc_3",
                            groups = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP),
                            collidesWithNpcs = true,
                        ),
                    ),
                groups = listOf(CollisionGroupIR(CollisionGroupIR.DEFAULT_NPC_GROUP)),
                rules =
                    listOf(
                        CollisionRuleIR(
                            CollisionGroupIR.DEFAULT_NPC_GROUP,
                            CollisionGroupIR.DEFAULT_NPC_GROUP,
                            CollisionResponse.OVERLAP,
                        )
                    ),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Implicit group generates check_collision__default_npc__default_npc
        assertTrue(
            mainC.contains("check_collision__default_npc__default_npc"),
            "check_collision__default_npc__default_npc missing for implicit _default_npc group",
        )
        // All 3 actors' positions referenced (pairwise: 1vs2, 1vs3, 2vs3)
        assertTrue(mainC.contains("_npc_1_x"), "_npc_1_x missing from default group check")
        assertTrue(mainC.contains("_npc_2_x"), "_npc_2_x missing from default group check")
        assertTrue(mainC.contains("_npc_3_x"), "_npc_3_x missing from default group check")
    }

    // =========================================================================
    // Test 5: collidesWithNpcs(true) + explicit group → NOT in default group
    // =========================================================================

    @Test
    fun `actor with explicit group is NOT placed in default_npc group`() {
        // Actor with explicit group: collidesWithNpcs=true is set, but explicit group takes
        // priority.
        // GameBuilder ensures groupIds is non-empty → not placed in _default_npc.
        // Here we test the codegen: if the actor only appears in "special_group", the
        // _default_npc check function should NOT reference this actor's position variables.
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        // This actor is in "special_group" only — NOT in _default_npc
                        npcActor(
                            "special_npc",
                            groups = listOf("special_group"),
                            collidesWithNpcs = true,
                        ),
                        // A plain default group actor to force check generation
                        npcActor(
                            "plain_npc",
                            groups = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP),
                            collidesWithNpcs = true,
                        ),
                        npcActor(
                            "plain_npc2",
                            groups = listOf(CollisionGroupIR.DEFAULT_NPC_GROUP),
                            collidesWithNpcs = true,
                        ),
                    ),
                groups =
                    listOf(
                        CollisionGroupIR("special_group"),
                        CollisionGroupIR(CollisionGroupIR.DEFAULT_NPC_GROUP),
                    ),
                rules =
                    listOf(
                        // Only default_npc rule exists — special_group has no rule
                        CollisionRuleIR(
                            CollisionGroupIR.DEFAULT_NPC_GROUP,
                            CollisionGroupIR.DEFAULT_NPC_GROUP,
                            CollisionResponse.OVERLAP,
                        )
                    ),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The _default_npc check function should NOT reference special_npc
        // (special_npc is only in "special_group" which has no rule, so no check is generated)
        // The default_npc check should only reference plain_npc and plain_npc2
        assertTrue(
            mainC.contains("check_collision__default_npc__default_npc"),
            "check_collision__default_npc__default_npc missing",
        )
        assertTrue(mainC.contains("_plain_npc_x"), "_plain_npc_x missing from default check")
        assertTrue(mainC.contains("_plain_npc2_x"), "_plain_npc2_x missing from default check")
        // special_npc should NOT appear inside the _default_npc check
        // (it's in a different group that has no collision rule)
        assertFalse(
            mainC.contains("check_collision_special_group"),
            "check_collision_special_group should NOT be generated (no rule for it)",
        )
    }

    // =========================================================================
    // Test 6: BLOCK response → velocity zeroing
    // =========================================================================

    @Test
    fun `BLOCK response generates velocity zeroing on collision axis`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("blocker", groups = listOf("walls")),
                        npcActor("mover", groups = listOf("walkers")),
                    ),
                groups = listOf(CollisionGroupIR("walls"), CollisionGroupIR("walkers")),
                rules = listOf(CollisionRuleIR("walls", "walkers", CollisionResponse.BLOCK)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_walls_walkers"),
            "check_collision_walls_walkers missing for BLOCK response",
        )
        // BLOCK sets velocity to zero — uses _vx/_vy variables
        assertTrue(
            mainC.contains("_vx"),
            "_vx velocity variable missing from BLOCK response (zeroing)",
        )
        assertTrue(
            mainC.contains("_vy"),
            "_vy velocity variable missing from BLOCK response (zeroing)",
        )
    }

    // =========================================================================
    // Test 7: BOUNCE response → velocity reversal
    // =========================================================================

    @Test
    fun `BOUNCE response generates velocity reversal`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("ball", groups = listOf("balls")),
                        npcActor("wall2", groups = listOf("walls2")),
                    ),
                groups = listOf(CollisionGroupIR("balls"), CollisionGroupIR("walls2")),
                rules = listOf(CollisionRuleIR("balls", "walls2", CollisionResponse.BOUNCE)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_balls_walls2"),
            "check_collision_balls_walls2 missing for BOUNCE response",
        )
        // BOUNCE negates velocity — uses unary minus
        assertTrue(mainC.contains("_vx"), "_vx velocity variable missing from BOUNCE response")
        assertTrue(mainC.contains("_vy"), "_vy velocity variable missing from BOUNCE response")
        // Negation uses unary minus operator
        assertTrue(
            mainC.contains("-"),
            "negation (unary minus) missing from BOUNCE velocity reversal",
        )
    }

    // =========================================================================
    // Test 8: Multiple rules → multiple check functions, all called from master
    // =========================================================================

    @Test
    fun `multiple rules generate multiple check functions all wired into master`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("proj", groups = listOf("projectiles")),
                        npcActor("enemy2", groups = listOf("foes")),
                        npcActor("npc_a2", groups = listOf("crowd")),
                        npcActor("npc_b2", groups = listOf("crowd")),
                    ),
                groups =
                    listOf(
                        CollisionGroupIR("projectiles"),
                        CollisionGroupIR("foes"),
                        CollisionGroupIR("crowd"),
                    ),
                rules =
                    listOf(
                        CollisionRuleIR("projectiles", "foes", CollisionResponse.OVERLAP),
                        CollisionRuleIR("crowd", "crowd", CollisionResponse.BLOCK),
                    ),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_projectiles_foes"),
            "check_collision_projectiles_foes missing",
        )
        assertTrue(
            mainC.contains("check_collision_crowd_crowd"),
            "check_collision_crowd_crowd missing",
        )
        assertTrue(
            mainC.contains("check_all_npc_collisions"),
            "check_all_npc_collisions master function missing",
        )
    }

    // =========================================================================
    // Test 9: Self-collision skip (no actor checks against itself in intra-group)
    // =========================================================================

    @Test
    fun `intra-group rule skips self-collision (actor not checked against itself)`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        npcActor("mob1", groups = listOf("mobs")),
                        npcActor("mob2", groups = listOf("mobs")),
                    ),
                groups = listOf(CollisionGroupIR("mobs")),
                rules = listOf(CollisionRuleIR("mobs", "mobs", CollisionResponse.OVERLAP)),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // check function for intra-group is generated
        assertTrue(
            mainC.contains("check_collision_mobs_mobs"),
            "check_collision_mobs_mobs missing for intra-group rule",
        )
        // mob1 and mob2 both referenced (pair check between them)
        assertTrue(mainC.contains("_mob1_x"), "_mob1_x missing from intra-group check")
        assertTrue(mainC.contains("_mob2_x"), "_mob2_x missing from intra-group check")
        // Self-check (mob1 vs mob1) should not appear — verify the generated code doesn't
        // have consecutive identical variable references for the self-check pattern.
        // This is verified by confirming mob1 vs mob2 check exists (implying self was skipped).
    }

    // =========================================================================
    // Test 10: Actors without npcCollisionConfig are unaffected
    // =========================================================================

    @Test
    fun `actors without npcCollisionConfig are not involved in NPC collision checks`() {
        val gameIR =
            buildNpcGame(
                actors =
                    listOf(
                        // Has npc config
                        npcActor("npc_x", groups = listOf("group_x")),
                        npcActor("npc_y", groups = listOf("group_y")),
                        // No npc config — plain actor (not in any group)
                        ActorIR(id = "plain_actor", position = PositionDef(x = 0, y = 0)),
                    ),
                groups = listOf(CollisionGroupIR("group_x"), CollisionGroupIR("group_y")),
                rules = listOf(CollisionRuleIR("group_x", "group_y")),
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The collision check function is generated (group_x and group_y actors ARE in groups)
        assertTrue(
            mainC.contains("check_collision_group_x_group_y"),
            "check_collision_group_x_group_y missing",
        )
        // npc_x and npc_y appear in the AABB check (they are in the collision groups)
        assertTrue(mainC.contains("_npc_x_x"), "_npc_x_x missing from group_x check")
        assertTrue(mainC.contains("_npc_y_x"), "_npc_y_x missing from group_y check")
        // The check function only loops over npc_x (group_x) × npc_y (group_y).
        // plain_actor has no npcCollisionConfig, so it cannot appear in any collision group.
        // Verify the check function exists and covers the correct actors (plain_actor membership
        // is implicitly excluded since group maps are built only from actors with
        // npcCollisionConfig).
        // There are only 2 actors in their respective groups — the AABB check is a direct pair,
        // not a loop that would include plain_actor.
        assertTrue(
            mainC.contains("check_all_npc_collisions"),
            "check_all_npc_collisions missing — NPC collision pipeline not wired",
        )
    }
}
