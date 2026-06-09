---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: "02"
subsystem: gbkt-ir
tags: [ir, metasprite, serializer, data-model, tdd]
dependency_graph:
  requires: [10-01]
  provides: [MetaspriteIR, MetaspriteFrame, MetaspriteTile, GameIRSerializer.metasprites]
  affects: [gbkt-lang, gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns: [TDD RED/GREEN, non-sealed IR data class, JSONObject serialize/deserialize]
key_files:
  created:
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/MetaspriteIRTest.kt
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt (wave-1 stub — no changes needed, already complete)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt (wave-1 stub — already had metasprites field)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt (added metasprite serialize/deserialize)
decisions:
  - "Wave-1 stub was already complete for MetaspriteIR.kt and GameIR.kt — plan scope reduced to serializer extension and tests only"
  - "MetaspriteIR does not implement PlatformAnnotatable per 10-PATTERNS.md deviation note"
  - "Serializer uses optJSONArray with emptyList fallback for backward-compatible JSON"
metrics:
  duration_minutes: 15
  completed: "2026-05-18T15:36:45Z"
  tasks_completed: 2
  files_changed: 2
---

# Phase 10 Plan 02: MetaspriteIR Data Model — Summary

MetaspriteIR leaf-module addition with full GameIRSerializer round-trip. Three data classes (`MetaspriteIR`, `MetaspriteFrame`, `MetaspriteTile`) were already complete in the wave-1 stub; this plan extended `GameIRSerializer` with metasprite serialize/deserialize support and added 13 IR-shape + round-trip tests.

## What Was Built

### MetaspriteIR.kt (wave-1 stub — already complete)
- `MetaspriteIR(id: String, frames: List<MetaspriteFrame>, sourceLocation: SourceLocation? = null)`
- `MetaspriteFrame(tiles: List<MetaspriteTile>)`
- `MetaspriteTile(relX: Int, relY: Int, tileId: Int)`
- Does NOT implement `PlatformAnnotatable` (per 10-PATTERNS.md deviation note)

### GameIR.kt (wave-1 stub — already complete)
- `val metasprites: List<MetaspriteIR> = emptyList()` immediately after `actors` field
- All existing GameIR construction sites backward-compatible (default emptyList())

### GameIRSerializer.kt (added in this plan)
- `serializeMetaspriteIR` / `deserializeMetaspriteIR`
- `serializeMetaspriteFrame` / `deserializeMetaspriteFrame`
- `serializeMetaspriteTile` / `deserializeMetaspriteTile`
- `metasprites` wired into `serializeGameIR` (after `actors`) and `deserializeGameIR`
- Uses `optJSONArray("metasprites")` — gracefully returns emptyList for old JSON without the key

### MetaspriteIRTest.kt (13 tests)
**IR shape tests (Task 1):**
- MetaspriteIR constructs with id, emptyList frames, null sourceLocation by default
- MetaspriteFrame constructs and exposes tiles of correct size
- MetaspriteTile relX/relY/tileId fields accessible
- Multi-frame, multi-tile construction
- sourceLocation can be set and read

**GameIR/Serializer tests (Task 2):**
- GameIR(name="X") has empty metasprites (backward compat)
- GameIR(metasprites=listOf(...)) preserves them
- Round-trip through GameIRSerializer (basic, with sourceLocation, multi-frame multi-tile)

## TDD Gate Compliance

- RED commit: `f102c03c` — 3 serializer round-trip tests failing (expected — serializer not yet extended)
- GREEN commit: `9cf4d336` — all 13 tests passing after serializer extension

## Deviations from Plan

### Wave-1 Pre-implementation

**Context:** Wave-1 (plan 10-10) created `MetaspriteIR.kt` as a "minimal stub" but in practice implemented the full data class shape including all three types with correct fields and KDocs. Similarly, `GameIR.kt` already had the `metasprites: List<MetaspriteIR> = emptyList()` field.

**Effect on plan scope:** Tasks 1 and 2 were scoped to CREATE these files, but wave-1 had already done the complete implementation. This plan's actual work was:
- Write the test file (MetaspriteIRTest.kt)
- Extend GameIRSerializer.kt with metasprite serialize/deserialize methods

**Deviation type:** None — the outcome matches the plan's acceptance criteria exactly. The wave-1 pre-implementation is documented in the `important_context_from_wave_1` executor prompt.

## Verification

```
./gradlew :gbkt-ir:test
```
All 13 MetaspriteIRTest tests pass. All pre-existing gbkt-ir tests pass (no regressions).

```
./gradlew :gbkt-ir:compileKotlin :gbkt-lang:compileKotlin :gbkt-backend-gbdk:compileKotlin
```
Downstream smoke check passes — all three modules compile cleanly.

## Known Stubs

None. The MetaspriteIR types are full data classes with no placeholder values. The serializer implements full round-trip fidelity.

## Threat Flags

None. This plan adds pure data classes and a JSON serializer extension. No network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.

## Self-Check: PASSED

- [x] `gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/MetaspriteIRTest.kt` — exists
- [x] `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt` — exists, declares MetaspriteIR/MetaspriteFrame/MetaspriteTile
- [x] `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` — contains `metasprites: List<MetaspriteIR> = emptyList()`
- [x] `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt` — contains `serializeMetaspriteIR` and `deserializeMetaspriteIR`
- [x] RED commit `f102c03c` — exists in git log
- [x] GREEN commit `9cf4d336` — exists in git log
- [x] `./gradlew :gbkt-ir:test` exits 0
