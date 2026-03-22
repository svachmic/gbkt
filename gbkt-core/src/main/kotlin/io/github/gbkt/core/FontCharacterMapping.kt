/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

// =============================================================================
// FONT CHARACTER MAPPING — Extended character-to-tile-index mapping
//
// Generic mechanism for mapping non-ASCII characters to font tile indices.
// This enables localized PO strings (Czech, French, etc.) to render correctly
// on Game Boy hardware using extended font tilesets.
//
// Usage:
//   val mapping = FontCharacterMapping.czech()
//   val encoded = mapping.encodeString("Vítejte!")  // encodes í → \x87 (tile 135)
//   // Use `encoded` as the content of a C string literal in the codegen output
//
// Design:
//   - Generic: not LotD-specific. Any game with an extended font can configure it.
//   - ASCII passthrough: unmapped ASCII chars (0x20-0x7E) map to themselves.
//   - Extended chars: mapped to tile indices via the charToTile map.
//   - Unmapped non-ASCII chars: produce a build warning (no silent corruption).
// =============================================================================

/**
 * Maps Unicode characters to font tile indices for Game Boy string rendering.
 *
 * Game Boy fonts use tile indices rather than ASCII/Unicode code points. The base font typically
 * covers ASCII 0x20-0x7E (tiles 0-94 or with FONT_OFFSET), but extended character sets (Czech
 * diacritics, French accents, etc.) require additional tiles appended after the base set.
 *
 * This class encodes Kotlin strings into C escape sequences that directly reference tile indices:
 * - ASCII 0x20-0x7E characters are passed through as-is (their code point = tile index)
 * - Extended characters are encoded as `\xNN` where NN is the tile index (0x00-0xFF)
 * - Unmapped characters produce a warning and are encoded as the replacement tile (default: `?`)
 *
 * @param charToTile Map from Unicode character to tile index (0-255).
 * @param replacementTileIndex Tile index used for unmapped characters (default: `?` = 0x3F).
 * @param warnings Accumulated warnings for unmapped characters during encoding.
 */
class FontCharacterMapping(
    /** Map from Unicode character to font tile index (0-255). */
    val charToTile: Map<Char, Int>,
    /**
     * Tile index emitted for characters not found in [charToTile] and not in ASCII 0x20-0x7E.
     * Default is `?` (0x3F = 63) — the standard question mark tile.
     */
    val replacementTileIndex: Int = 0x3F,
) {

    /**
     * Encode a Unicode string into a C-compatible byte sequence for Game Boy font rendering.
     *
     * Each character is resolved to a tile index:
     * - ASCII 0x20-0x7E: encoded as-is (tile index equals code point)
     * - Characters in [charToTile]: encoded as `\xNN` (hex escape of tile index)
     * - Other characters: encoded as `\x${replacementTileIndex.hex}` with a warning in [warnings]
     *
     * The resulting string is suitable for use inside a C string literal (without surrounding
     * quotes). Use [encodeForCLiteral] to get the quoted form.
     *
     * @param text The source string (may contain Unicode diacritics and special characters).
     * @param warningCollector Optional collector for unmapped character warnings.
     * @return C-safe byte sequence representing the encoded string.
     */
    fun encodeString(text: String, warningCollector: MutableList<String>? = null): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                // ASCII printable range: pass through directly
                ch.code in 0x20..0x7E -> sb.append(ch)

                // Extended character with explicit mapping
                charToTile.containsKey(ch) -> {
                    val tileIndex = charToTile[ch]!!
                    sb.append("\\x%02X".format(tileIndex))
                }

                // Null terminator: always pass through
                ch == '\u0000' -> sb.append("\\x00")

                // Newline/tab: common control characters
                ch == '\n' -> sb.append("\\n")
                ch == '\t' -> sb.append("\\t")

                // Unmapped character: use replacement tile and warn
                else -> {
                    val codeHex = ch.code.toString(16).uppercase().padStart(4, '0')
                    val tileHex = replacementTileIndex.toString(16).uppercase().padStart(2, '0')
                    warningCollector?.add(
                        "FontCharacterMapping: unmapped character '${ch}' (U+$codeHex) " +
                            "encoded as replacement tile 0x$tileHex"
                    )
                    sb.append("\\x%02X".format(replacementTileIndex))
                }
            }
        }
        return sb.toString()
    }

    /**
     * Encode a Unicode string and wrap it in C string literal quotes.
     *
     * Convenience wrapper around [encodeString] that adds surrounding `"` characters, suitable for
     * direct use in C code generation:
     * ```kotlin
     * val encoded = mapping.encodeForCLiteral("Vítejte!")
     * // → "\"V\\x87tejte!\""  (í at tile 135 = 0x87)
     * ```
     *
     * @param text The source string.
     * @param warningCollector Optional collector for unmapped character warnings.
     * @return Quoted C string literal containing tile-index escape sequences.
     */
    fun encodeForCLiteral(text: String, warningCollector: MutableList<String>? = null): String {
        return "\"${encodeString(text, warningCollector)}\""
    }

    /**
     * Compute the byte length of the encoded string.
     *
     * Returns the number of tile bytes that the encoded string occupies. This is important for
     * passing the correct `len` parameter to `_win_print_at` calls in the C codegen, since
     * multi-byte UTF-8 characters map to exactly one tile byte in the font encoding.
     *
     * @param text The source string.
     * @return Number of tile bytes in the encoded string (one per character, including unmapped).
     */
    fun encodedLength(text: String): Int = text.length

    companion object {

        /**
         * Standard mapping for Czech language diacritics.
         *
         * Maps the 14 Czech diacritic characters to their tile indices in the extended font:
         *
         * | Char | Tile | Notes       |
         * |------|------|-------------|
         * | ě    | 128  | e + háček   |
         * | š    | 129  | s + háček   |
         * | č    | 130  | c + háček   |
         * | ř    | 131  | r + háček   |
         * | ž    | 132  | z + háček   |
         * | ý    | 133  | y + čárka   |
         * | á    | 134  | a + čárka   |
         * | í    | 135  | i + čárka   |
         * | é    | 136  | e + čárka   |
         * | ú    | 137  | u + čárka   |
         * | ů    | 138  | u + kroužek |
         * | ď    | 139  | d + háček   |
         * | ť    | 140  | t + háček   |
         * | ň    | 141  | n + háček   |
         *
         * These tile indices correspond to the tiles appended after the 128-tile ASCII base in
         * `LabyrinthOfTheDragon-port/res/tiles/font.png`.
         */
        fun czech(): FontCharacterMapping =
            FontCharacterMapping(
                charToTile =
                    mapOf(
                        'ě' to 128,
                        'š' to 129,
                        'č' to 130,
                        'ř' to 131,
                        'ž' to 132,
                        'ý' to 133,
                        'á' to 134,
                        'í' to 135,
                        'é' to 136,
                        'ú' to 137,
                        'ů' to 138,
                        'ď' to 139,
                        'ť' to 140,
                        'ň' to 141,
                    )
            )

        /**
         * Standard mapping for French language diacritics.
         *
         * Placeholder showing the generic nature of [FontCharacterMapping] — any game can define
         * its own mapping. Actual tile indices depend on the game's font extension.
         *
         * Games using French localization would provide tile indices matching their extended font.
         */
        fun french(tileOffset: Int = 128): FontCharacterMapping =
            FontCharacterMapping(
                charToTile =
                    mapOf(
                        'à' to tileOffset + 0,
                        'â' to tileOffset + 1,
                        'ç' to tileOffset + 2,
                        'è' to tileOffset + 3,
                        'é' to tileOffset + 4,
                        'ê' to tileOffset + 5,
                        'ë' to tileOffset + 6,
                        'î' to tileOffset + 7,
                        'ï' to tileOffset + 8,
                        'ô' to tileOffset + 9,
                        'ù' to tileOffset + 10,
                        'û' to tileOffset + 11,
                        'ü' to tileOffset + 12,
                        'ÿ' to tileOffset + 13,
                        'æ' to tileOffset + 14,
                        'œ' to tileOffset + 15,
                    )
            )

        /**
         * Identity mapping (ASCII only) — no extended characters.
         *
         * Suitable for English-only games that do not extend their font. All non-ASCII characters
         * will produce warnings and use the replacement tile.
         */
        fun asciiOnly(): FontCharacterMapping = FontCharacterMapping(charToTile = emptyMap())
    }
}
