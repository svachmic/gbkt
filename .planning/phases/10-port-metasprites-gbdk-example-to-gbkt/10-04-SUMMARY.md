---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: "04"
subsystem: ir-dsl
tags: [metasprites, script-op, visitor, dsl, tdd]
dependency_graph:
  requires: [10-02, 10-03]
  provides: [MoveMetasprite ScriptOp, visitMoveMetasprite, moveMetasprite DSL function]
  affects: [gbkt-ir, gbkt-lang, gbkt-backend-gbdk, gbkt-core, gbkt-analysis]
tech_stack:
  added: []
  patterns: [visitor-dispatch, tdd-red-green, script-builder-context]
key_files:
  created: []
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/MetaspriteIRTest.kt
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/ActorPoolIRTest.kt
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/PuzzleAdvancedTest.kt
    - gbkt-ir/src/test/kotlin/io/github/gbkt/core/ir/PuzzleCoreTest.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilderTest.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/IRHierarchyTest.kt
decisions:
  - "Chose Open Question 3 resolution (b): user-emitted moveMetasprite() op in frame loop rather than always-emitted codegen"
  - "Added moveMetasprite as top-level DSL function (not ScriptBuilder method) mirroring ActorRef.moveTo() ScriptBuilderContext.current pattern"
  - "ScriptOpVisitor in gbkt-backend-gbdk uses error() stub (not Unit) to loudly fail if Plan 07 wiring is skipped"
metrics:
  duration: "9 minutes"
  completed: "2026-05-18T15:51:00Z"
  tasks_completed: 2
  files_changed: 10
---

# Phase 10 Plan 04: MoveMetasprite ScriptOp + DSL Surface Summary

Add `MoveMetasprite` ScriptOp with visitor interface hook and `moveMetasprite(ref)` DSL function, resolving RESEARCH Open Question 3 with approach (b): explicit user-emitted op in frame loop for render ordering control.

## Tasks Completed

| Task | Status | Commit |
|------|--------|--------|
| Task 1 (RED): Failing tests for MoveMetasprite ScriptOp | DONE | 2b524c90 |
| Task 1 (GREEN): Add MoveMetasprite + visitMoveMetasprite + stubs | DONE | 7fa3b57b |
| Task 2 (RED): Failing test for moveMetasprite DSL function | DONE | 8a681623 |
| Task 2 (GREEN): Implement moveMetasprite DSL function | DONE | c75b797c |

## What Was Built

### MoveMetasprite ScriptOp (gbkt-ir)

Added `data class MoveMetasprite(val metaspriteId: String, override val sourceLocation: SourceLocation? = null) : ScriptOp` to `ScriptOp.kt` under a new `// --- Metasprites ---` section. The `accept` override dispatches to `visitor.visitMoveMetasprite(this)`.

### ScriptOpVisitorI.visitMoveMetasprite (gbkt-ir)

Added `fun visitMoveMetasprite(op: MoveMetasprite): T` to `ScriptOpVisitorI` interface under a `// --- Metasprites ---` section, with KDoc noting Plan 07 will implement it in the backend.

### Visitor Stubs (all implementors)

Every `ScriptOpVisitorI` implementor was updated to compile with the new method:

- `ScriptOpVisitor` (gbkt-backend-gbdk): `error("implement in Plan 07")` stub — loudly fails if called before Plan 07 wires the real implementation
- `PuzzleAdvancedTest.kt`, `PuzzleCoreTest.kt` (gbkt-ir tests): `"MoveMetasprite(id)"` String stubs
- `ActorPoolIRTest.kt` (gbkt-ir tests): Two anonymous `Unit` stubs (both occurrences via replace_all)
- `IRHierarchyTest.kt` (gbkt-core tests): `"MoveMetasprite(id)"` String stub

### moveMetasprite DSL Function (gbkt-lang)

Added top-level `fun moveMetasprite(ref: MetaspriteRef)` to `MetaspriteBuilder.kt`. Emits `MoveMetasprite(ref.id)` via `ScriptBuilderContext.current?.emit()` — the same pattern used by `ActorRef.moveTo()`.

## Verification

```
./gradlew :gbkt-ir:test :gbkt-lang:test :gbkt-backend-gbdk:compileKotlin — all GREEN
```

- `MoveMetasprite("elephant").metaspriteId == "elephant"` — PASS
- `MoveMetasprite("elephant").sourceLocation == null` — PASS
- `MoveMetasprite.accept` dispatches to `visitMoveMetasprite` — PASS
- `moveMetasprite(foo)` in frame block emits `MoveMetasprite("foo")` op — PASS

## Deviations from Plan

None — plan executed exactly as written.

## TDD Gate Compliance

RED/GREEN/REFACTOR gates completed for both tasks:
1. `test(10-04): add failing tests for MoveMetasprite ScriptOp (TDD RED)` — 2b524c90
2. `feat(10-04): add MoveMetasprite ScriptOp + visitMoveMetasprite visitor hook (TDD GREEN)` — 7fa3b57b
3. `test(10-04): add failing test for moveMetasprite DSL function (TDD RED)` — 8a681623
4. `feat(10-04): add moveMetasprite DSL function in MetaspriteBuilder (TDD GREEN)` — c75b797c

No REFACTOR phase needed (no cleanup required).

## Self-Check: PASSED

- [x] `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt` contains `MoveMetasprite` — FOUND
- [x] `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt` contains `visitMoveMetasprite` — FOUND
- [x] `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt` contains `moveMetasprite` — FOUND
- [x] Commits 2b524c90, 7fa3b57b, 8a681623, c75b797c — all exist in git log
