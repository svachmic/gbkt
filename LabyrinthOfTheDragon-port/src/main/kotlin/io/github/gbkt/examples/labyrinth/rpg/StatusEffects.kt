/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.EffectCategory
import io.github.gbkt.core.rpg.StackMode
import io.github.gbkt.core.rpg.StatusEffect
import io.github.gbkt.core.rpg.statusEffect

// =============================================================================
// LABYRINTH OF THE DRAGON - STATUS EFFECTS
// =============================================================================
// Status effects for buffs, debuffs, and combat conditions.

/** Status effect definitions for the game. */
class StatusEffects(builder: GameBuilder) {

    // -------------------------------------------------------------------------
    // HEALING OVER TIME (HoT) EFFECTS
    // -------------------------------------------------------------------------

    /**
     * Regen - Heals a small amount of HP each turn. Duration: 5 turns, heals 8 HP per turn (40
     * total).
     */
    val regen: StatusEffect by
        builder.statusEffect {
            name("Regen")
            buff()
            duration(5)
            healPerTurn(8)
            stackMode(StackMode.REFRESH_DURATION)
            icon(0)
        }

    // -------------------------------------------------------------------------
    // DAMAGE OVER TIME (DoT) EFFECTS
    // -------------------------------------------------------------------------

    /** Poison - Deals damage each turn. Duration: 5 turns, deals 5 damage per turn. */
    val poison: StatusEffect by
        builder.statusEffect {
            name("Poison")
            debuff()
            duration(5)
            damagePerTurn(5)
            stackMode(StackMode.REFRESH_DURATION)
            icon(1)
        }

    /** Burn - Fire damage over time. Duration: 3 turns, deals 8 damage per turn. */
    val burn: StatusEffect by
        builder.statusEffect {
            name("Burn")
            debuff()
            duration(3)
            damagePerTurn(8)
            stackMode(StackMode.REFRESH_DURATION)
            icon(2)
        }

    // -------------------------------------------------------------------------
    // STAT BUFF EFFECTS
    // -------------------------------------------------------------------------

    /** ATK Up - Temporarily increases attack power by 25%. Duration: 5 turns. */
    val atkUp: StatusEffect by
        builder.statusEffect {
            name("ATK Up")
            category(EffectCategory.STAT_MOD)
            duration(5)
            atkUp(125) // +25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(3)
        }

    /** DEF Up - Temporarily increases defense by 25%. Duration: 5 turns. */
    val defUp: StatusEffect by
        builder.statusEffect {
            name("DEF Up")
            category(EffectCategory.STAT_MOD)
            duration(5)
            defUp(125) // +25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(4)
        }

    /** Haste - Temporarily increases agility by 25%. Duration: 5 turns. */
    val haste: StatusEffect by
        builder.statusEffect {
            name("Haste")
            category(EffectCategory.STAT_MOD)
            duration(5)
            aglUp(125) // +25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(5)
        }

    /** Evasion - Increases chance to dodge attacks. Duration: 3 turns, +30% evasion. */
    val evasion: StatusEffect by
        builder.statusEffect {
            name("Evasion")
            buff()
            duration(3)
            increaseEvasion(30)
            stackMode(StackMode.REFRESH_DURATION)
            icon(11)
        }

    /** Barkskin - Reduces incoming damage by 50%. Duration: 3 turns. */
    val barkskin: StatusEffect by
        builder.statusEffect {
            name("Barkskin")
            buff()
            duration(3)
            halveIncomingDamage()
            stackMode(StackMode.REFRESH_DURATION)
            icon(12)
        }

    /** Diamond Body - Massive defense increase. Duration: 2 turns, +50% DEF. */
    val diamondBody: StatusEffect by
        builder.statusEffect {
            name("Diamond Body")
            buff()
            duration(2)
            defUp(150) // +50%
            stackMode(StackMode.REFRESH_DURATION)
            icon(13)
        }

    // -------------------------------------------------------------------------
    // DEBUFF EFFECTS
    // -------------------------------------------------------------------------

    /** ATK Down - Reduces attack power by 25%. Duration: 3 turns. */
    val atkDown: StatusEffect by
        builder.statusEffect {
            name("ATK Down")
            category(EffectCategory.STAT_MOD)
            duration(3)
            atkDown(75) // -25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(6)
        }

    /** DEF Down - Reduces defense by 25%. Duration: 3 turns. */
    val defDown: StatusEffect by
        builder.statusEffect {
            name("DEF Down")
            category(EffectCategory.STAT_MOD)
            duration(3)
            defDown(75) // -25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(7)
        }

    /** Slow - Reduces agility by 25%. Duration: 3 turns. */
    val slow: StatusEffect by
        builder.statusEffect {
            name("Slow")
            category(EffectCategory.STAT_MOD)
            duration(3)
            aglDown(75) // -25%
            stackMode(StackMode.REFRESH_DURATION)
            icon(14)
        }

    /** Blind - Reduces hit chance. Duration: 2 turns, -30% hit. */
    val blind: StatusEffect by
        builder.statusEffect {
            name("Blind")
            debuff()
            duration(2)
            reduceHitChance(30)
            stackMode(StackMode.REFRESH_DURATION)
            icon(15)
        }

    /** Scared - Reduces attack by 30%. Duration: 3 turns. */
    val scared: StatusEffect by
        builder.statusEffect {
            name("Scared")
            debuff()
            duration(3)
            atkDown(70) // -30%
            stackMode(StackMode.REFRESH_DURATION)
            icon(16)
        }

    // -------------------------------------------------------------------------
    // CONDITION EFFECTS
    // -------------------------------------------------------------------------

    /** Stun - Prevents action for duration. Duration: 1 turn. */
    val stun: StatusEffect by
        builder.statusEffect {
            name("Stun")
            category(EffectCategory.CONDITION)
            duration(1)
            preventsAction()
            stackMode(StackMode.NONE) // Can't stack stun
            icon(8)
        }

    /** Sleep - Prevents action until hit. Duration: 3 turns (or until damaged). */
    val sleep: StatusEffect by
        builder.statusEffect {
            name("Sleep")
            category(EffectCategory.CONDITION)
            duration(3)
            preventsAction()
            stackMode(StackMode.NONE)
            icon(9)
        }

    /** Paralysis - May prevent action (50% chance). Duration: 3 turns. */
    val paralysis: StatusEffect by
        builder.statusEffect {
            name("Paralysis")
            category(EffectCategory.CONDITION)
            duration(3)
            // Note: 50% action prevention would need custom handling
            stackMode(StackMode.REFRESH_DURATION)
            icon(10)
        }

    /** Prone - Knocked down. Duration: 1 turn. Prevents action. */
    val prone: StatusEffect by
        builder.statusEffect {
            name("Prone")
            category(EffectCategory.CONDITION)
            duration(1)
            preventsAction()
            stackMode(StackMode.REFRESH_DURATION)
            icon(18)
        }

    /**
     * Confusion - Target acts randomly. Duration: 3 turns.
     *
     * Used by Mind Flayer's Mind Blast attack as part of the 2-phase kill chain. Confused targets
     * are vulnerable to the Extract Brain instakill.
     */
    val confusion: StatusEffect by
        builder.statusEffect {
            name("Confusion")
            category(EffectCategory.CONDITION)
            duration(3)
            // Confusion may cause random targeting or missed turns
            stackMode(StackMode.REFRESH_DURATION)
            icon(17)
        }
}

/** Initialize all status effects for the game. */
fun initStatusEffects(builder: GameBuilder): StatusEffects {
    return StatusEffects(builder)
}
