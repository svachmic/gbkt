/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.domain

// =============================================================================
// PUZZLE DOMAIN TYPES
// =============================================================================
//
// Plain Kotlin data classes and enums — NOT IR types. Carry puzzle game data
// (grid config, match rules, block-push config, cell types, scoring) that is
// then used by DSL builders to produce core IR types (GenericSystem).
// =============================================================================

/**
 * Operating mode for a puzzle grid system.
 *
 * MATCH: Cells can be swapped/matched; clearing happens when 3+ of the same type appear in a row or
 * column.
 *
 * BLOCK_PUSH: The player pushes blocks to reach goal tiles (Sokoban-style). Focuses on move
 * validation, undo stack, and goal satisfaction.
 */
enum class PuzzleMode {
    MATCH,
    BLOCK_PUSH,
}

/**
 * Direction in which pieces fall after a match clear.
 *
 * DOWN: Standard gravity — cleared pieces cause pieces above to fall down. UP: Inverted gravity —
 * pieces fall upward after clears. NONE: No gravity — cleared cells stay empty (panel-style
 * puzzles).
 */
enum class GravityDirection {
    DOWN,
    UP,
    NONE,
}

/**
 * Timer operating mode.
 *
 * COUNTDOWN: Timer starts at durationFrames and counts down to zero. Used for time-pressure
 * puzzles. ELAPSED: Timer starts at zero and counts up. Used for speed-run / score tracking.
 */
enum class TimerMode {
    COUNTDOWN,
    ELAPSED,
}

/**
 * Special behavior assigned to a custom cell type.
 *
 * NONE: Standard cell — no special behavior beyond matching. BOMB: Clears all adjacent cells when
 * matched. WILDCARD: Matches any cell type for match purposes. ICE: Cannot be directly matched;
 * must be cleared by adjacent matches. GRAVITY: Pulls adjacent cells toward it after each clear.
 */
enum class CellBehavior {
    NONE,
    BOMB,
    WILDCARD,
    ICE,
    GRAVITY,
}

/**
 * Built-in base cell types for puzzle grids.
 *
 * NORMAL: Standard matchable cell with an assigned color/icon. EMPTY: An empty slot — no cell is
 * present. WALL: An immovable obstacle that blocks pushes and matches.
 */
enum class BaseCellType {
    NORMAL,
    EMPTY,
    WALL,
}

/**
 * Developer-defined cell type extending the base set.
 *
 * @property id Unique identifier for this cell type (used in grid init arrays and C code).
 * @property name Human-readable display name for debugging and UI.
 * @property behavior Special behavior this cell type exhibits during gameplay.
 */
data class CustomCellType(
    val id: String,
    val name: String,
    val behavior: CellBehavior = CellBehavior.NONE,
)

/**
 * Configuration for match-3 mode gameplay.
 *
 * @property minMatchLength Minimum number of matching cells in a row/column to trigger a clear.
 *   Defaults to 3 (standard match-3). Can be raised for harder puzzles.
 * @property gravityDirection Direction pieces fall after a clear. Defaults to
 *   [GravityDirection.DOWN].
 * @property chainMultiplier Score multiplier applied per chain reaction after a single player move.
 *   Value 1.0 means no chain bonus; 1.5 means 50% extra per chain step.
 */
data class MatchConfig(
    val minMatchLength: Int = 3,
    val gravityDirection: GravityDirection = GravityDirection.DOWN,
    val chainMultiplier: Float = 1.5f,
)

/**
 * Configuration for block-push mode gameplay (Sokoban-style).
 *
 * @property goalTiles List of tile coordinate pairs (x, y) that must have a block on them for the
 *   puzzle to be considered solved. Empty list means no explicit win condition.
 * @property undoEnabled Whether the undo stack is active. When false, moves cannot be undone.
 * @property undoMaxDepth Maximum number of moves stored in the undo stack. Defaults to
 *   [Int.MAX_VALUE] (unlimited). Developers can reduce for WRAM budget.
 */
data class BlockPushConfig(
    val goalTiles: List<Pair<Int, Int>> = emptyList(),
    val undoEnabled: Boolean = true,
    val undoMaxDepth: Int = Int.MAX_VALUE,
)

/**
 * Optional timer configuration for puzzle scoring.
 *
 * @property mode Whether the timer counts down ([TimerMode.COUNTDOWN]) or up ([TimerMode.ELAPSED]).
 * @property durationFrames For COUNTDOWN mode: total frames before time-up. For ELAPSED mode: this
 *   value is unused (timer runs indefinitely until the puzzle is solved).
 */
data class TimerConfig(
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val durationFrames: Int = 3600, // 60 seconds at 60fps
)

/**
 * Scoring configuration for puzzle grids.
 *
 * @property baseScore Points awarded per cell cleared in a single match group.
 * @property chainMultiplier Multiplier applied to baseScore for each chain step beyond the first.
 * @property moveBonus Bonus points awarded for completing the puzzle within a move target. Zero
 *   means no move bonus is tracked.
 * @property timeBonus Bonus points awarded for completing the puzzle within a time target. Zero
 *   means no time bonus is tracked.
 */
data class PuzzleScoringConfig(
    val baseScore: Int = 100,
    val chainMultiplier: Float = 1.5f,
    val moveBonus: Int = 0,
    val timeBonus: Int = 0,
)

/**
 * Top-level configuration for a puzzle grid system.
 *
 * Single construct supporting both match-3 and block-push modes. The active [mode] determines which
 * sub-config ([matchConfig] or [blockPushConfig]) is used at runtime.
 *
 * @property id Unique system identifier used in generated C code.
 * @property mode Operating mode — determines which sub-config is active.
 * @property width Grid width in cells.
 * @property height Grid height in cells.
 * @property matchConfig Match-3 configuration. Only meaningful when mode = [PuzzleMode.MATCH].
 * @property blockPushConfig Block-push configuration. Only meaningful when mode =
 *   [PuzzleMode.BLOCK_PUSH].
 * @property customCellTypes Developer-registered cell types beyond the [BaseCellType] set.
 * @property timer Optional timer configuration. Null means no timer is active.
 * @property moveCounterEnabled Whether a move counter is tracked for scoring/objectives.
 * @property scoringConfig Scoring configuration (base score, multipliers, bonuses).
 */
data class PuzzleGridConfig(
    val id: String,
    val mode: PuzzleMode = PuzzleMode.MATCH,
    val width: Int = 6,
    val height: Int = 6,
    val matchConfig: MatchConfig = MatchConfig(),
    val blockPushConfig: BlockPushConfig = BlockPushConfig(),
    val customCellTypes: List<CustomCellType> = emptyList(),
    val timer: TimerConfig? = null,
    val moveCounterEnabled: Boolean = false,
    val scoringConfig: PuzzleScoringConfig = PuzzleScoringConfig(),
)
