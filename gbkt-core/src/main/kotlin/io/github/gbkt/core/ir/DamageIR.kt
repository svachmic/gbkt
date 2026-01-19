/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.DamageCalculation

// =============================================================================
// DAMAGE CALCULATION IR NODES
// =============================================================================

/**
 * IR node for calculating damage using the standard formula.
 *
 * The calculation follows:
 * - Base = (ATK or MATK) * power / 100
 * - Reduced = Base - (DEF or MDEF) [if not ignoring defense]
 * - Final = max(1, Reduced * aspectModifier / 100) + flatBonus
 */
data class IRDamageCalculate(val calculation: DamageCalculation) : IRExpression

/**
 * IR node for dealing calculated damage to a target.
 *
 * This combines damage calculation and application in one statement.
 */
data class IRDealDamage(
    val targetName: String,
    val calculation: DamageCalculation,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for dealing flat (uncalculated) damage to a target.
 *
 * Used for fixed damage amounts that bypass the normal formula.
 */
data class IRDealFlatDamage(
    val targetName: String,
    val amount: IRExpression,
    val aspect: Aspect,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
