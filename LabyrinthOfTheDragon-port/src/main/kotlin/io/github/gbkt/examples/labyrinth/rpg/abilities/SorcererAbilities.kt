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
 * Labyrinth of the Dragon — Sorcerer class ability references.
 *
 * Typed container holding [AbilityRef] instances for all 6 Sorcerer abilities. Populated by
 * [GameBuilder.defineSorcererAbilities]. Exposes an [all] list for registration in the
 * [io.github.gbkt.examples.labyrinth.rpg.Abilities] aggregator.
 *
 * ## Original C Reference
 *
 * Ability table: `LabyrinthOfTheDragon/src/player.data.c` lines 127–155. Implementations:
 * `LabyrinthOfTheDragon/src/player.c` lines 657–783.
 *
 * ## Ability Overview
 * | Slot | Name         | SP Cost | Target      | Aspect | Original C Function       |
 * |------|--------------|---------|-------------|--------|---------------------------|
 * | 0    | Darkness     | 4       | ALL enemies | DARK   | `sorcerer_darkness()`     |
 * | 1    | Fireball     | 12      | ALL enemies | FIRE   | `sorcerer_fireball()`     |
 * | 2    | Haste        | 15      | SELF        | —      | `sorcerer_haste()`        |
 * | 3    | Sleetstorm   | 20      | ALL enemies | ICE    | `sorcerer_sleetstorm()`   |
 * | 4    | Disintegrate | 28      | SINGLE      | DARK   | `sorcerer_disintegrate()` |
 * | 5    | Wild Magic   | 33      | ALL enemies | —      | `sorcerer_wild_magic()`   |
 */
data class LabyrinthSorcererAbilities(
    /**
     * Darkness — blinds all enemies (applies Blind debuff).
     *
     * Original: `sorcerer_darkness()` in `player.c` line 657. Calls `apply_blind()` on each active
     * monster. Duration 3–5 based on level. SP cost: 4.
     */
    val darkness: AbilityRef,

    /**
     * Fireball — FIRE explosion hitting all enemies.
     *
     * Original: `sorcerer_fireball()` in `player.c` line 681. Damage at C-tier (lvl 0–49) or B-tier
     * (50+). Hits all enemies with fire aspect. Half damage if roll fails. SP cost: 12.
     */
    val fireball: AbilityRef,

    /**
     * Haste — applies the Haste buff to self (double attacks, 150% healing).
     *
     * Original: `sorcerer_haste()` in `player.c` line 707. Calls
     * `apply_haste(encounter.player_status_effects, B_TIER, 0)`. Duration 0 = perpetual until end
     * of battle. SP cost: 15.
     */
    val haste: AbilityRef,

    /**
     * Sleetstorm — coats the battlefield in sleet (ICE aspect, all-enemy effect).
     *
     * Original: `sorcerer_sleetstorm()` in `player.c` line 713. Sets `player.special_flags |=
     * SPECIAL_SLEET_STORM` — causes monsters to trip each turn (slip on ice mechanic). ICE aspect,
     * immunity-resistant. Modeled as ALL_ENEMIES + AGL Down (slow/trip). SP cost: 20.
     */
    val sleetstorm: AbilityRef,

    /**
     * Disintegrate — single-target death ray with instant-kill chance.
     *
     * Original: `sorcerer_disintegrate()` in `player.c` line 719. Uses `player.matk + 10` for hit
     * roll. Chance to instant-kill (d8 < 2/3/4 by level). On non-kill: 2x S-tier damage with level
     * offset +5. SP cost: 28.
     */
    val disintegrate: AbilityRef,

    /**
     * Wild Magic — chaotic random effects on all enemies.
     *
     * Original: `sorcerer_wild_magic()` in `player.c` line 749. Per-enemy d8 roll: (0) 1 HP, (1)
     * max HP–1, (2–3) AGL/DEF/ATK Down, (4–5) Confused+Blind, (6) Fireball, (7) Sleetstorm. Modeled
     * as ALL_ENEMIES with AGL/DEF/ATK Down debuff chance. SP cost: 33.
     */
    val wildMagic: AbilityRef,
) {
    /** All Sorcerer abilities in slot order (0..5) for game builder registration. */
    val all: List<AbilityRef>
        get() = listOf(darkness, fireball, haste, sleetstorm, disintegrate, wildMagic)
}

// =============================================================================
// Sorcerer ability definitions DSL extension
// =============================================================================

/**
 * Registers all 6 Sorcerer abilities with the game builder.
 *
 * Must be called within the `gbGame {}` DSL block after status effects are defined via
 * [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/player.data.c` lines 127–155 for ability table entries.
 * `LabyrinthOfTheDragon/src/player.c` lines 657–783 for function implementations.
 *
 * @param statusEffects Typed status effect refs returned by
 *   [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 * @return [LabyrinthSorcererAbilities] typed container for downstream use.
 */
fun GameBuilder.defineSorcererAbilities(
    statusEffects: LabyrinthStatusEffects
): LabyrinthSorcererAbilities {

    // =========================================================================
    // Darkness — sorcerer0 in player.data.c
    // Target: TARGET_ALL, SP cost: 4
    // Original: apply_blind() on each active monster
    // Duration: 3 (lvl 0–29), 4 (30–49), 5 (50+) | Tier: B (0–44), A (45+)
    // =========================================================================

    /**
     * Blinds all enemies with magical darkness.
     *
     * Original: `sorcerer_darkness()` in `player.c` line 657. Calls
     * `apply_blind(monster->status_effects, tier, turns, monster->debuff_immune)` for each active
     * monster. Blind reduces hit accuracy (effective ATK for hit rolls).
     */
    val darknessRef =
        ability("sorcerer_darkness") {
            name("Darkness")
            cost(sp = 4)
            targeting(TargetingMode.ALL_ENEMIES)
            aspect(Aspect.DARK)
            power(0) // No damage — debuff only
            accuracy(100) // Resisted by debuff_immune flag
            appliesEffect(statusEffects.blind.id, chance = 90)
        }

    // =========================================================================
    // Fireball — sorcerer1 in player.data.c
    // Target: TARGET_ALL, SP cost: 12
    // Original: damage_all_no_miss(damage, DAMAGE_FIRE)
    // Damage tier: C (lvl 0–49), B (50+) | Half damage if matk roll fails
    // =========================================================================

    /**
     * Hurls a FIRE explosion at all enemies.
     *
     * Original: `sorcerer_fireball()` in `player.c` line 681. Uses `damage_all_no_miss()` — cannot
     * miss but takes immunities and resistances. Damage is halved if the initial
     * `roll_attack_player(player.matk, lowest_mdef)` fails. Monsters with FIRE immunity take no
     * damage.
     */
    val fireballRef =
        ability("sorcerer_fireball") {
            name("Fireball")
            cost(sp = 12)
            targeting(TargetingMode.ALL_ENEMIES)
            aspect(Aspect.FIRE)
            power(20) // C-tier fire AoE damage (scales to B-tier at level 50+)
            accuracy(85) // Reduced on fail; no-miss in Original but full damage needs matk roll
        }

    // =========================================================================
    // Haste — sorcerer2 in player.data.c
    // Target: TARGET_SELF, SP cost: 15
    // Original: apply_haste(encounter.player_status_effects, B_TIER, 0)
    // Duration: 0 (perpetual until end of battle)
    // =========================================================================

    /**
     * Speeds up the Sorcerer to grant double attacks and 150% healing for the battle.
     *
     * Original: `sorcerer_haste()` in `player.c` line 707. Calls
     * `apply_haste(encounter.player_status_effects, B_TIER, 0)`. Duration 0 =
     * `EFFECT_DURATION_PERPETUAL` — lasts until end of battle. `has_special(SPECIAL_HASTE)` check
     * in `damage_monster()` doubles damage rolls.
     */
    val hasteRef =
        ability("sorcerer_haste") {
            name("Haste")
            cost(sp = 15)
            targeting(TargetingMode.SELF)
            power(0) // Buff-only — no damage
            accuracy(100)
            appliesEffect(statusEffects.haste.id, chance = 100)
        }

    // =========================================================================
    // Sleetstorm — sorcerer3 in player.data.c
    // Target: TARGET_ALL, SP cost: 20
    // Original: sets player.special_flags |= SPECIAL_SLEET_STORM
    // Effect: monsters slip on ice each turn (trip_turns set in encounter.c)
    // Monsters with SPECIAL_SLEET_STORM immunity are unaffected
    // =========================================================================

    /**
     * Coats the battlefield in sleet, causing enemies to slip and fall.
     *
     * Original: `sorcerer_sleetstorm()` in `player.c` line 713. Sets `SPECIAL_SLEET_STORM` flag on
     * the encounter. Each monster turn there is a chance they slip on ice (checked in encounter.c),
     * setting `trip_turns` (prone). Immunity checked via `monster->special_immune &
     * SPECIAL_SLEET_STORM`.
     *
     * Modeled as AoE that applies [LabyrinthStatusEffects.aglDown] (slows enemies, reduces turn
     * order — closest DSL approximation of the slow/trip field effect).
     */
    val sleetstormRef =
        ability("sorcerer_sleetstorm") {
            name("Sleetstorm")
            cost(sp = 20)
            targeting(TargetingMode.ALL_ENEMIES)
            aspect(Aspect.ICE)
            power(0) // Field effect only — no direct damage
            accuracy(100)
            appliesEffect(statusEffects.aglDown.id, chance = 80)
        }

    // =========================================================================
    // Disintegrate — sorcerer4 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 28
    // Original: instant-kill chance (d8 < 2/3/4 by level) then 2x S-tier damage
    // Uses player.matk + 10 for hit roll (bonus accuracy vs normal attacks)
    // Instant-kill blocked by SPECIAL_INSTANT_KILL immunity
    // =========================================================================

    /**
     * Fires a ray of DEATH at a single target — chance of instant kill.
     *
     * Original: `sorcerer_disintegrate()` in `player.c` line 719. Hit check:
     * `roll_attack_player(player.matk + 10, target->mdef)` — enhanced accuracy. Instant-kill
     * chance: d8 < 2 (lvl 0–40), 3 (41–60), 4 (61+). If not killed: `2x S-tier damage` with
     * `level_offset(player.level, 5)`. Blocked by `SPECIAL_INSTANT_KILL` immunity flag.
     */
    val disintegrateRef =
        ability("sorcerer_disintegrate") {
            name("Disintegrate")
            cost(sp = 28)
            targeting(TargetingMode.SINGLE_ENEMY)
            aspect(Aspect.DARK)
            power(40) // 2x S-tier magical damage (highest single-target output)
            accuracy(95) // matk + 10 bonus in Original
        }

    // =========================================================================
    // Wild Magic — sorcerer5 in player.data.c
    // Target: TARGET_ALL, SP cost: 33
    // Original: per-enemy d8 roll with 8 outcomes (see function comment)
    // =========================================================================

    /**
     * Unleashes chaotic magic on all enemies with unpredictable effects.
     *
     * Original: `sorcerer_wild_magic()` in `player.c` line 749. Per-enemy d8 roll determines
     * effect: 0 → set target HP to 1 (near-death) 1 → set target HP to max-1 (near-heal) 2–3 →
     * apply AGL Down + DEF Down + ATK Down at A-tier for 10 turns 4–5 → apply Confused + Blind at
     * A-tier for 10 turns 6 → trigger `sorcerer_fireball()` (recursive) 7 → trigger
     * `sorcerer_sleetstorm()` (recursive)
     *
     * Modeled as AoE applying [LabyrinthStatusEffects.confused] (chaotic
     * behavior) + [LabyrinthStatusEffects.aglDown] (debuff outcome) as representative effects.
     */
    val wildMagicRef =
        ability("sorcerer_wild_magic") {
            name("Wild Magic")
            cost(sp = 33)
            targeting(TargetingMode.ALL_ENEMIES)
            power(0) // Variable — outcome determined by d8 roll per target
            accuracy(100)
            appliesEffect(statusEffects.confused.id, chance = 50) // 25% of outcomes (rolls 4-5)
        }

    return LabyrinthSorcererAbilities(
        darkness = darknessRef,
        fireball = fireballRef,
        haste = hasteRef,
        sleetstorm = sleetstormRef,
        disintegrate = disintegrateRef,
        wildMagic = wildMagicRef,
    )
}
