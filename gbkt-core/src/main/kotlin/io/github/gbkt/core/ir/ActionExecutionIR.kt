/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.ActionPipelineConfig
import io.github.gbkt.core.rpg.BattleActionType

// =============================================================================
// ACTION EXECUTION IR NODES
// =============================================================================

/** IR node for configuring action pipeline. */
data class IRActionPipelineConfig(
    val config: ActionPipelineConfig,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for adding action to queue. */
data class IRActionQueueAdd(
    val pipelineName: String,
    val actionType: BattleActionType,
    val actorIndex: Int,
    val targetIndices: List<Int>,
    val abilityId: Int? = null,
    val itemId: Int? = null,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for executing next action. */
data class IRActionExecute(
    val pipelineName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for advancing to next action without executing. */
data class IRNextAction(
    val pipelineName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for clearing action queue. */
data class IRActionQueueClear(
    val pipelineName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// ACTION EXECUTION QUERY EXPRESSIONS
// =============================================================================

/** IR expression for checking if there are queued actions. */
data class IRHasQueuedActions(val pipelineName: String) : IRExpression

/** IR expression for getting action queue count. */
data class IRGetActionCount(val pipelineName: String) : IRExpression

/** IR expression for getting current action type. */
data class IRGetCurrentActionType(val pipelineName: String) : IRExpression

/** IR expression for getting current actor index. */
data class IRGetCurrentActorIndex(val pipelineName: String) : IRExpression

/** IR expression for getting current target count. */
data class IRGetCurrentTargetCount(val pipelineName: String) : IRExpression
