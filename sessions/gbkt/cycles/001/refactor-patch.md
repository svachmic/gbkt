## Changes: 2

### Commit 1: Extract shared ScriptOp traversal utilities from analysis passes

**Files changed:** 6 (1 new, 5 modified)
**Net line delta:** -116 lines

Created `ScriptOpTraversal.kt` in `gbkt-analysis/src/main/kotlin/.../passes/` with shared utilities:

- `buildTransitionGraph(game)` + `collectNavigations(ops, out)` -- duplicated identically in `BankingAnalysisPass` and `DeadCodeEliminationPass`
- `bfsReachable(start, graph)` -- duplicated in `DeadCodeEliminationPass` (extracted for reuse)
- `transformExprsInOp(op, transformExpr, transformOps)` + `transformExprsInOps` + `transformExprsInGame` + `transformExprsInScene` -- the ScriptOp-to-ScriptOp expression transformation pattern duplicated identically (35 lines each) in `BitwiseOptimizationPass` and `ConstantFoldingPass`
- `collectAllOps(ops)` -- recursive ScriptOp flattener duplicated in `SemanticValidationPass`

Updated passes:
- `BankingAnalysisPass.kt` -- removed `buildTransitionGraph`, `collectNavigations`; calls shared functions
- `DeadCodeEliminationPass.kt` -- removed `buildTransitionGraph`, `collectNavigations`, `bfsReachable`; calls shared functions
- `BitwiseOptimizationPass.kt` -- removed `optimizeGame`, `optimizeScene`, `optimizeOps`, `optimizeOp`; uses `transformExprsInGame(game, ::optimizeExpr)`
- `ConstantFoldingPass.kt` -- removed `foldGame`, `foldScene`, `foldScriptOps`, `foldOp`; uses `transformExprsInGame(game, ::foldExpr)`
- `SemanticValidationPass.kt` -- removed local `collectAllOps` (23 lines) and replaced `countRawOps` (24 lines) with one-liner `collectAllOps(ops).count { it is RawOp }`

### Commit 2: Add ScriptBuilder.buildOps() and replace 9 three-line idioms

**Files changed:** 7 (7 modified)
**Net line delta:** -1 line (9 three-line blocks replaced with one-liners; companion object added)

Added `ScriptBuilder.buildOps(block)` companion factory that encapsulates the recurring pattern:
```kotlin
val sb = ScriptBuilder()
sb.runWith(block)
return sb.build()
```

Updated 9 call sites across 6 RPG builder files:
- `AtbCombatBuilder.kt` -- `onVictory`, `onDefeat`
- `CharacterBuilder.kt` -- `onLevelUp`
- `CombatHookBuilder.kt` -- `record`
- `SimpleBattleBuilder.kt` -- `onVictory`, `onDefeat`
- `AbilityBuilder.kt` -- `execute`
- `StatusEffectBuilder.kt` -- `onTrigger`, `onStackApplied`, `onStackRemoved`

### What was NOT changed (and why)

- **Operator overloads in `VariableBuilders.kt`**: The `isAbove`/`isBelow`/etc. overloads for `AssignableVar` are thin delegations needed for DSL ergonomics -- no refactoring possible without losing the API.
- **RPG delegate classes** (`AbilityDelegate`, `StatusEffectDelegate`, `CurrencyDelegate`, `ClassDelegate`): Structurally similar but each delegates to a different `GameBuilder` method with different types. Extracting a generic base would add complexity without meaningful simplification.
- **Long codegen methods** (`PlatformerVisitor.buildPhysicsUpdateFunction`): 167 lines, but the project explicitly documents that C codegen methods are intentionally long -- one IR node maps to C output.
- **`BackendRegistry` discover() guard calls**: Each public method calls `discover()` first. This is idiomatic guard-before-return, not duplication.
