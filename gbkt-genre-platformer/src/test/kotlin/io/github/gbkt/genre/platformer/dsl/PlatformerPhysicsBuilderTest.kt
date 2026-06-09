/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests covering Phase 12 D-12 (`solidThreshold`) and D-14 (`jumpHoldMaxFrames`) additions to
 * [PlatformerPhysicsConfig] and [PlatformerPhysicsBuilder]. These fields are pure-DSL until Plans
 * 12-08 / 12-13 wire codegen.
 */
class PlatformerPhysicsBuilderTest {

    // =========================================================================
    // D-12 / D-14 — domain-level defaults (data class)
    // =========================================================================

    @Test
    fun `default PlatformerPhysicsConfig has solidThreshold null and jumpHoldMaxFrames zero`() {
        val config = PlatformerPhysicsConfig()
        assertNull(config.solidThreshold)
        assertEquals(0, config.jumpHoldMaxFrames)
    }

    // =========================================================================
    // D-12 — solidThreshold(value: Int) builder method
    // =========================================================================

    @Test
    fun `solidThreshold round-trips through buildConfig`() {
        val builder = PlatformerPhysicsBuilder()
        builder.solidThreshold(17)
        val config = builder.buildConfig()
        assertEquals(17, config.solidThreshold)
    }

    // =========================================================================
    // D-14 — jumpHold(maxFrames: Int) builder method
    // =========================================================================

    @Test
    fun `jumpHold round-trips through buildConfig`() {
        val builder = PlatformerPhysicsBuilder()
        builder.jumpHold(20)
        val config = builder.buildConfig()
        assertEquals(20, config.jumpHoldMaxFrames)
    }

    // =========================================================================
    // D-12 + D-14 coexistence — existing field defaults must be unchanged
    // =========================================================================

    @Test
    fun `solidThreshold and jumpHold coexist without disturbing existing field defaults`() {
        val builder = PlatformerPhysicsBuilder()
        builder.solidThreshold(17)
        builder.jumpHold(20)
        val config = builder.buildConfig()

        // Both new fields applied.
        assertEquals(17, config.solidThreshold)
        assertEquals(20, config.jumpHoldMaxFrames)

        // Existing field defaults must be unchanged.
        assertEquals(2, config.gravity)
        assertEquals(8, config.jumpForce)
        assertEquals(12, config.terminalVelocity)
        assertEquals(6, config.coyoteFrames)
        assertEquals(8, config.jumpBufferFrames)
        assertEquals(75, config.airControlFactor)
        assertEquals(true, config.variableHeightJump)
        assertNull(config.wallJump)
    }
}
