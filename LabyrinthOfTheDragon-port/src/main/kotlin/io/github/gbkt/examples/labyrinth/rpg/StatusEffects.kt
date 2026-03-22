/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName", "LongMethod")

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.rpg.domain.EffectCategory
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.dsl.StatusEffectRef
import io.github.gbkt.rpg.dsl.statusEffect

/**
 * Labyrinth of the Dragon — status effect definitions.
 *
 * Ports all 13 active status effects from the Original C implementation in
 * `LabyrinthOfTheDragon/src/stats.h`.
 *
 * ## Original C Reference
 *
 * Status effects are defined as the `StatusEffect` enum in `stats.h` lines 43–61:
 * ```c
 * typedef enum StatusEffect {
 *   DEBUFF_BLIND,        // 0
 *   DEBUFF_SCARED,       // 1
 *   DEBUFF_PARALYZED,    // 2
 *   DEBUFF_POISONED,     // 3
 *   DEBUFF_CONFUSED,     // 4
 *   DEBUFF_AGL_DOWN,     // 5
 *   DEBUFF_ATK_DOWN,     // 6
 *   DEBUFF_DEF_DOWN,     // 7
 *   BUFF_UNUSED_0,       // 8  (unused placeholder)
 *   BUFF_UNUSED_1,       // 9  (unused placeholder)
 *   BUFF_UNUSED_2,       // 10 (unused placeholder)
 *   BUFF_HASTE,          // 11
 *   BUFF_REGEN,          // 12
 *   BUFF_AGL_UP,         // 13
 *   BUFF_ATK_UP,         // 14
 *   BUFF_DEF_UP,         // 15
 * } StatusEffect;
 * ```
 *
 * The 3 `BUFF_UNUSED_*` entries are skipped — they have no game behavior.
 *
 * ## Effect Mechanics (from `stats.c`, `encounter.h`, `item.c`)
 * - **Debuffs** applied by monsters to the player use tier (C/B/A/S) + duration
 * - **Buffs** applied by abilities (Bark Skin, Evasion, etc.) have 0 duration (perpetual until
 *   battle ends)
 * - **Item buffs** (ATK Up, DEF Up, Regen, Haste) are always perpetual
 *   (`EFFECT_DURATION_PERPETUAL`)
 * - Poison/Regen magnitude scales with tier × max_hp >> 4 (`poison_hp()`, `regen_hp()` in
 *   `stats.c`)
 * - Duration 0 in the DSL means "permanent until cleansed" — matches Original
 *   `EFFECT_DURATION_PERPETUAL`
 */

// =============================================================================
// Status effect references returned from defineStatusEffects()
// =============================================================================

/**
 * Typed container for all Labyrinth of the Dragon status effect definitions.
 *
 * Returned by [GameBuilder.defineStatusEffects] for zero-magic-string access in downstream plans
 * (items, abilities, battle scene, etc.).
 *
 * All fields correspond directly to entries in the Original's `StatusEffect` enum
 * (`LabyrinthOfTheDragon/src/stats.h` lines 43–60).
 */
data class LabyrinthStatusEffects(
    // -- Debuffs --
    /** Reduces hit accuracy — DEBUFF_BLIND (stats.h line 44). */
    val blind: StatusEffectRef,
    /** Causes flee attempts or frozen-in-fear on turn — DEBUFF_SCARED (stats.h line 45). */
    val scared: StatusEffectRef,
    /** Chance to skip turn each round — DEBUFF_PARALYZED (stats.h line 46). */
    val paralyzed: StatusEffectRef,
    /** Damage over time each turn — DEBUFF_POISONED (stats.h line 47). */
    val poisoned: StatusEffectRef,
    /** May attack self or allies — DEBUFF_CONFUSED (stats.h line 48). */
    val confused: StatusEffectRef,
    /** Reduces agility — DEBUFF_AGL_DOWN (stats.h line 49). */
    val aglDown: StatusEffectRef,
    /** Reduces physical attack — DEBUFF_ATK_DOWN (stats.h line 50). */
    val atkDown: StatusEffectRef,
    /** Reduces defense — DEBUFF_DEF_DOWN (stats.h line 51). */
    val defDown: StatusEffectRef,
    // -- Buffs --
    /** Double-attack and extra heal per regen tick — BUFF_HASTE (stats.h line 55). */
    val haste: StatusEffectRef,
    /** Heal-over-time each turn — BUFF_REGEN (stats.h line 56). */
    val regen: StatusEffectRef,
    /** Increases agility — BUFF_AGL_UP (stats.h line 57). */
    val aglUp: StatusEffectRef,
    /** Increases physical attack — BUFF_ATK_UP (stats.h line 58). */
    val atkUp: StatusEffectRef,
    /** Increases defense — BUFF_DEF_UP (stats.h line 59). */
    val defUp: StatusEffectRef,
)

// =============================================================================
// Status effect definitions DSL extension
// =============================================================================

/**
 * Registers all 13 active status effects from the Original C game.
 *
 * Skips the 3 `BUFF_UNUSED_*` entries which have no game behavior.
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/stats.h` lines 43–61 for the `StatusEffect` enum.
 * `LabyrinthOfTheDragon/src/stats.c` lines 171–186 for `poison_hp()`/`regen_hp()`.
 * `LabyrinthOfTheDragon/src/encounter.h` for `apply_*()` inline helper functions.
 */
fun GameBuilder.defineStatusEffects(): LabyrinthStatusEffects {

    // =========================================================================
    // DEBUFFS
    // =========================================================================

    // -------------------------------------------------------------------------
    // Blind — DEBUFF_BLIND
    // Applied by: Sorcerer's Darkness ability (apply_blind in encounter.h)
    // Effect: Reduces hit accuracy — targets have reduced ATK for hit rolls
    // Original: DEBUFF_BLIND = 0 in stats.h, FLAG_DEBUFF_BLIND = FLAG(0)
    // Duration: Applied with explicit turns (typically 3-5 based on player level)
    // -------------------------------------------------------------------------

    /**
     * Blind status debuff.
     *
     * Applied by Sorcerer's Darkness ability. Reduces the affected entity's hit accuracy by
     * lowering its effective ATK for attack rolls. Does not deal damage.
     *
     * Original: `DEBUFF_BLIND` in `stats.h` line 44. Applied via `apply_blind()` in `encounter.h`
     * line 358.
     */
    val blindRef =
        statusEffect("blind") {
            name("Blind")
            debuff()
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // Scared — DEBUFF_SCARED
    // Applied by: Fighter's Menace ability (apply_scared in encounter.h)
    // Effect: On each turn, entity may flee or freeze (based on tier roll)
    // Original: DEBUFF_SCARED = 1 in stats.h, FLAG_DEBUFF_SCARED = FLAG(1)
    // Duration: Applied with explicit turns (2-4 based on player level)
    // -------------------------------------------------------------------------

    /**
     * Scared status debuff.
     *
     * Applied by Fighter's Menace ability. On each affected entity's turn there is a tier-based
     * chance they attempt to flee or freeze instead of acting. Flee/freeze rolls are calculated in
     * `stats.c` `fear_flee_roll()` and `fear_shiver_roll()`.
     *
     * Original: `DEBUFF_SCARED` in `stats.h` line 45. Applied via `apply_scared()` in `encounter.h`
     * line 377.
     */
    val scaredRef =
        statusEffect("scared") {
            name("Scared")
            category(EffectCategory.CROWD_CONTROL)
            duration(2)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // Paralyzed — DEBUFF_PARALYZED
    // Applied by: Monsters (some have paralysis attacks in battle)
    // Effect: Each turn rolls against paralysis chance (tier-based) to skip action
    // Original: DEBUFF_PARALYZED = 2 in stats.h, FLAG_DEBUFF_PARALYZED = FLAG(2)
    // -------------------------------------------------------------------------

    /**
     * Paralyzed status debuff.
     *
     * Applied by certain monster abilities. Each turn the paralyzed entity rolls against a
     * tier-based chance (`paralyzed_roll()` in `stats.c` line 166) to determine whether they can
     * act or are frozen for the turn.
     *
     * Original: `DEBUFF_PARALYZED` in `stats.h` line 46. Applied via `apply_paralyzed()` in
     * `encounter.h` line 396.
     */
    val paralyzedRef =
        statusEffect("paralyzed") {
            name("Paralyzed")
            category(EffectCategory.CROWD_CONTROL)
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // Poisoned — DEBUFF_POISONED
    // Applied by: Some monsters (poison spray, venom bite, etc.)
    // Effect: Deals damage each turn proportional to tier × max_hp >> 4
    // Formula: poison_hp(tier, max_hp) = (max_hp * (tier + 2)) >> 4
    // Original: DEBUFF_POISONED = 3, FLAG_DEBUFF_POISONED = FLAG(3)
    // Notes: Also applied by Test class debug ability (player.c line 824)
    // -------------------------------------------------------------------------

    /**
     * Poisoned damage-over-time debuff.
     *
     * Deals HP damage each turn. The actual damage scales with the debuff's power tier and the
     * target's maximum HP: `poison_hp(tier, max_hp) = (max_hp * (tier+2)) >> 4`. A C-tier poison
     * deals ~12.5% max_hp/turn, S-tier ~37.5%.
     *
     * The `damagePerTurn(10)` here is a representative value for the framework; actual runtime
     * damage is computed dynamically from max HP.
     *
     * Original: `DEBUFF_POISONED` in `stats.h` line 47. Formula: `stats.c` line 171. Applied via
     * `apply_poison()` in `encounter.h` line 415.
     */
    val poisonedRef =
        statusEffect("poisoned") {
            name("Poisoned")
            category(EffectCategory.DOT)
            duration(5)
            damagePerTurn(10) // Representative; actual damage = max_hp * (tier+2) >> 4
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // Confused — DEBUFF_CONFUSED
    // Applied by: Sorcerer's Wild Magic (apply_confused in encounter.h)
    // Effect: Tier-based chance to attack self/ally instead of enemy each turn
    // Original: DEBUFF_CONFUSED = 4, FLAG_DEBUFF_CONFUSED = FLAG(4)
    // -------------------------------------------------------------------------

    /**
     * Confused status debuff.
     *
     * Applied by Sorcerer's Wild Magic. Each turn there is a tier-based chance the confused entity
     * attacks itself or an ally. Chance table defined in `stats.c` `confused_attack()` line 177:
     * C=25%, B=37.5%, A=50%, S=75%.
     *
     * Original: `DEBUFF_CONFUSED` in `stats.h` line 48. Applied via `apply_confused()` in
     * `encounter.h` line 434.
     */
    val confusedRef =
        statusEffect("confused") {
            name("Confused")
            category(EffectCategory.CROWD_CONTROL)
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // AGL Down — DEBUFF_AGL_DOWN
    // Applied by: Sorcerer's Wild Magic, some monsters
    // Effect: Reduces agility by tier-based amount (agl_down() in stats.c)
    // Formula: agl_mod = {2, 4, 8, 12}[tier]; result = max(0, base_agl - agl_mod)
    // Original: DEBUFF_AGL_DOWN = 5, FLAG_DEBUFF_AGL_DOWN = FLAG(5)
    // -------------------------------------------------------------------------

    /**
     * AGL Down stat debuff.
     *
     * Reduces the affected entity's agility by a tier-based amount. Lower AGL reduces turn order
     * priority and evasion. Formula: `agl_down()` in `stats.c` line 115 — C-tier: -2, B: -4, A: -8,
     * S: -12.
     *
     * Original: `DEBUFF_AGL_DOWN` in `stats.h` line 49. Applied via `apply_agl_down()` in
     * `encounter.h` line 453.
     */
    val aglDownRef =
        statusEffect("agl_down") {
            name("AGL Down")
            debuff()
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // ATK Down — DEBUFF_ATK_DOWN
    // Applied by: Sorcerer's Wild Magic, some monsters
    // Effect: Reduces physical attack by tier-based percentage
    // Formula: atk_down(base, tier) = base - (base * {1,2,4,6}[tier] >> 4)
    // Original: DEBUFF_ATK_DOWN = 6, FLAG_DEBUFF_ATK_DOWN = FLAG(6)
    // -------------------------------------------------------------------------

    /**
     * ATK Down stat debuff.
     *
     * Reduces the affected entity's physical attack. Percentage reduction scales with tier:
     * `atk_down()` in `stats.c` line 120 — C: ~6%, B: ~12.5%, A: ~25%, S: ~37.5%.
     *
     * Original: `DEBUFF_ATK_DOWN` in `stats.h` line 50. Applied via `apply_atk_down()` in
     * `encounter.h` line 472.
     */
    val atkDownRef =
        statusEffect("atk_down") {
            name("ATK Down")
            debuff()
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // -------------------------------------------------------------------------
    // DEF Down — DEBUFF_DEF_DOWN
    // Applied by: Sorcerer's Wild Magic, some monsters
    // Effect: Reduces defense by tier-based percentage (same formula as atk_down)
    // Original: DEBUFF_DEF_DOWN = 7, FLAG_DEBUFF_DEF_DOWN = FLAG(7)
    // -------------------------------------------------------------------------

    /**
     * DEF Down stat debuff.
     *
     * Reduces the affected entity's physical defense. Same percentage reduction formula as ATK
     * Down: `def_down()` in `stats.c` line 128.
     *
     * Original: `DEBUFF_DEF_DOWN` in `stats.h` line 51. Applied via `apply_def_down()` in
     * `encounter.h` line 491.
     */
    val defDownRef =
        statusEffect("def_down") {
            name("DEF Down")
            debuff()
            duration(3)
            stackMode(StackMode.REFRESH_DURATION)
        }

    // =========================================================================
    // BUFFS
    // =========================================================================
    // Note: Buff slots 8, 9, 10 are BUFF_UNUSED_* in the Original and are skipped.

    // -------------------------------------------------------------------------
    // Haste — BUFF_HASTE
    // Applied by: Sorcerer's Haste ability, Haste Potion (item)
    // Effect: Grants double attacks and 50% bonus to healing (has_special HASTE check)
    // Duration: Applied with 0 (perpetual for the battle)
    // Original: BUFF_HASTE = 11, FLAG_BUFF_HASTE = FLAG(3) in BuffFlag enum
    // -------------------------------------------------------------------------

    /**
     * Haste status buff.
     *
     * Applied by Sorcerer's Haste ability and the Haste Potion item. When hasted, the player's
     * damage attacks roll twice and add both results. Healing abilities also heal for 150% of
     * normal. Duration 0 = perpetual until end of battle.
     *
     * Original: `BUFF_HASTE = 11` in `stats.h` line 55. `FLAG_BUFF_HASTE = FLAG(3)`. Applied via
     * `apply_haste()` in `encounter.h` line 509. Checked in `player.c` via
     * `has_special(SPECIAL_HASTE)` for double-attack.
     */
    val hasteRef =
        statusEffect("haste") {
            name("Haste")
            buff()
            duration(0) // perpetual until end of battle
            stackMode(StackMode.NONE) // cannot be stacked; re-application rejected
        }

    // -------------------------------------------------------------------------
    // Regen — BUFF_REGEN
    // Applied by: Druid's Regen ability, Regen Potion (item)
    // Effect: Heals HP each turn (regen_hp formula same as poison_hp but heal)
    // Formula: regen_hp(tier, max_hp) = (max_hp * (tier + 2)) >> 4
    // Duration: Ability = A/S tier perpetual; Item = perpetual (EFFECT_DURATION_PERPETUAL)
    // Original: BUFF_REGEN = 12, FLAG_BUFF_REGEN = FLAG(4) in BuffFlag enum
    // -------------------------------------------------------------------------

    /**
     * Regen heal-over-time buff.
     *
     * Applied by Druid's Regen ability and the Regen Potion. Restores HP each turn. Formula mirrors
     * poison: `regen_hp(tier, max_hp) = (max_hp * (tier+2)) >> 4`.
     *
     * The `healPerTurn(10)` is a representative value; actual healing is max_hp based.
     *
     * Original: `BUFF_REGEN = 12` in `stats.h` line 56. `FLAG_BUFF_REGEN = FLAG(4)`. Applied via
     * `apply_regen()` in `encounter.h` line 526. Formula: `stats.c` line 182.
     */
    val regenRef =
        statusEffect("regen") {
            name("Regen")
            category(EffectCategory.BUFF)
            duration(0) // perpetual until end of battle
            healPerTurn(10) // Representative; actual = max_hp * (tier+2) >> 4
            stackMode(StackMode.NONE)
        }

    // -------------------------------------------------------------------------
    // AGL Up — BUFF_AGL_UP
    // Applied by: Monk's Evasion ability (apply_agl_up in encounter.h)
    // Effect: Increases agility by tier-based amount (agl_up() in stats.c)
    // Formula: agl_up(base_agl, tier) = base_agl + {2, 4, 8, 12}[tier]
    // Original: BUFF_AGL_UP = 13, FLAG_BUFF_AGL_UP = FLAG(5) in BuffFlag enum
    // -------------------------------------------------------------------------

    /**
     * AGL Up stat buff.
     *
     * Applied by Monk's Evasion ability. Increases agility by a tier-based flat amount. Higher AGL
     * improves turn order (acts before enemies) and evasion. Formula: `agl_up()` in `stats.c`
     * line 136.
     *
     * Original: `BUFF_AGL_UP = 13` in `stats.h` line 57. `FLAG_BUFF_AGL_UP = FLAG(5)`. Applied via
     * `apply_agl_up()` in `encounter.h` line 543.
     */
    val aglUpRef =
        statusEffect("agl_up") {
            name("AGL Up")
            buff()
            duration(0) // perpetual for the Evasion ability (applied with 2–3 turn duration)
            stackMode(StackMode.NONE)
        }

    // -------------------------------------------------------------------------
    // ATK Up — BUFF_ATK_UP
    // Applied by: ATK Up Potion (item) via set_item_buff in item.c
    // Effect: Increases physical attack by tier-based percentage
    // Formula: atk_up(base, tier) = base + (base * {1,2,4,6}[tier] >> 4)
    // Duration: Perpetual (EFFECT_DURATION_PERPETUAL) for items
    // Original: BUFF_ATK_UP = 14, FLAG_BUFF_ATK_UP = FLAG(6) in BuffFlag enum
    // -------------------------------------------------------------------------

    /**
     * ATK Up stat buff.
     *
     * Applied by the ATK Up Potion item. Increases physical attack by a tier-based percentage. Item
     * buffs are always applied at A-tier perpetual duration. Formula: `atk_up()` in `stats.c`
     * line 140.
     *
     * Original: `BUFF_ATK_UP = 14` in `stats.h` line 58. `FLAG_BUFF_ATK_UP = FLAG(6)`. Applied via
     * `apply_atk_up()` in `encounter.h` line 560.
     */
    val atkUpRef =
        statusEffect("atk_up") {
            name("ATK Up")
            buff()
            duration(0) // perpetual (item effect lasts for the full battle)
            stackMode(StackMode.NONE)
        }

    // -------------------------------------------------------------------------
    // DEF Up — BUFF_DEF_UP
    // Applied by: Druid's Bark Skin ability (apply_def_up), Monk's Diamond Body,
    //             DEF Up Potion (item)
    // Effect: Increases defense by tier-based percentage (same formula as atk_up)
    // Duration: Perpetual for abilities and items
    // Original: BUFF_DEF_UP = 15, FLAG_BUFF_DEF_UP = FLAG(7) in BuffFlag enum
    // -------------------------------------------------------------------------

    /**
     * DEF Up stat buff.
     *
     * Applied by Druid's Bark Skin, Monk's Diamond Body, and the DEF Up Potion. Increases physical
     * defense by a tier-based percentage. Formula: `def_up()` in `stats.c` line 148.
     *
     * Original: `BUFF_DEF_UP = 15` in `stats.h` line 59. `FLAG_BUFF_DEF_UP = FLAG(7)`. Applied via
     * `apply_def_up()` in `encounter.h` line 577.
     */
    val defUpRef =
        statusEffect("def_up") {
            name("DEF Up")
            buff()
            duration(0) // perpetual (Bark Skin, Diamond Body, DEF Up Potion all perpetual)
            stackMode(StackMode.NONE)
        }

    return LabyrinthStatusEffects(
        blind = blindRef,
        scared = scaredRef,
        paralyzed = paralyzedRef,
        poisoned = poisonedRef,
        confused = confusedRef,
        aglDown = aglDownRef,
        atkDown = atkDownRef,
        defDown = defDownRef,
        haste = hasteRef,
        regen = regenRef,
        aglUp = aglUpRef,
        atkUp = atkUpRef,
        defUp = defUpRef,
    )
}
