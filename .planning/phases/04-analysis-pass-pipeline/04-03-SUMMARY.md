---
phase: 04-analysis-pass-pipeline
plan: 03
subsystem: analysis
tags: [kotlin, analysis-pass, banking, bin-packing, ffd, scene-locality, tdd, gbkt-analysis]

# Dependency graph
requires:
  - phase: 04-analysis-pass-pipeline
    plan: 02
    provides: SemanticValidationPass, ResourceInventoryPass, ConstraintCheckPass, PassContext with bankAssignments map

provides:
  - BankingAnalysisPass: FFD bin-packing that assigns scene code to ROM banks 1..maxBanks-1
  - Bank 0 (HOME) reservation: no scene code ever placed in bank 0
  - Scene locality tie-breaking: transitioning scenes (via NavigateTo) grouped in same bank
  - Overflow detection: PassResult.Failed with ANLZ-02 diagnostic when code exceeds all banks
  - Fill warning: Diagnostic WARNING when bank fill exceeds 85% threshold
  - bankAssignments populated on PassContext: Map<String, BankSlot> keyed by scene ID

affects:
  - 04-08 (codegen pass reads bankAssignments to emit BANKED C functions and HOME trampolines)
  - 04-05 (RAM layout pass runs after banking allocation is complete)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - FFD (First-Fit-Decreasing) bin-packing for ROM bank allocation
    - Scene locality via NavigateTo transition graph traversal
    - Recursive ScriptOp walker for nested ops (IfOp, WhileOp, ForOp, FadeOp, ShowMenu)
    - PassResult.Failed for hard errors vs Diagnostic WARNING for soft limits

key-files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPassTest.kt
  modified: []

key-decisions:
  - "BankingAnalysisPass uses effectiveCapacity = (16384 * bankFillErrorThreshold).roundToInt() — capacity ceiling respects the error threshold to avoid issuing overflow on borderline cases"
  - "Transition graph covers both forward edges (A navigates to B) and reverse edges (B is the target of A) for locality — ensures bidirectional locality without double-counting"
  - "collectNavigations() recursively walks IfOp/WhileOp/ForOp/FadeOp/ShowMenu — NavigateTo inside nested control flow is still a real transition; flat walk would miss locality edges"
  - "Code unit estimatedBytes uses zero for empty scenes — ensures scenes with no ops don't occupy space while still receiving a bank assignment"

patterns-established:
  - "Analysis pass CodeUnit inner data class: private, holds (id, estimatedBytes) for bin-packing"
  - "findFirstFit() takes preferredBank as hint — tries locality bank first, falls back to sequential scan"
  - "bankOverflowError() returns PassResult.Failed directly — keeps run() main loop clean"
  - "generateBankDiagnostics() separate from packing loop — diagnostic generation decoupled from assignment logic"

requirements-completed:
  - ANLZ-02

# Metrics
duration: 5min
completed: 2026-02-18
---

# Phase 4 Plan 03: BankingAnalysisPass Summary

**FFD bin-packing with scene locality tie-breaking automatically allocates scene code to ROM banks 1..N — bank 0 reserved for HOME, overflow produces ANLZ-02 error, high fill produces WARNING**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-18T20:46:32Z
- **Completed:** 2026-02-18T20:51:44Z
- **Tasks:** 1 (TDD: RED + GREEN commits)
- **Files modified:** 2

## Accomplishments

- Implemented `BankingAnalysisPass` with FFD bin-packing: sorts code units by size descending, assigns each to the first bank 1..maxBanks-1 with sufficient remaining capacity
- Scene locality tie-breaking: builds a NavigateTo transition graph by recursively walking all ScriptOp lists; transitioning scenes prefer co-location in the same bank
- Bank overflow: returns `PassResult.Failed` with ANLZ-02 diagnostic describing which scene overflowed and how many bytes it needs
- Bank fill warning: emits `Severity.WARNING` diagnostic when a bank exceeds 85% fill (configurable via `AnalysisConfig.bankFillWarningThreshold`)
- Bank 0 (HOME) permanently reserved — `findFirstFit()` starts scanning from bank 1; bank 0 is never a candidate
- 8 tests written (RED phase) and all 8 pass (GREEN phase); all 43 total analysis tests pass

## Task Commits

TDD task produced two commits:

1. **RED phase: failing tests + stub** - `c19bf07` (test)
2. **GREEN phase: full implementation** - `ee9b374` (feat)

## Files Created/Modified

- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt` - FFD bin-packing pass, 248 lines including 6 private helper methods
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPassTest.kt` - 8 test cases covering assignment, bank 0 reservation, overflow, warnings, and locality

## Decisions Made

- `effectiveCapacity = (ROM_BANK_SIZE * bankFillErrorThreshold).roundToInt()` — uses the error threshold as the hard capacity ceiling for `findFirstFit`, ensuring the fill warning fires before the overflow error
- Transition graph is bidirectional for locality: reverse edges (B is a navigation target of A) are also checked so B can be placed in A's bank regardless of which was packed first
- `collectNavigations()` recurses into `IfOp`, `WhileOp`, `ForOp`, `FadeOp`, and `ShowMenu` — a `NavigateTo` inside a conditional is still a real scene transition
- Zero-op scenes receive a `BankSlot` assignment (bank 1 with 0 bytes consumed) — avoids missing entries in `bankAssignments` for scenes that exist but have no code yet

## Deviations from Plan

None - plan executed exactly as written.

### Out-of-Scope Issues Deferred

**Detekt violations in VRAMLayoutPass.kt (parallel plan 04-04)**
- **Discovered during:** Full build verification (`./gradlew :gbkt-analysis:build`)
- **Issue:** 4 detekt violations in `VRAMLayoutPass.kt`: `LongParameterList` (7 params), `FunctionOnlyReturningConstant`, and 2 `UnusedParameter` violations in `estimateBgTiles`
- **Not fixed:** That file belongs to plan 04-04; modifying it would violate parallel execution isolation
- **Documented in:** `.planning/phases/04-analysis-pass-pipeline/deferred-items.md`
- **Plan 04-03 detekt status:** Zero violations in `BankingAnalysisPass.kt`

## Issues Encountered

None — implementation matched the plan algorithm exactly. The recursive `collectNavigations()` was a minor elaboration of the plan's `buildTransitionGraph()` description but falls clearly within scope.

## Next Phase Readiness

- `BankingAnalysisPass` populates `bankAssignments` on `PassContext` with one `BankSlot` per scene
- Downstream codegen (plan 04-08) can read `bankAssignments["sceneId"]?.bank` to emit `BANKED` C functions and HOME-bank trampoline stubs
- All four prerequisite passes (Semantic, Inventory, Constraint, Banking) are complete
- Plans 04-05 and 04-06 (RAM layout, OAM allocation) can proceed independently

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*

## Self-Check: PASSED

- FOUND: `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt`
- FOUND: `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPassTest.kt`
- FOUND: `.planning/phases/04-analysis-pass-pipeline/04-03-SUMMARY.md`
- FOUND: commit `c19bf07` (RED phase — failing tests)
- FOUND: commit `ee9b374` (GREEN phase — implementation)
