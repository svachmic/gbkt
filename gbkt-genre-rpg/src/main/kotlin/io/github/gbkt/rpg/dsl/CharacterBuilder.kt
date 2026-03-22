/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.rpg.domain.AbilityLearningConfig
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.ExpCurve

/**
 * Builder for [CombatStats]. Collects all seven stat values.
 *
 * Used inside `stats { }` blocks in both [CharacterBuilder] and [MonsterBuilder].
 *
 * All fields default to 0 (hp defaults to 1) so that callers only need to set the stats they care
 * about.
 */
class CombatStatsBuilder {
    private var hp: Int = 1
    private var atk: Int = 0
    private var def: Int = 0
    private var sp: Int = 0
    private var matk: Int = 0
    private var mdef: Int = 0
    private var agl: Int = 0

    private fun requireNonNeg(stat: String, value: Int): Int {
        require(value >= 0) { "$stat must be non-negative, got $value" }
        return value
    }

    /** Sets the hit point value. Must be positive. */
    fun hp(value: Int) {
        require(value > 0) { "HP must be positive, got $value" }
        hp = value
    }

    /** Sets the attack power value. Must be non-negative. */
    fun atk(value: Int) { atk = requireNonNeg("ATK", value) }

    /** Sets the defense rating value. Must be non-negative. */
    fun def(value: Int) { def = requireNonNeg("DEF", value) }

    /** Sets the skill/magic point value. Must be non-negative. */
    fun sp(value: Int) { sp = requireNonNeg("SP", value) }

    /** Sets the magic attack power value. Must be non-negative. */
    fun matk(value: Int) { matk = requireNonNeg("MATK", value) }

    /** Sets the magic defense rating value. Must be non-negative. */
    fun mdef(value: Int) { mdef = requireNonNeg("MDEF", value) }

    /** Sets the agility value (determines turn order). Must be non-negative. */
    fun agl(value: Int) { agl = requireNonNeg("AGL", value) }

    /** Builds and validates the [CombatStats] instance. */
    fun build(): CombatStats =
        CombatStats(hp = hp, atk = atk, def = def, sp = sp, matk = matk, mdef = mdef, agl = agl)
}

/**
 * Builder for [CharacterDef]. Records character name, stats, and leveling configuration.
 *
 * @param id The unique identifier for the character, passed from the DSL call site.
 */
class CharacterBuilder(val id: String) {
    private var charName: String = id
    private var stats: CombatStats = CombatStats(hp = 1, atk = 0, def = 0)
    private var charLevel: Int = 1
    private var charMaxLevel: Int = 99
    private var charExpCurve: ExpCurve = ExpCurve.STANDARD
    private var onLevelUpOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var learningConfig: AbilityLearningConfig? = null

    /** Sets the display name for the character. */
    fun name(n: String) {
        charName = n
    }

    /** Configures combat statistics using the [CombatStatsBuilder] DSL. */
    fun stats(block: CombatStatsBuilder.() -> Unit) {
        val builder = CombatStatsBuilder()
        builder.block()
        stats = builder.build()
    }

    /**
     * Configures the starting level, maximum level, and experience curve.
     *
     * ```kotlin
     * level(1, maxLevel = 99, expCurve = ExpCurve.STANDARD)
     * ```
     *
     * @param initial Starting level. Must be >= 1.
     * @param maxLevel Maximum level reachable. Default: 99.
     * @param expCurve Leveling progression strategy. Default: [ExpCurve.STANDARD].
     */
    fun level(initial: Int, maxLevel: Int = 99, expCurve: ExpCurve = ExpCurve.STANDARD) {
        charLevel = initial
        charMaxLevel = maxLevel
        charExpCurve = expCurve
    }

    /**
     * Records script operations to execute when the character levels up.
     *
     * ```kotlin
     * onLevelUp { stats.hp += 10; stats.atk += 2 }
     * ```
     *
     * Uses the [ScriptBuilder] DSL to record ops — follows the same pattern as
     * [SimpleBattleBuilder.onVictory].
     */
    fun onLevelUp(block: ScriptBuilder.() -> Unit) {
        onLevelUpOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Configures ability learning for this character.
     *
     * Defines which abilities are learned automatically at specific levels, via skill points, or
     * through teaching items. When set, the backend generates a `check_auto_learn_{id}(level)`
     * function called in the character's level-up handler.
     *
     * ```kotlin
     * character("fighter") {
     *     name("Fighter")
     *     stats { hp(80); atk(15); def(10) }
     *     level(1, maxLevel = 99)
     *     learns {
     *         autoLearn("slash", atLevel = 1)
     *         autoLearn("power_slash", atLevel = 5)
     *         autoLearn("blade_storm", atLevel = 15)
     *         autoLearn("final_blow", atLevel = 35)
     *     }
     * }
     * ```
     *
     * @param block Configuration block for [AbilityLearningBuilder].
     */
    fun learns(block: AbilityLearningBuilder.() -> Unit) {
        learningConfig = AbilityLearningBuilder().apply(block).build()
    }

    /** Builds the [CharacterDef] domain object. */
    fun build(): CharacterDef =
        CharacterDef(
            id = id,
            name = charName,
            stats = stats,
            level = charLevel,
            maxLevel = charMaxLevel,
            expCurve = charExpCurve,
            onLevelUpOps = onLevelUpOps,
            learningConfig = learningConfig,
        )
}
