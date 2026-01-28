/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateExpr
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRAllEnemiesDefeated
import io.github.gbkt.core.ir.IRAllPartyDefeated
import io.github.gbkt.core.ir.IRBattleAction
import io.github.gbkt.core.ir.IRBattleEnd
import io.github.gbkt.core.ir.IRBattleMessage
import io.github.gbkt.core.ir.IRBattleStart
import io.github.gbkt.core.ir.IRBattleStateTransition
import io.github.gbkt.core.ir.IRBattleSystem
import io.github.gbkt.core.ir.IRBattleUpdate
import io.github.gbkt.core.ir.IRCombatBattleTransition
import io.github.gbkt.core.ir.IRCombatItemSelected
import io.github.gbkt.core.ir.IRCombatTargetConfirmed
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetAliveEnemyCount
import io.github.gbkt.core.ir.IRGetAlivePartyCount
import io.github.gbkt.core.ir.IRGetBattleState
import io.github.gbkt.core.ir.IRGetCurrentActor
import io.github.gbkt.core.ir.IRGetEnemyCount
import io.github.gbkt.core.ir.IRGetTurnNumber
import io.github.gbkt.core.ir.IRInitBattleFromEncounter
import io.github.gbkt.core.ir.IRInitBattleWithMonsters
import io.github.gbkt.core.ir.IRInitPartyFromClass
import io.github.gbkt.core.ir.IRIsBattleState
import io.github.gbkt.core.ir.IRIsInBattle
import io.github.gbkt.core.ir.IRShowDamageNumber
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.BattleActionType
import io.github.gbkt.core.rpg.BattleState
import kotlin.collections.iterator

// =============================================================================
// BATTLE SYSTEM CODE GENERATION
// =============================================================================

/**
 * Generate all battle systems at file scope.
 *
 * Battle systems must be generated at file scope (not inside functions) because they define
 * constants, variables, and helper functions used throughout the combat code.
 *
 * This function is called from GBDKCodeGenerator.generate() before scene functions are generated.
 */
internal fun GBDKCodeGenerator.generateBattleSystems() {
    for (battleSystem in game.battleSystems) {
        generateBattleSystemFromConfig(battleSystem)
    }
}

/** Generate a battle system from a BattleSystem configuration. */
private fun GBDKCodeGenerator.generateBattleSystemFromConfig(
    system: io.github.gbkt.core.rpg.BattleSystem
) {
    // Convert BattleSystem to IRBattleSystem and use existing generation code
    val irSystem =
        IRBattleSystem(
            name = system.name,
            maxPartySize = system.maxPartySize,
            maxEnemies = system.maxEnemies,
            stateCallbacks = system.stateCallbacks.map { (state, stmts) -> state to stmts }.toMap(),
            onVictory = system.onVictory,
            onDefeat = system.onDefeat,
            onFlee = system.onFlee,
            fleeChanceBase = system.fleeChanceBase,
            fleeChancePerAgility = system.fleeChancePerAgility,
            presentation = system.presentation,
        )
    generateBattleSystemDefinition(irSystem)
}

/** Get all battle states (built-in + custom). */
private fun GBDKCodeGenerator.getAllBattleStates(): List<BattleState> {
    return BattleState.BUILT_IN_STATES + game.customBattleStates
}

/** Convert a battle state name to a C-safe identifier. */
private fun stateToCName(state: BattleState): String =
    state.name.uppercase().replace(" ", "_").replace("-", "_")

/**
 * Handle battle system IR statements.
 *
 * @return true if this was a battle statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateBattleStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRBattleSystem -> {
            // Skip - battle systems are now generated at file scope via generateBattleSystems()
            // This case handles legacy code that might emit IRBattleSystem as a statement
            true
        }
        is IRBattleStart -> {
            generateBattleStart(stmt)
            true
        }
        is IRBattleEnd -> {
            generateBattleEnd(stmt)
            true
        }
        is IRBattleStateTransition -> {
            generateBattleTransition(stmt)
            true
        }
        is IRBattleAction -> {
            generateBattleAction(stmt)
            true
        }
        is IRBattleMessage,
        is IRShowDamageNumber -> {
            // Delegate to presentation codegen
            generateBattlePresentationStatement(stmt)
        }
        // Battle initialization statements
        is IRInitPartyFromClass -> {
            line("_party_init_from_class(${generateExpr(stmt.classIdExpr)});")
            true
        }
        is IRInitBattleFromEncounter -> {
            line("_battle_init_from_encounter();")
            true
        }
        is IRInitBattleWithMonsters -> {
            // Generate inline code to initialize battle with specific monsters
            val monsterIndices =
                stmt.monsterNames.mapNotNull { monsterId ->
                    game.monsters.indexOfFirst { it.id == monsterId }.takeIf { it >= 0 }
                }
            if (monsterIndices.isNotEmpty()) {
                line("// Initialize boss encounter")
                monsterIndices.forEachIndexed { slot, monsterIdx ->
                    line("// Slot $slot: ${stmt.monsterNames.getOrNull(slot) ?: "unknown"}")
                    line("_enemy_monster_id[$slot] = ${monsterIdx}u;")
                    line("_combatant_alive[MAX_PARTY_SIZE + $slot] = 1u;")
                    line("_combatant_hp[MAX_PARTY_SIZE + $slot] = _monster_base_hp[$monsterIdx];")
                    line(
                        "_combatant_hp_max[MAX_PARTY_SIZE + $slot] = _monster_base_hp[$monsterIdx];"
                    )
                }
                line("_enemy_count = ${monsterIndices.size}u;")
            }
            true
        }
        // Combat action statements
        is IRCombatTargetConfirmed -> {
            line("_combat_target_confirmed(${generateExpr(stmt.targetIndexExpr)});")
            true
        }
        is IRCombatItemSelected -> {
            line("_combat_item_selected(${generateExpr(stmt.itemIndexExpr)});")
            true
        }
        is IRCombatBattleTransition -> {
            line("_combat_battle_transition(${stmt.targetState});")
            true
        }
        is IRBattleUpdate -> {
            generateBattleUpdate(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for battle queries.
 *
 * @return the C expression string, or null if not a battle expression
 */
internal fun GBDKCodeGenerator.generateBattleExpr(expr: IRExpression): String? =
    when (expr) {
        is IRGetBattleState -> "_${expr.systemName}_battle_state"
        is IRIsInBattle -> "(_${expr.systemName}_battle_active)"
        is IRIsBattleState -> "(_${expr.systemName}_battle_state == ${expr.state.id}u)"
        is IRGetTurnNumber -> "_${expr.systemName}_turn_number"
        is IRGetCurrentActor -> "_${expr.systemName}_current_actor"
        is IRGetEnemyCount -> "_${expr.systemName}_enemy_count"
        is IRGetAliveEnemyCount -> "_${expr.systemName}_alive_enemies()"
        is IRGetAlivePartyCount -> "_${expr.systemName}_alive_party()"
        is IRAllEnemiesDefeated -> "(_${expr.systemName}_alive_enemies() == 0u)"
        is IRAllPartyDefeated -> "(_${expr.systemName}_alive_party() == 0u)"
        else -> null
    }

/** Generate complete battle system definition. */
private fun GBDKCodeGenerator.generateBattleSystemDefinition(system: IRBattleSystem) {
    val name = system.name

    line("// =============================================================================")
    line("// BATTLE SYSTEM: $name")
    line("// =============================================================================")
    line()

    // Generate state constants
    generateBattleStateConstants(name)

    // Generate action type constants
    generateActionTypeConstants(name)

    // Generate battle variables
    generateBattleVariables(name, system)

    // Collect banked function signatures for forward declarations
    val bankedSignatures = mutableListOf<String>()
    bankedSignatures.add("void _${name}_init_combatants(void)")
    bankedSignatures.add("void _${name}_execute_action(void)")
    bankedSignatures.add("void _${name}_tick_status_effects(void)")
    bankedSignatures.add("void _${name}_battle_update(void)")
    if (system.onVictory.isNotEmpty()) {
        bankedSignatures.add("void _${name}_on_victory(void)")
    }
    if (system.onDefeat.isNotEmpty()) {
        bankedSignatures.add("void _${name}_on_defeat(void)")
    }
    if (system.onFlee.isNotEmpty()) {
        bankedSignatures.add("void _${name}_on_flee(void)")
    }

    // Generate forward declarations for banked functions
    line("// =============================================================================")
    line("// FORWARD DECLARATIONS FOR BANKED BATTLE FUNCTIONS")
    line("// =============================================================================")
    line()
    for (sig in bankedSignatures) {
        line("$sig BANKED;")
    }
    line()

    // Generate helper functions (stay in bank 0 - small and frequently called)
    generateBattleHelpers(name, system)

    // Generate transition function (stays in bank 0 - small and frequently called)
    generateBattleTransitionFunction(name, system)

    // Generate banked functions in codeBankBattle
    // Init combatants - called once per battle
    setBank(codeBankBattle)
    generateCombatantInitFunctionBanked(name, system)

    // Execute action - called once per action (largest function)
    setBank(codeBankBattle)
    generateActionExecutionFunctionBanked(name, system)

    // Status effect tick - called once per turn end
    setBank(codeBankBattle)
    generateStatusEffectTickFunctionBanked(name, system)

    // State callbacks - called once on victory/defeat/flee
    setBank(codeBankBattle)
    generateBattleStateCallbacksBanked(name, system)

    // Return to bank 0 for small frequently called functions
    returnToHome()

    // Generate player action callback functions (small, stay in bank 0)
    generatePlayerActionCallbacks(name, system)

    // Generate update function in battle bank (large state machine, called from battle_frame)
    setBank(codeBankBattle)
    generateBattleUpdateFunction(name, system)

    // Return to bank 0 for presentation system (frequently used display functions)
    returnToHome()

    // Generate presentation system (damage numbers, messages, effects)
    generateBattlePresentationSystem(system)
}

/** Generate battle state constants. */
private fun GBDKCodeGenerator.generateBattleStateConstants(name: String) {
    val allStates = getAllBattleStates()
    line(
        "// Battle state constants (${BattleState.BUILT_IN_STATES.size} built-in + ${game.customBattleStates.size} custom)"
    )
    for (state in allStates) {
        val safeName = stateToCName(state)
        line("#define ${name.uppercase()}_STATE_$safeName ${state.id}u")
    }
    line("#define ${name.uppercase()}_STATE_COUNT ${allStates.size}u")
    line()
}

/** Generate action type constants. */
private fun GBDKCodeGenerator.generateActionTypeConstants(name: String) {
    line("// Action type constants")
    for ((index, action) in BattleActionType.entries.withIndex()) {
        line("#define ${name.uppercase()}_ACTION_${action.name} ${index}u")
    }
    line()
}

/** Generate battle state variables. */
private fun GBDKCodeGenerator.generateBattleVariables(name: String, system: IRBattleSystem) {
    line("// Battle state variables")
    line("static UINT8 _${name}_battle_active = 0u;")
    line("static UINT8 _${name}_battle_state = 0u;")
    line("static UINT8 _${name}_prev_state = 0u;")
    line("static UINT8 _${name}_turn_number = 0u;")
    line("static UINT8 _${name}_current_actor = 0u;")
    line()

    line("// Party slots (indices into character array)")
    line("static UINT8 _${name}_party[${system.maxPartySize}] = {0};")
    line("static UINT8 _${name}_party_count = 0u;")
    line()

    line("// Enemy slots (indices into enemy array)")
    line("static UINT8 _${name}_enemies[${system.maxEnemies}] = {0};")
    line("static UINT8 _${name}_enemy_count = 0u;")
    line()

    line("// Turn order (indices into combined party+enemy)")
    val maxCombatants = system.maxPartySize + system.maxEnemies
    line("static UINT8 _${name}_turn_order[$maxCombatants] = {0};")
    line("static UINT8 _${name}_turn_order_count = 0u;")
    line("static UINT8 _${name}_turn_index = 0u;")
    line()

    line("// Current action")
    line("static UINT8 _${name}_action_type = 0u;")
    line("static UINT8 _${name}_action_actor = 0u;")
    line("static UINT8 _${name}_action_target = 0u;")
    line("static UINT8 _${name}_action_ability = 0u;")
    line("static UINT8 _${name}_action_item = 0u;")
    line()

    line("// Flee mechanics")
    line("static UINT8 _${name}_flee_base = ${system.fleeChanceBase}u;")
    line("static UINT8 _${name}_flee_per_agi = ${system.fleeChancePerAgility}u;")
    line()

    // Note: Combatant arrays (_combatant_hp, _combatant_atk, etc.) and
    // _battle_enemy_types[] are now generated by CombatCoreCodegen as global
    // arrays shared across battle systems
}

// Note: STAT_* constants are now generated by CombatCoreCodegen

/** Generate combatant stat accessor functions. */
@Suppress("UNUSED_PARAMETER") // system reserved for future stat schema configuration
private fun GBDKCodeGenerator.generateCombatantStatAccessors(name: String, system: IRBattleSystem) {
    line("// Get stat value from combatant array")
    line("static UINT16 _${name}_get_stat(UINT8 idx, UINT8 stat_type) {")
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

    line("// Get max stat value from combatant array")
    line("static UINT16 _${name}_get_stat_max(UINT8 idx, UINT8 stat_type) {")
    indent++
    line("switch (stat_type) {")
    indent++
    line("case STAT_HP: return _combatant_hp_max[idx];")
    line("case STAT_SP: return (UINT16)_combatant_sp_max[idx];")
    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Convenience macros for combat formula use
    line("// Convenience function to get ATK stat (used by combat formulas)")
    line("static inline UINT8 _get_combatant_atk(UINT8 idx) { return _combatant_atk[idx]; }")
    line("static inline UINT8 _get_combatant_def(UINT8 idx) { return _combatant_def[idx]; }")
    line("static inline UINT8 _get_combatant_matk(UINT8 idx) { return _combatant_matk[idx]; }")
    line("static inline UINT8 _get_combatant_mdef(UINT8 idx) { return _combatant_mdef[idx]; }")
    line("static inline UINT8 _get_combatant_agl(UINT8 idx) { return _combatant_agl[idx]; }")
    line()
}

/** Generate combatant initialization function (banked - called once per battle start). */
private fun GBDKCodeGenerator.generateCombatantInitFunctionBanked(
    name: String,
    system: IRBattleSystem,
) {
    val charactersWithStats = game.characters.filter { it.hasStats }

    line("// Initialize combatant arrays from character/monster data at battle start")
    line("void _${name}_init_combatants(void) BANKED {")
    indent++

    // Initialize party member stats from character variables
    line("// Copy party member stats from character variables")
    if (charactersWithStats.isNotEmpty()) {
        line("for (UINT8 i = 0u; i < _${name}_party_count; i++) {")
        indent++
        line("UINT8 char_idx = _${name}_party[i];")
        line("switch (char_idx) {")
        indent++

        charactersWithStats.forEachIndexed { index, character ->
            val charName = character.name
            line("case ${index}u: // ${character.name}")
            indent++
            line("_combatant_hp[i] = ${charName}_hp;")
            line("_combatant_hp_max[i] = ${charName}_hp_max;")
            line("_combatant_sp[i] = ${charName}_sp;")
            line("_combatant_sp_max[i] = ${charName}_sp_max;")
            line("_combatant_atk[i] = ${charName}_atk;")
            line("_combatant_def[i] = ${charName}_def;")
            line("_combatant_matk[i] = ${charName}_matk;")
            line("_combatant_mdef[i] = ${charName}_mdef;")
            line("_combatant_agl[i] = ${charName}_agl;")
            line("break;")
            indent--
        }

        line("default: break;")
        indent--
        line("}")
        indent--
        line("}")
    } else {
        line("// No characters with stats defined")
    }
    line()

    // Initialize enemy stats from monster lookup tables
    line("// Copy enemy stats from monster lookup tables")
    line("for (UINT8 i = 0u; i < _${name}_enemy_count; i++) {")
    indent++
    line("UINT8 mon_type = _battle_enemy_types[i];")
    line("UINT8 idx = _${name}_party_count + i;")
    line("_combatant_hp[idx] = _monster_hp_table[mon_type];")
    line("_combatant_hp_max[idx] = _monster_hp_table[mon_type];")
    line("_combatant_sp[idx] = 0u; // Monsters typically don't use SP")
    line("_combatant_sp_max[idx] = 0u;")
    line("_combatant_atk[idx] = _monster_atk_table[mon_type];")
    line("_combatant_def[idx] = _monster_def_table[mon_type];")
    line("_combatant_matk[idx] = _monster_matk_table[mon_type];")
    line("_combatant_mdef[idx] = _monster_mdef_table[mon_type];")
    line("_combatant_agl[idx] = _monster_agl_table[mon_type];")
    indent--
    line("}")
    line()

    // Reset defending state
    line("// Reset defending state for all combatants")
    val maxCombatants = system.maxPartySize + system.maxEnemies
    line("for (UINT8 i = 0u; i < ${maxCombatants}u; i++) {")
    indent++
    line("_combatant_defending[i] = 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()
}

/** Generate helper functions. */
private fun GBDKCodeGenerator.generateBattleHelpers(name: String, system: IRBattleSystem) {
    // Note: STAT_* constants are now generated by CombatCoreCodegen

    // Combatant stat accessor functions (battle-system-specific wrappers)
    generateCombatantStatAccessors(name, system)

    // Note: generateCombatantInitFunction is now banked - called separately

    // Alive count helpers
    line("// Count alive party members")
    line("static UINT8 _${name}_alive_party(void) {")
    indent++
    line("UINT8 count = 0u;")
    line("for (UINT8 i = 0u; i < _${name}_party_count; i++) {")
    indent++
    line("// Party members are in combatant indices 0 to party_count-1")
    line("if (_combatant_hp[i] > 0u) {")
    indent++
    line("count++;")
    indent--
    line("}")
    indent--
    line("}")
    line("return count;")
    indent--
    line("}")
    line()

    line("// Count alive enemies")
    line("static UINT8 _${name}_alive_enemies(void) {")
    indent++
    line("UINT8 count = 0u;")
    line("for (UINT8 i = 0u; i < _${name}_enemy_count; i++) {")
    indent++
    line("// Enemies are in combatant indices party_count to party_count+enemy_count-1")
    line("if (_combatant_hp[_${name}_party_count + i] > 0u) {")
    indent++
    line("count++;")
    indent--
    line("}")
    indent--
    line("}")
    line("return count;")
    indent--
    line("}")
    line()

    // Generate canAct helper for current actor
    generateCurrentActorCanActHelper(name)
}

/** Generate helper function to check if the current actor can act (not stunned/tripped/etc). */
private fun GBDKCodeGenerator.generateCurrentActorCanActHelper(name: String) {
    // Get characters with stats that participate in battles
    val charactersWithStats = game.characters.filter { it.hasStats }
    if (charactersWithStats.isEmpty()) {
        // No characters with status effects to check
        line("// Check if current actor can act (no characters with stats)")
        line("static UINT8 _${name}_current_actor_can_act(void) {")
        indent++
        line("return 1u; // Always can act if no status effects")
        indent--
        line("}")
        line()
        return
    }

    line("// Check if current actor can act (not stunned/tripped/sleeping etc)")
    line("static UINT8 _${name}_current_actor_can_act(void) {")
    indent++

    line("// Map combatant index to character can_act check")
    line("UINT8 actor_idx = _${name}_current_actor;")
    line()

    // Generate switch to map actor index to character's can_act function
    line("// For party members (indices 0 to party_count-1), check character can_act")
    line("if (actor_idx < _${name}_party_count) {")
    indent++
    line("switch (actor_idx) {")
    indent++

    charactersWithStats.forEachIndexed { index, character ->
        line("case ${index}u: return _can_act_${character.name}();")
    }

    line("default: return 1u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // For enemies, use the combatant-level status effect check
    line("// Enemies (indices party_count and above)")
    line("// Use combatant-level status effect tracking")
    line("return _combatant_can_act(actor_idx);")

    indent--
    line("}")
    line()
}

/** Generate state callback functions (banked - victory/defeat/flee called once per battle). */
private fun GBDKCodeGenerator.generateBattleStateCallbacksBanked(
    name: String,
    system: IRBattleSystem,
) {
    // Victory callback - banked (called once at end of battle)
    if (system.onVictory.isNotEmpty()) {
        line("// Victory callback (banked)")
        line("void _${name}_on_victory(void) BANKED {")
        indent++
        system.onVictory.forEach { stmt -> generateStatement(stmt) }
        indent--
        line("}")
        line()
    }

    // Defeat callback - banked (called once at end of battle)
    if (system.onDefeat.isNotEmpty()) {
        line("// Defeat callback (banked)")
        line("void _${name}_on_defeat(void) BANKED {")
        indent++
        system.onDefeat.forEach { stmt -> generateStatement(stmt) }
        indent--
        line("}")
        line()
    }

    // Flee callback - banked (called once when fleeing)
    if (system.onFlee.isNotEmpty()) {
        line("// Flee callback (banked)")
        line("void _${name}_on_flee(void) BANKED {")
        indent++
        system.onFlee.forEach { stmt -> generateStatement(stmt) }
        indent--
        line("}")
        line()
    }

    // State-specific callbacks - stay in same bank as battle update function
    // (they're called from within the update function's switch statement)
    for ((state, statements) in system.stateCallbacks) {
        if (statements.isNotEmpty()) {
            val safeName = stateToCName(state)
            line("// State callback: ${state.name}")
            line("static void _${name}_state_${safeName.lowercase()}(void) {")
            indent++
            statements.forEach { stmt -> generateStatement(stmt) }
            indent--
            line("}")
            line()
        }
    }
}

/** Generate battle state transition function. */
@Suppress("UNUSED_PARAMETER") // system reserved for future transition hooks
private fun GBDKCodeGenerator.generateBattleTransitionFunction(
    name: String,
    system: IRBattleSystem,
) {
    line("// Transition to a new battle state")
    line("static void _${name}_battle_transition(UINT8 new_state) {")
    indent++

    line("if (_${name}_battle_state == new_state) return;")
    line()
    line("_${name}_prev_state = _${name}_battle_state;")
    line("_${name}_battle_state = new_state;")

    indent--
    line("}")
    line()
}

/** Generate battle update function with state machine (banked - called from battle_frame). */
private fun GBDKCodeGenerator.generateBattleUpdateFunction(name: String, system: IRBattleSystem) {
    val allStates = getAllBattleStates()

    line("// Update battle state machine (call once per frame)")
    line("void _${name}_battle_update(void) BANKED {")
    indent++

    line("if (!_${name}_battle_active) return;")
    line()

    line("switch (_${name}_battle_state) {")
    indent++

    for (state in allStates) {
        val safeName = stateToCName(state)
        line("case ${state.id}u: // ${state.name}")
        indent++

        // Call state callback if defined
        if (
            system.stateCallbacks.containsKey(state) &&
                system.stateCallbacks[state]?.isNotEmpty() == true
        ) {
            line("_${name}_state_${safeName.lowercase()}();")
        }

        // Add default state transitions for built-in states
        when {
            state == BattleState.INIT -> {
                line("// Initialize combatant stats from character/monster data")
                line("_${name}_init_combatants();")
                line("// Initialize battle, then move to INTRO")
                line("_${name}_battle_transition(${BattleState.INTRO.id}u);")
            }
            state == BattleState.INTRO -> {
                line("// Render enemy sprites at battle start")
                line("_battle_render_enemies();")
                line("// After intro, start turn")
                line("_${name}_battle_transition(${BattleState.TURN_START.id}u);")
            }
            state == BattleState.TURN_START -> {
                line("// Check combatants after turn start")
                line("_${name}_battle_transition(${BattleState.CHECK_COMBATANTS.id}u);")
            }
            state == BattleState.CHECK_COMBATANTS -> {
                line("// Check for victory/defeat")
                line("if (_${name}_alive_enemies() == 0u) {")
                indent++
                line("_${name}_battle_transition(${BattleState.VICTORY.id}u);")
                indent--
                line("} else if (_${name}_alive_party() == 0u) {")
                indent++
                line("_${name}_battle_transition(${BattleState.DEFEAT.id}u);")
                indent--
                line("} else if (!_${name}_current_actor_can_act()) {")
                indent++
                line("// Current actor cannot act (stunned/tripped/sleeping)")
                line("// Skip to next actor's turn")
                line("_${name}_turn_index++;")
                line("if (_${name}_turn_index >= _${name}_turn_order_count) {")
                indent++
                line("_${name}_turn_index = 0u;")
                line("_${name}_turn_number++;")
                indent--
                line("}")
                line("_${name}_current_actor = _${name}_turn_order[_${name}_turn_index];")
                line("_${name}_battle_transition(${BattleState.TURN_START.id}u);")
                indent--
                line("} else {")
                indent++
                line("// Determine if player or enemy turn based on current actor index")
                line("if (_${name}_current_actor < _${name}_party_count) {")
                indent++
                line("// Party member's turn - show menu")
                line("_${name}_battle_transition(${BattleState.PLAYER_MENU.id}u);")
                indent--
                line("} else {")
                indent++
                line("// Enemy's turn - AI decides action")
                line("_${name}_battle_transition(${BattleState.ENEMY_THINK.id}u);")
                indent--
                line("}")
                indent--
                line("}")
            }
            state == BattleState.PLAYER_MENU -> {
                line("// Player menu input is handled externally by menu system")
                line("// Call _${name}_player_action_selected() when player confirms action")
            }
            state == BattleState.TARGET_SELECT -> {
                line("// Target selection handled by menu system")
                line("// Call _${name}_target_confirmed() when target is selected")
            }
            state == BattleState.ABILITY_SELECT -> {
                line("// Ability selection handled by menu system")
                line("// Call _${name}_ability_selected() when ability is chosen")
            }
            state == BattleState.ITEM_SELECT -> {
                line("// Item selection handled by menu system")
                line("// Call _${name}_item_selected() when item is chosen")
            }
            state == BattleState.PLAYER_CONFIRM -> {
                line("// Player action confirmed - execute it")
                line("_${name}_battle_transition(${BattleState.ACTION_EXECUTE.id}u);")
            }
            state == BattleState.ENEMY_THINK -> {
                line("// Get current enemy slot and monster type")
                line("UINT8 enemy_slot = _${name}_current_actor - _${name}_party_count;")
                line("UINT8 monster_type = _battle_enemy_types[enemy_slot];")
                line("// Call monster AI to decide action")
                line("_call_monster_ai(monster_type, enemy_slot);")
                line("_${name}_battle_transition(${BattleState.ENEMY_DECIDE.id}u);")
            }
            state == BattleState.ENEMY_DECIDE -> {
                line("// Enemy has decided - execute action")
                line("_${name}_battle_transition(${BattleState.ACTION_EXECUTE.id}u);")
            }
            state == BattleState.ACTION_EXECUTE -> {
                line("// Execute the queued action")
                line("_${name}_execute_action();")
                line("_${name}_battle_transition(${BattleState.SHOW_RESULT.id}u);")
            }
            state == BattleState.SHOW_RESULT -> {
                line("// Show damage numbers/messages (handled by presentation system)")
                line("// Auto-advance after a delay")
                line("_${name}_battle_transition(${BattleState.APPLY_RESULT.id}u);")
            }
            state == BattleState.APPLY_RESULT -> {
                line("// Results have been applied, check for deaths and advance turn")
                line("// Clear sprites for any defeated enemies")
                line("for (UINT8 _i = 0u; _i < _${name}_enemy_count; _i++) {")
                indent++
                line("UINT8 _idx = _${name}_party_count + _i;")
                line("if (_combatant_hp[_idx] == 0u) {")
                indent++
                line("_battle_clear_enemy(_i);")
                indent--
                line("}")
                indent--
                line("}")
                line("_${name}_battle_transition(${BattleState.TURN_END.id}u);")
            }
            state == BattleState.TURN_END -> {
                line("// Process end-of-turn status effects")
                line("_${name}_tick_status_effects();")
                line("// Advance to next combatant")
                line("_${name}_battle_transition(${BattleState.NEXT_TURN.id}u);")
            }
            state == BattleState.NEXT_TURN -> {
                line("// Advance to next turn")
                line("_${name}_turn_index++;")
                line("if (_${name}_turn_index >= _${name}_turn_order_count) {")
                indent++
                line("_${name}_turn_index = 0u;")
                line("_${name}_turn_number++;")
                indent--
                line("}")
                line("_${name}_current_actor = _${name}_turn_order[_${name}_turn_index];")
                line("_${name}_battle_transition(${BattleState.TURN_START.id}u);")
            }
            state == BattleState.VICTORY -> {
                if (system.onVictory.isNotEmpty()) {
                    line("_${name}_on_victory();")
                }
                line("// Clear enemy sprites before ending battle")
                line("_battle_clear_all_enemies();")
                line("_${name}_battle_active = 0u;")
            }
            state == BattleState.DEFEAT -> {
                if (system.onDefeat.isNotEmpty()) {
                    line("_${name}_on_defeat();")
                }
                line("// Clear enemy sprites before ending battle")
                line("_battle_clear_all_enemies();")
                line("_${name}_battle_active = 0u;")
            }
            state == BattleState.FLED -> {
                if (system.onFlee.isNotEmpty()) {
                    line("_${name}_on_flee();")
                }
                line("// Clear enemy sprites before ending battle")
                line("_battle_clear_all_enemies();")
                line("_${name}_battle_active = 0u;")
            }
            !state.isBuiltIn -> {
                line("// Custom state: transition handled by callback")
            }
            else -> {
                line("// State logic handled by callback")
            }
        }

        line("break;")
        indent--
    }

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

/** Generate battle start code. */
private fun GBDKCodeGenerator.generateBattleStart(stmt: IRBattleStart) {
    val name = stmt.systemName

    lineWithSource("// Start battle", stmt.sourceLocation, name)
    line("_${name}_battle_active = 1u;")
    line("_${name}_battle_state = ${BattleState.INIT.id}u;")
    line("_${name}_turn_number = 0u;")
    line("_${name}_current_actor = 0u;")
    line("_${name}_enemy_count = ${stmt.enemyNames.size}u;")

    // Initialize enemy slots
    stmt.enemyNames.forEachIndexed { index, enemyName ->
        line("// Enemy $index: $enemyName")
        line("_${name}_enemies[$index] = $index;")
    }
}

/** Generate battle end code. */
private fun GBDKCodeGenerator.generateBattleEnd(stmt: IRBattleEnd) {
    val name = stmt.systemName
    lineWithSource("// End battle: ${stmt.result.name}", stmt.sourceLocation, name)
    line("_${name}_battle_state = ${stmt.result.id}u;")
    line("_${name}_battle_active = 0u;")
}

/** Generate battle state transition code. */
private fun GBDKCodeGenerator.generateBattleTransition(stmt: IRBattleStateTransition) {
    val name = stmt.systemName
    lineWithSource("// Battle transition to ${stmt.targetState.name}", stmt.sourceLocation, name)
    line("_${name}_battle_transition(${stmt.targetState.id}u);")
}

/** Generate battle action queue code. */
private fun GBDKCodeGenerator.generateBattleAction(stmt: IRBattleAction) {
    lineWithSource("// Queue action: ${stmt.actionType.name}", stmt.sourceLocation, stmt.actorName)
    line("_action_type = ${stmt.actionType.ordinal}u;")
    line("// Actor: ${stmt.actorName}")
    if (stmt.targetNames.isNotEmpty()) {
        line("// Targets: ${stmt.targetNames.joinToString(", ")}")
    }
    stmt.abilityId?.let { line("_action_ability = ${it}u;") }
    stmt.itemId?.let { line("_action_item = ${it}u;") }
}

/** Generate battle update call code. */
private fun GBDKCodeGenerator.generateBattleUpdate(stmt: IRBattleUpdate) {
    val name = stmt.systemName
    lineWithSource("// Update battle state machine", stmt.sourceLocation, name)
    line("_${name}_battle_update();")
}

// =============================================================================
// BATTLE ACTION EXECUTION
// =============================================================================

/** Generate battle action execution function (banked - called once per action). */
private fun GBDKCodeGenerator.generateActionExecutionFunctionBanked(
    name: String,
    system: IRBattleSystem,
) {
    line("// Execute the queued battle action")
    line("void _${name}_execute_action(void) BANKED {")
    indent++

    line("switch (_${name}_action_type) {")
    indent++

    // ATTACK action
    line("case ${name.uppercase()}_ACTION_ATTACK:")
    indent++
    line("{")
    indent++
    line("// Basic attack: calculate damage from ATK vs DEF")
    line("UINT8 actor = _${name}_action_actor;")
    line("UINT8 target = _${name}_action_target;")
    line("UINT8 atk = _combatant_atk[actor];")
    line("UINT8 def = _combatant_def[target];")
    line()
    line("// Base damage = ATK - (DEF / 2), minimum 1")
    line("UINT16 damage = (UINT16)atk;")
    line("if (damage > (UINT16)(def >> 1u)) {")
    indent++
    line("damage -= (UINT16)(def >> 1u);")
    indent--
    line("} else {")
    indent++
    line("damage = 1u;")
    indent--
    line("}")
    line()
    line("// Apply defending modifier (halve damage if defending)")
    line("if (_combatant_defending[target]) {")
    indent++
    line("damage = (damage + 1u) >> 1u;")
    indent--
    line("}")
    line()
    line("// Apply damage")
    line("if (_combatant_hp[target] > damage) {")
    indent++
    line("_combatant_hp[target] -= damage;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    // ABILITY action
    line("case ${name.uppercase()}_ACTION_ABILITY:")
    indent++
    line("{")
    indent++
    line("UINT8 actor = _${name}_action_actor;")
    line("UINT8 ability_id = _${name}_action_ability;")
    line("UINT8 target = _${name}_action_target;")
    line()
    line("// Validate and deduct cost")
    line("if (!_ability_can_afford(actor, ability_id)) {")
    indent++
    line("_show_battle_message(\"Not enough SP!\");")
    line("break;")
    indent--
    line("}")
    line("_ability_deduct_cost(actor, ability_id);")
    line()
    line("// Set up ability execution context")
    line("_ability_caster = actor;")
    line()
    line("// Populate targets based on targeting mode")
    line("UINT8 targeting = _ability_targeting_table[ability_id];")
    line("switch (targeting) {")
    indent++
    line("case 0u: // SINGLE_ENEMY")
    indent++
    line("_ability_target_count = 1u;")
    line("_ability_targets[0] = target;")
    line("break;")
    indent--
    line("case 1u: // ALL_ENEMIES")
    indent++
    line("{")
    indent++
    line("_ability_target_count = 0u;")
    line("for (UINT8 i = 0u; i < _${name}_enemy_count; i++) {")
    indent++
    line("UINT8 idx = _${name}_party_count + i;")
    line("if (_combatant_hp[idx] > 0u) {")
    indent++
    line("_ability_targets[_ability_target_count++] = idx;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--
    line("case 2u: // SINGLE_ALLY")
    indent++
    line("_ability_target_count = 1u;")
    line("_ability_targets[0] = target;")
    line("break;")
    indent--
    line("case 3u: // ALL_ALLIES")
    indent++
    line("{")
    indent++
    line("_ability_target_count = 0u;")
    line("for (UINT8 i = 0u; i < _${name}_party_count; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u) {")
    indent++
    line("_ability_targets[_ability_target_count++] = i;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--
    line("case 4u: // SELF")
    indent++
    line("_ability_target_count = 1u;")
    line("_ability_targets[0] = actor;")
    line("break;")
    indent--
    line("default:")
    indent++
    line("_ability_target_count = 1u;")
    line("_ability_targets[0] = target;")
    line("break;")
    indent--
    indent--
    line("}")
    line()
    line("// Dispatch to ability execute function")
    line("_execute_ability_dispatch(ability_id);")
    indent--
    line("}")
    line("break;")
    indent--

    // ITEM action
    line("case ${name.uppercase()}_ACTION_ITEM:")
    indent++
    line("{")
    indent++
    line("UINT8 item_id = _${name}_action_item;")
    line("UINT8 target = _${name}_action_target;")
    line()
    line("// Check inventory has item")
    line("if (!inventory_has_item(inventory_slots, INVENTORY_MAX_SLOTS, item_id, 1u)) {")
    indent++
    line("_show_battle_message(\"No item!\");")
    line("break;")
    indent--
    line("}")
    line()
    line("// Remove item from inventory")
    line("inventory_remove_item(inventory_slots, INVENTORY_MAX_SLOTS, item_id, 1u);")
    line()
    line("// Execute item effect")
    line("_execute_item_use(item_id, target);")
    indent--
    line("}")
    line("break;")
    indent--

    // DEFEND action
    line("case ${name.uppercase()}_ACTION_DEFEND:")
    indent++
    line("// Set defending flag for the actor")
    line("_combatant_defending[_${name}_action_actor] = 1u;")
    line("break;")
    indent--

    // FLEE action
    line("case ${name.uppercase()}_ACTION_FLEE:")
    indent++
    line("{")
    indent++
    line("// Calculate flee chance based on agility")
    line("UINT8 actor = _${name}_action_actor;")
    line("UINT8 agl = _combatant_agl[actor];")
    line("UINT8 chance = _${name}_flee_base + (agl * _${name}_flee_per_agi / 10u);")
    line("if (chance > 100u) chance = 100u;")
    line()
    line("// Roll for flee")
    line("if ((rand() % 100u) < chance) {")
    indent++
    line("_${name}_battle_transition(${BattleState.FLED.id}u);")
    indent--
    line("}")
    indent--
    line("}")
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

/** Generate status effect tick function (banked - called once per turn end). */
private fun GBDKCodeGenerator.generateStatusEffectTickFunctionBanked(
    name: String,
    system: IRBattleSystem,
) {
    val charactersWithStats = game.characters.filter { it.hasStats }

    line("// Tick status effects at end of turn")
    line("void _${name}_tick_status_effects(void) BANKED {")
    indent++

    if (charactersWithStats.isEmpty()) {
        line("// No characters with stats defined")
    } else {
        line("// Tick turn-based effects for all party members")
        for (character in charactersWithStats) {
            val charName = character.name
            line("// Tick effects for ${character.name}")
            line("for (UINT8 _i = 0; _i < 5u; _i++) {")
            indent++
            line("UINT8 _eff_id = ${charName}_effect_id[_i];")
            line(
                "if (_eff_id != 0u && ${charName}_effect_duration[_i] < 254u && ${charName}_effect_duration[_i] > 0u) {"
            )
            indent++
            line("// Only tick turn-based effects (not frame-based)")
            line("if (!_is_effect_frame_based(_eff_id)) {")
            indent++
            line("${charName}_effect_duration[_i]--;")
            line("if (${charName}_effect_duration[_i] == 0u) {")
            indent++
            line("${charName}_effect_id[_i] = 0u;")
            line("${charName}_effect_stacks[_i] = 0u;")
            indent--
            line("}")
            indent--
            line("}")
            indent--
            line("}")
            indent--
            line("}")
        }
    }

    line()
    line("// Clear defending status at end of turn")
    val maxCombatants = system.maxPartySize + system.maxEnemies
    line("for (UINT8 _i = 0; _i < ${maxCombatants}u; _i++) {")
    indent++
    line("_combatant_defending[_i] = 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()
}

/** Generate player action callback functions. */
@Suppress("UNUSED_PARAMETER") // system reserved for future action configuration
private fun GBDKCodeGenerator.generatePlayerActionCallbacks(name: String, system: IRBattleSystem) {
    line("// =============================================================================")
    line("// PLAYER ACTION CALLBACKS")
    line("// =============================================================================")
    line()

    // Action selected callback (from main battle menu)
    line("// Called when player selects an action from the main menu")
    line("static void _${name}_player_action_selected(UINT8 action_type) {")
    indent++
    line("_${name}_action_type = action_type;")
    line("_${name}_action_actor = _${name}_current_actor;")
    line()
    line("switch (action_type) {")
    indent++
    line("case ${name.uppercase()}_ACTION_ATTACK:")
    indent++
    line("// Attack needs target selection")
    line("_${name}_battle_transition(${BattleState.TARGET_SELECT.id}u);")
    line("break;")
    indent--
    line("case ${name.uppercase()}_ACTION_ABILITY:")
    indent++
    line("// Show ability menu")
    line("_${name}_battle_transition(${BattleState.ABILITY_SELECT.id}u);")
    line("break;")
    indent--
    line("case ${name.uppercase()}_ACTION_ITEM:")
    indent++
    line("// Show item menu")
    line("_${name}_battle_transition(${BattleState.ITEM_SELECT.id}u);")
    line("break;")
    indent--
    line("case ${name.uppercase()}_ACTION_DEFEND:")
    indent++
    line("// Defend doesn't need target")
    line("_${name}_battle_transition(${BattleState.PLAYER_CONFIRM.id}u);")
    line("break;")
    indent--
    line("case ${name.uppercase()}_ACTION_FLEE:")
    indent++
    line("// Flee doesn't need target")
    line("_${name}_battle_transition(${BattleState.PLAYER_CONFIRM.id}u);")
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

    // Target confirmed callback
    line("// Called when player selects a target")
    line("static void _${name}_target_confirmed(UINT8 target_idx) {")
    indent++
    line("_${name}_action_target = target_idx;")
    line("_${name}_battle_transition(${BattleState.PLAYER_CONFIRM.id}u);")
    indent--
    line("}")
    line()

    // Ability selected callback
    line("// Called when player selects an ability")
    line("static void _${name}_ability_selected(UINT8 ability_idx) {")
    indent++
    line("_${name}_action_ability = ability_idx;")
    line("// Most abilities need target selection")
    line("_${name}_battle_transition(${BattleState.TARGET_SELECT.id}u);")
    indent--
    line("}")
    line()

    // Item selected callback
    line("// Called when player selects an item")
    line("static void _${name}_item_selected(UINT8 item_idx) {")
    indent++
    line("_${name}_action_item = item_idx;")
    line("// Most items need target selection")
    line("_${name}_battle_transition(${BattleState.TARGET_SELECT.id}u);")
    indent--
    line("}")
    line()

    // Cancel callback (go back to previous menu)
    line("// Called when player presses B to cancel")
    line("static void _${name}_menu_cancel(void) {")
    indent++
    line("// Return to appropriate state based on current state")
    line("switch (_${name}_battle_state) {")
    indent++
    line("case ${BattleState.TARGET_SELECT.id}u:")
    line("case ${BattleState.ABILITY_SELECT.id}u:")
    line("case ${BattleState.ITEM_SELECT.id}u:")
    indent++
    line("_${name}_battle_transition(${BattleState.PLAYER_MENU.id}u);")
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
