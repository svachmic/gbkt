---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 26
subsystem: sport-codegen + rpg-codegen
tags: [sonar, s3776, extract-method, byte-identity, emitting]
dependency_graph:
  requires: ["18-25"]
  provides: ["SONAR-01-emitting-complete", "SONAR-02"]
  affects: ["gbkt-genre-sport", "gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["extract-method value-returning (Pitfall 1 safe)", "buildList{} per-section helpers"]
key_files:
  created: []
  modified:
    - gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/TrackSynthesizer.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/RpgVisitor.kt
decisions:
  - "E-28: scanlineFill decomposed into collectScanlineIntersections + fillScanlineSpans; CC 17 → ~4"
  - "E-29: generateApplyEffectFunction decomposed into buildImmunityCheckStatements + buildResistCheckStatements + buildStackHandlingStatements; CC 16 → ~4"
  - "Sport JVM tests serve as direct oracle for E-28 (no sport example ROM in 7-example suite per Pitfall 8)"
  - "7-example sweep is non-regression check; 6/6 non-pong hashes identical across both commits"
metrics:
  duration: "3 min"
  completed: "2026-06-13"
  tasks: 2
  files: 2
---

# Phase 18 Plan 26: E-28 + E-29 — Final EMITTING S3776 Findings Summary

**One-liner:** Extract-method refactor closes the final two EMITTING S3776 findings (TrackSynthesizer.scanlineFill cc17 + RpgVisitor.generateApplyEffectFunction cc16), completing all 29 EMITTING findings across Phase 18.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract-method TrackSynthesizer.scanlineFill (E-28, sport) | 06555a74 | TrackSynthesizer.kt |
| 2 | Extract-method RpgVisitor.generateApplyEffectFunction (E-29) | 2c86b261 | RpgVisitor.kt |

## Task 1: TrackSynthesizer.scanlineFill (E-28)

**File:** `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/TrackSynthesizer.kt`

**Extracted helpers:**
- `collectScanlineIntersections(waypoints, n, y): List<Int>` — the inner edge-intersection for-loop with Pitfall 5 fixes; returns sorted list; no shared mutable state captured
- `fillScanlineSpans(inside, y, intersections, mapWidth)` — even-odd span fill; only mutates the specific row it owns

**CC reduction:** 17 → ~4 (for y-loop + size check in `scanlineFill`; helpers each ~3-6)

**Oracle results:**
- `./gradlew :gbkt-genre-sport:test` — BUILD SUCCESSFUL, 38 tests (direct oracle — no sport example ROM per Pitfall 8/A3)
- 7-example non-regression sweep — BUILD SUCCESSFUL; sport module not in any example dependency tree

**ROM hashes (Task 1 post-refactor):**
```
banks.gb            12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274
breakout.gb         564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a
metasprites-stress  bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de
metasprites.gb      9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3
platformer-template 9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7
pong.gb             PASS* (non-deterministic toolchain — pre-existing sdcc/lcc issue)
simple-physics.gb   247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad
```

## Task 2: RpgVisitor.generateApplyEffectFunction (E-29)

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/RpgVisitor.kt`

**Extracted helpers:**
- `buildImmunityCheckStatements(def): List<CStatement>` — GAP-6 for-loop over `immuneToEffects`; returns one CIf early-return guard per immune-to entry
- `buildResistCheckStatements(def): List<CStatement>` — GAP-5 `when(resistType)` dispatch; STAT_CONTEST arm returns CVarDecl + CIf; FLAT arm returns optional CIf; buildList{} wrapper mirrors original `add()` pattern
- `buildStackHandlingStatements(id, def): List<CStatement>` — `when(stackMode)` NONE/REFRESH_DURATION+INDEPENDENT/INTENSITY dispatch; REFRESH_DURATION+INDEPENDENT multi-label arm preserved exactly; inner stackBody buildList preserved

**CC reduction:** 16 → ~4 (the `generateApplyEffectFunction` shell has no control flow; each helper is ~3-8)

**Byte-identity sweep (Task 2 post-refactor):** 6/6 non-pong ROMs IDENTICAL to Task 1 hashes; pong PASS*
```
banks.gb            12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274  IDENTICAL
breakout.gb         564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a  IDENTICAL
metasprites-stress  bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de  IDENTICAL
metasprites.gb      9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3  IDENTICAL
platformer-template 9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7  IDENTICAL
pong.gb             PASS* (expected non-deterministic toolchain hash change)
simple-physics.gb   247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad  IDENTICAL
```

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None — pure structural refactor inside existing private methods; no new network endpoints, auth paths, file access patterns, or schema changes.

## Known Stubs

None — this plan is a pure refactor (no new data paths, no UI rendering).

## EMITTING S3776 Milestone Status

All 29 EMITTING findings closed across Plans 18-15..18-26:
- E-01..E-27: Plans 18-15 through 18-25
- E-28 (TrackSynthesizer.scanlineFill): this plan, commit 06555a74
- E-29 (RpgVisitor.generateApplyEffectFunction): this plan, commit 2c86b261

NOSONAR budget used: 1 (E-12 CEmitter.emit — flat sealed-type dispatch, within ≤5 milestone budget).

## Self-Check: PASSED

- [x] `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/TrackSynthesizer.kt` — modified and committed 06555a74
- [x] `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/RpgVisitor.kt` — modified and committed 2c86b261
- [x] Commit 06555a74 exists in git log
- [x] Commit 2c86b261 exists in git log
- [x] `./gradlew :gbkt-genre-sport:test` — BUILD SUCCESSFUL
- [x] 7-example ROM sweep — BUILD SUCCESSFUL, 6/6 non-pong identical
- [x] `./gradlew :gbkt-genre-sport:spotlessApply :gbkt-genre-sport:detekt` — BUILD SUCCESSFUL
- [x] `./gradlew :gbkt-backend-gbdk:spotlessApply :gbkt-backend-gbdk:detekt` — BUILD SUCCESSFUL
