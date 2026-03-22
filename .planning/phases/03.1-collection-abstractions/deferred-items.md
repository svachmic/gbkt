# Deferred Items - Phase 03.1

## Pre-existing Detekt Warnings (out of scope)

These issues existed before Phase 03.1 work. Not caused by collection IR changes.
Verified via `git stash` + detekt run before changes were applied.

| File | Issue | Rule |
|------|-------|------|
| `test/SimulationContextV2.kt:96` | Use check() or error() | UseCheckOrError |
| `dsl/v2/DslMarkers.kt:17` | File name mismatch | MatchingDeclarationName |
| `dsl/v2/Errors.kt:18` | File name mismatch | MatchingDeclarationName |
| `dsl/v2/ExprBuilder.kt:7` | 35 functions in file (threshold 25) | TooManyFunctions |
| `dsl/v2/ScriptBuilder.kt:54` | 39 functions in class (threshold 20) | TooManyFunctions |
| `test/ScriptOpInterpreter.kt:292` | executeMathOp complexity 18 (threshold 15) | CyclomaticComplexMethod |

## Action
Address in a dedicated cleanup pass, not during Phase 03.1 execution.
