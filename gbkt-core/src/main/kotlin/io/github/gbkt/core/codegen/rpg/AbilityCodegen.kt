/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRAbilityApplyEffect
import io.github.gbkt.core.ir.IRAbilityApplyEffectToSelf
import io.github.gbkt.core.ir.IRAbilityCureDebuffs
import io.github.gbkt.core.ir.IRAbilityDealDamage
import io.github.gbkt.core.ir.IRAbilityDrain
import io.github.gbkt.core.ir.IRAbilityFullHeal
import io.github.gbkt.core.ir.IRAbilityHeal
import io.github.gbkt.core.ir.IRAbilityInstantKill
import io.github.gbkt.core.ir.IRAbilityPlayAnimation
import io.github.gbkt.core.ir.IRAbilityPlaySfx
import io.github.gbkt.core.ir.IRAbilityRestoreSp
import io.github.gbkt.core.ir.IRAbilityShowMessage
import io.github.gbkt.core.ir.IRAbilityUnlockCheck
import io.github.gbkt.core.ir.IRCanUseAbility
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetAbilityPower
import io.github.gbkt.core.ir.IRGetAbilitySpCost
import io.github.gbkt.core.ir.IRGrantAbility
import io.github.gbkt.core.ir.IRHasAbility
import io.github.gbkt.core.ir.IRRevokeAbility
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRUseAbility
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.core.rpg.AbilityCost
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.TargetingMode

// =============================================================================
// ABILITY CODE GENERATION
// =============================================================================

/**
 * Generate ability system code.
 *
 * Creates:
 * - Ability data structures
 * - Ability constants
 * - Ability execution functions
 * - Cost calculation helpers
 */
internal fun CodeGenerator.generateAbilitySystem() {
    val abilities = game.abilities
    if (abilities.isEmpty()) return

    line("// =============================================================================")
    line("// ABILITY SYSTEM")
    line("// =============================================================================")
    line()

    // Generate category constants
    generateAbilityCategoryConstants()

    // Generate aspect constants
    generateAspectConstants()

    // Generate ability data for each ability
    for ((index, ability) in abilities.withIndex()) {
        ability.abilityIndex = index
        generateAbilityData(ability)
    }

    // Generate ability lookup tables
    generateAbilityLookupTables(abilities)

    // Forward declaration for banked dispatch function
    line("// Forward declaration for banked ability dispatch")
    line("void _execute_ability_dispatch(UINT8 ability_id) BANKED;")
    line()

    // Generate ability helper functions FIRST (used by individual execute functions)
    generateAbilityHelperFunctions()

    // Generate individual ability execute functions (call helpers, called by dispatch)
    for (ability in abilities) {
        if (ability.hasExecute) {
            generateAbilityExecuteFunction(ability)
        }
    }

    // Generate dispatch function LAST (calls individual execute functions)
    generateAbilityDispatchFunction(abilities)

    // Generate ability unlock system (character ability flags and grant/revoke helpers)
    generateAbilityUnlockSystem()
}

/** Generate ability category constants. */
private fun CodeGenerator.generateAbilityCategoryConstants() {
    line("// Ability category constants")
    line("#define ABILITY_CAT_PHYSICAL 0u")
    line("#define ABILITY_CAT_MAGIC 1u")
    line("#define ABILITY_CAT_SUPPORT 2u")
    line("#define ABILITY_CAT_SPECIAL 3u")
    line()
}

/** Generate aspect constants. */
private fun CodeGenerator.generateAspectConstants() {
    line("// Aspect constants for damage calculation")
    for ((index, aspect) in Aspect.entries.withIndex()) {
        line("#define ASPECT_${aspect.name} ${index}u")
    }
    line()
}

/** Generate data for a single ability. */
private fun CodeGenerator.generateAbilityData(ability: Ability) {
    val abilityName = ability.id.uppercase()

    line("// -----------------------------------------------------------------------------")
    line("// Ability: ${ability.displayName} (${ability.id})")
    line("// -----------------------------------------------------------------------------")
    line()

    // Ability index constant
    line("#define ABILITY_${abilityName}_ID ${ability.abilityIndex}u")

    // Ability properties
    line("#define ABILITY_${abilityName}_POWER ${ability.power}u")
    line("#define ABILITY_${abilityName}_ASPECT ASPECT_${ability.aspect.name}")
    line("#define ABILITY_${abilityName}_CATEGORY ABILITY_CAT_${ability.category.name}")
    line("#define ABILITY_${abilityName}_TARGETING ${targetingModeValue(ability.targeting)}")

    // Cost
    when (val cost = ability.cost) {
        is AbilityCost.SP -> {
            line("#define ABILITY_${abilityName}_COST_TYPE 0u // SP")
            line("#define ABILITY_${abilityName}_COST ${cost.amount}u")
        }
        is AbilityCost.HP -> {
            line("#define ABILITY_${abilityName}_COST_TYPE 1u // HP")
            line("#define ABILITY_${abilityName}_COST ${cost.amount}u")
        }
        is AbilityCost.HPPercent -> {
            line("#define ABILITY_${abilityName}_COST_TYPE 2u // HP%")
            line("#define ABILITY_${abilityName}_COST ${cost.percent}u")
        }
        is AbilityCost.Free -> {
            line("#define ABILITY_${abilityName}_COST_TYPE 3u // Free")
            line("#define ABILITY_${abilityName}_COST 0u")
        }
    }

    // Level requirement
    line("#define ABILITY_${abilityName}_LEVEL ${ability.levelRequirement}u")

    // Usage flags
    val battleFlag = if (ability.usableInBattle) 1 else 0
    val fieldFlag = if (ability.usableOutOfBattle) 1 else 0
    line("#define ABILITY_${abilityName}_IN_BATTLE ${battleFlag}u")
    line("#define ABILITY_${abilityName}_IN_FIELD ${fieldFlag}u")

    line()
}

/** Convert targeting mode to integer value. */
private fun targetingModeValue(mode: TargetingMode): String =
    when (mode) {
        TargetingMode.SINGLE_ENEMY -> "0u"
        TargetingMode.ALL_ENEMIES -> "1u"
        TargetingMode.SINGLE_ALLY -> "2u"
        TargetingMode.ALL_ALLIES -> "3u"
        TargetingMode.SELF -> "4u"
        TargetingMode.NONE -> "255u"
    }

/** Generate ability lookup tables. */
private fun CodeGenerator.generateAbilityLookupTables(abilities: List<Ability>) {
    line("// Ability count")
    line("#define ABILITY_COUNT ${abilities.size}u")
    line()

    // Power table
    line("// Ability power lookup")
    line("static const UINT8 _ability_power_table[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { "${it.power}u" })
    indent--
    line("};")
    line()

    // SP cost table
    line("// Ability SP cost lookup")
    line("static const UINT8 _ability_sp_cost_table[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { "${it.spCost}u" })
    indent--
    line("};")
    line()

    // Aspect table
    line("// Ability aspect lookup")
    line("static const UINT8 _ability_aspect_table[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { "ASPECT_${it.aspect.name}" })
    indent--
    line("};")
    line()

    // Category table
    line("// Ability category lookup")
    line("static const UINT8 _ability_category_table[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { "ABILITY_CAT_${it.category.name}" })
    indent--
    line("};")
    line()

    // Targeting mode table
    line("// Ability targeting mode lookup")
    line("static const UINT8 _ability_targeting_table[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { targetingModeValue(it.targeting) })
    indent--
    line("};")
    line()
}

/** Generate ability helper functions (used by individual execute functions). */
private fun CodeGenerator.generateAbilityHelperFunctions() {
    line("// =============================================================================")
    line("// ABILITY EXECUTION HELPERS")
    line("// =============================================================================")
    line()

    // Current ability context variables
    line("// Ability execution context")
    line("static UINT8 _ability_caster = 0u;")
    line("static UINT8 _ability_target_count = 0u;")
    line("static UINT8 _ability_targets[MAX_PARTY_SIZE + MAX_ENEMY_SLOTS];")
    line()

    // Check if character can afford ability
    line("// Check if character can afford an ability")
    line("static UINT8 _ability_can_afford(UINT8 char_idx, UINT8 ability_id) {")
    indent++
    line("UINT8 cost = _ability_sp_cost_table[ability_id];")
    line("UINT16 current_sp = _party_get_stat(char_idx, STAT_SP);")
    line("return current_sp >= cost ? 1u : 0u;")
    indent--
    line("}")
    line()

    // Deduct ability cost
    line("// Deduct ability cost from caster")
    line("static void _ability_deduct_cost(UINT8 char_idx, UINT8 ability_id) {")
    indent++
    line("UINT8 cost = _ability_sp_cost_table[ability_id];")
    line("_party_modify_stat(char_idx, STAT_SP, -(INT16)cost);")
    indent--
    line("}")
    line()

    // Deal damage to targets
    line("// Deal damage from ability to all targets (uses lookup tables)")
    line("static void _ability_deal_damage_to_targets(UINT8 ability_id) {")
    indent++
    line("UINT8 power = _ability_power_table[ability_id];")
    line("UINT8 aspect = _ability_aspect_table[ability_id];")
    line("UINT8 category = _ability_category_table[ability_id];")
    line("UINT8 atk = (category == ABILITY_CAT_MAGIC) ?")
    line("    _party_get_stat(_ability_caster, STAT_MATK) :")
    line("    _party_get_stat(_ability_caster, STAT_ATK);")
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_combat_deal_damage(_ability_caster, target, atk * power / 100u, aspect);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Deal damage with custom power/aspect
    line("// Deal damage with custom power and aspect values")
    line("static void _ability_deal_damage_custom(UINT8 power, UINT8 aspect) {")
    indent++
    line("// Use physical ATK by default for custom damage")
    line("UINT8 atk = _party_get_stat(_ability_caster, STAT_ATK);")
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_combat_deal_damage(_ability_caster, target, atk * power / 100u, aspect);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Heal targets
    line("// Heal all targets")
    line("static void _ability_heal_targets(UINT8 power) {")
    indent++
    line("UINT8 matk = _party_get_stat(_ability_caster, STAT_MATK);")
    line("UINT16 heal_amount = (UINT16)matk * power / 100u;")
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_combat_heal(target, heal_amount);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Apply status effect to targets
    line("// Apply status effect to targets")
    line("static void _ability_apply_effect(UINT8 effect_id, UINT8 chance) {")
    indent++
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("if ((_rand() % 100u) < chance) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_status_apply(target, effect_id);")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Apply status effect to caster
    line("// Apply status effect to caster")
    line("static void _ability_apply_effect_to_self(UINT8 effect_id) {")
    indent++
    line("_status_apply(_ability_caster, effect_id);")
    indent--
    line("}")
    line()

    // Drain (damage + heal caster)
    line("// Drain HP from targets and heal caster")
    line("static void _ability_drain(UINT8 power, UINT8 aspect, UINT8 heal_percent) {")
    indent++
    line("UINT16 total_damage = 0u;")
    line("UINT8 atk = _party_get_stat(_ability_caster, STAT_ATK);")
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("UINT16 dmg = (UINT16)atk * power / 100u;")
    line("total_damage += _combat_deal_damage(_ability_caster, target, dmg, aspect);")
    indent--
    line("}")
    line("UINT16 heal = total_damage * heal_percent / 100u;")
    line("_combat_heal(_ability_caster, heal);")
    indent--
    line("}")
    line()

    // Instant kill
    line("// Attempt instant kill on targets")
    line("static void _ability_instant_kill(UINT8 chance, UINT8 ignore_immunity) {")
    indent++
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("// Check immunity unless ignored")
    line("if (!ignore_immunity && _combatant_has_immunity(target, IMMUNITY_INSTANT_KILL)) {")
    indent++
    line("continue;")
    indent--
    line("}")
    line("// Roll for instant kill")
    line("if ((_rand() % 100u) < chance) {")
    indent++
    line("_combatant_set_hp(target, 0u);")
    line("_show_battle_message(\"Instant death!\");")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Cure debuffs
    line("// Remove all debuffs from targets")
    line("static void _ability_cure_debuffs(void) {")
    indent++
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_status_clear_debuffs(target);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Restore SP
    line("// Restore SP to targets")
    line("static void _ability_restore_sp(UINT8 amount) {")
    indent++
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_party_modify_stat(target, STAT_SP, (INT16)amount);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Full heal
    line("// Fully heal targets (HP and SP to max)")
    line("static void _ability_full_heal(void) {")
    indent++
    line("for (UINT8 i = 0u; i < _ability_target_count; i++) {")
    indent++
    line("UINT8 target = _ability_targets[i];")
    line("_combatant_full_heal(target);")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate the ability dispatch function that routes to individual execute functions (banked). */
private fun CodeGenerator.generateAbilityDispatchFunction(abilities: List<Ability>) {
    val abilitiesWithExecute = abilities.filter { it.hasExecute }

    // Bank this function - it's 128+ lines and called only when abilities are used
    setBank(codeBankCombat)
    line("// Dispatch to ability-specific execute function")
    line("void _execute_ability_dispatch(UINT8 ability_id) BANKED {")
    indent++

    if (abilitiesWithExecute.isEmpty()) {
        line("// No abilities with custom execute blocks - use default damage")
        line("_ability_deal_damage_to_targets(ability_id);")
    } else {
        line("switch (ability_id) {")
        indent++

        for (ability in abilitiesWithExecute) {
            line("case ABILITY_${ability.id.uppercase()}_ID:")
            indent++
            line("${ability.id}_execute();")
            line("break;")
            indent--
        }

        line("default:")
        indent++
        line("// Fallback: use lookup table damage calculation")
        line("_ability_deal_damage_to_targets(ability_id);")
        line("break;")
        indent--

        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate individual ability execute function. */
private fun CodeGenerator.generateAbilityExecuteFunction(ability: Ability) {
    line(
        "// Execute function for ${ability.displayName} (ID: ${ability.id}, index: ${ability.abilityIndex})"
    )
    line(
        "// Power: ${ability.power}, Aspect: ${ability.aspect.name}, Targeting: ${ability.targeting.name}"
    )
    line("static void ${ability.id}_execute(void) {")
    indent++

    for (stmt in ability.executeStatements) {
        generateStatement(stmt)
    }

    indent--
    line("}")
    line()
}

// =============================================================================
// ABILITY STATEMENT GENERATION
// =============================================================================

/**
 * Handle ability-related IR statements.
 *
 * @return true if this was an ability statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateAbilityStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRAbilityDealDamage -> {
            generateAbilityDealDamage(stmt)
            true
        }
        is IRAbilityHeal -> {
            generateAbilityHeal(stmt)
            true
        }
        is IRAbilityApplyEffect -> {
            generateAbilityApplyEffect(stmt)
            true
        }
        is IRAbilityApplyEffectToSelf -> {
            generateAbilityApplyEffectToSelf(stmt)
            true
        }
        is IRAbilityDrain -> {
            generateAbilityDrain(stmt)
            true
        }
        is IRAbilityPlayAnimation -> {
            generateAbilityPlayAnimation(stmt)
            true
        }
        is IRAbilityPlaySfx -> {
            generateAbilityPlaySfx(stmt)
            true
        }
        is IRAbilityShowMessage -> {
            generateAbilityShowMessage(stmt)
            true
        }
        is IRAbilityInstantKill -> {
            generateAbilityInstantKill(stmt)
            true
        }
        is IRAbilityCureDebuffs -> {
            generateAbilityCureDebuffs(stmt)
            true
        }
        is IRAbilityRestoreSp -> {
            generateAbilityRestoreSp(stmt)
            true
        }
        is IRAbilityFullHeal -> {
            generateAbilityFullHeal(stmt)
            true
        }
        is IRUseAbility -> {
            generateUseAbility(stmt)
            true
        }
        is IRGrantAbility -> {
            generateGrantAbility(stmt)
            true
        }
        is IRRevokeAbility -> {
            generateRevokeAbility(stmt)
            true
        }
        else -> false
    }

/** Generate ability deal damage. */
private fun CodeGenerator.generateAbilityDealDamage(stmt: IRAbilityDealDamage) {
    lineWithSource(
        "// Deal damage (power: ${stmt.power}, aspect: ${stmt.aspect.name})",
        stmt.sourceLocation,
        stmt.abilityId,
    )

    // Check if using default ability power/aspect or custom values
    val ability = game.abilities.find { it.id == stmt.abilityId }
    val usingDefaultPower = ability?.power == stmt.power
    val usingDefaultAspect = ability?.aspect == stmt.aspect

    if (usingDefaultPower && usingDefaultAspect) {
        // Use lookup table version for code efficiency
        line("_ability_deal_damage_to_targets(ABILITY_${stmt.abilityId.uppercase()}_ID);")
    } else {
        // Use custom power/aspect values
        line("_ability_deal_damage_custom(${stmt.power}u, ASPECT_${stmt.aspect.name});")
    }
}

/** Generate ability heal. */
private fun CodeGenerator.generateAbilityHeal(stmt: IRAbilityHeal) {
    lineWithSource("// Heal targets (power: ${stmt.power})", stmt.sourceLocation, stmt.abilityId)
    line("_ability_heal_targets(${stmt.power}u);")
}

/** Generate ability apply effect. */
private fun CodeGenerator.generateAbilityApplyEffect(stmt: IRAbilityApplyEffect) {
    lineWithSource(
        "// Apply effect (id: ${stmt.effectId}, chance: ${stmt.chance}%)",
        stmt.sourceLocation,
        stmt.abilityId,
    )
    line("_ability_apply_effect(${stmt.effectId}u, ${stmt.chance}u);")
}

/** Generate ability apply effect to self. */
private fun CodeGenerator.generateAbilityApplyEffectToSelf(stmt: IRAbilityApplyEffectToSelf) {
    lineWithSource(
        "// Apply effect to caster (id: ${stmt.effectId})",
        stmt.sourceLocation,
        stmt.abilityId,
    )
    line("_ability_apply_effect_to_self(${stmt.effectId}u);")
}

/** Generate ability drain. */
private fun CodeGenerator.generateAbilityDrain(stmt: IRAbilityDrain) {
    lineWithSource(
        "// Drain (power: ${stmt.power}, heal: ${stmt.healPercent}%)",
        stmt.sourceLocation,
        stmt.abilityId,
    )
    line("_ability_drain(${stmt.power}u, ASPECT_${stmt.aspect.name}, ${stmt.healPercent}u);")
}

/** Generate ability play animation. */
private fun CodeGenerator.generateAbilityPlayAnimation(stmt: IRAbilityPlayAnimation) {
    lineWithSource("// Play animation: ${stmt.animationId}", stmt.sourceLocation, stmt.abilityId)
    line("_play_animation(\"${stmt.animationId}\");")
}

/** Generate ability play sfx. */
private fun CodeGenerator.generateAbilityPlaySfx(stmt: IRAbilityPlaySfx) {
    lineWithSource("// Play SFX: ${stmt.sfxId}", stmt.sourceLocation, stmt.abilityId)
    line("play_${stmt.sfxId}();")
}

/** Generate ability show message. */
private fun CodeGenerator.generateAbilityShowMessage(stmt: IRAbilityShowMessage) {
    val escapedMessage = stmt.message.replace("\"", "\\\"")
    lineWithSource("// Show message", stmt.sourceLocation, stmt.abilityId)
    line("_show_battle_message(\"$escapedMessage\");")
}

/** Generate ability instant kill. */
private fun CodeGenerator.generateAbilityInstantKill(stmt: IRAbilityInstantKill) {
    val immunityFlag = if (stmt.ignoreImmunity) "1u" else "0u"
    lineWithSource(
        "// Instant kill (chance: ${stmt.chance}%, ignoreImmunity: ${stmt.ignoreImmunity})",
        stmt.sourceLocation,
        stmt.abilityId,
    )
    line("_ability_instant_kill(${stmt.chance}u, $immunityFlag);")
}

/** Generate ability cure debuffs. */
private fun CodeGenerator.generateAbilityCureDebuffs(stmt: IRAbilityCureDebuffs) {
    lineWithSource("// Cure all debuffs from targets", stmt.sourceLocation, stmt.abilityId)
    line("_ability_cure_debuffs();")
}

/** Generate ability restore SP. */
private fun CodeGenerator.generateAbilityRestoreSp(stmt: IRAbilityRestoreSp) {
    lineWithSource("// Restore SP (amount: ${stmt.amount})", stmt.sourceLocation, stmt.abilityId)
    line("_ability_restore_sp(${stmt.amount}u);")
}

/** Generate ability full heal. */
private fun CodeGenerator.generateAbilityFullHeal(stmt: IRAbilityFullHeal) {
    lineWithSource("// Full heal (HP and SP to max)", stmt.sourceLocation, stmt.abilityId)
    line("_ability_full_heal();")
}

/** Generate use ability statement. */
private fun CodeGenerator.generateUseAbility(stmt: IRUseAbility) {
    val abilityIndex = game.abilities.indexOfFirst { it.id == stmt.abilityId }.coerceAtLeast(0)
    val casterIndex = game.characters.indexOfFirst { it.name == stmt.casterName }.coerceAtLeast(0)

    lineWithSource("// Use ability: ${stmt.abilityId}", stmt.sourceLocation, stmt.abilityId)
    line("_ability_caster = ${casterIndex}u;")
    line("_ability_target_count = ${stmt.targetNames.size}u;")
    for ((i, targetName) in stmt.targetNames.withIndex()) {
        val targetIndex =
            game.characters.indexOfFirst { it.name == targetName }.takeIf { it >= 0 } ?: 0
        line("_ability_targets[$i] = ${targetIndex}u;")
    }
    line("_ability_deduct_cost(${casterIndex}u, ${abilityIndex}u);")

    // Call the execute function if it exists
    val ability = game.abilities.find { it.id == stmt.abilityId }
    if (ability?.hasExecute == true) {
        line("${stmt.abilityId}_execute();")
    }
}

// =============================================================================
// ABILITY EXPRESSION GENERATION
// =============================================================================

/**
 * Generate C expression for ability-related queries.
 *
 * @return the C expression string, or null if not an ability expression
 */
internal fun CodeGenerator.generateAbilityExpr(expr: IRExpression): String? =
    when (expr) {
        is IRCanUseAbility -> {
            val abilityIndex =
                game.abilities.indexOfFirst { it.id == expr.abilityId }.coerceAtLeast(0)
            val charIndex =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            "_ability_can_afford(${charIndex}u, ${abilityIndex}u)"
        }
        is IRHasAbility -> {
            val abilityIndex =
                game.abilities.indexOfFirst { it.id == expr.abilityId }.coerceAtLeast(0)
            val charIndex =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            "_char_has_ability(${charIndex}u, ${abilityIndex}u)"
        }
        is IRGetAbilitySpCost -> {
            val abilityIndex =
                game.abilities.indexOfFirst { it.id == expr.abilityId }.coerceAtLeast(0)
            "_ability_sp_cost_table[${abilityIndex}u]"
        }
        is IRGetAbilityPower -> {
            val abilityIndex =
                game.abilities.indexOfFirst { it.id == expr.abilityId }.coerceAtLeast(0)
            "_ability_power_table[${abilityIndex}u]"
        }
        is IRAbilityUnlockCheck -> {
            val charIndex =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            "(_party_get_level(${charIndex}u) >= ${expr.unlockLevel}u)"
        }
        else -> null
    }

// =============================================================================
// ABILITY UNLOCK CODE GENERATION
// =============================================================================

/**
 * Generate ability unlock tracking variables and helpers.
 *
 * Creates:
 * - Ability flag variables per character (bitfield for unlocked abilities)
 * - Helper functions to grant/revoke/check abilities
 */
internal fun CodeGenerator.generateAbilityUnlockSystem() {
    val abilities = game.abilities
    val characters = game.characters.filter { it.hasStats }
    if (abilities.isEmpty() || characters.isEmpty()) return

    line("// =============================================================================")
    line("// ABILITY UNLOCK SYSTEM")
    line("// =============================================================================")
    line()

    // Calculate number of bytes needed for ability flags (1 bit per ability)
    val flagBytes = (abilities.size + 7) / 8 // Round up to full bytes

    // Generate ability flag variables for each character
    line("// Ability flags per character (1 bit per ability)")
    for (character in characters) {
        line("static UINT8 ${character.name}_ability_flags[$flagBytes] = {0};")
    }
    line()

    // Generate ability unlock level table
    line("// Ability unlock level lookup table")
    line("static const UINT8 _ability_unlock_level[${abilities.size}] = {")
    indent++
    line(abilities.joinToString(", ") { "${it.levelRequirement}u" })
    indent--
    line("};")
    line()

    // Generate helper to check if character has ability
    line("// Check if character has unlocked an ability")
    line("static UINT8 _char_has_ability(UINT8 char_idx, UINT8 ability_idx) {")
    indent++
    line("UINT8 byte_idx = ability_idx >> 3u;  // ability_idx / 8")
    line("UINT8 bit_mask = 1u << (ability_idx & 7u);  // ability_idx % 8")
    line("switch (char_idx) {")
    indent++
    for ((i, character) in characters.withIndex()) {
        line("case ${i}u: return (${character.name}_ability_flags[byte_idx] & bit_mask) != 0u;")
    }
    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate helper to grant ability
    line("// Grant an ability to a character")
    line("static void _char_grant_ability(UINT8 char_idx, UINT8 ability_idx) {")
    indent++
    line("UINT8 byte_idx = ability_idx >> 3u;")
    line("UINT8 bit_mask = 1u << (ability_idx & 7u);")
    line("switch (char_idx) {")
    indent++
    for ((i, character) in characters.withIndex()) {
        line("case ${i}u: ${character.name}_ability_flags[byte_idx] |= bit_mask; break;")
    }
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate helper to revoke ability
    line("// Revoke an ability from a character")
    line("static void _char_revoke_ability(UINT8 char_idx, UINT8 ability_idx) {")
    indent++
    line("UINT8 byte_idx = ability_idx >> 3u;")
    line("UINT8 bit_mask = 1u << (ability_idx & 7u);")
    line("switch (char_idx) {")
    indent++
    for ((i, character) in characters.withIndex()) {
        line("case ${i}u: ${character.name}_ability_flags[byte_idx] &= ~bit_mask; break;")
    }
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate helper to check if ability should unlock at current level
    line("// Check if ability unlocks at the given level")
    line("static UINT8 _ability_unlocks_at_level(UINT8 ability_idx, UINT8 level) {")
    indent++
    line("return _ability_unlock_level[ability_idx] == level ? 1u : 0u;")
    indent--
    line("}")
    line()

    // Generate helper to grant all abilities for a level
    line("// Grant all abilities that unlock at the given level")
    line("static void _grant_abilities_for_level(UINT8 char_idx, UINT8 level) {")
    indent++
    line("for (UINT8 i = 0u; i < ABILITY_COUNT; i++) {")
    indent++
    line("if (_ability_unlock_level[i] == level) {")
    indent++
    line("_char_grant_ability(char_idx, i);")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate grant ability statement. */
private fun CodeGenerator.generateGrantAbility(stmt: IRGrantAbility) {
    val charIndex =
        if (stmt.characterName == "_levelup_char") {
            "_levelup_char_idx"
        } else {
            val idx =
                game.characters.indexOfFirst { it.name == stmt.characterName }.coerceAtLeast(0)
            "${idx}u"
        }
    lineWithSource("// Grant ability: ${stmt.abilityId}", stmt.sourceLocation, stmt.characterName)
    line("_char_grant_ability($charIndex, ${stmt.abilityIndex}u);")
}

/** Generate revoke ability statement. */
private fun CodeGenerator.generateRevokeAbility(stmt: IRRevokeAbility) {
    val charIndex =
        if (stmt.characterName == "_levelup_char") {
            "_levelup_char_idx"
        } else {
            val idx =
                game.characters.indexOfFirst { it.name == stmt.characterName }.coerceAtLeast(0)
            "${idx}u"
        }
    lineWithSource("// Revoke ability: ${stmt.abilityId}", stmt.sourceLocation, stmt.characterName)
    line("_char_revoke_ability($charIndex, ${stmt.abilityIndex}u);")
}
