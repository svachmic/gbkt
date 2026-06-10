---
phase: 04-analysis-pass-pipeline
plan: 06
subsystem: analysis-passes
tags: [dead-code-elimination, constant-folding, optimization, tdd]
dependency_graph:
  requires:
    - "04-02 (PassContext, PassResult, AnalysisPass interface)"
    - "gbkt-core ScriptOp/Expr sealed hierarchies"
  provides:
    - "DeadCodeEliminationPass — unreachable scene detection via BFS"
    - "ConstantFoldingPass — compile-time Literal arithmetic evaluation"
  affects:
    - "Pass pipeline consumers: budget audit accuracy improved by folding"
    - "Developer diagnostics: unreachable scenes flagged as INFO"
tech_stack:
  added: []
  patterns:
    - "BFS reachability from startScene through NavigateTo transition graph"
    - "Recursive expression folding: foldExpr() bottom-up on BinaryExpr tree"
    - "Exhaustive when on ScriptOp sealed subtypes in foldOp()"
    - "TDD: RED (test first) -> GREEN (minimal impl) cycle for both passes"
key_files:
  created:
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/DeadCodeEliminationPass.kt
    - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/DeadCodeEliminationPassTest.kt
    - gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPassTest.kt
  modified: []
decisions:
  - "DeadCodeEliminationPass uses NavigateTo (not a hypothetical EnterScene) — the actual ScriptOp for scene transitions in the v2 IR is NavigateTo; plan referenced Expr.Binary/Expr.Literal but actual types are BinaryExpr/Literal"
  - "DeadCodeEliminationPass is analysis-only: does not remove unreachable scenes from GameIR — advisory INFO diagnostics only (ANLZ-01)"
  - "ConstantFoldingPass division by zero returns null from evalBinaryOp — original BinaryExpr preserved to avoid introducing compile-time errors"
  - "ConstantFoldingPass foldOp() uses else -> op for ops with no expression fields (NavigateTo, PlaySound, ShowDialog, SpawnActor, DestroyActor, AnimateOp, SetVisible, WaitFrames, RawOp)"
  - "ConstantFoldingPass returns modified GameIR via context.copy(game = foldedGame) — consistent with PassContext immutability pattern"
metrics:
  duration: 15 min
  completed: "2026-02-18"
  tasks: 2
  files: 4
---

# Phase 04 Plan 06: Dead Code Elimination and Constant Folding Summary

**One-liner:** BFS-based unreachable scene detection (INFO diagnostics) and recursive compile-time constant folding for Literal arithmetic, returning a mutated GameIR.

## What Was Built

### DeadCodeEliminationPass

Detects scenes unreachable from `GameIR.startScene` via BFS over the `NavigateTo` transition graph:

1. **Null guard**: if `startScene` is null, the pass returns immediately with no diagnostics.
2. **Transition graph**: walks `enterOps`, `frameOps`, `exitOps` of every scene recursively (including nested `IfOp`/`WhileOp`/`ForOp`/`FadeOp`/`ShowMenu` handlers) and records `NavigateTo.sceneId` edges.
3. **BFS**: starts from `startScene`, explores all reachable scenes.
4. **Diagnostics**: for each scene not in the reachable set, emits `Diagnostic(id="ANLZ-01", severity=INFO)`.

The pass is advisory — it never modifies `GameIR`. It appends diagnostics via `context.withDiagnostics()`.

### ConstantFoldingPass

Evaluates compile-time-known `BinaryExpr(Literal, op, Literal)` expressions:

1. **Tree walk**: `foldGame -> foldScene -> foldScriptOps -> foldOp -> foldExpr` recursively covers all expression sites in all scenes.
2. **Recursive folding**: `foldExpr()` folds children first (bottom-up), then checks if both are `Literal`.
3. **Supported ops**: ADD, SUB, MUL, DIV, MOD, AND, OR, XOR, SHL, SHR, EQ, NEQ, LT, LTE, GT, GTE, LOGICAL_AND, LOGICAL_OR.
4. **Division by zero**: `evalBinaryOp()` returns `null` for `DIV/MOD` with `r==0`; original node preserved.
5. **Result**: returns `PassResult.Success(context.copy(game = foldedGame))`.

## Deviations from Plan

### Auto-corrected Terminology

**Found during:** Task 1 and Task 2 implementation

**Issue:** Plan referred to `ScriptOp.EnterScene` and `Expr.Binary`/`Expr.Literal` — these type names do not exist in the v2 IR. The actual types are `NavigateTo` (for scene transitions) and `BinaryExpr`/`Literal` respectively.

**Fix:** Used the correct v2 IR types throughout. No functional deviation — the semantics described in the plan are correctly implemented.

**Files modified:** Both pass files and both test files use correct type names.

None — plan executed correctly after mapping terminology to actual v2 IR type names.

## Test Coverage

| Pass | Tests | All Pass |
|------|-------|----------|
| DeadCodeEliminationPass | 5 | Yes |
| ConstantFoldingPass | 7 | Yes |
| **Total** | **12** | **Yes** |

### DeadCodeEliminationPass tests
- `all scenes reachable produces no diagnostics`
- `unreachable scene produces INFO diagnostic`
- `no startScene skips analysis`
- `multiple unreachable scenes each get a diagnostic`
- `transitive reachability works`

### ConstantFoldingPass tests
- `Binary(Literal(2), ADD, Literal(3)) folds to Literal(5)`
- `Binary(Literal(10), SUBTRACT, Literal(3)) folds to Literal(7)`
- `Binary(Literal(4), MULTIPLY, Literal(5)) folds to Literal(20)`
- `Binary(Literal(10), DIVIDE, Literal(3)) folds to Literal(3) (integer division)`
- `Binary(VarRef, ADD, Literal(3)) stays unchanged`
- `nested folding Binary(Binary(Literal(1), ADD, Literal(2)), MULTIPLY, Literal(3)) folds to Literal(9)`
- `division by zero stays unchanged`

## Commits

| Hash | Description |
|------|-------------|
| `0e39a63` | feat(04-06): implement DeadCodeEliminationPass with TDD |
| `76840be` | feat(04-06): implement ConstantFoldingPass with TDD |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| DeadCodeEliminationPass.kt | FOUND |
| ConstantFoldingPass.kt | FOUND |
| DeadCodeEliminationPassTest.kt | FOUND |
| ConstantFoldingPassTest.kt | FOUND |
| 04-06-SUMMARY.md | FOUND |
| Commit 0e39a63 | FOUND |
| Commit 76840be | FOUND |
| All tests pass | BUILD SUCCESSFUL |
