/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GameMetadataTest {

    private val sampleJson =
        """
        {
          "scenes": {
            "gameover": 0,
            "game": 1,
            "title": 2
          },
          "actors": [
            {
              "name": "paddle1",
              "oamStart": 0,
              "oamCount": 2,
              "spriteWidth": 4,
              "spriteHeight": 16,
              "vars": { "x": "paddle1_x", "y": "paddle1_y" }
            },
            {
              "name": "paddle2",
              "oamStart": 2,
              "oamCount": 2,
              "spriteWidth": 4,
              "spriteHeight": 16,
              "vars": { "x": "paddle2_x", "y": "paddle2_y" }
            },
            {
              "name": "ball",
              "oamStart": 4,
              "oamCount": 1,
              "spriteWidth": 4,
              "spriteHeight": 4,
              "vars": { "x": "ball_x", "y": "ball_y" }
            }
          ]
        }
        """
            .trimIndent()

    @Test
    fun `fromJsonString parses scenes and actors`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertEquals(3, metadata.actors.size)
        assertEquals(0, metadata.scenes.indexOf("gameover"))
        assertEquals(1, metadata.scenes.indexOf("game"))
        assertEquals(2, metadata.scenes.indexOf("title"))
    }

    @Test
    fun `actorForSlot resolves single-tile actor`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertEquals("ball", metadata.actorForSlot(4))
    }

    @Test
    fun `actorForSlot resolves multi-tile actor`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertEquals("paddle1", metadata.actorForSlot(0))
        assertEquals("paddle1", metadata.actorForSlot(1))
        assertEquals("paddle2", metadata.actorForSlot(2))
        assertEquals("paddle2", metadata.actorForSlot(3))
    }

    @Test
    fun `actorForSlot returns null for unassigned slot`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertNull(metadata.actorForSlot(5))
        assertNull(metadata.actorForSlot(39))
    }

    @Test
    fun `actor lookup by name`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        val ball = metadata.actor("ball")
        assertNotNull(ball)
        assertEquals("ball", ball!!.name)
        assertEquals(4, ball.oamStart)
        assertEquals(1, ball.oamCount)
        assertEquals(4, ball.spriteWidth)
        assertEquals(4, ball.spriteHeight)
        assertEquals("ball_x", ball.xVar)
        assertEquals("ball_y", ball.yVar)

        assertNull(metadata.actor("nonexistent"))
    }

    @Test
    fun `scenes accessible as SceneMap`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertEquals("title", metadata.scenes.nameOf(2))
        assertEquals(1, metadata.scenes.indexOf("game"))
    }

    // ── Variables, texts, terminalScenes parsing ─────────────────────────────

    private val fullJson =
        """
        {
          "scenes": { "title": 0, "game": 1, "gameover": 2 },
          "actors": [],
          "variables": [
            { "name": "score", "type": "U8", "semantic": "score" },
            { "name": "ballDx", "type": "I8" }
          ],
          "texts": ["PONG", "PRESS START", "GAME OVER"],
          "terminalScenes": ["gameover"],
          "controls": {
            "game": [
              { "button": "UP", "type": "held" },
              { "button": "DOWN", "type": "held" }
            ],
            "title": [
              { "button": "START", "type": "pressed" }
            ]
          },
          "transitions": [
            { "from": "title", "to": "game" },
            { "from": "game", "to": "gameover" }
          ]
        }
        """
            .trimIndent()

    @Test
    fun `fromJsonString parses variables`() {
        val metadata = GameMetadata.fromJsonString(fullJson)

        assertEquals(2, metadata.variables.size)
        assertEquals("score", metadata.variables[0].name)
        assertEquals("U8", metadata.variables[0].type)
        assertEquals("score", metadata.variables[0].semantic)
        assertEquals("ballDx", metadata.variables[1].name)
        assertEquals("I8", metadata.variables[1].type)
        assertEquals("unknown", metadata.variables[1].semantic)
    }

    @Test
    fun `fromJsonString parses texts`() {
        val metadata = GameMetadata.fromJsonString(fullJson)

        assertEquals(3, metadata.texts.size)
        assertEquals("PONG", metadata.texts[0])
        assertEquals("PRESS START", metadata.texts[1])
        assertEquals("GAME OVER", metadata.texts[2])
    }

    @Test
    fun `fromJsonString parses terminalScenes`() {
        val metadata = GameMetadata.fromJsonString(fullJson)

        assertEquals(setOf("gameover"), metadata.terminalScenes)
    }

    @Test
    fun `missing variables defaults to empty`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertTrue(metadata.variables.isEmpty())
    }

    @Test
    fun `missing texts defaults to empty`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertTrue(metadata.texts.isEmpty())
    }

    @Test
    fun `missing terminalScenes defaults to empty`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertTrue(metadata.terminalScenes.isEmpty())
    }

    @Test
    fun `fromJsonString parses controls`() {
        val metadata = GameMetadata.fromJsonString(fullJson)

        assertEquals(2, metadata.controls.size)
        val gameControls = metadata.controls["game"]!!
        assertEquals(2, gameControls.size)
        assertEquals("UP", gameControls[0].button)
        assertEquals("held", gameControls[0].type)
        assertEquals("DOWN", gameControls[1].button)
        val titleControls = metadata.controls["title"]!!
        assertEquals(1, titleControls.size)
        assertEquals("START", titleControls[0].button)
        assertEquals("pressed", titleControls[0].type)
    }

    @Test
    fun `fromJsonString parses transitions`() {
        val metadata = GameMetadata.fromJsonString(fullJson)

        assertEquals(2, metadata.transitions.size)
        assertEquals("title", metadata.transitions[0].from)
        assertEquals("game", metadata.transitions[0].to)
        assertEquals("game", metadata.transitions[1].from)
        assertEquals("gameover", metadata.transitions[1].to)
    }

    @Test
    fun `missing controls defaults to empty map`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertTrue(metadata.controls.isEmpty())
    }

    @Test
    fun `missing transitions defaults to empty list`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertTrue(metadata.transitions.isEmpty())
    }

    // ── MetadataParseException tests ─────────────────────────────────────────

    @Test
    fun `throws MetadataParseException on malformed JSON`() {
        assertThrows<MetadataParseException> { GameMetadata.fromJsonString("not valid json {{{") }
    }

    @Test
    fun `throws MetadataParseException on missing scenes field`() {
        val e =
            assertThrows<MetadataParseException> {
                GameMetadata.fromJsonString("""{"actors":[]}""")
            }
        assertTrue(e.message!!.contains("scenes"))
    }

    @Test
    fun `throws MetadataParseException on missing actors field`() {
        val e =
            assertThrows<MetadataParseException> {
                GameMetadata.fromJsonString("""{"scenes":{"title":0}}""")
            }
        assertTrue(e.message!!.contains("actors"))
    }

    @Test
    fun `throws MetadataParseException on type mismatch`() {
        assertThrows<MetadataParseException> {
            GameMetadata.fromJsonString("""{"scenes":"string","actors":[]}""")
        }
    }

    @Test
    fun `throws MetadataParseException on empty string`() {
        assertThrows<MetadataParseException> { GameMetadata.fromJsonString("") }
    }

    @Test
    fun `throws MetadataParseException on partial truncated JSON`() {
        assertThrows<MetadataParseException> {
            GameMetadata.fromJsonString("""{"scenes":{"title":0},"actors":[{"name":"ball""")
        }
    }

    // ── TileDecoders parsing tests ────────────────────────────────────────────

    private val jsonWithTileDecoders =
        """
        {
          "scenes": { "title": 0, "game": 1 },
          "actors": [],
          "tileDecoders": {
            "bg": { "type": "gbdk_offset" },
            "win": { "type": "direct_ascii" }
          }
        }
        """
            .trimIndent()

    private val jsonWithCustomDecoder =
        """
        {
          "scenes": { "title": 0 },
          "actors": [],
          "tileDecoders": {
            "bg": {
              "type": "custom",
              "mapping": { "48": "0", "49": "1", "50": "2" }
            }
          }
        }
        """
            .trimIndent()

    @Test
    fun `fromJsonString parses tileDecoders with bg gbdk_offset and win direct_ascii`() {
        val metadata = GameMetadata.fromJsonString(jsonWithTileDecoders)

        assertNotNull(metadata.tileDecoders)
        val tileDecoders = metadata.tileDecoders!!
        assertNotNull(tileDecoders.bg)
        assertEquals("gbdk_offset", tileDecoders.bg!!.type)
        assertNotNull(tileDecoders.win)
        assertEquals("direct_ascii", tileDecoders.win!!.type)
    }

    @Test
    fun `fromJsonString returns null tileDecoders when key absent (backward compatibility)`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertNull(metadata.tileDecoders)
    }

    @Test
    fun `fromJsonString parses custom bg decoder with mapping table`() {
        val metadata = GameMetadata.fromJsonString(jsonWithCustomDecoder)

        assertNotNull(metadata.tileDecoders)
        val bgDecoder = metadata.tileDecoders!!.bg
        assertNotNull(bgDecoder)
        assertEquals("custom", bgDecoder!!.type)
        assertEquals('0', bgDecoder.mapping[48])
        assertEquals('1', bgDecoder.mapping[49])
        assertEquals('2', bgDecoder.mapping[50])
    }

    @Test
    fun `bgDecoder returns GBDK_BG_DECODER for gbdk_offset type`() {
        val metadata = GameMetadata.fromJsonString(jsonWithTileDecoders)

        val decoder = metadata.bgDecoder()
        assertNotNull(decoder)
        // GBDK_BG_DECODER: tile + 0x20 = ASCII. Tile 0x30 ('P' - 0x20) → 'P' (0x50)
        assertEquals('P', decoder!!.decode(0x30))
    }

    @Test
    fun `winDecoder returns DIRECT_ASCII_DECODER for direct_ascii type`() {
        val metadata = GameMetadata.fromJsonString(jsonWithTileDecoders)

        val decoder = metadata.winDecoder()
        assertNotNull(decoder)
        // DIRECT_ASCII_DECODER: tile = ASCII directly. Tile 0x50 = 'P'
        assertEquals('P', decoder!!.decode(0x50))
    }

    @Test
    fun `bgDecoder returns custom decoder for custom type with mapping`() {
        val metadata = GameMetadata.fromJsonString(jsonWithCustomDecoder)

        val decoder = metadata.bgDecoder()
        assertNotNull(decoder)
        // Custom mapping: tile 48 → '0', tile 49 → '1'
        assertEquals('0', decoder!!.decode(48))
        assertEquals('1', decoder.decode(49))
        // Unmapped tile falls back to '.'
        assertEquals('.', decoder.decode(99))
    }

    @Test
    fun `bgDecoder returns null when tileDecoders is null`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertNull(metadata.bgDecoder())
    }

    @Test
    fun `winDecoder returns null when tileDecoders is null`() {
        val metadata = GameMetadata.fromJsonString(sampleJson)

        assertNull(metadata.winDecoder())
    }
}
