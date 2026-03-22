/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.ir.GenericSystem

// =============================================================================
// PUZZLE DSL EXTENSIONS ON GameBuilder
// =============================================================================
//
// These functions extend GameBuilder with puzzle-specific DSL constructs.
// They follow the BOM separation pattern:
//   - gbkt-genre-puzzle depends on gbkt-core (one-directional)
//   - GameBuilder does NOT know about puzzle types
//   - Puzzle builders produce CORE IR types (GenericSystem) — no new sealed subtypes
//
// All puzzle configuration travels in the GenericSystem config map.
// The system type key is "puzzle_grid" for all configurations.
// =============================================================================

/**
 * Defines and registers a puzzle grid system.
 *
 * Supports both match-3 ([io.github.gbkt.genre.puzzle.domain.PuzzleMode.MATCH]) and block-push
 * ([io.github.gbkt.genre.puzzle.domain.PuzzleMode.BLOCK_PUSH]) modes through a single grid
 * construct. The active mode determines which sub-configuration drives gameplay.
 *
 * Produces a [GenericSystem] with config type `"puzzle_grid"`. All puzzle configuration travels in
 * the config map — NO new sealed IR subtypes are created.
 *
 * ```kotlin
 * puzzleGrid("grid") {
 *     size(8, 8)
 *     matchMode {
 *         minMatchLength(3)
 *         gravity(GravityDirection.DOWN)
 *         chainMultiplier(1.5f)
 *     }
 *     cellType("bomb") {
 *         name("Bomb")
 *         behavior(CellBehavior.BOMB)
 *     }
 *     timer { countdown(3600) }
 *     moveCounter(enabled = true)
 *     scoring { baseScore(200); chainMultiplier(2.0f) }
 * }
 * ```
 *
 * For block-push mode:
 * ```kotlin
 * puzzleGrid("sokoban") {
 *     size(10, 10)
 *     blockPushMode {
 *         goal(5, 5)
 *         goal(6, 6)
 *         undoDepth(20)
 *     }
 * }
 * ```
 *
 * @param id Unique system identifier used in generated C code.
 * @param block Configuration block executed against a [PuzzleGridBuilder].
 * @return The registered [GenericSystem] with type `"puzzle_grid"`.
 */
fun GameBuilder.puzzleGrid(
    id: String = "grid",
    block: PuzzleGridBuilder.() -> Unit,
): GenericSystem {
    val builder = PuzzleGridBuilder(id)
    builder.block()
    val config = builder.build()
    val configMap =
        buildMap<String, Any> {
            put("type", "puzzle_grid")
            put("mode", config.mode)
            put("width", config.width)
            put("height", config.height)
            put("matchConfig", config.matchConfig)
            put("blockPushConfig", config.blockPushConfig)
            put("customCellTypes", config.customCellTypes)
            put("moveCounterEnabled", config.moveCounterEnabled)
            put("scoringConfig", config.scoringConfig)
            // timer is optional — only add to map when present to avoid nullable Any values
            config.timer?.let { put("timer", it) }
        }
    val system = GenericSystem(id = id, config = configMap)
    registerSystem(system)
    return system
}
