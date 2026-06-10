/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 12.9 WR-03 — synthesizeZoneTilesetHeader includes <gb/cgb.h>
//
// Root cause (WR-03): synthesizeHeader (now synthesizeZoneTilesetHeader) emitted
// `extern const palette_color_t _zone_<id>_tileset_palettes[...]` without including
// `<gb/cgb.h>`. The `palette_color_t` typedef is defined in `<gb/cgb.h>` (GBDK
// header); the sibling ConvertSpritesTask.generateSpriteHeader already includes it.
// Without the include, the header relies on `<gbdk/platform.h>` transitively pulling
// in the typedef — which may not hold in all GBDK configurations.
//
// Fix (WR-03): add `appendLine("#include <gb/cgb.h>")` to synthesizeZoneTilesetHeader
// alongside the existing `<stdint.h>` and `<gbdk/platform.h>` includes.
//
// The function has been promoted to `internal` top-level so this test can call it
// directly without requiring GBDK / png2asset.
//
// Test:
//   1. synthesizeZoneTilesetHeader emits `#include <gb/cgb.h>` in the generated header.
// =============================================================================

class ConvertZoneTilesetsHeaderCgbIncludeTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun `synthesizeZoneTilesetHeader includes gb-cgb-h for palette_color_t typedef (WR-03)`() {
        // Create a minimal 8x8 PNG so ImageIO.read() in synthesizeZoneTilesetHeader succeeds.
        // The tilemap dimensions (8x8 px → 1x1 tiles) are sufficient to pass the
        // "multiples of 8" requirement; the content is irrelevant (just needs to be valid PNG).
        val tilemapPng = File(tempDir, "tilemap.png")
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "PNG", tilemapPng)

        val outputH = File(tempDir, "zone_test.h")

        synthesizeZoneTilesetHeader(
            sanitized = "test_zone",
            nativeStem = "test_zone",
            tileCount = 1,
            outputH = outputH,
            tilemapPng = tilemapPng,
            paletteArrayDim = 4,
            subPaletteCount = 1,
        )

        val hText = outputH.readText()

        // WR-03 fix: <gb/cgb.h> must be present so the palette_color_t typedef is declared
        // without relying on transitive includes from <gbdk/platform.h>.
        assertTrue(
            hText.contains("#include <gb/cgb.h>"),
            "WR-03: zone tileset header must include `#include <gb/cgb.h>` (provides " +
                "palette_color_t typedef for the palette extern). " +
                "header content:\n$hText",
        )

        // Sanity: the palette extern itself is present (the include is needed for it).
        assertTrue(
            hText.contains("extern const palette_color_t _zone_test_zone_tileset_palettes["),
            "Zone tileset header must still emit the palette extern " +
                "(sanity guard — the <gb/cgb.h> include is required by this declaration). " +
                "header content:\n$hText",
        )
    }
}
