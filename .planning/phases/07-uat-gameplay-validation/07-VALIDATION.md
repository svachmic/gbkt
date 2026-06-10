---
phase: 07
slug: uat-gameplay-validation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-13
---

# Phase 07 -- Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 (existing in all modules) |
| **Config file** | `build.gradle.kts` per module -- `tasks.test { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew :gbkt-emulator:test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :gbkt-emulator:test`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green + `./gradlew emulatorTest` green + manual UAT checklists 100% pass
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | UAT-01 | unit | `./gradlew :gbkt-emulator:test --tests "*InputScript*"` | W0 | pending |
| 07-01-02 | 01 | 1 | UAT-01 | unit | `./gradlew :gbkt-emulator:test --tests "*ScreenshotCapture*"` | W0 | pending |
| 07-02-01 | 02 | 1 | UAT-01 | unit | `./gradlew :gbkt-emulator:test --tests "*VariableInspector*"` | W0 | pending |
| 07-02-02 | 02 | 1 | UAT-01 | unit | `./gradlew :gbkt-emulator:test --tests "*SavestateManager*" --tests "*VisualDiff*"` | W0 | pending |
| 07-03-01 | 03 | 2 | UAT-01 | unit | `./gradlew :gbkt-emulator:test --tests "*AgentDebugSession*"` | W0 | pending |
| 07-04-01 | 04 | 3 | UAT-01 | compile + functional | `./gradlew :gbkt-gradle-plugin:test --tests "*AgentTasks*"` | W0 | pending |
| 07-05+ | 05-09 | 3-5 | UAT-01, UAT-02 | integration + manual | `./gradlew emulatorTest` + manual UAT checklists | W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/InputScriptTest.kt` -- input DSL builder
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/InputScriptPlayerTest.kt` -- input injection via EventBus
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt` -- screenshot + JSON sidecar
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VariableInspectorTest.kt` -- .sym variable name lookup
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/SavestateManagerTest.kt` -- state serialization round-trip
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VisualDiffTest.kt` -- pixel comparison
- [ ] `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/AgentDebugSessionTest.kt` -- agent session lifecycle (Plan 03)
- [ ] `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/AgentTasksTest.kt` -- Gradle task registration (Plan 04)
- [ ] Framework install: not needed -- JUnit Jupiter already configured

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Per-game UAT checklists pass in mGBA | UAT-01 | Visual/gameplay verification requires human player | Play each game in mGBA, follow UAT checklist scenarios |
| Per-game UAT checklists pass in Coffee-GB | UAT-01 | Visual/gameplay verification requires human player | Play each game in embedded emulator, follow UAT checklist scenarios |
| UAT_GUIDE.md is comprehensive and accurate | UAT-02 | Document quality requires human judgment | Review context/UAT_GUIDE.md for completeness |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
