/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.world

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.world.ConnectionType
import io.github.gbkt.core.world.GenericZone
import io.github.gbkt.core.world.ScrollDirection
import io.github.gbkt.core.world.Zone
import io.github.gbkt.core.world.ZoneType

// =============================================================================
// GENERIC ZONE CODE GENERATION
// =============================================================================

/** Helper to generate a list of statements. */
private fun GBDKCodeGenerator.generateStatementList(statements: List<IRStatement>) {
    for (stmt in statements) {
        generateStatement(stmt)
    }
}

/**
 * Generate zone system code.
 *
 * Creates:
 * - Zone type and connection type constants
 * - Zone configuration tables
 * - Zone map data
 * - Connection lookup tables
 * - Zone navigation functions
 * - Enter/exit callbacks
 */
internal fun GBDKCodeGenerator.generateZoneSystem() {
    val zones = game.zones
    if (zones.isEmpty()) return

    line("// =============================================================================")
    line("// ZONE SYSTEM")
    line("// =============================================================================")
    line()

    // Generate type constants
    generateZoneTypeConstants()
    generateConnectionTypeConstants()
    generateScrollDirectionConstants()

    // Generate zone index constants
    generateZoneIndexConstants(zones)

    // Generate zone configuration tables
    generateZoneConfigTables(zones)

    // Generate zone maps data
    generateZoneMapData(zones)

    // Generate connection data
    generateConnectionData(zones)

    // Generate zone state variables
    generateZoneStateVariables()

    // Generate zone navigation functions
    generateZoneNavigationFunctions(zones)

    // Generate floor exit checking function
    generateFloorCheckExits(zones)

    // Generate zone callbacks
    generateZoneCallbacks(zones)
}

/** Generate zone type constants. */
private fun GBDKCodeGenerator.generateZoneTypeConstants() {
    line("// Zone type constants")
    for ((index, type) in ZoneType.entries.withIndex()) {
        line("#define ZONE_TYPE_${type.name} ${index}u")
    }
    line()
}

/** Generate connection type constants. */
private fun GBDKCodeGenerator.generateConnectionTypeConstants() {
    line("// Connection type constants")
    for ((index, type) in ConnectionType.entries.withIndex()) {
        line("#define CONN_TYPE_${type.name} ${index}u")
    }
    line()
}

/** Generate scroll direction constants. */
private fun GBDKCodeGenerator.generateScrollDirectionConstants() {
    line("// Scroll direction constants")
    for ((index, dir) in ScrollDirection.entries.withIndex()) {
        line("#define SCROLL_${dir.name} ${index}u")
    }
    line()
}

/** Generate zone index constants. */
private fun GBDKCodeGenerator.generateZoneIndexConstants(zones: List<Zone>) {
    line("// Zone index constants")
    for ((index, zone) in zones.withIndex()) {
        line("#define ZONE_${zone.id.uppercase()} ${index}u")
    }
    line("#define ZONE_COUNT ${zones.size}u")
    line()
}

/** Generate zone configuration tables. */
private fun GBDKCodeGenerator.generateZoneConfigTables(zones: List<Zone>) {
    line("// =============================================================================")
    line("// ZONE CONFIGURATION TABLES")
    line("// =============================================================================")
    line()

    // Zone types
    line("static const UINT8 _zone_type[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "ZONE_TYPE_${it.zoneType.name}" })
    indent--
    line("};")
    line()

    // Zone names
    line("static const char* const _zone_names[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "\"${it.displayName}\"" })
    indent--
    line("};")
    line()

    // Default positions
    line("static const UINT8 _zone_default_x[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "${it.defaultX}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _zone_default_y[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "${it.defaultY}u" })
    indent--
    line("};")
    line()

    // Map counts per zone
    line("static const UINT8 _zone_map_count[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "${it.maps.size}u" })
    indent--
    line("};")
    line()

    // Connection counts per zone
    line("static const UINT8 _zone_conn_count[ZONE_COUNT] = {")
    indent++
    line(zones.joinToString(", ") { "${it.connections.size}u" })
    indent--
    line("};")
    line()

    // For generic zones, add scroll direction
    val genericZones = zones.filterIsInstance<GenericZone>()
    if (genericZones.isNotEmpty()) {
        line("static const UINT8 _zone_scroll_dir[ZONE_COUNT] = {")
        indent++
        line(
            zones.joinToString(", ") { zone ->
                val dir = (zone as? GenericZone)?.scrollDirection ?: ScrollDirection.NONE
                "SCROLL_${dir.name}"
            }
        )
        indent--
        line("};")
        line()
    }
}

/** Generate zone map data. */
private fun GBDKCodeGenerator.generateZoneMapData(zones: List<Zone>) {
    line("// =============================================================================")
    line("// ZONE MAP DATA")
    line("// =============================================================================")
    line()

    // Total maps across all zones
    val totalMaps = zones.sumOf { it.maps.size }
    if (totalMaps == 0) {
        line("// No zone maps defined")
        line()
        return
    }

    // Map offset table (where each zone's maps start)
    var mapOffset = 0
    line("static const UINT8 _zone_map_offset[ZONE_COUNT] = {")
    indent++
    val offsets = mutableListOf<Int>()
    for (zone in zones) {
        offsets.add(mapOffset)
        mapOffset += zone.maps.size
    }
    line(offsets.joinToString(", ") { "${it}u" })
    indent--
    line("};")
    line()

    // Flatten all maps into a single array
    val allMaps = zones.flatMap { zone -> zone.maps.values.map { zone.id to it } }

    // Map widths
    line("static const UINT8 _map_width[${allMaps.size}] = {")
    indent++
    line(allMaps.joinToString(", ") { "${it.second.width}u" })
    indent--
    line("};")
    line()

    // Map heights
    line("static const UINT8 _map_height[${allMaps.size}] = {")
    indent++
    line(allMaps.joinToString(", ") { "${it.second.height}u" })
    indent--
    line("};")
    line()

    // Map names
    line("static const char* const _map_names[${allMaps.size}] = {")
    indent++
    line(allMaps.joinToString(", ") { "\"${it.second.name}\"" })
    indent--
    line("};")
    line()
}

/** Generate connection data. */
private fun GBDKCodeGenerator.generateConnectionData(zones: List<Zone>) {
    line("// =============================================================================")
    line("// ZONE CONNECTION DATA")
    line("// =============================================================================")
    line()

    val allConnections = zones.flatMap { it.connections }
    if (allConnections.isEmpty()) {
        line("// No zone connections defined")
        line("#define TOTAL_CONNECTIONS 0u")
        line()
        return
    }

    line("#define TOTAL_CONNECTIONS ${allConnections.size}u")
    line()

    // Connection offset table (where each zone's connections start)
    var connOffset = 0
    line("static const UINT8 _zone_conn_offset[ZONE_COUNT] = {")
    indent++
    val offsets = mutableListOf<Int>()
    for (zone in zones) {
        offsets.add(connOffset)
        connOffset += zone.connections.size
    }
    line(offsets.joinToString(", ") { "${it}u" })
    indent--
    line("};")
    line()

    // Connection types
    line("static const UINT8 _conn_type[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { "CONN_TYPE_${it.type.name}" })
    indent--
    line("};")
    line()

    // Connection from positions
    line("static const UINT8 _conn_from_x[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { "${it.fromX}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _conn_from_y[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { "${it.fromY}u" })
    indent--
    line("};")
    line()

    // Connection destination zones (as indices)
    val zoneIdToIndex = zones.mapIndexed { idx, zone -> zone.id to idx }.toMap()
    line("static const UINT8 _conn_to_zone[TOTAL_CONNECTIONS] = {")
    indent++
    line(
        allConnections.joinToString(", ") {
            val idx = zoneIdToIndex[it.toZone] ?: 0
            "${idx}u"
        }
    )
    indent--
    line("};")
    line()

    // Connection destination positions
    line("static const UINT8 _conn_to_x[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { "${it.toX}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _conn_to_y[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { "${it.toY}u" })
    indent--
    line("};")
    line()

    // Connection active flags
    line("static const UINT8 _conn_active_default[TOTAL_CONNECTIONS] = {")
    indent++
    line(allConnections.joinToString(", ") { if (it.startActive) "1u" else "0u" })
    indent--
    line("};")
    line()
}

/** Generate zone state variables. */
private fun GBDKCodeGenerator.generateZoneStateVariables() {
    line("// =============================================================================")
    line("// ZONE STATE VARIABLES")
    line("// =============================================================================")
    line()

    line("// Current zone state")
    line("static UINT8 _current_zone = 0u;")
    line("static UINT8 _current_map = 0u;")
    line("static UINT8 _zone_pos_x = 0u;")
    line("static UINT8 _zone_pos_y = 0u;")
    line()

    line("// Previous zone (for transition callbacks)")
    line("static UINT8 _prev_zone = 0u;")
    line()

    line("// Connection active flags (runtime state)")
    line("static UINT8 _conn_active[TOTAL_CONNECTIONS > 0 ? TOTAL_CONNECTIONS : 1];")
    line()

    line("// Initialize zone state")
    line("static void _init_zone_state(UINT8 start_zone) {")
    indent++
    line("UINT8 i;")
    line("_current_zone = (start_zone < ZONE_COUNT) ? start_zone : 0u;")
    line("_current_map = 0u;")
    line("_zone_pos_x = _zone_default_x[_current_zone];")
    line("_zone_pos_y = _zone_default_y[_current_zone];")
    line("_prev_zone = _current_zone;")
    line()
    line("// Initialize connection active flags")
    line("for (i = 0u; i < TOTAL_CONNECTIONS; i++) {")
    indent++
    line("_conn_active[i] = _conn_active_default[i];")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Forward declarations for zone callbacks (needed by navigation functions)
    line("// Forward declarations for zone callbacks")
    line("static void _zone_on_enter(UINT8 zone_id);")
    line("static void _zone_on_exit(UINT8 zone_id);")
    line()
}

/** Generate zone navigation functions. */
private fun GBDKCodeGenerator.generateZoneNavigationFunctions(zones: List<Zone>) {
    line("// =============================================================================")
    line("// ZONE NAVIGATION FUNCTIONS")
    line("// =============================================================================")
    line()

    // Get current zone info
    line("// Get current zone ID")
    line("static UINT8 _get_current_zone(void) {")
    indent++
    line("return _current_zone;")
    indent--
    line("}")
    line()

    line("// Get current zone name")
    line("static const char* _get_zone_name(UINT8 zone_id) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return \"???\";")
    line("return _zone_names[zone_id];")
    indent--
    line("}")
    line()

    line("// Get zone type")
    line("static UINT8 _get_zone_type(UINT8 zone_id) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return 0u;")
    line("return _zone_type[zone_id];")
    indent--
    line("}")
    line()

    // Set position within zone
    line("// Set position within current zone")
    line("static void _set_zone_position(UINT8 x, UINT8 y) {")
    indent++
    line("_zone_pos_x = x;")
    line("_zone_pos_y = y;")
    indent--
    line("}")
    line()

    // Get position
    line("static UINT8 _get_zone_x(void) { return _zone_pos_x; }")
    line("static UINT8 _get_zone_y(void) { return _zone_pos_y; }")
    line()

    // Change zone
    line("// Change to a different zone")
    line("static void _change_zone(UINT8 zone_id, UINT8 x, UINT8 y) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return;")
    line()
    line("_prev_zone = _current_zone;")
    line("_current_zone = zone_id;")
    line("_current_map = 0u; // Default to first map")
    line("_zone_pos_x = x;")
    line("_zone_pos_y = y;")
    line()
    line("// Call exit callback for previous zone")
    line("_zone_on_exit(_prev_zone);")
    line()
    line("// Call enter callback for new zone")
    line("_zone_on_enter(_current_zone);")
    indent--
    line("}")
    line()

    // Change zone to default position
    line("// Change to zone with default position")
    line("static void _change_zone_default(UINT8 zone_id) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return;")
    line("_change_zone(zone_id, _zone_default_x[zone_id], _zone_default_y[zone_id]);")
    indent--
    line("}")
    line()

    // Check connection at position
    line("// Check for connection at current position")
    line("static INT8 _check_connection(UINT8 zone_id, UINT8 x, UINT8 y) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return -1;")
    line("UINT8 offset = _zone_conn_offset[zone_id];")
    line("UINT8 count = _zone_conn_count[zone_id];")
    line("UINT8 i;")
    line()
    line("for (i = 0u; i < count; i++) {")
    indent++
    line("UINT8 conn_idx = offset + i;")
    line("if (!_conn_active[conn_idx]) continue;")
    line("if (_conn_from_x[conn_idx] == x && _conn_from_y[conn_idx] == y) {")
    indent++
    line("return (INT8)conn_idx;")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("return -1; // No connection found")
    indent--
    line("}")
    line()

    // Use connection
    line("// Use a connection to travel to another zone")
    line("static UINT8 _use_connection(UINT8 conn_idx) {")
    indent++
    line("if (conn_idx >= TOTAL_CONNECTIONS) return 0u;")
    line("if (!_conn_active[conn_idx]) return 0u;")
    line()
    line("UINT8 to_zone = _conn_to_zone[conn_idx];")
    line("UINT8 to_x = _conn_to_x[conn_idx];")
    line("UINT8 to_y = _conn_to_y[conn_idx];")
    line()
    line("_change_zone(to_zone, to_x, to_y);")
    line("return 1u; // Success")
    indent--
    line("}")
    line()

    // Activate/deactivate connections
    line("// Activate a connection")
    line("static void _activate_connection(UINT8 conn_idx) {")
    indent++
    line("if (conn_idx < TOTAL_CONNECTIONS) _conn_active[conn_idx] = 1u;")
    indent--
    line("}")
    line()

    line("// Deactivate a connection")
    line("static void _deactivate_connection(UINT8 conn_idx) {")
    indent++
    line("if (conn_idx < TOTAL_CONNECTIONS) _conn_active[conn_idx] = 0u;")
    indent--
    line("}")
    line()

    // Map functions
    line("// Get map info")
    line("static UINT8 _get_map_width(UINT8 zone_id, UINT8 map_idx) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return 0u;")
    line("if (map_idx >= _zone_map_count[zone_id]) return 0u;")
    line("return _map_width[_zone_map_offset[zone_id] + map_idx];")
    indent--
    line("}")
    line()

    line("static UINT8 _get_map_height(UINT8 zone_id, UINT8 map_idx) {")
    indent++
    line("if (zone_id >= ZONE_COUNT) return 0u;")
    line("if (map_idx >= _zone_map_count[zone_id]) return 0u;")
    line("return _map_height[_zone_map_offset[zone_id] + map_idx];")
    indent--
    line("}")
    line()
}

/** Generate floor exit checking function. */
private fun GBDKCodeGenerator.generateFloorCheckExits(zones: List<Zone>) {
    line("// =============================================================================")
    line("// FLOOR EXIT CHECKING")
    line("// =============================================================================")
    line()

    line("// Check for exit at current player position and handle zone transition")
    line("static void floor_check_exits(void) {")
    indent++
    line("INT8 conn_idx = _check_connection(_current_zone, _zone_pos_x, _zone_pos_y);")
    line("if (conn_idx >= 0) {")
    indent++
    line("_use_connection((UINT8)conn_idx);")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate zone callbacks. */
private fun GBDKCodeGenerator.generateZoneCallbacks(zones: List<Zone>) {
    line("// =============================================================================")
    line("// ZONE CALLBACKS")
    line("// =============================================================================")
    line()

    // Generate individual zone callbacks
    for (zone in zones) {
        if (zone.onEnterStatements.isNotEmpty()) {
            line("// onEnter callback for zone: ${zone.id}")
            line("static void _${zone.id}_on_enter(void) {")
            indent++
            generateStatementList(zone.onEnterStatements)
            indent--
            line("}")
            line()
        }

        if (zone.onExitStatements.isNotEmpty()) {
            line("// onExit callback for zone: ${zone.id}")
            line("static void _${zone.id}_on_exit(void) {")
            indent++
            generateStatementList(zone.onExitStatements)
            indent--
            line("}")
            line()
        }
    }

    // Generate dispatcher for onEnter
    line("// Dispatch zone enter callback")
    line("static void _zone_on_enter(UINT8 zone_id) {")
    indent++
    line("switch (zone_id) {")
    indent++
    for (zone in zones) {
        if (zone.onEnterStatements.isNotEmpty()) {
            line("case ZONE_${zone.id.uppercase()}: _${zone.id}_on_enter(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate dispatcher for onExit
    line("// Dispatch zone exit callback")
    line("static void _zone_on_exit(UINT8 zone_id) {")
    indent++
    line("switch (zone_id) {")
    indent++
    for (zone in zones) {
        if (zone.onExitStatements.isNotEmpty()) {
            line("case ZONE_${zone.id.uppercase()}: _${zone.id}_on_exit(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}
