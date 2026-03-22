# Green Patch Summary

## Fixed (11/11)

### [F-001] PlatformerVisitor jump condition always true
- **File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
- **Fix:** Changed `_plat_grounded || 1` (always true) to `_plat_grounded || (_plat_coyote_timer > 0)` so jumping works both when grounded and during coyote time.

### [F-002] RPG builders skip ScriptBuilderContext.with()
- **Files:** `CharacterBuilder.kt`, `StatusEffectBuilder.kt`, `AtbCombatBuilder.kt`, `CombatHookBuilder.kt`, `AbilityBuilder.kt` (all in `gbkt-genre-rpg/src/.../rpg/dsl/`)
- **Fix:** Changed `scriptBuilder.block()` to `scriptBuilder.runWith(block)` in 8 callback methods. `runWith()` wraps in `ScriptBuilderContext.with()` so operator DSL works inside RPG callbacks.

### [F-003] Puzzle undo system has no save function
- **File:** `gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt`
- **Fix:** Added `buildSave()` generating `puzzle_save_<id>()` which snapshots the grid onto the undo stack before each push. Also registered it in `buildFunctions()` alongside `buildUndo()`.

### [F-004] Analysis passes skip PoolForEachActive body in tree walks
- **Files:** `ConstantFoldingPass.kt`, `BitwiseOptimizationPass.kt`, `DeadCodeEliminationPass.kt`, `BankingAnalysisPass.kt` (all in `gbkt-analysis/src/.../passes/`)
- **Fix:** Added `is PoolForEachActive -> op.copy(body = ...)` / `collectNavigations(op.body, out)` to the `when` expressions so code inside pool iteration blocks is no longer skipped.

### [F-005] ConstantFoldingPass.foldExpr doesn't recurse into non-BinaryExpr nodes
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt`
- **Fix:** Rewrote `foldExpr()` from an early-return-on-non-BinaryExpr to a `when` expression that recurses into `UnaryExpr`, `TernaryExpr`, `ArrayAccessExpr`, `CallExpr`, and `CastExpr`.

### [F-006] BitwiseOptimizationPass applies unsigned-only rewrites to signed types
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt`
- **Fix:** Added a `varTypes` map built from `GameIR.variables` and an `isMaybeSigned()` helper. DIV->SHR and MOD->AND rewrites are now skipped for known signed variables (I8, I16).

### [F-007] BackendRegistry has race condition in read methods
- **File:** `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/BackendRegistry.kt`
- **Fix:** Added `@Synchronized` to `forId()`, `forTarget()`, `all()`, and `supportedTargets()` so reads from the `backends` MutableMap are protected against concurrent `register()` calls.

### [F-008] checkFadeWithoutAudioMixer only checks top-level ops
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt`
- **Fix:** Added a recursive `collectAllOps()` helper that walks into IfOp, WhileOp, ForOp, FadeOp, DialogChoice, and PoolForEachActive bodies. `checkFadeWithoutAudioMixer` now uses it instead of flat iteration.

### [F-010] Smooth-follow camera snaps wrong on leftward/upward movement
- **File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
- **Fix:** Added direction checks inside the dead-zone correction: subtract dead zone when target > camera (rightward/downward), add dead zone when target < camera (leftward/upward).

### [F-011] Collectible collision only checks X axis
- **File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
- **Fix:** Extended the AABB collision condition with Y axis check (`player_y >= pickup_y && player_y < pickup_y + 8`).

### [F-013] VarDelegate.provideDelegate silently no-ops outside game {} block
- **File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt`
- **Fix:** Changed `GameBuilderContext.current?.registerVariable(...)` to throw an error when `current` is null. Also fixed the same pattern in `ArrayDelegate`. Variables declared outside `game {}` now fail fast.

## Skipped (0/11)

None.

## Final Test Status

**BUILD SUCCESSFUL** -- all 168 tasks passed (0 failures).

Command: `./gradlew test`
