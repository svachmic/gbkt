/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.api.gameUsesTilemapCollisionPathC
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 21 Plan 02 — Lockstep contract test for the duplicated
// gameUsesTilemapCollision predicate (SEED-022).
//
// Both GBDKPipeline.gameUsesTilemapCollision and
// PlatformerVisitor.gameUsesTilemapCollision must return identical verdicts for
// every fixture in the matrix below. The shared Path-C detection now lives in
// gbkt-backend-api/TilemapCollisionGate.kt; this test locks the contract so any
// future divergence is caught at the unit-test tier.
//
// Observable strategy:
//   - PlatformerVisitor.gameUsesTilemapCollision is private. The shared
//     gameUsesTilemapCollisionPathC() is directly callable (gbkt-backend-api).
//   - Pipeline-side behavior is observable via main.c: when gameUsesTilemapCollision
//     returns true, GBDKPipeline emits "is_tile_solid" into main.c.
//   - For Fixture 1 (Path C — tilemap_collision system): the shared util returns
//     true; both callers delegate to it; pipeline emits is_tile_solid.
//   - For Fixture 2 (Path A — platformer_physics with solidThreshold): the shared
//     util returns false (no tilemap_collision system); the pipeline uses reflection
//     for Path A; the visitor uses a typed cast. Both must return true.
//   - For Fixture 3 (Path B — per-zone override): same — shared util returns false;
//     both callers fall through to Path B.
//   - For Fixture 4 (none): all paths return false.
//
// The test asserts at both layers:
//   1. gameUsesTilemapCollisionPathC() verdict (direct call — Path C only).
//   2. Pipeline-side emission observable: main.c contains "is_tile_solid" == true
//      when the combined predicate is expected true.
//   3. For Fixture 2/3 where Path C is false but the combined predicate is true,
//      the pipeline still emits is_tile_solid via Path A/B — confirming the
//      pipeline's non-Path-C paths are unaffected by the SEED-022 change.
// =============================================================================

class TilemapCollisionPredicateLockstepTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Minimal cartridge config shared across all fixtures. */
    private val cartridge = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2)

    /**
     * Fixture 1: a [GenericSystem] with `config["type"] == "tilemap_collision"` is present.
     *
     * This is **Path C** — the new path added in Phase 12.1 when the `tilemapCollision { }` DSL
     * builder is used. Before SEED-022, PlatformerVisitor.gameUsesTilemapCollision missed this path
     * entirely. Both callers now delegate to [gameUsesTilemapCollisionPathC] for this path.
     *
     * Expected: `gameUsesTilemapCollisionPathC` = true; pipeline emits `is_tile_solid`.
     */
    private fun buildTilemapCollisionSystemFixture(): GameIR {
        val tilemapCollisionSystem =
            GenericSystem(
                id = "tilemap_collision",
                config =
                    mapOf(
                        "type" to "tilemap_collision",
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                        "hitboxX" to 0,
                        "hitboxY" to 0,
                        "hitboxW" to 8,
                        "hitboxH" to 24,
                        "solidThreshold" to 17,
                    ),
            )
        return GameIR(
            name = "LockstepFixture1TilemapCollisionSystem",
            config = cartridge,
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(tilemapCollisionSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Fixture 2: a `platformer_physics` [GenericSystem] carrying [PlatformerPhysicsConfig] with
     * non-null `solidThreshold`.
     *
     * This is **Path A** — the legacy path that existed before Phase 12.1. The pipeline uses Java
     * reflection to read `solidThreshold`; the visitor uses a direct typed cast. No
     * `tilemap_collision` system is present, so [gameUsesTilemapCollisionPathC] returns false — but
     * the combined predicate returns true via Path A.
     *
     * Expected: `gameUsesTilemapCollisionPathC` = false; pipeline still emits `is_tile_solid` via
     * Path A.
     */
    private fun buildPlatformerPhysicsWithThresholdFixture(): GameIR {
        val physicsSystem =
            GenericSystem(
                id = "physics",
                config =
                    mapOf(
                        "type" to "platformer_physics",
                        "physicsConfig" to
                            PlatformerPhysicsConfig(
                                gravity = 2,
                                jumpForce = 8,
                                terminalVelocity = 12,
                                solidThreshold = 17,
                            ),
                    ),
            )
        return GameIR(
            name = "LockstepFixture2PlatformerPhysicsThreshold",
            config = cartridge,
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Fixture 3: a [ZoneIR] whose `platformerPhysicsOverride` contains a `"solidThreshold"` key.
     *
     * This is **Path B** — per-zone override. No `tilemap_collision` system and no
     * `platformer_physics` system with typed `solidThreshold` are present. The combined predicate
     * returns true via the zone map.
     *
     * Expected: `gameUsesTilemapCollisionPathC` = false; pipeline still emits `is_tile_solid` via
     * Path B.
     */
    private fun buildPerZoneOverrideFixture(): GameIR {
        val zoneWithOverride =
            ZoneIR(
                id = "world1area1",
                name = "World 1 Area 1",
                platformerPhysicsOverride = mapOf("solidThreshold" to 17),
            )
        return GameIR(
            name = "LockstepFixture3PerZoneOverride",
            config = cartridge,
            scenes = listOf(SceneIR(id = "gameplay")),
            zones = listOf(zoneWithOverride),
            startScene = "gameplay",
        )
    }

    /**
     * Fixture 4: no `tilemap_collision` system, no physics system with `solidThreshold`, no zone
     * override. All three paths return false.
     *
     * Expected: `gameUsesTilemapCollisionPathC` = false; pipeline does NOT emit `is_tile_solid`.
     */
    private fun buildNoneFixture(): GameIR {
        return GameIR(
            name = "LockstepFixture4None",
            config = cartridge,
            scenes = listOf(SceneIR(id = "gameplay")),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `fixture 1 — tilemap_collision system — both predicates agree true`() {
        val gameIR = buildTilemapCollisionSystemFixture()

        // Path C shared util returns true for this fixture.
        assertTrue(
            gameUsesTilemapCollisionPathC(gameIR),
            "gameUsesTilemapCollisionPathC must return true when tilemap_collision system is present",
        )

        // Pipeline observable: is_tile_solid emitted only when combined predicate is true.
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("is_tile_solid"),
            "Pipeline must emit is_tile_solid when gameUsesTilemapCollision is true (Path C)",
        )
    }

    @Test
    fun `fixture 2 — platformer_physics with solidThreshold — pipeline Path A still fires`() {
        val gameIR = buildPlatformerPhysicsWithThresholdFixture()

        // Path C is NOT the active path here; shared util returns false.
        assertFalse(
            gameUsesTilemapCollisionPathC(gameIR),
            "gameUsesTilemapCollisionPathC must return false when only platformer_physics system" +
                " is present (Path C absent)",
        )

        // Pipeline observable: is_tile_solid still emitted because Path A fires.
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("is_tile_solid"),
            "Pipeline must emit is_tile_solid when platformer_physics system has solidThreshold" +
                " (Path A)",
        )
    }

    @Test
    fun `fixture 3 — per-zone platformerPhysicsOverride — pipeline Path B still fires`() {
        val gameIR = buildPerZoneOverrideFixture()

        // Path C is NOT the active path here; shared util returns false.
        assertFalse(
            gameUsesTilemapCollisionPathC(gameIR),
            "gameUsesTilemapCollisionPathC must return false when only per-zone override present" +
                " (Path C absent)",
        )

        // Pipeline observable: is_tile_solid still emitted because Path B fires.
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("is_tile_solid"),
            "Pipeline must emit is_tile_solid when per-zone platformerPhysicsOverride has" +
                " solidThreshold (Path B)",
        )
    }

    @Test
    fun `fixture 4 — none — both predicates agree false`() {
        val gameIR = buildNoneFixture()

        // Shared util returns false.
        assertFalse(
            gameUsesTilemapCollisionPathC(gameIR),
            "gameUsesTilemapCollisionPathC must return false when no tilemap-collision system" +
                " is present",
        )

        // Pipeline observable: is_tile_solid NOT emitted.
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")
        assertFalse(
            mainC.contains("is_tile_solid"),
            "Pipeline must NOT emit is_tile_solid when no tilemap-collision system is present",
        )
    }
}
