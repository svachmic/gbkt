/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.WhileOp
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// METADATA ENRICHMENT TESTS
// Verifies that GBDKPipelineV2.buildMetadataFile() emits the new sections:
//   - controls: per-scene input mappings extracted from IfOp conditions
//   - transitions: scene navigation graph extracted from NavigateTo ops
//   - variables with "semantic" field
// =============================================================================

class MetadataEnrichmentTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Controls extraction tests
    // =========================================================================

    @Test
    fun `controls extracted from dpad_held IfOp in frame script`() {
        val game = GameIR(
            name = "ControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        IfOp(
                            condition = CallExpr("dpad_held", listOf(VarRef("J_UP"))),
                            then = emptyList(),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control mapping for game scene")
        val mapping = gameControls.getJSONObject(0)
        assertEquals("UP", mapping.getString("button"), "button should be 'UP'")
        assertEquals("held", mapping.getString("type"), "type should be 'held'")
    }

    @Test
    fun `controls extracted from button_pressed IfOp in frame script`() {
        val game = GameIR(
            name = "ButtonTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        IfOp(
                            condition = CallExpr("button_pressed", listOf(VarRef("J_START"))),
                            then = emptyList(),
                        ),
                    ),
                ),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("title"), "controls should have 'title' scene key")
        val titleControls = controls.getJSONArray("title")
        assertEquals(1, titleControls.length(), "Expected 1 control mapping for title scene")
        val mapping = titleControls.getJSONObject(0)
        assertEquals("START", mapping.getString("button"), "button should be 'START'")
        assertEquals("pressed", mapping.getString("type"), "type should be 'pressed'")
    }

    @Test
    fun `controls extracted from nested IfOp`() {
        val game = GameIR(
            name = "NestedTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        IfOp(
                            condition = CallExpr("dpad_held", listOf(VarRef("J_LEFT"))),
                            then = listOf(
                                IfOp(
                                    condition = CallExpr("button_pressed", listOf(VarRef("J_A"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        val gameControls = controls.getJSONArray("game")
        val buttons = (0 until gameControls.length()).map { gameControls.getJSONObject(it).getString("button") }.toSet()
        assertTrue("LEFT" in buttons, "LEFT should be extracted")
        assertTrue("A" in buttons, "A should be extracted from nested IfOp")
    }

    @Test
    fun `controls object is empty for scene with no input ops`() {
        val game = GameIR(
            name = "NoInputTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        // game scene has no controls — it should not appear or be empty
        if (controls.has("game")) {
            assertEquals(0, controls.getJSONArray("game").length(), "game scene should have no controls")
        }
    }

    @Test
    fun `all GBDK button constants map to human-readable names`() {
        val buttonMappings = listOf(
            "J_UP" to "UP",
            "J_DOWN" to "DOWN",
            "J_LEFT" to "LEFT",
            "J_RIGHT" to "RIGHT",
            "J_A" to "A",
            "J_B" to "B",
            "J_START" to "START",
            "J_SELECT" to "SELECT",
        )
        for ((gbdkConst, expected) in buttonMappings) {
            val game = GameIR(
                name = "ButtonMappingTest",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = listOf(
                    SceneIR(
                        id = "game",
                        enterOps = emptyList(),
                        frameOps = listOf(
                            IfOp(
                                condition = CallExpr("button_pressed", listOf(VarRef(gbdkConst))),
                                then = emptyList(),
                            ),
                        ),
                    ),
                ),
                startScene = "game",
            )
            val json = JSONObject(pipeline.buildMetadataFile(game))
            val controls = json.getJSONObject("controls")
            val gameControls = controls.getJSONArray("game")
            assertEquals(1, gameControls.length(), "Expected 1 mapping for $gbdkConst")
            assertEquals(expected, gameControls.getJSONObject(0).getString("button"),
                "$gbdkConst should map to $expected")
        }
    }

    @Test
    fun `controls extracted from enterOps and exitOps`() {
        val game = GameIR(
            name = "EnterExitControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = listOf(
                        IfOp(
                            condition = CallExpr("button_pressed", listOf(VarRef("J_A"))),
                            then = emptyList(),
                        ),
                    ),
                    exitOps = listOf(
                        IfOp(
                            condition = CallExpr("dpad_held", listOf(VarRef("J_DOWN"))),
                            then = emptyList(),
                        ),
                    ),
                    frameOps = emptyList(),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        val gameControls = controls.getJSONArray("game")
        val buttons = (0 until gameControls.length()).map { gameControls.getJSONObject(it).getString("button") }.toSet()
        assertTrue("A" in buttons, "A should be extracted from enterOps")
        assertTrue("DOWN" in buttons, "DOWN should be extracted from exitOps")
    }

    @Test
    fun `transitions extracted from enterOps`() {
        val game = GameIR(
            name = "EnterTransitionTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = listOf(NavigateTo("game")),
                    frameOps = emptyList(),
                ),
                SceneIR(id = "game", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val transitions = json.getJSONArray("transitions")
        assertEquals(1, transitions.length(), "Expected 1 transition from enterOps")
        assertEquals("title", transitions.getJSONObject(0).getString("from"))
        assertEquals("game", transitions.getJSONObject(0).getString("to"))
    }

    // =========================================================================
    // Transitions extraction tests
    // =========================================================================

    @Test
    fun `transitions extracted from NavigateTo in frame script`() {
        val game = GameIR(
            name = "TransitionTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = listOf(NavigateTo("game")),
                ),
                SceneIR(id = "game", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val transitions = json.getJSONArray("transitions")
        assertEquals(1, transitions.length(), "Expected 1 transition")
        val t = transitions.getJSONObject(0)
        assertEquals("title", t.getString("from"), "transition 'from' should be 'title'")
        assertEquals("game", t.getString("to"), "transition 'to' should be 'game'")
    }

    @Test
    fun `transitions extracted from nested IfOp containing NavigateTo`() {
        val game = GameIR(
            name = "NestedNavTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        IfOp(
                            condition = CallExpr("button_pressed", listOf(VarRef("J_START"))),
                            then = listOf(NavigateTo("game")),
                        ),
                    ),
                ),
                SceneIR(id = "game", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val transitions = json.getJSONArray("transitions")
        assertEquals(1, transitions.length(), "Expected 1 transition from nested IfOp")
        val t = transitions.getJSONObject(0)
        assertEquals("title", t.getString("from"))
        assertEquals("game", t.getString("to"))
    }

    @Test
    fun `transitions are deduplicated`() {
        val game = GameIR(
            name = "DedupTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        NavigateTo("game"),
                        NavigateTo("game"), // duplicate
                    ),
                ),
                SceneIR(id = "game", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val transitions = json.getJSONArray("transitions")
        assertEquals(1, transitions.length(), "Duplicate transitions should be deduplicated")
    }

    @Test
    fun `transitions from multiple scenes`() {
        val game = GameIR(
            name = "MultiTransitionTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = listOf(NavigateTo("game")),
                ),
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(NavigateTo("gameover")),
                ),
                SceneIR(id = "gameover", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val transitions = json.getJSONArray("transitions")
        assertEquals(2, transitions.length(), "Expected 2 transitions")
        val pairs = (0 until transitions.length()).map {
            transitions.getJSONObject(it).getString("from") to transitions.getJSONObject(it).getString("to")
        }.toSet()
        assertTrue("title" to "game" in pairs, "Expected title->game transition")
        assertTrue("game" to "gameover" in pairs, "Expected game->gameover transition")
    }

    // =========================================================================
    // Variable semantics tests
    // =========================================================================

    @Test
    fun `inferVariableSemantic returns correct category for score`() {
        assertEquals("score", inferVariableSemantic("p1Score"), "p1Score should be 'score'")
        assertEquals("score", inferVariableSemantic("score"), "score should be 'score'")
        assertEquals("score", inferVariableSemantic("totalPoints"), "totalPoints should be 'score'")
        assertEquals("score", inferVariableSemantic("gold"), "gold should be 'score'")
        assertEquals("score", inferVariableSemantic("playerGold"), "playerGold should be 'score'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for position`() {
        assertEquals("position", inferVariableSemantic("ball_x"), "ball_x should be 'position'")
        assertEquals("position", inferVariableSemantic("player_y"), "player_y should be 'position'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for velocity`() {
        assertEquals("velocity", inferVariableSemantic("ballDx"), "ballDx should be 'velocity'")
        assertEquals("velocity", inferVariableSemantic("velX"), "velX should be 'velocity'")
        assertEquals("velocity", inferVariableSemantic("speed"), "speed should be 'velocity'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for counter`() {
        assertEquals("counter", inferVariableSemantic("stepCount"), "stepCount should be 'counter'")
        assertEquals("counter", inferVariableSemantic("frameTimer"), "frameTimer should be 'counter'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for state`() {
        assertEquals("state", inferVariableSemantic("current_scene"), "current_scene should be 'state'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for flag`() {
        assertEquals("flag", inferVariableSemantic("metElder"), "metElder should be 'flag'")
        assertEquals("flag", inferVariableSemantic("hasKey"), "hasKey should be 'flag'")
    }

    @Test
    fun `inferVariableSemantic returns correct category for stat`() {
        assertEquals("stat", inferVariableSemantic("hp"), "hp should be 'stat'")
        assertEquals("stat", inferVariableSemantic("playerHealth"), "playerHealth should be 'stat'")
    }

    @Test
    fun `inferVariableSemantic returns unknown for unrecognized names`() {
        assertEquals("unknown", inferVariableSemantic("myCustomVar"), "myCustomVar should be 'unknown'")
        assertEquals("unknown", inferVariableSemantic("fooBar"), "fooBar should be 'unknown'")
    }

    @Test
    fun `inferVariableSemantic rejects false positives from substring matching`() {
        // These names contain substrings like "sp", "met", "has", "is", "step", "count"
        // but should NOT match because those aren't word boundaries.
        assertEquals("unknown", inferVariableSemantic("display"), "display should not match 'sp'")
        assertEquals("unknown", inferVariableSemantic("spawn"), "spawn should not match 'sp'")
        assertEquals("unknown", inferVariableSemantic("temp"), "temp should not match anything")
        assertEquals("unknown", inferVariableSemantic("jump"), "jump should not match anything")
        assertEquals("unknown", inferVariableSemantic("sharpness"), "sharpness should not match 'hp'")
        assertEquals("unknown", inferVariableSemantic("island"), "island should not match 'is'")
        assertEquals("unknown", inferVariableSemantic("metal"), "metal should not match 'met'")
        assertEquals("unknown", inferVariableSemantic("hash"), "hash should not match 'has'")
        assertEquals("unknown", inferVariableSemantic("haste"), "haste should not match 'has'")
    }

    @Test
    fun `inferVariableSemantic true positives still match with word boundaries`() {
        assertEquals("stat", inferVariableSemantic("hp"), "hp should be 'stat'")
        assertEquals("stat", inferVariableSemantic("playerHealth"), "playerHealth should be 'stat'")
        assertEquals("flag", inferVariableSemantic("metElder"), "metElder should be 'flag'")
        assertEquals("flag", inferVariableSemantic("hasKey"), "hasKey should be 'flag'")
        assertEquals("counter", inferVariableSemantic("stepCount"), "stepCount should be 'counter'")
        assertEquals("velocity", inferVariableSemantic("ballDx"), "ballDx should be 'velocity'")
    }

    // =========================================================================
    // Controls extraction - recursion into compound ops
    // =========================================================================

    @Test
    fun `controls extracted from IfOp inside WhileOp`() {
        val game = GameIR(
            name = "WhileOpControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        WhileOp(
                            condition = CallExpr("always_true", emptyList()),
                            body = listOf(
                                IfOp(
                                    condition = CallExpr("dpad_held", listOf(VarRef("J_RIGHT"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control from IfOp inside WhileOp")
        assertEquals("RIGHT", gameControls.getJSONObject(0).getString("button"))
    }

    @Test
    fun `controls extracted from IfOp inside ForOp`() {
        val game = GameIR(
            name = "ForOpControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        ForOp(
                            variable = "i",
                            from = Literal(0),
                            to = Literal(10),
                            body = listOf(
                                IfOp(
                                    condition = CallExpr("button_pressed", listOf(VarRef("J_B"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control from IfOp inside ForOp")
        assertEquals("B", gameControls.getJSONObject(0).getString("button"))
    }

    @Test
    fun `controls extracted from IfOp inside FadeOp`() {
        val game = GameIR(
            name = "FadeOpControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        FadeOp(
                            fadeIn = true,
                            frames = 30,
                            after = listOf(
                                IfOp(
                                    condition = CallExpr("dpad_held", listOf(VarRef("J_LEFT"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control from IfOp inside FadeOp")
        assertEquals("LEFT", gameControls.getJSONObject(0).getString("button"))
    }

    @Test
    fun `controls extracted from IfOp inside PoolDestroyActor`() {
        val game = GameIR(
            name = "PoolDestroyControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        PoolDestroyActor(
                            poolId = "bullets",
                            slotExpr = Literal(0),
                            deathCallbackOps = listOf(
                                IfOp(
                                    condition = CallExpr("button_pressed", listOf(VarRef("J_A"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control from IfOp inside PoolDestroyActor")
        assertEquals("A", gameControls.getJSONObject(0).getString("button"))
    }

    @Test
    fun `controls extracted from IfOp inside PoolForEachActive`() {
        val game = GameIR(
            name = "PoolForEachControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        PoolForEachActive(
                            poolId = "bullets",
                            maxSize = 8,
                            slotVarName = "slot",
                            body = listOf(
                                IfOp(
                                    condition = CallExpr("dpad_pressed", listOf(VarRef("J_DOWN"))),
                                    then = emptyList(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        assertTrue(controls.has("game"), "controls should have 'game' scene key")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Expected 1 control from IfOp inside PoolForEachActive")
        assertEquals("DOWN", gameControls.getJSONObject(0).getString("button"))
    }

    @Test
    fun `controls are deduplicated`() {
        val game = GameIR(
            name = "DedupControlsTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "game",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        IfOp(
                            condition = CallExpr("dpad_held", listOf(VarRef("J_UP"))),
                            then = emptyList(),
                        ),
                        IfOp(
                            condition = CallExpr("dpad_held", listOf(VarRef("J_UP"))),
                            then = emptyList(),
                        ),
                    ),
                ),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val controls = json.getJSONObject("controls")
        val gameControls = controls.getJSONArray("game")
        assertEquals(1, gameControls.length(), "Duplicate controls should be deduplicated")
    }

    @Test
    fun `variables array in JSON contains semantic field`() {
        val game = GameIR(
            name = "SemanticTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            variables = listOf(
                io.github.gbkt.core.ir.VariableDef("score", io.github.gbkt.core.ir.VarType.U8, 0),
                io.github.gbkt.core.ir.VariableDef("ballDx", io.github.gbkt.core.ir.VarType.I8, 1),
                io.github.gbkt.core.ir.VariableDef("myCustomVar", io.github.gbkt.core.ir.VarType.U8, 0),
            ),
            scenes = listOf(
                SceneIR(id = "game", enterOps = emptyList(), frameOps = emptyList()),
            ),
            startScene = "game",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))
        val variables = json.getJSONArray("variables")
        assertEquals(3, variables.length(), "Expected 3 variables")

        val scoreVar = variables.getJSONObject(0)
        assertEquals("score", scoreVar.getString("name"))
        assertEquals("score", scoreVar.getString("semantic"), "score var should have 'score' semantic")

        val ballDxVar = variables.getJSONObject(1)
        assertEquals("ballDx", ballDxVar.getString("name"))
        assertEquals("velocity", ballDxVar.getString("semantic"), "ballDx should have 'velocity' semantic")

        val customVar = variables.getJSONObject(2)
        assertEquals("myCustomVar", customVar.getString("name"))
        assertEquals("unknown", customVar.getString("semantic"), "myCustomVar should have 'unknown' semantic")
    }

    // =========================================================================
    // Multi-scene metadata: text deduplication + terminal scene collection
    // =========================================================================

    @Test
    fun `metadata deduplicates texts and collects terminal scenes across multiple scenes`() {
        val game = GameIR(
            name = "MultiSceneMetadataTest",
            config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
            scenes = listOf(
                SceneIR(
                    id = "title",
                    enterOps = emptyList(),
                    frameOps = emptyList(),
                ),
                SceneIR(
                    id = "gameplay",
                    enterOps = emptyList(),
                    frameOps = emptyList(),
                ),
                SceneIR(
                    id = "gameover",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        PrintAt(x = 6, y = 8, text = "GAME OVER"),
                    ),
                ),
                SceneIR(
                    id = "victory",
                    enterOps = emptyList(),
                    frameOps = listOf(
                        PrintAt(x = 6, y = 8, text = "GAME OVER"),
                    ),
                ),
            ),
            startScene = "title",
        )
        val json = JSONObject(pipeline.buildMetadataFile(game))

        // Terminal scenes: both "gameover" and "victory" match TERMINAL_SCENE_PATTERNS
        val terminalScenes = json.getJSONArray("terminalScenes")
        val terminalIds = (0 until terminalScenes.length()).map { terminalScenes.getString(it) }.toSet()
        assertTrue("gameover" in terminalIds, "gameover should be a terminal scene")
        assertTrue("victory" in terminalIds, "victory should be a terminal scene")

        // Texts: "GAME OVER" appears in both scenes but should be deduplicated
        val texts = json.getJSONArray("texts")
        val textList = (0 until texts.length()).map { texts.getString(it) }
        assertEquals(1, textList.count { it == "GAME OVER" }, "GAME OVER should appear exactly once (deduplicated)")
    }
}
