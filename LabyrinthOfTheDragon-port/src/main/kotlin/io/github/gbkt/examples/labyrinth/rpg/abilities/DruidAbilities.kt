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
 * Labyrinth of the Dragon — Druid class ability references.
 *
 * Typed container holding [AbilityRef] instances for all 6 Druid abilities. Populated by
 * [GameBuilder.defineDruidAbilities]. Exposes an [all] list for registration in the
 * [io.github.gbkt.examples.labyrinth.rpg.Abilities] aggregator.
 *
 * ## Original C Reference
 *
 * Ability table: `LabyrinthOfTheDragon/src/player.data.c` lines 31–59. Implementations:
 * `LabyrinthOfTheDragon/src/player.c` lines 287–345.
 *
 * ## Ability Overview
 * | Slot | Name          | SP Cost | Target      | Aspect    | Original C Function     |
 * |------|---------------|---------|-------------|-----------|-------------------------|
 * | 0    | Cure Wounds   | 4       | SELF        | —         | `druid_cure_wounds()`   |
 * | 1    | Bark Skin     | 8       | SELF        | —         | `druid_bark_skin()`     |
 * | 2    | Lightning     | 15      | SINGLE      | LIGHTNING | `druid_lightning()`     |
 * | 3    | Heal          | 19      | SELF        | —         | `druid_heal()`          |
 * | 4    | Insect Plague | 28      | ALL enemies | MAGIC     | `druid_insect_plague()` |
 * | 5    | Regenerate    | 33      | SELF        | —         | `druid_regen()`         |
 *
 * ## Class Role
 *
 * Druid is a healer/support class with nature magic. The base attack uses DAMAGE_MAGICAL via
 * `player.matk` vs `target->mdef`. Healing abilities target SELF (no ally targeting in the Original
 * — single-player game). Regen provides sustained HP recovery.
 */
data class LabyrinthDruidAbilities(
    /**
     * Cure Wounds — restores 50% of the Druid's maximum HP.
     *
     * Original: `druid_cure_wounds()` in `player.c` line 287. Formula: `heal_player(player.max_hp /
     * 2)`. SP cost: 4.
     */
    val cureWounds: AbilityRef,

    /**
     * Bark Skin — applies DEF Up buff (wooden bark protects the Druid).
     *
     * Original: `druid_bark_skin()` in `player.c` line 292. Calls
     * `apply_def_up(encounter.player_status_effects, B_TIER, 0)`. Also sets `SPECIAL_BARKSKIN` flag
     * — reduces physical incoming damage. Duration 0 = perpetual for battle. SP cost: 8.
     */
    val barkSkin: AbilityRef,

    /**
     * Lightning — calls down a bolt of lightning on a single enemy.
     *
     * Original: `druid_lightning()` in `player.c` line 299. Uses `player.matk` vs `target->mdef`.
     * A-tier damage with `level_offset(player.level, 10)`. DAMAGE_AIR aspect. SP cost: 15.
     */
    val lightning: AbilityRef,

    /**
     * Heal — fully restores the Druid's HP to maximum.
     *
     * Original: `druid_heal()` in `player.c` line 316. Formula: `heal_player(player.max_hp)` —
     * heals to full. SP cost: 19.
     */
    val heal: AbilityRef,

    /**
     * Insect Plague — summons swarming insects to assault all enemies.
     *
     * Original: `druid_insect_plague()` in `player.c` line 321. Uses `damage_all()` with
     * DAMAGE_MAGICAL and `player.matk` hit check. Damage tier scales: B (lvl 0–34), A (35–74), S
     * (75+) with `level_offset(player.level, 5)`. SP cost: 28.
     */
    val insectPlague: AbilityRef,

    /**
     * Regenerate — applies the Regen buff for sustained HP recovery.
     *
     * Original: `druid_regen()` in `player.c` line 340. Calls
     * `apply_regen(encounter.player_status_effects, tier, 0)`. Tier: A (lvl 0–74), S (75+).
     * Duration 0 = perpetual until end of battle. Heal formula: `regen_hp(tier, max_hp) = (max_hp *
     * (tier+2)) >> 4`. SP cost: 33.
     */
    val regenerate: AbilityRef,
) {
    /** All Druid abilities in slot order (0..5) for game builder registration. */
    val all: List<AbilityRef>
        get() = listOf(cureWounds, barkSkin, lightning, heal, insectPlague, regenerate)
}

// =============================================================================
// Druid ability definitions DSL extension
// =============================================================================

/**
 * Registers all 6 Druid abilities with the game builder.
 *
 * Must be called within the `gbGame {}` DSL block after status effects are defined via
 * [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/player.data.c` lines 31–59 for ability table entries.
 * `LabyrinthOfTheDragon/src/player.c` lines 287–345 for function implementations.
 *
 * @param statusEffects Typed status effect refs returned by
 *   [io.github.gbkt.examples.labyrinth.rpg.defineStatusEffects].
 * @return [LabyrinthDruidAbilities] typed container for downstream use.
 */
fun GameBuilder.defineDruidAbilities(
    statusEffects: LabyrinthStatusEffects
): LabyrinthDruidAbilities {

    // =========================================================================
    // Cure Wounds — druid0 in player.data.c
    // Target: TARGET_SELF, SP cost: 4
    // Original: heal_player(player.max_hp / 2) — restores 50% max HP
    // =========================================================================

    /**
     * Restores 50% of the Druid's maximum HP.
     *
     * Original: `druid_cure_wounds()` in `player.c` line 287. Formula: `heal_player(player.max_hp /
     * 2)`. Cheapest heal — core early game ability.
     */
    val cureWoundsRef =
        ability("druid_cure_wounds") {
            name("Cure Wounds")
            cost(sp = 4)
            targeting(TargetingMode.SELF)
            power(50) // 50% max HP heal (representative; actual = max_hp / 2)
            accuracy(100)
        }

    // =========================================================================
    // Bark Skin — druid1 in player.data.c
    // Target: TARGET_SELF, SP cost: 8
    // Original: apply_def_up(encounter.player_status_effects, B_TIER, 0)
    //           + apply_special(SPECIAL_BARKSKIN)
    // Duration: 0 (perpetual), BARKSKIN reduces all physical incoming damage
    // =========================================================================

    /**
     * Causes the Druid's skin to harden as wood, granting DEF Up and Barkskin protection.
     *
     * Original: `druid_bark_skin()` in `player.c` line 292. Applies `BUFF_DEF_UP` at B-tier
     * perpetual via `apply_def_up()`. Sets `SPECIAL_BARKSKIN` flag — checked in monster hit code to
     * reduce physical damage. Dual-benefit: stat buff + damage mitigation flag.
     */
    val barkSkinRef =
        ability("druid_bark_skin") {
            name("Bark Skin")
            cost(sp = 8)
            targeting(TargetingMode.SELF)
            power(0) // Buff-only — no damage
            accuracy(100)
            appliesEffect(statusEffects.defUp.id, chance = 100)
        }

    // =========================================================================
    // Lightning — druid2 in player.data.c
    // Target: TARGET_SINGLE, SP cost: 15
    // Original: DAMAGE_AIR, A-tier, level_offset(player.level, 10)
    // Hit check: roll_attack_player(player.matk, target->mdef)
    // =========================================================================

    /**
     * Calls down a bolt of lightning on a single target.
     *
     * Original: `druid_lightning()` in `player.c` line 299. Damage: A-tier magical (DAMAGE_AIR)
     * with level offset +10 (scales later than base level). Hit check uses `player.matk` vs
     * `target->mdef`. LIGHTNING (AIR) aspect vulnerability/immunity applies per the monster's
     * `aspect_immune` and `aspect_vuln` flags.
     */
    val lightningRef =
        ability("druid_lightning") {
            name("Lightning")
            cost(sp = 15)
            targeting(TargetingMode.SINGLE_ENEMY)
            aspect(Aspect.LIGHTNING) // DAMAGE_AIR in Original
            power(28) // A-tier magical damage with +10 level offset
            accuracy(85) // matk vs mdef roll
        }

    // =========================================================================
    // Heal — druid3 in player.data.c
    // Target: TARGET_SELF, SP cost: 19
    // Original: heal_player(player.max_hp) — heals to full
    // =========================================================================

    /**
     * Calls radiant green light to fully restore the Druid's HP.
     *
     * Original: `druid_heal()` in `player.c` line 316. Formula: `heal_player(player.max_hp)` —
     * saturates to max HP. The Druid's primary sustain ability at higher levels.
     */
    val healRef =
        ability("druid_heal") {
            name("Heal")
            cost(sp = 19)
            targeting(TargetingMode.SELF)
            power(100) // Full max HP heal (representative; actual = player.max_hp)
            accuracy(100)
        }

    // =========================================================================
    // Insect Plague — druid4 in player.data.c
    // Target: TARGET_ALL, SP cost: 28
    // Original: damage_all(base_damage, player.matk, true, DAMAGE_MAGICAL)
    // Damage tier: B (lvl 0–34), A (35–74), S (75+) + level_offset(player.level, 5)
    // =========================================================================

    /**
     * Summons a swarm of biting insects to assault all active enemies.
     *
     * Original: `druid_insect_plague()` in `player.c` line 321. Uses `damage_all()` — each enemy
     * makes an individual hit check vs `player.matk`. Damage tier scales B→A→S by level. Level
     * offset +5 for damage calculation. DAMAGE_MAGICAL aspect (insect bites are treated as magical
     * in the Original).
     */
    val insectPlagueRef =
        ability("druid_insect_plague") {
            name("Insect Plague")
            cost(sp = 28)
            targeting(TargetingMode.ALL_ENEMIES)
            power(18) // B-tier magical AoE damage (scales A/S at higher levels)
            accuracy(80) // Per-enemy matk roll
        }

    // =========================================================================
    // Regenerate — druid5 in player.data.c
    // Target: TARGET_SELF, SP cost: 33
    // Original: apply_regen(encounter.player_status_effects, tier, 0)
    // Tier: A (lvl 0–74), S (75+) | Duration: 0 (perpetual)
    // HoT formula: regen_hp(tier, max_hp) = (max_hp * (tier+2)) >> 4
    // =========================================================================

    /**
     * Surges with vitality, applying a perpetual heal-over-time buff.
     *
     * Original: `druid_regen()` in `player.c` line 340. Calls
     * `apply_regen(encounter.player_status_effects, tier, 0)`. Tier A → heals ~25% max HP per turn.
     * Tier S (75+) → ~37.5% max HP per turn. Duration 0 = `EFFECT_DURATION_PERPETUAL` — runs until
     * battle ends.
     */
    val regenerateRef =
        ability("druid_regen") {
            name("Regenerate")
            cost(sp = 33)
            targeting(TargetingMode.SELF)
            power(0) // HoT-only — no direct damage
            accuracy(100)
            appliesEffect(statusEffects.regen.id, chance = 100)
        }

    return LabyrinthDruidAbilities(
        cureWounds = cureWoundsRef,
        barkSkin = barkSkinRef,
        lightning = lightningRef,
        heal = healRef,
        insectPlague = insectPlagueRef,
        regenerate = regenerateRef,
    )
}
