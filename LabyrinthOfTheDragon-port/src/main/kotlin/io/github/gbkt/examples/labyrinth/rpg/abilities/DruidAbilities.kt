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
 * Druid class abilities.
 *
 * Theme: Nature magic and healing. Druids focus on supportive spells with some elemental damage.
 */
class DruidAbilities(builder: GameBuilder, private val effects: StatusEffects) {

    val cureWounds: Ability by
        builder.ability {
            name("Cure Wounds")
            description("Restore a moderate amount of HP")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(4.sp)
            power(100)
            execute { heal() }
        }

    val barkSkin: Ability by
        builder.ability {
            name("Bark Skin")
            description("Harden skin to reduce incoming damage")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(8.sp)
            execute { applyEffectToSelf(effects.barkskin.definition) }
        }

    val lightning: Ability by
        builder.ability {
            name("Lightning")
            description("Strike an enemy with lightning")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(15.sp)
            aspect(Aspect.LIGHTNING)
            power(130)
            execute { dealDamage() }
        }

    val majorHeal: Ability by
        builder.ability {
            name("Major Heal")
            description("Restore a large amount of HP")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(19.sp)
            power(200)
            execute { heal() }
        }

    val insectPlague: Ability by
        builder.ability {
            name("Insect Plague")
            description("Summon a swarm to damage all enemies")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(28.sp)
            aspect(Aspect.EARTH)
            power(80)
            execute { dealDamage() }
        }

    val regenerate: Ability by
        builder.ability {
            name("Regenerate")
            description("Apply healing over time")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(33.sp)
            execute { applyEffectToSelf(effects.regen.definition) }
        }

    /** All Druid abilities in order. */
    val all: List<Ability>
        get() = listOf(cureWounds, barkSkin, lightning, majorHeal, insectPlague, regenerate)
}
