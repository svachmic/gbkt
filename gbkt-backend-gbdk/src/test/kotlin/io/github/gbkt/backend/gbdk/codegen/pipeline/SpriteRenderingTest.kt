/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.OAMSlot
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SPRITE RENDERING TESTS
// Focused tests for v2 sprite codegen:
// - OAM slot assignment (single and composite metasprites)
// - Sequential slot assignment for multiple actors
// - Hardware coordinate offsets (+8x, +16y)
// - SHOW_SPRITES in main()
// - set_sprite_data() tile loading in main()
// =============================================================================

/** Build a minimal single-actor GameIR for sprite codegen tests. */
private fun buildGameIR(vararg actors: ActorIR): GameIR =
    GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        actors = actors.toList(),
        scenes = listOf(SceneIR(id = "main")),
        startScene = "main",
    )

class SpriteRenderingTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Single 8x8 sprite generates exactly one OAM slot in update_sprites()
    // =========================================================================
    @Test
    fun `test single 8x8 sprite generates one oam slot`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // update_sprites() should contain exactly one move_sprite call: slot 0
        // CLiteral emits with 'u' suffix (GBDK UINT8 convention): 0u, 1u, 8u, 16u
        assertTrue(mainC.contains("update_sprites"), "Expected update_sprites() function in main.c")
        assertTrue(
            mainC.contains("move_sprite(0u,"),
            "Expected move_sprite(0u, ...) for the single 8x8 actor",
        )
        // Must NOT contain slot 1 (no second OAM slot for 8x8 sprite)
        assertFalse(
            mainC.contains("move_sprite(1u,"),
            "Unexpected move_sprite(1u, ...) for single 8x8 sprite — should only have one OAM slot",
        )
    }

    // =========================================================================
    // Test 2: Composite 16x16 sprite generates four OAM slots
    // =========================================================================
    @Test
    fun `test composite 16x16 sprite generates four oam slots`() {
        val actor =
            ActorIR(
                id = "boss",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/boss.png", AssetType.SPRITE),
                        size = SizeDef(16, 16),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // 16x16 → 2×2 OAM slots: slots 0, 1, 2, 3
        // CLiteral emits with 'u' suffix: 0u, 1u, 2u, 3u
        assertTrue(mainC.contains("move_sprite(0u,"), "Expected OAM slot 0u for 16x16 sprite")
        assertTrue(mainC.contains("move_sprite(1u,"), "Expected OAM slot 1u for 16x16 sprite")
        assertTrue(mainC.contains("move_sprite(2u,"), "Expected OAM slot 2u for 16x16 sprite")
        assertTrue(mainC.contains("move_sprite(3u,"), "Expected OAM slot 3u for 16x16 sprite")
        // Must NOT overflow to slot 4
        assertFalse(
            mainC.contains("move_sprite(4u,"),
            "Unexpected OAM slot 4u — 16x16 sprite should use exactly 4 slots (0-3)",
        )
    }

    // =========================================================================
    // Test 3: Multiple actors get sequential OAM slots
    // =========================================================================
    @Test
    fun `test multiple actors get sequential oam slots`() {
        val actor1 =
            ActorIR(
                id = "paddle",
                position = PositionDef(16, 64),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/paddle.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val actor2 =
            ActorIR(
                id = "ball",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/ball.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor1, actor2)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // paddle → slot 0, ball → slot 1 (CLiteral emits 'u' suffix: 0u, 1u)
        assertTrue(mainC.contains("move_sprite(0u,"), "Expected slot 0u for first actor (paddle)")
        assertTrue(mainC.contains("move_sprite(1u,"), "Expected slot 1u for second actor (ball)")
        // No slot 2 (only two 8x8 sprites)
        assertFalse(mainC.contains("move_sprite(2u,"), "Unexpected slot 2u for two 8x8 actors")
    }

    // =========================================================================
    // Test 4: update_sprites() includes GBDK hardware coordinate offsets (+8x, +16y)
    // =========================================================================
    @Test
    fun `test update_sprites includes hardware offset`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // The update_sprites() function should reference _player_x + 8 and _player_y + 16
        // Find the update_sprites function body section
        val updateSpritesIdx = mainC.indexOf("update_sprites")
        assertTrue(updateSpritesIdx >= 0, "Expected update_sprites function in main.c")

        // Verify hardware offsets appear — _player_x + 8u and _player_y + 16u
        // CLiteral emits non-negative values with 'u' suffix (GBDK UINT8 convention)
        assertTrue(
            mainC.contains("_player_x + 8u"),
            "Expected x hardware offset '+8u' in update_sprites for _player_x",
        )
        assertTrue(
            mainC.contains("_player_y + 16u"),
            "Expected y hardware offset '+16u' in update_sprites for _player_y",
        )
    }

    // =========================================================================
    // Test 5: SHOW_SPRITES is present in main() after DISPLAY_ON
    // =========================================================================
    @Test
    fun `test show sprites in main`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("SHOW_SPRITES"), "Expected SHOW_SPRITES in main()")
        assertTrue(mainC.contains("DISPLAY_ON"), "Expected DISPLAY_ON in main()")
        // SHOW_SPRITES should appear after DISPLAY_ON
        val displayOnIdx = mainC.indexOf("DISPLAY_ON")
        val showSpritesIdx = mainC.indexOf("SHOW_SPRITES")
        assertTrue(
            showSpritesIdx > displayOnIdx,
            "Expected SHOW_SPRITES to appear after DISPLAY_ON in main()",
        )
    }

    // =========================================================================
    // Test 6: set_sprite_data() calls appear in main() init section
    // =========================================================================
    @Test
    fun `test sprite data loading in main`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("set_sprite_data"),
            "Expected set_sprite_data() call in main() init section",
        )
        // The tile data array name is derived from the asset path:
        // "sprites/player.png" → "sprites_player_tiles"
        assertTrue(
            mainC.contains("sprites_player_tiles"),
            "Expected sprites_player_tiles array name derived from asset path",
        )
    }

    // =========================================================================
    // Test 7: update_sprites() is called in game loop after update_joypad()
    // =========================================================================
    @Test
    fun `test update_sprites called in game loop after update_joypad`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // update_sprites() must appear in main.c
        assertTrue(mainC.contains("update_sprites"), "Expected update_sprites in main.c")
        // The game loop call "update_sprites();" must appear after "update_joypad();"
        val updateJoypadIdx = mainC.lastIndexOf("update_joypad")
        val updateSpritesCallIdx = mainC.lastIndexOf("update_sprites()")
        val waitVblIdx = mainC.lastIndexOf("wait_vbl_done")
        assertTrue(
            updateSpritesCallIdx > updateJoypadIdx,
            "Expected update_sprites() call after update_joypad() in game loop",
        )
        assertTrue(
            waitVblIdx > updateSpritesCallIdx,
            "Expected wait_vbl_done() after update_sprites() in game loop",
        )
    }

    // =========================================================================
    // Test 8: Actor with explicit OAMSlot annotation uses that slot number
    // =========================================================================
    @Test
    fun `test actor with explicit oam slot uses annotated slot`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
                oamSlot = OAMSlot(slot = 10), // explicit slot annotation
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // Should use slot 10 (from oamSlot annotation), not 0 (default sequential)
        // CLiteral emits with 'u' suffix: 10u
        assertTrue(
            mainC.contains("move_sprite(10u,"),
            "Expected move_sprite(10u, ...) for actor with explicit OAMSlot(10)",
        )
        assertFalse(
            mainC.contains("move_sprite(0u,"),
            "Expected slot 0u to be unused when actor has explicit OAMSlot(10)",
        )
    }

    // =========================================================================
    // Test 9: sprite asset header #include directives appear in main.c
    // =========================================================================
    @Test
    fun `test sprite asset headers included in main_c`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // "sprites/player.png" → #include "sprites/player.h"
        assertTrue(
            mainC.contains("\"sprites/player.h\""),
            "Expected #include \"sprites/player.h\" in main.c for sprite asset",
        )
    }

    // =========================================================================
    // Test 10: Deduplication — two actors sharing same sprite asset emit one #include
    // =========================================================================
    @Test
    fun `test duplicate sprite assets generate one include`() {
        val paddle1 =
            ActorIR(
                id = "paddle1",
                position = PositionDef(16, 64),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/paddle.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val paddle2 =
            ActorIR(
                id = "paddle2",
                position = PositionDef(152, 64),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/paddle.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(paddle1, paddle2)
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // Both actors share "sprites/paddle.png" — only one #include should appear
        val includeCount = mainC.split("\"sprites/paddle.h\"").size - 1
        assertEquals(
            1,
            includeCount,
            "Expected exactly one #include \"sprites/paddle.h\" for two actors sharing the same sprite asset",
        )
    }

    // =========================================================================
    // Test 11: update_sprites and helper prototypes in game.h
    // =========================================================================
    @Test
    fun `test sprite function prototypes in game_h`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                        size = SizeDef(8, 8),
                    ),
            )
        val gameIR = buildGameIR(actor)
        val output = pipeline.generate(gameIR).files
        val gameH = output["game.h"] ?: error("game.h not generated")

        assertTrue(gameH.contains("update_sprites"), "Expected update_sprites prototype in game.h")
        assertTrue(
            gameH.contains("hide_sprites_range"),
            "Expected hide_sprites_range prototype in game.h",
        )
        assertTrue(
            gameH.contains("show_sprites_range"),
            "Expected show_sprites_range prototype in game.h",
        )
    }

    // =========================================================================
    // Test 12: hide_sprites_range has a real loop body (not a stub comment)
    // =========================================================================
    @Test
    fun `test hide_sprites_range has real body`() {
        val gameIR = buildGameIR()
        val output = pipeline.generate(gameIR).files
        val mainC = output["main.c"] ?: error("main.c not generated")

        // hide_sprites_range should contain a loop that calls move_sprite(i, 0, 0)
        assertTrue(
            mainC.contains("hide_sprites_range"),
            "Expected hide_sprites_range function in main.c",
        )
        assertTrue(
            mainC.contains("move_sprite(i, 0, 0)"),
            "Expected move_sprite(i, 0, 0) inside hide_sprites_range body",
        )
        // Must NOT contain the old stub comment
        assertFalse(
            mainC.contains("TODO: Phase 3"),
            "Expected hide_sprites_range to have real body, not TODO stub comment",
        )
    }
}
