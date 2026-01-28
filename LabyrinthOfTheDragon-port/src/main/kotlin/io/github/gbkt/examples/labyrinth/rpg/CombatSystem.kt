/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.rpg

import io.github.gbkt.core.print
import io.github.gbkt.core.rpg.BattleSystem
import io.github.gbkt.core.rpg.CombatFormulas
import io.github.gbkt.core.rpg.battleSystem
import io.github.gbkt.core.rpg.combatFormulas
import io.github.gbkt.core.screen
import io.github.gbkt.examples.labyrinth.Sounds

// =============================================================================
// LABYRINTH OF THE DRAGON - COMBAT SYSTEM
// =============================================================================
// Turn-based combat configuration matching the original game's battle flow.
//
// Battle Flow:
// 1. INIT - Set up battle, load enemy sprites
// 2. INTRO - Display encounter message
// 3. PLAYER_MENU - Show Fight/Ability/Item/Flee options
// 4. TARGET_SELECT - Select enemy target
// 5. ABILITY_SELECT - Browse abilities submenu
// 6. ITEM_SELECT - Browse inventory
// 7. ACTION_EXECUTE - Run the selected action
// 8. ENEMY_THINK - AI selects monster action
// 9. TURN_END - Check for victory/defeat
// 10. VICTORY/DEFEAT/FLED - End states
//
// The original LabyrinthOfTheDragon uses a simple 1-on-1 to 1-on-3 battle system
// with a single party member (the chosen character class).

/**
 * Combat system configuration for Labyrinth of the Dragon.
 *
 * Creates the battle system with proper turn flow, flee mechanics, and visual presentation matching
 * the original game.
 *
 * @param sounds Sound effects for combat feedback
 */
fun createCombatSystem(sounds: Sounds): BattleSystem =
    battleSystem("combat") {
        // Single character party vs up to 3 enemies
        maxPartySize(1)
        maxEnemies(3)

        // Flee mechanics - base 40% chance + 3% per agility point
        fleeMechanics(baseChance = 40, perAgility = 3)

        // =========================================================================
        // BATTLE STATE CALLBACKS
        // =========================================================================

        onInit {
            // Battle initialization
            screen.clear()
        }

        onIntro {
            // Show "Enemies appeared!" message
            print("MONSTER ATTACK!") at (4 to 1)
        }

        onPlayerMenu {
            // Main battle menu
            print("[A]TTACK") at (1 to 13)
            print("[B]ILITY") at (11 to 13)
            print("[START]ITEM") at (1 to 15)
            print("[SEL]FLEE") at (11 to 15)
        }

        onTargetSelect {
            // Target selection cursor
            print("SELECT TARGET") at (3 to 11)
        }

        onAbilitySelect {
            // Ability submenu
            print("ABILITIES") at (5 to 1)
        }

        onItemSelect {
            // Item submenu
            print("ITEMS") at (7 to 1)
        }

        onActionExecute {
            // Action animation/execution
        }

        onShowResult {
            // Show damage numbers, messages
        }

        onApplyResult {
            // Apply damage, status effects
        }

        onTurnEnd {
            // End of turn processing
            // Status effect ticks handled automatically
        }

        // =========================================================================
        // BATTLE OUTCOME HANDLERS
        // =========================================================================

        onVictory {
            // Award experience, items, gold
            screen.clear()
            print("VICTORY!") at (6 to 4)
            print("") at (0 to 8)
            print("EXP GAINED!") at (4 to 10)
            // Transition back to gameplay after delay
        }

        onDefeat {
            // Player lost - game over
            screen.clear()
            print("DEFEAT...") at (5 to 8)
        }

        onFlee {
            // Successful escape
            screen.clear()
            print("ESCAPED!") at (6 to 8)
            // Return to gameplay
        }

        // =========================================================================
        // PRESENTATION SETTINGS
        // =========================================================================

        presentation {
            // Enable damage numbers floating up
            damageNumbers(enabled = true, speed = 1, duration = 30)

            // Screen shake on hit (subtle)
            screenShakeOnHit(intensity = 2, duration = 4)

            // Stronger shake on critical hits
            screenShakeOnCrit(intensity = 4, duration = 8)

            // Flash screen on critical
            flashOnCrit(duration = 4)

            // Enable battle messages
            actionMessages(true)
            critMessages(true)
            defeatMessages(true)

            // Message display time
            messageDisplayDuration(60)

            // Event callbacks for sound effects
            onAttack { sounds.attack.play() }

            onDamage { sounds.damage.play() }

            onHeal { sounds.heal.play() }

            onDefeat { sounds.defeat.play() }

            onCrit { sounds.critical.play() }

            onMiss { sounds.miss.play() }
        }
    }

/** Battle menu option indices. */
object BattleMenuOption {
    const val ATTACK = 0
    const val ABILITY = 1
    const val ITEM = 2
    const val FLEE = 3
}

/** Maximum abilities per character class. */
const val MAX_ABILITIES_PER_CLASS = 6

/** Maximum items in inventory. */
const val MAX_INVENTORY_SLOTS = 8

// =============================================================================
// COMBAT FORMULAS - Hit/Crit/Damage calculation configuration
// =============================================================================
// Matching the original game's mechanics:
// - Hit chance: Percentage-based with ATK-DEF modifier (20-95% range)
// - Critical: ~12% chance (d16 >= 14), 1.5x damage
// - Damage variance: ±15% using multiplier table
// - Fumble: Low roll causes miss (threshold ~6%, or roll <= 1 on d16)

/**
 * Creates combat formulas matching the original Labyrinth of the Dragon game.
 *
 * Based on analysis of the original game's combat code:
 * - Hit chance uses a lookup table based on ATK-DEF difference
 * - Critical hits occur on high rolls (d16 >= 14, about 12.5%)
 * - Damage varies using a 16-element multiplier table (roughly 75-125%)
 * - Fumbles can occur on very low rolls
 */
fun createCombatFormulas(): CombatFormulas = combatFormulas {
    // Hit formula: Percentage-based matching original's hit_chance_table
    // Base 50% hit chance, ±3% per point of ATK-DEF difference
    // Clamped to 20% minimum, 95% maximum
    percentageHitChance(baseChance = 50, minChance = 20, maxChance = 95, perDiff = 3)

    // Critical hits: ~12.5% chance (d16 roll >= 14)
    // Matches original's damage_roll_critical check
    criticalOnHighRoll(threshold = 14, dieSize = 16)
    criticalMultiplier(150) // 1.5x damage on crit

    // Damage variance: ±15% using multiplier table
    // Simulates original's damage_roll_modifier[16] table
    damageMultiplierRange(min = 85, max = 115)

    // Fumble on very low rolls (roll <= 1 on d16 = ~6%)
    enableFumble(threshold = 1)
}
