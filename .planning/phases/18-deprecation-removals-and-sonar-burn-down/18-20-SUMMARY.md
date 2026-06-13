---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 20
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, cognitive-complexity, extract-method, gbdksystemvisitor, byte-identity]
dependency_graph:
  requires: ["18-19"]
  provides: ["GBDKSystemVisitor S3776 = 0"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns:
    - "Value-returning extract-method: helpers return List<CStatement>/CSwitch?, never mutate shared accumulator"
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
decisions:
  - "E-11: extracted buildZoneOnExitSwitch (CSwitch?) + buildEdgeAutoPositionSwitch (CSwitch) from buildZoneTransitionFunction"
  - "E-14: extracted buildEncounterRollStatements (List<CStatement>) + buildEncounterEntryGuard (CExpr?) from buildEncounterCheckFunction"
  - "Both extractions use value-returning pattern; no shared mutable accumulator captured (Pitfall 1 safe)"
  - "No NOSONAR suppressions used"
metrics:
  duration: "3 min"
  completed: "2026-06-13"
  tasks: 2
  files: 1
---

# Phase 18 Plan 20: GBDKSystemVisitor S3776 Final Clearance Summary

GBDKSystemVisitor.kt's last two S3776 findings (E-11 `buildZoneTransitionFunction` cc=30, E-14 `buildEncounterCheckFunction` cc=28) decomposed via extract-method into value-returning private helpers; 7-example byte-identity ROM sweep green after each commit; GBDKSystemVisitor.kt S3776 = 0.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract-method buildZoneTransitionFunction (E-11) | 8a58b485 | GBDKSystemVisitor.kt |
| 2 | Extract-method buildEncounterCheckFunction (E-14) | ec0ef267 | GBDKSystemVisitor.kt |

## What Was Built

### Task 1: E-11 `buildZoneTransitionFunction` (cc=30 → cleared)

Extracted two value-returning private helpers from `buildZoneTransitionFunction`:

**`buildZoneOnExitSwitch(zones: List<ZoneIR>): CSwitch?`**
- Returns `null` when no zone has onExit operations (eliminates outer if+loop nesting in caller)
- Returns the full `CSwitch` on `_current_zone_id` when onExit operations are present

**`buildEdgeAutoPositionSwitch(playerX: CVar, playerY: CVar): CSwitch`**
- Builds the 4-arm EAST/WEST/NORTH/SOUTH edge auto-position switch
- Removes the nested switch from the main function body

`buildZoneTransitionFunction` now delegates: `buildZoneOnExitSwitch?.let { add(it) }` → `CIf(entry_x != 0xFF, ..., else = listOf(buildEdgeAutoPositionSwitch(...)))` → zone load call. Same emission order preserved.

### Task 2: E-14 `buildEncounterCheckFunction` (cc=28 → cleared)

Extracted two value-returning private helpers from `buildEncounterCheckFunction`:

**`buildEncounterRollStatements(allEntries: List<EncounterEntryIR>): List<CStatement>`**
- Returns empty list immediately when no entries (eliminates the outer `if (allEntries.isNotEmpty())` nesting)
- Contains the roll/acc var decls and per-entry weight-check dispatch loop
- Calls `buildEncounterEntryGuard(entry)` per entry

**`buildEncounterEntryGuard(entry: EncounterEntryIR): CExpr?`**
- Builds the combined `conditionFlag && minLevel && maxLevel` guard condition
- Returns null when no guard is present
- Eliminates the deeply nested if/else chain from the original function

`buildEncounterCheckFunction` now delegates: two early-return guards → `addAll(buildEncounterRollStatements(allEntries))`. Same emission order preserved.

## Byte-Identity Sweep Evidence

### Baseline (pre-refactoring)
| ROM | SHA256 |
|-----|--------|
| banks.gb | `12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274` |
| breakout.gb | `564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a` |
| metasprites-stress.gb | `bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de` |
| metasprites.gb | `9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3` |
| platformer-template.gb | `9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7` |
| simple-physics.gb | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` |
| pong/main.c (PASS*) | `b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1` |

### After Task 1 commit (8a58b485) — IDENTICAL
All 6 non-pong hashes match baseline. Pong main.c matches baseline (PASS*).

### After Task 2 commit (ec0ef267) — IDENTICAL
All 6 non-pong hashes match baseline. Pong main.c matches baseline (PASS*).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — codegen-internal refactor; emitted C output byte-identical per sweep.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| GBDKSystemVisitor.kt exists | FOUND |
| SUMMARY.md exists | FOUND |
| Task 1 commit 8a58b485 | FOUND |
| Task 2 commit ec0ef267 | FOUND |
| buildZoneOnExitSwitch present | FOUND (line 2112) |
| buildEdgeAutoPositionSwitch present | FOUND (line 2133) |
| buildEncounterRollStatements present | FOUND (line 1862) |
| buildEncounterEntryGuard present | FOUND (line 1910) |
