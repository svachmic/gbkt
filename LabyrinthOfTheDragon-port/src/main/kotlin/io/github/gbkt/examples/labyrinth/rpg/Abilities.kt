/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.rpg.Ability
import io.github.gbkt.examples.labyrinth.rpg.abilities.DruidAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.FighterAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.MonkAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.SorcererAbilities

/**
 * Ability coordinator for Labyrinth of the Dragon.
 *
 * This class coordinates all character class abilities:
 * - [DruidAbilities] - Nature magic and healing
 * - [FighterAbilities] - Physical combat and self-buffs
 * - [MonkAbilities] - Martial arts and ki techniques
 * - [SorcererAbilities] - Arcane magic and elemental spells
 *
 * Each class has 6 unique abilities. Class IDs:
 * - 0 = Druid
 * - 1 = Fighter
 * - 2 = Monk
 * - 3 = Sorcerer
 */
class Abilities(builder: GameBuilder, effects: StatusEffects) {

    /** Druid abilities - Nature magic and healing. */
    val druid = DruidAbilities(builder, effects)

    /** Fighter abilities - Physical combat and self-buffs. */
    val fighter = FighterAbilities(builder, effects)

    /** Monk abilities - Martial arts and ki techniques. */
    val monk = MonkAbilities(builder, effects)

    /** Sorcerer abilities - Arcane magic and elemental spells. */
    val sorcerer = SorcererAbilities(builder, effects)

    // =========================================================================
    // Convenience accessors for individual abilities (preserves API compatibility)
    // =========================================================================

    // Druid
    val cureWounds: Ability
        get() = druid.cureWounds

    val barkSkin: Ability
        get() = druid.barkSkin

    val lightning: Ability
        get() = druid.lightning

    val majorHeal: Ability
        get() = druid.majorHeal

    val insectPlague: Ability
        get() = druid.insectPlague

    val regenerate: Ability
        get() = druid.regenerate

    // Fighter
    val secondWind: Ability
        get() = fighter.secondWind

    val actionSurge: Ability
        get() = fighter.actionSurge

    val cleave: Ability
        get() = fighter.cleave

    val tripAttack: Ability
        get() = fighter.tripAttack

    val menace: Ability
        get() = fighter.menace

    val indomitable: Ability
        get() = fighter.indomitable

    // Monk
    val evasion: Ability
        get() = monk.evasion

    val openPalm: Ability
        get() = monk.openPalm

    val stillMind: Ability
        get() = monk.stillMind

    val flurry: Ability
        get() = monk.flurry

    val diamondBody: Ability
        get() = monk.diamondBody

    val quiveringPalm: Ability
        get() = monk.quiveringPalm

    // Sorcerer
    val darkness: Ability
        get() = sorcerer.darkness

    val fireball: Ability
        get() = sorcerer.fireball

    val haste: Ability
        get() = sorcerer.haste

    val sleetStorm: Ability
        get() = sorcerer.sleetStorm

    val disintegrate: Ability
        get() = sorcerer.disintegrate

    val wildMagic: Ability
        get() = sorcerer.wildMagic

    /**
     * Get abilities for a specific class by ID.
     *
     * @param classId The class ID (0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer)
     * @return List of abilities for that class
     */
    fun forClass(classId: Int): List<Ability> =
        when (classId) {
            0 -> druid.all
            1 -> fighter.all
            2 -> monk.all
            3 -> sorcerer.all
            else -> emptyList()
        }

    /** All abilities across all classes. */
    val all: List<Ability>
        get() = druid.all + fighter.all + monk.all + sorcerer.all
}
