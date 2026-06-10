---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 15
subsystem: codegen
tags: [gbdk, metasprite, pipeline, codegen, c-generation, lcc]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    provides: Plan 10-14 first-blocker-analysis — named GBDKPipelineV2 wiring gap
provides:
  - "GBDKPipelineV2.buildHomeFile() now emits sprite_metasprite_N[] descriptor arrays and sprite_metasprites[] pointer table"
  - "metasprites.gb ROM builds cleanly (0 lcc errors)"
  - "MetaspriteDescriptorEmissionTest — 3-test golden-output coverage for descriptor emission"
affects:
  - 10-port-metasprites-gbdk-example-to-gbkt
  - gbkt-backend-gbdk codegen pipeline

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Metasprite descriptor raw sections added to GBDKPipelineV2.buildHomeFile() allRawSections — mirrors the paletteDataRaw pattern"

key-files:
  created:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MetaspriteDescriptorEmissionTest.kt
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/second-build-log.txt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt

key-decisions:
  - "D-05 fix landed: descriptor raw sections added to allRawSections via takeIf guard (emitted only when gameIR.metasprites is non-empty)"
  - "generateMetaspriteTileData() deferred: requires tileDataArrayName which is a PHASE-13 gap (no sprite asset pipeline wiring yet); runtime-only concern, not a compile blocker"
  - "Descriptor placed in rawSections (after palette data, before functions) — mirrors paletteDataRaw placement convention"

patterns-established:
  - "Pipeline wiring pattern: gameIR.metasprites.joinToString().takeIf { ... } → allRawSections — matches the paletteDataRaw idiom"

requirements-completed: []

# Metrics
duration: 35min
completed: 2026-05-18
---

# Phase 10 Plan 15: MetaspriteDescriptor Pipeline Wiring Summary

**GBDKPipelineV2 now calls MetaspriteVisitor.generateMetaspriteDescriptor() for each metasprite, emitting sprite_metasprite_N[] OAM arrays + sprite_metasprites[] pointer table into main.c — metasprites.gb compiles cleanly with 0 lcc errors**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-05-18T16:10:00Z
- **Completed:** 2026-05-18T16:52:00Z
- **Tasks:** 1 (TDD: RED commit + GREEN commit)
- **Files modified:** 3

## Accomplishments

- Fixed the single named D-05 blocker: `MetaspriteVisitor.generateMetaspriteDescriptor()` was fully implemented (Plan 10-06) but never called from `GBDKPipelineV2.buildHomeFile()`. Adding 11 lines to the pipeline (import + metaspriteDescriptorRaw + allRawSections entry) resolved all 4 lcc "Undefined identifier 'sprite_metasprites'" errors.
- `./gradlew :gbkt-examples:metasprites:buildRom` exits 0 — ROM compiled: `metasprites.gb (32 KB)`.
- 3-test golden-output coverage (`MetaspriteDescriptorEmissionTest`) locked via TDD RED→GREEN cycle.

## Task Commits

TDD cycle — two commits for the single task:

1. **RED — MetaspriteDescriptorEmissionTest (3 tests, 2 failing)** - `4be2ea26` (test)
2. **GREEN — Wire generateMetaspriteDescriptor into GBDKPipelineV2** - `435f494c` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — Added `import MetaspriteVisitor`, added `metaspriteDescriptorRaw` val and added it to `allRawSections`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MetaspriteDescriptorEmissionTest.kt` — 3-test pipeline-level golden-output suite for descriptor emission
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/second-build-log.txt` — Second buildRom log: BUILD SUCCESSFUL, 0 errors, 1 warning (ConvertSpritesTask PHASE-13 gap)

## Decisions Made

- `generateMetaspriteTileData()` deferred to a future plan. It requires a `tileDataArrayName` (the C array from the asset pipeline for `elephant.png`) which is a PHASE-13 gap — `MetaspriteBuilder.sprite()` is not yet implemented. The secondary concern in `first-blocker-analysis.md` explicitly noted "Deferred to Plan 15 scope or Plan 16 depending on whether the initial fix is sufficient for lcc to pass." Since the build passes cleanly without it, deferral is correct.
- Placed `metaspriteDescriptorRaw` after `paletteDataRaw` in `allRawSections` — this preserves the emit order (palette data, then descriptor arrays, before functions) consistent with the reference C shape.

## Deviations from Plan

None — plan executed exactly as written. The D-05 named blocker was fixed as specified in `evidence/first-blocker-analysis.md`. The secondary `generateMetaspriteTileData()` wiring was explicitly deferred per plan guidance ("Deferred to Plan 15 scope or Plan 16").

## Issues Encountered

- MCP emulator tools (`mcp__gbkt-emulator__*`) are not available in this agent execution context. UAT preview (behavior 1+2 smoke test) could not be run. This is expected for the checkpoint plan — the `checkpoint:human-verify` gate is the intended verification mechanism. Human can run `./gradlew :gbkt-examples:metasprites:runEmulator` to verify behavior 1 (B → animation frame advance) and behavior 2 (A → flip cycle).

## UAT Status

**Build:** PASSED — `metasprites.gb (32 KB)` compiled with 0 lcc errors, 1 warning.

**Warning retained (PHASE-13 gap, not a regression):**
```
ConvertSpritesTask: No sprite includes found in main.c
```
This is the elephant.png asset pipeline gap (D-13). `set_sprite_data()` / `set_sprite_palette()` wiring is missing but does not cause a compile error.

**Runtime verification:** Deferred to `checkpoint:human-verify` gate. Expected behaviors after this fix:
- Behavior 1 (B pressed → advance animation frame): Should work — `sprite_metasprites[_idx]` is now defined.
- Behavior 2 (A pressed → cycle flip state): Should work — all 4 `move_metasprite_*` variants are now linked.
- Behavior 3 (sub-palette cycling via `rot >> 2`): Will NOT work correctly — known PHASE-13 gap (all 4 sprite palettes load to slot 0).

**Visual appearance:** Sprite tiles will NOT render correctly — `set_sprite_data()` is missing from `play_enter()`, so VRAM tile data for the elephant is not loaded. The sprite will display garbage tiles or blank OAM slots. This is the next blocker to surface in Plan 16 if runtime verification confirms it.

## Known Stubs

- `set_sprite_data()` not called in `play_enter()` — VRAM tile data for the elephant metasprite is not loaded. Tracked as surplus defect in `evidence/first-blocker-analysis.md` §"Surplus defects deferred to seeds" item 1. Plan 18 (D-13) is the intended landing target; may become Plan 16 blocker depending on runtime severity.

## Surplus Defects Deferred to Plan 18 (D-06/D-13)

Per `evidence/first-blocker-analysis.md`, the following are deferred:

1. `set_sprite_data()` call for VRAM tile data loading missing from `play_enter()` — `generateMetaspriteTileData()` not called from pipeline (requires PHASE-13 asset wiring).
2. All 4 `set_sprite_palette()` calls use slot 0 — known PHASE-13 gap (D-13), deferred to Plan 18.
3. `ConvertSpritesTask: No sprite includes found in main.c` — elephant.png not wired to asset pipeline (D-13 gap 1).

## Next Phase Readiness

- Plan 15 complete — `metasprites.gb` ROM builds cleanly.
- Plan 16 (UAT) can now be attempted. Runtime behavior 1 and 2 may work; behavior 3 will not.
- If runtime shows sprite tiles are blank (VRAM not loaded), a follow-on fix plan is needed before Plan 16 can pass all behaviors.

## Self-Check: PASSED

- `GBDKPipelineV2.kt` modified: FOUND ✓
- `MetaspriteDescriptorEmissionTest.kt` created: FOUND ✓
- `second-build-log.txt` created: FOUND ✓
- Commits exist: `4be2ea26` (RED), `435f494c` (GREEN) — FOUND ✓

---

## Continuation: set_sprite_data() wiring

**Added by Plan 10-15 continuation — user requested `set_sprite_data()` wiring in same plan.**

### What was wired

`MetaspriteVisitor.generateMetaspriteTileData(ms, "<id>_tiles", startTile)` is now called from
`GBDKPipelineV2.buildMainFunction()` for each `MetaspriteIR` in `gameIR.metasprites` via the
new private helper `buildMetaspriteTileDataLoadStatements()`.

The generated `main.c` now contains:
```c
set_sprite_data(0u, 48u, elephant_tiles);
```
in `main()`, after SHOW_SPRITES, before the game loop — mirroring the actor sprite path.

### Asset include wiring (PHASE-13 convention)

To make `elephant_tiles` resolve, `buildHomeFile()` now emits `#include "sprites/<id>.h"` for
each metasprite. `ConvertSpritesTask` picks up this include and runs `png2asset` on
`sprites/<id>.png` (which `processAssets` already processes), producing `elephant_tiles[]` in
`sprites/elephant.c` + `sprites/elephant.h`.

### Deviations applied (Rule 2 — auto-add missing critical functionality)

The `set_sprite_data()` call is meaningless without the `elephant_tiles` C array. Emitting a
call to an undefined symbol would cause `lcc` to error. The include wiring is a correctness
requirement for the tile-load path — not a separate feature. Applied Rule 2 (missing critical
functionality) and fixed inline.

### Third build result

`./gradlew :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom`
— **BUILD SUCCESSFUL**, 0 lcc errors, `metasprites.gb (32 KB)`. Evidence captured to
`evidence/third-build-log.txt`.

### UAT / screenshot

MCP emulator tools (`mcp__gbkt-emulator__*`) are not available in this headless agent execution
context. Runtime screenshot cannot be captured. The `post-tile-fix-screenshot.png` was not
produced. Human verification via `./gradlew :gbkt-examples:metasprites:runEmulator` is needed.

Expected post-fix runtime: sprite tiles should now render as the elephant animation (previously
garbage/blank). Behavior 3 (sub-palette cycling) remains broken per the PHASE-13 slot assignment
gap deferred to Plan 18.

### Continuation commits

1. **RED — MetaspriteDescriptorEmissionTest tests 4-6 (set_sprite_data wiring)** - `7ef6a071` (test)
2. **GREEN — wire generateMetaspriteTileData into GBDKPipelineV2 main()** - `ac4fb46a` (feat)
3. **GREEN — wire metasprite sprite include + buildRom third build clean** - `83121e87` (feat)

### Files modified (continuation)

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  — Added `buildMetaspriteTileDataLoadStatements()` helper, wired into `buildMainFunction()`;
  added `metaspriteSpriteIncludes` to emit `#include "sprites/<id>.h"` for each metasprite.
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MetaspriteDescriptorEmissionTest.kt`
  — Tests 4-6 added (RED→GREEN for set_sprite_data pipeline emission).
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/third-build-log.txt`
  — Third buildRom log: BUILD SUCCESSFUL, 0 errors.

### Updated Known Stubs

- `set_sprite_data()` is now emitted — the VRAM tile-load gap is resolved.
- All 4 `set_sprite_palette()` calls still use slot 0 — PHASE-13 gap deferred to Plan 18.
- `ConvertSpritesTask` uses a `sprites_elephant_tiles` alias (backward-compat with actor path);
  the actual array `elephant_tiles` is what `main.c` references and `elephant.h` declares.

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
