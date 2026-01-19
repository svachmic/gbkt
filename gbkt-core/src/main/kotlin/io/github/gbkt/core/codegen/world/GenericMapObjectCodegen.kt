/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.world

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.SENTINEL_NO_FLAG
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.world.GenericMapObject
import io.github.gbkt.core.world.MapObjectTypeDefinition
import io.github.gbkt.core.world.ObjectCategory

// =============================================================================
// GENERIC (EXTENSIBLE) MAP OBJECT CODE GENERATION
// =============================================================================

/**
 * Generate generic map object system code.
 *
 * Creates:
 * - Custom object type definitions
 * - Generic object data tables
 * - Type-agnostic interaction handlers
 * - Custom property accessors
 */
internal fun CodeGenerator.generateGenericMapObjectSystem() {
    val types = game.mapObjectTypes
    val objects = game.genericMapObjects

    if (types.isEmpty() && objects.isEmpty()) return

    line("// =============================================================================")
    line("// GENERIC MAP OBJECT SYSTEM")
    line("// =============================================================================")
    line()

    // Generate custom type constants
    if (types.isNotEmpty()) {
        generateCustomTypeConstants(types)
    }

    // Generate category constants
    generateObjectCategoryConstants()

    // Generate generic object data
    if (objects.isNotEmpty()) {
        generateGenericObjectData(objects)
        generateGenericObjectHelpers(objects)
    }
}

/** Generate custom object type constants. */
private fun CodeGenerator.generateCustomTypeConstants(types: List<MapObjectTypeDefinition>) {
    line("// Custom object type constants")
    line("// These extend the built-in types (OBJ_TYPE_*)")
    line()

    // Start custom types after built-in ones
    val builtInCount = 9 // CHEST, DOOR, LEVER, SIGN, NPC, SCONCE, HIDDEN, SAVE_POINT, FOUNTAIN

    for (typeDef in types) {
        val index = builtInCount + typeDef.typeIndex
        line("#define OBJ_TYPE_${typeDef.id.uppercase()} ${index}u")
    }
    line("#define CUSTOM_TYPE_COUNT ${types.size}u")
    line("#define TOTAL_TYPE_COUNT ${builtInCount + types.size}u")
    line()

    // Generate type configuration
    line("// Custom type categories")
    line("static const UINT8 _custom_type_category[CUSTOM_TYPE_COUNT] = {")
    indent++
    line(types.joinToString(", ") { "CAT_${it.category.name}" })
    indent--
    line("};")
    line()

    line("// Custom type interaction flags")
    line("static const UINT8 _custom_type_interactable[CUSTOM_TYPE_COUNT] = {")
    indent++
    line(types.joinToString(", ") { if (it.interactable) "1u" else "0u" })
    indent--
    line("};")
    line()

    line("// Custom type blocking flags")
    line("static const UINT8 _custom_type_blocking[CUSTOM_TYPE_COUNT] = {")
    indent++
    line(types.joinToString(", ") { if (it.solid) "1u" else "0u" })
    indent--
    line("};")
    line()

    line("// Custom type visible flags")
    line("static const UINT8 _custom_type_visible[CUSTOM_TYPE_COUNT] = {")
    indent++
    line(types.joinToString(", ") { if (it.visible) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Generate type name lookup
    for (typeDef in types) {
        val escapedName = typeDef.displayName.replace("\"", "\\\"")
        line("static const char _custom_type_${typeDef.id}_name[] = \"$escapedName\";")
    }
    line()

    line("static const char* const _custom_type_names[CUSTOM_TYPE_COUNT] = {")
    indent++
    line(types.joinToString(", ") { "_custom_type_${it.id}_name" })
    indent--
    line("};")
    line()

    // Generate type helpers
    line("// Get custom type name")
    line("static const char* _get_custom_type_name(UINT8 type_id) {")
    indent++
    line("if (type_id < 9u) return \"built-in\"; // Built-in types")
    line("UINT8 custom_idx = type_id - 9u;")
    line("if (custom_idx >= CUSTOM_TYPE_COUNT) return \"???\";")
    line("return _custom_type_names[custom_idx];")
    indent--
    line("}")
    line()

    line("// Check if custom type is interactable")
    line("static UINT8 _is_custom_type_interactable(UINT8 type_id) {")
    indent++
    line("if (type_id < 9u) return 1u; // Built-in types always interactable")
    line("UINT8 custom_idx = type_id - 9u;")
    line("if (custom_idx >= CUSTOM_TYPE_COUNT) return 0u;")
    line("return _custom_type_interactable[custom_idx];")
    indent--
    line("}")
    line()

    line("// Check if custom type blocks movement")
    line("static UINT8 _is_custom_type_blocking(UINT8 type_id) {")
    indent++
    line("if (type_id < 9u) {")
    indent++
    line("// Built-in blocking types: DOOR (when closed), NPC")
    line("return (type_id == OBJ_TYPE_DOOR || type_id == OBJ_TYPE_NPC) ? 1u : 0u;")
    indent--
    line("}")
    line("UINT8 custom_idx = type_id - 9u;")
    line("if (custom_idx >= CUSTOM_TYPE_COUNT) return 0u;")
    line("return _custom_type_blocking[custom_idx];")
    indent--
    line("}")
    line()
}

/** Generate object category constants. */
private fun CodeGenerator.generateObjectCategoryConstants() {
    line("// Object category constants")
    for ((index, cat) in ObjectCategory.entries.withIndex()) {
        line("#define CAT_${cat.name} ${index}u")
    }
    line()
}

/** Generate generic object data tables. */
private fun CodeGenerator.generateGenericObjectData(objects: List<GenericMapObject>) {
    line("// =============================================================================")
    line("// GENERIC OBJECT INSTANCES")
    line("// =============================================================================")
    line()

    line("#define GENERIC_OBJECT_COUNT ${objects.size}u")
    line()

    // Generate object index constants
    line("// Generic object indices")
    for ((index, obj) in objects.withIndex()) {
        line("#define GOBJ_${obj.id.uppercase()} ${index}u")
    }
    line()

    // Generate position data
    line("// Generic object positions")
    line("static const UINT8 _generic_obj_positions[GENERIC_OBJECT_COUNT * 2u] = {")
    indent++
    for (obj in objects) {
        line("${obj.position.x}u, ${obj.position.y}u, // ${obj.id}")
    }
    indent--
    line("};")
    line()

    // Generate type data
    line("// Generic object types")
    line("static const UINT8 _generic_obj_types[GENERIC_OBJECT_COUNT] = {")
    indent++
    val typeIds = objects.map { obj -> "OBJ_TYPE_${obj.objectType.id.uppercase()}" }
    line(typeIds.joinToString(", "))
    indent--
    line("};")
    line()

    // Generate flag indices
    line("// Generic object flag indices ($SENTINEL_NO_FLAG = no persistence)")
    line("static const UINT8 _generic_obj_flags[GENERIC_OBJECT_COUNT] = {")
    indent++
    line(objects.joinToString(", ") { "${it.flagIndex ?: SENTINEL_NO_FLAG}u" })
    indent--
    line("};")
    line()

    // Generate initial state
    line("// Generic object initial state")
    line("static const UINT8 _generic_obj_initial_state[GENERIC_OBJECT_COUNT] = {")
    indent++
    line(objects.joinToString(", ") { if (it.initialState) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Generate state tracking
    line("// Generic object runtime state (0 = default, 1 = activated)")
    line("static UINT8 _generic_obj_state[GENERIC_OBJECT_COUNT];")
    line()

    // Generate custom properties for objects that have them
    generateGenericObjectCustomProperties(objects)

    // Generate callbacks
    generateGenericObjectCallbacks(objects)
}

/** Generate custom properties storage for generic objects. */
private fun CodeGenerator.generateGenericObjectCustomProperties(objects: List<GenericMapObject>) {
    // Collect all unique custom property keys
    val allPropertyKeys = objects.flatMap { it.properties.keys }.toSet()
    if (allPropertyKeys.isEmpty()) return

    line("// Generic object custom properties")
    for (key in allPropertyKeys) {
        // Determine if this is a numeric or string property
        val sampleValue = objects.firstNotNullOfOrNull { it.properties[key] }
        val isNumeric = sampleValue?.toIntOrNull() != null

        if (isNumeric) {
            line("static const UINT8 _generic_obj_prop_${key}[GENERIC_OBJECT_COUNT] = {")
            indent++
            val values =
                objects.map { obj -> obj.properties[key]?.toIntOrNull()?.toString() ?: "0" }
            line(values.joinToString(", ") { "${it}u" })
            indent--
            line("};")
        } else {
            // String properties - generate string literals
            for (obj in objects) {
                val value = obj.properties[key]
                if (value != null) {
                    val escaped = value.replace("\"", "\\\"")
                    line("static const char _gobj_${obj.id}_${key}[] = \"$escaped\";")
                }
            }
        }
    }
    line()
}

/** Generate callback functions for generic objects. */
private fun CodeGenerator.generateGenericObjectCallbacks(objects: List<GenericMapObject>) {
    // Generate interaction callbacks
    val objectsWithInteract = objects.filter { it.onInteractStatements.isNotEmpty() }
    if (objectsWithInteract.isNotEmpty()) {
        line("// Generic object interaction callbacks")
        for (obj in objectsWithInteract) {
            line("static void _gobj_${obj.id}_interact(void) {")
            indent++
            for (stmt in obj.onInteractStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }
    }

    // Generate step callbacks
    val objectsWithStep = objects.filter { it.onStepStatements.isNotEmpty() }
    if (objectsWithStep.isNotEmpty()) {
        line("// Generic object step callbacks")
        for (obj in objectsWithStep) {
            line("static void _gobj_${obj.id}_step(void) {")
            indent++
            for (stmt in obj.onStepStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }
    }

    // Generate state change callbacks
    val objectsWithStateChange = objects.filter { it.onStateChangeStatements.isNotEmpty() }
    if (objectsWithStateChange.isNotEmpty()) {
        line("// Generic object state change callbacks")
        for (obj in objectsWithStateChange) {
            line("static void _gobj_${obj.id}_state_change(void) {")
            indent++
            for (stmt in obj.onStateChangeStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }
    }
}

/** Generate generic object helper functions. */
private fun CodeGenerator.generateGenericObjectHelpers(objects: List<GenericMapObject>) {
    line("// =============================================================================")
    line("// GENERIC OBJECT HELPERS")
    line("// =============================================================================")
    line()

    // Initialize generic objects
    line("// Initialize generic object states")
    line("static void _init_generic_objects(void) {")
    indent++
    line("for (UINT8 i = 0u; i < GENERIC_OBJECT_COUNT; i++) {")
    indent++
    line("// Check if object has persistent flag")
    line("UINT8 flag_idx = _generic_obj_flags[i];")
    line("if (flag_idx != ${SENTINEL_NO_FLAG}u) {")
    indent++
    line("// Load state from flag")
    line("_generic_obj_state[i] = FLAG_GET(flag_idx >> 3u, 1u << (flag_idx & 7u));")
    indent--
    line("} else {")
    indent++
    line("// Use initial state")
    line("_generic_obj_state[i] = _generic_obj_initial_state[i];")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Find generic object at position
    line("// Find generic object at position")
    line("// Returns object index or 255 if none")
    line("static UINT8 _find_generic_object_at(UINT8 x, UINT8 y) {")
    indent++
    line("for (UINT8 i = 0u; i < GENERIC_OBJECT_COUNT; i++) {")
    indent++
    line("// Check position")
    line("if (_generic_obj_positions[i * 2u] == x && _generic_obj_positions[i * 2u + 1u] == y) {")
    indent++
    line("return i;")
    indent--
    line("}")
    indent--
    line("}")
    line("return 255u;")
    indent--
    line("}")
    line()

    // Get generic object state
    line("// Get generic object state")
    line("static UINT8 _get_generic_object_state(UINT8 obj_idx) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return 0u;")
    line("return _generic_obj_state[obj_idx];")
    indent--
    line("}")
    line()

    // Set generic object state
    line("// Set generic object state")
    line("static void _set_generic_object_state(UINT8 obj_idx, UINT8 state) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return;")
    line()
    line("// Update runtime state")
    line("_generic_obj_state[obj_idx] = state;")
    line()
    line("// Update persistent flag if available")
    line("UINT8 flag_idx = _generic_obj_flags[obj_idx];")
    line("if (flag_idx != ${SENTINEL_NO_FLAG}u) {")
    indent++
    line("if (state) {")
    indent++
    line("FLAG_SET(flag_idx >> 3u, 1u << (flag_idx & 7u));")
    indent--
    line("} else {")
    indent++
    line("FLAG_CLEAR(flag_idx >> 3u, 1u << (flag_idx & 7u));")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Interact with generic object
    line("// Interact with generic object")
    line("static void _interact_generic_object(UINT8 obj_idx) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return;")
    line()
    line("// Dispatch to object-specific callback")
    line("switch (obj_idx) {")
    indent++

    val objectsWithCallbacks = objects.filter { it.onInteractStatements.isNotEmpty() }
    for (obj in objectsWithCallbacks) {
        val idx = objects.indexOf(obj)
        line("case ${idx}u: _gobj_${obj.id}_interact(); break;")
    }

    line("default:")
    indent++
    line("// No custom callback - mark as activated")
    line("_set_generic_object_state(obj_idx, 1u);")
    line("break;")
    indent--

    indent--
    line("}")
    indent--
    line("}")
    line()

    // Step on generic object
    line("// Step on generic object (for triggerOnStep objects)")
    line("static void _step_on_generic_object(UINT8 obj_idx) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return;")
    line()
    line("// Dispatch to object-specific callback")
    line("switch (obj_idx) {")
    indent++

    val objectsWithStep = objects.filter { it.onStepStatements.isNotEmpty() }
    for (obj in objectsWithStep) {
        val idx = objects.indexOf(obj)
        line("case ${idx}u: _gobj_${obj.id}_step(); break;")
    }

    line("default: break;")

    indent--
    line("}")
    indent--
    line("}")
    line()

    // Get object type
    line("// Get generic object type")
    line("static UINT8 _get_generic_object_type(UINT8 obj_idx) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return 255u;")
    line("return _generic_obj_types[obj_idx];")
    indent--
    line("}")
    line()

    // Check if object blocks movement
    line("// Check if generic object blocks movement")
    line("static UINT8 _generic_object_blocks(UINT8 obj_idx) {")
    indent++
    line("if (obj_idx >= GENERIC_OBJECT_COUNT) return 0u;")
    line("UINT8 type_id = _generic_obj_types[obj_idx];")
    line("return _is_custom_type_blocking(type_id);")
    indent--
    line("}")
    line()
}
