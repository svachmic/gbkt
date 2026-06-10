---
phase: 07-uat-gameplay-validation
plan: 02
subsystem: testing
tags: [emulator, agent, debugging, sym-file, savestate, visual-diff, png, memory-access]

# Dependency graph
requires:
  - phase: 06.12-embedded-emulator
    provides: GbEmulator interface, MemoryAccess, CoffeeGbEmulator, coffee-gb event bus

provides:
  - VariableInspector: .sym-backed DSL variable name to memory address resolution and read
  - SavestateManager: binary GBST savestate format for WRAM+OAM+HRAM checkpoint/restore
  - VisualDiff: pixel-level PNG screenshot comparison with diff image output

affects: [07-03, 07-04, 07-05, 07-06, agent-uat-testing, screenshot-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - .sym file parsing: DEF _name bank:ADDR format, strip _ prefix, require colon separator
    - binary savestate format: 4-byte magic + raw memory regions via DataOutputStream/DataInputStream
    - pixel diff: ImageIO.read + per-pixel RGB comparison + red overlay diff image

key-files:
  created:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VariableInspector.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/SavestateManager.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VisualDiff.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VariableInspectorTest.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/SavestateManagerTest.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VisualDiffTest.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/InputScript.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/InputScriptPlayer.kt
  modified: []

key-decisions:
  - "VariableInspector requires colon in bank:addr format to skip bare addresses (e.g. C102 without 00: prefix is skipped)"
  - "SavestateManager uses require() not check() for pause guard — IllegalArgumentException rather than IllegalStateException"
  - "VisualDiff.compare returns null diffImage on match even if diffOutputDir provided — no unnecessary file writes"
  - "Rule 3 auto-fix: InputScript.kt and InputScriptPlayer.kt created to unblock compilation of pre-existing plan 07-01 test stubs"

patterns-established:
  - "Agent primitives pattern: each agent tool is an object/class with minimal surface, takes MemoryAccess or GbEmulator, no Swing dependencies"
  - "Savestate file format: GBST magic prefix enables reliable corruption detection at load time"
  - "VisualDiff diff image naming: {actual_name}_diff.png co-located in diffOutputDir for easy association"

requirements-completed: [UAT-01]

# Metrics
duration: 6min
completed: 2026-03-13
---

# Phase 07 Plan 02: VariableInspector, SavestateManager, VisualDiff Summary

**Agent DX debugging toolkit: .sym-backed variable inspection, GBST binary savestate checkpoint/restore (WRAM+OAM+HRAM), and pixel-level PNG screenshot comparison with red-pixel diff images**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-13T11:33:48Z
- **Completed:** 2026-03-13T11:39:58Z
- **Tasks:** 2
- **Files modified:** 8 (6 plan files + 2 Rule 3 auto-fix)

## Accomplishments
- VariableInspector reads named DSL variables (score, lives, ballDx) from emulator memory by resolving .sym file symbols to addresses — supports readNamed, readNamedInt16, readAddress, readAll, listVariables with type inference
- SavestateManager snapshots WRAM (8192 bytes) + OAM (160 bytes) + HRAM (127 bytes) to 8483-byte GBST binary file with round-trip restore and magic validation
- VisualDiff compares 160x144 PNG screenshots pixel-by-pixel with configurable tolerance (0.0 = pixel-perfect, 0.05 = 5%) and generates red-pixel diff images on mismatch

## Task Commits

Each task was committed atomically using TDD (RED then GREEN):

1. **Task 1: VariableInspector (RED)** - `d0a5bfa` (test)
2. **Task 1: VariableInspector (GREEN)** - `e98e4ae` (feat) — includes Rule 3 auto-fix
3. **Task 2: SavestateManager + VisualDiff (RED)** - `3aa897b` (test)
4. **Task 2: SavestateManager + VisualDiff (GREEN)** - `7b1cca2` (feat)

_Note: TDD tasks have separate RED (test) and GREEN (implementation) commits_

## Files Created/Modified
- `gbkt-emulator/.../agent/VariableInspector.kt` — .sym-backed DSL variable name to address resolver with readNamed/readAll/listVariables API
- `gbkt-emulator/.../agent/SavestateManager.kt` — binary GBST savestate with WRAM+OAM+HRAM regions and pause guard
- `gbkt-emulator/.../agent/VisualDiff.kt` — pixel-level PNG comparison, DiffResult, tolerance threshold, red diff image generation
- `gbkt-emulator/.../agent/InputScript.kt` — Rule 3 auto-fix: Button enum and InputStep DSL (plan 07-01 dependency)
- `gbkt-emulator/.../agent/InputScriptPlayer.kt` — Rule 3 auto-fix: EventBus dispatch player (plan 07-01 dependency)
- `gbkt-emulator/.../agent/VariableInspectorTest.kt` — 13 tests covering parsing, reads, type inference, null guards
- `gbkt-emulator/.../agent/SavestateManagerTest.kt` — 7 tests covering file size, magic, WRAM/OAM/HRAM round-trip, pause guards
- `gbkt-emulator/.../agent/VisualDiffTest.kt` — 8 tests covering match/mismatch, diff image generation, tolerance boundaries, size mismatch

## Decisions Made
- VariableInspector requires colon in bank:addr format (e.g. `00:C100`) — bare addresses like `C102` are skipped. This matches the SDCC .noi/.sym format specification and prevents accidentally treating non-address values as valid entries.
- SavestateManager uses `require()` (IllegalArgumentException) for pause guards, consistent with the plan's specification.
- VisualDiff returns `null` diffImage when images match — no empty file writes on success path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created InputScript.kt and InputScriptPlayer.kt to unblock test compilation**
- **Found during:** Task 1 (VariableInspector RED phase)
- **Issue:** Pre-existing test files `InputScriptTest.kt` and `InputScriptPlayerTest.kt` (committed as part of plan 07-01 planning) referenced `Button`, `InputStep`, `InputScript`, `inputScript`, and `InputScriptPlayer` production types that didn't exist. These blocked compilation of the entire `agent` test package, preventing any tests from running.
- **Fix:** Created `InputScript.kt` (Button enum, InputStep sealed interface, InputScript class, inputScript DSL builder) and `InputScriptPlayer.kt` (EventBus-based step executor with Coffee-GB button mapping). These are the complete plan 07-01 artifacts.
- **Files modified:** `InputScript.kt`, `InputScriptPlayer.kt`
- **Verification:** All 54 agent tests compile and pass including InputScriptTest and InputScriptPlayerTest
- **Committed in:** e98e4ae (feat(07-02) commit)

**2. [Rule 1 - Bug] Fixed VariableInspector sym parsing to require colon separator**
- **Found during:** Task 1 (VariableInspector GREEN phase — first test run)
- **Issue:** Test `loadSymbols parses DEF lines and strips underscore prefix` expected 3 symbols but got 4. Line `DEF _ignored_no_underscore C102` was being parsed because `"C102".substringAfter(":")` returns `"C102"` (Kotlin behavior: returns full string when delimiter not present), which then parsed as valid hex. Test expected this malformed line to be skipped.
- **Fix:** Added `if (!addrStr.contains(":")) return@forEach` before parsing to require the `bank:addr` colon format.
- **Files modified:** `VariableInspector.kt`
- **Verification:** 13/13 VariableInspector tests pass
- **Committed in:** e98e4ae (feat(07-02) commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking, 1 Rule 1 bug)
**Impact on plan:** Both fixes essential. Rule 3 fix completed plan 07-01 work that should have run first. Rule 1 fix corrected a Kotlin string parsing subtlety. No scope creep.

## Issues Encountered
- None beyond the deviations documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- VariableInspector, SavestateManager, and VisualDiff are ready for use in automated UAT playtest scripts (plan 07-03+)
- All plan 07-01 agent primitives are now also complete (InputScript, InputScriptPlayer, ScreenshotCapture pending)
- 219 total emulator tests pass (54 agent tests), zero failures

---
*Phase: 07-uat-gameplay-validation*
*Completed: 2026-03-13*

## Self-Check: PASSED

Files verified present:
- FOUND: VariableInspector.kt
- FOUND: SavestateManager.kt
- FOUND: VisualDiff.kt
- FOUND: VariableInspectorTest.kt
- FOUND: SavestateManagerTest.kt
- FOUND: VisualDiffTest.kt

Commits verified:
- FOUND: d0a5bfa (test: VariableInspector RED)
- FOUND: e98e4ae (feat: VariableInspector GREEN + Rule 3 fix)
- FOUND: 3aa897b (test: SavestateManager + VisualDiff RED)
- FOUND: 7b1cca2 (feat: SavestateManager + VisualDiff GREEN)
