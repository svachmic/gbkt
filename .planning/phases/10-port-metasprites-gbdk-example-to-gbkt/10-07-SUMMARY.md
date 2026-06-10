---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 07
subsystem: codegen
tags: [metasprite, gbdk, c-codegen, oam, flip-switch, hiwater]

# Dependency graph
requires:
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 05
    provides: generateMetaspriteTileData() in MetaspriteVisitor
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 06
    provides: generateMetaspriteDescriptor() in MetaspriteVisitor
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    plan: 04
    provides: MoveMetasprite ScriptOp + visitMoveMetasprite stub in ScriptOpVisitor
provides:
  - generateMetaspriteFrameSwitch() method on MetaspriteVisitor (sub-area C)
  - ScriptOpVisitor.visitMoveMetasprite() real implementation (no longer a stub)
  - hiwater OAM management pattern (Pitfall 1 mitigation)
affects:
  - 10-port-metasprites-gbdk-example-to-gbkt/10-13 (port assembly — declares _idx/_rot/_posX/_posY)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CRawCode for full C block emission (typed AST has no native switch-with-arbitrary-rhs)"
    - "hiwater += move_metasprite_*(...) pattern mitigates variable-length OAM ghost sprites"
    - "gameIRContext.get()?.metasprites lookup for MetaspriteIR resolution in visitor"

key-files:
  created:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitorFrameSwitchTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitorMoveMetaspriteTest.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt

key-decisions:
  - "Use CRawCode for the full frame-switch block — typed C AST has no switch primitive capable of representing arbitrary move_metasprite_* RHS without CRawCode"
  - "Hardcode canonical variable names _idx/_rot/_posX/_posY per approach (a) from 10-PATTERNS §8 — port assembly (Plan 13) must declare these"
  - "Fallback to synthetic MetaspriteIR(frames=emptyList()) when gameIRContext unavailable — preserves structural correctness without requiring setGameIR in unit tests"
  - "Do NOT emit hide_sprites_range function body — ActorVisitor.generateHideSpritesRange already places it in main.c; this method only calls it"

patterns-established:
  - "hiwater pattern: uint8_t hiwater = 0u; hiwater += move_metasprite_*(...); hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)"
  - "gameIRContext lookup: gameIRContext.get()?.metasprites?.find { it.id == op.metaspriteId } with synthetic fallback"

requirements-completed: []

# Metrics
duration: 5min
completed: 2026-05-18
---

# Phase 10 Plan 07: MetaspriteVisitor Frame-Switch + ScriptOpVisitor Wiring Summary

**Per-frame metasprite rendering: 4-case flip-variant switch with hiwater OAM management wired end-to-end from moveMetasprite() DSL to emitted C**

## Performance

- **Duration:** 5 min
- **Started:** 2026-05-18T16:09:06Z
- **Completed:** 2026-05-18T16:13:58Z
- **Tasks:** 2 (each with RED + GREEN TDD commits)
- **Files modified:** 4

## Accomplishments

- Added `generateMetaspriteFrameSwitch()` to `MetaspriteVisitor` — emits 4-case switch on `_rot & 0x3u` (flipy/flipxy/flipx/default=ex) matching reference metasprites.c lines 241-284
- Pitfall 1 mitigation: `hiwater += move_metasprite_*(...)` pattern with `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` tail clears ghost OAM slots from variable-length frames
- Replaced Plan 04 stub in `ScriptOpVisitor.visitMoveMetasprite()` with real delegation to `MetaspriteVisitor.generateMetaspriteFrameSwitch()`
- All three MetaspriteVisitor sub-areas (tile-data/05, descriptor/06, frame-switch/07) are now implemented

## Task Commits

Each TDD task produced two commits (RED test → GREEN implementation):

1. **Task 1 RED: MetaspriteVisitorFrameSwitch tests** - `216e7c08` (test)
2. **Task 1 GREEN: generateMetaspriteFrameSwitch() implementation** - `b43e8501` (feat)
3. **Task 2 RED: ScriptOpVisitorMoveMetasprite wiring tests** - `0dd19da8` (test)
4. **Task 2 GREEN: visitMoveMetasprite wired to MetaspriteVisitor** - `ad2de682` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `gbkt-backend-gbdk/src/main/kotlin/.../MetaspriteVisitor.kt` — Added `generateMetaspriteFrameSwitch()` sub-area C method with KDoc documenting Plan 13 variable name contract
- `gbkt-backend-gbdk/src/main/kotlin/.../ScriptOpVisitor.kt` — Replaced stub `visitMoveMetasprite()` with real `MetaspriteVisitor.generateMetaspriteFrameSwitch()` delegation
- `gbkt-backend-gbdk/src/test/.../MetaspriteVisitorFrameSwitchTest.kt` — 10 tests verifying all frame-switch emission tokens
- `gbkt-backend-gbdk/src/test/.../ScriptOpVisitorMoveMetaspriteTest.kt` — 3 end-to-end pipeline tests verifying DSL → play_frame chain

## Decisions Made

- **CRawCode for full block**: Typed C AST has no switch primitive with arbitrary call RHS — `CRawCode` used for the entire frame-switch block (consistent with Plan 10-PATTERNS §8 recommendation)
- **Canonical variable names hardcoded**: Approach (a) from PATTERNS §8 — `_idx`, `_rot`, `_posX`, `_posY` are hardcoded; Plan 13 port assembly must declare these by convention
- **Synthetic MetaspriteIR fallback**: When `gameIRContext` is unavailable (unit tests calling `visit()` without `setGameIR()`), falls back to `MetaspriteIR(id=op.metaspriteId, frames=emptyList())` — the frame-switch emission is structurally correct regardless of frame content

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- Gradle build was initially run against the main repo rather than the worktree — resolved by using the worktree's `gradlew` with `-p` pointing to the worktree root. All tests pass in the worktree context.

## TDD Gate Compliance

Plan type is `execute` with `tdd="true"` per-task. Gate sequence verified:

1. `test(10-07): add failing tests for MetaspriteVisitorFrameSwitch (TDD RED)` — `216e7c08` ✓
2. `feat(10-07): implement generateMetaspriteFrameSwitch() in MetaspriteVisitor (TDD GREEN)` — `b43e8501` ✓
3. `test(10-07): add failing tests for ScriptOpVisitor.visitMoveMetasprite wiring (TDD RED)` — `0dd19da8` ✓
4. `feat(10-07): wire ScriptOpVisitor.visitMoveMetasprite to MetaspriteVisitor (TDD GREEN)` — `ad2de682` ✓

## Next Phase Readiness

- All three MetaspriteVisitor sub-areas complete — tile-data (10-05), descriptor (10-06), frame-switch (10-07)
- ScriptOpVisitor end-to-end chain wired: `moveMetasprite(ref)` DSL → `MoveMetasprite` op → `visitMoveMetasprite` → `generateMetaspriteFrameSwitch` → emitted C
- Plan 13 (port assembly) can now use `moveMetasprite(elephant)` in scene frame blocks; must declare `idx`/`rot`/`posX`/`posY` variables by convention

---
*Phase: 10-port-metasprites-gbdk-example-to-gbkt*
*Completed: 2026-05-18*
