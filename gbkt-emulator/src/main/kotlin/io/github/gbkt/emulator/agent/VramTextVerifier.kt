/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.MemoryAccess

/**
 * Reads text from Game Boy VRAM tile maps by decoding tile indices as ASCII characters.
 *
 * GBDK's default IBM font maps tile index directly to ASCII code (`'P'` = tile 0x50). This verifier
 * reads the background or window tilemap and converts tile indices back to characters, enabling
 * text-based assertions in UAT tests without relying on screenshot comparison.
 *
 * Tilemap layout:
 * - Background: 0x9800–0x9BFF (32×32 tiles, 20×18 visible)
 * - Window: 0x9C00–0x9FFF (same layout)
 * - Row stride: 32 bytes per row (only first 20 tiles are visible on screen)
 */
object VramTextVerifier {

    /** Base address of the background tilemap in VRAM. */
    const val BG_TILEMAP_BASE = 0x9800

    /** Base address of the window tilemap in VRAM. */
    const val WIN_TILEMAP_BASE = 0x9C00

    /** Number of bytes per tilemap row (32 tiles, only 20 visible). */
    const val ROW_STRIDE = 32

    /** Number of visible tile columns on the Game Boy LCD. */
    const val VISIBLE_WIDTH = 20

    /** Number of visible tile rows on the Game Boy LCD. */
    const val VISIBLE_HEIGHT = 18

    /** GB hardware register: Background Y scroll position in pixels. */
    const val SCY_REG_ADDR = 0xFF42

    /** GB hardware register: Background X scroll position in pixels. */
    const val SCX_REG_ADDR = 0xFF43

    /** Selects which VRAM tilemap layer to read from. */
    enum class TilemapLayer {
        /** Background tilemap at 0x9800 — target of `gotoxy`/`printf`. */
        BACKGROUND,
        /** Window tilemap at 0x9C00 — target of `_win_print_at`. */
        WINDOW,
    }

    /**
     * Decodes a raw VRAM tile index to a printable character.
     *
     * GBDK uses two tile encodings depending on the layer:
     * - **Background** (`printf`/`gotoxy`): tile = ASCII - 0x20 (e.g. 'P' (0x50) → tile 0x30)
     * - **Window** (`_win_print_at`): tile = ASCII directly (e.g. 'P' → tile 0x50)
     *
     * Use [GBDK_BG_DECODER] for background, [DIRECT_ASCII_DECODER] for window, or [defaultDecoder]
     * to select automatically based on the tilemap layer.
     */
    fun interface TileDecoder {
        fun decode(tile: Int): Char
    }

    /** Decodes GBDK background tiles: tile + 0x20 = ASCII code. */
    val GBDK_BG_DECODER = TileDecoder { tile ->
        val ascii = tile + 0x20
        if (ascii in 0x20..0x7E) ascii.toChar() else '.'
    }

    /** Decodes tiles where tile index = ASCII code directly. */
    val DIRECT_ASCII_DECODER = TileDecoder { tile ->
        if (tile in 0x20..0x7E) tile.toChar() else '.'
    }

    /** Returns the default [TileDecoder] for the given tilemap [layer]. */
    fun defaultDecoder(layer: TilemapLayer): TileDecoder =
        when (layer) {
            TilemapLayer.BACKGROUND -> GBDK_BG_DECODER
            TilemapLayer.WINDOW -> DIRECT_ASCII_DECODER
        }

    /**
     * Reads [length] tiles starting at tile position ([x], [y]) and returns them as a string.
     *
     * Input coordinates are always viewport-relative (0–19 columns, 0–17 rows). When [scrollAware]
     * is `true` and [layer] is [TilemapLayer.BACKGROUND], the hardware scroll registers SCX
     * (0xFF43) and SCY (0xFF42) are read and applied to compute tilemap-absolute coordinates.
     * Coordinates wrap at 32 tiles using a bitwise AND with 31.
     *
     * The Window layer is unaffected by [scrollAware] — it has independent WX/WY registers and is
     * always read at the given viewport position.
     *
     * @param memory The emulator memory interface.
     * @param x Tile column (0-based, 0–19 visible).
     * @param y Tile row (0-based, 0–17 visible).
     * @param length Number of tiles to read.
     * @param layer Which tilemap layer to read from.
     * @param decoder Tile decoder to use. Null = per-layer default (GBDK offset for BG, direct for
     *   WIN).
     * @param scrollAware When true and layer is BACKGROUND, apply SCX/SCY register offsets with
     *   32-tile wrap. Default false preserves existing behaviour.
     * @return String of [length] characters decoded from tile indices.
     * @throws IllegalArgumentException if [x], [y], or [length] fall outside the visible tilemap
     *   area.
     */
    fun readText(
        memory: MemoryAccess,
        x: Int,
        y: Int,
        length: Int,
        layer: TilemapLayer = TilemapLayer.BACKGROUND,
        decoder: TileDecoder? = null,
        scrollAware: Boolean = false,
    ): String {
        require(x in 0 until VISIBLE_WIDTH) { "x=$x out of visible range 0..${VISIBLE_WIDTH - 1}" }
        require(y in 0 until VISIBLE_HEIGHT) {
            "y=$y out of visible range 0..${VISIBLE_HEIGHT - 1}"
        }
        require(length >= 0 && x + length <= VISIBLE_WIDTH) {
            "x=$x + length=$length exceeds visible width $VISIBLE_WIDTH"
        }
        val base =
            when (layer) {
                TilemapLayer.BACKGROUND -> BG_TILEMAP_BASE
                TilemapLayer.WINDOW -> WIN_TILEMAP_BASE
            }
        // Compute tilemap coordinates from viewport coords + scroll offset (BG only)
        val (startTileX, tileY) =
            if (scrollAware && layer == TilemapLayer.BACKGROUND) {
                val scx = memory.readByte(SCX_REG_ADDR)
                val scy = memory.readByte(SCY_REG_ADDR)
                ((x + scx / 8) and 31) to ((y + scy / 8) and 31)
            } else {
                x to y
            }
        val dec = decoder ?: defaultDecoder(layer)
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val tileX =
                if (scrollAware && layer == TilemapLayer.BACKGROUND) {
                    (startTileX + i) and 31
                } else {
                    x + i
                }
            val addr = base + tileY * ROW_STRIDE + tileX
            val tile = memory.readByte(addr)
            sb.append(dec.decode(tile))
        }
        return sb.toString()
    }

    /**
     * Reads the full visible row at tile row [y] (20 characters).
     *
     * @param memory The emulator memory interface.
     * @param y Tile row (0-based, 0–17 visible).
     * @param layer Which tilemap layer to read from.
     * @param decoder Tile decoder to use. Null = per-layer default.
     * @param scrollAware When true and layer is BACKGROUND, apply SCX/SCY register offsets.
     * @return 20-character string for the row.
     */
    fun readRow(
        memory: MemoryAccess,
        y: Int,
        layer: TilemapLayer = TilemapLayer.BACKGROUND,
        decoder: TileDecoder? = null,
        scrollAware: Boolean = false,
    ): String = readText(memory, 0, y, VISIBLE_WIDTH, layer, decoder, scrollAware)

    /**
     * Reads all 18 visible rows from the tilemap.
     *
     * @param memory The emulator memory interface.
     * @param layer Which tilemap layer to read from.
     * @param decoder Tile decoder to use. Null = per-layer default.
     * @param scrollAware When true and layer is BACKGROUND, apply SCX/SCY register offsets.
     * @return List of 18 strings, each 20 characters wide.
     */
    fun readAllRows(
        memory: MemoryAccess,
        layer: TilemapLayer = TilemapLayer.BACKGROUND,
        decoder: TileDecoder? = null,
        scrollAware: Boolean = false,
    ): List<String> =
        (0 until VISIBLE_HEIGHT).map { y -> readRow(memory, y, layer, decoder, scrollAware) }

    /**
     * Searches the visible tilemap for a substring and returns its position.
     *
     * Scans all 18 visible rows for [text] as a contiguous substring within each row. When
     * [scrollAware] is true and layer is BACKGROUND, each row is read with SCX/SCY applied.
     * Returned coordinates are viewport-relative.
     *
     * @param memory The emulator memory interface.
     * @param text The text to search for.
     * @param layer Which tilemap layer to search.
     * @param decoder Tile decoder to use. Null = per-layer default.
     * @param scrollAware When true and layer is BACKGROUND, apply SCX/SCY register offsets.
     * @return (x, y) viewport position of the first match, or null if not found.
     */
    fun findText(
        memory: MemoryAccess,
        text: String,
        layer: TilemapLayer = TilemapLayer.BACKGROUND,
        decoder: TileDecoder? = null,
        scrollAware: Boolean = false,
    ): Pair<Int, Int>? {
        for (y in 0 until VISIBLE_HEIGHT) {
            val row = readRow(memory, y, layer, decoder, scrollAware)
            val x = row.indexOf(text)
            if (x >= 0) return x to y
        }
        return null
    }

    /**
     * Searches both background and window tilemaps for a substring.
     *
     * Checks background first, then window. When [scrollAware] is true, SCX/SCY offsets are applied
     * to the BG layer read. The Window layer is never scroll-adjusted.
     *
     * @param memory The emulator memory interface.
     * @param text The text to search for.
     * @param bgDecoder Decoder for background layer. Null = default (GBDK offset).
     * @param winDecoder Decoder for window layer. Null = default (direct ASCII).
     * @param scrollAware When true, apply SCX/SCY offsets to the BG layer search.
     * @return (x, y, layer) of the first match, or null if not found in either layer.
     */
    fun findTextAnyLayer(
        memory: MemoryAccess,
        text: String,
        bgDecoder: TileDecoder? = null,
        winDecoder: TileDecoder? = null,
        scrollAware: Boolean = false,
    ): Triple<Int, Int, TilemapLayer>? {
        val bgPos = findText(memory, text, TilemapLayer.BACKGROUND, bgDecoder, scrollAware)
        if (bgPos != null) return Triple(bgPos.first, bgPos.second, TilemapLayer.BACKGROUND)
        val winPos = findText(memory, text, TilemapLayer.WINDOW, winDecoder, scrollAware)
        if (winPos != null) return Triple(winPos.first, winPos.second, TilemapLayer.WINDOW)
        return null
    }
}
