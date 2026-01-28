/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.core.rpg.AbilityCategory
import io.github.gbkt.core.rpg.AbilityCost
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.TargetingMode
import io.github.gbkt.core.rpg.ability

/**
 * Monster-specific abilities for Labyrinth of the Dragon.
 *
 * These abilities are used by monsters in their AI routines:
 * - Kobold: fire_loogie (fire projectile)
 * - Zombie: poison_bite (one-time poison attack)
 * - Death Knight: hellfire_orb (fire spell)
 * - Mind Flayer: mind_blast, extract_brain (confusion chain)
 * - Beholder: disintegration_ray, fear_ray
 * - Dragon: flame_breath, rage
 *
 * All monster abilities have no cost (AbilityCost.Free).
 */
class MonsterAbilities(builder: GameBuilder, private val effects: StatusEffects) {

    // =========================================================================
    // KOBOLD ABILITIES
    // =========================================================================

    /**
     * Fire Loogie - Kobold's ranged fire attack.
     *
     * 25% chance to use instead of basic attack. Uses MATK for damage calculation with FIRE aspect.
     */
    val fireLoogie: Ability by
        builder.ability {
            name("Fire Loogie")
            description("Spit a burning loogie at an enemy")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.FIRE)
            power(100) // Uses MATK
            execute { dealDamage() }
        }

    // =========================================================================
    // ZOMBIE ABILITIES
    // =========================================================================

    /**
     * Poison Bite - Zombie's one-time special attack.
     *
     * Can only be used once per battle (via hasSpecialCharge/useSpecialCharge). Applies poison
     * status effect to the target.
     */
    val poisonBite: Ability by
        builder.ability {
            name("Poison Bite")
            description("A venomous bite that poisons the target")
            category(AbilityCategory.PHYSICAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(80) // Weaker than basic attack, but applies poison
            execute {
                dealDamage()
                applyEffect(effects.poison.definition, chance = 100)
            }
        }

    // =========================================================================
    // DEATH KNIGHT ABILITIES
    // =========================================================================

    /**
     * Hellfire Orb - Death Knight's signature fire spell.
     *
     * Used when HP is below 30%. High magical damage with FIRE aspect.
     */
    val hellfireOrb: Ability by
        builder.ability {
            name("Hellfire Orb")
            description("Hurl a sphere of hellfire at an enemy")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.FIRE)
            power(150) // 1.5x MATK
            execute { dealDamage() }
        }

    // =========================================================================
    // MIND FLAYER ABILITIES
    // =========================================================================

    /**
     * Mind Blast - Mind Flayer's psionic attack.
     *
     * Deals dark damage and applies confusion. Part of the 2-phase kill chain: Phase 1: Mind Blast
     * (applies confusion) Phase 2: Extract Brain (instakill if confused)
     */
    val mindBlast: Ability by
        builder.ability {
            name("Mind Blast")
            description("A psionic attack that confuses the target")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(120) // 1.2x MATK
            execute {
                dealDamage()
                applyEffect(effects.confusion.definition, chance = 80)
            }
        }

    /**
     * Extract Brain - Mind Flayer's finishing move.
     *
     * Near-instakill attack that only works effectively on confused targets. 95% chance to
     * instantly kill. Used in Phase 2 of the kill chain.
     */
    val extractBrain: Ability by
        builder.ability {
            name("Extract Brain")
            description("Attempt to extract the target's brain")
            category(AbilityCategory.SPECIAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(100)
            execute { instantKill(chance = 95) }
        }

    // =========================================================================
    // BEHOLDER ABILITIES
    // =========================================================================

    /**
     * Disintegration Ray - Beholder's most powerful eye ray.
     *
     * Extremely high magical damage. 40% chance to use on any turn.
     */
    val disintegrationRay: Ability by
        builder.ability {
            name("Disintegration Ray")
            description("A beam that disintegrates the target")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.MAGICAL)
            power(200) // 2x MATK
            execute { dealDamage() }
        }

    /**
     * Fear Ray - Beholder's debuff eye ray.
     *
     * Applies the scared debuff (reduces attack). 30% chance to use.
     */
    val fearRay: Ability by
        builder.ability {
            name("Fear Ray")
            description("A beam that induces terror")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(80) // Lower damage
            execute {
                dealDamage()
                applyEffect(effects.scared.definition, chance = 100)
            }
        }

    /** Paralyze Ray - Applies paralysis to the target. */
    val paralyzeRay: Ability by
        builder.ability {
            name("Paralyze Ray")
            description("A beam that paralyzes the target")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.MAGICAL)
            power(80)
            execute {
                dealDamage()
                applyEffect(effects.paralysis.definition, chance = 100)
            }
        }

    /** Slow Ray - Reduces target's agility. */
    val slowRay: Ability by
        builder.ability {
            name("Slow Ray")
            description("A beam that slows the target")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.MAGICAL)
            power(80)
            execute {
                dealDamage()
                applyEffect(effects.slow.definition, chance = 100)
            }
        }

    /** Necro Ray - Applies poison with dark damage. */
    val necroRay: Ability by
        builder.ability {
            name("Necro Ray")
            description("A beam of necrotic energy")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(80)
            execute {
                dealDamage()
                applyEffect(effects.poison.definition, chance = 100)
            }
        }

    /** Ice Ray - Pure ice/water damage. */
    val iceRay: Ability by
        builder.ability {
            name("Ice Ray")
            description("A freezing beam of cold")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.WATER)
            power(120) // Higher damage, no debuff
            execute { dealDamage() }
        }

    /** Trip Ray - Knocks target prone. */
    val tripRay: Ability by
        builder.ability {
            name("Trip Ray")
            description("A beam that knocks the target down")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.MAGICAL)
            power(80)
            execute {
                dealDamage()
                applyEffect(effects.prone.definition, chance = 100)
            }
        }

    /** Fire Ray - Pure fire damage. */
    val fireRay: Ability by
        builder.ability {
            name("Fire Ray")
            description("A searing beam of fire")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.FIRE)
            power(120) // Higher damage, no debuff
            execute { dealDamage() }
        }

    /** Death Ray - 1% instant kill chance. The deadliest ray. */
    val deathRay: Ability by
        builder.ability {
            name("Death Ray")
            description("A black beam of pure death")
            category(AbilityCategory.SPECIAL)
            targeting(TargetingMode.SINGLE_ENEMY)
            cost(AbilityCost.Free)
            aspect(Aspect.DARK)
            power(80)
            execute {
                dealDamage()
                instantKill(chance = 1)
            }
        }

    // =========================================================================
    // DRAGON ABILITIES
    // =========================================================================

    /**
     * Flame Breath - Dragon's signature attack.
     *
     * High fire damage to all enemies. Used more frequently when HP is low.
     */
    val flameBreath: Ability by
        builder.ability {
            name("Flame Breath")
            description("Breathe a cone of fire")
            category(AbilityCategory.MAGIC)
            targeting(TargetingMode.ALL_ENEMIES)
            cost(AbilityCost.Free)
            aspect(Aspect.FIRE)
            power(150) // 1.5x MATK to all
            execute { dealDamage() }
        }

    /**
     * Rage - Dragon's berserk mode.
     *
     * Activated when HP drops below 25%. Increases attack power significantly.
     */
    val rage: Ability by
        builder.ability {
            name("Rage")
            description("Enter a berserk rage")
            category(AbilityCategory.SUPPORT)
            targeting(TargetingMode.SELF)
            cost(AbilityCost.Free)
            execute { applyEffectToSelf(effects.atkUp.definition) }
        }

    /** All monster abilities. */
    val all: List<Ability>
        get() =
            listOf(
                fireLoogie,
                poisonBite,
                hellfireOrb,
                mindBlast,
                extractBrain,
                disintegrationRay,
                fearRay,
                paralyzeRay,
                slowRay,
                necroRay,
                iceRay,
                tripRay,
                fireRay,
                deathRay,
                flameBreath,
                rage,
            )
}
