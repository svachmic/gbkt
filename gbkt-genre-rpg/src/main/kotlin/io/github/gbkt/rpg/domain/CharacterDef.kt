/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

import io.github.gbkt.core.ir.ScriptOp

/**
 * Domain data class representing a playable character definition.
 *
 * Plain Kotlin data class — NOT an IR type. Used by [io.github.gbkt.rpg.dsl.CharacterBuilder] to
 * carry character data. DSL builders produce core IR types from this data.
 *
 * All new fields have backward-compatible defaults so that existing `CharacterDef(id = "hero", name
 * = "Hero", stats = stats)` call sites continue to compile unchanged.
 *
 * @property id Unique identifier used in DSL references and party lists.
 * @property name Display name shown in battle UI.
 * @property stats Combat statistics (HP, ATK, DEF, and optionally SP, MATK, MDEF, AGL).
 * @property level Starting level. Must be in [1..[maxLevel]]. Default: 1.
 * @property maxLevel Maximum level the character can reach. Default: 99.
 * @property expCurve Leveling progression strategy. Default: [ExpCurve.STANDARD].
 * @property onLevelUpOps Script operations executed each time the character levels up. Default:
 *   empty.
 * @property learningConfig Optional ability learning configuration. When non-null, the backend
 *   generates level-based auto-learn checks and skill-point unlock logic for this character.
 *   Default: null (no ability learning — abilities must be granted manually).
 */
data class CharacterDef(
    val id: String,
    val name: String,
    val stats: CombatStats,
    val level: Int = 1,
    val maxLevel: Int = 99,
    val expCurve: ExpCurve = ExpCurve.STANDARD,
    val onLevelUpOps: List<ScriptOp> = emptyList(),
    val learningConfig: AbilityLearningConfig? = null,
) {
    init {
        require(level in 1..maxLevel) { "level ($level) must be in range 1..$maxLevel (maxLevel)" }
    }
}
