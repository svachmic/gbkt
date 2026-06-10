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
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.CollElementType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IRCollFixedSlots
import io.github.gbkt.core.ir.IRCollHashTable
import io.github.gbkt.core.ir.IRCollPool
import io.github.gbkt.core.ir.IRCollRingBuffer
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResourceInventoryPassTest {

    private val pass = ResourceInventoryPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 2))

    @Test
    fun `inventory counts actors, scenes, variables`() {
        val actors =
            listOf(
                ActorIR(id = "player", position = PositionDef(0, 0)),
                ActorIR(id = "enemy", position = PositionDef(10, 10)),
            )
        val scenes = listOf(SceneIR(id = "main"), SceneIR(id = "pause"), SceneIR(id = "gameover"))
        val variables =
            listOf(
                VariableDef("score", VarType.U16, 0),
                VariableDef("lives", VarType.U8, 3),
                VariableDef("health", VarType.U8, 100),
                VariableDef("speed", VarType.I8, 2),
            )
        val game = GameIR(name = "Test", actors = actors, scenes = scenes, variables = variables)

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(2, inventory.totalActors)
        assertEquals(3, inventory.totalScenes)
        assertEquals(4, inventory.totalVariables)
    }

    @Test
    fun `sprite tile count calculated from SpriteDef size`() {
        // 8x16 sprite = (8/8) * (16/8) = 1 * 2 = 2 tiles
        val sprite8x16 =
            SpriteDef(assetRef = AssetRef("player.png", AssetType.SPRITE), size = SizeDef(8, 16))
        // 16x16 sprite = (16/8) * (16/8) = 2 * 2 = 4 tiles
        val sprite16x16 =
            SpriteDef(assetRef = AssetRef("enemy.png", AssetType.SPRITE), size = SizeDef(16, 16))
        val actors =
            listOf(
                ActorIR(id = "player", position = PositionDef(0, 0), sprite = sprite8x16),
                ActorIR(id = "enemy", position = PositionDef(10, 10), sprite = sprite16x16),
            )
        val game = GameIR(name = "Test", actors = actors)

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(2, inventory.spriteTileCounts["player"])
        assertEquals(4, inventory.spriteTileCounts["enemy"])
    }

    @Test
    fun `empty game produces zero inventory`() {
        val game = GameIR(name = "Empty")

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(0, inventory.totalActors)
        assertEquals(0, inventory.totalScenes)
        assertEquals(0, inventory.totalVariables)
        assertEquals(0, inventory.totalAssets)
        assertEquals(0, inventory.variableBytes)
        assertEquals(0, inventory.collectionBytes)
        assertEquals(emptyMap<String, Int>(), inventory.spriteTileCounts)
        assertEquals(emptyMap<String, Int>(), inventory.perSceneActorCounts)
    }

    @Test
    fun `variable bytes summed correctly`() {
        // U8=1, U16=2, I8=1, I16=2
        val variables =
            listOf(
                VariableDef("a", VarType.U8, 0), // 1 byte
                VariableDef("b", VarType.U16, 0), // 2 bytes
                VariableDef("c", VarType.I8, 0), // 1 byte
                VariableDef("d", VarType.I16, 0), // 2 bytes
            )
        val game = GameIR(name = "Test", variables = variables)

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(6, inventory.variableBytes) // 1+2+1+2
    }

    @Test
    fun `per scene actor counts populated`() {
        val actors =
            listOf(
                ActorIR(id = "player", position = PositionDef(0, 0)),
                ActorIR(id = "enemy1", position = PositionDef(10, 0)),
                ActorIR(id = "enemy2", position = PositionDef(20, 0)),
            )
        val scenes =
            listOf(
                SceneIR(id = "gameplay", actorIds = listOf("player", "enemy1", "enemy2")),
                SceneIR(id = "menu", actorIds = listOf("player")),
            )
        val game = GameIR(name = "Test", actors = actors, scenes = scenes)

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(3, inventory.perSceneActorCounts["gameplay"])
        assertEquals(1, inventory.perSceneActorCounts["menu"])
    }

    @Test
    fun `actor without sprite has zero tile count`() {
        val actors = listOf(ActorIR(id = "invisible", position = PositionDef(0, 0), sprite = null))
        val game = GameIR(name = "Test", actors = actors)

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(0, inventory.spriteTileCounts["invisible"] ?: 0)
    }

    // =========================================================================
    // Collection memory accounting tests (Plan 06-06)
    // =========================================================================

    @Test
    fun `collection bytes is zero when no collections declared`() {
        val game = GameIR(name = "Test")

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        assertEquals(0, result.context.inventory?.collectionBytes)
    }

    @Test
    fun `hash table collection bytes computed correctly`() {
        // 16 slots * (keyType.byteSize=1 + valueType.byteSize=1 + 1) = 16 * 3 = 48 bytes
        val ht =
            IRCollHashTable(
                name = "scores",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U8),
                size = 16,
            )
        val game = GameIR(name = "Test", hashTables = listOf(ht))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(48, inventory.collectionBytes)
    }

    @Test
    fun `pool collection bytes computed correctly`() {
        // capacity=8, elementType=U8 (1 byte)
        // data[8] = 8 bytes, bitmap[ceil(8/8)=1] = 1 byte, count = 1 byte
        // total = 8 + 1 + 1 = 10 bytes
        val pool =
            IRCollPool(
                name = "bullets",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 8,
            )
        val game = GameIR(name = "Test", pools = listOf(pool))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(10, inventory.collectionBytes)
    }

    @Test
    fun `ring buffer collection bytes computed correctly`() {
        // capacity=4, elementType=U8 (1 byte)
        // buf[4] = 4 bytes + head(1) + tail(1) + count(1) = 7 bytes
        val rb =
            IRCollRingBuffer(
                name = "events",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 4,
            )
        val game = GameIR(name = "Test", ringBuffers = listOf(rb))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(7, inventory.collectionBytes)
    }

    @Test
    fun `fixed slots collection bytes uses UINT8 bitfield for 8 or fewer slots`() {
        // count=8, elementType=U8 (1 byte), <= 8 slots so UINT8 bitfield (1 byte)
        // data[8] = 8 bytes + active(1 byte) = 9 bytes
        val fs =
            IRCollFixedSlots(
                name = "powerups",
                elementType = CollElementType.Primitive(VarType.U8),
                count = 8,
            )
        val game = GameIR(name = "Test", fixedSlots = listOf(fs))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(9, inventory.collectionBytes)
    }

    @Test
    fun `fixed slots collection bytes uses UINT16 bitfield for 9 to 16 slots`() {
        // count=12, elementType=U8 (1 byte), > 8 slots so UINT16 bitfield (2 bytes)
        // data[12] = 12 bytes + active(2 bytes) = 14 bytes
        val fs =
            IRCollFixedSlots(
                name = "slots",
                elementType = CollElementType.Primitive(VarType.U8),
                count = 12,
            )
        val game = GameIR(name = "Test", fixedSlots = listOf(fs))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertEquals(14, inventory.collectionBytes)
    }

    @Test
    fun `collection bytes sums across multiple collections`() {
        // hashtable: 8 slots * (U8=1 + U8=1 + 1) = 8 * 3 = 24 bytes
        // pool: capacity=4, data=4, bitmap=ceil(4/8)=1, count=1 -> 4 + 1 + 1 = 6 bytes
        // total = 30 bytes
        val ht =
            IRCollHashTable(
                name = "ht",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Primitive(VarType.U8),
                size = 8,
            )
        val pool =
            IRCollPool(
                name = "pool",
                elementType = CollElementType.Primitive(VarType.U8),
                capacity = 4,
            )
        val game = GameIR(name = "Test", hashTables = listOf(ht), pools = listOf(pool))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val inventory = result.context.inventory
        assertNotNull(inventory)
        assertTrue(inventory.collectionBytes > 0, "Expected non-zero collection bytes")
        assertEquals(30, inventory.collectionBytes)
    }
}
