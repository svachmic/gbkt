/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites_stress

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.SpriteMode

/**
 * MetaspritesStress — synthetic codegen-verification ROM (Phase 10.1 D-06 / D-07).
 *
 * **THROWAWAY. NOT a user-facing example.** Forces composition of:
 *
 * - **CR-01:** 1 actor sprite (`player.png`) coexists with 2 metasprites in VRAM (unified loader
 *   must not collide).
 * - **CR-02:** 2 scenes (`title` + `play`) escape `BankingAnalysisPass.kt:91-95`'s single-scene
 *   HOME fast-path → real `bank1.c` is produced → per-bank include scan must import
 *   `<gbdk/metasprites.h>` for `move_metasprite_ex`.
 * - **CR-03:** 2 distinct metasprites (`elephant`, `tiger`) — symbol names must be
 *   per-metasprite-namespaced, otherwise SDCC reports duplicate symbol.
 * - **WR-05:** Play frame calls moveMetasprite on BOTH metasprites — hiwater
 *   (`__current_metasprite` / equivalent) must not reset between calls.
 * - **WR-01:** D-10 implicit scope-walk binders parameterize each metasprite with distinct
 *   user-declared variables.
 * - **WR-02:** `game.h` must extern both `sprite_elephant_frames` and `sprite_tiger_frames` so
 *   `bank1.c` sees them across the bank boundary.
 *
 * Per D-07 the ROM is allowed to be ugly: no input handling beyond `START` to navigate title →
 * play; no animation, no rotation, no positional integration. The binding integration evidence is
 * that `:buildRom` exits 0 and produces a linkable ROM.
 *
 * Per D-13 / D-13b the scoped binder DSL exception (`posX(varRef)` / `posY(varRef)` / `idx(varRef)`
 * / `rot(varRef)`) added by Plan 03 is EXERCISED here. Per `feedback_no_magic_strings.md` every
 * binder takes an `AssignableVar` so the variable name flows from the Kotlin property delegate — no
 * magic-string duplication.
 */
val metaspritesStress =
    game("MetaspritesStress") {
        config {
            // MBC5 supports up to 256 ROM banks — required because romBanks=4 exceeds
            // ROM_ONLY's 2-bank cap. The explicit 4-bank override forces the banked codegen
            // path (CR-02 forcing condition: 2 scenes → real bank1.c production).
            cartridge(Cartridge.MBC5)
            // 4 banks — multi-bank ROM forces banked codegen path even when scenes are
            // small (defensive against future BankingAnalysisPass fast-path changes).
            romBanks = 4
            // GBC_COMPATIBLE — matches sibling :metasprites example for parity; not
            // strictly required for the defects this ROM exercises.
            target(GbcTarget.GBC_COMPATIBLE)
        }

        // -----------------------------------------------------------------
        // Per-metasprite scoped variables (D-10 implicit scope-walk shape, option (a)).
        // Property-delegate names propagate into emitted C var names — no magic strings.
        // -----------------------------------------------------------------
        var elephantPosX by i16Var(1280)
        var elephantPosY by i16Var(1152)
        var elephantIdx by u8Var(0)
        var elephantRot by u8Var(0)
        var tigerPosX by i16Var(640)
        var tigerPosY by i16Var(720)
        var tigerIdx by u8Var(0)
        var tigerRot by u8Var(0)

        // -----------------------------------------------------------------
        // ACTOR sprite (CR-01 forcing condition — actor + metasprite VRAM coexistence)
        // -----------------------------------------------------------------
        val player by actor {
            position(40, 40)
            sprite(asset("sprites/player.png")) {
                size(8, 16)
                hitbox(0, 0, 8, 16)
            }
        }

        // -----------------------------------------------------------------
        // TWO metasprites (CR-03 forcing condition — distinct symbol namespacing)
        // WR-01 forcing condition — distinct posX/posY/idx/rot var-refs per metasprite
        // -----------------------------------------------------------------
        val elephant by metasprite {
            sprite(asset("sprites/elephant.png")) {
                // Same flags as :metasprites elephant — 64x240px, 5 frames at 64x48px each.
                // Reference: GBDK metasprites/Makefile: png2asset -sh 48 -spr8x8 -noflip
                mode(SpriteMode.SPR8x8)
                pivot(0, 0)
                frameSize(64, 48)
            }
            frames(5)
            posX(elephantPosX)
            posY(elephantPosY)
            idx(elephantIdx)
            rot(elephantRot)
        }
        val tiger by metasprite {
            sprite(asset("sprites/tiger.png")) {
                // tiger.png is the same dimensions as elephant.png (64x240px, intentional duplicate
                // per README). Same flags apply: -sh 48 -spr8x8 -noflip.
                mode(SpriteMode.SPR8x8)
                pivot(0, 0)
                frameSize(64, 48)
            }
            frames(5)
            posX(tigerPosX)
            posY(tigerPosY)
            idx(tigerIdx)
            rot(tigerRot)
        }

        // -----------------------------------------------------------------
        // TWO scenes (CR-02 forcing condition — escape BankingAnalysisPass single-scene
        // HOME fast-path → produces real bank1.c; play scene exercises WR-05 two-call hiwater).
        // -----------------------------------------------------------------
        val playScene =
            scene("play") {
                enter { showSprites() }
                frame {
                    // First moveMetasprite — hiwater contributes
                    moveMetasprite(elephant)
                    // Second moveMetasprite — WR-05 would surface as a visual glitch if hiwater
                    // reset between calls; the SDCC link succeeding under the composed output
                    // is the binding evidence the fix composes.
                    moveMetasprite(tiger)
                }
            }
        val titleScene =
            scene("title") {
                enter { showSprites() }
                frame { whenever(buttons.start.pressed) { navigate(playScene) } }
            }

        // Reference the actor so analysis sees it as a participant (silences any
        // unused-actor warnings without affecting codegen).
        @Suppress("UNUSED_EXPRESSION") player

        start = titleScene
    }
