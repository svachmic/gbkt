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
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.TargetingMode
import io.github.gbkt.core.rpg.ability
import io.github.gbkt.core.rpg.sp
import io.github.gbkt.examples.labyrinth.rpg.StatusEffects

/**
 * Sorcerer class abilities.
 *
 * Theme: Arcane magic and elemental spells. Sorcerers wield powerful offensive magic.
 */
class SorcererAbilities(builder: GameBuilder, private val effects: StatusEffects) {

    val darkness: Ability by
        builder.ability {
            name("Darkness")
            description("Shroud enemies in darkness, reducing their hit chance")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(4.sp)
            aspect(Aspect.DARK)
            execute { applyEffect(effects.blind.definition) }
        }

    val fireball: Ability by
        builder.ability {
            name("Fireball")
            description("Explosive ball of fire that burns enemies")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(12.sp)
            aspect(Aspect.FIRE)
            power(100)
            execute {
                dealDamage()
                applyEffect(effects.burn.definition, chance = 30)
            }
        }

    val haste: Ability by
        builder.ability {
            name("Haste")
            description("Magically increase your speed")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(15.sp)
            execute { applyEffectToSelf(effects.haste.definition) }
        }

    val sleetStorm: Ability by
        builder.ability {
            name("Sleet Storm")
            description("Freezing storm that damages and slows enemies")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(20.sp)
            aspect(Aspect.ICE)
            power(90)
            execute {
                dealDamage()
                applyEffect(effects.slow.definition)
            }
        }

    val disintegrate: Ability by
        builder.ability {
            name("Disintegrate")
            description("Powerful ray that destroys matter")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(28.sp)
            aspect(Aspect.MAGICAL)
            power(250)
            execute { dealDamage() }
        }

    val wildMagic: Ability by
        builder.ability {
            name("Wild Magic")
            description("Unleash chaotic magical energy")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(33.sp)
            aspect(Aspect.MAGICAL)
            power(150)
            execute { dealDamage() }
        }

    /** All Sorcerer abilities in order. */
    val all: List<Ability>
        get() = listOf(darkness, fireball, haste, sleetStorm, disintegrate, wildMagic)
}
