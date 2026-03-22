# Red Team Findings — Cycle 005 (Final)

## Critical

### [F-078] BitwiseOptimizationPass `isMaybeSigned` ignores CastExpr target type
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:173
- Issue: `isMaybeSigned(CastExpr)` recurses into `expr.inner` but never checks `expr.targetType`. A `CastExpr` with `targetType = VarType.I8` or `VarType.I16` produces a signed result regardless of whether the inner expression is unsigned. The current code treats `(INT8)(unsignedVar) / 4` as unsigned and rewrites it to `>> 2`, which is incorrect for negative values. The fix should check `expr.targetType == VarType.I8 || expr.targetType == VarType.I16` and return true immediately, rather than (or in addition to) recursing into `expr.inner`.
- Impact: Generates incorrect bitwise shift rewrites (`DIV -> SHR`, `MOD -> AND`) for expressions cast to signed types. Results in wrong runtime values on Game Boy hardware when the cast value is negative.

### [F-079] ConstantFoldingPass misses short-circuit folding for LOGICAL_AND/OR with constant left operand
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:75-91
- Issue: `foldExpr` for `BinaryExpr` unconditionally folds both children (lines 76-77) before checking whether both are `Literal`. For LOGICAL_AND where the left operand folds to `Literal(0)`, the pass should immediately return `Literal(0)` without folding the right branch — analogous to how `TernaryExpr` folding (lines 108-118) eliminates dead branches. Instead, the pass folds the right operand needlessly and then only folds the overall expression when *both* happen to be literal. When the right operand is non-literal (e.g., `VarRef`), the expression `Literal(0) LOGICAL_AND VarRef("x")` is not folded at all — even though the result is always 0 regardless of `x`. The same applies to `Literal(non-zero) LOGICAL_OR VarRef("x")` which should fold to `Literal(1)`.
- Impact: Missed optimization: `false && anything` and `true || anything` expressions are never folded, producing larger and slower generated C code. These patterns arise naturally in DSL code with feature flags or constant guards.

## High

### [F-080] `transformExprsInOp` uses catch-all `else` for non-sealed ScriptOp subtypes
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:293-296
- Issue: The `else` catch-all branch returns `op` unchanged for all ScriptOp subtypes not explicitly matched. Because `ScriptOp` is a non-sealed interface (V2 architecture), new subtypes with `Expr` fields can be added in any module without the compiler flagging that `transformExprsInOp` needs updating. The comment enumerates 8 types but at least 25 types currently fall through. If a new ScriptOp subtype is added with `Expr` children, the `else` branch will silently skip them, and neither ConstantFoldingPass nor BitwiseOptimizationPass will optimize those expressions.
- Impact: Future ScriptOp subtypes with Expr children will not be optimized, producing suboptimal code silently. No current bug, but a maintenance hazard.

### [F-081] `PickupBuilder.build()` does not validate that zone pickupIds reference defined pickups
- Location: gbkt-engine/src/main/kotlin/io/github/gbkt/core/pickup/PickupBuilder.kt:237-243
- Issue: `PickupBuilder.build()` creates a `GenericSystem` containing all zones and pickups, but never checks that each `PickupZone.pickupId` matches an actual `PickupDef.id` in the same builder. A typo like `zone("spot_1", pickupId = "cojn")` when the pickup is defined as `pickup("coin")` silently produces a zone that references a nonexistent pickup. No validation exists at the builder level, and `GenericSystem` stores everything as opaque config maps so downstream code cannot easily validate either.
- Impact: Dangling pickup zone references produce broken codegen or runtime crashes on Game Boy hardware (undefined pickup behavior tables).

### [F-082] `DropListBuilder.drop()` and `LootEntry` do not validate `chance` parameter range
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/MonsterBuilder.kt:42-44 and gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/LootTableDef.kt:34-40
- Issue: The `drop(itemId, chance)` method accepts any `Int` for `chance` without requiring it to be in the documented 0-100 range. The `DropEntry` data class also has no `init` block to validate `chance`. Passing `chance = 200` or `chance = -10` silently produces an invalid drop table. Similarly, `LootEntry.chance` has no range validation.
- Impact: Invalid probability values flow through to codegen, producing backend code with nonsensical drop rates. On Game Boy, a 200% chance could cause arithmetic overflow in the random-comparison logic.

### [F-083] `PickupDefBuilder` does not validate `effectType` string values
- Location: gbkt-engine/src/main/kotlin/io/github/gbkt/core/pickup/PickupBuilder.kt:43
- Issue: `effectType(type: String)` accepts any string but the only valid values are `"instant"`, `"timed"`, and `"permanent"`. There is no validation, no enum, and the docstring says "One of `"instant"`, `"timed"`, or `"permanent"`" but this is not enforced. A typo like `effectType("insant")` silently produces a pickup with an unrecognized effect type.
- Impact: Unrecognized effect types pass through to codegen, producing broken or no-op pickup behavior on Game Boy hardware.

## Medium

### [F-084] `LootEntry` allows `minQuantity > maxQuantity` without validation
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/LootTableDef.kt:34-40
- Issue: `LootEntry` has `minQuantity: Int = 1` and `maxQuantity: Int = 1` but no `init` block to validate `minQuantity <= maxQuantity`. Creating `LootEntry(itemId = "sword", chance = 50, minQuantity = 5, maxQuantity = 2)` is accepted without error.
- Impact: Backend codegen generates `rand() % (max - min + 1) + min` which with `max < min` produces negative modulus operand, leading to undefined behavior on Game Boy hardware.

### [F-085] `PickupZoneBuilder` allows zero or negative dimensions without validation
- Location: gbkt-engine/src/main/kotlin/io/github/gbkt/core/pickup/PickupBuilder.kt:113-117
- Issue: `PickupZoneBuilder.width(value: Int)` and `height(value: Int)` accept any integer without validating positivity. A zone with `width = 0` or `width = -5` produces a zero-area or negative-area pickup zone. The AABB collision check in `pickup_check_collect()` on Game Boy would never trigger for a zero-area zone, and would have undefined behavior for negative dimensions.
- Impact: Zero or negative zone dimensions produce uncollectable pickups (zero area) or undefined collision behavior (negative area).

### [F-086] `ScriptOpInterpreter.evaluateCallExpr` uses fragile suffix matching for collection dispatch
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt:632-645
- Issue: Collection dispatch uses `fn.startsWith("ht_")` then `rest.endsWith("_insert")` etc. to parse function names. Collection names that themselves contain operation-suffix strings produce ambiguous parses. For example, a hash table named `"clear"` generates function `ht_clear_clear`, and `rest = "clear_clear"` matches `endsWith("_clear")` extracting `name = "clear"`. This happens to work, but a hash table named `"get_insert"` would generate `ht_get_insert_get`, producing `rest = "get_insert_get"`, matching `endsWith("_get")` and extracting `name = "get_insert"`. The greedy longest-suffix-match approach is fragile and depends on collection names never ending with operation keywords.
- Impact: Collection names ending with operation suffixes (`_insert`, `_get`, `_size`, `_clear`, `_push`, `_pop`, `_peek`, `_alloc`, `_free`, `_set`) could produce incorrect dispatch in simulation, diverging from actual Game Boy runtime behavior.

### [F-087] `CombatStatsBuilder` does not enforce documented validation in setter methods
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CharacterBuilder.kt:31-64
- Issue: Each setter method's KDoc says the value "Must be positive" (hp) or "Must be non-negative" (atk, def, etc.), but the methods just assign the value without any check. Validation only happens later in `CombatStats.init{}` block. If a user calls `hp(-5)` inside a `stats {}` block, the error is deferred to `build()` time with a generic `require` message pointing to `CombatStats` constructor rather than the DSL call site.
- Impact: Confusing error messages — the stack trace points to `CombatStats.init` instead of the DSL call site where the invalid value was set.

### [F-088] `TournamentBuilder.build()` allows empty tournament (0 participants)
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/dsl/SportBuilders.kt:528-535
- Issue: `TournamentBuilder.build()` calls `TournamentConfig(...)` which validates `participantIds.size >= 2 || participantIds.isEmpty()`. This means 0 participants is accepted. The builder does not force the caller to add participants before building. A tournament with 0 participants is logically useless and will produce zero matches in the bracket, which may cause edge cases in backend bracket generation codegen.
- Impact: Empty tournaments pass validation but produce no matches. Backend bracket generation may not handle the zero-participant case.

## Low

### [F-089] `SimpleBattleBuilder.buildCombatEngineSystem` duplicates ops in encounter config map
- Location: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/SimpleBattleBuilder.kt:180-190
- Issue: The `encounterConfig` map stores `"onVictoryOps" to onVictoryOps` and `"onDefeatOps" to onDefeatOps`, but these same lists are also passed directly to `CombatEngineSystem(onVictoryOps = onVictoryOps, onDefeatOps = onDefeatOps)`. The op lists exist in two locations in the same object, creating an ambiguous API contract about which source is authoritative.
- Impact: Minor memory overhead and API ambiguity. No functional bug since lists are immutable.

### [F-090] `TiledParser.validate` does not check for empty layer list
- Location: gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt:145-181
- Issue: `validate()` checks tile size, map dimensions, and tileset count, but does not validate that the map has at least one tile layer. A Tiled map with only object layers (no tilelayer) would pass validation but produce an empty tile data set.
- Impact: Maps with no tile layers pass validation but may cause NullPointerException in downstream processing when `getFirstVisibleLayer()` returns null.

### [F-091] `GbktTestExtension.buildFailureJson` does not escape variable names in JSON output
- Location: gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt:190-200
- Issue: `buildFailureJson` constructs JSON by string concatenation. Variable names containing double quotes or backslashes are inserted without escaping: `"\"$name\": $value"`. Variable names with special characters would produce malformed JSON.
- Impact: Malformed JSON failure dump files for variable names containing special characters. Low likelihood since variable names are typically identifier-safe.
