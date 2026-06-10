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
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.7 Plan 03 Task 1: PlatformerObjPaletteUploadTest
//
// Root cause (13.7-DIAGNOSTIC.md OBJ section, D-04):
//   GBDKPipeline.buildMetaspriteSpritePaletteStatements() emits zero
//   `set_sprite_palette` calls for asset-driven metasprites (spritePath != null).
//   Phase 13.3-17 Direction B deliberately excluded ALL asset-driven metasprites
//   from auto upload to avoid triple-slot collision when a `spritePalette{}` is
//   present. With no upload, the platformer player renders under the
//   `cgb_compatibility()` descending default → dark body / light halo inversion.
//
// Fix (Plan 03, additive arm):
//   When the game has NO GBCPalette(type=SPRITE) declaration, emit
//   `set_sprite_palette` for asset-driven metasprites (spritePath != null).
//   When a spritePalette{} IS present (GBCPalette type=SPRITE exists in
//   gameIR.palettes), the Direction B suppression still applies — no regression.
//
// Slot numbering:
//   Procedural metasprites take slots 0..N-1; asset-driven fallback starts at
//   proceduralMetasprites.size (= proceduralCount + assetDrivenIdx).
//
// This test lives in the backend module as the explicit RED→GREEN emission test
// for the OBJ fix (D-03b exception: asserts the set_sprite_palette CALL is
// emitted, not byte order).
// =============================================================================

class PlatformerObjPaletteUploadTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Asset-driven MetaspriteIR (Path A): spritePath set, frames empty. */
    private fun assetDrivenMetasprite(id: String, spritePath: String): MetaspriteIR =
        MetaspriteIR(id = id, frames = emptyList(), spritePath = spritePath)

    /** Procedural MetaspriteIR (escape-hatch): spritePath null, frames with tiles. */
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

    /** A minimal GBCPalette(type=SPRITE) — simulates a spritePalette{} DSL declaration. */
    private fun spritePaletteEntry(): GBCPalette =
        GBCPalette(
            name = "sprite_pal",
            colors =
                listOf(
                    GBCColor.fromRGB888(0, 0, 0),
                    GBCColor.fromRGB888(85, 85, 85),
                    GBCColor.fromRGB888(170, 170, 170),
                    GBCColor.fromRGB888(255, 255, 255),
                ),
            type = PaletteType.SPRITE,
        )

    /**
     * Build a GBC-compatible game with no spritePalette{} declaration. This is the platformer
     * shape: asset-driven player, zero GBCPalette(type=SPRITE).
     */
    private fun buildGameNoSpritePalette(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "PlatformerObjPaletteTest",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "gameplay")),
            metasprites = metasprites,
            startScene = "gameplay",
        )

    /**
     * Build a GBC-compatible game WITH a spritePalette{} declaration. This is the
     * metasprites-example shape: asset-driven elephant + gray_pal spritePalette. Direction B
     * suppression must apply: no auto upload for asset-driven metasprites.
     */
    private fun buildGameWithSpritePalette(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "MetaspriteSpritePaletteGame",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "play")),
            metasprites = metasprites,
            palettes = listOf(spritePaletteEntry()),
            startScene = "play",
        )

    // =========================================================================
    // Test 1 (RED→GREEN): asset-driven player with NO spritePalette{} emits
    //   `set_sprite_palette(0u, 1u, player_palettes)` — the OBJ fix.
    //
    // RED before fix: buildMetaspriteSpritePaletteStatements() emits nothing for
    //   asset-driven metasprites (Direction B unconditional suppression) →
    //   this test FAILS (the line is absent).
    //
    // GREEN after fix: new additive arm emits the call when hasSpritePalette == false.
    // =========================================================================
    @Test
    fun `asset-driven player with no spritePalette emits set_sprite_palette in main_c`() {
        val game =
            buildGameNoSpritePalette(
                listOf(
                    assetDrivenMetasprite("player", "sprites/player-character-gbapduck-sprites.png")
                )
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, player_palettes)"),
            "13.7-03 OBJ fix: asset-driven player metasprite with no spritePalette{} must emit " +
                "`set_sprite_palette(0u, 1u, player_palettes)` in main.c. " +
                "Without this call the player renders under cgb_compatibility() descending default → " +
                "inverted colors (dark body / light halo). " +
                "Relevant palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 2 (Direction B preserved): asset-driven metasprite WITH a spritePalette{}
    //   present in the game must NOT have its png2asset palette auto-uploaded.
    //
    // This preserves Phase 13.3-17 Direction B: when a GBCPalette(type=SPRITE)
    // is present, the scene's spritePalette{} is the sole OBJ palette authority.
    //
    // Must stay GREEN both before and after the fix.
    // =========================================================================
    @Test
    fun `asset-driven metasprite with spritePalette present does NOT emit auto upload (Direction B)`() {
        val game =
            buildGameWithSpritePalette(
                listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png"))
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        assertFalse(
            mainC.contains("set_sprite_palette(0u, 1u, elephant_palettes)"),
            "13.3-17 Direction B preserved: when a spritePalette{} (GBCPalette type=SPRITE) is " +
                "present in the game, asset-driven metasprites must NOT get an auto set_sprite_palette. " +
                "The scene's spritePalette{} upload is the sole OBJ palette authority. " +
                "Relevant palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 3 (DMG gate): DMG-target game must never emit set_sprite_palette.
    //
    // The GbcTarget.DMG early return must remain unchanged — DMG has no
    // color palettes. Must stay GREEN both before and after the fix.
    // =========================================================================
    @Test
    fun `DMG target emits no set_sprite_palette even for asset-driven player`() {
        val game =
            GameIR(
                name = "DmgPlatformerGame",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.ROM_ONLY,
                        romBanks = 2,
                        gbcTarget = GbcTarget.DMG,
                    ),
                scenes = listOf(SceneIR(id = "gameplay")),
                metasprites =
                    listOf(
                        assetDrivenMetasprite(
                            "player",
                            "sprites/player-character-gbapduck-sprites.png",
                        )
                    ),
                startScene = "gameplay",
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        assertFalse(
            mainC.contains("set_sprite_palette"),
            "DMG gate: GbcTarget.DMG must suppress all set_sprite_palette emission (no GBC hardware). " +
                "Relevant palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 4 (slot offset): procedural metasprite (slot 0) + asset-driven (no
    //   spritePalette{}) → asset-driven uses slot 1 (proceduralCount=1), no
    //   slot collision.
    //
    // RED before fix: asset-driven emits nothing at all.
    // GREEN after fix: asset-driven emits `set_sprite_palette(1u, 1u, player_palettes)`.
    // =========================================================================
    @Test
    fun `slot offset procedural takes slot 0 asset-driven takes slot 1 no collision`() {
        val game =
            buildGameNoSpritePalette(
                listOf(
                    proceduralMetasprite("npc"),
                    assetDrivenMetasprite("player", "sprites/player-character-gbapduck-sprites.png"),
                )
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        // Procedural npc at slot 0
        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, npc_palettes)"),
            "Procedural metasprite must use OBJ palette slot 0 (no change). " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )

        // Asset-driven player at slot 1 (proceduralCount=1)
        assertTrue(
            mainC.contains("set_sprite_palette(1u, 1u, player_palettes)"),
            "Asset-driven player must use OBJ palette slot 1 (proceduralCount=1 offset). " +
                "No slot collision with the procedural npc at slot 0. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 5 (zero procedural, one asset-driven, no spritePalette):
    //   Platformer-template shape: proceduralCount=0 → asset-driven uses slot 0.
    //
    // RED before fix: no emission at all.
    // GREEN after fix: `set_sprite_palette(0u, 1u, player_palettes)` — correct
    //   slot index is proceduralCount (0) + assetDrivenIdx (0) = 0.
    // =========================================================================
    @Test
    fun `zero procedural one asset-driven no spritePalette emits slot 0`() {
        val game =
            buildGameNoSpritePalette(
                listOf(
                    assetDrivenMetasprite("player", "sprites/player-character-gbapduck-sprites.png")
                )
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, player_palettes)"),
            "Platformer-template shape (zero procedural, one asset-driven, no spritePalette{}): " +
                "proceduralCount=0 + assetDrivenIdx=0 → slot 0. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }

    // =========================================================================
    // Phase 13.8 Plan 01 Req 4 — Non-owning spritePalette{} in a different scene
    //   must NOT suppress asset-driven metasprite upload in the gameplay scene.
    //
    // Root cause (13.8-RESEARCH.md Req 4):
    //   GBDKPipeline.buildMetaspriteSpritePaletteStatements() uses a game-global
    //   predicate: hasSpritePalette = gameIR.palettes.any { it.type == SPRITE }.
    //   If ANY scene declares spritePalette{}, the entire game's asset-driven
    //   metasprite uploads are suppressed — even for scenes that never declared
    //   their own spritePalette{}.
    //
    // RED test (13.8-01): Multi-scene GameIR where:
    //   - "title" scene: has a SPRITE GBCPalette (non-owning for the player metasprite)
    //   - "gameplay" scene: has the asset-driven player metasprite
    //   Current behavior: hasSpritePalette=true → player upload suppressed → WRONG.
    //   Expected behavior (post-fix in 13.8-05): scene-scoped predicate → player upload
    //   emitted because the gameplay scene has no spritePalette{} of its own.
    //
    // This test is @Disabled until plan 13.8-05 implements scene-scoped suppression
    // (MetaspriteIR.sceneId field + scene-scoped predicate in
    // buildMetaspriteSpritePaletteStatements).
    // =========================================================================
    @Test
    fun `non-owning spritePalette in title scene does NOT suppress asset-driven upload in gameplay scene`() {
        // Multi-scene game:
        // - "title" scene owns a SPRITE palette (e.g. for a title-screen logo animation)
        //   but does NOT use the asset-driven player metasprite
        // - "gameplay" scene uses the asset-driven player metasprite
        //   and has NO spritePalette{} of its own
        // The player metasprite declares sceneId = "gameplay" (added in 13.8-05) so the
        // scene-scoped predicate checks only whether "gameplay" has a SPRITE palette (it does not).
        val game =
            GameIR(
                name = "MultiSceneNonOwningSpritePaletteTest",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.ROM_ONLY,
                        romBanks = 2,
                        gbcTarget = GbcTarget.GBC_COMPATIBLE,
                    ),
                scenes =
                    listOf(
                        SceneIR(
                            id = "title"
                        ), // non-owning scene — has spritePalette but no player metasprite
                        SceneIR(
                            id = "gameplay"
                        ), // owning scene — has asset-driven player, no spritePalette
                    ),
                metasprites =
                    listOf(
                        // sceneId = "gameplay" links the player to its owning scene
                        assetDrivenMetasprite(
                                "player",
                                "sprites/player-character-gbapduck-sprites.png",
                            )
                            .copy(sceneId = "gameplay")
                    ),
                // A SPRITE palette declared for the "title" scene — non-owning for the player
                palettes = listOf(spritePaletteEntry()),
                startScene = "title",
            )

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        // The player upload must be emitted because the gameplay scene has no spritePalette{}.
        // Currently suppressed because hasSpritePalette is game-global (13.8-RESEARCH.md Req 4).
        assertTrue(
            mainC.contains("set_sprite_palette(0u, 1u, player_palettes)"),
            "Req 4: non-owning spritePalette{} in 'title' scene must NOT suppress the asset-driven " +
                "player upload in 'gameplay' scene. Game-global hasSpritePalette=true currently " +
                "suppresses the upload even though gameplay has no spritePalette{}. " +
                "Fix (13.8-05): MetaspriteIR.sceneId field + scene-scoped predicate. " +
                "Relevant set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }
}
