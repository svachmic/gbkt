---
phase: 03-asset-pipeline-and-jvm-test-runner
plan: 04
subsystem: testing
tags: [kotlin, jvm, simulation, v2-ir, game-testing, scenario-tests]

# Dependency graph
requires:
  - phase: 03-asset-pipeline-and-jvm-test-runner
    plan: 03
    provides: SimulationContextV2 (advanceFrames/runUntil/tap/holdDpad/assertVar/enterScene), ScriptOpInterpreter
  - phase: 01-ir-foundation-and-dsl
    provides: PongV2.kt, BreakoutV2.kt, ExplorerV2.kt (v2 DSL game definitions), GameIR, ScriptOp sealed hierarchy
provides:
  - PongGameTest — 4 scenario tests verifying ball physics, scoring, and win condition
  - BreakoutGameTest — 3 scenario tests verifying paddle bounce, brick zone scoring, ball reset
  - ExplorerGameTest — 6 scenario tests verifying torch depletion, clamping, scene navigation
  - Full CI safety net: 13 new scenario tests + 57 pre-existing IR tests = 70 total, all passing
affects:
  - future-testing (pattern established for scenario-based game logic tests)
  - Phase 4+ (any new game feature can be tested with this pattern)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared GameIR fixture: build() called once per class in companion object — avoids per-test DSL rebuild cost"
    - "setVar() for forced initial conditions — avoids simulating many frames to reach test state"
    - "assertVar() + assertEquals() mixed — both work from kotlin.test"
    - "enableTracing() on at least one test per game — validates trace log is functional"
    - "buttonPressed() DSL emits CallExpr (no-op stub in interpreter) — test via variable comparison or enterScene() instead"

key-files:
  created:
    - gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongGameTest.kt
    - gbkt-examples/breakout/src/test/kotlin/io/github/gbkt/examples/breakout/BreakoutGameTest.kt
    - gbkt-examples/explorer/src/test/kotlin/io/github/gbkt/examples/explorer/ExplorerGameTest.kt
  modified: []

key-decisions:
  - "buttonPressed() DSL emits CallExpr — evaluates to 0 in ScriptOpInterpreter (hardware stub); Explorer pause-scene button test replaced with stepCount encounter trigger (variable comparison works)"
  - "Explorer pause scene tested via direct enterScene() call — verifies scene exists and torch does not deplete in pause"
  - "Shared companion object GameIR fixture — build() is cheap but called once per class to match plan guidance"
  - "Explorer combat (TriggerSystem no-op) not tested directly — test focuses on encounter trigger navigation via stepCount variable"

requirements-completed: [TEST-03]

# Metrics
duration: 3min
completed: 2026-02-18
---

# Phase 3 Plan 4: Example Game Scenario Tests Summary

**13 scenario-based game logic tests across Pong, Breakout, and Explorer using SimulationContextV2 — all passing in under 5 seconds total on JVM without GBDK**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-18T17:12:30Z
- **Completed:** 2026-02-18T17:16:07Z
- **Tasks:** 2 of 2
- **Files modified:** 3 (all new test files)

## Accomplishments

- PongGameTest: 4 scenarios — top-wall bounce (ballDy reverses to 1), bottom-wall bounce (ballDy reverses to -1), p1Score scoring with ball reset, win condition navigates to gameover
- BreakoutGameTest: 3 scenarios — paddle bounce (ballDy reverses to -1), brick zone scoring (score +10, bricksLeft -1, ballDy reverses), ball-below-paddle reset (lives -1, ball back to 128)
- ExplorerGameTest: 6 scenarios — torch depletion from 255 (5 frames = 250), torch depletion from set value (10 frames), left-clamping (x=5 → clamped to 8), right-clamping (x=160 → clamped to 152), stepCount encounter trigger (at 20 → combat_scene), pause scene entry
- All 13 new scenario tests pass alongside all 57 pre-existing IR structure tests (70 total, 0 failures)
- Total execution time: ~2 seconds across all three games (well under 5-second budget)

## Task Commits

Each task was committed atomically:

1. **Task 1: Pong and Breakout scenario tests** - `9558da8` (feat)
2. **Task 2: Explorer scenario tests** - `1eb23a6` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongGameTest.kt` — 4 scenario tests: wall bounce (top/bottom), p1 scoring with trace log verification, win condition navigation
- `gbkt-examples/breakout/src/test/kotlin/io/github/gbkt/examples/breakout/BreakoutGameTest.kt` — 3 scenario tests: paddle bounce with tracing, brick zone score/decrement, ball reset with life loss
- `gbkt-examples/explorer/src/test/kotlin/io/github/gbkt/examples/explorer/ExplorerGameTest.kt` — 6 scenario tests: torch depletion (x2), position clamping (x2), encounter trigger, pause scene entry

## Decisions Made

- **buttonPressed() → CallExpr (hardware stub):** The `buttonPressed("start")` DSL call compiles to `CallExpr("button_pressed", ...)` in the IR, which evaluates to 0 in ScriptOpInterpreter (hardware no-op stub). This means button-triggered navigation cannot be tested directly. Explorer scene navigation test was replaced with the stepCount encounter trigger (variable comparison works correctly in interpreter).
- **Shared GameIR fixture in companion object:** `build()` called once per test class, not per test method. This matches the plan guidance and keeps tests fast.
- **Explorer combat not tested:** TriggerSystem is a no-op stub per plan decision; combat scenarios were skipped in favor of movement, gauge, and navigation tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced button-navigation test with variable-based navigation test in Explorer**
- **Found during:** Task 2 (ExplorerGameTest — pressing START navigates to pause scene)
- **Issue:** `buttonPressed("start")` DSL emits `CallExpr` in IR, which is a hardware no-op stub in ScriptOpInterpreter (always returns 0). The test expected scene navigation on tap but the condition always evaluated false.
- **Fix:** Replaced the START-button navigation scenario with the stepCount-based encounter trigger (`stepCount >= 20 → navigate("combat_scene")`), which uses a variable comparison (evaluates correctly). Added a second test for pause scene entry via `enterScene()` directly.
- **Files modified:** `ExplorerGameTest.kt`
- **Rationale:** The plan explicitly states "if certain variables don't exist in ExplorerV2, choose scenarios that match what's actually implemented" — buttonPressed is a hardware stub, variable comparisons work.

---

**Total deviations:** 1 auto-fixed (1 bug — incorrect assumption about button input simulation)
**Impact on plan:** Test count unchanged (6 Explorer scenarios); substituted variable-based scenario for button-based scenario. No scope creep.

## Issues Encountered

- Pre-existing `gbkt-gradle-plugin:compileKotlin` failure (ProcessAssetsTask.kt unresolved references) — this is a pre-existing issue from plan 03-01, out of scope. Tests run successfully by excluding the gradle plugin task; the `--exclude-task` flag was required for CI-equivalent testing.

## Next Phase Readiness

- Phase 4 can use the same scenario-test pattern for any new DSL features
- buttonPressed/dpadHeld CallExpr evaluation could be enhanced in a future plan if button-driven navigation testing becomes important (would require implementing `CallExpr` evaluation in ScriptOpInterpreter for known hardware functions)
- Current test coverage is sufficient for game logic validation (physics, gauges, variable-driven navigation)

## Self-Check: PASSED

- PongGameTest.kt: FOUND
- BreakoutGameTest.kt: FOUND
- ExplorerGameTest.kt: FOUND
- 03-04-SUMMARY.md: FOUND (this file)
- Commit 9558da8 (Task 1): FOUND
- Commit 1eb23a6 (Task 2): FOUND
- Test results: 0 failures across all 70 tests
- Total execution time: 2 seconds (under 5-second budget)

---
*Phase: 03-asset-pipeline-and-jvm-test-runner*
*Completed: 2026-02-18*
