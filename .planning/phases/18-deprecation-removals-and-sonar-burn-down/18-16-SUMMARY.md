---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 16
subsystem: gbkt-backend-gbdk/codegen/pipeline
tags: [sonar-s3776, extract-method, gbdk-pipeline, byte-identity]
dependency_graph:
  requires: ["18-15"]
  provides: ["GBDKPipeline.kt S3776 fully cleared"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["extract-method (value-returning helpers)", "D-06 per-commit ROM sweep"]
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
decisions:
  - "Extracted buildGlobalFlagVarDecls + buildZoneObjectInlineFlagVarDecls to decompose buildFlagVarDecls (E-25 cc=19 → parent cc=0, helpers cc=6/13)"
  - "Extracted collectAllCallOpsFromGame + buildSingleCallOpStub to decompose buildCallOpStubFunctions (E-27 cc=18 → parent cc=6, helpers cc=4/5)"
  - "No NOSONAR used; both findings resolved via pure extract-method"
metrics:
  duration: 4min
  completed: "2026-06-13"
  tasks_completed: 2
  files_modified: 1
---

# Phase 18 Plan 16: GBDKPipeline S3776 Final Two Findings Summary

GBDKPipeline.kt E-25 (`buildFlagVarDecls`, cc=19) and E-27 (`buildCallOpStubFunctions`, cc=18) extracted via value-returning helpers; GBDKPipeline.kt S3776 count is now 0.

## Tasks Completed

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Extract-method buildFlagVarDecls (E-25) | ebf57422 | +buildGlobalFlagVarDecls, +buildZoneObjectInlineFlagVarDecls |
| 2 | Extract-method buildCallOpStubFunctions (E-27) | 2e5a7418 | +collectAllCallOpsFromGame, +buildSingleCallOpStub |

## What Was Built

### Task 1: E-25 buildFlagVarDecls (cc=19 → 0)

The original function had three nested `for` loops (pages/flags) followed by two nested `for` loops with three nested `if` blocks — total cc=19. Two helpers extracted:

- **`buildGlobalFlagVarDecls`** (cc=6): Iterates `gameIR.flags → pages → flags` and emits `_flag_*` `CVarDecl`s for each registered flag name.
- **`buildZoneObjectInlineFlagVarDecls`** (cc=13): Iterates `gameIR.zones → objects` and emits `CVarDecl`s for ad-hoc inline `usedFlagId` / `visibleFlagId` flags on zone objects.
- **Parent** becomes a single expression `buildGlobalFlagVarDecls(gameIR) + buildZoneObjectInlineFlagVarDecls(gameIR)` (cc=0).

### Task 2: E-27 buildCallOpStubFunctions (cc=18 → 6)

The original function mixed CallOp collection loops, deduplication, and per-stub building all in one body. Two helpers extracted:

- **`collectAllCallOpsFromGame`** (cc=4): Collects all `CallOp`s from zone-object scripts and scene lifecycle scripts (enter/frame/exit).
- **`buildSingleCallOpStub`** (cc=5): Builds one `CFunction` stub given a name, a `CallOp` (for parameter type inference), and an `isFirst` flag for the section comment.
- **Parent** retains the dedup/filter loop and delegates to the new helpers (cc=6).

## Byte-Identity Evidence

Baseline captured before any changes. Both commits verified identically:

| Example | Task 1 | Task 2 | Verdict |
|---------|--------|--------|---------|
| banks | `12c8ee2e` | `12c8ee2e` | PASS (identical to baseline) |
| breakout | `564465cd` | `564465cd` | PASS |
| metasprites-stress | `bc51eadd` | `bc51eadd` | PASS |
| metasprites | `9b2440db` | `9b2440db` | PASS |
| platformer-template | `9a8f268a` | `9a8f268a` | PASS |
| simple-physics | `247e16d2` | `247e16d2` | PASS |
| pong | (non-deterministic .gb) | (non-deterministic .gb) | PASS* (main.c `b5e81de7` identical to baseline) |

## S3776 Closure

- E-25 `buildFlagVarDecls`: closed (cc=19 → parent 0, helpers 6/13)
- E-27 `buildCallOpStubFunctions`: closed (cc=18 → parent 6, helpers 4/5)
- **GBDKPipeline.kt S3776 findings: 0** (all 10 original EMITTING findings in this file are now resolved across Plans 18-13 through 18-16)

## Deviations from Plan

None — plan executed exactly as written. Both findings resolved via extract-method, no NOSONAR.

## Known Stubs

None introduced.

## Threat Flags

None. Pure internal refactoring, no new network endpoints, auth paths, or trust-boundary changes.

## Self-Check: PASSED

- [x] `ebf57422` exists in git log
- [x] `2e5a7418` exists in git log
- [x] GBDKPipeline.kt modified (1 file, 2 commits)
- [x] Byte-identity: 6/6 non-pong identical; pong PASS*
