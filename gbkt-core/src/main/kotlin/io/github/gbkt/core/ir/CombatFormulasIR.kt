/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.CombatFormulas

// =============================================================================
// COMBAT FORMULAS IR NODES
// =============================================================================

/**
 * IR node for registering combat formula configuration.
 *
 * This generates:
 * - Hit roll lookup tables (for D20-based or percentage-based)
 * - Damage variance tables (for multiplier-based variance)
 * - Critical hit threshold constants
 * - Fumble threshold constants
 * - Helper functions for hit checks, crit checks, damage rolls
 */
data class IRCombatFormulas(
    val formulas: CombatFormulas,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR expression for performing a hit check.
 *
 * Returns true if the attack hits, false if it misses. The actual check depends on the configured
 * HitFormulaStrategy.
 */
data class IRHitCheck(val attackerName: String, val defenderName: String) : IRExpression

/**
 * IR expression for performing a critical hit check.
 *
 * Returns true if the attack is a critical hit. The actual check depends on the configured
 * CriticalFormulaStrategy.
 */
data class IRCriticalCheck(val attackerName: String) : IRExpression

/**
 * IR expression for performing a fumble check.
 *
 * Returns true if the attack is a fumble (critical miss). Only applicable when fumble is enabled in
 * combat formulas.
 */
data class IRFumbleCheck(val attackerName: String) : IRExpression

/**
 * IR expression for applying damage variance to a base damage value.
 *
 * Takes base damage and returns modified damage with variance applied. The variance depends on the
 * configured DamageVarianceStrategy.
 */
data class IRApplyDamageVariance(val baseDamage: IRExpression) : IRExpression

/**
 * IR expression for applying critical multiplier to damage.
 *
 * Takes base damage and returns damage * critMultiplier / 100.
 */
data class IRApplyCriticalMultiplier(val baseDamage: IRExpression) : IRExpression
