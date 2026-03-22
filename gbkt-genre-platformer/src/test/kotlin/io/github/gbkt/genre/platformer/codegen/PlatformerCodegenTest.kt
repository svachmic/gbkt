/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.GoalZoneDef
import io.github.gbkt.genre.platformer.domain.HazardDef
import io.github.gbkt.genre.platformer.domain.ParallaxLayer
import io.github.gbkt.genre.platformer.domain.PlatformDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.WallJumpConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PLATFORMER CODEGEN TESTS (Plan 06.8-10 Task 2 success criteria)
//
// 10 tests verifying PlatformerVisitor via GBDKPipelineV2 ServiceLoader discovery:
//   - Physics update generated with gravity application
//   - Variable-height jump body contains jump force and cut
//   - Coyote time timer decrement in physics update
//   - Wall-jump functions generated only when wallJump config present
//   - Camera smooth-follow generates dead zone check in camera update
//   - Camera screen-lock generates screen transition logic
//   - Parallax: layer update function generated per parallax layer
//   - Hazard: collision check function generated
//   - Goal zone: AABB check function generated
//   - Platform collision: one-way platform has directional check
// =============================================================================

/** Build a minimal GameIR carrying a single platformer GenericSystem. */
private fun buildPlatformerGameIR(
    systemType: String,
    configKey: String,
    configValue: Any,
    id: String = "plat",
): GameIR {
    val system =
        GenericSystem(id = id, config = mapOf("type" to systemType, configKey to configValue))
    return GameIR(
        name = "TestPlatformerGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "gameplay")),
        systems = listOf(system),
        startScene = "gameplay",
    )
}

class PlatformerCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Physics update function generated with gravity application
    // =========================================================================

    @Test
    fun `physics update function generated with gravity application`() {
        val config = PlatformerPhysicsConfig(gravity = 3, jumpForce = 10, terminalVelocity = 15)
        val gameIR = buildPlatformerGameIR("platformer_physics", "physicsConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("platformer_physics_update"),
            "Expected 'platformer_physics_update' function in generated C",
        )
        assertTrue(
            mainC.contains("Apply gravity"),
            "Expected 'Apply gravity' comment in physics update function",
        )
        assertTrue(
            mainC.contains("_plat_vy"),
            "Expected '_plat_vy' velocity variable in generated C",
        )
        assertTrue(
            mainC.contains("_plat_grounded"),
            "Expected '_plat_grounded' grounded flag in generated C",
        )
    }

    // =========================================================================
    // Test 2: Variable-height jump body contains jump force check and cut
    // =========================================================================

    @Test
    fun `variable height jump body contains jump force and cut`() {
        val config = PlatformerPhysicsConfig(gravity = 2, jumpForce = 8, variableHeightJump = true)
        val gameIR = buildPlatformerGameIR("platformer_physics", "physicsConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("Variable-height jump"),
            "Expected 'Variable-height jump' comment in physics update",
        )
        assertTrue(
            mainC.contains("button_released"),
            "Expected 'button_released' call for variable height jump cut",
        )
    }

    // =========================================================================
    // Test 3: Coyote time timer decrement in physics update
    // =========================================================================

    @Test
    fun `coyote time timer decrement in physics update`() {
        val config = PlatformerPhysicsConfig(coyoteFrames = 6, jumpBufferFrames = 8)
        val gameIR = buildPlatformerGameIR("platformer_physics", "physicsConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_plat_coyote_timer"),
            "Expected '_plat_coyote_timer' in generated C",
        )
        assertTrue(
            mainC.contains("Decrement coyote timer"),
            "Expected 'Decrement coyote timer' comment in physics update",
        )
        assertTrue(
            mainC.contains("_plat_jump_buffer"),
            "Expected '_plat_jump_buffer' jump buffer variable in generated C",
        )
    }

    // =========================================================================
    // Test 4: Wall-jump functions generated only when wallJump config present
    // =========================================================================

    @Test
    fun `wall jump functions generated only when wall jump config present`() {
        val configWithWall =
            PlatformerPhysicsConfig(
                wallJump = WallJumpConfig(wallSlideSpeed = 1, iFrameDuration = 8)
            )
        val gameIRWithWall =
            buildPlatformerGameIR("platformer_physics", "physicsConfig", configWithWall)
        val outputWithWall = pipeline.generate(gameIRWithWall)
        val mainCWithWall = outputWithWall.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainCWithWall.contains("platformer_wall_jump_update"),
            "Expected 'platformer_wall_jump_update' function when wallJump configured",
        )
        assertTrue(
            mainCWithWall.contains("_plat_wall_slide"),
            "Expected '_plat_wall_slide' variable when wallJump configured",
        )
        assertTrue(
            mainCWithWall.contains("_plat_iframes"),
            "Expected '_plat_iframes' variable when wallJump configured",
        )

        // Without wall-jump config — wall-jump function must NOT be generated
        val configNoWall = PlatformerPhysicsConfig(wallJump = null)
        val gameIRNoWall =
            buildPlatformerGameIR("platformer_physics", "physicsConfig", configNoWall, "nw")
        val outputNoWall = pipeline.generate(gameIRNoWall)
        val mainCNoWall = outputNoWall.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainCNoWall.contains("platformer_wall_jump_update"),
            "Wall-jump function must NOT be generated when wallJump = null",
        )
    }

    // =========================================================================
    // Test 5: Camera smooth-follow generates dead zone check
    // =========================================================================

    @Test
    fun `camera smooth follow generates dead zone check in camera update`() {
        val config =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SMOOTH_FOLLOW,
                deadZoneX = 8,
                deadZoneY = 16,
            )
        val gameIR = buildPlatformerGameIR("platformer_camera", "cameraConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("platformer_camera_update"),
            "Expected 'platformer_camera_update' function in generated C",
        )
        assertTrue(
            mainC.contains("Smooth-follow camera"),
            "Expected 'Smooth-follow camera' comment in camera update",
        )
        assertTrue(
            mainC.contains("_cam_x"),
            "Expected '_cam_x' camera position variable in generated C",
        )
        assertTrue(
            mainC.contains("_cam_y"),
            "Expected '_cam_y' camera position variable in generated C",
        )
    }

    // =========================================================================
    // Test 6: Camera screen-lock generates screen transition logic
    // =========================================================================

    @Test
    fun `camera screen lock generates screen transition logic`() {
        val config =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SCREEN_LOCK,
                deadZoneX = 0,
                deadZoneY = 0,
            )
        val gameIR = buildPlatformerGameIR("platformer_camera", "cameraConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("platformer_camera_update"),
            "Expected 'platformer_camera_update' function in generated C",
        )
        assertTrue(
            mainC.contains("Screen-lock camera"),
            "Expected 'Screen-lock camera' comment in camera update",
        )
        assertTrue(mainC.contains("160"), "Expected screen width 160 in screen-lock camera logic")
        assertTrue(mainC.contains("144"), "Expected screen height 144 in screen-lock camera logic")
    }

    // =========================================================================
    // Test 7: Parallax layer update function generated per parallax layer
    // =========================================================================

    @Test
    fun `parallax layer update function generated per configured layer`() {
        val config =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SMOOTH_FOLLOW,
                parallaxLayers =
                    listOf(
                        ParallaxLayer(assetId = "bg_sky", scrollSpeedX = 20, scrollSpeedY = 0),
                        ParallaxLayer(assetId = "bg_clouds", scrollSpeedX = 50, scrollSpeedY = 0),
                    ),
            )
        val gameIR = buildPlatformerGameIR("platformer_camera", "cameraConfig", config)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("platformer_parallax_scroll"),
            "Expected 'platformer_parallax_scroll' function in generated C",
        )
        assertTrue(
            mainC.contains("_parallax_offset_0"),
            "Expected '_parallax_offset_0' for first parallax layer",
        )
        assertTrue(
            mainC.contains("_parallax_offset_1"),
            "Expected '_parallax_offset_1' for second parallax layer",
        )
        // Without parallax layers — parallax function must NOT be generated
        val configNoParallax =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SMOOTH_FOLLOW,
                parallaxLayers = emptyList(),
            )
        val gameIRNoParallax =
            buildPlatformerGameIR("platformer_camera", "cameraConfig", configNoParallax, "np")
        val outputNoParallax = pipeline.generate(gameIRNoParallax)
        val mainCNoParallax = outputNoParallax.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainCNoParallax.contains("platformer_parallax_scroll"),
            "Parallax function must NOT be generated when no parallax layers configured",
        )
    }

    // =========================================================================
    // Test 8: Hazard collision check function generated
    // =========================================================================

    @Test
    fun `hazard collision check function generated`() {
        val hazard = HazardDef(id = "spikes", tileId = 42, damage = 5, instant = false)
        val gameIR = buildPlatformerGameIR("platformer_hazard", "hazard", hazard)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_hazard_collision_spikes"),
            "Expected 'check_hazard_collision_spikes' function in generated C",
        )
        assertTrue(mainC.contains("42"), "Expected hazard tile ID 42 in generated C")
        assertTrue(
            mainC.contains("_player_hp"),
            "Expected '_player_hp' damage application in hazard collision",
        )
    }

    // =========================================================================
    // Test 9: Goal zone AABB check function generated
    // =========================================================================

    @Test
    fun `goal zone AABB check function generated`() {
        val goalZone = GoalZoneDef(id = "exit_door", x = 200, y = 50, width = 16, height = 32)
        val gameIR = buildPlatformerGameIR("platformer_goal", "goalZone", goalZone)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_goal_zone_exit_door"),
            "Expected 'check_goal_zone_exit_door' function in generated C",
        )
        assertTrue(
            mainC.contains("on_goal_reached"),
            "Expected 'on_goal_reached' callback in goal zone check",
        )
        assertTrue(mainC.contains("200"), "Expected goal zone x=200 in generated C")
    }

    // =========================================================================
    // Test 10: One-way platform has directional check
    // =========================================================================

    @Test
    fun `one way platform collision has directional check`() {
        val platform = PlatformDef(id = "cloud", type = PlatformType.ONE_WAY)
        val gameIR = buildPlatformerGameIR("platformer_platform", "platform", platform)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_platform_collision_cloud"),
            "Expected 'check_platform_collision_cloud' function in generated C",
        )
        assertTrue(
            mainC.contains("One-way"),
            "Expected 'One-way' directional check comment in platform collision",
        )
        assertTrue(
            mainC.contains("_plat_vy"),
            "Expected '_plat_vy' velocity check for one-way collision direction",
        )
    }
}
