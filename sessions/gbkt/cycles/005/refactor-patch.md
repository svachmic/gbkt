## Changes: 4

### 1. Consolidate 27 no-op stub branches in `ScriptOpInterpreter.executeOp`
- **File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/test/ScriptOpInterpreter.kt`
- **Impact:** -112 lines net. Replaced 27 individual `is XxxOp -> { /* no-op stub */ }` branches with a single grouped `when` clause using comma-separated type checks.
- **Why:** The individual branches were identical empty blocks with different comments. Grouping them makes it immediately obvious which ops are simulated and which are hardware stubs.

### 2. Extract `requireNonNeg` helper in `CombatStatsBuilder`
- **File:** `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CharacterBuilder.kt`
- **Impact:** -13 lines net. Six stat setter methods (`atk`, `def`, `sp`, `matk`, `mdef`, `agl`) all had identical `require(value >= 0)` validation. Extracted to a private `requireNonNeg(stat, value)` helper.
- **Why:** Eliminates copy-paste validation code. `hp()` retains its unique `> 0` check.

### 3. Extract shared `buildSportPickup` factory in `SportBuilders`
- **File:** `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/dsl/SportBuilders.kt`
- **Impact:** Both `RacingBuilder.pickup()` and `BallSportBuilder.pickup()` had byte-identical bodies constructing `SportPickupDef`. Extracted to a private top-level function.
- **Why:** Single point of change if `SportPickupDef` construction evolves.

### 4. Extract `foldBinaryExpr`/`foldUnaryExpr`/`foldTernaryExpr` from `ConstantFoldingPass.foldExpr`
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt`
- **Impact:** `foldExpr` was a 67-line `when` expression with 3 levels of nesting in its `BinaryExpr` branch. Split into three focused private methods. `foldExpr` is now 6 lines dispatching by type.
- **Why:** Each extracted method has a single concern and uses early returns instead of nested if/else, making the logic easier to follow.

### Test results
Full suite (`./gradlew test`): **BUILD SUCCESSFUL** -- 168 tasks, all passing.
