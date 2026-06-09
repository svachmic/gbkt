# First buildRom Attempt — Plan 12-18

**Date:** 2026-05-22
**Plan:** 12-port-platformer-template-gbdk-example-to-gbkt / 12-18
**Toolchain:** GBDK-4.5.0 (lcc rev 2.0, 2025-12-28) at `/Users/michalsvacha/gbdk`
**Command:** `./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom`

## Status: RED (after 2 attempts — 1 defect inline-fixed, 4 remaining wide-blast-radius defects)

## Attempt 1 — pre-fix (log: /tmp/platformer-template-buildrom.log)

### Outcome
`:convertZoneTilesets` FAILED with D-C2 path resolution error.

### Defect 1 — Asset path duplication (`res/res/graphics/...`)
- **Error:** `Zone world1Area1Zone tileset PNG not found at /.../platformer-template/res/res/graphics/world1-tileset.png (D-C2)`
- **Root cause:** All five `tileset(asset("res/graphics/<file>.png"))` calls in `PlatformerTemplate.kt` carried a redundant `res/` prefix. The gradle plugin extension `assets("res")` (in `build.gradle.kts`) already establishes the project-root-relative `res/` directory; asset paths in the DSL are RELATIVE to that directory.
- **Reference contract:** Banks example uses `tileset(asset("tiles/checker.png"))` (no `res/` prefix) against `assets("res")`.
- **Disposition:** Rule 1 (bug) — inline-fix, single-file scope.
- **Fix commit:** `4830d69c` — `fix(12-18): correct platformer-template zone tileset asset paths` (-5 / +5 lines).
- **Outcome of fix:** Attempt 2 proceeds past `:convertZoneTilesets`.

## Attempt 2 — post-asset-fix (log: /tmp/platformer-template-buildrom-2.log)

### Outcome
- `:processAssets` GREEN — 8 assets processed.
- `:generateC` GREEN — 5 C files / 813 LOC (488 main.c, 75 bank1.c, 147 game.h, 5 zone_bank2.c, 98 game_metadata.json).
- `:convertSprites` GREEN — stub `sprites/player.h` emitted (no `res/sprites/player.png` exists; pre-existing gap per Plan 12-17 SUMMARY §"Known Stubs").
- `:convertZoneTilesets` **GREEN** — all 5 zone tilesets converted via png2asset, emitting `_zone_<id>_tileset.{c,h}` + `_zone_<id>_tilemap.c` for each of {world1Area1Zone, world1Area2Zone, world2Area1Zone, titleZone, nextLevelZone}.
- `:copyResources` GREEN.
- `:compileRom` **FAILED** with SDCC error 20 (undefined identifier) — 51 errors across main.c + bank1.c. No `.gb` file produced; no `.noi` file produced.

### Defect 2 — `BANK()` macro on HOME-bank tilemap arrays
- **Error:** `main.c:136: error 20: Undefined identifier '__bank__zone_world1Area1Zone_tilemap'` (and `world1Area2Zone`, `world2Area1Zone` siblings).
- **Root cause:** `setup_current_level()` emits `_current_area_bank = BANK(_zone_world1Area1Zone_tilemap);`. The `BANK(x)` macro expands to `__bank_<x>`. SDCC only synthesizes `__bank_<sym>` symbols for arrays defined in a NON-HOME bank (via `#pragma bank` or BANK_NUM-prefixed file). The Phase 11.1-17 design places `_zone_<id>_tilemap.c` in the HOME bank ("HOME bank placement (~1024 bytes): reachable from any banked scene via the existing _bkg_tiles_load_banked HOME-bank wrapper" — comment in generated tilemap.c). HOME-bank arrays have NO `__bank_` symbol, so `BANK(_zone_<id>_tilemap)` references an undefined identifier.
- **Blast radius:** Affects every per-case body in `setup_current_level` (Plan 12-17 Task 2 codegen). Either (a) the GBDKPipelineV2 `buildSetupCurrentLevelFunctionIfNeeded` must replace `BANK(_zone_<id>_tilemap)` with a literal `1u` (HOME bank) for HOME-bank tilemap arrays, or (b) ConvertZoneTilesetsTask must move tilemaps to a banked file and emit `#pragma bank <N>`, or (c) introduce a new `_zone_<id>_tilemap_BANK` symbol emitted by ConvertZoneTilesetsTask alongside the tilemap array.

### Defect 3 — Tilemap WIDTH / HEIGHT symbols never declared
- **Error:** `main.c:138: error 20: Undefined identifier '_zone_world1Area1Zone_tilemap_WIDTH'` + same for `_HEIGHT`, repeated for all 3 gameplay zones.
- **Root cause:** Plan 12-17 SUMMARY §"Known Stubs" anticipated that `_zone_<id>_tilemap_WIDTH/HEIGHT` would resolve at buildRom via ConvertZoneTilesetsTask. Inspection of the emitted `_zone_world1Area1Zone_tileset.h` (and tilemap.c) shows neither symbol is emitted by ConvertZoneTilesetsTask. The setup_current_level body references them speculatively but no producer exists in the pipeline.
- **Blast radius:** Either (a) ConvertZoneTilesetsTask must emit `#define _zone_<id>_tilemap_WIDTH <N>` + `#define _zone_<id>_tilemap_HEIGHT <M>` constants into the tileset.h header, or (b) GBDKPipelineV2 must consume `mapWidth`/`mapHeight` from `game_metadata.json` at codegen time and substitute literal values into the per-case body. Option (b) requires routing the metadata back into the codegen step, which currently writes-only the metadata.

### Defect 4 — `_player_x` / `_player_y` / `_player_vx` / `_player_vy` / `_grounded` undefined
- **Error:** 22 errors across main.c lines 312-368 referencing the above five symbols.
- **Root cause:** Two distinct symbol-naming conventions live inside `PlatformerVisitor`:
  - The PHYSICS UPDATE path (`platformer_physics_update`, Plan 12-11+12-13 tilemap-collision branch) emits `_player_x`, `_player_y`, `_player_vx`, `_player_vy`, `_grounded` (no prefix, `_player_` for position, bare `_grounded`).
  - The ACTOR EXTERNS path (game.h emission) declares `_plat_vx`, `_plat_vy`, `_plat_grounded`, `_plat_coyote_timer`, `_plat_jump_buffer`, `_jump_increase_timer` (with `_plat_` prefix and bare `_jump_*`).
  - Neither declaration covers `_player_x` / `_player_y` (player actor position) — the user-DSL omits the `val player by actor { position(x, y) ... }` declaration entirely (Plan 12-16 SUMMARY §"Known Stubs" anchor: `player actor with hitbox(0, 0, 8, 24) not declared`).
- **Blast radius:** Two codegen contracts must be reconciled. Either (a) PlatformerVisitor's physics-update path is rewritten to use the `_plat_*`/`_player_*` declared globals, or (b) PlatformerVisitor declares additional globals (`_player_x` / `_player_y` / `_player_vx` / `_player_vy` / `_grounded`) at the pipeline level (analogous to `buildTilemapCollisionGlobals` from Plan 12-08). Plus the user-DSL `val player by actor { position(...) }` substrate is missing entirely — without an actor, the moveMetasprite codegen path (Defect 5) also breaks.

### Defect 5 — `_posX` / `_posY` undefined (metasprite render in bank1.c)
- **Error:** `bank1.c:52-68: error 20: Undefined identifier '_posX' / '_posY'`.
- **Root cause:** `MetaspriteVisitor` lowers `moveMetasprite(player)` to:
  ```c
  hiwater += move_metasprite_ex(sprite_player_frames[_walkFrameIdx], 0, subpal, hiwater,
                                DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                                DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
  ```
  But `_posX` / `_posY` are NOT declared anywhere — they appear to be a magic-name expectation that the visitor inherits from an actor-positioning contract that does not fire in this DSL composition (because no `val player by actor { position(...) }` is declared). This is the symptom of Defect 4's "actor missing" — the metasprite render expects a position-state pair, the actor would have declared it as `_<actorName>_x` / `_<actorName>_y`, but the visitor uses magic `_posX` / `_posY`.
- **Blast radius:** Either (a) the visitor reads the actor's position-property convention (`_player_x` / `_player_y`) instead of magic `_posX` / `_posY`, or (b) the visitor declares `_posX` / `_posY` as pipeline-emitted globals when an unbound metasprite is detected, or (c) the user-DSL declares a player actor with `position(x, y)` (which would emit `_player_x` / `_player_y` per the actor-property delegate convention). All three options change a codegen contract.

### Generated artifact inventory (post-attempt-2)
```
build/gbkt/generated/
├── main.c                              (488 lines)
├── bank1.c                              (75 lines)
├── game.h                              (147 lines)
├── zone_bank2.c                          (5 lines)
├── game_metadata.json                   (98 lines)
├── _zone_world1Area1Zone_tileset.c / .h
├── _zone_world1Area1Zone_tilemap.c
├── _zone_world1Area2Zone_tileset.c / .h
├── _zone_world1Area2Zone_tilemap.c
├── _zone_world2Area1Zone_tileset.c / .h
├── _zone_world2Area1Zone_tilemap.c
├── _zone_titleZone_tileset.c / .h
├── _zone_titleZone_tilemap.c
├── _zone_nextLevelZone_tileset.c / .h
├── _zone_nextLevelZone_tilemap.c
└── sprites/player.h                    (stub)
```

No `build/gbkt/output/platformer-template.gb` produced.
No `build/gbkt/output/platformer-template.noi` produced — bank-size table N/A (compileRom failed before the link step that produces `.noi`).
No `:emulatorTest` run — ROM does not exist.

### Build duration
- Attempt 2 elapsed: ~1s (with build cache warm from attempt 1's compileKotlin / generateC).
- Cold attempt 1 elapsed: ~6s.

## Defect cluster taxonomy

| # | Defect | Layer | Scope | Inline-fixable? |
|---|--------|-------|-------|-----------------|
| 1 | Asset path duplication | DSL composition (PlatformerTemplate.kt) | Single file, 5 lines | YES — fixed in `4830d69c` |
| 2 | `BANK()` macro on HOME-bank tilemap | GBDKPipelineV2 `buildSetupCurrentLevelFunctionIfNeeded` (per-case body) OR ConvertZoneTilesetsTask (banking annotation) | Pipeline codegen contract change | NO — touches GBDKPipelineV2 + potentially ConvertZoneTilesetsTask |
| 3 | Tilemap WIDTH/HEIGHT missing | ConvertZoneTilesetsTask (emit `#define`s) OR GBDKPipelineV2 (substitute literals from metadata) | New symbol emission contract | NO — task-output contract change |
| 4 | `_player_x` / `_player_y` / `_player_vx` / `_player_vy` / `_grounded` undefined | PlatformerVisitor (naming convention) OR DSL (player actor missing) OR GBDKPipelineV2 (pipeline-emitted globals) | Cross-cuts visitor + DSL + pipeline | NO — multiple visitor methods + DSL composition |
| 5 | `_posX` / `_posY` undefined (metasprite render) | MetaspriteVisitor (magic-name lookup) OR DSL (player actor missing) | Visitor codegen contract OR DSL composition | NO — visitor convention change |

## Recommended disposition

Per memory rule `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` and the Plan 12-18 checkpoint protocol §"If FAILED with a wide-blast-radius defect": **escalate via `/gsd-phase --insert`** to create a focused defect-cluster phase (proposed: **12.1-platformer-template-codegen-contract-reconciliation**) that addresses defects 2 + 3 + 4 + 5 as 4 distinct sub-plans:

- **Plan A (Defect 2):** Resolve `BANK()` of HOME-bank tilemap arrays. Option to evaluate: rewrite per-case body to use literal `1u` (HOME) for tilemap arrays, OR move tilemaps to a banked file via `#pragma bank` (impacts banking analysis pass).
- **Plan B (Defect 3):** Emit `_zone_<id>_tilemap_WIDTH/HEIGHT` constants. Option: add `#define` emission to ConvertZoneTilesetsTask's tileset.h header.
- **Plan C (Defect 4):** Reconcile `_player_x/y/vx/vy/grounded` naming. Either rewrite PlatformerVisitor physics-update path to use `_plat_*` prefix, OR declare additional `_player_*` globals at pipeline level (mirroring `buildTilemapCollisionGlobals`).
- **Plan D (Defect 5):** Reconcile `_posX/_posY` magic-name expectation in MetaspriteVisitor. Either rewrite the visitor to use the bound actor's position-property convention, OR declare `_posX/_posY` as pipeline globals when an unbound metasprite is detected.

Plus, optionally:
- **Plan E (related substrate):** Add the missing `val player by actor { position(...); hitbox(0, 0, 8, 24) }` declaration to PlatformerTemplate.kt (Plan 12-16 SUMMARY §"Known Stubs" anchor).

### Why NOT inline-absorb

The Plan 12-18 charter says "additional defects surfaced here addressed inline up to reasonable bound" (D-01 lifted cap). FOUR distinct codegen-contract defects spanning THREE different visitors / pipeline modules (GBDKPipelineV2 + PlatformerVisitor + MetaspriteVisitor + ConvertZoneTilesetsTask) exceeds Plan 12-18's reasonable absorption bound. Per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`, the executor must escalate rather than drive inline.

### Why a SUB-phase 12.1 (not a sub-sub-phase)

Per `feedback_many_small_plans_terminal_subphase.md`: "subphases (10.1, 09.1, 07.3) must CLOSE their defect cluster — no 10.1.1 follow-ups." Phase 12.1 — if created — must absorb defects 2-5 completely and re-run :buildRom in its own terminal plan. No 12.1.1.

## Next step

This plan (12-18) is now BLOCKED on the decision: (a) approve `/gsd-phase --insert` to create 12.1, OR (b) the human directs inline-fix of the smallest of the 4 remaining defects to see if the rest cascade-resolve. The decision checkpoint awaits.
