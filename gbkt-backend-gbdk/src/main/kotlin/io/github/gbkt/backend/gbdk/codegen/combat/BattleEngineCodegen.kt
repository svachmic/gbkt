/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.combat

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.combat.ActiveTimeBattleEngine
import io.github.gbkt.core.combat.BattleEngine
import io.github.gbkt.core.combat.BattleOutcome
import io.github.gbkt.core.combat.CombatType
import io.github.gbkt.core.combat.RealTimeBattleEngine
import io.github.gbkt.core.combat.TacticalBattleEngine
import io.github.gbkt.core.combat.TurnBasedBattleEngine
import io.github.gbkt.core.combat.TurnOrderStrategy
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// PLUGGABLE BATTLE ENGINE CODE GENERATION
// =============================================================================

/** Helper to generate a list of statements. */
private fun GBDKCodeGenerator.generateStatementList(statements: List<IRStatement>) {
    for (stmt in statements) {
        generateStatement(stmt)
    }
}

/**
 * Generate pluggable battle engine system.
 *
 * Creates:
 * - Combat type and outcome constants
 * - Engine configuration tables
 * - Combat state variables
 * - Update functions for each engine type
 * - Common combat utilities
 */
internal fun GBDKCodeGenerator.generateBattleEngineSystem() {
    val engines = game.battleEngines
    if (engines.isEmpty()) return

    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// PLUGGABLE BATTLE ENGINE SYSTEM")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    // Generate type constants
    generateCombatTypeConstants()
    generateBattleOutcomeConstants()
    generateTurnOrderConstants()

    // Generate engine index constants
    generateEngineIndexConstants(engines)

    // Generate engine configuration tables
    generateEngineConfigTables(engines)

    // Generate type-specific configuration
    generateTurnBasedConfig(engines.filterIsInstance<TurnBasedBattleEngine>())
    generateActiveTimeConfig(engines.filterIsInstance<ActiveTimeBattleEngine>())
    generateRealTimeConfig(engines.filterIsInstance<RealTimeBattleEngine>())
    generateTacticalConfig(engines.filterIsInstance<TacticalBattleEngine>())

    // Generate combat state variables
    generateCombatStateVariables(engines)

    // Generate engine update functions
    generateTurnBasedUpdate(engines.filterIsInstance<TurnBasedBattleEngine>())
    generateActiveTimeUpdate(engines.filterIsInstance<ActiveTimeBattleEngine>())
    generateRealTimeUpdate(engines.filterIsInstance<RealTimeBattleEngine>())
    generateTacticalUpdate(engines.filterIsInstance<TacticalBattleEngine>())

    // Generate dispatch and common functions
    generateCombatDispatchFunction(engines)
    generateCombatCallbacks(engines)
    generateCombatHelperFunctions(engines)
}

/** Generate combat type constants. */
private fun GBDKCodeGenerator.generateCombatTypeConstants() {
    line("// Combat type constants")
    for ((index, type) in CombatType.entries.withIndex()) {
        line("#define COMBAT_TYPE_${type.name} ${index}u")
    }
    line()
}

/** Generate battle outcome constants. */
private fun GBDKCodeGenerator.generateBattleOutcomeConstants() {
    line("// Battle outcome constants")
    for ((index, outcome) in BattleOutcome.entries.withIndex()) {
        line("#define OUTCOME_${outcome.name} ${index}u")
    }
    line()
}

/** Generate turn order constants. */
private fun GBDKCodeGenerator.generateTurnOrderConstants() {
    line("// Turn order strategy constants")
    for ((index, strategy) in TurnOrderStrategy.entries.withIndex()) {
        line("#define TURN_ORDER_${strategy.name} ${index}u")
    }
    line()
}

/** Generate engine index constants. */
private fun GBDKCodeGenerator.generateEngineIndexConstants(engines: List<BattleEngine>) {
    line("// Battle engine index constants")
    for ((index, engine) in engines.withIndex()) {
        line("#define ENGINE_${engine.id.uppercase()} ${index}u")
    }
    line("#define ENGINE_COUNT ${engines.size}u")
    line()
}

/** Generate engine configuration tables. */
private fun GBDKCodeGenerator.generateEngineConfigTables(engines: List<BattleEngine>) {
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// ENGINE CONFIGURATION TABLES")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    // Combat types
    line("static const UINT8 _engine_type[ENGINE_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "COMBAT_TYPE_${it.combatType.name}" })
    indent--
    line("};")
    line()

    // Max party sizes
    line("static const UINT8 _engine_max_party[ENGINE_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.maxPartySize}u" })
    indent--
    line("};")
    line()

    // Max enemy counts
    line("static const UINT8 _engine_max_enemies[ENGINE_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.maxEnemies}u" })
    indent--
    line("};")
    line()
}

/** Generate turn-based configuration. */
private fun GBDKCodeGenerator.generateTurnBasedConfig(engines: List<TurnBasedBattleEngine>) {
    if (engines.isEmpty()) return

    line("// Turn-based engine configuration")
    line("#define TURN_BASED_COUNT ${engines.size}u")
    line()

    // Turn order strategy
    line("static const UINT8 _turn_order_strategy[TURN_BASED_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "TURN_ORDER_${it.turnOrderStrategy.name}" })
    indent--
    line("};")
    line()

    // Flee settings
    line("static const UINT8 _flee_base_chance[TURN_BASED_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.fleeChanceBase}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _flee_per_agi[TURN_BASED_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.fleeChancePerAgility}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _flee_allowed[TURN_BASED_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.allowFlee) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Engine index mapping
    val allEngines = game.battleEngines
    line("static const INT8 _engine_to_turn_idx[ENGINE_COUNT] = {")
    indent++
    var tbIdx = 0
    line(
        allEngines.joinToString(", ") { engine ->
            if (engine is TurnBasedBattleEngine) "${tbIdx++}" else "-1"
        }
    )
    indent--
    line("};")
    line()
}

/** Generate active time configuration. */
private fun GBDKCodeGenerator.generateActiveTimeConfig(engines: List<ActiveTimeBattleEngine>) {
    if (engines.isEmpty()) return

    line("// Active time engine configuration")
    line("#define ATB_COUNT ${engines.size}u")
    line()

    // Fill rate
    line("static const UINT8 _atb_fill_rate[ATB_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.baseFillRate}u" })
    indent--
    line("};")
    line()

    // Speed multiplier
    line("static const UINT8 _atb_speed_mult[ATB_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.speedMultiplier}u" })
    indent--
    line("};")
    line()

    // Pause settings
    line("static const UINT8 _atb_pause_menu[ATB_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.pauseOnMenu) "1u" else "0u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _atb_pause_anim[ATB_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.pauseOnAnimation) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Engine index mapping
    val allEngines = game.battleEngines
    line("static const INT8 _engine_to_atb_idx[ENGINE_COUNT] = {")
    indent++
    var atbIdx = 0
    line(
        allEngines.joinToString(", ") { engine ->
            if (engine is ActiveTimeBattleEngine) "${atbIdx++}" else "-1"
        }
    )
    indent--
    line("};")
    line()
}

/** Generate real-time configuration. */
private fun GBDKCodeGenerator.generateRealTimeConfig(engines: List<RealTimeBattleEngine>) {
    if (engines.isEmpty()) return

    line("// Real-time engine configuration")
    line("#define REALTIME_COUNT ${engines.size}u")
    line()

    // Hit stun
    line("static const UINT8 _rt_hitstun[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.hitStunFrames}u" })
    indent--
    line("};")
    line()

    // I-frames
    line("static const UINT8 _rt_iframes[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.invincibilityFrames}u" })
    indent--
    line("};")
    line()

    // Knockback
    line("static const UINT8 _rt_knockback[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.knockbackDistance}u" })
    indent--
    line("};")
    line()

    // Attack cancelling
    line("static const UINT8 _rt_atk_cancel[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.attackCancelling) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Block settings
    line("static const UINT8 _rt_block_allowed[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.allowBlock) "1u" else "0u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _rt_block_reduction[REALTIME_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.blockReduction}u" })
    indent--
    line("};")
    line()

    // Engine index mapping
    val allEngines = game.battleEngines
    line("static const INT8 _engine_to_rt_idx[ENGINE_COUNT] = {")
    indent++
    var rtIdx = 0
    line(
        allEngines.joinToString(", ") { engine ->
            if (engine is RealTimeBattleEngine) "${rtIdx++}" else "-1"
        }
    )
    indent--
    line("};")
    line()
}

/** Generate tactical configuration. */
private fun GBDKCodeGenerator.generateTacticalConfig(engines: List<TacticalBattleEngine>) {
    if (engines.isEmpty()) return

    line("// Tactical engine configuration")
    line("#define TACTICAL_COUNT ${engines.size}u")
    line()

    // Grid size
    line("static const UINT8 _tac_grid_w[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.gridWidth}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _tac_grid_h[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.gridHeight}u" })
    indent--
    line("};")
    line()

    // Move range
    line("static const UINT8 _tac_move_range[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.baseMoveRange}u" })
    indent--
    line("};")
    line()

    // Facing
    line("static const UINT8 _tac_facing[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { if (it.facingMatters) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Bonuses
    line("static const UINT8 _tac_flank_bonus[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.flankingBonus}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _tac_height_bonus[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "${it.heightBonus}u" })
    indent--
    line("};")
    line()

    // Turn order
    line("static const UINT8 _tac_turn_order[TACTICAL_COUNT] = {")
    indent++
    line(engines.joinToString(", ") { "TURN_ORDER_${it.turnOrder.name}" })
    indent--
    line("};")
    line()

    // Engine index mapping
    val allEngines = game.battleEngines
    line("static const INT8 _engine_to_tac_idx[ENGINE_COUNT] = {")
    indent++
    var tacIdx = 0
    line(
        allEngines.joinToString(", ") { engine ->
            if (engine is TacticalBattleEngine) "${tacIdx++}" else "-1"
        }
    )
    indent--
    line("};")
    line()
}

/** Generate combat state variables. */
private fun GBDKCodeGenerator.generateCombatStateVariables(engines: List<BattleEngine>) {
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// COMBAT STATE VARIABLES")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    val maxParty = engines.maxOfOrNull { it.maxPartySize } ?: 4
    val maxEnemies = engines.maxOfOrNull { it.maxEnemies } ?: 4

    line("// Maximum combatant slots")
    line("#define MAX_PARTY_SIZE ${maxParty}u")
    line("#define MAX_ENEMY_SIZE ${maxEnemies}u")
    line()

    line("// Current engine state")
    line("static UINT8 _current_engine = 0u;")
    line("static UINT8 _combat_active = 0u;")
    line("static UINT8 _combat_outcome = OUTCOME_VICTORY;")
    line()

    line("// Turn-based state")
    line("static UINT8 _current_turn = 0u;")
    line("static UINT8 _turn_phase = 0u;")
    line("static UINT8 _current_actor = 0u;")
    line()

    line("// ATB state")
    line("static UINT16 _atb_gauge[MAX_PARTY_SIZE + MAX_ENEMY_SIZE];")
    line("static UINT8 _atb_ready[MAX_PARTY_SIZE + MAX_ENEMY_SIZE];")
    line()

    line("// Real-time state")
    line("static UINT8 _rt_hitstun_timer = 0u;")
    line("static UINT8 _rt_iframes_timer = 0u;")
    line("static UINT8 _rt_blocking = 0u;")
    line()

    line("// Tactical state")
    line("static UINT8 _tac_selected_unit = 0u;")
    line("static UINT8 _tac_unit_moved = 0u;")
    line("static UINT8 _tac_unit_acted = 0u;")
    line()

    // Initialize function
    line("// Initialize combat state")
    line("static void _init_combat(UINT8 engine_idx) {")
    indent++
    line("UINT8 i;")
    line("if (engine_idx >= ENGINE_COUNT) return;")
    line()
    line("_current_engine = engine_idx;")
    line("_combat_active = 1u;")
    line("_combat_outcome = OUTCOME_VICTORY;")
    line("_current_turn = 0u;")
    line("_turn_phase = 0u;")
    line("_current_actor = 0u;")
    line()
    line("// Reset ATB gauges")
    line("for (i = 0u; i < MAX_PARTY_SIZE + MAX_ENEMY_SIZE; i++) {")
    indent++
    line("_atb_gauge[i] = 0u;")
    line("_atb_ready[i] = 0u;")
    indent--
    line("}")
    line()
    line("// Reset real-time state")
    line("_rt_hitstun_timer = 0u;")
    line("_rt_iframes_timer = 0u;")
    line("_rt_blocking = 0u;")
    line()
    line("// Reset tactical state")
    line("_tac_selected_unit = 0u;")
    line("_tac_unit_moved = 0u;")
    line("_tac_unit_acted = 0u;")
    indent--
    line("}")
    line()
}

/** Generate turn-based update function. */
private fun GBDKCodeGenerator.generateTurnBasedUpdate(engines: List<TurnBasedBattleEngine>) {
    if (engines.isEmpty()) return

    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// TURN-BASED ENGINE UPDATE")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Process turn-based combat (call each frame)")
    line("static void _update_turn_based(UINT8 engine_idx) {")
    indent++
    line("INT8 tb_idx = _engine_to_turn_idx[engine_idx];")
    line("if (tb_idx < 0) return;")
    line()
    line("// Turn-based state machine would go here")
    line("// For now, basic structure:")
    line()
    line("// Phase 0: Turn start")
    line("// Phase 1: Player action selection")
    line("// Phase 2: Enemy AI")
    line("// Phase 3: Execute actions")
    line("// Phase 4: Check victory/defeat")
    line()
    line("(void)tb_idx; // Use in full implementation")
    indent--
    line("}")
    line()

    line("// Attempt to flee")
    line("static UINT8 _try_flee(UINT8 engine_idx, UINT8 party_agility, UINT8 enemy_agility) {")
    indent++
    line("INT8 tb_idx = _engine_to_turn_idx[engine_idx];")
    line("if (tb_idx < 0) return 0u;")
    line("if (!_flee_allowed[(UINT8)tb_idx]) return 0u;")
    line()
    line("// Calculate flee chance")
    line("INT16 chance = _flee_base_chance[(UINT8)tb_idx];")
    line("chance += (INT16)party_agility * _flee_per_agi[(UINT8)tb_idx];")
    line("chance -= (INT16)enemy_agility;")
    line()
    line("if (chance < 0) chance = 0;")
    line("if (chance > 100) chance = 100;")
    line()
    line("UINT8 roll = _rand() % 100u;")
    line("return (roll < (UINT8)chance) ? 1u : 0u;")
    indent--
    line("}")
    line()
}

/** Generate active time update function. */
private fun GBDKCodeGenerator.generateActiveTimeUpdate(engines: List<ActiveTimeBattleEngine>) {
    if (engines.isEmpty()) return

    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// ACTIVE TIME ENGINE UPDATE")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Update ATB gauges (call each frame)")
    line("static void _update_atb(UINT8 engine_idx, UINT8 menu_open, UINT8 anim_playing) {")
    indent++
    line("INT8 atb_idx = _engine_to_atb_idx[engine_idx];")
    line("if (atb_idx < 0) return;")
    line()
    line("// Check pause conditions")
    line("if (menu_open && _atb_pause_menu[(UINT8)atb_idx]) return;")
    line("if (anim_playing && _atb_pause_anim[(UINT8)atb_idx]) return;")
    line()
    line("UINT8 fill_rate = _atb_fill_rate[(UINT8)atb_idx];")
    line("UINT8 speed_mult = _atb_speed_mult[(UINT8)atb_idx];")
    line("UINT8 i;")
    line()
    line("// Update each combatant's gauge")
    line("for (i = 0u; i < MAX_PARTY_SIZE + MAX_ENEMY_SIZE; i++) {")
    indent++
    line("if (_atb_ready[i]) continue; // Already full")
    line()
    line("// Get combatant's agility for ATB fill calculation")
    line("UINT8 speed = _combatant_agl[i];")
    line()
    line("UINT16 fill_amount = fill_rate + (UINT16)speed * speed_mult / 10u;")
    line("_atb_gauge[i] += fill_amount;")
    line()
    line("// Check if gauge is full (255 = full)")
    line("if (_atb_gauge[i] >= 255u) {")
    indent++
    line("_atb_gauge[i] = 255u;")
    line("_atb_ready[i] = 1u;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Get next ready combatant (-1 if none)")
    line("static INT8 _atb_get_ready(void) {")
    indent++
    line("UINT8 i;")
    line("for (i = 0u; i < MAX_PARTY_SIZE + MAX_ENEMY_SIZE; i++) {")
    indent++
    line("if (_atb_ready[i]) return (INT8)i;")
    indent--
    line("}")
    line("return -1;")
    indent--
    line("}")
    line()

    line("// Consume a combatant's ATB gauge")
    line("static void _atb_consume(UINT8 combatant_idx) {")
    indent++
    line("if (combatant_idx < MAX_PARTY_SIZE + MAX_ENEMY_SIZE) {")
    indent++
    line("_atb_gauge[combatant_idx] = 0u;")
    line("_atb_ready[combatant_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate real-time update function. */
private fun GBDKCodeGenerator.generateRealTimeUpdate(engines: List<RealTimeBattleEngine>) {
    if (engines.isEmpty()) return

    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// REAL-TIME ENGINE UPDATE")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Update real-time combat (call each frame)")
    line("static void _update_realtime(UINT8 engine_idx) {")
    indent++
    line("INT8 rt_idx = _engine_to_rt_idx[engine_idx];")
    line("if (rt_idx < 0) return;")
    line()
    line("// Count down timers")
    line("if (_rt_hitstun_timer > 0u) _rt_hitstun_timer--;")
    line("if (_rt_iframes_timer > 0u) _rt_iframes_timer--;")
    indent--
    line("}")
    line()

    line("// Apply damage to player in real-time combat")
    line("static UINT16 _rt_take_damage(UINT8 engine_idx, UINT16 damage) {")
    indent++
    line("INT8 rt_idx = _engine_to_rt_idx[engine_idx];")
    line("if (rt_idx < 0) return damage;")
    line()
    line("// Check i-frames")
    line("if (_rt_iframes_timer > 0u) return 0u;")
    line()
    line("// Check blocking")
    line("if (_rt_blocking && _rt_block_allowed[(UINT8)rt_idx]) {")
    indent++
    line("damage = damage * (100u - _rt_block_reduction[(UINT8)rt_idx]) / 100u;")
    indent--
    line("}")
    line()
    line("// Apply hit stun and i-frames")
    line("_rt_hitstun_timer = _rt_hitstun[(UINT8)rt_idx];")
    line("_rt_iframes_timer = _rt_iframes[(UINT8)rt_idx];")
    line()
    line("return damage;")
    indent--
    line("}")
    line()

    line("// Check if player can act (not in hit stun)")
    line("static UINT8 _rt_can_act(void) {")
    indent++
    line("return (_rt_hitstun_timer == 0u) ? 1u : 0u;")
    indent--
    line("}")
    line()

    line("// Check if player is invincible")
    line("static UINT8 _rt_is_invincible(void) {")
    indent++
    line("return (_rt_iframes_timer > 0u) ? 1u : 0u;")
    indent--
    line("}")
    line()

    line("// Set blocking state")
    line("static void _rt_set_blocking(UINT8 blocking) {")
    indent++
    line("_rt_blocking = blocking;")
    indent--
    line("}")
    line()
}

/** Generate tactical update function. */
private fun GBDKCodeGenerator.generateTacticalUpdate(engines: List<TacticalBattleEngine>) {
    if (engines.isEmpty()) return

    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// TACTICAL ENGINE UPDATE")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Calculate distance between two grid positions")
    line("static UINT8 _tac_distance(UINT8 x1, UINT8 y1, UINT8 x2, UINT8 y2) {")
    indent++
    line("UINT8 dx = (x1 > x2) ? (x1 - x2) : (x2 - x1);")
    line("UINT8 dy = (y1 > y2) ? (y1 - y2) : (y2 - y1);")
    line("return dx + dy; // Manhattan distance")
    indent--
    line("}")
    line()

    line("// Check if position is valid on grid")
    line("static UINT8 _tac_valid_pos(UINT8 engine_idx, UINT8 x, UINT8 y) {")
    indent++
    line("INT8 tac_idx = _engine_to_tac_idx[engine_idx];")
    line("if (tac_idx < 0) return 0u;")
    line("if (x >= _tac_grid_w[(UINT8)tac_idx]) return 0u;")
    line("if (y >= _tac_grid_h[(UINT8)tac_idx]) return 0u;")
    line("return 1u;")
    indent--
    line("}")
    line()

    line("// Check if unit can move to position")
    line(
        "static UINT8 _tac_can_move_to(UINT8 engine_idx, UINT8 from_x, UINT8 from_y, UINT8 to_x, UINT8 to_y) {"
    )
    indent++
    line("INT8 tac_idx = _engine_to_tac_idx[engine_idx];")
    line("if (tac_idx < 0) return 0u;")
    line("if (!_tac_valid_pos(engine_idx, to_x, to_y)) return 0u;")
    line()
    line("UINT8 distance = _tac_distance(from_x, from_y, to_x, to_y);")
    line("return (distance <= _tac_move_range[(UINT8)tac_idx]) ? 1u : 0u;")
    indent--
    line("}")
    line()

    line("// Calculate flanking bonus")
    line(
        "static UINT8 _tac_get_flank_bonus(UINT8 engine_idx, UINT8 atk_facing, UINT8 def_facing) {"
    )
    indent++
    line("INT8 tac_idx = _engine_to_tac_idx[engine_idx];")
    line("if (tac_idx < 0) return 0u;")
    line("if (!_tac_facing[(UINT8)tac_idx]) return 0u;")
    line()
    line("// Backstab check (opposite facing)")
    line("if ((atk_facing + 2) % 4 == def_facing) {")
    indent++
    line("return _tac_flank_bonus[(UINT8)tac_idx];")
    indent--
    line("}")
    line("// Side attack (90 degrees)")
    line("if (((atk_facing + 1) % 4 == def_facing) || ((atk_facing + 3) % 4 == def_facing)) {")
    indent++
    line("return _tac_flank_bonus[(UINT8)tac_idx] / 2u;")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()

    line("// End turn for selected unit")
    line("static void _tac_end_unit_turn(void) {")
    indent++
    line("_tac_unit_moved = 0u;")
    line("_tac_unit_acted = 0u;")
    indent--
    line("}")
    line()
}

/** Generate combat dispatch function. */
@Suppress("UNUSED_PARAMETER") // engines reserved for future multi-engine routing
private fun GBDKCodeGenerator.generateCombatDispatchFunction(engines: List<BattleEngine>) {
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// COMBAT DISPATCH")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Update combat (routes to appropriate engine)")
    line("static void _update_combat(UINT8 menu_open, UINT8 anim_playing) {")
    indent++
    line("if (!_combat_active) return;")
    line()
    line("switch (_engine_type[_current_engine]) {")
    indent++
    line("case COMBAT_TYPE_TURN_BASED:")
    indent++
    line("_update_turn_based(_current_engine);")
    line("break;")
    indent--
    line("case COMBAT_TYPE_ACTIVE_TIME:")
    indent++
    line("_update_atb(_current_engine, menu_open, anim_playing);")
    line("break;")
    indent--
    line("case COMBAT_TYPE_REAL_TIME:")
    indent++
    line("_update_realtime(_current_engine);")
    line("break;")
    indent--
    line("case COMBAT_TYPE_TACTICAL:")
    indent++
    line("// Tactical is event-driven, minimal per-frame update")
    line("break;")
    indent--
    line("default:")
    indent++
    line("break;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// End combat with outcome")
    line("static void _end_combat(UINT8 outcome) {")
    indent++
    line("_combat_outcome = outcome;")
    line("_combat_active = 0u;")
    line()
    line("// Dispatch outcome callback")
    line("switch (outcome) {")
    indent++
    line("case OUTCOME_VICTORY:")
    indent++
    line("_engine_on_victory(_current_engine);")
    line("break;")
    indent--
    line("case OUTCOME_DEFEAT:")
    indent++
    line("_engine_on_defeat(_current_engine);")
    line("break;")
    indent--
    line("case OUTCOME_FLED:")
    indent++
    line("_engine_on_flee(_current_engine);")
    line("break;")
    indent--
    line("default:")
    indent++
    line("break;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate combat callbacks. */
private fun GBDKCodeGenerator.generateCombatCallbacks(engines: List<BattleEngine>) {
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// COMBAT CALLBACKS")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    // Generate individual callbacks
    for (engine in engines) {
        if (engine.onVictoryStatements.isNotEmpty()) {
            line("static void _${engine.id}_on_victory(void) {")
            indent++
            generateStatementList(engine.onVictoryStatements)
            indent--
            line("}")
            line()
        }

        if (engine.onDefeatStatements.isNotEmpty()) {
            line("static void _${engine.id}_on_defeat(void) {")
            indent++
            generateStatementList(engine.onDefeatStatements)
            indent--
            line("}")
            line()
        }

        // Type-specific callbacks
        if (engine is TurnBasedBattleEngine && engine.onFleeStatements.isNotEmpty()) {
            line("static void _${engine.id}_on_flee(void) {")
            indent++
            generateStatementList(engine.onFleeStatements)
            indent--
            line("}")
            line()
        }

        if (engine is ActiveTimeBattleEngine && engine.onGaugeFullStatements.isNotEmpty()) {
            line("static void _${engine.id}_on_gauge_full(void) {")
            indent++
            generateStatementList(engine.onGaugeFullStatements)
            indent--
            line("}")
            line()
        }

        if (engine is RealTimeBattleEngine) {
            if (engine.onHitStatements.isNotEmpty()) {
                line("static void _${engine.id}_on_hit(void) {")
                indent++
                generateStatementList(engine.onHitStatements)
                indent--
                line("}")
                line()
            }
            if (engine.onBlockStatements.isNotEmpty()) {
                line("static void _${engine.id}_on_block(void) {")
                indent++
                generateStatementList(engine.onBlockStatements)
                indent--
                line("}")
                line()
            }
        }
    }

    // Generate dispatchers
    line("// Dispatch victory callback")
    line("static void _engine_on_victory(UINT8 engine_idx) {")
    indent++
    line("switch (engine_idx) {")
    indent++
    for (engine in engines) {
        if (engine.onVictoryStatements.isNotEmpty()) {
            line("case ENGINE_${engine.id.uppercase()}: _${engine.id}_on_victory(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Dispatch defeat callback")
    line("static void _engine_on_defeat(UINT8 engine_idx) {")
    indent++
    line("switch (engine_idx) {")
    indent++
    for (engine in engines) {
        if (engine.onDefeatStatements.isNotEmpty()) {
            line("case ENGINE_${engine.id.uppercase()}: _${engine.id}_on_defeat(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Dispatch flee callback")
    line("static void _engine_on_flee(UINT8 engine_idx) {")
    indent++
    line("switch (engine_idx) {")
    indent++
    for (engine in engines) {
        if (engine is TurnBasedBattleEngine && engine.onFleeStatements.isNotEmpty()) {
            line("case ENGINE_${engine.id.uppercase()}: _${engine.id}_on_flee(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate combat helper functions. */
@Suppress("UNUSED_PARAMETER") // engines reserved for future multi-engine configuration
private fun GBDKCodeGenerator.generateCombatHelperFunctions(engines: List<BattleEngine>) {
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line("// COMBAT HELPER FUNCTIONS")
    line(GBDKCodeGenerator.SECTION_SEPARATOR)
    line()

    line("// Start combat with specified engine")
    line("static void _start_combat(UINT8 engine_idx) {")
    indent++
    line("if (engine_idx >= ENGINE_COUNT) return;")
    line("_init_combat(engine_idx);")
    indent--
    line("}")
    line()

    line("// Check if combat is active")
    line("static UINT8 _is_combat_active(void) {")
    indent++
    line("return _combat_active;")
    indent--
    line("}")
    line()

    line("// Get current combat outcome")
    line("static UINT8 _get_combat_outcome(void) {")
    indent++
    line("return _combat_outcome;")
    indent--
    line("}")
    line()

    line("// Get current engine type")
    line("static UINT8 _get_combat_type(void) {")
    indent++
    line("if (_current_engine >= ENGINE_COUNT) return 0u;")
    line("return _engine_type[_current_engine];")
    indent--
    line("}")
    line()

    line("// Check if current combat allows fleeing")
    line("static UINT8 _can_flee(void) {")
    indent++
    line("INT8 tb_idx = _engine_to_turn_idx[_current_engine];")
    line("if (tb_idx < 0) return 0u;")
    line("return _flee_allowed[(UINT8)tb_idx];")
    indent--
    line("}")
    line()

    line("// Get max party size for current engine")
    line("static UINT8 _get_max_party(void) {")
    indent++
    line("if (_current_engine >= ENGINE_COUNT) return 4u;")
    line("return _engine_max_party[_current_engine];")
    indent--
    line("}")
    line()

    line("// Get max enemies for current engine")
    line("static UINT8 _get_max_enemies(void) {")
    indent++
    line("if (_current_engine >= ENGINE_COUNT) return 4u;")
    line("return _engine_max_enemies[_current_engine];")
    indent--
    line("}")
    line()
}
