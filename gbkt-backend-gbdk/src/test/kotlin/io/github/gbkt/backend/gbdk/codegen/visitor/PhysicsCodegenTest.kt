/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PhysicsStep
import io.github.gbkt.core.ir.PositionDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// PHYSICS CODEGEN TESTS
// Unit tests for ActorVisitor physics variable generation and
// ScriptOpVisitor.visitPhysicsStep codegen.
//
// Tests:
// 1.  Physics config generates velocity variables as INT8
// 2.  Physics config generates acceleration, gravity, and bounce defines
// 3.  visitPhysicsStep generates acceleration before gravity application
// 4.  visitPhysicsStep generates fall speed clamping
// 5.  visitPhysicsStep generates velocity to position transfer
// 6.  Actors without physics generate no velocity variables
// 7.  Actor without physics generates no #define constants
// 8.  PhysicsConfig default values are reflected in generated code
// =============================================================================

class PhysicsCodegenTest {

    // =========================================================================
    // TEST 1: Physics config generates velocity variables as INT8
    // =========================================================================

    @Test
    fun `physics config generates velocity variables as INT8`() {
        val actor =
            ActorIR(
                id = "ball",
                position = PositionDef(80, 72),
                physicsConfig = PhysicsConfig(velocityX = 0, velocityY = -2),
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)

        assertEquals(2, vars.size, "Expected exactly 2 velocity variable declarations")

        val vx = vars[0]
        assertEquals("_ball_vx", vx.name, "Expected _ball_vx variable name")
        assertIs<CI8>(vx.type, "Expected INT8 type for velocity X")

        val vy = vars[1]
        assertEquals("_ball_vy", vy.name, "Expected _ball_vy variable name")
        assertIs<CI8>(vy.type, "Expected INT8 type for velocity Y")

        // Verify emitted C text contains INT8 type and variable names
        val emittedBlock = CEmitter.emitStatement(CBlock(vars))
        assertTrue(emittedBlock.contains("INT8"), "Expected INT8 type in emitted VX declaration")
        assertTrue(
            emittedBlock.contains("_ball_vx"),
            "Expected _ball_vx name in emitted declaration",
        )
        assertTrue(
            emittedBlock.contains("_ball_vy"),
            "Expected _ball_vy name in emitted declaration",
        )
    }

    // =========================================================================
    // TEST 2: Physics config generates acceleration, gravity, and bounce defines
    // =========================================================================

    @Test
    fun `physics config generates acceleration gravity and bounce defines`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                physicsConfig =
                    PhysicsConfig(
                        accelerationX = 2,
                        accelerationY = 0,
                        gravity = 1,
                        bounce = 204,
                        maxFallSpeed = 8,
                    ),
            )

        val defines = ActorVisitor.generatePhysicsDefines(actor)

        assertEquals(5, defines.size, "Expected 5 #define constants for physics")

        val accelX = defines.find { it.name == "ACCEL_X_PLAYER" }
        assertIs<CDefine>(accelX, "Expected ACCEL_X_PLAYER define")
        assertEquals("2", accelX.value, "Expected acceleration X value of 2")

        val accelY = defines.find { it.name == "ACCEL_Y_PLAYER" }
        assertIs<CDefine>(accelY, "Expected ACCEL_Y_PLAYER define")
        assertEquals("0", accelY.value, "Expected acceleration Y value of 0")

        val gravity = defines.find { it.name == "GRAVITY_PLAYER" }
        assertIs<CDefine>(gravity, "Expected GRAVITY_PLAYER define")
        assertEquals("1", gravity.value, "Expected gravity value of 1")

        val maxFall = defines.find { it.name == "MAX_FALL_PLAYER" }
        assertIs<CDefine>(maxFall, "Expected MAX_FALL_PLAYER define")
        assertEquals("8", maxFall.value, "Expected max fall speed of 8")

        val bounce = defines.find { it.name == "BOUNCE_PLAYER" }
        assertIs<CDefine>(bounce, "Expected BOUNCE_PLAYER define")
        assertEquals("204", bounce.value, "Expected bounce coefficient of 204 (≈0.8 * 256)")
    }

    // =========================================================================
    // TEST 3: visitPhysicsStep generates acceleration then gravity application
    // =========================================================================

    @Test
    fun `visitPhysicsStep generates acceleration then gravity application`() {
        val op = PhysicsStep(actorId = "ball")

        val statement = ScriptOpVisitor.visit(op)

        assertIs<CBlock>(statement, "Expected CBlock from visitPhysicsStep")
        assertTrue(
            statement.statements.size >= 3,
            "Expected at least 3 statements (accel X, accel Y, gravity)",
        )

        // First statement: apply ACCEL_X to vx
        val stmt0 = statement.statements[0]
        assertIs<CExprStatement>(stmt0, "First statement should be CExprStatement (accel X)")
        val accelXExpr = stmt0.expr
        assertIs<CBinaryExpr>(accelXExpr, "Expected CBinaryExpr for ACCEL_X assignment")
        assertEquals("+=", accelXExpr.op, "Expected += for acceleration X")

        // Second statement: apply ACCEL_Y to vy
        val stmt1 = statement.statements[1]
        assertIs<CExprStatement>(stmt1, "Second statement should be CExprStatement (accel Y)")
        val accelYExpr = stmt1.expr
        assertIs<CBinaryExpr>(accelYExpr, "Expected CBinaryExpr for ACCEL_Y assignment")

        // Third statement: apply gravity to vy
        val stmt2 = statement.statements[2]
        assertIs<CExprStatement>(stmt2, "Third statement should be CExprStatement (gravity)")
        val gravityExpr = stmt2.expr
        assertIs<CBinaryExpr>(gravityExpr, "Expected CBinaryExpr for gravity application")

        // Verify emitted order: ACCEL_X_BALL, ACCEL_Y_BALL, GRAVITY_BALL appear in order
        val emittedFull = CEmitter.emitStatement(statement)
        val accelXPos = emittedFull.indexOf("ACCEL_X_BALL")
        val accelYPos = emittedFull.indexOf("ACCEL_Y_BALL")
        val gravityPos = emittedFull.indexOf("GRAVITY_BALL")
        assertTrue(accelXPos >= 0, "Expected ACCEL_X_BALL constant in output")
        assertTrue(accelYPos >= 0, "Expected ACCEL_Y_BALL constant in output")
        assertTrue(gravityPos >= 0, "Expected GRAVITY_BALL constant in output")
        assertTrue(accelXPos < gravityPos, "Expected ACCEL_X to be applied before GRAVITY")
        assertTrue(accelYPos < gravityPos, "Expected ACCEL_Y to be applied before GRAVITY")
    }

    // =========================================================================
    // TEST 4: visitPhysicsStep generates fall speed clamping
    // =========================================================================

    @Test
    fun `visitPhysicsStep generates fall speed clamping`() {
        val op = PhysicsStep(actorId = "player")

        val statement = ScriptOpVisitor.visit(op)

        assertIs<CBlock>(statement, "Expected CBlock from visitPhysicsStep")

        // 4th statement (index 3) should be CIf for fall speed clamp
        assertTrue(
            statement.statements.size >= 4,
            "Expected at least 4 statements including fall clamp",
        )
        val clampStmt = statement.statements[3]
        assertIs<CIf>(clampStmt, "Expected CIf for fall speed clamping")

        val emittedClamp = CEmitter.emitStatement(clampStmt)
        assertTrue(emittedClamp.contains("_player_vy"), "Expected _player_vy in fall clamp check")
        assertTrue(
            emittedClamp.contains("MAX_FALL_PLAYER"),
            "Expected MAX_FALL_PLAYER in fall clamp",
        )
        assertTrue(emittedClamp.contains(">"), "Expected > comparison in fall clamp condition")
    }

    // =========================================================================
    // TEST 5: visitPhysicsStep generates velocity to position transfer
    // =========================================================================

    @Test
    fun `visitPhysicsStep generates velocity to position transfer`() {
        val op = PhysicsStep(actorId = "ball")

        val statement = ScriptOpVisitor.visit(op)

        assertIs<CBlock>(statement, "Expected CBlock from visitPhysicsStep")

        // Must have 6 statements: accel X, accel Y, gravity, clamp, y+=vy, x+=vx
        assertEquals(
            6,
            statement.statements.size,
            "Expected exactly 6 statements in PhysicsStep output",
        )

        val emittedFull = CEmitter.emitStatement(statement)

        // Position update: _ball_y += (UINT8)_ball_vy
        assertTrue(emittedFull.contains("_ball_y"), "Expected _ball_y in position update")
        // Position update: _ball_x += (UINT8)_ball_vx
        assertTrue(emittedFull.contains("_ball_x"), "Expected _ball_x in position update")
        // Velocity cast to UINT8 for position update
        assertTrue(
            emittedFull.contains("UINT8"),
            "Expected UINT8 cast in velocity-to-position transfer",
        )
        // Both velocity variables referenced for position update
        val vyIdx = emittedFull.indexOf("_ball_vy")
        val vxIdx = emittedFull.indexOf("_ball_vx")
        assertTrue(vyIdx >= 0, "Expected _ball_vy in output")
        assertTrue(vxIdx >= 0, "Expected _ball_vx in output")
    }

    // =========================================================================
    // TEST 6: Actors without physics generate no velocity variables
    // =========================================================================

    @Test
    fun `actors without physics generate no velocity variables`() {
        val actorNoPhysics =
            ActorIR(id = "paddle", position = PositionDef(16, 64), physicsConfig = null)

        val vars = ActorVisitor.generatePhysicsVars(actorNoPhysics)
        assertTrue(vars.isEmpty(), "Expected no velocity variables for actor without physics")
    }

    // =========================================================================
    // TEST 7: Actor without physics generates no #define constants
    // =========================================================================

    @Test
    fun `actors without physics generate no define constants`() {
        val actorNoPhysics =
            ActorIR(id = "paddle", position = PositionDef(16, 64), physicsConfig = null)

        val defines = ActorVisitor.generatePhysicsDefines(actorNoPhysics)
        assertTrue(defines.isEmpty(), "Expected no #define constants for actor without physics")
    }

    // =========================================================================
    // TEST 8: PhysicsConfig default values are reflected in generated code
    // =========================================================================

    @Test
    fun `physics config default values are used when not explicitly set`() {
        val actor =
            ActorIR(
                id = "coin",
                position = PositionDef(80, 40),
                physicsConfig = PhysicsConfig(), // all defaults
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        assertEquals(2, vars.size, "Expected 2 velocity variables even with default physics config")

        val defines = ActorVisitor.generatePhysicsDefines(actor)
        assertEquals(5, defines.size, "Expected 5 #define constants with default physics config")

        // Defaults: accel=0, gravity=0, maxFallSpeed=8, bounce=0
        val gravity = defines.find { it.name == "GRAVITY_COIN" }
        assertEquals("0", gravity?.value, "Default gravity should be 0")

        val maxFall = defines.find { it.name == "MAX_FALL_COIN" }
        assertEquals("8", maxFall?.value, "Default maxFallSpeed should be 8")

        val bounce = defines.find { it.name == "BOUNCE_COIN" }
        assertEquals("0", bounce?.value, "Default bounce should be 0")
    }
}
