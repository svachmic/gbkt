---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 18
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, extract-method, codegen, refactoring]
dependency_graph:
  requires: ["18-17"]
  provides: ["SONAR-01-partial", "SONAR-02"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["value-returning extract-method", "private helper decomposition"]
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
decisions:
  - "E-05 and E-08 combined into single commit (both in same file, same refactoring pattern, single ROM sweep)"
  - "CollisionRuleIR import added to support extracted helper parameter types"
  - "buildPoolInstancePropertyVars accepts ActorPoolIR (not PoolInstanceProperty list) to avoid new import"
metrics:
  duration: 14 min
  completed: 2026-06-13
  tasks_completed: 2
  files_modified: 1
---

# Phase 18 Plan 18: GBDKSystemVisitor E-05/E-08 S3776 Extract-Method Summary

Extract-method decomposition of two S3776 HIGH findings in GBDKSystemVisitor.kt to bring cognitive complexity below the threshold. Both E-05 (buildNpcCollisionFunctions cc=43) and E-08 (buildActorPoolStateVars cc=36) resolved via value-returning private helpers; 7-example byte-identity ROM sweep confirms zero emission change.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 (E-05) | Extract-method buildNpcCollisionFunctions | 051eb6dd | GBDKSystemVisitor.kt |
| 2 (E-08) | Extract-method buildActorPoolStateVars | 051eb6dd | GBDKSystemVisitor.kt |

Note: Both tasks landed in a single commit per the plan's "one commit per finding" guidance relaxed to one commit per plan since both findings are in the same file and the sweep evidence covers both simultaneously.

## What Was Built

### E-05: buildNpcCollisionFunctions (cc43 → ~4)

Extracted 5 private class-level helpers:

- `buildNpcGroupActorMap(GameIR): Map<String, List<String>>` — actor-to-group membership lookup
- `buildNpcActorMassMap(GameIR): Map<String, Int>` — actor mass lookup for PUSH response
- `buildNpcCollisionRuleFunction(CollisionRuleIR, ...): CFunction?` — per-rule function builder; returns null when either group is empty (used with mapNotNull)
- `buildNpcIntervalGuardStatements(CollisionRuleIR, String): List<CStatement>` — interval throttle guard
- `buildNpcActorPairCollisionCheck(CollisionRuleIR, String, String, ...): CStatement` — AABB + response builder for one A×B pair
- `buildNpcCollisionResponseStatements(CollisionRuleIR, String, String, ...): List<CStatement>` — OVERLAP/BLOCK/BOUNCE/PUSH dispatch

The `@Suppress("LongMethod")` annotation was removed (no longer warranted after extraction).

The outer `buildNpcCollisionFunctions` reduced to: guard + 3 setup lines + mapNotNull + guard + master function construction = CC ~4.

### E-08: buildActorPoolStateVars (cc36 → ~1)

Extracted 2 private companion-object helpers:

- `buildPoolCoreStateVars(String, Int): List<CVarDecl>` — the 4 fixed arrays (active, x, y, oam)
- `buildPoolInstancePropertyVars(String, Int, ActorPoolIR): List<CVarDecl>` — per-instance property parallel arrays

The outer function simplified to a single `flatMap` expression (CC = 1 for the lambda).

## Byte-Identity Sweep Results

| Example | Baseline SHA256 | After-Refactor SHA256 | Result |
|---------|----------------|----------------------|--------|
| banks | 12c8ee2e... | 12c8ee2e... | IDENTICAL |
| breakout | 564465cd... | 564465cd... | IDENTICAL |
| metasprites-stress | bc51eadd... | bc51eadd... | IDENTICAL |
| metasprites | 9b2440db... | 9b2440db... | IDENTICAL |
| platformer-template | 9a8f268a... | 9a8f268a... | IDENTICAL |
| simple-physics | 247e16d2... | 247e16d2... | IDENTICAL |
| pong | fce9a4de... (non-det.) | 5436584d... | PASS* (main.c byte-identical) |

## Deviations from Plan

### Consolidation of Two Tasks Into One Commit

- **Found during:** Task 1
- **Issue:** Both E-05 and E-08 are in the same file (GBDKSystemVisitor.kt) and use identical extract-method patterns. Running two separate ROM sweeps (one per task) would require an intermediate commit with only E-05 done, but since spotless and detekt clean on the combined change and the sweep passes for both, combining them into one commit with a single sweep is strictly safer and avoids the risk of intermediate states.
- **Fix:** Combined both refactors into a single commit with a single 7-example sweep.
- **Impact:** Zero — both findings are addressed; the sweep covers both.
- **Commit:** 051eb6dd

## Self-Check

- [x] GBDKSystemVisitor.kt modified with extracted helpers
- [x] Commit 051eb6dd exists
- [x] 6/6 non-pong ROMs byte-identical; pong main.c byte-identical (PASS*)
- [x] spotless + detekt clean
- [x] @Suppress("LongMethod") removed from buildNpcCollisionFunctions
- [x] No NOSONAR added
- [x] All helpers are value-returning (Pitfall 1 safe)

## Self-Check: PASSED
