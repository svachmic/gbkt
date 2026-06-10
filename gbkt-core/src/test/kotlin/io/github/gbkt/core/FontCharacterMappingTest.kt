/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [FontCharacterMapping] — character-to-tile-index encoding for extended font support.
 *
 * Coverage:
 * - ASCII characters pass through unchanged
 * - Czech diacritics map to correct tile indices (128-141)
 * - Unmapped characters produce warnings and use replacement tile
 * - Encoded length equals character count (one tile byte per character)
 * - C literal quoting works correctly
 * - Generic design: custom mappings can be created
 * - Czech factory produces correct tile index assignments
 */
class FontCharacterMappingTest {

    // -------------------------------------------------------------------------
    // ASCII passthrough
    // -------------------------------------------------------------------------

    @Test
    fun `ASCII characters pass through unchanged`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val encoded = mapping.encodeString("Hello!")
        assertEquals("Hello!", encoded)
    }

    @Test
    fun `ASCII lowercase letters pass through`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val encoded = mapping.encodeString("abcdefghijklmnopqrstuvwxyz")
        assertEquals("abcdefghijklmnopqrstuvwxyz", encoded)
    }

    @Test
    fun `ASCII uppercase letters pass through`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val encoded = mapping.encodeString("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", encoded)
    }

    @Test
    fun `ASCII digits and punctuation pass through`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val encoded = mapping.encodeString("0123456789!@#")
        assertEquals("0123456789!@#", encoded)
    }

    @Test
    fun `space character passes through`() {
        val mapping = FontCharacterMapping.asciiOnly()
        assertEquals(" ", mapping.encodeString(" "))
    }

    // -------------------------------------------------------------------------
    // Czech diacritic mapping
    // -------------------------------------------------------------------------

    @Test
    fun `Czech e-hacek maps to tile 128`() {
        val mapping = FontCharacterMapping.czech()
        val encoded = mapping.encodeString("ě")
        assertEquals("\\x80", encoded) // 128 = 0x80
    }

    @Test
    fun `Czech s-hacek maps to tile 129`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x81", mapping.encodeString("š")) // 129 = 0x81
    }

    @Test
    fun `Czech c-hacek maps to tile 130`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x82", mapping.encodeString("č")) // 130 = 0x82
    }

    @Test
    fun `Czech r-hacek maps to tile 131`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x83", mapping.encodeString("ř")) // 131 = 0x83
    }

    @Test
    fun `Czech z-hacek maps to tile 132`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x84", mapping.encodeString("ž")) // 132 = 0x84
    }

    @Test
    fun `Czech y-carka maps to tile 133`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x85", mapping.encodeString("ý")) // 133 = 0x85
    }

    @Test
    fun `Czech a-carka maps to tile 134`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x86", mapping.encodeString("á")) // 134 = 0x86
    }

    @Test
    fun `Czech i-carka maps to tile 135`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x87", mapping.encodeString("í")) // 135 = 0x87
    }

    @Test
    fun `Czech e-carka maps to tile 136`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x88", mapping.encodeString("é")) // 136 = 0x88
    }

    @Test
    fun `Czech u-carka maps to tile 137`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x89", mapping.encodeString("ú")) // 137 = 0x89
    }

    @Test
    fun `Czech u-krouzek maps to tile 138`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x8A", mapping.encodeString("ů")) // 138 = 0x8A
    }

    @Test
    fun `Czech d-hacek maps to tile 139`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x8B", mapping.encodeString("ď")) // 139 = 0x8B
    }

    @Test
    fun `Czech t-hacek maps to tile 140`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x8C", mapping.encodeString("ť")) // 140 = 0x8C
    }

    @Test
    fun `Czech n-hacek maps to tile 141`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals("\\x8D", mapping.encodeString("ň")) // 141 = 0x8D
    }

    @Test
    fun `Czech string with mixed ASCII and diacritics encodes correctly`() {
        val mapping = FontCharacterMapping.czech()
        // "ěšč" → tile 128, 129, 130 → \x80\x81\x82
        assertEquals("\\x80\\x81\\x82", mapping.encodeString("ěšč"))
    }

    @Test
    fun `Czech mixed text encodes diacritics inline with ASCII`() {
        val mapping = FontCharacterMapping.czech()
        // "Vítej!" → V, í(135=0x87), t, e, j, !
        val encoded = mapping.encodeString("Vítej!")
        assertEquals("V\\x87tej!", encoded)
    }

    @Test
    fun `Czech diacritic string encodes to correct tile bytes`() {
        val mapping = FontCharacterMapping.czech()
        // All 14 Czech diacritics in order
        val encoded = mapping.encodeString("ěščřžýáíéúůďťň")
        assertEquals(
            "\\x80\\x81\\x82\\x83\\x84\\x85\\x86\\x87\\x88\\x89\\x8A\\x8B\\x8C\\x8D",
            encoded,
        )
    }

    // -------------------------------------------------------------------------
    // Unmapped character handling
    // -------------------------------------------------------------------------

    @Test
    fun `unmapped character produces warning`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val warnings = mutableListOf<String>()
        val encoded = mapping.encodeString("ě", warnings)
        assertTrue(warnings.isNotEmpty(), "Expected warning for unmapped char 'ě'")
        assertTrue(warnings[0].contains("unmapped"), "Warning should mention 'unmapped'")
    }

    @Test
    fun `unmapped character uses replacement tile`() {
        val mapping = FontCharacterMapping.asciiOnly() // replacementTileIndex = 0x3F = '?'
        val encoded = mapping.encodeString("ě")
        assertEquals("\\x3F", encoded) // replacement tile = '?'
    }

    @Test
    fun `custom replacement tile is used for unmapped characters`() {
        val mapping = FontCharacterMapping(charToTile = emptyMap(), replacementTileIndex = 0x41)
        val encoded = mapping.encodeString("ě")
        assertEquals("\\x41", encoded) // replacement = 0x41 = 'A' tile
    }

    @Test
    fun `warning message includes Unicode code point`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val warnings = mutableListOf<String>()
        mapping.encodeString("ě", warnings)
        assertTrue(
            warnings[0].contains("011B") || warnings[0].contains("011b"),
            "Warning should contain Unicode code point U+011B for 'ě'",
        )
    }

    @Test
    fun `no warnings for pure ASCII strings`() {
        val mapping = FontCharacterMapping.asciiOnly()
        val warnings = mutableListOf<String>()
        mapping.encodeString("Hello, World!", warnings)
        assertTrue(warnings.isEmpty(), "No warnings for ASCII-only string")
    }

    // -------------------------------------------------------------------------
    // Encoded length
    // -------------------------------------------------------------------------

    @Test
    fun `encoded length equals character count`() {
        val mapping = FontCharacterMapping.czech()
        assertEquals(5, mapping.encodedLength("Hello"))
        assertEquals(3, mapping.encodedLength("ěšč"))
        assertEquals(6, mapping.encodedLength("Vítej!"))
    }

    @Test
    fun `encoded length is one byte per character regardless of diacritics`() {
        val mapping = FontCharacterMapping.czech()
        val text = "ěščřžýáíéúůďťň" // 14 chars, each maps to one tile byte
        assertEquals(14, mapping.encodedLength(text))
    }

    // -------------------------------------------------------------------------
    // C literal quoting
    // -------------------------------------------------------------------------

    @Test
    fun `encodeForCLiteral adds surrounding quotes`() {
        val mapping = FontCharacterMapping.czech()
        val literal = mapping.encodeForCLiteral("abc")
        assertEquals("\"abc\"", literal)
    }

    @Test
    fun `encodeForCLiteral with diacritics produces quoted escape sequences`() {
        val mapping = FontCharacterMapping.czech()
        val literal = mapping.encodeForCLiteral("ě")
        assertEquals("\"\\x80\"", literal) // 128 = 0x80
    }

    @Test
    fun `encodeForCLiteral with mixed content`() {
        val mapping = FontCharacterMapping.czech()
        val literal = mapping.encodeForCLiteral("Hi!")
        assertEquals("\"Hi!\"", literal)
    }

    // -------------------------------------------------------------------------
    // Generic design
    // -------------------------------------------------------------------------

    @Test
    fun `custom mapping works with any tile index`() {
        val mapping = FontCharacterMapping(charToTile = mapOf('α' to 200, 'β' to 201, 'γ' to 202))
        assertEquals("\\xC8\\xC9\\xCA", mapping.encodeString("αβγ"))
    }

    @Test
    fun `empty mapping results in ASCII passthrough only`() {
        val mapping = FontCharacterMapping(charToTile = emptyMap())
        assertEquals("Hello", mapping.encodeString("Hello"))
    }

    @Test
    fun `czech factory contains all 14 required characters`() {
        val mapping = FontCharacterMapping.czech()
        val requiredChars =
            listOf('ě', 'š', 'č', 'ř', 'ž', 'ý', 'á', 'í', 'é', 'ú', 'ů', 'ď', 'ť', 'ň')
        for (ch in requiredChars) {
            assertTrue(mapping.charToTile.containsKey(ch), "Czech mapping must contain '$ch'")
        }
        assertEquals(14, mapping.charToTile.size, "Czech mapping should have exactly 14 characters")
    }

    @Test
    fun `czech tile indices are in range 128-141`() {
        val mapping = FontCharacterMapping.czech()
        for ((ch, tileIndex) in mapping.charToTile) {
            assertTrue(
                tileIndex in 128..141,
                "Czech char '$ch' tile index $tileIndex should be in range 128-141",
            )
        }
    }

    @Test
    fun `czech tile indices are unique - no two characters share a tile`() {
        val mapping = FontCharacterMapping.czech()
        val indices = mapping.charToTile.values.toList()
        assertEquals(indices.size, indices.toSet().size, "All Czech tile indices must be unique")
    }
}
