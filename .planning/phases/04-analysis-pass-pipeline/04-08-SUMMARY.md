---
phase: 04-analysis-pass-pipeline
plan: 08
subsystem: codegen
tags: [kotlin, bank-wiring, trampolines, gbdk-backend, gradle-plugin, scene-visitor]
dependency_graph:
  requires:
    - "04-07: BudgetAuditPass + DefaultPipeline + GBDKBackend analysis integration"
    - "04-01: C AST types (CFunction.bank, isBanked fields)"
    - "04-02: SceneVisitor, GBDKPipelineV2"
  provides:
    - "SceneVisitor reads bankSlot and sets CFunction.bank + isBanked"
    - "GBDKPipelineV2 HOME-bank trampoline stubs for banked scenes"
    - "budgetReport Gradle task"
  affects:
    - "C code generation output (BANKED annotations on scene functions)"
    - "navigate_to_scene dispatch (trampolines for banked scenes)"
tech_stack:
  added: []
  patterns:
    - "Reflection-based worker action (same as GenerateCTask) for budgetReport task"
    - "Trampoline pattern for GBDK cross-bank calls"
    - "bankSlot?.bank null-safe read for backward compat"
key_files:
  created:
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BudgetReportTask.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitorTest.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt
decisions:
  - "buildSceneFile() uses firstOrNull bankSlot to pick file bank — scenes in one bank per file is the current model; future multi-bank support would require splitting into per-bank files"
  - "BudgetReportTask uses reflection via classloader isolation — analysis classes live in user runtime classpath, not plugin compile classpath; mirrors GenerateCTask pattern exactly"
  - "navigate_to_scene and main() dispatch through trampolines for banked scenes — HOME-resident code must never call BANKED functions directly; trampoline indirection ensures GBDK bank switching"
  - "isBanked logic: (sceneBank == null || sceneBank > 0) — null bankSlot = backward compat (all scenes BANKED), bank 0 = HOME not banked, bank > 0 = BANKED with explicit bank number"
metrics:
  duration: "5 min"
  completed: "2026-02-18"
  tasks: 2
  files: 5
---

# Phase 4 Plan 08: Bank Wiring + BudgetReport Gradle Task Summary

Bank annotations from the analysis pipeline now flow into C code generation: SceneVisitor reads bankSlot and emits BANKED C functions with explicit bank numbers; GBDKPipelineV2 generates HOME-bank trampoline stubs for cross-bank calls; `gradle budgetReport` is a registered task that runs the full analysis pipeline and prints the ASCII budget report.

## What Was Built

### Task 1: SceneVisitor bank wiring + GBDKPipelineV2 trampolines

**SceneVisitor.visit() — bankSlot read:**
- Reads `scene.bankSlot?.bank` (null-safe)
- When `bankSlot` is null (no analysis ran): `isBanked=true`, `bank=null` (inherits from CFile — backward compat)
- When `bankSlot.bank > 0`: `isBanked=true`, `bank=bankSlot.bank` (explicit bank assignment)
- When `bankSlot.bank == 0`: `isBanked=false`, `bank=0` (HOME bank, not banked)
- All 3 lifecycle functions (enter, frame, exit) inherit the same bank settings

**GBDKPipelineV2 — trampoline stubs:**
- `buildTrampolineStubs(gameIR)` generates HOME-resident wrapper functions for each banked scene
- For each scene with `bankSlot.bank > 0`, generates `{scene}_enter_trampoline`, `{scene}_frame_trampoline`, `{scene}_exit_trampoline`
- Trampolines have `bank=0`, `isBanked=false` (they live in HOME)
- Trampoline body: single call to the BANKED function (`{scene}_enter()`, etc.)
- Added to HOME file's function list after sprite helper stubs

**GBDKPipelineV2 — navigate_to_scene dispatch:**
- `enterFunctionName(scene)`, `frameFunctionName(scene)`, `exitFunctionName(scene)` helpers
- Returns `{scene}_enter_trampoline` if `bankSlot.bank > 0`, else `{scene}_enter`
- navigate_to_scene switch cases call trampolines for banked scenes
- main() game loop and start scene enter call also use trampoline for banked scenes

**GBDKPipelineV2 — buildSceneFile():**
- Uses `gameIR.scenes.firstOrNull { it.bankSlot != null }?.bankSlot?.bank ?: 1` for CFile bank
- Preserves backward compat (defaults to bank 1) when no bankSlot assigned

**SceneVisitorTest — new tests:**
- Test 10: `scene with bankSlot sets bank on CFunction` — bankSlot=BankSlot(2) → bank=2, isBanked=true
- Test 11: `scene without bankSlot keeps isBanked true with null bank` — backward compat
- Test 12: `scene with bankSlot bank=0 sets isBanked=false` — HOME bank scenes not banked

### Task 2: BudgetReportTask + GbktPlugin registration

**BudgetReportTask.kt:**
- `abstract class BudgetReportTask @Inject constructor(WorkerExecutor)` — follows GenerateCTask pattern
- Inputs: `gameSpec: Property<String>`, `target: Property<String>`, `runtimeClasspath: ConfigurableFileCollection`
- Uses `workerExecutor.classLoaderIsolation` so analysis classes load from user runtime classpath
- `BudgetReportWorkAction` does reflection-based game loading and pipeline execution
- `resolveGameIR(game)`: returns game directly if it IS a GameIR, otherwise tries `getIr()` accessor
- `runAnalysisOnGameIR(gameIR)`: loads DefaultPipeline, AnalysisConfig, PassContext via Class.forName
- Builds initial PassContext via synthetic constructor reflection with defaults bitmask
- Calls `DefaultPipeline.create().execute(initialContext)` and prints `budgetReport` from result
- On PassResult.Failed: throws GradleException with diagnostic messages

**GbktPlugin.kt:**
- Import `BudgetReportTask` added
- `budgetReport` task registered after `generateC`, before `copyResources`
- Depends on `compileKotlinTask`; receives `gameSpec`, `target`, `runtimeClasspath`

## Verification

```
./gradlew :gbkt-backend-gbdk:test   # 24 tests PASS (3 new bank-wiring tests)
./gradlew :gbkt-gradle-plugin:test  # all plugin tests PASS
./gradlew build                     # full project build PASS (131 tasks)
```

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Notes

- `ActorVisitor` was reviewed: it produces only `CVarDecl` nodes (position variables), not `CFunction` nodes, so no bank wiring was needed there. Plan item 2 in Task 1 was a no-op as expected.
- Spotless formatting was applied automatically to GBDKPipelineV2.kt after creation.

## Self-Check: PASSED

All files verified:
- FOUND: SceneVisitor.kt
- FOUND: GBDKPipelineV2.kt
- FOUND: BudgetReportTask.kt (NEW)
- FOUND: GbktPlugin.kt
- FOUND: 04-08-SUMMARY.md

All commits verified:
- FOUND: f526fca (Task 1 — SceneVisitor bank wiring + trampolines)
- FOUND: 60a1be7 (Task 2 — budgetReport Gradle task)
