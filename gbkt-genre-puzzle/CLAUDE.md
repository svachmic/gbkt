# gbkt-genre-puzzle -- Puzzle Genre Plugin

Provides puzzle-game DSL constructs and GBDK code generation for grid-based puzzles: match-3 with gravity and chain combos, Sokoban-style block pushing with undo, timers, move counters, and scoring.

## Dependencies
- **Depends on:** `gbkt-core`, `gbkt-backend-api`, `gbkt-backend-gbdk`
- **Used by:** `gbkt-backend-gbdk` (via ServiceLoader)

## Structure
- `domain/` -- Type definitions (`PuzzleTypes.kt`)
- `dsl/` -- Builder DSL (`PuzzleBuilders.kt`) and extension functions (`PuzzleExtensions.kt`)
- `codegen/` -- GBDK visitor (`PuzzleVisitor.kt`) for C generation

## Key Types
| Type | Role |
|------|------|
| `PuzzleGridConfig` | Top-level grid config: mode, dimensions, match/block-push settings, timer, scoring |
| `PuzzleMode` | Enum: `MATCH`, `BLOCK_PUSH` |
| `MatchConfig` | Min match length, gravity direction, chain multiplier |
| `BlockPushConfig` | Goal tiles, undo enable/disable, undo stack depth |
| `TimerConfig` | Countdown or elapsed timer with duration in frames |
| `TimerMode` | Enum: `COUNTDOWN`, `ELAPSED` |
| `PuzzleScoringConfig` | Base score, chain multiplier, move bonus, time bonus |
| `GravityDirection` | Enum: direction pieces fall after matches |
| `BaseCellType` | Enum: built-in cell types |
| `CellBehavior` | Enum: how custom cells interact (matchable, pushable, etc.) |
| `CustomCellType` | User-defined cell with name and behavior |

## DSL Extensions
`puzzleGrid` -- entry point that takes a `PuzzleGridBuilder` lambda to configure the grid, mode, matching rules, block-push rules, custom cell types, timer, and scoring.

## Codegen
`PuzzleVisitor` generates C functions for the puzzle grid: `buildInitGrid`, `buildCheckMatch`, `buildApplyGravity`, `buildUpdateChain`, `buildPushBlock`, `buildUndo`, `buildUpdateTimer`, `buildCheckCellType`, plus variable declarations.

## Testing
```bash
./gradlew :gbkt-genre-puzzle:test
```
