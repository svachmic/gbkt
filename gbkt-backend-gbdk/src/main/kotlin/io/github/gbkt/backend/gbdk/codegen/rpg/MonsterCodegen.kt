/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRAIEnemyCountCheck
import io.github.gbkt.core.ir.IRAliveMonsterCount
import io.github.gbkt.core.ir.IRAllMonstersDefeated
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRHasMonsterInBattle
import io.github.gbkt.core.ir.IRMonsterAwardBonusExp
import io.github.gbkt.core.ir.IRMonsterBasicAttack
import io.github.gbkt.core.ir.IRMonsterBasicAttackExpr
import io.github.gbkt.core.ir.IRMonsterCancelHit
import io.github.gbkt.core.ir.IRMonsterDecrementEvasion
import io.github.gbkt.core.ir.IRMonsterDefend
import io.github.gbkt.core.ir.IRMonsterFlee
import io.github.gbkt.core.ir.IRMonsterGetHp
import io.github.gbkt.core.ir.IRMonsterGetHpPercent
import io.github.gbkt.core.ir.IRMonsterGetMaxHp
import io.github.gbkt.core.ir.IRMonsterHasAlly
import io.github.gbkt.core.ir.IRMonsterHasEvasion
import io.github.gbkt.core.ir.IRMonsterHasSpecialCharge
import io.github.gbkt.core.ir.IRMonsterHitWasCancelled
import io.github.gbkt.core.ir.IRMonsterHpCheck
import io.github.gbkt.core.ir.IRMonsterIsAlive
import io.github.gbkt.core.ir.IRMonsterIsDefending
import io.github.gbkt.core.ir.IRMonsterModifyHitDamage
import io.github.gbkt.core.ir.IRMonsterRevive
import io.github.gbkt.core.ir.IRMonsterSkipTurn
import io.github.gbkt.core.ir.IRMonsterTargetHasEffect
import io.github.gbkt.core.ir.IRMonsterTransform
import io.github.gbkt.core.ir.IRMonsterUseAbility
import io.github.gbkt.core.ir.IRMonsterUseAbilityExpr
import io.github.gbkt.core.ir.IRMonsterUseSpecialCharge
import io.github.gbkt.core.ir.IRMonsterWasRevived
import io.github.gbkt.core.ir.IRRandomChance
import io.github.gbkt.core.ir.IRSpawnMonster
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.Monster
import io.github.gbkt.core.rpg.MonsterVariant
import io.github.gbkt.core.world.EncounterEntry
import io.github.gbkt.core.world.EncounterMonster
import io.github.gbkt.core.world.EncounterTable

// =============================================================================
// MONSTER CODE GENERATION
// =============================================================================

/**
 * Generate monster system code.
 *
 * Creates:
 * - Monster data structures
 * - Monster stat constants
 * - Monster AI decision functions
 * - Drop table data
 */
internal fun GBDKCodeGenerator.generateMonsterSystem() {
    val monsters = game.monsters
    if (monsters.isEmpty()) return

    line("// =============================================================================")
    line("// MONSTER SYSTEM")
    line("// =============================================================================")
    line()

    // Generate monster size constants
    generateMonsterSizeConstants()

    // Generate monster tier constants
    generateMonsterTierConstants()

    // Assign monster indices first (needed for lookup tables)
    for ((index, monster) in monsters.withIndex()) {
        monster.monsterIndex = index
    }

    // Collect unique variants from encounters and assign indices
    val variants = collectVariantsFromEncounters()
    for ((index, variant) in variants.withIndex()) {
        variant.variantIndex = monsters.size + index
    }

    // Generate monster lookup tables FIRST (AI helpers depend on them)
    // Now includes variant entries after base monsters
    generateMonsterLookupTable(monsters, variants)

    // Generate AI helper functions (depend on lookup tables)
    // Note: Death/hit hook dispatches are deferred until after monster data
    generateAIHelpers()

    // Generate monster data constants (defines, stats) in bank 0
    // AI functions will be generated in monster banks below
    for (monster in monsters) {
        generateMonsterDataConstants(monster)
    }

    // Generate death and hit hook dispatches AFTER monster data
    // (dispatch tables reference monster-specific hook functions)
    generateDeathHookDispatch()
    generateHitHookDispatch()

    // Forward declarations for banked monster AI functions
    line("// Forward declarations for banked monster AI functions")
    line("void _call_monster_ai(UINT8 monster_type, UINT8 monster_slot) BANKED;")
    line()

    // Generate all monster AI functions in the same bank as the dispatch
    // This ensures cross-function calls within the switch statement work correctly
    // (non-BANKED functions can only be called from their own bank)
    val monstersWithAI = monsters.filter { it.aiStatements.isNotEmpty() }

    if (monstersWithAI.isNotEmpty()) {
        setBank(codeBankMonster)
        line("// =============================================================================")
        line("// MONSTER AI FUNCTIONS (BANK $codeBankMonster)")
        line("// =============================================================================")
        line()

        for (monster in monstersWithAI) {
            generateMonsterAIFunction(monster)
            // Generate death hook if present
            if (monster.onDeathStatements.isNotEmpty()) {
                generateMonsterDeathHook(monster)
            }
            // Generate hit hook if present
            if (monster.onHitStatements.isNotEmpty()) {
                generateMonsterHitHook(monster)
            }
        }
    }

    // Generate monster AI dispatch function in same bank (called once per enemy turn)
    generateMonsterAIDispatchBanked(variants)
    returnToHome()

    // Generate monster sprite system (background tile rendering for battles)
    generateMonsterSpriteSystem(monsters, variants)
}

/** Generate monster size constants. */
private fun GBDKCodeGenerator.generateMonsterSizeConstants() {
    line("// Monster size constants")
    line("#define MONSTER_SIZE_SMALL 0u")
    line("#define MONSTER_SIZE_MEDIUM 1u")
    line("#define MONSTER_SIZE_LARGE 2u")
    line("#define MONSTER_SIZE_BOSS 3u")
    line()
}

/** Generate monster tier constants. */
private fun GBDKCodeGenerator.generateMonsterTierConstants() {
    line("// Monster tier multipliers (percentage)")
    line("#define MONSTER_TIER_C 100u")
    line("#define MONSTER_TIER_B 125u")
    line("#define MONSTER_TIER_A 150u")
    line("#define MONSTER_TIER_S 200u")
    line()
}

/**
 * Collect unique MonsterVariants from all encounters.
 *
 * Variants are identified by their variantId and collected from:
 * - Standalone encounter tables
 * - Floor-associated encounter tables
 *
 * Only variants that differ from their base monster are collected (base variants are excluded since
 * they use the base monster's index).
 */
private fun GBDKCodeGenerator.collectVariantsFromEncounters(): List<MonsterVariant> {
    val variantMap = mutableMapOf<String, MonsterVariant>()

    // Helper to collect variants from a list of entries
    fun collectFromEntries(entries: List<EncounterEntry>) {
        for (entry in entries) {
            for (em in entry.encounterMonsters) {
                if (em is EncounterMonster.Variant && !em.variant.isBaseVariant) {
                    variantMap.putIfAbsent(em.variant.variantId, em.variant)
                }
            }
        }
    }

    // Helper to collect from a table (including level-gated entries)
    fun collectFromTable(table: EncounterTable) {
        collectFromEntries(table.entries)
        table.lowLevelEntries?.let { collectFromEntries(it) }
        table.highLevelEntries?.let { collectFromEntries(it) }
    }

    // Collect from standalone encounter tables
    for (table in game.encounterTables) {
        collectFromTable(table)
    }

    // Collect from zone-associated encounter tables
    for (zone in game.zones) {
        zone.encounterTable?.let { table -> collectFromTable(table) }
    }

    return variantMap.values.toList()
}

/**
 * Generate constants and drop table data for a single monster.
 * AI functions and hooks are generated separately in monster banks.
 */
private fun GBDKCodeGenerator.generateMonsterDataConstants(monster: Monster) {
    val monsterName = monster.id.uppercase()

    line("// -----------------------------------------------------------------------------")
    line("// Monster: ${monster.displayName} (${monster.id})")
    line("// -----------------------------------------------------------------------------")
    line()

    // Monster index constant
    line("#define MONSTER_${monsterName}_ID ${monster.monsterIndex}u")
    line()

    // Base stats (before tier scaling)
    line("// Base stats for ${monster.displayName}")
    line("#define MONSTER_${monsterName}_BASE_HP ${monster.baseStats.hp}u")
    line("#define MONSTER_${monsterName}_BASE_ATK ${monster.baseStats.atk}u")
    line("#define MONSTER_${monsterName}_BASE_DEF ${monster.baseStats.def}u")
    line("#define MONSTER_${monsterName}_BASE_MATK ${monster.baseStats.matk}u")
    line("#define MONSTER_${monsterName}_BASE_MDEF ${monster.baseStats.mdef}u")
    line("#define MONSTER_${monsterName}_BASE_AGL ${monster.baseStats.agl}u")
    line()

    // Scaled stats (after tier)
    line("// Scaled stats for ${monster.displayName} (tier ${monster.tier.name})")
    line("#define MONSTER_${monsterName}_HP ${monster.scaledHp}u")
    line("#define MONSTER_${monsterName}_ATK ${monster.scaledAtk}u")
    line("#define MONSTER_${monsterName}_DEF ${monster.scaledDef}u")
    line("#define MONSTER_${monsterName}_MATK ${monster.scaledMatk}u")
    line("#define MONSTER_${monsterName}_MDEF ${monster.scaledMdef}u")
    line("#define MONSTER_${monsterName}_AGL ${monster.scaledAgl}u")
    line()

    // Size and tier
    line("#define MONSTER_${monsterName}_SIZE MONSTER_SIZE_${monster.size.name}")
    line("#define MONSTER_${monsterName}_TIER MONSTER_TIER_${monster.tier.name.first()}")
    line()

    // Experience reward
    line("#define MONSTER_${monsterName}_EXP ${monster.expReward}u")
    line()

    // Drop table (data tables stay in bank 0 for lookup)
    if (monster.lootDrops.isNotEmpty()) {
        generateMonsterDropTable(monster)
    }
}

/** Generate drop table for a monster. */
private fun GBDKCodeGenerator.generateMonsterDropTable(monster: Monster) {
    val monsterName = monster.id

    line("// Drop table for ${monster.displayName}")
    line("#define MONSTER_${monsterName.uppercase()}_DROP_COUNT ${monster.lootDrops.size}u")
    line()

    // Generate drop data: item_id, chance, min_qty, max_qty
    line("static const UINT8 ${monsterName}_drops[${monster.lootDrops.size * 4}] = {")
    indent++
    for (drop in monster.lootDrops) {
        val itemIndex = game.items.indexOfFirst { it.id == drop.item.id }.coerceAtLeast(0)
        line(
            "${itemIndex}u, ${drop.chance}u, ${drop.minQuantity}u, ${drop.maxQuantity}u, // ${drop.item.displayName}"
        )
    }
    indent--
    line("};")
    line()
}

/** Generate AI decision function for a monster. */
private fun GBDKCodeGenerator.generateMonsterAIFunction(monster: Monster) {
    val monsterName = monster.id

    line("// AI decision function for ${monster.displayName}")
    line("static void ${monsterName}_ai(UINT8 monster_slot) {")
    indent++

    // Set up AI context variables
    line("// Set up AI context")
    line("_ai_monster_slot = monster_slot;")
    line("_ai_hp_percent = _monster_hp_percent(monster_slot);")
    line("_ai_ally_count = _alive_enemies();")
    line("_ai_enemy_count = _alive_party();")
    line()

    // Generate AI statements
    for (stmt in monster.aiStatements) {
        generateStatement(stmt)
    }

    indent--
    line("}")
    line()
}

/**
 * Generate death hook function for a monster.
 *
 * Death hooks are called when a monster's HP reaches 0 but BEFORE it is removed from battle. The
 * hook can revive the monster or transform it into a different monster type.
 *
 * @return 1 if the monster should be removed (dead), 0 if revived/transformed
 */
private fun GBDKCodeGenerator.generateMonsterDeathHook(monster: Monster) {
    val monsterName = monster.id

    line("// Death hook for ${monster.displayName}")
    line("// Returns: 0 if monster was revived/transformed (stays in battle), 1 if dead")
    line("static UINT8 ${monsterName}_on_death(UINT8 monster_slot) {")
    indent++

    // Set up death context
    line("// Set up death hook context")
    line("_death_hook_slot = monster_slot;")
    line("_death_hook_revived = 0u;")
    line()

    // Generate death hook statements
    for (stmt in monster.onDeathStatements) {
        generateStatement(stmt)
    }

    line()
    line("// Return whether monster is still dead (not revived)")
    line("return _death_hook_revived ? 0u : 1u;")

    indent--
    line("}")
    line()
}

/**
 * Generate hit hook function for a monster.
 *
 * Hit hooks are called when a monster is about to be hit but BEFORE damage is applied. The hook can
 * cancel the hit (evasion) or modify the incoming damage.
 *
 * @return the damage to apply (0 if cancelled, modified value otherwise)
 */
private fun GBDKCodeGenerator.generateMonsterHitHook(monster: Monster) {
    val monsterName = monster.id

    line("// Hit hook for ${monster.displayName}")
    line("// Returns: modified damage to apply (0 if hit was cancelled/evaded)")
    line("static UINT16 ${monsterName}_on_hit(UINT8 monster_slot, UINT16 damage) {")
    indent++

    // Set up hit context
    line("// Set up hit hook context")
    line("_hit_hook_slot = monster_slot;")
    line("_hit_hook_cancelled = 0u;")
    line("_hit_hook_damage = damage;")
    line()

    // Generate hit hook statements
    for (stmt in monster.onHitStatements) {
        generateStatement(stmt)
    }

    line()
    line("// Return 0 if cancelled, otherwise return (possibly modified) damage")
    line("return _hit_hook_cancelled ? 0u : _hit_hook_damage;")

    indent--
    line("}")
    line()
}

/** Generate monster action functions (basic attack, use ability, defend, flee). */
private fun GBDKCodeGenerator.generateMonsterActionFunctions() {
    line("// =============================================================================")
    line("// MONSTER ACTION FUNCTIONS")
    line("// =============================================================================")
    line()

    // Monster basic attack
    line("// Monster basic attack - calculates damage based on ATK vs DEF")
    line("static void _monster_basic_attack(UINT8 monster_slot, UINT8 target_idx) {")
    indent++
    line("UINT8 actor_idx = MAX_PARTY_SIZE + monster_slot;")
    line("UINT8 atk = _combatant_atk[actor_idx];")
    line("UINT8 def = _combatant_def[target_idx];")
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
    line("// Apply defending modifier (halve damage if target is defending)")
    line("if (_combatant_defending[target_idx]) {")
    indent++
    line("damage = (damage + 1u) >> 1u;")
    indent--
    line("}")
    line()
    line("// Apply damage to target")
    line("if (_combatant_hp[target_idx] > damage) {")
    indent++
    line("_combatant_hp[target_idx] -= damage;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Monster use ability
    line("// Monster use ability - executes ability on target")
    line(
        "static void _monster_use_ability(UINT8 monster_slot, UINT8 ability_idx, UINT8 target_idx) {"
    )
    indent++
    line("// Ability execution would be handled by ability system")
    line("// For now, treat as enhanced attack with 1.5x multiplier")
    line("UINT8 actor_idx = MAX_PARTY_SIZE + monster_slot;")
    line("UINT8 atk = _combatant_atk[actor_idx];")
    line("UINT16 damage = ((UINT16)atk * 150u) / 100u;")
    line("(void)ability_idx; // Would use ability data in full implementation")
    line()
    line("// Apply damage to target")
    line("if (_combatant_hp[target_idx] > damage) {")
    indent++
    line("_combatant_hp[target_idx] -= damage;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Monster defend
    line("// Monster defend - sets defending flag")
    line("static void _monster_defend(UINT8 monster_slot) {")
    indent++
    line("UINT8 actor_idx = MAX_PARTY_SIZE + monster_slot;")
    line("_combatant_defending[actor_idx] = 1u;")
    indent--
    line("}")
    line()

    // Monster flee
    line("// Monster flee - removes monster from battle")
    line("static void _monster_flee(UINT8 monster_slot) {")
    indent++
    line("UINT8 actor_idx = MAX_PARTY_SIZE + monster_slot;")
    line("// Set HP to 0 to remove from combat (without death/loot)")
    line("_combatant_hp[actor_idx] = 0u;")
    line("// Mark as fled, not killed (no EXP/loot)")
    indent--
    line("}")
    line()

    // Monster is defending check
    line("// Check if monster is currently defending")
    line("static UINT8 _monster_is_defending(UINT8 monster_slot) {")
    indent++
    line("UINT8 actor_idx = MAX_PARTY_SIZE + monster_slot;")
    line("return _combatant_defending[actor_idx];")
    indent--
    line("}")
    line()

    // Battle spawn monster
    line("// Spawn a new monster into an empty slot during battle")
    line("static void _battle_spawn_monster(UINT8 monster_type, UINT8 slot) {")
    indent++
    line("if (slot >= MAX_ENEMIES) return;")
    line("UINT8 idx = MAX_PARTY_SIZE + slot;")
    line("_battle_enemy_types[slot] = monster_type;")
    line("_combatant_hp[idx] = _monster_hp_table[monster_type];")
    line("_combatant_hp_max[idx] = _monster_hp_table[monster_type];")
    line("_combatant_atk[idx] = _monster_atk_table[monster_type];")
    line("_combatant_def[idx] = _monster_def_table[monster_type];")
    line("_combatant_matk[idx] = _monster_matk_table[monster_type];")
    line("_combatant_mdef[idx] = _monster_mdef_table[monster_type];")
    line("_combatant_agl[idx] = _monster_agl_table[monster_type];")
    line("_combatant_defending[idx] = 0u;")
    line("_monster_parameter[slot] = 0u; // Reset special ability flag")
    indent--
    line("}")
    line()

    // Battle has monster check
    line("// Check if a specific monster type is currently in battle")
    line("static UINT8 _battle_has_monster(UINT8 monster_type) {")
    indent++
    line("for (UINT8 i = 0u; i < MAX_ENEMIES; i++) {")
    indent++
    line("UINT8 idx = MAX_PARTY_SIZE + i;")
    line("if (_combatant_hp[idx] > 0u && _battle_enemy_types[i] == monster_type) {")
    indent++
    line("return 1u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()

    // Note: _rand is provided by CombatCoreCodegen

    // Alive party helper
    line("// Count alive party members")
    line("static UINT8 _alive_party(void) {")
    indent++
    line("UINT8 count = 0u;")
    line("for (UINT8 i = 0u; i < MAX_PARTY_SIZE; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u) count++;")
    indent--
    line("}")
    line("return count;")
    indent--
    line("}")
    line()

    // Alive enemies helper
    line("// Count alive enemies")
    line("static UINT8 _alive_enemies(void) {")
    indent++
    line("UINT8 count = 0u;")
    line("for (UINT8 i = 0u; i < MAX_ENEMIES; i++) {")
    indent++
    line("if (_combatant_hp[MAX_PARTY_SIZE + i] > 0u) count++;")
    indent--
    line("}")
    line("return count;")
    indent--
    line("}")
    line()
}

/**
 * Generate monster AI dispatch function (banked - called once per enemy turn).
 *
 * Variants are mapped to their base monster's AI function using the _variant_base_monster lookup
 * table.
 */
private fun GBDKCodeGenerator.generateMonsterAIDispatchBanked(variants: List<MonsterVariant>) {
    val monstersWithAI = game.monsters.filter { it.aiStatements.isNotEmpty() }

    line("// =============================================================================")
    line("// MONSTER AI DISPATCH (BANKED)")
    line("// =============================================================================")
    line()

    line("// Dispatch to monster-specific AI function")
    line("// Variants use their base monster's AI")
    line("void _call_monster_ai(UINT8 monster_type, UINT8 monster_slot) BANKED {")
    indent++

    // If there are variants, map to base monster index first
    if (variants.isNotEmpty()) {
        line("// Map variant to base monster for AI lookup")
        line(
            "UINT8 ai_type = (monster_type < MONSTER_COUNT) ? _variant_base_monster[monster_type] : monster_type;"
        )
        line()
    }

    val switchVar = if (variants.isNotEmpty()) "ai_type" else "monster_type"

    if (monstersWithAI.isEmpty()) {
        line("// No monsters with AI defined - default to basic attack")
        if (variants.isEmpty()) {
            line("(void)monster_type;")
        } else {
            line("(void)$switchVar;")
        }
        line("_monster_basic_attack(monster_slot, _ai_random_target());")
    } else {
        line("switch ($switchVar) {")
        indent++

        for (monster in monstersWithAI) {
            line("case ${monster.monsterIndex}u: ${monster.id}_ai(monster_slot); break;")
        }

        line("default:")
        indent++
        line("// Monster without AI - default to basic attack")
        line("_ai_monster_slot = monster_slot;")
        line("_monster_basic_attack(monster_slot, _ai_random_target());")
        line("break;")
        indent--

        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate AI helper functions. */
private fun GBDKCodeGenerator.generateAIHelpers() {
    line("// =============================================================================")
    line("// AI HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    // AI context variables (must be declared before action functions that use them)
    line("// AI context variables (set before AI function call)")
    line("static UINT8 _ai_monster_slot = 0u;")
    line("static UINT8 _ai_hp_percent = 100u;")
    line("static UINT8 _ai_ally_count = 0u;")
    line("static UINT8 _ai_enemy_count = 0u;")
    line()

    // Monster parameter array for one-time abilities (must be before spawn function)
    line("// Monster parameter array for one-time special abilities")
    line("// Bit 7 (0x80): Special ability charge used")
    line("// Bits 0-6: Monster-specific parameters")
    line("static UINT8 _monster_parameter[MAX_ENEMIES] = {0u};")
    line()

    // Monster action functions MUST be generated after variables they depend on
    generateMonsterActionFunctions()

    // Monster HP percent (needed before targeting functions)
    line("// Get monster's HP as percentage")
    line("static UINT8 _monster_hp_percent(UINT8 slot) {")
    indent++
    line("UINT8 idx = MAX_PARTY_SIZE + slot;")
    line("if (_combatant_hp_max[idx] == 0u) return 0u;")
    line("return (UINT8)((_combatant_hp[idx] * 100u) / _combatant_hp_max[idx]);")
    indent--
    line("}")
    line()

    // Random target selection
    line("// Get a random alive party member as target")
    line("static UINT8 _ai_random_target(void) {")
    indent++
    line("UINT8 count = _alive_party();")
    line("if (count == 0u) return 255u;")
    line("UINT8 roll = _rand() % count;")
    line("UINT8 seen = 0u;")
    line("for (UINT8 i = 0u; i < MAX_PARTY_SIZE; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u) {")
    indent++
    line("if (seen == roll) return i;")
    line("seen++;")
    indent--
    line("}")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()

    // Weakest target selection
    line("// Get the party member with lowest HP")
    line("static UINT8 _ai_weakest_target(void) {")
    indent++
    line("UINT8 weakest = 255u;")
    line("UINT16 lowest_hp = 65535u;")
    line("for (UINT8 i = 0u; i < MAX_PARTY_SIZE; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u && _combatant_hp[i] < lowest_hp) {")
    indent++
    line("lowest_hp = _combatant_hp[i];")
    line("weakest = i;")
    indent--
    line("}")
    indent--
    line("}")
    line("return weakest;")
    indent--
    line("}")
    line()

    // Strongest target selection (highest ATK)
    line("// Get the party member with highest ATK")
    line("static UINT8 _ai_strongest_target(void) {")
    indent++
    line("UINT8 strongest = 0u;")
    line("UINT8 highest_atk = 0u;")
    line("for (UINT8 i = 0u; i < MAX_PARTY_SIZE; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u) {")
    indent++
    line("UINT8 atk = _party_get_stat(i, STAT_ATK);")
    line("if (atk > highest_atk) {")
    indent++
    line("highest_atk = atk;")
    line("strongest = i;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line("return strongest;")
    indent--
    line("}")
    line()

    // First target
    line("// Get the first alive party member")
    line("static UINT8 _ai_first_target(void) {")
    indent++
    line("for (UINT8 i = 0u; i < MAX_PARTY_SIZE; i++) {")
    indent++
    line("if (_combatant_hp[i] > 0u) return i;")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()

    // Target has effect check
    line("// Check if a party member has a specific status effect active")
    line("static UINT8 _target_has_effect(UINT8 target_idx, UINT8 effect_id) {")
    indent++
    line("if (target_idx >= MAX_PARTY_SIZE) return 0u;")
    line("// Check effect slots for the target")
    line("for (UINT8 i = 0u; i < MAX_EFFECTS_PER_COMBATANT; i++) {")
    indent++
    line("if (_combatant_effect_id[target_idx][i] == effect_id &&")
    line("    _combatant_effect_duration[target_idx][i] > 0u) {")
    indent++
    line("return 1u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()

    // Death hook helpers
    generateDeathHookHelpers()

    // Hit hook helpers
    generateHitHookHelpers()
}

/** Generate death hook helper functions and variables (but NOT dispatch table). */
private fun GBDKCodeGenerator.generateDeathHookHelpers() {
    val monstersWithDeathHooks = game.monsters.filter { it.onDeathStatements.isNotEmpty() }
    if (monstersWithDeathHooks.isEmpty()) return

    line("// =============================================================================")
    line("// DEATH HOOK HELPERS")
    line("// =============================================================================")
    line()

    // Death hook context variables
    line("// Death hook context variables")
    line("static UINT8 _death_hook_slot = 0u;")
    line("static UINT8 _death_hook_revived = 0u;")
    line("static UINT16 _battle_bonus_exp = 0u;")
    line()

    // Monster revive function
    line("// Revive monster at percentage of max HP")
    line("static void _monster_revive(UINT8 slot, UINT8 hp_percent) {")
    indent++
    line("UINT8 idx = MAX_PARTY_SIZE + slot;")
    line("UINT16 max_hp = _combatant_hp_max[idx];")
    line("_combatant_hp[idx] = (max_hp * hp_percent) / 100u;")
    line("if (_combatant_hp[idx] == 0u) _combatant_hp[idx] = 1u; // At least 1 HP")
    line("_death_hook_revived = 1u;")
    indent--
    line("}")
    line()

    // Monster transform function
    line("// Transform monster into a different type")
    line("static void _monster_transform(UINT8 slot, UINT8 new_monster_id) {")
    indent++
    line("UINT8 idx = MAX_PARTY_SIZE + slot;")
    line("// Update monster type in slot")
    line("_battle_enemy_types[slot] = new_monster_id;")
    line("// Reset to new monster's max HP")
    line("_combatant_hp_max[idx] = _monster_hp_table[new_monster_id];")
    line("_combatant_hp[idx] = _combatant_hp_max[idx];")
    line("_death_hook_revived = 1u; // Transformed counts as alive")
    indent--
    line("}")
    line()
}

/**
 * Generate death hook dispatch table. Must be called AFTER monster data generation (references
 * monster-specific hook functions).
 */
private fun GBDKCodeGenerator.generateDeathHookDispatch() {
    val monstersWithDeathHooks = game.monsters.filter { it.onDeathStatements.isNotEmpty() }
    if (monstersWithDeathHooks.isEmpty()) return

    line("// =============================================================================")
    line("// DEATH HOOK DISPATCH")
    line("// =============================================================================")
    line()

    // Generate death hook dispatch table
    line("// Death hook dispatch - returns 0 if monster stays in battle, 1 if dead")
    line("static UINT8 _call_death_hook(UINT8 monster_type, UINT8 slot) {")
    indent++
    line("switch (monster_type) {")
    indent++

    for (monster in monstersWithDeathHooks) {
        line("case ${monster.monsterIndex}u: return ${monster.id}_on_death(slot);")
    }

    line("default: return 1u; // No death hook, monster dies")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate hit hook helper functions and variables (but NOT dispatch table). */
private fun GBDKCodeGenerator.generateHitHookHelpers() {
    val monstersWithHitHooks = game.monsters.filter { it.onHitStatements.isNotEmpty() }
    if (monstersWithHitHooks.isEmpty()) return

    line("// =============================================================================")
    line("// HIT HOOK HELPERS")
    line("// =============================================================================")
    line()

    // Hit hook context variables
    line("// Hit hook context variables")
    line("static UINT8 _hit_hook_slot = 0u;")
    line("static UINT8 _hit_hook_cancelled = 0u;")
    line("static UINT16 _hit_hook_damage = 0u;")
    line()

    // Evasion counter array (for phasing mechanics)
    line("// Evasion counter per monster slot (for phasing mechanics)")
    line("static UINT8 _monster_evasion[MAX_ENEMIES] = {0u};")
    line()
}

/**
 * Generate hit hook dispatch table. Must be called AFTER monster data generation (references
 * monster-specific hook functions).
 */
private fun GBDKCodeGenerator.generateHitHookDispatch() {
    val monstersWithHitHooks = game.monsters.filter { it.onHitStatements.isNotEmpty() }
    if (monstersWithHitHooks.isEmpty()) return

    line("// =============================================================================")
    line("// HIT HOOK DISPATCH")
    line("// =============================================================================")
    line()

    // Generate hit hook dispatch table
    line("// Hit hook dispatch - returns modified damage (0 if cancelled)")
    line("static UINT16 _call_hit_hook(UINT8 monster_type, UINT8 slot, UINT16 damage) {")
    indent++
    line("switch (monster_type) {")
    indent++

    for (monster in monstersWithHitHooks) {
        line("case ${monster.monsterIndex}u: return ${monster.id}_on_hit(slot, damage);")
    }

    line("default: return damage; // No hit hook, apply full damage")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/**
 * Generate monster lookup tables including variant entries.
 *
 * Tables are organized with base monsters first (indices 0 to N-1), followed by variants (indices N
 * to N+M-1). Variants share AI with their base monster but have different scaled stats.
 */
private fun GBDKCodeGenerator.generateMonsterLookupTable(
    monsters: List<Monster>,
    variants: List<MonsterVariant>,
) {
    val totalCount = monsters.size + variants.size

    line("// Monster count (base + variants)")
    line("#define MONSTER_BASE_COUNT ${monsters.size}u")
    line("#define MONSTER_VARIANT_COUNT ${variants.size}u")
    line("#define MONSTER_COUNT ${totalCount}u")
    line()

    // Generate variant ID constants
    if (variants.isNotEmpty()) {
        line("// Variant monster IDs (indices after base monsters)")
        for (variant in variants) {
            val constName = "MONSTER_${variant.variantId.uppercase()}_ID"
            line("#define $constName ${variant.variantIndex}u")
        }
        line()
    }

    // Helper function to generate a stat array with both monsters and variants
    fun generateStatArray(
        name: String,
        type: String,
        monsterStat: (Monster) -> Int,
        variantStat: (MonsterVariant) -> Int,
    ) {
        line("static const $type $name[$totalCount] = {")
        indent++
        // Base monsters
        if (monsters.isNotEmpty()) {
            line("// Base monsters")
            line(
                monsters.joinToString(", ") { "${monsterStat(it)}u" } +
                    if (variants.isNotEmpty()) "," else ""
            )
        }
        // Variants
        if (variants.isNotEmpty()) {
            line("// Variants")
            line(variants.joinToString(", ") { "${variantStat(it)}u" })
        }
        indent--
        line("};")
        line()
    }

    // Generate stat lookup arrays
    line("// Monster stat lookup tables (base monsters + variants)")
    generateStatArray("_monster_base_hp", "UINT16", { it.scaledHp }, { it.scaledHp })

    // Alias for compatibility with code that uses _monster_hp_table
    line("#define _monster_hp_table _monster_base_hp")
    line()

    generateStatArray("_monster_atk_table", "UINT8", { it.scaledAtk }, { it.scaledAtk })
    generateStatArray("_monster_def_table", "UINT8", { it.scaledDef }, { it.scaledDef })
    generateStatArray("_monster_matk_table", "UINT8", { it.scaledMatk }, { it.scaledMatk })
    generateStatArray("_monster_mdef_table", "UINT8", { it.scaledMdef }, { it.scaledMdef })
    generateStatArray("_monster_agl_table", "UINT8", { it.scaledAgl }, { it.scaledAgl })

    // Determine exp table type based on max value to avoid overflow warnings
    val maxExpReward =
        maxOf(
            monsters.maxOfOrNull { it.expReward } ?: 0,
            variants.maxOfOrNull { it.scaledExpReward } ?: 0,
        )
    val expTableType =
        when {
            maxExpReward <= 255 -> "UINT8"
            maxExpReward <= 65535 -> "UINT16"
            else -> "UINT32"
        }
    line("static const $expTableType _monster_exp_table[$totalCount] = {")
    indent++
    if (monsters.isNotEmpty()) {
        line("// Base monsters")
        line(
            monsters.joinToString(", ") { "${it.expReward}u" } +
                if (variants.isNotEmpty()) "," else ""
        )
    }
    if (variants.isNotEmpty()) {
        line("// Variants (scaled exp)")
        line(variants.joinToString(", ") { "${it.scaledExpReward}u" })
    }
    indent--
    line("};")
    line()

    // Generate base monster index lookup for variants (used for AI dispatch)
    if (variants.isNotEmpty()) {
        line("// Base monster index for each variant (for AI dispatch)")
        line("static const UINT8 _variant_base_monster[$totalCount] = {")
        indent++
        line("// Base monsters map to themselves")
        line(monsters.indices.joinToString(", ") { "${it}u" } + ",")
        line("// Variants map to their base monster")
        line(variants.joinToString(", ") { "${it.baseMonster.monsterIndex}u" })
        indent--
        line("};")
        line()
    }

    // Generate aspect modifier table for monsters (including variants)
    generateMonsterAspectTable(monsters, variants)
}

/**
 * Generate aspect modifier lookup table for monsters and variants.
 *
 * Each monster/variant has a modifier per aspect (100 = normal, 50 = resist, 200 = vulnerable, 0 =
 * immune). Variants inherit their base monster's aspect profile. The table is indexed as
 * _monster_aspect_mod[monster_id][aspect_id].
 */
private fun GBDKCodeGenerator.generateMonsterAspectTable(
    monsters: List<Monster>,
    variants: List<MonsterVariant>,
) {
    val aspectCount = io.github.gbkt.core.rpg.Aspect.entries.size
    val totalCount = monsters.size + variants.size

    // Check if any monster has aspect modifiers defined
    val hasAspectMods = monsters.any { it.aspectProfile != null }
    if (!hasAspectMods) {
        // No monsters have aspect profiles - generate a simple constant
        line("// No monsters have aspect modifiers defined")
        line("// _get_monster_aspect_mod returns 100 (normal) for all")
        line("static UINT8 _get_monster_aspect_mod(UINT8 monster_id, UINT8 aspect) {")
        indent++
        line("(void)monster_id; (void)aspect;")
        line("return 100u; // Normal damage")
        indent--
        line("}")
        line()
        return
    }

    line(
        "// Aspect modifiers per monster/variant (0=immune, 50=resist, 100=normal, 150=weak, 200=vulnerable)"
    )
    line(
        "// Aspects: PHYSICAL=0, MAGICAL=1, FIRE=2, ICE=3, LIGHTNING=4, EARTH=5, WIND=6, WATER=7, LIGHT=8, DARK=9, PURE=10"
    )
    line("static const UINT8 _monster_aspect_mod[$totalCount][$aspectCount] = {")
    indent++

    // Base monsters
    line("// Base monsters")
    for (monster in monsters) {
        val mods =
            io.github.gbkt.core.rpg.Aspect.entries.map { aspect ->
                monster.getAspectModifier(aspect).multiplier
            }
        line("{ ${mods.joinToString(", ") { "${it}u" }} }, // ${monster.displayName}")
    }

    // Variants (inherit base monster's aspect profile)
    if (variants.isNotEmpty()) {
        line("// Variants (inherit base monster aspects)")
        for (variant in variants) {
            val mods =
                io.github.gbkt.core.rpg.Aspect.entries.map { aspect ->
                    variant.baseMonster.getAspectModifier(aspect).multiplier
                }
            line("{ ${mods.joinToString(", ") { "${it}u" }} }, // ${variant.displayName}")
        }
    }

    indent--
    line("};")
    line()

    // Generate helper function to get aspect modifier for a monster
    line("// Get aspect modifier for a monster type (including variants)")
    line("static UINT8 _get_monster_aspect_mod(UINT8 monster_id, UINT8 aspect) {")
    indent++
    line("if (monster_id >= MONSTER_COUNT || aspect >= ${aspectCount}u) return 100u;")
    line("return _monster_aspect_mod[monster_id][aspect];")
    indent--
    line("}")
    line()
}

// =============================================================================
// MONSTER STATEMENT GENERATION
// =============================================================================

/**
 * Handle monster-related IR statements.
 *
 * @return true if this was a monster statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateMonsterStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRMonsterBasicAttack -> {
            generateMonsterBasicAttack(stmt)
            true
        }
        is IRMonsterBasicAttackExpr -> {
            generateMonsterBasicAttackExpr(stmt)
            true
        }
        is IRMonsterUseAbility -> {
            generateMonsterUseAbility(stmt)
            true
        }
        is IRMonsterUseAbilityExpr -> {
            generateMonsterUseAbilityExpr(stmt)
            true
        }
        is IRMonsterFlee -> {
            generateMonsterFlee(stmt)
            true
        }
        is IRMonsterDefend -> {
            generateMonsterDefend(stmt)
            true
        }
        is IRMonsterSkipTurn -> {
            generateMonsterSkipTurn(stmt)
            true
        }
        is IRSpawnMonster -> {
            generateSpawnMonster(stmt)
            true
        }
        is IRMonsterRevive -> {
            generateMonsterRevive(stmt)
            true
        }
        is IRMonsterTransform -> {
            generateMonsterTransform(stmt)
            true
        }
        is IRMonsterAwardBonusExp -> {
            generateMonsterAwardBonusExp(stmt)
            true
        }
        is IRMonsterCancelHit -> {
            generateMonsterCancelHit(stmt)
            true
        }
        is IRMonsterModifyHitDamage -> {
            generateMonsterModifyHitDamage(stmt)
            true
        }
        is IRMonsterDecrementEvasion -> {
            generateMonsterDecrementEvasion(stmt)
            true
        }
        is IRMonsterUseSpecialCharge -> {
            generateMonsterUseSpecialCharge(stmt)
            true
        }
        else -> false
    }

/** Generate monster basic attack. */
private fun GBDKCodeGenerator.generateMonsterBasicAttack(stmt: IRMonsterBasicAttack) {
    val targetExpr =
        if (stmt.targetName != null) {
            // Find character index by name
            val charIndex = game.characters.indexOfFirst { it.name == stmt.targetName }
            if (charIndex >= 0) "${charIndex}u" else "_ai_random_target()"
        } else {
            "_ai_random_target()"
        }
    lineWithSource(
        "_monster_basic_attack(_ai_monster_slot, $targetExpr);",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster basic attack with expression target. */
private fun GBDKCodeGenerator.generateMonsterBasicAttackExpr(stmt: IRMonsterBasicAttackExpr) {
    lineWithSource(
        "_monster_basic_attack(_ai_monster_slot, ${stmt.targetExpression});",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster use ability. */
private fun GBDKCodeGenerator.generateMonsterUseAbility(stmt: IRMonsterUseAbility) {
    val abilityIndex = game.abilities.indexOfFirst { it.id == stmt.abilityId }.coerceAtLeast(0)
    val targetExpr =
        if (stmt.targetName != null) {
            val charIndex = game.characters.indexOfFirst { it.name == stmt.targetName }
            if (charIndex >= 0) "${charIndex}u" else "_ai_random_target()"
        } else {
            "_ai_random_target()"
        }
    lineWithSource(
        "_monster_use_ability(_ai_monster_slot, ${abilityIndex}u, $targetExpr);",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster use ability with expression target. */
private fun GBDKCodeGenerator.generateMonsterUseAbilityExpr(stmt: IRMonsterUseAbilityExpr) {
    val abilityIndex = game.abilities.indexOfFirst { it.id == stmt.abilityId }.coerceAtLeast(0)
    lineWithSource(
        "_monster_use_ability(_ai_monster_slot, ${abilityIndex}u, ${stmt.targetExpression});",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster flee. */
private fun GBDKCodeGenerator.generateMonsterFlee(stmt: IRMonsterFlee) {
    lineWithSource("_monster_flee(_ai_monster_slot);", stmt.sourceLocation, stmt.monsterId)
}

/** Generate monster defend. */
private fun GBDKCodeGenerator.generateMonsterDefend(stmt: IRMonsterDefend) {
    lineWithSource("_monster_defend(_ai_monster_slot);", stmt.sourceLocation, stmt.monsterId)
}

/** Generate monster skip turn. */
private fun GBDKCodeGenerator.generateMonsterSkipTurn(stmt: IRMonsterSkipTurn) {
    lineWithSource("// Monster ${stmt.monsterId} skips turn", stmt.sourceLocation, stmt.monsterId)
}

/** Generate spawn monster in battle. */
private fun GBDKCodeGenerator.generateSpawnMonster(stmt: IRSpawnMonster) {
    val monsterIndex = game.monsters.indexOfFirst { it.id == stmt.monsterId }.coerceAtLeast(0)
    lineWithSource(
        "_battle_spawn_monster(${monsterIndex}u, ${stmt.slot}u);",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster revive in death hook. */
private fun GBDKCodeGenerator.generateMonsterRevive(stmt: IRMonsterRevive) {
    lineWithSource(
        "_monster_revive(_death_hook_slot, ${stmt.hpPercent}u);",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate monster transform in death hook. */
private fun GBDKCodeGenerator.generateMonsterTransform(stmt: IRMonsterTransform) {
    val newMonsterIndex = game.monsters.indexOfFirst { it.id == stmt.newMonsterId }.coerceAtLeast(0)
    lineWithSource(
        "_monster_transform(_death_hook_slot, ${newMonsterIndex}u);",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate bonus EXP award in death hook. */
private fun GBDKCodeGenerator.generateMonsterAwardBonusExp(stmt: IRMonsterAwardBonusExp) {
    lineWithSource("_battle_bonus_exp += ${stmt.amount}u;", stmt.sourceLocation, stmt.monsterId)
}

/** Generate cancel hit in hit hook. */
private fun GBDKCodeGenerator.generateMonsterCancelHit(stmt: IRMonsterCancelHit) {
    lineWithSource("_hit_hook_cancelled = 1u;", stmt.sourceLocation, stmt.monsterId)
}

/** Generate modify damage in hit hook. */
private fun GBDKCodeGenerator.generateMonsterModifyHitDamage(stmt: IRMonsterModifyHitDamage) {
    lineWithSource(
        "_hit_hook_damage = (_hit_hook_damage * ${stmt.multiplier}u) / 100u;",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

/** Generate decrement evasion counter in hit hook. */
private fun GBDKCodeGenerator.generateMonsterDecrementEvasion(stmt: IRMonsterDecrementEvasion) {
    lineWithSource("_monster_evasion[_hit_hook_slot]--;", stmt.sourceLocation, stmt.monsterId)
}

/** Generate use special charge (marks monster's one-time ability as used). */
private fun GBDKCodeGenerator.generateMonsterUseSpecialCharge(stmt: IRMonsterUseSpecialCharge) {
    lineWithSource(
        "_monster_parameter[_ai_monster_slot] |= 0x80u;",
        stmt.sourceLocation,
        stmt.monsterId,
    )
}

// =============================================================================
// MONSTER EXPRESSION GENERATION
// =============================================================================

/**
 * Generate C expression for monster-related queries.
 *
 * @return the C expression string, or null if not a monster expression
 */
internal fun GBDKCodeGenerator.generateMonsterExpr(expr: IRExpression): String? =
    when (expr) {
        is IRMonsterHpCheck -> {
            val op = if (expr.below) "<" else ">"
            "(_ai_hp_percent $op ${expr.percent}u)"
        }
        is IRRandomChance -> {
            // Constant-fold edge cases to avoid always-true/always-false warnings
            when (expr.percent) {
                0 -> "0u" // chance(0) is always false
                100 -> "1u" // chance(100) is always true
                else -> "((_rand() % 100u) < ${expr.percent}u)"
            }
        }
        is IRMonsterHasAlly -> "(_ai_ally_count > 1u)"
        is IRAIEnemyCountCheck -> "(_ai_enemy_count == ${expr.count}u)"
        is IRMonsterGetHp -> "_combatant_hp[MAX_PARTY_SIZE + _ai_monster_slot]"
        is IRMonsterGetMaxHp -> "_combatant_hp_max[MAX_PARTY_SIZE + _ai_monster_slot]"
        is IRMonsterGetHpPercent -> "_monster_hp_percent(_ai_monster_slot)"
        is IRMonsterIsAlive -> "(_combatant_hp[MAX_PARTY_SIZE + _ai_monster_slot] > 0u)"
        is IRMonsterIsDefending -> "_monster_is_defending(_ai_monster_slot)"
        is IRHasMonsterInBattle -> {
            val monsterIndex =
                game.monsters.indexOfFirst { it.id == expr.monsterId }.coerceAtLeast(0)
            "_battle_has_monster(${monsterIndex}u)"
        }
        is IRAliveMonsterCount -> "_alive_enemies()"
        is IRAllMonstersDefeated -> "(_alive_enemies() == 0u)"
        is IRMonsterWasRevived -> "(_death_hook_revived != 0u)"
        is IRMonsterHasEvasion -> "(_monster_evasion[_hit_hook_slot] > 0u)"
        is IRMonsterHitWasCancelled -> "(_hit_hook_cancelled != 0u)"
        is IRMonsterHasSpecialCharge -> "((_monster_parameter[_ai_monster_slot] & 0x80u) == 0u)"
        is IRMonsterTargetHasEffect -> {
            val effectIndex =
                game.statusEffects.indexOfFirst { it.name == expr.effectId }.coerceAtLeast(0)
            "_target_has_effect(_ai_first_target(), ${effectIndex}u)"
        }
        else -> null
    }

// =============================================================================
// MONSTER SPRITE SYSTEM CODE GENERATION
// =============================================================================

/**
 * Generate monster sprite rendering system for battles.
 *
 * Monsters are rendered as background tiles (not OAM sprites) because:
 * 1. OAM has a limit of 40 sprites - battles with multiple monsters would exhaust this
 * 2. Original Game Boy games render monsters as background tiles
 * 3. Monsters can be larger than OAM limits (8x16 max per sprite)
 * 4. Tier palette variations work efficiently with background tiles
 *
 * Creates:
 * - Tile dimension constants per monster
 * - Base tile index lookup table (VRAM offset for each monster's tiles)
 * - Render function to draw monster at specified tile position
 * - Clear function to erase monster from background
 */
private fun GBDKCodeGenerator.generateMonsterSpriteSystem(
    monsters: List<Monster>,
    variants: List<MonsterVariant>,
) {
    // Only generate if we have monsters with sprites
    val monstersWithSprites = monsters.filter { it.spriteInfo != null }
    if (monstersWithSprites.isEmpty()) return

    line("// =============================================================================")
    line("// MONSTER SPRITE SYSTEM")
    line("// =============================================================================")
    line()

    // Generate tile dimension constants for each monster
    line("// Monster sprite tile dimensions (in 8x8 pixel tiles)")
    for (monster in monstersWithSprites) {
        val info = monster.spriteInfo!!
        val name = monster.id.uppercase()
        line("#define MONSTER_${name}_TILE_WIDTH ${info.tileWidth}u")
        line("#define MONSTER_${name}_TILE_HEIGHT ${info.tileHeight}u")
    }
    line()

    // Generate tile dimension lookup tables
    val totalCount = monsters.size + variants.size
    line("// Tile width lookup table (indexed by monster_id)")
    line("static const UINT8 _monster_tile_width[$totalCount] = {")
    indent++
    line("// Base monsters")
    val widths =
        monsters.map { monster ->
            val info = monster.spriteInfo
            if (info != null) "${info.tileWidth}u" else "2u"
        }
    line(widths.joinToString(", ") + if (variants.isNotEmpty()) "," else "")
    if (variants.isNotEmpty()) {
        line("// Variants (inherit from base monster)")
        val variantWidths =
            variants.map { variant ->
                val info = variant.baseMonster.spriteInfo
                if (info != null) "${info.tileWidth}u" else "2u"
            }
        line(variantWidths.joinToString(", "))
    }
    indent--
    line("};")
    line()

    line("// Tile height lookup table (indexed by monster_id)")
    line("static const UINT8 _monster_tile_height[$totalCount] = {")
    indent++
    line("// Base monsters")
    val heights =
        monsters.map { monster ->
            val info = monster.spriteInfo
            if (info != null) "${info.tileHeight}u" else "2u"
        }
    line(heights.joinToString(", ") + if (variants.isNotEmpty()) "," else "")
    if (variants.isNotEmpty()) {
        line("// Variants (inherit from base monster)")
        val variantHeights =
            variants.map { variant ->
                val info = variant.baseMonster.spriteInfo
                if (info != null) "${info.tileHeight}u" else "2u"
            }
        line(variantHeights.joinToString(", "))
    }
    indent--
    line("};")
    line()

    // Generate base tile index lookup table
    // This maps monster_id to the starting tile index in VRAM
    // Tiles are loaded consecutively: monster0 tiles, monster1 tiles, etc.
    line("// Base VRAM tile index for each monster's sprite")
    line("// Calculated at compile time based on cumulative tile count")
    var currentTileIndex = 128 // Start after background tiles (typically 0-127)
    val tileIndices = mutableListOf<Int>()

    for (monster in monsters) {
        tileIndices.add(currentTileIndex)
        val info = monster.spriteInfo
        if (info != null) {
            currentTileIndex += info.tileWidth * info.tileHeight
        } else {
            currentTileIndex += 4 // Default 2x2 = 4 tiles
        }
    }

    line("static const UINT8 _monster_base_tile[$totalCount] = {")
    indent++
    line("// Base monsters")
    line(tileIndices.joinToString(", ") { "${it}u" } + if (variants.isNotEmpty()) "," else "")
    if (variants.isNotEmpty()) {
        line("// Variants (use same tiles as base monster)")
        val variantTileIndices =
            variants.map { variant ->
                val baseIndex = monsters.indexOf(variant.baseMonster)
                if (baseIndex >= 0) tileIndices[baseIndex] else 128
            }
        line(variantTileIndices.joinToString(", ") { "${it}u" })
    }
    indent--
    line("};")
    line()

    // Generate palette lookup table for tier-based coloring
    generateMonsterPaletteLookup(monsters, variants)

    // Battle monster positions (stay in bank 0 as they're lookup data)
    line("// Enemy sprite positions in battle (tile coordinates)")
    line("// Positions depend on monster count and size")
    line("// Default: 3 enemy slots at x=2, 10, 18 (centered for 2-tile wide sprites)")
    line("static const UINT8 _battle_enemy_x[MAX_ENEMIES] = {2u, 10u, 18u};")
    line("static const UINT8 _battle_enemy_y = 2u; // Top of screen")
    line()

    // Forward declarations for banked monster sprite functions
    line("// =============================================================================")
    line("// FORWARD DECLARATIONS FOR BANKED MONSTER SPRITE FUNCTIONS")
    line("// =============================================================================")
    line()
    line("void _render_monster(UINT8 monster_id, UINT8 x_tile, UINT8 y_tile, UINT8 tier) BANKED;")
    line("void _clear_monster(UINT8 x_tile, UINT8 y_tile, UINT8 width, UINT8 height) BANKED;")
    line("void _battle_render_enemies(void) BANKED;")
    line("void _battle_clear_enemy(UINT8 slot) BANKED;")
    line("void _battle_clear_all_enemies(void) BANKED;")
    line()

    // Switch to monster bank for sprite functions (called infrequently)
    setBank(codeBankMonster)

    // Generate render function (banked)
    generateMonsterRenderFunctionBanked()

    // Generate clear function (banked)
    generateMonsterClearFunctionBanked()

    // Generate battle monster rendering helpers (banked)
    generateBattleMonsterHelpersBanked()

    // Return to bank 0
    returnToHome()
}

/** Generate palette lookup table for monster tier variations. */
private fun GBDKCodeGenerator.generateMonsterPaletteLookup(
    monsters: List<Monster>,
    variants: List<MonsterVariant>,
) {
    val totalCount = monsters.size + variants.size

    line("// Default palette lookup by tier (can be overridden per monster)")
    line("static const UINT8 _tier_default_palette[4] = {")
    indent++
    line("0u, // Tier C")
    line("1u, // Tier B")
    line("2u, // Tier A")
    line("3u  // Tier S")
    indent--
    line("};")
    line()

    line("// Monster palette lookup (indexed by monster_id, accounts for tier palettes)")
    line("static UINT8 _get_monster_palette(UINT8 monster_id, UINT8 tier) {")
    indent++

    // Check if any monster has custom tier palettes
    val hasCustomPalettes = monsters.any { it.spriteInfo?.tierPalettes?.isNotEmpty() == true }

    if (!hasCustomPalettes) {
        line("(void)monster_id; // No custom palettes defined")
        line("// Use tier-based default palette")
        line("if (tier < 4u) return _tier_default_palette[tier];")
        line("return 0u;")
    } else {
        line("// Check for monster-specific tier palettes")
        line("switch (monster_id) {")
        indent++

        for (monster in monsters) {
            val info = monster.spriteInfo
            if (info != null && info.tierPalettes.isNotEmpty()) {
                line("case ${monster.monsterIndex}u: // ${monster.displayName}")
                indent++
                line("switch (tier) {")
                indent++
                for ((tierVal, palette) in info.tierPalettes) {
                    line("case ${tierVal.ordinal}u: return ${palette}u; // ${tierVal.name}")
                }
                line("default: return _tier_default_palette[tier];")
                indent--
                line("}")
                indent--
            }
        }

        line("default:")
        indent++
        line("// Use tier-based default palette")
        line("if (tier < 4u) return _tier_default_palette[tier];")
        line("return 0u;")
        indent--
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate function to render a monster sprite at a tile position (banked). */
private fun GBDKCodeGenerator.generateMonsterRenderFunctionBanked() {
    line("// Render monster sprite at tile position using background tiles")
    line("// x_tile, y_tile: Position in tile coordinates (0-31 for 256px screen)")
    line("// monster_id: Index into monster tables")
    line("// tier: Monster tier for palette selection (0=C, 1=B, 2=A, 3=S)")
    line("void _render_monster(UINT8 monster_id, UINT8 x_tile, UINT8 y_tile, UINT8 tier) BANKED {")
    indent++
    line("if (monster_id >= MONSTER_COUNT) return;")
    line()
    line("UINT8 tile_w = _monster_tile_width[monster_id];")
    line("UINT8 tile_h = _monster_tile_height[monster_id];")
    line("UINT8 base_tile = _monster_base_tile[monster_id];")
    line("UINT8 palette = _get_monster_palette(monster_id, tier);")
    line()
    line("// Set palette for this monster (GBC only)")
    line("#ifdef CGB")
    line("VBK_REG = 1u; // Switch to attribute map")
    line("for (UINT8 ty = 0u; ty < tile_h; ty++) {")
    indent++
    line("for (UINT8 tx = 0u; tx < tile_w; tx++) {")
    indent++
    line("set_bkg_tile_xy(x_tile + tx, y_tile + ty, palette);")
    indent--
    line("}")
    indent--
    line("}")
    line("VBK_REG = 0u; // Switch back to tile map")
    line("#else")
    line("(void)palette; // DMG doesn't support per-tile palettes")
    line("#endif")
    line()
    line("// Draw tiles row by row")
    line("UINT8 tile_idx = base_tile;")
    line("for (UINT8 ty = 0u; ty < tile_h; ty++) {")
    indent++
    line("for (UINT8 tx = 0u; tx < tile_w; tx++) {")
    indent++
    line("set_bkg_tile_xy(x_tile + tx, y_tile + ty, tile_idx);")
    line("tile_idx++;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate function to clear a monster sprite from background (banked). */
private fun GBDKCodeGenerator.generateMonsterClearFunctionBanked() {
    line("// Clear monster sprite area (fill with empty tile)")
    line("// x_tile, y_tile: Position in tile coordinates")
    line("// width, height: Size in tiles")
    line("void _clear_monster(UINT8 x_tile, UINT8 y_tile, UINT8 width, UINT8 height) BANKED {")
    indent++
    line("for (UINT8 ty = 0u; ty < height; ty++) {")
    indent++
    line("for (UINT8 tx = 0u; tx < width; tx++) {")
    indent++
    line("set_bkg_tile_xy(x_tile + tx, y_tile + ty, 0u); // Tile 0 = empty")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate battle-specific monster sprite helpers (banked). */
private fun GBDKCodeGenerator.generateBattleMonsterHelpersBanked() {
    line("// =============================================================================")
    line("// BATTLE MONSTER SPRITE HELPERS (BANKED)")
    line("// =============================================================================")
    line()

    // Render all enemies in battle
    line("// Render all enemy sprites in battle")
    line("void _battle_render_enemies(void) BANKED {")
    indent++
    line("for (UINT8 i = 0u; i < _enemy_count; i++) {")
    indent++
    line("UINT8 idx = MAX_PARTY_SIZE + i;")
    line("if (_combatant_hp[idx] > 0u) {")
    indent++
    line("UINT8 monster_id = _battle_enemy_types[i];")
    line("// Get tier from monster or variant (stored in lookup table)")
    line("UINT8 tier = 0u; // Default to tier C; could be enhanced with tier lookup")
    line("_render_monster(monster_id, _battle_enemy_x[i], _battle_enemy_y, tier);")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Clear specific enemy slot
    line("// Clear enemy sprite when defeated")
    line("void _battle_clear_enemy(UINT8 slot) BANKED {")
    indent++
    line("if (slot >= MAX_ENEMIES) return;")
    line("UINT8 monster_id = _battle_enemy_types[slot];")
    line("UINT8 tile_w = _monster_tile_width[monster_id];")
    line("UINT8 tile_h = _monster_tile_height[monster_id];")
    line("_clear_monster(_battle_enemy_x[slot], _battle_enemy_y, tile_w, tile_h);")
    indent--
    line("}")
    line()

    // Clear all enemies
    line("// Clear all enemy sprites (end of battle)")
    line("void _battle_clear_all_enemies(void) BANKED {")
    indent++
    line("for (UINT8 i = 0u; i < MAX_ENEMIES; i++) {")
    indent++
    line("_battle_clear_enemy(i);")
    indent--
    line("}")
    indent--
    line("}")
    line()
}
