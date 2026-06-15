---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "02"
subsystem: gbkt-backend-api, gbkt-backend-gbdk, gbkt-genre-platformer
tags: [predicate-consolidation, seed-closure, fix, contract-test, backend-api]
dependency_graph:
  requires: [21-01]
  provides: [gameUsesTilemapCollisionPathC-shared-util, tilemap-collision-lockstep-test]
  affects: [gbkt-backend-api, gbkt-backend-gbdk, gbkt-genre-platformer]
tech_stack:
  added: []
  patterns:
    - shared-top-level-util-in-backend-api (TilemapCollisionGate.kt — mirrors sanitizeCId pattern)
    - path-c-first-check-delegation (both callers delegate Path C before local Path A/B)
    - lockstep-contract-test-over-4-fixture-matrix (TilemapCollisionPredicateLockstepTest)
key_files:
  created:
    - gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/TilemapCollisionGate.kt
    - gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionPredicateLockstepTest.kt
    - .planning/seeds/archive/SEED-022-tilemap-collision-predicate-consolidation.md
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
    - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
decisions:
  - "Shared Path-C util placed in gbkt-backend-api (both callers depend on it; no circular dep)"
  - "Path A stays local in each caller (GBDKPipeline uses reflection; PlatformerVisitor uses direct cast)"
  - "Lockstep test observes pipeline-side via is_tile_solid emission rather than calling private visitor method"
metrics:
  duration: "6 min"
  completed: "2026-06-14"
  tasks: 2
  files: 5
---

# Phase 21 Plan 02: Tilemap-Collision Predicate Consolidation (SEED-022) Summary

**One-liner:** Extracted `gameUsesTilemapCollisionPathC(GameIR)` into `gbkt-backend-api/TilemapCollisionGate.kt` and routed both callers through it, fixing the previously-missing Path C in `PlatformerVisitor` (latent bug: visitor under-detected tilemap-collision games); a 4-fixture lockstep contract test guards against future divergence.

## What Was Built

### Task 1: Shared Path-C util + caller delegation + SEED-022 archival

Created `TilemapCollisionGate.kt` in `gbkt-backend-api` with a single top-level function:
```kotlin
fun gameUsesTilemapCollisionPathC(gameIR: GameIR): Boolean =
    gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
        (sys.config["type"] as? String) == "tilemap_collision"
    }
```
File follows the `sanitizeCId` pattern in `GenreSystemVisitor.kt` — MPL 2.0 header, package `io.github.gbkt.backend.api`, imports only `GameIR` and `GenericSystem`. KDoc cites SEED-022 and explains the Path-A local split.

**GBDKPipeline.gameUsesTilemapCollision** — replaced its 8-line inline Path-C block with `if (gameUsesTilemapCollisionPathC(gameIR)) return true`. Import added. Path A (reflective) and Path B (zone override) preserved verbatim.

**PlatformerVisitor.gameUsesTilemapCollision** — added `if (gameUsesTilemapCollisionPathC(gameIR)) return true` as the first check (the previously-missing Path C — the bug fix). Updated KDoc to document all three paths. Removed the SEED-022 deferred marker comment from `visitCamera` (lines 1557-1559).

Archived SEED-022 seed with a FIXED note appended to the file before `git mv` to `.planning/seeds/archive/`.

### Task 2: Lockstep contract test over 4-fixture matrix

Created `TilemapCollisionPredicateLockstepTest.kt` in `gbkt-genre-platformer` with 4 tests:

| Fixture | Description | `gameUsesTilemapCollisionPathC` | Pipeline `is_tile_solid` |
|---------|-------------|--------------------------------|--------------------------|
| 1 | `tilemap_collision` GenericSystem (Path C) | true | emitted |
| 2 | `platformer_physics` with `solidThreshold` (Path A) | false | emitted (via Path A) |
| 3 | per-zone `platformerPhysicsOverride` with `solidThreshold` key (Path B) | false | emitted (via Path B) |
| 4 | none of the above | false | NOT emitted |

Observable strategy: `PlatformerVisitor.gameUsesTilemapCollision` is private; the test calls `gameUsesTilemapCollisionPathC` directly for the shared-util assertion, then observes the pipeline-side behavior via the presence/absence of `is_tile_solid` in `main.c`. This confirms both callers produce the expected combined verdict.

## Verification

- `grep -n "fun gameUsesTilemapCollisionPathC"` returns line 34 in `TilemapCollisionGate.kt`
- `grep -c "gameUsesTilemapCollisionPathC" GBDKPipeline.kt` = 2 (import + call)
- `grep -c "gameUsesTilemapCollisionPathC" PlatformerVisitor.kt` = 3 (import + call + KDoc reference)
- `grep -c "Deferred (SEED-022)" PlatformerVisitor.kt` = 0
- SEED-022 absent from `.planning/seeds/`, present in `.planning/seeds/archive/`
- `:gbkt-backend-api:test` GREEN
- `:gbkt-backend-gbdk:test` GREEN (Path A/B behavior unchanged)
- `:gbkt-genre-platformer:test` GREEN (4 new lockstep tests + all pre-existing tests)
- `spotlessApply` + `detekt` clean for all 3 modules

## Commits

| Task | Commit | Files | Description |
|------|--------|-------|-------------|
| 1 | b84833e9 | 4 | feat(21-02): extract shared gameUsesTilemapCollisionPathC util + fix visitor Path-C miss (SEED-022) |
| 2 | 5b2760fa | 1 | test(21-02): add TilemapCollisionPredicateLockstepTest over 4-fixture matrix (SEED-022) |

## Deviations from Plan

None — plan executed exactly as written. The PATTERNS.md fixture matrix and file-creation shapes were followed precisely.

## Threat Flags

None — offline build-time codegen only; no new runtime input surface, no network, no auth.

## Known Stubs

None — the predicate consolidation is fully wired; the lockstep test covers the complete 4-fixture matrix.

## Self-Check: PASSED

- [x] `TilemapCollisionGate.kt` created (line 34: `fun gameUsesTilemapCollisionPathC`)
- [x] `GBDKPipeline.kt` imports and calls `gameUsesTilemapCollisionPathC` (2 occurrences)
- [x] `PlatformerVisitor.kt` imports and calls `gameUsesTilemapCollisionPathC` (3 occurrences)
- [x] `Deferred (SEED-022)` marker count in PlatformerVisitor = 0
- [x] SEED-022 absent from `.planning/seeds/` directory
- [x] SEED-022 present in `.planning/seeds/archive/` with FIXED note
- [x] `TilemapCollisionPredicateLockstepTest.kt` created (4 tests, all GREEN)
- [x] Commits b84833e9 and 5b2760fa in git log
- [x] All 3 modules: spotless + detekt clean
