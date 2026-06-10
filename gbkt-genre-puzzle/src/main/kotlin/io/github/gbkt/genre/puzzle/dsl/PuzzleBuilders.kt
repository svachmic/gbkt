/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.dsl

import io.github.gbkt.genre.puzzle.domain.BaseCellType
import io.github.gbkt.genre.puzzle.domain.BlockPushConfig
import io.github.gbkt.genre.puzzle.domain.CellBehavior
import io.github.gbkt.genre.puzzle.domain.CustomCellType
import io.github.gbkt.genre.puzzle.domain.GravityDirection
import io.github.gbkt.genre.puzzle.domain.MatchConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleGridConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleMode
import io.github.gbkt.genre.puzzle.domain.PuzzleScoringConfig
import io.github.gbkt.genre.puzzle.domain.TimerConfig
import io.github.gbkt.genre.puzzle.domain.TimerMode

// =============================================================================
// PUZZLE DSL BUILDERS
// =============================================================================
//
// Builder classes for constructing PuzzleGridConfig instances. The single
// PuzzleGridBuilder supports both MATCH and BLOCK_PUSH modes via a mode switch.
//
// Design constraint: builders produce PuzzleGridConfig domain objects.
// PuzzleExtensions.kt converts these to GenericSystem IR types.
// =============================================================================

/**
 * Builder for [MatchConfig] — match-3 mode configuration.
 *
 * ```kotlin
 * matchMode {
 *     minMatchLength(4)
 *     gravity(GravityDirection.DOWN)
 *     chainMultiplier(2.0f)
 * }
 * ```
 */
class MatchConfigBuilder {
    private var minMatchLength: Int = 3
    private var gravityDirection: GravityDirection = GravityDirection.DOWN
    private var chainMultiplier: Float = 1.5f

    /** Minimum cells in a row/column to trigger a clear. Default: 3. */
    fun minMatchLength(length: Int) {
        require(length >= 2) { "minMatchLength must be at least 2, got $length" }
        this.minMatchLength = length
    }

    /** Direction gravity applies after cells are cleared. Default: DOWN. */
    fun gravity(direction: GravityDirection) {
        this.gravityDirection = direction
    }

    /** Score multiplier per chain reaction. Default: 1.5f. */
    fun chainMultiplier(multiplier: Float) {
        require(multiplier >= 1.0f) { "chainMultiplier must be >= 1.0f, got $multiplier" }
        this.chainMultiplier = multiplier
    }

    internal fun build(): MatchConfig =
        MatchConfig(
            minMatchLength = minMatchLength,
            gravityDirection = gravityDirection,
            chainMultiplier = chainMultiplier,
        )
}

/**
 * Builder for [BlockPushConfig] — block-push / Sokoban-style mode configuration.
 *
 * ```kotlin
 * blockPushMode {
 *     goal(3, 3)
 *     goal(4, 4)
 *     undoDepth(20)
 * }
 * ```
 */
class BlockPushConfigBuilder {
    private val goalTiles: MutableList<Pair<Int, Int>> = mutableListOf()
    private var undoEnabled: Boolean = true
    private var undoMaxDepth: Int = Int.MAX_VALUE

    /**
     * Adds a goal tile position (x, y) that must contain a block to solve the puzzle.
     *
     * Multiple calls accumulate goal positions.
     */
    fun goal(x: Int, y: Int) {
        goalTiles.add(Pair(x, y))
    }

    /** Disables the undo stack entirely. When disabled, moves cannot be undone. */
    fun disableUndo() {
        undoEnabled = false
    }

    /**
     * Limits the undo stack depth. Lower values save WRAM.
     *
     * By default, undo depth is unlimited ([Int.MAX_VALUE]).
     */
    fun undoDepth(depth: Int) {
        require(depth >= 1) { "undoDepth must be at least 1, got $depth" }
        this.undoMaxDepth = depth
    }

    internal fun build(): BlockPushConfig =
        BlockPushConfig(
            goalTiles = goalTiles.toList(),
            undoEnabled = undoEnabled,
            undoMaxDepth = undoMaxDepth,
        )
}

/**
 * Builder for [TimerConfig] — optional puzzle timer.
 *
 * ```kotlin
 * timer {
 *     countdown(durationFrames = 3600)
 * }
 * ```
 */
class TimerConfigBuilder {
    private var mode: TimerMode = TimerMode.COUNTDOWN
    private var durationFrames: Int = 3600

    /** Countdown timer mode — starts at durationFrames and counts down to zero. */
    fun countdown(durationFrames: Int = 3600) {
        this.mode = TimerMode.COUNTDOWN
        this.durationFrames = durationFrames
    }

    /** Elapsed timer mode — starts at zero and counts up indefinitely. */
    fun elapsed() {
        this.mode = TimerMode.ELAPSED
    }

    /** Sets the duration in frames for COUNTDOWN mode. Has no effect in ELAPSED mode. */
    fun durationFrames(frames: Int) {
        require(frames > 0) { "durationFrames must be positive, got $frames" }
        this.durationFrames = frames
    }

    internal fun build(): TimerConfig = TimerConfig(mode = mode, durationFrames = durationFrames)
}

/**
 * Builder for [PuzzleScoringConfig] — score and bonus configuration.
 *
 * ```kotlin
 * scoring {
 *     baseScore(200)
 *     chainMultiplier(2.0f)
 *     moveBonus(500)
 *     timeBonus(1000)
 * }
 * ```
 */
class PuzzleScoringConfigBuilder {
    private var baseScore: Int = 100
    private var chainMultiplier: Float = 1.5f
    private var moveBonus: Int = 0
    private var timeBonus: Int = 0

    /** Points per cell cleared in a single match group. Default: 100. */
    fun baseScore(score: Int) {
        require(score >= 0) { "baseScore must be non-negative, got $score" }
        this.baseScore = score
    }

    /** Score multiplier per chain step beyond the first. Default: 1.5f. */
    fun chainMultiplier(multiplier: Float) {
        require(multiplier >= 1.0f) { "chainMultiplier must be >= 1.0f, got $multiplier" }
        this.chainMultiplier = multiplier
    }

    /** Bonus points for completing within a move target (0 = disabled). */
    fun moveBonus(bonus: Int) {
        require(bonus >= 0) { "moveBonus must be non-negative, got $bonus" }
        this.moveBonus = bonus
    }

    /** Bonus points for completing within a time target (0 = disabled). */
    fun timeBonus(bonus: Int) {
        require(bonus >= 0) { "timeBonus must be non-negative, got $bonus" }
        this.timeBonus = bonus
    }

    internal fun build(): PuzzleScoringConfig =
        PuzzleScoringConfig(
            baseScore = baseScore,
            chainMultiplier = chainMultiplier,
            moveBonus = moveBonus,
            timeBonus = timeBonus,
        )
}

/**
 * Builder for [PuzzleGridConfig] — the main puzzle grid system.
 *
 * Supports both [PuzzleMode.MATCH] and [PuzzleMode.BLOCK_PUSH] through a single construct. The
 * active mode determines which sub-configuration is active at runtime.
 *
 * Default mode is [PuzzleMode.MATCH]. Call [blockPushMode] or [matchMode] to set the mode and
 * configure mode-specific settings.
 *
 * ```kotlin
 * puzzleGrid("grid") {
 *     size(8, 8)
 *     matchMode {
 *         minMatchLength(3)
 *         gravity(GravityDirection.DOWN)
 *     }
 *     cellType("bomb") {
 *         name("Bomb")
 *         behavior(CellBehavior.BOMB)
 *     }
 *     timer { countdown(3600) }
 *     moveCounter(enabled = true)
 *     scoring { baseScore(200) }
 * }
 * ```
 */
class PuzzleGridBuilder(private val id: String) {
    private var mode: PuzzleMode = PuzzleMode.MATCH
    private var width: Int = 6
    private var height: Int = 6
    private var matchConfig: MatchConfig = MatchConfig()
    private var blockPushConfig: BlockPushConfig = BlockPushConfig()
    private val customCellTypes: MutableList<CustomCellType> = mutableListOf()
    private var timerConfig: TimerConfig? = null
    private var moveCounterEnabled: Boolean = false
    private var scoringConfig: PuzzleScoringConfig = PuzzleScoringConfig()

    /**
     * Sets the grid dimensions.
     *
     * @param w Grid width in cells. Default: 6.
     * @param h Grid height in cells. Default: 6.
     */
    fun size(w: Int, h: Int) {
        require(w >= 2) { "Grid width must be at least 2, got $w" }
        require(h >= 2) { "Grid height must be at least 2, got $h" }
        this.width = w
        this.height = h
    }

    /**
     * Switches to [PuzzleMode.MATCH] and configures match-3 settings.
     *
     * ```kotlin
     * matchMode {
     *     minMatchLength(4)
     *     gravity(GravityDirection.UP)
     * }
     * ```
     */
    fun matchMode(block: MatchConfigBuilder.() -> Unit = {}) {
        this.mode = PuzzleMode.MATCH
        val builder = MatchConfigBuilder()
        builder.block()
        this.matchConfig = builder.build()
    }

    /**
     * Switches to [PuzzleMode.BLOCK_PUSH] and configures Sokoban-style settings.
     *
     * ```kotlin
     * blockPushMode {
     *     goal(3, 3)
     *     undoDepth(10)
     * }
     * ```
     */
    fun blockPushMode(block: BlockPushConfigBuilder.() -> Unit = {}) {
        this.mode = PuzzleMode.BLOCK_PUSH
        val builder = BlockPushConfigBuilder()
        builder.block()
        this.blockPushConfig = builder.build()
    }

    /**
     * Registers a custom cell type extending the base [BaseCellType] set.
     *
     * ```kotlin
     * cellType("bomb") {
     *     name("Bomb")
     *     behavior(CellBehavior.BOMB)
     * }
     * ```
     */
    fun cellType(id: String, block: CustomCellTypeBuilder.() -> Unit) {
        val builder = CustomCellTypeBuilder(id)
        builder.block()
        customCellTypes.add(builder.build())
    }

    /**
     * Enables an optional timer.
     *
     * ```kotlin
     * timer { countdown(durationFrames = 3600) }
     * ```
     */
    fun timer(block: TimerConfigBuilder.() -> Unit) {
        val builder = TimerConfigBuilder()
        builder.block()
        this.timerConfig = builder.build()
    }

    /**
     * Enables or disables the move counter.
     *
     * The move counter tracks the number of moves made for scoring and objective purposes. Default:
     * disabled.
     */
    fun moveCounter(enabled: Boolean = true) {
        this.moveCounterEnabled = enabled
    }

    /**
     * Configures scoring parameters.
     *
     * ```kotlin
     * scoring {
     *     baseScore(200)
     *     chainMultiplier(2.0f)
     * }
     * ```
     */
    fun scoring(block: PuzzleScoringConfigBuilder.() -> Unit) {
        val builder = PuzzleScoringConfigBuilder()
        builder.block()
        this.scoringConfig = builder.build()
    }

    internal fun build(): PuzzleGridConfig =
        PuzzleGridConfig(
            id = id,
            mode = mode,
            width = width,
            height = height,
            matchConfig = matchConfig,
            blockPushConfig = blockPushConfig,
            customCellTypes = customCellTypes.toList(),
            timer = timerConfig,
            moveCounterEnabled = moveCounterEnabled,
            scoringConfig = scoringConfig,
        )
}

/** Builder for [CustomCellType] — used within [PuzzleGridBuilder.cellType]. */
class CustomCellTypeBuilder(private val id: String) {
    private var name: String = id
    private var behavior: CellBehavior = CellBehavior.NONE

    /** Human-readable display name for this cell type. Defaults to the ID. */
    fun name(name: String) {
        this.name = name
    }

    /** Special behavior assigned to this cell type. Default: [CellBehavior.NONE]. */
    fun behavior(behavior: CellBehavior) {
        this.behavior = behavior
    }

    internal fun build(): CustomCellType = CustomCellType(id = id, name = name, behavior = behavior)
}
