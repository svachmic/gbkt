/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// VARIABLES AND ENUMS CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateVariables() {
    line("// === Variables ===")

    // Internal state
    line("static UINT8 _joypad;")
    line("static UINT8 _joypad_prev;")
    line("static UINT16 _frame_count;")
    line("static UINT8 _current_scene;")
    line("static UINT8 _next_scene;")
    line("static UINT8 _scene_changed;")

    // Music state
    if (game.music.isNotEmpty()) {
        line("static UINT8 _music_playing;")
        line("static UINT8 _music_fade_timer;")
        line("static UINT8 _music_fade_duration;")
        line("static UINT8 _music_fade_start_vol;")
    }
    line()

    // Game variables
    for (v in game.variables) {
        line("static ${v.type.cType} ${v.name};")
    }

    // Game arrays
    for (arr in game.arrays) {
        if (arr.initialValue == 0) {
            line("static ${arr.elementType.cType} ${arr.name}[${arr.size}];")
        } else {
            val values = (0 until arr.size).joinToString(", ") { "${arr.initialValue}" }
            line("static ${arr.elementType.cType} ${arr.name}[${arr.size}] = { $values };")
        }
    }

    // State machine variables
    for (machine in game.stateMachines) {
        line("static UINT8 _${machine.name}_state;")
        line("static UINT8 _${machine.name}_next;")
        line("static UINT8 _${machine.name}_changed;")
    }

    // Animation state variables
    for (sprite in game.sprites.filter { it.hasAnimations }) {
        line("static UINT8 _${sprite.name}_anim;")
        line("static UINT8 _${sprite.name}_frame;")
        line("static UINT8 _${sprite.name}_timer;")
        line("static UINT8 _${sprite.name}_speed;") // 100 = 1.0x, fixed-point
        line("static UINT8 _${sprite.name}_flags;") // PAUSED, REVERSED, COMPLETE
        line("static UINT8 _${sprite.name}_queue[4];") // Animation queue (up to 4)
        line("static UINT8 _${sprite.name}_queue_len;") // Queue length
    }

    // Sprite-owned position variables
    for (sprite in game.sprites.filter { it.position != null }) {
        val pos = sprite.position!!
        line("static UINT8 ${pos.xVarName} = ${pos.initialX};")
        line("static UINT8 ${pos.yVarName} = ${pos.initialY};")
    }

    // Physics state variables (for entities with physics component)
    val entitiesWithPhysics = game.entities.filter { it.hasPhysics }
    if (entitiesWithPhysics.isNotEmpty()) {
        line()
        line("// Physics state (fixed-point 8.8 for velocity)")
        for (entity in entitiesWithPhysics) {
            val physics = entity.physicsComponent!!
            line("// Entity: ${entity.name} physics")
            line("static INT16 _${entity.name}_vel_x_fp = 0;  // Fixed-point 8.8 velocity X")
            line("static INT16 _${entity.name}_vel_y_fp = 0;  // Fixed-point 8.8 velocity Y")
            line("#define ${entity.name.uppercase()}_GRAVITY ${physics.gravity}")
            line("#define ${entity.name.uppercase()}_FRICTION ${physics.friction}")
            line("#define ${entity.name.uppercase()}_MAX_VEL_X ${physics.maxVelocityX}")
            line("#define ${entity.name.uppercase()}_MAX_VEL_Y ${physics.maxVelocityY}")
        }
    }

    // Camera system variables
    if (game.camera != null) {
        line()
        line("// Camera state")
        line("static UINT8 _camera_x = 0;")
        line("static UINT8 _camera_y = 0;")
        line("static UINT8 _camera_target_x = 0;")
        line("static UINT8 _camera_target_y = 0;")
        line("static UINT8 _camera_smoothing = 0;")
        line("static UINT8 _camera_follow_active = 0;")
        line("static UINT8 *_camera_follow_x = 0;")
        line("static UINT8 *_camera_follow_y = 0;")
        line("static INT8 _camera_offset_x = 0;")
        line("static INT8 _camera_offset_y = 0;")
        line()
        line("// Camera bounds")
        val cam = game.camera
        line("static UINT8 _camera_bounds_min_x = ${cam.config.boundsMinX};")
        line("static UINT8 _camera_bounds_max_x = ${cam.config.boundsMaxX};")
        line("static UINT8 _camera_bounds_min_y = ${cam.config.boundsMinY};")
        line("static UINT8 _camera_bounds_max_y = ${cam.config.boundsMaxY};")
        line()
        line("// Camera shake state")
        line("static UINT8 _shake_intensity = 0;")
        line("static UINT8 _shake_timer = 0;")
        line("static UINT8 _shake_decay = 1;") // 0=none, 1=linear, 2=exponential
        line("static INT8 _shake_offset_x = 0;")
        line("static INT8 _shake_offset_y = 0;")
        line()
        line("// Transition state")
        line("static UINT8 _transition_type = 0;")
        line("static UINT8 _transition_timer = 0;")
        line("static UINT8 _transition_duration = 0;")
        line("static UINT8 _transition_callback = 0;")
        line("static UINT8 _transition_center_x = 80;")
        line("static UINT8 _transition_center_y = 72;")
        line("static UINT16 _transition_flash_color = 0x7FFF;") // RGB555 white
        line()
        line("// Composed transition sequence state")
        line("static UINT8 _trans_seq_active = 0;")
        line("static UINT8 _trans_seq_id = 0;")
        line("static UINT8 _trans_seq_step = 0;")
        line("static UINT8 _trans_seq_timer = 0;")
        line("static UINT8 _trans_target_scene = 0;")
        line("static UINT8 _trans_scene_on_shake_complete = 0;")
        line()
        line("// Transition type constants")
        line("#define TRANS_NONE      0")
        line("#define TRANS_FADE_OUT  1")
        line("#define TRANS_FADE_IN   2")
        line("#define TRANS_FLASH     3")
        line("#define TRANS_WIPE_L    4")
        line("#define TRANS_WIPE_R    5")
        line("#define TRANS_WIPE_U    6")
        line("#define TRANS_WIPE_D    7")
        line("#define TRANS_IRIS_OUT  8")
        line("#define TRANS_IRIS_IN   9")
        line("#define TRANS_WAIT      10")
        line("#define TRANS_SHAKE     11")
    }
    line()

    // Input buffer variables
    if (game.inputBuffers.isNotEmpty()) {
        line("// Input buffer state")
        for (buffer in game.inputBuffers) {
            line(
                "static UINT8 ${buffer.name};  // Window: ${buffer.windowFrames} frames, button mask: 0x${buffer.buttonMask.toString(16)}"
            )
        }
        line()
    }

    // Link cable variables
    if (game.link != null) {
        line("// Link cable state")
        line("static UINT8 _link_connected = 0;")
        line("static UINT8 _link_is_master = 0;")
        line("static UINT8 _link_received = 0;")
        line("static UINT8 _link_has_data = 0;")
        line("static UINT8 _link_send_buffer = 0;")
        line("static UINT8 _link_state = 0;")
        line()
    }

    // Cutscene state variables
    if (game.cutscenes.isNotEmpty()) {
        line("// Cutscene state")
        for (cutscene in game.cutscenes) {
            line("// Cutscene: ${cutscene.name}")
            line("static UINT8 _${cutscene.name}_playing = 0;")
            line("static UINT8 _${cutscene.name}_complete = 0;")
            line("static UINT8 _${cutscene.name}_step = 0;")
            line("static UINT16 _${cutscene.name}_timer = 0;")
        }
        line()
    }

    // Pathfinding variables
    if (game.navGrids.isNotEmpty()) {
        line("// === Pathfinding ===")
        line()
        line("// A* constants")
        line("#define ASTAR_MAX_NODES 64")
        line("#define PATH_MAX_LENGTH 32")
        line()
        line("// A* working memory")
        line("static UINT16 _astar_nodes[ASTAR_MAX_NODES];  // tile<<6 | parent_idx")
        line("static UINT8 _astar_g_scores[ASTAR_MAX_NODES];")
        line("static UINT8 _astar_open_heap[ASTAR_MAX_NODES];")
        line("static UINT8 _astar_open_count;")
        line()
        line("// Path result")
        line("static UINT8 _path_waypoints[PATH_MAX_LENGTH * 2];  // x,y pairs")
        line("static UINT8 _path_length;")
        line("static UINT8 _path_current;")
        line("static UINT8 _path_found;")
        line()

        // Generate navgrid data for each grid
        for (navGrid in game.navGrids) {
            val bytes =
                navGrid.walkableData.toList().chunked(8).map { chunk ->
                    chunk.foldIndexed(0) { i, acc, walkable ->
                        if (walkable) acc or (1 shl i) else acc
                    }
                }
            val bytesStr = bytes.joinToString(", ") { "0x${it.toString(16).padStart(2, '0')}" }
            line("// Navigation grid: ${navGrid.name} (${navGrid.width}x${navGrid.height})")
            line(
                "static const UINT8 ${navGrid.name}_navgrid[] = { ${navGrid.width}, ${navGrid.height}, $bytesStr };"
            )
        }
        line()
    }
}

internal fun CodeGenerator.generateStateMachineEnums() {
    // State machine enums
    if (game.stateMachines.isNotEmpty()) {
        line("// === State Machine States ===")
        for (machine in game.stateMachines) {
            line("enum ${machine.name}_states {")
            indent++
            machine.states.keys.forEachIndexed { i, stateName ->
                val comma = if (i < machine.states.size - 1) "," else ""
                line("STATE_${machine.name.uppercase()}_${stateName.uppercase()}$comma")
            }
            indent--
            line("};")
            line()
        }
    }

    // Animation state enum for sprites with animations
    val animatedSprites = game.sprites.filter { it.hasAnimations }
    if (animatedSprites.isNotEmpty()) {
        line("// === Animation States ===")
        line("#define ANIM_NONE 255")
        line("#define ANIM_FLAG_LOOPING  0x01")
        line("#define ANIM_FLAG_PAUSED   0x02")
        line("#define ANIM_FLAG_COMPLETE 0x04")
        line("#define ANIM_FLAG_REVERSED 0x08")
        for (sprite in animatedSprites) {
            var idx = 0
            for (animName in sprite.animations.keys) {
                line("#define ANIM_${sprite.name.uppercase()}_${animName.uppercase()} $idx")
                idx++
            }
        }
        line()
    }
}
