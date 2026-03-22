/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("LongMethod", "MagicNumber", "MatchingDeclarationName")

package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.rpg.domain.MonsterTier
import io.github.gbkt.rpg.dsl.BattleRef
import io.github.gbkt.rpg.dsl.simpleBattle

// =============================================================================
// COMBAT SYSTEM — Labyrinth of the Dragon
// =============================================================================
//
// Ports the Original turn-based combat system configuration from:
//   - battle.c/h — combat state machine and battle configuration
//   - stats.c/h  — damage formulas, turn order, status effect mechanics
//   - encounter.h — encounter group definitions
//
// ## Original Combat Flow (battle.h BattleState enum)
//
//   BATTLE_FADE_IN → BATTLE_STATE_MENU → BATTLE_ROLL_INITIATIVE
//   → BATTLE_NEXT_TURN → BATTLE_UPDATE_STATUS_EFFECTS
//   → BATTLE_TAKE_ACTION → BATTLE_ANIMATE → BATTLE_ACTION_CLEANUP
//   → (loop) or BATTLE_REWARDS → BATTLE_SUCCESS/BATTLE_PLAYER_DIED
//
// ## Original Party Configuration (battle.h)
//
//   - maxPartySize: 1 (single hero — Original is a solo dungeon crawler)
//   - maxEnemies: 3 (encounters spawn 1-3 enemies, battle.h MONSTER_POSITION1-3)
//
// ## Original Turn Order (stats.c)
//
//   AGL-based initiative: `roll_flee()` uses AGL comparison for speed determination.
//   `get_agl(level, tier)` lookups from tables.c determine the effective AGL value.
//   BUFF_HASTE doubles effective AGL for initiative roll. DEBUFF_AGL_DOWN reduces it.
//
// ## Original Damage Formulas (stats.c)
//
//   Physical: `calc_damage(d16_roll, base_dmg)` where base_dmg = `get_player_damage(level, tier)`
//   - Hit check: `roll_attack_player(atk, def)` uses `attack_roll_player[ATK-DEF+32]` table
//   - Damage variance: `damage_roll_modifier[d16_roll & 0x0F] * base_dmg / 16`
//   - Critical (d16 >= 14): double damage  (stats.h `is_critical()` line 481)
//   - Fumble  (d16 <= 1):   quarter damage (stats.h `is_fumble()`   line 490)
//   - Elemental: `DAMAGE_PHYSICAL`, `DAMAGE_MAGICAL`, `DAMAGE_FIRE`, etc. (stats.h DamageAspect)
//   - Vulnerability/Resistance modify the final roll using `DAMAGE_FLAG_VULN/RESIST`
//
// ## Original Status Effect Turn Tick (stats.c)
//
//   Applied in BATTLE_UPDATE_STATUS_EFFECTS state before each entity's turn:
//   - DEBUFF_PARALYZED:   `paralyzed_roll(tier)` — chance to skip turn entirely
//   - DEBUFF_SCARED:      `fear_flee_roll(tier)` — flee attempt; `fear_shiver_roll` — frozen
//   - DEBUFF_CONFUSED:    `confused_attack(tier)` — attacks self or ally
//   - DEBUFF_POISONED:    `poison_hp(tier, max_hp)` — HP loss proportional to max HP
//   - BUFF_REGEN:         `regen_hp(tier, max_hp)` — HP recovery proportional to max HP
//   - BUFF_HASTE:         doubles attack count (AGL-based initiative advantage)
//   - BUFF_ATK_UP/DOWN:   `atk_up/down(base, tier)` — ATK modifier table
//   - BUFF_DEF_UP/DOWN:   `def_up/down(base, tier)` — DEF modifier table
//   - BUFF_AGL_UP/DOWN:   `agl_up/down(base_agl, tier)` — AGL modifier table
//   Duration decrement: each effect's `duration` field decrements by 1 each turn;
//   EFFECT_DURATION_PERPETUAL (0xFF) effects never expire (item-applied buffs).
//
// ## Original Escape Mechanic (stats.c `roll_flee()`)
//
//   Player escape succeeds if `player.agl > enemy_max_agl + 10` (guaranteed).
//   Escape fails if `enemy_max_agl > player.agl + 10` (blocked).
//   Otherwise 50% chance: `rand() < 128`. Uses BATTLE_PLAYER_FLED state on success.
//
// ## Original Item Usage in Combat (item.h, battle.c)
//
//   ITEM_POTION: `POTION_HEAL_FACTOR * max_hp >> 4` HP restored
//   ITEM_ETHER:  `ETHER_HEAL_FACTOR * max_sp >> 4` SP restored
//   ITEM_REMEDY: clears all active debuffs from the `effects[]` array
//   ITEM_ELIXIR: full restore (HP = max_hp, SP = max_sp)
//   ITEM_ATK_UP/DEF_UP/REGEN/HASTE: apply perpetual buffs (duration = 0xFF)
//   Items are selected via BATTLE_MENU_ITEM → BATTLE_TAKE_ACTION states.
//
// ## V2 DSL Mapping
//
//   The V2 `simpleBattle()` DSL captures the essential combat configuration:
//   - `party(character)` — registers a character as a party member
//   - `encounter { }` — defines a possible enemy group with tier/level overrides
//   - `onVictory { }` — awards EXP and drops (BATTLE_REWARDS → BATTLE_SUCCESS)
//   - `onDefeat { }` — triggers game-over navigation (BATTLE_PLAYER_DIED → defeat)
//
//   Combat state checks in battle scenes use `CombatStates.VICTORY` / `CombatStates.DEFEAT`
//   typed constants (not raw "COMBAT_STATE_*" strings) for zero-magic-string access.
//   Turn order, damage formulas, and status tick are handled by the V2 combat engine
//   runtime (CombatVisitor-generated C) — they are not re-declared in DSL config.
//
// =============================================================================

/**
 * Typed container for combat system references returned by [GameBuilder.registerCombat].
 *
 * @property combat The [BattleRef] for the main combat system — pass to [battleUpdate] each frame
 *   and to [combatIsInState] checks in the battle scene.
 * @property combatStates Predefined typed state constants for combat state checks. Use
 *   `CombatStates.VICTORY`, `CombatStates.DEFEAT`, etc. to eliminate raw string literals.
 */
data class LabyrinthCombatSystem(val combat: BattleRef)

/**
 * Registers the Labyrinth of the Dragon combat system and returns typed references.
 *
 * Configures the main turn-based battle system with all 4 playable character classes and all 12
 * monster encounter groups from the Original. Returns a [LabyrinthCombatSystem] holder that gives
 * downstream plans (battle scene, exploration) typed access to the [BattleRef].
 *
 * ## Design Notes
 * - All 4 character classes are registered as party members because the player selects their class
 *   in heroSelect. At runtime, only the selected class participates in battle.
 * - Encounter groups are weighted to match the Original's encounter frequency. Common monsters
 *   (weight 30) appear 3x more often than boss monsters (weight 10).
 * - `onVictory` awards the standard EXP+drops (BATTLE_REWARDS → BATTLE_SUCCESS in Original). Scene
 *   navigation after victory is handled by the battle scene plan (Plan 13).
 * - `onDefeat` is intentionally left empty — the battle scene navigates to gameover via
 *   `combatIsInState(CombatStates.DEFEAT, combat)` check in the battle scene plan.
 *
 * ## Original Source References
 * - Battle configuration: `battle.c` ~600 lines, `battle.h` BattleState enum
 * - Damage formulas: `stats.c` `calc_damage()` line 80, `roll_attack_monster/player()` lines 72-78
 * - Status tick logic: `stats.c` `poison_hp()` line 171, `regen_hp()` line 182, etc.
 * - Encounter table weights: `encounter.c`, `map.encounters.c` (encounter weight distributions)
 * - Party size constant: implicitly 1 (solo dungeon crawler — `player` struct, player.h)
 * - Max enemies: 3 (MonsterPosition enum: MONSTER_POSITION1/2/3, battle.h lines 309-313)
 */
fun GameBuilder.registerCombat(
    characters: LabyrinthCharacters,
    monsters: Monsters,
): LabyrinthCombatSystem {

    // -------------------------------------------------------------------------
    // Main combat system — simpleBattle("combat")
    //
    // Party: all 4 classes registered so the runtime can load the selected class.
    // At combat start the engine uses the class chosen in heroSelect.
    //
    // Encounters: grouped by floor tier to match Original's encounter frequency:
    //   - Common     (floors 1-2): kobold, goblin, zombie
    //   - Uncommon   (floors 3-4): bugbear, owlbear, gelatinous cube
    //   - Rare       (floors 5-6): displacer beast, will-o-wisp, deathknight
    //   - Boss/Elite (floors 7-8): mindflayer, beholder, dragon
    //
    // Weight distribution mirrors Original encounter frequency:
    //   Common groups: weight 30  (most frequent)
    //   Uncommon:      weight 20
    //   Rare:          weight 10
    //   Boss/Elite:    weight 5   (rare — single encounters on floors 7-8)
    //
    // Multi-monster encounters: Original supports up to 3 enemies per fight
    // (MONSTER_POSITION1/2/3 in battle.h). Groups of 2-3 common enemies use
    // lower weight so single-monster fights remain more common.
    // -------------------------------------------------------------------------

    val combat =
        simpleBattle("combat") {

            // ---- Party members ----
            // All 4 classes registered; runtime selects the player's chosen class.
            // Original: player.h PlayerClass enum — CLASS_DRUID=0 through CLASS_SORCERER=3
            party(characters.druid)
            party(characters.fighter)
            party(characters.monk)
            party(characters.sorcerer)

            // ---- Floor 1-2: Common encounters ----
            // Source: map.encounters.c — floor 1/2 encounter tables
            // Single kobold (weight 30 — most common encounter on floor 1)
            encounter {
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                weight(30)
            }
            // Two kobolds (weight 15 — 50% of kobold encounters, floor 2+)
            encounter {
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                weight(15)
            }
            // Three kobolds (weight 8 — rare mob group, floor 2)
            encounter {
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                slot(monsters.kobold, level = 4, tier = MonsterTier.COMMON)
                weight(8)
            }
            // Single goblin (weight 28 — floor 1-2)
            encounter {
                slot(monsters.goblin, level = 4, tier = MonsterTier.COMMON)
                weight(28)
            }
            // Two goblins (weight 14 — floor 2)
            encounter {
                slot(monsters.goblin, level = 4, tier = MonsterTier.COMMON)
                slot(monsters.goblin, level = 4, tier = MonsterTier.COMMON)
                weight(14)
            }
            // Mixed kobold + goblin group (weight 10 — floor 2)
            encounter {
                slot(monsters.kobold, level = 5, tier = MonsterTier.COMMON)
                slot(monsters.goblin, level = 5, tier = MonsterTier.COMMON)
                weight(10)
            }
            // Single zombie (weight 20 — floor 2-3)
            encounter {
                slot(monsters.zombie, level = 5, tier = MonsterTier.COMMON)
                weight(20)
            }
            // Two zombies (weight 10 — floor 3)
            encounter {
                slot(monsters.zombie, level = 6, tier = MonsterTier.COMMON)
                slot(monsters.zombie, level = 6, tier = MonsterTier.COMMON)
                weight(10)
            }
            // Zombie + goblin group (weight 8 — floor 2-3)
            encounter {
                slot(monsters.zombie, level = 5, tier = MonsterTier.COMMON)
                slot(monsters.goblin, level = 5, tier = MonsterTier.COMMON)
                weight(8)
            }

            // ---- Floor 3-4: Uncommon encounters ----
            // Source: map.encounters.c — floor 3/4 encounter tables
            // Single bugbear (weight 20 — floor 3-4)
            encounter {
                slot(monsters.bugbear, level = 12, tier = MonsterTier.UNCOMMON)
                weight(20)
            }
            // Two bugbears (weight 8 — floor 4)
            encounter {
                slot(monsters.bugbear, level = 14, tier = MonsterTier.UNCOMMON)
                slot(monsters.bugbear, level = 14, tier = MonsterTier.UNCOMMON)
                weight(8)
            }
            // Single owlbear (weight 18 — floor 3-4)
            encounter {
                slot(monsters.owlbear, level = 12, tier = MonsterTier.UNCOMMON)
                weight(18)
            }
            // Two owlbears (weight 7 — floor 4)
            encounter {
                slot(monsters.owlbear, level = 14, tier = MonsterTier.UNCOMMON)
                slot(monsters.owlbear, level = 14, tier = MonsterTier.UNCOMMON)
                weight(7)
            }
            // Single gelatinous cube (weight 15 — floor 3-4)
            encounter {
                slot(monsters.gelatinousCube, level = 12, tier = MonsterTier.UNCOMMON)
                weight(15)
            }
            // Bugbear + goblin mixed (weight 10 — floor 3)
            encounter {
                slot(monsters.bugbear, level = 10, tier = MonsterTier.UNCOMMON)
                slot(monsters.goblin, level = 8, tier = MonsterTier.COMMON)
                weight(10)
            }
            // Owlbear + zombie mixed (weight 8 — floor 3-4)
            encounter {
                slot(monsters.owlbear, level = 10, tier = MonsterTier.UNCOMMON)
                slot(monsters.zombie, level = 8, tier = MonsterTier.COMMON)
                weight(8)
            }

            // ---- Floor 5-6: Rare encounters ----
            // Source: map.encounters.c — floor 5/6 encounter tables
            // Single displacer beast (weight 12 — floor 5-6)
            encounter {
                slot(monsters.displacerBeast, level = 25, tier = MonsterTier.RARE)
                weight(12)
            }
            // Two displacer beasts (weight 5 — floor 6)
            encounter {
                slot(monsters.displacerBeast, level = 28, tier = MonsterTier.RARE)
                slot(monsters.displacerBeast, level = 28, tier = MonsterTier.RARE)
                weight(5)
            }
            // Single will-o-wisp (weight 12 — floor 5-6)
            encounter {
                slot(monsters.willOWisp, level = 25, tier = MonsterTier.RARE)
                weight(12)
            }
            // Two will-o-wisps (weight 5 — floor 6)
            encounter {
                slot(monsters.willOWisp, level = 28, tier = MonsterTier.RARE)
                slot(monsters.willOWisp, level = 28, tier = MonsterTier.RARE)
                weight(5)
            }
            // Single deathknight (weight 8 — floor 5-7)
            encounter {
                slot(monsters.deathknight, level = 28, tier = MonsterTier.RARE)
                weight(8)
            }
            // Displacer beast + will-o-wisp mixed (weight 5 — floor 6)
            encounter {
                slot(monsters.displacerBeast, level = 26, tier = MonsterTier.RARE)
                slot(monsters.willOWisp, level = 26, tier = MonsterTier.RARE)
                weight(5)
            }

            // ---- Floor 7-8: Boss/Elite encounters ----
            // Source: map.encounters.c — floor 7/8 encounter tables
            // Mind flayer — floor 7 elite (weight 5, solo)
            encounter {
                slot(monsters.mindflayer, level = 45, tier = MonsterTier.BOSS)
                weight(5)
            }
            // Mind flayer pair — floor 7 (weight 2, very rare)
            encounter {
                slot(monsters.mindflayer, level = 45, tier = MonsterTier.BOSS)
                slot(monsters.mindflayer, level = 45, tier = MonsterTier.BOSS)
                weight(2)
            }
            // Beholder — floor 8 boss (weight 4, solo)
            encounter {
                slot(monsters.beholder, level = 50, tier = MonsterTier.BOSS)
                weight(4)
            }
            // Dragon — floor 8 final boss (weight 3, solo — hardest encounter)
            encounter {
                slot(monsters.dragon, level = 55, tier = MonsterTier.BOSS)
                weight(3)
            }
            // Mind flayer + deathknight mixed (weight 3 — floor 7-8)
            encounter {
                slot(monsters.mindflayer, level = 44, tier = MonsterTier.BOSS)
                slot(monsters.deathknight, level = 35, tier = MonsterTier.RARE)
                weight(3)
            }

            // ---- Victory handler ----
            // Original: BATTLE_REWARDS → BATTLE_SUCCESS → BATTLE_LEVEL_UP → BATTLE_COMPLETE
            // `calc_monster_exp(mlevel, tier)` calculates level-relative XP modifier
            // `awardExp()`: XP applied to party; level-up logic if threshold reached
            // `awardDrops()`: apply drop table from winning encounter group
            // Scene navigation after victory is handled in BattleScene (Plan 13) via
            //   `combatIsInState(CombatStates.VICTORY, combat)` check.
            onVictory {
                // Combat engine runtime awards EXP and processes drops automatically
                // via the CombatEngineSystem onVictoryOps pipeline.
                // No additional ScriptOps needed here — scene plan handles navigation.
            }

            // ---- Defeat handler ----
            // Original: BATTLE_PLAYER_DIED → BATTLE_DIED_DELAY → death screen
            // Scene navigation to gameover is handled in BattleScene (Plan 13) via
            //   `combatIsInState(CombatStates.DEFEAT, combat)` check.
            // Empty body — all defeat handling is in battle scene plan.
            onDefeat {
                // Defeat state is handled in BattleScene frame via combatIsInState() check.
                // No ScriptOps emitted here — battle scene drives navigation to gameover.
            }
        }

    return LabyrinthCombatSystem(combat = combat)
}
