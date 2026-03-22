/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatHookPoint
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.CombatantDef
import io.github.gbkt.core.ir.CombatantSide
import io.github.gbkt.core.ir.DamageFormulaRef
import io.github.gbkt.core.ir.ScriptOp
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// COMBAT ENGINE REFERENCE
// =============================================================================

/**
 * Typed reference to a registered combat engine system.
 *
 * Returned by [GameBuilder.combatEngine] for use in [TriggerSystem] script ops and scene
 * configuration without requiring raw string IDs.
 */
data class CombatEngineRef(val id: String)

// =============================================================================
// COMBAT ENGINE BUILDER
// =============================================================================

/**
 * Builder for the turn-based or real-time combat engine system.
 *
 * Produces a [CombatEngineSystem] IR node with combatant slots, state hierarchy, victory/defeat
 * conditions, and action ops.
 *
 * Usage:
 * ```kotlin
 * val combat by combatEngine {
 *     type(CombatType.TURN_BASED)
 *     combatant("hero", CombatantSide.PLAYER)
 *     combatant("goblin", CombatantSide.ENEMY)
 *     maxCombatants(6)
 *     onVictoryWhen { whenever(score isAbove 0) { ... } }
 *     onDefeatWhen { whenever(hp isEqualTo 0) { ... } }
 *     onVictory { navigate(victoryScene) }
 *     onDefeat { navigate(gameOverScene) }
 *     subState("PLAYER_TURN", "SELECTING_ACTION")
 *     subState("PLAYER_TURN", "SELECTING_TARGET")
 *     damageFormula("my_damage_calc")
 * }
 * ```
 */
@GbktDsl
class CombatEngineBuilder(val id: String) {
    private var combatType: CombatType = CombatType.TURN_BASED
    private val _combatants = mutableListOf<CombatantDef>()
    private var _maxCombatants: Int = 8
    private var victoryConditionCallback: (ScriptBuilder.() -> Unit)? = null
    private var defeatConditionCallback: (ScriptBuilder.() -> Unit)? = null
    private var victoryOpsCallback: (ScriptBuilder.() -> Unit)? = null
    private var defeatOpsCallback: (ScriptBuilder.() -> Unit)? = null
    private val _customStates = mutableListOf<CombatStateId>()
    private val _stateHierarchy = mutableMapOf<CombatStateId, MutableList<CombatStateId>>()
    private var _damageFormula: DamageFormulaRef? = null
    private var _combatHooks: Map<CombatHookPoint, List<ScriptOp>> = emptyMap()

    /** Sets the combat mode (default: [CombatType.TURN_BASED]). */
    fun type(t: CombatType) {
        combatType = t
    }

    /**
     * Adds an abstract combatant slot.
     *
     * @param id Unique identifier for this combatant slot.
     * @param side Which side this combatant fights on.
     * @param canAct Whether this combatant receives turns. Set to false for passive/environmental
     *   combatants that participate in combat but never act (e.g., a trapped character).
     */
    fun combatant(id: String, side: CombatantSide, canAct: Boolean = true) {
        _combatants += CombatantDef(id = id, side = side, canAct = canAct)
    }

    /** Sets the maximum number of active combatants (used for C array sizing in codegen). */
    fun maxCombatants(n: Int) {
        _maxCombatants = n
    }

    /**
     * Records the victory CONDITION predicate.
     *
     * The ops in this block are evaluated every frame; when the condition evaluates to true, the
     * engine transitions to the VICTORY state.
     */
    fun onVictoryWhen(block: ScriptBuilder.() -> Unit) {
        victoryConditionCallback = block
    }

    /**
     * Records the defeat CONDITION predicate.
     *
     * The ops in this block are evaluated every frame; when the condition evaluates to true, the
     * engine transitions to the DEFEAT state.
     */
    fun onDefeatWhen(block: ScriptBuilder.() -> Unit) {
        defeatConditionCallback = block
    }

    /**
     * Records action ops executed after entering the VICTORY state.
     *
     * Typical usage: award XP, loot drops, navigate to victory scene.
     */
    fun onVictory(block: ScriptBuilder.() -> Unit) {
        victoryOpsCallback = block
    }

    /**
     * Records action ops executed after entering the DEFEAT state.
     *
     * Typical usage: navigate to game over scene, reset battle state.
     */
    fun onDefeat(block: ScriptBuilder.() -> Unit) {
        defeatOpsCallback = block
    }

    /**
     * Registers an extensible custom state ID.
     *
     * Custom states are extension points for RPG and genre packages. The ID is stored in
     * [CombatEngineSystem.customStates] for backend use.
     */
    fun customState(id: String) {
        _customStates += CombatStateId(id)
    }

    /**
     * Registers a hierarchical sub-state relationship.
     *
     * Example: `subState("PLAYER_TURN", "SELECTING_TARGET")` records that SELECTING_TARGET is a
     * sub-state of PLAYER_TURN in the state hierarchy map.
     *
     * @param parentId The parent state ID string.
     * @param childId The child (sub-state) ID string.
     */
    fun subState(parentId: String, childId: String) {
        val parent = CombatStateId(parentId)
        val child = CombatStateId(childId)
        _stateHierarchy.getOrPut(parent) { mutableListOf() }.add(child)
    }

    /**
     * Registers a pluggable C damage formula function reference.
     *
     * The referenced function must have signature `UINT16 <functionName>(UINT8 atk, UINT8 def)`.
     */
    fun damageFormula(functionName: String) {
        _damageFormula = DamageFormulaRef(functionName)
    }

    /**
     * Sets the combat lifecycle hooks map.
     *
     * Called by the `hooks { }` DSL extension in `gbkt-rpg` (via [CombatHookBuilder]). Not intended
     * to be called directly — use the `hooks { }` extension function instead.
     *
     * @param hooksMap Immutable map from hook point to ScriptOp list.
     */
    fun setCombatHooks(hooksMap: Map<CombatHookPoint, List<ScriptOp>>) {
        _combatHooks = hooksMap
    }

    /** Builds and returns the [CombatEngineSystem] IR node. */
    fun build(): CombatEngineSystem =
        CombatEngineSystem(
            id = id,
            combatType = combatType,
            combatants = _combatants.toList(),
            onVictoryCondition =
                victoryConditionCallback?.let { recordStatements(it) } ?: emptyList(),
            onDefeatCondition =
                defeatConditionCallback?.let { recordStatements(it) } ?: emptyList(),
            onVictoryOps = victoryOpsCallback?.let { recordStatements(it) } ?: emptyList(),
            onDefeatOps = defeatOpsCallback?.let { recordStatements(it) } ?: emptyList(),
            customStates = _customStates.toList(),
            stateHierarchy = _stateHierarchy.mapValues { it.value.toList() },
            damageFormula = _damageFormula,
            maxCombatants = _maxCombatants,
            combatHooks = _combatHooks,
        )
}

// =============================================================================
// COMBAT ENGINE DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers a combat engine's ID from the Kotlin property and registers it with
 * the current [GameBuilder].
 *
 * Mirrors the [ActorDelegate] pattern so that `val combat by combatEngine { ... }` syntax works.
 *
 * Usage:
 * ```kotlin
 * val combat by combatEngine {
 *     type(CombatType.TURN_BASED)
 *     combatant("hero", CombatantSide.PLAYER)
 * }
 * ```
 *
 * @param id When empty, the property name is used as the combat engine ID.
 * @param block The combat engine configuration block.
 */
class CombatEngineDelegate(
    private val id: String,
    private val block: CombatEngineBuilder.() -> Unit,
) : ReadOnlyProperty<Any?, CombatEngineRef> {
    private var ref: CombatEngineRef? = null

    /**
     * Called by Kotlin when `val x by combatEngine { ... }` is evaluated.
     *
     * Captures the property name, calls [GameBuilder.combatEngine], and stores the resulting
     * [CombatEngineRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: GameBuilder,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, CombatEngineRef> {
        val resolvedId = id.ifEmpty { property.name }
        ref = thisRef.combatEngine(resolvedId, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): CombatEngineRef =
        ref ?: error("CombatEngineDelegate not initialized — was provideDelegate called?")
}
