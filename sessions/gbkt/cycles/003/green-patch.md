# Green Patch Report -- Cycle 003

**Branch:** `autoresearch/improve`
**Date:** 2026-03-22
**Final test status:** BUILD SUCCESSFUL (168 actionable tasks, all tests pass)

## Fixed (13/13)

| ID | Summary | Files Changed |
|----|---------|---------------|
| F-041 | `transformExprsInOp` now handles `PoolSpawnActor` (x/y), `PoolDestroyActor` (slotExpr + deathCallbackOps), and `GotoXYOp` (x/y) | `ScriptOpTraversal.kt` |
| F-042 | `forEachNestedOpList` now walks `PoolDestroyActor.deathCallbackOps` | `ScriptOpTraversal.kt` |
| F-043 | `transformExprsInGame` now transforms menu item bodies and all puzzle event handler actions (switch, door, pressure plate, timed block, trigger) | `ScriptOpTraversal.kt` |
| F-044 | `transformExprsInZone` now transforms zone object `onInteract` scripts plus type-specific callbacks (`onLit`/`onExtinguished` for sconces, `onActivate`/`onDeactivate` for levers) | `ScriptOpTraversal.kt` |
| F-045 | `OAMAllocationPass` and `ConstraintCheckPass` now compute total OAM entries by summing per-actor tile counts (`spriteTileCounts`) instead of comparing actor count against `maxSprites` | `OAMAllocationPass.kt`, `ConstraintCheckPass.kt`, `ConstraintCheckPassTest.kt` |
| F-046 | `PoParser.extractQuotedString` escape replacement order fixed: `\\` is processed first via placeholder to prevent `\\n` from being incorrectly parsed as newline | `PoParser.kt` |
| F-047 | `buildTransitionGraph` now collects `NavigateTo` from collision rules, zone callbacks, zone object interactions, menu item bodies, actor pool death callbacks, exploration system callbacks, combat engine hooks, and puzzle event handlers | `ScriptOpTraversal.kt` |
| F-048 | `transformExprsInSystem` now handles `CombatEngineSystem` (onVictoryCondition, onDefeatCondition, onVictoryOps, onDefeatOps, combatHooks) | `ScriptOpTraversal.kt` |
| F-049 | `checkFadeWithoutAudioMixer` now searches all game op sources (zones, collision rules, actor pools, menus, puzzle handlers, exploration, combat) | `SemanticValidationPass.kt` |
| F-050 | `ConstantFoldingPass` now eliminates dead `TernaryExpr` branches when condition folds to `Literal(0)` or `Literal(nonzero)` | `ConstantFoldingPass.kt` |
| F-051 | `isMaybeSigned` now recursively checks compound expressions (BinaryExpr, UnaryExpr, TernaryExpr, CastExpr) for signed VarRefs, preventing incorrect div-to-shift rewrites | `BitwiseOptimizationPass.kt` |
| F-052 | `executeAssign` now uses `getOrPut` to initialize actor position on first `.x`/`.y` property assignment, instead of silently skipping when actor not in `actorPositions` | `ScriptOpInterpreter.kt` |
| F-053 | `BackendRegistry` uses `ThreadLocal` per-thread state so `clear()` only affects the calling thread, preventing cross-test interference during parallel execution | `BackendRegistry.kt` |

## Skipped

None -- all 13 issues were fixed successfully.

## Notes

- F-045 required updating test helper `makeInventory` in `ConstraintCheckPassTest.kt` to populate `spriteTileCounts` (1 entry per actor simulating 8x8 sprites), since the pass now uses tile counts instead of actor counts.
- F-047 adds non-scene navigations as edges from every scene in the graph, ensuring BFS reachability is conservative (no false "unreachable" reports for scenes targeted from non-scene contexts).
- F-053 preserves the existing `clear()` API but makes it thread-scoped. Global ServiceLoader discovery is cached once and copied into each thread's local state on first access.
