---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 11
subsystem: dsl-bg-fill
tags: [tdd, metasprite, bg-fill, checkerboard, visual-parity]
dependency_graph:
  requires: [10-10]
  provides: [bgFillCheckerboard-dsl, bg-checkerboard-emission]
  affects: [gbkt-lang, gbkt-backend-gbdk]
tech_stack:
  added: []
  patterns: [RawOp-via-ScriptBuilderContext, TDD-RED-GREEN]
key_files:
  created:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/BgCheckerboardEmissionTest.kt
  modified:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt
decisions:
  - "Used RawOp via ScriptBuilderContext.current (consistent with moveMetasprite pattern) instead of new IR node — no BgFill surface existed, CRawCode path is minimal and correct"
  - "Pattern constant declared as static const inside function body for SDCC ROM placement without requiring file-scope emission path"
metrics:
  duration: ~8 minutes
  completed: 2026-05-18T16:06:13Z
  tasks_completed: 1
  files_changed: 2
---

# Phase 10 Plan 11: BG Checkerboard Fill Emission Summary

**One-liner:** `bgFillCheckerboard()` DSL helper emits `fill_bkg_rect + set_bkg_data` with 16-byte checkerboard pattern for D-10 visual parity.

## What Was Built

Added `bgFillCheckerboard()` top-level DSL function in `MetaspriteBuilder.kt` (gbkt-lang module). Callable from any `ScriptBuilder` scope (e.g. `scene { enter { bgFillCheckerboard() } }`).

**Emits (via single RawOp):**
```c
static const UINT8 _checkerboard_bg_pattern[] = {
    0x80,0x80,0x40,0x40,0x20,0x20,0x10,0x10,
    0x08,0x08,0x04,0x04,0x02,0x02,0x01,0x01};
fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
set_bkg_data(0, 1, _checkerboard_bg_pattern);
```

Pattern bytes are verbatim from reference `metasprites.c` line 43.

## TDD Gate Compliance

| Phase | Commit | Status |
|-------|--------|--------|
| RED | `96017f42` | BgCheckerboardEmissionTest 4 tests — compile error confirmed (bgFillCheckerboard unresolved) |
| GREEN | `8f8d4274` | BgCheckerboardEmissionTest 4/4 PASS |

## Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Add failing BgCheckerboardEmissionTest | 96017f42 | BgCheckerboardEmissionTest.kt |
| 1 (GREEN) | Implement bgFillCheckerboard() helper | 8f8d4274 | MetaspriteBuilder.kt |

## Test Coverage

`BgCheckerboardEmissionTest` (4 tests, all GREEN):
1. `bgFillCheckerboard emits fill_bkg_rect in play_enter` — brace-walk scoped to play_enter body
2. `bgFillCheckerboard emits set_bkg_data in play_enter` — brace-walk scoped to play_enter body
3. `bgFillCheckerboard emits checkerboard pattern constant at file scope` — checks bank1.c for reference bytes
4. `bgFillCheckerboard does not emit printf in play_enter` — Pitfall 5 guard

## Verification

```
./gradlew :gbkt-lang:test :gbkt-backend-gbdk:test → BUILD SUCCESSFUL
```

## Deviations from Plan

None - plan executed exactly as written.

The plan offered CRawCode as the "simpler shape" when no BgFill surface existed, and that is exactly what was implemented. No new ScriptOp IR node was needed.

## 10-08 Preservation Confirmed

`GBDKPipelineV2.buildHomeFile()` flipX/flipY global declarations (Plan 10-08) verified intact:
- `_${ms.id}_flipX` and `_${ms.id}_flipY` CVarDecl blocks at lines 790-791
- `metaspriteRuntimeVars` included in `allVariablesRaw` at line 890

## Known Stubs

None. `bgFillCheckerboard()` is fully wired — pattern bytes are literal, both GBDK calls are emitted.

## Self-Check: PASSED

- FOUND: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt`
- FOUND: `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/BgCheckerboardEmissionTest.kt`
- FOUND commit: `96017f42` (TDD RED)
- FOUND commit: `8f8d4274` (TDD GREEN)
