---
phase: 07-uat-gameplay-validation
plan: 03
subsystem: testing
tags: [emulator, agent-dx, orchestrator, lifecycle, coffee-gb, gbc-mode, junit5]

# Dependency graph
requires:
  - phase: 07-uat-gameplay-validation plan 01
    provides: InputScript DSL, InputScriptPlayer, ScreenshotCapture
  - phase: 07-uat-gameplay-validation plan 02
    provides: VariableInspector, SavestateManager, VisualDiff

provides:
  - AgentDebugSession: unified orchestrator over all agent debug primitives
  - AgentSessionConfig: typed configuration for agent sessions (ROM, sym, GBC mode)

affects:
  - 07-04+ (future agent-driven UAT playtest scripts use AgentDebugSession as entry point)
  - Gradle emulatorTest task (can use AgentDebugSession for automated ROM verification)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Orchestrator pattern: AgentDebugSession delegates to individual primitives (InputScriptPlayer, ScreenshotCapture, VariableInspector, SavestateManager, VisualDiff)
    - Test injection via constructor parameter: stubEmulatorFactory: (() -> GbEmulator)? = null allows pure unit tests without real ROM
    - NoOpEventBus inner object: enables InputScriptPlayer to be wired even with stub GbEmulator in tests

key-files:
  created:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentDebugSession.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentDebugSessionTest.kt
  modified:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/EmulatorConfig.kt (added gbcMode field)
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt (wire gbcMode to GameboyType)

key-decisions:
  - "stubEmulatorFactory constructor param (not a test subclass) provides clean test injection without exposing internal state or creating a test-only API surface"
  - "gbcMode added to EmulatorConfig and wired to GameboyType.CGB/DMG in CoffeeGbEmulator.start() — clean separation, AgentSessionConfig.toEmulatorConfig() propagates it transparently"
  - "NoOpEventBus inner object used for stub emulators — avoids failing when CoffeeGbEmulator.eventBus cast cannot succeed in tests"
  - "watchVariables empty = include all sym vars; non-empty = filter to listed names — zero-config default with precision opt-in"

requirements-completed:
  - UAT-01

# Metrics
duration: 4min
completed: 2026-03-13
---

# Phase 07 Plan 03: AgentDebugSession Orchestrator Summary

**Unified agent session API (AgentDebugSession) orchestrating all Plan 01/02 agent debug primitives behind a single entry point with lifecycle management, GBC mode support, and stub-injectable constructor for unit testing**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-13T11:43:11Z
- **Completed:** 2026-03-13T11:47:00Z
- **Tasks:** 2 (TDD: 3 commits — 1 config feat + 1 test RED + 1 impl GREEN)
- **Files created:** 3
- **Files modified:** 2

## Accomplishments

- AgentSessionConfig data class with romFile, symFile, sourceMapsDir, screenshotDir (defaults adjacent to ROM), logFile, watchVariables, gbcMode fields
- AgentSessionConfig.toEmulatorConfig() always produces headless=true EmulatorConfig with gbcMode propagated
- AgentDebugSession provides full lifecycle: start() wires emulator+inspector+player, stop()/close() cleans up
- All methods throw ISE with clear message if called before start()
- runFrames(n) increments frameCount, captureScreenshot() reads watchVariables-filtered inspector snapshot
- executeInputScript() delegates to InputScriptPlayer (wired to CoffeeGbEmulator.eventBus after start)
- readVariable/readAllVariables, saveState/loadState, diffScreenshots, getDebugLog all delegate to respective primitives
- GBC mode: gbcMode added to EmulatorConfig, CoffeeGbEmulator selects GameboyType.CGB vs DMG
- 15 unit tests green covering: ISE guard, frameCount, readVariable/readAll, captureScreenshot, watchVariables filter, Closeable.close(), AgentSessionConfig validation

## Task Commits

Each task was committed atomically using TDD (RED then GREEN):

1. **Task 1: AgentSessionConfig + gbcMode** - `11d5e72` (feat) — config class + EmulatorConfig/CoffeeGbEmulator wiring
2. **Task 2 (RED): AgentDebugSession tests** - `a1eea3f` (test) — 15 failing tests
3. **Task 2 (GREEN): AgentDebugSession impl** - `abd0540` (feat) — implementation, all tests pass

## Files Created/Modified

- `gbkt-emulator/.../agent/AgentSessionConfig.kt` — Config data class with toEmulatorConfig() and romFile.exists() validation
- `gbkt-emulator/.../agent/AgentDebugSession.kt` — Orchestrator implementing Closeable; NoOpEventBus inner object; stubEmulatorFactory injection
- `gbkt-emulator/.../agent/AgentDebugSessionTest.kt` — 15 unit tests using stub GbEmulator and fake ROM files
- `gbkt-emulator/.../EmulatorConfig.kt` — Added gbcMode: Boolean = false field
- `gbkt-emulator/.../CoffeeGbEmulator.kt` — Wired gbcMode to GameboyType.CGB/DMG selection in start()

## Decisions Made

- **stubEmulatorFactory injection**: `AgentDebugSession(config, stubEmulatorFactory = { stub })` enables pure unit tests that don't need a real GB ROM. The factory parameter is nullable (null = production path via CoffeeGbEmulator). This keeps the public API clean while making the session fully testable.
- **gbcMode in EmulatorConfig**: Rather than having AgentDebugSession reach into CoffeeGbEmulator internals to set GBC mode post-construction, the mode is propagated through toEmulatorConfig() → EmulatorConfig.gbcMode → CoffeeGbEmulator.start(). Clean layered propagation.
- **NoOpEventBus**: When stubEmulatorFactory provides a non-CoffeeGbEmulator, the `emu is CoffeeGbEmulator` cast fails, so InputScriptPlayer is wired to a NoOpEventBus. This allows full session lifecycle tests without a real emulator, while production path always uses the real EventBusImpl from CoffeeGbEmulator.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Functionality] Added gbcMode to EmulatorConfig and CoffeeGbEmulator**
- **Found during:** Task 1 (AgentSessionConfig creation)
- **Issue:** Plan specified AgentSessionConfig.gbcMode and toEmulatorConfig() mapping, but EmulatorConfig had no gbcMode field. Without it, gbcMode would be silently discarded and CoffeeGbEmulator would always use DMG mode — making GBC ROM support advertised but non-functional.
- **Fix:** Added `val gbcMode: Boolean = false` to EmulatorConfig; updated CoffeeGbEmulator.start() to select `GameboyType.CGB` when `config.gbcMode = true`.
- **Files modified:** EmulatorConfig.kt, CoffeeGbEmulator.kt
- **Commit:** 11d5e72

---

**Total deviations:** 1 (Rule 2 - missing critical functionality), auto-fixed
**Impact on plan:** GBC mode now actually works end-to-end rather than being a no-op field. No scope creep.

## Issues Encountered

None beyond the deviation documented above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- AgentDebugSession is the complete unified entry point for agent-driven ROM testing
- All 7 agent primitives are production-ready and wired: InputScript, InputScriptPlayer, ScreenshotCapture, VariableInspector, SavestateManager, VisualDiff, AgentDebugSession
- Ready for Plan 07-04+ (actual game scenario scripts that use AgentDebugSession)
- GBC ROM support is functional via AgentSessionConfig.gbcMode = true

## Self-Check: PASSED

Files verified present:
- FOUND: gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt
- FOUND: gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentDebugSession.kt
- FOUND: gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentDebugSessionTest.kt

Commits verified:
- FOUND: 11d5e72 (feat: AgentSessionConfig + gbcMode)
- FOUND: a1eea3f (test: AgentDebugSession RED)
- FOUND: abd0540 (feat: AgentDebugSession GREEN)

---
*Phase: 07-uat-gameplay-validation*
*Completed: 2026-03-13*
