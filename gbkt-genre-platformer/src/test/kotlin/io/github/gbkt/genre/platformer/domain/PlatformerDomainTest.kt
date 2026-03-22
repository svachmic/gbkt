/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformerDomainTest {

    // -------------------------------------------------------------------------
    // PlatformerPhysicsConfig defaults
    // -------------------------------------------------------------------------

    @Test
    fun `physics config has sensible defaults`() {
        val config = PlatformerPhysicsConfig()
        assertEquals(2, config.gravity)
        assertEquals(8, config.jumpForce)
        assertEquals(12, config.terminalVelocity)
        assertEquals(6, config.coyoteFrames)
        assertEquals(8, config.jumpBufferFrames)
        assertEquals(75, config.airControlFactor)
        assertTrue(config.variableHeightJump)
        assertNull(config.wallJump)
    }

    @Test
    fun `wall jump config has sensible defaults`() {
        val config = WallJumpConfig()
        assertEquals(1, config.wallSlideSpeed)
        assertEquals(8, config.iFrameDuration)
        assertEquals(10, config.cooldownFrames)
    }

    @Test
    fun `physics config stores custom values`() {
        val config =
            PlatformerPhysicsConfig(
                gravity = 3,
                jumpForce = 10,
                terminalVelocity = 15,
                coyoteFrames = 4,
                jumpBufferFrames = 6,
                airControlFactor = 50,
                variableHeightJump = false,
                wallJump =
                    WallJumpConfig(wallSlideSpeed = 2, iFrameDuration = 12, cooldownFrames = 8),
            )
        assertEquals(3, config.gravity)
        assertEquals(10, config.jumpForce)
        assertEquals(15, config.terminalVelocity)
        assertEquals(4, config.coyoteFrames)
        assertEquals(6, config.jumpBufferFrames)
        assertEquals(50, config.airControlFactor)
        assertEquals(false, config.variableHeightJump)
        val wallJump = config.wallJump
        assertEquals(2, wallJump?.wallSlideSpeed)
        assertEquals(12, wallJump?.iFrameDuration)
        assertEquals(8, wallJump?.cooldownFrames)
    }

    // -------------------------------------------------------------------------
    // PlatformType enum completeness
    // -------------------------------------------------------------------------

    @Test
    fun `platform type enum has all expected values`() {
        val types = PlatformType.values()
        assertTrue(types.contains(PlatformType.SOLID))
        assertTrue(types.contains(PlatformType.ONE_WAY))
        assertTrue(types.contains(PlatformType.MOVING))
        assertTrue(types.contains(PlatformType.CRUMBLING))
        assertEquals(4, types.size)
    }

    @Test
    fun `platform def defaults to solid type`() {
        val def = PlatformDef(id = "ground")
        assertEquals("ground", def.id)
        assertEquals(PlatformType.SOLID, def.type)
    }

    @Test
    fun `platform def stores crumbling config`() {
        val def =
            PlatformDef(
                id = "crumble_1",
                type = PlatformType.CRUMBLING,
                crumbleDelay = 20,
                crumbleRespawn = 60,
            )
        assertEquals(PlatformType.CRUMBLING, def.type)
        assertEquals(20, def.crumbleDelay)
        assertEquals(60, def.crumbleRespawn)
    }

    // -------------------------------------------------------------------------
    // Camera config with parallax
    // -------------------------------------------------------------------------

    @Test
    fun `camera config defaults to smooth follow horizontal`() {
        val config = PlatformerCameraConfig()
        assertEquals(CameraScrollMode.SMOOTH_FOLLOW, config.mode)
        assertEquals(ScrollDirection.HORIZONTAL, config.scrollDirections)
        assertEquals(8, config.deadZoneX)
        assertEquals(16, config.deadZoneY)
        assertTrue(config.parallaxLayers.isEmpty())
    }

    @Test
    fun `camera config stores parallax layers in order`() {
        val layers =
            listOf(
                ParallaxLayer(assetId = "bg_far", scrollSpeedX = 20, scrollSpeedY = 0),
                ParallaxLayer(assetId = "bg_mid", scrollSpeedX = 50, scrollSpeedY = 0),
                ParallaxLayer(assetId = "bg_near", scrollSpeedX = 80, scrollSpeedY = 10),
            )
        val config = PlatformerCameraConfig(parallaxLayers = layers)
        assertEquals(3, config.parallaxLayers.size)
        assertEquals("bg_far", config.parallaxLayers[0].assetId)
        assertEquals(20, config.parallaxLayers[0].scrollSpeedX)
        assertEquals("bg_mid", config.parallaxLayers[1].assetId)
        assertEquals("bg_near", config.parallaxLayers[2].assetId)
        assertEquals(10, config.parallaxLayers[2].scrollSpeedY)
    }

    @Test
    fun `screen lock mode can be configured`() {
        val config =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SCREEN_LOCK,
                scrollDirections = ScrollDirection.MULTI,
            )
        assertEquals(CameraScrollMode.SCREEN_LOCK, config.mode)
        assertEquals(ScrollDirection.MULTI, config.scrollDirections)
    }

    @Test
    fun `scroll direction enum has all expected values`() {
        val directions = ScrollDirection.values()
        assertTrue(directions.contains(ScrollDirection.HORIZONTAL))
        assertTrue(directions.contains(ScrollDirection.VERTICAL))
        assertTrue(directions.contains(ScrollDirection.MULTI))
        assertEquals(3, directions.size)
    }

    // -------------------------------------------------------------------------
    // Hazard and goal zone construction
    // -------------------------------------------------------------------------

    @Test
    fun `hazard def stores tile and damage values`() {
        val hazard = HazardDef(id = "spikes", tileId = 42, damage = 5, instant = false)
        assertEquals("spikes", hazard.id)
        assertEquals(42, hazard.tileId)
        assertEquals(5, hazard.damage)
        assertEquals(false, hazard.instant)
    }

    @Test
    fun `hazard def supports instant death`() {
        val hazard = HazardDef(id = "pit", tileId = 0, instant = true)
        assertTrue(hazard.instant)
    }

    @Test
    fun `goal zone def stores coordinates and dimensions`() {
        val goal = GoalZoneDef(id = "exit", x = 100, y = 50, width = 24, height = 32)
        assertEquals("exit", goal.id)
        assertEquals(100, goal.x)
        assertEquals(50, goal.y)
        assertEquals(24, goal.width)
        assertEquals(32, goal.height)
    }

    @Test
    fun `goal zone has sensible defaults`() {
        val goal = GoalZoneDef(id = "exit")
        assertEquals(0, goal.x)
        assertEquals(0, goal.y)
        assertEquals(16, goal.width)
        assertEquals(16, goal.height)
    }

    // -------------------------------------------------------------------------
    // Collectible and Ladder types
    // -------------------------------------------------------------------------

    @Test
    fun `collectible type enum has all expected values`() {
        val types = CollectibleType.values()
        assertTrue(types.contains(CollectibleType.COIN))
        assertTrue(types.contains(CollectibleType.POWER_UP))
        assertTrue(types.contains(CollectibleType.CHECKPOINT))
        assertTrue(types.contains(CollectibleType.KEY))
        assertEquals(4, types.size)
    }

    @Test
    fun `collectible def stores type and value`() {
        val coin =
            CollectibleDef(id = "gold_coin", type = CollectibleType.COIN, value = 10, tileId = 5)
        assertEquals("gold_coin", coin.id)
        assertEquals(CollectibleType.COIN, coin.type)
        assertEquals(10, coin.value)
        assertEquals(5, coin.tileId)
    }

    @Test
    fun `ladder config stores climb speed and tile id`() {
        val ladder = LadderConfig(climbSpeed = 3, tileId = 12)
        assertEquals(3, ladder.climbSpeed)
        assertEquals(12, ladder.tileId)
    }

    @Test
    fun `ladder config has sensible defaults`() {
        val ladder = LadderConfig()
        assertEquals(2, ladder.climbSpeed)
        assertEquals(0, ladder.tileId)
    }
}
