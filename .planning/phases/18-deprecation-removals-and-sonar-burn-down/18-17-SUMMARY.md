---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 17
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar-s3776, extract-method, puzzle-codegen, byte-identity, emitting]
dependency_graph:
  requires: [18-16]
  provides: [buildPuzzleObjectFunctions-decomposed]
  affects: [gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns: [value-returning-extract-method, per-type-sub-builder, PuzzleObjectOutput-data-class]
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
decisions:
  - "Per-type helpers return PuzzleObjectOutput(vars, functions, perFrameCalls) — no shared-mutable accumulation (Pitfall 1)"
  - "PuzzleObjectOutput is a private nested data class inside GBDKSystemVisitor"
  - "buildPuzzleObjectFunctions becomes a thin dispatcher: sealed when + accumulation loop"
metrics:
  duration: 7 min
  completed_date: "2026-06-13"
  tasks_completed: 1
  files_modified: 1
---

# Phase 18 Plan 17: Decompose buildPuzzleObjectFunctions (E-01) Summary

**One-liner:** Decomposed `buildPuzzleObjectFunctions` (cc=92) into 5 value-returning per-puzzle-type private helpers via `PuzzleObjectOutput` data class; 7-example byte-identity sweep green (6/6 identical, pong PASS*).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract per-type sub-builders from buildPuzzleObjectFunctions | 257e044c | GBDKSystemVisitor.kt |

## What Was Built

Finding E-01: `GBDKSystemVisitor.buildPuzzleObjectFunctions` at line 4862 had cognitive complexity 92 (S3776 threshold=15). The function handled all 5 puzzle types (`SwitchObjectIR`, `DoorObjectIR`, `PressurePlateObjectIR`, `TimedBlockObjectIR`, `TriggerObjectIR`) in a single large `when` dispatch block.

**Refactoring:**
- Added `private data class PuzzleObjectOutput(vars, functions, perFrameCalls)` nested inside `GBDKSystemVisitor`
- Extracted 5 value-returning private helpers:
  - `buildSwitchObjectOutput(obj, id, puzzleById): PuzzleObjectOutput`
  - `buildDoorObjectOutput(obj, id, puzzleById): PuzzleObjectOutput`
  - `buildPressurePlateObjectOutput(obj, id, puzzleById): PuzzleObjectOutput`
  - `buildTimedBlockObjectOutput(obj, id, puzzleById): PuzzleObjectOutput`
  - `buildTriggerObjectOutput(obj, id, puzzleById): PuzzleObjectOutput`
- `buildPuzzleObjectFunctions` becomes a thin sealed-`when` dispatcher that accumulates results: `vars += output.vars`, `functions += output.functions`, `perFrameCalls += output.perFrameCalls`

**Pitfall 1 compliance:** Each helper returns its contribution as a value; no shared-mutable accumulator passed by reference.

## Byte-Identity Sweep Results

| Example | Baseline SHA256 | Post-refactor SHA256 | Result |
|---------|----------------|----------------------|--------|
| banks | `12c8ee2e...` | `12c8ee2e...` | PASS |
| breakout | `564465cd...` | `564465cd...` | PASS |
| metasprites-stress | `bc51eadd...` | `bc51eadd...` | PASS |
| metasprites | `9b2440db...` | `9b2440db...` | PASS |
| platformer-template | `9a8f268a...` | `9a8f268a...` | PASS |
| pong | (non-deterministic) | (non-deterministic) | PASS* (generated C byte-identical) |
| simple-physics | `247e16d2...` | `247e16d2...` | PASS |

6/6 non-pong ROMs byte-identical. Pong generated `main.c` SHA256 identical (`b5e81de7...`).

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None — pure extract-method refactor, no new C emission paths or trust boundaries introduced.

## Known Stubs

None.

## Self-Check: PASSED

- [x] Commit 257e044c exists: confirmed
- [x] GBDKSystemVisitor.kt modified: confirmed
- [x] Byte-identity sweep: 6/6 non-pong PASS, pong PASS*
- [x] spotless + detekt: BUILD SUCCESSFUL
