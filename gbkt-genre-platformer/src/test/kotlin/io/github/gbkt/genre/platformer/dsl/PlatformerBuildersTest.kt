/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.CollectibleDef
import io.github.gbkt.genre.platformer.domain.CollectibleType
import io.github.gbkt.genre.platformer.domain.GoalZoneDef
import io.github.gbkt.genre.platformer.domain.HazardDef
import io.github.gbkt.genre.platformer.domain.LadderConfig
import io.github.gbkt.genre.platformer.domain.PlatformDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.ScrollDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformerBuildersTest {

    // =========================================================================
    // PlatformerPhysicsBuilder
    // =========================================================================

    @Test
    fun `physics builder produces GenericSystem with correct type`() {
        val system = PlatformerPhysicsBuilder("physics").build()
        assertEquals("physics", system.id)
        assertEquals("platformer_physics", system.config["type"])
    }

    @Test
    fun `physics builder captures all config values correctly`() {
        val builder = PlatformerPhysicsBuilder("physics")
        builder.gravity(3)
        builder.jumpForce(10)
        builder.terminalVelocity(15)
        builder.coyoteTime(4)
        builder.jumpBuffer(6)
        builder.airControl(50)
        val system = builder.build()

        val config = system.config["physicsConfig"] as PlatformerPhysicsConfig
        assertEquals(3, config.gravity)
        assertEquals(10, config.jumpForce)
        assertEquals(15, config.terminalVelocity)
        assertEquals(4, config.coyoteFrames)
        assertEquals(6, config.jumpBufferFrames)
        assertEquals(50, config.airControlFactor)
    }

    @Test
    fun `variable-height jump is enabled by default`() {
        val system = PlatformerPhysicsBuilder("physics").build()
        val config = system.config["physicsConfig"] as PlatformerPhysicsConfig
        assertTrue(config.variableHeightJump)
    }

    @Test
    fun `fixedJump disables variable-height jump`() {
        val builder = PlatformerPhysicsBuilder("physics")
        builder.fixedJump()
        val system = builder.build()
        val config = system.config["physicsConfig"] as PlatformerPhysicsConfig
        assertEquals(false, config.variableHeightJump)
    }

    @Test
    fun `wall-jump is null (opt-in) by default`() {
        val system = PlatformerPhysicsBuilder("physics").build()
        val config = system.config["physicsConfig"] as PlatformerPhysicsConfig
        assertNull(config.wallJump)
    }

    @Test
    fun `wall-jump builder configures wall-jump correctly`() {
        val builder = PlatformerPhysicsBuilder("physics")
        builder.wallJump {
            slideSpeed(2)
            iFrames(12)
            cooldown(8)
        }
        val system = builder.build()
        val config = system.config["physicsConfig"] as PlatformerPhysicsConfig
        val wallJump = config.wallJump
        assertNotNull(wallJump)
        assertEquals(2, wallJump.wallSlideSpeed)
        assertEquals(12, wallJump.iFrameDuration)
        assertEquals(8, wallJump.cooldownFrames)
    }

    // =========================================================================
    // PlatformerCameraBuilder
    // =========================================================================

    @Test
    fun `camera builder produces GenericSystem with correct type`() {
        val system = PlatformerCameraBuilder("camera").build()
        assertEquals("camera", system.id)
        assertEquals("platformer_camera", system.config["type"])
    }

    @Test
    fun `camera builder defaults to smooth-follow horizontal`() {
        val system = PlatformerCameraBuilder("camera").build()
        val config = system.config["cameraConfig"] as PlatformerCameraConfig
        assertEquals(CameraScrollMode.SMOOTH_FOLLOW, config.mode)
        assertEquals(ScrollDirection.HORIZONTAL, config.scrollDirections)
    }

    @Test
    fun `camera builder supports screen-lock mode`() {
        val builder = PlatformerCameraBuilder("camera")
        builder.screenLock()
        val system = builder.build()
        val config = system.config["cameraConfig"] as PlatformerCameraConfig
        assertEquals(CameraScrollMode.SCREEN_LOCK, config.mode)
    }

    @Test
    fun `camera builder supports multi-directional scroll`() {
        val builder = PlatformerCameraBuilder("camera")
        builder.multiDirectional()
        val system = builder.build()
        val config = system.config["cameraConfig"] as PlatformerCameraConfig
        assertEquals(ScrollDirection.MULTI, config.scrollDirections)
    }

    @Test
    fun `camera builder configures dead zone`() {
        val builder = PlatformerCameraBuilder("camera")
        builder.deadZone(x = 12, y = 20)
        val system = builder.build()
        val config = system.config["cameraConfig"] as PlatformerCameraConfig
        assertEquals(12, config.deadZoneX)
        assertEquals(20, config.deadZoneY)
    }

    @Test
    fun `camera builder captures parallax layers in order`() {
        val builder = PlatformerCameraBuilder("camera")
        builder.parallax("bg_sky") { speedX(20) }
        builder.parallax("bg_clouds") { speedX(50) }
        builder.parallax("bg_hills") {
            speedX(80)
            speedY(5)
        }
        val system = builder.build()
        val config = system.config["cameraConfig"] as PlatformerCameraConfig

        assertEquals(3, config.parallaxLayers.size)
        assertEquals("bg_sky", config.parallaxLayers[0].assetId)
        assertEquals(20, config.parallaxLayers[0].scrollSpeedX)
        assertEquals("bg_clouds", config.parallaxLayers[1].assetId)
        assertEquals(50, config.parallaxLayers[1].scrollSpeedX)
        assertEquals("bg_hills", config.parallaxLayers[2].assetId)
        assertEquals(80, config.parallaxLayers[2].scrollSpeedX)
        assertEquals(5, config.parallaxLayers[2].scrollSpeedY)
    }

    // =========================================================================
    // PlatformDefBuilder
    // =========================================================================

    @Test
    fun `platform builder produces GenericSystem with correct type`() {
        val system = PlatformDefBuilder("ground").build()
        assertEquals("ground", system.id)
        assertEquals("platformer_platform", system.config["type"])
    }

    @Test
    fun `platform builder defaults to solid type`() {
        val system = PlatformDefBuilder("floor").build()
        val def = system.config["platform"] as PlatformDef
        assertEquals(PlatformType.SOLID, def.type)
    }

    @Test
    fun `platform builder captures moving platform config`() {
        val builder = PlatformDefBuilder("moving_floor")
        builder.type(PlatformType.MOVING)
        builder.moveSpeed(3)
        val system = builder.build()
        val def = system.config["platform"] as PlatformDef
        assertEquals(PlatformType.MOVING, def.type)
        assertEquals(3, def.moveSpeed)
    }

    @Test
    fun `platform builder captures crumbling platform config`() {
        val builder = PlatformDefBuilder("crumble")
        builder.type(PlatformType.CRUMBLING)
        builder.crumbleDelay(20)
        builder.crumbleRespawn(90)
        val system = builder.build()
        val def = system.config["platform"] as PlatformDef
        assertEquals(PlatformType.CRUMBLING, def.type)
        assertEquals(20, def.crumbleDelay)
        assertEquals(90, def.crumbleRespawn)
    }

    @Test
    fun `one-way platform type produces correct GenericSystem type string`() {
        val builder = PlatformDefBuilder("one_way")
        builder.type(PlatformType.ONE_WAY)
        val system = builder.build()
        assertEquals("platformer_platform", system.config["type"])
        val def = system.config["platform"] as PlatformDef
        assertEquals(PlatformType.ONE_WAY, def.type)
    }

    // =========================================================================
    // HazardDefBuilder
    // =========================================================================

    @Test
    fun `hazard builder produces GenericSystem with correct type`() {
        val system = HazardDefBuilder("spikes").build()
        assertEquals("spikes", system.id)
        assertEquals("platformer_hazard", system.config["type"])
    }

    @Test
    fun `hazard builder captures damage and instant-death flag`() {
        val builder = HazardDefBuilder("spikes")
        builder.tileId(42)
        builder.damage(5)
        val system = builder.build()
        val def = system.config["hazard"] as HazardDef
        assertEquals(42, def.tileId)
        assertEquals(5, def.damage)
        assertEquals(false, def.instant)
    }

    @Test
    fun `hazard builder supports instant death flag`() {
        val builder = HazardDefBuilder("pit")
        builder.tileId(0)
        builder.instantDeath()
        val system = builder.build()
        val def = system.config["hazard"] as HazardDef
        assertEquals(true, def.instant)
    }

    // =========================================================================
    // GoalZoneBuilder
    // =========================================================================

    @Test
    fun `goal zone builder produces GenericSystem with correct type`() {
        val system = GoalZoneBuilder("exit").build()
        assertEquals("exit", system.id)
        assertEquals("platformer_goal", system.config["type"])
    }

    @Test
    fun `goal zone captures coordinates and dimensions`() {
        val builder = GoalZoneBuilder("exit")
        builder.position(x = 200, y = 50)
        builder.size(width = 24, height = 32)
        val system = builder.build()
        val def = system.config["goalZone"] as GoalZoneDef
        assertEquals(200, def.x)
        assertEquals(50, def.y)
        assertEquals(24, def.width)
        assertEquals(32, def.height)
    }

    @Test
    fun `goal zone builder has sensible defaults`() {
        val system = GoalZoneBuilder("exit").build()
        val def = system.config["goalZone"] as GoalZoneDef
        assertEquals(0, def.x)
        assertEquals(0, def.y)
        assertEquals(16, def.width)
        assertEquals(16, def.height)
    }

    // =========================================================================
    // CollectibleDefBuilder
    // =========================================================================

    @Test
    fun `collectible builder produces GenericSystem with correct type`() {
        val system = CollectibleDefBuilder("gold_coin").build()
        assertEquals("gold_coin", system.id)
        assertEquals("platformer_collectible", system.config["type"])
    }

    @Test
    fun `collectible builder captures type and value`() {
        val builder = CollectibleDefBuilder("gold_coin")
        builder.type(CollectibleType.COIN)
        builder.value(10)
        builder.tileId(5)
        val system = builder.build()
        val def = system.config["collectible"] as CollectibleDef
        assertEquals(CollectibleType.COIN, def.type)
        assertEquals(10, def.value)
        assertEquals(5, def.tileId)
    }

    @Test
    fun `collectible builder supports key type`() {
        val builder = CollectibleDefBuilder("bronze_key")
        builder.type(CollectibleType.KEY)
        builder.value(1)
        val system = builder.build()
        val def = system.config["collectible"] as CollectibleDef
        assertEquals(CollectibleType.KEY, def.type)
    }

    // =========================================================================
    // LadderConfigBuilder
    // =========================================================================

    @Test
    fun `ladder builder produces GenericSystem with correct type`() {
        val system = LadderConfigBuilder("ladder").build()
        assertEquals("ladder", system.id)
        assertEquals("platformer_ladder", system.config["type"])
    }

    @Test
    fun `ladder builder captures climb speed and tile id`() {
        val builder = LadderConfigBuilder("ladder")
        builder.climbSpeed(3)
        builder.tileId(15)
        val system = builder.build()
        val config = system.config["ladderConfig"] as LadderConfig
        assertEquals(3, config.climbSpeed)
        assertEquals(15, config.tileId)
    }

    @Test
    fun `ladder builder has sensible defaults`() {
        val system = LadderConfigBuilder().build()
        val config = system.config["ladderConfig"] as LadderConfig
        assertEquals(2, config.climbSpeed)
        assertEquals(0, config.tileId)
    }
}
