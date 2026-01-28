/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg.abilities

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.core.rpg.AbilityCategory
import io.github.gbkt.core.rpg.TargetingMode
import io.github.gbkt.core.rpg.ability
import io.github.gbkt.core.rpg.sp
import io.github.gbkt.examples.labyrinth.rpg.StatusEffects

/**
 * Monk class abilities.
 *
 * Theme: Martial arts and ki techniques. Monks use focused strikes and internal energy.
 */
class MonkAbilities(builder: GameBuilder, private val effects: StatusEffects) {

    val evasion: Ability by
        builder.ability {
            name("Evasion")
            description("Enter an evasive stance, increasing dodge chance")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(7.sp)
            execute { applyEffectToSelf(effects.evasion.definition) }
        }

    val openPalm: Ability by
        builder.ability {
            name("Open Palm")
            description("A focused palm strike")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(10.sp)
            power(120)
            execute { dealDamage() }
        }

    val stillMind: Ability by
        builder.ability {
            name("Still Mind")
            description("Clear all debuffs through meditation")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(13.sp)
            execute { cureDebuffs() }
        }

    val flurry: Ability by
        builder.ability {
            name("Flurry")
            description("A rapid series of strikes")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(19.sp)
            power(180)
            execute { dealDamage() }
        }

    val diamondBody: Ability by
        builder.ability {
            name("Diamond Body")
            description("Harden your body like diamond, greatly boosting defense")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(15.sp)
            execute { applyEffectToSelf(effects.diamondBody.definition) }
        }

    val quiveringPalm: Ability by
        builder.ability {
            name("Quivering Palm")
            description("A deadly touch that can kill instantly")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(30.sp)
            power(200)
            execute {
                instantKill(25)
                dealDamage()
            }
        }

    /** All Monk abilities in order. */
    val all: List<Ability>
        get() = listOf(evasion, openPalm, stillMind, flurry, diamondBody, quiveringPalm)
}
