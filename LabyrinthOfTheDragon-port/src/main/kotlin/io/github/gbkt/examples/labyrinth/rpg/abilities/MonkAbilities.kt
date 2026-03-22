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
import io.github.gbkt.rpg.domain.TargetingMode
import io.github.gbkt.rpg.dsl.AbilityRef
import io.github.gbkt.rpg.dsl.ability

/**
 * Labyrinth of the Dragon — Monk class ability references.
 *
 * Typed container holding [AbilityRef] instances for all 6 Monk abilities. Populated by
 * [GameBuilder.defineMonkAbilities]. Exposes an [all] list for registration in the
 * [io.github.gbkt.examples.labyrinth.rpg.Abilities] aggregator.
 *
 * ## Original C Reference
 *
 * Ability table: `LabyrinthOfTheDragon/src/player.data.c` lines 95–123. Implementations:
 * `LabyrinthOfTheDragon/src/player.c` lines 494–631.
 *
 * ## Ability Overview
 * | Slot | Name           | SP Cost | Target | Original C Function     |
 * |------|----------------|---------|--------|-------------------------|
 * | 0    | Evasion        | 7       | SELF   | `monk_evasion()`        |
 * | 1    | Open Palm      | 10      | SINGLE | `monk_open_palm()`      |
 * | 2    | Still Mind     | 13      | SELF   | `monk_still_mind()`     |
 * | 3    | Flurry         | 19      | SINGLE | `monk_flurry()`         |
 * | 4    | Diamond Body   | 15      | SELF   | `monk_diamond_body()`   |
 * | 5    | Quivering Palm | 30      | SINGLE | `monk_quivering_palm()` |
 *
 * ## Damage and Scaling
 *
 * Monk uses ATK + AGL combined for hit rolls (`roll_attack_player(player.atk + player.agl, ...)`).
 * This high hit rate makes Monk the most reliable physical attacker. `open_palm` has a level-based
 * trip chance. `flurry` deals 2–4 attacks. `quivering_palm` has instant-kill chance (d8 <
 * kill_chance) then 2x S-tier damage.
 */
data class LabyrinthMonkAbilities(
    /**
     * Evasion — boosts AGL and grants evasion flag for the battle.
     *
     * Original: `monk_evasion()` in `player.c` line 494. Sets `SPECIAL_EVASION` flag and calls
     * `apply_agl_up()` at C/B/A tier by level. Duration 2–3 turns. SP cost: 7.
     */
    val evasion: AbilityRef,

    /**
     * Open Palm — physical strike with chance to knock target prone.
     *
     * Original: `monk_open_palm()` in `player.c` line 516. Hit roll: `player.atk + player.agl` vs
     * `target->def`. Trip chance = d8 < 2/3/4 by level. Damage at B→S tier scaled by
     * `level_offset(player.level, player.agl)`. SP cost: 10.
     */
    val openPalm: AbilityRef,

    /**
     * Still Mind — removes all active debuffs from the Monk.
     *
     * Original: `monk_still_mind()` in `player.c` line 551. Iterates
     * `encounter.player_status_effects`, clears all `is_debuff()` entries. No damage. SP cost: 13.
     */
    val stillMind: AbilityRef,

    /**
     * Flurry — multiple rapid physical strikes on a single target.
     *
     * Original: `monk_flurry()` in `player.c` line 563. 2 attacks (lvl 0–59), 3 attacks (60–79), 4
     * attacks (80+). Damage tier B (0–29), A (30–64), S (65+). SP cost: 19.
     */
    val flurry: AbilityRef,

    /**
     * Diamond Body — grants physical/magical resistance and DEF Up buff.
     *
     * Original: `monk_diamond_body()` in `player.c` line 591. Sets `player.aspect_resist =
     * DAMAGE_PHYSICAL | DAMAGE_MAGICAL` and applies `apply_def_up()` at B/A tier by level. SP
     * cost: 15.
     */
    val diamondBody: AbilityRef,

    /**
     * Quivering Palm — strikes at an enemy's vital energy with instant-kill chance.
     *
     * Original: `monk_quivering_palm()` in `player.c` line 603. Hit roll: `player.atk + player.agl`
     * vs `target->def`. Instant-kill chance: d8 < 1 (lvl 0–60), 2 (61–80), 3 (81+). Blocked by
     * `SPECIAL_INSTANT_KILL` immunity. On non-kill: 2x S-tier damage. SP cost: 30.
     */
    val quiveringPalm: AbilityRef,
) {
    /** All Monk abilities in slot order (0..5) for game builder registration. */
    val all: List<AbilityRef>
        get() = listOf(evasion, openPalm, stillMind, flurry, diamondBody, quiveringPalm)
}

// =============================================================================
// Monk ability definitions DSL extension
// =============================================================================

/**
 * Registers all 6 Monk abilities with the game builder.
 *
 * Must be called within the `gbGame {}` DSL block after status effects are defined via
 * [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/player.data.c` lines 95–123 for ability table entries.
 * `LabyrinthOfTheDragon/src/player.c` lines 494–631 for function implementations.
 *
 * @param statusEffects Typed status effect refs returned by
 *   [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 * @return [LabyrinthMonkAbilities] typed container for downstream use.
 */
fun GameBuilder.defineMonkAbilities(statusEffects: LabyrinthStatusEffects): LabyrinthMonkAbilities {

    // =========================================================================
    // Evasion — monk0 in player.data.c
    // Target: TARGET_SELF, SP cost: 7
    // Original: sets SPECIAL_EVASION flag + apply_agl_up() at C/B/A tier by level
    // AGL Up duration: 2 (lvl 0–34), 3 (35+) | Tier: C (0–34), B (35–69), A (70+)
    // =========================================================================

    /**
     * Grants enhanced evasion and increases agility for several turns.
     *
     * Original: `monk_evasion()` in `player.c` line 494. Sets `player.special_flags |=
     * SPECIAL_EVASION` (checked on monster hits — miss chance). Calls
     * `apply_agl_up(encounter.player_status_effects, agl_up_tier, agl_up_duration)`. Higher AGL
     * improves both turn order and evasion chance.
     */
    val evasionRef =
        ability("monk_evasion") {
            name("Evasion")
            cost(sp = 7)
            targeting(TargetingMode.SELF)
            power(0) // Buff-only — no damage
            accuracy(100)
            appliesEffect(statusEffects.aglUp.id, chance = 100)
        }

    // =========================================================================
    // Open Palm — monk1 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 10
    // Original: ATK+AGL hit roll, B/S tier damage, d8-based trip chance
    // Trip chance: d8 < 2 (lvl 0–30), 3 (31–64), 4 (65+)
    // Damage scaled by level_offset(player.level, player.agl)
    // =========================================================================

    /**
     * A precise open-palm strike with a level-based chance to knock the enemy prone.
     *
     * Original: `monk_open_palm()` in `player.c` line 516. Hit roll: `player.atk + player.agl` vs
     * `target->def` — high accuracy due to AGL addition. On trip: sets
     * `encounter.target->trip_turns = 2/3` (prone skip-turn). Blocked by `SPECIAL_SLEET_STORM`
     * immunity for trip effect.
     *
     * Applies [LabyrinthStatusEffects.paralyzed] at 30% chance to model the trip effect.
     */
    val openPalmRef =
        ability("monk_open_palm") {
            name("Open Palm")
            cost(sp = 10)
            targeting(TargetingMode.SINGLE_ENEMY)
            power(20) // B-tier physical damage scaled by ATK+AGL level offset
            accuracy(95) // High accuracy (ATK + AGL hit roll)
            appliesEffect(statusEffects.paralyzed.id, chance = 30)
        }

    // =========================================================================
    // Still Mind — monk2 in player.data.c
    // Target: TARGET_SELF, SP cost: 13
    // Original: clears all debuffs from encounter.player_status_effects
    // Iterates MAX_ACTIVE_EFFECTS, sets effect->active = false for all is_debuff()
    // =========================================================================

    /**
     * Enters a meditative state that purges all active debuffs.
     *
     * Original: `monk_still_mind()` in `player.c` line 551. Iterates over all active status effect
     * instances and deactivates those where `is_debuff(effect->effect)` returns true. Removes:
     * blind, scared, paralyzed, poisoned, confused, aglDown, atkDown, defDown.
     */
    val stillMindRef =
        ability("monk_still_mind") {
            name("Still Mind")
            cost(sp = 13)
            targeting(TargetingMode.SELF)
            power(0) // Debuff-clear only — no damage or healing
            accuracy(100)
            // No appliesEffect — this removes effects rather than applying them
        }

    // =========================================================================
    // Flurry — monk3 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 19
    // Original: 2–4 attacks, B/A/S tier damage, ATK+AGL hit roll
    // Attack count: 2 (lvl 0–59), 3 (60–79), 4 (80+)
    // Damage tier: B (0–29), A (30–64), S (65+)
    // =========================================================================

    /**
     * Unleashes a rapid flurry of 2–4 physical strikes on a single target.
     *
     * Original: `monk_flurry()` in `player.c` line 563. Uses `ATK + AGL` hit roll. Damage `base_dmg
     * *= attacks` then single call to `damage_monster()`. Effectively multiplies total damage by
     * attack count. Highest single-target sustained DPS for the Monk.
     */
    val flurryRef =
        ability("monk_flurry") {
            name("Flurry")
            cost(sp = 19)
            targeting(TargetingMode.SINGLE_ENEMY)
            power(35) // Multi-hit total: 2x B-tier at lower levels, scales to 4x S-tier
            accuracy(90) // ATK + AGL roll — high hit chance
        }

    // =========================================================================
    // Diamond Body — monk4 in player.data.c
    // Target: TARGET_SELF, SP cost: 15
    // Original: player.aspect_resist = DAMAGE_PHYSICAL | DAMAGE_MAGICAL + apply_def_up()
    // DEF Up tier: B (lvl 0–49), A (50+)
    // =========================================================================

    /**
     * Hardens the Monk's body to resist physical and magical damage.
     *
     * Original: `monk_diamond_body()` in `player.c` line 591. Sets `player.aspect_resist =
     * DAMAGE_PHYSICAL | DAMAGE_MAGICAL` — halves all physical and magical incoming damage. Calls
     * `apply_def_up(encounter.player_status_effects, def_up_tier, 0)`.
     *
     * Applies [LabyrinthStatusEffects.defUp] to represent the defense increase.
     */
    val diamondBodyRef =
        ability("monk_diamond_body") {
            name("Diamond Body")
            cost(sp = 15)
            targeting(TargetingMode.SELF)
            power(0) // Defense buff — no damage
            accuracy(100)
            appliesEffect(statusEffects.defUp.id, chance = 100)
        }

    // =========================================================================
    // Quivering Palm — monk5 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 30
    // Original: ATK+AGL hit roll, instant-kill chance, then 2x S-tier damage
    // Kill chance: d8 < 1 (lvl 0–60), 2 (61–80), 3 (81+)
    // Blocked by SPECIAL_INSTANT_KILL immunity
    // =========================================================================

    /**
     * Strikes at an enemy's vital essence with an instant-kill chance.
     *
     * Original: `monk_quivering_palm()` in `player.c` line 603. Uses `player.atk + player.agl` for
     * hit roll. Instant-kill roll: d8 < 1/2/3 by player level (blocked by SPECIAL_INSTANT_KILL). On
     * non-kill: 2x S-tier damage with `level_offset(player.level, player.agl)`. The Monk's
     * highest-damage, highest-SP-cost ability.
     */
    val quiveringPalmRef =
        ability("monk_quivering_palm") {
            name("Quivering Palm")
            cost(sp = 30)
            targeting(TargetingMode.SINGLE_ENEMY)
            power(45) // 2x S-tier physical damage (highest Monk single-target output)
            accuracy(90) // ATK + AGL roll — high hit chance
        }

    return LabyrinthMonkAbilities(
        evasion = evasionRef,
        openPalm = openPalmRef,
        stillMind = stillMindRef,
        flurry = flurryRef,
        diamondBody = diamondBodyRef,
        quiveringPalm = quiveringPalmRef,
    )
}
