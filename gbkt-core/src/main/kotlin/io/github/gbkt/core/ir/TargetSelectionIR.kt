/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.TargetSelectionConfig
import io.github.gbkt.core.rpg.TargetingMode

// =============================================================================
// TARGET SELECTION IR NODES
// =============================================================================

/** IR node for configuring target selection system. */
data class IRTargetSelectionConfig(
    val config: TargetSelectionConfig,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for starting target selection. */
data class IRStartTargetSelection(
    val systemName: String,
    val mode: TargetingMode,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for moving target cursor. */
data class IRMoveTargetCursor(
    val systemName: String,
    val deltaX: Int,
    val deltaY: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for selecting current target. */
data class IRSelectTarget(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for selecting all valid targets. */
data class IRSelectAllTargets(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for confirming target selection. */
data class IRConfirmTarget(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for processing target selection. */
data class IRTargetSelectionTick(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// TARGET SELECTION QUERY EXPRESSIONS
// =============================================================================

/** IR expression for checking if target selection is active. */
data class IRIsTargetSelectionActive(val systemName: String) : IRExpression

/** IR expression for getting current target cursor index. */
data class IRGetSelectedTargetIndex(val systemName: String) : IRExpression

/** IR expression for getting number of selected targets. */
data class IRGetSelectedTargetCount(val systemName: String) : IRExpression

/** IR expression for checking if target at index is alive. */
data class IRIsTargetAlive(val targetIndex: Int) : IRExpression

/** IR expression for checking if target at index is an enemy. */
data class IRIsTargetEnemy(val targetIndex: Int) : IRExpression
