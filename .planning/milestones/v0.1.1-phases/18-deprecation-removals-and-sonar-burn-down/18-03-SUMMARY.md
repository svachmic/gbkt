---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "03"
subsystem: gbkt-genre-rpg
tags: [deprecation-removal, combat-state, magic-string-elimination, DEPR-02]
dependency_graph:
  requires: []
  provides: [DEPR-02]
  affects: [gbkt-genre-rpg]
tech_stack:
  added: []
  patterns: [typed-only-overload]
key_files:
  modified:
    - gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt
    - gbkt-genre-rpg/src/test/kotlin/io/github/gbkt/rpg/dsl/CombatStatesTest.kt
decisions:
  - "Deleted the typed-vs-string equivalence test entirely (D-03 developer discretion): after String overload removal the test has no meaningful assertion to express"
  - "Removed stale KDoc line ('Prefer this over the string-based overload') from the typed overload since it referenced a now-deleted overload"
metrics:
  duration: "3 min"
  completed: "2026-06-13"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 2
---

# Phase 18 Plan 03: DEPR-02 — Hard-remove combatIsInState String Overload Summary

Hard-removed deprecated `combatIsInState(stateId: String, battleId: String)` from RpgExtensions.kt and migrated the sole call site in CombatStatesTest.kt.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Remove String combatIsInState overload and migrate call site | 50051dd7 | RpgExtensions.kt, CombatStatesTest.kt |

## What Was Built

Eliminated the magic-string `combatIsInState(String, String)` overload (SEED-025, Project Rule #1). The DSL surface is now typed-only via `combatIsInState(CombatStateId, BattleRef)`.

### Changes

**RpgExtensions.kt:**
- Deleted the entire `@Deprecated`-annotated `combatIsInState(stateId: String, battleId: String)` block (KDoc + annotation + function body, 20 lines).
- Removed stale "Prefer this over the string-based overload" KDoc sentence from the typed overload (referencing a now-deleted overload).
- Typed overload `combatIsInState(state: CombatStateId, battle: BattleRef)` unchanged.

**CombatStatesTest.kt:**
- Deleted the `typed and string overloads of combatIsInState produce identical CallExpr` test — the assertion was between the two overloads and has no meaningful equivalent after String overload removal.
- Removed `@Suppress("DEPRECATION")` annotation (had annotated the call to the now-deleted String overload).

## Deviations from Plan

None — plan executed exactly as written.

## Verification

```
./gradlew :gbkt-genre-rpg:test
```

BUILD SUCCESSFUL — 28 tasks, 4 executed.

Acceptance criteria confirmed:
- `combatIsInState(stateId: String` — 0 matches in RpgExtensions.kt
- `@Suppress("DEPRECATION")` — 0 matches in CombatStatesTest.kt
- `:gbkt-genre-rpg:test` — PASSED

## Known Stubs

None.

## Threat Flags

None — DSL API removal only; no new network, auth, file, or schema surface introduced.

## Self-Check: PASSED

- [x] RpgExtensions.kt exists and String overload is absent
- [x] CombatStatesTest.kt exists with no DEPRECATION suppression
- [x] Commit 50051dd7 exists
