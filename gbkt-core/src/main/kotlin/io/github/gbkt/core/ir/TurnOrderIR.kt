/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.InitiativeMethod

// =============================================================================
// TURN ORDER IR NODES
// =============================================================================

/** IR node for configuring turn order system. */
data class IRTurnOrderConfig(
    val method: InitiativeMethod,
    val randomVariance: Int,
    val maxCombatants: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for calculating initiative for all combatants. */
data object IRCalculateInitiative : IRStatement {
    override val sourceLocation: SourceLocation? = null
}

/** IR node for sorting turn order by initiative. */
data object IRSortTurnOrder : IRStatement {
    override val sourceLocation: SourceLocation? = null
}

/** IR node for resetting turn order. */
data object IRResetTurnOrder : IRStatement {
    override val sourceLocation: SourceLocation? = null
}

/** IR node for advancing to next turn. */
data object IRNextTurn : IRStatement {
    override val sourceLocation: SourceLocation? = null
}

// =============================================================================
// TURN ORDER QUERY EXPRESSIONS
// =============================================================================

/** IR expression for getting current combatant index. */
data object IRGetCurrentCombatantIndex : IRExpression

/** IR expression for getting current combatant's initiative value. */
data object IRGetCurrentInitiative : IRExpression

/** IR expression for checking if current combatant is a party member. */
data object IRIsCurrentCombatantParty : IRExpression

/** IR expression for getting turn count in current round. */
data object IRGetTurnCount : IRExpression

/** IR expression for getting round number. */
data object IRGetRoundNumber : IRExpression

/** IR expression for checking if all combatants have acted this round. */
data object IRIsRoundComplete : IRExpression
