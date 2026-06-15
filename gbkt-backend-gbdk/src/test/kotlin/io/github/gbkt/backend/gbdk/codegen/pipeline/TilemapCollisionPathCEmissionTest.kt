/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import io.github.gbkt.genre.platformer.dsl.tilemapCollision
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Plan 12.1-05 Task 2 — gameUsesTilemapCollision Path C emission gate
//
// Locks the contract that registering a GenericSystem with
// `config["type"] == "tilemap_collision"` (via the new `tilemapCollision { }`
// builder from Task 1) activates `gameUsesTilemapCollision == true` even when
// neither Path A (platformerPhysics.solidThreshold) nor Path B (per-zone
// platformerPhysicsOverride.solidThreshold) is present.
//
// The function `gameUsesTilemapCollision` is private; this test exercises it
// indirectly via the pipeline's downstream emission — when the predicate fires,
// `buildTilemapCollisionGlobals` emits `_current_area_bank` and
// `buildIsTileSolidHelperIfNeeded` emits the `is_tile_solid` HOME-bank helper.
// Both symbols are absent from `main.c` when the predicate returns false
// (regression Test 4 below) — locking the negative gate ensures byte-identical
// codegen for games that opt out.
//
// Acceptance contract (Plan 12.1-05 Task 2 behavior list):
//   - Test 1 — tilemap_collision system alone fires Path C.
//   - Test 2 — platformerPhysics + solidThreshold (Path A) STILL fires (no
//     regression from adding Path C).
//   - Test 3 — both systems coexist; predicate still true (no exclusion).
//   - Test 4 — empty game (no systems, no zones) keeps predicate false.
// =============================================================================

class TilemapCollisionPathCEmissionTest {
    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Test 1 — Path C fires for tilemap_collision GenericSystem alone
    // -------------------------------------------------------------------------

    @Test
    fun `Path C fires when only tilemap_collision system is present`() {
        val playerX = AssignableVar("playerX")
        val playerY = AssignableVar("playerY")
        val gameIR =
            game("path_c_only") {
                    // No platformerPhysics block — Path A absent.
                    tilemapCollision {
                        position(playerX, playerY)
                        hitbox(0, 0, 8, 24)
                        solidThreshold(17)
                    }
                    val z1 by zone { tileset(asset("res/graphics/level1.png")) }
                    // No platformerPhysicsOverride — Path B absent.
                    val gameplayScene =
                        scene("gameplay") {
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    start = gameplayScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("_current_area_bank"),
            "Path C must activate gameUsesTilemapCollision (no Path A, no Path B). " +
                "Expected `_current_area_bank` declaration in main.c — produced by " +
                "buildTilemapCollisionGlobals when the predicate is true.",
        )
        assertTrue(
            mainC.contains("is_tile_solid"),
            "Path C must activate buildIsTileSolidHelperIfNeeded — expected `is_tile_solid` " +
                "function in main.c.",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — Path A (existing) still fires (back-compat regression guard)
    // -------------------------------------------------------------------------

    @Test
    fun `Path A still fires when only platformerPhysics solidThreshold is present`() {
        val gameIR =
            game("path_a_only") {
                    platformerPhysics { solidThreshold(17) }
                    val z1 by zone { tileset(asset("res/graphics/level1.png")) }
                    val gameplayScene =
                        scene("gameplay") {
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    start = gameplayScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("_current_area_bank"),
            "Path A back-compat — gameUsesTilemapCollision must still fire when only " +
                "platformerPhysics.solidThreshold is registered.",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — Path A + Path C coexist (no mutual exclusion)
    // -------------------------------------------------------------------------

    @Test
    fun `Path A and Path C coexist without exclusion`() {
        val playerX = AssignableVar("playerX")
        val playerY = AssignableVar("playerY")
        val gameIR =
            game("path_a_plus_c") {
                    platformerPhysics { solidThreshold(17) }
                    tilemapCollision {
                        position(playerX, playerY)
                        hitbox(0, 0, 8, 24)
                        solidThreshold(17)
                    }
                    val z1 by zone { tileset(asset("res/graphics/level1.png")) }
                    val gameplayScene =
                        scene("gameplay") {
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    start = gameplayScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("_current_area_bank"),
            "Both paths must coexist — registering tilemap_collision must NOT disable " +
                "platformer_physics emission.",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 — neither system present → predicate false → no emission
    // -------------------------------------------------------------------------

    @Test
    fun `empty game keeps gameUsesTilemapCollision false`() {
        val gameIR =
            game("no_tilemap_collision") {
                    // Intentionally NO platformerPhysics, NO tilemapCollision, NO zones.
                    val gameplayScene =
                        scene("gameplay") {
                            frame {
                                runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    start = gameplayScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        assertFalse(
            mainC.contains("_current_area_bank"),
            "Negative gate — without any of A/B/C, no tilemap-collision globals must emit.",
        )
        assertFalse(
            mainC.contains("is_tile_solid"),
            "Negative gate — without any of A/B/C, no is_tile_solid helper must emit.",
        )
    }
}
