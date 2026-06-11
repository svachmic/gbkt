/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AnimTransition
import io.github.gbkt.core.ir.AnimationStateDef
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SetAnimationState
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// MOVEMENT AND ANIMATION CODEGEN TESTS
// Unit tests for ActorVisitor movement and animation code generation methods,
// and ScriptOpVisitor.visitSetAnimationState codegen.
//
// Tests:
// 1.  Grid movement generates dpad-driven position updates with bounds checking
// 2.  Smooth movement generates pixel-level updates without bounds checking
// 3.  Simple animation generates typed frame counter (no state machine)
// 4.  Animation state machine generates enum #defines for all states
// 5.  Animation state machine generates CSwitch-based update function
// 6.  setAnimationState generates state assignment + frame/counter reset
// 7.  Animation state with transition condition generates CIf check
// 8.  Actor without movement config generates no movement function
// 9.  PHYSICS mode generates dpad→acceleration→velocity→position movement function
// 9a. PHYSICS mode with gravity emits gravity and fall clamp statements
// 9b. PHYSICS mode without gravity omits gravity and fall clamp statements
// 9c. PHYSICS mode with explicit PhysicsConfig uses those parameters
// 10. Animation vars produced for state machine actor only
// 11. Simple animation vars produced for frameSpeed-only actor
// 12. No animation vars for actor without animation config
// =============================================================================

class MovementAnimationCodegenTest {

    // =========================================================================
    // TEST 1: Grid movement generates dpad-driven updates with bounds checking
    // =========================================================================
    @Test
    fun `grid movement generates dpad_held checks with boundary conditions`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig =
                    MovementConfig(style = MovementStyle.GRID, speed = 8, tileSize = 8),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertEquals(1, functions.size, "Expected exactly one movement function")
        val fn = functions[0]
        assertEquals("update_movement_player", fn.name)

        // Function body should contain 4 CIf statements (UP, DOWN, LEFT, RIGHT)
        assertEquals(4, fn.body.size, "Expected 4 dpad direction checks in grid mode")

        // All should be CIf nodes
        fn.body.forEach { stmt ->
            assertIs<CIf>(stmt, "Expected all movement statements to be CIf")
        }

        // Emit to string for content verification
        val emitted =
            CEmitter.emitStatement(io.github.gbkt.backend.gbdk.codegen.ast.CBlock(fn.body))
        assertTrue(emitted.contains("dpad_held"), "Expected dpad_held calls in grid movement")
        assertTrue(emitted.contains("J_UP"), "Expected J_UP direction check")
        assertTrue(emitted.contains("J_DOWN"), "Expected J_DOWN direction check")
        assertTrue(emitted.contains("J_LEFT"), "Expected J_LEFT direction check")
        assertTrue(emitted.contains("J_RIGHT"), "Expected J_RIGHT direction check")
        // Grid mode has boundary checks (144 - speed=8 = 136 for DOWN, 160 - speed=8 = 152 for
        // RIGHT)
        assertTrue(
            emitted.contains("136"),
            "Expected screen height boundary (144-speed) in grid mode",
        )
        assertTrue(
            emitted.contains("152"),
            "Expected screen width boundary (160-speed) in grid mode",
        )
    }

    // =========================================================================
    // TEST 2: Smooth movement generates pixel-level updates without bounds check
    // =========================================================================
    @Test
    fun `smooth movement generates dpad_held checks without boundary conditions`() {
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

        // Smooth mode: 4 CIf statements, simpler (no bound check in condition)
        assertEquals(4, fn.body.size, "Expected 4 direction checks in smooth mode")

        val emitted =
            CEmitter.emitStatement(io.github.gbkt.backend.gbdk.codegen.ast.CBlock(fn.body))
        assertTrue(emitted.contains("dpad_held"), "Expected dpad_held calls")
        // Smooth mode should NOT have the && boundary compound condition
        // Each CIf condition is just CCall(dpad_held), not CBinaryExpr with &&
        fn.body.forEach { stmt ->
            val cif = stmt as CIf
            // Smooth mode: condition is just a CCall, not a CBinaryExpr with &&
            assertIs<CCall>(
                cif.condition,
                "Smooth mode condition should be simple CCall without &&",
            )
        }
    }

    // =========================================================================
    // TEST 3: Simple animation generates typed frame counter
    // =========================================================================
    @Test
    fun `simple animation with frameSpeed generates update_animation function`() {
        val actor = ActorIR(id = "coin", position = PositionDef(100, 50), frameSpeed = 6)

        val functions = ActorVisitor.generateAnimationFunction(actor)

        assertEquals(1, functions.size, "Expected exactly one animation function")
        val fn = functions[0]
        assertEquals("update_animation_coin", fn.name)

        // Should have 2 statements: increment counter + CIf speed check
        assertEquals(2, fn.body.size, "Expected increment + speed check")

        // First statement: _coin_anim_ctr++
        val incrStmt = fn.body[0]
        assertIs<CExprStatement>(incrStmt, "First statement should be CExprStatement")
        val incrExpr = incrStmt.expr
        assertIs<CUnaryExpr>(incrExpr, "Increment should be a CUnaryExpr")
        assertEquals("++", incrExpr.op, "Should be post-increment")

        // Second statement: CIf (_coin_anim_ctr >= 6) { reset; increment frame }
        val speedCheck = fn.body[1]
        assertIs<CIf>(speedCheck, "Second statement should be CIf")
        val condition = speedCheck.condition
        assertIs<CBinaryExpr>(condition)
        assertEquals(">=", condition.op)
        assertEquals(CLiteral(6), condition.right, "Speed check should use frameSpeed=6")
    }

    // =========================================================================
    // TEST 4: Animation state machine generates #define constants for states
    // =========================================================================
    @Test
    fun `animation state machine generates ANIM defines for each state`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(0, 0),
                animationStates =
                    listOf(
                        AnimationStateDef(name = "idle", startFrame = 0, endFrame = 3, speed = 8),
                        AnimationStateDef(name = "walk", startFrame = 4, endFrame = 7, speed = 4),
                        AnimationStateDef(name = "jump", startFrame = 8, endFrame = 9, speed = 6),
                    ),
            )

        val defines = ActorVisitor.generateAnimationDefines(actor)

        assertEquals(3, defines.size, "Expected one define per state")
        assertEquals("ANIM_PLAYER_IDLE", defines[0].name)
        assertEquals("0", defines[0].value)
        assertEquals("ANIM_PLAYER_WALK", defines[1].name)
        assertEquals("1", defines[1].value)
        assertEquals("ANIM_PLAYER_JUMP", defines[2].name)
        assertEquals("2", defines[2].value)
    }

    // =========================================================================
    // TEST 5: Animation state machine generates CSwitch-based update function
    // =========================================================================
    @Test
    fun `animation state machine generates CSwitch update function with cases per state`() {
        val actor =
            ActorIR(
                id = "enemy",
                position = PositionDef(50, 50),
                animationStates =
                    listOf(
                        AnimationStateDef(name = "idle", startFrame = 0, endFrame = 2, speed = 8),
                        AnimationStateDef(name = "attack", startFrame = 3, endFrame = 5, speed = 4),
                    ),
            )

        val functions = ActorVisitor.generateAnimationFunction(actor)

        assertEquals(1, functions.size, "Expected exactly one animation function")
        val fn = functions[0]
        assertEquals("update_animation_enemy", fn.name)

        // Body should be a single CSwitch statement
        assertEquals(1, fn.body.size, "State machine body should be single CSwitch")
        val switchStmt = fn.body[0]
        assertIs<CSwitch>(switchStmt, "State machine body should be CSwitch")

        // Switch should have one case per state
        assertEquals(2, switchStmt.cases.size, "Expected one case per animation state")
    }

    // =========================================================================
    // TEST 6: setAnimationState generates state assignment + frame/counter reset
    // =========================================================================
    @Test
    fun `visitSetAnimationState generates state var assignment and frame counter reset`() {
        val op = SetAnimationState(actorId = "player", stateName = "walk")
        val result = ScriptOpVisitor.visit(op)

        // Result should be a CBlock with 3 statements
        assertIs<CBlock>(result, "SetAnimationState should generate CBlock")
        assertEquals(
            3,
            result.statements.size,
            "Expected 3 statements: state=X, frame=0, counter=0",
        )

        // Statement 0: _player_anim_state = ANIM_PLAYER_WALK
        val stateAssign = result.statements[0]
        assertIs<CExprStatement>(stateAssign)
        val stateExpr = stateAssign.expr
        assertIs<CBinaryExpr>(stateExpr)
        assertEquals("=", stateExpr.op)
        assertEquals(CVar("_player_anim_state"), stateExpr.left)
        assertEquals(CVar("ANIM_PLAYER_WALK"), stateExpr.right)

        // Statement 1: _player_anim_frame = 0
        val frameReset = result.statements[1]
        assertIs<CExprStatement>(frameReset)
        val frameExpr = frameReset.expr
        assertIs<CBinaryExpr>(frameExpr)
        assertEquals(CVar("_player_anim_frame"), frameExpr.left)
        assertEquals(CLiteral(0), frameExpr.right)

        // Statement 2: _player_anim_counter = 0
        val counterReset = result.statements[2]
        assertIs<CExprStatement>(counterReset)
        val counterExpr = counterReset.expr
        assertIs<CBinaryExpr>(counterExpr)
        assertEquals(CVar("_player_anim_counter"), counterExpr.left)
        assertEquals(CLiteral(0), counterExpr.right)
    }

    // =========================================================================
    // TEST 7: Transition condition generates CIf check inside state case
    // =========================================================================
    @Test
    fun `animation state transition with condition generates CIf in state case body`() {
        val actor =
            ActorIR(
                id = "npc",
                position = PositionDef(10, 10),
                animationStates =
                    listOf(
                        AnimationStateDef(
                            name = "idle",
                            startFrame = 0,
                            endFrame = 3,
                            speed = 8,
                            transitions =
                                listOf(
                                    AnimTransition(
                                        fromState = "idle",
                                        toState = "walk",
                                        condition =
                                            BinaryExpr(
                                                VarRef("npc_moving"),
                                                BinaryOp.GTE,
                                                Literal(1),
                                            ),
                                    )
                                ),
                        ),
                        AnimationStateDef(name = "walk", startFrame = 4, endFrame = 7, speed = 4),
                    ),
            )

        val functions = ActorVisitor.generateAnimationFunction(actor)
        val fn = functions[0]
        val switchStmt = fn.body[0]
        assertIs<CSwitch>(switchStmt)

        // First case = idle state
        val idleCase = switchStmt.cases[0]
        // The case body should contain a CIf for the transition (plus the frame cycling logic)
        val transitionIf = idleCase.body.filterIsInstance<CIf>().lastOrNull()
        assertNotNull(transitionIf, "Expected a CIf for the auto-transition condition")

        // The transition body should set state to ANIM_NPC_WALK and reset frame/counter
        val emitted =
            CEmitter.emitStatement(
                io.github.gbkt.backend.gbdk.codegen.ast.CBlock(transitionIf.thenBody)
            )
        assertTrue(
            emitted.contains("ANIM_NPC_WALK"),
            "Transition should set state to target: got $emitted",
        )
        assertTrue(
            emitted.contains("_npc_anim_frame"),
            "Transition should reset frame: got $emitted",
        )
        assertTrue(
            emitted.contains("_npc_anim_counter"),
            "Transition should reset counter: got $emitted",
        )
    }

    // =========================================================================
    // TEST 8: Actor without movement config generates no movement function
    // =========================================================================
    @Test
    fun `actor without movement config generates no movement function`() {
        val actor =
            ActorIR(
                id = "background_object",
                position = PositionDef(80, 80),
                // movementConfig not set (null)
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertTrue(
            functions.isEmpty(),
            "Actor without movementConfig should generate no movement function",
        )
    }

    // =========================================================================
    // TEST 9: PHYSICS mode generates dpad→acceleration→velocity→position
    // =========================================================================
    @Test
    fun `actor with PHYSICS movement style generates velocity-based movement function`() {
        val actor =
            ActorIR(
                id = "ball",
                position = PositionDef(60, 60),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS, speed = 4),
                physicsConfig =
                    PhysicsConfig(
                        gravity = 1,
                        accelerationX = 2,
                        accelerationY = 2,
                        maxFallSpeed = 6,
                    ),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertEquals(1, functions.size, "PHYSICS should generate one movement function")
        val fn = functions[0]
        assertEquals("update_movement_ball", fn.name, "Function name should match actor ID")

        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // D-pad input applies acceleration to velocity
        assertTrue(emitted.contains("dpad_held(J_LEFT)"), "Should check LEFT d-pad for VX")
        assertTrue(emitted.contains("dpad_held(J_RIGHT)"), "Should check RIGHT d-pad for VX")
        assertTrue(emitted.contains("dpad_held(J_UP)"), "Should check UP d-pad for VY")
        assertTrue(emitted.contains("dpad_held(J_DOWN)"), "Should check DOWN d-pad for VY")
        assertTrue(emitted.contains("_ball_vx"), "Should reference VX velocity variable")
        assertTrue(emitted.contains("_ball_vy"), "Should reference VY velocity variable")
        assertTrue(emitted.contains("ACCEL_X_BALL"), "Should reference X acceleration define")
        assertTrue(emitted.contains("ACCEL_Y_BALL"), "Should reference Y acceleration define")

        // Gravity and fall clamp
        assertTrue(emitted.contains("GRAVITY_BALL"), "Should apply gravity to VY")
        assertTrue(emitted.contains("MAX_FALL_BALL"), "Should clamp fall speed")

        // Velocity to position (with UINT8 cast)
        assertTrue(
            emitted.contains("_ball_x += (UINT8)_ball_vx"),
            "Should apply VX to X position with cast",
        )
        assertTrue(
            emitted.contains("_ball_y += (UINT8)_ball_vy"),
            "Should apply VY to Y position with cast",
        )
    }

    // =========================================================================
    // TEST 9a: PHYSICS without gravity omits gravity and clamp statements
    // =========================================================================
    @Test
    fun `PHYSICS mode without gravity omits gravity and fall clamp`() {
        val actor =
            ActorIR(
                id = "ship",
                position = PositionDef(80, 80),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS, speed = 2),
                physicsConfig = PhysicsConfig(gravity = 0, accelerationX = 1, accelerationY = 1),
            )

        val functions = ActorVisitor.generateMovementFunction(actor)
        val fn = functions[0]
        val emitted = CEmitter.emitStatement(CBlock(fn.body))

        // D-pad input should still work
        assertTrue(emitted.contains("dpad_held(J_LEFT)"), "D-pad LEFT should still be generated")

        // No gravity or fall clamp when gravity = 0
        assertTrue(!emitted.contains("GRAVITY_SHIP"), "Zero gravity should omit gravity statement")
        assertTrue(!emitted.contains("MAX_FALL_SHIP"), "Zero gravity should omit fall clamp")

        // Velocity to position still works
        assertTrue(
            emitted.contains("_ship_x += (UINT8)_ship_vx"),
            "Velocity to position should still apply",
        )
    }

    // =========================================================================
    // TEST 9b: PHYSICS with default PhysicsConfig when no explicit config
    // =========================================================================
    @Test
    fun `PHYSICS mode uses default PhysicsConfig when actor has no explicit physics`() {
        val actor =
            ActorIR(
                id = "rock",
                position = PositionDef(40, 40),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS, speed = 1),
                // No physicsConfig — should use defaults
            )

        val functions = ActorVisitor.generateMovementFunction(actor)

        assertEquals(
            1,
            functions.size,
            "Should still generate movement function with default physics",
        )
        val fn = functions[0]
        assertEquals("update_movement_rock", fn.name)

        val emitted = CEmitter.emitStatement(CBlock(fn.body))
        assertTrue(emitted.contains("_rock_vx"), "Should use velocity vars with defaults")
        // Default gravity = 0, so no gravity/clamp statements
        assertTrue(!emitted.contains("GRAVITY_ROCK"), "Default gravity=0 should omit gravity")
    }

    // =========================================================================
    // TEST 9c: PHYSICS with explicit PhysicsConfig uses config parameters
    // =========================================================================
    @Test
    fun `PHYSICS generatePhysicsVars and generatePhysicsDefines work with MovementStyle PHYSICS`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                movementConfig = MovementConfig(style = MovementStyle.PHYSICS, speed = 2),
                // No explicit physicsConfig — MovementStyle.PHYSICS should trigger defaults
            )

        val vars = ActorVisitor.generatePhysicsVars(actor)
        assertEquals(
            2,
            vars.size,
            "Should generate vx and vy vars even without explicit physicsConfig",
        )
        assertEquals("_player_vx", vars[0].name)
        assertEquals("_player_vy", vars[1].name)

        val defines = ActorVisitor.generatePhysicsDefines(actor)
        assertEquals(
            5,
            defines.size,
            "Should generate 5 physics defines even without explicit physicsConfig",
        )
        assertTrue(defines.any { it.name == "ACCEL_X_PLAYER" }, "Should have ACCEL_X define")
        assertTrue(defines.any { it.name == "GRAVITY_PLAYER" }, "Should have GRAVITY define")
    }

    // =========================================================================
    // TEST 10: Animation vars produced for state machine actor only
    // =========================================================================
    @Test
    fun `generateAnimationVars returns state, frame, counter vars for state machine actor`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(0, 0),
                animationStates =
                    listOf(
                        AnimationStateDef(name = "idle", startFrame = 0, endFrame = 3, speed = 8)
                    ),
            )

        val vars = ActorVisitor.generateAnimationVars(actor)

        assertEquals(3, vars.size, "State machine actor should produce 3 animation vars")
        assertTrue(vars.any { it.name == "_player_anim_state" }, "Expected _player_anim_state var")
        assertTrue(vars.any { it.name == "_player_anim_frame" }, "Expected _player_anim_frame var")
        assertTrue(
            vars.any { it.name == "_player_anim_counter" },
            "Expected _player_anim_counter var",
        )
        vars.forEach { v ->
            assertEquals(CU8, v.type, "All animation vars should be CU8")
            assertEquals(CLiteral(0), v.initializer, "All animation vars should initialize to 0")
        }
    }

    // =========================================================================
    // TEST 11: Simple animation vars produced for frameSpeed-only actor
    // =========================================================================
    @Test
    fun `generateSimpleAnimationVars returns frame and counter vars for frameSpeed actor`() {
        val actor = ActorIR(id = "gem", position = PositionDef(0, 0), frameSpeed = 12)

        val vars = ActorVisitor.generateSimpleAnimationVars(actor)

        assertEquals(2, vars.size, "frameSpeed actor should produce 2 animation vars")
        assertTrue(vars.any { it.name == "_gem_anim_frame" }, "Expected _gem_anim_frame var")
        assertTrue(vars.any { it.name == "_gem_anim_ctr" }, "Expected _gem_anim_ctr var")
    }

    // =========================================================================
    // TEST 12: No animation vars for actor without animation config
    // =========================================================================
    @Test
    fun `generateAnimationVars and generateSimpleAnimationVars both empty for plain actor`() {
        val actor =
            ActorIR(
                id = "wall",
                position = PositionDef(0, 0),
                // No animationStates, no frameSpeed
            )

        val stateMachineVars = ActorVisitor.generateAnimationVars(actor)
        val simpleVars = ActorVisitor.generateSimpleAnimationVars(actor)

        assertTrue(stateMachineVars.isEmpty(), "Plain actor should have no state machine vars")
        assertTrue(simpleVars.isEmpty(), "Plain actor should have no simple animation vars")
    }
}
