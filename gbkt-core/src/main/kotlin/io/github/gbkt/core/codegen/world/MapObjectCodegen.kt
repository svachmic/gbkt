/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.world

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.SENTINEL_NO_FLAG
import io.github.gbkt.core.codegen.SENTINEL_NO_OBJECT
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.world.GenericMapObject
import io.github.gbkt.core.world.PredefinedObjectTypes

// =============================================================================
// MAP OBJECT CODE GENERATION
// =============================================================================

/**
 * Generate map object system code.
 *
 * Creates:
 * - Object type constants
 * - Direction constants
 * - Object state tracking arrays
 * - Object data tables per floor
 * - Interaction handler functions
 * - Object rendering helpers
 */
/** Generate map object stub functions for when no objects are defined. */
internal fun CodeGenerator.generateMapObjectStubs() {
    line("// =============================================================================")
    line("// MAP OBJECT STUB FUNCTIONS (No objects defined)")
    line("// =============================================================================")
    line()
    line("// Check if there's an object at the given position (stub)")
    line("static UINT8 object_at_position(UINT8 floor_id, UINT8 x, UINT8 y) {")
    indent++
    line("(void)floor_id; (void)x; (void)y;")
    line("return ${SENTINEL_NO_OBJECT}u;")
    indent--
    line("}")
    line()
    line("// Interact with object at position (stub)")
    line("static void object_interact(UINT8 floor_id, UINT8 obj_idx) {")
    indent++
    line("(void)floor_id; (void)obj_idx;")
    indent--
    line("}")
    line()
}

internal fun CodeGenerator.generateMapObjectSystem() {
    // Collect all map objects from zones
    val allObjects = collectMapObjects()
    if (allObjects.isEmpty()) {
        // Generate stubs when no objects exist
        generateMapObjectStubs()
        return
    }

    line("// =============================================================================")
    line("// MAP OBJECT SYSTEM")
    line("// =============================================================================")
    line()

    // Generate type constants
    generateMapObjectTypeConstants()

    // Generate direction constants (for NPC facing)
    generateDirectionConstants()

    // Generate object state tracking
    generateObjectStateVariables(allObjects)

    // Generate object data for each zone
    for (zone in game.zones) {
        if (zone.objects.isNotEmpty()) {
            generateZoneObjectData(zone.id, zone.objects)
        }
    }

    // Generate interaction handlers
    generateObjectInteractionHandlers()
}

/** Collect all map objects from all zones. */
private fun CodeGenerator.collectMapObjects(): List<GenericMapObject> =
    game.zones.flatMap { it.objects }

/** Generate map object type constants from PredefinedObjectTypes. */
private fun CodeGenerator.generateMapObjectTypeConstants() {
    line("// Map object type constants")
    val types = PredefinedObjectTypes.all
    for ((index, type) in types.withIndex()) {
        line("#define OBJ_TYPE_${type.id.uppercase()} ${index}u")
    }
    line()
}

/** Generate direction constants (only if not already defined by exploration system). */
private fun CodeGenerator.generateDirectionConstants() {
    // Use #ifndef guards to avoid redefining if ExplorationCodegen already defined them.
    // ExplorationCodegen uses 1-indexed values (DIR_UP=1, DIR_DOWN=2, etc) to allow DIR_NONE=0.
    // We match those values here for consistency.
    line("// Direction constants (guarded - may already be defined by exploration)")
    line("#ifndef DIR_UP")
    line("#define DIR_UP 1u")
    line("#define DIR_DOWN 2u")
    line("#define DIR_LEFT 3u")
    line("#define DIR_RIGHT 4u")
    line("#endif")
    line()
}

/** Generate object state tracking variables. */
private fun CodeGenerator.generateObjectStateVariables(objects: List<GenericMapObject>) {
    line("// Object state tracking")
    line("// Objects with flag indices use global flags for persistence")
    line("// Local state for runtime tracking (not persisted)")
    line("static UINT8 _object_count = ${objects.size}u;")
    line()

    // Generate index constants for each object
    line("// Object index constants")
    var globalIndex = 0
    for (zone in game.zones) {
        for (obj in zone.objects) {
            line("#define OBJ_${obj.id.uppercase()} ${globalIndex}u")
            globalIndex++
        }
    }
    line()
}

/** Generate object data for a specific zone. */
private fun CodeGenerator.generateZoneObjectData(zoneId: String, objects: List<GenericMapObject>) {
    val zoneName = zoneId.uppercase()

    line("// -----------------------------------------------------------------------------")
    line("// Map Objects for Zone: $zoneId")
    line("// -----------------------------------------------------------------------------")
    line()

    line("#define ${zoneName}_OBJECT_COUNT ${objects.size}u")
    line()

    // Generate object position data
    line("// Object positions (x, y) for $zoneId")
    line("static const UINT8 ${zoneId}_object_positions[${objects.size * 2}] = {")
    indent++
    for (obj in objects) {
        line("${obj.position.x}u, ${obj.position.y}u, // ${obj.id}")
    }
    indent--
    line("};")
    line()

    // Generate object types array
    line("// Object types for $zoneId")
    line("static const UINT8 ${zoneId}_object_types[${objects.size}] = {")
    indent++
    line(objects.joinToString(", ") { "OBJ_TYPE_${it.objectType.id.uppercase()}" })
    indent--
    line("};")
    line()

    // Generate object flag indices (SENTINEL_NO_FLAG = no flag)
    line("// Object flag indices for $zoneId ($SENTINEL_NO_FLAG = no persistence)")
    line("static const UINT8 ${zoneId}_object_flags[${objects.size}] = {")
    indent++
    line(objects.joinToString(", ") { "${it.flagIndex ?: SENTINEL_NO_FLAG}u" })
    indent--
    line("};")
    line()

    // Generate type-specific data using properties
    generateChestData(zoneId, objects.filter { it.objectType.id == "chest" })
    generateDoorData(zoneId, objects.filter { it.objectType.id == "door" })
    generateLeverData(zoneId, objects.filter { it.objectType.id == "lever" })
    generateSignData(zoneId, objects.filter { it.objectType.id == "sign" })
    generateNpcData(zoneId, objects.filter { it.objectType.id == "npc" })
    generateSconceData(zoneId, objects.filter { it.objectType.id == "sconce" })
    generateSavePointData(zoneId, objects.filter { it.objectType.id == "save_point" })

    // Generate interaction callbacks
    generateObjectCallbacks(zoneId, objects)
}

/** Generate chest-specific data from properties. */
private fun CodeGenerator.generateChestData(zoneId: String, chests: List<GenericMapObject>) {
    if (chests.isEmpty()) return

    line("// Chest data for $zoneId")
    for (chest in chests) {
        val chestName = "${zoneId}_${chest.id}"
        val locked = chest.properties["locked"]?.toBoolean() ?: false
        val consumesKey = chest.properties["consumesKey"]?.toBoolean() ?: false

        line("// Chest: ${chest.id}")
        line("#define ${chestName.uppercase()}_LOCKED ${if (locked) 1 else 0}u")
        line("#define ${chestName.uppercase()}_CONSUMES_KEY ${if (consumesKey) 1 else 0}u")

        // Generate contents array from properties (item_0, quantity_0, item_1, quantity_1, etc.)
        val contents = mutableListOf<Pair<String, Int>>()
        var i = 0
        while (true) {
            val itemId = chest.properties["item_$i"] ?: break
            val quantity = chest.properties["quantity_$i"]?.toIntOrNull() ?: 1
            contents.add(itemId to quantity)
            i++
        }

        if (contents.isNotEmpty()) {
            line("static const UINT8 ${chestName}_contents[${contents.size * 2}] = {")
            indent++
            for ((itemId, quantity) in contents) {
                val itemIndex = game.items.indexOfFirst { it.id == itemId }.coerceAtLeast(0)
                line("${itemIndex}u, ${quantity}u, // $itemId")
            }
            indent--
            line("};")
        }
    }
    line()
}

/** Generate door-specific data from properties. */
private fun CodeGenerator.generateDoorData(zoneId: String, doors: List<GenericMapObject>) {
    if (doors.isEmpty()) return

    line("// Door data for $zoneId")
    for (door in doors) {
        val doorName = "${zoneId}_${door.id}"
        val startsOpen = door.initialState
        val locked = door.properties["locked"]?.toBoolean() ?: false
        val consumesKey = door.properties["consumesKey"]?.toBoolean() ?: false

        line("// Door: ${door.id}")
        line("#define ${doorName.uppercase()}_STARTS_OPEN ${if (startsOpen) 1 else 0}u")
        line("#define ${doorName.uppercase()}_LOCKED ${if (locked) 1 else 0}u")
        line("#define ${doorName.uppercase()}_CONSUMES_KEY ${if (consumesKey) 1 else 0}u")
    }
    line()
}

/** Generate lever-specific data from properties. */
private fun CodeGenerator.generateLeverData(zoneId: String, levers: List<GenericMapObject>) {
    if (levers.isEmpty()) return

    line("// Lever data for $zoneId")
    for (lever in levers) {
        val leverName = "${zoneId}_${lever.id}"
        val startsOn = lever.initialState
        val oneShot = lever.properties["oneShot"]?.toBoolean() ?: false

        line("// Lever: ${lever.id}")
        line("#define ${leverName.uppercase()}_STARTS_ON ${if (startsOn) 1 else 0}u")
        line("#define ${leverName.uppercase()}_ONE_SHOT ${if (oneShot) 1 else 0}u")
    }
    line()
}

/** Generate sign-specific data from properties. */
private fun CodeGenerator.generateSignData(zoneId: String, signs: List<GenericMapObject>) {
    if (signs.isEmpty()) return

    line("// Sign data for $zoneId")
    for (sign in signs) {
        val signName = "${zoneId}_${sign.id}"
        val requiresFacing = sign.properties["requiresFacing"]?.toBoolean() ?: true
        val text = sign.properties["text"] ?: ""

        line("// Sign: ${sign.id}")
        line("#define ${signName.uppercase()}_REQUIRES_FACING ${if (requiresFacing) 1 else 0}u")

        // Generate sign text as string constant
        val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")
        line("static const char ${signName}_text[] = \"$escapedText\";")
    }
    line()
}

/** Generate NPC-specific data from properties. */
private fun CodeGenerator.generateNpcData(zoneId: String, npcs: List<GenericMapObject>) {
    if (npcs.isEmpty()) return

    line("// NPC data for $zoneId")
    for (npc in npcs) {
        val npcName = "${zoneId}_${npc.id}"
        val displayName = npc.properties["name"] ?: npc.id.replaceFirstChar { it.uppercaseChar() }
        val facing = npc.properties["facing"] ?: "DOWN"
        val stationary = npc.properties["stationary"]?.toBoolean() ?: true
        val movementPattern = npc.properties["movementPattern"]

        line("// NPC: ${npc.id} ($displayName)")
        line("#define ${npcName.uppercase()}_FACING DIR_$facing")
        line("#define ${npcName.uppercase()}_STATIONARY ${if (stationary) 1 else 0}u")
        movementPattern?.let { line("#define ${npcName.uppercase()}_MOVEMENT MOVE_$it") }

        // Generate name string
        val escapedName = displayName.replace("\"", "\\\"")
        line("static const char ${npcName}_name[] = \"$escapedName\";")
    }
    line()
}

/** Generate sconce-specific data from properties. */
private fun CodeGenerator.generateSconceData(zoneId: String, sconces: List<GenericMapObject>) {
    if (sconces.isEmpty()) return

    line("// Sconce data for $zoneId")
    for (sconce in sconces) {
        val sconceName = "${zoneId}_${sconce.id}"
        val startsLit = sconce.initialState
        val lightColor = sconce.properties["lightColor"]?.toIntOrNull() ?: 0
        val lightRadius = sconce.properties["lightRadius"]?.toIntOrNull() ?: 3

        line("// Sconce: ${sconce.id}")
        line("#define ${sconceName.uppercase()}_STARTS_LIT ${if (startsLit) 1 else 0}u")
        line("#define ${sconceName.uppercase()}_LIGHT_COLOR ${lightColor}u")
        line("#define ${sconceName.uppercase()}_LIGHT_RADIUS ${lightRadius}u")
    }
    line()
}

/** Generate save point-specific data from properties. */
private fun CodeGenerator.generateSavePointData(
    zoneId: String,
    savePoints: List<GenericMapObject>,
) {
    if (savePoints.isEmpty()) return

    line("// Save point data for $zoneId")
    for (savePoint in savePoints) {
        val saveName = "${zoneId}_${savePoint.id}"
        val healsParty = savePoint.properties["healsParty"]?.toBoolean() ?: true

        line("// Save point: ${savePoint.id}")
        line("#define ${saveName.uppercase()}_HEALS_PARTY ${if (healsParty) 1 else 0}u")
    }
    line()
}

/** Generate interaction callback functions for objects. */
private fun CodeGenerator.generateObjectCallbacks(zoneId: String, objects: List<GenericMapObject>) {
    for (obj in objects) {
        // onInteract callback (used for chests opening, NPC interaction, sconce lighting, etc.)
        if (obj.onInteractStatements.isNotEmpty()) {
            val callbackName =
                when (obj.objectType.id) {
                    "chest" -> "on_open"
                    "door" -> "on_open"
                    "lever" -> "on_pull"
                    "sconce" -> "on_lit"
                    "save_point" -> "on_save"
                    else -> "on_interact"
                }
            line("static void ${zoneId}_${obj.id}_$callbackName(void) {")
            indent++
            for (stmt in obj.onInteractStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        // onStateChange callback (used for door closing, etc.)
        if (obj.onStateChangeStatements.isNotEmpty()) {
            val callbackName =
                when (obj.objectType.id) {
                    "door" -> "on_close"
                    "sconce" -> "on_state_change"
                    else -> "on_state_change"
                }
            line("static void ${zoneId}_${obj.id}_$callbackName(void) {")
            indent++
            for (stmt in obj.onStateChangeStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        // onStep callback
        if (obj.onStepStatements.isNotEmpty()) {
            line("static void ${zoneId}_${obj.id}_on_step(void) {")
            indent++
            for (stmt in obj.onStepStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }
    }
}

/** Generate generic object interaction handlers. */
private fun CodeGenerator.generateObjectInteractionHandlers() {
    val zonesWithObjects = game.zones.filter { it.objects.isNotEmpty() }

    line("// =============================================================================")
    line("// OBJECT INTERACTION HANDLERS")
    line("// =============================================================================")
    line()

    // Check if object is at position
    line("// Check if there's an object at the given position")
    line("// Returns object index (local to zone) or $SENTINEL_NO_OBJECT if none")
    line("static UINT8 object_at_position(UINT8 floor_id, UINT8 x, UINT8 y) {")
    indent++

    if (zonesWithObjects.isNotEmpty()) {
        line("const UINT8* positions;")
        line("UINT8 count;")
        line("UINT8 i;")
        line()

        line("switch (floor_id) {")
        indent++
        for (zone in zonesWithObjects) {
            line("case ${zone.zoneIndex}u:")
            indent++
            line("positions = ${zone.id}_object_positions;")
            line("count = ${zone.id.uppercase()}_OBJECT_COUNT;")
            line("break;")
            indent--
        }
        line("default: return ${SENTINEL_NO_OBJECT}u;")
        indent--
        line("}")
        line()

        line("for (i = 0u; i < count; i++) {")
        indent++
        line("if (positions[i * 2u] == x && positions[i * 2u + 1u] == y) {")
        indent++
        line("return i;")
        indent--
        line("}")
        indent--
        line("}")
    }

    line("return ${SENTINEL_NO_OBJECT}u;")
    indent--
    line("}")
    line()

    // Get object state (using flags for persistent objects)
    line("// Get object state (0 = initial state, 1 = activated)")
    line("static UINT8 object_get_state(UINT8 floor_id, UINT8 obj_idx) {")
    indent++

    if (zonesWithObjects.isNotEmpty()) {
        line("const UINT8* flags;")
        line("UINT8 flag_idx;")
        line()

        line("switch (floor_id) {")
        indent++
        for (zone in zonesWithObjects) {
            line("case ${zone.zoneIndex}u: flags = ${zone.id}_object_flags; break;")
        }
        line("default: return 0u;")
        indent--
        line("}")
        line()

        line("flag_idx = flags[obj_idx];")
        line("if (flag_idx == ${SENTINEL_NO_FLAG}u) return 0u; // No persistence")
        line("return FLAG_GET(flag_idx >> 3u, 1u << (flag_idx & 7u));")
    } else {
        line("return 0u;")
    }

    indent--
    line("}")
    line()

    // Set object state
    line("// Set object state")
    line("static void object_set_state(UINT8 floor_id, UINT8 obj_idx, UINT8 state) {")
    indent++

    if (zonesWithObjects.isNotEmpty()) {
        line("const UINT8* flags;")
        line("UINT8 flag_idx;")
        line()

        line("switch (floor_id) {")
        indent++
        for (zone in zonesWithObjects) {
            line("case ${zone.zoneIndex}u: flags = ${zone.id}_object_flags; break;")
        }
        line("default: return;")
        indent--
        line("}")
        line()

        line("flag_idx = flags[obj_idx];")
        line("if (flag_idx == ${SENTINEL_NO_FLAG}u) return; // No persistence")
        line()
        line("if (state) {")
        indent++
        line("FLAG_SET(flag_idx >> 3u, 1u << (flag_idx & 7u));")
        indent--
        line("} else {")
        indent++
        line("FLAG_CLEAR(flag_idx >> 3u, 1u << (flag_idx & 7u));")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()

    // Generic interact function
    line("// Interact with object at position")
    line("static void object_interact(UINT8 floor_id, UINT8 obj_idx) {")
    indent++

    if (zonesWithObjects.isNotEmpty()) {
        line("const UINT8* types;")
        line("UINT8 obj_type;")
        line()

        line("// Get object type")
        line("switch (floor_id) {")
        indent++
        for (zone in zonesWithObjects) {
            line("case ${zone.zoneIndex}u: types = ${zone.id}_object_types; break;")
        }
        line("default: return;")
        indent--
        line("}")
        line()

        line("obj_type = types[obj_idx];")
        line()

        line("// Dispatch to type-specific handler")
        line("switch (obj_type) {")
        indent++

        line("case OBJ_TYPE_CHEST:")
        indent++
        line("// Already opened check")
        line("if (object_get_state(floor_id, obj_idx)) return;")
        line("// Mark as opened")
        line("object_set_state(floor_id, obj_idx, 1u);")
        line("// Call zone-specific chest callback")
        generateObjectTypeDispatch(zonesWithObjects, "chest", "on_open")
        line("break;")
        indent--

        line("case OBJ_TYPE_DOOR:")
        indent++
        line("// Toggle door state")
        line("if (object_get_state(floor_id, obj_idx)) {")
        indent++
        line("object_set_state(floor_id, obj_idx, 0u);")
        generateObjectTypeDispatch(zonesWithObjects, "door", "on_close")
        indent--
        line("} else {")
        indent++
        line("object_set_state(floor_id, obj_idx, 1u);")
        generateObjectTypeDispatch(zonesWithObjects, "door", "on_open")
        indent--
        line("}")
        line("break;")
        indent--

        line("case OBJ_TYPE_LEVER:")
        indent++
        line("// One-shot check (would need runtime tracking)")
        line("object_set_state(floor_id, obj_idx, 1u);")
        generateObjectTypeDispatch(zonesWithObjects, "lever", "on_pull")
        line("break;")
        indent--

        line("case OBJ_TYPE_SIGN:")
        indent++
        line("// Show sign text - dispatch to dialog system")
        line("// Sign text display would be handled here")
        line("break;")
        indent--

        line("case OBJ_TYPE_NPC:")
        indent++
        line("// Talk to NPC")
        generateObjectTypeDispatch(zonesWithObjects, "npc", "on_interact")
        line("break;")
        indent--

        line("case OBJ_TYPE_SAVE_POINT:")
        indent++
        line("// Save game")
        generateObjectTypeDispatch(zonesWithObjects, "save_point", "on_save")
        line("break;")
        indent--

        line("case OBJ_TYPE_SCONCE:")
        indent++
        line("// Toggle sconce")
        line("if (!object_get_state(floor_id, obj_idx)) {")
        indent++
        line("object_set_state(floor_id, obj_idx, 1u);")
        generateObjectTypeDispatch(zonesWithObjects, "sconce", "on_lit")
        indent--
        line("}")
        line("break;")
        indent--

        line("default: break;")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate dispatch switch for a specific object type callback. */
private fun CodeGenerator.generateObjectTypeDispatch(
    zones: List<io.github.gbkt.core.world.GenericZone>,
    objectTypeId: String,
    callbackName: String,
) {
    // Check if any zone has objects of this type with callbacks
    val zonesWithCallbacks =
        zones.filter { zone ->
            zone.objects.any { obj ->
                obj.objectType.id == objectTypeId &&
                    when (callbackName) {
                        "on_open",
                        "on_pull",
                        "on_lit",
                        "on_save",
                        "on_interact" -> obj.onInteractStatements.isNotEmpty()
                        "on_close",
                        "on_state_change" -> obj.onStateChangeStatements.isNotEmpty()
                        "on_step" -> obj.onStepStatements.isNotEmpty()
                        else -> false
                    }
            }
        }

    if (zonesWithCallbacks.isEmpty()) {
        line("// No $callbackName callbacks defined for $objectTypeId")
        return
    }

    line("switch (floor_id) {")
    indent++

    for (zone in zonesWithCallbacks) {
        line("case ${zone.zoneIndex}u:")
        indent++
        line("switch (obj_idx) {")
        indent++

        for ((objIndex, obj) in zone.objects.withIndex()) {
            if (obj.objectType.id == objectTypeId) {
                val hasCallback =
                    when (callbackName) {
                        "on_open",
                        "on_pull",
                        "on_lit",
                        "on_save",
                        "on_interact" -> obj.onInteractStatements.isNotEmpty()
                        "on_close",
                        "on_state_change" -> obj.onStateChangeStatements.isNotEmpty()
                        "on_step" -> obj.onStepStatements.isNotEmpty()
                        else -> false
                    }
                if (hasCallback) {
                    line("case ${objIndex}u: ${zone.id}_${obj.id}_$callbackName(); break;")
                }
            }
        }

        line("default: break;")
        indent--
        line("}")
        line("break;")
        indent--
    }

    line("default: break;")
    indent--
    line("}")
}
