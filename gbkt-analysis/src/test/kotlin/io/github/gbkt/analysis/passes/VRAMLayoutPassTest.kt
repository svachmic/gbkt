/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.ResourceInventory
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.AssetManifest
import io.github.gbkt.core.AssetManifestEntry
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VRAMLayoutPassTest {

    private val pass = VRAMLayoutPass()

    private fun makeSprite(width: Int, height: Int) =
        SpriteDef(
            assetRef = AssetRef("sprite.png", AssetType.SPRITE),
            size = SizeDef(width, height),
        )

    /** Builds a PassContext with a pre-populated ResourceInventory. */
    private fun makeContext(
        game: GameIR,
        spriteTileCounts: Map<String, Int> = emptyMap(),
        config: AnalysisConfig = AnalysisConfig(maxBanks = 2),
    ): PassContext {
        val inventory =
            ResourceInventory(
                totalActors = game.actors.size,
                totalScenes = game.scenes.size,
                spriteTileCounts = spriteTileCounts,
                perSceneActorCounts =
                    game.scenes.associate { s -> s.id to s.actorIds.size }.filterValues { it > 0 },
            )
        return PassContext(
            game = game,
            profile = FakeProfile,
            config = config,
            inventory = inventory,
        )
    }

    // -------------------------------------------------------------------------
    // Empty scene
    // -------------------------------------------------------------------------

    @Test
    fun `empty scene uses no tiles`() {
        val scene = SceneIR(id = "empty")
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["empty"]
        assertNotNull(vram)
        // No sprite tiles, no global tiles → scene BG range starts at tile 0
        assertEquals(0, vram.startTile)
        assertEquals(0, vram.endTile) // 0 BG tiles used
    }

    // -------------------------------------------------------------------------
    // Sprite reservation
    // -------------------------------------------------------------------------

    @Test
    fun `sprite actors reserve VRAM tiles per scene`() {
        // 2 actors with 8x16 sprites = 2 tiles each → 4 sprite tiles total
        val actor1 = ActorIR(id = "a1", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val actor2 = ActorIR(id = "a2", position = PositionDef(10, 0), sprite = makeSprite(8, 16))
        val scene = SceneIR(id = "play", actorIds = listOf("a1", "a2"))
        val game = GameIR(name = "Test", scenes = listOf(scene), actors = listOf(actor1, actor2))
        // spriteTileCounts pre-populated: each 8x16 = 2 tiles
        val ctx = makeContext(game, spriteTileCounts = mapOf("a1" to 2, "a2" to 2))

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["play"]
        assertNotNull(vram)
        // Sprite tiles (4) come first; BG scene range starts after sprites
        assertEquals(4, vram.startTile)
    }

    // -------------------------------------------------------------------------
    // Scene within budget passes
    // -------------------------------------------------------------------------

    @Test
    fun `scene within budget passes`() {
        // 200 BG tiles (via tileset asset) + 24 sprite tiles = 224 total < 384
        val spriteActors =
            (1..12).map { i ->
                ActorIR(id = "a$i", position = PositionDef(i * 8, 0), sprite = makeSprite(8, 16))
            }
        val actorIds = spriteActors.map { it.id }
        val scene = SceneIR(id = "gameplay", actorIds = actorIds)
        // Add a tileset asset with metadata indicating 200 tiles
        val tileset = AssetRef("level1.png", AssetType.TILESET)
        val game =
            GameIR(
                name = "Test",
                scenes = listOf(scene),
                actors = spriteActors,
                assets = listOf(tileset),
            )
        // 12 actors × 2 tiles each = 24 sprite tiles
        val spriteTileCounts = spriteActors.associate { it.id to 2 }
        val ctx = makeContext(game, spriteTileCounts = spriteTileCounts)

        val result = pass.run(ctx)

        // Should succeed — 24 sprite + 0 global (no font) + heuristic BG < 384
        assertIs<PassResult.Success>(result)
        assertTrue(result.context.diagnostics.none { it.severity == Severity.ERROR })
    }

    // -------------------------------------------------------------------------
    // Tile overflow error
    // -------------------------------------------------------------------------

    @Test
    fun `scene exceeding 384 tiles fails with actionable error`() {
        // 300 BG tile-equivalents via 300-tile tileset + 100 sprite tiles
        // We model 100 sprite tiles as 50 actors with 8x16 sprites (2 tiles each)
        val spriteActors =
            (1..50).map { i ->
                ActorIR(
                    id = "a$i",
                    position = PositionDef(i.rem(20) * 8, 0),
                    sprite = makeSprite(8, 16),
                )
            }
        val actorIds = spriteActors.map { it.id }
        val scene = SceneIR(id = "overflow", actorIds = actorIds)
        val game =
            GameIR(
                name = "Test",
                scenes = listOf(scene),
                actors = spriteActors,
                // Provide enough tileset assets to push BG tiles over budget:
                // We'll use a custom config with no warning threshold to trigger overflow cleanly.
                assets =
                    listOf(
                        AssetRef("bg1.png", AssetType.TILESET),
                        AssetRef("bg2.png", AssetType.TILESET),
                        AssetRef("bg3.png", AssetType.TILESET),
                    ),
            )
        // 50 actors × 2 tiles = 100 sprite tiles
        val spriteTileCounts = spriteActors.associate { it.id to 2 }
        // Force overflow by using a tiny tile budget config
        val config =
            AnalysisConfig(maxBanks = 2, vramTileWarningThreshold = 1, vramTileErrorThreshold = 1)
        val inventory =
            ResourceInventory(
                totalActors = game.actors.size,
                totalScenes = game.scenes.size,
                spriteTileCounts = spriteTileCounts,
                perSceneActorCounts = mapOf("overflow" to 50),
            )
        val ctx =
            PassContext(game = game, profile = FakeProfile, config = config, inventory = inventory)

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        val errorDiag = result.diagnostics.first { it.severity == Severity.ERROR }
        // Error must include the scene name
        assertTrue(
            errorDiag.message.contains("overflow"),
            "Error message should contain scene name 'overflow' but was: ${errorDiag.message}",
        )
        // Error must include a breakdown (sprite tiles count)
        assertTrue(
            errorDiag.message.contains("sprite", ignoreCase = true) ||
                errorDiag.message.lowercase().contains("actor"),
            "Error message should mention sprite/actor tile breakdown but was: ${errorDiag.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Splitting suggestion
    // -------------------------------------------------------------------------

    @Test
    fun `error message includes splitting suggestion`() {
        val actor = ActorIR(id = "a1", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val scene = SceneIR(id = "busy", actorIds = listOf("a1"))
        val game = GameIR(name = "Test", scenes = listOf(scene), actors = listOf(actor))
        val config =
            AnalysisConfig(maxBanks = 2, vramTileWarningThreshold = 1, vramTileErrorThreshold = 1)
        val inventory =
            ResourceInventory(
                totalActors = 1,
                totalScenes = 1,
                spriteTileCounts = mapOf("a1" to 2),
                perSceneActorCounts = mapOf("busy" to 1),
            )
        val ctx =
            PassContext(game = game, profile = FakeProfile, config = config, inventory = inventory)

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        val errorDiag = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            errorDiag.message.contains("split", ignoreCase = true) ||
                errorDiag.suggestion?.contains("split", ignoreCase = true) == true,
            "Error/suggestion should contain 'split' but message was: ${errorDiag.message}, suggestion: ${errorDiag.suggestion}",
        )
    }

    // -------------------------------------------------------------------------
    // Global tiles reduce budget
    // -------------------------------------------------------------------------

    @Test
    fun `global tiles reduce available budget per scene`() {
        // 36 font tiles globally — each scene's BG range starts at 36
        val font = AssetRef("font.png", AssetType.FONT)
        val scene = SceneIR(id = "main")
        val game = GameIR(name = "Test", scenes = listOf(scene), assets = listOf(font))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["main"]
        assertNotNull(vram)
        // Font = 36 global tiles → BG scene range starts after global tiles
        assertEquals(36, vram.startTile)
    }

    // -------------------------------------------------------------------------
    // Warning threshold
    // -------------------------------------------------------------------------

    @Test
    fun `tile warning produced at warning threshold`() {
        // Scene using exactly 351 tiles (1 above warning threshold of 350)
        // Use 175 sprite actors with 8x8 = 1 tile each → 175 sprite tiles + 36 font = 211 total
        // We need a way to push over 350 — use a custom threshold of 1
        val actor = ActorIR(id = "a1", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val scene = SceneIR(id = "heavy", actorIds = listOf("a1"))
        val font = AssetRef("font.png", AssetType.FONT)
        val game =
            GameIR(
                name = "Test",
                scenes = listOf(scene),
                actors = listOf(actor),
                assets = listOf(font),
            )
        // Warning threshold = 1, error threshold = 1000 (so warning triggers but not error)
        val config =
            AnalysisConfig(
                maxBanks = 2,
                vramTileWarningThreshold = 1,
                vramTileErrorThreshold = 1000,
            )
        val inventory =
            ResourceInventory(
                totalActors = 1,
                totalScenes = 1,
                spriteTileCounts = mapOf("a1" to 2),
                perSceneActorCounts = mapOf("heavy" to 1),
            )
        val ctx =
            PassContext(game = game, profile = FakeProfile, config = config, inventory = inventory)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.isNotEmpty(),
            "Expected at least one WARNING diagnostic but got: ${result.context.diagnostics}",
        )
    }

    // -------------------------------------------------------------------------
    // Multiple scenes with different budgets
    // -------------------------------------------------------------------------

    @Test
    fun `multiple scenes have different sprite-based budgets`() {
        // Scene A has 3 actors (6 sprite tiles), Scene B has 1 actor (2 sprite tiles)
        val a1 = ActorIR(id = "a1", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val a2 = ActorIR(id = "a2", position = PositionDef(10, 0), sprite = makeSprite(8, 16))
        val a3 = ActorIR(id = "a3", position = PositionDef(20, 0), sprite = makeSprite(8, 16))
        val b1 = ActorIR(id = "b1", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val sceneA = SceneIR(id = "sceneA", actorIds = listOf("a1", "a2", "a3"))
        val sceneB = SceneIR(id = "sceneB", actorIds = listOf("b1"))
        val game =
            GameIR(name = "Test", scenes = listOf(sceneA, sceneB), actors = listOf(a1, a2, a3, b1))
        val spriteTileCounts = mapOf("a1" to 2, "a2" to 2, "a3" to 2, "b1" to 2)
        val ctx = makeContext(game, spriteTileCounts = spriteTileCounts)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vramA = result.context.vramAssignments["sceneA"]
        val vramB = result.context.vramAssignments["sceneB"]
        assertNotNull(vramA)
        assertNotNull(vramB)
        // Scene A's BG range starts after 6 sprite tiles; Scene B's after 2 sprite tiles
        assertEquals(6, vramA.startTile) // 3 actors × 2 tiles each
        assertEquals(2, vramB.startTile) // 1 actor × 2 tiles
    }

    // -------------------------------------------------------------------------
    // VRAMRange written to vramAssignments
    // -------------------------------------------------------------------------

    @Test
    fun `VRAMRange start and end indices written to vramAssignments`() {
        val actor = ActorIR(id = "player", position = PositionDef(0, 0), sprite = makeSprite(8, 16))
        val scene = SceneIR(id = "game", actorIds = listOf("player"))
        val game = GameIR(name = "Test", scenes = listOf(scene), actors = listOf(actor))
        val ctx = makeContext(game, spriteTileCounts = mapOf("player" to 2))

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        // Scene VRAM range: startTile = 2 (after 2 sprite tiles), endTile = 2 (0 BG tiles used)
        val sceneVram = result.context.vramAssignments["game"]
        assertNotNull(sceneVram)
        assertEquals(2, sceneVram.startTile) // after 2 sprite tiles
        // Actor VRAM range: starts at 0, ends at 2
        val actorVram = result.context.vramAssignments["player"]
        assertNotNull(actorVram)
        assertEquals(0, actorVram.startTile)
        assertEquals(2, actorVram.endTile)
    }

    // -------------------------------------------------------------------------
    // BG-tile overflow via tilesetRef
    // -------------------------------------------------------------------------

    @Test
    fun `scene with tilesetRef exceeding budget fails with BG tile overflow error`() {
        // 256 BG tiles (default estimate) alone exceeds a threshold of 200
        val scene = SceneIR(id = "dungeon", tilesetRef = AssetRef("dungeon.png", AssetType.TILESET))
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val config =
            AnalysisConfig(
                maxBanks = 2,
                vramTileWarningThreshold = 100,
                vramTileErrorThreshold = 200,
            )
        val ctx = makeContext(game, config = config)

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        val errorDiag = result.diagnostics.first { it.severity == Severity.ERROR }
        // Error must name the scene
        assertTrue(
            errorDiag.message.contains("dungeon"),
            "Error message should contain scene name 'dungeon' but was: ${errorDiag.message}",
        )
        // Error must mention background tiles in the breakdown
        assertTrue(
            errorDiag.message.contains("Background", ignoreCase = true) ||
                errorDiag.message.contains("BG", ignoreCase = true),
            "Error message should mention background tiles but was: ${errorDiag.message}",
        )
        // Error must include a splitting suggestion
        assertTrue(
            errorDiag.message.contains("split", ignoreCase = true) ||
                errorDiag.suggestion?.contains("split", ignoreCase = true) == true,
            "Error/suggestion should mention splitting but was: message=${errorDiag.message}, " +
                "suggestion=${errorDiag.suggestion}",
        )
    }

    @Test
    fun `scene with tilesetRef within budget passes`() {
        // 256 BG tiles (default estimate) is within the default threshold of 384
        val scene = SceneIR(id = "simple", tilesetRef = AssetRef("simple.png", AssetType.TILESET))
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        assertTrue(result.context.diagnostics.none { it.severity == Severity.ERROR })
        val vram = result.context.vramAssignments["simple"]
        assertNotNull(vram)
        // No sprite tiles, no global tiles: BG range is [0 .. BG_TILES_DEFAULT_ESTIMATE]
        assertEquals(0, vram.startTile)
        assertEquals(VRAMLayoutPass.BG_TILES_DEFAULT_ESTIMATE, vram.endTile)
    }

    // -------------------------------------------------------------------------
    // Missing inventory guard
    // -------------------------------------------------------------------------

    @Test
    fun `fails with error when ResourceInventory is missing`() {
        val scene = SceneIR(id = "s1")
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val ctx =
            PassContext(
                game = game,
                profile = FakeProfile,
                config = AnalysisConfig(maxBanks = 2),
                inventory = null,
            )

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        assertTrue(result.diagnostics.any { it.severity == Severity.ERROR })
    }

    // -------------------------------------------------------------------------
    // Manifest-aware BG tile estimation (J3)
    // -------------------------------------------------------------------------

    @Test
    fun `estimateBgTiles returns manifest uniqueTileCount when manifest has matching entry`() {
        // Manifest has TilemapEntry with tilesetPath matching the scene's tilesetRef,
        // uniqueTileCount=128
        val tilesetPath = "dungeon.png"
        val scene = SceneIR(id = "dungeon", tilesetRef = AssetRef(tilesetPath, AssetType.TILESET))
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val tilemapEntry =
            AssetManifestEntry.TilemapEntry(
                path = "dungeon_map.tmx",
                width = 32,
                height = 32,
                hasCollision = false,
                tilesetPath = tilesetPath,
                uniqueTileCount = 128,
            )
        val manifest = AssetManifest(assets = listOf(tilemapEntry))
        val ctx = makeContext(game).copy(assetManifest = manifest)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["dungeon"]
        assertNotNull(vram)
        // No sprites, no global tiles → BG range is [0 .. 128]
        assertEquals(0, vram.startTile)
        assertEquals(128, vram.endTile) // manifest uniqueTileCount used, not 256
    }

    @Test
    fun `estimateBgTiles falls back to 256 when manifest is null`() {
        // No manifest → falls back to BG_TILES_DEFAULT_ESTIMATE = 256
        val scene = SceneIR(id = "level", tilesetRef = AssetRef("level.png", AssetType.TILESET))
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val ctx = makeContext(game) // assetManifest is null by default

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["level"]
        assertNotNull(vram)
        // Should use default estimate
        assertEquals(VRAMLayoutPass.BG_TILES_DEFAULT_ESTIMATE, vram.endTile)
    }

    @Test
    fun `estimateBgTiles falls back to 256 when manifest has no matching entry`() {
        // Manifest exists but has no entry for the scene's tileset
        val scene = SceneIR(id = "cave", tilesetRef = AssetRef("cave.png", AssetType.TILESET))
        val game = GameIR(name = "Test", scenes = listOf(scene))
        val unrelatedEntry =
            AssetManifestEntry.TilemapEntry(
                path = "forest_map.tmx",
                width = 20,
                height = 20,
                hasCollision = false,
                tilesetPath = "forest.png", // different tileset
                uniqueTileCount = 64,
            )
        val manifest = AssetManifest(assets = listOf(unrelatedEntry))
        val ctx = makeContext(game).copy(assetManifest = manifest)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val vram = result.context.vramAssignments["cave"]
        assertNotNull(vram)
        // No matching entry → falls back to default
        assertEquals(VRAMLayoutPass.BG_TILES_DEFAULT_ESTIMATE, vram.endTile)
    }
}
