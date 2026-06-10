# Green Team Patch Report - Cycle 004

## Fixed (7 of 10)

### [F-061] ConstantFoldingPass returns stale BinaryExpr on division by zero
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt`
- **Fix:** Changed the `else` branch (when `evalBinaryOp` returns null) to return `expr.copy(left = foldedLeft, right = foldedRight)` instead of the original `expr`, preserving already-folded sub-expressions.
- **Commit:** `c734e3a`

### [F-062] transformExprsInOp skips DialogSay segments containing Expr nodes
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt`
- **Fix:** Added a `DialogSay` case to `transformExprsInOp` that maps over segments and transforms `DialogExprSegment.expr` values. Added imports for `DialogSay`, `DialogExprSegment`, `DialogTextSegment`.
- **Commit:** `bf1c74d`

### [F-063] BankAllocator allocates string tables starting from bank 0 (HOME)
- **File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt`
- **Fix:** Changed `for (bank in 0..maxBanks)` to `for (bank in 1..maxBanks)` to skip HOME bank.
- **Commit:** `0f96553`

### [F-064] ScriptOpInterpreter ignores deathCallbackOps in PoolDestroyActor
- **File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt`
- **Fix:** Added `op.deathCallbackOps.forEach { executeOp(it) }` before marking the pool slot inactive, matching the C backend behavior and the IR documentation.
- **Commit:** `15c9ab7`

### [F-065] checkPalettePrecision false-positives for all non-zero RGB555 colors
- **Files:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt`, `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPassTest.kt`
- **Fix:** Replaced the broken comparison (`(r5<<3)|(r5>>2)` vs `r5<<3`) with a correct round-trip check: expand RGB555 to RGB888 then re-quantize back to RGB555 and compare with the original. Updated the test that was asserting the false-positive behavior.
- **Commit:** `a527115`

### [F-067] BitwiseOptimizationPass only checks right operand for power-of-2
- **Files:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt`, `gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPassTest.kt`
- **Fix:** Added left-operand power-of-2 check for commutative MUL operations. `4 * x` now rewrites to `x << 2`. DIV and MOD remain right-operand only (non-commutative). Updated test to expect the new behavior.
- **Commit:** `3cf5acd`

### [F-069] SimulationContextV2.runUntil evaluates predicate before first frame
- **Files:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt`, `gbkt-core/src/test/kotlin/io/github/gbkt/core/test/SimulationContextV2Test.kt`
- **Fix:** Changed `while` loop to `do-while` so at least one frame executes before the predicate is checked, preventing vacuous test passes. Updated the test that expected zero frames.
- **Commit:** `ebff1b9`

## Skipped (3 of 10)

### [F-068] ArrayVar helpers use untyped loop variable that may be INT8 for arrays >127
- **Reason:** The `ForOp` IR node has no type field, and the GBDK backend (`ScriptOpVisitor.visitForOp`) hardcodes `CI8` for all loop variables. Fixing this requires either adding a type field to `ForOp` (affecting many callers across all modules) or changing the backend codegen. The backend module (`gbkt-backend-gbdk/src/`) is outside the allowed scope for this patch. Registering the variable via `GameBuilder.registerVariable()` would not help since the codegen does not look up variable types from the game IR for `ForOp` loop variables.

### [F-070] transformExprsInGame does not transform dialog Expr segments in game.dialogs
- **Reason:** The finding references `game.dialogs[*].defaultSegments` but `DialogDef` has no `defaultSegments` field. `DialogDef` is a layout/configuration-only data class (text speed, border style, box dimensions, font mode) with zero expression fields. Dialog content with `DialogExprSegment` lives in `DialogSay` ScriptOps, which was already fixed in F-062. This finding is based on an incorrect assumption about the `DialogDef` structure.

### [F-071] SceneBuilder.build() always sets actorIds to emptyList()
- **Reason:** This is a design gap, not a bug with a minimal fix. `SceneBuilder` has no mechanism to collect actor IDs, actors don't reference scenes, and `GameBuilder.build()` doesn't infer actor-scene relationships. Properly fixing this requires either adding explicit `actors()` DSL API to `SceneBuilder` or building inference logic to scan ScriptOps for actor references -- both are significant new features rather than bug fixes.

## Final Test Status

```
BUILD SUCCESSFUL in 43s
168 actionable tasks: 56 executed, 112 up-to-date
```

All tests pass across all modules (gbkt-ir, gbkt-lang, gbkt-engine, gbkt-core, gbkt-backend-api, gbkt-backend-gbdk, gbkt-analysis, gbkt-genre-rpg, gbkt-genre-platformer, gbkt-genre-puzzle, gbkt-genre-sport, gbkt-test, gbkt-examples).
