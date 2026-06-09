/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 12.9 CR-01 — generateSpriteHeader palette extern is unconditional
//
// Root cause (CR-01): png2asset emits a `const palette_color_t <stem>_palettes[]`
// array for EVERY sprite PNG (no -no_palettes flag is passed; -keep_palette_order
// only affects palette ORDER, not whether the array is emitted). The pre-fix
// generateSpriteHeader gated BOTH the `#include <gb/cgb.h>` and the
// `extern const palette_color_t <stem>_palettes[]` declaration on `isIndexed`.
// A non-indexed (RGB) metasprite PNG therefore produced a missing extern → SDCC
// "Undefined identifier '<stem>_palettes'" when GBDKPipeline emits
// `set_sprite_palette(<slot>u, 1u, <stem>_palettes)` for every GBC metasprite.
//
// Fix (CR-01): remove the `if (isIndexed)` guards around the <gb/cgb.h> include
// and the palette extern. Both are now emitted UNCONDITIONALLY. The function has
// been promoted to `internal` top-level (mirroring buildPng2AssetArgs) so tests
// can reach it directly.
//
// RED: for a non-indexed (RGB, color-type 2) sprite PNG, the pre-fix header
//   omits both the include and the extern → `assertTrue(hText.contains(...))`
//   fails for both.
// GREEN: after removing the isIndexed gates both assertions pass.
//
// Tests:
//   1. Non-indexed PNG (isIndexed=false) → header still contains
//      `#include <gb/cgb.h>` and `extern const palette_color_t paddle_palettes[]`.
//   2. Indexed PNG (isIndexed=true) → header also contains both (regression guard).
// =============================================================================

class ConvertSpritesHeaderPaletteExternTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // Test 1 — Non-indexed (RGB) PNG → header emits <gb/cgb.h> + palette extern
    //
    // RED: isIndexed=false → if (isIndexed) gate skips both lines.
    // GREEN: after CR-01 fix removes the gate, both lines are always emitted.
    // -------------------------------------------------------------------------

    @Test
    fun `generateSpriteHeader emits cgb include and palette extern for NON-indexed PNG (CR-01)`() {
        val outputH = File(tempDir, "sprites/paddle.h")
        outputH.parentFile.mkdirs()

        // Directly call the internal top-level function (same package — accessible from tests).
        // isIndexed = false is the scenario under test: a non-indexed (RGB) sprite PNG.
        generateSpriteHeader(
            stemName = "paddle",
            nativeArrayName = "paddle_tiles",
            pathBasedArrayName = "sprites_paddle_tiles",
            outputH = outputH,
            isIndexed = false,
        )

        val hText = outputH.readText()

        // CR-01 fix: <gb/cgb.h> must be present unconditionally (provides palette_color_t typedef).
        assertTrue(
            hText.contains("#include <gb/cgb.h>"),
            "CR-01: sprite header must include <gb/cgb.h> unconditionally (even for non-indexed " +
                "PNG) — png2asset always emits the _palettes array; the typedef is always needed. " +
                "header content:\n$hText",
        )

        // CR-01 fix: palette extern must be present unconditionally so set_sprite_palette() in
        // main.c resolves at SDCC link time without "Undefined identifier 'paddle_palettes'".
        assertTrue(
            hText.contains("extern const palette_color_t paddle_palettes[]"),
            "CR-01: sprite header must declare `extern const palette_color_t paddle_palettes[]` " +
                "unconditionally (even for non-indexed PNG) — the symbol exists in the png2asset " +
                ".c output for ALL sprite PNGs; the declaration must always be present. " +
                "header content:\n$hText",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — Indexed PNG → header still emits <gb/cgb.h> + palette extern
    //          (regression guard: indexed path was already emitting both)
    // -------------------------------------------------------------------------

    @Test
    fun `generateSpriteHeader emits cgb include and palette extern for indexed PNG (regression guard)`() {
        val outputH = File(tempDir, "sprites/player.h")
        outputH.parentFile.mkdirs()

        generateSpriteHeader(
            stemName = "player",
            nativeArrayName = "player_tiles",
            pathBasedArrayName = "sprites_player_tiles",
            outputH = outputH,
            isIndexed = true,
        )

        val hText = outputH.readText()

        assertTrue(
            hText.contains("#include <gb/cgb.h>"),
            "Regression guard: indexed PNG path must still include <gb/cgb.h>. " +
                "header content:\n$hText",
        )

        assertTrue(
            hText.contains("extern const palette_color_t player_palettes[]"),
            "Regression guard: indexed PNG path must still declare palette extern. " +
                "header content:\n$hText",
        )
    }
}
