/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SystemIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Plan 12.1-05 Task 1 — locks the new `tilemapCollision { }` builder + GameBuilder extension.
 *
 * The builder packages per-game tilemap-collision configuration (position-var binding, velocity-
 * var binding, grounded-var binding, hitbox rect, solid threshold) into a [GenericSystem] with
 * `config["type"] == "tilemap_collision"`. Plan 12.1-06 will read these config keys from
 * `PlatformerVisitor` to rewrite the tilemap-physics path to use the user-DSL property names per
 * `feedback_no_magic_strings.md`.
 *
 * The 5 setters mirror the shape from PATTERNS.md and CONTEXT.md §D-claude-2/D-claude-4. Storage
 * keys (`posXVar`, `posYVar`, `vxVar`, `vyVar`, `groundedVar`, `hitboxX`/`Y`/`W`/`H`,
 * `solidThreshold`) match the visitor's expected reflective config-map shape (RESEARCH §Risks #6).
 */
class TilemapCollisionBuilderTest {

    // =========================================================================
    // Test 1 — extension registers a tilemap_collision-typed GenericSystem on GameBuilder
    // =========================================================================

    @Test
    fun `tilemapCollision DSL block registers GenericSystem with type tilemap_collision`() {
        val playerX = AssignableVar("playerX")
        val playerY = AssignableVar("playerY")
        val playerVx = AssignableVar("playerVx")
        val playerVy = AssignableVar("playerVy")
        val groundedVar = AssignableVar("grounded")

        val gb = GameBuilder("tc_test")
        gb.tilemapCollision {
            position(playerX, playerY)
            velocity(playerVx, playerVy)
            grounded(groundedVar)
            hitbox(0, 0, 8, 24)
            solidThreshold(17)
        }

        val systems: List<SystemIR> = gb.currentSystems()
        val tc =
            systems.filterIsInstance<GenericSystem>().firstOrNull { sys ->
                sys.config["type"] == "tilemap_collision"
            }
        assertNotNull(
            tc,
            "tilemapCollision { } must register a GenericSystem with type tilemap_collision",
        )
        assertEquals("playerX", tc.config["posXVar"] as? String)
    }

    // =========================================================================
    // Test 2 — builder accepts all 5 setters (compile + storage check)
    // =========================================================================

    @Test
    fun `tilemapCollision builder stores all five setters under expected config keys`() {
        val playerX = AssignableVar("playerX")
        val playerY = AssignableVar("playerY")
        val playerVx = AssignableVar("playerVx")
        val playerVy = AssignableVar("playerVy")
        val groundedVar = AssignableVar("grounded")

        val builder = TilemapCollisionBuilder("tilemap_collision")
        builder.position(playerX, playerY)
        builder.velocity(playerVx, playerVy)
        builder.grounded(groundedVar)
        builder.hitbox(0, 0, 8, 24)
        builder.solidThreshold(17)
        val system = builder.build()

        assertEquals("tilemap_collision", system.id)
        assertEquals("tilemap_collision", system.config["type"])
        assertEquals("playerX", system.config["posXVar"])
        assertEquals("playerY", system.config["posYVar"])
        assertEquals("playerVx", system.config["vxVar"])
        assertEquals("playerVy", system.config["vyVar"])
        assertEquals("grounded", system.config["groundedVar"])
        assertEquals(0, system.config["hitboxX"])
        assertEquals(0, system.config["hitboxY"])
        assertEquals(8, system.config["hitboxW"])
        assertEquals(24, system.config["hitboxH"])
        assertEquals(17, system.config["solidThreshold"])
    }

    // =========================================================================
    // Test 3 — default id is "tilemap_collision"
    // =========================================================================

    @Test
    fun `tilemapCollision extension defaults id to tilemap_collision`() {
        val gb = GameBuilder("tc_default_id")
        gb.tilemapCollision {
            hitbox(0, 0, 8, 24)
            solidThreshold(17)
        }
        val systems: List<SystemIR> = gb.currentSystems()
        val tc =
            systems.filterIsInstance<GenericSystem>().firstOrNull { sys ->
                sys.config["type"] == "tilemap_collision"
            }
        assertNotNull(tc)
        assertEquals("tilemap_collision", tc.id)
    }
}
