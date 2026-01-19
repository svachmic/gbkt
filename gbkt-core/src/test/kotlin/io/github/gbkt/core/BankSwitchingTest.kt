/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.assets.BankAllocator
import io.github.gbkt.core.assets.PoParser
import io.github.gbkt.core.assets.TablesParser
import io.github.gbkt.core.ir.ColumnType
import io.github.gbkt.core.ir.GameString
import io.github.gbkt.core.ir.StringNamespace
import io.github.gbkt.core.ir.StringTable
import kotlin.test.*

class BankSwitchingTest {

    @Test
    fun `CompiledTileData has bank field with default -1`() {
        val tileData = CompiledTileData("test", listOf(ByteArray(16)), 1)
        assertEquals(-1, tileData.bank)
    }

    @Test
    fun `CompiledTileData can specify custom bank`() {
        val tileData = CompiledTileData("test", listOf(ByteArray(16)), 1, bank = 8)
        assertEquals(8, tileData.bank)
    }

    @Test
    fun `CompiledTileData calculates sizeBytes correctly`() {
        val tileData = CompiledTileData("test", listOf(ByteArray(16), ByteArray(16)), 2)
        assertEquals(32, tileData.sizeBytes)
    }

    @Test
    fun `CompiledMapData has bank field with default -1`() {
        val mapData = CompiledMapData("test", 10, 10, ByteArray(100), "tileset")
        assertEquals(-1, mapData.bank)
    }

    @Test
    fun `CompiledMapData can specify custom bank`() {
        val mapData = CompiledMapData("test", 10, 10, ByteArray(100), "tileset", bank = 9)
        assertEquals(9, mapData.bank)
    }

    @Test
    fun `CompiledMapData calculates sizeBytes correctly`() {
        val mapData = CompiledMapData("test", 10, 10, ByteArray(100), "tileset")
        assertEquals(100, mapData.sizeBytes)
    }

    @Test
    fun `CompiledMapData with collision data calculates combined sizeBytes`() {
        val mapData =
            CompiledMapData(
                "test",
                10,
                10,
                ByteArray(100),
                "tileset",
                collisionData = ByteArray(100),
            )
        assertEquals(200, mapData.sizeBytes)
    }

    @Test
    fun `BankAllocator allocates tiles to starting bank`() {
        val allocator = BankAllocator(spriteStartBank = 8)
        val tileData = listOf(CompiledTileData("sprite1", listOf(ByteArray(16)), 1))

        val result = allocator.allocateTileData(tileData)

        assertEquals(1, result.size)
        assertEquals(8, result[0].bank)
    }

    @Test
    fun `BankAllocator moves to next bank when current is full`() {
        val allocator = BankAllocator(spriteStartBank = 8, bankSize = 100)

        // First allocation uses most of bank 8
        val bank1 = allocator.allocateForTiles(90)
        assertEquals(8, bank1)

        // Second allocation should go to bank 9
        val bank2 = allocator.allocateForTiles(50)
        assertEquals(9, bank2)
    }

    @Test
    fun `BankAllocator allocates floor maps to dedicated banks`() {
        val allocator = BankAllocator(spriteStartBank = 8)
        val mapData =
            listOf(
                CompiledMapData("floor1", 32, 32, ByteArray(1024), "tileset"),
                CompiledMapData("floor2", 32, 32, ByteArray(1024), "tileset"),
                CompiledMapData("floor_3", 32, 32, ByteArray(1024), "tileset"),
            )

        val result = allocator.allocateMapData(mapData)

        assertEquals(3, result.size)
        assertEquals(8, result[0].bank) // floor1 -> bank 8
        assertEquals(9, result[1].bank) // floor2 -> bank 9
        assertEquals(10, result[2].bank) // floor_3 -> bank 10
    }

    @Test
    fun `PoParser parses namespace with bank hint`() {
        val content =
            """
            msgid ""
            msgstr ""
            "Language: en\n"

            #. @bank 3
            #. test namespace
            msgctxt "test"
            msgid "key1"
            msgstr "value1"

            msgctxt "test"
            msgid "key2"
            msgstr "value2"
            """
                .trimIndent()

        val table = PoParser.parse(content)

        assertEquals(1, table.namespaces.size)
        assertEquals("test", table.namespaces[0].name)
        assertEquals(3, table.namespaces[0].bank)
        assertEquals(2, table.namespaces[0].strings.size)
    }

    @Test
    fun `PoParser parses multiple namespaces`() {
        val content =
            """
            msgid ""
            msgstr ""
            "Language: en\n"

            #. @bank 0
            msgctxt "ns1"
            msgid "k1"
            msgstr "v1"

            #. @bank 1
            msgctxt "ns2"
            msgid "k2"
            msgstr "v2"

            msgctxt "ns2"
            msgid "k3"
            msgstr "v3"
            """
                .trimIndent()

        val table = PoParser.parse(content)

        assertEquals(2, table.namespaces.size)
        assertEquals("ns1", table.namespaces[0].name)
        assertEquals(0, table.namespaces[0].bank)
        assertEquals("ns2", table.namespaces[1].name)
        assertEquals(1, table.namespaces[1].bank)
    }

    @Test
    fun `StringTable groups namespaces by bank`() {
        val table =
            StringTable(
                listOf(
                    StringNamespace("a", 0, listOf(GameString("k1", "v1"))),
                    StringNamespace("b", 0, listOf(GameString("k2", "v2"))),
                    StringNamespace("c", 1, listOf(GameString("k3", "v3"))),
                )
            )

        val byBank = table.byBank
        assertEquals(2, byBank.size)
        assertEquals(2, byBank[0]?.size)
        assertEquals(1, byBank[1]?.size)
    }

    @Test
    fun `TablesParser parses simple CSV`() {
        val lines = listOf("exp_required,hp_base", "uint16_t,uint8_t", "0,10", "100,12", "250,14")

        val table = TablesParser.parse(lines, bank = 5)

        assertEquals(5, table.bank)
        assertEquals(2, table.columns.size)
        assertEquals("exp_required", table.columns[0].name)
        assertEquals(ColumnType.UINT16, table.columns[0].type)
        assertEquals(listOf(0, 100, 250), table.columns[0].values)
        assertEquals("hp_base", table.columns[1].name)
        assertEquals(ColumnType.UINT8, table.columns[1].type)
        assertEquals(listOf(10, 12, 14), table.columns[1].values)
    }

    @Test
    fun `TablesParser groups composite columns`() {
        val lines =
            listOf(
                "monster_hp_c,monster_hp_b,monster_hp_a,monster_hp_s",
                "uint8_t,uint8_t,uint8_t,uint8_t",
                "10,15,20,30",
                "12,18,24,36",
            )

        val table = TablesParser.parse(lines, bank = 5)

        // Should create a composite instead of 4 separate columns
        assertEquals(1, table.composites.size)
        assertEquals("monster_hp", table.composites[0].baseName)
        assertEquals(4, table.composites[0].columns.size)
        assertEquals(0, table.columns.size) // All columns should be in composite
    }

    @Test
    fun `CodeGenerator generates bank pragma for tile data`() {
        // Create a game with banked tile data
        val tileData = listOf(CompiledTileData("sprite1", listOf(ByteArray(16)), 1, bank = 8))
        val gameWithData =
            Game(
                name = "BankTest",
                config = GameConfig(),
                variables = emptyList(),
                sprites = emptyList(),
                scenes =
                    mapOf(
                        "main" to
                            io.github.gbkt.core.scene.Scene(
                                "main",
                                emptyList(),
                                emptyList(),
                                emptyList(),
                            )
                    ),
                startScene = "main",
                tileData = tileData,
            )

        // Use compileForTest to bypass validation
        val code = gameWithData.compileForTest()

        assertTrue(code.contains("#pragma bank 8"), "Should contain bank 8 pragma")
        assertTrue(code.contains("sprite1_tiles"), "Should contain sprite1_tiles")
    }

    @Test
    fun `CodeGenerator generates bank pragma for map data`() {
        val mapData = listOf(CompiledMapData("floor1", 10, 10, ByteArray(100), "tileset", bank = 9))
        val gameWithData =
            Game(
                name = "BankTest",
                config = GameConfig(),
                variables = emptyList(),
                sprites = emptyList(),
                scenes =
                    mapOf(
                        "main" to
                            io.github.gbkt.core.scene.Scene(
                                "main",
                                emptyList(),
                                emptyList(),
                                emptyList(),
                            )
                    ),
                startScene = "main",
                mapData = mapData,
            )

        val code = gameWithData.compileForTest()

        assertTrue(code.contains("#pragma bank 9"), "Should contain bank 9 pragma")
        assertTrue(code.contains("floor1_map"), "Should contain floor1_map")
    }

    @Test
    fun `CodeGenerator returns to home bank after data`() {
        val tileData = listOf(CompiledTileData("sprite1", listOf(ByteArray(16)), 1, bank = 8))
        val gameWithData =
            Game(
                name = "BankTest",
                config = GameConfig(),
                variables = emptyList(),
                sprites = emptyList(),
                scenes =
                    mapOf(
                        "main" to
                            io.github.gbkt.core.scene.Scene(
                                "main",
                                emptyList(),
                                emptyList(),
                                emptyList(),
                            )
                    ),
                startScene = "main",
                tileData = tileData,
            )

        val code = gameWithData.compileForTest()

        // Should have bank 8, then back to bank 0
        val bankLines = code.lines().filter { it.contains("#pragma bank ") }
        assertTrue(bankLines.any { it.contains("bank 8") }, "Should have bank 8")
        assertTrue(bankLines.any { it.contains("bank 0") }, "Should return to bank 0")
    }
}
