/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

/**
 * Determines the persistence model for a roguelike run.
 *
 * [PURE] — classic permadeath: all progress is lost on death. No SRAM carryover between runs.
 * [ROGUELITE] — meta-progression: select unlocks and currencies survive across runs via SRAM.
 */
enum class RoguelikeMode {
    PURE,
    ROGUELITE,
}

/**
 * Top-level configuration for a roguelike system.
 *
 * Passed as `"config"` in the [io.github.gbkt.core.ir.GenericSystem] config map with type
 * `"roguelike_system"`. The backend reads this data to emit the appropriate C functions and
 * variable declarations.
 *
 * @property mode Whether this is a pure roguelike or a roguelite with meta-progression.
 * @property permadeath If true, all run-local state is wiped on [runLifecycle] death.
 * @property seedBased If true, the RNG is initialised with [_rogue_seed] for reproducible runs.
 * @property dailyChallenge Optional daily challenge configuration (date-based seed).
 * @property metaProgression Optional roguelite meta-progression configuration (requires [mode] ==
 *   [RoguelikeMode.ROGUELITE]).
 * @property roomClearGating If true, room exits are locked until all enemies are defeated.
 */
data class RoguelikeConfig(
    val mode: RoguelikeMode = RoguelikeMode.PURE,
    val permadeath: Boolean = true,
    val seedBased: Boolean = true,
    val dailyChallenge: DailyChallengeConfig? = null,
    val metaProgression: MetaProgressionConfig? = null,
    val roomClearGating: Boolean = false,
)

/**
 * Configuration for daily challenge mode.
 *
 * When enabled, [roguelike_daily_seed()] computes a deterministic seed from the current date so
 * that all players share the same run layout each day.
 *
 * @property enabled Whether daily challenges are active.
 */
data class DailyChallengeConfig(val enabled: Boolean = true)

/**
 * Configuration for roguelite meta-progression (SRAM-backed).
 *
 * Unlocked items, characters, or abilities survive across runs and are stored in SRAM.
 *
 * @property unlockSlots Number of persistent unlock slots in SRAM. Each slot stores one unlock ID.
 * @property carryOverCurrencies List of currency IDs that persist between runs (e.g. "meta_gold").
 */
data class MetaProgressionConfig(
    val unlockSlots: Int = 8,
    val carryOverCurrencies: List<String> = emptyList(),
)
