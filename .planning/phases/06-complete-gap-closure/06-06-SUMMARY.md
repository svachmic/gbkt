---
phase: 06-complete-gap-closure
plan: 06
subsystem: codegen
tags: [collections, hashtable, pool, ring-buffer, fixed-slots, wram, codegen, analysis]

# Dependency graph
requires:
  - phase: 06-02
    provides: GameIR type with collection list fields already wired
  - phase: 06-01
    provides: CollectionCodegen interface in gbkt-backend-api
provides:
  - GBDKCollectionCodegen implementing all 8 CollectionCodegen interface methods
  - CFile.rawSections field for injecting file-scope raw C blocks
  - Collection memory accounting in ResourceInventoryPass
  - Collections section in BudgetReporter
affects: [06-07, 06-09]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CFile.rawSections: inject raw C code between variable declarations and function definitions"
    - "generateAllCollections(): returns (dataRaw, functionsRaw) pair for pipeline integration"
    - "VarType.cName: extension property mapping IR types to GBDK C type names"

key-files:
  created:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCollectionCodegen.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCollectionCodegenTest.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFile.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassContext.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/report/BudgetReporter.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPassTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/IRHierarchyTest.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt

key-decisions:
  - "CFile.rawSections used for collection code injection instead of extending the typed C AST — collections require 3+ parallel arrays per instance which don't fit cleanly into CVarDecl"
  - "VarType.cName defined as internal extension in GBDKCollectionCodegen to keep GBDK type names isolated in the backend module"
  - "generateAllCollections() returns (dataRaw, functionsRaw) pair so pipeline can place data after variable declarations and functions with scene functions"
  - "Fixed-slots bitfield uses UINT8 for count <= 8, UINT16 for 9-16 matching GBDK hardware register widths"

patterns-established:
  - "Collection codegen pattern: data (static arrays) + functions (insert/lookup/clear etc.) as separate raw C strings"
  - "CFile.rawSections emitted between variables and functions by CEmitter"

requirements-completed: [COLL-D1, COLL-D2, COLL-D3]

# Metrics
duration: 45min
completed: 2026-02-21
---

# Phase 06 Plan 06: Collection Codegen Summary

**GBDKCollectionCodegen with 8 interface methods generating C arrays and functions for hash tables, object pools, ring buffers, and fixed-slot collections, wired through CFile.rawSections into GBDKPipelineV2, with RAM accounting in ResourceInventoryPass**

## Performance

- **Duration:** 45 min
- **Started:** 2026-02-21T13:00:00Z
- **Completed:** 2026-02-21T13:45:00Z
- **Tasks:** 1 (atomic implementation of D1+D2+D3)
- **Files modified:** 8

## Accomplishments

- `GBDKCollectionCodegen` implements all 8 `CollectionCodegen` interface methods: data and function generation for hash tables, object pools, ring buffers, and fixed-slot collections
- `CFile.rawSections: List<String>` added to the typed C AST to inject file-scope raw C blocks; `CEmitter` emits them between variable declarations and function definitions
- `ResourceInventoryPass` now computes actual `collectionBytes` from `GameIR` collection lists using per-type byte formulas (hash table: `N * (keySize + valueSize + 1)`, pool: `N * elemSize + ceil(N/8) + 1`, ring buffer: `N * elemSize + 3`, fixed slots: `N * elemSize + bitfieldSize`)
- `BudgetReporter` shows a Collections section when `collectionBytes > 0`, with per-collection breakdown

## Task Commits

1. **Task 1: Implement GBDKCollectionCodegen and wire collections** - `0f0df24` (feat)
2. **Chore: Spotless and accumulated changes** - `15ff5d2` (chore)
3. **Chore: Add ActorVisitorTest** - `b83600f` (chore)

## Files Created/Modified

- `gbkt-backend-gbdk/.../GBDKCollectionCodegen.kt` — 8-method CollectionCodegen implementation with `generateAllCollections()` helper and `VarType.cName` extension
- `gbkt-backend-gbdk/.../ast/CFile.kt` — Added `rawSections: List<String> = emptyList()` field
- `gbkt-backend-gbdk/.../GBDKCollectionCodegenTest.kt` — Tests for all 4 collection types and pipeline integration
- `gbkt-analysis/.../PassContext.kt` — Updated `collectionBytes` KDoc to reflect actual computation
- `gbkt-analysis/.../report/BudgetReporter.kt` — Added Collections section when `collectionBytes > 0`
- `gbkt-analysis/.../passes/ResourceInventoryPassTest.kt` — 7 new collection memory accounting tests
- `gbkt-core/.../ir/IRHierarchyTest.kt` — Added `visitCast` to `ExprDescriber` (Rule 3 fix)
- `gbkt-backend-gbdk/.../visitor/ScriptOpVisitor.kt` — Reverted `MusicPause`/`MusicResume` to `CLiteral` to match test expectations

## Decisions Made

- **CFile.rawSections vs typed AST extension:** Collections require 3+ parallel arrays per instance (keys, values, used flags for hash table). Adding a typed collection node to the C AST would require significant AST changes. `rawSections: List<String>` provides a pragmatic injection point without coupling the AST to collection semantics.
- **`VarType.cName` internal to GBDKCollectionCodegen:** The GBDK C type names (`UINT8`, `UINT16`, `INT8`, `INT16`) are backend-specific. Defining `cName` as `internal` in the GBDK module keeps GBDK naming out of the IR module.
- **Fixed-slots bitfield width:** UINT8 for count <= 8 (1 byte), UINT16 for 9-16 (2 bytes). Matches GameBoy register access patterns and keeps memory tight.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing `visitCast` implementation in IRHierarchyTest.ExprDescriber**
- **Found during:** Task 1 (full build verification)
- **Issue:** `ExprVisitorI` gained a `visitCast(expr: CastExpr): T` method, but `ExprDescriber` in `IRHierarchyTest.kt` (a test-only implementation) was not updated. Compilation of `gbkt-core:compileTestKotlin` failed.
- **Fix:** Added `override fun visitCast(expr: CastExpr): String = "CastExpr(${expr.targetType})"` to `ExprDescriber`
- **Files modified:** `gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/IRHierarchyTest.kt`
- **Verification:** Full build passes
- **Committed in:** `0f0df24` (Task 1 commit)

**2. [Rule 1 - Bug] Detekt ReturnCount violation in `containsMusicOp`**
- **Found during:** Task 1 (full build, detekt phase)
- **Issue:** `GBDKPipelineV2.containsMusicOp()` had 7 return statements exceeding Detekt's limit of 4 (ReturnCount rule)
- **Fix:** Refactored to use `ops.any { op -> when(op) { ... } }` pattern (single return via `any`)
- **Files modified:** `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt`
- **Verification:** Detekt passes, `hasMusicOps` logic preserved
- **Committed in:** `0f0df24` (Task 1 commit)

**3. [Rule 1 - Bug] MusicPause/MusicResume test expectations mismatch**
- **Found during:** Task 1 (test execution)
- **Issue:** `MusicCodegenTest` (untracked file from prior session) expected `hUGE_set_pause(1u)` (CLiteral emits unsigned suffix), but implementation had been temporarily changed to `CRawCode("hUGE_set_pause(1);")` which emits `1` without `u`
- **Fix:** Reverted `ScriptOpVisitor.visitMusicPause/visitMusicResume` to use `CExprStatement(CCall("hUGE_set_pause", listOf(CLiteral(1))))` matching the test assertions
- **Files modified:** `gbkt-backend-gbdk/.../visitor/ScriptOpVisitor.kt`
- **Verification:** MusicCodegenTest passes
- **Committed in:** `0f0df24` (Task 1 commit)

**4. [Rule 1 - Bug] Spotless formatting violations in accumulated untracked files**
- **Found during:** Task 1 (full build, spotless check)
- **Issue:** Multiple files modified in prior plan sessions had accumulated spotless formatting violations
- **Fix:** `./gradlew spotlessApply` applied formatting
- **Files modified:** BitwiseOptimizationPass, BudgetReporter, BudgetReporterTest, ActorVisitor, SimpleBattleAndTilesetTest, BreakoutIRTest, BreakoutV2, ExplorerV2, various package-info.kt files
- **Verification:** `spotlessCheck` passes in full build
- **Committed in:** `15ff5d2` (chore commit)

---

**Total deviations:** 4 auto-fixed (1 missing interface method, 1 Detekt violation, 1 test expectation mismatch, 1 spotless batch)
**Impact on plan:** All auto-fixes necessary for build correctness. No scope creep.

## Issues Encountered

- **CFile rawSections injection ordering:** The pipeline needed to split collection code into data (before function definitions) and functions (alongside scene functions). The `generateAllCollections()` helper returns a `(dataRaw, functionsRaw)` pair to support this separation cleanly.
- **UP-TO-DATE test caching:** Gradle's incremental build showed tests as UP-TO-DATE despite code changes. Required `./gradlew :module:clean :module:test` to force re-execution.

## Next Phase Readiness

- Collection codegen pipeline is complete: DSL → `GameIR.hashTables/pools/ringBuffers/fixedSlots` → `GBDKCollectionCodegen` → `CFile.rawSections` → C output
- RAM accounting includes collection bytes, visible in BudgetReporter output
- Build passes across all modules including detekt and spotless

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
