/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * Deep copy and parameter substitution utilities for IR nodes.
 *
 * These are used by LogicBlock to create independent copies of recorded IR and to substitute
 * placeholder parameters with actual expressions.
 */

// =============================================================================
// EXPRESSION DEEP COPY
// =============================================================================

/**
 * Create a deep copy of an expression, optionally substituting variables.
 *
 * @param substitutions Map of variable names to replacement expressions
 */
fun IRExpression.deepCopy(substitutions: Map<String, IRExpression> = emptyMap()): IRExpression {
    return when (this) {
        is IRLiteral -> this // Immutable, no copy needed
        is IRVar -> substitutions[name] ?: this
        is IRBinary -> IRBinary(left.deepCopy(substitutions), op, right.deepCopy(substitutions))
        is IRUnary -> IRUnary(op, operand.deepCopy(substitutions))
        is IRTernary ->
            IRTernary(
                cond.deepCopy(substitutions),
                then.deepCopy(substitutions),
                otherwise.deepCopy(substitutions),
            )
        is IRCallExpr -> IRCallExpr(function, args.map { it.deepCopy(substitutions) })
        is IRArrayAccess -> IRArrayAccess(array, index.deepCopy(substitutions))

        // Pool expressions
        is IRPoolActiveCount -> this
        is IRPoolHasSpace -> this
        is IRPoolIsFull -> this
        is IRPoolEntityVar -> this

        // Save expressions
        is IRSaveFieldRead -> this
        is IRSaveExists -> IRSaveExists(saveName, slot.deepCopy(substitutions))
        is IRSaveArrayAccess ->
            IRSaveArrayAccess(saveName, fieldName, index.deepCopy(substitutions))

        // Camera expressions
        is IRCameraX -> this
        is IRCameraY -> this
        is IRTransitionActive -> this

        // Link expressions
        is IRLinkConnected -> this
        is IRLinkHasData -> this
        is IRLinkReceivedData -> this
        is IRLinkIsMaster -> this

        // Cutscene expressions
        is IRCutsceneIsPlaying -> this
        is IRCutsceneIsComplete -> this

        // Input buffer expressions
        is IRInputBufferActive -> this
        is IRInputBufferConsumed -> this

        // Handle any other expression types
        else -> this
    }
}

// =============================================================================
// STATEMENT DEEP COPY
// =============================================================================

/**
 * Create a deep copy of a statement, optionally substituting variables.
 *
 * @param substitutions Map of variable names to replacement expressions
 */
fun IRStatement.deepCopy(substitutions: Map<String, IRExpression> = emptyMap()): IRStatement {
    return when (this) {
        // Core statements
        is IRAssign -> copy(value = value.deepCopy(substitutions))
        is IRIf ->
            copy(
                condition = condition.deepCopy(substitutions),
                then = then.map { it.deepCopy(substitutions) },
                otherwise = otherwise?.map { it.deepCopy(substitutions) },
            )
        is IRWhen ->
            copy(
                branches =
                    branches.map { branch ->
                        WhenBranch(
                            branch.condition.deepCopy(substitutions),
                            branch.body.map { it.deepCopy(substitutions) },
                        )
                    },
                otherwise = otherwise?.map { it.deepCopy(substitutions) },
            )
        is IRWhile ->
            copy(
                condition = condition.deepCopy(substitutions),
                body = body.map { it.deepCopy(substitutions) },
            )
        is IRFor -> copy(body = body.map { it.deepCopy(substitutions) })
        is IRCall -> copy(args = args.map { it.deepCopy(substitutions) })
        is IRSceneChange -> this
        is IRRaw -> this
        is IRArrayAssign ->
            copy(index = index.deepCopy(substitutions), value = value.deepCopy(substitutions))

        // Pool statements
        is IRPoolUpdate -> this
        is IRPoolSpawn -> copy(initStatements = initStatements.map { it.deepCopy(substitutions) })
        is IRPoolSpawnAt ->
            copy(
                x = x.deepCopy(substitutions),
                y = y.deepCopy(substitutions),
                initStatements = initStatements.map { it.deepCopy(substitutions) },
            )
        is IRPoolTrySpawn ->
            copy(
                initStatements = initStatements.map { it.deepCopy(substitutions) },
                elseStatements = elseStatements.map { it.deepCopy(substitutions) },
            )
        is IRPoolDespawn -> copy(indexExpr = indexExpr.deepCopy(substitutions))
        is IRPoolDespawnAll -> this
        is IRPoolForEach -> copy(bodyStatements = bodyStatements.map { it.deepCopy(substitutions) })
        is IRPoolDespawnWhere -> copy(condition = condition.deepCopy(substitutions))

        // Animation statements
        is IRAnimationPlay -> this
        is IRAnimationStop -> this
        is IRAnimationSetFrame -> this
        is IRAnimationPause -> this
        is IRAnimationResume -> this
        is IRAnimationSetSpeed -> this
        is IRAnimationQueue -> this

        // Camera statements
        is IRCameraSetPosition -> copy(x = x.deepCopy(substitutions), y = y.deepCopy(substitutions))
        is IRCameraUpdate -> this
        is IRCameraFollow -> this
        is IRCameraStopFollow -> this
        is IRCameraSnapTo -> copy(x = x.deepCopy(substitutions), y = y.deepCopy(substitutions))
        is IRCameraSetBounds -> this
        is IRCameraShake -> this
        is IRCameraShakeStop -> this

        // Transition statements
        is IRTransitionFadeOut -> copy(onComplete = onComplete.map { it.deepCopy(substitutions) })
        is IRTransitionFadeIn -> copy(onComplete = onComplete.map { it.deepCopy(substitutions) })
        is IRTransitionFlash -> this
        is IRTransitionWipe -> copy(onComplete = onComplete.map { it.deepCopy(substitutions) })
        is IRTransitionIris ->
            copy(
                centerX = centerX.deepCopy(substitutions),
                centerY = centerY.deepCopy(substitutions),
                onComplete = onComplete.map { it.deepCopy(substitutions) },
            )
        is IRComposedTransition -> this
        is IRTransitionCancel -> this

        // Save statements
        is IRSaveLoad -> copy(slot = slot.deepCopy(substitutions))
        is IRSaveSave -> copy(slot = slot.deepCopy(substitutions))
        is IRSaveErase -> copy(slot = slot.deepCopy(substitutions))
        is IRSaveCopy ->
            copy(
                fromSlot = fromSlot.deepCopy(substitutions),
                toSlot = toSlot.deepCopy(substitutions),
            )
        is IRSaveFieldWrite -> copy(value = value.deepCopy(substitutions))
        is IRSaveArrayWrite ->
            copy(index = index.deepCopy(substitutions), value = value.deepCopy(substitutions))

        // State machine statements
        is IRStateMachineUpdate -> this
        is IRStateMachineStart -> this
        is IRStateMachineGoto -> this

        // System statements
        is IRPaletteApply -> this
        is IRPaletteSetColor -> this
        is IRPaletteFade -> copy(progress = progress.deepCopy(substitutions))
        is IRSpriteSetPalette -> this
        is IRPaletteFlash -> this
        is IRClearScreen -> this
        is IRShowSprites -> this
        is IRShowBackground -> this
        is IRPrintAt ->
            copy(
                parts =
                    parts.map { part ->
                        when (part) {
                            is TextPart.Literal -> part
                            is TextPart.Variable ->
                                TextPart.Variable(part.expr.deepCopy(substitutions), part.format)
                        }
                    }
            )

        // Entity statements
        is IREntityUpdate -> this
        is IRPhysicsApply -> this
        is IRPhysicsWorldUpdate -> this
        is IRCollisionResponse -> this

        // Link statements
        is IRLinkInit -> this
        is IRLinkUpdate -> this
        is IRLinkSend -> copy(data = data.deepCopy(substitutions))

        // Cutscene statements
        is IRCutsceneStart -> this
        is IRCutsceneUpdate -> this
        is IRCutsceneSkip -> this

        // Tween and input buffer statements
        is IRTween -> copy(from = from.deepCopy(substitutions), to = to.deepCopy(substitutions))
        is IRInputBufferDecl -> this
        is IRInputBufferReset -> this
        is IRInputBufferFill -> this

        // Handle any other statement types
        else -> this
    }
}

// =============================================================================
// CONVENIENCE FUNCTIONS
// =============================================================================

/** Substitute a single parameter in a statement. */
fun IRStatement.substituteParameter(parameterName: String, value: IRExpression): IRStatement =
    deepCopy(mapOf(parameterName to value))

/** Substitute a single parameter in an expression. */
fun IRExpression.substituteParameter(parameterName: String, value: IRExpression): IRExpression =
    deepCopy(mapOf(parameterName to value))

/** Deep copy a list of statements. */
fun List<IRStatement>.deepCopy(
    substitutions: Map<String, IRExpression> = emptyMap()
): List<IRStatement> = map { it.deepCopy(substitutions) }
