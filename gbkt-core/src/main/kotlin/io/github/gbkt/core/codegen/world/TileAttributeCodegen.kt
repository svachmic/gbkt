/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.world

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.world.TileBehavior
import io.github.gbkt.core.world.TileDirection

// =============================================================================
// TILE ATTRIBUTE CODE GENERATION
// =============================================================================

/**
 * Generate built-in tile type constants.
 *
 * These constants are always generated as they are used by the exploration and collision systems.
 */
internal fun CodeGenerator.generateBuiltInTileConstants() {
    line("// =============================================================================")
    line("// BUILT-IN TILE TYPE CONSTANTS")
    line("// =============================================================================")
    line()
    line("// Built-in tile type constants (used by exploration and collision systems)")
    line("#define TILE_GROUND 0u")
    line("#define TILE_WALL 1u")
    line("#define TILE_WATER 2u")
    line("#define TILE_EXIT 3u")
    line("#define TILE_PIT 4u")
    line()
}

/**
 * Generate extensible tile attribute system code.
 *
 * Creates:
 * - Tile behavior flag constants
 * - Tile attribute lookup tables
 * - Tile effect application functions
 * - Tile callback handling
 */
internal fun CodeGenerator.generateTileAttributeSystem() {
    if (game.tileAttributes.isEmpty()) return

    line("// =============================================================================")
    line("// EXTENSIBLE TILE ATTRIBUTE SYSTEM")
    line("// =============================================================================")
    line()

    // Generate constants
    generateTileAttributeConstants()

    // Generate lookup tables
    generateTileAttributeTables()

    // Generate callback functions
    generateTileCallbackFunctions()

    // Generate tile effect functions
    generateTileEffectFunctions()
}

/** Generate tile attribute constants. */
private fun CodeGenerator.generateTileAttributeConstants() {
    line("// Tile behavior flags")
    TileBehavior.entries.forEachIndexed { index, behavior ->
        line("#define TILE_BEHAVIOR_${behavior.name} (1u << ${index}u)")
    }
    line()

    line("// Tile direction constants")
    TileDirection.entries.forEachIndexed { index, dir ->
        line("#define TILE_DIR_${dir.name} ${index}u")
    }
    line()

    line("// Custom tile attribute indices")
    line("#define TILE_ATTR_COUNT ${game.tileAttributes.size}u")
    for (attr in game.tileAttributes) {
        line("#define TILE_ATTR_${attr.id.uppercase()} ${attr.attributeIndex}u")
    }
    line()
}

/** Generate tile attribute lookup tables. */
private fun CodeGenerator.generateTileAttributeTables() {
    line("// Tile behavior flags (bitfield)")
    line("static const UINT16 _tile_attr_behaviors[TILE_ATTR_COUNT] = {")
    indent++
    for (attr in game.tileAttributes) {
        val flags = attr.behaviors.map { "TILE_BEHAVIOR_${it.name}" }
        val flagExpr = if (flags.isNotEmpty()) flags.joinToString(" | ") else "0u"
        line("$flagExpr, // ${attr.id}")
    }
    indent--
    line("};")
    line()

    // Speed modifiers (fixed point 8.8)
    line("// Speed modifiers (fixed point 8.8, 256 = 1.0)")
    line("static const UINT16 _tile_attr_speed[TILE_ATTR_COUNT] = {")
    indent++
    for (attr in game.tileAttributes) {
        val speedFixed = (attr.speedModifier * 256).toInt()
        line("${speedFixed}u, // ${attr.id} (${attr.speedModifier}x)")
    }
    indent--
    line("};")
    line()

    // Friction modifiers (fixed point 8.8)
    line("// Friction modifiers (fixed point 8.8, 256 = 1.0)")
    line("static const UINT16 _tile_attr_friction[TILE_ATTR_COUNT] = {")
    indent++
    for (attr in game.tileAttributes) {
        val frictionFixed = (attr.frictionModifier * 256).toInt()
        line("${frictionFixed}u, // ${attr.id} (${attr.frictionModifier})")
    }
    indent--
    line("};")
    line()

    // Direction and speed for conveyor tiles
    val hasConveyors = game.tileAttributes.any { TileBehavior.CONVEYOR in it.behaviors }
    if (hasConveyors) {
        line("// Conveyor direction")
        line("static const UINT8 _tile_attr_direction[TILE_ATTR_COUNT] = {")
        indent++
        for (attr in game.tileAttributes) {
            line("TILE_DIR_${attr.direction.name}, // ${attr.id}")
        }
        indent--
        line("};")
        line()

        line("// Conveyor speed")
        line("static const UINT8 _tile_attr_conveyor_speed[TILE_ATTR_COUNT] = {")
        indent++
        for (attr in game.tileAttributes) {
            line("${attr.conveyorSpeed}u, // ${attr.id}")
        }
        indent--
        line("};")
        line()
    }

    // Damage for hazardous tiles
    val hasHazards = game.tileAttributes.any { TileBehavior.HAZARDOUS in it.behaviors }
    if (hasHazards) {
        line("// Hazard damage")
        line("static const UINT8 _tile_attr_damage[TILE_ATTR_COUNT] = {")
        indent++
        for (attr in game.tileAttributes) {
            line("${attr.damage}u, // ${attr.id}")
        }
        indent--
        line("};")
        line()

        line("// Damage interval (frames)")
        line("static const UINT8 _tile_attr_damage_interval[TILE_ATTR_COUNT] = {")
        indent++
        for (attr in game.tileAttributes) {
            line("${attr.damageInterval}u, // ${attr.id}")
        }
        indent--
        line("};")
        line()
    }

    // Bounce strength
    val hasBouncers = game.tileAttributes.any { TileBehavior.BOUNCING in it.behaviors }
    if (hasBouncers) {
        line("// Bounce strength")
        line("static const UINT8 _tile_attr_bounce[TILE_ATTR_COUNT] = {")
        indent++
        for (attr in game.tileAttributes) {
            line("${attr.bounceStrength}u, // ${attr.id}")
        }
        indent--
        line("};")
        line()
    }
}

/** Generate tile callback functions. */
private fun CodeGenerator.generateTileCallbackFunctions() {
    val attrsWithCallbacks = game.tileAttributes.filter { it.hasCallbacks }
    if (attrsWithCallbacks.isEmpty()) return

    line("// =============================================================================")
    line("// TILE CALLBACKS")
    line("// =============================================================================")
    line()

    // Generate individual callback functions
    for (attr in attrsWithCallbacks) {
        val prefix = attr.id.lowercase()

        if (attr.onEnterStatements.isNotEmpty()) {
            line("// On enter callback for ${attr.id}")
            line("static void _tile_${prefix}_on_enter(void) {")
            indent++
            for (stmt in attr.onEnterStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (attr.onExitStatements.isNotEmpty()) {
            line("// On exit callback for ${attr.id}")
            line("static void _tile_${prefix}_on_exit(void) {")
            indent++
            for (stmt in attr.onExitStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (attr.onStandingStatements.isNotEmpty()) {
            line("// On standing callback for ${attr.id}")
            line("static void _tile_${prefix}_on_standing(void) {")
            indent++
            for (stmt in attr.onStandingStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (attr.onDestroyStatements.isNotEmpty()) {
            line("// On destroy callback for ${attr.id}")
            line("static void _tile_${prefix}_on_destroy(void) {")
            indent++
            for (stmt in attr.onDestroyStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }
    }

    // Generate dispatcher functions
    val attrsWithEnter = game.tileAttributes.filter { it.onEnterStatements.isNotEmpty() }
    if (attrsWithEnter.isNotEmpty()) {
        line("// Dispatch tile enter callback")
        line("static void _tile_on_enter(UINT8 attr_idx) {")
        indent++
        line("switch (attr_idx) {")
        indent++
        for (attr in attrsWithEnter) {
            line(
                "case TILE_ATTR_${attr.id.uppercase()}: _tile_${attr.id.lowercase()}_on_enter(); break;"
            )
        }
        line("default: break;")
        indent--
        line("}")
        indent--
        line("}")
        line()
    }

    val attrsWithExit = game.tileAttributes.filter { it.onExitStatements.isNotEmpty() }
    if (attrsWithExit.isNotEmpty()) {
        line("// Dispatch tile exit callback")
        line("static void _tile_on_exit(UINT8 attr_idx) {")
        indent++
        line("switch (attr_idx) {")
        indent++
        for (attr in attrsWithExit) {
            line(
                "case TILE_ATTR_${attr.id.uppercase()}: _tile_${attr.id.lowercase()}_on_exit(); break;"
            )
        }
        line("default: break;")
        indent--
        line("}")
        indent--
        line("}")
        line()
    }

    val attrsWithStanding = game.tileAttributes.filter { it.onStandingStatements.isNotEmpty() }
    if (attrsWithStanding.isNotEmpty()) {
        line("// Dispatch tile standing callback")
        line("static void _tile_on_standing(UINT8 attr_idx) {")
        indent++
        line("switch (attr_idx) {")
        indent++
        for (attr in attrsWithStanding) {
            line(
                "case TILE_ATTR_${attr.id.uppercase()}: _tile_${attr.id.lowercase()}_on_standing(); break;"
            )
        }
        line("default: break;")
        indent--
        line("}")
        indent--
        line("}")
        line()
    }
}

/** Generate tile effect application functions. */
private fun CodeGenerator.generateTileEffectFunctions() {
    line("// =============================================================================")
    line("// TILE EFFECT FUNCTIONS")
    line("// =============================================================================")
    line()

    // Check if tile has behavior
    line("// Check if tile has a specific behavior")
    line("static UINT8 _tile_has_behavior(UINT8 attr_idx, UINT16 behavior) {")
    indent++
    line("if (attr_idx >= TILE_ATTR_COUNT) return 0u;")
    line("return (_tile_attr_behaviors[attr_idx] & behavior) != 0u;")
    indent--
    line("}")
    line()

    // Check if tile is passable
    line("// Check if tile is passable")
    line("static UINT8 _tile_is_passable(UINT8 attr_idx) {")
    indent++
    line("return _tile_has_behavior(attr_idx, TILE_BEHAVIOR_PASSABLE);")
    indent--
    line("}")
    line()

    // Check if tile is blocking
    line("// Check if tile is blocking")
    line("static UINT8 _tile_is_blocking(UINT8 attr_idx) {")
    indent++
    line("return _tile_has_behavior(attr_idx, TILE_BEHAVIOR_BLOCKING);")
    indent--
    line("}")
    line()

    // Get tile speed modifier
    line("// Get tile speed modifier (fixed point 8.8)")
    line("static UINT16 _tile_get_speed_modifier(UINT8 attr_idx) {")
    indent++
    line("if (attr_idx >= TILE_ATTR_COUNT) return 256u; // 1.0")
    line("return _tile_attr_speed[attr_idx];")
    indent--
    line("}")
    line()

    // Get tile friction
    line("// Get tile friction modifier (fixed point 8.8)")
    line("static UINT16 _tile_get_friction(UINT8 attr_idx) {")
    indent++
    line("if (attr_idx >= TILE_ATTR_COUNT) return 256u; // 1.0")
    line("return _tile_attr_friction[attr_idx];")
    indent--
    line("}")
    line()

    // Apply conveyor effect
    if (game.tileAttributes.any { TileBehavior.CONVEYOR in it.behaviors }) {
        line("// Apply conveyor effect to position")
        line("static void _tile_apply_conveyor(UINT8 attr_idx, INT8* dx, INT8* dy) {")
        indent++
        line("UINT8 dir, speed;")
        line("if (attr_idx >= TILE_ATTR_COUNT) return;")
        line("if (!_tile_has_behavior(attr_idx, TILE_BEHAVIOR_CONVEYOR)) return;")
        line()
        line("dir = _tile_attr_direction[attr_idx];")
        line("speed = _tile_attr_conveyor_speed[attr_idx];")
        line()
        line("switch (dir) {")
        indent++
        line("case TILE_DIR_NORTH: *dy -= speed; break;")
        line("case TILE_DIR_SOUTH: *dy += speed; break;")
        line("case TILE_DIR_EAST: *dx += speed; break;")
        line("case TILE_DIR_WEST: *dx -= speed; break;")
        line("case TILE_DIR_NORTH_EAST: *dx += speed; *dy -= speed; break;")
        line("case TILE_DIR_NORTH_WEST: *dx -= speed; *dy -= speed; break;")
        line("case TILE_DIR_SOUTH_EAST: *dx += speed; *dy += speed; break;")
        line("case TILE_DIR_SOUTH_WEST: *dx -= speed; *dy += speed; break;")
        line("default: break;")
        indent--
        line("}")
        indent--
        line("}")
        line()
    }

    // Apply hazard damage
    if (game.tileAttributes.any { TileBehavior.HAZARDOUS in it.behaviors }) {
        line("// Get hazard damage for tile")
        line("static UINT8 _tile_get_damage(UINT8 attr_idx) {")
        indent++
        line("if (attr_idx >= TILE_ATTR_COUNT) return 0u;")
        line("if (!_tile_has_behavior(attr_idx, TILE_BEHAVIOR_HAZARDOUS)) return 0u;")
        line("return _tile_attr_damage[attr_idx];")
        indent--
        line("}")
        line()

        line("// Get damage interval for hazard tile")
        line("static UINT8 _tile_get_damage_interval(UINT8 attr_idx) {")
        indent++
        line("if (attr_idx >= TILE_ATTR_COUNT) return 0u;")
        line("return _tile_attr_damage_interval[attr_idx];")
        indent--
        line("}")
        line()
    }

    // Apply bounce effect
    if (game.tileAttributes.any { TileBehavior.BOUNCING in it.behaviors }) {
        line("// Get bounce strength for tile")
        line("static UINT8 _tile_get_bounce_strength(UINT8 attr_idx) {")
        indent++
        line("if (attr_idx >= TILE_ATTR_COUNT) return 0u;")
        line("if (!_tile_has_behavior(attr_idx, TILE_BEHAVIOR_BOUNCING)) return 0u;")
        line("return _tile_attr_bounce[attr_idx];")
        indent--
        line("}")
        line()
    }
}
