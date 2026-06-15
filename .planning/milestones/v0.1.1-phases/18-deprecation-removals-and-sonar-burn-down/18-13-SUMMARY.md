---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 13
subsystem: gbkt-backend-gbdk/codegen/pipeline
tags: [sonar-s3776, extract-method, gbdk-pipeline, cognitive-complexity]
dependency_graph:
  requires: [18-01, 18-12]
  provides: [SONAR-01-partial, SONAR-02]
  affects: [gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns:
    - "value-returning sub-builder extraction (List<CVarDecl> / List<CDefine> return; no shared-accumulator mutation)"
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt
decisions:
  - "D-06 per-finding commit: each finding (E-03, E-04) gets its own commit + 7-example ROM sweep"
  - "Value-returning helpers, never mutate a shared mutable accumulator (Pitfall 1 safe)"
  - "GenericSystem early-return when genreVisitor != null (semantically identical to original else-if chain)"
  - "buildZoneDefsForHome returns Pair<List<CDefine>, List<CVarDecl>> matching destructure call site"
metrics:
  duration: 8 min
  completed: 2026-06-13
---

# Phase 18 Plan 13: E-03 + E-04 GBDKPipeline Extract-Method Summary

**One-liner:** Extract per-system global-var and home-file section sub-builders from GBDKPipeline.kt to resolve S3776 findings E-03 (cc=71) and E-04 (cc=44).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extract-method GBDKPipeline.buildSystemGlobalVars (E-03) | 2dd7ff46 | GBDKPipeline.kt |
| 2 | Extract-method GBDKPipeline.buildHomeFile (E-04) | 0166b9d7 | GBDKPipeline.kt |

## What Was Built

### Task 1: E-03 `buildSystemGlobalVars` (cc=71 → below threshold)

Decomposed the monolithic `buildSystemGlobalVars` for-loop+when dispatch into 8 focused per-system value-returning helpers. Each helper returns `List<CVarDecl>` and early-returns `emptyList()` when its subsystem is absent.

Extracted helpers:
- `buildCameraSystemGlobalVars()` — 5 camera-state vars
- `buildExplorationSystemGlobalVars(gameIR, system)` — exploration, gauge, key vars; delegates to:
  - `buildEntityCollisionGlobalVars(gameIR)` — entity collision arrays and position vars
  - `buildEntityPushAllowedInit(collisionActors)` — push-direction bitmask computation
- `buildDialogSystemGlobalVars(system)` — default speed + border vars
- `buildGenericSystemGlobalVars(gameIR, system, sanitizedId, genreVisitors)` — genre visitor dispatch + type-specific vars; delegates to:
  - `buildAudioMixerSystemGlobalVars(system)` — mixer channel, volume, priority vars
- `buildCombatEngineSystemGlobalVars(system, sanitizedId, gameIR)` — ATB, wave, hook globals

The outer `buildSystemGlobalVars` retains the `for (system in gameIR.systems) { when (system) { ... } }` skeleton with single-line per-branch calls.

### Task 2: E-04 `buildHomeFile` (cc=44 → below threshold)

Extracted 10 focused private helpers from the inline blocks inside `buildHomeFile`. All helpers return values — no shared mutable state.

Extracted helpers:
- `buildCrossBankHelperList(gameIR, bankAllocation)` — sport_racing / zone-binder cross-bank wrapper decision
- `buildPaletteDataRaw(gameIR)` — GBC palette raw-C section (Plan 10.1-22 / D-08)
- `buildHomeFileRawSections(...)` — 8-input ordered raw section assembly
- `buildCollisionFunctionsWithFallback(gameIR, collisionFunctionsRaw)` — exploration stub injection
- `buildZoneDefsForHome(gameIR, bankAllocation)` — banking vs. legacy zone data dispatch
- `buildAllHomeFileIncludes(gameIR, soundVisitor)` — all #include assembly
- `buildMenuCursorDefinesForHome(gameIR)` — MENU_CURSOR_SPRITE_ID define
- `buildAudioMixerDefinesForHome(gameIR)` — MIXER_GROUP_* defines
- `buildEntityCollisionDefinesForHome(gameIR)` — MAX_ENTITIES / MAP_SIZE defines
- `buildAtbDefinesForHome(gameIR)` — ATB_BASE_RATE / ATB_MAX_GAUGE defines

## Byte-Identity ROM Sweep Evidence

Both tasks: 6/6 non-pong ROMs sha256-identical to pre-refactor baseline; pong main.c sha256-identical (PASS* — known sdcc/lcc non-determinism).

### Pre-refactor baselines (captured before any edit)

| Example | SHA256 (pre) |
|---------|-------------|
| pong.gb | 6c6d428c (PASS* — non-det) |
| pong main.c | b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1 |
| breakout.gb | 564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a |
| simple-physics.gb | 247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad |
| metasprites.gb | 9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3 |
| metasprites-stress.gb | bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de |
| banks.gb | 12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274 |
| platformer-template.gb | 9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7 |

### After E-03 commit (2dd7ff46)

| Example | SHA256 (post) | Result |
|---------|--------------|--------|
| pong main.c | b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1 | PASS* |
| breakout.gb | 564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a | PASS |
| simple-physics.gb | 247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad | PASS |
| metasprites.gb | 9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3 | PASS |
| metasprites-stress.gb | bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de | PASS |
| banks.gb | 12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274 | PASS |
| platformer-template.gb | 9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7 | PASS |

### After E-04 commit (0166b9d7)

| Example | SHA256 (post) | Result |
|---------|--------------|--------|
| pong main.c | b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1 | PASS* |
| breakout.gb | 564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a | PASS |
| simple-physics.gb | 247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad | PASS |
| metasprites.gb | 9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3 | PASS |
| metasprites-stress.gb | bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de | PASS |
| banks.gb | 12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274 | PASS |
| platformer-template.gb | 9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7 | PASS |

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

None — purely internal codegen refactor; no new network endpoints, auth paths, or schema changes.

## Self-Check: PASSED

- `2dd7ff46` exists: confirmed via `git log`
- `0166b9d7` exists: confirmed via `git log`
- GBDKPipeline.kt exists: FOUND
- 19 extracted private helpers present in file: CONFIRMED
- spotlessApply: GREEN (both tasks)
- detekt: GREEN (both tasks)
- ROM sweep: 6/6 PASS + 1 PASS* (both tasks)
