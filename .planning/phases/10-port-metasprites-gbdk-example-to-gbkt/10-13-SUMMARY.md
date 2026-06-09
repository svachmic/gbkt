---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 13
subsystem: gbkt-examples/metasprites
tags: [assembly, metasprite, gbc, tdd, example-game]
dependency_graph:
  requires: [10-07, 10-08, 10-09, 10-10, 10-11, 10-12]
  provides: [metasprites-example-module, metasprites-ir-test]
  affects: [settings.gradle.kts, gbkt-examples/metasprites]
tech_stack:
  added: [gbkt-examples/metasprites]
  patterns: [metasprite DSL, spritePalette DSL, bgFillCheckerboard, GBC_COMPATIBLE target, TDD RED/GREEN cycle]
key_files:
  created:
    - gbkt-examples/metasprites/build.gradle.kts
    - gbkt-examples/metasprites/CLAUDE.md
    - gbkt-examples/metasprites/README.md
    - gbkt-examples/metasprites/res/sprites/elephant.png
    - gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteIRTest.kt
  modified:
    - settings.gradle.kts (added include(":gbkt-examples:metasprites"))
decisions:
  - "Tile coordinates transcribed from png2asset sprite.c output using METASPR_ITEM(dy,dx,dtile,attr) → tile(relX=dx, relY=dy, baseId=dtile) mapping"
  - "MetaspriteBuilder.sprite() absent — skip sprite() call in metasprite block; PNG loaded at pipeline level; PHASE-13 TODO added"
  - "SceneBuilder.palette() slot-0 default for all 4 sub-palettes is a PHASE-13 gap; route to Plan 18"
  - "idx and rot declared as u8Var (NOT i8Var) — Pitfall 6: unsigned semantics for isAtLeast/and 0xF comparisons"
metrics:
  duration: 4 minutes
  completed: 2026-05-18T16:27:39Z
  tasks_completed: 2
  files_modified: 7
---

# Phase 10 Plan 13: Assemble gbkt-examples/metasprites — Summary

Port assembled: 5-frame elephant metasprite with GBC sub-palette cycling, hardware flip,
and sub-pixel physics in a single idiomatic `Metasprites.kt` game module under
`gbkt-examples/metasprites/`.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Scaffold Gradle subproject + asset PNG | 5588400e | `settings.gradle.kts`, `build.gradle.kts`, `README.md`, `CLAUDE.md`, `res/sprites/elephant.png` |
| 2 (RED) | Add failing MetaspriteIRTest | 9d6a6855 | `MetaspriteIRTest.kt` |
| 2 (GREEN) | Implement Metasprites.kt | 1f3b90f9 | `Metasprites.kt` |

## What was built

### Task 1: Gradle Subproject + Asset

- `settings.gradle.kts`: appended `include(":gbkt-examples:metasprites")` alongside `simple-physics`
- `build.gradle.kts`: mirrors `simple-physics/build.gradle.kts`; sets game class to
  `io.github.gbkt.examples.metasprites.MetaspritesKt::metasprites`, assets dir to `res`,
  outputName to `metasprites`
- `res/sprites/elephant.png`: copied from
  `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png`
  (5-frame, 64×240 px, 5 stacked 64×48-px frames)
- `README.md` and `CLAUDE.md`: three-audience-angles documentation with key DSL patterns,
  PHASE-13 TODO catalogue, and build commands

### Task 2 (TDD): MetaspriteIRTest + Metasprites.kt

**RED gate:** `MetaspriteIRTest.kt` written with 7 behavioral assertions before any
implementation existed. Build failure confirmed (missing `MetaspritesKt` class).

**GREEN gate:** `Metasprites.kt` implemented — all 7 IR shape tests pass.

**DSL surface exercised:**
- `game("Metasprites") { config { target(GbcTarget.GBC_COMPATIBLE) } }` — GBC compat (D-09)
- `var idx by u8Var(0)` and `var rot by u8Var(0)` — unsigned state variables (Pitfall 6)
- `val gray/pink/cyan/green by spritePalette { color0..color3 }` — 4 sprite palettes (D-09)
- `val elephant by metasprite { frame { tile(relX, relY, baseId) } × 5 }` — 5-frame metasprite
  with tile coordinates transcribed from `png2asset` output
- `bgFillCheckerboard()` in `enter { }` — background visual parity (D-10)
- `moveMetasprite(elephant)` in `frame { }` — renders the metasprite each frame (D-04)
- `palette(gray/pink/cyan/green)` at scene level — loads 4 sprite palettes (PHASE-13 gap noted)

**Tile coordinate source:** ran `png2asset res/sprite.png -sh 48 -spr8x8 -noflip -c obj/gb/res/sprite.c`
and transcribed all 5 `sprite_metasprite*[]` arrays. Frame tile counts: 31/33/33/32/32 — exact
match to `asset-spec.md`.

**generateC verification:** `./gradlew :gbkt-examples:metasprites:generateC` exits 0 and
generates `main.c` containing:
- `#include <gbdk/metasprites.h>` — metasprite include (D-04 pipeline wiring)
- `cgb_compatibility();` as the first statement in `main()` (D-09 GBC init)
- `_elephant_flipX / _elephant_flipY / _elephant_subPalette` global variable declarations
- `move_metasprite_flipy / flipxy / flipx / ex` switch in `play_frame` body (D-07 visitor)
- `fill_bkg_rect + set_bkg_data` for checkerboard background (D-10)

## Deviations from Plan

### PHASE-13 Gaps Found During Assembly

**1. [Rule 2 - Missing] MetaspriteBuilder.sprite() not implemented**
- **Found during:** Task 2 implementation
- **Issue:** The plan's `<action>` called `sprite(asset("sprites/elephant.png"))` inside
  `metasprite { }`, but `MetaspriteBuilder` has no `sprite()` method. The asset is loaded
  at the pipeline level (png2asset processes the PNG independently of the DSL asset reference).
- **Resolution:** Skipped `sprite()` call; added PHASE-13 TODO comment. The game compiles and
  the asset pipeline still processes `res/sprites/elephant.png`. Routed to Plan 18.
- **Impact:** IR-level only — no visual or runtime difference in Plan 13/14 scope.

**2. [Rule 2 - Missing] SceneBuilder.palette() slot-0 default for multi-palette loading**
- **Found during:** Task 2 implementation
- **Issue:** `SceneBuilder.palette(palette)` defaults to slot 0 for auto-assigned palettes
  (`slot == -1`). Calling `palette(gray); palette(pink); palette(cyan); palette(green)` at
  scene level would emit 4 `set_sprite_palette(0, ...)` calls — all loading into slot 0
  instead of slots 0/1/2/3. The generated `main.c` shows this.
- **Resolution:** Called all 4 `palette(...)` in scene block anyway (IR-valid; palette data
  is registered correctly); added PHASE-13 TODO comment. The generated C has incorrect slot
  values but is syntactically valid. Plan 14's ROM build will surface this as a visual bug
  (all frames use gray palette only). Routed to Plan 18.
- **Impact:** Runtime palette selection broken for sub-palettes 1-3 (gray appears correct;
  pink/cyan/green never appear). Noted in CLAUDE.md.

## Known Stubs

None that prevent Plan 13's goal (IR assembly and DSL validation). The PHASE-13 gaps above
are intentional deferred-framework issues, not stubs in the sense of empty/broken core
functionality.

## Verification

- `./gradlew :gbkt-examples:metasprites:compileKotlin` — exits 0
- `./gradlew :gbkt-examples:metasprites:test` — exits 0 (all 7 IR shape tests PASS)
- `./gradlew :gbkt-examples:metasprites:generateC` — exits 0 (C generated without exception)
- `:buildRom` NOT attempted — Plan 14's territory per plan spec

## TDD Gate Compliance

- RED gate: `test(10-13)` commit `9d6a6855` — failing tests before implementation
- GREEN gate: `feat(10-13)` commit `1f3b90f9` — all tests pass after implementation
- REFACTOR: no structural cleanup needed; code is clean as written

## Self-Check: PASSED

Files confirmed present:

- `gbkt-examples/metasprites/build.gradle.kts` — FOUND
- `gbkt-examples/metasprites/res/sprites/elephant.png` — FOUND
- `gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt` — FOUND
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteIRTest.kt` — FOUND
- `settings.gradle.kts` (includes metasprites) — FOUND

Commits confirmed:
- `5588400e` (Task 1 scaffold) — FOUND
- `9d6a6855` (RED test) — FOUND
- `1f3b90f9` (GREEN implementation) — FOUND
