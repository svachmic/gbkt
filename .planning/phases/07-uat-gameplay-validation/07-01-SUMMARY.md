---
phase: 07-uat-gameplay-validation
plan: 01
subsystem: testing
tags: [emulator, agent-dx, input-script, screenshot, coffee-gb, game-boy, junit5]

# Dependency graph
requires:
  - phase: gbkt-emulator
    provides: GbEmulator interface, CoffeeGbEmulator, EventBus, ButtonPressEvent/ButtonReleaseEvent

provides:
  - InputScript DSL: type-safe game input sequence builder (press/hold/release/wait)
  - InputScriptPlayer: executes InputScript against paused GbEmulator via Coffee-GB EventBus
  - ScreenshotCapture: 160x144 PNG capture with JSON metadata sidecar

affects:
  - 07-02-uat-gameplay-validation (VariableInspector uses ScreenshotCapture for snapshots)
  - future agent-driven testing plans

# Tech tracking
tech-stack:
  added: []
  patterns:
    - TDD red-green cycle for agent DX components
    - Agent primitives accept EventBus as constructor parameter (not reaching into CoffeeGbEmulator internals)
    - InputScript is an immutable data holder; InputScriptBuilder is the mutable accumulator

key-files:
  created:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/InputScript.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/InputScriptPlayer.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/InputScriptTest.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/InputScriptPlayerTest.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt
  modified: []

key-decisions:
  - "InputScriptPlayer takes EventBus as constructor parameter — avoids reaching into CoffeeGbEmulator internals, allows stub injection in tests"
  - "Button enum in agent package maps to Coffee-GB joypad.Button via toCoffeeGb() extension — clean separation"
  - "ScreenshotCapture uses org.json.JSONObject (already in deps) instead of kotlinx.serialization to keep dependency footprint small"
  - "JSON sidecar is human-readable (toString(2) pretty-print) for debugging use"

patterns-established:
  - "Agent primitives (Player, Capture) accept interfaces/dependencies via constructor — enables pure unit tests with stubs"
  - "Frame buffer validation: require(size == 23040) with descriptive message at entry point"
  - "EventBus stub pattern: anonymous object implementing EventBus interface to record posted events"

requirements-completed:
  - UAT-01

# Metrics
duration: 25min
completed: 2026-03-13
---

# Phase 07 Plan 01: InputScript DSL and ScreenshotCapture Summary

**Type-safe agent input scripting (press/hold/release/wait DSL) and 160x144 PNG screenshot capture with JSON metadata sidecar for automated Game Boy scenario testing**

## Performance

- **Duration:** 25 min
- **Started:** 2026-03-13T11:30:33Z
- **Completed:** 2026-03-13T11:55:00Z
- **Tasks:** 2 (TDD: 4 commits — 2 test + 2 impl)
- **Files created:** 6

## Accomplishments

- InputScript DSL: `inputScript { press(Button.A); hold(Button.LEFT); wait(30) }` builds typed step lists
- InputScriptPlayer dispatches Coffee-GB `ButtonPressEvent`/`ButtonReleaseEvent` into EventBus with correct frame timing
- ScreenshotCapture.capture() writes 160x144 PNG and JSON sidecar (frameNumber, label, capturedAt, variables)
- 25 unit tests green across 3 test classes (8 InputScript, 8 InputScriptPlayer, 9 ScreenshotCapture)

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): InputScript DSL and InputScriptPlayer tests** - `104fd86` (test)
2. **Task 1 (GREEN): InputScript DSL and InputScriptPlayer impl** - already committed as part of plan 07-02 auto-fix (e98e4ae), tests verified green
3. **Task 2 (RED): ScreenshotCapture tests** - `e78e43f` (test)
4. **Task 2 (GREEN): ScreenshotCapture implementation** - `898e021` (feat)

_Note: TDD tasks have test → feat commits per task_

## Files Created/Modified

- `gbkt-emulator/src/main/kotlin/.../agent/InputScript.kt` - Button enum, InputStep sealed interface, InputScript holder, InputScriptBuilder DSL
- `gbkt-emulator/src/main/kotlin/.../agent/InputScriptPlayer.kt` - Executes InputScript steps via EventBus.post() + emulator.stepFrame()
- `gbkt-emulator/src/main/kotlin/.../agent/ScreenshotCapture.kt` - PNG capture via BufferedImage/ImageIO + JSON sidecar via org.json.JSONObject
- `gbkt-emulator/src/test/.../agent/InputScriptTest.kt` - 8 DSL unit tests (no emulator needed)
- `gbkt-emulator/src/test/.../agent/InputScriptPlayerTest.kt` - 8 player tests using stub EventBus and GbEmulator
- `gbkt-emulator/src/test/.../agent/ScreenshotCaptureTest.kt` - 9 capture tests using temp directory

## Decisions Made

- **EventBus injection via constructor**: InputScriptPlayer takes `EventBus` as constructor param rather than reaching into `CoffeeGbEmulator.eventBus` (which is `internal`). This keeps the player testable with pure stubs.
- **Coffee-GB `post()` not `dispatch()`**: The plan spec mentioned `dispatch()` but Coffee-GB EventBus only has `post()`. Used existing `InputHandler` pattern as authoritative reference.
- **org.json for JSON sidecar**: JSONObject from `org.json` (already a dependency) instead of adding kotlinx.serialization. Keeps sidecar format simple.
- **`toString(2)` pretty-print**: JSON written with 2-space indent for human readability during debugging.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] InputScript.kt and InputScriptPlayer.kt already existed as plan 07-02 auto-fix stubs**
- **Found during:** Task 1 (InputScript DSL and InputScriptPlayer)
- **Issue:** Files were pre-committed in `e98e4ae` as part of plan 07-02 execution (Rule 3 auto-fix to unblock VariableInspector test compilation)
- **Fix:** Tests written against pre-existing production code; all 16 tests passed immediately
- **Files modified:** None additional
- **Verification:** 8 + 8 tests green
- **Committed in:** Stubs in e98e4ae (plan 07-02 commit)

**2. [Rule 3 - Blocking] VisualDiff.kt already existed, SavestateManager.kt already existed**
- **Found during:** Task 2 (ScreenshotCapture tests), when running `compileTestKotlin`
- **Issue:** `VisualDiffTest.kt` and `SavestateManagerTest.kt` were pre-committed test stubs referencing `VisualDiff` and `SavestateManager` — these production classes were also already committed in plan 07-02 (`7b1cca2`)
- **Fix:** Compilation succeeded without any additional action; no stubs needed
- **Verification:** All agent tests green (54 total: 8+8+9+8+8+13 across all test classes)

---

**Total deviations:** 2 (both Rule 3 - blocking), both pre-resolved by prior plan 07-02 execution
**Impact on plan:** No scope creep. Plan 07-01 goals fully met. Prior auto-fix work from 07-02 actually accelerated this plan.

## Issues Encountered

- Plan spec mentioned `eventBus.dispatch()` but Coffee-GB EventBus uses `post()`. Resolved by checking `InputHandler.kt` as the authoritative existing pattern.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- InputScript, InputScriptPlayer, and ScreenshotCapture are production-ready
- All 3 components can be combined for scenario testing: `pause emulator → play script → capture screenshot → verify state`
- Plan 07-02 components (VariableInspector, SavestateManager, VisualDiff) are also already committed and tested

## Self-Check: PASSED

- InputScript.kt: FOUND
- InputScriptPlayer.kt: FOUND
- ScreenshotCapture.kt: FOUND
- InputScriptTest.kt: FOUND
- InputScriptPlayerTest.kt: FOUND
- ScreenshotCaptureTest.kt: FOUND
- Commit 104fd86: FOUND
- Commit e78e43f: FOUND
- Commit 898e021: FOUND

---
*Phase: 07-uat-gameplay-validation*
*Completed: 2026-03-13*
