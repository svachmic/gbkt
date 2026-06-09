/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("UNUSED_VARIABLE") // delegate-bound properties are intentionally never read
package io.github.gbkt.examples.platformer_template

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.SpriteMode
import io.github.gbkt.genre.platformer.dsl.levelCardScene
import io.github.gbkt.genre.platformer.dsl.platformerCamera
import io.github.gbkt.genre.platformer.dsl.platformerInput
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import io.github.gbkt.genre.platformer.dsl.tilemapCollision

/**
 * PlatformerTemplate — Phase 12 reference port of GBDK's `platformer_template` cross-platform
 * example.
 *
 * Demonstrates the integrated platformer codegen surface: tilemap-collision (D-12), horizontal
 * scroll codegen (D-13), variable-height jump via `jumpHold` (D-14), multi-tileset bank allocation
 * (D-15), 6-frame metasprite with hflip via `facingRot` (D-04), banked title + NextLevel cards
 * (D-02 — wired by Plan 12-17), and a 3-level substrate with level-switch.
 *
 * This file is the user-facing DSL composition. It drives all 5 UAT anchors (per 12-CONTEXT.md
 * §D-08):
 *
 * 1. Title → gameplay transition
 * 2. Tilemap collision (jump + land on solid)
 * 3. Horizontal scroll
 * 4. Multi-frame metasprite walk animation + hflip
 * 5. Level-switch via NextLevel card
 *
 * Wave-8 (Plan 12-16) authors zones + game-level physics + 6-frame metasprite + scene placeholders.
 * Wave-8 follow-up plans add:
 *   - Plan 12-17: banked title + NextLevel card scenes; level-switch wiring.
 *   - Plan 12-18: first `:buildRom` attempt (human checkpoint).
 *
 * Substrate (D-02, D-claude-1):
 *   - 3 zones: `world1Area1Zone`, `world1Area2Zone`, `world2Area1Zone` (names derived from the
 *     Kotlin property delegate per `feedback_no_magic_strings.md`).
 *   - World 1 zones share `world1-tileset.png` (shared-tileset duplication GAP is
 *     intentional for Phase 12 — see SEED-PHASE-12-SHARED-TILESET for the Phase 13 dedup follow-up).
 *   - World 2 has its own tileset and a per-level `platformerPhysics` override (D-12).
 *   - 2 scenes today (title + gameplay) — `nextLevelScene` added in Plan 12-17.
 */
val platformerTemplate =
    game("PlatformerTemplate") {
        // -----------------------------------------------------------------
        // Cartridge config (D-claude-3, D-claude-4)
        // -----------------------------------------------------------------
        // D-claude-3: MBC1. Reference uses minimal `0x01` (MBC1 without RAM) and Phase
        // 12 needs ≥ 3 ROM banks (HOME + scenes + zone banks 2..N for 3 tilemaps + 2 tilesets +
        // banked title + NextLevel card). romBanks is omitted — auto-derived by BankingAnalysisPass
        // (D-05). If the derivation undersizes and buildRom fails, add back `romBanks = 8` as a
        // D-05 override (Plan 13.1-10 terminal smoke sweep confirms).
        //
        // D-claude-4: GBC_COMPATIBLE — runs on both DMG and GBC; palette load is conditional on
        // CGB_TYPE at runtime per the reference's _cpu == CGB_TYPE detect. Substrate does NOT need
        // CGB-only features, so COMPATIBLE is the right floor.
        config {
            cartridge(Cartridge.MBC1)
            target(GbcTarget.GBC_COMPATIBLE)
        }

        // -----------------------------------------------------------------
        // Game-level platformer physics (D-12 + D-14 — game defaults)
        // -----------------------------------------------------------------
        // Values transcribed from reference's `platformer_template/src/player.c` lines 14-17:
        //   gravity = 2; jump force = 8; terminal velocity = 12.
        //
        // `solidThreshold(17)` (D-12): tiles with index < 17 are walkable; ≥ 17 are solid in the
        // world1 tileset. `IsTileSolid()` codegen (Plan 12-11) reads this as the default; world 2's
        // zone block overrides it (per-level shadow, D-12 spec).
        //
        // `jumpHold(20)` (D-14): variable-height jump — while A/Up held and timer > 0, gravity is
        // suppressed for up to 20 frames. Lowered by Plan 12-13's PlatformerVisitor extension.
        platformerPhysics {
            gravity(2)
            jumpForce(8)
            terminalVelocity(12)
            jumpHold(20)
            solidThreshold(17)
        }

        // -----------------------------------------------------------------
        // Platformer camera (D-13: column-by-column horizontal scroll)
        // -----------------------------------------------------------------
        // Gating: SMOOTH_FOLLOW + HORIZONTAL is required for Plan 12-11's column-scroll codegen
        // path to fire. The reference's `camera.c` UpdateCamera() implements a half-screen-trigger
        // smooth follow — the gbkt SMOOTH_FOLLOW mode lowers to the same pattern (Plan 12-11
        // visitCamera). `deadZone(64, 0)` approximates the reference's 80-px half-screen threshold
        // and will be tuned in Plan 12-18 if scroll feels off under UAT.
        platformerCamera {
            smoothFollow()
            horizontal()
            deadZone(64, 0)
        }

        // -----------------------------------------------------------------
        // Player facing + animation state (D-04 — 6-frame metasprite + hflip)
        // -----------------------------------------------------------------
        // `facingRot`: encodes facing direction via MetaspriteVisitor's rot >> 2 → flipX path
        // (Phase 10.1-05 namespacing fix). rot=0 ⇒ no flip (right-facing); rot=3 ⇒ flipX (left-
        // facing). Wired into the metasprite via the rot(...) binder when Plan 12-17 lights up the
        // moveMetasprite emission.
        //
        // `walkFrameIdx`: cycles 0..2 (walk1 / walk2 / walk3) and switches to 3 (idle) when not
        // walking; jump frames (4, 5) are selected by vertical velocity (Plan 12-18).
        //
        // `threeFrameCounter`: counts up to 3 → resets `walkFrameIdx`, mirroring the reference's
        // `threeFrameCounter` walking-cadence pattern (player.c).
        //
        // Pitfall 6 (Phase 10 PATTERNS.md): `u8Var` is required because the wrap comparisons use
        // unsigned semantics — `i8Var` would silently break for values ≥ 128.
        var facingRot by u8Var(0)
        var walkFrameIdx by u8Var(0)
        var threeFrameCounter by u8Var(0)

        // -----------------------------------------------------------------
        // Player state vars (Phase 12.1-05 — Defects 4 + 5 closure)
        // -----------------------------------------------------------------
        // Single declaration site for the user's player-physics + render symbol
        // contract, per feedback_no_magic_strings.md. Property names flow through
        // `AssignableVar.name` (VariableBuilders.kt:357 — `val name = property.name`)
        // to the C global `_<propertyName>`, and are picked up by:
        //   - MetaspriteBuilder.posX/posY → MetaspriteIR.posXVarName / posYVarName →
        //     MetaspriteVisitor.lowerMoveMetasprite emits `_playerX/_playerY` in the
        //     `move_metasprite_ex()` call (closes Defect 5).
        //   - The new `tilemapCollision { }` block (below) → GenericSystem config
        //     keys posXVar / posYVar / vxVar / vyVar / groundedVar → PlatformerVisitor
        //     (Plan 12.1-06) rewrites the tilemap-physics path to emit `_playerX`
        //     instead of the legacy `_player_x` magic string (closes Defect 4).
        //
        // RESEARCH §D-claude-1 — variable sizing:
        //   - `playerX` / `playerY` are i16FixedVar (12.4 fixed-point; construct in pixels).
        //     The metasprite render extracts pixel coords via .toPixel() internally.
        //   - `playerVx` is i8Var (horizontal velocity fits INT8 range).
        //   - `playerVy` is i16Var (must hold the -800 jump-init value from
        //     the reference player.c's jump magnitude).
        //   - `grounded` is u8Var — RESEARCH §D-claude-1 static-lock evidence:
        //     `PlatformerVisitor.kt:150` declares `_plat_grounded` (with `_plat_`
        //     prefix) but bare `_grounded` is referenced at lines 610/631/672/918
        //     WITHOUT a `CVarDecl(name = "_grounded", ...)` anywhere. This is the
        //     Defect 4 symptom; the fix is to provide the symbol via user DSL.
        //
        // Initial values use the same starting position as the reference
        // (`platformer_template/src/player.c` initial: posX = 80, posY = 72).
        var playerX by i16FixedVar(80)
        var playerY by i16FixedVar(72)
        var playerVx by i16Var(0)
        var playerVy by i16Var(0)
        var grounded by u8Var(0)

        // -----------------------------------------------------------------
        // Tilemap-collision substrate (Phase 12.1-05 — Defect 4 wiring)
        // -----------------------------------------------------------------
        // Binds the player-state vars above into the GenericSystem config that
        // PlatformerVisitor (Plan 12.1-06) reads at codegen time to emit
        // `_playerX/_playerY/_playerVx/_playerVy/_grounded` references in the
        // tilemap-physics path.
        //
        // `solidThreshold(17)` mirrors the value set on `platformerPhysics` above;
        // the values can coexist (Path A still fires for back-compat, Path C
        // fires for this system — see GBDKPipeline.gameUsesTilemapCollision).
        //
        // `hitbox(0, 0, 8, 24)` matches the reference's 1-tile-wide, 3-tile-tall
        // player AABB used by the 5-point bounding-box probe (Phase 12 D-12b).
        tilemapCollision {
            position(playerX, playerY)
            velocity(playerVx, playerVy)
            grounded(grounded)
            hitbox(0, 0, 8, 24)
            solidThreshold(17)
        }

        // -----------------------------------------------------------------
        // Platformer input (Phase 12.3 R1 / R5 — auto-emitted input + walk-cycle)
        // -----------------------------------------------------------------
        // Registers `GenericSystem(type="platformer_input")` so PlatformerVisitor
        // (Plan 12.3-02) auto-emits the dpad → playerVx velocity wiring with
        // ground-friction deceleration on release, AND (Plan 12.3-08) auto-emits
        // the 3-frame walk-cycle counter → walkFrameIdx advance during horizontal
        // motion. Both emissions are GATED on this block being present plus the
        // AssignableVar binders below being set (D-03 skip-when-unset contract).
        //
        // The two `AssignableVar` binders capture property names via Kotlin
        // property-delegate reflection (feedback_no_magic_strings.md):
        //   - `walkFrameIdx(walkFrameIdx)` → GenericSystem.config["walkFrameIdxVar"]
        //     = "walkFrameIdx" → C symbol `_walkFrameIdx` (drives the metasprite
        //     render switch at moveMetasprite render time).
        //   - `threeFrameCounter(threeFrameCounter)` → GenericSystem.config
        //     ["threeFrameCounterVar"] = "threeFrameCounter" → C symbol
        //     `_threeFrameCounter` (Plan 12.3-08's cycle accumulator).
        //
        // Numeric setters omitted → defaults apply from PlatformerInputConfig
        // (D-01a — match reference `player.c`):
        //   walkSpeed=128, friction=8, airFriction=0, walkFrameCount=3, cyclePeriod=6.
        platformerInput {
            walkFrameIdx(walkFrameIdx)
            threeFrameCounter(threeFrameCounter)
        }

        // -----------------------------------------------------------------
        // Level-state vars (D-02 + D-claude-6 — D-08 anchor 5 substrate)
        // -----------------------------------------------------------------
        // NOTE: `_current_level` + `_next_level` are PIPELINE-EMITTED globals (Plan 12-17 Task 2 —
        // gated on `gameUsesTilemapCollision`), NOT user-DSL u8Var declarations. They join the
        // existing pipeline-emitted tilemap-collision globals (`_current_level_map`,
        // `_current_level_width_in_tiles`, `_current_level_height`,
        // `_current_level_non_solid_tile_count`, `_current_area_bank`) declared by
        // `buildTilemapCollisionGlobals` (Plan 12-08).
        //
        // Why pipeline-emitted not DSL-emitted: gbkt's u8Var delegate produces `_<camelCaseName>`
        // C globals (e.g. `var nextLevel by u8Var(0)` → `_nextLevel`), but
        // PlatformerVisitor.kt:802 already emits `CUnaryExpr("++", CVar("_next_level"))`
        // (snake_case, matching the existing pipeline naming convention for tilemap-collision
        // globals like `_current_level_map`). To preserve a single canonical name set without
        // splitting into 4 redundant variables (2 DSL + 2 pipeline), Plan 12-17 routes the
        // declarations to the pipeline. The visitor's `_next_level++` increment + the main()
        // guard `_next_level != _current_level` both reference the pipeline-declared globals.
        //
        // User-DSL has no reason to read or write these directly — they are codegen-internal
        // state (analogous to `_current_tileset_id`, which is also pipeline-emitted, not
        // user-DSL).

        // -----------------------------------------------------------------
        // 3 zones (D-02 substrate — 3-level platformer)
        // -----------------------------------------------------------------
        // D-claude-1: zone IDs match the Kotlin property name exactly so the metadata manifest
        // (Plan 12-15 ConvertZoneTilesetsTask) emits stable per-zone tileset filenames matching
        // the substrate documented in `res/README.md`. The `zone(id, block)` single-string-arg
        // form is the current DSL convention (see Banks.kt); a `by zone { }` delegate is a Phase
        // 13 cleanup — see SUMMARY §Deviations.
        //
        // World 1 zones share `world1-tileset.png` (D-15 SEED — duplication intentional for Phase
        // 12; tracked by SEED-PHASE-12-SHARED-TILESET for Phase 13 dedup).
        //
        // World 2 zone declares a per-level `platformerPhysics` override (D-12) — `gravity(3)` is
        // heavier; `solidThreshold(68)` reflects the world 2 tileset's different solid-tile range.
        // Fields NOT set inherit from the game-level defaults above (Plan 12-07 shadow semantics).
        // PHASE-12 FRAMEWORK GAP: `by zone { }` delegate is not yet implemented in gbkt-lang
        // (no ZoneDelegate exists). The `zone(id, block)` single-string-arg form below mirrors the
        // existing convention in Banks.kt — but it duplicates the property name as a magic string,
        // which is the anti-pattern that `feedback_no_magic_strings.md` warns against. Tracked as a
        // Phase 13 cleanup (analogous to `MetaspriteDelegate` in MetaspriteBuilder.kt). Plan 12-16
        // documents this deviation in 12-16-SUMMARY.md.
        // Phase 12.6 D-08 — per-zone spawn declarations on all three gameplay zones
        // (DEFECT-2 closure). X=40 pixels mirrors the reference SetupPlayer() at player.c:94
        // verbatim; Y=120 pixels is the gbkt deviation locked in
        // evidence/reference-toolchain-notes.md § "Locked recommendation for D-08" (places
        // the player on the visible ground row rather than relying on a fall-onto-floor
        // startup the gbkt port does not yet replicate). DSL accepts pixels; codegen applies
        // the <<4 subpixel shift at C emission time (Plan 12.6-05
        // buildLevelSpawnTablesIfNeeded). Without these, the spawn-table emission falls back
        // to the default (16, 120) and emits a build-time WARNING.
        // 13.4 D-05: world zones migrate to `by zone` delegate; tilemap() presence → derive 60×32
        // from the tilemap PNG (480×256px = 60×32 tiles). NO explicit size() call — resolveZoneSize
        // derives dims from the tilemap PNG (D-03: tilemap() present → derive from tilemap PNG).
        // Previously truncated to 32×32 (magic default) — 60×32 derivation is a correctness fix
        // that grows the emitted tilemap array (60×32=1920 bytes vs 32×32=1024 bytes; 6120 total
        // << 14336 bank threshold per RESEARCH §D-05 Bank Overflow Verification).
        val world1Area1Zone by zone {
            tileset(asset("graphics/world1-tileset.png"))
            tilemap(asset("graphics/world1-area1.png"))   // Phase 12.2 D-01 two-invocation path
            spawn(40u, 120u)
        }
        val world1Area2Zone by zone {
            tileset(asset("graphics/world1-tileset.png"))
            tilemap(asset("graphics/world1-area2.png"))   // Phase 12.2 D-01 two-invocation path
            spawn(40u, 120u)
        }
        val world2Area1Zone by zone {
            tileset(asset("graphics/world2-tileset.png"))
            tilemap(asset("graphics/world2-area1.png"))   // Phase 12.2 D-01 two-invocation path
            spawn(40u, 120u)
            // Per-level platformerPhysics override (D-12). Shadows the game-level config above.
            // Fields NOT set fall through to the game-level defaults — `jumpForce`, `jumpHold`,
            // and `terminalVelocity` are inherited from the game-level platformerPhysics block.
            platformerPhysics {
                gravity(3)
                solidThreshold(68)
            }
        }

        // -----------------------------------------------------------------
        // Player metasprite (D-04 — 6 frames: walk1 / walk2 / walk3 / idle / jump-up / jump-fall)
        // -----------------------------------------------------------------
        // 6-frame metasprite per D-04 (selected over 12-frame faithful and 4-frame reduced).
        //
        // hflip via `facingRot`: rotVar binding flows facingRot → MoveMetasprite.rotVar →
        // MetaspriteVisitor's rot >> 2 → flipX path (Phase 10.1-05). idxVar binding flows
        // walkFrameIdx → frame index.
        //
        // Phase 12.4 D-03/D-14: multi-tile composition (3 cols × 2 8x16-pair-rows per frame); sprite()
        // binder + baseIds from png2asset first-run capture (.planning/phases/12.4-.../evidence/png2asset-first-run/player-tiles-ordering.md).
        //
        // Debug E-03 (2026-05-24): tile() argument convention is `tile(dx, dy, tileId)` — x
        // (horizontal) FIRST, y (vertical) SECOND. This is the OPPOSITE order from the reference's
        // `METASPR_ITEM(dy, dx, dtile, ...)` macro. When transcribing reference coordinates, swap
        // the first two arguments. Example: reference `METASPR_ITEM(-6, -12, 0, ...)` → gbkt
        // `tile(-12, -6, 0)`. Pre-fix the args were transcribed directly producing a vertical
        // column layout instead of the 3col × 2row horizontal grid (the "duck blob" symptom).
        val player by metasprite {
            sprite(asset("graphics/player-character-gbapduck-sprites.png")) {
                mode(SpriteMode.SPR8x16)
                pivot(12, 6)
                frameSize(24, 32)
            }
            // Phase 12.1-05 — bind metasprite position to the user-DSL playerX/playerY
            // (closes Defect 5 per RESEARCH §D-claude-3 — TEST-ONLY meaning no visitor
            // change required, the bound-var path in MetaspriteVisitor.lowerMoveMetasprite
            // already picks up posXVarName/posYVarName from MetaspriteIR). After this
            // binding, the generated `move_metasprite_ex()` call references `_playerX` /
            // `_playerY` instead of the magic fallback `_posX` / `_posY`.
            posX(playerX)
            posY(playerY)

            // Wire facing + animation state vars (no magic strings — names flow from delegates).
            rot(facingRot)
            idx(walkFrameIdx)

            // 13.4 D-08 auto-fix [Rule 1 - Bug]: D-08 exactly-one guard (Plan 13.3-06) rejects
            // mixed asset-driven + procedural frame{} form. Replaced the 6 procedural frame{}
            // blocks with frames(12) — the asset-driven path derives layout from
            // player-character-gbapduck-sprites.png (144×64px / frameSize 24×32 = 12 frames)
            // via ConvertSpritesTask; pivot(12,6) + frameSize(24,32) set in sprite() above
            // supply the geometry to png2asset.
            // 13.4-10 Rule 1 fix: original frames(6) caused frame-count validation failure
            // (png2asset reports 12 frames); corrected to frames(12).
            frames(12)
        }

        // -----------------------------------------------------------------
        // Scenes (title + gameplay; nextLevelScene added in Plan 12-17)
        // -----------------------------------------------------------------
        // Title scene — Plan 12-17 will wire the banked title-card render via the
        // `ShowCentered`-equivalent banked tile-data load (D-02 + 12-CONTEXT canonical_refs:
        // common.c). For now this is a navigation-only placeholder: Start → gameplay.
        //
        // D-claude-5: `buttons.start.pressed` is edge-triggered (rising edge only) — matches the
        // reference's `joypadCurrent & J_START && !(joypadPrevious & J_START)` intent.
        val titleScene =
            scene("title") {
                // Phase 13.5 Req #18: screen() primitive synthesises a _screen_title ZoneIR with
                // screenMode=true. SceneVisitor's screenMode superset branch emits:
                //   hide_sprites_range + move_bkg(0,0) + fill_bkg_rect(0,0,32,32,0) +
                //   centered _bkg_tiles_load_banked + DISPLAY_ON
                // replacing the old zone(titleZone) + manual raw-escape ceremony.
                screen(asset("graphics/title-screen.png"))
                // D-claude-5: `buttons.start.pressed` is edge-triggered (rising edge only) —
                // matches the reference's `joypadCurrent & J_START && !(joypadPrevious & J_START)`.
                // 13.4 D-07: navigate(ref) form. gameplayScene is declared after titleScene so
                // SceneRef("gameplay") provides a forward ref (resolved at game build() time).
                frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
            }

        // Gameplay scene — Plan 12-18 wires per-frame physics + camera updates (the
        // PlatformerVisitor auto-emits gravity/jump/scroll into the frame block via Plans 12-11 +
        // 12-13). Today this scene exercises:
        //
        //   - dpad.right.held / dpad.left.held → facing rotation (D-04 hflip via facingRot).
        //   - moveMetasprite(player) → OAM render every frame (Plan 10 surface).
        //
        // Per-frame physics + horizontal scroll are NOT explicit DSL calls — they are auto-emitted
        // by PlatformerVisitor when (a) `platformerPhysics` is registered (above), (b) the scene
        // is bound to a zone with a tileset, AND (c) `platformerCamera` is configured (above). The
        // zone-binding for the active level is wired in Plan 12-17 (level-switch).
        //
        // Phase 12.6 Pitfall 5 (D-03 ordering): gameplayScene MUST be declared BEFORE
        // nextLevelScene so the Kotlin reference resolves at DSL-recording time when the new
        // levelCardScene helper captures `onStartPress(gameplayScene)`. The order was inverted
        // in the prior commit; this is the Pitfall 5 mitigation.
        val gameplayScene =
            scene("gameplay") {
                // Plan 12-19 deviation [Rule 1 - Bug]: call setup_current_level() on every
                // gameplay enter so the current level's tileset+tilemap is loaded into VRAM
                // BEFORE the first gameplay frame renders. Without this, the gameplay scene
                // inherits whatever tileset/tilemap the prior scene (title or NextLevel card)
                // left in VRAM — making the gameplay background visually identical to the
                // title screen. Originally a raw-escape workaround (Plan 12-19); migrated to
                // typed bindCurrentLevel() in Phase 13.5 Plan 06 (Req #17).
                //
                // Idempotent: setup_current_level() sets _current_level = _next_level (no-op if
                // already equal) and writes the tileset+tilemap to VRAM unconditionally (an
                // idempotent VRAM write is runtime-neutral). Kept as a safety net per Phase
                // 12.6 RESEARCH § "State of the Art" — the load-bearing setup_current_level()
                // call now lives in the levelCardScene Start-press path (Plan 12.6-04 helper).
                enter {
                    // Phase 13.5 Req #17: typed BindCurrentLevel IR node.
                    // Lowers to CCall("setup_current_level") in ScriptOpVisitor.
                    bindCurrentLevel()
                }
                frame {
                    // Facing rotation via D-pad held (D-04).
                    // rot=0 ⇒ right-facing (no flip); rot=3 ⇒ left-facing (flipX) per Phase 10.1's
                    // MetaspriteVisitor rot >> 2 → flipX path.
                    whenever(dpad.right.held) { facingRot set 0 }
                    whenever(dpad.left.held) { facingRot set 3 }

                    // Per-frame metasprite render. PlatformerVisitor's auto-emitted input +
                    // physics + camera update happens automatically (Phase 12.3 codegen wiring).
                    // Walk-cycle is also auto-emitted (Plan 12.3-08) because the game-level
                    // platformer-input block declares the walkFrameIdx + threeFrameCounter binders.
                    moveMetasprite(player)
                }
            }

        // NextLevel card scene — Phase 12.6 D-03 levelCardScene delegate-pattern (DEFECT-1
        // closure).
        //
        // Mirrors the reference's `ShowCentered(NextLevel_..., ...); WaitForStartOrA();` block at
        // `gbdk/examples/cross-platform/platformer_template/src/main.c` lines 44-82. The main()
        // level-switch guard (Plan 12.6-02 trimmed) navigates here when `_next_level !=
        // _current_level`; the helper's lowered scene paints card art on enter, then on
        // buttons.start.pressed emits `setup_current_level();` BEFORE `navigate(gameplayScene)`
        // — so the new-level tilemap write happens IMMEDIATELY BEFORE the gameplay scene takes
        // over (no inter-frame VRAM stomp).
        //
        // Property-name capture (Project Rule #1 — no magic strings): `provideDelegate` reads
        // `property.name == "nextLevelScene"` as the scene id; the widened main-loop guard
        // matcher (Plan 12.6-02) substrings on `lower.contains("nextlevel")` so the
        // SCENE_NEXTLEVEL navigation path continues to fire.
        val nextLevelScene by levelCardScene {
            // Phase 13.5 Req #18: screen() primitive synthesises a _screen_nextLevelScene ZoneIR
            // with screenMode=true. SceneVisitor's screenMode superset branch emits the full
            // centered-draw ceremony: hide_sprites + move_bkg(0,0) + fill_bkg_rect(0,0,32,32,0)
            // + centered _bkg_tiles_load_banked + DISPLAY_ON.
            screen(asset("graphics/next-level.png"))
            onStartPress(gameplayScene)
        }

        // 13.4 D-07: SceneRef migration target from Plan 13.4-03.
        start = titleScene
        // gameplayScene + nextLevelScene are referenced; file-level @file:Suppress covers these.
        val _gameplaySceneRef = gameplayScene
        val _nextLevelSceneRef = nextLevelScene
    }
