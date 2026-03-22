---
phase: 07-uat-gameplay-validation
plan: 04
subsystem: tooling
tags: [gradle-plugin, agent-dx, tasks, headless-emulator, screenshot, input-script, savestate, visual-diff]

# Dependency graph
requires:
  - phase: 07-uat-gameplay-validation plan 01
    provides: InputScript DSL, InputScriptPlayer, ScreenshotCapture
  - phase: 07-uat-gameplay-validation plan 02
    provides: VariableInspector, SavestateManager, VisualDiff
  - phase: 07-uat-gameplay-validation plan 03
    provides: AgentDebugSession, AgentSessionConfig

provides:
  - CaptureScreenshotTask: ./gradlew captureScreenshot --frames=N --label=name
  - RunInputScriptTask: ./gradlew runScript --script=scripts/test.txt
  - ReadVariableTask: ./gradlew readVariable --variable=score --frames=300
  - SaveStateTask: ./gradlew saveState --frames=300 --state-file=checkpoint.gbst
  - DiffScreenshotsTask: ./gradlew diffScreenshots --expected=ref.png --actual=current.png

affects:
  - All agent-driven UAT playtest sessions can now use ./gradlew commands
  - GbktPlugin now exposes 5 additional tasks under "gbkt-agent" group

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Abstract DefaultTask with @Input/@InputFile properties + @TaskAction (same as EmulatorTestTask)
    - AgentDebugSession.use {} (Closeable) for clean lifecycle in Gradle task actions
    - Line-based text script format for RunInputScriptTask (no .kts eval complexity)
    - ProjectBuilder unit tests for default conventions; GradleRunner functional tests for registration/deps

key-files:
  created:
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CaptureScreenshotTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/RunInputScriptTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ReadVariableTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SaveStateTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/DiffScreenshotsTask.kt
    - gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/AgentTasksTest.kt
  modified:
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt

key-decisions:
  - "Line-based script format for RunInputScriptTask: wait/press/hold/release/screenshot commands. Simpler and more robust for agents than .kts eval. Screenshot commands collected and executed after the input script completes."
  - "DiffScreenshotsTask does NOT depend on buildRom — it is a pure file comparison. All other 4 tasks depend on buildRom to ensure ROM is current."
  - "screenshotDir convention for captureScreenshot: build/gbkt/screenshots (consistent with build output tree)"
  - "Re-publish gbkt-emulator to mavenLocal required before compilation (agent subpackage added in Plans 01-03 wasn't in Feb 28 jar)"

requirements-completed:
  - UAT-01

# Metrics
duration: 4min
completed: 2026-03-13
---

# Phase 07 Plan 04: Agent Gradle Tasks Summary

**5 Gradle tasks (captureScreenshot, runScript, readVariable, saveState, diffScreenshots) registered under the "gbkt-agent" group in GbktPlugin, exposing all AgentDebugSession primitives as CLI-callable ./gradlew commands**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-13T11:51:10Z
- **Completed:** 2026-03-13T11:54:55Z
- **Tasks:** 2 (Task 1: 5 task classes; Task 2: GbktPlugin registration + test)
- **Files created:** 6
- **Files modified:** 1

## Accomplishments

- **CaptureScreenshotTask**: Runs ROM headlessly for N frames (default: 60), captures 160x144 PNG to `build/gbkt/screenshots`; optional symFile for variable JSON sidecar
- **RunInputScriptTask**: Parses line-based script format (wait/press/hold/release/screenshot) into InputScript; runs against ROM headlessly; captures screenshots after script completes
- **ReadVariableTask**: Runs ROM for N frames, reads named variable (or "all") from WRAM via VariableInspector; logs `name = value (0xHEX)` for agent consumption
- **SaveStateTask**: Save-only or load-then-run-then-save workflow via loadStateFile optional property; GBST output to configurable stateFile
- **DiffScreenshotsTask**: Pure file comparison via VisualDiff.compare(); throws GradleException on mismatch for non-zero exit code; diff image written next to actualFile
- **GbktPlugin**: All 5 tasks registered in registerTasks(); ROM tasks wired to compileRom.flatMap { it.outputRom }; diffScreenshots has no ROM dependency
- **AgentTasksTest**: 10 tests — task registration, gbkt-agent group in output, dry-run dependency ordering (3 tasks), per-class default conventions (4 tests), script parser correctness

## Task Commits

1. **Task 1: 5 Gradle task classes** - `0e3bf27` (feat) — CaptureScreenshotTask, RunInputScriptTask, ReadVariableTask, SaveStateTask, DiffScreenshotsTask
2. **Task 2: GbktPlugin + AgentTasksTest** - `ddd5c4d` (feat) — task registration in GbktPlugin + 10-test suite

## Files Created/Modified

- `gbkt-gradle-plugin/.../tasks/CaptureScreenshotTask.kt` — Screenshot capture task (frames, label, screenshotDir props)
- `gbkt-gradle-plugin/.../tasks/RunInputScriptTask.kt` — Input script parser + execution task (scriptFile prop)
- `gbkt-gradle-plugin/.../tasks/ReadVariableTask.kt` — Variable reader task (variableName, frames props)
- `gbkt-gradle-plugin/.../tasks/SaveStateTask.kt` — State checkpoint task (stateFile output, loadStateFile optional input)
- `gbkt-gradle-plugin/.../tasks/DiffScreenshotsTask.kt` — Screenshot diff task (expectedFile, actualFile, tolerance props)
- `gbkt-gradle-plugin/.../tasks/AgentTasksTest.kt` — 10 unit + functional tests
- `gbkt-gradle-plugin/.../GbktPlugin.kt` — Added 5 import statements + 5 task registrations in registerTasks()

## Decisions Made

- **Line-based script format**: `wait/press/hold/release/screenshot` commands are simpler and more agent-friendly than .kts eval. Agents can generate these text files with simple string construction. Screenshot commands are batched at the end of the script (v1 simplification — adequate for capturing final state after input sequence).
- **DiffScreenshotsTask has no buildRom dependency**: It operates on any two PNG files, not necessarily from the current ROM build. This allows comparing screenshots from different sessions, different ROMs, or previously saved references.
- **No stateFile convention for SaveStateTask in registration**: The stateFile convention is set to `build/gbkt/states/checkpoint.gbst` in GbktPlugin. Users can override via `--state-file` or task configuration.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Re-published gbkt-emulator to mavenLocal**
- **Found during:** Task 1 (compileKotlin failed with "Unresolved reference: agent")
- **Issue:** The `io.github.gbkt.emulator.agent` subpackage (created in Plans 01-03 on 2026-03-13) was not in the mavenLocal gbkt-emulator jar (dated 2026-02-28). The gradle-plugin depends on `gbkt-emulator:0.1.0-SNAPSHOT` from mavenLocal.
- **Fix:** Ran `./gradlew :gbkt-emulator:publishToMavenLocal -x :gbkt-gradle-plugin:compileKotlin -x detekt -x spotlessCheck` to refresh the jar with the agent subpackage.
- **Impact:** Zero — a build environment sync step, not a code change.

---

**Total deviations:** 1 (Rule 3 - blocking issue), auto-fixed

## Issues Encountered

None beyond the deviation documented above.

## User Setup Required

None — all tasks are registered and available after `./gradlew build`.

## Next Phase Readiness

- All 5 agent tasks are functional and CLI-callable via `./gradlew <task>`
- Agents can now perform the full agent DX workflow:
  - `./gradlew captureScreenshot --frames=120 --label=title_screen`
  - `./gradlew runScript --script=scripts/pong_test.txt`
  - `./gradlew readVariable --variable=score --frames=300`
  - `./gradlew saveState --frames=300 --state-file=build/gbkt/states/cp.gbst`
  - `./gradlew diffScreenshots --expected=ref.png --actual=current.png --tolerance=0.05`
- Ready for Plan 07-05+ (actual game scenario scripts using these tasks)

## Self-Check: PASSED

Files verified present:
- FOUND: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CaptureScreenshotTask.kt
- FOUND: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/RunInputScriptTask.kt
- FOUND: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ReadVariableTask.kt
- FOUND: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SaveStateTask.kt
- FOUND: gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/DiffScreenshotsTask.kt
- FOUND: gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/AgentTasksTest.kt

Commits verified:
- FOUND: 0e3bf27 (feat: 5 agent Gradle task classes)
- FOUND: ddd5c4d (feat: GbktPlugin registration + AgentTasksTest)

---
*Phase: 07-uat-gameplay-validation*
*Completed: 2026-03-13*
