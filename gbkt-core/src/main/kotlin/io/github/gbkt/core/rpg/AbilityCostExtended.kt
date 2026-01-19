/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl

// =============================================================================
// EXTENDED ABILITY COST SYSTEM
// =============================================================================

/**
 * Extended ability cost system.
 *
 * Supports multi-resource costs, escalating costs, and custom cost logic.
 *
 * Usage:
 * ```kotlin
 * val powerfulSpell by ability {
 *     name("Powerful Spell")
 *
 *     // Multi-resource cost: 5 SP AND 10 HP
 *     cost {
 *         sp(5)
 *         hp(10)
 *     }
 * }
 *
 * val lastResort by ability {
 *     name("Last Resort")
 *
 *     // Costs HP percentage
 *     cost {
 *         hpPercent(25)
 *     }
 * }
 *
 * val escalatingAbility by ability {
 *     name("Escalating Power")
 *
 *     // Cost increases each use
 *     cost {
 *         sp(base = 5, perUse = 2, max = 20)
 *     }
 * }
 *
 * val chainAbility by ability {
 *     name("Chain Attack")
 *
 *     // Costs more if used consecutively
 *     cost {
 *         sp(5)
 *         consecutiveMultiplier(150)  // 150% cost if used last turn
 *     }
 * }
 *
 * val guardBreak by ability {
 *     name("Guard Break")
 *
 *     // Costs resource (like a shield gauge)
 *     cost {
 *         custom("guard", 50)
 *     }
 * }
 * ```
 */
data class ExtendedAbilityCost(
    /** Base costs (multiple can be specified) */
    val baseCosts: List<ResourceCost>,
    /** Escalation configuration (if any) */
    val escalation: CostEscalation?,
    /** Consecutive use multiplier percentage (100 = no change) */
    val consecutiveMultiplier: Int,
    /** Cooldown in turns (0 = no cooldown) */
    val cooldown: Int,
    /** Charge turns required before use (0 = instant) */
    val chargeTurns: Int,
    /** Custom conditions for usability */
    val customConditions: List<CostCondition>,
) {
    /** Check if this is a simple single-resource cost */
    val isSimple: Boolean
        get() =
            baseCosts.size == 1 &&
                escalation == null &&
                consecutiveMultiplier == 100 &&
                cooldown == 0 &&
                chargeTurns == 0 &&
                customConditions.isEmpty()

    /** Convert to legacy AbilityCost if possible */
    fun toLegacy(): AbilityCost? {
        if (!isSimple || baseCosts.isEmpty()) return null

        return when (val cost = baseCosts.first()) {
            is ResourceCost.SP -> AbilityCost.SP(cost.amount)
            is ResourceCost.HP -> AbilityCost.HP(cost.amount)
            is ResourceCost.HPPercent -> AbilityCost.HPPercent(cost.percent)
            is ResourceCost.Free -> AbilityCost.Free
            else -> null
        }
    }

    companion object {
        /** Create from legacy AbilityCost */
        fun fromLegacy(cost: AbilityCost): ExtendedAbilityCost {
            val resourceCost =
                when (cost) {
                    is AbilityCost.SP -> ResourceCost.SP(cost.amount)
                    is AbilityCost.HP -> ResourceCost.HP(cost.amount)
                    is AbilityCost.HPPercent -> ResourceCost.HPPercent(cost.percent)
                    AbilityCost.Free -> ResourceCost.Free
                }
            return ExtendedAbilityCost(
                baseCosts = listOf(resourceCost),
                escalation = null,
                consecutiveMultiplier = 100,
                cooldown = 0,
                chargeTurns = 0,
                customConditions = emptyList(),
            )
        }

        /** Create a free (no cost) ability */
        val FREE =
            ExtendedAbilityCost(
                baseCosts = listOf(ResourceCost.Free),
                escalation = null,
                consecutiveMultiplier = 100,
                cooldown = 0,
                chargeTurns = 0,
                customConditions = emptyList(),
            )
    }
}

/** Individual resource cost. */
sealed class ResourceCost {
    /** Costs SP (Skill Points/Magic Points) */
    data class SP(val amount: Int) : ResourceCost()

    /** Costs HP (Health Points) - character must survive */
    data class HP(val amount: Int) : ResourceCost()

    /** Costs HP as percentage of max HP */
    data class HPPercent(val percent: Int) : ResourceCost()

    /** Costs a custom stat/resource */
    data class Custom(val resourceName: String, val amount: Int) : ResourceCost()

    /** Costs an item */
    data class Item(val itemId: String, val quantity: Int = 1) : ResourceCost()

    /** No cost for this resource */
    data object Free : ResourceCost()
}

/**
 * Cost escalation configuration.
 *
 * For abilities that cost more with each use.
 */
data class CostEscalation(
    /** Resource that escalates (SP or HP) */
    val resource: EscalatingResource,
    /** Base cost */
    val baseCost: Int,
    /** Cost increase per use */
    val increasePerUse: Int,
    /** Maximum cost (cap) */
    val maxCost: Int,
    /** Whether cost resets at end of battle */
    val resetOnBattleEnd: Boolean,
)

enum class EscalatingResource {
    SP,
    HP,
}

/** Custom condition for ability usability. */
sealed class CostCondition {
    /** Must have HP above threshold */
    data class HPAbove(val amount: Int) : CostCondition()

    /** Must have HP below threshold */
    data class HPBelow(val amount: Int) : CostCondition()

    /** Must have a status effect */
    data class HasStatus(val effectId: String) : CostCondition()

    /** Must not have a status effect */
    data class NoStatus(val effectId: String) : CostCondition()

    /** Must be a specific class */
    data class ClassRequired(val classId: String) : CostCondition()

    /** Must have an item equipped */
    data class ItemEquipped(val itemId: String) : CostCondition()

    /** Custom condition (C code expression) */
    data class Custom(val expression: String) : CostCondition()
}

// =============================================================================
// EXTENDED COST BUILDER
// =============================================================================

/** Builder for extended ability costs. */
@GbktDsl
class ExtendedCostBuilder {
    private val baseCosts = mutableListOf<ResourceCost>()
    private var escalation: CostEscalation? = null
    private var consecutiveMultiplier: Int = 100
    private var cooldown: Int = 0
    private var chargeTurns: Int = 0
    private val customConditions = mutableListOf<CostCondition>()

    // ========= Basic Resource Costs =========

    /** Add SP cost */
    fun sp(amount: Int) {
        baseCosts.add(ResourceCost.SP(amount))
    }

    /** Add HP cost */
    fun hp(amount: Int) {
        baseCosts.add(ResourceCost.HP(amount))
    }

    /** Add HP percentage cost */
    fun hpPercent(percent: Int) {
        baseCosts.add(ResourceCost.HPPercent(percent))
    }

    /** Add custom resource cost */
    fun custom(resourceName: String, amount: Int) {
        baseCosts.add(ResourceCost.Custom(resourceName, amount))
    }

    /** Add item cost */
    fun item(itemId: String, quantity: Int = 1) {
        baseCosts.add(ResourceCost.Item(itemId, quantity))
    }

    /** Mark as free (no cost) */
    fun free() {
        baseCosts.clear()
        baseCosts.add(ResourceCost.Free)
    }

    // ========= Escalating Costs =========

    /** Add escalating SP cost */
    fun sp(base: Int, perUse: Int, max: Int, resetOnBattleEnd: Boolean = true) {
        baseCosts.removeAll { it is ResourceCost.SP }
        escalation =
            CostEscalation(
                resource = EscalatingResource.SP,
                baseCost = base,
                increasePerUse = perUse,
                maxCost = max,
                resetOnBattleEnd = resetOnBattleEnd,
            )
    }

    /** Add escalating HP cost */
    fun hp(base: Int, perUse: Int, max: Int, resetOnBattleEnd: Boolean = true) {
        baseCosts.removeAll { it is ResourceCost.HP }
        escalation =
            CostEscalation(
                resource = EscalatingResource.HP,
                baseCost = base,
                increasePerUse = perUse,
                maxCost = max,
                resetOnBattleEnd = resetOnBattleEnd,
            )
    }

    // ========= Cost Modifiers =========

    /** Set consecutive use multiplier (percentage) */
    fun consecutiveMultiplier(percent: Int) {
        consecutiveMultiplier = percent
    }

    /** Set cooldown in turns */
    fun cooldown(turns: Int) {
        cooldown = turns
    }

    /** Set charge turns before use */
    fun chargeTurns(turns: Int) {
        chargeTurns = turns
    }

    // ========= Conditions =========

    /** Require HP above threshold */
    fun requireHPAbove(amount: Int) {
        customConditions.add(CostCondition.HPAbove(amount))
    }

    /** Require HP below threshold (desperation moves) */
    fun requireHPBelow(amount: Int) {
        customConditions.add(CostCondition.HPBelow(amount))
    }

    /** Require a status effect */
    fun requireStatus(effectId: String) {
        customConditions.add(CostCondition.HasStatus(effectId))
    }

    /** Require not having a status effect */
    fun requireNoStatus(effectId: String) {
        customConditions.add(CostCondition.NoStatus(effectId))
    }

    /** Require specific class */
    fun requireClass(classId: String) {
        customConditions.add(CostCondition.ClassRequired(classId))
    }

    /** Require item equipped */
    fun requireEquipped(itemId: String) {
        customConditions.add(CostCondition.ItemEquipped(itemId))
    }

    /** Add custom condition */
    fun requireCondition(expression: String) {
        customConditions.add(CostCondition.Custom(expression))
    }

    internal fun build(): ExtendedAbilityCost {
        // Ensure at least Free if no costs specified
        if (baseCosts.isEmpty() && escalation == null) {
            baseCosts.add(ResourceCost.Free)
        }

        return ExtendedAbilityCost(
            baseCosts = baseCosts.toList(),
            escalation = escalation,
            consecutiveMultiplier = consecutiveMultiplier,
            cooldown = cooldown,
            chargeTurns = chargeTurns,
            customConditions = customConditions.toList(),
        )
    }
}

// =============================================================================
// HELPER EXTENSIONS
// =============================================================================

/** Create extended cost from simple values */
fun simpleSpCost(amount: Int) =
    ExtendedAbilityCost(
        baseCosts = listOf(ResourceCost.SP(amount)),
        escalation = null,
        consecutiveMultiplier = 100,
        cooldown = 0,
        chargeTurns = 0,
        customConditions = emptyList(),
    )

fun simpleHpCost(amount: Int) =
    ExtendedAbilityCost(
        baseCosts = listOf(ResourceCost.HP(amount)),
        escalation = null,
        consecutiveMultiplier = 100,
        cooldown = 0,
        chargeTurns = 0,
        customConditions = emptyList(),
    )

/** Check if an extended cost can be afforded */
fun ExtendedAbilityCost.canAfford(
    currentSp: Int,
    currentHp: Int,
    maxHp: Int,
    customResources: Map<String, Int> = emptyMap(),
): Boolean {
    for (cost in baseCosts) {
        when (cost) {
            is ResourceCost.SP -> if (currentSp < cost.amount) return false
            is ResourceCost.HP -> if (currentHp <= cost.amount) return false
            is ResourceCost.HPPercent -> if (currentHp <= maxHp * cost.percent / 100) return false
            is ResourceCost.Custom -> {
                val current = customResources[cost.resourceName] ?: 0
                if (current < cost.amount) return false
            }
            is ResourceCost.Item -> {
                // Item check needs inventory system - assume true for now
            }
            ResourceCost.Free -> {
                /* Always affordable */
            }
        }
    }
    return true
}
