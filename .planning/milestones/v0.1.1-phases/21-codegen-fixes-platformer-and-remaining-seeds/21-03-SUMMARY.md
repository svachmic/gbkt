---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "03"
subsystem: gbkt-ir
tags: [serializer, round-trip, seed-closure, SEED-020, FIX-06]
dependency_graph:
  requires: []
  provides: [GameIRSerializer-10-collection-deserializers, GameIRSerializerRoundTripTest]
  affects: [gbkt-ir]
tech_stack:
  added: []
  patterns: [deserializeList, supported-subset contract, KDoc round-trip table]
key_files:
  created:
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/GameIRSerializerRoundTripTest.kt
    - .planning/seeds/archive/SEED-020-gameir-serializer-full-roundtrip.md
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/GameIRSerializerSubsystemsTest.kt
decisions:
  - Option 2 (explicit documented contract) chosen over Option 1 (full round-trip): GenericSystem id+type recoverable; full typed SystemIR (CombatEngineSystem etc.) remains serialize-only due to open-interface complexity
  - ZoneIR serialize side updated to emit name+spawnX+spawnY (not previously emitted) to enable round-trip
  - PuzzleObjectIR reconstructed as SwitchObjectIR placeholder (x=0, y=0) since sealed interface requires concrete type
  - GameIRSerializerSubsystemsTest "serialize-only" assertion updated to reflect new deserializer behavior (Rule 1 bug fix)
metrics:
  duration: "5 minutes"
  completed: "2026-06-14"
  tasks_completed: 2
  files_modified: 4
---

# Phase 21 Plan 03: GameIR Serializer Round-Trip Summary

**One-liner:** Closed SEED-020 by replacing all 10 emptyList() deserialization stubs with real supported-subset deserializers and a round-trip test guarding the contract.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Deserialize 10 stubbed collections with documented contract | c356e772 | GameIRSerializer.kt, GameIRSerializerSubsystemsTest.kt |
| 2 | Round-trip test + SEED-020 archival | d9977895 | GameIRSerializerRoundTripTest.kt, SEED-020 archive |

## What Was Built

### Task 1: 10 deserializers + KDoc contract

Replaced all 10 `emptyList()` stubs in `GameIRSerializer.deserializeGameIR()` with real `deserializeList` blocks:

| Collection | Recovered fields |
|------------|-----------------|
| GlobalFlagsIR | id |
| ItemCategoryDef | id |
| ItemDef | id, name, categoryId |
| ContainerIR | id, slots |
| DropTableIR | id |
| PuzzleObjectIR | id (as SwitchObjectIR placeholder) |
| CollisionGroupIR | id |
| CollisionRuleIR | groupA, groupB, response |
| ZoneIR | id, name, spawnX, spawnY, screenMode, tilesetPath |
| SystemIR (GenericSystem) | id, config["type"] |

Also updated the serialize side:
- `serializeZoneIR` now emits `name`, `spawnX`, `spawnY` (previously missing)
- `serializeSystemIR` now emits `configType` field for GenericSystem

### Task 2: Round-trip test + SEED archival

Created `GameIRSerializerRoundTripTest.kt` with two tests:
1. **Maximal fixture** — GameIR with all 10 collections populated; asserts non-empty collections and matching IDs after `toJson` → `fromJson`
2. **Minimal fixture** — empty GameIR round-trips to empty collections (no spurious elements)

Archived SEED-020 with FIXED note.

## Verification

- `./gradlew :gbkt-ir:test` — 218 tests GREEN (including 2 new round-trip tests)
- `./gradlew :gbkt-ir:spotlessApply :gbkt-ir:detekt` — CLEAN
- `grep -c "SEED-020.*emptyList"` returns 0 — all stubs replaced
- SEED-020 absent from `.planning/seeds/`, present in `.planning/seeds/archive/`
- No codegen blast radius — serializer is off the Kotlin→C compilation path

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] GameIRSerializerSubsystemsTest "serialize-only" assertion**
- **Found during:** Task 1 GREEN phase
- **Issue:** `GameIRSerializerSubsystemsTest` had assertions `assertTrue(back.zones.isEmpty())` and `assertTrue(back.collisionRules.isEmpty())` documenting the old stale behavior
- **Fix:** Updated assertions to verify the new deserialized data (zone count, ids, screenMode, collisionRule fields)
- **Files modified:** `gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/GameIRSerializerSubsystemsTest.kt`
- **Commit:** c356e772

**2. [Rule 2 - Missing field] serializeZoneIR missing name/spawnX/spawnY**
- **Found during:** Task 1 implementation — ZoneIR.name is required but not emitted in old serializer
- **Issue:** `serializeZoneIR` only emitted `id`, `tilesetPath`, `screenMode` — missing `name` (required field) and `spawnX`/`spawnY` (needed for round-trip)
- **Fix:** Added `json.put("name", zone.name)` + conditional `spawnX`/`spawnY` emission
- **Files modified:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt`
- **Commit:** c356e772

## Known Stubs

No functional stubs remaining. The following are DOCUMENTED serialize-only limitations (not stubs):
- PuzzleObjectIR concrete type lost on round-trip (reconstructed as SwitchObjectIR placeholder) — concrete type discriminator not in the serialized {type, id} shape
- SystemIR non-GenericSystem types (CombatEngineSystem, etc.) cannot reconstruct from {id, configType} — documented in KDoc

## Threat Flags

No new security surface introduced. Deserializers use `optString`/`optInt` (T-21-03-01 mitigated — no crash on absent/null keys; `deserializeList` returns emptyList() for null arrays).

## Self-Check: PASSED

- `c356e772` exists in git log: FOUND
- `d9977895` exists in git log: FOUND
- `GameIRSerializer.kt` modified: FOUND
- `GameIRSerializerRoundTripTest.kt` created: FOUND
- `SEED-020` in archive: FOUND
