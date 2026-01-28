/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Character
import io.github.gbkt.core.rpg.at
import io.github.gbkt.core.rpg.character

/**
 * The four playable character classes in Labyrinth of the Dragon.
 *
 * Each class has distinct stat distributions and abilities:
 * - Druid: Nature magic, healing, balanced stats
 * - Fighter: Physical combat, high HP/ATK/DEF
 * - Monk: Martial arts, high AGI, instant kill potential
 * - Sorcerer: Arcane magic, high MATK/SP, low defenses
 *
 * Each class learns 6 abilities at specific levels:
 * - Level 1: Starting ability
 * - Level 5: Second ability (~Floor 2)
 * - Level 10: Third ability (~Floor 3)
 * - Level 15: Fourth ability (~Floor 4)
 * - Level 20: Fifth ability (~Floor 5)
 * - Level 25: Ultimate ability (~Floor 6)
 */
class Characters(builder: GameBuilder, private val abilities: Abilities) {

    /**
     * Druid - Nature Magic Class
     *
     * Balanced healer with nature magic. Can both heal and deal elemental damage. Good
     * survivability with moderate stats.
     *
     * Stat Focus: MATK, MDEF, balanced HP Role: Healer / Elemental DPS
     */
    val druid: Character by
        builder.character {
            sprite(SpriteAsset("sprites/hero.png"))
            // Stat tiers: HP=B, SP=B, ATK=C, DEF=B, MATK=B, MDEF=A, AGL=B
            stats {
                hp(20, max = 999) // B-tier at Lv5
                sp(12, max = 99) // B-tier at Lv5
                atk(10) // C-tier at Lv5
                def(12) // B-tier at Lv5
                matk(16) // B-tier at Lv5
                mdef(15) // A-tier at Lv5
                agl(1) // B-tier at Lv5
                level(5) // Original: NEW_CHARACTER_LEVEL = 5
            }
            leveling {
                maxLevel(99)
                learns(
                    abilities.cureWounds at 1, // Starting ability
                    abilities.barkSkin at 5, // ~Floor 2
                    abilities.lightning at 10, // ~Floor 3
                    abilities.majorHeal at 15, // ~Floor 4
                    abilities.insectPlague at 20, // ~Floor 5
                    abilities.regenerate at 25, // ~Floor 6
                )
            }
        }

    /**
     * Fighter - Martial Warrior Class
     *
     * Physical powerhouse with the highest HP and defense. Excels at dealing and taking physical
     * damage. Limited magical ability.
     *
     * Stat Focus: HP, ATK, DEF Role: Tank / Physical DPS
     */
    val fighter: Character by
        builder.character {
            sprite(SpriteAsset("sprites/hero.png"))
            // Stat tiers: HP=A, SP=C, ATK=B, DEF=A, MATK=C, MDEF=B, AGL=B
            stats {
                hp(23, max = 999) // A-tier at Lv5
                sp(8, max = 99) // C-tier at Lv5
                atk(16) // B-tier at Lv5
                def(15) // A-tier at Lv5
                matk(10) // C-tier at Lv5
                mdef(12) // B-tier at Lv5
                agl(1) // B-tier at Lv5
                level(5) // Original: NEW_CHARACTER_LEVEL = 5
            }
            leveling {
                maxLevel(99)
                learns(
                    abilities.secondWind at 1, // Starting ability - self heal
                    abilities.actionSurge at 5, // ~Floor 2 - extra attack
                    abilities.cleave at 10, // ~Floor 3 - AoE attack
                    abilities.tripAttack at 15, // ~Floor 4 - stun
                    abilities.menace at 20, // ~Floor 5 - debuff enemies
                    abilities.indomitable at 25, // ~Floor 6 - massive DEF buff
                )
            }
        }

    /**
     * Monk - Martial Artist Class
     *
     * Agile combatant with the highest speed. Uses ki-powered techniques including the deadly
     * Quivering Palm. Balanced between offense and evasion.
     *
     * Stat Focus: AGL, ATK, balanced defenses Role: Speedy DPS / Debuff clearer
     */
    val monk: Character by
        builder.character {
            sprite(SpriteAsset("sprites/hero.png"))
            // Stat tiers: HP=B, SP=B, ATK=B, DEF=B, MATK=C, MDEF=B, AGL=A
            stats {
                hp(20, max = 999) // B-tier at Lv5
                sp(12, max = 99) // B-tier at Lv5
                atk(16) // B-tier at Lv5
                def(12) // B-tier at Lv5
                matk(10) // C-tier at Lv5
                mdef(12) // B-tier at Lv5
                agl(3) // A-tier at Lv5
                level(5) // Original: NEW_CHARACTER_LEVEL = 5
            }
            leveling {
                maxLevel(99)
                learns(
                    abilities.evasion at 1, // Starting ability - evasion buff
                    abilities.openPalm at 5, // ~Floor 2 - push + damage
                    abilities.stillMind at 10, // ~Floor 3 - cure debuffs
                    abilities.flurry at 15, // ~Floor 4 - multi-hit
                    abilities.diamondBody at 20, // ~Floor 5 - huge DEF buff
                    abilities.quiveringPalm at 25, // ~Floor 6 - instant kill
                )
            }
        }

    /**
     * Sorcerer - Arcane Magic Class
     *
     * Pure spellcaster with devastating magical attacks. Highest MATK and SP but lowest HP and
     * physical stats. Classic "glass cannon" archetype.
     *
     * Stat Focus: MATK, SP, MDEF Role: Magical DPS / AoE specialist
     */
    val sorcerer: Character by
        builder.character {
            sprite(SpriteAsset("sprites/hero.png"))
            // Stat tiers: HP=C, SP=A, ATK=C, DEF=C, MATK=A, MDEF=B, AGL=A
            stats {
                hp(16, max = 999) // C-tier at Lv5
                sp(15, max = 99) // A-tier at Lv5
                atk(10) // C-tier at Lv5
                def(9) // C-tier at Lv5
                matk(21) // A-tier at Lv5
                mdef(12) // B-tier at Lv5
                agl(3) // A-tier at Lv5
                level(5) // Original: NEW_CHARACTER_LEVEL = 5
            }
            leveling {
                maxLevel(99)
                learns(
                    abilities.darkness at 1, // Starting ability - blind enemies
                    abilities.fireball at 5, // ~Floor 2 - fire damage + burn
                    abilities.haste at 10, // ~Floor 3 - speed buff
                    abilities.sleetStorm at 15, // ~Floor 4 - ice AoE
                    abilities.disintegrate at 20, // ~Floor 5 - high damage
                    abilities.wildMagic at 25, // ~Floor 6 - random powerful effect
                )
            }
        }
}
