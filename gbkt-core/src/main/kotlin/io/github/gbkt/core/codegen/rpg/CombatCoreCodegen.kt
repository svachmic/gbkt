/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// COMBAT CORE CODE GENERATION
// =============================================================================

/**
 * Generate combat core constants and bridge functions.
 *
 * This runs before ability system generation to provide:
 * - MAX_PARTY_SIZE, MAX_ENEMY_SLOTS constants
 * - Stat type constants (STAT_HP, STAT_SP, etc.)
 * - Combat bridge functions (_party_get_stat, _combat_deal_damage, etc.)
 *
 * These functions work with global combatant arrays that get populated when a battle starts.
 */
internal fun CodeGenerator.generateCombatCoreSystem() {
    // Only generate if we have abilities, characters, or monsters (indicating combat is used)
    if (game.abilities.isEmpty() && game.characters.isEmpty() && game.monsters.isEmpty()) return

    line("// =============================================================================")
    line("// COMBAT CORE SYSTEM")
    line("// =============================================================================")
    line()

    // Use configured party/enemy sizes from game config
    val maxPartySize = game.config.maxPartySize
    val maxEnemies = game.config.maxEnemies
    val maxCombatants = maxPartySize + maxEnemies

    // Combat constants
    generateCombatConstants(maxPartySize, maxEnemies)

    // Stat type constants
    generateStatTypeConstantsCore()

    // Immunity constants
    generateImmunityConstants()

    // Combat arrays (global, shared across battle systems)
    generateCombatArrays(maxCombatants)

    // Character base stat lookup tables
    generateCharacterBaseTables()

    // Combat bridge functions (defines _status_apply, _status_clear_all, etc.)
    // Must be generated BEFORE party init functions which call these
    generateCombatBridgeFunctions(maxCombatants)

    // Party initialization functions (calls _status_clear_all)
    generatePartyInitFunctions()
}

/** Generate combat constants. */
private fun CodeGenerator.generateCombatConstants(maxPartySize: Int, maxEnemies: Int) {
    line("// Combat size constants")
    line("#define MAX_PARTY_SIZE ${maxPartySize}u")
    line("#define MAX_ENEMIES ${maxEnemies}u")
    line("#define MAX_ENEMY_SLOTS ${maxEnemies}u") // Alias for compatibility
    line("#define MAX_COMBATANTS ${maxPartySize + maxEnemies}u")
    line()
}

/** Generate stat type constants. */
private fun CodeGenerator.generateStatTypeConstantsCore() {
    line("// Stat type constants")
    line("#define STAT_HP 0u")
    line("#define STAT_SP 1u")
    line("#define STAT_ATK 2u")
    line("#define STAT_DEF 3u")
    line("#define STAT_MATK 4u")
    line("#define STAT_MDEF 5u")
    line("#define STAT_AGL 6u")
    line()
}

/** Generate immunity constants. */
private fun CodeGenerator.generateImmunityConstants() {
    line("// Immunity type constants")
    line("#define IMMUNITY_INSTANT_KILL 0u")
    line("#define IMMUNITY_POISON 1u")
    line("#define IMMUNITY_PARALYSIS 2u")
    line("#define IMMUNITY_SLEEP 3u")
    line("#define IMMUNITY_SILENCE 4u")
    line("#define IMMUNITY_CONFUSION 5u")
    line()
}

/** Generate combat arrays (global). */
private fun CodeGenerator.generateCombatArrays(maxCombatants: Int) {
    val maxEnemies = maxCombatants / 2 // Half are enemies

    line("// Global combatant arrays (populated at battle start)")
    line("static UINT16 _combatant_hp[$maxCombatants] = {0};")
    line("static UINT16 _combatant_hp_max[$maxCombatants] = {0};")
    line("static UINT8 _combatant_sp[$maxCombatants] = {0};")
    line("static UINT8 _combatant_sp_max[$maxCombatants] = {0};")
    line("static UINT8 _combatant_atk[$maxCombatants] = {0};")
    line("static UINT8 _combatant_def[$maxCombatants] = {0};")
    line("static UINT8 _combatant_matk[$maxCombatants] = {0};")
    line("static UINT8 _combatant_mdef[$maxCombatants] = {0};")
    line("static UINT8 _combatant_agl[$maxCombatants] = {0};")
    line("static UINT8 _combatant_defending[$maxCombatants] = {0};")
    line("static UINT8 _combatant_x[$maxCombatants] = {0};")
    line("static UINT8 _combatant_y[$maxCombatants] = {0};")
    line("static UINT8 _combatant_immunities[$maxCombatants] = {0}; // Bitmask of immunities")
    line("static UINT8 _combatant_alive[$maxCombatants] = {0}; // Alive flag")
    line()

    // Status effect tracking per combatant (up to 4 effects per combatant)
    val maxEffectsPerCombatant = 4
    line("// Status effect tracking per combatant")
    line("#define MAX_EFFECTS_PER_COMBATANT ${maxEffectsPerCombatant}u")
    line("static UINT8 _combatant_effect_id[$maxCombatants][$maxEffectsPerCombatant] = {{0}};")
    line(
        "static UINT8 _combatant_effect_duration[$maxCombatants][$maxEffectsPerCombatant] = {{0}};"
    )
    line("static UINT8 _combatant_effect_stacks[$maxCombatants][$maxEffectsPerCombatant] = {{0}};")
    line()

    // Battle enemy type tracking (which monster type is in each slot)
    line("// Battle enemy type tracking (monster type ID for each enemy slot)")
    line("static UINT8 _battle_enemy_types[$maxEnemies] = {0};")
    line()

    // Active party/enemy tracking
    line("// Active combatant tracking")
    line("static UINT8 _party_count = 0u;")
    line("static UINT8 _enemy_count = 0u;")
    line("static UINT8 _party_size = 1u;  // Default party size")
    line()

    // Generate enemy monster ID tracking for AI
    line("// Enemy monster ID tracking (for AI lookup)")
    line("static UINT8 _enemy_monster_id[MAX_ENEMIES] = {0};")
    line()
}

/** Generate character base stat lookup tables for indexed access. */
private fun CodeGenerator.generateCharacterBaseTables() {
    val charactersWithStats = game.characters.filter { it.hasStats }
    if (charactersWithStats.isEmpty()) return

    line("// Character base stat lookup tables (for party initialization from selected class)")
    line("#define CHARACTER_COUNT ${charactersWithStats.size}u")
    line()

    // Generate base stat arrays for each stat type
    line("// Base HP values per character")
    line("static const UINT16 _character_base_hp[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "HP" }?.baseValue?.toString()
                ?: "100"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT16 _character_base_hp_max[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "HP" }?.maxValue?.toString()
                ?: "999"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_sp[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "SP" }?.baseValue?.toString()
                ?: "50"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_sp_max[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "SP" }?.maxValue?.toString()
                ?: "99"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_atk[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "ATK" }?.baseValue?.toString()
                ?: "10"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_def[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "DEF" }?.baseValue?.toString()
                ?: "10"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_matk[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "MATK" }?.baseValue?.toString()
                ?: "10"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_mdef[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "MDEF" }?.baseValue?.toString()
                ?: "10"
        } + "u"
    )
    indent--
    line("};")

    line("static const UINT8 _character_base_agl[${charactersWithStats.size}] = {")
    indent++
    line(
        charactersWithStats.joinToString(", ") {
            it.stats.definition.stats.find { s -> s.type.name == "AGL" }?.baseValue?.toString()
                ?: "10"
        } + "u"
    )
    indent--
    line("};")

    line()
}

/** Generate party initialization functions. */
private fun CodeGenerator.generatePartyInitFunctions() {
    val charactersWithStats = game.characters.filter { it.hasStats }
    if (charactersWithStats.isEmpty()) return

    line("// Initialize party slot 0 from a character class index")
    line("// Call this when starting a new game with a selected class")
    line("static void _party_init_from_class(UINT8 class_idx) {")
    indent++
    line("if (class_idx >= CHARACTER_COUNT) return;")
    line()
    line("// Copy base stats to combatant slot 0")
    line("_combatant_hp[0] = _character_base_hp[class_idx];")
    line("_combatant_hp_max[0] = _character_base_hp_max[class_idx];")
    line("_combatant_sp[0] = _character_base_sp[class_idx];")
    line("_combatant_sp_max[0] = _character_base_sp_max[class_idx];")
    line("_combatant_atk[0] = _character_base_atk[class_idx];")
    line("_combatant_def[0] = _character_base_def[class_idx];")
    line("_combatant_matk[0] = _character_base_matk[class_idx];")
    line("_combatant_mdef[0] = _character_base_mdef[class_idx];")
    line("_combatant_agl[0] = _character_base_agl[class_idx];")
    line()
    line("// Mark slot 0 as alive and set party count")
    line("_combatant_alive[0] = 1u;")
    line("_party_count = 1u;")
    line("_party_size = 1u;")
    line()
    line("// Clear status effects")
    line("_status_clear_all(0);")
    indent--
    line("}")
    line()
}

/** Generate combat bridge functions. */
private fun CodeGenerator.generateCombatBridgeFunctions(maxCombatants: Int) {
    line("// -----------------------------------------------------------------------------")
    line("// Combat Bridge Functions")
    line("// -----------------------------------------------------------------------------")
    line()

    // _party_get_stat
    line("// Get stat value from combatant")
    line("static UINT16 _party_get_stat(UINT8 idx, UINT8 stat_type) {")
    indent++
    line("switch (stat_type) {")
    indent++
    line("case STAT_HP: return _combatant_hp[idx];")
    line("case STAT_SP: return (UINT16)_combatant_sp[idx];")
    line("case STAT_ATK: return (UINT16)_combatant_atk[idx];")
    line("case STAT_DEF: return (UINT16)_combatant_def[idx];")
    line("case STAT_MATK: return (UINT16)_combatant_matk[idx];")
    line("case STAT_MDEF: return (UINT16)_combatant_mdef[idx];")
    line("case STAT_AGL: return (UINT16)_combatant_agl[idx];")
    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // _party_modify_stat
    line("// Modify stat value for combatant")
    line("static void _party_modify_stat(UINT8 idx, UINT8 stat_type, INT16 amount) {")
    indent++
    line("switch (stat_type) {")
    indent++
    line("case STAT_HP: {")
    indent++
    line("INT32 new_val = (INT32)_combatant_hp[idx] + amount;")
    line("if (new_val < 0) new_val = 0;")
    line("if (new_val > (INT32)_combatant_hp_max[idx]) new_val = _combatant_hp_max[idx];")
    line("_combatant_hp[idx] = (UINT16)new_val;")
    line("break;")
    indent--
    line("}")
    line("case STAT_SP: {")
    indent++
    line("INT16 new_val = (INT16)_combatant_sp[idx] + amount;")
    line("if (new_val < 0) new_val = 0;")
    line("if (new_val > (INT16)_combatant_sp_max[idx]) new_val = _combatant_sp_max[idx];")
    line("_combatant_sp[idx] = (UINT8)new_val;")
    line("break;")
    indent--
    line("}")
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // _combat_deal_damage
    line("// Deal damage to a combatant (returns actual damage dealt)")
    line(
        "static UINT16 _combat_deal_damage(UINT8 attacker, UINT8 target, UINT16 base_damage, UINT8 aspect) {"
    )
    indent++
    line("(void)attacker; // May be used for counter-attacks etc")
    line("(void)aspect; // May be used for elemental modifiers")
    line()
    line("// Apply defense reduction")
    line("UINT8 def = _combatant_def[target];")
    line(
        "if (_combatant_defending[target]) def = (UINT8)(def * 3u / 2u); // 1.5x defense when defending"
    )
    line()
    line("// Calculate damage (minimum 1)")
    line("UINT16 damage = base_damage;")
    line("if (damage > def) damage -= def; else damage = 1u;")
    line()
    line("// Apply damage")
    line("if (damage >= _combatant_hp[target]) {")
    indent++
    line("damage = _combatant_hp[target];")
    line("_combatant_hp[target] = 0u;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target] -= damage;")
    indent--
    line("}")
    line()
    line("return damage;")
    indent--
    line("}")
    line()

    // _combat_heal
    line("// Heal a combatant")
    line("static void _combat_heal(UINT8 target, UINT16 amount) {")
    indent++
    line("UINT16 new_hp = _combatant_hp[target] + amount;")
    line("if (new_hp > _combatant_hp_max[target]) new_hp = _combatant_hp_max[target];")
    line("_combatant_hp[target] = new_hp;")
    indent--
    line("}")
    line()

    // _status_apply - Apply status effect to combatant
    generateStatusApplyFunction()

    // _status_clear_debuffs - Clear all debuffs from combatant
    generateStatusClearDebuffsFunction()

    // _status_clear_all - Clear all status effects from combatant
    generateStatusClearAllFunction()

    // _status_clear_effect - Clear a specific status effect from combatant
    generateStatusClearEffectFunction()

    // _status_has_effect - Check if combatant has specific effect
    generateStatusHasEffectFunction()

    // _status_tick - Decrement effect durations
    generateStatusTickFunction()

    // _combatant_can_act - Check if combatant can act (not stunned/sleeping/etc)
    generateCombatantCanActFunction()

    // _combatant_has_immunity
    line("// Check if combatant has immunity")
    line("static UINT8 _combatant_has_immunity(UINT8 target, UINT8 immunity_type) {")
    indent++
    line("return (_combatant_immunities[target] & (1u << immunity_type)) ? 1u : 0u;")
    indent--
    line("}")
    line()

    // _combatant_set_hp
    line("// Set combatant HP directly")
    line("static void _combatant_set_hp(UINT8 target, UINT16 hp) {")
    indent++
    line("if (hp > _combatant_hp_max[target]) hp = _combatant_hp_max[target];")
    line("_combatant_hp[target] = hp;")
    indent--
    line("}")
    line()

    // _combatant_full_heal
    line("// Fully heal combatant (HP and SP to max)")
    line("static void _combatant_full_heal(UINT8 target) {")
    indent++
    line("_combatant_hp[target] = _combatant_hp_max[target];")
    line("_combatant_sp[target] = _combatant_sp_max[target];")
    indent--
    line("}")
    line()

    // _show_battle_message - Display message in battle UI
    line("// Show battle message at bottom of screen")
    line("static void _show_battle_message(const char* msg) {")
    indent++
    line("// Render message at row 16 (bottom area typical for battle messages)")
    line("gotoxy(1u, 16u);")
    line("printf(\"%-18s\", msg);")
    indent--
    line("}")
    line()

    // _battle_show_damage_number - Display floating damage number
    line("// Show floating damage number above target")
    line(
        "static void _battle_show_damage_number(UINT8 target_idx, UINT16 amount, UINT8 is_crit, UINT8 is_heal) {"
    )
    indent++
    line("// Get target position (from combatant position arrays)")
    line("UINT8 x = 40u + (target_idx * 24u); // Simple horizontal layout")
    line("UINT8 y = 48u;")
    line("// Convert to tile coordinates and display")
    line("gotoxy(x >> 3u, y >> 3u);")
    line("if (is_heal) {")
    indent++
    line("printf(\"+%u\", amount);")
    indent--
    line("} else if (is_crit) {")
    indent++
    line("printf(\"%u!\", amount);")
    indent--
    line("} else {")
    indent++
    line("printf(\"%u\", amount);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // _rand wrapper if not already available
    line("// Random number helper (wraps GBDK rand)")
    line("static inline UINT16 _rand(void) { return (UINT16)rand(); }")
    line()
}

/** Generate _status_apply function for combatant-level status effect application. */
private fun CodeGenerator.generateStatusApplyFunction() {
    val effects = game.statusEffects

    line("// Apply status effect to combatant")
    line("// Returns 1 if effect was applied, 0 if failed (e.g., effect slot full)")
    line("static UINT8 _status_apply(UINT8 target, UINT8 effect_id) {")
    indent++

    if (effects.isEmpty()) {
        line("(void)target;")
        line("(void)effect_id;")
        line("return 0u; // No status effects defined")
    } else {
        line(
            "if (target >= MAX_COMBATANTS || effect_id == 0u || effect_id > STATUS_EFFECT_COUNT) return 0u;"
        )
        line()
        line("// Get base duration from lookup table")
        line("UINT8 _base_dur = _effect_base_duration[effect_id - 1u];")
        line()
        line("// Find existing slot with this effect or first empty slot")
        line("UINT8 _slot = 255u;")
        line("UINT8 _existing = 255u;")
        line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
        indent++
        line("if (_combatant_effect_id[target][_i] == effect_id) { _existing = _i; break; }")
        line("if (_combatant_effect_id[target][_i] == 0u && _slot == 255u) { _slot = _i; }")
        indent--
        line("}")
        line()
        line("// If effect exists, refresh duration")
        line("if (_existing != 255u) {")
        indent++
        line("_combatant_effect_duration[target][_existing] = _base_dur;")
        line("return 1u;")
        indent--
        line("}")
        line()
        line("// If no empty slot, fail")
        line("if (_slot == 255u) return 0u;")
        line()
        line("// Apply new effect")
        line("_combatant_effect_id[target][_slot] = effect_id;")
        line("_combatant_effect_duration[target][_slot] = _base_dur;")
        line("_combatant_effect_stacks[target][_slot] = 1u;")
        line("return 1u;")
    }

    indent--
    line("}")
    line()
}

/** Generate _status_clear_debuffs function. */
private fun CodeGenerator.generateStatusClearDebuffsFunction() {
    val effects = game.statusEffects
    val debuffIds =
        effects
            .filter {
                it.category == io.github.gbkt.core.rpg.EffectCategory.DEBUFF ||
                    it.category == io.github.gbkt.core.rpg.EffectCategory.CONDITION
            }
            .map { it.id.value }

    line("// Clear all debuffs from combatant")
    line("static void _status_clear_debuffs(UINT8 target) {")
    indent++

    if (debuffIds.isEmpty()) {
        line("(void)target;")
        line("// No debuffs defined")
    } else {
        line("if (target >= MAX_COMBATANTS) return;")
        line()
        line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
        indent++
        line("UINT8 _eid = _combatant_effect_id[target][_i];")
        line("if (_eid != 0u) {")
        indent++
        line("// Check if this is a debuff")
        for (id in debuffIds) {
            line(
                "if (_eid == ${id}u) { _combatant_effect_id[target][_i] = 0u; _combatant_effect_duration[target][_i] = 0u; _combatant_effect_stacks[target][_i] = 0u; }"
            )
        }
        indent--
        line("}")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate _status_clear_all function. */
private fun CodeGenerator.generateStatusClearAllFunction() {
    line("// Clear all status effects from combatant")
    line("static void _status_clear_all(UINT8 target) {")
    indent++
    line("if (target >= MAX_COMBATANTS) return;")
    line()
    line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
    indent++
    line("_combatant_effect_id[target][_i] = 0u;")
    line("_combatant_effect_duration[target][_i] = 0u;")
    line("_combatant_effect_stacks[target][_i] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate _status_clear_effect function to clear a specific status effect. */
private fun CodeGenerator.generateStatusClearEffectFunction() {
    line("// Clear a specific status effect from combatant")
    line("static void _status_clear_effect(UINT8 target, UINT8 effect_id) {")
    indent++
    line("if (target >= MAX_COMBATANTS || effect_id == 0u) return;")
    line()
    line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
    indent++
    line("if (_combatant_effect_id[target][_i] == effect_id) {")
    indent++
    line("_combatant_effect_id[target][_i] = 0u;")
    line("_combatant_effect_duration[target][_i] = 0u;")
    line("_combatant_effect_stacks[target][_i] = 0u;")
    line("return; // Only clear first instance")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate _status_has_effect function. */
private fun CodeGenerator.generateStatusHasEffectFunction() {
    line("// Check if combatant has a specific status effect")
    line("static UINT8 _status_has_effect(UINT8 target, UINT8 effect_id) {")
    indent++
    line("if (target >= MAX_COMBATANTS || effect_id == 0u) return 0u;")
    line()
    line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
    indent++
    line("if (_combatant_effect_id[target][_i] == effect_id) return 1u;")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()
}

/** Generate _status_tick function for decrementing effect durations and applying DoT/HoT. */
private fun CodeGenerator.generateStatusTickFunction() {
    val hasStatusEffects = game.statusEffects.isNotEmpty()

    line("// Tick status effect durations for all combatants (call at end of turn)")
    line("// Also applies DoT (damage over time) and HoT (heal over time) effects")
    line("static void _status_tick_all(void) {")
    indent++
    line("for (UINT8 _t = 0u; _t < MAX_COMBATANTS; _t++) {")
    indent++
    line("if (!_combatant_alive[_t]) continue;")
    line()
    line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
    indent++
    line("UINT8 _eid = _combatant_effect_id[_t][_i];")
    line("if (_eid != 0u && _combatant_effect_duration[_t][_i] > 0u) {")
    indent++

    if (hasStatusEffects) {
        line("// Apply DoT damage if any")
        line("if (_eid <= STATUS_EFFECT_COUNT && _effect_dot_damage[_eid - 1u] > 0u) {")
        indent++
        line("UINT8 _dot = _effect_dot_damage[_eid - 1u];")
        line("// Apply stacking multiplier")
        line("_dot = _dot * _combatant_effect_stacks[_t][_i];")
        line("if (_combatant_hp[_t] > _dot) {")
        indent++
        line("_combatant_hp[_t] -= _dot;")
        indent--
        line("} else {")
        indent++
        line("_combatant_hp[_t] = 0u;")
        line("_combatant_alive[_t] = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line()
        line("// Apply HoT healing if any")
        line("if (_eid <= STATUS_EFFECT_COUNT && _effect_hot_heal[_eid - 1u] > 0u) {")
        indent++
        line("UINT8 _hot = _effect_hot_heal[_eid - 1u];")
        line("// Apply stacking multiplier")
        line("_hot = _hot * _combatant_effect_stacks[_t][_i];")
        line("UINT16 _new_hp = (UINT16)_combatant_hp[_t] + _hot;")
        line("if (_new_hp > _combatant_hp_max[_t]) _new_hp = _combatant_hp_max[_t];")
        line("_combatant_hp[_t] = (UINT8)_new_hp;")
        indent--
        line("}")
        line()
    }

    line("// Decrement duration")
    line("_combatant_effect_duration[_t][_i]--;")
    line("if (_combatant_effect_duration[_t][_i] == 0u) {")
    indent++
    line("// Effect expired - clear it")
    line("_combatant_effect_id[_t][_i] = 0u;")
    line("_combatant_effect_stacks[_t][_i] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate _combatant_can_act function that checks if a combatant can act. */
private fun CodeGenerator.generateCombatantCanActFunction() {
    val hasStatusEffects = game.statusEffects.isNotEmpty()

    line("// Check if combatant can act (not stunned/sleeping/paralyzed/etc)")
    line("// Returns 1 if can act, 0 if prevented by status effect")
    line("static UINT8 _combatant_can_act(UINT8 target) {")
    indent++
    line("if (target >= MAX_COMBATANTS || !_combatant_alive[target]) return 0u;")
    line()

    if (hasStatusEffects) {
        line("// Check all active effects for preventsAction flag")
        line("for (UINT8 _i = 0u; _i < MAX_EFFECTS_PER_COMBATANT; _i++) {")
        indent++
        line("UINT8 _eid = _combatant_effect_id[target][_i];")
        line("if (_eid != 0u && _eid <= STATUS_EFFECT_COUNT) {")
        indent++
        line("if (_effect_prevents_action[_eid - 1u]) return 0u;")
        indent--
        line("}")
        indent--
        line("}")
    }

    line("return 1u;")
    indent--
    line("}")
    line()
}
