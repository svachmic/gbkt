/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.WallResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PHYSICS MOVEMENT TESTS
// Tests for advanced physics codegen extensions added in plan 06.7-05:
// variable-height jump, coyote time, wall slide/stop, wall-jump, and
// platformer vs top-down mode differentiation with per-actor configs.
//
// Tests:
//  1.  Variable-height jump: jump cut code emitted when variableJump=true
//  2.  Variable-height jump: no jump cut when variableJump=false (default)
//  3.  Coyote time: coyote counter variable declared when coyoteFrames > 0
//  4.  Coyote time: no coyote variable when coyoteFrames = 0 (default)
//  5.  Coyote time: COYOTE define emitted with correct frame count
//  6.  Wall slide: wall_contact variable declared for SLIDE response
//  7.  Wall stop: no wall_contact variable for STOP response without wallJump
//  8.  Wall-jump: kick velocity code emitted when wallJump=true
//  9.  Wall-jump: wall_contact variable declared when wallJump=true
// 10.  Wall-jump: WALLJUMP_VX and WALLJUMP_VY defines emitted
// 11.  Platformer mode: UP/DOWN d-pad acceleration NOT emitted (jump via button)
// 12.  Top-down mode: UP/DOWN d-pad acceleration emitted (both axes controlled)
// 13.  Per-actor config: two actors produce distinct #define values
// 14.  Variable-height jump: JUMP_CUT define emitted with correct multiplier
// 15.  Jump_held variable declared when variableJump=true
// =============================================================================

class PhysicsMovementTest {

    // =========================================================================
    // TEST 1: Variable-height jump: jump cut code emitted when variableJump=true
    // =========================================================================

    @Test
    fun `variable jump emits jump cut code in movement function when enabled`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(variableJump = true, jumpCutMultiplier = 2),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            )

        val fns = ActorVisitor.generateMovementFunction(actor)
        assertTrue(fns.isNotEmpty(), "Expected movement function to be generated")

        val emitted = CEmitter.emitStatement(CBlock(fns[0].body))
        assertTrue(
            emitted.contains("joypad() & J_A"),
            "Expected joypad jump button check in variable jump code",
        )
        assertTrue(
            emitted.contains("_player_jump_held"),
            "Expected jump_held variable in variable jump code",
        )
        assertTrue(
            emitted.contains("JUMP_CUT_PLAYER"),
            "Expected JUMP_CUT define in variable jump code",
        )
    }

    // =========================================================================
    // TEST 2: Variable-height jump: no jump cut code when variableJump=false
    // =========================================================================

    @Test
    fun `variable jump code NOT emitted when disabled (default)`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(variableJump = false),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            )

        val fns = ActorVisitor.generateMovementFunction(actor)
        assertTrue(fns.isNotEmpty(), "Expected movement function to be generated")

        val emitted = CEmitter.emitStatement(CBlock(fns[0].body))
        assertFalse(
            emitted.contains("_player_jump_held"),
            "Expected no jump_held code when variableJump disabled",
        )
        assertFalse(
            emitted.contains("JUMP_CUT_PLAYER"),
            "Expected no JUMP_CUT define when variableJump disabled",
        )
    }

    // =========================================================================
    // TEST 3: Coyote time: coyote counter variable declared when coyoteFrames > 0
    // =========================================================================

    @Test
    fun `coyote counter variable declared when coyoteFrames is positive`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(coyoteFrames = 4),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertTrue(
            "_player_coyote" in varNames,
            "Expected _player_coyote variable when coyoteFrames=4",
        )
    }

    // =========================================================================
    // TEST 4: Coyote time: no coyote variable when coyoteFrames = 0 (default)
    // =========================================================================

    @Test
    fun `no coyote variable declared when coyoteFrames is zero`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(coyoteFrames = 0),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertFalse("_player_coyote" in varNames, "Expected no coyote variable when coyoteFrames=0")
    }

    // =========================================================================
    // TEST 5: Coyote time: COYOTE define emitted with correct frame count
    // =========================================================================

    @Test
    fun `coyote defines emitted with correct frame count`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(coyoteFrames = 6),
            )

        val defines = ActorVisitor.generatePhysicsDefines(actor)
        val coyoteDef = defines.find { it.name == "COYOTE_PLAYER" }
        assertTrue(coyoteDef != null, "Expected COYOTE_PLAYER define when coyoteFrames=6")
        assertEquals("6", coyoteDef!!.value, "Expected COYOTE_PLAYER value to be 6")
    }

    // =========================================================================
    // TEST 6: Wall slide: wall_contact variable declared for SLIDE response
    // =========================================================================

    @Test
    fun `wall contact variable declared when wallResponse is SLIDE`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(wallResponse = WallResponse.SLIDE),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertTrue(
            "_player_wall_contact" in varNames,
            "Expected _player_wall_contact variable for SLIDE response",
        )
    }

    // =========================================================================
    // TEST 7: Wall stop: no wall_contact variable for STOP response without wallJump
    // =========================================================================

    @Test
    fun `no wall contact variable when wallResponse is STOP and wallJump disabled`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(wallResponse = WallResponse.STOP, wallJump = false),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertFalse(
            "_player_wall_contact" in varNames,
            "Expected no wall_contact for STOP response without wallJump",
        )
    }

    // =========================================================================
    // TEST 8: Wall-jump: kick velocity code emitted when wallJump=true
    // =========================================================================

    @Test
    fun `wall jump kick velocity code emitted when wallJump enabled`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig =
                    PhysicsConfig(wallJump = true, wallJumpVelocityX = 3, wallJumpVelocityY = 5),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            )

        val fns = ActorVisitor.generateMovementFunction(actor)
        assertTrue(fns.isNotEmpty(), "Expected movement function to be generated")

        val emitted = CEmitter.emitStatement(CBlock(fns[0].body))
        assertTrue(
            emitted.contains("_player_wall_contact"),
            "Expected wall_contact check in wall-jump code",
        )
        assertTrue(
            emitted.contains("WALLJUMP_VX_PLAYER"),
            "Expected WALLJUMP_VX define in wall-jump code",
        )
        assertTrue(
            emitted.contains("WALLJUMP_VY_PLAYER"),
            "Expected WALLJUMP_VY define in wall-jump code",
        )
        assertTrue(
            emitted.contains("new_buttons & J_A"),
            "Expected jump button check in wall-jump code",
        )
    }

    // =========================================================================
    // TEST 9: Wall-jump: wall_contact variable declared when wallJump=true
    // =========================================================================

    @Test
    fun `wall contact variable declared when wallJump is enabled`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(wallJump = true),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertTrue(
            "_player_wall_contact" in varNames,
            "Expected _player_wall_contact variable when wallJump=true",
        )
    }

    // =========================================================================
    // TEST 10: Wall-jump: WALLJUMP_VX and WALLJUMP_VY defines emitted
    // =========================================================================

    @Test
    fun `wall jump velocity defines emitted with correct values`() {
        val actor =
            ActorIR(
                id = "hero",
                position = PositionDef(80, 72),
                physicsConfig =
                    PhysicsConfig(wallJump = true, wallJumpVelocityX = 4, wallJumpVelocityY = 6),
            )

        val defines = ActorVisitor.generatePhysicsDefines(actor)
        val vxDef = defines.find { it.name == "WALLJUMP_VX_HERO" }
        val vyDef = defines.find { it.name == "WALLJUMP_VY_HERO" }
        assertTrue(vxDef != null, "Expected WALLJUMP_VX_HERO define")
        assertTrue(vyDef != null, "Expected WALLJUMP_VY_HERO define")
        assertEquals("4", vxDef!!.value, "Expected WALLJUMP_VX_HERO value of 4")
        assertEquals("6", vyDef!!.value, "Expected WALLJUMP_VY_HERO value of 6")
    }

    // =========================================================================
    // TEST 11: Platformer mode: UP/DOWN d-pad acceleration NOT emitted
    // =========================================================================

    @Test
    fun `platformer mode does NOT emit up down dpad acceleration`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(platformerMode = true, gravity = 1),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            )

        val fns = ActorVisitor.generateMovementFunction(actor)
        assertTrue(fns.isNotEmpty(), "Expected movement function to be generated")

        val emitted = CEmitter.emitStatement(CBlock(fns[0].body))
        // In platformer mode, UP/DOWN d-pad should not accelerate vertical velocity
        // (jump is handled by jump button, not d-pad)
        assertFalse(
            emitted.contains("J_UP") && emitted.contains("ACCEL_Y_PLAYER"),
            "Expected no J_UP → ACCEL_Y in platformer mode",
        )
    }

    // =========================================================================
    // TEST 12: Top-down mode: UP/DOWN d-pad acceleration emitted (both axes)
    // =========================================================================

    @Test
    fun `top down mode emits up down dpad acceleration on both axes`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig =
                    PhysicsConfig(platformerMode = false, accelerationX = 1, accelerationY = 1),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            )

        val fns = ActorVisitor.generateMovementFunction(actor)
        assertTrue(fns.isNotEmpty(), "Expected movement function to be generated")

        val emitted = CEmitter.emitStatement(CBlock(fns[0].body))
        // In top-down mode, both axes should have d-pad acceleration
        assertTrue(emitted.contains("J_UP"), "Expected J_UP check in top-down mode")
        assertTrue(emitted.contains("J_DOWN"), "Expected J_DOWN check in top-down mode")
        assertTrue(emitted.contains("ACCEL_Y_PLAYER"), "Expected ACCEL_Y in top-down mode")
    }

    // =========================================================================
    // TEST 13: Per-actor config: two actors with different configs have distinct #define values
    // =========================================================================

    @Test
    fun `two actors with different physics configs produce distinct define values`() {
        val player =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig =
                    PhysicsConfig(
                        gravity = 1,
                        maxFallSpeed = 8,
                        variableJump = true,
                        jumpCutMultiplier = 2,
                    ),
            )
        val enemy =
            ActorIR(
                id = "enemy",
                position = PositionDef(40, 40),
                physicsConfig =
                    PhysicsConfig(
                        gravity = 2,
                        maxFallSpeed = 12,
                        variableJump = true,
                        jumpCutMultiplier = 4,
                    ),
            )

        val playerDefines = ActorVisitor.generatePhysicsDefines(player)
        val enemyDefines = ActorVisitor.generatePhysicsDefines(enemy)

        val playerGravity = playerDefines.find { it.name == "GRAVITY_PLAYER" }
        val enemyGravity = enemyDefines.find { it.name == "GRAVITY_ENEMY" }
        assertEquals("1", playerGravity?.value, "Expected player gravity of 1")
        assertEquals("2", enemyGravity?.value, "Expected enemy gravity of 2")

        val playerJumpCut = playerDefines.find { it.name == "JUMP_CUT_PLAYER" }
        val enemyJumpCut = enemyDefines.find { it.name == "JUMP_CUT_ENEMY" }
        assertEquals("2", playerJumpCut?.value, "Expected player jump cut multiplier of 2")
        assertEquals("4", enemyJumpCut?.value, "Expected enemy jump cut multiplier of 4")

        val playerMaxFall = playerDefines.find { it.name == "MAX_FALL_PLAYER" }
        val enemyMaxFall = enemyDefines.find { it.name == "MAX_FALL_ENEMY" }
        assertEquals("8", playerMaxFall?.value, "Expected player max fall speed of 8")
        assertEquals("12", enemyMaxFall?.value, "Expected enemy max fall speed of 12")
    }

    // =========================================================================
    // TEST 14: Variable-height jump: JUMP_CUT define emitted with correct multiplier
    // =========================================================================

    @Test
    fun `jump cut define emitted with correct multiplier value`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(variableJump = true, jumpCutMultiplier = 3),
            )

        val defines = ActorVisitor.generatePhysicsDefines(actor)
        val jumpCutDef = defines.find { it.name == "JUMP_CUT_PLAYER" }
        assertTrue(jumpCutDef != null, "Expected JUMP_CUT_PLAYER define when variableJump=true")
        assertEquals("3", jumpCutDef!!.value, "Expected JUMP_CUT_PLAYER value of 3")
    }

    // =========================================================================
    // TEST 15: Jump_held variable declared when variableJump=true
    // =========================================================================

    @Test
    fun `jump held variable declared when variableJump is enabled`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(variableJump = true),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val varNames = vars.map { it.name }
        assertTrue(
            "_player_jump_held" in varNames,
            "Expected _player_jump_held variable when variableJump=true",
        )
    }
}
