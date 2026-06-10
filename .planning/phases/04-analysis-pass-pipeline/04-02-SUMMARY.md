---
phase: 04-analysis-pass-pipeline
plan: 02
subsystem: analysis
tags: [kotlin, analysis-pass, semantic-validation, resource-inventory, constraint-check, tdd, gbkt-analysis]

# Dependency graph
requires:
  - phase: 04-analysis-pass-pipeline
    plan: 01
    provides: gbkt-analysis module skeleton with AnalysisPass, PassPipeline, PassContext, Diagnostic, AnalysisConfig

provides:
  - SemanticValidationPass: detects duplicate scene/actor/variable IDs, dangling startScene, dangling actorIds in scenes
  - ResourceInventoryPass: populates PassContext.inventory with totalActors/scenes/variables/assets, spriteTileCounts (w/8*h/8), variableBytes (U8/I8=1, U16/I16=2), perSceneActorCounts
  - ConstraintCheckPass: validates totalActors vs profile.sprites.maxSprites, variableBytes+collectionBytes vs profile.memory.workRam
  - Updated ResourceInventory data class with full field set (replaces stub from plan 01)

affects:
  - 04-03 through 04-05 (allocation passes consume inventory counts and expect semantic validation to have run first)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - TDD pass pattern: write failing tests with stub class, then implement to pass
    - Private helper method extraction for LongMethod compliance in analysis passes
    - requireNotNull() guard at pass entry for prerequisite inventory validation
    - filterValues { it > 0 } to exclude zero-count scenes from perSceneActorCounts map

key-files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPassTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPassTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPassTest.kt
  modified:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassContext.kt (ResourceInventory fleshed out from stub)

key-decisions:
  - "SemanticValidationPass.run() refactored into 5 private helpers — detekt LongMethod threshold is 80 lines and the flat implementation was 83; extraction improves readability as a side effect"
  - "ResourceInventory.collectionBytes is always 0 in v2 pipeline — v2 GameIR does not carry collection IR fields (they are in v1 Game model); documented as TODO comment in ResourceInventoryPass"
  - "ConstraintCheckPass uses requireNotNull(context.inventory) — hard fail at runtime if ResourceInventoryPass did not run before; explicit prerequisite enforcement preferred over silent null handling"
  - "perSceneActorCounts filters zero-count scenes — scenes with no actorIds are not useful in the constraint map and would add noise"

patterns-established:
  - "TDD stub pattern: create minimal stub returning Success to allow compilation, run RED, then replace with implementation for GREEN"
  - "Analysis pass private helpers: break run() into check* private methods for LongMethod compliance"
  - "Diagnostic id='ANLZ-01' for semantic issues, 'ANLZ-02' for OAM, 'ANLZ-03' for WRAM — consistent error code namespace"

requirements-completed:
  - ANLZ-01

# Metrics
duration: 7min
completed: 2026-02-18
---

# Phase 4 Plan 02: Three Analysis Passes Summary

**SemanticValidationPass (duplicate/dangling refs), ResourceInventoryPass (tile counts, variable bytes, per-scene actors), and ConstraintCheckPass (OAM limit, WRAM limit) — 35 tests passing, v2 pipeline validation foundation complete**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-18T20:36:11Z
- **Completed:** 2026-02-18T20:43:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Implemented `SemanticValidationPass` with 5 checks (duplicate scene/actor/variable IDs, dangling startScene, dangling actorId refs) producing ANLZ-01 diagnostics with descriptive messages and suggestions
- Implemented `ResourceInventoryPass` computing tile counts per actor `(width/8)*(height/8)`, variable bytes per type, per-scene actor counts, and total resource counts
- Implemented `ConstraintCheckPass` comparing actor count vs `profile.sprites.maxSprites` and RAM usage vs `profile.memory.workRam` with configurable warning/error thresholds
- Updated `ResourceInventory` data class from 4-field stub to 8-field production model; TODO comment documents v2/v1 collection gap

## Task Commits

Each task was committed atomically:

1. **Task 1: SemanticValidationPass with TDD** - `639d874` (feat)
2. **Task 2: ResourceInventoryPass and ConstraintCheckPass with TDD** - `7bf4be1` (feat)

## Files Created/Modified

- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassContext.kt` - ResourceInventory expanded from stub to 8-field data class
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt` - Validates cross-references and structural correctness
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPass.kt` - Walks GameIR to compute ResourceInventory
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt` - Validates against hardware limits from TargetProfile
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPassTest.kt` - 7 tests for semantic checks
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPassTest.kt` - 7 tests for inventory computation
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPassTest.kt` - 6 tests for constraint validation

## Decisions Made

- `SemanticValidationPass.run()` refactored into 5 private helpers (`collectDuplicateSceneIds`, `collectDuplicateActorIds`, `collectDuplicateVariableNames`, `checkStartScene`, `checkDanglingActorRefs`) — detekt LongMethod threshold is 80 lines; flat implementation was 83 lines after spotless formatting
- `ResourceInventory.collectionBytes` always 0 in v2 pipeline — documented as TODO comment; v2 `GameIR` has no collection fields (v1 `Game` model carries `collPools`, `collHashTables`, etc.)
- `ConstraintCheckPass` uses `requireNotNull(context.inventory)` — hard fail at runtime if prerequisites not met; explicit over silent

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Refactored SemanticValidationPass to satisfy detekt LongMethod rule**
- **Found during:** Task 1 (full `./gradlew :gbkt-analysis:build` verification)
- **Issue:** `SemanticValidationPass.run()` was 83 lines; detekt LongMethod threshold is 80
- **Fix:** Extracted 5 private helper methods; each check became its own method; `run()` reduced to ~15 lines
- **Files modified:** `SemanticValidationPass.kt`
- **Verification:** `./gradlew :gbkt-analysis:build` passes with detekt clean
- **Committed in:** `7bf4be1` (included in Task 2 commit since discovered during Task 2 build check)

---

**Total deviations:** 1 auto-fixed (1 bug — detekt LongMethod violation)
**Impact on plan:** Auto-fix improved readability; no scope creep. Both passes work identically.

## Issues Encountered

None beyond the auto-fixed deviation above.

## Next Phase Readiness

- Three passes form the prerequisite chain for plans 04-03 through 04-05 (allocation passes)
- `SemanticValidationPass` must run first (validates structural correctness)
- `ResourceInventoryPass` must run second (populates inventory for constraint/allocation passes)
- `ConstraintCheckPass` must run third (validates counts against hardware limits before allocation)
- `collectionBytes` gap is documented as TODO — can be wired in a future plan when v2 GameIR gains collection IR fields

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*
