/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.world

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.world.EncounterTrigger
import io.github.gbkt.core.world.RegionBasedTrigger
import io.github.gbkt.core.world.StepBasedTrigger
import io.github.gbkt.core.world.TimeBasedTrigger
import io.github.gbkt.core.world.TriggerType
import io.github.gbkt.core.world.WaveBasedTrigger

// =============================================================================
// PLUGGABLE ENCOUNTER TRIGGER CODE GENERATION
// =============================================================================

/** Helper to generate a list of statements. */
private fun CodeGenerator.generateStatementList(statements: List<IRStatement>) {
    for (stmt in statements) {
        generateStatement(stmt)
    }
}

/**
 * Generate encounter trigger system code.
 *
 * Creates:
 * - Trigger type constants
 * - Trigger configuration tables
 * - Trigger state variables
 * - Check functions for each trigger type
 * - Trigger dispatch function
 */
internal fun CodeGenerator.generateEncounterTriggerSystem() {
    val triggers = game.encounterTriggers
    if (triggers.isEmpty()) return

    line("// =============================================================================")
    line("// PLUGGABLE ENCOUNTER TRIGGER SYSTEM")
    line("// =============================================================================")
    line()

    // Generate trigger type constants
    generateTriggerTypeConstants()

    // Generate trigger index constants
    generateTriggerIndexConstants(triggers)

    // Generate trigger configuration tables
    generateTriggerConfigTables(triggers)

    // Generate type-specific configuration
    generateStepTriggerConfig(triggers.filterIsInstance<StepBasedTrigger>())
    generateTimeTriggerConfig(triggers.filterIsInstance<TimeBasedTrigger>())
    generateRegionTriggerConfig(triggers.filterIsInstance<RegionBasedTrigger>())
    generateWaveTriggerConfig(triggers.filterIsInstance<WaveBasedTrigger>())

    // Generate trigger state variables
    generateTriggerStateVariables(triggers)

    // Generate check functions
    generateStepTriggerCheck(triggers.filterIsInstance<StepBasedTrigger>())
    generateTimeTriggerCheck(triggers.filterIsInstance<TimeBasedTrigger>())
    generateRegionTriggerCheck(triggers.filterIsInstance<RegionBasedTrigger>())
    generateWaveTriggerCheck(triggers.filterIsInstance<WaveBasedTrigger>())

    // Generate dispatch function
    generateTriggerDispatchFunction(triggers)

    // Generate trigger callbacks
    generateTriggerCallbacks(triggers)

    // Generate helper functions
    generateTriggerHelperFunctions(triggers)
}

/** Generate trigger type constants. */
private fun CodeGenerator.generateTriggerTypeConstants() {
    line("// Trigger type constants")
    for ((index, type) in TriggerType.entries.withIndex()) {
        line("#define TRIGGER_TYPE_${type.name} ${index}u")
    }
    line()
}

/** Generate trigger index constants. */
private fun CodeGenerator.generateTriggerIndexConstants(triggers: List<EncounterTrigger>) {
    line("// Trigger index constants")
    for ((index, trigger) in triggers.withIndex()) {
        line("#define TRIGGER_${trigger.id.uppercase()} ${index}u")
    }
    line("#define TRIGGER_COUNT ${triggers.size}u")
    line()
}

/** Generate trigger configuration tables. */
private fun CodeGenerator.generateTriggerConfigTables(triggers: List<EncounterTrigger>) {
    line("// =============================================================================")
    line("// TRIGGER CONFIGURATION TABLES")
    line("// =============================================================================")
    line()

    // Trigger types
    line("static const UINT8 _trigger_type[TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "TRIGGER_TYPE_${it.triggerType.name}" })
    indent--
    line("};")
    line()

    // Active flags (whether each trigger is currently active)
    line("static UINT8 _trigger_active[TRIGGER_COUNT];")
    line()
}

/** Generate step trigger configuration. */
private fun CodeGenerator.generateStepTriggerConfig(triggers: List<StepBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// Step-based trigger configuration")
    line("#define STEP_TRIGGER_COUNT ${triggers.size}u")
    line()

    // Safe steps
    line("static const UINT8 _step_safe_steps[STEP_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.safeSteps}u" })
    indent--
    line("};")
    line()

    // Initial chance
    line("static const UINT8 _step_initial_chance[STEP_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.initialChance}u" })
    indent--
    line("};")
    line()

    // Increment per step
    line("static const UINT8 _step_increment[STEP_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.incrementPerStep}u" })
    indent--
    line("};")
    line()

    // Max chance
    line("static const UINT8 _step_max_chance[STEP_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.maxChance}u" })
    indent--
    line("};")
    line()

    // Reset on encounter
    line("static const UINT8 _step_reset_on_enc[STEP_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { if (it.resetOnEncounter) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Trigger index mapping (trigger index -> step config index)
    line("// Map trigger index to step config index (-1 if not step-based)")
    val allTriggers = game.encounterTriggers
    line("static const INT8 _trigger_to_step_idx[TRIGGER_COUNT] = {")
    indent++
    var stepIdx = 0
    line(
        allTriggers.joinToString(", ") { trigger ->
            if (trigger is StepBasedTrigger) {
                "${stepIdx++}"
            } else {
                "-1"
            }
        }
    )
    indent--
    line("};")
    line()
}

/** Generate time trigger configuration. */
private fun CodeGenerator.generateTimeTriggerConfig(triggers: List<TimeBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// Time-based trigger configuration")
    line("#define TIME_TRIGGER_COUNT ${triggers.size}u")
    line()

    // Safe frames
    line("static const UINT16 _time_safe_frames[TIME_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.safeFrames}u" })
    indent--
    line("};")
    line()

    // Check interval
    line("static const UINT8 _time_check_interval[TIME_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.checkInterval}u" })
    indent--
    line("};")
    line()

    // Base chance
    line("static const UINT8 _time_base_chance[TIME_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.baseChance}u" })
    indent--
    line("};")
    line()

    // Idle multiplier
    line("static const UINT8 _time_idle_mult[TIME_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.idleMultiplier}u" })
    indent--
    line("};")
    line()

    // Moving multiplier
    line("static const UINT8 _time_moving_mult[TIME_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.movingMultiplier}u" })
    indent--
    line("};")
    line()

    // Trigger index mapping
    val allTriggers = game.encounterTriggers
    line("static const INT8 _trigger_to_time_idx[TRIGGER_COUNT] = {")
    indent++
    var timeIdx = 0
    line(
        allTriggers.joinToString(", ") { trigger ->
            if (trigger is TimeBasedTrigger) {
                "${timeIdx++}"
            } else {
                "-1"
            }
        }
    )
    indent--
    line("};")
    line()
}

/** Generate region trigger configuration. */
private fun CodeGenerator.generateRegionTriggerConfig(triggers: List<RegionBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// Region-based trigger configuration")
    line("#define REGION_TRIGGER_COUNT ${triggers.size}u")
    line()

    // Count total danger zones
    val totalZones = triggers.sumOf { it.dangerZones.size }
    line("#define TOTAL_DANGER_ZONES ${totalZones}u")
    line()

    // Danger zone offset per trigger
    var zoneOffset = 0
    line("static const UINT8 _region_zone_offset[REGION_TRIGGER_COUNT] = {")
    indent++
    val offsets =
        triggers.map { trigger ->
            val offset = zoneOffset
            zoneOffset += trigger.dangerZones.size
            "${offset}u"
        }
    line(offsets.joinToString(", "))
    indent--
    line("};")
    line()

    // Zone count per trigger
    line("static const UINT8 _region_zone_count[REGION_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.dangerZones.size}u" })
    indent--
    line("};")
    line()

    // Flatten all danger zones
    val allZones = triggers.flatMap { it.dangerZones }

    // Zone bounds
    line("static const UINT8 _zone_x1[TOTAL_DANGER_ZONES] = {")
    indent++
    line(allZones.joinToString(", ") { "${it.x1}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _zone_y1[TOTAL_DANGER_ZONES] = {")
    indent++
    line(allZones.joinToString(", ") { "${it.y1}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _zone_x2[TOTAL_DANGER_ZONES] = {")
    indent++
    line(allZones.joinToString(", ") { "${it.x2}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _zone_y2[TOTAL_DANGER_ZONES] = {")
    indent++
    line(allZones.joinToString(", ") { "${it.y2}u" })
    indent--
    line("};")
    line()

    // Zone encounter chances
    line("static const UINT8 _zone_chance[TOTAL_DANGER_ZONES] = {")
    indent++
    line(allZones.joinToString(", ") { "${it.chance}u" })
    indent--
    line("};")
    line()

    // Trigger index mapping
    val allTriggers = game.encounterTriggers
    line("static const INT8 _trigger_to_region_idx[TRIGGER_COUNT] = {")
    indent++
    var regionIdx = 0
    line(
        allTriggers.joinToString(", ") { trigger ->
            if (trigger is RegionBasedTrigger) {
                "${regionIdx++}"
            } else {
                "-1"
            }
        }
    )
    indent--
    line("};")
    line()
}

/** Generate wave trigger configuration. */
private fun CodeGenerator.generateWaveTriggerConfig(triggers: List<WaveBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// Wave-based trigger configuration")
    line("#define WAVE_TRIGGER_COUNT ${triggers.size}u")
    line()

    // Total waves
    val totalWaves = triggers.sumOf { it.waves.size }
    line("#define TOTAL_WAVES ${totalWaves}u")
    line()

    // Wave offset per trigger
    var waveOffset = 0
    line("static const UINT8 _wave_trigger_offset[WAVE_TRIGGER_COUNT] = {")
    indent++
    val offsets =
        triggers.map { trigger ->
            val offset = waveOffset
            waveOffset += trigger.waves.size
            "${offset}u"
        }
    line(offsets.joinToString(", "))
    indent--
    line("};")
    line()

    // Wave count per trigger
    line("static const UINT8 _wave_trigger_count[WAVE_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.waves.size}u" })
    indent--
    line("};")
    line()

    // Loop settings
    line("static const UINT8 _wave_loop[WAVE_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { if (it.loopWaves) "1u" else "0u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _wave_loop_scaling[WAVE_TRIGGER_COUNT] = {")
    indent++
    line(triggers.joinToString(", ") { "${it.loopScaling}u" })
    indent--
    line("};")
    line()

    // Flatten all waves
    val allWaves = triggers.flatMap { it.waves }

    // Wave delays
    line("static const UINT16 _wave_delay[TOTAL_WAVES] = {")
    indent++
    line(allWaves.joinToString(", ") { "${it.delay}u" })
    indent--
    line("};")
    line()

    // Trigger index mapping
    val allTriggers = game.encounterTriggers
    line("static const INT8 _trigger_to_wave_idx[TRIGGER_COUNT] = {")
    indent++
    var waveIdx = 0
    line(
        allTriggers.joinToString(", ") { trigger ->
            if (trigger is WaveBasedTrigger) {
                "${waveIdx++}"
            } else {
                "-1"
            }
        }
    )
    indent--
    line("};")
    line()
}

/** Generate trigger state variables. */
private fun CodeGenerator.generateTriggerStateVariables(triggers: List<EncounterTrigger>) {
    line("// =============================================================================")
    line("// TRIGGER STATE VARIABLES")
    line("// =============================================================================")
    line()

    line("// Step-based trigger state")
    line("static UINT8 _step_count[TRIGGER_COUNT]; // Current step count")
    line("static UINT8 _step_chance[TRIGGER_COUNT]; // Current encounter chance")
    line()

    line("// Time-based trigger state")
    line("static UINT16 _time_elapsed[TRIGGER_COUNT]; // Frames since last check")
    line("static UINT16 _time_safe_remaining[TRIGGER_COUNT]; // Remaining safe frames")
    line()

    line("// Region-based trigger state")
    line("static UINT8 _region_check_timer[TRIGGER_COUNT]; // Frames until next check")
    line()

    line("// Wave-based trigger state")
    line("static UINT8 _wave_current[TRIGGER_COUNT]; // Current wave number")
    line("static UINT16 _wave_timer[TRIGGER_COUNT]; // Delay timer")
    line("static UINT8 _wave_loop_count[TRIGGER_COUNT]; // Number of loops completed")
    line()

    // Initialize function
    line("// Initialize trigger state")
    line("static void _init_triggers(void) {")
    indent++
    line("UINT8 i;")
    line("for (i = 0u; i < TRIGGER_COUNT; i++) {")
    indent++
    line("_trigger_active[i] = 1u; // All triggers start active")
    line("_step_count[i] = 0u;")
    line("_step_chance[i] = 0u;")
    line("_time_elapsed[i] = 0u;")
    line("_time_safe_remaining[i] = 0u;")
    line("_region_check_timer[i] = 0u;")
    line("_wave_current[i] = 0u;")
    line("_wave_timer[i] = 0u;")
    line("_wave_loop_count[i] = 0u;")
    indent--
    line("}")
    line()

    // Initialize type-specific state
    line("// Initialize step trigger chances")
    line("for (i = 0u; i < TRIGGER_COUNT; i++) {")
    indent++
    line("INT8 step_idx = _trigger_to_step_idx[i];")
    line("if (step_idx >= 0) {")
    indent++
    line("_step_chance[i] = _step_initial_chance[(UINT8)step_idx];")
    indent--
    line("}")
    indent--
    line("}")

    // Add initialization for time-based safe remaining if we have time triggers
    val hasTimeTriggers = triggers.any { it is TimeBasedTrigger }
    if (hasTimeTriggers) {
        line()
        line("// Initialize time trigger safe frames")
        line("for (i = 0u; i < TRIGGER_COUNT; i++) {")
        indent++
        line("INT8 time_idx = _trigger_to_time_idx[i];")
        line("if (time_idx >= 0) {")
        indent++
        line("_time_safe_remaining[i] = _time_safe_frames[(UINT8)time_idx];")
        indent--
        line("}")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate step trigger check function. */
private fun CodeGenerator.generateStepTriggerCheck(triggers: List<StepBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// =============================================================================")
    line("// STEP-BASED TRIGGER CHECK")
    line("// =============================================================================")
    line()

    line("// Check step-based trigger (call on each step)")
    line("static UINT8 _check_step_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("if (!_trigger_active[trigger_idx]) return 0u;")
    line()
    line("INT8 step_idx = _trigger_to_step_idx[trigger_idx];")
    line("if (step_idx < 0) return 0u; // Not a step trigger")
    line()
    line("// Increment step count")
    line("_step_count[trigger_idx]++;")
    line()
    line("// Check safe steps")
    line("if (_step_count[trigger_idx] <= _step_safe_steps[(UINT8)step_idx]) {")
    indent++
    line("return 0u;")
    indent--
    line("}")
    line()
    line("// Roll for encounter")
    line("UINT8 roll = _rand() % 100u;")
    line("if (roll < _step_chance[trigger_idx]) {")
    indent++
    line("// Encounter triggered!")
    line("if (_step_reset_on_enc[(UINT8)step_idx]) {")
    indent++
    line("_step_count[trigger_idx] = 0u;")
    line("_step_chance[trigger_idx] = _step_initial_chance[(UINT8)step_idx];")
    indent--
    line("}")
    line("return 1u;")
    indent--
    line("}")
    line()
    line("// Increase chance for next step")
    line("_step_chance[trigger_idx] += _step_increment[(UINT8)step_idx];")
    line("if (_step_chance[trigger_idx] > _step_max_chance[(UINT8)step_idx]) {")
    indent++
    line("_step_chance[trigger_idx] = _step_max_chance[(UINT8)step_idx];")
    indent--
    line("}")
    line()
    line("return 0u;")
    indent--
    line("}")
    line()
}

/** Generate time trigger check function. */
private fun CodeGenerator.generateTimeTriggerCheck(triggers: List<TimeBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// =============================================================================")
    line("// TIME-BASED TRIGGER CHECK")
    line("// =============================================================================")
    line()

    line("// Check time-based trigger (call every frame)")
    line("static UINT8 _check_time_trigger(UINT8 trigger_idx, UINT8 is_moving) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("if (!_trigger_active[trigger_idx]) return 0u;")
    line()
    line("INT8 time_idx = _trigger_to_time_idx[trigger_idx];")
    line("if (time_idx < 0) return 0u; // Not a time trigger")
    line()
    line("// Count down safe frames")
    line("if (_time_safe_remaining[trigger_idx] > 0u) {")
    indent++
    line("_time_safe_remaining[trigger_idx]--;")
    line("return 0u;")
    indent--
    line("}")
    line()
    line("// Increment elapsed time")
    line("_time_elapsed[trigger_idx]++;")
    line()
    line("// Check if it's time to roll")
    line("if (_time_elapsed[trigger_idx] < _time_check_interval[(UINT8)time_idx]) {")
    indent++
    line("return 0u;")
    indent--
    line("}")
    line()
    line("// Reset elapsed time")
    line("_time_elapsed[trigger_idx] = 0u;")
    line()
    line("// Calculate chance with multiplier")
    line("UINT8 base_chance = _time_base_chance[(UINT8)time_idx];")
    line(
        "UINT8 multiplier = is_moving ? _time_moving_mult[(UINT8)time_idx] : _time_idle_mult[(UINT8)time_idx];"
    )
    line("UINT16 effective_chance = (UINT16)base_chance * multiplier / 100u;")
    line("if (effective_chance > 100u) effective_chance = 100u;")
    line()
    line("// Roll for encounter")
    line("UINT8 roll = _rand() % 100u;")
    line("return (roll < effective_chance) ? 1u : 0u;")
    indent--
    line("}")
    line()
}

/** Generate region trigger check function. */
private fun CodeGenerator.generateRegionTriggerCheck(triggers: List<RegionBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// =============================================================================")
    line("// REGION-BASED TRIGGER CHECK")
    line("// =============================================================================")
    line()

    line("// Check region-based trigger")
    line("static UINT8 _check_region_trigger(UINT8 trigger_idx, UINT8 player_x, UINT8 player_y) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("if (!_trigger_active[trigger_idx]) return 0u;")
    line()
    line("INT8 region_idx = _trigger_to_region_idx[trigger_idx];")
    line("if (region_idx < 0) return 0u; // Not a region trigger")
    line()
    line("// Check timer")
    line("if (_region_check_timer[trigger_idx] > 0u) {")
    indent++
    line("_region_check_timer[trigger_idx]--;")
    line("return 0u;")
    indent--
    line("}")
    line()
    line("// Reset timer (default 60 frames)")
    line("_region_check_timer[trigger_idx] = 60u;")
    line()
    line("// Check all danger zones for this trigger")
    line("UINT8 offset = _region_zone_offset[(UINT8)region_idx];")
    line("UINT8 count = _region_zone_count[(UINT8)region_idx];")
    line("UINT8 i;")
    line()
    line("for (i = 0u; i < count; i++) {")
    indent++
    line("UINT8 zone_idx = offset + i;")
    line("// Check if player is in zone")
    line("if (player_x >= _zone_x1[zone_idx] && player_x <= _zone_x2[zone_idx] &&")
    line("    player_y >= _zone_y1[zone_idx] && player_y <= _zone_y2[zone_idx]) {")
    indent++
    line("// Roll for encounter")
    line("UINT8 roll = _rand() % 100u;")
    line("if (roll < _zone_chance[zone_idx]) {")
    indent++
    line("return 1u;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("return 0u;")
    indent--
    line("}")
    line()
}

/** Generate wave trigger check function. */
private fun CodeGenerator.generateWaveTriggerCheck(triggers: List<WaveBasedTrigger>) {
    if (triggers.isEmpty()) return

    line("// =============================================================================")
    line("// WAVE-BASED TRIGGER CHECK")
    line("// =============================================================================")
    line()

    line("// Advance wave trigger (call every frame)")
    line("static UINT8 _advance_wave_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("if (!_trigger_active[trigger_idx]) return 0u;")
    line()
    line("INT8 wave_idx = _trigger_to_wave_idx[trigger_idx];")
    line("if (wave_idx < 0) return 0u; // Not a wave trigger")
    line()
    line("UINT8 offset = _wave_trigger_offset[(UINT8)wave_idx];")
    line("UINT8 count = _wave_trigger_count[(UINT8)wave_idx];")
    line()
    line("// Check if all waves complete")
    line("if (_wave_current[trigger_idx] >= count) {")
    indent++
    line("if (_wave_loop[(UINT8)wave_idx]) {")
    indent++
    line("// Loop back to first wave")
    line("_wave_current[trigger_idx] = 0u;")
    line("_wave_loop_count[trigger_idx]++;")
    indent--
    line("} else {")
    indent++
    line("return 0u; // All waves complete")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("// Check wave timer")
    line("if (_wave_timer[trigger_idx] > 0u) {")
    indent++
    line("_wave_timer[trigger_idx]--;")
    line("return 0u;")
    indent--
    line("}")
    line()
    line("// Spawn next wave")
    line("UINT8 wave_num = _wave_current[trigger_idx];")
    line("_wave_timer[trigger_idx] = _wave_delay[offset + wave_num];")
    line("_wave_current[trigger_idx]++;")
    line()
    line("return 1u; // Wave should spawn now")
    indent--
    line("}")
    line()

    line("// Get current wave number")
    line("static UINT8 _get_current_wave(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("return _wave_current[trigger_idx];")
    indent--
    line("}")
    line()

    line("// Get loop count")
    line("static UINT8 _get_wave_loop_count(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("return _wave_loop_count[trigger_idx];")
    indent--
    line("}")
    line()
}

/** Generate trigger dispatch function. */
private fun CodeGenerator.generateTriggerDispatchFunction(triggers: List<EncounterTrigger>) {
    line("// =============================================================================")
    line("// TRIGGER DISPATCH")
    line("// =============================================================================")
    line()

    line("// Generic trigger check - routes to appropriate handler")
    line(
        "static UINT8 _check_trigger(UINT8 trigger_idx, UINT8 player_x, UINT8 player_y, UINT8 is_moving) {"
    )
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("if (!_trigger_active[trigger_idx]) return 0u;")
    line()
    line("switch (_trigger_type[trigger_idx]) {")
    indent++
    line("case TRIGGER_TYPE_STEP_BASED:")
    indent++
    line("return _check_step_trigger(trigger_idx);")
    indent--
    line("case TRIGGER_TYPE_TIME_BASED:")
    indent++
    line("return _check_time_trigger(trigger_idx, is_moving);")
    indent--
    line("case TRIGGER_TYPE_REGION_BASED:")
    indent++
    line("return _check_region_trigger(trigger_idx, player_x, player_y);")
    indent--
    line("case TRIGGER_TYPE_WAVE_BASED:")
    indent++
    line("return _advance_wave_trigger(trigger_idx);")
    indent--
    line("case TRIGGER_TYPE_EVENT_BASED:")
    indent++
    line("return 0u; // Event triggers are called manually")
    indent--
    line("default:")
    indent++
    line("return 0u;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate trigger callbacks. */
private fun CodeGenerator.generateTriggerCallbacks(triggers: List<EncounterTrigger>) {
    line("// =============================================================================")
    line("// TRIGGER CALLBACKS")
    line("// =============================================================================")
    line()

    // Generate individual trigger callbacks
    for (trigger in triggers) {
        if (trigger.onTriggerStatements.isNotEmpty()) {
            line("// onTrigger callback for: ${trigger.id}")
            line("static void _${trigger.id}_on_trigger(void) {")
            indent++
            generateStatementList(trigger.onTriggerStatements)
            indent--
            line("}")
            line()
        }

        if (trigger is WaveBasedTrigger && trigger.onWaveCompleteStatements.isNotEmpty()) {
            line("// onWaveComplete callback for: ${trigger.id}")
            line("static void _${trigger.id}_on_wave_complete(void) {")
            indent++
            generateStatementList(trigger.onWaveCompleteStatements)
            indent--
            line("}")
            line()
        }
    }

    // Generate dispatcher
    line("// Dispatch trigger callback")
    line("static void _trigger_on_trigger(UINT8 trigger_idx) {")
    indent++
    line("switch (trigger_idx) {")
    indent++
    for (trigger in triggers) {
        if (trigger.onTriggerStatements.isNotEmpty()) {
            line("case TRIGGER_${trigger.id.uppercase()}: _${trigger.id}_on_trigger(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate trigger helper functions. */
private fun CodeGenerator.generateTriggerHelperFunctions(triggers: List<EncounterTrigger>) {
    line("// =============================================================================")
    line("// TRIGGER HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    line("// Enable a trigger")
    line("static void _enable_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx < TRIGGER_COUNT) _trigger_active[trigger_idx] = 1u;")
    indent--
    line("}")
    line()

    line("// Disable a trigger")
    line("static void _disable_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx < TRIGGER_COUNT) _trigger_active[trigger_idx] = 0u;")
    indent--
    line("}")
    line()

    line("// Check if trigger is active")
    line("static UINT8 _is_trigger_active(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return 0u;")
    line("return _trigger_active[trigger_idx];")
    indent--
    line("}")
    line()

    line("// Reset trigger state")
    line("static void _reset_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return;")
    line()
    line("_step_count[trigger_idx] = 0u;")
    line("_time_elapsed[trigger_idx] = 0u;")
    line("_wave_current[trigger_idx] = 0u;")
    line("_wave_timer[trigger_idx] = 0u;")
    line("_wave_loop_count[trigger_idx] = 0u;")
    line()
    line("// Reset step chance to initial")
    line("INT8 step_idx = _trigger_to_step_idx[trigger_idx];")
    line("if (step_idx >= 0) {")
    indent++
    line("_step_chance[trigger_idx] = _step_initial_chance[(UINT8)step_idx];")
    indent--
    line("}")
    line()
    line("// Reset time safe frames")
    line("INT8 time_idx = _trigger_to_time_idx[trigger_idx];")
    line("if (time_idx >= 0) {")
    indent++
    line("_time_safe_remaining[trigger_idx] = _time_safe_frames[(UINT8)time_idx];")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Manually trigger an event-based encounter")
    line("static void _fire_event_trigger(UINT8 trigger_idx) {")
    indent++
    line("if (trigger_idx >= TRIGGER_COUNT) return;")
    line("if (_trigger_type[trigger_idx] != TRIGGER_TYPE_EVENT_BASED) return;")
    line("_trigger_on_trigger(trigger_idx);")
    indent--
    line("}")
    line()
}
