/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.world

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.world.EncounterMonster
import io.github.gbkt.core.world.EncounterTable

// =============================================================================
// ENCOUNTER CODE GENERATION
// =============================================================================

/**
 * Generate pending encounter variables.
 *
 * These variables are always generated as they are used by the encounter and battle systems
 * regardless of whether encounter tables are defined.
 */
internal fun CodeGenerator.generatePendingEncounterVariables() {
    line("// =============================================================================")
    line("// PENDING ENCOUNTER VARIABLES")
    line("// =============================================================================")
    line()
    line("// Pending encounter data (for passing to battle scene)")
    line("static UINT8 _pending_encounter_table = 0u;")
    line("static UINT8 _pending_encounter_entry = 0u;")
    line("static UINT8 _pending_encounter_valid = 0u;")
    line()
    line("// Forward declaration for banked encounter initialization")
    line("void _battle_init_from_encounter(void) BANKED;")
    line()
}

/** Generate encounter stub functions for when no encounter tables are defined. */
internal fun CodeGenerator.generateEncounterStubs() {
    line("// =============================================================================")
    line("// ENCOUNTER STUB FUNCTIONS (No encounter tables defined)")
    line("// =============================================================================")
    line()
    line("// Reset encounter state (stub)")
    line("static void encounter_reset(UINT8 table_id) {")
    indent++
    line("(void)table_id;")
    indent--
    line("}")
    line()
    line("// Check for encounter (stub - always returns 255 = no encounter)")
    line("static UINT8 encounter_check_step(void) {")
    indent++
    line("return 255u;")
    indent--
    line("}")
    line()
    line("// Disable encounters (stub)")
    line("static void encounter_disable(void) {")
    line("}")
    line()
    line("// Enable encounters (stub)")
    line("static void encounter_enable(void) {")
    line("}")
    line()
    line("// Initialize battle from pending encounter (stub)")
    setBank(codeBankEncounter)
    line("void _battle_init_from_encounter(void) BANKED {")
    line("}")
    line()
}

/**
 * Generate encounter system code.
 *
 * Creates:
 * - Encounter table data
 * - Encounter state variables
 * - Encounter check function
 * - Encounter enable/disable functions
 */
internal fun CodeGenerator.generateEncounterSystem() {
    // Collect all encounter tables from floors
    val encounterTables = collectEncounterTables()
    if (encounterTables.isEmpty()) {
        // Generate stubs when no encounter tables exist
        generateEncounterStubs()
        return
    }

    line("// =============================================================================")
    line("// RANDOM ENCOUNTER SYSTEM")
    line("// =============================================================================")
    line()

    // Generate encounter state variables
    generateEncounterVariables()

    // Generate each encounter table
    for (table in encounterTables) {
        generateEncounterTable(table)
    }

    // Generate encounter parameter arrays FIRST (needed by roll and check functions)
    generateEncounterParameterArrays(encounterTables)

    // Generate encounter helper functions (roll functions need parameter arrays)
    generateEncounterHelpers()

    // Generate encounter check function (calls roll functions from helpers)
    generateEncounterCheckFunction(encounterTables)
}

/** Collect encounter tables from the game. */
private fun CodeGenerator.collectEncounterTables(): List<EncounterTable> {
    // Collect standalone encounter tables
    val tables = game.encounterTables.toMutableList()

    // Also collect encounter tables associated with zones
    game.zones.forEach { zone ->
        zone.encounterTable?.let { table ->
            // Avoid duplicates
            if (tables.none { it.id == table.id }) {
                tables.add(table)
            }
        }
    }

    return tables
}

/** Generate encounter state variables. */
private fun CodeGenerator.generateEncounterVariables() {
    line("// Encounter state")
    line("static UINT8 _encounter_step_count = 0u;")
    line("static UINT8 _encounter_current_chance = 0u;")
    line("static UINT8 _encounter_disabled = 0u;")
    line("static UINT8 _encounter_table_id = 0u;")
    line()
    line("// Forward declarations for banked roll functions")
    line("UINT8 encounter_roll_table_with_level(UINT8 seed, UINT8 player_level) BANKED;")
    line("static UINT8 encounter_roll_table(UINT8 seed);")
    line()
}

/** Generate data for an encounter table. */
private fun CodeGenerator.generateEncounterTable(table: EncounterTable) {
    val tableName = table.id.uppercase()

    line("// Encounter table: ${table.id}")
    line("#define ENCOUNTER_${tableName}_SAFE_STEPS ${table.safeSteps}u")
    line("#define ENCOUNTER_${tableName}_INITIAL_CHANCE ${table.initialChance}u")
    line("#define ENCOUNTER_${tableName}_INCREMENT ${table.incrementPerStep}u")
    line("#define ENCOUNTER_${tableName}_MAX_CHANCE ${table.maxChance}u")
    line("#define ENCOUNTER_${tableName}_TOTAL_WEIGHT ${table.totalWeight}u")

    // Level-gated encounter constants
    if (table.isLevelGated) {
        line("#define ENCOUNTER_${tableName}_LEVEL_THRESHOLD ${table.levelThreshold}u")
        line("#define ENCOUNTER_${tableName}_LOW_LEVEL_WEIGHT ${table.lowLevelTotalWeight}u")
        line("#define ENCOUNTER_${tableName}_HIGH_LEVEL_WEIGHT ${table.highLevelTotalWeight}u")
        table.lowLevelSafeSteps?.let {
            line("#define ENCOUNTER_${tableName}_LOW_SAFE_STEPS ${it}u")
        }
        table.highLevelSafeSteps?.let {
            line("#define ENCOUNTER_${tableName}_HIGH_SAFE_STEPS ${it}u")
        }
    }
    line()

    // Generate entry weights array (for non-level-gated tables)
    if (!table.isLevelGated && table.entries.isNotEmpty()) {
        generateEncounterEntryArrays(table.id, table.entries)
    }

    // Generate level-gated arrays
    if (table.isLevelGated) {
        table.lowLevelEntries
            ?.takeIf { it.isNotEmpty() }
            ?.let { entries ->
                line("// Low-level encounter data for ${table.id}")
                generateEncounterEntryArrays("${table.id}_low", entries)
            }
        table.highLevelEntries
            ?.takeIf { it.isNotEmpty() }
            ?.let { entries ->
                line("// High-level encounter data for ${table.id}")
                generateEncounterEntryArrays("${table.id}_high", entries)
            }
    }
}

/** Generate entry data arrays (weights, monster counts, monster IDs). */
private fun CodeGenerator.generateEncounterEntryArrays(
    prefix: String,
    entries: List<io.github.gbkt.core.world.EncounterEntry>,
) {
    if (entries.isEmpty()) return

    line("// Entry weights for $prefix")
    line("static const UINT8 ${prefix}_weights[${entries.size}] = {")
    indent++
    line(entries.joinToString(", ") { "${it.weight}u" })
    indent--
    line("};")
    line()

    // Generate monster counts per entry (use encounterMonsters for correct count)
    line("// Monster counts per entry for $prefix")
    line("static const UINT8 ${prefix}_monster_counts[${entries.size}] = {")
    indent++
    line(entries.joinToString(", ") { "${it.encounterMonsters.size}u" })
    indent--
    line("};")
    line()

    // Generate monster IDs (flattened) - supports both base monsters and variants
    val allMonsterIds =
        entries.flatMap { entry ->
            entry.encounterMonsters.map { em -> getEncounterMonsterIndex(em) }
        }
    if (allMonsterIds.isNotEmpty()) {
        line("// Monster IDs for $prefix (flattened, includes variant indices)")
        line("static const UINT8 ${prefix}_monster_ids[${allMonsterIds.size}] = {")
        indent++
        allMonsterIds.chunked(8).forEach { chunk ->
            line(chunk.joinToString(", ") { "${it}u" } + ",")
        }
        indent--
        line("};")
        line()
    }
}

/**
 * Get the lookup table index for an encounter monster.
 *
 * For base monsters, returns the monster's index. For variants, returns the variant's index (which
 * is assigned during codegen).
 */
private fun CodeGenerator.getEncounterMonsterIndex(em: EncounterMonster): Int =
    when (em) {
        is EncounterMonster.Base -> {
            game.monsters.indexOfFirst { it.id == em.baseMonster.id }.coerceAtLeast(0)
        }
        is EncounterMonster.Variant -> {
            // If the variant is a base variant (no tier override), use base monster index
            if (em.variant.isBaseVariant) {
                game.monsters.indexOfFirst { it.id == em.variant.baseMonster.id }.coerceAtLeast(0)
            } else {
                // Use the variant's assigned index
                em.variant.variantIndex
            }
        }
    }

/** Generate encounter parameter arrays (needed by roll and check functions). */
private fun CodeGenerator.generateEncounterParameterArrays(tables: List<EncounterTable>) {
    val hasLevelGating = tables.any { it.isLevelGated }

    // Generate lookup tables for encounter parameters
    line("// Encounter table parameter lookup")
    line("static const UINT8 _encounter_safe_steps[${tables.size}] = {")
    indent++
    line(tables.joinToString(", ") { "${it.safeSteps}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _encounter_initial_chance[${tables.size}] = {")
    indent++
    line(tables.joinToString(", ") { "${it.initialChance}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _encounter_increment[${tables.size}] = {")
    indent++
    line(tables.joinToString(", ") { "${it.incrementPerStep}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _encounter_max_chance[${tables.size}] = {")
    indent++
    line(tables.joinToString(", ") { "${it.maxChance}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _encounter_total_weight[${tables.size}] = {")
    indent++
    line(tables.joinToString(", ") { "${it.totalWeight}u" })
    indent--
    line("};")
    line()

    // Generate level-gated lookup tables
    if (hasLevelGating) {
        line("// Level-gated encounter table parameters")
        line("// Level threshold (0 = not level-gated)")
        line("static const UINT8 _encounter_level_threshold[${tables.size}] = {")
        indent++
        line(tables.joinToString(", ") { "${it.levelThreshold ?: 0}u" })
        indent--
        line("};")
        line()

        line("// Low-level safe steps override (uses default if same as base)")
        line("static const UINT8 _encounter_low_safe_steps[${tables.size}] = {")
        indent++
        line(tables.joinToString(", ") { "${it.lowLevelSafeSteps ?: it.safeSteps}u" })
        indent--
        line("};")
        line()

        line("// High-level safe steps override (uses default if same as base)")
        line("static const UINT8 _encounter_high_safe_steps[${tables.size}] = {")
        indent++
        line(tables.joinToString(", ") { "${it.highLevelSafeSteps ?: it.safeSteps}u" })
        indent--
        line("};")
        line()

        line("// Low-level total weight (0 if no low-level table)")
        line("static const UINT8 _encounter_low_weight[${tables.size}] = {")
        indent++
        line(tables.joinToString(", ") { "${it.lowLevelTotalWeight}u" })
        indent--
        line("};")
        line()

        line("// High-level total weight (0 if no high-level table)")
        line("static const UINT8 _encounter_high_weight[${tables.size}] = {")
        indent++
        line(tables.joinToString(", ") { "${it.highLevelTotalWeight}u" })
        indent--
        line("};")
        line()
    }
}

/** Generate encounter check function. */
private fun CodeGenerator.generateEncounterCheckFunction(tables: List<EncounterTable>) {
    // Check if any tables use level gating
    val hasLevelGating = tables.any { it.isLevelGated }

    // Note: Lookup tables are generated by generateEncounterParameterArrays()

    line("// Check for random encounter on step")
    line("// Returns entry index (0-255) or 255 if no encounter")
    if (hasLevelGating) {
        line("// player_level is used for level-gated tables")
        line("static UINT8 encounter_check_step_with_level(UINT8 player_level) {")
    } else {
        line("static UINT8 encounter_check_step(void) {")
    }
    indent++

    line("if (_encounter_disabled) return 255u;")
    line("if (_encounter_table_id >= ${tables.size}u) return 255u;")
    line()
    line("_encounter_step_count++;")
    line()

    if (hasLevelGating) {
        line("// Determine effective safe steps (level-gated tables may have different values)")
        line("UINT8 effective_safe_steps = _encounter_safe_steps[_encounter_table_id];")
        line("UINT8 level_threshold = _encounter_level_threshold[_encounter_table_id];")
        line("if (level_threshold > 0u) {")
        indent++
        line("if (player_level < level_threshold) {")
        indent++
        line("effective_safe_steps = _encounter_low_safe_steps[_encounter_table_id];")
        indent--
        line("} else {")
        indent++
        line("effective_safe_steps = _encounter_high_safe_steps[_encounter_table_id];")
        indent--
        line("}")
        indent--
        line("}")
        line()
        line("// Check safe steps")
        line("if (_encounter_step_count <= effective_safe_steps) return 255u;")
    } else {
        line("// Check safe steps for current table")
        line(
            "if (_encounter_step_count <= _encounter_safe_steps[_encounter_table_id]) return 255u;"
        )
    }
    line()
    line("// Roll for encounter")
    line("UINT8 roll = _rand() & 0xFFu;")
    line("if (roll < _encounter_current_chance) {")
    indent++
    line("// Encounter triggered - reset chance and select encounter")
    line("_encounter_current_chance = _encounter_initial_chance[_encounter_table_id];")
    if (hasLevelGating) {
        line("return encounter_roll_table_with_level(roll, player_level);")
    } else {
        line("return encounter_roll_table(roll);")
    }
    indent--
    line("}")
    line()
    line("// No encounter - increment chance")
    line("_encounter_current_chance += _encounter_increment[_encounter_table_id];")
    line("UINT8 max_chance = _encounter_max_chance[_encounter_table_id];")
    line("if (_encounter_current_chance > max_chance) _encounter_current_chance = max_chance;")
    line("return 255u;")

    indent--
    line("}")
    line()

    // Generate convenience wrapper if level-gating exists
    if (hasLevelGating) {
        line("// Convenience wrapper when player level not needed")
        line("static UINT8 encounter_check_step(void) {")
        indent++
        line("// Default to level 1 for non-level-gated tables")
        line("return encounter_check_step_with_level(1u);")
        indent--
        line("}")
        line()
    }
}

/** Generate encounter helper functions. */
private fun CodeGenerator.generateEncounterHelpers() {
    val tables = collectEncounterTables()
    val hasLevelGating = tables.any { it.isLevelGated }

    if (hasLevelGating) {
        generateLevelGatedRollFunction(tables)
    } else {
        generateSimpleRollFunction(tables)
    }
}

/** Generate simple roll function (no level gating). */
private fun CodeGenerator.generateSimpleRollFunction(tables: List<EncounterTable>) {
    line("// Roll encounter table to select an entry using weights")
    line("static UINT8 encounter_roll_table(UINT8 seed) {")
    indent++
    line("UINT8 total = _encounter_total_weight[_encounter_table_id];")
    line("if (total == 0u) return 0u;")
    line()
    line("// Scale roll to total weight")
    line("UINT8 roll = seed % total;")
    line("UINT8 cumulative = 0u;")
    line()
    line("// Table-specific selection (dispatch by table ID)")
    line("switch (_encounter_table_id) {")
    indent++

    for ((tableIndex, table) in tables.withIndex()) {
        if (table.entries.isNotEmpty()) {
            line("case ${tableIndex}u: {")
            indent++
            line("const UINT8* weights = ${table.id}_weights;")
            line("for (UINT8 i = 0u; i < ${table.entries.size}u; i++) {")
            indent++
            line("cumulative += weights[i];")
            line("if (roll < cumulative) return i;")
            indent--
            line("}")
            line("return 0u;")
            indent--
            line("}")
        }
    }

    line("default: return 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()

    // Generate common helper functions
    line("// Reset encounter state for a floor/area")
    line("static void encounter_reset(UINT8 table_id) {")
    indent++
    line("_encounter_table_id = table_id;")
    line("_encounter_step_count = 0u;")
    line("_encounter_current_chance = (table_id < ${tables.size}u) ?")
    line("    _encounter_initial_chance[table_id] : 5u;")
    line("_encounter_disabled = 0u;")
    indent--
    line("}")
    line()

    line("// Disable encounters (repel effect)")
    line("static void encounter_disable(void) {")
    indent++
    line("_encounter_disabled = 1u;")
    indent--
    line("}")
    line()

    line("// Enable encounters")
    line("static void encounter_enable(void) {")
    indent++
    line("_encounter_disabled = 0u;")
    indent--
    line("}")
    line()

    // Generate battle initialization from encounter
    generateBattleInitFromEncounter(tables)
}

/** Generate level-gated roll function (banked - called only when encounter triggers). */
private fun CodeGenerator.generateLevelGatedRollFunction(tables: List<EncounterTable>) {
    // Bank this function - it's 170+ lines and only called when an encounter triggers
    setBank(codeBankEncounter)
    line("// Roll encounter table with level gating support")
    line("// Returns entry index, with high bit (0x80) set if from high-level table")
    line("UINT8 encounter_roll_table_with_level(UINT8 seed, UINT8 player_level) BANKED {")
    indent++
    line("UINT8 level_threshold = _encounter_level_threshold[_encounter_table_id];")
    line(
        "UINT8 use_high_level = (level_threshold > 0u && player_level >= level_threshold) ? 1u : 0u;"
    )
    line()
    line("// Table-specific selection (dispatch by table ID)")
    line("switch (_encounter_table_id) {")
    indent++

    for ((tableIndex, table) in tables.withIndex()) {
        line("case ${tableIndex}u: {")
        indent++

        if (table.isLevelGated) {
            // Level-gated table - select low or high level entries
            line("if (use_high_level) {")
            indent++
            if (!table.highLevelEntries.isNullOrEmpty()) {
                val highTotal = table.highLevelTotalWeight
                line("// High-level encounters")
                // Only generate zero check if total could actually be 0
                if (highTotal == 0) {
                    line("return 0x80u; // No weight defined")
                } else {
                    line("UINT8 roll = seed % ${highTotal}u;")
                    line("UINT8 cumulative = 0u;")
                    line("const UINT8* weights = ${table.id}_high_weights;")
                    line("for (UINT8 i = 0u; i < ${table.highLevelEntries.size}u; i++) {")
                    indent++
                    line("cumulative += weights[i];")
                    line("if (roll < cumulative) return i | 0x80u; // High bit marks high-level")
                    indent--
                    line("}")
                    line("return 0x80u;")
                }
            } else {
                line("return 0u; // No high-level entries defined")
            }
            indent--
            line("} else {")
            indent++
            if (!table.lowLevelEntries.isNullOrEmpty()) {
                val lowTotal = table.lowLevelTotalWeight
                line("// Low-level encounters")
                // Only generate zero check if total could actually be 0
                if (lowTotal == 0) {
                    line("return 0u; // No weight defined")
                } else {
                    line("UINT8 roll = seed % ${lowTotal}u;")
                    line("UINT8 cumulative = 0u;")
                    line("const UINT8* weights = ${table.id}_low_weights;")
                    line("for (UINT8 i = 0u; i < ${table.lowLevelEntries.size}u; i++) {")
                    indent++
                    line("cumulative += weights[i];")
                    line("if (roll < cumulative) return i;")
                    indent--
                    line("}")
                    line("return 0u;")
                }
            } else {
                line("return 0u; // No low-level entries defined")
            }
            indent--
            line("}")
        } else {
            // Non-level-gated table - use regular entries
            if (table.entries.isNotEmpty()) {
                val total = table.totalWeight
                line("// Non-level-gated table")
                // Only generate zero check if total could actually be 0
                if (total == 0) {
                    line("return 0u; // No weight defined")
                } else {
                    line("UINT8 roll = seed % ${total}u;")
                    line("UINT8 cumulative = 0u;")
                    line("const UINT8* weights = ${table.id}_weights;")
                    line("for (UINT8 i = 0u; i < ${table.entries.size}u; i++) {")
                    indent++
                    line("cumulative += weights[i];")
                    line("if (roll < cumulative) return i;")
                    indent--
                    line("}")
                    line("return 0u;")
                }
            } else {
                line("return 0u;")
            }
        }

        indent--
        line("}")
    }

    line("default: return 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()

    // Return to bank 0 for small helper functions
    returnToHome()

    // Generate backward-compatible wrapper (small, stays in bank 0)
    line("// Backward-compatible wrapper (defaults to level 1)")
    line("static UINT8 encounter_roll_table(UINT8 seed) {")
    indent++
    line("return encounter_roll_table_with_level(seed, 1u);")
    indent--
    line("}")
    line()

    line("// Reset encounter state for a floor/area")
    line("static void encounter_reset(UINT8 table_id) {")
    indent++
    line("_encounter_table_id = table_id;")
    line("_encounter_step_count = 0u;")
    line("_encounter_current_chance = (table_id < ${tables.size}u) ?")
    line("    _encounter_initial_chance[table_id] : 5u;")
    line("_encounter_disabled = 0u;")
    indent--
    line("}")
    line()

    line("// Disable encounters (repel effect)")
    line("static void encounter_disable(void) {")
    indent++
    line("_encounter_disabled = 1u;")
    indent--
    line("}")
    line()

    line("// Enable encounters")
    line("static void encounter_enable(void) {")
    indent++
    line("_encounter_disabled = 0u;")
    indent--
    line("}")
    line()

    // Generate battle initialization from encounter
    generateBattleInitFromEncounter(tables)
}

/**
 * Generate function to initialize battle from pending encounter.
 *
 * Supports both base monsters and variants - uses the appropriate index into the stat lookup
 * tables.
 *
 * For level-gated tables, the entry index has bit 7 (0x80) set if from high-level table.
 */
private fun CodeGenerator.generateBattleInitFromEncounter(tables: List<EncounterTable>) {
    if (tables.isEmpty() || game.monsters.isEmpty()) return

    val hasLevelGating = tables.any { it.isLevelGated }

    line("// Initialize battle from pending encounter")
    setBank(codeBankEncounter)
    line("void _battle_init_from_encounter(void) BANKED {")
    indent++
    line("if (!_pending_encounter_valid) return;")
    line()
    line("UINT8 table = _pending_encounter_table;")
    line("UINT8 entry = _pending_encounter_entry;")
    if (hasLevelGating) {
        line("UINT8 is_high_level = (entry & 0x80u) ? 1u : 0u;")
        line("entry = entry & 0x7Fu; // Strip high-level marker")
    }
    line("_pending_encounter_valid = 0u;")
    line()

    line("// Reset enemy count")
    line("_enemy_count = 0u;")
    line()

    line("// Dispatch by table to populate enemies")
    line("switch (table) {")
    indent++

    for ((tableIndex, table) in tables.withIndex()) {
        // Skip tables with no entries (including level-gated tables with no regular entries)
        val hasAnyEntries =
            table.entries.isNotEmpty() ||
                !table.lowLevelEntries.isNullOrEmpty() ||
                !table.highLevelEntries.isNullOrEmpty()
        if (!hasAnyEntries) continue

        line("case ${tableIndex}u: {")
        indent++
        line("// Table: ${table.id}")

        if (table.isLevelGated) {
            // Level-gated table - dispatch by is_high_level flag
            line("if (is_high_level) {")
            indent++
            generateEntrySwitch(table.highLevelEntries ?: emptyList(), "${table.id} (high)")
            indent--
            line("} else {")
            indent++
            generateEntrySwitch(table.lowLevelEntries ?: emptyList(), "${table.id} (low)")
            indent--
            line("}")
        } else {
            // Regular table
            generateEntrySwitch(table.entries, table.id)
        }

        line("break;")
        indent--
        line("}")
    }

    line("default: break;")
    indent--
    line("}")

    indent--
    line("}")
    line()
}

/** Generate switch statement for encounter entries. */
private fun CodeGenerator.generateEntrySwitch(
    entries: List<io.github.gbkt.core.world.EncounterEntry>,
    tableName: String,
) {
    if (entries.isEmpty()) {
        line("// No entries for $tableName")
        return
    }

    line("switch (entry) {")
    indent++

    for ((entryIndex, encounterEntry) in entries.withIndex()) {
        line("case ${entryIndex}u:")
        indent++

        // Build description string from encounter monsters
        val monsterDesc =
            encounterEntry.encounterMonsters.joinToString(" + ") { em ->
                when (em) {
                    is EncounterMonster.Base -> em.baseMonster.id
                    is EncounterMonster.Variant -> em.variant.variantId
                }
            }
        line("// $monsterDesc")

        for ((monsterSlot, em) in encounterEntry.encounterMonsters.withIndex()) {
            val monsterIndex = getEncounterMonsterIndex(em)
            val monsterName =
                when (em) {
                    is EncounterMonster.Base -> em.baseMonster.id
                    is EncounterMonster.Variant -> em.variant.variantId
                }

            line("// Slot $monsterSlot: $monsterName")
            line("_enemy_monster_id[$monsterSlot] = ${monsterIndex}u;")
            line("_combatant_alive[MAX_PARTY_SIZE + $monsterSlot] = 1u;")

            // Initialize HP from lookup table (works for both base monsters and variants)
            line("_combatant_hp[MAX_PARTY_SIZE + $monsterSlot] = _monster_base_hp[$monsterIndex];")
            line(
                "_combatant_hp_max[MAX_PARTY_SIZE + $monsterSlot] = _monster_base_hp[$monsterIndex];"
            )
        }

        line("_enemy_count = ${encounterEntry.encounterMonsters.size}u;")
        line("break;")
        indent--
    }

    line("default: break;")
    indent--
    line("}")
}
