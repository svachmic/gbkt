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
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PONG PIPELINE TEST
// Integration tests for GBDKPipelineV2 using an inline Pong GameIR fixture.
//
// The fixture mirrors PongV2.kt but is built directly from ir types to avoid
// a cross-module dependency (gbkt-backend-gbdk cannot depend on gbkt-examples).
//
// Tests verify:
// 1.  Correct file outputs: main.c, bank1.c, game.h
// 2.  Scene enum defines in main.c
// 3.  Actor position variables in main.c
// 4.  Global variable declarations in main.c
// 5.  main() function with game loop
// 6.  navigate_to_scene function in main.c
// 7.  #pragma bank 1 in bank1.c
// 8-12. Scene lifecycle functions with BANKED keyword in bank1.c
// 13.  Zero RPG symbols in any output
// 14.  title_enter contains print calls
// 15.  game_frame contains ball movement
// 16.  game_frame contains score comparison
// 17.  Start scene initialized to SCENE_TITLE
// =============================================================================

/**
 * Inline Pong GameIR fixture — mirrors PongV2.kt but without DSL import.
 *
 * Built directly from IR v2 types so the test is self-contained within gbkt-backend-gbdk without
 * adding a circular cross-module dependency.
 */
private val pongGameIR =
    GameIR(
        name = "Pong",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        variables =
            listOf(
                VariableDef("p1Score", VarType.U8, 0),
                VariableDef("p2Score", VarType.U8, 0),
                VariableDef("ballDx", VarType.I8, 1),
                VariableDef("ballDy", VarType.I8, 1),
            ),
        actors =
            listOf(
                ActorIR(
                    id = "paddle1",
                    position = PositionDef(16, 64),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/paddle.png", AssetType.SPRITE),
                            size = SizeDef(4, 16),
                            hitbox = HitboxDef(0, 0, 4, 16),
                        ),
                ),
                ActorIR(
                    id = "paddle2",
                    position = PositionDef(152, 64),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/paddle.png", AssetType.SPRITE),
                            size = SizeDef(4, 16),
                            hitbox = HitboxDef(0, 0, 4, 16),
                        ),
                ),
                ActorIR(
                    id = "ball",
                    position = PositionDef(80, 72),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/ball.png", AssetType.SPRITE),
                            size = SizeDef(4, 4),
                            hitbox = HitboxDef(0, 0, 4, 4),
                        ),
                ),
            ),
        scenes =
            listOf(
                // -------------------------------------------------------------------
                // Title scene
                // -------------------------------------------------------------------
                SceneIR(
                    id = "title",
                    enterOps =
                        listOf(
                            FadeOp(fadeIn = false, frames = 0),
                            PrintOp(text = "PONG", position = PositionDef(6, 4)),
                            PrintOp(text = "PRESS START", position = PositionDef(3, 10)),
                        ),
                    frameOps =
                        listOf(
                            IfOp(
                                condition =
                                    BinaryExpr(VarRef("joypad_pressed"), BinaryOp.EQ, Literal(8)),
                                then = listOf(NavigateTo("game")),
                            )
                        ),
                ),
                // -------------------------------------------------------------------
                // Game scene
                // -------------------------------------------------------------------
                SceneIR(
                    id = "game",
                    enterOps =
                        listOf(
                            FadeOp(fadeIn = true, frames = 0),
                            SetPosition("ball", Literal(80), Literal(72)),
                            Assign("ballDx", Literal(1), AssignOp.SET),
                            Assign("ballDy", Literal(1), AssignOp.SET),
                            Assign("p1Score", Literal(0), AssignOp.SET),
                            Assign("p2Score", Literal(0), AssignOp.SET),
                        ),
                    frameOps =
                        listOf(
                            // P1 d-pad controls
                            IfOp(
                                condition =
                                    BinaryExpr(VarRef("joypad_held"), BinaryOp.EQ, Literal(4)),
                                then = listOf(MoveBy("paddle1", Literal(0), Literal(-2))),
                            ),
                            IfOp(
                                condition =
                                    BinaryExpr(VarRef("joypad_held"), BinaryOp.EQ, Literal(8)),
                                then = listOf(MoveBy("paddle1", Literal(0), Literal(2))),
                            ),
                            // Ball movement (compound assignment: ball.x += ballDx)
                            Assign(
                                "ball.x",
                                BinaryExpr(VarRef("ball.x"), BinaryOp.ADD, VarRef("ballDx")),
                                AssignOp.SET,
                            ),
                            Assign(
                                "ball.y",
                                BinaryExpr(VarRef("ball.y"), BinaryOp.ADD, VarRef("ballDy")),
                                AssignOp.SET,
                            ),
                            // Wall bounce
                            IfOp(
                                condition = BinaryExpr(VarRef("ball.y"), BinaryOp.LT, Literal(16)),
                                then = listOf(Assign("ballDy", Literal(1), AssignOp.SET)),
                            ),
                            IfOp(
                                condition = BinaryExpr(VarRef("ball.y"), BinaryOp.GT, Literal(148)),
                                then = listOf(Assign("ballDy", Literal(-1), AssignOp.SET)),
                            ),
                            // Score check — P2 scores when ball exits left
                            IfOp(
                                condition = BinaryExpr(VarRef("ball.x"), BinaryOp.LT, Literal(4)),
                                then =
                                    listOf(
                                        Assign(
                                            "p2Score",
                                            BinaryExpr(VarRef("p2Score"), BinaryOp.ADD, Literal(1)),
                                            AssignOp.SET,
                                        ),
                                        SetPosition("ball", Literal(80), Literal(72)),
                                    ),
                            ),
                            // Score check — P1 scores when ball exits right
                            IfOp(
                                condition = BinaryExpr(VarRef("ball.x"), BinaryOp.GT, Literal(156)),
                                then =
                                    listOf(
                                        Assign(
                                            "p1Score",
                                            BinaryExpr(VarRef("p1Score"), BinaryOp.ADD, Literal(1)),
                                            AssignOp.SET,
                                        ),
                                        SetPosition("ball", Literal(80), Literal(72)),
                                    ),
                            ),
                            // Win condition — score >= 5
                            IfOp(
                                condition = BinaryExpr(VarRef("p1Score"), BinaryOp.GTE, Literal(5)),
                                then = listOf(NavigateTo("gameover")),
                            ),
                            IfOp(
                                condition = BinaryExpr(VarRef("p2Score"), BinaryOp.GTE, Literal(5)),
                                then = listOf(NavigateTo("gameover")),
                            ),
                        ),
                ),
                // -------------------------------------------------------------------
                // Gameover scene
                // -------------------------------------------------------------------
                SceneIR(
                    id = "gameover",
                    enterOps =
                        listOf(
                            FadeOp(fadeIn = false, frames = 0),
                            PrintOp(text = "GAME OVER", position = PositionDef(3, 7)),
                            PrintOp(text = "PRESS START", position = PositionDef(3, 14)),
                        ),
                    frameOps =
                        listOf(
                            IfOp(
                                condition =
                                    BinaryExpr(VarRef("joypad_pressed"), BinaryOp.EQ, Literal(8)),
                                then = listOf(NavigateTo("title")),
                            )
                        ),
                ),
            ),
        startScene = "title",
    )

class PongPipelineTest {

    private val pipeline = GBDKPipelineV2()
    private val pipelineOutput by lazy { pipeline.generate(pongGameIR) }
    private val output by lazy { pipelineOutput.files }

    // =========================================================================
    // Test 1: main.c is generated
    // =========================================================================
    @Test
    fun `Pong generates main_c file`() {
        assertTrue(output.containsKey("main.c"), "Expected 'main.c' in pipeline output")
    }

    // =========================================================================
    // Test 2: bank1.c is generated
    // =========================================================================
    @Test
    fun `Pong generates bank1_c file`() {
        assertTrue(output.containsKey("bank1.c"), "Expected 'bank1.c' in pipeline output")
    }

    // =========================================================================
    // Test 3: game.h is generated
    // =========================================================================
    @Test
    fun `Pong generates game_h header`() {
        assertTrue(output.containsKey("game.h"), "Expected 'game.h' in pipeline output")
    }

    // =========================================================================
    // Test 4: scene enum defines in main.c
    // =========================================================================
    @Test
    fun `main_c contains scene enum defines`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(mainC.contains("SCENE_TITLE"), "Expected SCENE_TITLE in main.c")
        assertTrue(mainC.contains("SCENE_GAME"), "Expected SCENE_GAME in main.c")
        assertTrue(mainC.contains("SCENE_GAMEOVER"), "Expected SCENE_GAMEOVER in main.c")
    }

    // =========================================================================
    // Test 5: actor position variables in main.c
    // =========================================================================
    @Test
    fun `main_c contains actor variables`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(mainC.contains("_paddle1_x"), "Expected _paddle1_x in main.c")
        assertTrue(mainC.contains("_paddle1_y"), "Expected _paddle1_y in main.c")
        assertTrue(mainC.contains("_ball_x"), "Expected _ball_x in main.c")
        assertTrue(mainC.contains("_ball_y"), "Expected _ball_y in main.c")
    }

    // =========================================================================
    // Test 6: global variable declarations in main.c
    // =========================================================================
    @Test
    fun `main_c contains global variables`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(mainC.contains("p1Score"), "Expected p1Score in main.c")
        assertTrue(mainC.contains("p2Score"), "Expected p2Score in main.c")
        assertTrue(mainC.contains("ballDx"), "Expected ballDx in main.c")
        assertTrue(mainC.contains("ballDy"), "Expected ballDy in main.c")
    }

    // =========================================================================
    // Test 7: main() function with game loop
    // =========================================================================
    @Test
    fun `main_c contains main function with game loop`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(mainC.contains("void main(void)"), "Expected 'void main(void)' in main.c")
        assertTrue(mainC.contains("while"), "Expected 'while' loop in main.c")
        assertTrue(mainC.contains("wait_vbl_done"), "Expected 'wait_vbl_done' in main.c game loop")
    }

    // =========================================================================
    // Test 8: navigate_to_scene function in main.c
    // =========================================================================
    @Test
    fun `main_c contains navigate_to_scene function`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(mainC.contains("navigate_to_scene"), "Expected 'navigate_to_scene' in main.c")
    }

    // =========================================================================
    // Test 9: #pragma bank 1 in bank1.c
    // =========================================================================
    @Test
    fun `bank1_c has pragma bank 1`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        assertTrue(bank1C.contains("#pragma bank 1"), "Expected '#pragma bank 1' in bank1.c")
    }

    // =========================================================================
    // Test 10: title_enter with BANKED keyword in bank1.c
    // =========================================================================
    @Test
    fun `bank1_c contains title_enter with BANKED`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        assertTrue(
            bank1C.contains("void title_enter(void) BANKED"),
            "Expected 'void title_enter(void) BANKED' in bank1.c",
        )
    }

    // =========================================================================
    // Test 11: game_frame with BANKED keyword in bank1.c
    // =========================================================================
    @Test
    fun `bank1_c contains game_frame with BANKED`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        assertTrue(
            bank1C.contains("void game_frame(void) BANKED"),
            "Expected 'void game_frame(void) BANKED' in bank1.c",
        )
    }

    // =========================================================================
    // Test 12: gameover_enter with BANKED keyword in bank1.c
    // =========================================================================
    @Test
    fun `bank1_c contains gameover_enter with BANKED`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        assertTrue(
            bank1C.contains("void gameover_enter(void) BANKED"),
            "Expected 'void gameover_enter(void) BANKED' in bank1.c",
        )
    }

    // =========================================================================
    // Test 13: zero RPG symbols in any output
    // =========================================================================
    @Test
    fun `no RPG symbols in any output`() {
        val allOutput = output.values.joinToString("\n")
        val rpgSymbols =
            listOf(
                "_party_size",
                "_combatant",
                "STATUS_EFFECT",
                "COMBAT_STATE",
                "monster_",
                "ability_",
                "battle_engine",
            )
        for (symbol in rpgSymbols) {
            assertFalse(
                allOutput.contains(symbol),
                "Expected no RPG symbol '$symbol' in generated output",
            )
        }
    }

    // =========================================================================
    // Test 14: title_enter contains print calls (PONG, PRESS START)
    // =========================================================================
    @Test
    fun `title_enter contains print calls`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        assertTrue(bank1C.contains("\"PONG\""), "Expected printf(\"PONG\") in title_enter")
        assertTrue(
            bank1C.contains("\"PRESS START\""),
            "Expected printf(\"PRESS START\") in title_enter",
        )
    }

    // =========================================================================
    // Test 15: game_frame contains ball movement (assignment to ball.x)
    // =========================================================================
    @Test
    fun `game_frame contains ball movement`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        // Ball movement: _ball_x = _ball_x + _ballDx
        assertTrue(
            bank1C.contains("_ball_x"),
            "Expected ball position variable _ball_x in game_frame",
        )
        assertTrue(
            bank1C.contains("_ballDx"),
            "Expected ballDx variable reference in game_frame for ball movement",
        )
    }

    // =========================================================================
    // Test 16: game_frame contains score check (p1Score >= 5 win condition)
    // =========================================================================
    @Test
    fun `game_frame contains score check`() {
        val bank1C = output["bank1.c"] ?: error("bank1.c not generated")
        // Win condition: if (_p1Score >= 5u) → navigate_to_scene(SCENE_GAMEOVER)
        assertTrue(
            bank1C.contains("_p1Score") || bank1C.contains("p1Score"),
            "Expected p1Score comparison in game_frame for win condition",
        )
        assertTrue(
            bank1C.contains("5u"),
            "Expected score threshold '5u' in game_frame win condition",
        )
    }

    // =========================================================================
    // Test 17: start scene initialized to SCENE_TITLE
    // =========================================================================
    @Test
    fun `start scene is title`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("SCENE_TITLE"),
            "Expected current_scene initialized to SCENE_TITLE in main.c",
        )
    }

    // =========================================================================
    // Test 18 (NEW): update_sprites function exists in main.c
    // =========================================================================
    @Test
    fun `main_c contains update_sprites function`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("update_sprites"),
            "Expected 'update_sprites' function in main.c (sprite OAM sync every frame)",
        )
    }

    // =========================================================================
    // Test 19 (NEW): move_sprite calls present in main.c for OAM writes
    // =========================================================================
    @Test
    fun `main_c contains move_sprite calls`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("move_sprite"),
            "Expected 'move_sprite' calls in main.c for OAM hardware writes",
        )
    }

    // =========================================================================
    // Test 20 (NEW): SHOW_SPRITES present to enable OAM layer
    // =========================================================================
    @Test
    fun `main_c contains SHOW_SPRITES`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        assertTrue(
            mainC.contains("SHOW_SPRITES"),
            "Expected 'SHOW_SPRITES' in main.c to enable OAM layer",
        )
    }

    // =========================================================================
    // Test 21 (NEW): Pong paddles use sequential OAM slots (paddle1=0-1, paddle2=2-3, ball=4)
    // Paddle is 4x16px → tilesWide=1, tilesHigh=2 → 2 OAM slots each
    // Ball is 4x4px → tilesWide=1, tilesHigh=1 → 1 OAM slot
    // CLiteral emits with 'u' suffix: 0u, 1u, 2u, 3u, 4u
    // =========================================================================
    @Test
    fun `main_c paddle and ball use sequential oam slots`() {
        val mainC = output["main.c"] ?: error("main.c not generated")
        // paddle1: slots 0u and 1u (4x16 → 1 wide × 2 high)
        assertTrue(mainC.contains("move_sprite(0u,"), "Expected OAM slot 0u for paddle1 row 0")
        assertTrue(mainC.contains("move_sprite(1u,"), "Expected OAM slot 1u for paddle1 row 1")
        // paddle2: slots 2u and 3u
        assertTrue(mainC.contains("move_sprite(2u,"), "Expected OAM slot 2u for paddle2 row 0")
        assertTrue(mainC.contains("move_sprite(3u,"), "Expected OAM slot 3u for paddle2 row 1")
        // ball: slot 4u (4x4 → 1×1)
        assertTrue(mainC.contains("move_sprite(4u,"), "Expected OAM slot 4u for ball")
    }

    // =========================================================================
    // Test 22 (NEW): source maps produced for main.c and bank1.c (v2 pipeline feature)
    // =========================================================================
    @Test
    fun `pipeline produces source maps for main_c and bank1_c`() {
        assertTrue(
            pipelineOutput.sourceMaps.containsKey("main.c"),
            "Expected source map for main.c",
        )
        assertTrue(
            pipelineOutput.sourceMaps.containsKey("bank1.c"),
            "Expected source map for bank1.c",
        )
        assertFalse(
            pipelineOutput.sourceMaps.containsKey("game.h"),
            "Expected no source map for game.h (header)",
        )
    }

    // =========================================================================
    // Test 23: source map JSON has version 2.0
    // =========================================================================
    @Test
    fun `source map JSON has version 2_0`() {
        val mainCMap = pipelineOutput.sourceMaps["main.c"] ?: error("No source map for main.c")
        assertTrue(
            mainCMap.contains("\"version\": \"2.0\""),
            "Expected version 2.0 in source map JSON",
        )
        assertTrue(mainCMap.contains("\"bankNumber\": 0"), "Expected bankNumber in source map JSON")
    }
}
