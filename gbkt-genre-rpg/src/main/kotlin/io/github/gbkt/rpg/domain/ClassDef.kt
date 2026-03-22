/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (class/job domain types)

package io.github.gbkt.rpg.domain

// =============================================================================
// CLASS/JOB DOMAIN TYPES
// =============================================================================
//
// Plain Kotlin data classes — NOT IR types. Used by ClassBuilder to carry
// character class/job configuration data. DSL builders produce core IR types
// (GenericSystem) from this data.
// =============================================================================

/**
 * Stat growth amounts applied to a character each time they level up.
 *
 * Each field represents the flat bonus added to the corresponding stat on level-up. All fields
 * default to 0 so that only relevant stats need to be specified.
 *
 * @property hp HP gained per level.
 * @property sp SP gained per level.
 * @property atk ATK gained per level.
 * @property def DEF gained per level.
 * @property matk MATK gained per level.
 * @property mdef MDEF gained per level.
 * @property agl AGL gained per level.
 */
data class StatGrowthRate(
    val hp: Int = 0,
    val sp: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    val matk: Int = 0,
    val mdef: Int = 0,
    val agl: Int = 0,
)

/**
 * Records that a character of this class automatically learns an ability at a specific level.
 *
 * @property abilityId The ID of the [AbilityDef] (or ability reference) to learn.
 * @property level The level at which the ability is automatically learned.
 */
data class AbilityLearnEntry(val abilityId: String, val level: Int)

/**
 * Determines whether and how a character can change their class/job.
 * - [LOCKED]: Class is fixed at character creation; cannot be changed.
 * - [SWITCHABLE_FRESH]: Class can be changed, but learned abilities reset.
 * - [SWITCHABLE_WITH_SKILLS]: Class can be changed and learned abilities are retained.
 */
enum class JobChangeMode {
    /** Class is fixed; no job change available. */
    LOCKED,

    /** Job change allowed; abilities learned in previous class are lost. */
    SWITCHABLE_FRESH,

    /** Job change allowed; abilities from previous classes are retained. */
    SWITCHABLE_WITH_SKILLS,
}

/**
 * Definition of a character class or job.
 *
 * Determines stat growth rates, equipment access, learnable abilities, and job-change behavior.
 *
 * @property id Unique identifier for this class (e.g., "warrior", "mage").
 * @property name Display name (e.g., "Warrior", "Black Mage").
 * @property growthRates Per-level stat growth values.
 * @property equipRestrictions The set of [EquipSlot] values this class can use. Defaults to all
 *   slots.
 * @property learnableAbilities Auto-learn ability entries: ability ID + level threshold.
 * @property jobChangeMode How (and whether) a character can switch to this or another class.
 */
data class ClassDef(
    val id: String,
    val name: String,
    val growthRates: StatGrowthRate,
    val equipRestrictions: Set<EquipSlot> = EquipSlot.entries.toSet(),
    val learnableAbilities: List<AbilityLearnEntry> = emptyList(),
    val jobChangeMode: JobChangeMode = JobChangeMode.LOCKED,
)
