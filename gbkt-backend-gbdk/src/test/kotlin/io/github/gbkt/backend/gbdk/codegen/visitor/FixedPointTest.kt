/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.FixedPointMode
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SmoothMovementConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// FIXED-POINT ARITHMETIC TESTS
// Unit tests for fixed-point movement codegen in ActorVisitor.
//
// Tests:
//  1.  FP44 mode (PHYSICS): fractional variables declared as UINT8 (position) / INT8 (velocity)
//  2.  FP88 mode (PHYSICS): fractional variables declared as UINT16 (position) / INT16 (velocity)
//  3.  INTEGER mode (PHYSICS): no fractional variables generated (existing behavior unchanged)
//  4.  FP44 mode (PHYSICS): movement function uses >> 4 shift to extract integer position
//  5.  FP88 mode (PHYSICS): movement function uses >> 8 shift to extract integer position
//  6.  INTEGER mode (PHYSICS): movement function uses direct += assignment (no shift)
//  7.  FP44 mode (SMOOTH): fractional variables declared as UINT8 (position) / INT8 (velocity)
//  8.  FP88 mode (SMOOTH): fractional variables declared as UINT16 (position) / INT16 (velocity)
//  9.  INTEGER mode (SMOOTH): no fractional variables (existing behavior unchanged)
// 10.  FP44 mode (SMOOTH): movement function uses >> 4 shift to extract integer position
// 11.  FP88 mode (SMOOTH): movement function uses >> 8 shift to extract integer position
// 12.  Per-actor: two actors with different modes each generate correct vars
// 13.  FP44 fractional variables have correct names: _actorId_x_frac, _actorId_vx_frac, etc.
// 14.  FP88 fractional variables have correct names matching the actor ID prefix
// 15.  PHYSICS + FP44: gravity path generates fractional accumulation code
// =============================================================================

class FixedPointTest {

    // =========================================================================
    // Test helpers
    // =========================================================================

    private fun physicsActorFP44(id: String = "player"): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(80, 72),
            physicsConfig =
                PhysicsConfig(gravity = 1, maxFallSpeed = 8, fixedPointMode = FixedPointMode.FP44),
            movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
        )

    private fun physicsActorFP88(id: String = "player"): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(80, 72),
            physicsConfig =
                PhysicsConfig(gravity = 1, maxFallSpeed = 8, fixedPointMode = FixedPointMode.FP88),
            movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
        )

    private fun physicsActorInteger(id: String = "player"): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(80, 72),
            physicsConfig =
                PhysicsConfig(
                    gravity = 1,
                    maxFallSpeed = 8,
                    fixedPointMode = FixedPointMode.INTEGER,
                ),
            movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
        )

    private fun smoothActorFP44(id: String = "hero"): ActorIR =
        ActorIR(
            id = id,
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
                            fixedPointMode = FixedPointMode.FP44,
                        ),
                ),
        )

    private fun smoothActorFP88(id: String = "hero"): ActorIR =
        ActorIR(
            id = id,
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
                            fixedPointMode = FixedPointMode.FP88,
                        ),
                ),
        )

    private fun smoothActorInteger(id: String = "hero"): ActorIR =
        ActorIR(
            id = id,
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
                            fixedPointMode = FixedPointMode.INTEGER,
                        ),
                ),
        )

    // =========================================================================
    // TEST 1: FP44 mode (PHYSICS): fractional variables declared as UINT8/INT8
    // =========================================================================

    @Test
    fun `FP44 PHYSICS mode generates fractional variables as UINT8 position and INT8 velocity`() {
        val actor = physicsActorFP44()

        val vars = ActorVisitor.generatePhysicsVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(4, fracVars.size, "Expected exactly 4 fractional variables for FP44")

        val xFrac = fracVars.first { it.name == "_player_x_frac" }
        val yFrac = fracVars.first { it.name == "_player_y_frac" }
        val vxFrac = fracVars.first { it.name == "_player_vx_frac" }
        val vyFrac = fracVars.first { it.name == "_player_vy_frac" }

        assertIs<CU8>(xFrac.type, "FP44: _player_x_frac should be UINT8")
        assertIs<CU8>(yFrac.type, "FP44: _player_y_frac should be UINT8")
        assertIs<CI8>(vxFrac.type, "FP44: _player_vx_frac should be INT8 (signed velocity)")
        assertIs<CI8>(vyFrac.type, "FP44: _player_vy_frac should be INT8 (signed velocity)")
    }

    // =========================================================================
    // TEST 2: FP88 mode (PHYSICS): fractional variables declared as UINT16/INT16
    // =========================================================================

    @Test
    fun `FP88 PHYSICS mode generates fractional variables as UINT16 position and INT16 velocity`() {
        val actor = physicsActorFP88()

        val vars = ActorVisitor.generatePhysicsVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(4, fracVars.size, "Expected exactly 4 fractional variables for FP88")

        val xFrac = fracVars.first { it.name == "_player_x_frac" }
        val yFrac = fracVars.first { it.name == "_player_y_frac" }
        val vxFrac = fracVars.first { it.name == "_player_vx_frac" }
        val vyFrac = fracVars.first { it.name == "_player_vy_frac" }

        assertIs<CU16>(xFrac.type, "FP88: _player_x_frac should be UINT16")
        assertIs<CU16>(yFrac.type, "FP88: _player_y_frac should be UINT16")
        assertIs<CI16>(vxFrac.type, "FP88: _player_vx_frac should be INT16 (signed velocity)")
        assertIs<CI16>(vyFrac.type, "FP88: _player_vy_frac should be INT16 (signed velocity)")
    }

    // =========================================================================
    // TEST 3: INTEGER mode (PHYSICS): no fractional variables
    // =========================================================================

    @Test
    fun `INTEGER PHYSICS mode generates no fractional variables`() {
        val actor = physicsActorInteger()

        val vars = ActorVisitor.generatePhysicsVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(0, fracVars.size, "INTEGER mode should generate no fractional variables")

        // Should still have the standard vx, vy variables
        assertTrue(vars.any { it.name == "_player_vx" }, "Should still have _player_vx")
        assertTrue(vars.any { it.name == "_player_vy" }, "Should still have _player_vy")
    }

    // =========================================================================
    // TEST 4: FP44 mode (PHYSICS): >> 4 shift in movement function
    // =========================================================================

    @Test
    fun `FP44 PHYSICS movement function uses right-shift 4 for integer position extraction`() {
        val actor = physicsActorFP44()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        assertTrue(emitted.contains(">> 4"), "FP44 should use >> 4 shift for position extraction")
        assertFalse(emitted.contains(">> 8"), "FP44 should NOT use >> 8 shift")
    }

    // =========================================================================
    // TEST 5: FP88 mode (PHYSICS): >> 8 shift in movement function
    // =========================================================================

    @Test
    fun `FP88 PHYSICS movement function uses right-shift 8 for integer position extraction`() {
        val actor = physicsActorFP88()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        assertTrue(emitted.contains(">> 8"), "FP88 should use >> 8 shift for position extraction")
        assertFalse(emitted.contains(">> 4"), "FP88 should NOT use >> 4 shift")
    }

    // =========================================================================
    // TEST 6: INTEGER mode (PHYSICS): direct += without bit shift
    // =========================================================================

    @Test
    fun `INTEGER PHYSICS movement function uses direct position update without bit shift`() {
        val actor = physicsActorInteger()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        assertFalse(emitted.contains(">> 4"), "INTEGER mode should not use >> 4 shift")
        assertFalse(emitted.contains(">> 8"), "INTEGER mode should not use >> 8 shift")
        // Should use direct cast+add pattern
        assertTrue(emitted.contains("(UINT8)"), "INTEGER mode should use UINT8 cast for velocity")
    }

    // =========================================================================
    // TEST 7: FP44 mode (SMOOTH): fractional variables declared as UINT8/INT8
    // =========================================================================

    @Test
    fun `FP44 SMOOTH mode generates fractional variables as UINT8 position and INT8 velocity`() {
        val actor = smoothActorFP44()

        val vars = ActorVisitor.generateSmoothMovementVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(4, fracVars.size, "Expected exactly 4 fractional variables for SMOOTH FP44")

        val xFrac = fracVars.first { it.name == "_hero_x_frac" }
        val yFrac = fracVars.first { it.name == "_hero_y_frac" }
        val vxFrac = fracVars.first { it.name == "_hero_vx_frac" }
        val vyFrac = fracVars.first { it.name == "_hero_vy_frac" }

        assertIs<CU8>(xFrac.type, "SMOOTH FP44: _hero_x_frac should be UINT8")
        assertIs<CU8>(yFrac.type, "SMOOTH FP44: _hero_y_frac should be UINT8")
        assertIs<CI8>(vxFrac.type, "SMOOTH FP44: _hero_vx_frac should be INT8")
        assertIs<CI8>(vyFrac.type, "SMOOTH FP44: _hero_vy_frac should be INT8")
    }

    // =========================================================================
    // TEST 8: FP88 mode (SMOOTH): fractional variables declared as UINT16/INT16
    // =========================================================================

    @Test
    fun `FP88 SMOOTH mode generates fractional variables as UINT16 position and INT16 velocity`() {
        val actor = smoothActorFP88()

        val vars = ActorVisitor.generateSmoothMovementVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(4, fracVars.size, "Expected exactly 4 fractional variables for SMOOTH FP88")

        val xFrac = fracVars.first { it.name == "_hero_x_frac" }
        val yFrac = fracVars.first { it.name == "_hero_y_frac" }
        val vxFrac = fracVars.first { it.name == "_hero_vx_frac" }
        val vyFrac = fracVars.first { it.name == "_hero_vy_frac" }

        assertIs<CU16>(xFrac.type, "SMOOTH FP88: _hero_x_frac should be UINT16")
        assertIs<CU16>(yFrac.type, "SMOOTH FP88: _hero_y_frac should be UINT16")
        assertIs<CI16>(vxFrac.type, "SMOOTH FP88: _hero_vx_frac should be INT16")
        assertIs<CI16>(vyFrac.type, "SMOOTH FP88: _hero_vy_frac should be INT16")
    }

    // =========================================================================
    // TEST 9: INTEGER mode (SMOOTH): no fractional variables
    // =========================================================================

    @Test
    fun `INTEGER SMOOTH mode generates no fractional variables`() {
        val actor = smoothActorInteger()

        val vars = ActorVisitor.generateSmoothMovementVars(actor)

        val fracVars = vars.filter { it.name.contains("_frac") }
        assertEquals(
            0,
            fracVars.size,
            "INTEGER SMOOTH mode should generate no fractional variables",
        )

        // Should still have the standard vx, vy variables
        assertTrue(vars.any { it.name == "_hero_vx" }, "Should still have _hero_vx")
        assertTrue(vars.any { it.name == "_hero_vy" }, "Should still have _hero_vy")
    }

    // =========================================================================
    // TEST 10: FP44 mode (SMOOTH): >> 4 shift in movement function
    // =========================================================================

    @Test
    fun `FP44 SMOOTH movement function uses right-shift 4 for integer position extraction`() {
        val actor = smoothActorFP44()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        assertTrue(
            emitted.contains(">> 4"),
            "SMOOTH FP44 should use >> 4 shift for position extraction",
        )
        assertFalse(emitted.contains(">> 8"), "SMOOTH FP44 should NOT use >> 8 shift")
    }

    // =========================================================================
    // TEST 11: FP88 mode (SMOOTH): >> 8 shift in movement function
    // =========================================================================

    @Test
    fun `FP88 SMOOTH movement function uses right-shift 8 for integer position extraction`() {
        val actor = smoothActorFP88()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        assertTrue(
            emitted.contains(">> 8"),
            "SMOOTH FP88 should use >> 8 shift for position extraction",
        )
        assertFalse(emitted.contains(">> 4"), "SMOOTH FP88 should NOT use >> 4 shift")
    }

    // =========================================================================
    // TEST 12: Per-actor modes: two actors with different modes each correct
    // =========================================================================

    @Test
    fun `Two actors with different fixed-point modes each generate correct variables`() {
        val fp44Actor = physicsActorFP44(id = "player")
        val integerActor = physicsActorInteger(id = "ball")

        val fp44Vars = ActorVisitor.generatePhysicsVars(fp44Actor)
        val integerVars = ActorVisitor.generatePhysicsVars(integerActor)

        // FP44 actor should have fractional vars
        val fp44FracVars = fp44Vars.filter { it.name.contains("_frac") }
        assertEquals(4, fp44FracVars.size, "FP44 actor should have 4 fractional vars")
        assertTrue(
            fp44FracVars.all { it.name.startsWith("_player_") },
            "FP44 frac vars should be for player",
        )

        // INTEGER actor should have no fractional vars
        val integerFracVars = integerVars.filter { it.name.contains("_frac") }
        assertEquals(0, integerFracVars.size, "INTEGER actor should have no fractional vars")

        // INTEGER actor should still have standard vx/vy
        assertTrue(integerVars.any { it.name == "_ball_vx" }, "INTEGER actor should have _ball_vx")
    }

    // =========================================================================
    // TEST 13: FP44 fractional variable names use correct actor ID prefix
    // =========================================================================

    @Test
    fun `FP44 fractional variables use correct actor ID prefix in variable names`() {
        val actor = physicsActorFP44(id = "enemy")

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val fracVarNames = vars.filter { it.name.contains("_frac") }.map { it.name }.toSet()

        assertEquals(
            setOf("_enemy_x_frac", "_enemy_y_frac", "_enemy_vx_frac", "_enemy_vy_frac"),
            fracVarNames,
            "FP44 fractional variable names must use the actor ID prefix correctly",
        )
    }

    // =========================================================================
    // TEST 14: FP88 fractional variable names use correct actor ID prefix
    // =========================================================================

    @Test
    fun `FP88 fractional variables use correct actor ID prefix in variable names`() {
        val actor = physicsActorFP88(id = "hero")

        val vars = ActorVisitor.generatePhysicsVars(actor)
        val fracVarNames = vars.filter { it.name.contains("_frac") }.map { it.name }.toSet()

        assertEquals(
            setOf("_hero_x_frac", "_hero_y_frac", "_hero_vx_frac", "_hero_vy_frac"),
            fracVarNames,
            "FP88 fractional variable names must use the actor ID prefix correctly",
        )
    }

    // =========================================================================
    // TEST 15: PHYSICS + FP44: movement function accumulates fractional velocity
    // =========================================================================

    @Test
    fun `FP44 PHYSICS movement function accumulates fractional velocity then extracts position`() {
        val actor = physicsActorFP44()

        val functions = ActorVisitor.generateMovementFunction(actor)
        assertEquals(1, functions.size, "Expected one movement function")

        val emitted = CEmitter.emitFunction(functions[0])
        // Should have fractional variable accumulations
        assertTrue(emitted.contains("_player_vx_frac"), "Should accumulate _player_vx_frac")
        assertTrue(emitted.contains("_player_x_frac"), "Should accumulate _player_x_frac")
        assertTrue(emitted.contains(">> 4"), "Should extract position with >> 4 shift")
        // Should NOT use direct += from integer velocity to position
        assertFalse(
            emitted.contains("_player_x += (UINT8)_player_vx"),
            "FP44 should not use direct velocity-to-position assignment",
        )
    }
}
