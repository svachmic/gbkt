# Green Patch Summary - Cycle 002

## Fixed (11/11)

### [F-022] Puzzle grid cell-type lookup uses wrong index formula
- **File:** `gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt`
- **Fix:** Changed `buildCheckCellType` array index from `y + x` to `y * width + x`. Added `width: Int` parameter and updated the caller to pass `config.width`.
- **Commit:** `7ec58df`

### [F-023] Coyote timer resets indefinitely while airborne
- **File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
- **Fix:** Replaced the buggy "set coyote timer when airborne AND timer==0" logic with "maintain coyote timer at coyoteFrames while grounded". The timer now counts down naturally when airborne and stays at 0 once expired.
- **Commit:** `1a8e214`

### [F-024] Ladder climb modifies pass-by-value parameter (no effect)
- **File:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
- **Fix:** Changed `player_x` and `player_y` parameters from `CU8` (pass-by-value) to `CPointer(CU8)` (pass-by-pointer). All reads/writes in the function body now dereference via `CUnaryExpr("*", ...)`.
- **Commit:** `dad6fc2`

### [F-025] BitwiseOptimizationPass doesn't recurse into compound expressions
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt`
- **Fix:** Added recursion into `UnaryExpr`, `TernaryExpr`, `ArrayAccessExpr`, `CallExpr`, and `CastExpr` children, matching the pattern already used by `ConstantFoldingPass`.
- **Commit:** `4a16446`

### [F-026] ConstantFoldingPass doesn't evaluate UnaryExpr with Literal operand
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt`
- **Fix:** Added `evalUnaryOp` function that evaluates `NEGATE` (-v), `BITWISE_NOT` (v.inv()), and `LOGICAL_NOT` (v==0?1:0). The `UnaryExpr` branch now folds to `Literal` when the operand is a compile-time constant.
- **Commit:** `e498c0f`

### [F-027] Ball sport movement uses unsigned dx/dy parameters
- **File:** `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt`
- **Fix:** Changed `sport_ball_update` parameters `dx` and `dy` from `CU8` (0-255) to `CI8` (-128..127) so the ball can move in all directions.
- **Commit:** `512f672`

### [F-028] Racing waypoint advances unconditionally every frame
- **File:** `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt`
- **Fix:** Generated waypoint coordinate arrays (`_racing_wp_x_`, `_racing_wp_y_`) with pixel coordinates. Added proximity check using absolute distance (ternary abs pattern) with 8-pixel threshold before advancing the waypoint index.
- **Commit:** `2bfe0d1`

### [F-029] ConstraintCheckPass underestimates WRAM vs RAMPlanningPass
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt`
- **Fix:** Added `actorStateBytes` (N * `ACTOR_STATE_BYTES`) and `overheadBytes` (`ENGINE_OVERHEAD_BYTES`) from `RAMPlanningPass` companion to the WRAM calculation, making both passes consistent.
- **Commit:** `35d0f71`

### [F-030] transformExprsInGame only walks scene ops, missing system/actor expressions
- **File:** `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt`
- **Fix:** Extended `transformExprsInGame` to also walk `systems` (ExplorationSystem callbacks and gauge callbacks), `zones` (onEnter/onExit), `collisionRules` (onCollide), and `actorPools` (deathCallback). Added `transformExprsInSystem` and `transformExprsInZone` helper functions.
- **Commit:** `f38bb2e`

### [F-031] Ball sport match timer overflow with >546s half duration
- **File:** `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt`
- **Fix:** Changed `_sport_match_timer` type from `CI16` (max 32767, ~546s) to `CU16` (max 65535, ~1092s). Timer values are always non-negative so unsigned is correct.
- **Commit:** `85ba5ce`

### [F-032] Puzzle push_block validates but never updates the grid
- **File:** `gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt`
- **Fix:** Added grid array writes: copies the cell value from source `(px,py)` to destination `(nx,ny)` using `grid[ny*width+nx] = grid[py*width+px]`, then clears the source cell with `grid[py*width+px] = 0`.
- **Commit:** `2c7eb47`

## Skipped

None.

## Final Test Status

```
BUILD SUCCESSFUL in 41s
168 actionable tasks: 49 executed, 119 up-to-date
```

All tests pass across all modules.
