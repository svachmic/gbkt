# Cycle 002 - Evaluation Results

## Test status: BUILD SUCCESSFUL (all pass)

## Commits: 14 (11 fixes + 3 refactors)
## Files changed: 9

## What was fixed
- F-022: Puzzle grid cell-type lookup corrected from `y + x` to `y * width + x`
- F-023: Coyote timer now resets while grounded, expires while airborne
- F-024: Ladder climb uses pointer params so position writes take effect in C
- F-025: BitwiseOptimizationPass recurses into compound expression types
- F-026: ConstantFoldingPass evaluates UnaryExpr with Literal operand
- F-027: Ball sport dx/dy changed from CU8 to CI8 for negative movement
- F-028: Racing waypoint requires proximity check before advancing
- F-029: ConstraintCheckPass includes actor state + engine overhead in WRAM
- F-030: transformExprsInGame walks systems, zones, collision rules, pools
- F-031: Match timer uses CU16 to avoid overflow with long halves
- F-032: Push block now copies cell to destination and clears source

## Refactoring
- Extracted `forEachNestedOpList` helper in ScriptOpTraversal (DRY for nested op enumeration)
- Extracted `sanitizeCId()` utility for C identifier sanitization (replaced 8 inline occurrences)
- Extracted `buildDeadZoneCheck` in PlatformerVisitor (collapsed 106 lines of duplicated axis logic to 14)

## What remains (deferred to ideas.md)
- F-033: Tournament standings sort misses losses array
- F-034: Match-3 detection doesn't clear matched cells
- F-035: Puzzle gravity only single-pass per call
- Plus cycle 1 deferred items and all test coverage gaps

## End commit: 9fe07a74513d6df9fd02f4728de738a3eabefe68
