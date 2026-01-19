/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.features

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.movement.FreeRoamMovementController
import io.github.gbkt.core.movement.GridMovementController
import io.github.gbkt.core.movement.MovementController
import io.github.gbkt.core.movement.MovementType
import io.github.gbkt.core.movement.PhysicsMovementController
import io.github.gbkt.core.movement.TopDownMovementController

// =============================================================================
// PLUGGABLE MOVEMENT CONTROLLER CODE GENERATION
// =============================================================================

/** Helper to generate a list of statements. */
private fun CodeGenerator.generateStatementList(statements: List<IRStatement>) {
    for (stmt in statements) {
        generateStatement(stmt)
    }
}

/**
 * Generate pluggable movement controller system.
 *
 * Creates:
 * - Movement type constants
 * - Controller configuration tables
 * - Position and velocity state variables
 * - Movement update functions for each type
 * - Input handling and collision helpers
 */
internal fun CodeGenerator.generateMovementControllerSystem() {
    val controllers = game.movementControllers
    if (controllers.isEmpty()) return

    line("// =============================================================================")
    line("// PLUGGABLE MOVEMENT CONTROLLER SYSTEM")
    line("// =============================================================================")
    line()

    // Generate movement type constants
    generateMovementTypeConstants()

    // Generate controller index constants
    generateControllerIndexConstants(controllers)

    // Generate controller configuration tables
    generateControllerConfigTables(controllers)

    // Generate movement state variables
    generateMovementStateVariables(controllers)

    // Generate movement update functions for each type
    generateGridMovementUpdate(controllers.filterIsInstance<GridMovementController>())
    generatePhysicsMovementUpdate(controllers.filterIsInstance<PhysicsMovementController>())
    generateFreeRoamMovementUpdate(controllers.filterIsInstance<FreeRoamMovementController>())
    generateTopDownMovementUpdate(controllers.filterIsInstance<TopDownMovementController>())

    // Generate dispatch function
    generateMovementDispatchFunction(controllers)

    // Generate helper functions
    generateMovementHelperFunctions(controllers)
}

/** Generate movement type constants. */
private fun CodeGenerator.generateMovementTypeConstants() {
    line("// Movement type constants")
    for ((index, type) in MovementType.entries.withIndex()) {
        line("#define MOVE_TYPE_${type.name} ${index}u")
    }
    line()
}

/** Generate controller index constants. */
private fun CodeGenerator.generateControllerIndexConstants(controllers: List<MovementController>) {
    line("// Movement controller index constants")
    for ((index, controller) in controllers.withIndex()) {
        line("#define CTRL_${controller.id.uppercase()} ${index}u")
    }
    line("#define CTRL_COUNT ${controllers.size}u")
    line()
}

/** Generate controller configuration tables. */
private fun CodeGenerator.generateControllerConfigTables(controllers: List<MovementController>) {
    line("// Controller configuration tables")
    line()

    // Movement types
    line("static const UINT8 _ctrl_move_type[CTRL_COUNT] = {")
    indent++
    line(controllers.joinToString(", ") { "MOVE_TYPE_${it.movementType.name}" })
    indent--
    line("};")
    line()

    // Tile sizes
    line("static const UINT8 _ctrl_tile_size[CTRL_COUNT] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.tileSize}u" })
    indent--
    line("};")
    line()

    // Speeds
    line("static const UINT8 _ctrl_speed[CTRL_COUNT] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.speed}u" })
    indent--
    line("};")
    line()

    // Collision enabled flags
    line("static const UINT8 _ctrl_collision[CTRL_COUNT] = {")
    indent++
    line(controllers.joinToString(", ") { if (it.collisionEnabled) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Generate index mapping tables from ctrl_idx to type-specific index
    generateIndexMappingTables(controllers)

    // Type-specific configuration tables
    generateGridConfigTables(controllers.filterIsInstance<GridMovementController>())
    generatePhysicsConfigTables(controllers.filterIsInstance<PhysicsMovementController>())
    generateFreeRoamConfigTables(controllers.filterIsInstance<FreeRoamMovementController>())
    generateTopDownConfigTables(controllers.filterIsInstance<TopDownMovementController>())
}

/** Generate grid movement configuration tables. */
private fun CodeGenerator.generateGridConfigTables(controllers: List<GridMovementController>) {
    if (controllers.isEmpty()) return

    line("// Grid movement configuration")
    line("static const UINT8 _grid_smooth[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { if (it.smoothInterpolation) "1u" else "0u" })
    indent--
    line("};")

    line("static const UINT8 _grid_diagonal[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { if (it.allowDiagonal) "1u" else "0u" })
    indent--
    line("};")
    line()
}

/** Generate physics movement configuration tables. */
private fun CodeGenerator.generatePhysicsConfigTables(
    controllers: List<PhysicsMovementController>
) {
    if (controllers.isEmpty()) return

    line("// Physics movement configuration")
    line("static const UINT8 _phys_accel[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.acceleration}u" })
    indent--
    line("};")

    line("static const UINT8 _phys_friction[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.friction}u" })
    indent--
    line("};")

    line("static const UINT8 _phys_gravity[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.gravity}u" })
    indent--
    line("};")

    line("static const UINT8 _phys_max_speed_x[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.maxSpeedX}u" })
    indent--
    line("};")

    line("static const UINT8 _phys_max_speed_y[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.maxSpeedY}u" })
    indent--
    line("};")

    line("static const INT8 _phys_jump_vel[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.jumpVelocity}" })
    indent--
    line("};")

    line("static const UINT8 _phys_air_jumps[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.airJumps}u" })
    indent--
    line("};")
    line()
}

/** Generate free-roam movement configuration tables. */
private fun CodeGenerator.generateFreeRoamConfigTables(
    controllers: List<FreeRoamMovementController>
) {
    if (controllers.isEmpty()) return

    line("// Free-roam movement configuration")
    line("static const UINT8 _free_eight_dir[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { if (it.eightDirection) "1u" else "0u" })
    indent--
    line("};")

    line("static const UINT8 _free_accel[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.acceleration}u" })
    indent--
    line("};")

    line("static const UINT8 _free_decel[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.deceleration}u" })
    indent--
    line("};")

    line("static const UINT8 _free_max_speed[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.maxSpeed}u" })
    indent--
    line("};")
    line()
}

/** Generate top-down movement configuration tables. */
private fun CodeGenerator.generateTopDownConfigTables(
    controllers: List<TopDownMovementController>
) {
    if (controllers.isEmpty()) return

    line("// Top-down movement configuration")
    line("static const UINT8 _topdown_pixel_perfect[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { if (it.pixelPerfect) "1u" else "0u" })
    indent--
    line("};")

    line("static const UINT8 _topdown_hitbox_w[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.hitboxWidth}u" })
    indent--
    line("};")

    line("static const UINT8 _topdown_hitbox_h[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.hitboxHeight}u" })
    indent--
    line("};")

    line("static const INT8 _topdown_hitbox_ox[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.hitboxOffsetX}" })
    indent--
    line("};")

    line("static const INT8 _topdown_hitbox_oy[${controllers.size}] = {")
    indent++
    line(controllers.joinToString(", ") { "${it.hitboxOffsetY}" })
    indent--
    line("};")
    line()
}

/**
 * Generate index mapping tables from ctrl_idx to type-specific index.
 *
 * Each controller type has its own configuration arrays (e.g., _phys_accel, _free_max_speed). These
 * mapping tables convert from the global ctrl_idx to the type-specific array index. Returns 255 for
 * controllers of a different type.
 */
private fun CodeGenerator.generateIndexMappingTables(controllers: List<MovementController>) {
    val gridControllers = controllers.filterIsInstance<GridMovementController>()
    val physicsControllers = controllers.filterIsInstance<PhysicsMovementController>()
    val freeRoamControllers = controllers.filterIsInstance<FreeRoamMovementController>()
    val topDownControllers = controllers.filterIsInstance<TopDownMovementController>()

    line("// Index mapping tables: ctrl_idx -> type-specific index (255 = invalid)")

    // Grid index mapping
    if (gridControllers.isNotEmpty()) {
        line("static const UINT8 _ctrl_to_grid_idx[CTRL_COUNT] = {")
        indent++
        var gridIdx = 0
        line(
            controllers.joinToString(", ") {
                if (it is GridMovementController) "${gridIdx++}u" else "255u"
            }
        )
        indent--
        line("};")
    }

    // Physics index mapping
    if (physicsControllers.isNotEmpty()) {
        line("static const UINT8 _ctrl_to_phys_idx[CTRL_COUNT] = {")
        indent++
        var physIdx = 0
        line(
            controllers.joinToString(", ") {
                if (it is PhysicsMovementController) "${physIdx++}u" else "255u"
            }
        )
        indent--
        line("};")
    }

    // Free-roam index mapping
    if (freeRoamControllers.isNotEmpty()) {
        line("static const UINT8 _ctrl_to_free_idx[CTRL_COUNT] = {")
        indent++
        var freeIdx = 0
        line(
            controllers.joinToString(", ") {
                if (it is FreeRoamMovementController) "${freeIdx++}u" else "255u"
            }
        )
        indent--
        line("};")
    }

    // Top-down index mapping
    if (topDownControllers.isNotEmpty()) {
        line("static const UINT8 _ctrl_to_td_idx[CTRL_COUNT] = {")
        indent++
        var tdIdx = 0
        line(
            controllers.joinToString(", ") {
                if (it is TopDownMovementController) "${tdIdx++}u" else "255u"
            }
        )
        indent--
        line("};")
    }

    line()
}

/** Generate movement state variables. */
private fun CodeGenerator.generateMovementStateVariables(controllers: List<MovementController>) {
    line("// =============================================================================")
    line("// MOVEMENT STATE VARIABLES")
    line("// =============================================================================")
    line()

    line("// Position state (per controller)")
    line("static UINT16 _move_pos_x[CTRL_COUNT];")
    line("static UINT16 _move_pos_y[CTRL_COUNT];")
    line("static UINT8 _move_sub_x[CTRL_COUNT]; // Sub-pixel X")
    line("static UINT8 _move_sub_y[CTRL_COUNT]; // Sub-pixel Y")
    line()

    line("// Velocity state (for physics/free-roam)")
    line("static INT16 _move_vel_x[CTRL_COUNT];")
    line("static INT16 _move_vel_y[CTRL_COUNT];")
    line()

    line("// Movement state flags")
    line("static UINT8 _move_is_moving[CTRL_COUNT];")
    line("static UINT8 _move_direction[CTRL_COUNT]; // 0=none, 1=right, 2=left, 3=up, 4=down")
    line("static UINT8 _move_on_ground[CTRL_COUNT]; // For physics")
    line("static UINT8 _move_air_jump_count[CTRL_COUNT]; // For physics")
    line()

    line("// Grid movement state")
    line("static UINT8 _move_tile_x[CTRL_COUNT]; // Current tile X")
    line("static UINT8 _move_tile_y[CTRL_COUNT]; // Current tile Y")
    line("static UINT8 _move_target_tile_x[CTRL_COUNT]; // Target tile X (for interpolation)")
    line("static UINT8 _move_target_tile_y[CTRL_COUNT]; // Target tile Y")
    line("static UINT8 _move_progress[CTRL_COUNT]; // Movement progress (0-speed)")
    line()

    // Initialize state
    line("// Initialize movement controller state")
    line("static void _init_movement_controllers(void) {")
    indent++
    line("UINT8 i;")
    line("for (i = 0u; i < CTRL_COUNT; i++) {")
    indent++
    line("_move_pos_x[i] = 0u;")
    line("_move_pos_y[i] = 0u;")
    line("_move_sub_x[i] = 0u;")
    line("_move_sub_y[i] = 0u;")
    line("_move_vel_x[i] = 0;")
    line("_move_vel_y[i] = 0;")
    line("_move_is_moving[i] = 0u;")
    line("_move_direction[i] = 0u;")
    line("_move_on_ground[i] = 1u;")
    line("_move_air_jump_count[i] = 0u;")
    line("_move_tile_x[i] = 0u;")
    line("_move_tile_y[i] = 0u;")
    line("_move_target_tile_x[i] = 0u;")
    line("_move_target_tile_y[i] = 0u;")
    line("_move_progress[i] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate grid movement update functions. */
private fun CodeGenerator.generateGridMovementUpdate(controllers: List<GridMovementController>) {
    if (controllers.isEmpty()) return

    line("// =============================================================================")
    line("// GRID MOVEMENT UPDATE")
    line("// =============================================================================")
    line()

    line("// Update grid movement for a controller")
    line("static void _update_grid_movement(UINT8 ctrl_idx, INT8 dx, INT8 dy) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT || _ctrl_move_type[ctrl_idx] != MOVE_TYPE_GRID) return;")
    line()

    line("// If currently moving, continue interpolation")
    line("if (_move_is_moving[ctrl_idx]) {")
    indent++
    line("_move_progress[ctrl_idx]++;")
    line("if (_move_progress[ctrl_idx] >= _ctrl_speed[ctrl_idx]) {")
    indent++
    line("// Movement complete")
    line("_move_tile_x[ctrl_idx] = _move_target_tile_x[ctrl_idx];")
    line("_move_tile_y[ctrl_idx] = _move_target_tile_y[ctrl_idx];")
    line("_move_is_moving[ctrl_idx] = 0u;")
    line("_move_progress[ctrl_idx] = 0u;")
    indent--
    line("}")
    line("return;")
    indent--
    line("}")
    line()

    line("// No input, no movement")
    line("if (dx == 0 && dy == 0) return;")
    line()

    line("// Calculate target tile")
    line("UINT8 target_x = _move_tile_x[ctrl_idx] + dx;")
    line("UINT8 target_y = _move_tile_y[ctrl_idx] + dy;")
    line()

    line("// Collision check - uses tilemap collision if configured")
    line("if (_ctrl_collision[ctrl_idx]) {")
    indent++
    line("// Check if target tile is blocked (via tilemap collision layer)")
    line("// Uses _check_tile_collision if available from tilemap system")
    line("if (_check_tile_collision(target_x, target_y)) {")
    indent++
    line("// Movement blocked - call onBlocked callback if defined")
    line("return;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Start movement")
    line("_move_target_tile_x[ctrl_idx] = target_x;")
    line("_move_target_tile_y[ctrl_idx] = target_y;")
    line("_move_is_moving[ctrl_idx] = 1u;")
    line("_move_progress[ctrl_idx] = 0u;")
    line("_move_direction[ctrl_idx] = (dx > 0) ? 1u : (dx < 0) ? 2u : (dy < 0) ? 3u : 4u;")
    indent--
    line("}")
    line()

    line("// Get interpolated pixel position for grid controller")
    line("static UINT16 _grid_get_pixel_x(UINT8 ctrl_idx) {")
    indent++
    line("if (!_move_is_moving[ctrl_idx]) {")
    indent++
    line("return _move_tile_x[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    indent--
    line("}")
    line("UINT16 from_x = _move_tile_x[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    line("UINT16 to_x = _move_target_tile_x[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    line("return from_x + (to_x - from_x) * _move_progress[ctrl_idx] / _ctrl_speed[ctrl_idx];")
    indent--
    line("}")
    line()

    line("static UINT16 _grid_get_pixel_y(UINT8 ctrl_idx) {")
    indent++
    line("if (!_move_is_moving[ctrl_idx]) {")
    indent++
    line("return _move_tile_y[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    indent--
    line("}")
    line("UINT16 from_y = _move_tile_y[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    line("UINT16 to_y = _move_target_tile_y[ctrl_idx] * _ctrl_tile_size[ctrl_idx];")
    line("return from_y + (to_y - from_y) * _move_progress[ctrl_idx] / _ctrl_speed[ctrl_idx];")
    indent--
    line("}")
    line()

    // Generate callbacks for each grid controller
    for (controller in controllers) {
        if (controller.onStepStatements.isNotEmpty()) {
            line("// onStep callback for ${controller.id}")
            line("static void _${controller.id}_on_step(void) {")
            indent++
            generateStatementList(controller.onStepStatements)
            indent--
            line("}")
            line()
        }
    }
}

/** Generate physics movement update functions. */
private fun CodeGenerator.generatePhysicsMovementUpdate(
    controllers: List<PhysicsMovementController>
) {
    if (controllers.isEmpty()) return

    line("// =============================================================================")
    line("// PHYSICS MOVEMENT UPDATE")
    line("// =============================================================================")
    line()

    line("// Update physics movement for a controller")
    line("static void _update_physics_movement(UINT8 ctrl_idx, INT8 input_x, UINT8 jump_pressed) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT || _ctrl_move_type[ctrl_idx] != MOVE_TYPE_PHYSICS) return;")
    line()

    line("// Get physics config index via mapping table")
    line("UINT8 phys_idx = _ctrl_to_phys_idx[ctrl_idx];")
    line("if (phys_idx == 255u) return; // Invalid mapping")
    line()

    line("// Apply horizontal acceleration")
    line("if (input_x != 0) {")
    indent++
    line("_move_vel_x[ctrl_idx] += input_x * _phys_accel[phys_idx];")
    line("// Clamp to max speed")
    line(
        "if (_move_vel_x[ctrl_idx] > _phys_max_speed_x[phys_idx]) _move_vel_x[ctrl_idx] = _phys_max_speed_x[phys_idx];"
    )
    line(
        "if (_move_vel_x[ctrl_idx] < -_phys_max_speed_x[phys_idx]) _move_vel_x[ctrl_idx] = -_phys_max_speed_x[phys_idx];"
    )
    indent--
    line("} else {")
    indent++
    line("// Apply friction")
    line("if (_move_vel_x[ctrl_idx] > 0) {")
    indent++
    line("_move_vel_x[ctrl_idx] -= _phys_friction[phys_idx];")
    line("if (_move_vel_x[ctrl_idx] < 0) _move_vel_x[ctrl_idx] = 0;")
    indent--
    line("} else if (_move_vel_x[ctrl_idx] < 0) {")
    indent++
    line("_move_vel_x[ctrl_idx] += _phys_friction[phys_idx];")
    line("if (_move_vel_x[ctrl_idx] > 0) _move_vel_x[ctrl_idx] = 0;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Apply gravity")
    line("_move_vel_y[ctrl_idx] += _phys_gravity[phys_idx];")
    line("if (_move_vel_y[ctrl_idx] > _phys_max_speed_y[phys_idx]) {")
    indent++
    line("_move_vel_y[ctrl_idx] = _phys_max_speed_y[phys_idx];")
    indent--
    line("}")
    line()

    line("// Handle jump")
    line("if (jump_pressed) {")
    indent++
    line("if (_move_on_ground[ctrl_idx]) {")
    indent++
    line("_move_vel_y[ctrl_idx] = _phys_jump_vel[phys_idx];")
    line("_move_on_ground[ctrl_idx] = 0u;")
    line("_move_air_jump_count[ctrl_idx] = 0u;")
    indent--
    line("} else if (_move_air_jump_count[ctrl_idx] < _phys_air_jumps[phys_idx]) {")
    indent++
    line("_move_vel_y[ctrl_idx] = _phys_jump_vel[phys_idx];")
    line("_move_air_jump_count[ctrl_idx]++;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Apply velocity to position (fixed-point: vel is pixels*16)")
    line("_move_sub_x[ctrl_idx] += _move_vel_x[ctrl_idx];")
    line("_move_sub_y[ctrl_idx] += _move_vel_y[ctrl_idx];")
    line()

    line("// Convert sub-pixel to pixel and store new position")
    line("INT16 new_x = _move_pos_x[ctrl_idx] + _move_sub_x[ctrl_idx] / 16;")
    line("INT16 new_y = _move_pos_y[ctrl_idx] + _move_sub_y[ctrl_idx] / 16;")
    line("_move_sub_x[ctrl_idx] = _move_sub_x[ctrl_idx] % 16;")
    line("_move_sub_y[ctrl_idx] = _move_sub_y[ctrl_idx] % 16;")
    line()

    line("// Collision detection and response")
    line("if (_ctrl_collision[ctrl_idx]) {")
    indent++
    line("// Check horizontal collision")
    line("UINT8 tile_x = new_x / _ctrl_tile_size[ctrl_idx];")
    line("UINT8 tile_y = _move_pos_y[ctrl_idx] / _ctrl_tile_size[ctrl_idx];")
    line("if (_check_tile_collision(tile_x, tile_y)) {")
    indent++
    line("// Hit wall horizontally - stop horizontal movement")
    line("new_x = _move_pos_x[ctrl_idx];")
    line("_move_vel_x[ctrl_idx] = 0;")
    indent--
    line("}")
    line()
    line("// Check vertical collision (ground/ceiling)")
    line("tile_x = _move_pos_x[ctrl_idx] / _ctrl_tile_size[ctrl_idx];")
    line("tile_y = new_y / _ctrl_tile_size[ctrl_idx];")
    line("if (_check_tile_collision(tile_x, tile_y)) {")
    indent++
    line("// Hit floor or ceiling")
    line("if (_move_vel_y[ctrl_idx] > 0) {")
    indent++
    line("// Landing on ground")
    line("_move_on_ground[ctrl_idx] = 1u;")
    line("_move_air_jump_count[ctrl_idx] = 0u;")
    indent--
    line("}")
    line("new_y = _move_pos_y[ctrl_idx];")
    line("_move_vel_y[ctrl_idx] = 0;")
    indent--
    line("} else if (_move_vel_y[ctrl_idx] > 0) {")
    indent++
    line("// Falling and no ground hit - set airborne")
    line("_move_on_ground[ctrl_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Apply final position")
    line("_move_pos_x[ctrl_idx] = new_x;")
    line("_move_pos_y[ctrl_idx] = new_y;")
    indent--
    line("}")
    line()

    // Generate callbacks for physics controllers
    for (controller in controllers) {
        if (controller.onLandStatements.isNotEmpty()) {
            line("// onLand callback for ${controller.id}")
            line("static void _${controller.id}_on_land(void) {")
            indent++
            generateStatementList(controller.onLandStatements)
            indent--
            line("}")
            line()
        }
        if (controller.onAirborneStatements.isNotEmpty()) {
            line("// onAirborne callback for ${controller.id}")
            line("static void _${controller.id}_on_airborne(void) {")
            indent++
            generateStatementList(controller.onAirborneStatements)
            indent--
            line("}")
            line()
        }
    }
}

/** Generate free-roam movement update functions. */
private fun CodeGenerator.generateFreeRoamMovementUpdate(
    controllers: List<FreeRoamMovementController>
) {
    if (controllers.isEmpty()) return

    line("// =============================================================================")
    line("// FREE-ROAM MOVEMENT UPDATE")
    line("// =============================================================================")
    line()

    line("// Update free-roam movement for a controller")
    line("static void _update_free_roam_movement(UINT8 ctrl_idx, INT8 input_x, INT8 input_y) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT || _ctrl_move_type[ctrl_idx] != MOVE_TYPE_FREE_ROAM) return;")
    line()

    line("// Get free-roam config index via mapping table")
    line("UINT8 free_idx = _ctrl_to_free_idx[ctrl_idx];")
    line("if (free_idx == 255u) return; // Invalid mapping")
    line()

    line("// Direct movement (no acceleration)")
    line("if (_free_accel[free_idx] == 0u) {")
    indent++
    line("_move_vel_x[ctrl_idx] = input_x * _ctrl_speed[ctrl_idx];")
    line("_move_vel_y[ctrl_idx] = input_y * _ctrl_speed[ctrl_idx];")
    indent--
    line("} else {")
    indent++
    line("// Apply acceleration")
    line("if (input_x != 0) {")
    indent++
    line("_move_vel_x[ctrl_idx] += input_x * _free_accel[free_idx];")
    indent--
    line("} else if (_free_decel[free_idx] > 0u) {")
    indent++
    line("// Apply deceleration")
    line("if (_move_vel_x[ctrl_idx] > 0) _move_vel_x[ctrl_idx] -= _free_decel[free_idx];")
    line("if (_move_vel_x[ctrl_idx] < 0) _move_vel_x[ctrl_idx] += _free_decel[free_idx];")
    indent--
    line("}")
    line("// Same for Y")
    line("if (input_y != 0) {")
    indent++
    line("_move_vel_y[ctrl_idx] += input_y * _free_accel[free_idx];")
    indent--
    line("} else if (_free_decel[free_idx] > 0u) {")
    indent++
    line("if (_move_vel_y[ctrl_idx] > 0) _move_vel_y[ctrl_idx] -= _free_decel[free_idx];")
    line("if (_move_vel_y[ctrl_idx] < 0) _move_vel_y[ctrl_idx] += _free_decel[free_idx];")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Clamp to max speed")
    line(
        "if (_move_vel_x[ctrl_idx] > _free_max_speed[free_idx]) _move_vel_x[ctrl_idx] = _free_max_speed[free_idx];"
    )
    line(
        "if (_move_vel_x[ctrl_idx] < -_free_max_speed[free_idx]) _move_vel_x[ctrl_idx] = -_free_max_speed[free_idx];"
    )
    line(
        "if (_move_vel_y[ctrl_idx] > _free_max_speed[free_idx]) _move_vel_y[ctrl_idx] = _free_max_speed[free_idx];"
    )
    line(
        "if (_move_vel_y[ctrl_idx] < -_free_max_speed[free_idx]) _move_vel_y[ctrl_idx] = -_free_max_speed[free_idx];"
    )
    line()

    line("// Apply velocity")
    line("_move_pos_x[ctrl_idx] += _move_vel_x[ctrl_idx];")
    line("_move_pos_y[ctrl_idx] += _move_vel_y[ctrl_idx];")
    line()

    line("// Update moving flag")
    line(
        "_move_is_moving[ctrl_idx] = (_move_vel_x[ctrl_idx] != 0 || _move_vel_y[ctrl_idx] != 0) ? 1u : 0u;"
    )
    indent--
    line("}")
    line()
}

/** Generate top-down movement update functions. */
private fun CodeGenerator.generateTopDownMovementUpdate(
    controllers: List<TopDownMovementController>
) {
    if (controllers.isEmpty()) return

    line("// =============================================================================")
    line("// TOP-DOWN MOVEMENT UPDATE")
    line("// =============================================================================")
    line()

    line("// Update top-down movement for a controller")
    line("static void _update_topdown_movement(UINT8 ctrl_idx, INT8 input_x, INT8 input_y) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT || _ctrl_move_type[ctrl_idx] != MOVE_TYPE_TOP_DOWN) return;")
    line()

    line("// Get top-down config index via mapping table")
    line("UINT8 td_idx = _ctrl_to_td_idx[ctrl_idx];")
    line("if (td_idx == 255u) return; // Invalid mapping")
    line()

    line("// Calculate new position")
    line("INT16 new_x = _move_pos_x[ctrl_idx] + input_x * _ctrl_speed[ctrl_idx];")
    line("INT16 new_y = _move_pos_y[ctrl_idx] + input_y * _ctrl_speed[ctrl_idx];")
    line()

    line("// Collision detection with hitbox")
    line("if (_ctrl_collision[ctrl_idx]) {")
    indent++
    line("// Calculate hitbox bounds for new position")
    line("INT16 hb_left = new_x + _topdown_hitbox_ox[td_idx];")
    line("INT16 hb_top = new_y + _topdown_hitbox_oy[td_idx];")
    line("INT16 hb_right = hb_left + _topdown_hitbox_w[td_idx];")
    line("INT16 hb_bottom = hb_top + _topdown_hitbox_h[td_idx];")
    line()
    line("// Check collision at hitbox corners (tile-based)")
    line("UINT8 tl_x = hb_left / _ctrl_tile_size[ctrl_idx];")
    line("UINT8 tl_y = hb_top / _ctrl_tile_size[ctrl_idx];")
    line("UINT8 tr_x = hb_right / _ctrl_tile_size[ctrl_idx];")
    line("UINT8 br_y = hb_bottom / _ctrl_tile_size[ctrl_idx];")
    line()
    line("// Check X movement")
    line("if (input_x != 0) {")
    indent++
    line("if (_check_tile_collision(input_x > 0 ? tr_x : tl_x, tl_y) ||")
    line("    _check_tile_collision(input_x > 0 ? tr_x : tl_x, br_y)) {")
    indent++
    line("new_x = _move_pos_x[ctrl_idx]; // Block X movement")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("// Check Y movement")
    line("if (input_y != 0) {")
    indent++
    line("if (_check_tile_collision(tl_x, input_y > 0 ? br_y : tl_y) ||")
    line("    _check_tile_collision(tr_x, input_y > 0 ? br_y : tl_y)) {")
    indent++
    line("new_y = _move_pos_y[ctrl_idx]; // Block Y movement")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Apply final position")
    line("_move_pos_x[ctrl_idx] = new_x;")
    line("_move_pos_y[ctrl_idx] = new_y;")
    line()

    line("// Update tile position")
    line("UINT8 new_tile_x = new_x / _ctrl_tile_size[ctrl_idx];")
    line("UINT8 new_tile_y = new_y / _ctrl_tile_size[ctrl_idx];")
    line()

    line("// Check for tile change and call onTileEnter callback")
    line("if (new_tile_x != _move_tile_x[ctrl_idx] || new_tile_y != _move_tile_y[ctrl_idx]) {")
    indent++
    line("_move_tile_x[ctrl_idx] = new_tile_x;")
    line("_move_tile_y[ctrl_idx] = new_tile_y;")
    line("// Call per-controller onTileEnter callback via dispatch")
    line("_topdown_on_tile_enter_dispatch(ctrl_idx);")
    indent--
    line("}")
    line()

    line("// Update moving flag and direction")
    line("if (input_x != 0 || input_y != 0) {")
    indent++
    line("_move_is_moving[ctrl_idx] = 1u;")
    line(
        "_move_direction[ctrl_idx] = (input_x > 0) ? 1u : (input_x < 0) ? 2u : (input_y < 0) ? 3u : 4u;"
    )
    indent--
    line("} else {")
    indent++
    line("_move_is_moving[ctrl_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate callbacks for top-down controllers
    for (controller in controllers) {
        if (controller.onTileEnterStatements.isNotEmpty()) {
            line("// onTileEnter callback for ${controller.id}")
            line("static void _${controller.id}_on_tile_enter(void) {")
            indent++
            generateStatementList(controller.onTileEnterStatements)
            indent--
            line("}")
            line()
        }
    }
}

/** Generate tile collision helper function. */
private fun CodeGenerator.generateTileCollisionHelper() {
    line("// =============================================================================")
    line("// TILE COLLISION HELPER")
    line("// =============================================================================")
    line()

    line("// Stub for tile collision - should be implemented by tilemap system")
    line("// Returns 1 if tile at (x,y) is solid/blocked, 0 otherwise")
    line("static UINT8 _check_tile_collision(UINT8 tile_x, UINT8 tile_y) {")
    indent++
    line("// Default implementation - override via tilemap collision layer")
    line("// This can be replaced by tilemap codegen when collision layer is defined")
    line("(void)tile_x; (void)tile_y;")
    line("return 0u; // No collision by default")
    indent--
    line("}")
    line()
}

/** Generate onTileEnter callback dispatch for top-down controllers. */
private fun CodeGenerator.generateTopDownCallbackDispatch(
    controllers: List<TopDownMovementController>
) {
    if (controllers.isEmpty()) return

    line("// Top-down onTileEnter callback dispatch")
    line("static void _topdown_on_tile_enter_dispatch(UINT8 ctrl_idx) {")
    indent++
    line("switch (ctrl_idx) {")
    indent++

    val allControllers = game.movementControllers
    for ((index, controller) in allControllers.withIndex()) {
        if (
            controller is TopDownMovementController && controller.onTileEnterStatements.isNotEmpty()
        ) {
            line("case ${index}u: _${controller.id}_on_tile_enter(); break;")
        }
    }

    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate movement dispatch function. */
private fun CodeGenerator.generateMovementDispatchFunction(controllers: List<MovementController>) {
    // Generate tile collision helper first
    generateTileCollisionHelper()

    // Generate top-down callback dispatch if needed
    generateTopDownCallbackDispatch(controllers.filterIsInstance<TopDownMovementController>())

    line("// =============================================================================")
    line("// MOVEMENT DISPATCH")
    line("// =============================================================================")
    line()

    line("// Update movement for any controller type")
    line("static void _update_movement(UINT8 ctrl_idx, INT8 input_x, INT8 input_y, UINT8 jump) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return;")
    line()

    line("switch (_ctrl_move_type[ctrl_idx]) {")
    indent++
    line("case MOVE_TYPE_GRID:")
    indent++
    line("_update_grid_movement(ctrl_idx, input_x, input_y);")
    line("break;")
    indent--
    line("case MOVE_TYPE_PHYSICS:")
    indent++
    line("_update_physics_movement(ctrl_idx, input_x, jump);")
    line("break;")
    indent--
    line("case MOVE_TYPE_FREE_ROAM:")
    indent++
    line("_update_free_roam_movement(ctrl_idx, input_x, input_y);")
    line("break;")
    indent--
    line("case MOVE_TYPE_TOP_DOWN:")
    indent++
    line("_update_topdown_movement(ctrl_idx, input_x, input_y);")
    line("break;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate movement helper functions. */
private fun CodeGenerator.generateMovementHelperFunctions(controllers: List<MovementController>) {
    line("// =============================================================================")
    line("// MOVEMENT HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    line("// Get current pixel X position")
    line("static UINT16 _get_move_x(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0u;")
    line("if (_ctrl_move_type[ctrl_idx] == MOVE_TYPE_GRID) {")
    indent++
    line("return _grid_get_pixel_x(ctrl_idx);")
    indent--
    line("}")
    line("return _move_pos_x[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Get current pixel Y position")
    line("static UINT16 _get_move_y(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0u;")
    line("if (_ctrl_move_type[ctrl_idx] == MOVE_TYPE_GRID) {")
    indent++
    line("return _grid_get_pixel_y(ctrl_idx);")
    indent--
    line("}")
    line("return _move_pos_y[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Set position directly (teleport)")
    line("static void _set_move_position(UINT8 ctrl_idx, UINT16 x, UINT16 y) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return;")
    line("_move_pos_x[ctrl_idx] = x;")
    line("_move_pos_y[ctrl_idx] = y;")
    line("if (_ctrl_move_type[ctrl_idx] == MOVE_TYPE_GRID) {")
    indent++
    line("_move_tile_x[ctrl_idx] = x / _ctrl_tile_size[ctrl_idx];")
    line("_move_tile_y[ctrl_idx] = y / _ctrl_tile_size[ctrl_idx];")
    line("_move_target_tile_x[ctrl_idx] = _move_tile_x[ctrl_idx];")
    line("_move_target_tile_y[ctrl_idx] = _move_tile_y[ctrl_idx];")
    line("_move_is_moving[ctrl_idx] = 0u;")
    line("_move_progress[ctrl_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Set tile position (grid mode)")
    line("static void _set_move_tile(UINT8 ctrl_idx, UINT8 tile_x, UINT8 tile_y) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return;")
    line("_move_tile_x[ctrl_idx] = tile_x;")
    line("_move_tile_y[ctrl_idx] = tile_y;")
    line("_move_target_tile_x[ctrl_idx] = tile_x;")
    line("_move_target_tile_y[ctrl_idx] = tile_y;")
    line("_move_pos_x[ctrl_idx] = tile_x * _ctrl_tile_size[ctrl_idx];")
    line("_move_pos_y[ctrl_idx] = tile_y * _ctrl_tile_size[ctrl_idx];")
    line("_move_is_moving[ctrl_idx] = 0u;")
    indent--
    line("}")
    line()

    line("// Check if controller is moving")
    line("static UINT8 _is_moving(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0u;")
    line("return _move_is_moving[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Get movement direction (1=right, 2=left, 3=up, 4=down, 0=none)")
    line("static UINT8 _get_move_direction(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0u;")
    line("return _move_direction[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Check if on ground (physics mode)")
    line("static UINT8 _is_on_ground(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0u;")
    line("return _move_on_ground[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Get velocity (physics/free-roam)")
    line("static INT16 _get_velocity_x(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0;")
    line("return _move_vel_x[ctrl_idx];")
    indent--
    line("}")
    line()

    line("static INT16 _get_velocity_y(UINT8 ctrl_idx) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return 0;")
    line("return _move_vel_y[ctrl_idx];")
    indent--
    line("}")
    line()

    line("// Set velocity (physics/free-roam)")
    line("static void _set_velocity(UINT8 ctrl_idx, INT16 vx, INT16 vy) {")
    indent++
    line("if (ctrl_idx >= CTRL_COUNT) return;")
    line("_move_vel_x[ctrl_idx] = vx;")
    line("_move_vel_y[ctrl_idx] = vy;")
    indent--
    line("}")
    line()

    // Generate common callbacks for all controllers
    for (controller in controllers) {
        if (controller.onMoveStatements.isNotEmpty()) {
            line("// onMove callback for ${controller.id}")
            line("static void _${controller.id}_on_move(void) {")
            indent++
            generateStatementList(controller.onMoveStatements)
            indent--
            line("}")
            line()
        }
        if (controller.onBlockedStatements.isNotEmpty()) {
            line("// onBlocked callback for ${controller.id}")
            line("static void _${controller.id}_on_blocked(void) {")
            indent++
            generateStatementList(controller.onBlockedStatements)
            indent--
            line("}")
            line()
        }
        if (controller.onPositionChangeStatements.isNotEmpty()) {
            line("// onPositionChange callback for ${controller.id}")
            line("static void _${controller.id}_on_position_change(void) {")
            indent++
            generateStatementList(controller.onPositionChangeStatements)
            indent--
            line("}")
            line()
        }
    }
}
