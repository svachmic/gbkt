/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.core.ir.AtbConfig
import io.github.gbkt.core.ir.AtbGaugeModel
import io.github.gbkt.core.ir.AtbWaitMode
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.CombatantDef
import io.github.gbkt.core.ir.CombatantSide
import io.github.gbkt.core.ir.DamageFormulaRef
import io.github.gbkt.core.ir.TurnOrderStrategy
import io.github.gbkt.rpg.domain.CharacterDef

// =============================================================================
// ATB COMBAT BUILDER
// =============================================================================
//
// Wraps CombatEngineSystem construction with ATB-specific configuration.
// Pre-sets combatType=ATB and provides ATB-focused DSL methods.
// Also delegates all CombatEngineBuilder-like methods (combatants, victory/defeat).
//
// Usage:
//   atbCombat("id") {
//       gaugeModel(AtbGaugeModel.FILL)
//       waitMode(AtbWaitMode.WAIT)
//       fillRate(4)
//       maxGauge(200)
//       turnOrder(TurnOrderStrategy.SPEED_BASED)
//       onVictory { navigate(winScene) }
//       onDefeat { navigate(gameoverScene) }
//   }
// =============================================================================

/**
 * Builder for an ATB (Active Time Battle) combat system.
 *
 * Pre-configures a [CombatEngineSystem] with [CombatType.ATB]. Provides ATB-specific DSL methods
 * for gauge model, wait mode, fill rate, and turn order strategy. All base CombatEngineSystem
 * fields (combatants, victory/defeat ops, damage formula, max combatants) are also available.
 *
 * The [build] method returns a [CombatEngineSystem] with [CombatType.ATB] and an [AtbConfig]
 * populated from the builder state.
 *
 * @param id Unique system identifier. Used in generated C function names.
 */
class AtbCombatBuilder(val id: String) {

    // ATB configuration fields
    private var gaugeModel: AtbGaugeModel = AtbGaugeModel.FILL
    private var waitMode: AtbWaitMode = AtbWaitMode.WAIT
    private var baseGaugeFillRate: Int = 4
    private var maxGaugeValue: Int = 255
    private var allowPlayerToggleFlag: Boolean = false

    // CombatEngineSystem fields
    private val combatants: MutableList<CombatantDef> = mutableListOf()
    private var onVictoryOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var onDefeatOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var turnOrderStrategy: TurnOrderStrategy? = null
    private var damageFormula: DamageFormulaRef? = null
    private var maxCombatants: Int = 8

    // -------------------------------------------------------------------------
    // ATB-specific configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the gauge progression model.
     *
     * [AtbGaugeModel.FILL] — classic FF: gauge fills from 0 to max, then combatant acts.
     * [AtbGaugeModel.CHARGE] — per-action cast time: gauge fills per-action charge counter.
     */
    fun gaugeModel(model: AtbGaugeModel) {
        gaugeModel = model
    }

    /**
     * Sets the wait mode for gauge progression during menus.
     *
     * [AtbWaitMode.WAIT] — gauges pause when any menu is open. [AtbWaitMode.ACTIVE] — gauges keep
     * filling even during menus.
     */
    fun waitMode(mode: AtbWaitMode) {
        waitMode = mode
    }

    /**
     * Shortcut to set [AtbWaitMode.ACTIVE] mode — gauges keep filling during menus.
     *
     * Equivalent to `waitMode(AtbWaitMode.ACTIVE)`.
     */
    fun activeMode() {
        waitMode = AtbWaitMode.ACTIVE
    }

    /**
     * Exposes a Wait/Active toggle to the player in-game.
     *
     * When enabled, the player can switch between [AtbWaitMode.WAIT] and [AtbWaitMode.ACTIVE] from
     * the pause menu.
     */
    fun allowPlayerToggle() {
        allowPlayerToggleFlag = true
    }

    /**
     * Sets the base gauge fill rate per frame.
     *
     * Actual rate per combatant is `base + (agl >> 2)` where `agl` is the combatant's agility stat.
     * Higher values make gauges fill faster for all combatants.
     *
     * @param base Base fill rate (default 4).
     */
    fun fillRate(base: Int) {
        baseGaugeFillRate = base
    }

    /**
     * Sets the maximum gauge value.
     *
     * Default is 255 (UINT8 max) which is safe for GBDK's unsigned byte arrays.
     *
     * @param max Maximum gauge value (1–255).
     */
    fun maxGauge(max: Int) {
        maxGaugeValue = max
    }

    // -------------------------------------------------------------------------
    // CombatEngineSystem-level configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the turn order strategy for this ATB combat instance.
     *
     * [TurnOrderStrategy.SPEED_BASED] — faster combatants (higher agility) act first.
     * [TurnOrderStrategy.FIXED_ORDER] — predefined static sequence.
     */
    fun turnOrder(strategy: TurnOrderStrategy) {
        turnOrderStrategy = strategy
    }

    /**
     * Adds a player-side combatant slot.
     *
     * @param character The [CharacterDef] whose ID is used as the slot identifier.
     */
    fun party(character: CharacterDef) {
        combatants.add(CombatantDef(id = character.id, side = CombatantSide.PLAYER))
    }

    /**
     * Adds multiple player-side combatant slots.
     *
     * @param characters The [CharacterDef] instances to add as party members.
     */
    fun party(vararg characters: CharacterDef) {
        characters.forEach { combatants.add(CombatantDef(id = it.id, side = CombatantSide.PLAYER)) }
    }

    /**
     * Records the script operations to execute when the player wins the battle.
     *
     * ```kotlin
     * onVictory { navigate(gameplayScene) }
     * ```
     */
    fun onVictory(block: ScriptBuilder.() -> Unit) {
        onVictoryOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Records the script operations to execute when the player loses the battle.
     *
     * ```kotlin
     * onDefeat { navigate(gameoverScene) }
     * ```
     */
    fun onDefeat(block: ScriptBuilder.() -> Unit) {
        onDefeatOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Sets a custom damage formula function (optional).
     *
     * @param functionName C function name with signature `UINT16 <name>(UINT8 atk, UINT8 def)`.
     */
    fun damageFormula(functionName: String) {
        damageFormula = DamageFormulaRef(functionName)
    }

    /**
     * Sets the maximum number of simultaneous combatants.
     *
     * Default is 8. Used for array sizing in codegen.
     *
     * @param max Maximum combatant count (1–8 recommended for Game Boy RAM constraints).
     */
    fun maxCombatants(max: Int) {
        maxCombatants = max
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Constructs the [CombatEngineSystem] with [CombatType.ATB] and the configured [AtbConfig].
     *
     * @return A [CombatEngineSystem] ready for registration in the game IR.
     */
    fun build(): CombatEngineSystem =
        CombatEngineSystem(
            id = id,
            combatType = CombatType.ATB,
            combatants = combatants.toList(),
            onVictoryOps = onVictoryOps,
            onDefeatOps = onDefeatOps,
            damageFormula = damageFormula,
            maxCombatants = maxCombatants,
            atbConfig =
                AtbConfig(
                    gaugeModel = gaugeModel,
                    waitMode = waitMode,
                    baseGaugeFillRate = baseGaugeFillRate,
                    maxGauge = maxGaugeValue,
                    allowPlayerToggle = allowPlayerToggleFlag,
                ),
            turnOrderStrategy = turnOrderStrategy,
        )
}
