/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName")

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.ExpCurve
import io.github.gbkt.rpg.dsl.character

/**
 * Labyrinth of the Dragon — character class definitions.
 *
 * Ports all 4 playable character classes and the Test debug class from the Original C
 * implementation in `LabyrinthOfTheDragon/src/player.c` and `player.h`.
 *
 * ## Original C Reference
 * - `PlayerClass` enum: `player.h` line 21–27
 * - Stat tiers: `player.c` `*_update_stats()` functions (lines 258–648)
 * - Starting level: `player.c` `init_player()` calls `set_player_level(4)` (line 1066)
 * - Max level: 99 (inferred from level cap in `set_player_level`)
 * - Default character names: `player.c` `init_player()` switch statement
 *
 * ## Stat Tier Mapping
 *
 * The Original uses a four-tier power system (C < B < A < S) with lookup tables. Stats at starting
 * level 4 are approximated from tier ranks:
 *
 * | Tier | HP  | SP  | ATK | DEF | MATK | MDEF | AGL |
 * |------|-----|-----|-----|-----|------|------|-----|
 * | C    | 60  | 10  | 8   | 6   | 8    | 6    | 8   |
 * | B    | 80  | 20  | 12  | 10  | 12   | 10   | 12  |
 * | A    | 100 | 35  | 16  | 14  | 16   | 14   | 16  |
 * | S    | 120 | 50  | 20  | 18  | 20   | 18   | 20  |
 *
 * ## Ability Learning
 *
 * Each class earns 6 abilities through the ability-grant system. Abilities are defined in
 * `rpg/abilities/` and wired through the `learns {}` block when ability definitions are available
 * in the combat plan.
 */

// =============================================================================
// Character class references returned from defineCharacters()
// =============================================================================

/**
 * Typed container for all Labyrinth of the Dragon character class definitions.
 *
 * Returned by [GameBuilder.defineCharacters] for zero-magic-string access in downstream plans
 * (combat, hero-select scene, etc.).
 *
 * @property druid Druid class — balanced magic/defense, healer + lightning damage.
 * @property fighter Fighter class — high HP/DEF melee combatant.
 * @property monk Monk class — high AGL martial artist, debuff-clearing abilities.
 * @property sorcerer Sorcerer class — fragile but high SP and MATK, AOE magic.
 * @property test Test debug class — max stats, special abilities for development. Not selectable in
 *   normal gameplay (CLASS_TEST = 0xFF).
 */
data class LabyrinthCharacters(
    val druid: CharacterDef,
    val fighter: CharacterDef,
    val monk: CharacterDef,
    val sorcerer: CharacterDef,
    val test: CharacterDef,
)

// =============================================================================
// Character definitions DSL extension
// =============================================================================

/**
 * Registers all 4 playable character classes and the debug Test class.
 *
 * Registers with the current [GameBuilder] so the GBDK backend can emit C stat structures. Returns
 * [LabyrinthCharacters] for downstream typed references.
 *
 * ## Original C Source
 * `LabyrinthOfTheDragon/src/player.c` — `druid_update_stats()` line 258, `fighter_update_stats()`
 * line 351, `monk_update_stats()` line 468, `sorcerer_update_stats()` line 637,
 * `test_class_update_stats()` line 789.
 */
fun GameBuilder.defineCharacters(): LabyrinthCharacters {

    // -------------------------------------------------------------------------
    // Druid (CLASS_DRUID = 0)
    // Tiers: hp=B, sp=B, atk=C, def=B, matk=B, mdef=A, agl=B
    // Magic class — healer and lightning caster. Named "Lyra" by default.
    // Abilities: cure_wounds, bark_skin, lightning, heal, insect_plague, regen
    // Original: player.c lines 258–345
    // -------------------------------------------------------------------------

    /**
     * Druid character class.
     *
     * A magic class specialising in healing and nature magic. Uses MATK for base attacks (Poison
     * Spray). High MDEF makes her resilient against magical damage. Learns regen and bark-skin for
     * sustainability, with lightning and insect plague as offense.
     *
     * Default name: "Lyra" — see `player.c` `init_player()`. Original index: `CLASS_DRUID = 0` —
     * `player.h` line 22.
     */
    val druidDef =
        character("druid") {
            name("Druid")
            stats {
                hp(80) // B tier
                sp(20) // B tier
                atk(8) // C tier — magic class, physical ATK is low
                def(10) // B tier
                matk(12) // B tier — primary offensive stat
                mdef(14) // A tier — strong magical defense
                agl(12) // B tier
            }
            level(4, maxLevel = 99, expCurve = ExpCurve.STANDARD)
        }

    // -------------------------------------------------------------------------
    // Fighter (CLASS_FIGHTER = 1)
    // Tiers: hp=A, sp=C, atk=B, def=A, matk=C, mdef=B, agl=B
    // Martial class — tanky melee warrior. Named "Deneth" by default.
    // Abilities: second_wind, action_surge, cleave, trip_attack, menace, indomitable
    // Original: player.c lines 351–463
    // -------------------------------------------------------------------------

    /**
     * Fighter character class.
     *
     * The archetypal melee combatant with the highest HP and DEF in the game. Low SP (C tier) and
     * minimal magic stats — pure physical damage dealer and tank. Trip Attack and Menace provide
     * crowd control. Indomitable gives full damage resistance.
     *
     * Default name: "Deneth" — see `player.c` `init_player()`. Original index: `CLASS_FIGHTER = 1`
     * — `player.h` line 23.
     */
    val fighterDef =
        character("fighter") {
            name("Fighter")
            stats {
                hp(100) // A tier — highest HP
                sp(10) // C tier — limited resource
                atk(12) // B tier — strong physical attacker
                def(14) // A tier — highest defense
                matk(8) // C tier — weak magic
                mdef(10) // B tier — moderate magical defense
                agl(12) // B tier
            }
            level(4, maxLevel = 99, expCurve = ExpCurve.STANDARD)
        }

    // -------------------------------------------------------------------------
    // Monk (CLASS_MONK = 2)
    // Tiers: hp=B, sp=B, atk=B, def=B, matk=C, mdef=B, agl=A
    // Martial class — fastest character, debuff immunity. Named "Ken" by default.
    // Abilities: evasion, open_palm, still_mind, flurry, diamond_body, quivering_palm
    // Original: player.c lines 468–631
    // -------------------------------------------------------------------------

    /**
     * Monk character class.
     *
     * A martial arts specialist with the highest AGL in the game. Balanced physical stats with weak
     * magic. Still Mind clears all debuffs; Diamond Body grants physical/magical resistance.
     * Quivering Palm has a chance for instant kill.
     *
     * Default name: "Ken" — see `player.c` `init_player()`. Original index: `CLASS_MONK = 2` —
     * `player.h` line 24.
     */
    val monkDef =
        character("monk") {
            name("Monk")
            stats {
                hp(80) // B tier
                sp(20) // B tier
                atk(12) // B tier — uses atk+agl for attack rolls
                def(10) // B tier
                matk(8) // C tier — weak magic attack
                mdef(10) // B tier
                agl(16) // A tier — fastest character
            }
            level(4, maxLevel = 99, expCurve = ExpCurve.STANDARD)
        }

    // -------------------------------------------------------------------------
    // Sorcerer (CLASS_SORCERER = 3)
    // Tiers: hp=C, sp=A, atk=C, def=C, matk=A, mdef=B, agl=A
    // Magic class — highest damage potential, but fragile. Named "Tyrion" by default.
    // Abilities: darkness, fireball, haste, sleetstorm, disintegrate, wild_magic
    // Original: player.c lines 637–783
    // -------------------------------------------------------------------------

    /**
     * Sorcerer character class.
     *
     * A glass cannon with the lowest HP and DEF but the highest SP and MATK. AOE-focused with
     * Fireball and Darkness (blind all enemies). Haste gives double attacks. Wild Magic is chaotic
     * but powerful at higher levels. Sleet Storm marks enemies for guaranteed critical hits.
     * Disintegrate has instant-kill chance.
     *
     * Default name: "Tyrion" — see `player.c` `init_player()`. Original index: `CLASS_SORCERER = 3`
     * — `player.h` line 25.
     */
    val sorcererDef =
        character("sorcerer") {
            name("Sorcerer")
            stats {
                hp(60) // C tier — fragile
                sp(35) // A tier — largest SP pool
                atk(8) // C tier — magic class, weak physical
                def(6) // C tier — lowest defense
                matk(16) // A tier — strongest magic attacker
                mdef(10) // B tier
                agl(16) // A tier — fast caster
            }
            level(4, maxLevel = 99, expCurve = ExpCurve.STANDARD)
        }

    // -------------------------------------------------------------------------
    // Test Class (CLASS_TEST = 0xFF)
    // Tiers: all S tier — max stats for debug/development use
    // NOT selectable in normal gameplay — for Phase 07 UAT testing
    // Original: player.c lines 789–862, player.h lines 462–475
    // -------------------------------------------------------------------------

    /**
     * Test debug class (not playable in normal gameplay).
     *
     * All stats at S tier (maximum) for rapid debugging and UAT. The Test class has 6 special debug
     * abilities (Damage All, (De)buff, SUPERKILL, etc.) and bypasses normal class ability gates.
     * Used during Phase 07 UAT to test all game systems.
     *
     * Original index: `CLASS_TEST = 0xFF` — `player.h` line 26. Debug abilities defined in
     * `player.data.c` lines 159–181.
     */
    val testDef =
        character("test") {
            name("Test")
            stats {
                hp(120) // S tier
                sp(50) // S tier
                atk(20) // S tier
                def(18) // S tier
                matk(20) // S tier
                mdef(18) // S tier
                agl(20) // S tier
            }
            level(4, maxLevel = 99, expCurve = ExpCurve.STANDARD)
        }

    return LabyrinthCharacters(
        druid = druidDef,
        fighter = fighterDef,
        monk = monkDef,
        sorcerer = sorcererDef,
        test = testDef,
    )
}
