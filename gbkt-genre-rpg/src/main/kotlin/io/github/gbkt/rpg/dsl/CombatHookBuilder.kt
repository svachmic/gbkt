/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.core.ir.CombatHookPoint
import io.github.gbkt.core.ir.ScriptOp

// =============================================================================
// COMBAT HOOK BUILDER
// =============================================================================
//
// CombatHookBuilder provides a DSL for registering combat lifecycle hooks on a
// CombatEngineSystem. Each hook point corresponds to a key moment in the combat
// state machine where custom ScriptOps can be injected.
//
// Usage:
//   combatEngine("combat") {
//       hooks {
//           beforeAction { /* record stats */ }
//           afterDamage { navigate("flash_effect") }
//           onVictory { /* extra effects */ }
//       }
//   }
//
// Design: hooks() DSL method is an extension on CombatEngineBuilder (gbkt-lang)
// added via RpgExtensions.kt (gbkt-rpg). This preserves the one-directional
// dependency: gbkt-rpg → gbkt-core; gbkt-lang ← gbkt-core.
// =============================================================================

/**
 * DSL builder for registering combat lifecycle hooks.
 *
 * Collects [ScriptOp] lists per [CombatHookPoint]. Multiple calls to the same hook point are
 * concatenated (ops from later calls are appended to earlier ones).
 *
 * Produced by [CombatHookBuilder.build] as an immutable [Map] suitable for
 * [io.github.gbkt.core.ir.CombatEngineSystem.combatHooks].
 *
 * ```kotlin
 * combatEngine("combat") {
 *     hooks {
 *         beforeAction { /* ScriptBuilder DSL here */ }
 *         afterDamage { navigate("damage_flash") }
 *         onVictory { /* extra victory effects */ }
 *         onDefeat { /* extra defeat effects */ }
 *     }
 * }
 * ```
 */
class CombatHookBuilder {
    private val hooks = mutableMapOf<CombatHookPoint, MutableList<ScriptOp>>()

    /** Records ScriptOps to execute before any combatant's action. */
    fun beforeAction(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.BEFORE_ACTION, block)

    /** Records ScriptOps to execute after any combatant's action completes. */
    fun afterAction(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.AFTER_ACTION, block)

    /** Records ScriptOps to execute after damage is dealt to any combatant. */
    fun afterDamage(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.AFTER_DAMAGE, block)

    /** Records ScriptOps to execute before each combat turn begins. */
    fun beforeTurn(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.BEFORE_TURN, block)

    /** Records ScriptOps to execute after each combat turn completes. */
    fun afterTurn(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.AFTER_TURN, block)

    /**
     * Records ScriptOps to execute when the victory condition is met.
     *
     * Hook runs BEFORE user-defined [onVictory] ops on the [CombatEngineSystem].
     */
    fun onVictory(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.ON_VICTORY, block)

    /**
     * Records ScriptOps to execute when the defeat condition is met.
     *
     * Hook runs BEFORE user-defined [onDefeat] ops on the [CombatEngineSystem].
     */
    fun onDefeat(block: ScriptBuilder.() -> Unit) = record(CombatHookPoint.ON_DEFEAT, block)

    /**
     * Records ScriptOps for the given [point] using a [ScriptBuilder] block.
     *
     * Multiple calls for the same point concatenate ops (ops from later calls are appended).
     */
    private fun record(point: CombatHookPoint, block: ScriptBuilder.() -> Unit) {
        hooks.getOrPut(point) { mutableListOf() }.addAll(ScriptBuilder.buildOps(block))
    }

    /**
     * Builds the immutable hooks map.
     *
     * @return Map from [CombatHookPoint] to lists of [ScriptOp]. Empty points are not included.
     */
    fun build(): Map<CombatHookPoint, List<ScriptOp>> = hooks.mapValues { it.value.toList() }
}
