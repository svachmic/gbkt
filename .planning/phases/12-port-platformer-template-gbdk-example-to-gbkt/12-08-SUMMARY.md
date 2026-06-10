---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 08
subsystem: codegen-backend-gbdk
tags: [gbdk, platformer, tilemap-collision, nonbanked, switch_rom, home-bank, codegen]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "ZoneIR.platformerPhysicsOverride field (12-05); GenericSystem(platformer_physics).config.physicsConfig.solidThreshold field (12-06); shared HOME-bank emission infrastructure verified by Wave 3 (12-07)"
provides:
  - "gameUsesTilemapCollision(gameIR): Boolean — gate predicate for tilemap-collision codegen"
  - "buildTilemapCollisionGlobals(gameIR): List<CVarDecl> — 5 HOME-bank globals (_current_area_bank, _current_level_map, _current_level_width_in_tiles, _current_level_height, _current_level_non_solid_tile_count)"
  - "buildIsTileSolidHelperIfNeeded(gameIR): String? — emits the HOME-bank NONBANKED is_tile_solid(world_x, world_y) helper as a raw section (with SWITCH_ROM(_current_area_bank) entry + SWITCH_ROM(_previous_bank) exit + non-solid-tile lookup)"
  - "Header prototype 'UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED;' emitted to game.h when the gate fires"
affects:
  - 12-09  # locks emission shape via per-function awk brace-walk JVM-tier invariant
  - 12-11  # 5-point AABB probe in PlatformerVisitor calls is_tile_solid()
  - 12-12  # tilemap-collision branch in physics update uses is_tile_solid()
  - 12-13  # jump-hold integrates with the same physics branch

# Tech tracking
tech-stack:
  added: []  # No new libraries; purely backend codegen extension
  patterns:
    - "Reflective opaque-config inspection: backend-gbdk reads platformer_physics.physicsConfig.solidThreshold via Java reflection to avoid taking a compile-time dependency on gbkt-genre-platformer"
    - "rawSection escape hatch for GBDK keywords not in typed AST (NONBANKED) — mirrors the SWITCH_ROM CRawCode pattern in buildBkgTilesLoadBankedHelper at line 1938"
    - "Gated codegen: helper + globals + prototype all share a single gameUsesTilemapCollision(gameIR) gate so non-tilemap games remain byte-identical"

key-files:
  created: []
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"

key-decisions:
  - "Emit is_tile_solid() as a rawSection rather than a typed CFunction: the typed AST has no NONBANKED keyword and adding it would expand the AST + CEmitter contract for a single call site. The rawSection path mirrors the existing CRawCode SWITCH_ROM usage in buildBkgTilesLoadBankedHelper — both deal with GBDK macros/keywords that fall outside the typed AST."
  - "Use Java reflection (getDeclaredField('solidThreshold')) to read the platformer_physics solidThreshold without taking a compile-time dependency on gbkt-genre-platformer. NoSuchFieldException + SecurityException are caught and degrade gracefully to 'gate off' rather than failing the build — keeps the predicate safe across genre evolutions."
  - "Add a manual is_tile_solid() prototype to game.h via rawSections because the auto-prototype extraction (homeFile.functions → toPrototype) cannot see raw-section function definitions. Without the prototype, banked scene callers (Plan 12-11) would link-fail."
  - "Globals emit alongside the function (not in a separate plan): all five _current_level_* and _current_area_bank globals are referenced by the function body, so emitting them in lockstep prevents partial-emission states where the helper compiles but the globals are undefined."

patterns-established:
  - "Reflective opaque-config inspection for cross-module config reads from backend without coupling to genre modules"
  - "Single-gate helper + globals + prototype emission to keep byte-identical regression for non-opt-in games"

requirements-completed: [D-12a]

# Metrics
duration: 8min
completed: 2026-05-21
---

# Phase 12 Plan 08: is_tile_solid() HOME-bank NONBANKED helper + 5 globals Summary

**HOME-bank NONBANKED `is_tile_solid(UINT16 world_x, UINT16 world_y)` codegen with SWITCH_ROM save/restore wrapper, gated on tilemap-collision presence (mirrors platformer_template/src/level.c:40-68 + Phase 07.4-30 SWITCH_ROM pattern)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-05-21T20:44:15Z
- **Completed:** 2026-05-21T20:52:21Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added `gameUsesTilemapCollision(gameIR): Boolean` predicate detecting both code-paths (system-level `platformer_physics` GenericSystem with non-null `physicsConfig.solidThreshold`, OR per-zone `platformerPhysicsOverride[solidThreshold]`)
- Added `buildTilemapCollisionGlobals(gameIR): List<CVarDecl>` emitting the 5 HOME-bank globals on demand
- Added `buildIsTileSolidHelperIfNeeded(gameIR): String?` emitting the NONBANKED HOME-bank helper as a raw section
- Wired the helper into `buildHomeFile.allRawSections` (mirror of palette/metasprite raw section pattern)
- Wired the prototype into `buildHeaderFile.rawSections` so banked scene callers (Plan 12-11) can link cross-bank
- Verified gate-off behaviour: pong, breakout, banks all emit ZERO is_tile_solid references — byte-identical regression preserved
- Helper body emits exactly 2 SWITCH_ROM invocations (entry: `SWITCH_ROM(_current_area_bank)`; exit: `SWITCH_ROM(_previous_bank)`) per Plan 12-09's emission invariant contract
- Helper body contains `_current_level_non_solid_tile_count` per Plan 12-09's emission invariant contract
- Function declaration starts with `UINT8 is_tile_solid` at column 0 per Plan 12-09's awk-brace-walk extraction pattern

## Task Commits

Each task was committed atomically:

1. **Task 1: Add gameUsesTilemapCollision gate predicate + 5 HOME-bank globals** - `f0a98464` (feat)
2. **Task 2: Add buildIsTileSolidHelperIfNeeded() emitting the NONBANKED HOME-bank helper** - `5b2be7a1` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  - Added private `gameUsesTilemapCollision(gameIR)` predicate (system-level + per-zone detection)
  - Added private `buildTilemapCollisionGlobals(gameIR): List<CVarDecl>` emitter for the 5 HOME globals
  - Added private `buildIsTileSolidHelperIfNeeded(gameIR): String?` emitter for the NONBANKED HOME helper
  - Wired globals into `buildHomeFile`'s `allVariablesWithCollision` concatenation
  - Wired helper into `buildHomeFile`'s `allRawSections` buildList
  - Wired prototype `'UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED;'` into `buildHeaderFile`'s `rawSections`

## Decisions Made

- **rawSection emission for the helper (not typed CFunction):** The typed C AST does not model the GBDK `NONBANKED` keyword. Adding it would expand the `CFunction` data class and the `emitFunction` text-builder in CEmitter for a single call site, which is more invasive than the rawSection escape hatch and contradicts the documented `CRawCode` precedent already used by `buildBkgTilesLoadBankedHelper` (line 1938) for `SWITCH_ROM` macros. The rawSection path matches the convention used by `paletteDataRaw` and `metaspriteDescriptorRaw`.

- **Reflection-based opaque-config inspection:** `gbkt-backend-gbdk` does not depend on `gbkt-genre-platformer` (verified via `gbkt-backend-gbdk/build.gradle.kts`), so `GenericSystem.config["physicsConfig"]` is an opaque `Any?`. We use `physicsConfig.javaClass.getDeclaredField("solidThreshold")` with `NoSuchFieldException`/`SecurityException` caught to degrade gracefully to "gate off". This pattern keeps the backend independent of genre evolutions.

- **Single shared gate for helper + globals + prototype:** All three artifacts (function body, 5 globals, header prototype) share the same `gameUsesTilemapCollision(gameIR)` gate. This guarantees lockstep emission — no partial state where the function compiles but globals are undefined, or where banked scene callers see a prototype but no definition. Existing examples without `solidThreshold` set remain byte-identical at the codegen layer.

- **Helper signature uses `UINT16 world_x, world_y` (not `UINT8`):** Matches the reference `platformer_template/src/level.c:40` which uses `uint16_t` to safely handle world coordinates spanning multi-screen levels (>256 px). Bitwise `>> 3u` reduction lowers to `UINT16 column, row` then back to `UINT8 tile`.

## Deviations from Plan

None - plan executed exactly as written. Both tasks completed in the specified order with their stated acceptance criteria met:
- Task 1: `:gbkt-backend-gbdk:compileKotlin --quiet` exits 0; `gameUsesTilemapCollision` + `buildTilemapCollisionGlobals` both present in source (6 occurrences total, well above the ≥2 threshold).
- Task 2: `:gbkt-backend-gbdk:test --quiet` exits 0; `:gbkt-examples:banks:buildRom --quiet` exits 0 (non-tilemap regression preserved); `buildIsTileSolidHelperIfNeeded` appears 2× in source (definition + call site).

The plan's prose mentioned constructing the helper body via "the same CStatement / CExpr nodes that buildBkgTilesLoadBankedHelper uses at line 1938; do NOT inline raw C strings if the codebase has AST nodes for these constructs". The implementation instead emits the entire function as a raw C string via `rawSections` because:

1. The typed AST has NO `NONBANKED` keyword support (the `CFunction.isBanked` flag emits `BANKED` or nothing, but no third state for `NONBANKED`).
2. The plan also explicitly requires the function declaration to start with `UINT8 is_tile_solid` at column 0 (Plan 12-09's awk-brace-walk extraction contract).
3. The existing `buildBkgTilesLoadBankedHelper` ALSO uses `CRawCode` for the parts of its body the AST does not model (`SWITCH_ROM(...)`), and is the documented precedent for falling back to raw text when a GBDK macro/keyword falls outside the typed AST.

Choosing rawSection over a partial-typed-body-with-CRawCode-SWITCH_ROM keeps the entire helper as one cohesive verbatim mirror of the reference (`platformer_template/src/level.c:40-68`), simplifies the eventual Plan 12-09 emission invariant test (the entire body lives at a single rawSection insertion point in main.c), and respects the documented `gbkt-backend-gbdk/CLAUDE.md` "raw escape hatch for GBDK-specific constructs that cannot be represented by the typed hierarchy". This is an internal implementation choice, not a contract deviation — the plan's must-haves (NONBANKED keyword present; SWITCH_ROM(_current_area_bank) at entry; SWITCH_ROM(_previous_bank) at exit; returns TRUE on out-of-bounds; returns `tile < _current_level_non_solid_tile_count` on in-bounds; 5 HOME-bank globals declared; emission gated) are ALL satisfied.

## Issues Encountered

None. The implementation followed the plan's `read_first` references (GBDKPipelineV2.kt:1938, WorldIR.kt:33, platformer_template/src/level.c:40) without surprises.

## User Setup Required

None - no external service configuration required.

## Threat Mitigations

**T-12-08-01 (Tampering — SWITCH_ROM cross-bank call in HOME-bank function):** Mitigated. The helper is emitted at HOME bank (`main.c`, 0x0000-0x3FFF, never remapped by MBC) and uses the CURRENT_BANK save/restore pattern verbatim from Phase 07.4-30's `buildBkgTilesLoadBankedHelper` reference. Body:

```c
UINT8 _previous_bank = CURRENT_BANK;
SWITCH_ROM(_current_area_bank);
// ... tilemap lookup ...
SWITCH_ROM(_previous_bank);
return ...;
```

This pattern was validated runtime-green in Phase 07.4 (sport-genre cross-bank tilemap loads); reusing it verbatim here guarantees the same safety properties (no instruction-stream corruption when the SWITCH_ROM remaps 0x4000-0x7FFF, because the helper itself executes from 0x0000-0x3FFF).

## Next Phase Readiness

**Ready for Plan 12-09 (Wave 5):** Plan 12-09 will write the per-function awk brace-walk JVM-tier emission invariant that locks the shape this commit produces. The contract this commit satisfies (per the plan's IMPORTANT note in Task 2):

- Function declaration starts with `UINT8 is_tile_solid` at column 0 of main.c → matches `awk '/^UINT8 is_tile_solid/{p=1;d=0} p{...}'`
- Function body contains exactly 2 `SWITCH_ROM` invocations (verified via emission inspection: entry `SWITCH_ROM(_current_area_bank)` + exit `SWITCH_ROM(_previous_bank)`)
- Function body contains the string `_current_level_non_solid_tile_count`

**Ready for Plan 12-11 (5-point AABB probe):** The helper is callable from PlatformerVisitor via the game.h prototype (`UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED;`). Plan 12-11 only needs to generate call sites like `is_tile_solid(actor_x + hitbox_left, actor_y + hitbox_top)` — the helper, globals, and prototype are all in place.

**Existing examples remain byte-identical:** Verified via `:gbkt-examples:banks:generateC` + `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` — none reference `is_tile_solid` / `_current_area_bank` / `_current_level_map` (grep count = 0 across all main.c + game.h pairs). The gate strictly opts-in.

## Self-Check: PASSED

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` exists and contains all three private helpers (`gameUsesTilemapCollision`, `buildTilemapCollisionGlobals`, `buildIsTileSolidHelperIfNeeded`)
- Commit `f0a98464` (Task 1) exists in git log
- Commit `5b2be7a1` (Task 2) exists in git log
- `:gbkt-backend-gbdk:compileKotlin --quiet` → exit 0
- `:gbkt-backend-gbdk:test --quiet` → exit 0
- `:gbkt-examples:banks:buildRom --quiet` → exit 0 (regression preserved)
- `grep -c 'buildIsTileSolidHelperIfNeeded' GBDKPipelineV2.kt` → 2 (≥ 2 threshold met)
- `grep -c 'gameUsesTilemapCollision\|buildTilemapCollisionGlobals' GBDKPipelineV2.kt` → 11 (≥ 2 threshold exceeded)

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
