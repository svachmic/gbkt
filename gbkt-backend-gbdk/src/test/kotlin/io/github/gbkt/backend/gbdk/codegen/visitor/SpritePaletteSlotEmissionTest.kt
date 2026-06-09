/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.8 Plan 01 Req 5 — SpritePaletteSlotEmissionTest (Wave 0 RED scaffold)
//
// Root cause (13.8-RESEARCH.md Req 5, 12.9 WR-05):
//   GBDKPipeline.buildMetaspriteSpritePaletteStatements() assigns OBJ palette
//   slots via mapIndexed { slot, ms -> ... }, which uses list-position as the slot.
//   For games with ≥2 metasprites, the upload slot for each metasprite is its list
//   index — regardless of which actual sub-palette slot that metasprite's draw path
//   uses at runtime.
//
//   If the game author declares metasprites in list order A, B but wants A to use
//   sub-palette slot 2 and B to use sub-palette slot 0, the current code emits:
//     set_sprite_palette(0u, 1u, A_palettes)  ← list index 0, but A needs slot 2
//     set_sprite_palette(1u, 1u, B_palettes)  ← list index 1, but B needs slot 0
//   This mismatches the draw-path's sub-palette selection.
//
// Fix (13.8-05): Add MetaspriteIR.initialSubPaletteSlot: Int? field.
//   When non-null, the pipeline uses this declared slot instead of list index.
//   When null (default), falls back to list index (byte-identity for shipped shapes).
//
// These tests are @Disabled until 13.8-05 adds MetaspriteIR.initialSubPaletteSlot
// and the pipeline reads it. They compile now (no new IR fields referenced) but
// the assertion logic is documented in comments.
// =============================================================================

class SpritePaletteSlotEmissionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Procedural MetaspriteIR (spritePath null). */
    private fun proceduralMetasprite(id: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames =
                listOf(
                    MetaspriteFrame(
                        tiles =
                            listOf(
                                MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                            )
                    )
                ),
            spritePath = null,
        )

    /**
     * Build a minimal GBC game with two procedural metasprites in list order [first, second]. No
     * spritePalette{} — all procedural metasprites get auto uploads.
     */
    private fun buildTwoMetaspriteGame(first: MetaspriteIR, second: MetaspriteIR): GameIR =
        GameIR(
            name = "SpritePaletteSlotTest",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "gameplay")),
            metasprites = listOf(first, second),
            startScene = "gameplay",
        )

    // =========================================================================
    // Test 1 (current list-index behavior — regression guard):
    //   Two procedural metasprites in order [hero, enemy] with no declared sub-palette.
    //   Current code assigns slots 0 (hero) and 1 (enemy) by list index.
    //   This must remain TRUE after 13.8-05 (null-default preserves list-index behavior).
    //
    // This test is GREEN now (current behavior) and must stay GREEN after 13.8-05.
    // =========================================================================
    @Test
    fun `two procedural metasprites without declared slots use list-index slot assignment`() {
        val hero = proceduralMetasprite("hero")
        val enemy = proceduralMetasprite("enemy")
        val game = buildTwoMetaspriteGame(hero, enemy)

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        // hero (list index 0) → slot 0
        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, hero_palettes)"),
            "Regression guard: hero (list index 0) must use slot 0 when no initialSubPaletteSlot declared. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )

        // enemy (list index 1) → slot 1
        assertTrue(
            mainC.contains("set_sprite_palette(1u, 1u, enemy_palettes)"),
            "Regression guard: enemy (list index 1) must use slot 1 when no initialSubPaletteSlot declared. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 2 (Req 5 — GREEN after 13.8-05): Two procedural metasprites with out-of-order
    //   declared sub-palette slots.
    //
    //   hero declares initialSubPaletteSlot = 2 (not list index 0)
    //   enemy declares initialSubPaletteSlot = 0 (not list index 1)
    //
    //   Expected emission:
    //     set_sprite_palette(2u, 1u, hero_palettes)   ← slot 2 = hero's declared slot
    //     set_sprite_palette(0u, 1u, enemy_palettes)  ← slot 0 = enemy's declared slot
    //
    //   Previous emission (WRONG — list-index before 13.8-05):
    //     set_sprite_palette(0u, 1u, hero_palettes)   ← slot 0 = list index 0
    //     set_sprite_palette(1u, 1u, enemy_palettes)  ← slot 1 = list index 1
    // =========================================================================
    @Test
    fun `out-of-order declared sub-palette slots emit declared slot not list index`() {
        val hero = proceduralMetasprite("hero").copy(initialSubPaletteSlot = 2)
        val enemy = proceduralMetasprite("enemy").copy(initialSubPaletteSlot = 0)
        val game = buildTwoMetaspriteGame(hero, enemy)

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        // hero declares slot 2 — must emit set_sprite_palette(2u, 1u, hero_palettes)
        assertTrue(
            mainC.contains("set_sprite_palette(2u, 1u, hero_palettes)"),
            "Req 5: hero declares initialSubPaletteSlot=2 — must emit slot 2 not list-index slot 0. " +
                "Current code uses mapIndexed → always emits slot 0 for the first metasprite. " +
                "Fix in 13.8-05: read MetaspriteIR.initialSubPaletteSlot when non-null. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )

        // enemy declares slot 0 — must emit set_sprite_palette(0u, 1u, enemy_palettes)
        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, enemy_palettes)"),
            "Req 5: enemy declares initialSubPaletteSlot=0 — must emit slot 0 not list-index slot 1. " +
                "Fix in 13.8-05: read MetaspriteIR.initialSubPaletteSlot when non-null. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )

        // hero must NOT emit the wrong list-index slot
        assertTrue(
            !mainC.contains("set_sprite_palette(0u, 1u, hero_palettes)"),
            "Req 5: hero must NOT use list-index slot 0 when initialSubPaletteSlot=2 is declared. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }
}
