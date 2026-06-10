# Cycle 004 - Evaluation Results

## Test status: BUILD SUCCESSFUL (all pass)

## Commits: 11 (7 fixes + 4 refactors)
## Files changed: 10

## What was fixed
- F-061: ConstantFoldingPass preserves folded sub-expressions on div-by-zero
- F-062: transformExprsInOp handles DialogSay segments with Expr nodes
- F-063: String table bank allocation starts from bank 1 (not HOME bank 0)
- F-064: ScriptOpInterpreter executes deathCallbackOps in PoolDestroyActor
- F-065: Palette precision check fixed — no more false positives for all non-zero colors
- F-067: BitwiseOptimizationPass handles left-constant MUL (4 * x → x << 2)
- F-069: SimulationContextV2.runUntil executes at least one frame before predicate check

## Skipped
- F-068: ForOp codegen hardcodes INT8 in gbkt-backend-gbdk (out of scope)
- F-070: False finding — DialogDef has no defaultSegments field
- F-071: Design gap requiring new DSL API, not a minimal fix

## Refactoring
- mapExprChildren() shared expression-child recursion helper
- applyAssignOp() extracted from duplicated 9-branch when blocks
- collectDuplicates<T>() generic helper in SemanticValidationPass
- sanitizePuzzleId() extracted from 5 inline occurrences

## End commit: f0d119fad435026ac27374252c30fca9553cf8a9
