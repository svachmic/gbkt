/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

/**
 * Combat statistics for a character or monster.
 *
 * Plain Kotlin data class — NOT an IR type. Carries game data that is then used by DSL builders to
 * produce core IR types (SystemIR.GenericSystem).
 *
 * All new fields have backward-compatible defaults so that existing `CombatStats(hp=20, atk=5,
 * def=3)` call sites continue to compile unchanged.
 *
 * @property hp Hit points — must be positive (> 0).
 * @property atk Attack power — must be non-negative (>= 0).
 * @property def Defense rating — must be non-negative (>= 0).
 * @property sp Skill/magic points — must be non-negative (>= 0). Default: 0.
 * @property matk Magic attack power — must be non-negative (>= 0). Default: 0.
 * @property mdef Magic defense rating — must be non-negative (>= 0). Default: 0.
 * @property agl Agility — determines turn order in speed-based combat. Must be non-negative (>= 0).
 *   Default: 0.
 */
data class CombatStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val sp: Int = 0,
    val matk: Int = 0,
    val mdef: Int = 0,
    val agl: Int = 0,
) {
    init {
        require(hp > 0) { "HP must be positive, got $hp" }
        require(atk >= 0) { "ATK must be non-negative, got $atk" }
        require(def >= 0) { "DEF must be non-negative, got $def" }
        require(sp >= 0) { "SP must be non-negative, got $sp" }
        require(matk >= 0) { "MATK must be non-negative, got $matk" }
        require(mdef >= 0) { "MDEF must be non-negative, got $mdef" }
        require(agl >= 0) { "AGL must be non-negative, got $agl" }
    }
}
