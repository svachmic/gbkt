/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.validation

import io.github.gbkt.core.GameValidator
import io.github.gbkt.core.ValidationCategory
import io.github.gbkt.core.ValidationError
import io.github.gbkt.core.ValidationWarning
import io.github.gbkt.core.ir.IRArrayAccess
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile

// =============================================================================
// ARRAY BOUNDS VALIDATION
// =============================================================================

/**
 * Validate array bounds for dynamic indices. Detects provably out-of-bounds accesses and warns on
 * unchecked dynamic access.
 */
internal fun GameValidator.validateArrayBounds() {
    val arrayBounds = game.arrays.associate { it.name to it.size }

    // Scan all scenes
    for ((sceneName, scene) in game.scenes) {
        validateArrayBoundsInStatements(
            scene.onEnter,
            "scene '$sceneName' enter",
            arrayBounds,
            emptyMap(),
        )
        validateArrayBoundsInStatements(
            scene.onFrame,
            "scene '$sceneName' frame",
            arrayBounds,
            emptyMap(),
        )
        validateArrayBoundsInStatements(
            scene.onExit,
            "scene '$sceneName' exit",
            arrayBounds,
            emptyMap(),
        )
    }

    // Scan pools
    for (pool in game.pools) {
        validateArrayBoundsInStatements(
            pool.onFrameStatements,
            "pool '${pool.name}'",
            arrayBounds,
            emptyMap(),
        )
    }

    // Scan state machines
    for (machine in game.stateMachines) {
        for ((stateName, state) in machine.states) {
            val context = "state machine '${machine.name}::$stateName'"
            validateArrayBoundsInStatements(
                state.onEnter,
                "$context onEnter",
                arrayBounds,
                emptyMap(),
            )
            validateArrayBoundsInStatements(
                state.onTick,
                "$context onTick",
                arrayBounds,
                emptyMap(),
            )
            validateArrayBoundsInStatements(
                state.onExit,
                "$context onExit",
                arrayBounds,
                emptyMap(),
            )
        }
    }
}

/** Recursively validate array bounds in statements, tracking known variable ranges from loops. */
private fun GameValidator.validateArrayBoundsInStatements(
    statements: List<IRStatement>,
    context: String,
    arrayBounds: Map<String, Int>,
    knownBounds: Map<String, IntRange>,
) {
    for (stmt in statements) {
        when (stmt) {
            is IRFor -> {
                // Inside loop, counter has known bounds
                val newBounds = knownBounds + (stmt.counter to stmt.range)
                validateArrayBoundsInStatements(stmt.body, context, arrayBounds, newBounds)
            }
            is IRArrayAssign -> {
                validateArrayIndex(stmt.array, stmt.index, context, arrayBounds, knownBounds)
            }
            is IRIf -> {
                validateArrayBoundsInStatements(stmt.then, context, arrayBounds, knownBounds)
                stmt.otherwise?.let {
                    validateArrayBoundsInStatements(it, context, arrayBounds, knownBounds)
                }
                // Also check expressions in condition
                validateArrayBoundsInExpression(stmt.condition, context, arrayBounds, knownBounds)
            }
            is IRWhen -> {
                for (branch in stmt.branches) {
                    validateArrayBoundsInStatements(branch.body, context, arrayBounds, knownBounds)
                    validateArrayBoundsInExpression(
                        branch.condition,
                        context,
                        arrayBounds,
                        knownBounds,
                    )
                }
                stmt.otherwise?.let {
                    validateArrayBoundsInStatements(it, context, arrayBounds, knownBounds)
                }
            }
            is IRWhile -> {
                validateArrayBoundsInStatements(stmt.body, context, arrayBounds, knownBounds)
                validateArrayBoundsInExpression(stmt.condition, context, arrayBounds, knownBounds)
            }
            is IRPoolForEach -> {
                validateArrayBoundsInStatements(
                    stmt.bodyStatements,
                    context,
                    arrayBounds,
                    knownBounds,
                )
            }
            is IRPoolSpawn -> {
                validateArrayBoundsInStatements(
                    stmt.initStatements,
                    context,
                    arrayBounds,
                    knownBounds,
                )
            }
            is IRPoolSpawnAt -> {
                validateArrayBoundsInStatements(
                    stmt.initStatements,
                    context,
                    arrayBounds,
                    knownBounds,
                )
            }
            is IRPoolTrySpawn -> {
                validateArrayBoundsInStatements(
                    stmt.initStatements,
                    context,
                    arrayBounds,
                    knownBounds,
                )
                validateArrayBoundsInStatements(
                    stmt.elseStatements,
                    context,
                    arrayBounds,
                    knownBounds,
                )
            }
            is IRTransitionFadeOut -> {
                validateArrayBoundsInStatements(stmt.onComplete, context, arrayBounds, knownBounds)
            }
            is IRTransitionFadeIn -> {
                validateArrayBoundsInStatements(stmt.onComplete, context, arrayBounds, knownBounds)
            }
            is IRTransitionWipe -> {
                validateArrayBoundsInStatements(stmt.onComplete, context, arrayBounds, knownBounds)
            }
            is IRTransitionIris -> {
                validateArrayBoundsInStatements(stmt.onComplete, context, arrayBounds, knownBounds)
            }
            else -> {
                // Check any expressions in the statement for array access
            }
        }
    }
}

/** Recursively validate array bounds in expressions. */
private fun GameValidator.validateArrayBoundsInExpression(
    expr: IRExpression,
    context: String,
    arrayBounds: Map<String, Int>,
    knownBounds: Map<String, IntRange>,
) {
    when (expr) {
        is IRArrayAccess -> {
            validateArrayIndex(expr.array, expr.index, context, arrayBounds, knownBounds)
            // Also validate nested expressions in the index
            validateArrayBoundsInExpression(expr.index, context, arrayBounds, knownBounds)
        }
        is IRBinary -> {
            validateArrayBoundsInExpression(expr.left, context, arrayBounds, knownBounds)
            validateArrayBoundsInExpression(expr.right, context, arrayBounds, knownBounds)
        }
        is IRUnary -> {
            validateArrayBoundsInExpression(expr.operand, context, arrayBounds, knownBounds)
        }
        is IRTernary -> {
            validateArrayBoundsInExpression(expr.cond, context, arrayBounds, knownBounds)
            validateArrayBoundsInExpression(expr.`then`, context, arrayBounds, knownBounds)
            validateArrayBoundsInExpression(expr.`otherwise`, context, arrayBounds, knownBounds)
        }
        is IRCallExpr -> {
            expr.args.forEach { arg ->
                validateArrayBoundsInExpression(arg, context, arrayBounds, knownBounds)
            }
        }
        // Leaf expressions - no nested array access possible
        is IRLiteral,
        is IRVar -> {
            // No array access in these expression types
        }
        else -> {
            // Other expression types - may contain IRExpression fields, but most are
            // domain-specific and don't typically contain array accesses. Skip for now.
        }
    }
}

/** Validate a single array index for bounds. */
private fun GameValidator.validateArrayIndex(
    arrayName: String,
    index: IRExpression,
    context: String,
    arrayBounds: Map<String, Int>,
    knownBounds: Map<String, IntRange>,
) {
    val arraySize = arrayBounds[arrayName] ?: return // Unknown array, skip

    when (index) {
        is IRLiteral -> {
            // Static index - check bounds
            val i = extractLiteralValue(index)
            if (i != null && (i < 0 || i >= arraySize)) {
                errors.add(
                    ValidationError(
                        ValidationCategory.ARRAY_BOUNDS,
                        "$context: Array '$arrayName' access with literal index $i is out of bounds (size: $arraySize)",
                    )
                )
            }
        }
        is IRVar -> {
            val range = knownBounds[index.name]
            if (range != null) {
                // We know the variable's range from a for loop
                if (range.first < 0 || range.last >= arraySize) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.ARRAY_BOUNDS,
                            "$context: Array '$arrayName' access with loop variable '${index.name}' " +
                                "(range: ${range.first}..${range.last}) may be out of bounds (size: $arraySize)",
                        )
                    )
                }
            } else {
                // Unknown range - warn about unchecked access
                warnings.add(
                    ValidationWarning(
                        ValidationCategory.ARRAY_BOUNDS,
                        "$context: Array '$arrayName' access with unchecked dynamic index '${index.name}'. " +
                            "Consider using bounds checking or a for loop with known range.",
                    )
                )
            }
        }
        else -> {
            // Complex expression - warn about unchecked access
            warnings.add(
                ValidationWarning(
                    ValidationCategory.ARRAY_BOUNDS,
                    "$context: Array '$arrayName' access with complex expression index. " +
                        "Cannot verify bounds at compile time.",
                )
            )
        }
    }
}

/** Extract an integer value from an IRExpression if it's a literal. */
internal fun extractLiteralValue(expr: IRExpression): Int? {
    return when (expr) {
        is IRLiteral -> {
            when (val value = expr.value) {
                is Int -> value
                is Long -> value.toInt()
                is Short -> value.toInt()
                is Byte -> value.toInt()
                else -> null
            }
        }
        else -> null
    }
}
