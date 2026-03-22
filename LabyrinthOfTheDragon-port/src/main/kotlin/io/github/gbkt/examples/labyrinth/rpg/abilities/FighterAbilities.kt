/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName")

package io.github.gbkt.examples.labyrinth.rpg.abilities

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.examples.labyrinth.rpg.LabyrinthStatusEffects
import io.github.gbkt.rpg.domain.Aspect
import io.github.gbkt.rpg.domain.TargetingMode
import io.github.gbkt.rpg.dsl.AbilityRef
import io.github.gbkt.rpg.dsl.ability

/**
 * Labyrinth of the Dragon — Fighter class ability references.
 *
 * Typed container holding [AbilityRef] instances for all 6 Fighter abilities. Populated by
 * [GameBuilder.defineFighterAbilities]. Exposes an [all] list for registration in the
 * [io.github.gbkt.examples.labyrinth.rpg.Abilities] aggregator.
 *
 * ## Original C Reference
 *
 * Ability table: `LabyrinthOfTheDragon/src/player.data.c` lines 63–91. Implementations:
 * `LabyrinthOfTheDragon/src/player.c` lines 376–462.
 *
 * ## Ability Overview
 * | Slot | Name         | SP Cost | Target      | Original C Function      |
 * |------|--------------|---------|-------------|--------------------------|
 * | 0    | Second Wind  | 7       | SELF        | `fighter_second_wind()`  |
 * | 1    | Action Surge | 14      | SINGLE      | `fighter_action_surge()` |
 * | 2    | Cleave       | 19      | ALL enemies | `fighter_cleave()`       |
 * | 3    | Trip Attack  | 23      | SINGLE      | `fighter_trip_attack()`  |
 * | 4    | Menace       | 28      | ALL enemies | `fighter_menace()`       |
 * | 5    | Indomitable  | 35      | SELF        | `fighter_indomitable()`  |
 */
data class LabyrinthFighterAbilities(
    /**
     * Second Wind — restores 25% of the Fighter's maximum HP.
     *
     * Original: `fighter_second_wind()` in `player.c` line 376. Formula: `heal_player(player.max_hp
     * / 4)`. SP cost: 7.
     */
    val secondWind: AbilityRef,

    /**
     * Action Surge — deals a powerful double-damage physical strike to a single target.
     *
     * Original: `fighter_action_surge()` in `player.c` line 381. Formula: `base_dmg * 2` at B/A/S
     * tier with `level_offset(player.level, 3)`. SP cost: 14.
     */
    val actionSurge: AbilityRef,

    /**
     * Cleave — swings at all enemies simultaneously.
     *
     * Original: `fighter_cleave()` in `player.c` line 396. Hits all enemies at C→S tier damage
     * scaling with level. SP cost: 19. Note: Original uses `DAMAGE_MAGICAL` aspect with
     * `player.matk` for the hit check.
     */
    val cleave: AbilityRef,

    /**
     * Trip Attack — knocks a single enemy prone (skip next turn).
     *
     * Original: `fighter_trip_attack()` in `player.c` line 417. Sets `target->trip_turns = 2/3/4`
     * (level-based). No direct damage. Modeled as applying `paralyzed` status (forced turn-skip).
     * SP cost: 23.
     */
    val tripAttack: AbilityRef,

    /**
     * Menace — applies Scared debuff to all active enemies.
     *
     * Original: `fighter_menace()` in `player.c` line 437. Calls `apply_scared()` for each active
     * monster. SP cost: 28.
     */
    val menace: AbilityRef,

    /**
     * Indomitable — grants resistance to all damage types for the round.
     *
     * Original: `fighter_indomitable()` in `player.c` line 458. Sets `player.aspect_resist = 0xFF`
     * (all damage aspects resisted). Modeled as applying DEF Up (best DSL approximation of
     * all-resist). SP cost: 35.
     */
    val indomitable: AbilityRef,
) {
    /** All Fighter abilities in slot order (0..5) for game builder registration. */
    val all: List<AbilityRef>
        get() = listOf(secondWind, actionSurge, cleave, tripAttack, menace, indomitable)
}

// =============================================================================
// Fighter ability definitions DSL extension
// =============================================================================

/**
 * Registers all 6 Fighter abilities with the game builder.
 *
 * Must be called within the `gbGame {}` DSL block after status effects are defined via
 * [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/player.data.c` lines 63–91 for ability table entries.
 * `LabyrinthOfTheDragon/src/player.c` lines 376–462 for function implementations.
 *
 * @param statusEffects Typed status effect refs returned by
 *   [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 * @return [LabyrinthFighterAbilities] typed container for downstream use.
 */
fun GameBuilder.defineFighterAbilities(
    statusEffects: LabyrinthStatusEffects
): LabyrinthFighterAbilities {

    // =========================================================================
    // Second Wind — fighter0 in player.data.c
    // Target: TARGET_SELF, SP cost: 7
    // Original: heal_player(player.max_hp / 4) — restores 25% max HP
    // =========================================================================

    /**
     * Restores 25% of the Fighter's maximum HP.
     *
     * Original: `fighter_second_wind()` in `player.c` line 376. Formula: `heal_player(player.max_hp
     * / 4)`.
     */
    val secondWindRef =
        ability("fighter_second_wind") {
            name("Second Wind")
            cost(sp = 7)
            targeting(TargetingMode.SELF)
            power(25) // 25% max HP heal (representative; actual = max_hp / 4)
            accuracy(100)
        }

    // =========================================================================
    // Action Surge — fighter1 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 14
    // Original: damage_monster(base_dmg * 2, DAMAGE_PHYSICAL)
    // Damage tier: B_TIER (lvl 0–19), A_TIER (20–39), S_TIER (40+)
    // =========================================================================

    /**
     * Deals a powerful double-damage physical strike to a single target.
     *
     * Original: `fighter_action_surge()` in `player.c` line 381. Formula: `base_dmg * 2` at B/A/S
     * tier with `level_offset(player.level, 3)`.
     */
    val actionSurgeRef =
        ability("fighter_action_surge") {
            name("Action Surge")
            cost(sp = 14)
            targeting(TargetingMode.SINGLE_ENEMY)
            power(30) // High physical damage (2x B-tier base; scales to A/S at higher levels)
            accuracy(90)
            aspect(Aspect.NONE) // DAMAGE_PHYSICAL in Original
        }

    // =========================================================================
    // Cleave — fighter2 in player.data.c
    // Target: TARGET_ALL, SP cost: 19
    // Original: damage_all(base_damage, player.matk, true, DAMAGE_MAGICAL)
    // Damage tier: C (lvl 0–19), B (20–49), A (50–74), S (75+)
    // =========================================================================

    /**
     * Swings at all enemies simultaneously for physical damage.
     *
     * Original: `fighter_cleave()` in `player.c` line 396. Damage tier scales C→S with player
     * level. Note: Original quirk — uses `DAMAGE_MAGICAL` aspect with `player.matk` for hit check.
     */
    val cleaveRef =
        ability("fighter_cleave") {
            name("Cleave")
            cost(sp = 19)
            targeting(TargetingMode.ALL_ENEMIES)
            power(15) // Reduced per-target power (C-tier AoE; scales with level)
            accuracy(80)
        }

    // =========================================================================
    // Trip Attack — fighter3 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 23
    // Original: sets target->trip_turns = 2/3/4 — forces prone (skip next turn)
    // Uses reduced defense roll: get_monster_def(level_offset(target->level, -5), C_TIER)
    // =========================================================================

    /**
     * Sweeps a single enemy's legs, knocking them prone (skip next turn).
     *
     * Original: `fighter_trip_attack()` in `player.c` line 417. Easier hit check than normal
     * (reduced monster def by 5 level tiers). On hit, sets `target->trip_turns` causing the monster
     * to skip 2–4 turns. No direct damage — pure crowd control.
     *
     * Applies [LabyrinthStatusEffects.paralyzed] (forced turn-skip) as the closest DSL match.
     */
    val tripAttackRef =
        ability("fighter_trip_attack") {
            name("Trip Attack")
            cost(sp = 23)
            targeting(TargetingMode.SINGLE_ENEMY)
            power(0) // No direct damage in Original — prone effect only
            accuracy(85) // Easier hit check (reduced monster def)
            appliesEffect(statusEffects.paralyzed.id, chance = 100)
        }

    // =========================================================================
    // Menace — fighter4 in player.data.c
    // Target: TARGET_ALL, SP cost: 28
    // Original: apply_scared() to each active monster
    // Duration: 2 (lvl 0–29), 3 (30–59), 4 (60+) | Tier: A (0–59), S (60+)
    // =========================================================================

    /**
     * Growls menacingly to apply the Scared debuff to all active enemies.
     *
     * Original: `fighter_menace()` in `player.c` line 437. Calls
     * `apply_scared(monster->status_effects, tier, turns, monster->debuff_immune)` for each active
     * monster. Scared causes enemies to flee or freeze instead of acting.
     */
    val menaceRef =
        ability("fighter_menace") {
            name("Menace")
            cost(sp = 28)
            targeting(TargetingMode.ALL_ENEMIES)
            power(0) // No damage — debuff only
            accuracy(100) // Resisted by debuff_immune, not a standard accuracy roll
            appliesEffect(statusEffects.scared.id, chance = 90)
        }

    // =========================================================================
    // Indomitable — fighter5 in player.data.c
    // Target: TARGET_SELF, SP cost: 35
    // Original: player.aspect_resist = 0xFF — resists ALL damage aspects this round
    // =========================================================================

    /**
     * Bolsters the Fighter's resolve, granting resistance to all damage types this round.
     *
     * Original: `fighter_indomitable()` in `player.c` line 458. Sets `player.aspect_resist = 0xFF`
     * (resists all damage aspects). Damage resistance halves incoming damage in the Original.
     *
     * Modeled as applying [LabyrinthStatusEffects.defUp] (best DSL approximation of all-type damage
     * resistance — DEF Up increases physical+magical mitigation).
     */
    val indomitableRef =
        ability("fighter_indomitable") {
            name("Indomitable")
            cost(sp = 35)
            targeting(TargetingMode.SELF)
            power(0) // Effect-only (all-type damage resist)
            accuracy(100)
            appliesEffect(statusEffects.defUp.id, chance = 100)
        }

    return LabyrinthFighterAbilities(
        secondWind = secondWindRef,
        actionSurge = actionSurgeRef,
        cleave = cleaveRef,
        tripAttack = tripAttackRef,
        menace = menaceRef,
        indomitable = indomitableRef,
    )
}
