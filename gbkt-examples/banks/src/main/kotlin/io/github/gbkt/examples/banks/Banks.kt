/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("UNUSED_VARIABLE") // delegate-bound properties are intentionally never read

package io.github.gbkt.examples.banks

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.core.ir.Cartridge

/**
 * Banks — GBDK banks reference port.
 *
 * Demonstrates: multi-bank ROM (MBC5_RAM_BATTERY), BANKED calling convention via cross-bank scene
 * navigation, banked zone tilemap load via SWITCH_ROM-from-HOME wrapper, SRAM persistence via
 * SaveDataBuilder.
 *
 * See `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md` for full design rationale
 * (decisions D-01 through D-20).
 *
 * Substrate (D-01): 3 small distinct scenes (title / play / pause) + 1 banked zone (play_zone) + 1
 * SaveDataBuilder slot. No actors, no sound effects, no HUD, no exploration system, no
 * RPG/platformer genre constructs — Banks.kt uses ONLY `io.github.gbkt.core.dsl.*` so its codegen
 * surface is a clean substrate the four UAT anchors verify against. Manual-banking DSL is
 * permanently out of scope per REQUIREMENTS.md.
 */
val banks =
    game("Banks") {
        config {
            cartridge(Cartridge.MBC5_RAM_BATTERY)
            ramBanks(2)
        }

        // Non-transient u8; written by SaveDataBuilder into SRAM slot offset 0 per
        // RESEARCH §SaveDataBuilder SRAM Path. Saved variables are flattened in declaration
        // order; with one u8 + sentinel byte, slotSize = 2.
        var saveFlag by u8Var(0)

        // 2 slots × (1 byte saveFlag + 1 sentinel byte) = 4 bytes total SRAM footprint.
        //
        // WARNING: the save-trigger call in the play frame below requires the codegen
        // fix in Plan 11-10 (adds `trigger_saves` stub in GBDKSystemVisitor.visitSaveSystem).
        // Until then, generateC succeeds but the linker reports `undefined identifier
        // 'trigger_saves'` — that's the named bug per RESEARCH §Pitfall 4.
        //
        // Phase 13.2 Req #12 (D-03): per-site @Suppress removed; file-level @file:Suppress covers
        // all delegate-bound properties.
        val saves by saveData { slots(2) }

        // Zone declared at game scope (matches Dungeon.kt:87 pattern). allocateZoneBanks
        // places the tilemap const arrays in bank 2 per RESEARCH §BankingAnalysisPass; the
        // HOME-bank `_bkg_tiles_load_banked` wrapper (Plan 07.4-30) calls SWITCH_ROM(2),
        // set_bkg_tiles(...), SWITCH_ROM(1) on scene-enter.
        //
        // 13.4 D-06: drop explicit `size(20, 18)` — tileset-only zone (no tilemap()) falls
        // through to the 20×18 fallback in resolveZoneSize, emitting a byte-identical tilemap.
        val playZone by zone { tileset(asset("tiles/checker.png")) }

        // Play scene forward ref — captured as a SceneRef so pauseScene can navigate(playScene)
        // without a string literal. The play scene is declared below (after pauseScene, to keep
        // the original declaration order), but the SceneRef is registered at game build() time
        // so the ref is valid even though playScene is Kotlin-val-captured after the pauseScene.
        //
        // 13.4 D-07: navigate(ref) form; string "play" removed.
        val playSceneRef = sceneRef("play")

        // Pause scene defined first because the `play` scene navigates to it by SceneRef
        // below. navigate(playSceneRef) uses the ref form (D-07).
        val pauseScene =
            scene("pause") {
                enter { clear() }
                frame { runIf(buttons.start.pressed) { navigate(playSceneRef) } }
            }

        // Play scene — UAT anchor 4 save trigger (Select) + cross-bank navigation to pause
        // (Start). The play scene is the multi-bank-trampoline target from the title scene.
        //
        // Req #15 (Phase 13.5): the framework auto-emits play_exit BANKED for MBC games
        // (MBC5_RAM_BATTERY, maxRomBanks=256 → isMbcGame true). No explicit exit block needed.
        val playScene =
            scene("play") {
                // SEED-014 (Phase 11.1 D-01): scene-to-zone binder DSL — Banks's play scene loads
                // playZone's tileset on enter via _bkg_tiles_load_banked.
                zone(playZone)
                enter { showSprites() }
                frame {
                    runIf(buttons.select.pressed) { triggerSystem(saves) }
                    runIf(buttons.start.pressed) { navigate(pauseScene) }
                }
            }

        // Title scene — UAT anchor 1 trigger (Start navigates HOME → bank-1 BANKED play
        // scene). 13.4 D-07: navigate(ref) form.
        val titleScene =
            scene("title") {
                enter { clear() }
                frame { runIf(buttons.start.pressed) { navigate(playScene) } }
            }

        // 13.4 D-07: SceneRef migration target from Plan 13.4-03.
        start = titleScene
    }
