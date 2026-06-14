---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "09"
subsystem: gbkt-backend-gbdk/test
tags: [evidence-dir, emission-tests, test-infrastructure, path-redirect]
dependency_graph:
  requires: []
  provides: [emission-test-scratch-path-redirected-to-build]
  affects: [gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns: [user.dir-relative-build-path]
key_files:
  modified:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/AutoExitSynthesisTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/BindCurrentLevelEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelCardSceneEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/LevelSwitchEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/ScreenPrimitiveEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/TitleSceneEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/CLiteralAuditScanTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteCameraOffsetEmissionTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SignedComparisonLiteralEmissionTest.kt
decisions:
  - EVIDENCE_DIR redirected to build/gbkt/test-evidence (gitignored by module build/ default)
  - user.dir-relative path used consistently across all 9 files (CLiteralAuditScanTest and SignedComparisonLiteralEmissionTest migrated from findRepoRoot() pattern to user.dir pattern)
metrics:
  duration: "2 min"
  completed: "2026-06-14"
  tasks: 2
  files: 9
---

# Phase 22 Plan 09: Redirect Emission Test EVIDENCE_DIR to build/ Summary

Redirected the emission-test scratch EVIDENCE_DIR in 9 `gbkt-backend-gbdk` test classes from archived `.planning/phases/**/evidence` paths to the module-local `build/gbkt/test-evidence/` directory (gitignored). Zero production code changes; in-test C assertions remain the gate.

## What Was Done

**Task 1 (6 pipeline tests):** Replaced multi-line `.planning/phases/<phase>/evidence/tier1-shape` resolves with `"build/gbkt/test-evidence"` in all 6 pipeline emission test companions:
- AutoExitSynthesisTest (was: `../.planning/phases/13.5-framework-primitives-graphics-level-codegen-inserted/evidence/tier1-shape`)
- BindCurrentLevelEmissionTest (was: same 13.5 path)
- LevelCardSceneEmissionTest (was: `../.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/tier1-shape`)
- LevelSwitchEmissionTest (was: `../.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape`)
- ScreenPrimitiveEmissionTest (was: same 13.5 path)
- TitleSceneEmissionTest (was: same 12 path)

**Task 2 (3 visitor tests + test suite):** Applied the same redirect to the 3 visitor emission test companions:
- CLiteralAuditScanTest (was: `findRepoRoot() + ".planning/phases/07.9-c-codegen-signed-vs-unsigned-literal-discipline/evidence/audit-scan"`)
- MetaspriteCameraOffsetEmissionTest (was: `../.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/tier1-shape`)
- SignedComparisonLiteralEmissionTest (was: `findRepoRoot() + ".planning/phases/07.9-.../evidence/tier1-shape"`)

For CLiteralAuditScanTest and SignedComparisonLiteralEmissionTest, migrated from the `findRepoRoot()` pattern to `File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence")` — consistent with the 6 pipeline test pattern. The `findRepoRoot()` helper (TestRepoRoot.kt) is now unused by these files; spotless/ktlint confirmed no unused import warnings.

## Verification

- `grep -rl "planning/phases" <9 files> | wc -l` → 0 (verified before each commit)
- `./gradlew :gbkt-backend-gbdk:test` → BUILD SUCCESSFUL (all emission tests GREEN)
- `./gradlew :gbkt-backend-gbdk:spotlessApply :gbkt-backend-gbdk:detekt` → clean after each task

## Commits

| Commit | Description |
|--------|-------------|
| 9f95a75a | refactor(22-09): redirect EVIDENCE_DIR to build/gbkt/test-evidence in 6 pipeline emission tests |
| c7735229 | refactor(22-09): redirect EVIDENCE_DIR to build/gbkt/test-evidence in 3 visitor emission tests |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan redirects scratch paths; no stubs introduced.

## Threat Flags

None — test infrastructure path change only; no new network endpoints, auth paths, file access at trust boundaries.

## Self-Check: PASSED

- All 9 modified files exist on disk.
- Both commits exist: 9f95a75a and c7735229 confirmed via git log.
- `grep "planning/phases" <9 files>` returned 0 matches.
- `./gradlew :gbkt-backend-gbdk:test` BUILD SUCCESSFUL.
