---
phase: "14"
plan: "05"
subsystem: "backend-gbdk, gradle-plugin, core"
tags: ["rename", "cleanup", "v2-suffix-removal", "behavior-neutral"]
dependency_graph:
  requires: ["14-04"]
  provides: ["14-06"]
  affects: ["gbkt-backend-gbdk", "gbkt-gradle-plugin", "gbkt-core", "gbkt-analysis"]
tech_stack:
  added: []
  patterns: ["word-boundary perl rename", "bridge-pattern CodegenBackend override"]
key_files:
  created:
    - ".planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/RENAME-BYTEIDENTITY.md"
  modified:
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt"
    - "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt (→ GBDKPipeline class)"
    - "gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt (→ SimulationContext class)"
    - "gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt"
    - "132+ files across all modules (word-boundary perl rename)"
decisions:
  - "D-14-05-1: pipelineV2 local var renamed to codegenPipeline (not pipeline) to avoid shadowing val pipeline = DefaultPipeline.create() on line 128 of GBDKBackend.kt"
  - "D-14-05-2: bridge pattern kept — override fun generate(game, options) satisfies CodegenBackend interface; @JvmOverloads fun generate(gameIR, ...) is the full impl found by reflection"
  - "D-14-05-3: platformer-template/main.c baseline updated from a307c7ed to 4ad00ae3 — rename correctly updated a Kotlin-source comment that propagates verbatim into generated C (no semantic code change)"
metrics:
  duration: "~90 minutes"
  completed: "2026-06-06T20:21:38Z"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 135
---

# Phase 14 Plan 05: V2 Symbol Rename Summary

Behavior-neutral rename: GBDKPipelineV2 → GBDKPipeline, SimulationContextV2 → SimulationContext,
PipelineV2Output → PipelineOutput, generateV2 → generate, executeV2Path → executePath.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | GBDKPipelineV2→GBDKPipeline, SimulationContextV2→SimulationContext, PipelineV2Output→PipelineOutput, pipelineV2→codegenPipeline across 132+ files | 85e40853 |
| 2 | Promote generateV2→generate (bridge removal), rename executeV2Path→executePath, fix reflection string literal "generateV2"→"generate" in GenerateCTask.kt | 9a07e80b |
| 3 | Byte-identity gate: all 7 examples generateC EXIT 0, 14/14 files match baseline | 667f9f52 |

## Rename Scope

- **GBDKPipelineV2 → GBDKPipeline**: 132+ files, 541+ occurrences (word-boundary perl)
  - Class declaration: `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt`
  - All call sites, imports, KDoc references, test files
  - Test class names (`GBDKPipelineV2MetadataSpritesTest`) intentionally preserved (plan 06 scope)
- **SimulationContextV2 → SimulationContext**: class declaration + all usages in test files
  - Class declaration: `gbkt-core/.../test/SimulationContextV2.kt`
  - Test class name `SimulationContextV2Test` intentionally preserved (plan 06 scope)
- **PipelineV2Output → PipelineOutput**: data class + all usages
- **generateV2 → generate**: method name + all call sites in tests + reflection string literal
- **executeV2Path → executePath**: GenerateCTask internal method

## Reflection String Fix (CRITICAL)

`GenerateCTask.kt` invokes `GBDKBackend.generate()` via Java reflection across a ClassLoader
boundary. The string literal `"generateV2"` in `backend.javaClass.getMethod(...)` was updated to
`"generate"`. Without this fix, every `generateC` Gradle task would throw `NoSuchMethodException`
at runtime.

Verified: all 7 `generateC` tasks exit 0 with no exception.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] pipelineV2 local var naming collision in GBDKBackend.kt**
- **Found during:** Task 1
- **Issue:** Renaming `pipelineV2` → `pipeline` would shadow `val pipeline = DefaultPipeline.create()` on line 128
- **Fix:** Renamed to `codegenPipeline` instead to distinguish the GBDK codegen pipeline from the analysis pipeline
- **Files modified:** `gbkt-backend-gbdk/.../GBDKBackend.kt`
- **Commit:** 85e40853

**2. [Rule 1 - Bug] StackOverflowError from ambiguous dispatch in bridge**
- **Found during:** Task 2
- **Issue:** `override fun generate(game, options) = generate(game)` caused infinite recursion — `generate(game)` with one arg matched BOTH the interface override (which has default `options`) AND the `@JvmOverloads fun generate(gameIR, ...)` with defaults. Kotlin resolved to the interface override → infinite loop.
- **Fix:** Used named parameters: `generate(gameIR = game, assetManifest = null, outputDirectory = null, assetRoot = null)` to unambiguously dispatch to the full-signature overload.
- **Files modified:** `gbkt-backend-gbdk/.../GBDKBackend.kt`
- **Commit:** 9a07e80b

**3. [Rule 2 - Expected rename artifact] platformer-template/main.c comment update**
- **Found during:** Task 3
- **Issue:** Baseline hash for `platformer-template/main.c` differed. Root cause: line 2556 of GBDKPipelineV2.kt contains a Kotlin comment (`// helper (declared at GBDKPipeline buildSetLevelSubmapHelperIfNeeded;...`) that propagates verbatim into generated C. The perl rename correctly updated `GBDKPipelineV2` → `GBDKPipeline` in this comment.
- **Fix:** Updated baseline hash from `a307c7ed...` to `4ad00ae3...`. No semantic C code was altered.
- **Files modified:** `evidence/baseline/baseline-platformer-template.sha256`
- **Commit:** 667f9f52

## Byte-Identity Gate Results

All 14 generateC-produced files across 7 examples match their (updated) baselines:

| Example | Files checked | Result |
|---------|--------------|--------|
| pong | main.c, bank1.c | PASS |
| breakout | main.c, bank1.c | PASS |
| simple-physics | main.c | PASS |
| metasprites | main.c | PASS |
| metasprites-stress | main.c, bank1.c | PASS |
| banks | main.c, bank1.c, zone_bank2.c | PASS |
| platformer-template | main.c, bank1.c, zone_bank2.c | PASS |

Files skipped (not generateC output): `sprites/*.c` (png2asset), `_zone_*.c` (convertZoneTilesets).

## Pre-existing Failures (Not Regressions)

- **pluginTest 12 IntegrationTest failures**: `SceneIR.copy$default` signature mismatch from stale
  mavenLocal. Confirmed pre-existing at Plan 14-04 commit `660e8c7d` (before Plan 14-05 changes).
  Tracked as IntegrationTest baseline-red pre-dating Phase 11.1-04.
- **BanksUatTest 2 failures**: Stale ROM file. Not Plan 14-05 scope.

## Self-Check: PASSED
- All 3 task commits exist: 85e40853, 9a07e80b, 667f9f52 ✓
- Evidence/RENAME-BYTEIDENTITY.md created ✓
- All 7 generateC tasks EXIT 0 ✓
- Byte-identity gate: 14/14 PASS ✓
