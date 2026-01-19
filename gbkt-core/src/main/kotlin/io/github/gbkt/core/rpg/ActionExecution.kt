/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRActionExecute
import io.github.gbkt.core.ir.IRActionPipelineConfig
import io.github.gbkt.core.ir.IRActionQueueAdd
import io.github.gbkt.core.ir.IRActionQueueClear
import io.github.gbkt.core.ir.IRGetActionCount
import io.github.gbkt.core.ir.IRGetCurrentActionType
import io.github.gbkt.core.ir.IRHasQueuedActions
import io.github.gbkt.core.ir.IRNextAction
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// ACTION EXECUTION PIPELINE
// =============================================================================

/** Represents an action in the queue. */
data class QueuedAction(
    val actorIndex: Int,
    val type: BattleActionType,
    val targetIndices: List<Int>,
    val abilityId: Int? = null,
    val itemId: Int? = null,
)

/** Action execution result. */
data class ActionResult(
    val success: Boolean,
    val damage: Int = 0,
    val healing: Int = 0,
    val statusApplied: String? = null,
    val targetDefeated: Boolean = false,
    val actorDefeated: Boolean = false,
)

/** Action pipeline configuration. */
data class ActionPipelineConfig(
    val name: String,
    val maxQueueSize: Int = 16,
    val onActionStart: List<IRStatement> = emptyList(),
    val onActionComplete: List<IRStatement> = emptyList(),
    val onActionFailed: List<IRStatement> = emptyList(),
)

/** Handle for action pipeline runtime operations. */
class ActionPipelineHandle internal constructor(internal val config: ActionPipelineConfig) {
    /** Queue an attack action. */
    fun queueAttack(actorIndex: Int, targetIndex: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.ATTACK,
                        actorIndex = actorIndex,
                        targetIndices = listOf(targetIndex),
                    )
                )
        }
    }

    /** Queue an ability action. */
    fun queueAbility(actorIndex: Int, abilityId: Int, targetIndices: List<Int>) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.ABILITY,
                        actorIndex = actorIndex,
                        targetIndices = targetIndices,
                        abilityId = abilityId,
                    )
                )
        }
    }

    /** Queue an item use action. */
    fun queueItem(actorIndex: Int, itemId: Int, targetIndex: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.ITEM,
                        actorIndex = actorIndex,
                        targetIndices = listOf(targetIndex),
                        itemId = itemId,
                    )
                )
        }
    }

    /** Queue a defend action. */
    fun queueDefend(actorIndex: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.DEFEND,
                        actorIndex = actorIndex,
                        targetIndices = listOf(actorIndex),
                    )
                )
        }
    }

    /** Queue a flee action. */
    fun queueFlee(actorIndex: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.FLEE,
                        actorIndex = actorIndex,
                        targetIndices = emptyList(),
                    )
                )
        }
    }

    /** Queue a wait action. */
    fun queueWait(actorIndex: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRActionQueueAdd(
                        pipelineName = config.name,
                        actionType = BattleActionType.WAIT,
                        actorIndex = actorIndex,
                        targetIndices = emptyList(),
                    )
                )
        }
    }

    /** Execute the next action in the queue. */
    fun executeNext() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRActionExecute(config.name))
        }
    }

    /** Advance to next action without executing. */
    fun skipToNext() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRNextAction(config.name))
        }
    }

    /** Clear all queued actions. */
    fun clearQueue() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRActionQueueClear(config.name))
        }
    }

    // =========================================================================
    // State Queries
    // =========================================================================

    /** Are there actions in the queue? */
    val hasActions: Condition
        get() = Condition(IRHasQueuedActions(config.name))

    /** Number of actions in queue */
    val actionCount: Expr
        get() = Expr(IRGetActionCount(config.name))

    /** Current action type being executed */
    val currentActionType: Expr
        get() = Expr(IRGetCurrentActionType(config.name))
}

// =============================================================================
// ACTION PIPELINE BUILDER
// =============================================================================

/** Builder for action pipeline configuration. */
@GbktDsl
class ActionPipelineBuilder internal constructor(private val name: String) {
    private var maxQueueSize = 16
    private var onActionStartStatements: List<IRStatement> = emptyList()
    private var onActionCompleteStatements: List<IRStatement> = emptyList()
    private var onActionFailedStatements: List<IRStatement> = emptyList()

    /** Set maximum queue size */
    fun maxQueueSize(size: Int) {
        require(size in 1..32) { "Max queue size must be 1-32" }
        this.maxQueueSize = size
    }

    /** Called when action execution starts */
    fun onActionStart(block: ActionCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { ActionCallbackScope().block() }
        onActionStartStatements = recorder.statements
    }

    /** Called when action execution completes */
    fun onActionComplete(block: ActionCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { ActionCallbackScope().block() }
        onActionCompleteStatements = recorder.statements
    }

    /** Called when action execution fails */
    fun onActionFailed(block: ActionCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { ActionCallbackScope().block() }
        onActionFailedStatements = recorder.statements
    }

    internal fun build() =
        ActionPipelineConfig(
            name = name,
            maxQueueSize = maxQueueSize,
            onActionStart = onActionStartStatements,
            onActionComplete = onActionCompleteStatements,
            onActionFailed = onActionFailedStatements,
        )
}

/** Scope available in action callbacks. */
@GbktDsl
class ActionCallbackScope(private val systemName: String = "battle") {
    /** Transition to battle state */
    fun transitionTo(state: BattleState) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRBattleStateTransition(
                    systemName = systemName,
                    targetState = state,
                )
            )
    }

    /** Emit arbitrary statement */
    internal fun emit(statement: IRStatement) {
        RecordingContext.require().emit(statement)
    }
}

// =============================================================================
// DSL FUNCTIONS
// =============================================================================

/**
 * Create an action execution pipeline.
 *
 * Example:
 * ```kotlin
 * val actions = actionPipeline("battle") {
 *     maxQueueSize(16)
 *     onActionStart { /* play animation */ }
 *     onActionComplete { transitionTo(BattleState.SHOW_RESULT) }
 * }
 * ```
 */
fun actionPipeline(
    name: String,
    block: ActionPipelineBuilder.() -> Unit = {},
): ActionPipelineHandle {
    val builder = ActionPipelineBuilder(name)
    builder.block()
    val config = builder.build()
    return ActionPipelineHandle(config)
}

/** Register action pipeline for code generation. */
fun registerActionPipeline(pipeline: ActionPipelineHandle) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRActionPipelineConfig(pipeline.config))
    }
}
