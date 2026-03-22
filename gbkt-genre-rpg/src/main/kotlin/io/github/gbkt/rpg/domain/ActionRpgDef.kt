/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

/**
 * Selects the real-time combat style for an action RPG system.
 * - [REALTIME_COOLDOWN]: Player attacks trigger per-action cooldown timers. No turn queue.
 * - [HYBRID_ATB]: Active-Time Battle gauge fills passively; actions are allowed when gauge is full.
 */
enum class CombatModel {
    REALTIME_COOLDOWN,
    HYBRID_ATB,
}

/**
 * Top-level configuration for an action RPG combat system.
 *
 * Produced by [io.github.gbkt.rpg.dsl.ActionRpgBuilder] and stored in a
 * [io.github.gbkt.core.ir.GenericSystem] config map with type `"arpg_combat"`.
 *
 * @property model The real-time combat style ([CombatModel.REALTIME_COOLDOWN] or
 *   [CombatModel.HYBRID_ATB]).
 * @property dodgeRoll Optional dodge/roll mechanic configuration.
 * @property staminaGauge Optional stamina resource. Bridges to exploration gauge infrastructure at
 *   codegen time.
 * @property atb Optional ATB gauge configuration — only relevant when [model] is
 *   [CombatModel.HYBRID_ATB].
 * @property behaviorPresets List of AI behavior presets for enemies.
 */
data class ActionRpgConfig(
    val model: CombatModel = CombatModel.REALTIME_COOLDOWN,
    val dodgeRoll: DodgeRollConfig? = null,
    val staminaGauge: StaminaGaugeConfig? = null,
    val atb: AtbConfig? = null,
    val behaviorPresets: List<BehaviorPreset> = emptyList(),
)

/**
 * Configuration for the player dodge/roll mechanic.
 *
 * Codegen produces `arpg_dodge_roll()` which sets an i-frame counter and triggers a cooldown.
 *
 * @property iFrameDuration Number of frames the player is invincible during a dodge roll.
 * @property cooldownFrames Number of frames the player must wait before the next dodge roll.
 */
data class DodgeRollConfig(val iFrameDuration: Int = 8, val cooldownFrames: Int = 16)

/**
 * Stamina configuration that bridges to the exploration gauge infrastructure.
 *
 * At codegen time, this is converted to an `ExplorationGaugeIR` with id="stamina", reusing the
 * existing gauge codegen (decrement, onLow, onDepleted callbacks). The [attackCost] and [dodgeCost]
 * are ARPG-specific additions that emit stamina deduction calls in the combat codegen functions:
 * - `arpg_attack()` emits `_gauge_stamina -= attackCost`
 * - `arpg_dodge_roll()` emits `_gauge_stamina -= dodgeCost`
 * - `arpg_update()` emits `_gauge_stamina += regenRate` (clamped to [maxStamina])
 *
 * @property maxStamina Maximum stamina value. Maps to ExplorationGaugeIR.max.
 * @property regenRate Stamina regenerated per frame update.
 * @property attackCost Stamina consumed per attack.
 * @property dodgeCost Stamina consumed per dodge roll.
 */
data class StaminaGaugeConfig(
    val maxStamina: Int = 100,
    val regenRate: Int = 1,
    val attackCost: Int = 20,
    val dodgeCost: Int = 30,
)

/**
 * Configuration for an Active-Time Battle (ATB) gauge.
 *
 * Used when [ActionRpgConfig.model] is [CombatModel.HYBRID_ATB]. The gauge fills passively each
 * frame and an action is allowed once it reaches [maxGauge].
 *
 * @property maxGauge Gauge value at which a character may act.
 * @property baseSpeed Base fill rate per frame (actual speed may scale with character AGL).
 */
data class AtbConfig(val maxGauge: Int = 100, val baseSpeed: Int = 1)

/**
 * Type identifier for an enemy behavior preset AI.
 * - [CHASE]: Enemy moves toward the player within detection range.
 * - [PATROL]: Enemy follows a waypoint path until the player is in range.
 * - [ATTACK_WHEN_CLOSE]: Enemy attacks when within melee range of the player.
 * - [FLEE]: Enemy runs away when HP falls below a threshold.
 */
enum class BehaviorPresetType {
    CHASE,
    PATROL,
    ATTACK_WHEN_CLOSE,
    FLEE,
}

/**
 * A single AI behavior preset for an enemy entity.
 *
 * Multiple presets may be combined on a single builder (composable). Codegen emits an
 * `ai_update(entity_id)` function that dispatches on [BehaviorPresetType].
 *
 * @property type The behavior preset type.
 * @property range Detection or attack range in tiles (used by [BehaviorPresetType.CHASE] and
 *   [BehaviorPresetType.ATTACK_WHEN_CLOSE]).
 * @property threshold HP threshold (0-100) at which [BehaviorPresetType.FLEE] triggers.
 * @property path Ordered waypoints for [BehaviorPresetType.PATROL] (list of tile x,y pairs).
 */
data class BehaviorPreset(
    val type: BehaviorPresetType,
    val range: Int = 0,
    val threshold: Int = 0,
    val path: List<Pair<Int, Int>> = emptyList(),
)
