/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.core.rpg.CostCondition
import io.github.gbkt.core.rpg.EscalatingResource
import io.github.gbkt.core.rpg.ResourceCost

// =============================================================================
// EXTENDED ABILITY COST CODE GENERATION
// =============================================================================

/**
 * Generate extended ability cost system code.
 *
 * Creates:
 * - Multi-resource cost checking
 * - Escalating cost tracking
 * - Cooldown and charge tracking
 * - Custom condition evaluation
 */
internal fun GBDKCodeGenerator.generateExtendedAbilityCostSystem() {
    val abilitiesWithExtendedCost =
        game.abilities.filter { ability ->
            val cost = ability.extendedCost
            cost != null && !cost.isSimple
        }
    if (abilitiesWithExtendedCost.isEmpty()) return

    line("// =============================================================================")
    line("// EXTENDED ABILITY COST SYSTEM")
    line("// =============================================================================")
    line()

    // Generate cost type constants
    generateExtendedCostTypeConstants()

    // Generate escalating cost tracking
    val hasEscalatingCosts = abilitiesWithExtendedCost.any { it.extendedCost?.escalation != null }
    if (hasEscalatingCosts) {
        generateEscalatingCostTracking(abilitiesWithExtendedCost)
    }

    // Generate cooldown tracking
    val hasCooldowns = abilitiesWithExtendedCost.any { (it.extendedCost?.cooldown ?: 0) > 0 }
    if (hasCooldowns) {
        generateCooldownTracking(abilitiesWithExtendedCost)
    }

    // Generate charge tracking
    val hasCharges = abilitiesWithExtendedCost.any { (it.extendedCost?.chargeTurns ?: 0) > 0 }
    if (hasCharges) {
        generateChargeTracking(abilitiesWithExtendedCost)
    }

    // Generate extended cost check function
    generateExtendedCostCheckFunction(abilitiesWithExtendedCost)

    // Generate extended cost deduction function
    generateExtendedCostDeductFunction(abilitiesWithExtendedCost)
}

/** Generate extended cost type constants. */
private fun GBDKCodeGenerator.generateExtendedCostTypeConstants() {
    line("// Extended cost resource type constants")
    line("#define COST_TYPE_SP 0u")
    line("#define COST_TYPE_HP 1u")
    line("#define COST_TYPE_HP_PERCENT 2u")
    line("#define COST_TYPE_CUSTOM 3u")
    line("#define COST_TYPE_ITEM 4u")
    line("#define COST_TYPE_FREE 5u")
    line()
}

/** Generate escalating cost tracking variables. */
private fun GBDKCodeGenerator.generateEscalatingCostTracking(abilities: List<Ability>) {
    val escalatingAbilities = abilities.filter { it.extendedCost?.escalation != null }
    if (escalatingAbilities.isEmpty()) return

    line("// =============================================================================")
    line("// ESCALATING COST TRACKING")
    line("// =============================================================================")
    line()

    line("// Use count per ability (for escalating costs)")
    line("static UINT8 _ability_use_count[${game.abilities.size}];")
    line()

    // Generate escalation configuration per ability
    for (ability in escalatingAbilities) {
        val esc = ability.extendedCost!!.escalation!!
        val name = ability.id.uppercase()
        line("// Escalation config for ${ability.displayName}")
        line("#define ESC_${name}_BASE ${esc.baseCost}u")
        line("#define ESC_${name}_PER_USE ${esc.increasePerUse}u")
        line("#define ESC_${name}_MAX ${esc.maxCost}u")
        line(
            "#define ESC_${name}_RESOURCE ${if (esc.resource == EscalatingResource.SP) "COST_TYPE_SP" else "COST_TYPE_HP"}"
        )
        line("#define ESC_${name}_RESET_ON_BATTLE ${if (esc.resetOnBattleEnd) "1u" else "0u"}")
    }
    line()

    // Generate escalating cost calculation function
    line("// Calculate current escalating cost for ability")
    line("static UINT8 _get_escalating_cost(UINT8 ability_id) {")
    indent++
    line("UINT8 uses = _ability_use_count[ability_id];")
    line("switch (ability_id) {")
    indent++

    for (ability in escalatingAbilities) {
        val name = ability.id.uppercase()
        line("case ABILITY_${name}_ID: {")
        indent++
        line("UINT8 cost = ESC_${name}_BASE + (uses * ESC_${name}_PER_USE);")
        line("return (cost > ESC_${name}_MAX) ? ESC_${name}_MAX : cost;")
        indent--
        line("}")
    }

    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Increment ability use count")
    line("static void _increment_ability_uses(UINT8 ability_id) {")
    indent++
    line("if (_ability_use_count[ability_id] < 255u) {")
    indent++
    line("_ability_use_count[ability_id]++;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Reset escalating costs (call on battle end)")
    line("static void _reset_escalating_costs(void) {")
    indent++
    for (ability in
        escalatingAbilities.filter { it.extendedCost?.escalation?.resetOnBattleEnd == true }) {
        line("_ability_use_count[ABILITY_${ability.id.uppercase()}_ID] = 0u;")
    }
    indent--
    line("}")
    line()
}

/** Generate cooldown tracking. */
private fun GBDKCodeGenerator.generateCooldownTracking(abilities: List<Ability>) {
    val cooldownAbilities = abilities.filter { (it.extendedCost?.cooldown ?: 0) > 0 }
    if (cooldownAbilities.isEmpty()) return

    line("// =============================================================================")
    line("// ABILITY COOLDOWN TRACKING")
    line("// =============================================================================")
    line()

    line("// Cooldown counters per character per ability (remaining turns)")
    line("static UINT8 _ability_cooldowns[MAX_PARTY_SIZE][ABILITY_COUNT];")
    line()

    // Generate cooldown configuration
    line("// Cooldown durations per ability")
    line("static const UINT8 _ability_cooldown_duration[ABILITY_COUNT] = {")
    indent++
    val cooldowns = game.abilities.map { ability -> "${ability.extendedCost?.cooldown ?: 0}u" }
    line(cooldowns.joinToString(", "))
    indent--
    line("};")
    line()

    line("// Check if ability is on cooldown")
    line("static UINT8 _is_on_cooldown(UINT8 char_idx, UINT8 ability_id) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return 0u;")
    line("return _ability_cooldowns[char_idx][ability_id] > 0u ? 1u : 0u;")
    indent--
    line("}")
    line()

    line("// Start cooldown for ability")
    line("static void _start_cooldown(UINT8 char_idx, UINT8 ability_id) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return;")
    line("_ability_cooldowns[char_idx][ability_id] = _ability_cooldown_duration[ability_id];")
    indent--
    line("}")
    line()

    line("// Tick cooldowns at end of turn")
    line("static void _tick_cooldowns(UINT8 char_idx) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return;")
    line("for (UINT8 i = 0u; i < ABILITY_COUNT; i++) {")
    indent++
    line("if (_ability_cooldowns[char_idx][i] > 0u) {")
    indent++
    line("_ability_cooldowns[char_idx][i]--;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate charge tracking. */
private fun GBDKCodeGenerator.generateChargeTracking(abilities: List<Ability>) {
    val chargeAbilities = abilities.filter { (it.extendedCost?.chargeTurns ?: 0) > 0 }
    if (chargeAbilities.isEmpty()) return

    line("// =============================================================================")
    line("// ABILITY CHARGE TRACKING")
    line("// =============================================================================")
    line()

    line("// Charge progress per character (255 = no ability charging)")
    line("static UINT8 _charging_ability[MAX_PARTY_SIZE];")
    line("static UINT8 _charge_progress[MAX_PARTY_SIZE];")
    line()

    // Generate charge configuration
    line("// Charge turns required per ability")
    line("static const UINT8 _ability_charge_turns[ABILITY_COUNT] = {")
    indent++
    val charges = game.abilities.map { ability -> "${ability.extendedCost?.chargeTurns ?: 0}u" }
    line(charges.joinToString(", "))
    indent--
    line("};")
    line()

    line("// Start charging an ability")
    line("static void _start_charging(UINT8 char_idx, UINT8 ability_id) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return;")
    line("_charging_ability[char_idx] = ability_id;")
    line("_charge_progress[char_idx] = 0u;")
    indent--
    line("}")
    line()

    line("// Tick charge progress (call each turn)")
    line("static UINT8 _tick_charge(UINT8 char_idx) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return 0u;")
    line("UINT8 ability_id = _charging_ability[char_idx];")
    line("if (ability_id == 255u) return 0u; // Not charging")
    line()
    line("_charge_progress[char_idx]++;")
    line("if (_charge_progress[char_idx] >= _ability_charge_turns[ability_id]) {")
    indent++
    line("// Charge complete!")
    line("_charging_ability[char_idx] = 255u;")
    line("return ability_id; // Return ability that's ready")
    indent--
    line("}")
    line("return 255u; // Still charging")
    indent--
    line("}")
    line()

    line("// Check if charging")
    line("static UINT8 _is_charging(UINT8 char_idx) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return 0u;")
    line("return _charging_ability[char_idx] != 255u ? 1u : 0u;")
    indent--
    line("}")
    line()
}

/** Generate extended cost check function. */
private fun GBDKCodeGenerator.generateExtendedCostCheckFunction(abilities: List<Ability>) {
    line("// =============================================================================")
    line("// EXTENDED COST CHECKING")
    line("// =============================================================================")
    line()

    line("// Check if character can afford extended ability cost")
    line("static UINT8 _can_afford_extended(UINT8 char_idx, UINT8 ability_id) {")
    indent++

    line("switch (ability_id) {")
    indent++

    for (ability in abilities) {
        val ext = ability.extendedCost ?: continue
        if (ext.isSimple) continue

        line("case ABILITY_${ability.id.uppercase()}_ID: {")
        indent++

        // Check cooldown first
        if (ext.cooldown > 0) {
            line("if (_is_on_cooldown(char_idx, ability_id)) return 0u;")
        }

        // Check each resource cost
        for (cost in ext.baseCosts) {
            when (cost) {
                is ResourceCost.SP -> {
                    line("if (_party_get_stat(char_idx, STAT_SP) < ${cost.amount}u) return 0u;")
                }
                is ResourceCost.HP -> {
                    line(
                        "if (_party_get_stat(char_idx, STAT_HP) <= ${cost.amount}u) return 0u; // Must survive"
                    )
                }
                is ResourceCost.HPPercent -> {
                    line(
                        "if (_party_get_stat(char_idx, STAT_HP) <= (_party_get_max_stat(char_idx, STAT_HP) * ${cost.percent}u / 100u)) return 0u;"
                    )
                }
                is ResourceCost.Custom -> {
                    line("// Custom resource: ${cost.resourceName}")
                    line(
                        "// if (_get_custom_resource(char_idx, \"${cost.resourceName}\") < ${cost.amount}u) return 0u;"
                    )
                }
                is ResourceCost.Item -> {
                    line("// Item cost: ${cost.itemId} x${cost.quantity}")
                    line(
                        "// if (_inventory_count(\"${cost.itemId}\") < ${cost.quantity}u) return 0u;"
                    )
                }
                ResourceCost.Free -> {
                    // No cost to check
                }
            }
        }

        // Check escalating cost if applicable
        val checkEsc = ext.escalation
        if (checkEsc != null) {
            val statCheck = if (checkEsc.resource == EscalatingResource.SP) "STAT_SP" else "STAT_HP"
            line("UINT8 esc_cost = _get_escalating_cost(ability_id);")
            line("if (_party_get_stat(char_idx, $statCheck) < esc_cost) return 0u;")
        }

        // Check custom conditions
        for (cond in ext.customConditions) {
            when (cond) {
                is CostCondition.HPAbove -> {
                    line("if (_party_get_stat(char_idx, STAT_HP) < ${cond.amount}u) return 0u;")
                }
                is CostCondition.HPBelow -> {
                    line("if (_party_get_stat(char_idx, STAT_HP) >= ${cond.amount}u) return 0u;")
                }
                is CostCondition.HasStatus -> {
                    line("// if (!_has_status(char_idx, \"${cond.effectId}\")) return 0u;")
                }
                is CostCondition.NoStatus -> {
                    line("// if (_has_status(char_idx, \"${cond.effectId}\")) return 0u;")
                }
                is CostCondition.ClassRequired -> {
                    line(
                        "// if (_get_class(char_idx) != CLASS_${cond.classId.uppercase()}) return 0u;"
                    )
                }
                is CostCondition.ItemEquipped -> {
                    line("// if (!_has_equipped(char_idx, \"${cond.itemId}\")) return 0u;")
                }
                is CostCondition.Custom -> {
                    line("// Custom condition: ${cond.expression}")
                }
            }
        }

        line("return 1u;")
        indent--
        line("}")
    }

    line("default:")
    indent++
    line("// Fall back to simple SP check")
    line("return _ability_can_afford(char_idx, ability_id);")
    indent--

    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate extended cost deduction function. */
private fun GBDKCodeGenerator.generateExtendedCostDeductFunction(abilities: List<Ability>) {
    line("// =============================================================================")
    line("// EXTENDED COST DEDUCTION")
    line("// =============================================================================")
    line()

    line("// Deduct extended ability cost from character")
    line("static void _deduct_extended_cost(UINT8 char_idx, UINT8 ability_id) {")
    indent++

    line("switch (ability_id) {")
    indent++

    for (ability in abilities) {
        val ext = ability.extendedCost ?: continue
        if (ext.isSimple) continue

        line("case ABILITY_${ability.id.uppercase()}_ID: {")
        indent++

        // Deduct each resource cost
        for (cost in ext.baseCosts) {
            when (cost) {
                is ResourceCost.SP -> {
                    line("_party_modify_stat(char_idx, STAT_SP, -${cost.amount});")
                }
                is ResourceCost.HP -> {
                    line("_party_modify_stat(char_idx, STAT_HP, -${cost.amount});")
                }
                is ResourceCost.HPPercent -> {
                    line(
                        "_party_modify_stat(char_idx, STAT_HP, -((INT16)(_party_get_max_stat(char_idx, STAT_HP) * ${cost.percent}u / 100u)));"
                    )
                }
                is ResourceCost.Custom -> {
                    line(
                        "// _modify_custom_resource(char_idx, \"${cost.resourceName}\", -${cost.amount});"
                    )
                }
                is ResourceCost.Item -> {
                    line("// _inventory_remove(\"${cost.itemId}\", ${cost.quantity}u);")
                }
                ResourceCost.Free -> {
                    // Nothing to deduct
                }
            }
        }

        // Handle escalating cost
        val deductEsc = ext.escalation
        if (deductEsc != null) {
            val statMod = if (deductEsc.resource == EscalatingResource.SP) "STAT_SP" else "STAT_HP"
            line(
                "_party_modify_stat(char_idx, $statMod, -(INT16)_get_escalating_cost(ability_id));"
            )
            line("_increment_ability_uses(ability_id);")
        }

        // Start cooldown
        if (ext.cooldown > 0) {
            line("_start_cooldown(char_idx, ability_id);")
        }

        line("break;")
        indent--
        line("}")
    }

    line("default:")
    indent++
    line("// Fall back to simple SP deduction")
    line("_ability_deduct_cost(char_idx, ability_id);")
    line("break;")
    indent--

    indent--
    line("}")
    indent--
    line("}")
    line()
}
