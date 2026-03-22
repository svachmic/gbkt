# Red Team Findings — Cycle 002

## Critical

### [F-022] Puzzle grid cell-type lookup uses wrong index formula
- Location: gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt:921
- Issue: `buildCheckCellType` computes the grid index as `y + x` instead of `y * width + x`. The CVarDecl initializer reads `CArrayAccess(CVar("_puzzle_grid_$id"), CBinaryExpr(CVar("y"), "+", CVar("x")))`, which is a linear offset rather than a row-major 2D access. For any grid wider than 1 column, this reads the wrong cell.
- Impact: All custom cell type lookups (BOMB, WILDCARD, ICE, GRAVITY) return wrong results. A bomb at (3, 2) on a 6-wide grid would read index 5 instead of index 15, corrupting puzzle mechanics for any game using custom cell types.

### [F-023] Coyote timer resets indefinitely while airborne
- Location: gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:258-280
- Issue: The generated C code resets `_plat_coyote_timer` to `cfg.coyoteFrames` whenever the player is airborne (`_plat_grounded == 0`) AND the timer has reached zero (`_plat_coyote_timer == 0`). Since the timer decrements earlier in the same function, once it counts down to 0, it is immediately refilled on the same frame. The player effectively has infinite coyote time — they can jump at any point after leaving a platform, forever.
- Impact: Coyote time is a core platformer mechanic. This bug makes it non-functional: the grace period never expires, allowing mid-air jumps at any time after walking off a ledge.

### [F-024] Ladder climb modifies pass-by-value parameter (no effect)
- Location: gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:1065-1079
- Issue: `visitLadder` generates a function `platformer_ladder_update(player_x, player_y)` that modifies `player_y -= climbSpeed` and `player_y += climbSpeed` inside the function body. In C, function parameters are passed by value, so these modifications have no effect on the caller's actual player position.
- Impact: Ladder climbing is completely non-functional in generated code. The player cannot move up or down on ladders despite correct input detection.

## High

### [F-025] BitwiseOptimizationPass does not recurse into compound expressions
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt:79
- Issue: `optimizeExpr` begins with `if (expr !is BinaryExpr) return expr`, meaning it never recurses into `UnaryExpr`, `TernaryExpr`, `ArrayAccessExpr`, `CallExpr`, or `CastExpr` children. When `ConstantFoldingPass.foldExpr` was fixed (F-005) to recurse into these compound types, the same fix was not applied to `BitwiseOptimizationPass.optimizeExpr`. Any BinaryExpr nested inside these wrappers (e.g., `-(x * 4)`, `arr[y * 8]`, `flag ? n * 2 : n * 4`) will miss power-of-2 optimization.
- Impact: Missed optimizations for expressions nested inside unary, ternary, array-access, call, and cast nodes. These patterns are common in game code (e.g., tile coordinate calculations using array indexing with multiplications).

### [F-026] ConstantFoldingPass does not evaluate UnaryExpr with Literal operand
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt:95
- Issue: `foldExpr` handles `UnaryExpr` by recursing into the operand (`expr.copy(operand = foldExpr(expr.operand))`) but does not check whether the folded operand is a `Literal` and evaluate the unary operation. For example, `NEGATE(Literal(5))` is left as `UnaryExpr(NEGATE, Literal(5))` instead of being folded to `Literal(-5)`. Similarly, `BITWISE_NOT(Literal(0xFF))` and `LOGICAL_NOT(Literal(1))` are not folded.
- Impact: Missed constant-folding opportunities for negation, bitwise complement, and logical NOT of known constants. These propagate through to generated C code as unnecessary runtime operations.

### [F-027] Ball sport movement uses unsigned dx/dy parameters
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt:420
- Issue: `sport_ball_update_{id}` takes `dx` and `dy` as `CU8` (UINT8, range 0-255), but ball movement requires negative deltas for leftward/upward motion. The function adds `dx` and `dy` to ball position: `_sport_ball_x += dx; _sport_ball_y += dy`. With unsigned types, the ball can only move right and down.
- Impact: Ball movement in generated sport games is limited to one direction per axis. The ball cannot be kicked left or up, breaking fundamental ball sport gameplay.

### [F-028] Racing waypoint advances unconditionally every frame
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt:142-180
- Issue: The `racing_update_{id}` function increments `_racing_waypoint_idx` by 1 every frame the index is less than `waypointCount`, with no proximity check against the player position (despite accepting `player_x, player_y` parameters that are never used). The race completes in exactly `waypointCount * laps` frames regardless of the player's actual position or input.
- Impact: Racing is non-interactive — the player's position has no effect on race progress. The race always finishes at a fixed time.

## Medium

### [F-029] ConstraintCheckPass underestimates WRAM usage vs RAMPlanningPass
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt:84
- Issue: `ConstraintCheckPass` calculates total WRAM as `variableBytes + collectionBytes`, while `RAMPlanningPass` calculates it as `variableBytes + actorStateBytes + collectionBytes + ENGINE_OVERHEAD_BYTES`. The constraint check omits actor state (N * 8 bytes) and engine overhead (64 bytes). For a game with 10 actors, this is 144 bytes of unaccounted RAM.
- Impact: A game that passes `ConstraintCheckPass` validation may still fail `RAMPlanningPass` with a WRAM overflow error. The two passes give contradictory results for the same resource.

### [F-030] transformExprsInGame only transforms scene ops, missing system/actor expressions
- Location: gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ScriptOpTraversal.kt:151-154
- Issue: `transformExprsInGame` only maps over `game.scenes`, applying expression transforms to scene enter/frame/exit ops. It does not transform expressions in `game.systems` configs, `game.actors` physics configs, or any RPG system callbacks (onLevelUp, onVictory, etc.). Both `ConstantFoldingPass` and `BitwiseOptimizationPass` use this function as their sole entry point.
- Impact: Constant folding and bitwise optimization are silently skipped for all expressions outside scene lifecycle handlers. Combat hooks, level-up handlers, AI behavior trees, and system trigger args retain unoptimized expressions.

### [F-031] Ball sport match timer can overflow INT16 with valid configuration
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt:354-363
- Issue: The match timer is stored as `CI16` (signed 16-bit, max 32767) and initialized to `halfDurationSeconds * 60`. The `MatchStructure` validates `halfDurationSeconds >= 0` with no upper bound. Any value above 546 seconds (~9 minutes) produces a frame count exceeding 32767, causing INT16 overflow and wrapping to a negative value. The timer check `_sport_match_timer > 0` would immediately enter the "half ended" branch.
- Impact: Ball sport matches with half durations longer than ~9 minutes start in the "ended" state, immediately advancing through all halves.

### [F-032] Puzzle push_block validates but does not move the block
- Location: gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt:702-764
- Issue: `buildPushBlock` generates a function that validates the target position (bounds check, wall check) and increments the move counter, but does not actually update the grid array. The function computes `nx = px + dx` and `ny = py + dy`, checks validity, then returns 1 (success) without writing `_puzzle_grid[ny*width+nx]` or clearing the source cell.
- Impact: Block-push puzzle mode is non-functional: the function reports success but the grid state never changes. Blocks appear frozen in place.

## Low

### [F-033] Tournament standings bubble sort only swaps wins array, not losses
- Location: gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt:674-773
- Issue: The `tournament_standings_{id}` function performs a bubble sort on `_tournament_wins` array but does not swap the corresponding `_tournament_losses` array entries. After sorting, the wins array is reordered but losses remain in original participant order, making the two arrays inconsistent.
- Impact: Post-sort standings display would show correct win counts but incorrect loss counts for each participant position, giving misleading tournament results.

### [F-034] Puzzle match detection does not clear matched cells
- Location: gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt:210-401
- Issue: `buildCheckMatch` scans for horizontal and vertical runs and sets `found = 1` when a match of length >= `minMatch` is detected, but never zeroes out the matched cells in `_puzzle_grid`. The function is a pure detection pass that returns whether any match exists, but the matched cells remain on the grid.
- Impact: Without cell clearing, repeated calls to check_match always find the same match. The match-3 game loop (detect -> clear -> gravity -> repeat) cannot progress because the "clear" step is missing. The chain counter increments but the grid never changes.

### [F-035] Puzzle gravity DOWN only performs single-pass swap per call
- Location: gbkt-genre-puzzle/src/main/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleVisitor.kt:416-534
- Issue: The gravity-DOWN loop iterates from `r = height-1` down to `r = 1` and swaps each empty cell with the one above it (single pass). If a column has a piece at row 0 and empty cells at rows 1-5, a single call only moves the piece from row 0 to row 1. It requires `height-1` successive calls to fully settle all pieces — but the generated code provides no loop or repeated-call mechanism.
- Impact: After clearing matched cells, pieces only fall one row per gravity call. Without external loop integration, the grid appears to "drip" one row at a time rather than pieces settling immediately.

## Test coverage gaps

### [F-036] No tests for BitwiseOptimizationPass recursion into compound expressions
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPassTest.kt
- Issue: Tests only cover top-level BinaryExpr optimization (MUL/DIV/MOD with power-of-2 constants). No tests verify that optimization applies to BinaryExpr nodes nested inside UnaryExpr, TernaryExpr, ArrayAccessExpr, CallExpr, or CastExpr. This gap allowed F-025 to ship undetected.
- Impact: The optimization pass silently skips compound expressions without any test catching the regression.

### [F-037] No tests for ConstantFoldingPass on UnaryExpr with literal operands
- Location: gbkt-analysis/src/test/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPassTest.kt
- Issue: Tests verify BinaryExpr folding and recursion into compound types (UnaryExpr, TernaryExpr, etc.), but do not test that `UnaryExpr(NEGATE, Literal(5))` is actually evaluated to `Literal(-5)`. The recursion test only checks structural traversal, not evaluation.
- Impact: F-026 (missing unary evaluation) ships without detection.

### [F-038] No integration test for coyote time expiration
- Location: gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerCodegenTest.kt
- Issue: Platformer codegen tests verify that the physics update function is generated and contains expected structure, but do not simulate frame-by-frame execution to verify that coyote time actually expires after the configured number of frames. A behavioral test would have caught F-023.
- Impact: The infinite coyote time bug (F-023) is undetectable by current tests.

### [F-039] No test for puzzle buildCheckCellType grid index calculation
- Location: gbkt-genre-puzzle/src/test/kotlin/io/github/gbkt/genre/puzzle/codegen/PuzzleCodegenTest.kt
- Issue: No test verifies that the generated cell-type lookup function uses `y * width + x` for grid indexing. The critical off-by-one in F-022 (`y + x`) would have been caught by a test asserting on the generated C expression structure.
- Impact: Wrong grid indexing in cell-type dispatch ships without detection.

### [F-040] No test for ladder parameter mutation ineffectiveness
- Location: gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerCodegenTest.kt
- Issue: No test verifies that the ladder update function modifies a global variable rather than the `player_y` parameter. A test checking that the generated code writes to a global (e.g., `_player_y`) rather than the local param would catch F-024.
- Impact: Completely non-functional ladder climbing ships without detection.
