---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "06"
subsystem: gbkt-analysis
tags: [sonar, s3776, cognitive-complexity, extract-method, non-emitting]
dependency_graph:
  requires: []
  provides: [SONAR-01-N02, SONAR-01-N08, SONAR-01-N13]
  affects: [gbkt-analysis]
tech_stack:
  added: []
  patterns: [extract-method, value-returning-helpers, per-operator-group-dispatch]
key_files:
  created: []
  modified:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt
decisions:
  - "Split collectAllTopLevelOps into 9 private helpers (7 per-category + 2 per-system-type); caller composes via + concatenation"
  - "Split evalBinaryOp into 4 per-operator-group helpers (arithmetic/bitwise/comparison/logical); dispatcher reduced to 4 when-branches"
  - "Split optimizeExpr when-branch bodies into optimizeMul/optimizeDiv/optimizeMod; K2 smart-cast caveat comment preserved in optimizeMul"
metrics:
  duration: "3 min"
  completed: "2026-06-13"
  tasks: 3
  files: 3
requirements: [SONAR-01]
---

# Phase 18 Plan 06: SONAR-01 NON-EMITTING Batch (D-06) — Analysis Passes Summary

Behavior-preserving extract-method refactor of three `gbkt-analysis` Sonar S3776 cognitive-complexity findings. Each finding received its own commit; JVM-test-only evidence (NON-EMITTING passes — no C output path, no ROM sweep needed per D-06).

## Tasks Completed

| Task | Finding | File | CC Before | Action | Commit |
|------|---------|------|-----------|--------|--------|
| 1 | N-02 | SemanticValidationPass.kt | 34 | Extract 9 private helpers for op-category fan-out | f462e6e4 |
| 2 | N-08 | ConstantFoldingPass.kt | 23 | Split flat when-dispatch into 4 per-operator-group helpers | d3958cb0 |
| 3 | N-13 | BitwiseOptimizationPass.kt | 17 | Extract MUL/DIV/MOD rewrite cases into focused helpers | 83a2c664 |

## Decisions Made

1. **N-02 decomposition shape:** `collectAllTopLevelOps` now delegates to 9 private helpers (7 per-category: `collectSceneOps`, `collectZoneOps`, `collectCollisionRuleOps`, `collectActorPoolOps`, `collectMenuOps`, `collectPuzzleObjectOps`, `collectSystemOps`; plus 2 per-system-type: `collectExplorationSystemOps`, `collectCombatSystemOps`). The caller assembles via `+` concatenation in the same traversal order as the original `buildList` body. The `when (system)` dispatch is itself isolated inside `collectSystemOps` so each helper has a single responsibility.

2. **N-08 decomposition shape:** `evalBinaryOp` becomes a 4-branch dispatcher that routes to `evalArithmeticOp`, `evalBitwiseOp`, `evalComparisonOp`, `evalLogicalOp`. Each helper returns `Int?` and mirrors the original branch logic exactly (including div-by-zero null returns). The `else -> null` arms in each helper are dead code guards required for exhaustive `when`; they do not change fold results.

3. **N-13 decomposition shape:** `optimizeExpr` dispatcher is unchanged in structure — only the three non-trivial `when` branch bodies are extracted to `optimizeMul`, `optimizeDiv`, `optimizeMod`. The K2 smart-cast caveat comment (Sonar S6531 false positive) is preserved in `optimizeMul`'s doc.

## Verification

All three commits verified with `./gradlew :gbkt-analysis:test` — BUILD SUCCESSFUL (38 tasks, all pass).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — internal analysis-pass refactor; no new trust boundary, network endpoint, auth path, file access pattern, or schema change.

## Self-Check: PASSED

- [x] SemanticValidationPass.kt modified — confirmed at f462e6e4
- [x] ConstantFoldingPass.kt modified — confirmed at d3958cb0
- [x] BitwiseOptimizationPass.kt modified — confirmed at 83a2c664
- [x] `./gradlew :gbkt-analysis:test` green after each commit
