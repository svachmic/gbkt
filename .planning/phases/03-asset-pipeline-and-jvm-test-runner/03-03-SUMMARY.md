---
phase: 03-asset-pipeline-and-jvm-test-runner
plan: 03
subsystem: testing
tags: [kotlin, jvm, simulation, v2-ir, scriptop, tdd, game-testing]

# Dependency graph
requires:
  - phase: 01-ir-foundation-and-dsl
    provides: ScriptOp sealed hierarchy (24 subtypes), Expr sealed hierarchy (9 subtypes), GameIR, SceneIR, ActorIR
  - phase: 02-structured-codegen-and-migration-cut
    provides: GBDKPipelineV2, v2 IR foundation proven end-to-end
provides:
  - ScriptOpInterpreter — exhaustive when-based JVM execution engine for all 24 ScriptOp subtypes
  - SimulationContextV2 — public test API (advanceFrames, runUntil, tap, holdDpad, assertVar, enableTracing)
  - GameBoyButton and DpadDirection enums with GBDK bitmask constants
  - Bounding box collision detection on JVM for v2 actors
  - Frame trace logging for test debugging
affects:
  - 03-04 (example game tests will use SimulationContextV2 as their test harness)
  - future-testing (any v2 game test inherits this infrastructure)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TDD: RED tests written before implementation, then GREEN to pass"
    - "Exhaustive when: sealed interface dispatch with NO else branch — compiler enforces coverage"
    - "No-op stub pattern: hardware-dependent ops execute without error but have no JVM effect"
    - "__joypad/__joypad_prev variables synced in executeFrame() for script-readable input"
    - "kotlin.test.* (not JUnit 5) for test assertions — project standard"

key-files:
  created:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/test/ScriptOpInterpreterTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/test/SimulationContextV2Test.kt
  modified:
    - gbkt-core/build.gradle.kts

key-decisions:
  - "kotlin.test.* used instead of JUnit 5 — project already uses kotlin.test universally; JUnit 6.0.1 BOM in build.gradle.kts was an invalid pre-written dependency that blocked compilation"
  - "__joypad and __joypad_prev variables synced each frame — allows scripts to read input via VarRef without interpreter knowing about game-specific variable names"
  - "evaluateBinaryExpr uses else -> for non-short-circuit BinaryOp enum cases — acceptable because BinaryOp is an enum (not sealed), else handles the remaining arithmetic/comparison ops after LOGICAL_AND/LOGICAL_OR are handled"
  - "ScriptOpInterpreter.interpreter field on SimulationContextV2 is internal (not private) — test files in same package need access for joypad state verification"
  - "MathFunction.RAND returns 0 deterministically — prevents non-deterministic test results"
  - "WhileOp/ForOp capped at 10000 iterations — prevents infinite loops in test execution"

patterns-established:
  - "Exhaustive ScriptOp dispatch: when(op) with exactly 24 branches and NO else — adding a new ScriptOp subtype without updating ScriptOpInterpreter is a compile error"
  - "Exhaustive Expr dispatch: when(expr) with exactly 9 branches and NO else — same guarantee for Expr subtypes"
  - "Hardware no-op stubs: single-line { /* no-op stub: reason */ } comments explain why each stub exists"
  - "SimulationContextV2 wraps interpreter: thin API layer, delegates all state to ScriptOpInterpreter"
  - "assertVar throws AssertionError with format: assertVar failed: 'name' expected=X actual=Y"
  - "runUntil throws IllegalStateException (not AssertionError) on timeout — distinguishes test setup errors from assertion failures"

requirements-completed: [TEST-01, TEST-02]

# Metrics
duration: 13min
completed: 2026-02-18
---

# Phase 3 Plan 3: JVM ScriptOp Interpreter and SimulationContextV2 Summary

**ScriptOpInterpreter executing all 24 v2 ScriptOps via exhaustive when-matching, wrapped by SimulationContextV2 with advanceFrames/runUntil/tap/holdDpad/assertVar/enableTracing API**

## Performance

- **Duration:** 13 min
- **Started:** 2026-02-18T16:55:14Z
- **Completed:** 2026-02-18T17:08:30Z
- **Tasks:** 2 of 2
- **Files modified:** 5 (2 impl, 2 tests, 1 build config)

## Accomplishments

- Built ScriptOpInterpreter with exhaustive when dispatch over all 24 ScriptOp sealed subtypes and all 9 Expr sealed subtypes — zero else branches on sealed interfaces
- Hardware-dependent ops (PlaySound, FadeOp, ShowDialog, CameraOp, etc.) are no-op stubs that execute without error
- Full simulation of core logic: Assign (all 9 AssignOps), IfOp, WhileOp, ForOp, SetPosition, MoveBy, NavigateTo, MathOp (ABS/MIN/MAX/CLAMP)
- Bounding box collision detection from GameIR actor hitbox definitions (defaults to 8x8)
- Built SimulationContextV2 public test API: 7 frame-control methods, 6 state inspection methods, 4 input methods, 2 tracing methods
- GameBoyButton (A/B/SELECT/START) and DpadDirection (UP/DOWN/LEFT/RIGHT) enums with correct GBDK bitmasks
- 71 ScriptOpInterpreterTest + 28 SimulationContextV2Test = 99 new TDD tests, all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: ScriptOpInterpreter with exhaustive ScriptOp execution** - `ac9123b` (feat)
2. **Task 2: SimulationContextV2 public test API** - `c16420f` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt` — JVM execution engine with exhaustive ScriptOp/Expr when dispatch, actor position tracking, collision detection, frame trace log
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt` — Public test API: advanceFrames, runUntil, tap, holdDpad, press, release, getVar, setVar, assertVar, enterScene, enableTracing, getTraceLog; GameBoyButton and DpadDirection enums
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/test/ScriptOpInterpreterTest.kt` — 71 TDD tests covering all ScriptOp execution paths, Expr evaluation, collision, tracing
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/test/SimulationContextV2Test.kt` — 28 TDD tests covering complete public API, input simulation, runUntil timeout
- `gbkt-core/build.gradle.kts` — Removed invalid JUnit 6.0.1 BOM dependency that was blocking test compilation

## Decisions Made

- **kotlin.test.* instead of JUnit 5:** The pre-written `build.gradle.kts` had `org.junit:junit-bom:6.0.1` (alpha/non-existent). Removed it; the project uses `kotlin.test.*` universally (confirmed from all existing test files).
- **`__joypad`/`__joypad_prev` variable sync:** `executeFrame()` syncs joypad to named variables each frame so game scripts can read input via `VarRef("__joypad")` without requiring a special input expression type.
- **`interpreter` field is `internal`:** SimulationContextV2Test needs to inspect `interpreter.joypad` directly in tests. Same package makes `internal` sufficient.
- **`MathFunction.RAND` returns 0:** Deterministic behavior is essential for reproducible unit tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed invalid JUnit 6.0.1 dependency from build.gradle.kts**
- **Found during:** Task 1 (RED phase — test compilation attempt)
- **Issue:** `build.gradle.kts` had `testImplementation(platform("org.junit:junit-bom:6.0.1"))` pre-written (JUnit 6.0.1 does not exist as a stable release). This pulled in non-existent dependencies, causing ALL test compilation to fail including pre-existing tests.
- **Fix:** Removed the three JUnit 6.0.1 lines from `build.gradle.kts`. Tests use `kotlin.test.*` which is already on the classpath.
- **Files modified:** `gbkt-core/build.gradle.kts`
- **Verification:** `./gradlew :gbkt-core:compileTestKotlin` succeeds; 99 new tests pass alongside all 1500+ pre-existing tests
- **Committed in:** `ac9123b` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking issue)
**Impact on plan:** Required for test compilation to function. No scope creep — single line group removed.

## Issues Encountered

- Pre-written test files for Plan 03-01 (AssetManifestTest, LdtkParserTest) appeared to fail compilation due to missing source files — but this was actually caused by the invalid JUnit 6.0.1 dependency pulling in broken artifacts. Once the JUnit lines were removed, all pre-existing tests compiled and ran correctly (their source implementations already exist).

## Next Phase Readiness

- Plan 03-04 can immediately use `SimulationContextV2` to test PongV2, BreakoutV2, and ExplorerV2 game logic
- ScriptOpInterpreter handles all ops used by example games; any missing ops will fail silently as no-op stubs
- Tracing is available for debugging test failures in Plan 03-04
- Collision detection works for bounding box overlap — adequate for Pong/Breakout ball-paddle detection

## Self-Check: PASSED

- ScriptOpInterpreter.kt: FOUND
- SimulationContextV2.kt: FOUND
- ScriptOpInterpreterTest.kt: FOUND
- SimulationContextV2Test.kt: FOUND
- 03-03-SUMMARY.md: FOUND
- Commit ac9123b (Task 1): FOUND
- Commit c16420f (Task 2): FOUND
- Test results: 0 failures in ScriptOpInterpreterTest, 0 failures in SimulationContextV2Test
- No else branch in sealed dispatch: 3 else branches found — all in non-sealed contexts (enum dispatch, boolean conditions)

---
*Phase: 03-asset-pipeline-and-jvm-test-runner*
*Completed: 2026-02-18*
