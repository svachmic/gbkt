/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName")

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.examples.labyrinth.rpg.abilities.LabyrinthDruidAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.LabyrinthFighterAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.LabyrinthMonkAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.LabyrinthSorcererAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.defineDruidAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.defineFighterAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.defineMonkAbilities
import io.github.gbkt.examples.labyrinth.rpg.abilities.defineSorcererAbilities
import io.github.gbkt.rpg.dsl.AbilityRef

/**
 * Labyrinth of the Dragon — all class ability references, aggregated.
 *
 * Typed container holding the ability definitions for all 4 playable character classes (24
 * abilities total). Returned by [GameBuilder.defineAbilities] for zero-magic-string access in
 * downstream plans (combat system, character learning, game builder registration).
 *
 * ## Structure
 *
 * Each class sub-container exposes an `all` list of its 6 ability refs, in the slot order defined
 * in `LabyrinthOfTheDragon/src/player.data.c`:
 *
 * | Class    | Slot 0      | Slot 1       | Slot 2     | Slot 3      | Slot 4        | Slot 5         |
 * |----------|-------------|--------------|------------|-------------|---------------|----------------|
 * | Druid    | Cure Wounds | Bark Skin    | Lightning  | Heal        | Insect Plague | Regenerate     |
 * | Fighter  | Second Wind | Action Surge | Cleave     | Trip Attack | Menace        | Indomitable    |
 * | Monk     | Evasion     | Open Palm    | Still Mind | Flurry      | Diamond Body  | Quivering Palm |
 * | Sorcerer | Darkness    | Fireball     | Haste      | Sleetstorm  | Disintegrate  | Wild Magic     |
 *
 * @property druid Druid class abilities — nature magic, healing, sustain.
 * @property fighter Fighter class abilities — physical strikes, debuffs, defense.
 * @property monk Monk class abilities — martial strikes, debuff-clear, evasion.
 * @property sorcerer Sorcerer class abilities — elemental AoE, buffs, chaos magic.
 */
data class LabyrinthAbilities(
    val druid: LabyrinthDruidAbilities,
    val fighter: LabyrinthFighterAbilities,
    val monk: LabyrinthMonkAbilities,
    val sorcerer: LabyrinthSorcererAbilities,
) {
    /**
     * All 24 abilities across all 4 classes, in class+slot order.
     *
     * Order: Druid (0–5), Fighter (6–11), Monk (12–17), Sorcerer (18–23).
     *
     * Used for bulk registration in the game builder.
     */
    val all: List<AbilityRef> = buildList {
        addAll(druid.all)
        addAll(fighter.all)
        addAll(monk.all)
        addAll(sorcerer.all)
    }
}

// =============================================================================
// Abilities aggregator DSL extension
// =============================================================================

/**
 * Registers all 24 class abilities (6 per class across 4 classes) with the game builder.
 *
 * Must be called within the `gbGame {}` DSL block after status effects are defined via
 * [GameBuilder.defineStatusEffects].
 *
 * Delegates to per-class extension functions:
 * - [GameBuilder.defineDruidAbilities]
 * - [GameBuilder.defineFighterAbilities]
 * - [GameBuilder.defineMonkAbilities]
 * - [GameBuilder.defineSorcererAbilities]
 *
 * ## Usage
 *
 * ```kotlin
 * val statusEffects = defineStatusEffects()
 * val abilities = defineAbilities(statusEffects)
 *
 * // Access typed refs:
 * abilities.druid.cureWounds
 * abilities.fighter.secondWind
 * abilities.monk.quiveringPalm
 * abilities.sorcerer.fireball
 *
 * // Access all as list for bulk registration:
 * abilities.all // 24 AbilityRef entries in order
 * ```
 *
 * @param statusEffects Typed status effect refs returned by [GameBuilder.defineStatusEffects].
 *   Required for abilities that apply status effects via `appliesEffect()`.
 * @return [LabyrinthAbilities] typed container for downstream use.
 */
fun GameBuilder.defineAbilities(statusEffects: LabyrinthStatusEffects): LabyrinthAbilities {
    val druidAbilities = defineDruidAbilities(statusEffects)
    val fighterAbilities = defineFighterAbilities(statusEffects)
    val monkAbilities = defineMonkAbilities(statusEffects)
    val sorcererAbilities = defineSorcererAbilities(statusEffects)

    return LabyrinthAbilities(
        druid = druidAbilities,
        fighter = fighterAbilities,
        monk = monkAbilities,
        sorcerer = sorcererAbilities,
    )
}
