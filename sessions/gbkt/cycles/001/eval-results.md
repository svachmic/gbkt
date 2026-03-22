# Cycle 001 - Evaluation Results

## Test status: BUILD SUCCESSFUL (all pass)

## Commits: 13 (11 fixes + 2 refactors)
## Files changed: 17

## What was fixed
- F-001: PlatformerVisitor jump condition always true (`_plat_grounded || 1`)
- F-002: RPG builders skip ScriptBuilderContext.with() in 8 callback methods across 5 files
- F-003: Puzzle undo system missing save function
- F-004: Four analysis passes skip PoolForEachActive body in tree walks
- F-005: ConstantFoldingPass.foldExpr doesn't recurse into non-BinaryExpr nodes
- F-006: BitwiseOptimizationPass applies unsigned-only rewrites to signed types
- F-007: BackendRegistry race condition in read methods
- F-008: checkFadeWithoutAudioMixer only checks top-level ops
- F-010: Smooth-follow camera snaps wrong on leftward/upward movement
- F-011: Collectible collision only checks X axis
- F-013: VarDelegate.provideDelegate silently no-ops outside game {} block

## Refactoring
- Extracted shared ScriptOp traversal utilities (net -116 lines across 4 analysis passes)
- Added ScriptBuilder.buildOps() factory replacing 9 occurrences of 3-line idiom

## What remains (deferred to ideas.md)
- F-009: Mutable instance state in analysis passes (low risk)
- F-012: Unchecked casts in SportVisitor
- F-014: Diagnostic ID reuse in SemanticValidationPass
- F-015: Redundant unsigned bounds check in ArrayVar.exists
- F-016: GBCColor constant precision loss
- F-017-F-021: Test coverage gaps

## End commit: b44842323c351f94c2d0898ca516453975db9af9
