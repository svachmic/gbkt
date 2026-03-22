---
phase: 04-analysis-pass-pipeline
plan: "04"
subsystem: analysis
tags: [kotlin, vram, analysis-pass, game-boy, tdd, tile-budget]

# Dependency graph
requires:
  - phase: 04-02
    provides: PassContext with vramAssignments map, ResourceInventory with spriteTileCounts
provides:
  - VRAMLayoutPass implementing per-scene VRAM tile allocation with hybrid dedup
  - Tile overflow ERROR diagnostics with scene name, breakdown, and splitting suggestion
  - Per-scene and per-actor VRAMRange entries in PassContext.vramAssignments
  - Warning diagnostics at configurable vramTileWarningThreshold
affects:
  - 04-05 (any future pass consuming vramAssignments)
  - gbkt-backend-gbdk (backend will read vramAssignments during code generation)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "VRAMLayoutPass uses ResourceInventory.spriteTileCounts (not raw ActorIR) for sprite tile counts — inventory is the authoritative source"
    - "estimateBgTiles returns 0 for v2 IR — v2 GameIR has no per-scene tileset refs; documented as future wiring point"
    - "buildTileOverflowError places splitting suggestion in Diagnostic.suggestion field (not just message body)"

key-files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPassTest.kt
  modified: []

key-decisions:
  - "VRAMLayoutPass reads spriteTileCounts from ResourceInventory rather than re-computing from ActorIR — inventory pass is the single source of truth for tile counts"
  - "estimateBgTiles returns 0 for v2 GameIR — v2 IR has no per-scene tileset refs; the heuristic is conservative and correct; backend resolves actual tile counts during codegen"
  - "FONT assets imply 36 fixed global tile slots (FONT_TILE_COUNT constant) subtracted from all scene budgets — matches GBDK standard font tile reservation"
  - "Tile overflow error includes scene name + breakdown by source (sprites from actors, BG tiles, global/fonts) + splitting suggestion per locked decision in plan"

patterns-established:
  - "Prerequisite guard: VRAMLayoutPass returns PassResult.Failed with ANLZ-03 ERROR when inventory is null — explicit fail-fast, same pattern as ConstraintCheckPass"
  - "Diagnostic.suggestion field used for actionable guidance separate from message — message describes the problem, suggestion describes the fix"

requirements-completed:
  - ANLZ-03

# Metrics
duration: 3min
completed: 2026-02-18
---

# Phase 04 Plan 04: VRAMLayoutPass Summary

**Per-scene VRAM tile allocator using hybrid dedup: sprite tiles reserved per-scene from ResourceInventory, font assets claim fixed 36 global tile slots, overflow produces ANLZ-03 errors with scene name, tile breakdown, and splitting suggestion.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-18T20:46:15Z
- **Completed:** 2026-02-18T20:49:30Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments

- VRAMLayoutPass computes per-scene VRAM tile budgets with sprite reservation (per-scene) and global tile accounting (all scenes)
- Tile overflow produces ANLZ-03 ERROR with scene name, breakdown by source (actor sprites, BG, global), and "Consider splitting" suggestion — matches locked decision exactly
- Warning emitted when usage exceeds configurable vramTileWarningThreshold (default 350)
- VRAMRange entries written to PassContext.vramAssignments for each scene and per-actor
- 10 TDD tests cover: empty scene, sprite reservation, budget pass, overflow, splitting suggestion, global font tiles, warning threshold, multi-scene budgets, VRAMRange verification, missing inventory guard
- All 53 analysis module tests pass (10 new + 43 existing)

## Task Commits

1. **Task 1: VRAMLayoutPass with per-scene allocation and TDD** - `7b73070` (feat)

**Plan metadata:** (pending final commit)

## Files Created/Modified

- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt` - Per-scene VRAM tile allocator with sprite reservation, global tile counting, overflow detection, and VRAMRange population
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPassTest.kt` - 10 TDD tests covering all behavior requirements

## Decisions Made

- VRAMLayoutPass reads `spriteTileCounts` from `ResourceInventory` rather than re-computing from `ActorIR` — the inventory pass is the authoritative source for tile counts, avoids duplicate logic
- `estimateBgTiles` returns 0 for v2 GameIR — v2 IR has no per-scene tileset refs (unlike v1 Game model); the heuristic is conservative and correct for the current IR model; backend resolves actual tile counts during code generation
- `FONT_TILE_COUNT = 36` constant for standard Game Boy font reservation — each FONT asset implies 36 globally fixed tiles subtracted from all scene budgets
- `VRAMRange.startTile` and `VRAMRange.endTile` fields used (not `start`/`end` as in plan pseudocode) — actual field names from PlatformAnnotations.kt

## Deviations from Plan

None - plan executed exactly as written. The plan pseudocode used `start`/`end` field names but the actual `VRAMRange` class uses `startTile`/`endTile` — adapted automatically from reading the existing IR types.

## Issues Encountered

None. The plan pseudocode had minor field name discrepancies (`start`/`end` vs `startTile`/`endTile`) that were resolved by inspecting the existing `VRAMRange` data class in `PlatformAnnotations.kt`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- VRAMLayoutPass is complete and populates `PassContext.vramAssignments` for all scenes and actors
- PassContext ready for any additional analysis passes (OAM layout, RAM layout, etc.)
- The `estimateBgTiles` heuristic returns 0 for v2 IR — when v2 SceneIR gains tileset references, `VRAMLayoutPass.estimateBgTiles()` is the extension point to update

## Self-Check: PASSED

- FOUND: `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt`
- FOUND: `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPassTest.kt`
- FOUND: `.planning/phases/04-analysis-pass-pipeline/04-04-SUMMARY.md`
- FOUND: commit `7b73070`
- All 53 analysis module tests pass (10 VRAMLayoutPassTest + 43 existing)

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*
