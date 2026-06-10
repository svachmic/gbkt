---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 10
subsystem: codegen-backend-gbdk + genre-platformer
tags: [gbdk, platformer, tilemap-collision, nonbanked, switch_rom, home-bank, codegen, camera, column-scroll]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "is_tile_solid() HOME-bank helper template (12-08) + gameUsesTilemapCollision(gameIR) gate predicate shape (12-08); ZoneIR.platformerPhysicsOverride (12-05); PlatformerPhysicsConfig.solidThreshold (12-06)"
provides:
  - "buildSetLevelSubmapHelperIfNeeded(gameIR): String? — emits HOME-bank NONBANKED _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) helper as a raw section with SWITCH_ROM(_current_area_bank) entry + SWITCH_ROM(_previous_bank) exit wrapping set_bkg_submap()"
  - "Header prototype 'void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED;' emitted to game.h when the gate fires"
  - "PlatformerVisitor.gameUsesTilemapCollision(gameIR): Boolean — direct (non-reflective) predicate mirroring GBDKPipelineV2's version exactly"
  - "PlatformerVisitor.visitCamera now threads gameIR; emits 4 new WRAM globals (_camera_x UINT16, _old_camera_x UINT16, _map_pos_x UINT8, _old_map_pos_x UINT8) alongside the existing _cam_x INT8 family when gameUsesTilemapCollision(gameIR) == true"
affects:
  - 12-11  # column-scroll codegen branch emits CALLS into _bkg_set_level_submap_banked() and reads/writes _camera_x / _old_camera_x / _map_pos_x / _old_map_pos_x
  - 12-12  # per-function emission invariant test for _bkg_set_level_submap_banked (declaration column 0; exactly 2 SWITCH_ROM; calls set_bkg_submap)

# Tech tracking
tech-stack:
  added: []  # No new libraries; purely additive codegen surface
  patterns:
    - "Gated codegen via shared gameUsesTilemapCollision(gameIR) predicate — same gate, same shape, same justification as the is_tile_solid helper (Plan 12-08). The new helper, the new prototype, and the 4 new WRAM globals all flip together — no partial-emission states."
    - "rawSection escape hatch for the NONBANKED keyword (typed C AST has no NONBANKED state). Mirrors buildIsTileSolidHelperIfNeeded (Plan 12-08) and the SWITCH_ROM CRawCode usage inside buildBkgTilesLoadBankedHelper at line 1938."
    - "Twin predicates: GBDKPipelineV2 uses Java reflection on physicsConfig because the backend module does not depend on gbkt-genre-platformer; PlatformerVisitor uses a direct cast (PlatformerPhysicsConfig?.solidThreshold) because the genre module owns that type. TODO: consolidate in Phase 13 via a shared utility."

key-files:
  created: []
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"
    - "gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt"

key-decisions:
  - "Emit _bkg_set_level_submap_banked() as a rawSection (not a typed CFunction): the typed C AST has no NONBANKED keyword. Adding it would expand CFunction + CEmitter for a single call site. The rawSection path is the documented precedent from Plan 12-08 (is_tile_solid) and from the SWITCH_ROM CRawCode usage in buildBkgTilesLoadBankedHelper (line 1938) — both deal with GBDK macros / keywords that fall outside the typed AST."
  - "Manual game.h prototype is required because the auto-prototype extraction (homeFile.functions → toPrototype) cannot see raw-section function definitions. Without the prototype, the banked column-scroll caller (Plan 12-11) would link-fail at lcc."
  - "Use UINT16 for _camera_x / _old_camera_x even though _cam_x is INT8: tilemap-collision levels routinely exceed 256 px wide (the platformer_template reference uses uint16_t to safely index columns across multi-screen levels). RESEARCH § Pitfall 3 documents the INT8 overflow class of bug. UINT8 for _map_pos_x / _old_map_pos_x is sufficient because they index into the tilemap column index, not a pixel coordinate (max 255 columns = 2040 px, well above any practical level)."
  - "Duplicate gameUsesTilemapCollision() between GBDKPipelineV2 (reflective) and PlatformerVisitor (direct) rather than introducing a cross-module shared utility right now. The duplication is consciously documented with a TODO. Phase 13 should consolidate once we have ≥3 call sites; doing it here would force a new shared-module decision (where does the predicate live?) and is out of scope for a Wave-5 plan dedicated to D-13."

patterns-established:
  - "Twin-predicate pattern (reflective in backend + direct in genre) for genre-config gates that need to fire from both layers without coupling the backend to the genre"
  - "Single-gate helper + globals + prototype emission for byte-identical regression in opt-out games (extended from Plan 12-08 to Plan 12-10 — same shape, just a new pair of emissions)"

requirements-completed: [D-13]

# Metrics
duration: 6min
completed: 2026-05-21
---

# Phase 12 Plan 10: _bkg_set_level_submap_banked() HOME helper + 4 tilemap-camera WRAM globals Summary

**HOME-bank NONBANKED `_bkg_set_level_submap_banked(x, y, w, h)` codegen + 4 new WRAM globals (`_camera_x` UINT16, `_old_camera_x` UINT16, `_map_pos_x` UINT8, `_old_map_pos_x` UINT8) gated on tilemap-collision presence — D-13 foundation for the column-scroll codegen in Plan 12-11.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-05-21T20:56:01Z
- **Completed:** 2026-05-21T21:02:40Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `buildSetLevelSubmapHelperIfNeeded(gameIR): String?` in GBDKPipelineV2 — gated on the existing `gameUsesTilemapCollision(gameIR)` predicate; emits the NONBANKED helper as a raw section alongside `is_tile_solid()`.
- Helper body matches the contract for Plan 12-12's emission invariant test exactly:
  - Function declaration starts with `void _bkg_set_level_submap_banked` at column 0.
  - Body contains exactly 2 `SWITCH_ROM` invocations (entry `SWITCH_ROM(_current_area_bank)` + exit `SWITCH_ROM(_previous_bank)`).
  - Body calls `set_bkg_submap(x, y, w, h, _current_level_map, (UINT8)_current_level_width_in_tiles)`.
- Wired the helper into `buildHomeFile.allRawSections` (after the is_tile_solid raw section — both share the same gate, so they emit together).
- Wired the matching prototype `"void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED;"` into `buildHeaderFile.rawSections` so the banked Plan 12-11 column-scroll caller can link cross-bank.
- Added `gameUsesTilemapCollision(gameIR): Boolean` predicate to PlatformerVisitor (direct cast to `PlatformerPhysicsConfig?.solidThreshold`; mirrors GBDKPipelineV2's reflective version exactly).
- Threaded `gameIR` into `visitCamera()` (signature: `visitCamera(config: Map<String, Any>, gameIR: GameIR)`) and updated the dispatch in `visit()`.
- Emitted the 4 new WRAM globals (`_camera_x`, `_old_camera_x`, `_map_pos_x`, `_old_map_pos_x`) alongside the existing `_cam_x` INT8 family, gated on `gameUsesTilemapCollision(gameIR)`.
- Verified gate-off behaviour: `:gbkt-examples:banks:generateC`, `:gbkt-examples:pong:generateC`, `:gbkt-examples:breakout:generateC` all emit ZERO references to `_bkg_set_level_submap_banked` / `_camera_x` / `_old_camera_x` / `_map_pos_x` / `_old_map_pos_x` — byte-identical regression preserved.
- Verified ROM build still passes: `:gbkt-examples:banks:buildRom` exits 0.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add `_bkg_set_level_submap_banked` HOME-bank NONBANKED helper + header prototype to GBDKPipelineV2** — `b793b845` (feat)
2. **Task 2: Declare tilemap-camera WRAM globals + `gameUsesTilemapCollision` predicate in PlatformerVisitor.visitCamera** — `9f6eb936` (feat)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  - Added private `buildSetLevelSubmapHelperIfNeeded(gameIR): String?` next to `buildIsTileSolidHelperIfNeeded` (Plan 12-08).
  - Wired into `buildHomeFile`'s `allRawSections` `buildList { … }` (after the is_tile_solid raw section).
  - Added prototype `"void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED;"` into `buildHeaderFile`'s `rawSections`.
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
  - Added imports: `CU16` (for new globals) and `GenericSystem` (for the gate predicate).
  - Added private `gameUsesTilemapCollision(gameIR): Boolean` (direct cast to `PlatformerPhysicsConfig?.solidThreshold`).
  - Threaded `gameIR` into `visitCamera()` signature; updated `visit()` dispatch.
  - Appended 4 new WRAM globals to `varDecls` when the gate fires; existing `_cam_x` family continues to emit unconditionally.
  - Added a TODO comment to consolidate the twin predicate (PlatformerVisitor + GBDKPipelineV2) into a shared utility in Phase 13.

## Decisions Made

- **rawSection emission (not typed CFunction):** Same justification as Plan 12-08's `is_tile_solid` helper — the typed C AST does not model the GBDK `NONBANKED` keyword. The rawSection path mirrors `paletteDataRaw`, `metaspriteDescriptorRaw`, and `isTileSolidHelperRaw`. Adding `NONBANKED` to `CFunction` + `CEmitter` for a single new call site would be more invasive than the documented escape hatch.

- **Manual header prototype (not auto-extracted):** Auto-prototype extraction pulls from `homeFile.functions`, which is the typed CFunction list. Raw-section function definitions are invisible to that pass. Without a manual prototype in `buildHeaderFile.rawSections`, the banked Plan 12-11 column-scroll caller would link-fail with "undefined reference to `_bkg_set_level_submap_banked`" at lcc.

- **UINT16 for `_camera_x` / `_old_camera_x`:** Direct application of RESEARCH § Pitfall 3 — the existing `_cam_x` INT8 globals overflow on tilemap-collision levels that exceed 256 px wide, which is the common case (the platformer_template reference uses uint16_t for the same reason). UINT8 is retained for `_map_pos_x` / `_old_map_pos_x` because they index columns (max 255 columns = 2040 px, well above any practical level).

- **Twin predicate (duplicate, not extract):** `GBDKPipelineV2.gameUsesTilemapCollision` uses Java reflection because `gbkt-backend-gbdk` does NOT depend on `gbkt-genre-platformer`. `PlatformerVisitor.gameUsesTilemapCollision` uses a direct cast because the platformer genre module DOES own `PlatformerPhysicsConfig`. The duplication is intentional and is documented with a `TODO(Phase 13)` comment. Extracting to a shared utility right now would force a cross-module shared-module decision (where does the predicate live? a new module? a backend-api callback?) out of scope for a focused Wave-5 plan.

- **Helper inserted right after `is_tile_solid` (both in raw sections and in the header):** Lock-step emission preserves the "all-or-nothing" gate property — if `gameUsesTilemapCollision == true`, both helpers and both prototypes emit; if false, neither emits. Maintains the same byte-identical regression contract that Plan 12-08 established.

## Deviations from Plan

**Minor deviation — global count:** The plan's success_criteria header (line 6 of the prompt's success_criteria block) says "4 new WRAM globals". The plan's must_haves prose at line 29 also lists exactly 4 globals (`_camera_x`, `_old_camera_x`, `_map_pos_x`, `_old_map_pos_x`). However, line 38 of the plan body and the description in the prompt say "5 new WRAM globals for tilemap-camera mode" (likely a typo from the planner — there are only 4 listed). The implementation emits **exactly 4 globals** as enumerated in `must_haves.truths`. This matches the success_criteria header verbatim and is the only consistent reading of the plan. No follow-up needed.

Other than the global-count clarification above, the plan executed exactly as written:
- Task 1: `:gbkt-backend-gbdk:compileKotlin` exits 0; `buildSetLevelSubmapHelperIfNeeded` appears 2× in source (definition + call-site invocation); `:gbkt-backend-gbdk:test` exits 0; `:gbkt-examples:banks:buildRom` exits 0.
- Task 2: `:gbkt-genre-platformer:compileKotlin` exits 0; literal strings `_camera_x` / `_old_camera_x` / `_map_pos_x` / `_old_map_pos_x` all present; `gameUsesTilemapCollision` predicate present (5 occurrences); `:gbkt-genre-platformer:test --rerun-tasks` exits 0.

The plan's prose for Task 1 mentioned constructing the helper "via AST equivalents — mirror buildBkgTilesLoadBankedHelper shape". The implementation instead emits the helper as a raw C string via `rawSections` (NOT a typed CFunction). Justification:
1. The typed AST has NO `NONBANKED` keyword support — `CFunction.isBanked: Boolean` emits `BANKED` or nothing, but there's no third state for `NONBANKED`.
2. Plan 12-12's per-function emission invariant test expects the function declaration to start with `void _bkg_set_level_submap_banked` at column 0 (mirror of Plan 12-09's `awk '/^UINT8 is_tile_solid/' main.c` extraction).
3. Plan 12-08 (predecessor) chose the same rawSection escape hatch for `is_tile_solid()` and is the documented direct template for this plan.

Choosing rawSection over a partial-typed-body keeps the entire helper as one cohesive verbatim mirror of `platformer_template/src/camera.c` lines 30-40, simplifies the eventual Plan 12-12 emission invariant test, and respects the documented `gbkt-backend-gbdk/CLAUDE.md` "raw escape hatch for GBDK-specific constructs". This is an internal implementation choice, not a contract deviation — the plan's must-haves (NONBANKED keyword present; SWITCH_ROM(_current_area_bank) at entry; SWITCH_ROM(_previous_bank) at exit; calls set_bkg_submap; gated identically to is_tile_solid) are ALL satisfied.

## Issues Encountered

None. The implementation followed the plan's `read_first` references (GBDKPipelineV2.kt line 1938, the Plan 12-08 is_tile_solid pattern at lines 2057-2078, and the camera.c lines 30-40 from the platformer_template) without surprises.

## User Setup Required

None — no external service configuration required.

## Threat Mitigations

**T-12-10-01 (Tampering — SWITCH_ROM inside `_bkg_set_level_submap_banked`):** Mitigated by reusing Phase 07.4-30's `buildBkgTilesLoadBankedHelper` save/restore shape verbatim. Body:

```c
UINT8 _previous_bank = CURRENT_BANK;
SWITCH_ROM(_current_area_bank);
set_bkg_submap(x, y, w, h, _current_level_map, (UINT8)_current_level_width_in_tiles);
SWITCH_ROM(_previous_bank);
```

The helper executes from HOME bank (0x0000-0x3FFF, never remapped by the MBC) — the NONBANKED keyword forces SDCC to place it there. The MBC remap of 0x4000-0x7FFF triggered by `SWITCH_ROM(_current_area_bank)` therefore cannot corrupt the instruction stream that contains the helper. Restore via `SWITCH_ROM(_previous_bank)` returns the caller's bank context before the helper returns, so banked callers (Plan 12-11 column-scroll codegen will live in bank1.c) execute resumption code at the correct bank. Same shape, same safety properties as the runtime-green Phase 07.4 reference.

## Next Phase Readiness

**Ready for Plan 12-11 (Wave 5 — column-scroll codegen):** The helper is callable from PlatformerVisitor via the game.h prototype (`void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED;`). The 4 supporting WRAM globals (`_camera_x` / `_old_camera_x` / `_map_pos_x` / `_old_map_pos_x`) are declared. Plan 12-11 needs only to:
1. Emit reads/writes to those 4 globals in the column-scroll branch of `buildCameraUpdateFunction` / `buildSmoothFollowBody`.
2. Emit calls to `_bkg_set_level_submap_banked(x, y, w, h)` at the column boundaries.

**Ready for Plan 12-12 (per-function emission invariant test for `_bkg_set_level_submap_banked`):** The function shape locks the same three-point contract that Plan 12-09 locks for `is_tile_solid`:
- Function declaration starts with `void _bkg_set_level_submap_banked` at column 0 (awk brace-walk friendly).
- Body contains exactly 2 `SWITCH_ROM` invocations (entry + exit).
- Body calls `set_bkg_submap`.

**Existing examples remain byte-identical:** Verified via `:gbkt-examples:banks:generateC` + `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` — none reference `_bkg_set_level_submap_banked` / `_camera_x` / `_old_camera_x` / `_map_pos_x` / `_old_map_pos_x` (grep count = 0 across all main.c files). The gate strictly opts-in.

## Self-Check: PASSED

- `gbkt-backend-gbdk/.../GBDKPipelineV2.kt` exists; contains `buildSetLevelSubmapHelperIfNeeded` (2× — definition + call site)
- `gbkt-backend-gbdk/.../GBDKPipelineV2.kt` contains `_bkg_set_level_submap_banked` literal in both the helper body and the header prototype
- `gbkt-genre-platformer/.../PlatformerVisitor.kt` contains `_camera_x` (3×), `_old_camera_x` (2×), `_map_pos_x` (3×), `_old_map_pos_x` (2×), `gameUsesTilemapCollision` (5×)
- Commit `b793b845` (Task 1) exists in git log
- Commit `9f6eb936` (Task 2) exists in git log
- `:gbkt-backend-gbdk:compileKotlin --quiet` → exit 0
- `:gbkt-backend-gbdk:test --rerun-tasks` → exit 0
- `:gbkt-genre-platformer:compileKotlin --quiet` → exit 0
- `:gbkt-genre-platformer:test --rerun-tasks` → exit 0
- `:gbkt-examples:banks:generateC` → 0 references to new helper/globals in main.c (regression preserved)
- `:gbkt-examples:pong:generateC` → 0 references to new helper/globals in main.c
- `:gbkt-examples:breakout:generateC` → 0 references to new helper/globals in main.c
- `:gbkt-examples:banks:buildRom --quiet` → exit 0

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
