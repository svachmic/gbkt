/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LdtkParserTest {

    private fun minimalLdtkJson(layers: String): String =
        """
        {
          "jsonVersion": "1.5.3",
          "defaultGridSize": 8,
          "levels": [
            {
              "identifier": "Level_0",
              "layerInstances": [$layers]
            }
          ]
        }
    """
            .trimIndent()

    private fun tilesLayerJson(
        identifier: String = "Tiles",
        gridSize: Int = 8,
        cWid: Int = 4,
        cHei: Int = 4,
        gridTiles: String = "",
        fieldInstances: String = "",
    ): String {
        val gridTilesField = if (gridTiles.isNotEmpty()) gridTiles else "[]"
        val fieldInstancesField = if (fieldInstances.isNotEmpty()) "[$fieldInstances]" else "[]"
        return """
            {
              "__identifier": "$identifier",
              "__type": "Tiles",
              "__gridSize": $gridSize,
              "__cWid": $cWid,
              "__cHei": $cHei,
              "intGridCsv": [],
              "gridTiles": $gridTilesField,
              "autoLayerTiles": [],
              "fieldInstances": $fieldInstancesField
            }
        """
            .trimIndent()
    }

    private fun intGridLayerJson(
        identifier: String = "IntGrid",
        cWid: Int = 4,
        cHei: Int = 4,
        intGridCsv: List<Int> = emptyList(),
        fieldInstances: String = "",
    ): String {
        val csvData = intGridCsv.joinToString(",")
        val fieldInstancesField = if (fieldInstances.isNotEmpty()) "[$fieldInstances]" else "[]"
        return """
            {
              "__identifier": "$identifier",
              "__type": "IntGrid",
              "__gridSize": 8,
              "__cWid": $cWid,
              "__cHei": $cHei,
              "intGridCsv": [$csvData],
              "gridTiles": [],
              "autoLayerTiles": [],
              "fieldInstances": $fieldInstancesField
            }
        """
            .trimIndent()
    }

    @Test
    fun `parse minimal LDtk JSON with one Tiles layer - correct dimensions and tile count`() {
        val gridTiles = """[{"px": [0, 0], "t": 5, "f": 0}, {"px": [8, 0], "t": 6, "f": 0}]"""
        val layer =
            tilesLayerJson(identifier = "Background", cWid = 4, cHei = 4, gridTiles = gridTiles)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertEquals(8, map.tileSize)
        assertEquals(1, map.layers.size)
        val bg = map.layers.first()
        assertEquals("Background", bg.identifier)
        assertEquals("Tiles", bg.type)
        assertEquals(4, bg.cWid)
        assertEquals(4, bg.cHei)
        assertEquals(2, bg.gridTiles.size)
    }

    @Test
    fun `parse IntGrid layer - intGridCsv values read correctly`() {
        val csvValues = listOf(0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0)
        val layer =
            intGridLayerJson(identifier = "Collision", cWid = 4, cHei = 4, intGridCsv = csvValues)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertEquals(1, map.layers.size)
        val collisionLayer = map.layers.first()
        assertEquals("IntGrid", collisionLayer.type)
        assertEquals(csvValues, collisionLayer.intGridCsv)
    }

    @Test
    fun `layer with gbkt_collision=true in fieldInstances detected as collision`() {
        val fieldInstance =
            """{"__identifier": "gbkt_collision", "__type": "Bool", "__value": true}"""
        val layer = tilesLayerJson(identifier = "CollisionTiles", fieldInstances = fieldInstance)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertTrue(
            map.layers.first().isCollision,
            "Layer with gbkt_collision=true field should be collision layer",
        )
    }

    @Test
    fun `layer without fieldInstances has isCollision == false`() {
        val layer = tilesLayerJson(identifier = "Background")
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertFalse(
            map.layers.first().isCollision,
            "Layer without fieldInstances should not be collision layer",
        )
    }

    @Test
    fun `layer with gbkt_collision=false has isCollision == false`() {
        val fieldInstance =
            """{"__identifier": "gbkt_collision", "__type": "Bool", "__value": false}"""
        val layer = tilesLayerJson(identifier = "Background", fieldInstances = fieldInstance)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertFalse(
            map.layers.first().isCollision,
            "Layer with gbkt_collision=false should not be collision layer",
        )
    }

    @Test
    fun `unsupported jsonVersion throws clear error`() {
        val json =
            """
            {
              "jsonVersion": "0.9.3",
              "defaultGridSize": 8,
              "levels": [{"identifier": "Level_0", "layerInstances": []}]
            }
            """
                .trimIndent()
        val ex = assertFailsWith<IllegalArgumentException> { LdtkParser.parse(json) }
        assertTrue(
            ex.message?.contains("0.9.3") == true,
            "Error message should mention the unsupported version",
        )
    }

    @Test
    fun `gridSize is read from __gridSize field of layer`() {
        val layer = tilesLayerJson(identifier = "Tiles16", gridSize = 16, cWid = 2, cHei = 2)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        assertEquals(16, map.layers.first().gridSize)
    }

    @Test
    fun `tile placements have correct px coordinates and tile id`() {
        val gridTiles = """[{"px": [16, 24], "t": 42, "f": 0}]"""
        val layer = tilesLayerJson(gridTiles = gridTiles)
        val map = LdtkParser.parse(minimalLdtkJson(layer))
        val placement = map.layers.first().gridTiles.first()
        assertEquals(Pair(16, 24), placement.px)
        assertEquals(42, placement.t)
    }
}
