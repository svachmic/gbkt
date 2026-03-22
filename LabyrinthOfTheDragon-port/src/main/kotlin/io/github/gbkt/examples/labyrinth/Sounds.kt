/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName") // LabyrinthSounds — prefixed to avoid stdlib conflict

package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.SoundRef
import io.github.gbkt.core.dsl.soundEffect
import io.github.gbkt.core.ir.SoundPreset

/**
 * Typed container for all Labyrinth of the Dragon sound effect references.
 *
 * Returned by [GameBuilder.defineSounds] for zero-magic-string SFX access in scenes.
 *
 * ## No Background Music
 *
 * The original game is SFX-only. No music tracks are registered.
 *
 * @source sound.h — 31 `sfx_*` function declarations
 */
data class LabyrinthSounds(
    // ---- Exploration ----
    /** Descend/ascend stairs. @source sound.c:sfx_stairs() */
    val stairs: SoundRef,
    /** Walk into a wall (blocked movement). @source sound.c:sfx_wall_hit() */
    val wallHit: SoundRef,
    /** Light a sconce torch. @source sound.c:sfx_light_fire() */
    val lightFire: SoundRef,
    /** Step on a no-no/transporter square. @source sound.c:sfx_no_no_square() */
    val noNoSquare: SoundRef,
    /** Falling through a pit or trap. @source sound.c:sfx_falling() */
    val falling: SoundRef,
    // ---- Menu ----
    /** Move cursor in any menu. @source sound.c:sfx_menu_move() */
    val menuMove: SoundRef,
    /** Advance to next combat round. @source sound.c:sfx_next_round() */
    val nextRound: SoundRef,
    // ---- Chest / Door ----
    /** Open a treasure chest. @source sound.c:sfx_open_chest() */
    val openChest: SoundRef,
    /** Unlock a magic-key-locked door. @source sound.c:sfx_door_unlock() */
    val doorUnlock: SoundRef,
    /** Open a large boss door. @source sound.c:sfx_big_door_open() */
    val bigDoorOpen: SoundRef,
    // ---- Battle start / outcome ----
    /** Initiate a random encounter. @source sound.c:sfx_start_battle() */
    val startBattle: SoundRef,
    /** Player wins a battle. @source sound.c:sfx_battle_success() */
    val battleSuccess: SoundRef,
    /** Player dies in battle. @source sound.c:sfx_battle_death() */
    val battleDeath: SoundRef,
    // ---- Attacks ----
    /** Player performs a physical (melee) attack. @source sound.c:sfx_melee_attack() */
    val meleeAttack: SoundRef,
    /** Monster is defeated. @source sound.c:sfx_monster_death() */
    val monsterDeath: SoundRef,
    /** Monster performs a basic attack (variant 1). @source sound.c:sfx_monster_attack1() */
    val monsterAttack1: SoundRef,
    /** Monster performs a magic attack (variant 2). @source sound.c:sfx_monster_attack2() */
    val monsterAttack2: SoundRef,
    /** Monster lands a critical hit. @source sound.c:sfx_monster_critical() */
    val monsterCritical: SoundRef,
    /** Monster fails an action. @source sound.c:sfx_monster_fail() */
    val monsterFail: SoundRef,
    /** Player misses an attack. @source sound.c:sfx_miss() */
    val miss: SoundRef,
    // ---- Special abilities ----
    /** Fighter's Action Surge activates. @source sound.c:sfx_action_surge() */
    val actionSurge: SoundRef,
    /** Healing effect applied. @source sound.c:sfx_heal() */
    val heal: SoundRef,
    /** Major power-up applied. @source sound.c:sfx_big_powerup() */
    val bigPowerup: SoundRef,
    /** Minor power-up applied. @source sound.c:sfx_mid_powerup() */
    val midPowerup: SoundRef,
    /** Poison spray / poison status applied. @source sound.c:sfx_poison_spray() */
    val poisonSpray: SoundRef,
    /** Monk's Unarmed Strike / Flurry of Blows. @source sound.c:sfx_monk_strike() */
    val monkStrike: SoundRef,
    /** Player evades / dodges an attack. @source sound.c:sfx_evade() */
    val evade: SoundRef,
    /** Magic Missile / Frost Ray spell cast. @source sound.c:sfx_magic_missile() */
    val magicMissile: SoundRef,
    // ---- Level up ----
    /** Player gains a level. @source sound.c:sfx_level_up() */
    val levelUp: SoundRef,
    // ---- Title ----
    /** Animated fire crackling on the title screen. @source sound.c:sfx_title_fire() */
    val titleFire: SoundRef,
    /**
     * Hero selected on the hero selection screen. @source sound.c:sfx_hero_selected() — calls
     * sfx_battle_success()
     */
    val heroSelected: SoundRef,
    /**
     * "NESHacker Presents" intro jingle. @source sound.c:sfx_neshacker_presents() — calls
     * sfx_magic_missile()
     */
    val neshackerPresents: SoundRef,
    // ---- Debug ----
    /** Debug/test sound (same as falling in production). @source sound.c:sfx_test() */
    val testSfx: SoundRef,
)

/**
 * Registers all 31 Labyrinth of the Dragon sound effects into the [GameBuilder].
 *
 * Called inside the `game { }` DSL block so that all SFX are declared in game scope and become
 * available to scene frame blocks via [LabyrinthSounds] typed references.
 *
 * ## Audio System
 *
 * The original uses a timer ISR (`update_sound_isr`) to advance NRxx register sequences. In the V2
 * port, SFX are declared using `soundEffect { preset(...) }` — the preset system maps common Game
 * Boy SFX patterns to NRxx register configurations.
 *
 * ## No Background Music
 *
 * The original game is SFX-only — no music tracks are registered here.
 *
 * @source sound.c:sound_init() — channels 1, 2 & 4 routed to both SO1 & SO2 (stereo output)
 */
fun GameBuilder.defineSounds(): LabyrinthSounds {

    // ---- Exploration ----

    /** Descend/ascend stairs between floors — noise channel 4-step descending sequence. */
    val stairs by soundEffect { preset(SoundPreset.BUMP) }

    /** Walk into a wall — sharp buzz: NR10 sweep + quick envelope decay. */
    val wallHit by soundEffect { preset(SoundPreset.BUMP) }

    /** Light a sconce torch — rising noise pop: NR41/42/43/44 noise channel. */
    val lightFire by soundEffect { preset(SoundPreset.COIN) }

    /** Step on a no-no square — repeated 4-step frequency sweep on pulse channel 1. */
    val noNoSquare by soundEffect { preset(SoundPreset.BEEP) }

    /** Falling through a pit — 5-step descending tone (0xAC → 0x37) with increasing duration. */
    val falling by soundEffect { preset(SoundPreset.LOSE) }

    // ---- Menu ----

    /** Move cursor in any menu — quick 4-step ascending frequency on pulse channel 1. */
    val menuMove by soundEffect { preset(SoundPreset.BEEP) }

    /** Advance to next combat round — two-tone descending pulse, 13 frames each. */
    val nextRound by soundEffect { preset(SoundPreset.BEEP) }

    // ---- Chest / Door ----

    /** Open a treasure chest — three-note melody: NR12=envelope(14,0,1), 3-step descending. */
    val openChest by soundEffect { preset(SoundPreset.COIN) }

    /** Unlock a magic-key-locked door — two-phase noise unlock, 20 frames per phase. */
    val doorUnlock by soundEffect { preset(SoundPreset.COIN) }

    /** Open a large boss door — single loud noise burst: NR42=envelope(11,0,2). */
    val bigDoorOpen by soundEffect { preset(SoundPreset.HIT) }

    // ---- Battle start / outcome ----

    /** Initiate battle — double 4-beat noise pattern, 8-step sequence on noise channel. */
    val startBattle by soundEffect { preset(SoundPreset.HIT) }

    /** Battle victory — two-channel chord: pulse 1 + pulse 2, 0x87 trigger. */
    val battleSuccess by soundEffect { preset(SoundPreset.WIN) }

    /** Battle death — 8-note descending melody on pulse channel 1. */
    val battleDeath by soundEffect { preset(SoundPreset.LOSE) }

    // ---- Attacks ----

    /** Player melee attack — dual-channel attack: noise burst + delayed sweep pulse. */
    val meleeAttack by soundEffect { preset(SoundPreset.HIT) }

    /** Monster death — single noise burst: NR42=envelope(12,0,7), noise_freq(8,0,1). */
    val monsterDeath by soundEffect { preset(SoundPreset.EXPLODE) }

    /** Monster attack 1 — two-step sweep attack: NR10 sweep(7,0,5), 18 frames each. */
    val monsterAttack1 by soundEffect { preset(SoundPreset.HIT) }

    /** Monster attack 2 — 7-note ascending melody on pulse channel 1. */
    val monsterAttack2 by soundEffect { preset(SoundPreset.SHOOT) }

    /** Monster critical hit — single loud noise: NR42=envelope(13,0,3). */
    val monsterCritical by soundEffect { preset(SoundPreset.HIT) }

    /** Monster fail — three-note stuttering failure tone on pulse channel 1. */
    val monsterFail by soundEffect { preset(SoundPreset.BUMP) }

    /** Miss — quick two-note miss sting, NR12=envelope(12,0,1). */
    val miss by soundEffect { preset(SoundPreset.BUMP) }

    // ---- Special abilities ----

    /** Action Surge — double 4-beat noise burst with envelope swell on noise channel. */
    val actionSurge by soundEffect { preset(SoundPreset.POWERUP) }

    /** Heal — two-channel ascending melody: pulse 1 + pulse 2 staggered 30 frames. */
    val heal by soundEffect { preset(SoundPreset.POWERUP) }

    /** Big powerup — dual-channel 5+9 note ascending fanfare. */
    val bigPowerup by soundEffect { preset(SoundPreset.POWERUP) }

    /** Mid powerup — single-note rising sweep: NR10=sweep(7,0,7), NR12=envelope(13,0,2). */
    val midPowerup by soundEffect { preset(SoundPreset.POWERUP) }

    /** Poison spray — single loud noise burst: NR42=envelope(12,0,2), noise_freq(2,0,1). */
    val poisonSpray by soundEffect { preset(SoundPreset.HIT) }

    /** Monk strike — two-punch combo: immediate pulse 1 + delayed noise. */
    val monkStrike by soundEffect { preset(SoundPreset.HIT) }

    /** Evade — two-note descending noise, same register shape as stairs. */
    val evade by soundEffect { preset(SoundPreset.JUMP) }

    /** Magic missile — two-channel 4-note ascending spell, 30-frame pulse 2 offset. */
    val magicMissile by soundEffect { preset(SoundPreset.SHOOT) }

    // ---- Level up ----

    /** Level up — dual-channel 7-note ascending fanfare: pulse 1 + pulse 2 staggered 1 step. */
    val levelUp by soundEffect { preset(SoundPreset.WIN) }

    // ---- Title ----

    /** Title fire crackling — 9-step noise sequence with descending frequency at end. */
    val titleFire by soundEffect { preset(SoundPreset.BUMP) }

    /** Hero selected — same as battleSuccess (sfx_hero_selected calls sfx_battle_success). */
    val heroSelected by soundEffect { preset(SoundPreset.WIN) }

    /**
     * NESHacker presents — same as magicMissile (sfx_neshacker_presents calls sfx_magic_missile).
     */
    val neshackerPresents by soundEffect { preset(SoundPreset.SHOOT) }

    // ---- Debug ----

    /** Debug test sound — same as falling (sfx_test calls sfx_falling). */
    val testSfx by soundEffect { preset(SoundPreset.BEEP) }

    return LabyrinthSounds(
        stairs = stairs,
        wallHit = wallHit,
        lightFire = lightFire,
        noNoSquare = noNoSquare,
        falling = falling,
        menuMove = menuMove,
        nextRound = nextRound,
        openChest = openChest,
        doorUnlock = doorUnlock,
        bigDoorOpen = bigDoorOpen,
        startBattle = startBattle,
        battleSuccess = battleSuccess,
        battleDeath = battleDeath,
        meleeAttack = meleeAttack,
        monsterDeath = monsterDeath,
        monsterAttack1 = monsterAttack1,
        monsterAttack2 = monsterAttack2,
        monsterCritical = monsterCritical,
        monsterFail = monsterFail,
        miss = miss,
        actionSurge = actionSurge,
        heal = heal,
        bigPowerup = bigPowerup,
        midPowerup = midPowerup,
        poisonSpray = poisonSpray,
        monkStrike = monkStrike,
        evade = evade,
        magicMissile = magicMissile,
        levelUp = levelUp,
        titleFire = titleFire,
        heroSelected = heroSelected,
        neshackerPresents = neshackerPresents,
        testSfx = testSfx,
    )
}
