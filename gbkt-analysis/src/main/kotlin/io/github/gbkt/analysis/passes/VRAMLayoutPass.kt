/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.AssetManifest
import io.github.gbkt.core.AssetManifestEntry
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VRAMRange

/**
 * Analysis pass that computes per-scene VRAM tile allocation with hybrid deduplication strategy.
 *
 * Allocation model:
 * - Global tiles (fonts, UI): fixed global slots counted across all scenes (~36 for standard font)
 * - Sprite tiles: reserved per-scene based on actors active in that scene
 * - BG tiles: scene-specific tiles fill the remaining budget
 *
 * Overflow detection per the locked decision: tile overflow in a scene produces an ERROR with the
 * scene name, a breakdown by source (sprites, BG, global), and a splitting suggestion.
 *
 * Warning is emitted when total tile usage exceeds [AnalysisConfig.vramTileWarningThreshold].
 *
 * Outputs: Populates [PassContext.vramAssignments] with [VRAMRange] entries for each scene and
 * per-actor assignment.
 *
 * Prerequisites: [ResourceInventoryPass] must run first — [PassContext.inventory] must be non-null.
 */
class VRAMLayoutPass : AnalysisPass {

    companion object {
        /** Total VRAM tile count on Game Boy ($8000-$97FF, 16 bytes per tile). */
        const val TOTAL_VRAM_TILES = 384

        /** Standard font tile reservation when a FONT asset is present. */
        const val FONT_TILE_COUNT = 36

        /**
         * Default BG tile estimate when a scene has a non-null [SceneIR.tilesetRef].
         *
         * A standard Game Boy tileset PNG is organized as columns of 8x8 tiles. Without file I/O at
         * analysis time, a conservative heuristic assumes a full 256-tile background set. The
         * actual tile count will be refined by the asset pipeline in Phase 5.
         */
        const val BG_TILES_DEFAULT_ESTIMATE = 256
    }

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val inventory =
            context.inventory
                ?: return PassResult.Failed(
                    listOf(
                        Diagnostic(
                            id = "ANLZ-03",
                            severity = Severity.ERROR,
                            message =
                                "VRAMLayoutPass requires a non-null ResourceInventory on PassContext. " +
                                    "Ensure ResourceInventoryPass runs before VRAMLayoutPass.",
                        )
                    )
                )

        val diagnostics = mutableListOf<Diagnostic>()
        val vramAssignments = mutableMapOf<String, VRAMRange>()

        // 1. Count global tiles (fonts, UI) — shared across all scenes
        val globalTiles = countGlobalTiles(game)

        // 2. For each scene, compute tile budget and check for overflow/warnings
        for (scene in game.scenes) {
            val sceneActors = game.actors.filter { it.id in scene.actorIds }
            val spriteTiles = sceneActors.sumOf { actor ->
                inventory.spriteTileCounts[actor.id] ?: computeSpriteTileCount(actor)
            }

            // Background tiles used by this scene (manifest-aware; falls back to heuristic)
            val bgTilesUsed = estimateBgTiles(scene, game, context.assetManifest)

            val totalUsed = spriteTiles + globalTiles + bgTilesUsed

            if (totalUsed > context.config.vramTileErrorThreshold) {
                diagnostics.add(
                    buildTileOverflowError(
                        scene,
                        spriteTiles,
                        sceneActors,
                        bgTilesUsed,
                        globalTiles,
                        game,
                    )
                )
                // Continue checking other scenes — collect all errors before returning
            } else if (totalUsed > context.config.vramTileWarningThreshold) {
                diagnostics.add(
                    Diagnostic(
                        id = "ANLZ-03",
                        severity = Severity.WARNING,
                        message =
                            "Scene '${scene.id}' uses $totalUsed / $TOTAL_VRAM_TILES VRAM tiles " +
                                "(${totalUsed * 100 / TOTAL_VRAM_TILES}%). " +
                                "Sprites: $spriteTiles, BG: $bgTilesUsed, Global: $globalTiles.",
                        location = "scene '${scene.id}'",
                        suggestion =
                            "Reduce sprite frame counts or use a smaller tileset to stay " +
                                "below the $TOTAL_VRAM_TILES tile limit.",
                    )
                )
            }

            // Assign VRAM ranges:
            // [0 .. spriteTiles-1]                    — sprite tiles
            // [spriteTiles .. spriteTiles+globalTiles-1] — global tiles (fonts, UI)
            // [spriteTiles+globalTiles .. ..+bgTilesUsed-1] — scene BG tiles
            vramAssignments[scene.id] =
                VRAMRange(
                    startTile = spriteTiles + globalTiles,
                    endTile = spriteTiles + globalTiles + bgTilesUsed,
                )

            // Per-actor sprite VRAM ranges (sprites packed from tile 0)
            var spriteOffset = 0
            for (actor in sceneActors) {
                val tileCount =
                    inventory.spriteTileCounts[actor.id] ?: computeSpriteTileCount(actor)
                vramAssignments[actor.id] =
                    VRAMRange(startTile = spriteOffset, endTile = spriteOffset + tileCount)
                spriteOffset += tileCount
            }
        }

        // Fail if any errors were produced
        val errors = diagnostics.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            return PassResult.Failed(diagnostics)
        }

        return PassResult.Success(
            context.copy(
                vramAssignments = context.vramAssignments + vramAssignments,
                diagnostics = context.diagnostics + diagnostics,
            )
        )
    }

    /**
     * Computes sprite tile count for a single actor from its [SpriteDef].
     *
     * Formula: `(width / 8) * (height / 8)`. Returns 0 if the actor has no sprite. This is a
     * fallback — [ResourceInventory.spriteTileCounts] should be preferred when available.
     */
    private fun computeSpriteTileCount(actor: ActorIR): Int {
        val sprite = actor.sprite ?: return 0
        return (sprite.size.width / 8) * (sprite.size.height / 8)
    }

    /**
     * Counts tiles reserved globally across all scenes.
     *
     * Heuristic rules:
     * - Standard font asset present → reserves [FONT_TILE_COUNT] tiles
     * - Each additional font asset → adds [FONT_TILE_COUNT] tiles
     *
     * These tiles are subtracted from every scene's available budget because fonts are shared.
     */
    private fun countGlobalTiles(game: GameIR): Int {
        val fontCount = game.assets.count { it.type == AssetType.FONT }
        return fontCount * FONT_TILE_COUNT
    }

    /**
     * Estimates background tiles used by [scene] from [SceneIR.tilesetRef].
     *
     * When [manifest] contains a [AssetManifestEntry.TilemapEntry] with a matching [tilesetPath]
     * and a positive [AssetManifestEntry.TilemapEntry.uniqueTileCount], the actual unique tile
     * count is returned. This replaces the conservative 256-tile heuristic with real data from the
     * asset pipeline.
     *
     * Falls back to [BG_TILES_DEFAULT_ESTIMATE] (256 tiles) when:
     * - [manifest] is null (asset pipeline has not run yet)
     * - No matching [AssetManifestEntry.TilemapEntry] exists in the manifest
     * - The matching entry has [AssetManifestEntry.TilemapEntry.uniqueTileCount] == 0 (unknown)
     *
     * When [SceneIR.tilesetRef] is null, returns 0 — the scene uses no dedicated BG tileset.
     *
     * The [game] parameter is retained for potential future refinement (e.g. cross-referencing
     * [GameIR.assets] by path when no manifest is present).
     */
    @Suppress("UnusedParameter") // game retained for future refinement
    private fun estimateBgTiles(scene: SceneIR, game: GameIR, manifest: AssetManifest?): Int {
        val tilesetRef = scene.tilesetRef ?: return 0
        // Try to find actual unique tile count from the asset manifest
        val entry =
            manifest?.assets?.filterIsInstance<AssetManifestEntry.TilemapEntry>()?.firstOrNull {
                it.tilesetPath == tilesetRef.path || it.path == tilesetRef.path
            }
        if (entry != null && entry.uniqueTileCount > 0) {
            return entry.uniqueTileCount
        }
        return BG_TILES_DEFAULT_ESTIMATE
    }

    /**
     * Builds an actionable tile overflow [Diagnostic] for the given scene.
     *
     * The error message includes:
     * - Scene name
     * - Total tiles required vs. the 384-tile limit
     * - Breakdown by source: sprite tiles (with actor list), BG tiles, global tiles
     * - A splitting suggestion
     */
    @Suppress("LongParameterList") // All 6 params are required for a complete error message
    private fun buildTileOverflowError(
        scene: SceneIR,
        spriteTiles: Int,
        actors: List<ActorIR>,
        bgTiles: Int,
        globalTiles: Int,
        game: GameIR,
    ): Diagnostic {
        val total = spriteTiles + bgTiles + globalTiles
        val actorList = actors.joinToString(", ") { it.id }
        val tilesetList =
            game.assets.filter { it.type == AssetType.TILESET }.joinToString(", ") { it.path }

        val breakdown =
            buildString {
                    append("Sprites: $spriteTiles tile(s) from actor(s) [$actorList]. ")
                    if (bgTiles > 0)
                        append("Background: $bgTiles tile(s) from tileset(s) [$tilesetList]. ")
                    if (globalTiles > 0) append("Global (UI/fonts): $globalTiles tile(s). ")
                }
                .trimEnd()

        return Diagnostic(
            id = "ANLZ-03",
            severity = Severity.ERROR,
            message =
                "Scene '${scene.id}' requires $total VRAM tiles but the Game Boy limit is " +
                    "$TOTAL_VRAM_TILES. $breakdown",
            location = "scene '${scene.id}'",
            suggestion =
                "Consider splitting scene '${scene.id}' into sub-scenes with fewer active actors. " +
                    "Reduce actor sprite frame counts or use a smaller tileset.",
        )
    }
}
