/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.DailyChallengeConfig
import io.github.gbkt.rpg.domain.MetaProgressionConfig
import io.github.gbkt.rpg.domain.RoguelikeConfig
import io.github.gbkt.rpg.domain.RoguelikeMode

/** Builder for daily challenge configuration within [RoguelikeBuilder]. */
class DailyChallengeBuilder {
    private var enabled: Boolean = true

    /** Enables or disables daily challenge mode. Default true. */
    fun enabled(value: Boolean) {
        enabled = value
    }

    /** Builds the [DailyChallengeConfig]. */
    fun build(): DailyChallengeConfig = DailyChallengeConfig(enabled = enabled)
}

/** Builder for roguelite meta-progression configuration within [RoguelikeBuilder]. */
class MetaProgressionBuilder {
    private var unlockSlots: Int = 8
    private val carryOverCurrencies: MutableList<String> = mutableListOf()

    /**
     * Sets the number of persistent unlock slots in SRAM.
     *
     * Each slot stores one unlock ID (character, ability, or modifier).
     *
     * @param count Number of unlock slots (1-255).
     */
    fun unlockSlots(count: Int) {
        unlockSlots = count
    }

    /**
     * Adds a currency ID that persists between runs.
     *
     * ```kotlin
     * carryOver("meta_gold")
     * carryOver("prestige_tokens")
     * ```
     */
    fun carryOver(currencyId: String) {
        carryOverCurrencies.add(currencyId)
    }

    /** Builds the [MetaProgressionConfig]. */
    fun build(): MetaProgressionConfig =
        MetaProgressionConfig(
            unlockSlots = unlockSlots,
            carryOverCurrencies = carryOverCurrencies.toList(),
        )
}

/**
 * Builder for a roguelike/roguelite system registered via [io.github.gbkt.rpg.dsl.roguelike].
 *
 * Collects all roguelike configuration options — mode, permadeath, seed-based RNG, daily challenge,
 * meta-progression, and room-clear gating — and produces a [RoguelikeConfig].
 *
 * Example usage:
 * ```kotlin
 * roguelike("dungeon_run") {
 *     mode(RoguelikeMode.ROGUELITE)
 *     permadeath(true)
 *     seedBased(true)
 *     dailyChallenge { enabled(true) }
 *     metaProgression {
 *         unlockSlots(16)
 *         carryOver("meta_gold")
 *     }
 *     roomClearGating(true)
 * }
 * ```
 */
class RoguelikeBuilder {
    private var mode: RoguelikeMode = RoguelikeMode.PURE
    private var permadeath: Boolean = true
    private var seedBased: Boolean = true
    private var dailyChallenge: DailyChallengeConfig? = null
    private var metaProgression: MetaProgressionConfig? = null
    private var roomClearGating: Boolean = false

    /**
     * Sets whether this is a pure roguelike or a roguelite with SRAM meta-progression.
     *
     * Default: [RoguelikeMode.PURE].
     */
    fun mode(value: RoguelikeMode) {
        mode = value
    }

    /**
     * Enables or disables permadeath.
     *
     * When enabled, all run-local state (HP, inventory, floor progress) is wiped on death. Default:
     * true.
     */
    fun permadeath(enabled: Boolean) {
        permadeath = enabled
    }

    /**
     * Enables or disables seed-based RNG.
     *
     * When enabled, the run RNG is initialised from `_rogue_seed`, making runs reproducible.
     * Default: true.
     */
    fun seedBased(enabled: Boolean) {
        seedBased = enabled
    }

    /**
     * Configures daily challenge mode using a [DailyChallengeBuilder] block.
     *
     * When configured, `roguelike_daily_seed()` computes a date-based seed so all players share the
     * same run layout each day.
     *
     * ```kotlin
     * dailyChallenge { enabled(true) }
     * ```
     */
    fun dailyChallenge(block: DailyChallengeBuilder.() -> Unit) {
        val builder = DailyChallengeBuilder()
        builder.block()
        dailyChallenge = builder.build()
    }

    /**
     * Configures roguelite meta-progression using a [MetaProgressionBuilder] block.
     *
     * Requires [mode] to be [RoguelikeMode.ROGUELITE]. Persistent unlocks and currencies are stored
     * in SRAM and survive across runs.
     *
     * ```kotlin
     * metaProgression {
     *     unlockSlots(16)
     *     carryOver("meta_gold")
     * }
     * ```
     */
    fun metaProgression(block: MetaProgressionBuilder.() -> Unit) {
        val builder = MetaProgressionBuilder()
        builder.block()
        metaProgression = builder.build()
    }

    /**
     * Enables or disables room-clear gating.
     *
     * When enabled, exits from a room are locked until all enemies in that room are defeated. The
     * codegen emits `roguelike_check_room_clear()` to test this condition. Default: false.
     */
    fun roomClearGating(enabled: Boolean) {
        roomClearGating = enabled
    }

    /** Builds the [RoguelikeConfig] from all configured options. */
    fun build(): RoguelikeConfig =
        RoguelikeConfig(
            mode = mode,
            permadeath = permadeath,
            seedBased = seedBased,
            dailyChallenge = dailyChallenge,
            metaProgression = metaProgression,
            roomClearGating = roomClearGating,
        )
}
