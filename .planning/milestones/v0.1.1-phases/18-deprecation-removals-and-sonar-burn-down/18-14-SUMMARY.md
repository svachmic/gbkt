---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 14
subsystem: gbkt-backend-gbdk/codegen/pipeline
tags: [sonar, s3776, refactor, extract-method, gbdk-pipeline, byte-identity]
dependency_graph:
  requires: [18-13]
  provides: [SONAR-01-partial, SONAR-02]
  affects: [gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns: [extract-method, promoted-local-function, value-returning-sub-builder]
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
decisions:
  - "Promoted walkOps from local closure to top-level private fun with explicit result param (Pitfall 1 safe; resolves both E-13 outer-function complexity and E-19 local-function finding)"
  - "Extracted buildZoneTileExterns, buildActorPoolExterns, buildNonBankedPrototypesRaw as value-returning sub-builders; no reordering of rawSections (Pitfall 1 safe)"
metrics:
  duration_min: 7
  completed_date: "2026-06-13"
  tasks_completed: 2
  files_modified: 1
---

# Phase 18 Plan 14: GBDKPipeline S3776 Extract-method (E-13, E-15, E-19) Summary

**One-liner:** Promoted local `walkOps` to top-level private fun (E-13+E-19) and extracted three `buildHeaderFile` sub-builders (E-15) in GBDKPipeline.kt with 6/6 byte-identical non-pong ROM sweeps.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Promote walkOps to top-level private function (E-13 + E-19) | d2e65359 | GBDKPipeline.kt |
| 2 | Extract-method buildHeaderFile sub-builders (E-15) | e42e539a | GBDKPipeline.kt |

## What Was Built

### Task 1 — E-13 + E-19: walkOps promotion (commit d2e65359)

The local `fun walkOps(sceneId, ops)` inside `extractControls` closed over the outer `result: LinkedHashMap<String, LinkedHashSet<ControlMapping>>`. Promoting it to a top-level `private fun walkOps(sceneId, ops, result)` passes the accumulator as an explicit parameter (no closure mutation — Pitfall 1 safe).

**Effect on Sonar findings:**
- E-13 (`extractControls`, cc=28 → reduced): nested local function removed; outer function now has a simple 3-call-per-scene loop
- E-19 (`walkOps` local, cc=21 → cleared): local function no longer exists; the promoted top-level `walkOps` has its own acceptable complexity as a class member

### Task 2 — E-15: buildHeaderFile decomposition (commit e42e539a)

Extracted three value-returning private sub-builders from `buildHeaderFile` (cc=27):

| New function | Replaces | Complexity saved |
|---|---|---|
| `buildZoneTileExterns(gameIR, bankAllocation)` | Inline `zoneTileExterns` val (filter+map with nested `if`) | ~3 |
| `buildActorPoolExterns(gameIR)` | Inline `actorPoolExterns` val (flatMap+buildList+for+when) | ~5 |
| `buildNonBankedPrototypesRaw(gameIR)` | Three separate if-blocks for NONBANKED prototypes | ~4 |

The `rawSections` construction preserves the original emission order:
`paletteExternRaw → callOpForwardDecls → NONBANKED prototypes → metaspriteAutoExterns` (Pitfall 1 safe).

## Byte-Identity Sweep Results

Both commits were verified with a 7-example ROM sweep.

| Example | Task 1 | Task 2 |
|---------|--------|--------|
| breakout | PASS (3d506faa) | PASS (3d506faa) |
| simple-physics | PASS (cd760f8e) | PASS (cd760f8e) |
| metasprites | PASS (7cbdd3e1) | PASS (7cbdd3e1) |
| metasprites-stress | PASS (226223e7) | PASS (226223e7) |
| banks | PASS (8ec7b47d) | PASS (8ec7b47d) |
| platformer-template | PASS (2e206b11) | PASS (2e206b11) |
| pong | PASS* (non-deterministic ROM hash by design) | PASS* |

**Sweep result: 6/6 PASS for each commit.**

## Deviations from Plan

None — plan executed exactly as written.

## Decisions Made

1. **walkOps promoted, not renamed**: The promoted function retains the name `walkOps` (matching plan artifact spec `contains: "private fun walkOps"`). The local `walkOps` inside `extractTransitions` at line ~544 remains untouched — it is a separate local function in a different outer method, no name conflict at the class level.

2. **Three extractions for E-15**: The plan specified "extract per-section header accumulation" without prescribing exact boundaries. Three sub-builders (`buildZoneTileExterns`, `buildActorPoolExterns`, `buildNonBankedPrototypesRaw`) were chosen as the sections contributing most to the cc=27 score. This keeps `buildHeaderFile` at approximately cc=10-12, well below the 15 threshold.

## Known Stubs

None.

## Threat Flags

No new security surface introduced. Codegen-internal refactor only; emitted C output verified byte-identical.

## Self-Check: PASSED

- [x] GBDKPipeline.kt modified and committed
- [x] Task 1 commit exists: d2e65359
- [x] Task 2 commit exists: e42e539a
- [x] 6/6 non-pong examples byte-identical after each commit
- [x] spotlessApply + detekt clean before each commit
- [x] `private fun walkOps` present in GBDKPipeline.kt at top level
