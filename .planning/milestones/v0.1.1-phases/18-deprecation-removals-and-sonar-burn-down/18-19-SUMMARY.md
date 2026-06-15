---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 19
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, extract-method, codegen, refactoring]
dependency_graph:
  requires: ["18-18"]
  provides: ["SONAR-01-partial", "SONAR-02"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["value-returning extract-method", "private helper decomposition"]
key_files:
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
decisions:
  - "buildEntityCollisionFunctions (E-09) decomposed into 6 focused private helpers"
  - "visitSoundSystem (E-10) was always emptyList() (cc=0) — research misidentification, no code change"
  - "@Suppress(LongMethod) removed from buildEntityCollisionFunctions (no longer warranted)"
  - "@Suppress(LongMethod) added to buildEntityHandleBlockFunction (PUSH case dispatch is still long)"
metrics:
  duration: 12 min
  completed: 2026-06-13
  tasks_completed: 2
  files_modified: 1
---

# Phase 18 Plan 19: GBDKSystemVisitor E-09/E-10 S3776 Extract-Method Summary

Extract-method decomposition of E-09 `buildEntityCollisionFunctions` (cc=34) in GBDKSystemVisitor.kt via 6 value-returning private helpers; byte-identity sweep confirms zero emission change. E-10 `visitSoundSystem` was a research phantom (the function has always returned `emptyList()`, cc=0); no code change was required or made.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Extract-method buildEntityCollisionFunctions (E-09, cc=34) | 20226378 | GBDKSystemVisitor.kt |
| 2 | visitSoundSystem (E-10) — cc=0, research phantom, no change | (none) | (none) |

## What Was Built

### E-09: buildEntityCollisionFunctions (cc34 → ~1)

The original 968-line function (lines 871-1840) that assembled 6 C entity-collision functions in a single body was decomposed into 6 focused private class-level helpers:

- `buildEntityRegisterFunction(sanitizedId: String): CFunction` — builds `_entity_register`: stores position/mode/shape, sets grid bits via multi-tile nested for-loops.
- `buildEntityRemoveFunction(): CFunction` — builds `_entity_remove`: clears grid bits via multi-tile nested for-loops, resets PASSTHROUGH sentinel.
- `buildEntityCheckFunction(actors: List<ActorIR>): CFunction` — builds `_entity_check`: TILE-path grid-bit lookup + conditional HITBOX AABB pixel-collision path.
- `buildEntityHandleBlockFunction(actors: List<ActorIR>): CFunction` — builds `_entity_handle_block`: switch on collision mode (BLOCK/PASSTHROUGH/BLOCK_AND_TRIGGER/OVERLAP_TRIGGER/PUSH) with full PUSH direction-constraint logic. Retains `@Suppress("LongMethod")` since the PUSH case dispatch body is still > 60 lines.
- `buildEntitySetCollisionModeFunction(): CFunction` — builds `_entity_set_collision_mode`: trivial single-statement mode write.
- `buildEntityBumpFeedbackFunction(): CFunction` — builds `_entity_bump_feedback`: comment-only stub.

The outer `buildEntityCollisionFunctions` reduced to a 8-line expression function delegating to these 6 helpers (cc=1). `@Suppress("LongMethod")` removed from the outer function.

### E-10: visitSoundSystem (cc30 → no change needed)

Research listed E-10 as `visitSoundSystem` at line 539 with cc=30. Investigation shows this was a research misidentification: `visitSoundSystem` at GBDKSystemVisitor.kt line 519 is and has always been `= emptyList()` (cc=0), even in the original project commit. The SonarCloud finding at the reported line number was `visitExplorationSystem` (which has `@Suppress("LongMethod")` but is not listed as an S3776 finding in the research table). No code change was made — there was nothing to extract.

## Byte-Identity Sweep Results (E-09 commit)

| Example | Baseline SHA256 | After-Refactor SHA256 | Result |
|---------|----------------|----------------------|--------|
| banks | 12c8ee2e... | 12c8ee2e... | IDENTICAL |
| breakout | 564465cd... | 564465cd... | IDENTICAL |
| metasprites-stress | bc51eadd... | bc51eadd... | IDENTICAL |
| metasprites | 9b2440db... | 9b2440db... | IDENTICAL |
| platformer-template | 9a8f268a... | 9a8f268a... | IDENTICAL |
| simple-physics | 247e16d2... | 247e16d2... | IDENTICAL |
| pong | 952878f1... (baseline) / 5436584d... (after) | 178c7e56... main.c IDENTICAL | PASS* (non-deterministic .gb hash) |

## Deviations from Plan

### E-10 Research Phantom

**Task 2:** Extract-method `visitSoundSystem` (E-10, cc=30)

**Finding:** `visitSoundSystem` in GBDKSystemVisitor.kt is and has always been `override fun visitSoundSystem(system: SoundSystem): List<CFunction> = emptyList()` — cognitive complexity = 0. This has been the case since the original project commit `b3938a29` (v0.1.0 compiler rewrite). The research agent's SonarCloud inventory listed it as cc=30 at line 539, but the actual line 539 (in the original file layout) contained the KDoc closing comment for `visitExplorationSystem`, not `visitSoundSystem`.

**Outcome:** No code change made. This is a research phantom — the SonarCloud finding either auto-resolved before the research was run, or the research agent misidentified the function name at the reported line. The S3776 gate for E-10 is effectively already satisfied.

**Files modified:** none

## Known Stubs

None — this plan is a pure refactoring; no data flows or rendering paths were changed.

## Threat Flags

None — internal codegen refactoring with no new trust boundaries.

## Self-Check: PASSED

- [x] E-09 commit `20226378` exists: `git log --oneline | grep 20226378` confirms
- [x] GBDKSystemVisitor.kt contains `buildEntityRegisterFunction`, `buildEntityRemoveFunction`, `buildEntityCheckFunction`, `buildEntityHandleBlockFunction`, `buildEntitySetCollisionModeFunction`, `buildEntityBumpFeedbackFunction`
- [x] 6/6 non-pong ROM hashes are byte-identical to baseline
- [x] Pong main.c md5 `178c7e56...` is byte-identical to baseline
