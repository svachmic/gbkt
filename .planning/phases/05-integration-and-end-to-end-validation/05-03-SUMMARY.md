---
phase: 05-integration-and-end-to-end-validation
plan: 03
subsystem: gbdk-backend-codegen
tags: [codegen, gbdk, breakout, pong, lcc, rom-build, C-codegen, joypad, extern, prototype]

# Dependency graph
requires:
  - phase: 05-integration-and-end-to-end-validation
    provides: v2 GameBuilder bridge (05-01), Pong buildRom (05-02)
provides:
  - Breakout buildRom end-to-end: DSL → IR → codegen → lcc → .gb
  - Pong buildRom regression verification
  - Fixed C codegen pipeline applicable to all v2 DSL games
affects:
  - 05-integration-and-end-to-end-validation
  - gbkt-examples:pong (regression benefit)
  - gbkt-examples:explorer (future)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CFunction.isPrototype=true emits function prototype (;) instead of definition body"
    - "CVarDecl.isExtern=true emits extern keyword in header declarations"
    - "game.h is generated with isHeader=true include guard wrapping"
    - "J_ constant passthrough in ExprVisitor.sanitizeVarName (no underscore prefix for GBDK joypad constants)"
    - "ScriptBuilder maps buttonPressed/dpadHeld args to GBDK J_UP/J_START etc. constants"
    - "Joypad state pattern: __joypad + __joypad_prev globals + update_joypad() per frame"

key-files:
  created: []
  modified:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilder.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt

key-decisions:
  - "cls() replaces CLS — GBDK-2020 removed the CLS macro; cls() is the function in gbdk/console.h"
  - "HIDE_SPRITES; and SHOW_SPRITES; need trailing semicolons — macros expand to register assignments (expressions), not statements"
  - "Variable declarations prefixed with _ in pipeline to match ExprVisitor.sanitizeVarName() convention"
  - "J_ constant passthrough in sanitizeVarName — GBDK joypad bitmask macros must not receive underscore prefix"
  - "ScriptBuilder.buttonToGbdkConstant() maps DSL names (start, a, up) to GBDK constants (J_START, J_A, J_UP)"
  - "Joypad helpers added to HOME bank (main.c) — button_pressed/held/dpad_held/dpad_pressed use __joypad state"
  - "update_joypad() called at top of game loop per frame — before scene frame dispatch"
  - "CFunction.isPrototype=true emits prototype declaration (;) — prevents duplicate definitions in game.h"
  - "CVarDecl.isExtern=true added — game.h uses extern declarations; main.c provides definitions"
  - "Scene function prototypes moved to game.h — both main.c and bank1.c see them via include"
  - "stdio.h required for printf — gbdk/console.h does NOT declare printf; it is in stdio.h"

patterns-established:
  - "Header-safe codegen: isPrototype/isExtern flags enable single-source-of-truth declarations"
  - "GBDK joypad pattern: poll once per frame, track edge with __joypad_prev"

requirements-completed: [INTG-02]

# Metrics
duration: 13min
completed: 2026-02-19
---

# Phase 5 Plan 03: Breakout End-to-End ROM Build Summary

**Breakout ROM builds end-to-end: DSL → IR → analysis → codegen → lcc → .gb — 6 codegen bugs fixed, 2 ROMs now compile successfully**

## Performance

- **Duration:** 13 min
- **Started:** 2026-02-19T17:33:39Z
- **Completed:** 2026-02-19T17:46:58Z
- **Tasks:** 1
- **Files modified:** 6

## Accomplishments

- `./gradlew :gbkt-examples:breakout:buildRom` succeeds — produces `breakout.gb` (32 KB)
- `./gradlew :gbkt-examples:pong:buildRom` continues to succeed — no regressions
- All `gbkt-backend-gbdk:test` and `gbkt-core:test` pass (24 + 25 tests)
- Fixed 6 codegen bugs discovered during Breakout lcc compilation

## Task Commits

1. **Task 1: Fix codegen pipeline for Breakout ROM compilation** - `3af8007` (feat)

## Files Created/Modified

- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilder.kt` — fixed CLS/HIDE_SPRITES/SHOW_SPRITES, added buttonToGbdkConstant() mapping
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt` — J_ constant passthrough in sanitizeVarName
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — variable prefix fix, joypad helpers, extern/prototype headers, stdio.h include
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt` — isPrototype field
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt` — isExtern field on CVarDecl
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/CEmitter.kt` — prototype and extern emission support

## Decisions Made

- `cls()` replaces `CLS` — GBDK-2020 removed the CLS macro; `cls()` is the function in `<gbdk/console.h>`
- `HIDE_SPRITES;` / `SHOW_SPRITES;` need trailing semicolons — macros expand to register assignment expressions, not statements
- Variable declarations now prefixed with `_` in GBDKPipelineV2 to match `ExprVisitor.sanitizeVarName()` convention (both must agree on naming)
- `J_` constant passthrough in `sanitizeVarName` — GBDK joypad bitmask macros (J_START, J_UP etc.) must not receive underscore prefix
- `ScriptBuilder.buttonToGbdkConstant()` maps DSL button names to GBDK constants
- Joypad helpers (`button_pressed`, `button_held`, `dpad_held`, `dpad_pressed`) generated in HOME bank — HOME functions are always callable from banked code
- `update_joypad()` called at top of main game loop, before scene frame dispatch — consistent per-frame joypad state
- `CFunction.isPrototype = true` emits `type name(params);` without body — prevents duplicate definition errors when header is included by multiple TUs
- `CVarDecl.isExtern = true` emits `extern` storage class — game.h has extern declarations; main.c provides definitions
- Scene function prototypes moved to game.h — enables bank1.c to see them via include
- `<stdio.h>` required for `printf` — `<gbdk/console.h>` only declares `gotoxy`, `cls`, and `posx/y`; `printf` lives in `<stdio.h>`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed 6 C codegen bugs causing lcc compilation failure**

These bugs existed in the pipeline but only manifested when running the actual GBDK lcc compiler (not detectable at Kotlin level):

1. **CLS undefined** — `ScriptBuilder.clear()` emitted `RawOp("CLS")` but CLS is not a GBDK-2020 macro. Fixed to `cls()`.
2. **HIDE_SPRITES/SHOW_SPRITES missing semicolons** — macros expand to expressions, need `;` to be valid C statements. Fixed in ScriptBuilder.
3. **Variable naming mismatch** — pipeline declared `score` without underscore in main.c but `ExprVisitor.sanitizeVarName()` accesses it as `_score`. Fixed by adding `_` prefix in pipeline declarations.
4. **GBDK joypad constants getting underscore prefix** — `button_pressed(J_START)` was becoming `button_pressed(_J_START)`. Fixed by J_ passthrough in sanitizeVarName.
5. **Missing <stdio.h>** — `printf` was implicitly declared causing "too many parameters" error. Fixed by adding stdio.h to includes.
6. **Multiple definition errors** — game.h variables not extern (both main.c and bank1.c defined them), and scene function stubs were full definitions (not prototypes). Fixed with isExtern and isPrototype support.

- **Found during:** Task 1 — iterative lcc compilation
- **Fix:** 6 focused fixes across ScriptBuilder, ExprVisitor, GBDKPipelineV2, and C AST types
- **Files modified:** See above
- **Commit:** 3af8007

## Issues Encountered

None beyond the auto-fixed codegen bugs.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Breakout ROM builds end-to-end (INTG-02 resolved)
- Pong ROM continues to build (no regression)
- Explorer ROM (05-04) will benefit from all these fixes — same codegen pipeline
- All codegen bugs are now fixed at the source (Kotlin codegen), not in generated C
- Joypad input pattern is established for all v2 games

---
*Phase: 05-integration-and-end-to-end-validation*
*Completed: 2026-02-19*

## Self-Check: PASSED

- FOUND: ScriptBuilder.kt
- FOUND: GBDKPipelineV2.kt
- FOUND: ExprVisitor.kt
- FOUND: CFunction.kt (isPrototype)
- FOUND: CStatement.kt (isExtern)
- FOUND: CEmitter.kt (prototype/extern support)
- FOUND: breakout.gb (32768 bytes)
- FOUND: pong.gb (32768 bytes)
- FOUND: 05-03-SUMMARY.md
- FOUND commit: 3af8007 (feat(05-03): fix codegen pipeline)
