/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// WAVE SURVIVAL IR TYPES
// =============================================================================
//
// Wave survival extends CombatEngineSystem with wave progression support.
// These types are in gbkt-ir so that CombatEngineSystem (also in gbkt-ir)
// can reference them directly without a circular dependency.
//
// Wave survival adds:
//   - WaveDef: per-wave configuration (scripted or procedural monster spawning)
//   - WaveSurvivalConfig: overall wave survival configuration
//   - BetweenWaveBehavior: what happens between waves
//   - WaveTrigger: what starts the next wave
// =============================================================================

/**
 * Defines the content (monsters) for a single wave.
 *
 * Either scripted (hand-authored monster ID list) or procedural (PRNG selection from a pool).
 */
sealed interface WaveContent

/**
 * A hand-authored wave with a fixed list of monster IDs.
 *
 * @property monsters Monster IDs to spawn for this wave.
 */
data class ScriptedWave(val monsters: List<String>) : WaveContent

/**
 * A procedurally generated wave that selects monsters from a pool using PRNG.
 *
 * @property monsterPool Pool of monster IDs to select from.
 * @property minCount Minimum number of monsters to spawn (inclusive).
 * @property maxCount Maximum number of monsters to spawn (inclusive).
 * @property difficultyMultiplier Per-wave difficulty scaling as a percentage (100 = 1x, 200 = 2x).
 */
data class ProceduralWave(
    val monsterPool: List<String>,
    val minCount: Int = 1,
    val maxCount: Int = 3,
    val difficultyMultiplier: Int = 100, // percent scaling per wave
) : WaveContent

/**
 * Configuration for a single wave in a wave-survival encounter.
 *
 * @property waveNumber The wave number (1-indexed).
 * @property content The wave content — either [ScriptedWave] or [ProceduralWave].
 * @property betweenWavePauseDuration Duration in frames to pause after this wave completes (before
 *   between-wave behavior triggers). Default 60 frames (~1 second at 60fps).
 */
data class WaveDef(
    val waveNumber: Int,
    val content: WaveContent,
    val betweenWavePauseDuration: Int = 60, // frames
)

/**
 * Controls what happens between waves.
 * - [PAUSE]: A brief pause before the next wave auto-starts (timer controlled by
 *   [WaveSurvivalConfig.pauseDuration]).
 * - [IMMEDIATE]: No pause — next wave starts instantly after the previous wave clears.
 * - [PLAYER_READY]: Wait for the player to press a button before starting the next wave.
 */
enum class BetweenWaveBehavior {
    PAUSE,
    IMMEDIATE,
    PLAYER_READY,
}

/**
 * Controls what triggers the start of the next wave.
 * - [TIMER]: Next wave starts automatically after [WaveSurvivalConfig.pauseDuration] frames.
 * - [PLAYER_READY]: Next wave starts when the player presses a button.
 */
enum class WaveTrigger {
    TIMER,
    PLAYER_READY,
}

/**
 * Complete configuration for wave-survival combat.
 *
 * Attached to a [CombatEngineSystem] with [CombatType.WAVE_SURVIVAL].
 *
 * @property waves The ordered list of wave definitions. Empty = procedural-only mode.
 * @property betweenWaveBehavior What happens between waves.
 * @property healBetweenWaves HP amount healed after each wave (0 = no heal).
 * @property shopAccessBetweenWaves Whether a shop is accessible between waves.
 * @property nextWaveTrigger What triggers the next wave to start.
 * @property pauseDuration Frames to wait before next wave when [betweenWaveBehavior] is
 *   [BetweenWaveBehavior.PAUSE] or [nextWaveTrigger] is [WaveTrigger.TIMER].
 * @property maxWaves Maximum number of waves before victory (0 = unlimited / endless mode).
 */
data class WaveSurvivalConfig(
    val waves: List<WaveDef> = emptyList(),
    val betweenWaveBehavior: BetweenWaveBehavior = BetweenWaveBehavior.PAUSE,
    val healBetweenWaves: Int = 0, // HP heal amount (0 = no heal)
    val shopAccessBetweenWaves: Boolean = false,
    val nextWaveTrigger: WaveTrigger = WaveTrigger.TIMER,
    val pauseDuration: Int = 120, // frames before next wave auto-starts
    val maxWaves: Int = 0, // 0 = unlimited (endless mode)
)
