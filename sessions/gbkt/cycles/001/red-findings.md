# Red Team Findings - Cycle 001

## Critical

### [F-001] PlatformerVisitor jump condition is always true (`_plat_grounded || 1`)
- Location: gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:192-196
- Issue: The generated C code for the jump input check uses `(_plat_grounded || 1)` as part of the condition. The expression `x || 1` is always truthy in C, making the grounded check meaningless. The intent was likely `_plat_grounded == 1` or just `_plat_grounded`. The generated condition `(_plat_grounded || 1) && (_plat_coyote_timer > 0)` means the player can only jump when the coyote timer is active, but never from a standing position on the ground (where coyote_timer would be 0).
- Impact: Platformer physics are fundamentally broken -- the player can never jump from a grounded state. Jump only works during the coyote time window after leaving a platform, which is the exact opposite of the intended behavior.

### [F-002] RPG builders skip ScriptBuilderContext.with() -- operator-based DSL broken inside callbacks
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CharacterBuilder.kt:125-128
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/StatusEffectBuilder.kt:138-139, 214-215, 225-226
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/AtbCombatBuilder.kt:176-177, 189-190
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CombatHookBuilder.kt:93-94
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/AbilityBuilder.kt:152-153
- Issue: These builders create a `ScriptBuilder()` and call `scriptBuilder.block()` directly, without wrapping in `ScriptBuilderContext.with(scriptBuilder) { ... }`. The `ScriptBuilderContext` thread-local is required for operator-based DSL features (e.g., `score += 10`, `bricks[i] = 0`, `arrayVar.fill()`) to emit ops into the correct builder. Without it, `ScriptBuilderContext.current` is null, causing `error("... called outside a ScriptBuilder block")` at runtime. The correct pattern (used by `SceneBuilder.enter/frame/exit` and `SimpleBattleBuilder.onVictory`) is `ScriptBuilderContext.with(builder) { builder.block() }` or `scriptBuilder.runWith(block)`.
- Impact: Any DSL user who uses operator-based variable assignment, array bracket writes, or collection helpers inside `onLevelUp {}`, `execute {}` (ability), `onApply/onRemove/onTick {}` (status effect), `onTurnStart/onTurnEnd {}` (ATB), or `onRoundStart {}` (combat hooks) will get a runtime crash. Only explicit `assign()`/`arrayAssign()` method calls work, undermining the DSL's type-safe operator API.

## High

### [F-003] Puzzle undo system has no save function -- only restore exists
- Location: gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt:145-151 (buildFunctions), 768-816 (buildUndo)
- Issue: The block-push puzzle mode generates `puzzle_undo_<id>()` to restore grid state from an undo stack (pops `_puzzle_undo_top` and copies from `_puzzle_undo_stack`), but there is no corresponding save/snapshot function that pushes the current grid state onto the undo stack before a push. The `buildPushBlock()` function (line 700-762) does not save to the undo stack either. Variable `_puzzle_undo_top` is allocated but never incremented.
- Impact: The undo feature for Sokoban-style puzzles is non-functional. Calling `puzzle_undo_<id>()` reads from an uninitialized undo stack (all zeros), corrupting the grid state. The feature is wired up in DSL and codegen but fundamentally incomplete.

### [F-004] Analysis passes skip PoolForEachActive body in tree walks
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:128-130 (else branch)
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:129-131 (else branch)
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/DeadCodeEliminationPass.kt:136-138 (else branch in collectNavigations)
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt:160-162 (else branch in collectNavigations)
- Issue: `PoolForEachActive` is a `ScriptOp` that contains a `body: List<ScriptOp>` field (nested script operations), but all four analysis passes that recursively walk ScriptOps fall through to the `else` branch, ignoring `PoolForEachActive.body` entirely. The passes explicitly handle `IfOp`, `WhileOp`, `ForOp`, `FadeOp`, and `DialogChoice` nested bodies but miss `PoolForEachActive`.
- Impact: Constant folding, bitwise optimization, dead code detection, and banking analysis all miss any expressions/navigations/ops inside `PoolForEachActive` blocks. Games using actor pools with per-slot logic (e.g., bullet-hell patterns) will have unoptimized and unanalyzed code in those blocks, and scene transitions inside pool iteration loops will not be detected for reachability analysis.

### [F-005] ConstantFoldingPass.foldExpr does not recurse into UnaryExpr, TernaryExpr, or ArrayAccessExpr
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:145-146
- Issue: `foldExpr()` returns immediately for any `Expr` that is not `BinaryExpr` (`if (expr !is BinaryExpr) return expr`). This means sub-expressions inside `UnaryExpr(op, operand)`, `TernaryExpr(condition, then, else)`, `ArrayAccessExpr(array, index)`, and `CallExpr(name, args)` are never folded. For example, `-(-5)` or `arr[2 + 3]` would not be folded even though they contain foldable `BinaryExpr` children.
- Impact: Missed optimization opportunities. Expressions that are children of non-BinaryExpr nodes are never constant-folded, leaving more work for the C compiler. While the C compiler will likely fold these, the optimization report will undercount transformations and the IR will be less optimized than expected.

### [F-006] BitwiseOptimizationPass applies unsigned-only rewrites to signed types unconditionally
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:37-60 (class doc), 157-178 (rewrite logic)
- Issue: The pass rewrites `x / N` to `x >> log2(N)` and `x % N` to `x & (N-1)` for all expressions, regardless of signedness. For signed integers (I8, I16), arithmetic right shift and bitwise AND produce different results than division and modulo when the left operand is negative. For example, `-7 / 4 = -1` in C (truncation toward zero), but `-7 >> 2 = -2` (arithmetic shift rounds toward negative infinity). Similarly, `-7 % 4 = -3` in C, but `-7 & 3 = 1`.
- Impact: Silent incorrect behavior for signed arithmetic on Game Boy. If a game uses `i8Var` or `i16Var` with division/modulo by powers of 2 and negative values, the optimized code will produce wrong results. The pass docs acknowledge this ("assumes unsigned operand") but there is no guard to skip I8/I16 expressions.

### [F-007] BackendRegistry has race condition in non-synchronized read methods
- Location: gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/BackendRegistry.kt:60-62 (forId), 70-72 (forTarget), 76-78 (all), 82-84 (supportedTargets)
- Issue: `forId()`, `forTarget()`, `all()`, and `supportedTargets()` call `discover()` (which is `@Synchronized`) but then read from `backends` (a `MutableMap`) without synchronization. If another thread calls `register()` (which IS `@Synchronized` and mutates the map) between `discover()` returning and the map read, there is a data race. The `backends` field is a `MutableMap` which is not thread-safe for concurrent read-write.
- Impact: Potential `ConcurrentModificationException` or stale reads in multi-threaded environments. While the typical usage is single-threaded during build, the `object` singleton pattern suggests shared state across threads.

## Medium

### [F-008] SemanticValidationPass.checkFadeWithoutAudioMixer does not walk nested ops
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt:348-375
- Issue: The method flattens `enterOps + frameOps + exitOps` and iterates only the top-level ops. `MusicPlay` or `MusicStop` ops nested inside `IfOp`, `WhileOp`, `ForOp`, `FadeOp`, or `DialogChoice` will not be detected. Other checks in the same file (e.g., `countRawOps` at line 212-235) correctly recurse into nested op lists.
- Impact: False negatives in fade-without-mixer validation. A `MusicPlay` with fade inside a conditional block will not trigger the warning, and the generated C code will silently fall back to instant play at runtime.

### [F-009] ConstantFoldingPass and BitwiseOptimizationPass use mutable instance state -- not rerun-safe
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:57-58
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:62-63
- Issue: Both passes use mutable instance fields (`foldCount`, `foldDetails`, `rewriteDiagnostics`, `rewriteDetails`) that are cleared at the start of `run()`. If the same pass instance is reused across multiple pipeline executions (which `DefaultPipeline.create()` would do since it instantiates passes once), the fields are correctly reset. However, if `run()` were called concurrently on the same instance, the mutable lists would be shared. More importantly, the pattern of clearing state at the start of `run()` is fragile -- if `run()` throws partway through, subsequent calls inherit partial state from the failed run in the `foldDetails`/`rewriteDetails` lists until they are cleared.
- Impact: Low risk in current usage but violates the stateless-pass principle documented in the architecture. Could cause subtle bugs if the pipeline is ever parallelized.

### [F-010] Smooth-follow camera snaps incorrectly when target moves away from camera
- Location: gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:468-523 (buildSmoothFollowBody)
- Issue: The smooth-follow camera implementation subtracts `deadZoneX` from `_cam_target_x` when the target exceeds the dead zone. This only handles the case where the target moves to the right of the camera. When the target moves to the left (negative delta), the condition `abs(target - cam) > deadZone` is true, but the correction `cam = target - deadZoneX` pushes the camera further from the target instead of toward it. A correct implementation would check the sign of the delta and add/subtract the dead zone accordingly.
- Impact: Camera snaps incorrectly when the player moves leftward or upward past the dead zone boundary, causing jarring visual behavior.

### [F-011] Collectible collision check only tests X axis
- Location: gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:894-912
- Issue: The `pickup_collect_<id>()` function receives both `player_x/player_y` and `pickup_x/pickup_y` parameters, but the AABB collision check only compares X coordinates (`player_x >= pickup_x && player_x < pickup_x + 8`). The Y coordinate is completely ignored, meaning a collectible can be "picked up" from any vertical position as long as the X overlaps.
- Impact: Collectibles can be collected from incorrect positions -- walking past a collectible at any height will trigger collection, breaking level design that relies on vertical positioning of collectibles.

### [F-012] SportVisitor uses UNCHECKED_CAST for CodegenFragment lists in pickup reconciliation
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt:105, 321-322, 331, 578-579, 588
- Issue: Three methods (`visitRacing`, `visitBallSport`, `visitTournament`) use `@Suppress("UNCHECKED_CAST")` to cast `pickupResult.functions as List<CFunction>` and `pickupResult.varDecls as List<CVarDecl>`. While `GenreVisitorResult` now uses `List<CodegenFragment>`, the cast to concrete `CFunction`/`CVarDecl` types is unchecked. If `buildPickupResult()` ever returns a result containing non-CFunction/CVarDecl CodegenFragments, this will produce a `ClassCastException` at runtime.
- Impact: Fragile type safety at the boundary between pickup system and sport system codegen. Changes to the pickup system visitor could silently break the sport visitors.

### [F-013] VarDelegate.provideDelegate silently no-ops when GameBuilderContext.current is null
- Location: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:360-361
- Issue: `GameBuilderContext.current?.registerVariable(...)` uses safe-call (`?.`), so if `provideDelegate` is called outside a `game { }` block (where `GameBuilderContext.current` is null), the variable is never registered with any GameBuilder. The variable delegate still creates an `AssignableVar` and appears to work in Kotlin, but the variable will be missing from the generated IR and C code. No error or warning is emitted.
- Impact: Variables declared outside `game { }` (e.g., at file scope or in helper functions called before the game builder runs) silently disappear from the output. Users get no feedback that their variable was not registered, leading to "undefined identifier" errors during GBDK compilation that are hard to trace back to the DSL.

## Low

### [F-014] SemanticValidationPass reuses diagnostic ID "ANLZ-01" for multiple unrelated checks
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt:74, 95, 113, 135, 157
- Issue: Five different validation checks (duplicate scenes, duplicate actors, duplicate variables, invalid start scene, dangling actor refs) all emit diagnostics with `id = "ANLZ-01"`. This makes it impossible to filter or programmatically categorize diagnostics by type.
- Impact: Tooling that processes diagnostics (e.g., CI filtering, IDE integration, reporting) cannot distinguish between fundamentally different validation failures. Minor usability issue.

### [F-015] ArrayVar.exists bounds check on unsigned types includes redundant `>= 0` check
- Location: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:485-489
- Issue: The `exists()` method generates `BinaryExpr(index, GTE, Literal(0))` as part of the bounds check. For `UINT8` (unsigned) index types on Game Boy, this comparison is always true -- unsigned values cannot be negative. The GBDK C compiler (SDCC) may emit a warning about a tautological comparison.
- Impact: Minor -- generates a redundant comparison in C output. The compiler will likely optimize it away, but it adds noise to generated code and may trigger SDCC warnings.

### [F-016] GBCColor common constants lose precision silently
- Location: gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt:80-83
- Issue: `LIGHT_GRAY = fromRGB888(192, 192, 192)` and `DARK_GRAY = fromRGB888(96, 96, 96)` lose precision when quantized to RGB555. 192 >> 3 = 24, expanded back = 24*8+24/4 = 198, not 192. Similarly 96 >> 3 = 12, expanded back = 12*8+12/4 = 99, not 96. While `hasPrecisionLoss()` exists to check for this, the built-in constants themselves have precision loss.
- Impact: Very minor -- the quantization error is small (6 and 3 units respectively), and all GBC colors are inherently 5-bit. However, users may expect that built-in constants are "exact" GBC colors. This is a documentation/expectation issue rather than a functional bug.

## Test coverage gaps

### [F-017] No tests for gbkt-engine module
- Location: gbkt-engine/src/test/ (directory does not exist)
- Issue: The `gbkt-engine` module containing `CombatTypes.kt`, `InputTypes.kt`, `EntityTypes.kt`, `PickupTypes.kt`, `PickupBuilder.kt`, `InventoryTypes.kt`, `SceneTypes.kt`, and `GraphicsTypes.kt` has no test directory at all.
- Impact: Engine runtime types are untested. Any validation logic in builders (e.g., `PickupBuilder`) or computed properties in types is not verified.

### [F-018] No tests for PlatformerVisitor jump condition logic
- Location: gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerCodegenTest.kt
- Issue: While a codegen test file exists, it does not verify the generated C expressions for jump conditions. The critical bug in F-001 (`_plat_grounded || 1`) was not caught because there are no assertions on the actual C expression structure of the physics update function.
- Impact: Critical bugs in generated C logic go undetected. Tests should assert on the structure of generated CIf conditions, not just that functions are generated.

### [F-019] No tests for puzzle undo save/restore roundtrip
- Location: gbkt-genre-puzzle/src/test/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleCodegenTest.kt
- Issue: There are no tests that verify the undo stack push and pop functions work together as a pair. The missing save function (F-003) was not caught by tests.
- Impact: Undo feature is completely non-functional and untested.

### [F-020] Analysis pass tests do not exercise PoolForEachActive with nested ops
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/
- Issue: None of the analysis pass tests create a scene with `PoolForEachActive` containing foldable expressions, navigations, or other nested ops. The systematic omission of PoolForEachActive from pass tree walks (F-004) is not tested.
- Impact: The optimization and analysis gap for pool iteration bodies is not caught by the test suite.

### [F-021] RPG builder ScriptBuilderContext omission not tested with operator-based DSL
- Location: gbkt-genre-rpg/src/test/kotlin/io/github/gbkt/rpg/dsl/
- Issue: RPG builder tests (CharacterLearnsTest, AbilityStatusEffectTest, AtbCombatTest, CombatHooksTest) do not exercise operator-based variable assignments (`score += 10`, `array[i] = value`) inside callbacks. They only test with explicit `assign()` calls or simple DSL constructs that don't need `ScriptBuilderContext`. The bug in F-002 would be caught by a test that uses `score += 10` inside `onLevelUp {}`.
- Impact: The most user-friendly DSL patterns are untested in RPG callbacks, masking a critical runtime crash.
