/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (enum types for RPG domain)

package io.github.gbkt.rpg.domain

// =============================================================================
// RPG DOMAIN ENUMERATIONS
// =============================================================================
//
// Shared enums used across AbilityDef, StatusEffectDef, MonsterDef, and related
// domain types. Plain Kotlin enums — NOT IR types.
// =============================================================================

/** Determines which targets an ability or effect can be applied to. */
enum class TargetingMode {
    /** Ability targets the caster only. */
    SELF,

    /** Ability targets a single ally (including self). */
    SINGLE_ALLY,

    /** Ability targets a single enemy. */
    SINGLE_ENEMY,

    /** Ability targets all allies simultaneously. */
    ALL_ALLIES,

    /** Ability targets all enemies simultaneously. */
    ALL_ENEMIES,

    /** Ability targets all combatants (allies and enemies). */
    ALL,
}

/**
 * Elemental or magical aspect of an ability or status effect.
 *
 * Used for type-effectiveness calculations (e.g., FIRE vs ICE interactions).
 */
enum class Aspect {
    /** No elemental aspect (physical). */
    NONE,
    FIRE,
    ICE,
    LIGHTNING,
    EARTH,
    WIND,
    WATER,
    LIGHT,
    DARK,
    HOLY,
}

/**
 * Defines how multiple applications of the same status effect interact.
 * - [NONE]: Second application is rejected; only one instance at a time.
 * - [REFRESH_DURATION]: Resets duration back to full; single-instance.
 * - [INTENSITY]: Adds another stack (up to [StatusEffectDef.maxStacks]); damage/healing scales per
 *   stack.
 * - [INDEPENDENT]: Each application is tracked separately (source-aware stacking).
 */
enum class StackMode {
    /** Only one instance allowed; re-application is ignored. */
    NONE,

    /** Re-application resets the duration counter. */
    REFRESH_DURATION,

    /** Adds a stack up to maxStacks; damage/healing may scale with stack count. */
    INTENSITY,

    /**
     * Each application tracked independently (source-aware). Different casters maintain separate
     * stacks.
     */
    INDEPENDENT,
}

/**
 * Broad category of a status effect.
 *
 * Used for immunity checks — a character immune to [DEBUFF] blocks any effect in that category.
 */
enum class EffectCategory {
    /** Positive effect (increases stats, regeneration, etc.). */
    BUFF,

    /** Negative effect (decreases stats, penalties). */
    DEBUFF,

    /** Restricts or prevents character actions (stun, freeze, sleep). */
    CROWD_CONTROL,

    /** Damage-over-time effect (poison, burn, bleed). */
    DOT,
}

/**
 * Event hooks that trigger custom script operations.
 *
 * Stored in [StatusEffectDef.triggers] / [StatusEffectDef.triggerOps] to specify when the effect's
 * script ops fire.
 */
enum class EffectTrigger {
    /** Fires when the affected character lands a hit on an enemy. */
    ON_HIT,

    /** Fires when the affected character takes damage. */
    ON_DAMAGE_TAKEN,

    /** Fires at the start of each of the affected character's turns. */
    ON_TURN_START,

    /** Fires at the end of each of the affected character's turns. */
    ON_TURN_END,

    /** Fires when the affected character is reduced to 0 HP. */
    ON_DEATH,
}

/** Rarity tier of a monster, used for drop/encounter weighting and difficulty scaling. */
enum class MonsterTier {
    COMMON,
    UNCOMMON,
    RARE,
    BOSS,
}

/**
 * Area-of-effect pattern for an ability in the tactical grid combat variant.
 *
 * Ignored by non-grid combat variants (Simple Battle, Combat Engine).
 */
enum class AoeShape {
    /** Targets a single cell. */
    SINGLE,

    /** Targets a straight line of cells (horizontal or vertical). */
    LINE,

    /** Targets a plus-shaped (+) area around the origin cell. */
    CROSS,

    /** Targets a diamond-shaped area around the origin cell. */
    DIAMOND,

    /** Targets all cells in a square area around the origin cell. */
    SQUARE,
}

/**
 * Determines how the resistance check against a status effect is resolved.
 * - [FLAT]: Uses [StatusEffectDef.applyChance] directly as the percentage chance to apply.
 * - [STAT_CONTEST]: Modifies the chance based on caster/target stats: `applyChance -
 *   (target_resistStat - caster_matk)`. Uses [StatusEffectDef.resistStat] to identify the opposing
 *   stat.
 *
 * (GAP-5: developer chooses resist resolution model per effect)
 */
enum class ResistType {
    /** Apply chance is used directly (flat percentage). */
    FLAT,

    /**
     * Apply chance is modified by stat contest: `applyChance - (target_resistStat - caster_matk)`.
     */
    STAT_CONTEST,
}
