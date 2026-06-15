---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 10
subsystem: test-infra, simulation, dsl
tags: [sonar, s3776, extract-method, non-emitting, refactor]
dependency_graph:
  requires: []
  provides: [SONAR-01-N06-closed, SONAR-01-N03-closed, SONAR-01-N11-closed]
  affects: [gbkt-test, gbkt-core, gbkt-lang]
tech_stack:
  added: []
  patterns: [extract-method, value-returning-helpers]
key_files:
  created: []
  modified:
    - gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestRecipes.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt
decisions:
  - "N-03: evaluateBinaryExpr split into 5 helpers — dispatcher, evaluateLogicalAnd, evaluateLogicalOr, evaluateEagerBinaryOp, evaluateDivMod, evaluateComparisonOp (compareTo avoids per-case ifs)"
  - "N-06: verifyMetadataSymbolAgreement delegates to 5 private helpers — verifyActorXySymbols, verifyCurrentSceneSymbol, verifySceneHeaderConsistency, verifySceneExpectations, verifyOamNoOverlaps"
  - "N-11: build() delegates palette injection (buildScenesWithActorPalettes), NPC collision setup (buildEffectiveNpcCollisions), and actor re-mapping (assignImplicitNpcGroups) to private helpers"
metrics:
  duration_minutes: 10
  completed_date: "2026-06-13"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 3
---

# Phase 18 Plan 10: NON-EMITTING S3776 Batch D-06 (N-06, N-03, N-11) Summary

**One-liner:** Extract-method refactor across gbkt-test, gbkt-core test infra, and gbkt-lang DSL builder closes three S3776 NON-EMITTING findings via focused private helpers returning values.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Extract-method GbktTestRecipes.verifyMetadataSymbolAgreement (N-06) | 1811b7f2 | GbktTestRecipes.kt |
| 2 | Extract-method ScriptOpInterpreter.evaluateBinaryExpr (N-03) | 83e6cb7b | ScriptOpInterpreter.kt |
| 3 | Extract-method GameBuilder.build (N-11) | 4272189e | GameBuilder.kt |

## Findings Closed

### N-06: GbktTestRecipes.verifyMetadataSymbolAgreement (cc=32 → below threshold)

Extracted 5 private helper functions from the 7-check orchestrator:
- `verifyActorXySymbols(metadata, loadedVars)` — actor X/Y symbol table check
- `verifyCurrentSceneSymbol(currentSceneVar, loadedVars)` — current-scene var check
- `verifySceneHeaderConsistency(metadata, gameHeader)` — game.h ↔ metadata scene consistency
- `verifySceneExpectations(metadata, expectation)` — scene count/names/actors/OAM checks
- `verifyOamNoOverlaps(metadata)` — OAM slot overlap detection

Removed the `@Suppress("CyclomaticComplexMethod", "ThrowsCount")` annotation (no longer needed).

### N-03: ScriptOpInterpreter.evaluateBinaryExpr (cc=33 → below threshold)

Decomposed into 5 focused helpers:
- `evaluateBinaryExpr` — dispatcher (outer when: LOGICAL_AND → evaluateLogicalAnd, LOGICAL_OR → evaluateLogicalOr, else → evaluateEagerBinaryOp)
- `evaluateLogicalAnd(expr)` — short-circuit AND
- `evaluateLogicalOr(expr)` — short-circuit OR
- `evaluateEagerBinaryOp(op, left, right)` — arithmetic/bitwise/comparison dispatch (delegates DIV/MOD and comparison cases)
- `evaluateDivMod(op, left, right)` — division/modulo with zero-denominator guard
- `evaluateComparisonOp(op, left, right)` — EQ/NEQ/LT/LTE/GT/GTE via `compareTo` (eliminates per-case `if` expressions)

Simulation results identical (behavior-preserving extract).

### N-11: GameBuilder.build (cc=18 → below threshold)

Extracted 3 private helpers; `build()` now only handles validation, actor collection, and GameIR assembly:
- `buildScenesWithActorPalettes(actors, scenes)` — auto-slot palette op injection (SEED-007 counter preserved)
- `buildEffectiveNpcCollisions(actors)` — implicit `_default_npc` group/rule auto-creation; returns `Pair<MutableList<CollisionGroupIR>, MutableList<CollisionRuleIR>>`
- `assignImplicitNpcGroups(actors)` — actor re-mapping to assign `_default_npc` groupId

GameIR assembly order preserved; no byte-identity concern (non-emitting).

Added `ActorIR` and `SceneIR` imports; simplified `sceneBuilders` type declaration from FQN to imported short name.

## Verification

```
./gradlew :gbkt-test:test :gbkt-core:test :gbkt-lang:test
BUILD SUCCESSFUL
```

All three module test suites pass.

## Deviations from Plan

None — plan executed exactly as written. All three helpers follow the extract-returning-value pattern from PATTERNS.md §"Track D".

## Known Stubs

None.

## Threat Flags

None — test infra and IR-assembly refactor; no new trust boundaries.

## Self-Check: PASSED

Files exist:
- gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestRecipes.kt ✓
- gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt ✓
- gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt ✓

Commits exist:
- 1811b7f2 (N-06) ✓
- 83e6cb7b (N-03) ✓
- 4272189e (N-11) ✓
