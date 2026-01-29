/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.validation

import io.github.gbkt.core.GameValidator
import io.github.gbkt.core.Suggestions
import io.github.gbkt.core.ValidationCategory
import io.github.gbkt.core.ValidationError
import io.github.gbkt.core.ir.IRAnimationPause
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationQueue
import io.github.gbkt.core.ir.IRAnimationResume
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationSetSpeed
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRDialogSay
import io.github.gbkt.core.ir.IRDialogShow
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRMenuCancel
import io.github.gbkt.core.ir.IRMenuMoveTo
import io.github.gbkt.core.ir.IRMenuOpen
import io.github.gbkt.core.ir.IRMenuSelect
import io.github.gbkt.core.ir.IRMenuShow
import io.github.gbkt.core.ir.IRMenuTick
import io.github.gbkt.core.ir.IRPoolDespawn
import io.github.gbkt.core.ir.IRPoolDespawnAll
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRPoolUpdate
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile

// =============================================================================
// IR REFERENCE VALIDATION
// =============================================================================

/**
 * Validate IR references (pools, menus, dialogs, sprites) in all IR statements. This extends the
 * existing validation to cover references outside state machines.
 */
internal fun GameValidator.validateIRReferences() {
    // Collect known names
    val knownSprites =
        game.sprites.map { it.name }.toSet() +
            game.entities.mapNotNull { it.sprite?.name }.toSet() +
            game.pools.map { "${it.name}_sprite" }.toSet()
    val knownPools = game.pools.map { it.name }.toSet()
    val knownMenus = game.menus.map { it.name }.toSet()
    val knownDialogs = game.dialogs.map { it.name }.toSet()

    // Scan all scenes
    for ((sceneName, scene) in game.scenes) {
        validateIRReferencesInStatements(
            scene.onEnter,
            "scene '$sceneName' enter",
            knownSprites,
            knownPools,
            knownMenus,
            knownDialogs,
        )
        validateIRReferencesInStatements(
            scene.onFrame,
            "scene '$sceneName' frame",
            knownSprites,
            knownPools,
            knownMenus,
            knownDialogs,
        )
        validateIRReferencesInStatements(
            scene.onExit,
            "scene '$sceneName' exit",
            knownSprites,
            knownPools,
            knownMenus,
            knownDialogs,
        )
    }

    // Scan pools
    for (pool in game.pools) {
        validateIRReferencesInStatements(
            pool.onFrameStatements,
            "pool '${pool.name}'",
            knownSprites,
            knownPools,
            knownMenus,
            knownDialogs,
        )
    }

    // Scan state machines
    for (machine in game.stateMachines) {
        for ((stateName, state) in machine.states) {
            val context = "state machine '${machine.name}::$stateName'"
            validateIRReferencesInStatements(
                state.onEnter,
                "$context onEnter",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
            validateIRReferencesInStatements(
                state.onTick,
                "$context onTick",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
            validateIRReferencesInStatements(
                state.onExit,
                "$context onExit",
                knownSprites,
                knownPools,
                knownMenus,
                knownDialogs,
            )
        }
    }
}

/** Recursively validate IR references in statements. */
private fun GameValidator.validateIRReferencesInStatements(
    statements: List<IRStatement>,
    context: String,
    knownSprites: Set<String>,
    knownPools: Set<String>,
    knownMenus: Set<String>,
    knownDialogs: Set<String>,
) {
    for (stmt in statements) {
        when (stmt) {
            // Animation references
            is IRAnimationPlay -> {
                if (stmt.spriteName !in knownSprites) {
                    val suggestion = Suggestions.formatSuggestion(stmt.spriteName, knownSprites)
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation references unknown sprite '${stmt.spriteName}'.$suggestion",
                        )
                    )
                }
            }
            is IRAnimationStop -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation stop references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }
            is IRAnimationPause -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation pause references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }
            is IRAnimationResume -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation resume references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }
            is IRAnimationSetFrame -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation setFrame references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }
            is IRAnimationSetSpeed -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation setSpeed references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }
            is IRAnimationQueue -> {
                if (stmt.spriteName !in knownSprites) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.SPRITE_REFERENCE,
                            "$context: Animation queue references unknown sprite '${stmt.spriteName}'.",
                        )
                    )
                }
            }

            // Pool references
            is IRPoolSpawn -> {
                if (stmt.poolName !in knownPools) {
                    val suggestion = Suggestions.formatSuggestion(stmt.poolName, knownPools)
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool spawn references unknown pool '${stmt.poolName}'.$suggestion",
                        )
                    )
                }
                validateIRReferencesInStatements(
                    stmt.initStatements,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
            is IRPoolSpawnAt -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool spawnAt references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
                validateIRReferencesInStatements(
                    stmt.initStatements,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
            is IRPoolTrySpawn -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool trySpawn references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
                validateIRReferencesInStatements(
                    stmt.initStatements,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
                validateIRReferencesInStatements(
                    stmt.elseStatements,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
            is IRPoolUpdate -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool update references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
            }
            is IRPoolDespawn -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool despawn references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
            }
            is IRPoolDespawnAll -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool despawnAll references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
            }
            is IRPoolForEach -> {
                if (stmt.poolName !in knownPools) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.POOL_REFERENCE,
                            "$context: Pool forEach references unknown pool '${stmt.poolName}'.",
                        )
                    )
                }
                validateIRReferencesInStatements(
                    stmt.bodyStatements,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }

            // Menu references
            is IRMenuShow -> {
                if (stmt.menuName !in knownMenus) {
                    val suggestion = Suggestions.formatSuggestion(stmt.menuName, knownMenus)
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu show references unknown menu '${stmt.menuName}'.$suggestion",
                        )
                    )
                }
            }
            is IRMenuOpen -> {
                if (stmt.menuName !in knownMenus) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu open references unknown menu '${stmt.menuName}'.",
                        )
                    )
                }
            }
            is IRMenuTick -> {
                if (stmt.menuName !in knownMenus) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu tick references unknown menu '${stmt.menuName}'.",
                        )
                    )
                }
            }
            is IRMenuSelect -> {
                if (stmt.menuName !in knownMenus) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu select references unknown menu '${stmt.menuName}'.",
                        )
                    )
                }
            }
            is IRMenuCancel -> {
                if (stmt.menuName !in knownMenus) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu cancel references unknown menu '${stmt.menuName}'.",
                        )
                    )
                }
            }
            is IRMenuMoveTo -> {
                if (stmt.menuName !in knownMenus) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.MENU_REFERENCE,
                            "$context: Menu moveTo references unknown menu '${stmt.menuName}'.",
                        )
                    )
                }
            }

            // Dialog references
            is IRDialogShow -> {
                if (stmt.dialogName !in knownDialogs) {
                    val suggestion = Suggestions.formatSuggestion(stmt.dialogName, knownDialogs)
                    errors.add(
                        ValidationError(
                            ValidationCategory.DIALOG_REFERENCE,
                            "$context: Dialog show references unknown dialog '${stmt.dialogName}'.$suggestion",
                        )
                    )
                }
            }
            is IRDialogSay -> {
                if (stmt.dialogName !in knownDialogs) {
                    errors.add(
                        ValidationError(
                            ValidationCategory.DIALOG_REFERENCE,
                            "$context: Dialog say references unknown dialog '${stmt.dialogName}'.",
                        )
                    )
                }
            }

            // Nested statements - recurse
            is IRIf -> {
                validateIRReferencesInStatements(
                    stmt.then,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
                stmt.otherwise?.let {
                    validateIRReferencesInStatements(
                        it,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
            }
            is IRWhen -> {
                for (branch in stmt.branches) {
                    validateIRReferencesInStatements(
                        branch.body,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
                stmt.otherwise?.let {
                    validateIRReferencesInStatements(
                        it,
                        context,
                        knownSprites,
                        knownPools,
                        knownMenus,
                        knownDialogs,
                    )
                }
            }
            is IRWhile, is IRFor -> {
                val body = when (stmt) {
                    is IRWhile -> stmt.body
                    is IRFor -> stmt.body
                    else -> return
                }
                validateIRReferencesInStatements(
                    body,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
            is IRTransitionFadeOut, is IRTransitionFadeIn, is IRTransitionWipe, is IRTransitionIris -> {
                val onComplete = when (stmt) {
                    is IRTransitionFadeOut -> stmt.onComplete
                    is IRTransitionFadeIn -> stmt.onComplete
                    is IRTransitionWipe -> stmt.onComplete
                    is IRTransitionIris -> stmt.onComplete
                    else -> return
                }
                validateIRReferencesInStatements(
                    onComplete,
                    context,
                    knownSprites,
                    knownPools,
                    knownMenus,
                    knownDialogs,
                )
            }
            else -> Unit  // No nested statements or references to validate
        }
    }
}
