/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.SoundEffect
import io.github.gbkt.core.SoundPreset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.builder.soundEffect

// =============================================================================
// LABYRINTH OF THE DRAGON - AUDIO
// =============================================================================
// Sound effects and music definitions for the game.
//
// Game Boy has 4 audio channels:
// - CH1 (Pulse1): Used for melodies, sweeps
// - CH2 (Pulse2): Supporting melodies
// - CH3 (Wave): Bass, custom waveforms
// - CH4 (Noise): Drums, explosions, hits

/** Sound effects for Labyrinth of the Dragon. */
class Sounds(builder: GameBuilder) {

    // -------------------------------------------------------------------------
    // UI SOUNDS
    // -------------------------------------------------------------------------

    /** Menu cursor movement */
    val menuMove: SoundEffect = builder.soundEffect("menu_move") { preset = SoundPreset.TICK }

    /** Menu selection confirm */
    val menuSelect: SoundEffect = builder.soundEffect("menu_select") { preset = SoundPreset.SELECT }

    /** Cancel/back sound */
    val menuCancel: SoundEffect = builder.soundEffect("menu_cancel") { preset = SoundPreset.BEEP }

    /** Pause game */
    val pause: SoundEffect = builder.soundEffect("pause") { preset = SoundPreset.PAUSE }

    // -------------------------------------------------------------------------
    // BATTLE SOUNDS
    // -------------------------------------------------------------------------

    /** Physical attack hit */
    val attack: SoundEffect = builder.soundEffect("attack") { preset = SoundPreset.HIT }

    /** Enemy/player takes damage */
    val damage: SoundEffect = builder.soundEffect("damage") { preset = SoundPreset.HIT }

    /** Critical hit */
    val critical: SoundEffect = builder.soundEffect("critical") { preset = SoundPreset.POWERUP }

    /** Attack missed */
    val miss: SoundEffect = builder.soundEffect("miss") { preset = SoundPreset.BEEP }

    /** Magic spell cast */
    val magic: SoundEffect = builder.soundEffect("magic") { preset = SoundPreset.LASER }

    /** Healing effect */
    val heal: SoundEffect = builder.soundEffect("heal") { preset = SoundPreset.POWERUP }

    /** Enemy/player defeated */
    val defeat: SoundEffect = builder.soundEffect("defeat") { preset = SoundPreset.DEATH }

    /** Victory fanfare (short) */
    val victory: SoundEffect = builder.soundEffect("victory") { preset = SoundPreset.COIN }

    /** Flee successful */
    val flee: SoundEffect = builder.soundEffect("flee") { preset = SoundPreset.JUMP }

    /** Attack evaded */
    val evade: SoundEffect = builder.soundEffect("evade") { preset = SoundPreset.JUMP }

    /** Poison/acid attack */
    val poisonSpray: SoundEffect =
        builder.soundEffect("poison_spray") { preset = SoundPreset.LASER }

    /** Monk martial arts strike */
    val monkStrike: SoundEffect = builder.soundEffect("monk_strike") { preset = SoundPreset.HIT }

    /** Action surge / buff activation */
    val actionSurge: SoundEffect =
        builder.soundEffect("action_surge") { preset = SoundPreset.POWERUP }

    // -------------------------------------------------------------------------
    // EXPLORATION SOUNDS
    // -------------------------------------------------------------------------

    /** Footstep/movement */
    val step: SoundEffect = builder.soundEffect("step") { preset = SoundPreset.TICK }

    /** Bump into wall */
    val bump: SoundEffect = builder.soundEffect("bump") { preset = SoundPreset.LAND }

    /** Open chest */
    val chestOpen: SoundEffect = builder.soundEffect("chest_open") { preset = SoundPreset.COIN }

    /** Collect item */
    val itemGet: SoundEffect = builder.soundEffect("item_get") { preset = SoundPreset.COIN }

    /** Door open */
    val doorOpen: SoundEffect = builder.soundEffect("door_open") { preset = SoundPreset.SELECT }

    /** Use key */
    val keyUse: SoundEffect = builder.soundEffect("key_use") { preset = SoundPreset.SELECT }

    /** Torch refill */
    val torchRefill: SoundEffect =
        builder.soundEffect("torch_refill") { preset = SoundPreset.POWERUP }

    /** Random encounter triggered */
    val encounter: SoundEffect = builder.soundEffect("encounter") { preset = SoundPreset.EXPLOSION }

    /** Level up */
    val levelUp: SoundEffect = builder.soundEffect("level_up") { preset = SoundPreset.POWERUP }

    /** Save game */
    val saveGame: SoundEffect = builder.soundEffect("save_game") { preset = SoundPreset.SELECT }

    /** Stairs (ascending/descending floors) */
    val stairs: SoundEffect = builder.soundEffect("stairs") { preset = SoundPreset.SELECT }

    /** Falling (trap/pit) */
    val falling: SoundEffect = builder.soundEffect("falling") { preset = SoundPreset.DEATH }
}

// Note: Music tracks would be defined similarly:
// val dungeonMusic = builder.music("dungeon.uge")
// val battleMusic = builder.music("battle.uge")
// val bossMusic = builder.music("boss.uge")
