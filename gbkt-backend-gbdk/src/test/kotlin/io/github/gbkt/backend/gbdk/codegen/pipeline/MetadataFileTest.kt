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
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.OAMSlot
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.emulator.agent.GameMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// METADATA FILE ROUND-TRIP TEST
// Verifies that GBDKPipelineV2.buildMetadataFile() produces JSON that
// GameMetadata.fromJsonString() can parse back faithfully.
//
// Fixture: 3 scenes, 5 actors (one spriteless, one with null OAM slot).
// =============================================================================

/**
 * Minimal GameIR fixture for metadata round-trip testing.
 *
 * Actors:
 * - "player":   8x16 sprite, OAMSlot(0) -> oamCount = 1*2 = 2
 * - "enemy":    16x16 sprite, OAMSlot(2) -> oamCount = 2*2 = 4
 * - "bullet":   8x8 sprite, OAMSlot(6)  -> oamCount = 1*1 = 1
 * - "trigger":  NO sprite              -> excluded from JSON
 * - "particle": 8x8 sprite, null OAM   -> oamStart = -1
 */
private val metadataTestFixture =
    GameIR(
        name = "MetadataTest",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        variables = listOf(
            VariableDef("score", VarType.U8, 0),
            VariableDef("ballDx", VarType.I8, 1),
        ),
        actors =
            listOf(
                ActorIR(
                    id = "player",
                    position = PositionDef(80, 72),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                            size = SizeDef(8, 16),
                            hitbox = HitboxDef(0, 0, 8, 16),
                        ),
                    oamSlot = OAMSlot(0),
                ),
                ActorIR(
                    id = "enemy",
                    position = PositionDef(120, 72),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/enemy.png", AssetType.SPRITE),
                            size = SizeDef(16, 16),
                            hitbox = HitboxDef(0, 0, 16, 16),
                        ),
                    oamSlot = OAMSlot(2),
                ),
                ActorIR(
                    id = "bullet",
                    position = PositionDef(80, 80),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/bullet.png", AssetType.SPRITE),
                            size = SizeDef(8, 8),
                            hitbox = HitboxDef(0, 0, 8, 8),
                        ),
                    oamSlot = OAMSlot(6),
                ),
                ActorIR(
                    id = "trigger",
                    position = PositionDef(40, 40),
                    // No sprite — should be excluded from metadata JSON
                ),
                ActorIR(
                    id = "particle",
                    position = PositionDef(60, 60),
                    sprite =
                        SpriteDef(
                            assetRef = AssetRef("sprites/particle.png", AssetType.SPRITE),
                            size = SizeDef(8, 8),
                            hitbox = HitboxDef(0, 0, 8, 8),
                        ),
                    // oamSlot = null — should emit oamStart = -1
                ),
            ),
        scenes =
            listOf(
                SceneIR(
                    id = "title",
                    enterOps = listOf(
                        PrintCentered("HELLO", 5),
                        PrintAt(0, 1, "PRESS START"),
                        // Duplicate — should be deduplicated
                        PrintCentered("HELLO", 8),
                    ),
                    frameOps = emptyList(),
                ),
                SceneIR(
                    id = "gameplay",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        // Format string with values — should be SKIPPED
                        PrintOp("P1:%d P2:%d", listOf(Literal(0), Literal(0))),
                        // Nested in IfOp
                        IfOp(
                            condition = Literal(1),
                            then = listOf(PrintCentered("GAME OVER", 9)),
                            otherwise = listOf(PrintAt(0, 0, "VICTORY")),
                        ),
                    ),
                ),
                SceneIR(id = "gameover", enterOps = emptyList(), frameOps = emptyList()),
            ),
        startScene = "title",
    )

class MetadataFileTest {

    private val pipeline = GBDKPipelineV2()
    private val json by lazy { pipeline.buildMetadataFile(metadataTestFixture) }
    private val metadata by lazy { GameMetadata.fromJsonString(json) }

    // =========================================================================
    // Test 1: Round trip preserves scene count and mapping
    // =========================================================================
    @Test
    fun `round trip preserves scene count and mapping`() {
        val scenes = metadata.scenes
        assertEquals(3, scenes.sceneNames.size, "Expected 3 scenes in metadata")
        assertEquals(0, scenes.indexOf("title"), "title should be scene index 0")
        assertEquals(1, scenes.indexOf("gameplay"), "gameplay should be scene index 1")
        assertEquals(2, scenes.indexOf("gameover"), "gameover should be scene index 2")
        // Reverse lookup
        assertEquals("title", scenes.nameOf(0), "Scene index 0 should be 'title'")
        assertEquals("gameplay", scenes.nameOf(1), "Scene index 1 should be 'gameplay'")
        assertEquals("gameover", scenes.nameOf(2), "Scene index 2 should be 'gameover'")
    }

    // =========================================================================
    // Test 2: Round trip excludes spriteless actors
    // =========================================================================
    @Test
    fun `round trip excludes spriteless actors`() {
        assertEquals(4, metadata.actors.size, "Expected 4 actors (trigger has no sprite)")
        val actorNames = metadata.actors.map { it.name }
        assert("player" in actorNames) { "Expected 'player' in actors" }
        assert("enemy" in actorNames) { "Expected 'enemy' in actors" }
        assert("bullet" in actorNames) { "Expected 'bullet' in actors" }
        assert("particle" in actorNames) { "Expected 'particle' in actors" }
        assert("trigger" !in actorNames) { "Expected 'trigger' NOT in actors (no sprite)" }
    }

    // =========================================================================
    // Test 3: Round trip preserves OAM start and tile count
    // =========================================================================
    @Test
    fun `round trip preserves OAM start and tile count`() {
        val player = metadata.actor("player")
        assertNotNull(player, "player actor should exist")
        assertEquals(0, player.oamStart, "player oamStart")
        assertEquals(2, player.oamCount, "player oamCount (8x16 = 1*2 tiles)")

        val enemy = metadata.actor("enemy")
        assertNotNull(enemy, "enemy actor should exist")
        assertEquals(2, enemy.oamStart, "enemy oamStart")
        assertEquals(4, enemy.oamCount, "enemy oamCount (16x16 = 2*2 tiles)")

        val bullet = metadata.actor("bullet")
        assertNotNull(bullet, "bullet actor should exist")
        assertEquals(6, bullet.oamStart, "bullet oamStart")
        assertEquals(1, bullet.oamCount, "bullet oamCount (8x8 = 1*1 tile)")
    }

    // =========================================================================
    // Test 4: Round trip preserves sprite dimensions
    // =========================================================================
    @Test
    fun `round trip preserves sprite dimensions`() {
        val player = metadata.actor("player")
        assertNotNull(player, "player actor should exist")
        assertEquals(8, player.spriteWidth, "player spriteWidth")
        assertEquals(16, player.spriteHeight, "player spriteHeight")

        val enemy = metadata.actor("enemy")
        assertNotNull(enemy, "enemy actor should exist")
        assertEquals(16, enemy.spriteWidth, "enemy spriteWidth")
        assertEquals(16, enemy.spriteHeight, "enemy spriteHeight")

        val bullet = metadata.actor("bullet")
        assertNotNull(bullet, "bullet actor should exist")
        assertEquals(8, bullet.spriteWidth, "bullet spriteWidth")
        assertEquals(8, bullet.spriteHeight, "bullet spriteHeight")
    }

    // =========================================================================
    // Test 5: Round trip preserves variable name convention
    // =========================================================================
    @Test
    fun `round trip preserves variable name convention`() {
        val player = metadata.actor("player")
        assertNotNull(player, "player actor should exist")
        assertEquals("player_x", player.xVar, "player xVar")
        assertEquals("player_y", player.yVar, "player yVar")

        val enemy = metadata.actor("enemy")
        assertNotNull(enemy, "enemy actor should exist")
        assertEquals("enemy_x", enemy.xVar, "enemy xVar")
        assertEquals("enemy_y", enemy.yVar, "enemy yVar")
    }

    // =========================================================================
    // Test 6: Null OAM slot emits oamStart minus one
    // =========================================================================
    @Test
    fun `null OAM slot emits oamStart minus one`() {
        val particle = metadata.actor("particle")
        assertNotNull(particle, "particle actor should exist")
        assertEquals(-1, particle.oamStart, "particle oamStart should be -1 (null OAM slot)")
    }

    // =========================================================================
    // Test 7: Variables array is present with correct names and types
    // =========================================================================
    @Test
    fun `variables array contains DSL-declared variables`() {
        val parsed = org.json.JSONObject(json)
        val variables = parsed.getJSONArray("variables")
        assertEquals(2, variables.length(), "Expected 2 variables")

        val v0 = variables.getJSONObject(0)
        assertEquals("score", v0.getString("name"))
        assertEquals("U8", v0.getString("type"))

        val v1 = variables.getJSONObject(1)
        assertEquals("ballDx", v1.getString("name"))
        assertEquals("I8", v1.getString("type"))
    }

    // =========================================================================
    // Test 8: Texts array contains literal strings, deduplicates, skips formats
    // =========================================================================
    @Test
    fun `texts array extracts literals and deduplicates`() {
        val parsed = org.json.JSONObject(json)
        val texts = (0 until parsed.getJSONArray("texts").length()).map {
            parsed.getJSONArray("texts").getString(it)
        }

        // Literal strings should be present
        assertTrue("HELLO" in texts, "HELLO should be extracted from PrintCentered")
        assertTrue("PRESS START" in texts, "PRESS START should be extracted from PrintAt")
        assertTrue("GAME OVER" in texts, "GAME OVER should be extracted from nested IfOp.then")
        assertTrue("VICTORY" in texts, "VICTORY should be extracted from nested IfOp.otherwise")

        // Format string with values should be skipped
        assertTrue("P1:%d P2:%d" !in texts, "Format string with values should be skipped")

        // HELLO should appear only once (deduplicated)
        assertEquals(1, texts.count { it == "HELLO" }, "HELLO should be deduplicated")

        // Total count: HELLO, PRESS START, GAME OVER, VICTORY
        assertEquals(4, texts.size, "Expected 4 unique texts")
    }

    // =========================================================================
    // Test 9: Padded text is trimmed
    // =========================================================================
    @Test
    fun `padded text is trimmed`() {
        val game = GameIR(
            name = "TrimTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "main",
                    enterOps = listOf(PrintAt(0, 0, "     SCORE!     ")),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "main",
        )
        val parsed = org.json.JSONObject(pipeline.buildMetadataFile(game))
        val texts = (0 until parsed.getJSONArray("texts").length()).map {
            parsed.getJSONArray("texts").getString(it)
        }
        assertEquals(listOf("SCORE!"), texts, "Padded text should be trimmed")
    }

    // =========================================================================
    // Test 10: Padded and unpadded variants merge into one entry
    // =========================================================================
    @Test
    fun `padded and unpadded variants merge`() {
        val game = GameIR(
            name = "MergeTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "main",
                    enterOps = listOf(
                        PrintCentered("     SCORE!     ", 5),
                        PrintAt(0, 1, "SCORE!"),
                    ),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "main",
        )
        val parsed = org.json.JSONObject(pipeline.buildMetadataFile(game))
        val texts = (0 until parsed.getJSONArray("texts").length()).map {
            parsed.getJSONArray("texts").getString(it)
        }
        assertEquals(1, texts.size, "Padded and unpadded should merge")
        assertEquals("SCORE!", texts[0])
    }

    // =========================================================================
    // Test 11: All-whitespace string is excluded
    // =========================================================================
    @Test
    fun `all whitespace string is excluded`() {
        val game = GameIR(
            name = "WhitespaceTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "main",
                    enterOps = listOf(
                        PrintAt(0, 0, "HELLO"),
                        PrintAt(0, 1, "                "),
                    ),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "main",
        )
        val parsed = org.json.JSONObject(pipeline.buildMetadataFile(game))
        val texts = (0 until parsed.getJSONArray("texts").length()).map {
            parsed.getJSONArray("texts").getString(it)
        }
        assertEquals(listOf("HELLO"), texts, "All-whitespace should be excluded")
    }

    // =========================================================================
    // Test 12: Internal whitespace is preserved
    // =========================================================================
    @Test
    fun `internal whitespace is preserved`() {
        val game = GameIR(
            name = "InternalSpaceTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "main",
                    enterOps = listOf(PrintAt(0, 0, "  PRESS  START  ")),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "main",
        )
        val parsed = org.json.JSONObject(pipeline.buildMetadataFile(game))
        val texts = (0 until parsed.getJSONArray("texts").length()).map {
            parsed.getJSONArray("texts").getString(it)
        }
        assertEquals(listOf("PRESS  START"), texts, "Internal whitespace should be preserved")
    }

    // =========================================================================
    // Test 13: terminalScenes contains gameover
    // =========================================================================
    @Test
    fun `terminalScenes contains gameover`() {
        val parsed = org.json.JSONObject(json)
        val terminal = (0 until parsed.getJSONArray("terminalScenes").length()).map {
            parsed.getJSONArray("terminalScenes").getString(it)
        }
        assertTrue("gameover" in terminal, "gameover should appear in terminalScenes")
    }

    // =========================================================================
    // Test 14: terminalScenes empty when no terminal scenes
    // =========================================================================
    @Test
    fun `terminalScenes empty when no terminal scenes`() {
        val game = GameIR(
            name = "NoTerminalTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(id = "title", enterOps = emptyList(), frameOps = emptyList()),
                SceneIR(id = "gameplay", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val parsed = org.json.JSONObject(pipeline.buildMetadataFile(game))
        val terminal = parsed.getJSONArray("terminalScenes")
        assertEquals(0, terminal.length(), "Expected empty terminalScenes")
    }

    // =========================================================================
    // Test 15: Round trip preserves variables in GameMetadata
    // =========================================================================
    @Test
    fun `round trip preserves variables in GameMetadata`() {
        assertEquals(2, metadata.variables.size, "Expected 2 variables")
        assertEquals("score", metadata.variables[0].name)
        assertEquals("U8", metadata.variables[0].type)
        assertEquals("ballDx", metadata.variables[1].name)
        assertEquals("I8", metadata.variables[1].type)
    }

    // =========================================================================
    // Test 16: Round trip preserves terminalScenes in GameMetadata
    // =========================================================================
    @Test
    fun `round trip preserves terminalScenes in GameMetadata`() {
        assertEquals(setOf("gameover"), metadata.terminalScenes)
    }
}
