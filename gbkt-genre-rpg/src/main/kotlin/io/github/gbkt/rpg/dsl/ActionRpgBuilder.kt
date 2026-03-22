/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.ActionRpgConfig
import io.github.gbkt.rpg.domain.AtbConfig
import io.github.gbkt.rpg.domain.BehaviorPreset
import io.github.gbkt.rpg.domain.BehaviorPresetType
import io.github.gbkt.rpg.domain.CombatModel
import io.github.gbkt.rpg.domain.DodgeRollConfig
import io.github.gbkt.rpg.domain.StaminaGaugeConfig

// =============================================================================
// SUB-BUILDERS
// =============================================================================

/**
 * Builder for [DodgeRollConfig] within an [ActionRpgBuilder].
 *
 * ```kotlin
 * actionRpg("combat") {
 *     dodgeRoll {
 *         iFrames(10)
 *         cooldown(20)
 *     }
 * }
 * ```
 */
class DodgeRollConfigBuilder {
    private var iFrameDuration: Int = 8
    private var cooldownFrames: Int = 16

    /** Number of invincibility frames during a dodge roll. */
    fun iFrames(frames: Int) {
        iFrameDuration = frames
    }

    /** Number of cooldown frames before the next dodge roll is allowed. */
    fun cooldown(frames: Int) {
        cooldownFrames = frames
    }

    internal fun build(): DodgeRollConfig =
        DodgeRollConfig(iFrameDuration = iFrameDuration, cooldownFrames = cooldownFrames)
}

/**
 * Builder for [StaminaGaugeConfig] within an [ActionRpgBuilder].
 *
 * The stamina gauge bridges to the exploration gauge infrastructure at codegen time. An
 * `ExplorationGaugeIR` with id="stamina" is generated using [maxStamina] as the max, and the ARPG
 * codegen adds deduction calls for attack and dodge.
 *
 * ```kotlin
 * actionRpg("combat") {
 *     stamina {
 *         max(120)
 *         regen(2)
 *         attackCost(25)
 *         dodgeCost(35)
 *     }
 * }
 * ```
 */
class StaminaGaugeConfigBuilder {
    private var maxStamina: Int = 100
    private var regenRate: Int = 1
    private var attackCost: Int = 20
    private var dodgeCost: Int = 30

    /** Maximum stamina value (maps to ExplorationGaugeIR.max). */
    fun max(value: Int) {
        maxStamina = value
    }

    /** Stamina regenerated per frame update. */
    fun regen(rate: Int) {
        regenRate = rate
    }

    /** Stamina consumed per attack action. */
    fun attackCost(cost: Int) {
        attackCost = cost
    }

    /** Stamina consumed per dodge roll. */
    fun dodgeCost(cost: Int) {
        dodgeCost = cost
    }

    internal fun build(): StaminaGaugeConfig =
        StaminaGaugeConfig(
            maxStamina = maxStamina,
            regenRate = regenRate,
            attackCost = attackCost,
            dodgeCost = dodgeCost,
        )
}

/**
 * Builder for [AtbConfig] within an [ActionRpgBuilder].
 *
 * ```kotlin
 * actionRpg("combat") {
 *     combatModel(CombatModel.HYBRID_ATB)
 *     atb {
 *         maxGauge(200)
 *         baseSpeed(2)
 *     }
 * }
 * ```
 */
class AtbConfigBuilder {
    private var maxGauge: Int = 100
    private var baseSpeed: Int = 1

    /** Gauge value at which a character may act. */
    fun maxGauge(value: Int) {
        maxGauge = value
    }

    /** Base fill rate per frame. Actual speed may scale with character AGL. */
    fun baseSpeed(speed: Int) {
        baseSpeed = speed
    }

    internal fun build(): AtbConfig = AtbConfig(maxGauge = maxGauge, baseSpeed = baseSpeed)
}

// =============================================================================
// MAIN BUILDER
// =============================================================================

/**
 * Builder for an action RPG combat system ([ActionRpgConfig]).
 *
 * Produces a [GenericSystem] with config type `"arpg_combat"`. The backend generates
 * `arpg_update()`, `arpg_attack(target_id)`, optional `arpg_dodge_roll()`, optional
 * `atb_check_ready(char_id)`, and `ai_update(entity_id)` functions.
 *
 * Stamina configuration bridges to the exploration gauge infrastructure — the codegen creates an
 * `ExplorationGaugeIR` with id="stamina" so that existing gauge codegen handles the global variable
 * and step-based housekeeping.
 *
 * ```kotlin
 * actionRpg("combat") {
 *     combatModel(CombatModel.REALTIME_COOLDOWN)
 *     dodgeRoll { iFrames(8); cooldown(16) }
 *     stamina { max(100); regen(1); attackCost(20); dodgeCost(30) }
 *     behaviorPreset(BehaviorPresetType.CHASE, range = 5)
 *     behaviorPreset(BehaviorPresetType.ATTACK_WHEN_CLOSE, range = 1)
 * }
 * ```
 *
 * @param id Unique system identifier used in generated C function names.
 */
class ActionRpgBuilder(val id: String) {
    private var model: CombatModel = CombatModel.REALTIME_COOLDOWN
    private var dodgeRoll: DodgeRollConfig? = null
    private var staminaGauge: StaminaGaugeConfig? = null
    private var atb: AtbConfig? = null
    private val behaviorPresets: MutableList<BehaviorPreset> = mutableListOf()

    /**
     * Sets the real-time combat model.
     *
     * Defaults to [CombatModel.REALTIME_COOLDOWN] if not called.
     */
    fun combatModel(model: CombatModel) {
        this.model = model
    }

    /**
     * Configures the dodge roll mechanic.
     *
     * When configured, codegen emits `arpg_dodge_roll()` which sets the i-frame counter and
     * enforces the cooldown timer.
     */
    fun dodgeRoll(block: DodgeRollConfigBuilder.() -> Unit) {
        val builder = DodgeRollConfigBuilder()
        builder.block()
        dodgeRoll = builder.build()
    }

    /**
     * Configures the stamina resource gauge.
     *
     * Bridges to `ExplorationGaugeIR(id="stamina")` at codegen time. The gauge global
     * `_gauge_stamina` is managed by the existing exploration gauge codegen; ARPG codegen adds
     * attack/dodge deduction and per-frame regen calls.
     */
    fun stamina(block: StaminaGaugeConfigBuilder.() -> Unit) {
        val builder = StaminaGaugeConfigBuilder()
        builder.block()
        staminaGauge = builder.build()
    }

    /**
     * Configures the ATB (Active Time Battle) gauge.
     *
     * Only meaningful when [combatModel] is [CombatModel.HYBRID_ATB]. Codegen emits
     * `atb_check_ready(char_id)` which returns 1 when the gauge has reached [AtbConfig.maxGauge].
     */
    fun atb(block: AtbConfigBuilder.() -> Unit) {
        val builder = AtbConfigBuilder()
        builder.block()
        atb = builder.build()
    }

    /**
     * Adds a composable behavior preset to the AI dispatch function.
     *
     * Multiple presets may be added; codegen emits a single `ai_update(entity_id)` that evaluates
     * each preset in order.
     *
     * @param type The preset AI behavior type.
     * @param range Detection or attack range in tiles ([BehaviorPresetType.CHASE] /
     *   [BehaviorPresetType.ATTACK_WHEN_CLOSE]).
     * @param threshold HP threshold (0-100) triggering [BehaviorPresetType.FLEE].
     * @param path Ordered waypoints for [BehaviorPresetType.PATROL].
     */
    fun behaviorPreset(
        type: BehaviorPresetType,
        range: Int = 0,
        threshold: Int = 0,
        path: List<Pair<Int, Int>> = emptyList(),
    ) {
        behaviorPresets.add(
            BehaviorPreset(type = type, range = range, threshold = threshold, path = path)
        )
    }

    /**
     * Builds a [GenericSystem] with config type `"arpg_combat"` and [ActionRpgConfig] stored under
     * the `"config"` key.
     *
     * Config map layout:
     * - `"type"` → `"arpg_combat"`
     * - `"config"` → [ActionRpgConfig]
     */
    fun build(): GenericSystem {
        val config =
            ActionRpgConfig(
                model = model,
                dodgeRoll = dodgeRoll,
                staminaGauge = staminaGauge,
                atb = atb,
                behaviorPresets = behaviorPresets.toList(),
            )
        return GenericSystem(id = id, config = mapOf("type" to "arpg_combat", "config" to config))
    }
}
