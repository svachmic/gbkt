---
phase: 04-analysis-pass-pipeline
plan: 09
subsystem: analysis
tags: [vram, tileset, SceneIR, VRAMLayoutPass, budgetReport, GameIR]

# Dependency graph
requires:
  - phase: 04-analysis-pass-pipeline
    provides: VRAMLayoutPass, BudgetReportTask, SceneIR with bankSlot
provides:
  - SceneIR.tilesetRef field wiring background tileset into IR
  - VRAMLayoutPass.estimateBgTiles reads tilesetRef for 256-tile heuristic estimate
  - BG-tile-driven VRAM overflow is reachable and covered by tests
  - Example build.gradle.kts files reference v2 GameIR properties (pongV2, breakoutV2, explorerV2)
affects: [phase-05-vblank-and-asset-pipeline, VRAMLayoutPass, BudgetReportTask]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "tilesetRef null-default on data class field pattern — all existing call sites unaffected; new functionality opt-in"
    - "BG_TILES_DEFAULT_ESTIMATE constant pattern — heuristic value named at companion scope, documented for Phase 5 refinement"

key-files:
  created: []
  modified:
    - gbkt-examples/pong/build.gradle.kts
    - gbkt-examples/breakout/build.gradle.kts
    - gbkt-examples/explorer/build.gradle.kts
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SceneIR.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SceneBuilder.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/IRHierarchyTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPassTest.kt

key-decisions:
  - "BG_TILES_DEFAULT_ESTIMATE = 256 — conservative heuristic for any non-null tilesetRef; actual tile count deferred to Phase 5 asset pipeline file I/O"
  - "estimateBgTiles retains game parameter with @Suppress UnusedParameter for Phase 5 refinement (tileset metadata lookup)"
  - "tilesetRef placed before bankSlot in SceneIR field order — logical grouping: asset fields before platform annotation fields"

patterns-established:
  - "Null-defaulted IR fields enable forward compatibility — add a field to SceneIR without touching any existing call sites"

requirements-completed: [ANLZ-03, ANLZ-06]

# Metrics
duration: 3min
completed: 2026-02-18
---

# Phase 4 Plan 9: Gap Closure — v2 Game Wiring and tilesetRef Summary

**SceneIR.tilesetRef added and wired into VRAMLayoutPass with 256-tile heuristic; all three example builds now reference v2 GameIR properties closing the budgetReport output gap**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-18T22:04:35Z
- **Completed:** 2026-02-18T22:08:09Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- All three example `build.gradle.kts` files now reference `PongV2Kt::pongV2`, `BreakoutV2Kt::breakoutV2`, and `ExplorerV2Kt::explorerV2` — `BudgetReportTask.resolveGameIR()` can now receive a real `GameIR` for each example game
- `SceneIR` gained a `tilesetRef: AssetRef?` field (null default) with DSL support via `SceneBuilder.tileset(path)` — all existing `SceneIR(...)` call sites unchanged
- `VRAMLayoutPass.estimateBgTiles()` now reads `scene.tilesetRef`, returning `BG_TILES_DEFAULT_ESTIMATE = 256` when non-null — BG-tile VRAM overflow is now structurally reachable
- Two new tests in `VRAMLayoutPassTest` prove BG-tile overflow triggers ERROR with scene name + background tile breakdown, and that a scene within budget passes with correct `VRAMRange`

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire v2 game definitions into example build files and add tilesetRef to SceneIR** - `838156b` (feat)
2. **Task 2: Wire estimateBgTiles to read tilesetRef and add BG-tile overflow test** - `8655d6d` (feat)

## Files Created/Modified

- `gbkt-examples/pong/build.gradle.kts` - Changed game ref from `PongKt::pong` to `PongV2Kt::pongV2`
- `gbkt-examples/breakout/build.gradle.kts` - Changed game ref from `BreakoutKt::breakout` to `BreakoutV2Kt::breakoutV2`
- `gbkt-examples/explorer/build.gradle.kts` - Changed game ref from `ExplorerKt::explorer` to `ExplorerV2Kt::explorerV2`
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/SceneIR.kt` - Added `tilesetRef: AssetRef? = null` field with KDoc
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/SceneBuilder.kt` - Added `tilesetRef` property and `tileset(path)` DSL function
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt` - Replaced `estimateBgTiles` body with tilesetRef logic; added `BG_TILES_DEFAULT_ESTIMATE` constant
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/v2/IRHierarchyTest.kt` - Updated `SceneIR has correct fields` and `SceneIR has default empty collections` to assert tilesetRef
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPassTest.kt` - Added two new BG-tile tests

## Decisions Made

- `BG_TILES_DEFAULT_ESTIMATE = 256` — conservative heuristic for any non-null tilesetRef; Phase 5 asset pipeline will refine with actual file I/O
- `estimateBgTiles` retains `game` parameter with `@Suppress UnusedParameter` annotation to make future Phase 5 tileset metadata lookup easier without another API change
- `tilesetRef` field placed before `bankSlot` in `SceneIR` — logical ordering: asset-level fields before platform annotation fields

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 4 success criteria now 5/5: all gaps closed
- `BG_TILES_DEFAULT_ESTIMATE = 256` is a placeholder; Phase 5 should refine via actual PNG metadata at analysis time
- All three example games can now produce budget reports via `./gradlew budgetReport`

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*
