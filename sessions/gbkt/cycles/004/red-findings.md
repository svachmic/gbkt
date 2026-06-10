# Red Team Findings — Cycle 4

## Critical

### [F-061] ConstantFoldingPass returns stale BinaryExpr on division by zero
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:86
- Issue: When both operands are `Literal` and the operation is DIV or MOD with a zero divisor, `evalBinaryOp` returns `null` and the code falls through to `return expr` — the *original* unfolded `BinaryExpr`. But the sub-expressions may have been recursively folded into different `Literal` values. The correct return should be `expr.copy(left = foldedLeft, right = foldedRight)` (the rebuilt expression with folded children), not the original `expr`.
- Impact: If a nested sub-expression was folded (e.g., `(2+3) / 0`), the original `2+3` binary node is preserved instead of the already-folded `Literal(5)`. The generated C code contains an unoptimized sub-expression that should have been folded.

## High

### [F-062] transformExprsInOp skips Expr fields inside DialogSay segments
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:249
- Issue: `DialogSay` falls into the `else -> op` branch of `transformExprsInOp`, meaning its `segments` list (which can contain `DialogExprSegment(val expr: Expr)`) is never visited by expression transformation passes. Both `ConstantFoldingPass` and `BitwiseOptimizationPass` silently skip expression optimization inside dialog text.
- Impact: Compile-time-known expressions embedded in dialog text (e.g., `DialogExprSegment(BinaryExpr(Literal(2), MUL, Literal(4)))`) are never folded or optimized. These expressions generate unoptimized C code in the final ROM.

### [F-063] BankAllocator allocates string tables to bank 0 (HOME bank)
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt:326
- Issue: The allocation loop iterates `for (bank in 0..maxBanks)`, starting at bank 0. The class doc states "Bank 0 is the home bank (always present without bank-switching)" and the constructor doc says "Default: banks 1-7". Allocating string data to bank 0 (HOME) wastes precious non-banked space that is needed for engine code and interrupt handlers.
- Impact: Largest string namespaces get allocated to HOME bank first (first-fit-decreasing), competing with core engine code for the limited 16KB of non-bankable ROM. This can cause HOME bank overflow on games with moderate localization.

### [F-064] ScriptOpInterpreter silently ignores deathCallbackOps in PoolDestroyActor
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt (executePoolDestroyActor method, ~line 880)
- Issue: `executePoolDestroyActor` reads `slotExpr`, marks the slot inactive, and removes position variables — but it never executes `op.deathCallbackOps`. The `PoolDestroyActor` IR node has a `deathCallbackOps: List<ScriptOp>` field specifically for running ops before slot release. The C backend generates code that calls these ops, but the simulator doesn't.
- Impact: Unit tests using `SimulationContextV2` cannot verify death callback behavior (e.g., spawning particles, incrementing score on pool entity destruction). Test results diverge from actual ROM behavior.

## Medium

### [F-065] SemanticValidationPass.checkPalettePrecision always triggers for non-zero RGB555 values
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt:280-305
- Issue: The precision check compares `r8 = (r5 << 3) | (r5 >> 2)` with `rSimple = r5 << 3`. For any non-zero 5-bit component, the `r5 >> 2` term is non-zero, so `r8 != rSimple` is always true. This means every non-zero GBCColor in strict mode generates a spurious warning. For example, pure white `GBCColor(0x7FFF)` has `r5=31`, `r8 = (31<<3)|(31>>2) = 248|7 = 255`, `rSimple = 31<<3 = 248`, so `255 != 248` triggers a warning even though the color is perfectly representable.
- Impact: Strict palette mode emits false-positive warnings for virtually every color, making the feature unusable. Users will either disable strict mode (losing real precision warnings) or ignore all warnings.

### [F-066] ConstantFoldingPass uses mutable instance state across multiple runs
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:49-52
- Issue: `foldCount` and `foldDetails` are instance fields reset at the start of `run()`. If the pass instance is reused across multiple pipeline runs (which `DefaultPipeline` could do), this is fine. But if `foldExpr` is called concurrently or the pass is used as a shared singleton, the mutable state causes data races. Similarly, `BitwiseOptimizationPass` has the same pattern with `rewriteDiagnostics`, `rewriteDetails`, and `varTypes`.
- Impact: In a concurrent/parallel analysis scenario, fold counts and diagnostic details could be corrupted. Currently not exploitable in single-threaded pipeline execution, but the design is fragile.

### [F-067] BitwiseOptimizationPass only checks right operand for power-of-2
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:118-119
- Issue: The optimization only checks `if (optimizedRight !is Literal)` and `if (!isPow2(n))`. For commutative operations like `MUL`, the power-of-2 constant could be on the left side (e.g., `4 * score` from `Literal(4) * VarRef("score")`). This form is never optimized.
- Impact: Expressions like `4 * x` are not rewritten to `x << 2`, missing an optimization opportunity. The DSL typically puts constants on the right, but manual IR construction or expression rewriting could produce left-constant forms.

### [F-068] ArrayVar.fill/forEach/indexOf/count use INT8 loop variable for arrays larger than 127
- Location: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:579-669
- Issue: The `fill`, `forEach`, `indexOf`, and `count` helpers emit `ForOp` with a temp variable `_arr_${name}_i`. This variable is not registered with `GameBuilder.registerVariable()`, so the backend infers its type. If the backend defaults to INT8, arrays with size > 127 will cause a signed overflow in the loop counter, creating an infinite loop on hardware.
- Impact: Any game using `u8Array(N)` where N > 127, then calling `.fill()`, `.forEach()`, `.indexOf()`, or `.count()` will produce an infinite loop in the generated C code on Game Boy hardware.

### [F-069] SimulationContextV2.runUntil evaluates predicate before first frame execution
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/test/SimulationContextV2.kt:95-104
- Issue: The `runUntil` loop structure is `while (!predicate()) { ... executeFrame() ... }`. This means the predicate is checked before the first frame executes. If the predicate happens to be true from initial state, zero frames are executed and the method returns immediately — which may not reflect intended test semantics where at least one frame should be processed.
- Impact: Tests using `runUntil` may pass vacuously if the initial game state already satisfies the predicate, masking bugs where the frame handler was expected to establish the condition.

### [F-070] transformExprsInGame does not transform dialog Expr segments in game.dialogs
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:259-282
- Issue: `transformExprsInGame` transforms scenes, systems, zones, collision rules, actor pools, menus, and puzzle objects — but not `game.dialogs`. The `DialogDef` type is in `GameIR.dialogs` and each dialog's `defaultSegments` could contain `DialogExprSegment(val expr: Expr)` with foldable expressions. These are never visited.
- Impact: Expressions in dialog definitions (not just DialogSay ops) miss constant folding and bitwise optimization.

### [F-071] SceneBuilder.build() does not include actorIds from GameBuilder
- Location: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt:163-174
- Issue: `SceneBuilder.build()` constructs `SceneIR` with `actorIds = emptyList()` — there is no mechanism in `SceneBuilder` to declare which actors belong to the scene. The `actorIds` field exists on `SceneIR` and is used by `OAMAllocationPass` and `SemanticValidationPass`, but it is never populated from the DSL builder. It must be filled later by `GameBuilder.build()`.
- Impact: If `GameBuilder.build()` does not wire up `actorIds`, OAM allocation per-scene warnings and dangling actor reference checks in `SemanticValidationPass.checkDanglingActorRefs` will always see empty actor lists, silently passing invalid scenes.

## Low

### [F-072] PoolDestroyActor.deathCallbackOps not traversed by collectAllGameOps for puzzle-style ops
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:438
- Issue: `collectAllGameOps` collects `pool.deathCallback` from `game.actorPools` but does NOT collect `deathCallbackOps` from `PoolDestroyActor` script ops embedded inside scene frame/enter/exit handlers. The `collectAllOps` recursive helper handles this via `forEachNestedOpList`, so ops nested inside `PoolDestroyActor` are collected when reached from scene ops — but only if the scene itself is included. This is a subtle coverage gap: death callbacks defined at the actor pool IR level ARE collected, but those same callbacks when inlined into ops are only collected if their parent scene is traversed.
- Impact: Minor: `checkFadeWithoutAudioMixer` and `checkRawOpUsage` would miss ops inside pool-level death callbacks if they were also directly embedded in pool-level callbacks that aren't already in the ops tree. In practice the overlap is low.

### [F-073] TournamentConfig allows exactly 1 participant via init guard
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/domain/TournamentTypes.kt:101-103
- Issue: The `init` block checks `participantIds.size >= 2 || participantIds.isEmpty()` which correctly rejects size 1, but the error message says "must have 0 or >= 2 participants". A tournament with 0 participants is allowed, which is semantically nonsensical for an active tournament — it should require >= 2 when the list is non-empty, or simply always require >= 2.
- Impact: Allows construction of a tournament config with zero participants, which may cause empty bracket generation or runtime errors in codegen.

### [F-074] GbktTestExtension.buildFailureJson does not escape JSON string values
- Location: gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt:182-195
- Issue: `buildFailureJson` writes `gameName` directly into a JSON string without escaping special characters (backslashes, quotes, newlines). If the game name contains a quote character, the output JSON is invalid. The error message in the catch block does a partial escape (`replace("\"", "'")`) but the game name is not escaped.
- Impact: If a game name contains special characters, the failure dump JSON file will be malformed and unparseable by downstream tooling.

### [F-075] BankAllocator overflow fallback assigns to maxBanks even when maxBanks bank is full
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt:333-337
- Issue: When no bank has space, the fallback assigns the namespace to `maxBanks` unconditionally without checking if that bank already has data. This can exceed the `bankSizeBytes` limit on bank `maxBanks`, silently overflowing a ROM bank.
- Impact: If multiple large namespaces don't fit, they all pile into the last bank, potentially generating C code that exceeds the bank's 16KB limit and fails during GBDK compilation with a cryptic "bank overflow" error.

### [F-076] PoParser.extractQuotedString does not handle all PO escape sequences
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt:248-254
- Issue: The `extractQuotedString` method only handles `\\`, `\n`, `\t`, and `\"`. Standard PO files can also contain `\r`, `\0`, and octal/hex escapes. Missing escapes pass through literally, so a PO string containing `\r` would become the two characters `\` and `r` in the output.
- Impact: PO files using carriage returns or null characters in strings will be parsed incorrectly. Most Game Boy games won't use these, but PO files from external translation tools may include them.

### [F-077] checkPalettePrecision reconstructs hex from RGB555 but claims to check original RGB888
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt:267-305
- Issue: The method has access only to the already-quantized RGB555 value (stored in `GBCColor`). It expands back to RGB888 via `(r5 << 3) | (r5 >> 2)` and compares against `r5 << 3`. The original RGB888 color is lost at this point. The diagnostic message says the color "loses precision" but the input was already quantized — every non-zero color triggers the warning (see F-065). The whole approach is fundamentally wrong: the check should be done at palette construction time when the original RGB888 is available, not after quantization.
- Impact: Same as F-065 — compounds the problem by making the diagnostic message misleading. It reports precision loss on colors that were already perfectly quantized.
