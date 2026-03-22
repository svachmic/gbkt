/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TiledParserTest {

    private fun minimalTiledMapJson(layers: String): String =
        """
        {
          "width": 8,
          "height": 8,
          "tilewidth": 8,
          "tileheight": 8,
          "tilesets": [
            {
              "name": "tileset",
              "firstgid": 1,
              "tilecount": 16,
              "tilewidth": 8,
              "tileheight": 8,
              "image": "",
              "columns": 4
            }
          ],
          "layers": [$layers]
        }
    """
            .trimIndent()

    private fun tileLayerJson(name: String, properties: String = ""): String {
        val propsField = if (properties.isNotEmpty()) ""","properties": [$properties]""" else ""
        val data = (1..64).joinToString(",")
        return """
            {
              "type": "tilelayer",
              "name": "$name",
              "width": 8,
              "height": 8,
              "visible": true,
              "data": [$data]
              $propsField
            }
        """
            .trimIndent()
    }

    @Test
    fun `layer with gbkt_collision=true property detected via isCollisionLayer`() {
        val layerJson =
            tileLayerJson(
                name = "collision",
                properties = """{"name": "gbkt_collision", "type": "bool", "value": true}""",
            )
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertTrue(
            layer.isCollisionLayer,
            "Layer with gbkt_collision=true should be collision layer",
        )
    }

    @Test
    fun `layer with no properties has isCollisionLayer == false`() {
        val layerJson = tileLayerJson(name = "background")
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertFalse(
            layer.isCollisionLayer,
            "Layer with no properties should not be collision layer",
        )
    }

    @Test
    fun `layer with gbkt_collision=false has isCollisionLayer == false`() {
        val layerJson =
            tileLayerJson(
                name = "background",
                properties = """{"name": "gbkt_collision", "type": "bool", "value": false}""",
            )
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertFalse(
            layer.isCollisionLayer,
            "Layer with gbkt_collision=false should not be collision layer",
        )
    }

    @Test
    fun `multiple property types parsed correctly - bool, int, string`() {
        val layerJson =
            tileLayerJson(
                name = "test",
                properties =
                    """
                    {"name": "gbkt_collision", "type": "bool", "value": true},
                    {"name": "z_index", "type": "int", "value": 2},
                    {"name": "layer_name", "type": "string", "value": "foreground"}
                    """
                        .trimIndent(),
            )
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertEquals(true, layer.properties["gbkt_collision"])
        assertEquals(2, layer.properties["z_index"])
        assertEquals("foreground", layer.properties["layer_name"])
    }

    @Test
    fun `layer with string property but no gbkt_collision has isCollisionLayer == false`() {
        val layerJson =
            tileLayerJson(
                name = "foreground",
                properties = """{"name": "layer_type", "type": "string", "value": "decoration"}""",
            )
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertFalse(layer.isCollisionLayer)
    }

    @Test
    fun `properties map is empty by default for layer without properties field`() {
        val layerJson = tileLayerJson(name = "background")
        val map = TiledParser.parseContent(minimalTiledMapJson(layerJson))
        val layer = map.layers.first()
        assertTrue(
            layer.properties.isEmpty(),
            "Properties should be empty for layer with no properties field",
        )
    }
}
