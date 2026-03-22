/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.ir.BetweenWaveBehavior
import io.github.gbkt.core.ir.ProceduralWave
import io.github.gbkt.core.ir.ScriptedWave
import io.github.gbkt.core.ir.WaveDef
import io.github.gbkt.core.ir.WaveSurvivalConfig
import io.github.gbkt.core.ir.WaveTrigger

// =============================================================================
// WAVE SURVIVAL BUILDER
// =============================================================================
//
// WaveSurvivalBuilder builds a WaveSurvivalConfig that is attached to a
// CombatEngineSystem with combatType = WAVE_SURVIVAL.
//
// The DSL extension function GameBuilder.waveSurvival() (in RpgExtensions.kt)
// wraps the resulting CombatEngineSystem in a CombatEngineSystem IR node.
// =============================================================================

/**
 * Builder for [WaveSurvivalConfig] — wave-based survival combat configuration.
 *
 * Supports scripted waves (hand-authored monster lists) and procedural waves (PRNG-selected from a
 * monster pool), plus between-wave behavior configuration.
 *
 * ```kotlin
 * waveSurvival("waves") {
 *     wave(1) { monsters("goblin", "goblin") }
 *     wave(2) { monsters("goblin", "orc") }
 *     proceduralWave(3) {
 *         pool("goblin", "orc", "troll")
 *         count(min = 2, max = 4)
 *         difficulty(150)
 *     }
 *     betweenWaves {
 *         heal(20)
 *         shop()
 *         behavior(BetweenWaveBehavior.PAUSE)
 *     }
 *     maxWaves(10)
 * }
 * ```
 */
class WaveSurvivalBuilder {
    private val waves = mutableListOf<WaveDef>()
    private var betweenWaveBehavior: BetweenWaveBehavior = BetweenWaveBehavior.PAUSE
    private var healBetweenWaves: Int = 0
    private var shopAccessBetweenWaves: Boolean = false
    private var nextWaveTrigger: WaveTrigger = WaveTrigger.TIMER
    private var pauseDuration: Int = 120
    private var maxWavesCount: Int = 0

    /**
     * Defines a scripted wave with a hand-authored list of monsters.
     *
     * @param number The wave number (1-indexed). Waves are displayed in the order they are defined.
     * @param block Configuration block for the scripted wave content.
     */
    fun wave(number: Int, block: WaveContentBuilder.() -> Unit) {
        val builder = WaveContentBuilder()
        builder.block()
        waves.add(WaveDef(waveNumber = number, content = ScriptedWave(builder.monsterIds)))
    }

    /**
     * Defines a procedurally generated wave that selects monsters from a pool using PRNG.
     *
     * @param number The wave number (1-indexed).
     * @param block Configuration block for the procedural wave content.
     */
    fun proceduralWave(number: Int, block: ProceduralWaveBuilder.() -> Unit) {
        val builder = ProceduralWaveBuilder()
        builder.block()
        waves.add(
            WaveDef(
                waveNumber = number,
                content =
                    ProceduralWave(
                        monsterPool = builder.pool,
                        minCount = builder.minCount,
                        maxCount = builder.maxCount,
                        difficultyMultiplier = builder.difficultyMultiplier,
                    ),
            )
        )
    }

    /**
     * Configures the between-wave behavior (healing, shop access, trigger, pause duration).
     *
     * @param block Configuration block for between-wave behavior.
     */
    fun betweenWaves(block: BetweenWaveBuilder.() -> Unit) {
        val builder = BetweenWaveBuilder()
        builder.block()
        betweenWaveBehavior = builder.behavior
        healBetweenWaves = builder.healAmount
        shopAccessBetweenWaves = builder.shopAccess
        nextWaveTrigger = builder.trigger
        pauseDuration = builder.pauseDuration
    }

    /**
     * Sets the maximum number of waves before triggering victory.
     *
     * @param n Maximum waves. 0 = unlimited (endless mode).
     */
    fun maxWaves(n: Int) {
        maxWavesCount = n
    }

    /**
     * Sets the HP heal amount between waves directly (alternative to [betweenWaves] block).
     *
     * @param amount HP to restore after each wave clears (0 = no heal).
     */
    fun healBetweenWaves(amount: Int) {
        healBetweenWaves = amount
    }

    /** Enables shop access between waves directly (alternative to [betweenWaves] block). */
    fun shopAccess() {
        shopAccessBetweenWaves = true
    }

    /**
     * Sets the trigger for starting the next wave directly (alternative to [betweenWaves] block).
     *
     * @param trigger [WaveTrigger.TIMER] auto-starts after [pauseDuration] frames;
     *   [WaveTrigger.PLAYER_READY] waits for player input.
     */
    fun nextWaveTrigger(trigger: WaveTrigger) {
        nextWaveTrigger = trigger
    }

    /** Builds the [WaveSurvivalConfig] from accumulated configuration. */
    fun build(): WaveSurvivalConfig =
        WaveSurvivalConfig(
            waves = waves.toList(),
            betweenWaveBehavior = betweenWaveBehavior,
            healBetweenWaves = healBetweenWaves,
            shopAccessBetweenWaves = shopAccessBetweenWaves,
            nextWaveTrigger = nextWaveTrigger,
            pauseDuration = pauseDuration,
            maxWaves = maxWavesCount,
        )
}

// =============================================================================
// NESTED BUILDERS
// =============================================================================

/**
 * Builder for scripted wave content (hand-authored monster list).
 *
 * Used inside [WaveSurvivalBuilder.wave] blocks.
 */
class WaveContentBuilder {
    internal val monsterIds = mutableListOf<String>()

    /**
     * Adds monsters to this scripted wave by their IDs.
     *
     * @param ids Monster IDs to spawn (vararg for convenience).
     */
    fun monsters(vararg ids: String) {
        monsterIds.addAll(ids)
    }

    /**
     * Adds monsters to this scripted wave from a list.
     *
     * @param ids Monster ID list.
     */
    fun monsters(ids: List<String>) {
        monsterIds.addAll(ids)
    }
}

/**
 * Builder for procedural wave content (PRNG selection from a monster pool).
 *
 * Used inside [WaveSurvivalBuilder.proceduralWave] blocks.
 */
class ProceduralWaveBuilder {
    internal val pool = mutableListOf<String>()
    internal var minCount: Int = 1
    internal var maxCount: Int = 3
    internal var difficultyMultiplier: Int = 100

    /**
     * Sets the monster pool to select from.
     *
     * @param ids Monster IDs in the pool (vararg for convenience).
     */
    fun pool(vararg ids: String) {
        pool.addAll(ids)
    }

    /**
     * Sets the pool from a list.
     *
     * @param ids Monster ID list.
     */
    fun pool(ids: List<String>) {
        pool.addAll(ids)
    }

    /**
     * Sets the spawn count range for this procedural wave.
     *
     * @param min Minimum number of monsters to spawn (inclusive).
     * @param max Maximum number of monsters to spawn (inclusive).
     */
    fun count(min: Int = 1, max: Int = 3) {
        minCount = min
        maxCount = max
    }

    /**
     * Sets the difficulty multiplier as a percentage.
     *
     * 100 = 1x (normal), 150 = 1.5x, 200 = 2x. Applied to monster HP/ATK scaling in codegen.
     *
     * @param percent Scaling percentage.
     */
    fun difficulty(percent: Int) {
        difficultyMultiplier = percent
    }
}

/**
 * Builder for between-wave behavior configuration.
 *
 * Used inside [WaveSurvivalBuilder.betweenWaves] blocks.
 */
class BetweenWaveBuilder {
    internal var behavior: BetweenWaveBehavior = BetweenWaveBehavior.PAUSE
    internal var healAmount: Int = 0
    internal var shopAccess: Boolean = false
    internal var trigger: WaveTrigger = WaveTrigger.TIMER
    internal var pauseDuration: Int = 120

    /**
     * Sets the between-wave behavior mode.
     *
     * @param mode [BetweenWaveBehavior.PAUSE] auto-timer, [IMMEDIATE] no pause, [PLAYER_READY] wait
     *   for player input.
     */
    fun behavior(mode: BetweenWaveBehavior) {
        behavior = mode
        // Sync trigger with behavior for PLAYER_READY
        if (mode == BetweenWaveBehavior.PLAYER_READY) {
            trigger = WaveTrigger.PLAYER_READY
        }
    }

    /**
     * Sets the HP heal amount between waves.
     *
     * @param amount HP restored after each wave (0 = no heal).
     */
    fun heal(amount: Int) {
        healAmount = amount
    }

    /** Enables shop access between waves. */
    fun shop() {
        shopAccess = true
    }

    /**
     * Sets the trigger for starting the next wave.
     *
     * @param t [WaveTrigger.TIMER] auto-starts, [WaveTrigger.PLAYER_READY] waits for player.
     */
    fun trigger(t: WaveTrigger) {
        trigger = t
    }

    /**
     * Sets the pause duration in frames before the next wave auto-starts (TIMER mode).
     *
     * @param frames Frames to wait (60 frames ≈ 1 second at 60fps).
     */
    fun pause(frames: Int) {
        pauseDuration = frames
    }
}
