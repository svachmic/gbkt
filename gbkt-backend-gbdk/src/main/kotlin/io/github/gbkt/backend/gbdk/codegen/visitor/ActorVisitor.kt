/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.DiagonalMode
import io.github.gbkt.core.ir.FixedPointMode
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.SmoothMovementConfig
import io.github.gbkt.core.ir.WallResponse

// =============================================================================
// ACTOR VISITOR
// Translates IR v2 ActorIR nodes into typed C AST CVarDecl nodes and
// OAM management statements for sprite rendering.
// =============================================================================

/**
 * Visitor that converts IR v2 [ActorIR] nodes to lists of C AST nodes.
 *
 * **Position variables** ([visit]): Each [ActorIR] produces two position variable declarations:
 * - `_actorId_x` — x position, initialized from [ActorIR.position.x]
 * - `_actorId_y` — y position, initialized from [ActorIR.position.y]
 *
 * Variable names use the GBDK underscore-prefix convention: `ActorIR(id = "ball", position =
 * PositionDef(80, 72))` produces:
 * - `CVarDecl("_ball_x", CU8, CLiteral(80))`
 * - `CVarDecl("_ball_y", CU8, CLiteral(72))`
 *
 * **Sprite data loading** ([generateSpriteDataLoad]): Emits `set_sprite_data(startTile, totalTiles,
 * tileDataArrayName)` to copy sprite tile data into VRAM at game init.
 *
 * **OAM init** ([generateOAMInit]): Emits `set_sprite_tile()` and initial `move_sprite()` calls for
 * each OAM slot in the actor's metasprite grid.
 *
 * **Per-frame OAM sync** ([generateUpdateSprites]): Generates the `update_sprites()` function that
 * syncs all position variables to OAM every frame. GBDK hardware coordinate offset: +8 for x, +16
 * for y.
 *
 * Actor IDs are sanitized: dots replaced with underscores (mirrors [ExprVisitor.sanitizeVarName]).
 * Position variables use [CU8] type matching the Game Boy's UINT8 native coordinate range (0-255).
 */
object ActorVisitor {

    /** Convert an [ActorIR] node to a list of [CVarDecl] position variable declarations. */
    fun visit(actor: ActorIR): List<CVarDecl> {
        val prefix = "_${sanitizeId(actor.id)}"
        return listOf(
            CVarDecl(name = "${prefix}_x", type = CU8, initializer = CLiteral(actor.position.x)),
            CVarDecl(name = "${prefix}_y", type = CU8, initializer = CLiteral(actor.position.y)),
        )
    }

    /**
     * Generate `set_sprite_data()` calls to load tile data for this actor's sprite into VRAM.
     *
     * Computes `tilesWide = (width + 7) / 8`, `tilesHigh = (height + 7) / 8`, `totalTiles =
     * tilesWide * tilesHigh`. Returns empty list if actor has no sprite.
     *
     * @param actor The actor whose sprite tile data to load.
     * @param tileDataArrayName C identifier of the tile data array (from the sprite asset header).
     * @param startTile First VRAM tile slot to place this actor's tiles.
     * @return A list containing the `set_sprite_data()` call statement, or empty if no sprite.
     */
    fun generateSpriteDataLoad(
        actor: ActorIR,
        tileDataArrayName: String,
        startTile: Int,
    ): List<CStatement> {
        val sprite = actor.sprite ?: return emptyList()
        val tilesWide = (sprite.size.width + 7) / 8
        val tilesHigh = (sprite.size.height + 7) / 8
        val totalTiles = tilesWide * tilesHigh
        return listOf(
            CExprStatement(
                CCall(
                    "set_sprite_data",
                    listOf(CLiteral(startTile), CLiteral(totalTiles), CVar(tileDataArrayName)),
                )
            )
        )
    }

    /**
     * Generate OAM initialization statements for this actor.
     *
     * For each slot in the actor's metasprite grid (all rows and columns):
     * - `set_sprite_tile(slot, tileStart + tileIndex)` — binds OAM slot to tile
     * - `move_sprite(slot, _actorId_x + 8 + col*8, _actorId_y + 16 + row*8)` — initial position
     *
     * GBDK hardware coordinate offset: +8 for x, +16 for y (OAM hardware requires this offset).
     *
     * Returns empty list if actor has no sprite.
     *
     * @param actor The actor to initialize.
     * @param oamSlotStart First OAM slot to use (fallback when [ActorIR.oamSlot] is null).
     * @param tileStart First tile index to bind.
     * @return List of OAM init statements for all slots in the metasprite grid.
     */
    fun generateOAMInit(actor: ActorIR, oamSlotStart: Int, tileStart: Int): List<CStatement> {
        val sprite = actor.sprite ?: return emptyList()
        val tilesWide = (sprite.size.width + 7) / 8
        val tilesHigh = (sprite.size.height + 7) / 8
        val baseSlot = actor.oamSlot?.slot ?: oamSlotStart
        val prefix = "_${sanitizeId(actor.id)}"
        val statements = mutableListOf<CStatement>()
        var tileIndex = 0
        for (row in 0 until tilesHigh) {
            for (col in 0 until tilesWide) {
                val slot = baseSlot + row * tilesWide + col
                // Emit the set_sprite_tile call assigning this slot its sequential tile index.
                statements.add(
                    CExprStatement(
                        CCall(
                            "set_sprite_tile",
                            listOf(CLiteral(slot), CLiteral(tileStart + tileIndex)),
                        )
                    )
                )
                // Emit the move_sprite call placing the slot at the actor position plus the
                // hardware offset (8/16) and the per-tile column/row offset.
                val xArg = buildPositionExpr("${prefix}_x", colOffset = col, hardwareOffset = 8)
                val yArg = buildPositionExpr("${prefix}_y", colOffset = row, hardwareOffset = 16)
                statements.add(
                    CExprStatement(CCall("move_sprite", listOf(CLiteral(slot), xArg, yArg)))
                )
                tileIndex++
            }
        }
        return statements
    }

    /**
     * Generate the `update_sprites()` function that syncs position variables to OAM every frame.
     *
     * For each actor with a sprite, for each OAM slot in the metasprite grid: `move_sprite(slot,
     * _actorId_x + 8 + col*8, _actorId_y + 16 + row*8)`
     *
     * GBDK hardware coordinate offset is +8 for x, +16 for y. Position (0,0) in game logic maps to
     * (8,16) in OAM hardware.
     *
     * OAM slot numbers are assigned sequentially: first actor gets slots starting from 0 (or from
     * [ActorIR.oamSlot] if set by analysis pass), next actor starts where the previous ended.
     * Actors in [excludeIds] are skipped — they do not get `move_sprite` calls and do not consume
     * static OAM slots (used to exclude pool template actors which use dynamic OAM allocation).
     *
     * @param actors All actors in the game. Only actors with sprites generate move_sprite calls.
     * @param excludeIds Actor IDs to skip (pool template actors with dynamic OAM allocation).
     * @return A [CFunction] named `update_sprites` with all per-frame OAM sync calls.
     */
    fun generateUpdateSprites(
        actors: List<ActorIR>,
        excludeIds: Set<String> = emptySet(),
    ): CFunction {
        val statements = mutableListOf<CStatement>()
        var nextSlot = 0
        for (actor in actors) {
            val sprite = actor.sprite ?: continue
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            val baseSlot = actor.oamSlot?.slot ?: nextSlot
            // Skip pool template actors — they use dynamic OAM allocation, not static slots
            if (sanitizeId(actor.id) in excludeIds) {
                nextSlot = baseSlot + tilesWide * tilesHigh
                continue
            }
            val prefix = "_${sanitizeId(actor.id)}"
            for (row in 0 until tilesHigh) {
                for (col in 0 until tilesWide) {
                    val slot = baseSlot + row * tilesWide + col
                    val xArg = buildPositionExpr("${prefix}_x", colOffset = col, hardwareOffset = 8)
                    val yArg =
                        buildPositionExpr("${prefix}_y", colOffset = row, hardwareOffset = 16)
                    statements.add(
                        CExprStatement(CCall("move_sprite", listOf(CLiteral(slot), xArg, yArg)))
                    )
                }
            }
            nextSlot = baseSlot + tilesWide * tilesHigh
        }
        return CFunction(
            name = "update_sprites",
            returnType = CVoid,
            body = statements,
            sectionComment = "Sprite OAM sync (called every frame)",
        )
    }

    /**
     * Generate hide_sprites_range(from, to) body.
     *
     * Moves sprites off-screen (to position 0,0) because GBDK has no dedicated hide_sprite API.
     * Position (0,0) is above and to the left of the visible area due to the +8/+16 hardware offset
     * used by OAM hardware.
     */
    fun generateHideSpritesRange(): CFunction {
        // C89 compliance: declare loop variable before for loop (GBDK lcc is C89)
        val iVar = CVar("i")
        val loopVarDecl = CVarDecl("i", CU8, initializer = null)
        val forLoop =
            CFor(
                init = CExprStatement(CBinaryExpr(iVar, "=", CVar("from"))),
                condition = CBinaryExpr(iVar, "<", CVar("to")),
                increment = CUnaryExpr("++", iVar),
                body =
                    listOf(
                        CExprStatement(
                            CCall("move_sprite", listOf(iVar, CRawExpr("0"), CRawExpr("0")))
                        )
                    ),
            )
        return CFunction(
            name = "hide_sprites_range",
            returnType = CVoid,
            params = listOf(CParam("from", CU8), CParam("to", CU8)),
            body = listOf(loopVarDecl, forLoop),
            sectionComment = "Sprite helpers (real OAM management)",
        )
    }

    /**
     * Generate show_sprites_range(from, to) body.
     *
     * Semantically a no-op: sprites become visible by moving them to valid on-screen positions
     * (which update_sprites does every frame). The function body emits `(void)from;` and
     * `(void)to;` casts to silence SDCC warning 85 (unused parameter) — see Phase 09.1 plan 02 D-08
     * and the matching exception in `CRawCodeEliminationTest.kt:232-243`. The function is kept for
     * DSL compatibility.
     */
    fun generateShowSpritesRange(): CFunction {
        return CFunction(
            name = "show_sprites_range",
            returnType = CVoid,
            params = listOf(CParam("from", CU8), CParam("to", CU8)),
            body =
                listOf(
                    CRawCode("(void)from;"),
                    CRawCode("(void)to;"),
                    CComment("Sprites shown by moving to valid positions via update_sprites()"),
                ),
        )
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Generate a `set_<actorId>_frame(frame)` function for multi-frame sprites.
     *
     * When [SpriteDef.frameWidth] and [SpriteDef.frameHeight] are set, the sprite sheet has
     * multiple animation frames arranged in a grid. This function updates the OAM tile bindings to
     * display the given frame by computing:
     * - `tiles_per_frame = (frameWidth / 8) * (frameHeight / 8)`
     * - For each OAM slot: `set_sprite_tile(slot, base_tile + frame * tiles_per_frame + slotIndex)`
     *
     * Returns an empty list if the actor has no sprite or if no frame metadata is set.
     *
     * @param actor The actor whose sprite frames to animate.
     * @param tileStart First VRAM tile slot allocated to this actor's sprite sheet.
     * @param oamSlotStart First OAM slot allocated to this actor.
     * @return A list containing the generated `CFunction`, or empty if no frame metadata is set.
     */
    fun generateFrameOffsetInit(
        actor: ActorIR,
        tileStart: Int,
        oamSlotStart: Int,
    ): List<CFunction> {
        val sprite = actor.sprite ?: return emptyList()
        val fw = sprite.frameWidth ?: return emptyList()
        val fh = sprite.frameHeight ?: fw // fall back to frameWidth if frameHeight not set
        val tilesPerFrame = (fw / 8) * (fh / 8)
        val tilesWide = (sprite.size.width + 7) / 8
        val tilesHigh = (sprite.size.height + 7) / 8
        val baseSlot = actor.oamSlot?.slot ?: oamSlotStart
        val actorId = sanitizeId(actor.id)
        val fnName = "set_${actorId}_frame"
        val statements = mutableListOf<CStatement>()
        var slotIndex = 0
        for (row in 0 until tilesHigh) {
            for (col in 0 until tilesWide) {
                val slot = baseSlot + row * tilesWide + col
                // Emit the set_sprite_tile call: the slot's tile is the frame number scaled by
                // tiles-per-frame, offset from the tileset start.
                val tileExpr =
                    CBinaryExpr(
                        CBinaryExpr(CVar("frame"), "*", CLiteral(tilesPerFrame)),
                        "+",
                        CLiteral(tileStart + slotIndex),
                    )
                statements.add(
                    CExprStatement(CCall("set_sprite_tile", listOf(CLiteral(slot), tileExpr)))
                )
                slotIndex++
            }
        }
        val fn =
            CFunction(
                name = fnName,
                returnType = CVoid,
                params = listOf(CParam("frame", CU8)),
                body = statements,
                sectionComment = "Frame animation for $actorId (tiles_per_frame=$tilesPerFrame)",
            )
        return listOf(fn)
    }

    // =========================================================================
    // Movement function generation
    // =========================================================================

    /**
     * Generate per-actor movement variable declarations for actors with [ActorIR.animationStates].
     *
     * For each actor with a non-empty animation state list, produces:
     * - `UINT8 _actorId_anim_state = 0;` — current state index
     * - `UINT8 _actorId_anim_frame = 0;` — current frame within the state's range
     * - `UINT8 _actorId_anim_counter = 0;` — frame counter for speed control
     *
     * Returns empty list for actors without animation state machines.
     */
    fun generateAnimationVars(actor: ActorIR): List<CVarDecl> {
        if (actor.animationStates.isEmpty()) return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        return listOf(
            CVarDecl(name = "${prefix}_anim_state", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "${prefix}_anim_frame", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "${prefix}_anim_counter", type = CU8, initializer = CLiteral(0)),
        )
    }

    /**
     * Generate animation variable declarations for simple animation (no state machine).
     *
     * For actors with [ActorIR.frameSpeed] but empty [ActorIR.animationStates], produces:
     * - `UINT8 _actorId_anim_frame = 0;` — current frame index
     * - `UINT8 _actorId_anim_ctr = 0;` — counter for speed control (matches AnimateOp variable)
     *
     * Returns empty list if no frameSpeed or if animationStates is non-empty.
     */
    fun generateSimpleAnimationVars(actor: ActorIR): List<CVarDecl> {
        if (actor.frameSpeed == null || actor.animationStates.isNotEmpty()) return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        return listOf(
            CVarDecl(name = "${prefix}_anim_frame", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "${prefix}_anim_ctr", type = CU8, initializer = CLiteral(0)),
        )
    }

    /**
     * Generate `#define` constants for animation state indices.
     *
     * Each state in [ActorIR.animationStates] gets a `#define ANIM_{ACTORID}_{STATENAME} N`
     * constant. Used by the state machine switch and by [visitSetAnimationState] in
     * [ScriptOpVisitor].
     *
     * Returns empty list for actors without animation states.
     *
     * Example: `ANIM_PLAYER_IDLE 0`, `ANIM_PLAYER_WALK 1`
     */
    fun generateAnimationDefines(actor: ActorIR): List<CDefine> {
        return actor.animationStates.mapIndexed { idx, state ->
            val actorId = sanitizeId(actor.id).uppercase()
            CDefine("ANIM_${actorId}_${state.name.uppercase()}", "$idx")
        }
    }

    /**
     * Generate the `update_movement_{actorId}()` function for an actor with a [MovementConfig].
     *
     * **GRID mode**: D-pad input moves by [speed] pixels per step, bounded by tile size:
     * ```c
     * if (dpad_held(J_UP) && _player_y > 0) { _player_y -= speed; }
     * // similar for DOWN, LEFT, RIGHT
     * ```
     *
     * **SMOOTH mode (legacy)**: D-pad input moves by [speed] pixels per frame, no snapping:
     * ```c
     * if (dpad_held(J_UP)) _player_y -= speed;
     * // similar for DOWN, LEFT, RIGHT
     * ```
     *
     * **SMOOTH mode (acceleration)**: When [MovementConfig.smoothConfig] is non-null, generates
     * velocity-variable-based movement with acceleration/friction/clamp:
     * ```c
     * if (dpad_held(J_LEFT))  _player_vx -= ACCEL_PLAYER;
     * if (dpad_held(J_RIGHT)) _player_vx += ACCEL_PLAYER;
     * if (!(joypad() & (J_LEFT | J_RIGHT))) { /* friction toward zero */ }
     * if (_player_vx >  SPEED_PLAYER) _player_vx =  SPEED_PLAYER;
     * if (_player_vx < -SPEED_PLAYER) _player_vx = -SPEED_PLAYER;
     * _player_x += (UINT8)_player_vx;
     * ```
     *
     * **PHYSICS mode**: D-pad input applies acceleration to velocity, then integrates velocity to
     * position with gravity and fall speed clamping. Uses [PhysicsConfig] parameters (defaults to
     * [PhysicsConfig()] if actor has no explicit physics config):
     * ```c
     * if (dpad_held(J_LEFT)) _actor_vx -= ACCEL_X_ACTOR;
     * // ... apply gravity, clamp fall speed, velocity→position
     * ```
     *
     * Returns empty list if [ActorIR.movementConfig] is null.
     */
    fun generateMovementFunction(actor: ActorIR): List<CFunction> {
        val config = actor.movementConfig ?: return emptyList()

        val actorId = sanitizeId(actor.id)
        val xVar = CVar("_${actorId}_x")
        val yVar = CVar("_${actorId}_y")
        val speed = config.speed

        val statements = mutableListOf<CStatement>()

        when (config.style) {
            MovementStyle.GRID -> {
                // Grid mode: move by speed, bounded by tile size at edges
                // UP: y > 0 (don't go above top)
                statements +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("dpad_held", listOf(CVar("J_UP"))),
                                "&&",
                                CBinaryExpr(yVar, ">", CLiteral(0)),
                            ),
                        thenBody = listOf(CExprStatement(CBinaryExpr(yVar, "-=", CLiteral(speed)))),
                    )
                // DOWN: y < 144 (screen height 144 pixels)
                statements +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("dpad_held", listOf(CVar("J_DOWN"))),
                                "&&",
                                CBinaryExpr(
                                    yVar,
                                    "<",
                                    CLiteral(GameBoyConstants.SCREEN_HEIGHT - speed),
                                ),
                            ),
                        thenBody = listOf(CExprStatement(CBinaryExpr(yVar, "+=", CLiteral(speed)))),
                    )
                // LEFT: x > 0 (don't go past left edge)
                statements +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("dpad_held", listOf(CVar("J_LEFT"))),
                                "&&",
                                CBinaryExpr(xVar, ">", CLiteral(0)),
                            ),
                        thenBody = listOf(CExprStatement(CBinaryExpr(xVar, "-=", CLiteral(speed)))),
                    )
                // RIGHT: x < 160 (screen width 160 pixels)
                statements +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("dpad_held", listOf(CVar("J_RIGHT"))),
                                "&&",
                                CBinaryExpr(
                                    xVar,
                                    "<",
                                    CLiteral(GameBoyConstants.SCREEN_WIDTH - speed),
                                ),
                            ),
                        thenBody = listOf(CExprStatement(CBinaryExpr(xVar, "+=", CLiteral(speed)))),
                    )
            }
            MovementStyle.SMOOTH -> {
                val smooth = config.smoothConfig
                if (smooth != null) {
                    // Acceleration/friction SMOOTH mode
                    statements += generateSmoothAccelerationStatements(actorId, smooth, xVar, yVar)
                } else {
                    // Legacy SMOOTH mode: pixel-level movement, no bounds checking
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_UP"))),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(yVar, "-=", CLiteral(speed)))),
                        )
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_DOWN"))),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(yVar, "+=", CLiteral(speed)))),
                        )
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_LEFT"))),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(xVar, "-=", CLiteral(speed)))),
                        )
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_RIGHT"))),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(xVar, "+=", CLiteral(speed)))),
                        )
                }
            }
            MovementStyle.PHYSICS -> {
                // Physics mode: d-pad input → acceleration → velocity → position
                // Uses PhysicsConfig for parameters (defaults if not set on actor)
                val physics = actor.physicsConfig ?: PhysicsConfig()
                val actorIdUpper = actorId.uppercase()
                val vxVar = CVar("_${actorId}_vx")
                val vyVar = CVar("_${actorId}_vy")
                val accelXDef = CVar("ACCEL_X_$actorIdUpper")
                val accelYDef = CVar("ACCEL_Y_$actorIdUpper")
                val gravityDef = CVar("GRAVITY_$actorIdUpper")
                val maxFallDef = CVar("MAX_FALL_$actorIdUpper")

                // 1. D-pad input applies acceleration to velocity
                // LEFT: vx -= accel
                statements +=
                    CIf(
                        condition = CCall("dpad_held", listOf(CVar("J_LEFT"))),
                        thenBody = listOf(CExprStatement(CBinaryExpr(vxVar, "-=", accelXDef))),
                    )
                // RIGHT: vx += accel
                statements +=
                    CIf(
                        condition = CCall("dpad_held", listOf(CVar("J_RIGHT"))),
                        thenBody = listOf(CExprStatement(CBinaryExpr(vxVar, "+=", accelXDef))),
                    )
                // Platformer mode: UP/DOWN d-pad only if not in platformer mode (top-down)
                // In platformer mode, vertical movement is via jump button, not d-pad Y
                if (!physics.platformerMode) {
                    // UP: vy -= accel (top-down only)
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_UP"))),
                            thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "-=", accelYDef))),
                        )
                    // DOWN: vy += accel (top-down only)
                    statements +=
                        CIf(
                            condition = CCall("dpad_held", listOf(CVar("J_DOWN"))),
                            thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "+=", accelYDef))),
                        )
                }

                // 2. Variable-height jump: track jump button held state and cut velocity on release
                if (physics.variableJump) {
                    val jumpHeldVar = CVar("_${actorId}_jump_held")
                    val jumpCutDef = CVar("JUMP_CUT_$actorIdUpper")
                    // If jump button held: set flag
                    // If button released while moving up: cut velocity
                    statements +=
                        CIf(
                            condition = CRawExpr("joypad() & J_A"),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(jumpHeldVar, "=", CLiteral(1)))),
                            elseBody =
                                listOf(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CBinaryExpr(jumpHeldVar, "!=", CLiteral(0)),
                                                "&&",
                                                CBinaryExpr(vyVar, "<", CIntLiteral(0)),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(CBinaryExpr(vyVar, "/=", jumpCutDef))
                                            ),
                                    ),
                                    CExprStatement(CBinaryExpr(jumpHeldVar, "=", CLiteral(0))),
                                ),
                        )
                }

                // 3. Coyote time: manage coyote counter (decrements after leaving ground)
                // Note: actual coyote counter management happens in game logic (when on_ground
                // tracked)
                // We emit the counter decrement; game logic sets it to COYOTE_N when on ground
                if (physics.coyoteFrames > 0) {
                    val coyoteVar = CVar("_${actorId}_coyote")
                    statements +=
                        CIf(
                            condition = CBinaryExpr(coyoteVar, ">", CLiteral(0)),
                            thenBody = listOf(CExprStatement(CUnaryExpr("--", coyoteVar))),
                        )
                }

                // 4. Apply gravity to VY (platformer mode: gravity on Y; top-down: no gravity)
                if (physics.platformerMode && physics.gravity != 0) {
                    statements += CExprStatement(CBinaryExpr(vyVar, "+=", gravityDef))
                } else if (!physics.platformerMode && physics.gravity != 0) {
                    // Top-down: still support gravity if explicitly set (unusual but valid)
                    statements += CExprStatement(CBinaryExpr(vyVar, "+=", gravityDef))
                }

                // 5. Clamp fall speed (only when gravity is active)
                if (physics.gravity != 0) {
                    statements +=
                        CIf(
                            condition = CBinaryExpr(vyVar, ">", maxFallDef),
                            thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "=", maxFallDef))),
                        )
                }

                // 6. Wall-jump: if touching wall and jump pressed, kick off in opposite direction
                if (physics.wallJump) {
                    val wallContactVar = CVar("_${actorId}_wall_contact")
                    val wjVxDef = CVar("WALLJUMP_VX_$actorIdUpper")
                    val wjVyDef = CVar("WALLJUMP_VY_$actorIdUpper")
                    statements +=
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(wallContactVar, "!=", CLiteral(0)),
                                    "&&",
                                    CRawExpr("new_buttons & J_A"),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(vyVar, "=", CUnaryExpr("-", wjVyDef))
                                    ),
                                    CIf(
                                        condition = CBinaryExpr(wallContactVar, "==", CLiteral(1)),
                                        thenBody =
                                            listOf(
                                                CExprStatement(CBinaryExpr(vxVar, "=", wjVxDef))
                                            ),
                                        elseBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        vxVar,
                                                        "=",
                                                        CUnaryExpr("-", wjVxDef),
                                                    )
                                                )
                                            ),
                                    ),
                                    CExprStatement(CBinaryExpr(wallContactVar, "=", CLiteral(0))),
                                ),
                        )
                }

                // 7. Apply velocity to position
                // Fixed-point mode: accumulate fractional velocity into fractional position,
                // then extract integer pixel position via bit shift.
                // INTEGER mode (default): cast I8 velocity directly to UINT8 for position.
                when (physics.fixedPointMode) {
                    FixedPointMode.FP44 -> {
                        // 4.4 fixed-point: accumulate sub-pixel velocity, extract with >> 4
                        val xFracVar = CVar("_${actorId}_x_frac")
                        val yFracVar = CVar("_${actorId}_y_frac")
                        val vxFracVar = CVar("_${actorId}_vx_frac")
                        val vyFracVar = CVar("_${actorId}_vy_frac")
                        statements += CExprStatement(CBinaryExpr(vxFracVar, "+=", vxVar))
                        statements += CExprStatement(CBinaryExpr(vyFracVar, "+=", vyVar))
                        statements += CExprStatement(CBinaryExpr(xFracVar, "+=", vxFracVar))
                        statements += CExprStatement(CBinaryExpr(yFracVar, "+=", vyFracVar))
                        statements +=
                            CExprStatement(
                                CBinaryExpr(
                                    xVar,
                                    "=",
                                    CCast(CU8, CRawExpr("_${actorId}_x_frac >> 4")),
                                )
                            )
                        statements +=
                            CExprStatement(
                                CBinaryExpr(
                                    yVar,
                                    "=",
                                    CCast(CU8, CRawExpr("_${actorId}_y_frac >> 4")),
                                )
                            )
                    }
                    FixedPointMode.FP88 -> {
                        // 8.8 fixed-point: accumulate sub-pixel velocity, extract with >> 8
                        val xFracVar = CVar("_${actorId}_x_frac")
                        val yFracVar = CVar("_${actorId}_y_frac")
                        val vxFracVar = CVar("_${actorId}_vx_frac")
                        val vyFracVar = CVar("_${actorId}_vy_frac")
                        statements += CExprStatement(CBinaryExpr(vxFracVar, "+=", vxVar))
                        statements += CExprStatement(CBinaryExpr(vyFracVar, "+=", vyVar))
                        statements += CExprStatement(CBinaryExpr(xFracVar, "+=", vxFracVar))
                        statements += CExprStatement(CBinaryExpr(yFracVar, "+=", vyFracVar))
                        statements +=
                            CExprStatement(
                                CBinaryExpr(
                                    xVar,
                                    "=",
                                    CCast(CU8, CRawExpr("_${actorId}_x_frac >> 8")),
                                )
                            )
                        statements +=
                            CExprStatement(
                                CBinaryExpr(
                                    yVar,
                                    "=",
                                    CCast(CU8, CRawExpr("_${actorId}_y_frac >> 8")),
                                )
                            )
                    }
                    FixedPointMode.INTEGER -> {
                        // INTEGER mode (default): direct integer velocity to position
                        statements += CExprStatement(CBinaryExpr(xVar, "+=", CCast(CU8, vxVar)))
                        statements += CExprStatement(CBinaryExpr(yVar, "+=", CCast(CU8, vyVar)))
                    }
                }
            }
        }

        return listOf(
            CFunction(
                name = "update_movement_$actorId",
                returnType = CVoid,
                body = statements,
                sectionComment =
                    "Per-actor movement: $actorId (${config.style}, speed=${config.speed})",
            )
        )
    }

    /**
     * Generate the `update_animation_{actorId}()` function for animation state machine or simple.
     *
     * **State machine** (when [ActorIR.animationStates] is non-empty): Generates a switch on
     * `_actorId_anim_state` with:
     * - Frame cycling: increment counter, when >= state.speed → advance frame (wraps at endFrame)
     * - Auto-transition checks: for each outgoing transition with a non-null condition, generate
     *   `if (condition) { state = TARGET; frame = 0; counter = 0; }`
     *
     * **Simple animation** (when [ActorIR.frameSpeed] is set but animationStates is empty):
     * Generates the same frame counter logic as [visitAnimateOp] in [ScriptOpVisitor].
     *
     * Returns empty list if neither animationStates nor frameSpeed is set.
     *
     * The condition IRExpression in transitions is translated using [ExprVisitor].
     */
    fun generateAnimationFunction(
        actor: ActorIR,
        exprVisitor: ExprVisitor = ExprVisitor(),
    ): List<CFunction> {
        val actorId = sanitizeId(actor.id)

        if (actor.animationStates.isNotEmpty()) {
            return listOf(generateStateMachineFunction(actor, actorId, exprVisitor))
        }

        val frameSpeed = actor.frameSpeed ?: return emptyList()
        return listOf(generateSimpleAnimationFunction(actorId, frameSpeed))
    }

    /**
     * Generate the simple animation update function (no state machine).
     *
     * Generated C:
     * ```c
     * void update_animation_player(void) {
     *     _player_anim_ctr++;
     *     if (_player_anim_ctr >= 8) {
     *         _player_anim_ctr = 0;
     *         _player_anim_frame++;
     *     }
     * }
     * ```
     */
    private fun generateSimpleAnimationFunction(actorId: String, frameSpeed: Int): CFunction {
        val counterVar = CVar("_${actorId}_anim_ctr")
        val frameVar = CVar("_${actorId}_anim_frame")
        return CFunction(
            name = "update_animation_$actorId",
            returnType = CVoid,
            body =
                listOf(
                    CExprStatement(CUnaryExpr("++", counterVar)),
                    CIf(
                        condition = CBinaryExpr(counterVar, ">=", CLiteral(frameSpeed)),
                        thenBody =
                            listOf(
                                CExprStatement(CBinaryExpr(counterVar, "=", CLiteral(0))),
                                CExprStatement(CUnaryExpr("++", frameVar)),
                            ),
                    ),
                ),
            sectionComment = "Simple animation: $actorId (frameSpeed=$frameSpeed)",
        )
    }

    /**
     * Generate the state machine animation update function.
     *
     * For each state, generates a switch case that:
     * 1. Increments the frame counter
     * 2. When counter reaches state.speed: resets counter, advances frame (wraps in state range)
     * 3. Checks each outgoing transition's condition; on true: transitions state, resets
     *    frame/counter
     */
    private fun generateStateMachineFunction(
        actor: ActorIR,
        actorId: String,
        exprVisitor: ExprVisitor,
    ): CFunction {
        val stateVar = CVar("_${actorId}_anim_state")
        val frameVar = CVar("_${actorId}_anim_frame")
        val counterVar = CVar("_${actorId}_anim_counter")
        val actorIdUpper = actorId.uppercase()

        val cases =
            actor.animationStates.map { state ->
                val stateConst = CRawExpr("ANIM_${actorIdUpper}_${state.name.uppercase()}")
                val caseBody = mutableListOf<CStatement>()

                // Frame cycling: increment counter, when >= speed reset and advance frame
                caseBody += CExprStatement(CUnaryExpr("++", counterVar))
                val frameCycleBody = mutableListOf<CStatement>()
                frameCycleBody += CExprStatement(CBinaryExpr(counterVar, "=", CLiteral(0)))
                if (state.loop) {
                    // Advance frame and wrap: if frame > endFrame, reset to startFrame
                    frameCycleBody += CExprStatement(CUnaryExpr("++", frameVar))
                    frameCycleBody +=
                        CIf(
                            condition = CBinaryExpr(frameVar, ">", CLiteral(state.endFrame)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(frameVar, "=", CLiteral(state.startFrame))
                                    )
                                ),
                        )
                } else {
                    // No loop: clamp at endFrame
                    frameCycleBody +=
                        CIf(
                            condition = CBinaryExpr(frameVar, "<", CLiteral(state.endFrame)),
                            thenBody = listOf(CExprStatement(CUnaryExpr("++", frameVar))),
                        )
                }
                caseBody +=
                    CIf(
                        condition = CBinaryExpr(counterVar, ">=", CLiteral(state.speed)),
                        thenBody = frameCycleBody,
                    )

                // Auto-transition condition checks
                for (transition in state.transitions) {
                    val condition = transition.condition
                    if (condition != null) {
                        val targetConst =
                            CRawExpr("ANIM_${actorIdUpper}_${transition.toState.uppercase()}")
                        val transitionBody =
                            listOf(
                                CExprStatement(CBinaryExpr(stateVar, "=", targetConst)),
                                CExprStatement(CBinaryExpr(frameVar, "=", CLiteral(0))),
                                CExprStatement(CBinaryExpr(counterVar, "=", CLiteral(0))),
                            )
                        caseBody +=
                            CIf(condition = exprVisitor.visit(condition), thenBody = transitionBody)
                    }
                }

                caseBody += CBreak
                CSwitchCase(value = stateConst, body = caseBody)
            }

        return CFunction(
            name = "update_animation_$actorId",
            returnType = CVoid,
            body = listOf(CSwitch(expr = stateVar, cases = cases)),
            sectionComment =
                "Animation state machine: $actorId (${actor.animationStates.size} states)",
        )
    }

    // =========================================================================
    // Physics support
    // =========================================================================

    /**
     * Generate signed velocity variable declarations for actors with [ActorIR.physicsConfig] or
     * [MovementStyle.PHYSICS] movement.
     *
     * For each actor with physics configuration, produces:
     * - `INT8 _actorId_vx = velocityX;` — signed X velocity (pixels/frame)
     * - `INT8 _actorId_vy = velocityY;` — signed Y velocity (pixels/frame)
     *
     * Velocity variables use [CI8] (signed 8-bit) to represent both positive and negative
     * velocities. Returns empty list for actors without physics.
     *
     * When an actor has [MovementStyle.PHYSICS] but no explicit [PhysicsConfig], defaults are used
     * (zero velocity, zero acceleration, zero gravity).
     */
    fun generatePhysicsVars(actor: ActorIR): List<CVarDecl> {
        val config =
            actor.physicsConfig
                ?: if (actor.movementConfig?.style == MovementStyle.PHYSICS) PhysicsConfig()
                else return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        val vars =
            mutableListOf(
                CVarDecl(
                    name = "${prefix}_vx",
                    type = CI8,
                    initializer = CLiteral(config.velocityX),
                ),
                CVarDecl(
                    name = "${prefix}_vy",
                    type = CI8,
                    initializer = CLiteral(config.velocityY),
                ),
            )
        // Advanced physics: coyote time counter (UINT8, counts down from coyoteFrames to 0)
        if (config.coyoteFrames > 0) {
            vars += CVarDecl(name = "${prefix}_coyote", type = CU8, initializer = CLiteral(0))
        }
        // Advanced physics: wall contact flag (0=none, 1=left wall, 2=right wall)
        if (config.wallJump || config.wallResponse == WallResponse.SLIDE) {
            vars += CVarDecl(name = "${prefix}_wall_contact", type = CU8, initializer = CLiteral(0))
        }
        // Advanced physics: jump-held flag for variable-height jump
        if (config.variableJump) {
            vars += CVarDecl(name = "${prefix}_jump_held", type = CU8, initializer = CLiteral(0))
        }
        // Fixed-point fractional position/velocity accumulators
        when (config.fixedPointMode) {
            FixedPointMode.FP44 -> {
                // 4.4 fixed-point: UINT8 position accumulators, INT8 velocity accumulators
                vars += CVarDecl(name = "${prefix}_x_frac", type = CU8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_y_frac", type = CU8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vx_frac", type = CI8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vy_frac", type = CI8, initializer = CLiteral(0))
            }
            FixedPointMode.FP88 -> {
                // 8.8 fixed-point: UINT16 position accumulators, INT16 velocity accumulators
                vars += CVarDecl(name = "${prefix}_x_frac", type = CU16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_y_frac", type = CU16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vx_frac", type = CI16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vy_frac", type = CI16, initializer = CLiteral(0))
            }
            FixedPointMode.INTEGER -> {
                // INTEGER mode: no fractional variables (existing behavior, unchanged)
            }
        }
        return vars
    }

    /**
     * Generate `#define` constants for physics parameters.
     *
     * For each actor with a non-null [ActorIR.physicsConfig] or [MovementStyle.PHYSICS] movement,
     * produces:
     * - `#define ACCEL_X_{ACTORID} accelerationX` — X acceleration
     * - `#define ACCEL_Y_{ACTORID} accelerationY` — Y acceleration
     * - `#define GRAVITY_{ACTORID} gravity` — gravity constant
     * - `#define MAX_FALL_{ACTORID} maxFallSpeed` — max downward velocity
     * - `#define BOUNCE_{ACTORID} bounce` — bounce coefficient 0-255
     *
     * When an actor has [MovementStyle.PHYSICS] but no explicit [PhysicsConfig], defaults are used
     * (zero acceleration, zero gravity, maxFallSpeed=8, zero bounce).
     *
     * Returns empty list for actors without physics.
     */
    fun generatePhysicsDefines(actor: ActorIR): List<CDefine> {
        val config =
            actor.physicsConfig
                ?: if (actor.movementConfig?.style == MovementStyle.PHYSICS) PhysicsConfig()
                else return emptyList()
        val actorIdUpper = sanitizeId(actor.id).uppercase()
        val defines =
            mutableListOf(
                CDefine("ACCEL_X_$actorIdUpper", "${config.accelerationX}"),
                CDefine("ACCEL_Y_$actorIdUpper", "${config.accelerationY}"),
                CDefine("GRAVITY_$actorIdUpper", "${config.gravity}"),
                CDefine("MAX_FALL_$actorIdUpper", "${config.maxFallSpeed}"),
                CDefine("BOUNCE_$actorIdUpper", "${config.bounce}"),
            )
        // Advanced physics defines (only emitted when the feature is enabled)
        if (config.variableJump) {
            defines += CDefine("JUMP_CUT_$actorIdUpper", "${config.jumpCutMultiplier}")
        }
        if (config.coyoteFrames > 0) {
            defines += CDefine("COYOTE_$actorIdUpper", "${config.coyoteFrames}")
        }
        if (config.wallJump) {
            defines += CDefine("WALLJUMP_VX_$actorIdUpper", "${config.wallJumpVelocityX}")
            defines += CDefine("WALLJUMP_VY_$actorIdUpper", "${config.wallJumpVelocityY}")
        }
        return defines
    }

    // =========================================================================
    // SMOOTH movement (acceleration/friction) support
    // =========================================================================

    /**
     * Generate signed velocity variable declarations for SMOOTH movement with acceleration.
     *
     * For each actor whose [MovementConfig.smoothConfig] is non-null, produces:
     * - `INT8 _actorId_vx = 0;` — signed X velocity (pixels/frame)
     * - `INT8 _actorId_vy = 0;` — signed Y velocity (pixels/frame)
     *
     * Returns empty list when smooth acceleration is not configured.
     */
    fun generateSmoothMovementVars(actor: ActorIR): List<CVarDecl> {
        if (actor.movementConfig?.style != MovementStyle.SMOOTH) return emptyList()
        val smooth = actor.movementConfig?.smoothConfig ?: return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        val vars =
            mutableListOf(
                CVarDecl(name = "${prefix}_vx", type = CI8, initializer = CLiteral(0)),
                CVarDecl(name = "${prefix}_vy", type = CI8, initializer = CLiteral(0)),
            )
        // Fixed-point fractional position/velocity accumulators for SMOOTH movement
        when (smooth.fixedPointMode) {
            FixedPointMode.FP44 -> {
                // 4.4 fixed-point: UINT8 position accumulators, INT8 velocity accumulators
                vars += CVarDecl(name = "${prefix}_x_frac", type = CU8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_y_frac", type = CU8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vx_frac", type = CI8, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vy_frac", type = CI8, initializer = CLiteral(0))
            }
            FixedPointMode.FP88 -> {
                // 8.8 fixed-point: UINT16 position accumulators, INT16 velocity accumulators
                vars += CVarDecl(name = "${prefix}_x_frac", type = CU16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_y_frac", type = CU16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vx_frac", type = CI16, initializer = CLiteral(0))
                vars += CVarDecl(name = "${prefix}_vy_frac", type = CI16, initializer = CLiteral(0))
            }
            FixedPointMode.INTEGER -> {
                // INTEGER mode: no fractional variables (existing behavior, unchanged)
            }
        }
        return vars
    }

    /**
     * Generate `#define` constants for SMOOTH movement with acceleration.
     *
     * For each actor with a non-null [MovementConfig.smoothConfig], produces:
     * - `#define ACCEL_{ACTORID} acceleration` — acceleration per frame
     * - `#define FRICTION_{ACTORID} friction` — friction deceleration per frame
     * - `#define SPEED_{ACTORID} speed` — maximum velocity clamp
     *
     * Returns empty list when smooth acceleration is not configured.
     */
    fun generateSmoothMovementDefines(actor: ActorIR): List<CDefine> {
        val smooth =
            actor.movementConfig?.takeIf { it.style == MovementStyle.SMOOTH }?.smoothConfig
                ?: return emptyList()
        val actorIdUpper = sanitizeId(actor.id).uppercase()
        return listOf(
            CDefine("ACCEL_$actorIdUpper", "${smooth.acceleration}"),
            CDefine("FRICTION_$actorIdUpper", "${smooth.friction}"),
            CDefine("SPEED_$actorIdUpper", "${smooth.speed}"),
        )
    }

    /**
     * Generate the acceleration/friction SMOOTH movement statements for one axis.
     *
     * Internal helper used by [generateMovementFunction] when [MovementConfig.smoothConfig] is set.
     *
     * For each axis (X = LEFT/RIGHT, Y = UP/DOWN):
     * 1. Apply acceleration toward d-pad direction
     * 2. Apply friction toward zero when no d-pad on that axis
     * 3. Clamp to [-SPEED_ACTORID, SPEED_ACTORID]
     *
     * If [SmoothMovementConfig.diagonalMode] is [DiagonalMode.NORMALIZED], appends diagonal
     * scaling: `* 181 >> 8` (≈ 0.707) when both axes have non-zero velocity.
     *
     * Final position update: `_x += (UINT8)_vx; _y += (UINT8)_vy`
     */
    @Suppress("LongMethod") // Movement codegen is inherently multi-statement
    private fun generateSmoothAccelerationStatements(
        actorId: String,
        smooth: SmoothMovementConfig,
        xVar: CVar,
        yVar: CVar,
    ): List<CStatement> {
        val actorIdUpper = actorId.uppercase()
        val vxVar = CVar("_${actorId}_vx")
        val vyVar = CVar("_${actorId}_vy")
        val accelDef = CVar("ACCEL_$actorIdUpper")
        val frictionDef = CVar("FRICTION_$actorIdUpper")
        val speedDef = CVar("SPEED_$actorIdUpper")

        val stmts = mutableListOf<CStatement>()

        // --- Horizontal axis (X) ---
        // Apply acceleration from d-pad
        stmts +=
            CIf(
                condition = CCall("dpad_held", listOf(CVar("J_LEFT"))),
                thenBody = listOf(CExprStatement(CBinaryExpr(vxVar, "-=", accelDef))),
            )
        stmts +=
            CIf(
                condition = CCall("dpad_held", listOf(CVar("J_RIGHT"))),
                thenBody = listOf(CExprStatement(CBinaryExpr(vxVar, "+=", accelDef))),
            )
        // Apply friction toward zero when no horizontal d-pad
        stmts +=
            CIf(
                condition = CUnaryExpr("!", CCall("dpad_held", listOf(CVar("J_LEFT | J_RIGHT")))),
                thenBody = buildFrictionStatements(vxVar, frictionDef),
            )
        // Clamp to [-SPEED, SPEED]
        stmts +=
            CIf(
                condition = CBinaryExpr(vxVar, ">", speedDef),
                thenBody = listOf(CExprStatement(CBinaryExpr(vxVar, "=", speedDef))),
            )
        stmts +=
            CIf(
                condition = CBinaryExpr(vxVar, "<", CUnaryExpr("-", speedDef)),
                thenBody =
                    listOf(CExprStatement(CBinaryExpr(vxVar, "=", CUnaryExpr("-", speedDef)))),
            )

        // --- Vertical axis (Y) ---
        // Apply acceleration from d-pad
        stmts +=
            CIf(
                condition = CCall("dpad_held", listOf(CVar("J_UP"))),
                thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "-=", accelDef))),
            )
        stmts +=
            CIf(
                condition = CCall("dpad_held", listOf(CVar("J_DOWN"))),
                thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "+=", accelDef))),
            )
        // Apply friction toward zero when no vertical d-pad
        stmts +=
            CIf(
                condition = CUnaryExpr("!", CCall("dpad_held", listOf(CVar("J_UP | J_DOWN")))),
                thenBody = buildFrictionStatements(vyVar, frictionDef),
            )
        // Clamp to [-SPEED, SPEED]
        stmts +=
            CIf(
                condition = CBinaryExpr(vyVar, ">", speedDef),
                thenBody = listOf(CExprStatement(CBinaryExpr(vyVar, "=", speedDef))),
            )
        stmts +=
            CIf(
                condition = CBinaryExpr(vyVar, "<", CUnaryExpr("-", speedDef)),
                thenBody =
                    listOf(CExprStatement(CBinaryExpr(vyVar, "=", CUnaryExpr("-", speedDef)))),
            )

        // --- Diagonal normalization (NORMALIZED mode) ---
        // When both axes non-zero: scale each by * 181 >> 8 (≈ 0.707)
        if (smooth.diagonalMode == DiagonalMode.NORMALIZED) {
            stmts +=
                CIf(
                    condition =
                        CBinaryExpr(
                            CBinaryExpr(vxVar, "!=", CLiteral(0)),
                            "&&",
                            CBinaryExpr(vyVar, "!=", CLiteral(0)),
                        ),
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(vxVar, "=", CRawExpr("(_${actorId}_vx * 181) >> 8"))
                            ),
                            CExprStatement(
                                CBinaryExpr(vyVar, "=", CRawExpr("(_${actorId}_vy * 181) >> 8"))
                            ),
                        ),
                )
        }

        // --- Apply velocity to position ---
        // Fixed-point mode: accumulate fractional velocity into fractional position,
        // then extract integer pixel position via bit shift.
        // INTEGER mode (default): cast I8 velocity directly to UINT8 for position.
        when (smooth.fixedPointMode) {
            FixedPointMode.FP44 -> {
                // 4.4 fixed-point: accumulate sub-pixel velocity, extract with >> 4
                val xFracVar = CVar("_${actorId}_x_frac")
                val yFracVar = CVar("_${actorId}_y_frac")
                val vxFracVar = CVar("_${actorId}_vx_frac")
                val vyFracVar = CVar("_${actorId}_vy_frac")
                stmts += CExprStatement(CBinaryExpr(vxFracVar, "+=", vxVar))
                stmts += CExprStatement(CBinaryExpr(vyFracVar, "+=", vyVar))
                stmts += CExprStatement(CBinaryExpr(xFracVar, "+=", vxFracVar))
                stmts += CExprStatement(CBinaryExpr(yFracVar, "+=", vyFracVar))
                stmts +=
                    CExprStatement(
                        CBinaryExpr(xVar, "=", CCast(CU8, CRawExpr("_${actorId}_x_frac >> 4")))
                    )
                stmts +=
                    CExprStatement(
                        CBinaryExpr(yVar, "=", CCast(CU8, CRawExpr("_${actorId}_y_frac >> 4")))
                    )
            }
            FixedPointMode.FP88 -> {
                // 8.8 fixed-point: accumulate sub-pixel velocity, extract with >> 8
                val xFracVar = CVar("_${actorId}_x_frac")
                val yFracVar = CVar("_${actorId}_y_frac")
                val vxFracVar = CVar("_${actorId}_vx_frac")
                val vyFracVar = CVar("_${actorId}_vy_frac")
                stmts += CExprStatement(CBinaryExpr(vxFracVar, "+=", vxVar))
                stmts += CExprStatement(CBinaryExpr(vyFracVar, "+=", vyVar))
                stmts += CExprStatement(CBinaryExpr(xFracVar, "+=", vxFracVar))
                stmts += CExprStatement(CBinaryExpr(yFracVar, "+=", vyFracVar))
                stmts +=
                    CExprStatement(
                        CBinaryExpr(xVar, "=", CCast(CU8, CRawExpr("_${actorId}_x_frac >> 8")))
                    )
                stmts +=
                    CExprStatement(
                        CBinaryExpr(yVar, "=", CCast(CU8, CRawExpr("_${actorId}_y_frac >> 8")))
                    )
            }
            FixedPointMode.INTEGER -> {
                // INTEGER mode (default): direct integer velocity to position
                stmts += CExprStatement(CBinaryExpr(xVar, "+=", CCast(CU8, vxVar)))
                stmts += CExprStatement(CBinaryExpr(yVar, "+=", CCast(CU8, vyVar)))
            }
        }

        return stmts
    }

    /**
     * Build friction deceleration statements toward zero for a velocity variable.
     *
     * Generated C logic:
     * ```c
     * if (vVar > 0) { vVar -= friction; if (vVar < 0) vVar = 0; }
     * if (vVar < 0) { vVar += friction; if (vVar > 0) vVar = 0; }
     * ```
     */
    private fun buildFrictionStatements(vVar: CVar, frictionDef: CVar): List<CStatement> =
        listOf(
            CIf(
                condition = CBinaryExpr(vVar, ">", CIntLiteral(0)),
                thenBody =
                    listOf(
                        CExprStatement(CBinaryExpr(vVar, "-=", frictionDef)),
                        CIf(
                            condition = CBinaryExpr(vVar, "<", CIntLiteral(0)),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(vVar, "=", CIntLiteral(0)))),
                        ),
                    ),
            ),
            CIf(
                condition = CBinaryExpr(vVar, "<", CIntLiteral(0)),
                thenBody =
                    listOf(
                        CExprStatement(CBinaryExpr(vVar, "+=", frictionDef)),
                        CIf(
                            condition = CBinaryExpr(vVar, ">", CIntLiteral(0)),
                            thenBody =
                                listOf(CExprStatement(CBinaryExpr(vVar, "=", CIntLiteral(0)))),
                        ),
                    ),
            ),
        )

    /**
     * Generate waypoint patrol route variable declarations for actors with [ActorIR.waypointRoute].
     *
     * For each actor with a non-null waypoint route, produces:
     * - `const UINT8 _actorId_wp_x[] = {x1, x2, ...};` — X coordinates (pixels)
     * - `const UINT8 _actorId_wp_y[] = {y1, y2, ...};` — Y coordinates (pixels)
     * - `UINT8 _actorId_wp_idx = 0;` — current waypoint index
     *
     * The arrays store pixel coordinates as provided by the DSL. Backend code (visitWaypointStep)
     * handles tile-coordinate conversion at runtime via PF_GRID_SIZE division when needed.
     *
     * Returns empty list for actors without waypoint routes.
     */
    fun generateWaypointVars(actor: ActorIR): List<CVarDecl> {
        val route = actor.waypointRoute ?: return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        val xValues = route.points.joinToString(", ") { it.first.toString() }
        val yValues = route.points.joinToString(", ") { it.second.toString() }
        return listOf(
            CVarDecl(
                name = "${prefix}_wp_x",
                type = CArray(CU8),
                initializer = CRawExpr("{ $xValues }"),
                isConst = true,
            ),
            CVarDecl(
                name = "${prefix}_wp_y",
                type = CArray(CU8),
                initializer = CRawExpr("{ $yValues }"),
                isConst = true,
            ),
            CVarDecl(name = "${prefix}_wp_idx", type = CU8, initializer = CLiteral(0)),
        )
    }

    /**
     * Generate `#define` constant for waypoint count per actor.
     *
     * For each actor with a non-null [ActorIR.waypointRoute], produces:
     * - `#define _actorId_wp_count N` — number of waypoints in the route
     *
     * This constant is referenced by [ScriptOpVisitor.visitWaypointStep] for index wrapping when
     * the patrol route loops.
     *
     * Returns empty list for actors without waypoint routes.
     */
    fun generateWaypointDefines(actor: ActorIR): List<CDefine> {
        val route = actor.waypointRoute ?: return emptyList()
        val prefix = "_${sanitizeId(actor.id)}"
        return listOf(CDefine("${prefix}_wp_count", "${route.points.size}"))
    }

    /**
     * Sanitize an actor ID for use in C variable names.
     *
     * Replaces dots with underscores to handle IDs like `player.entity` → `player_entity`. Other
     * sanitization may be added here in future without changing the public API.
     */
    fun sanitizeId(id: String): String = id.replace('.', '_')

    /**
     * Build a position expression with hardware offset and tile column/row offset.
     *
     * For a base position variable (e.g. `_ball_x`) and a column offset of 1 at tile size 8px:
     * - `colOffset=0`: `_ball_x + 8` (just hardware offset)
     * - `colOffset=1`: `_ball_x + 16` (hardware offset + one tile)
     *
     * @param varName The position variable name (e.g. `_ball_x`).
     * @param colOffset The column (or row) index within the metasprite grid.
     * @param hardwareOffset The GBDK hardware offset (+8 for x, +16 for y).
     */
    private fun buildPositionExpr(varName: String, colOffset: Int, hardwareOffset: Int): CExpr {
        val tileOffset = colOffset * 8
        val totalOffset = hardwareOffset + tileOffset
        return CBinaryExpr(CVar(varName), "+", CLiteral(totalOffset))
    }
}
