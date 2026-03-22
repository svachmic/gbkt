# Red Team Findings - Cycle 003

## Critical

### [F-041] transformExprsInOp misses PoolSpawnActor, PoolDestroyActor, and GotoXYOp Expr fields
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:109-153
- Issue: The `transformExprsInOp` function falls through to `else -> op` for `PoolSpawnActor` (has `x: Expr`, `y: Expr`), `PoolDestroyActor` (has `slotExpr: Expr`), and `GotoXYOp` (has `x: Expr`, `y: Expr`). These Expr fields are never visited by ConstantFoldingPass or BitwiseOptimizationPass.
- Impact: Constant expressions inside pool spawn coordinates, pool destroy slot indices, and gotoxy positions will not be folded or bitwise-optimized. More critically, any analysis pass that relies on `transformExprsInOp` for correctness (not just optimization) will silently skip these ops.

### [F-042] forEachNestedOpList misses PoolDestroyActor.deathCallbackOps
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:37-54
- Issue: `forEachNestedOpList` does not handle `PoolDestroyActor`, which has a `deathCallbackOps: List<ScriptOp>` field. This means `collectNavigations`, `collectAllOps`, and the dead-code elimination BFS traversal will never descend into death callback script ops.
- Impact: If a `NavigateTo` is inside a pool destroy death callback, the target scene will be incorrectly reported as unreachable by `DeadCodeEliminationPass`. Any analysis or transformation that relies on `forEachNestedOpList` will silently miss ops inside death callbacks.

### [F-043] transformExprsInGame skips menu item bodies, puzzle object handlers, and CombatEngineSystem hooks
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:163-180
- Issue: `transformExprsInGame` does not transform expressions in: (a) `game.menus[*].items[*].body: List<ScriptOp>` (MenuItemDef), (b) `game.puzzleObjects[*].handlers[*].actions: List<ScriptOp>` (PuzzleEventHandler), (c) CombatEngineSystem fields (`onVictoryCondition`, `onDefeatCondition`, `onVictoryOps`, `onDefeatOps`, `combatHooks` values). The doc comment at line 166 claims these are covered but the implementation does not visit them.
- Impact: Expressions inside menu selections, puzzle event callbacks, and combat engine victory/defeat/hook scripts bypass constant folding and bitwise optimization entirely. The comment is misleading.

### [F-044] transformExprsInZone skips zone object onInteract scripts
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:219-229
- Issue: `transformExprsInZone` transforms `zone.onEnter` and `zone.onExit` but not `zone.objects[*].onInteract: List<ScriptOp>` (ZoneObjectIR). Zone objects (chests, signs, levers, doors) can contain complex script logic with expressions.
- Impact: Expressions in zone object interact handlers are never constant-folded or bitwise-optimized.

## High

### [F-045] OAMAllocationPass and ConstraintCheckPass compare actor count against OAM entry limit
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPass.kt:51 and gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt:44
- Issue: Both passes compare `game.actors.size` (or `inventory.totalActors`) against `profile.sprites.maxSprites`, which is documented as "Maximum number of hardware sprites (OAM entries)". On Game Boy, a 16x16 actor requires 4 OAM entries. The `OAMAllocationPass` correctly computes `nextOamSlot` (cumulative tile count) but only uses it for assignment — the hard overflow check at line 51 still uses actor count. The `ConstraintCheckPass` has the same problem. A game with 11 actors using 16x16 sprites needs 44 OAM entries but would pass the 40-OAM-entry check.
- Impact: Games can silently exceed the hardware OAM limit, causing sprite corruption or invisible sprites on real hardware.

### [F-046] PoParser extractQuotedString processes escape sequences in wrong order
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt:254-258
- Issue: Escape sequence replacement applies `\\n` -> newline before `\\\\` -> backslash. If a PO string contains a literal `\\n` (two backslashes followed by 'n', meaning escaped backslash + n), the `\\n` substring matches the first replace rule and is incorrectly converted to a newline. The `\\` -> `\` replacement should happen first, or processing should be character-by-character.
- Impact: PO localization files containing literal backslashes before 'n', 't', or '"' characters will be parsed incorrectly, producing corrupted game strings.

### [F-047] buildTransitionGraph only collects NavigateTo from scene ops
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:60-76
- Issue: `buildTransitionGraph` only walks `scene.enterOps`, `scene.frameOps`, and `scene.exitOps`. Scene transitions can also occur in: collision rule callbacks (`game.collisionRules[*].onCollide`), actor pool death callbacks, zone enter/exit/object-interact callbacks, exploration system step/interact/blocked callbacks, menu item body ops, combat engine hooks, and puzzle event handlers.
- Impact: `DeadCodeEliminationPass` and `BankingAnalysisPass` (which uses transition graph for locality) may incorrectly classify scenes as unreachable or suboptimally pack banks due to incomplete transition information.

### [F-048] transformExprsInSystem only handles ExplorationSystem, skips CombatEngineSystem
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:195-217
- Issue: `transformExprsInSystem` has an `else -> system` fallback that passes through all non-ExplorationSystem types unchanged. `CombatEngineSystem` has 5 `List<ScriptOp>` fields and a `Map<CombatHookPoint, List<ScriptOp>>` that all contain Expr nodes that will never be folded or optimized. The comment says "Only ExplorationSystem has ScriptOp lists" which is false.
- Impact: Combat engine victory/defeat conditions, action ops, and hook scripts bypass all expression optimization passes.

## Medium

### [F-049] checkFadeWithoutAudioMixer only searches scene ops for MusicPlay/MusicStop
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt:328-330
- Issue: The validation only collects ops from `game.scenes.flatMap { it.enterOps + it.frameOps + it.exitOps }`. MusicPlay/MusicStop ops can appear in zone callbacks, collision rules, exploration system callbacks, menu item bodies, combat hooks, and puzzle event handlers.
- Impact: Music ops with fade parameters outside scene scripts will silently fall back to instant play/stop at runtime without any analysis-time warning.

### [F-050] ConstantFoldingPass does not simplify TernaryExpr when condition is a Literal
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:113-118
- Issue: When `foldExpr` processes a `TernaryExpr`, it recursively folds the condition, thenExpr, and elseExpr but does not check if the folded condition is a `Literal`. If `condition` folds to `Literal(1)`, the entire ternary should be replaced with `thenExpr`; if `Literal(0)`, with `elseExpr`.
- Impact: Missed optimization opportunity. Ternary expressions with compile-time constant conditions remain in the IR and generate unnecessary branching in C output. Not a correctness bug but unnecessary code size on a constrained Game Boy ROM.

### [F-051] BitwiseOptimizationPass.isMaybeSigned only checks direct VarRef, misses compound expressions
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:161-173
- Issue: `isMaybeSigned` returns `false` for any expression that is not a plain `VarRef`. A `BinaryExpr(VarRef("signed_var"), ADD, Literal(1))` where `signed_var` is I8/I16 would be treated as unsigned, potentially allowing incorrect div-to-shift or mod-to-mask rewrites on signed values.
- Impact: Bitwise optimization could produce incorrect results for signed arithmetic wrapped in compound expressions (e.g., `(signedVar + offset) / 8` would be rewritten to `(signedVar + offset) >> 3`, which gives wrong results for negative values in C).

### [F-052] ScriptOpInterpreter.executeAssign only syncs actorPositions if actor already exists in map
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt:396-406
- Issue: When assigning to a target like `"actor.x"`, the code only updates `actorPositions` if `actorPositions[actorId] != null`. If the actor has not been initialized via `SetPosition` or `initActors`, a direct assignment to `"actor.x"` updates the `variables` map but leaves `actorPositions` empty for that actor. Subsequent `evaluateExpr` for `PropertyAccessExpr` will check `actorPositions` first and miss the update.
- Impact: In simulation tests, directly assigning to actor position properties before the actor is positioned via SetPosition will cause inconsistent state between `variables` and `actorPositions` maps, leading to incorrect PropertyAccessExpr evaluation.

### [F-053] BackendRegistry singleton mutable state not safe for parallel tests
- Location: gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/BackendRegistry.kt:19-20
- Issue: `BackendRegistry` is a Kotlin `object` (singleton) with mutable `backends` map and `discovered` flag. The `clear()` method resets state for testing, but if tests run in parallel on the same classloader, one test calling `clear()` can wipe another test's registered backends mid-execution.
- Impact: Flaky test failures in parallel test execution. One test clearing the registry can cause another test to get `null` from `forId()`.

## Low

### [F-054] ResourceInventoryPass computes spriteTileCounts but OAM checks never use it
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPass.kt:35-43
- Issue: `ResourceInventoryPass` carefully computes `spriteTileCounts` (mapping actor ID to tile count based on sprite width/height), but neither `OAMAllocationPass` nor `ConstraintCheckPass` use this map to validate against the OAM entry limit. The tile counts are only used by `VRAMLayoutPass`. This is related to F-045 — the infrastructure to fix the bug already exists but is unused.
- Impact: Wasted computation and a misleading data structure that appears to support OAM validation but does not.

### [F-055] SourceMap.findKotlinLocation uses linear scan
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/SourceMap.kt:153-155
- Issue: `findKotlinLocation(cLine: Int)` uses `mappings.find { it.cLine == cLine }` which is O(n) per lookup. For large generated C files with thousands of lines, this could be slow when used in tight loops (e.g., debugging tools).
- Impact: Performance degradation in source-map lookup for large projects. Not a correctness issue.

## Test coverage gaps

### [F-056] No test for transformExprsInOp handling of PoolSpawnActor, PoolDestroyActor, GotoXYOp
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPassTest.kt
- Issue: Constant folding and bitwise optimization pass tests do not include test cases for `PoolSpawnActor`, `PoolDestroyActor`, or `GotoXYOp` with foldable Expr fields. These would immediately expose F-041.
- Impact: The missing transformation is silently untested.

### [F-057] No test for transformExprsInGame on menus, puzzleObjects, or CombatEngineSystem
- Location: gbkt-analysis/src/test/
- Issue: No integration test verifies that constant folding or bitwise optimization reaches into `MenuItemDef.body`, `PuzzleEventHandler.actions`, or `CombatEngineSystem` script ops. This would expose F-043.
- Impact: Expression optimization silently skips these locations without test detection.

### [F-058] No test for PoParser escape sequence ordering with literal backslash
- Location: gbkt-core/src/test/
- Issue: No test exercises a PO string containing `\\n` (literal backslash + n) to verify it is not incorrectly parsed as a newline. A test like `msgstr "path\\name"` would expose F-046.
- Impact: Localization corruption in strings with backslash characters goes undetected.

### [F-059] No test for OAMAllocationPass with multi-tile sprites exceeding OAM limit
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPassTest.kt
- Issue: No test creates actors with 16x16 sprites (4 OAM entries each) where the actor count passes the 40-OAM check but total OAM entries exceed 40. This would expose F-045.
- Impact: Multi-tile sprite OAM overflow silently passes validation without test detection.

### [F-060] No test for buildTransitionGraph collecting NavigateTo from non-scene locations
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/DeadCodeEliminationPassTest.kt
- Issue: No test verifies that scenes reachable only through collision rule callbacks, zone interactions, or combat hooks are correctly included in the reachability graph. This would expose F-047.
- Impact: False positive dead-scene warnings in real games using navigation from non-scene contexts.
