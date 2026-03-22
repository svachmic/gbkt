---
phase: 06-complete-gap-closure
plan: 08
subsystem: analysis, codegen
tags: [bitwise-optimization, analysis-pass, budget-reporter, tile-collision, gbdk-codegen]

# Dependency graph
requires:
  - phase: 06-02
    provides: GBDKPipelineV2 infrastructure and SceneIR v2 types
provides:
  - BitwiseOptimizationPass (F1) — rewrites x*N→x<<log2(N), x/N→x>>log2(N), x%N→x&(N-1)
  - Polished BudgetReporter (F2) — ANSI colors, [====----] percentage bars, per-scene breakdown
  - Tile collision system (G1+G2+G3) — SceneIR.collisionData, _map_collision codegen, exploration wiring
affects: [06-09, future-exploration-games]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Power-of-2 arithmetic rewrite: isPow2/log2 helpers for MUL→SHL, DIV→SHR, MOD→AND
    - ANSI color thresholds: green <75%, yellow 75-90%, red >90%
    - Dispatch pattern for per-scene collision functions via current_scene switch
    - Guard-and-return pattern in exploration_move before position update

key-files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPassTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TileCollisionCodegenTest.kt
  modified:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/pipeline/DefaultPipeline.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/report/BudgetReporter.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/report/BudgetReporterTest.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SceneIR.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt

key-decisions:
  - "BitwiseOptimizationPass applies rewrites unconditionally — Game Boy variables are typically unsigned (U8/U16); assumption documented in diagnostics"
  - "Collision dispatch uses switch(current_scene) not function pointers — C89 GBDK compatibility requires switch-based dispatch"
  - "SceneIR requires manual equals/hashCode — ByteArray has reference equality in data classes; contentEquals/contentHashCode used"
  - "Entity obstacle detection left as TODO comment in exploration_move — entity system completion is a separate plan"
  - "collisionHelperDecls in game.h includes both per-scene and dispatch prototypes — dispatch prototype enables banked code to call _map_collision"

patterns-established:
  - "Collision codegen pattern: per-scene lookup array + per-scene function + dispatch function switching on current_scene"
  - "Exploration guard pattern: compute candidate (nx, ny), check _map_collision(nx, ny), return early if blocked"
  - "ANSI report pattern: colorize(text, pct, ansiEnabled) helper with green/yellow/red thresholds; ansiEnabled=false for plain-text output"

requirements-completed: [COLL-01, COLL-02]

# Metrics
duration: 18min
completed: 2026-02-21
---

# Phase 06 Plan 08: BitwiseOptimizationPass, BudgetReporter Polish, and Tile Collision System Summary

**BitwiseOptimizationPass rewrites power-of-2 arithmetic to bitwise ops; BudgetReporter adds ANSI colors and percentage bars; tile collision system generates _map_collision() and wires it into exploration movement**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-02-21
- **Completed:** 2026-02-21
- **Tasks:** 2 (Task 1: F1+F2 analysis; Task 2: G1+G2+G3 codegen)
- **Files modified:** 9 files across 3 modules

## Accomplishments

- BitwiseOptimizationPass (F1): 11-pass pipeline now includes power-of-2 arithmetic optimizer wired between ConstantFoldingPass and BankingAnalysisPass; 17 tests covering all three rewrite types (MUL→SHL, DIV→SHR, MOD→AND) plus non-rewrite cases
- BudgetReporter (F2): ANSI color-coded output with green/yellow/red thresholds, `[====----]` percentage fill bars, per-scene breakdown table (Scene | Bank | Est. Size | Bank Fill), overall ROM size estimate header
- Tile collision system (G1+G2+G3): SceneIR gets `collisionData: ByteArray?` and `mapWidth: Int?`; GBDKPipelineV2 generates `const UINT8 map_<scene>_collision[]` arrays, per-scene `_map_collision_<scene>(x, y)` lookup functions, and dispatch `_map_collision(x, y)` switching on `current_scene`; GBDKSystemVisitor.visitExplorationSystem replaces the TODO stub with `if (_map_collision(nx, ny)) return;` guard

## Task Commits

Each task was committed atomically:

1. **Task 1: BitwiseOptimizationPass and BudgetReporter polish** - `367c37f` (feat)
2. **Task 1 fix: remove duplicate VarType.byteSize** - `e904a47` (fix)
3. **Task 2: Tile collision system G1+G2+G3** - included in `1b1f3c8`

## Files Created/Modified

- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt` - Power-of-2 arithmetic IR optimizer pass
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/pipeline/DefaultPipeline.kt` - Added BitwiseOptimizationPass after ConstantFoldingPass
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/report/BudgetReporter.kt` - ANSI colors, percentage bars, per-scene breakdown, overall ROM size
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPassTest.kt` - 17 tests for all rewrite cases
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/report/BudgetReporterTest.kt` - Updated and extended tests (14 total)
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SceneIR.kt` - Added collisionData: ByteArray? and mapWidth: Int? with manual equals/hashCode
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` - buildCollisionArrayDecl, buildCollisionFunction, buildCollisionDispatchFunction, updated header prototypes
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt` - Replaced TODO stub with real _map_collision() guard in visitExplorationSystem
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TileCollisionCodegenTest.kt` - 12 tests covering collision array generation, lookup functions, dispatch, header prototypes, and exploration movement wiring

## Decisions Made

- BitwiseOptimizationPass applies rewrites unconditionally (no type annotation required) — Game Boy variables are typically unsigned; documented assumption in each diagnostic
- Collision dispatch uses `switch(current_scene)` not function pointers — C89/GBDK compatibility
- SceneIR manual equals/hashCode necessary since `data class` doesn't implement `contentEquals` for ByteArray fields
- Entity obstacle detection left as TODO comment — entity system completion is a separate future plan
- `collisionHelperDecls` in game.h includes both per-scene and dispatch prototypes so banked code can call `_map_collision`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed duplicate VarType.byteSize extension function**
- **Found during:** Task 1 (BitwiseOptimizationPass)
- **Issue:** `CollectionsIR.kt` re-declared `val VarType.byteSize: Int` extension already defined in `Types.kt:92`, causing Kotlin compilation error "Conflicting declarations"
- **Fix:** Removed the duplicate from `CollectionsIR.kt`, replaced with comment referencing Types.kt
- **Files modified:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CollectionsIR.kt`
- **Verification:** `./gradlew :gbkt-analysis:test` passed after fix
- **Committed in:** e904a47

---

**Total deviations:** 1 auto-fixed (Rule 1 - duplicate declaration bug)
**Impact on plan:** Required for compilation. No scope creep.

## Issues Encountered

- Linter modifications during editing caused one git add attempt to fail — staged files explicitly after each edit to ensure correct commit scope
- Task 2 changes were committed as part of the docs(06-05) plan metadata commit due to session continuity; all functional changes are correctly present in HEAD

## Next Phase Readiness

- All 06-08 requirements complete (COLL-01, COLL-02): collision data can be populated in SceneIR and will generate correct C code
- Phase 06-09 (IntelliJ DX) is already complete (committed separately)
- Exploration games can now use tile collision data from TMX/LDtk assets to block player movement

## Self-Check: PASSED

All created files confirmed present:
- BitwiseOptimizationPass.kt — FOUND
- BitwiseOptimizationPassTest.kt — FOUND
- TileCollisionCodegenTest.kt — FOUND

All task commits confirmed in git log:
- 367c37f (feat: BitwiseOptimizationPass + BudgetReporter) — FOUND
- e904a47 (fix: duplicate VarType.byteSize) — FOUND
- 1b1f3c8 (tile collision system G1+G2+G3) — FOUND

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
