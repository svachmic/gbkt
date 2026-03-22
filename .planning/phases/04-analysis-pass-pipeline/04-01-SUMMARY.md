---
phase: 04-analysis-pass-pipeline
plan: 01
subsystem: analysis
tags: [kotlin, analysis-pass, pipeline, ir, gbkt-analysis]

# Dependency graph
requires:
  - phase: 03.1-collection-abstractions
    provides: IRColl* collection types and GameIR with collPools field
  - phase: 01-ir-foundation-and-dsl
    provides: GameIR, TargetProfile, CartridgeConfig, BankSlot, VRAMRange, OAMSlot types

provides:
  - gbkt-analysis Gradle module with api(gbkt-backend-api) dependency
  - AnalysisPass fun interface with PassResult sealed hierarchy (Success/Failed)
  - PassPipeline executor with fail-fast and before/after extension hooks
  - PassContext immutable data class accumulating bank/vram/oam/ram/inventory annotations and diagnostics
  - Diagnostic data class with Severity enum (ERROR, WARNING, INFO)
  - AnalysisConfig with MBC bank limits and configurable warning/error thresholds
  - RAMLayout and ResourceInventory data class stubs for subsequent passes

affects:
  - 04-02 through 04-05 (all analysis pass implementations depend on this skeleton)

# Tech tracking
tech-stack:
  added:
    - gbkt-analysis Gradle module (kotlin("jvm"), jvmToolchain(21), api(gbkt-backend-api))
  patterns:
    - fun interface AnalysisPass for lambda-style pass registration
    - Fail-fast pipeline: first PassResult.Failed stops all subsequent passes
    - Immutable PassContext: each pass returns a new copy via .copy()
    - beforePasses + builtInPasses + afterPasses extension hook pattern
    - AnalysisConfig.fromCartridgeConfig factory maps MBC type strings to bank limits

key-files:
  created:
    - gbkt-analysis/build.gradle.kts
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/AnalysisPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/Diagnostic.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassContext.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassPipeline.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/config/AnalysisConfig.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/PassPipelineTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/PassContextTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/TestFixtures.kt
  modified:
    - settings.gradle.kts (added include("gbkt-analysis"))

key-decisions:
  - "gbkt-analysis has no dependency on gbkt-backend-gbdk — analysis is platform-agnostic; backends consume analysis output, not the reverse"
  - "PassPipeline.execute() concatenates all three pass lists before looping — single loop simplifies fail-fast logic at cost of one extra list allocation per pipeline run"
  - "FakeProfile shared in TestFixtures.kt with @file:Suppress(MatchingDeclarationName) — both test files use the same minimal TargetProfile; Kotlin redeclaration error required extracting to shared file"
  - "AnalysisConfig.fromCartridgeConfig uses minOf(typeMax, romBanks) — respects game author's declared bank count; type maximum is a ceiling not a floor"

patterns-established:
  - "Pass ordering pattern: beforePasses + builtInPasses + afterPasses, iterated as a single list"
  - "Immutability pattern: helpers return copy() with changed field, original always unchanged"
  - "FakeProfile test fixture pattern: internal object in TestFixtures.kt, used across all test files in the package"
  - "AnalysisConfig companion factory pattern: fromCartridgeConfig maps config strings to hardware limits"

requirements-completed:
  - ANLZ-01
  - ANLZ-02
  - ANLZ-03
  - ANLZ-04
  - ANLZ-05
  - ANLZ-06

# Metrics
duration: 12min
completed: 2026-02-18
---

# Phase 4 Plan 01: Analysis Pass Pipeline Infrastructure Summary

**gbkt-analysis Gradle module with AnalysisPass fun interface, fail-fast PassPipeline, immutable PassContext, Diagnostic model, and AnalysisConfig with MBC bank limits — the foundation for all 9 analysis passes**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-18T20:20:00Z
- **Completed:** 2026-02-18T20:32:02Z
- **Tasks:** 1
- **Files modified:** 10

## Accomplishments

- Created `gbkt-analysis` Gradle module with `api(project(":gbkt-backend-api"))` dependency — no gbkt-backend-gbdk coupling (analysis is platform-agnostic)
- Implemented `PassPipeline` with fail-fast semantics and three-stage extension hook (`beforePasses`, `builtInPasses`, `afterPasses`)
- Built immutable `PassContext` with bank, VRAM, OAM, RAM, and inventory annotation maps plus `withDiagnostics`/`withBankAssignment` copy helpers
- Created `AnalysisConfig` with `fromCartridgeConfig` factory mapping MBC type strings to bank count limits
- 16 tests across `PassPipelineTest` and `PassContextTest` proving ordering, fail-fast, extension hooks, and immutability

## Task Commits

Each task was committed atomically:

1. **Task 1: Create gbkt-analysis module skeleton with core types and tests** - `a008685` (feat)

## Files Created/Modified

- `settings.gradle.kts` - Added `include("gbkt-analysis")` entry
- `gbkt-analysis/build.gradle.kts` - MPL-2.0 header, kotlin("jvm"), jvmToolchain(21), api(gbkt-backend-api), testImplementation(kotlin("test"))
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/AnalysisPass.kt` - `fun interface AnalysisPass` and `sealed interface PassResult` with Success/Failed subtypes
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/Diagnostic.kt` - `data class Diagnostic` with id, severity, message, location, suggestion; `enum class Severity`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassContext.kt` - Immutable `data class PassContext` with all annotation maps, RAMLayout, ResourceInventory, withDiagnostics/withBankAssignment helpers
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/PassPipeline.kt` - `class PassPipeline` with execute() fail-fast loop over before+builtin+after passes
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/config/AnalysisConfig.kt` - `data class AnalysisConfig` with all thresholds and `fromCartridgeConfig` factory
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/TestFixtures.kt` - Shared `FakeProfile` and `baseContext()` for tests
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/PassPipelineTest.kt` - 9 tests for pipeline ordering, fail-fast, and extension hooks
- `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/PassContextTest.kt` - 7 tests for immutability and annotation accumulation helpers

## Decisions Made

- No dependency on `gbkt-backend-gbdk` — analysis is platform-agnostic; backends consume analysis output, not the reverse
- `PassPipeline.execute()` concatenates all three pass lists before looping — single loop simplifies fail-fast logic at cost of one extra list allocation per pipeline run
- `AnalysisConfig.fromCartridgeConfig` uses `minOf(typeMax, romBanks)` — respects game author's declared bank count; type maximum is a ceiling not a floor

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Kotlin redeclaration of FakeProfile across test files**
- **Found during:** Task 1 (compile test sources)
- **Issue:** Both `PassPipelineTest.kt` and `PassContextTest.kt` declared `private object FakeProfile` in the same package, causing Kotlin compiler redeclaration error
- **Fix:** Extracted shared `FakeProfile` and `baseContext()` to `TestFixtures.kt` with `@file:Suppress("MatchingDeclarationName")` annotation to satisfy detekt; both test files now use the shared fixture
- **Files modified:** TestFixtures.kt (created), PassPipelineTest.kt (removed duplicate), PassContextTest.kt (removed duplicate)
- **Verification:** `./gradlew :gbkt-analysis:test` passes with 16 green tests
- **Committed in:** `a008685` (part of Task 1 commit)

**2. [Rule 1 - Bug] Applied Spotless formatting fixes**
- **Found during:** Task 1 (build verification with detekt/spotless)
- **Issue:** kdoc comment line wrapping, property alignment in KDoc, and enum formatting violated Spotless ktfmt rules
- **Fix:** Ran `./gradlew :gbkt-analysis:spotlessApply` to auto-correct all violations
- **Files modified:** AnalysisPass.kt, Diagnostic.kt, PassContext.kt, PassPipeline.kt, AnalysisConfig.kt, test files
- **Verification:** `./gradlew :gbkt-analysis:build` passes including spotlessKotlinCheck and detekt
- **Committed in:** `a008685` (part of Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both auto-fixes required for compilation and build tooling compliance. No scope creep.

## Issues Encountered

None beyond the auto-fixed deviations above.

## Next Phase Readiness

- `gbkt-analysis` module is the skeleton dependency for plans 04-02 through 04-05
- `PassContext` has stub fields for `ramLayout`, `inventory` — plans 04-02/04-03 will populate these
- `AnalysisConfig` thresholds are wired and ready for plans 04-04/04-05 validation passes

---
*Phase: 04-analysis-pass-pipeline*
*Completed: 2026-02-18*

## Self-Check: PASSED

All artifacts verified present:
- FOUND: gbkt-analysis/build.gradle.kts
- FOUND: AnalysisPass.kt
- FOUND: PassPipeline.kt
- FOUND: PassContext.kt
- FOUND: Diagnostic.kt
- FOUND: AnalysisConfig.kt
- FOUND: commit a008685
