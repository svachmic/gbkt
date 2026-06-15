---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "07"
subsystem: analysis, emulator
tags: [sonar, s3776, extract-method, cognitive-complexity, non-emitting]
dependency_graph:
  requires: []
  provides: [N-15-closed, N-04-closed, N-05-closed]
  affects: [gbkt-analysis, gbkt-emulator]
tech_stack:
  added: []
  patterns: [extract-method-returns-value]
key_files:
  modified:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/EmulatorSession.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GameMetadata.kt
decisions:
  - "N-15: Extract collectNonSceneNavigations; buildTransitionGraph complexity 16→4"
  - "N-04: Extract createAndWireWindowsOnEdt, safeMemoryRead, wireToolbarCallbacks, wireInputAfterStart; launch cc 32→3"
  - "N-05: Extract per-section parseScenes/parseActors/parseVariables/parseTexts/parseTerminalScenes/parseControls/parseTransitions/parseTileDecoders/parseTileDecoderConfig; fromJsonString cc 32→3"
  - "ThrowsCount @Suppress retained on fromJsonString (4 throw sites remain); LongMethod+CyclomaticComplexMethod suppressions removed"
metrics:
  duration: "2 min"
  completed: "2026-06-13"
  tasks_completed: 3
  files_modified: 3
---

# Phase 18 Plan 07: SONAR-01 NON-EMITTING Batch D-06 (N-15, N-04, N-05) Summary

Three S3776 findings closed via extract-method in `gbkt-analysis` and `gbkt-emulator`. JVM-test-only evidence per D-06 (non-emitting batch). Each finding has its own commit.

## Tasks Completed

| # | Task | Finding | CC Before | CC After | Commit |
|---|------|---------|-----------|----------|--------|
| 1 | Extract `collectNonSceneNavigations` from `buildTransitionGraph` | N-15 | 16 | ~4 | e88f4a4f |
| 2 | Decompose `EmulatorSession.launch` into focused helpers | N-04 | 32 | ~3 | 763d6aa0 |
| 3 | Extract per-section parsers from `GameMetadata.fromJsonString` | N-05 | 32 | ~3 | bd4b8452 |

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

### Files exist
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt` — FOUND
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/EmulatorSession.kt` — FOUND
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GameMetadata.kt` — FOUND

### Commits exist
- e88f4a4f — FOUND
- 763d6aa0 — FOUND
- bd4b8452 — FOUND

### Tests
- `./gradlew :gbkt-analysis:test` — BUILD SUCCESSFUL (38 tests)
- `./gradlew :gbkt-emulator:test` — BUILD SUCCESSFUL (all tests)

## Self-Check: PASSED

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. Pure in-file extract-method refactoring.
