---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: "06"
subsystem: metasprite-codegen
tags: [codegen, metasprite, gbdk, tdd, craw-code]
dependency_graph:
  requires: [10-05]
  provides: [MetaspriteVisitor.generateMetaspriteDescriptor]
  affects:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt
tech_stack:
  added: []
  patterns:
    - CRawCode escape hatch for struct-literal array emission
    - TDD RED/GREEN cycle for visitor method development
key_files:
  created:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitorDescriptorTest.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt
decisions:
  - "Used CRawCode escape hatch because typed C AST has no struct-literal array primitive for {int8_t, int8_t, uint8_t} initializer lists"
  - "Coordinate order {dy, dx, dtile} matches GBDK METASPRITE_DEF convention — relY maps to dy (Y first), relX maps to dx"
  - "Per-frame array names: sprite_metasprite_N (matching reference png2asset output); pointer table: sprite_metasprites"
metrics:
  duration: "~10 minutes"
  completed: "2026-05-18"
  tasks_completed: 1
  files_changed: 2
---

# Phase 10 Plan 06: MetaspriteVisitor Descriptor Emission Summary

## One-liner

`generateMetaspriteDescriptor()` added to `MetaspriteVisitor` — emits `const metasprite_t sprite_metasprite_N[]` per-frame OAM arrays and `const metasprite_t* const sprite_metasprites[]` pointer table via `CRawCode` escape hatch, faithful to GBDK variable-length metasprite convention.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 RED | Add failing tests for generateMetaspriteDescriptor | 3c933711 | MetaspriteVisitorDescriptorTest.kt (new) |
| 1 GREEN | Implement generateMetaspriteDescriptor() | 46b607e0 | MetaspriteVisitor.kt (modified) |

## What Was Built

`MetaspriteVisitor.generateMetaspriteDescriptor(MetaspriteIR): CRawCode` builds the C global declarations that GBDK's `move_metasprite_*` family requires at runtime:

```c
const metasprite_t sprite_metasprite_0[] = {
    {0, 0, 0}, {0, 8, 1}, {metasprite_end}
};
const metasprite_t* const sprite_metasprites[] = {
    sprite_metasprite_0,
};
```

Key design points:
- Uses `CRawCode` because `CStatement` typed hierarchy has no struct-literal array primitive.
- Coordinate order `{dy, dx, dtile}` matches GBDK `METASPRITE_DEF` (`int8_t dy, dx; uint8_t dtile`) — `relY` maps to `dy` (Y first), `relX` maps to `dx`.
- `{metasprite_end}` sentinel terminates each frame array per GBDK convention.
- Pointer table element names `sprite_metasprite_N` match the reference `png2asset` output.
- Works for single-frame and multi-frame metasprites.

## Verification

- `./gradlew :gbkt-backend-gbdk:test --tests "*MetaspriteVisitorDescriptorTest*"` — 3/3 tests GREEN
- `./gradlew :gbkt-backend-gbdk:test --tests "*MetaspriteVisitor*Test*"` — all MetaspriteVisitor tests pass (Plan 05 TileData + Plan 06 Descriptor)

## Deviations from Plan

None — plan executed exactly as written.

## TDD Gate Compliance

- RED commit `3c933711` — test file with 3 failing tests (compile error: unresolved reference)
- GREEN commit `46b607e0` — implementation makes all 3 tests pass
- No REFACTOR needed — implementation is clean and minimal

## Known Stubs

None. `generateMetaspriteDescriptor()` is a complete, wired implementation with no placeholders.

## Threat Flags

None — this is a pure JVM-tier codegen method with no security-relevant surface.

## Self-Check: PASSED

- [x] MetaspriteVisitorDescriptorTest.kt exists at `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitorDescriptorTest.kt`
- [x] MetaspriteVisitor.kt modified (contains `generateMetaspriteDescriptor`)
- [x] RED commit 3c933711 exists: `git log --oneline --all | grep 3c933711`
- [x] GREEN commit 46b607e0 exists: `git log --oneline --all | grep 46b607e0`
- [x] All 3 descriptor tests GREEN
- [x] Plan 05 tile-data tests still GREEN (no regressions)
