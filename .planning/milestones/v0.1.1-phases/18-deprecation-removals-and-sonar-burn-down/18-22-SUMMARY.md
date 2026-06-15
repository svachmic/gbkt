---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 22
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, extract-method, scene-visitor, combat-visitor, refactoring]
dependency_graph:
  requires: ["18-21"]
  provides: ["SONAR-01 E-07 cleared", "SONAR-02 E-16 cleared"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["extract-method (value-returning)", "listOfNotNull composition", "per-type dispatch helpers"]
key_files:
  created: []
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/CombatVisitor.kt
decisions:
  - "E-07: extracted buildEnterFunction, buildFrameFunction, buildExitFunction, buildZoneLoadStatements as private helpers; visit() delegates via listOfNotNull"
  - "E-16: extracted buildCoreFunctions, buildAtbFunctions, buildWaveSurvivalFunctions, buildTacticalGridFunctions; generateCombatFunctions becomes a single list concatenation"
  - "No NOSONAR suppressions used (D-05 budget preserved)"
metrics:
  duration: 8 min
  completed_date: "2026-06-13"
  tasks: 2
  files: 2
---

# Phase 18 Plan 22: S3776 EMITTING D-06 — SceneVisitor.visit + CombatVisitor.generateCombatFunctions Summary

Reduced two SonarCloud S3776 HIGH cognitive-complexity findings (E-07 cc=39, E-16 cc=23) via extract-method refactoring with value-returning private helpers. Each finding received its own commit plus a full 7-example byte-identity ROM sweep per D-06.

## Tasks Completed

### Task 1: Extract per-lifecycle helpers from SceneVisitor.visit (E-07)

**What:** `SceneVisitor.visit` (CC=39) refactored to delegate to four private helpers.

**Helpers added:**
- `buildEnterFunction(scene, exprVisitor, ..., sceneBank, sceneBanked): CFunction?` — returns the enter function or null; contains zone-load prepend call + user enter ops.
- `buildZoneLoadStatements(scene, zones, zoneBankAllocation, gbcTarget): List<CStatement>` — carries the entire zone-load `flatMap` block (the primary complexity source).
- `buildFrameFunction(scene, exprVisitor, sceneBank, sceneBanked): CFunction?` — returns frame function or null.
- `buildExitFunction(scene, exprVisitor, sceneBank, sceneBanked, isMbcGame): CFunction?` — handles both user exit ops and the Req-15/D-07 auto-synthesize path.

**After refactoring, `visit()` becomes:**
```kotlin
return listOfNotNull(
    buildEnterFunction(...),
    buildFrameFunction(...),
    buildExitFunction(...),
)
```

**Commit:** `c9360e57` — 329 insertions, 324 deletions (net: lines reorganized, no logic changes)

**Byte-identity sweep (Task 1):** 7/7 PASS — pong PASS*, 6/6 non-pong byte-identical on generated C and ROM.

### Task 2: Extract per-combat-type helpers from CombatVisitor.generateCombatFunctions (E-16)

**What:** `generateCombatFunctions` (CC=23) refactored to delegate to four private helpers.

**Helpers added:**
- `buildCoreFunctions(system, id, exprVisitor): List<CFunction>` — always-present functions (requestState, updateCombat, isInState, trigger) plus optional parentState and damage functions.
- `buildAtbFunctions(system, id): List<CFunction>` — ATB gauge-update and optional turn-order functions; returns emptyList() if not ATB.
- `buildWaveSurvivalFunctions(system, id): List<CFunction>` — start/check/between/advance functions; returns emptyList() if not WAVE_SURVIVAL.
- `buildTacticalGridFunctions(system, id): List<CFunction>` — movement BFS, LOS, optional facing/elevation, AoE; returns emptyList() if not TACTICAL_GRID.

**After refactoring, `generateCombatFunctions` becomes:**
```kotlin
return buildCoreFunctions(system, id, exprVisitor) +
    buildAtbFunctions(system, id) +
    buildWaveSurvivalFunctions(system, id) +
    buildTacticalGridFunctions(system, id) +
    generateHookFunctions(system, id, exprVisitor)
```

**Commit:** `724c22a3` — 76 insertions, 41 deletions

**Byte-identity sweep (Task 2):** 7/7 PASS — pong PASS*, 6/6 non-pong byte-identical on generated C and ROM.

## Verification

| Check | Result |
|-------|--------|
| spotlessApply | PASS (both tasks) |
| detekt | PASS (both tasks) |
| :gbkt-backend-gbdk:test | PASS (both tasks) |
| C byte-identity sweep (Task 1) | 7/7 PASS (pong PASS*) |
| ROM byte-identity sweep (Task 1) | 7/7 PASS (pong PASS*) |
| C byte-identity sweep (Task 2) | 7/7 PASS (pong PASS*) |
| ROM byte-identity sweep (Task 2) | 7/7 PASS (pong PASS*) |
| SonarCloud E-07 cleared | Expected (CC reduced from 39 to ~6) |
| SonarCloud E-16 cleared | Expected (CC reduced from 23 to ~4) |

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 (E-07) | `c9360e57` | `refactor(18-22): extract per-lifecycle helpers from SceneVisitor.visit (E-07)` |
| 2 (E-16) | `724c22a3` | `refactor(18-22): extract per-combat-type helpers from generateCombatFunctions (E-16)` |

## Deviations from Plan

None — plan executed exactly as written. Both extractions used value-returning private helpers (Pitfall 1 avoided), preserved emission order, and passed D-06 byte-identity sweeps.

## Known Stubs

None. No stub patterns introduced.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes. The refactoring is codegen-internal; the emitted C/ROM output is byte-identical to baseline.

## Self-Check: PASSED

- SUMMARY.md: FOUND at `.planning/phases/18-deprecation-removals-and-sonar-burn-down/18-22-SUMMARY.md`
- Commit c9360e57 (Task 1): FOUND
- Commit 724c22a3 (Task 2): FOUND
- SceneVisitor.kt: FOUND and spotless+detekt PASS
- CombatVisitor.kt: FOUND and spotless+detekt PASS
