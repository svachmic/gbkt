/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

import io.github.gbkt.core.ir.ScriptOp

/**
 * Domain data class representing an ability (skill/spell) definition.
 *
 * Plain Kotlin data class — NOT an IR type. Used by [io.github.gbkt.rpg.dsl.AbilityBuilder] to
 * carry ability data. The DSL extension function produces a [io.github.gbkt.core.ir.GenericSystem]
 * from this data.
 *
 * @property id Unique identifier used in DSL references.
 * @property name Display name shown in menus and battle UI.
 * @property spCost SP (skill points/mana) consumed on use. Default 0.
 * @property hpCost Optional HP cost to use this ability. Default 0.
 * @property targeting Which targets this ability can be applied to.
 * @property aspect Elemental/magical aspect for type-effectiveness calculations.
 * @property power Base power value for damage or healing calculations.
 * @property accuracy Hit chance as a percentage (0-100). Default 100.
 * @property chargeTurns Number of turns to telegraph/charge before executing. 0 = instant.
 * @property executeOps Script operations that execute the ability effect.
 * @property rangeMin Minimum tile range (0 = melee/self). Used by tactical grid variant only.
 * @property rangeMax Maximum tile range (1 = adjacent). Used by tactical grid variant only.
 * @property aoeShape Area-of-effect pattern. Used by tactical grid variant only.
 * @property appliesEffect ID of the status effect to apply on hit, or null for none.
 * @property effectChance Percentage chance to apply [appliesEffect] (0-100). Default 100.
 */
data class AbilityDef(
    val id: String,
    val name: String,
    val spCost: Int = 0,
    val hpCost: Int = 0,
    val targeting: TargetingMode = TargetingMode.SINGLE_ENEMY,
    val aspect: Aspect = Aspect.NONE,
    val power: Int = 0,
    val accuracy: Int = 100,
    val chargeTurns: Int = 0,
    val executeOps: List<ScriptOp> = emptyList(),
    val rangeMin: Int = 0,
    val rangeMax: Int = 1,
    val aoeShape: AoeShape = AoeShape.SINGLE,
    val appliesEffect: String? = null,
    val effectChance: Int = 100,
)
