/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.ir.AssignableExpr

// =============================================================================
// SCENE STATE HOLDERS
// =============================================================================
// These data classes hold references to DSL variables for use in scene logic.
// Variables are defined in LabyrinthOfTheDragon.kt and wrapped here for access.

/**
 * Battle scene state variables.
 *
 * Tracks menu navigation, battle flow state, and visual animations.
 */
data class BattleSceneState(
    // =========================================================================
    // MENU STATE
    // =========================================================================

    /** Current menu state (0=main, 1=target, 2=ability, 3=item, 4=execute, 5=enemy, 6=result) */
    val menuState: AssignableExpr,
    /** Cursor position in main menu (0-3: Attack, Ability, Item, Flee) */
    val menuCursor: AssignableExpr,
    /** Cursor position for target selection (0-2: up to 3 enemies) */
    val targetCursor: AssignableExpr,
    /** Cursor position in ability list (0-5: 6 abilities per class) */
    val abilityCursor: AssignableExpr,
    /** Cursor position in item list (0-7: 8 item slots) */
    val itemCursor: AssignableExpr,
    /** Current turn phase (0=player, 1=enemy) */
    val turnPhase: AssignableExpr,
    /** Animation/delay timer for battle effects */
    val animTimer: AssignableExpr,

    // =========================================================================
    // HP BAR ANIMATION (for up to 3 monsters)
    // Original: smooth tween over ~16 frames with exponential decay
    // =========================================================================

    /** Monster 1 currently displayed HP (animates toward target) */
    val monster1DisplayHP: AssignableExpr,
    /** Monster 1 target HP (actual current HP) */
    val monster1TargetHP: AssignableExpr,
    /** Monster 2 currently displayed HP */
    val monster2DisplayHP: AssignableExpr,
    /** Monster 2 target HP */
    val monster2TargetHP: AssignableExpr,
    /** Monster 3 currently displayed HP */
    val monster3DisplayHP: AssignableExpr,
    /** Monster 3 target HP */
    val monster3TargetHP: AssignableExpr,

    // =========================================================================
    // DEATH ANIMATION STATE
    // Original: 23-frame delay + 6-step palette fade (5 frames each)
    // =========================================================================

    /** Death animation state (0=none, 1=delay, 2=fading) */
    val deathAnimState: AssignableExpr,
    /** Which monster is dying (0-2) */
    val deathAnimMonster: AssignableExpr,
    /** Current fade step (0-5) */
    val deathAnimStep: AssignableExpr,
    /** Frame counter within current step */
    val deathAnimTimer: AssignableExpr,

    // =========================================================================
    // SCREEN SHAKE
    // Original: pattern +6, -6, +4, -4, 0 pixels, 3 frames each
    // =========================================================================

    /** Screen shake step (0=none, 1-5=shaking) */
    val shakeStep: AssignableExpr,
    /** Frame counter within shake step */
    val shakeTimer: AssignableExpr,

    // =========================================================================
    // ACTION RESULT MESSAGE
    // =========================================================================

    /** Last action damage dealt (for display) */
    val lastDamage: AssignableExpr,
    /** Last action result type (0=none, 1=hit, 2=crit, 3=miss, 4=heal) */
    val lastActionResult: AssignableExpr,
    /** Message display timer */
    val messageTimer: AssignableExpr,

    // =========================================================================
    // STATUS EFFECT ICONS
    // Each combatant can have up to 4 active status effects displayed
    // Bitmask format: bit 0-18 = effect active, displayed as up to 4 icons
    // =========================================================================

    /** Player active status effects bitmask */
    val playerStatusEffects: AssignableExpr,
    /** Monster 1 active status effects bitmask */
    val monster1StatusEffects: AssignableExpr,
    /** Monster 2 active status effects bitmask */
    val monster2StatusEffects: AssignableExpr,
    /** Monster 3 active status effects bitmask */
    val monster3StatusEffects: AssignableExpr,
)

/**
 * Credits Scene State
 *
 * Tracks the state machine for credits display with fade transitions.
 */
data class CreditsSceneState(
    /** Current state: 0=FADE_IN, 1=HOLD, 2=FADE_OUT */
    val creditsState: AssignableExpr,
    /** Current page index (0-7) */
    val pageIndex: AssignableExpr,
    /** Frame counter for timing */
    val frameCounter: AssignableExpr,
)

/**
 * Settings Scene State
 *
 * Tracks the settings menu state and audio configuration.
 */
data class SettingsSceneState(
    /** Master volume level (0-7, Game Boy hardware levels) */
    val masterVolume: AssignableExpr,
    /** SFX enabled flag (0=off, 1=on) */
    val sfxEnabled: AssignableExpr,
    /** Menu cursor position (0=Volume, 1=SFX, 2=Back) */
    val settingsCursor: AssignableExpr,
)
