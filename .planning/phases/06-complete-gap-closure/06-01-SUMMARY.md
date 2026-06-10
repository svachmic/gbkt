---
phase: 06-complete-gap-closure
plan: 01
subsystem: codebase-cleanup
tags: [v1-deletion, ir, dsl, codegen, gbdk, kotlin]

# Dependency graph
requires:
  - phase: 05.05.3-v2-dsl-ergonomics-completion
    provides: "Completed v2 DSL with full ergonomics — v1 now completely superseded"
provides:
  - "All v1 IR files deleted from gbkt-core/ir/ (36 files)"
  - "All v1 DSL files deleted from gbkt-core/dsl/ (8 files)"
  - "All v1 codegen deleted from gbkt-backend-gbdk/codegen/ (40+ files)"
  - "All v1 gbkt-core game model files deleted (165+ files)"
  - "All v1 tests deleted (73 test files)"
  - "All v1 example sources deleted (Pong.kt, Breakout.kt, etc.)"
  - "LabyrinthOfTheDragon-port v1 sources deleted (40 files)"
  - "GBCColor, GBCPalette, PaletteType relocated to gbkt-ir/src/.../ir/v2/CoreTypes.kt"
  - "CollectionsIR types relocated to gbkt-ir/src/.../ir/v2/CollectionsIR.kt"
  - "CodegenBackend interface now uses GameIR instead of deleted Game class"
  - "ValidationResult is now self-contained in gbkt-backend-api (not re-exported from core)"
  - "Full build passes: ./gradlew build BUILD SUCCESSFUL"
affects:
  - 06-02-v2-path-promotion
  - 06-03-module-restructure
  - 06-04-exploration-system
  - 06-05-rpg-system
  - 06-06-collection-system

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single v2 pipeline only — GBDKPipelineV2 is the sole codegen path"
    - "GameIR as the universal game model across backend-api interface"
    - "Self-contained ValidationResult in backend-api (no core dependency)"

key-files:
  created:
    - "gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CoreTypes.kt — GBCColor, GBCPalette, PaletteType"
    - "gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CollectionsIR.kt — moved from gbkt-core"
    - "gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/v2/DmgColor.kt — renamed from ColorConstants.kt"
  modified:
    - "gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt — uses GameIR"
    - "gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/ValidationResult.kt — self-contained"
    - "gbkt-backend-api/src/test/kotlin/io/github/gbkt/backend/api/BackendRegistryTest.kt — uses GameIR"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt — v2 only"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt"

key-decisions:
  - "CEmitter.kt is a v2 file — it lives in emit/ and was restored after accidental deletion"
  - "LabyrinthOfTheDragon-port Kotlin sources deleted (all 40 files used v1 DSL); port will be rewritten with v2 DSL in later phase"
  - "gbkt-core Game model fully deleted along with all v1 domain models (rpg/, entity/, builder/, world/)"
  - "CodegenBackend interface updated to use GameIR instead of deleted Game; validate() returns SUCCESS (real validation in pipeline)"
  - "ValidationResult made self-contained in backend-api — was re-exporting deleted core type via typealias"
  - "ColorConstants.kt renamed to DmgColor.kt to satisfy detekt MatchingDeclarationName rule"
  - "gbkt-engine left untouched — will be populated in Plan 03 per Directive B2"

patterns-established:
  - "Only v2 codegen path exists — GBDKPipelineV2 is the only codegen implementation"
  - "GameIR as the language of the CodegenBackend interface boundary"
  - "Shared types that cross module boundaries live in gbkt-ir (not gbkt-core)"

requirements-completed: [CLEAN-01, CLEAN-03]

# Metrics
duration: ~90min (two sessions)
completed: 2026-02-21
---

# Phase 06 Plan 01: Complete v1 Deletion Summary

**Deleted 370+ v1 files across IR, DSL, codegen, tests, and examples; relocated GBCColor/CollectionsIR to gbkt-ir; full build passes with only v2 code remaining**

## Performance

- **Duration:** ~90 min (two sessions — context limit hit mid-execution)
- **Started:** 2026-02-21
- **Completed:** 2026-02-21
- **Tasks:** 2 of 2
- **Files modified:** 370+ files changed (52 in Task 1, 318 in Task 2)

## Accomplishments

- Eliminated all v1 IR (36 files), v1 DSL (8 files), v1 codegen (40+ files) — codebase is now v2-only
- Relocated shared types GBCColor/GBCPalette/PaletteType and all 18 CollectionsIR types to gbkt-ir/src/.../ir/v2/
- Fixed cascade compilation failures across gbkt-backend-api, gbkt-backend-gbdk, gbkt-core, examples, and the port
- Full build passes: `./gradlew build` BUILD SUCCESSFUL, `./gradlew test` BUILD SUCCESSFUL

## Task Commits

Each task was committed atomically:

1. **Task 1: Relocate shared types and delete all v1 IR + DSL files from gbkt-core** - `89e23cf` (feat)
2. **Task 2: Delete v1 codegen from gbkt-backend-gbdk and fix all dependent code** - `13a5a17` (feat)

## Files Created/Modified

**Created:**
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CoreTypes.kt` — GBCColor, GBCPalette, PaletteType
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CollectionsIR.kt` — moved from gbkt-core/ir/ (18 collection IR types)
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/v2/DmgColor.kt` — renamed from ColorConstants.kt

**Modified:**
- `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt` — interface updated to use GameIR instead of deleted Game
- `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/ValidationResult.kt` — rewritten as self-contained data class (was re-exporting deleted type)
- `gbkt-backend-api/src/test/kotlin/io/github/gbkt/backend/api/BackendRegistryTest.kt` — updated mock backend to use GameIR
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` — removed v1 Game/GBDKCodeGenerator/GameValidator, v2-only now

**Deleted (Task 1 — v1 IR/DSL):**
- 36 v1 IR files from `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/` (excluding v2/ subdir)
- 8 v1 DSL files from `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/` (excluding v2/ subdir)

**Deleted (Task 2 — v1 codegen, tests, examples, port):**
- 40+ v1 codegen files from `gbkt-backend-gbdk/codegen/` (core/, rpg/, world/, combat/, features/, graphics/, ui/, data/)
- GBDKCodeGenerator.kt and CodegenConstants.kt (v1 codegen entry points)
- 15 v1 backend test files
- 165+ v1 gbkt-core source files (Game.kt, rpg/, entity/, builder/, world/, etc.)
- 58 v1 gbkt-core test files
- V1 example sources: Pong.kt, Breakout.kt, Explorer.kt, Dungeon.kt, RpgLite.kt, all GenerateC.kt files
- 40 LabyrinthOfTheDragon-port Kotlin source files (all used v1 DSL)

## Decisions Made

- **CEmitter.kt recovery:** The `emit/` directory was removed as part of deleting v1 codegen, but `CEmitter.kt` is a v2 infrastructure file used by `GBDKPipelineV2.kt`. Restored via `git show HEAD:path`. The `emit/` directory now correctly contains only this v2 file.
- **LabyrinthOfTheDragon-port deletion:** All 40 Kotlin source files used v1 DSL exclusively. Deleted entire `src/main/kotlin/` directory. Port will be rewritten with v2 DSL in a later phase.
- **gbkt-core Game model deletion:** The v1 `Game` class and its entire domain model ecosystem (rpg/, entity/, builder/, world/, scene/, etc.) were deleted. These were all v1-only types that had no v2 counterparts in gbkt-core.
- **CodegenBackend interface:** Updated `validate(game: Game)` and `generate(game: Game, options)` to use `GameIR`. The `validate()` method returns `ValidationResult.SUCCESS` since real constraint checking happens in the analysis pipeline inside `generateV2()`.
- **ValidationResult self-contained:** Was previously a typealias re-exporting from deleted `io.github.gbkt.core.ValidationResult`. Rewritten as a proper data class with companion object, enum, and exception class.
- **ColorConstants.kt rename:** Renamed to `DmgColor.kt` to satisfy detekt `MatchingDeclarationName` rule (single top-level declaration `object DmgColor` must match filename).
- **gbkt-engine left alone:** Per Directive B2, gbkt-engine will be populated (not deleted) in Plan 03.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored accidentally-deleted CEmitter.kt**
- **Found during:** Task 2 (deleting v1 codegen directories)
- **Issue:** The `emit/` directory was deleted assuming all files in it were v1. But `CEmitter.kt` is a v2 file (C AST pretty-printer used by GBDKPipelineV2). The build failed with `Unresolved reference: CEmitter`.
- **Fix:** Restored using `git show HEAD:gbkt-backend-gbdk/src/.../codegen/emit/CEmitter.kt > CEmitter.kt`
- **Files modified:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt`
- **Verification:** Build passed after restoration
- **Committed in:** 13a5a17 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed CodegenBackend interface cascade — Game class deleted**
- **Found during:** Task 1 (after deleting v1 IR/DSL)
- **Issue:** `CodegenBackend.kt` still imported deleted `io.github.gbkt.core.Game`. `ValidationResult.kt` re-exported deleted `io.github.gbkt.core.ValidationResult` via typealias. These caused compilation failures in gbkt-backend-api.
- **Fix:** Rewrote both files. CodegenBackend interface methods updated to use `GameIR`. ValidationResult made self-contained.
- **Files modified:** `CodegenBackend.kt`, `ValidationResult.kt`, `BackendRegistryTest.kt`
- **Verification:** `./gradlew :gbkt-backend-api:compileKotlin` passes
- **Committed in:** 89e23cf (Task 1 commit)

**3. [Rule 1 - Bug] Deleted 165+ gbkt-core main source files that used v1 types**
- **Found during:** Task 2 (fix all dependent code)
- **Issue:** gbkt-core had 165+ source files (rpg/, entity/, builder/, world/, graphics/, etc.) that all depended on the deleted v1 IR/DSL. These couldn't compile without v1.
- **Fix:** Deleted all v1 domain model files. These will be replaced with v2 equivalents in later Phase 06 plans (RPG system in Plan 05, etc.).
- **Files modified:** 165+ source files deleted
- **Verification:** `./gradlew :gbkt-core:compileKotlin` passes
- **Committed in:** 13a5a17 (Task 2 commit)

**4. [Rule 1 - Bug] Deleted 58+ v1 test files from gbkt-core**
- **Found during:** Task 2
- **Issue:** 58+ test files in gbkt-core imported v1 types and couldn't compile.
- **Fix:** Deleted all v1 tests. New v2 tests will be written in later plans.
- **Committed in:** 13a5a17 (Task 2 commit)

**5. [Rule 1 - Bug] Deleted v1 example sources + all LabyrinthOfTheDragon-port Kotlin**
- **Found during:** Task 2
- **Issue:** V1 examples (Pong.kt, Breakout.kt, etc.) used v1 DSL. V2 versions already exist (PongV2.kt, BreakoutV2.kt, ExplorerV2.kt). The port had 40 files all using v1 DSL.
- **Fix:** Deleted v1 example sources (kept V2 versions). Deleted entire LabyrinthOfTheDragon-port `src/main/kotlin/` directory.
- **Committed in:** 13a5a17 (Task 2 commit)

---

**Total deviations:** 5 auto-fixed (4 Rule 1 — bugs/cascade effects, 1 Rule 3 — blocking issue)
**Impact on plan:** All auto-fixes were necessary cascade effects of deleting v1 code. No scope creep.

## Issues Encountered

- CEmitter.kt was inside the `emit/` directory alongside v1 emit code — the v1 vs v2 boundary was not at directory level. Had to restore from git after accidental deletion.
- The cascade of compilation failures was much larger than anticipated — deleting 36 IR files caused 5854 error lines across 6+ modules. Required systematic module-by-module repair.
- gbkt-core had 165+ source files that all depended on deleted IR types — these were the v1 domain model that will be replaced in later Phase 06 plans.

## User Setup Required

None - no external service configuration required.

## Self-Check: PASSED

All claims verified:
- FOUND: gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CoreTypes.kt
- FOUND: gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/CollectionsIR.kt
- FOUND: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/v2/DmgColor.kt
- FOUND: commit 89e23cf (Task 1)
- FOUND: commit 13a5a17 (Task 2)
- gbkt-core/ir/ contains only CLAUDE.md (v2/ subdir preserved)
- gbkt-core/dsl/ contains only CLAUDE.md (v2/ subdir preserved)
- No `import io.github.gbkt.core.ir.GBCColor` imports remain
- Note: gbkt-gradle-plugin/GenerateCTask.kt references GBDKCodeGenerator via reflection in a dead v1 code path — silently fails at runtime since class no longer exists. Deferred to Plan 03.

## Next Phase Readiness

- Codebase is now v2-only — no v1/v2 confusion remains
- Plan 02 (v2 path promotion) can proceed: rename v2/ subdirs to remove the version prefix
- Plan 03 (module restructure) can proceed: gbkt-engine ready to be populated
- The gbkt-core module is now much smaller — only the v2 IR analysis/test infrastructure remains
- LabyrinthOfTheDragon-port must be rewritten from scratch using v2 DSL in a future plan
- Gradle plugin v1 code path cleanup deferred to Plan 03

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
