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
 * Fighter class abilities.
 *
 * Theme: Physical combat and self-buffs. Fighters use powerful melee attacks and combat techniques.
 */
class FighterAbilities(builder: GameBuilder, private val effects: StatusEffects) {

    val secondWind: Ability by
        builder.ability {
            name("Second Wind")
            description("Catch your breath and recover HP")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(7.sp)
            power(50)
            execute { heal() }
        }

    val actionSurge: Ability by
        builder.ability {
            name("Action Surge")
            description("A powerful strike with extra force")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(14.sp)
            power(180)
            execute { dealDamage() }
        }

    val cleave: Ability by
        builder.ability {
            name("Cleave")
            description("Sweep attack hitting all enemies")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(19.sp)
            power(90)
            execute { dealDamage() }
        }

    val tripAttack: Ability by
        builder.ability {
            name("Trip Attack")
            description("Attack that stuns the enemy")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(23.sp)
            power(100)
            execute {
                dealDamage()
                applyEffect(effects.stun.definition, chance = 50)
            }
        }

    val menace: Ability by
        builder.ability {
            name("Menace")
            description("Intimidate all enemies, reducing their attack")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(28.sp)
            execute { applyEffect(effects.scared.definition) }
        }

    val indomitable: Ability by
        builder.ability {
            name("Indomitable")
            description("Surge with unstoppable power, boosting attack")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(35.sp)
            execute { applyEffectToSelf(effects.atkUp.definition) }
        }

    /** All Fighter abilities in order. */
    val all: List<Ability>
        get() = listOf(secondWind, actionSurge, cleave, tripAttack, menace, indomitable)
}
