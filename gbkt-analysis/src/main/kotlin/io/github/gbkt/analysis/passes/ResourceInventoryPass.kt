/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.ResourceInventory
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.byteSize

/**
 * Analysis pass that walks all game IR nodes and computes a [ResourceInventory].
 *
 * The populated inventory is stored on the output [PassContext] and consumed by
 * [ConstraintCheckPass] and subsequent allocation passes.
 *
 * Sprite tile count formula: `(width / 8) * (height / 8)` per actor (1 frame assumed when no
 * explicit frame count is available in [SpriteDef]).
 *
 * Collection bytes are computed from all four collection types in [GameIR]:
 * - Hash table: `size * (keyType.byteSize + valueType.byteSize + 1)`
 * - Pool: `capacity * elementType.byteSize + ceil(capacity/8) + 1` (bitmap + count byte)
 * - Ring buffer: `capacity * elementType.byteSize + 3` (head + tail + count)
 * - Fixed slots: `count * elementType.byteSize + if (count <= 8) 1 else 2` (bitfield)
 */
class ResourceInventoryPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val game = context.game

        val spriteTileCounts =
            game.actors
                .mapNotNull { actor ->
                    val sprite = actor.sprite ?: return@mapNotNull null
                    val tileCount = (sprite.size.width / 8) * (sprite.size.height / 8)
                    actor.id to tileCount
                }
                .toMap()

        val variableBytes =
            game.variables.sumOf { v ->
                when (v.type) {
                    VarType.U8,
                    VarType.I8 -> 1
                    VarType.U16,
                    VarType.I16 -> 2
                }
            }

        val perSceneActorCounts =
            game.scenes
                .associate { scene -> scene.id to scene.actorIds.size }
                .filterValues { it > 0 }

        // Compute collection RAM bytes from GameIR collection fields (wired in Plan 06-06)
        val collectionBytes = computeCollectionBytes(game)

        val inventory =
            ResourceInventory(
                totalActors = game.actors.size,
                totalScenes = game.scenes.size,
                totalVariables = game.variables.size,
                totalAssets = game.assets.size,
                spriteTileCounts = spriteTileCounts,
                variableBytes = variableBytes,
                collectionBytes = collectionBytes,
                perSceneActorCounts = perSceneActorCounts,
            )

        return PassResult.Success(context.copy(inventory = inventory))
    }

    // -------------------------------------------------------------------------
    // Collection memory accounting
    // -------------------------------------------------------------------------

    /**
     * Computes the total WRAM bytes required for all collection declarations in [game].
     *
     * Formula per collection type (matches GBDKCollectionCodegen static array layout):
     * - Hash table: `size * (keyType.byteSize + valueType.byteSize + 1)`
     * - Pool: `capacity * elementType.byteSize + ceil(capacity/8) + 1` (bitmap + active count)
     * - Ring buffer: `capacity * elementType.byteSize + 3` (head + tail + count bytes)
     * - Fixed slots: `count * elementType.byteSize + if (count <= 8) 1 else 2` (bitfield)
     */
    private fun computeCollectionBytes(game: GameIR): Int {
        var total = 0

        for (ht in game.hashTables) {
            // Three arrays: keys[size], values[size], used[size]
            total += ht.size * (ht.keyType.byteSize + ht.valueType.byteSize + 1)
        }

        for (pool in game.pools) {
            // data[capacity] + free-list bitmap[ceil(capacity/8)] + count[1]
            val bitmapBytes = (pool.capacity + 7) / 8
            total += pool.capacity * pool.elementType.byteSize + bitmapBytes + 1
        }

        for (rb in game.ringBuffers) {
            // buf[capacity] + head[1] + tail[1] + count[1]
            total += rb.capacity * rb.elementType.byteSize + 3
        }

        for (fs in game.fixedSlots) {
            // data[count] + active bitfield (UINT8 for <=8 slots, UINT16 for 9-16 slots)
            val bitfieldBytes = if (fs.count <= 8) 1 else 2
            total += fs.count * fs.elementType.byteSize + bitfieldBytes
        }

        return total
    }
}
