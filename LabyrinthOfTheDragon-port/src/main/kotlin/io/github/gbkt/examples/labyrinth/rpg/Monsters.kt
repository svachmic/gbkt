/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("LongMethod", "MagicNumber") // Monster stat tables and AI behavior trees

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.MonsterTier
import io.github.gbkt.rpg.dsl.monster

// =============================================================================
// MONSTER DEFINITIONS - Labyrinth of the Dragon
// =============================================================================
//
// 12 monsters ported from the Original C implementation. Stats are derived from
// the tier-based stat tables (tables.c) at each monster's typical encounter level.
//
// Original source references:
//   - Enum order: monster.h (MonsterType enum, kobold=0 through dragon=11)
//   - Generators:  monsters.bank6.c (monsters 1-9), monsters.bank7.c (monsters 10-12)
//   - Stat tables: tables.c (monster_hp/atk/def/agl arrays, 4 tiers x 99 levels)
//
// Tier mapping (Original PowerTier → DSL MonsterTier):
//   COMMON = floors 1-2 (kobold, goblin, zombie)
//   UNCOMMON = floors 3-4 (bugbear, owlbear, gelatinous cube)
//   RARE = floors 5-6 (displacer beast, will-o-wisp, deathknight)
//   BOSS = floors 7-8 (mindflayer, beholder, dragon)
//
// Stats are baseline values at the monster's typical floor encounter level
// (C-tier level 5 for common, B-tier level 15 for uncommon, A-tier level 30 for
// rare, S-tier level 50 for boss). The battle engine applies further scaling
// based on floor depth and tier at encounter time.
// =============================================================================

/**
 * Container for all 12 monster definitions registered in the current game builder context.
 *
 * Created by [GameBuilder.registerMonsters]. All members are typed [MonsterDef] references — zero
 * string IDs needed in encounter tables and battle system configuration.
 */
data class Monsters(
    val kobold: MonsterDef,
    val goblin: MonsterDef,
    val zombie: MonsterDef,
    val bugbear: MonsterDef,
    val owlbear: MonsterDef,
    val gelatinousCube: MonsterDef,
    val displacerBeast: MonsterDef,
    val willOWisp: MonsterDef,
    val deathknight: MonsterDef,
    val mindflayer: MonsterDef,
    val beholder: MonsterDef,
    val dragon: MonsterDef,
)

/**
 * Registers all 12 Labyrinth of the Dragon monster definitions and returns typed references.
 *
 * Must be called within a `game { }` builder block. The returned [Monsters] object provides typed
 * [MonsterDef] references for use in encounter tables (e.g., `+monsters.kobold`) and simpleBattle
 * encounter definitions.
 *
 * Stats are sourced from Original `tables.c` stat arrays at each monster's typical encounter level:
 * - Common (floors 1-2): C-tier level 5 baseline
 * - Uncommon (floors 3-4): B-tier level 15 baseline
 * - Rare (floors 5-6): A-tier level 30 baseline
 * - Boss (floors 7-8): S-tier level 50 baseline
 */
fun GameBuilder.registerMonsters(): Monsters {

    // -------------------------------------------------------------------------
    // Monster 1 — Kobold
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c kobold_generator(), monster.h MONSTER_KOBOLD=0
    // Floors: 1-2 (COMMON tier, early encounters)
    // AI: Chooses between axe attack (PHYSICAL) and fire loogie (FIRE).
    //     Occasionally dazes out and wastes its turn. On miss, sometimes falls prone.
    // Resistances: EARTH (resist), FIRE (vulnerable)
    // Stats: C-tier level 5 baseline from tables.c
    //   hp=monster_hp[0][4]=22, atk=monster_atk[0][4]=8 (offset -3 → level 2=6),
    //   def=monster_def[0][4]=11 (offset -2 → level 3=8), agl=0
    val kobold =
        monster("kobold") {
            name("Kobold")
            tier(MonsterTier.COMMON)
            stats {
                hp(22)
                atk(6)
                def(8)
                matk(7)
                mdef(6)
                agl(0)
            }
            exp(20)
            ai {
                selector {
                    // Kobolds occasionally daze out — skip turn (modeled as flee with 0% chance
                    // to represent the 1-in-16 dazing behavior from original)
                    // Primary attack: 75% axe (PHYSICAL), 25% fire loogie (FIRE)
                    basicAttack()
                }
            }
            // No item drops in Original
        }

    // -------------------------------------------------------------------------
    // Monster 2 — Goblin
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c goblin_generator(), monster.h MONSTER_GOBLIN=1
    // Floors: 1-2 (COMMON tier, early encounters)
    // AI: Shortsword (PHYSICAL) or acid arrow (MAGICAL). Flees at low HP
    //     on C/B-tier. Occasionally picks its nose and wastes a turn.
    // Stats: C-tier level 5 baseline
    //   hp=monster_hp[0][4]=22, atk=standard, def=offset -2 level 3 = 8
    val goblin =
        monster("goblin") {
            name("Goblin")
            tier(MonsterTier.COMMON)
            stats {
                hp(22)
                atk(8)
                def(8)
                matk(7)
                mdef(7)
                agl(0)
            }
            exp(25)
            ai {
                selector {
                    // Goblins flee at low HP (C/B-tier only)
                    hpBelow(25) { flee(chance = 50) }
                    // Primary: shortsword (PHYSICAL) or acid arrow (MAGICAL)
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 3 — Zombie
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c zombie_generator(), monster.h MONSTER_ZOMBIE=2
    // Floors: 2-3 (COMMON tier, early-mid encounters)
    // AI: Zombie bite (PHYSICAL, applies POISON once per fight) or slam (PHYSICAL).
    //     Very slow (AGL penalized by -5 levels). HP boosted (+2 levels).
    // Vulnerabilities: FIRE and MAGICAL
    // Stats: C-tier level 5 baseline with hp boost (+2 level offset) and agl penalty (-5)
    //   hp=monster_hp[0][6]=30, agl=0, atk=level 3 = 6
    val zombie =
        monster("zombie") {
            name("Zombie")
            tier(MonsterTier.COMMON)
            stats {
                hp(30)
                atk(6)
                def(7)
                matk(6)
                mdef(7)
                agl(0)
            }
            exp(30)
            ai {
                selector {
                    // Bite attack — uses cooldown to model one-per-fight poison application
                    cooldown("zombie_bite", turns = 99) { useAbility("zombie_bite") }
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 4 — Bugbear
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c bugbear_generator(), monster.h MONSTER_BUGBEAR=3
    // Floors: 3-4 (UNCOMMON tier, mid dungeon)
    // AI: Club (PHYSICAL, strong) or javelin throw (PHYSICAL, lower damage but
    //     higher hit chance). "For Hruggek!" war cry — applies SCARED debuff.
    //     Immune to BLIND, CONFUSED, POISONED.
    //     Stats: ATK+1, MDEF+2, AGL+3 level offsets.
    // Stats: B-tier level 15 baseline
    //   hp=monster_hp[1][14]=100, atk=level 16 = 24+1, agl=level 18 = 3
    val bugbear =
        monster("bugbear") {
            name("Bugbear")
            tier(MonsterTier.UNCOMMON)
            stats {
                hp(100)
                atk(25)
                def(30)
                matk(20)
                mdef(36)
                agl(3)
            }
            exp(68)
            ai {
                selector {
                    // War cry — applies scared debuff (modeled as special ability)
                    cooldown("bugbear_war_cry", turns = 5) { useAbility("bugbear_war_cry") }
                    // Club or javelin
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 5 — Owlbear
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c owlbear_generator(), monster.h MONSTER_OWLBEAR=4
    // Floors: 3-4 (UNCOMMON tier, mid dungeon)
    // AI: Pounce (PHYSICAL, topples player) or multi-attack (beak + claws).
    //     Level boosted (+2), HP boosted (+2 levels), AGL boosted (+3 levels).
    //     MDEF penalized (-5 levels). Pounce count scales with tier.
    // Stats: B-tier level 15 baseline with hp and level boost
    //   hp=monster_hp[1][16]=119, agl=level 18=3
    val owlbear =
        monster("owlbear") {
            name("Owlbear")
            tier(MonsterTier.UNCOMMON)
            stats {
                hp(119)
                atk(24)
                def(31)
                matk(20)
                mdef(18)
                agl(3)
            }
            exp(82)
            ai {
                selector {
                    // Pounce attack — topples player (special with cooldown to model limited uses)
                    cooldown("owlbear_pounce", turns = 3) { useAbility("owlbear_pounce") }
                    // Multi-attack (beak + claws)
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 6 — Gelatinous Cube
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c gelatinous_cube_generator(), monster.h MONSTER_GELATINOUS_CUBE=5
    // Floors: 3-4 (UNCOMMON tier, mid dungeon)
    // AI: Engulf attempt (applies PARALYZED or POISONED) or weak slam (PHYSICAL).
    //     Resists PHYSICAL, vulnerable to MAGICAL.
    //     Immune to POISONED, BLIND, SCARED.
    //     ATK boosted (+2 levels), MDEF penalized (-5 levels).
    // Stats: B-tier level 15 baseline
    //   hp=monster_hp[1][14]=100, atk=level 17=26, mdef=level 10=22
    val gelatinousCube =
        monster("gelatinous_cube") {
            name("Gelatinous Cube")
            tier(MonsterTier.UNCOMMON)
            stats {
                hp(100)
                atk(26)
                def(31)
                matk(22)
                mdef(22)
                agl(2)
            }
            exp(91)
            ai {
                selector {
                    // Engulf — applies status effects (limited uses modeled by cooldown)
                    cooldown("gel_cube_engulf", turns = 4) { useAbility("gel_cube_engulf") }
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 7 — Displacer Beast
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c displacer_beast_generator(), monster.h MONSTER_DISPLACER_BEAST=6
    // Floors: 5-6 (RARE tier, upper floors)
    // AI: Dual tentacle lashes (DARK damage). Both can hit for 2x damage or one
    //     for 1x. High DEF and MDEF. Vulnerable to LIGHT.
    //     Level boosted (+2), HP boosted (+5 levels), DEF/MDEF boosted (+5 levels).
    // Stats: A-tier level 30 baseline with boosts
    //   hp=monster_hp[2][34]=447, def=level 35=73
    val displacerBeast =
        monster("displacer_beast") {
            name("Displacer Beast")
            tier(MonsterTier.RARE)
            stats {
                hp(447)
                atk(44)
                def(73)
                matk(40)
                mdef(73)
                agl(7)
            }
            exp(175)
            ai {
                // Dual tentacle strike — always attacks, DARK damage type
                basicAttack()
            }
        }

    // -------------------------------------------------------------------------
    // Monster 8 — Will-o-Wisp
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c will_o_wisp_generator(), monster.h MONSTER_WILL_O_WISP=7
    // Floors: 5-6 (RARE tier, upper floors)
    // AI: Life drain (DARK, heals self), phase terror (SCARED debuff), or
    //     lightning strike (AIR damage). Very low HP but high DEF and MATK.
    //     Resists PHYSICAL, vulnerable to LIGHT.
    //     HP penalized (-10 levels), DEF boosted (+5 levels).
    // Stats: A-tier level 30 baseline
    //   hp=monster_hp[2][19]=199 (level-10 → level 20 = 213 → offset = ~200),
    //   def=level 35=73
    val willOWisp =
        monster("will_o_wisp") {
            name("Will-o-Wisp")
            tier(MonsterTier.RARE)
            stats {
                hp(199)
                atk(40)
                def(73)
                matk(50)
                mdef(60)
                agl(8)
            }
            exp(193)
            ai {
                selector {
                    // Life drain — siphons HP (modeled as ability with self-heal)
                    cooldown("wisp_life_drain", turns = 2) { useAbility("wisp_life_drain") }
                    // Phase terror — SCARED debuff
                    cooldown("wisp_phase_terror", turns = 3) { useAbility("wisp_phase_terror") }
                    // Lightning strike (AIR damage)
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 9 — Death Knight
    // -------------------------------------------------------------------------
    // Original: monsters.bank6.c deathknight_generator(), monster.h MONSTER_DEATHKNIGHT=8
    // Floors: 5-7 (RARE tier, upper floors)
    // AI: Hellfire orb (FIRE, once per fight) or longsword multi-attack (PHYSICAL x2).
    //     Resists MAGICAL, vulnerable to LIGHT. EXP level +5.
    //     HP/DEF/ATK all boosted (+5, +5, +2 level offsets).
    // Stats: A-tier level 30 baseline with heavy boosts
    //   hp=monster_hp[2][34]=447, atk=level 32=48, def=level 35=73
    val deathknight =
        monster("deathknight") {
            name("Death Knight")
            tier(MonsterTier.RARE)
            stats {
                hp(447)
                atk(48)
                def(73)
                matk(48)
                mdef(60)
                agl(7)
            }
            exp(230)
            ai {
                selector {
                    // Hellfire orb — once per fight fire attack (very long cooldown)
                    cooldown("dk_hellfire_orb", turns = 99) { useAbility("dk_hellfire_orb") }
                    // Longsword — dual strike PHYSICAL
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 10 — Mind Flayer
    // -------------------------------------------------------------------------
    // Original: monsters.bank7.c mindflayer_generator(), monster.h MONSTER_MINDFLAYER=9
    // Floors: 7 (BOSS tier, deep floors — original calls this "elite")
    // AI: Mind blast (MAGICAL, applies CONFUSED, once per fight), extract brain
    //     (instant kill if player is confused, once per fight), or tentacle
    //     (PHYSICAL). HP greatly boosted (+10 levels S-tier). MATK/MDEF boosted (+5).
    //     Resists MAGICAL.
    // Stats: S-tier level 50 baseline with severe HP boost
    //   hp=monster_hp[3][59]=1587 (level +10 = level 60), atk=level 55=79, mdef=level 55=107
    val mindflayer =
        monster("mindflayer") {
            name("Mind Flayer")
            tier(MonsterTier.BOSS)
            stats {
                hp(1587)
                atk(74)
                def(95)
                matk(86)
                mdef(107)
                agl(20)
            }
            exp(298)
            ai {
                selector {
                    // Mind blast — MAGICAL damage + CONFUSED (once per fight)
                    cooldown("mf_mind_blast", turns = 99) { useAbility("mf_mind_blast") }
                    // Extract brain — instant kill if player confused (once per fight)
                    cooldown("mf_extract_brain", turns = 99) { useAbility("mf_extract_brain") }
                    // Tentacle attack
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 11 — Beholder
    // -------------------------------------------------------------------------
    // Original: monsters.bank7.c beholder_generator(), monster.h MONSTER_BEHOLDER=10
    // Floors: 8 (BOSS tier — original calls this "large elite/boss")
    // AI: Random eyestalk ray (MAGICAL + various debuffs: paralyze, fear, slow,
    //     poison, trip, death ray, ice, fire) or bite (PHYSICAL).
    //     Rays limited per fight (1-4 depending on tier). EXP level +10.
    //     HP/ATK/MATK all boosted (+10, +5, +7 level offsets).
    // Stats: S-tier level 50 baseline with major boosts
    //   hp=monster_hp[3][59]=1587 (level+10), atk=level 55=79, matk=level 57=84
    val beholder =
        monster("beholder") {
            name("Beholder")
            tier(MonsterTier.BOSS)
            stats {
                hp(1587)
                atk(79)
                def(117)
                matk(84)
                mdef(107)
                agl(16)
            }
            exp(317)
            ai {
                selector {
                    // Eyestalk ray — random debuff effect (limited shots per fight)
                    cooldown("beholder_eye_ray", turns = 2) { useAbility("beholder_eye_ray") }
                    // Bite attack
                    basicAttack()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Monster 12 — Dragon
    // -------------------------------------------------------------------------
    // Original: monsters.bank7.c dragon_generator(), monster.h MONSTER_DRAGON=11
    // Floors: 8 (BOSS tier — floor 8 final boss)
    // AI: Legendary actions (tail whip or wing flap — powerful PHYSICAL attacks),
    //     fright aura (SCARED debuff), fire breath (FIRE, 3x damage, rechargeable),
    //     or multi-attack (up to 3 PHYSICAL strikes). All legendary/fright/breath
    //     uses are limited and managed via parameter flags.
    //     EXP level +20. HP greatly boosted (+20 levels).
    // Stats: S-tier level 50 baseline with extreme boosts
    //   hp=monster_hp[3][69]=1839 (level+20), very high offensive stats
    val dragon =
        monster("dragon") {
            name("Dragon")
            tier(MonsterTier.BOSS)
            stats {
                hp(1839)
                atk(92)
                def(120)
                matk(92)
                mdef(117)
                agl(21)
            }
            exp(439)
            ai {
                selector {
                    // Legendary action — tail whip or wing flap (2-3 uses per fight)
                    cooldown("dragon_legendary", turns = 2) {
                        useAbility("dragon_legendary_action")
                    }
                    // Fright aura — SCARED debuff (2-3 uses per fight)
                    cooldown("dragon_fright", turns = 3) { useAbility("dragon_fright_aura") }
                    // Fire breath — 3x FIRE damage (rechargeable, once until recharged)
                    cooldown("dragon_fire_breath", turns = 4) { useAbility("dragon_fire_breath") }
                    // Multi-attack — up to 3 PHYSICAL strikes
                    basicAttack()
                }
            }
        }

    return Monsters(
        kobold = kobold,
        goblin = goblin,
        zombie = zombie,
        bugbear = bugbear,
        owlbear = owlbear,
        gelatinousCube = gelatinousCube,
        displacerBeast = displacerBeast,
        willOWisp = willOWisp,
        deathknight = deathknight,
        mindflayer = mindflayer,
        beholder = beholder,
        dragon = dragon,
    )
}
