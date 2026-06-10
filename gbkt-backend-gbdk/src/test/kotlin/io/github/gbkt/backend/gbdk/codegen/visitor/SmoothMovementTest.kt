/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.DiagonalMode
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SmoothMovementConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// SMOOTH MOVEMENT CODEGEN TESTS
// Unit tests for ActorVisitor SMOOTH movement with acceleration/friction model.
//
// Tests:
// 1.  SMOOTH with acceleration config generates velocity variables as INT8
// 2.  SMOOTH with acceleration config generates ACCEL/FRICTION/SPEED defines
// 3.  SMOOTH with acceleration generates acceleration/friction logic in movement function
// 4.  SMOOTH with acceleration: velocity clamped to max speed
// 5.  SMOOTH with acceleration: friction drives velocity toward zero when no d-pad
// 6.  Diagonal NORMALIZED mode generates * 181 >> 8 scaling in generated C
// 7.  Diagonal RAW mode does NOT generate diagonal scaling
// 8.  SMOOTH without acceleration (legacy) — pixel-step behavior unchanged
// 9.  SMOOTH without acceleration generates no velocity variables
// 10. SMOOTH without acceleration generates no SMOOTH defines
// =============================================================================

class SmoothMovementTest {

    // =========================================================================
    // TEST 1: SMOOTH with acceleration config generates velocity variables as INT8
    // =========================================================================

    @Test
    fun `SMOOTH with acceleration config generates velocity variables as INT8`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(speed = 4, acceleration = 1, friction = 1),
                    ),
            )

        val vars = ActorVisitor.generateSmoothMovementVars(actor)

        assertEquals(2, vars.size, "Expected exactly 2 velocity variable declarations")

        val vx = vars[0]
        assertEquals("_player_vx", vx.name, "Expected _player_vx variable name")
        assertIs<CI8>(vx.type, "Expected INT8 type for velocity X")

        val vy = vars[1]
        assertEquals("_player_vy", vy.name, "Expected _player_vy variable name")
        assertIs<CI8>(vy.type, "Expected INT8 type for velocity Y")

        val emittedBlock = CEmitter.emitStatement(CBlock(vars))
        assertTrue(emittedBlock.contains("INT8"), "Expected INT8 type in emitted VX declaration")
        assertTrue(
            emittedBlock.contains("_player_vx"),
            "Expected _player_vx name in emitted declaration",
        )
        assertTrue(
            emittedBlock.contains("_player_vy"),
            "Expected _player_vy name in emitted declaration",
        )
    }

    // =========================================================================
    // TEST 2: SMOOTH with acceleration config generates ACCEL/FRICTION/SPEED defines
    // =========================================================================

    @Test
    fun `SMOOTH with acceleration config generates ACCEL FRICTION SPEED defines`() {
        val actor =
            ActorIR(
                id = "hero",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(speed = 4, acceleration = 2, friction = 1),
                    ),
            )

        val defines = ActorVisitor.generateSmoothMovementDefines(actor)

        assertEquals(3, defines.size, "Expected 3 #define constants for SMOOTH acceleration")

        val accel = defines.find { it.name == "ACCEL_HERO" }
        assertIs<CDefine>(accel, "Expected ACCEL_HERO define")
        assertEquals("2", accel.value, "Expected acceleration value of 2")

        val friction = defines.find { it.name == "FRICTION_HERO" }
        assertIs<CDefine>(friction, "Expected FRICTION_HERO define")
        assertEquals("1", friction.value, "Expected friction value of 1")

        val speed = defines.find { it.name == "SPEED_HERO" }
        assertIs<CDefine>(speed, "Expected SPEED_HERO define")
        assertEquals("4", speed.value, "Expected speed (max velocity) value of 4")
    }

    // =========================================================================
    // TEST 3: SMOOTH with acceleration generates acceleration/friction logic
    // =========================================================================

    @Test
    fun `SMOOTH with acceleration generates acceleration and friction logic in movement function`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(speed = 4, acceleration = 1, friction = 1),
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertEquals(1, functions.size, "Expected exactly one movement function")
        val fn = functions[0]
        assertEquals("update_movement_player", fn.name)

        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // Acceleration applied by d-pad
        assertTrue(emitted.contains("ACCEL_PLAYER"), "Expected ACCEL_PLAYER constant in output")
        assertTrue(emitted.contains("dpad_held(J_LEFT)"), "Expected LEFT d-pad for VX acceleration")
        assertTrue(
            emitted.contains("dpad_held(J_RIGHT)"),
            "Expected RIGHT d-pad for VX acceleration",
        )
        assertTrue(emitted.contains("dpad_held(J_UP)"), "Expected UP d-pad for VY acceleration")
        assertTrue(emitted.contains("dpad_held(J_DOWN)"), "Expected DOWN d-pad for VY acceleration")

        // Friction applied when d-pad released
        assertTrue(
            emitted.contains("FRICTION_PLAYER"),
            "Expected FRICTION_PLAYER constant in output",
        )

        // Velocity variables declared and used
        assertTrue(emitted.contains("_player_vx"), "Expected _player_vx velocity variable")
        assertTrue(emitted.contains("_player_vy"), "Expected _player_vy velocity variable")

        // Position updated from velocity
        assertTrue(
            emitted.contains("_player_x += (UINT8)_player_vx"),
            "Expected VX applied to X position with UINT8 cast",
        )
        assertTrue(
            emitted.contains("_player_y += (UINT8)_player_vy"),
            "Expected VY applied to Y position with UINT8 cast",
        )
    }

    // =========================================================================
    // TEST 4: SMOOTH with acceleration: velocity clamped to max speed
    // =========================================================================

    @Test
    fun `SMOOTH with acceleration clamps velocity to max speed in generated C`() {
        val actor =
            ActorIR(
                id = "ship",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(speed = 4, acceleration = 1, friction = 1),
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)
        val fn = functions[0]
        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // Speed clamp: SPEED_SHIP constant must appear for both VX and VY
        val speedOccurrences = "SPEED_SHIP".toRegex().findAll(emitted).count()
        assertTrue(
            speedOccurrences >= 2,
            "Expected SPEED_SHIP constant used multiple times for clamp (got $speedOccurrences)",
        )

        // Clamping uses comparison: > and <
        assertTrue(emitted.contains("SPEED_SHIP"), "Expected SPEED_SHIP in generated code")
    }

    // =========================================================================
    // TEST 5: Friction drives velocity toward zero when no d-pad input
    // =========================================================================

    @Test
    fun `SMOOTH with acceleration applies friction toward zero when no d-pad input`() {
        val actor =
            ActorIR(
                id = "ball",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(speed = 4, acceleration = 1, friction = 1),
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)
        val fn = functions[0]
        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // Friction is applied when no d-pad held in each axis
        assertTrue(
            emitted.contains("FRICTION_BALL"),
            "Expected FRICTION_BALL applied toward zero in generated output",
        )

        // Friction prevents overshoot: velocity is set to 0 when it crosses zero
        // This is checked by the presence of '= 0' in the friction body
        assertTrue(emitted.contains("= 0"), "Expected velocity clamped to 0 on friction overshoot")
    }

    // =========================================================================
    // TEST 6: Diagonal NORMALIZED mode generates * 181 >> 8 scaling
    // =========================================================================

    @Test
    fun `diagonal NORMALIZED mode generates 181 256 scaling in generated C`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(
                                speed = 4,
                                acceleration = 1,
                                friction = 1,
                                diagonalMode = DiagonalMode.NORMALIZED,
                            ),
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)
        val fn = functions[0]
        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // Diagonal normalization: both axes scaled by * 181 >> 8 (≈ 0.707)
        assertTrue(
            emitted.contains("181"),
            "Expected diagonal normalization factor 181 in generated C",
        )
        assertTrue(
            emitted.contains(">> 8"),
            "Expected right-shift 8 for diagonal normalization in generated C",
        )
        // Guard condition: both axes non-zero
        assertTrue(emitted.contains("_player_vx != 0"), "Expected VX != 0 check in diagonal guard")
        assertTrue(emitted.contains("_player_vy != 0"), "Expected VY != 0 check in diagonal guard")
    }

    // =========================================================================
    // TEST 7: Diagonal RAW mode does NOT generate diagonal scaling
    // =========================================================================

    @Test
    fun `diagonal RAW mode does NOT generate 181 scaling`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 4,
                        smoothConfig =
                            SmoothMovementConfig(
                                speed = 4,
                                acceleration = 1,
                                friction = 1,
                                diagonalMode = DiagonalMode.RAW,
                            ),
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)
        val fn = functions[0]
        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // RAW mode should NOT include diagonal scaling
        assertTrue(
            !emitted.contains("181"),
            "RAW diagonal mode should not generate 181 scaling factor",
        )
        assertTrue(
            !emitted.contains(">> 8"),
            "RAW diagonal mode should not generate right-shift for scaling",
        )
    }

    // =========================================================================
    // TEST 8: SMOOTH without acceleration (legacy) — pixel-step behavior unchanged
    // =========================================================================

    @Test
    fun `SMOOTH without acceleration preserves legacy pixel-step movement behavior`() {
        val actor =
            ActorIR(
                id = "hero",
                position = PositionDef(40, 40),
                movementConfig = MovementConfig(style = MovementStyle.SMOOTH, speed = 2),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertEquals(1, functions.size, "Expected exactly one movement function")
        val fn = functions[0]
        assertEquals("update_movement_hero", fn.name)

        // Legacy smooth mode: 4 CIf statements, direct pixel movement
        assertEquals(4, fn.body.size, "Expected 4 direction checks in legacy smooth mode")

        val emitted = CEmitter.emitStatement(CBlock(fn.body))
        assertTrue(emitted.contains("dpad_held"), "Expected dpad_held calls in legacy smooth mode")

        // Legacy smooth mode does NOT use velocity variables or ACCEL/FRICTION defines
        assertTrue(!emitted.contains("_hero_vx"), "Legacy smooth should not use VX velocity var")
        assertTrue(!emitted.contains("_hero_vy"), "Legacy smooth should not use VY velocity var")
        assertTrue(!emitted.contains("ACCEL_HERO"), "Legacy smooth should not use ACCEL define")
        assertTrue(
            !emitted.contains("FRICTION_HERO"),
            "Legacy smooth should not use FRICTION define",
        )
    }

    // =========================================================================
    // TEST 9: SMOOTH without acceleration generates no velocity variables
    // =========================================================================

    @Test
    fun `SMOOTH without acceleration generates no velocity variables`() {
        val actorLegacy =
            ActorIR(
                id = "paddle",
                position = PositionDef(16, 64),
                movementConfig = MovementConfig(style = MovementStyle.SMOOTH, speed = 4),
            )

        val vars = ActorVisitor.generateSmoothMovementVars(actorLegacy)
        assertTrue(
            vars.isEmpty(),
            "Legacy SMOOTH without acceleration should generate no velocity variables",
        )
    }

    // =========================================================================
    // TEST 10: SMOOTH without acceleration generates no SMOOTH defines
    // =========================================================================

    @Test
    fun `SMOOTH without acceleration generates no SMOOTH defines`() {
        val actorLegacy =
            ActorIR(
                id = "paddle",
                position = PositionDef(16, 64),
                movementConfig = MovementConfig(style = MovementStyle.SMOOTH, speed = 4),
            )

        val defines = ActorVisitor.generateSmoothMovementDefines(actorLegacy)
        assertTrue(
            defines.isEmpty(),
            "Legacy SMOOTH without acceleration should generate no #define constants",
        )
    }
}
