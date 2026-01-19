/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IRCalculateInitiative
import io.github.gbkt.core.ir.IRNextTurn
import io.github.gbkt.core.ir.IRResetTurnOrder
import io.github.gbkt.core.ir.IRSortTurnOrder
import io.github.gbkt.core.ir.IRTurnOrderConfig

// =============================================================================
// TURN ORDER SYSTEM
// =============================================================================

/** Initiative calculation methods. */
enum class InitiativeMethod {
    /** Pure agility-based: initiative = agility */
    AGILITY_ONLY,

    /** Agility + random factor: initiative = agility + random(0, variance) */
    AGILITY_PLUS_RANDOM,

    /** Speed tiers: Fast > Normal > Slow actions */
    SPEED_TIERS,

    /** Fixed order: party first, then enemies */
    PARTY_FIRST,

    /** Fixed order: enemies first, then party */
    ENEMIES_FIRST,

    /** Round-robin: alternate between party and enemies */
    ALTERNATING,
}

/** Speed tier for actions (used with SPEED_TIERS initiative method). */
enum class SpeedTier(val priority: Int) {
    /** Instant actions (flee, certain items) */
    INSTANT(0),

    /** Fast actions (quick attacks) */
    FAST(1),

    /** Normal speed actions */
    NORMAL(2),

    /** Slow actions (powerful spells) */
    SLOW(3),
}

/** Represents a combatant in the turn order. */
data class Combatant(val name: String, val isPartyMember: Boolean, val index: Int)

/** Turn order configuration and state. */
class TurnOrderSystem
internal constructor(val method: InitiativeMethod, val randomVariance: Int, val maxCombatants: Int)

/** Builder for turn order configuration. */
@GbktDsl
class TurnOrderBuilder internal constructor() {
    private var method: InitiativeMethod = InitiativeMethod.AGILITY_PLUS_RANDOM
    private var randomVariance: Int = 10
    private var maxCombatants: Int = 8

    /** Set the initiative calculation method */
    fun method(value: InitiativeMethod) {
        method = value
    }

    /** Set random variance for AGILITY_PLUS_RANDOM method (0-255) */
    fun randomVariance(value: Int) {
        require(value in 0..255) { "Random variance must be 0-255" }
        randomVariance = value
    }

    /** Set maximum combatants (party + enemies) */
    fun maxCombatants(value: Int) {
        require(value in 2..16) { "Max combatants must be 2-16" }
        maxCombatants = value
    }

    internal fun build(): TurnOrderSystem =
        TurnOrderSystem(
            method = method,
            randomVariance = randomVariance,
            maxCombatants = maxCombatants,
        )
}

/**
 * Create a turn order configuration.
 *
 * Usage:
 * ```kotlin
 * val turnOrder = turnOrder {
 *     method(InitiativeMethod.AGILITY_PLUS_RANDOM)
 *     randomVariance(10)
 * }
 * ```
 */
fun turnOrder(block: TurnOrderBuilder.() -> Unit = {}): TurnOrderSystem {
    val builder = TurnOrderBuilder()
    builder.block()
    return builder.build()
}

// =============================================================================
// TURN ORDER OPERATIONS
// =============================================================================

/** Register turn order configuration for code generation. */
fun registerTurnOrder(system: TurnOrderSystem) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRTurnOrderConfig(
                    method = system.method,
                    randomVariance = system.randomVariance,
                    maxCombatants = system.maxCombatants,
                )
            )
    }
}

/**
 * Calculate initiative for all combatants and sort turn order.
 *
 * This should be called at the start of each round.
 */
fun calculateTurnOrder() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRCalculateInitiative)
    }
}

/** Sort the turn order based on calculated initiative values. */
fun sortTurnOrder() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRSortTurnOrder)
    }
}

/** Reset turn order for a new battle or round. */
fun resetTurnOrder() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRResetTurnOrder)
    }
}

/**
 * Advance to the next combatant's turn.
 *
 * Returns to the first combatant if at the end of the order.
 */
fun nextTurn() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRNextTurn)
    }
}

// =============================================================================
// INITIATIVE MODIFIERS
// =============================================================================

/** Temporary initiative modifier (e.g., from status effects). */
data class InitiativeModifier(val targetName: String, val modifier: Int, val duration: Int = 1)

/** Initiative bonus/penalty constants. */
object InitiativeBonus {
    /** Haste effect bonus */
    const val HASTE = 50

    /** Slow effect penalty */
    const val SLOW = -50

    /** Surprise attack bonus */
    const val SURPRISE = 100

    /** Ambush penalty (caught off-guard) */
    const val AMBUSHED = -100

    /** Heavy armor penalty */
    const val HEAVY_ARMOR = -10

    /** Light armor bonus */
    const val LIGHT_ARMOR = 5
}
