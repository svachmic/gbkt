/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.json.JSONObject

// =============================================================================
// GAME IR SERIALIZER ROUND-TRIP TESTS
// Verifies that GameIRSerializer.toJson/fromJson produce correct JSON and that
// core IR types round-trip with full fidelity.
// =============================================================================

class GameIRSerializerTest {

    // =========================================================================
    // Test 1: Minimal game round-trip
    // =========================================================================

    @Test
    fun `minimal game round-trips through JSON`() {
        val game =
            GameIR(
                name = "MinimalGame",
                startScene = "main",
                scenes =
                    listOf(
                        SceneIR(
                            id = "main",
                            enterOps =
                                listOf(
                                    Assign(target = "score", value = Literal(0), op = AssignOp.SET)
                                ),
                        )
                    ),
                variables = listOf(VariableDef(name = "score", type = VarType.U8, initialValue = 0)),
            )

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertEquals(game.name, back.name)
        assertEquals(game.startScene, back.startScene)
        assertEquals(1, back.scenes.size)
        assertEquals("main", back.scenes[0].id)
        assertEquals(1, back.scenes[0].enterOps.size)
        val op = back.scenes[0].enterOps[0]
        assertIs<Assign>(op)
        assertEquals("score", op.target)
        assertIs<Literal>(op.value)
        assertEquals(0, op.value.value)
        assertEquals(1, back.variables.size)
        assertEquals("score", back.variables[0].name)
        assertEquals(VarType.U8, back.variables[0].type)
    }

    // =========================================================================
    // Test 2: Complex game round-trip (scenes, actors, ScriptOps, Exprs)
    // =========================================================================

    @Test
    fun `complex game with scenes actors and ScriptOps round-trips through JSON`() {
        val game =
            GameIR(
                name = "ComplexGame",
                startScene = "gameplay",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.MBC5,
                        romBanks = 8,
                        ramBanks = 1,
                        gbcTarget = GbcTarget.GBC_COMPATIBLE,
                    ),
                scenes =
                    listOf(
                        SceneIR(
                            id = "title",
                            enterOps =
                                listOf(
                                    PrintAt(x = 5, y = 8, text = "Press Start"),
                                    NavigateTo(sceneId = "gameplay"),
                                ),
                            frameOps =
                                listOf(
                                    IfOp(
                                        condition =
                                            BinaryExpr(
                                                left = VarRef("score"),
                                                op = BinaryOp.GTE,
                                                right = Literal(100),
                                            ),
                                        then = listOf(NavigateTo(sceneId = "victory")),
                                        otherwise =
                                            listOf(Assign(target = "score", value = Literal(0))),
                                    )
                                ),
                            exitOps = listOf(ScreenClear()),
                            actorIds = listOf("player", "enemy"),
                        ),
                        SceneIR(
                            id = "gameplay",
                            enterOps = listOf(Assign(target = "lives", value = Literal(3))),
                            frameOps =
                                listOf(
                                    WhileOp(
                                        condition = VarRef("running"),
                                        body = listOf(RawOp("// loop")),
                                    )
                                ),
                        ),
                        SceneIR(
                            id = "victory",
                            enterOps = listOf(MusicPlay(songId = "theme", fadeInFrames = 30)),
                        ),
                    ),
                actors =
                    listOf(
                        ActorIR(
                            id = "player",
                            position = PositionDef(80, 72),
                            sprite =
                                SpriteDef(
                                    assetRef = AssetRef("sprites/player.png", AssetType.SPRITE),
                                    size = SizeDef(16, 16),
                                    hitbox = HitboxDef(0, 8, 16, 8),
                                ),
                        ),
                        ActorIR(id = "enemy", position = PositionDef(40, 40)),
                        ActorIR(id = "boss", position = PositionDef(120, 32)),
                    ),
                variables =
                    listOf(
                        VariableDef("score", VarType.U16, 0),
                        VariableDef("lives", VarType.U8, 3),
                        VariableDef("running", VarType.U8, 1),
                    ),
                arrays = listOf(ArrayDef("bricks", VarType.U8, 30)),
            )

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        // Top-level fields
        assertEquals("ComplexGame", back.name)
        assertEquals("gameplay", back.startScene)
        assertEquals(Cartridge.MBC5, back.config.cartridge)
        assertEquals(GbcTarget.GBC_COMPATIBLE, back.config.gbcTarget)

        // Scenes
        assertEquals(3, back.scenes.size)
        val titleScene = back.scenes[0]
        assertEquals("title", titleScene.id)
        assertEquals(2, titleScene.enterOps.size)
        assertIs<PrintAt>(titleScene.enterOps[0])
        assertEquals("Press Start", (titleScene.enterOps[0] as PrintAt).text)
        val navOp = titleScene.enterOps[1]
        assertIs<NavigateTo>(navOp)
        assertEquals("gameplay", navOp.sceneId)

        // IfOp round-trip
        assertEquals(1, titleScene.frameOps.size)
        val ifOp = titleScene.frameOps[0]
        assertIs<IfOp>(ifOp)
        assertIs<BinaryExpr>(ifOp.condition)
        assertEquals(BinaryOp.GTE, ifOp.condition.op)
        assertEquals(1, ifOp.then.size)
        assertIs<NavigateTo>(ifOp.then[0])
        assertEquals(1, ifOp.otherwise.size)
        assertIs<Assign>(ifOp.otherwise[0])

        // exitOps
        assertEquals(1, titleScene.exitOps.size)
        assertIs<ScreenClear>(titleScene.exitOps[0])

        // actorIds
        assertEquals(listOf("player", "enemy"), titleScene.actorIds)

        // WhileOp
        val gameplayScene = back.scenes[1]
        assertEquals(1, gameplayScene.frameOps.size)
        val whileOp = gameplayScene.frameOps[0]
        assertIs<WhileOp>(whileOp)
        assertIs<VarRef>(whileOp.condition)
        assertEquals("running", whileOp.condition.name)
        assertEquals(1, whileOp.body.size)
        assertIs<RawOp>(whileOp.body[0])

        // MusicPlay
        val victoryScene = back.scenes[2]
        assertEquals(1, victoryScene.enterOps.size)
        val musicPlay = victoryScene.enterOps[0]
        assertIs<MusicPlay>(musicPlay)
        assertEquals("theme", musicPlay.songId)
        assertEquals(30, musicPlay.fadeInFrames)

        // Actors
        assertEquals(3, back.actors.size)
        val player = back.actors[0]
        assertEquals("player", player.id)
        assertEquals(80, player.position.x)
        assertEquals(72, player.position.y)
        val sprite = player.sprite
        assertTrue(sprite != null)
        assertEquals("sprites/player.png", sprite.assetRef.path)
        assertEquals(AssetType.SPRITE, sprite.assetRef.type)
        assertEquals(16, sprite.size.width)
        assertEquals(16, sprite.size.height)
        val hitbox = sprite.hitbox
        assertTrue(hitbox != null)
        assertEquals(0, hitbox.x)
        assertEquals(8, hitbox.y)

        // Variables
        assertEquals(3, back.variables.size)
        assertEquals("score", back.variables[0].name)
        assertEquals(VarType.U16, back.variables[0].type)

        // Arrays
        assertEquals(1, back.arrays.size)
        assertEquals("bricks", back.arrays[0].name)
        assertEquals(VarType.U8, back.arrays[0].elementType)
        assertEquals(30, back.arrays[0].size)
    }

    // =========================================================================
    // Test 3: Collections round-trip
    // =========================================================================

    @Test
    fun `collections (hashTables, pools, ringBuffers, fixedSlots) round-trip through JSON`() {
        val game =
            GameIR(
                name = "CollectionsGame",
                hashTables =
                    listOf(
                        IRCollHashTable(
                            name = "tileCache",
                            keyType = CollElementType.Primitive(VarType.U8),
                            valueType = CollElementType.Primitive(VarType.U16),
                            size = 16,
                        )
                    ),
                pools =
                    listOf(
                        IRCollPool(
                            name = "bulletPool",
                            elementType = CollElementType.Primitive(VarType.U8),
                            capacity = 8,
                        )
                    ),
                ringBuffers =
                    listOf(
                        IRCollRingBuffer(
                            name = "eventQueue",
                            elementType = CollElementType.Primitive(VarType.U8),
                            capacity = 4,
                        )
                    ),
                fixedSlots =
                    listOf(
                        IRCollFixedSlots(
                            name = "itemSlots",
                            elementType = CollElementType.Primitive(VarType.U8),
                            count = 8,
                            namedSlots = mapOf("WEAPON" to 0, "ARMOR" to 1),
                        )
                    ),
            )

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        // Hash tables
        assertEquals(1, back.hashTables.size)
        val ht = back.hashTables[0]
        assertEquals("tileCache", ht.name)
        assertEquals(16, ht.size)
        val htKeyType = ht.keyType
        assertIs<CollElementType.Primitive>(htKeyType)
        assertEquals(VarType.U8, htKeyType.varType)
        val htValueType = ht.valueType
        assertIs<CollElementType.Primitive>(htValueType)
        assertEquals(VarType.U16, htValueType.varType)

        // Pools
        assertEquals(1, back.pools.size)
        val pool = back.pools[0]
        assertEquals("bulletPool", pool.name)
        assertEquals(8, pool.capacity)

        // Ring buffers
        assertEquals(1, back.ringBuffers.size)
        val rb = back.ringBuffers[0]
        assertEquals("eventQueue", rb.name)
        assertEquals(4, rb.capacity)

        // Fixed slots
        assertEquals(1, back.fixedSlots.size)
        val fs = back.fixedSlots[0]
        assertEquals("itemSlots", fs.name)
        assertEquals(8, fs.count)
        assertEquals(mapOf("WEAPON" to 0, "ARMOR" to 1), fs.namedSlots)
    }

    // =========================================================================
    // Test 4: Schema version mismatch — warns but continues
    // =========================================================================

    @Test
    fun `schema version mismatch produces valid GameIR without throwing`() {
        // Construct JSON with future schema version
        val futureJson =
            JSONObject()
                .put("schemaVersion", 999)
                .put("name", "FutureGame")
                .put("startScene", JSONObject.NULL)
                .put(
                    "config",
                    JSONObject()
                        .put("cartridge", "ROM_ONLY")
                        .put("romBanks", 2)
                        .put("ramBanks", 0)
                        .put("gbcTarget", "DMG"),
                )
                .put("sourceLocation", JSONObject.NULL)
                .put("scenes", org.json.JSONArray())
                .put("actors", org.json.JSONArray())
                .put("variables", org.json.JSONArray())
                .put("arrays", org.json.JSONArray())
                .put("assets", org.json.JSONArray())
                .put("palettes", org.json.JSONArray())
                .put("soundEffects", org.json.JSONArray())
                .put("structs", org.json.JSONArray())
                .put("hashTables", org.json.JSONArray())
                .put("pools", org.json.JSONArray())
                .put("ringBuffers", org.json.JSONArray())
                .put("fixedSlots", org.json.JSONArray())
                .put("dialogs", org.json.JSONArray())
                .put("menus", org.json.JSONArray())
                .put("huds", org.json.JSONArray())
                .put("musicDefs", org.json.JSONArray())
                .put("actorPools", org.json.JSONArray())
                .put("systems", org.json.JSONArray())
                .put("zones", org.json.JSONArray())
                .put("flags", org.json.JSONArray())
                .put("itemCategories", org.json.JSONArray())
                .put("items", org.json.JSONArray())
                .put("containers", org.json.JSONArray())
                .put("dropTables", org.json.JSONArray())
                .put("puzzleObjects", org.json.JSONArray())
                .put("collisionGroups", org.json.JSONArray())
                .put("collisionRules", org.json.JSONArray())
                .toString(2)

        // Should not throw — deserializes with warning
        val result = GameIRSerializer.fromJson(futureJson)
        assertEquals("FutureGame", result.name)
    }

    // =========================================================================
    // Test 5: Unknown ScriptOp type produces RawOp placeholder
    // =========================================================================

    @Test
    fun `unknown ScriptOp type in JSON produces RawOp placeholder without throwing`() {
        // Build a scene JSON with an unknown op type
        val unknownOpJson = JSONObject().put("type", "UnknownFutureThing")

        val opsArray = org.json.JSONArray().put(unknownOpJson)
        val sceneJson =
            JSONObject()
                .put("id", "test")
                .put("enterOps", opsArray)
                .put("frameOps", org.json.JSONArray())
                .put("exitOps", org.json.JSONArray())
                .put("actorIds", org.json.JSONArray())
                .put("tilesetRef", JSONObject.NULL)
                .put("collisionData", JSONObject.NULL)
                .put("mapWidth", JSONObject.NULL)
                .put("sourceLocation", JSONObject.NULL)
                .put("bankSlot", JSONObject.NULL)
                .put("vramRange", JSONObject.NULL)
                .put("oamSlot", JSONObject.NULL)

        val op = GameIRSerializer.deserializeOp(unknownOpJson)
        assertIs<RawOp>(op)
        assertTrue(op.code.contains("UnknownFutureThing"))
    }

    // =========================================================================
    // Test 6: JSON structure validation (schemaVersion, name, scenes keys + indentation)
    // =========================================================================

    @Test
    fun `toJson output contains schemaVersion, name, and scenes keys with 2-space indentation`() {
        val game = GameIR(name = "StructureTest", scenes = listOf(SceneIR("s1"), SceneIR("s2")))

        val json = GameIRSerializer.toJson(game)

        // Parse and verify keys exist
        val parsed = JSONObject(json)
        assertTrue(parsed.has("schemaVersion"), "should have schemaVersion")
        assertEquals(GameIRSerializer.SCHEMA_VERSION, parsed.getInt("schemaVersion"))
        assertTrue(parsed.has("name"), "should have name")
        assertEquals("StructureTest", parsed.getString("name"))
        assertTrue(parsed.has("scenes"), "should have scenes")
        assertEquals(2, parsed.getJSONArray("scenes").length())

        // Verify 2-space indentation — lines after first should start with spaces
        val lines = json.lines()
        assertTrue(lines.size > 1, "JSON should have multiple lines")
        // Check that indented content exists (2-space indent means lines starting with "  ")
        val indentedLines = lines.filter { it.startsWith("  ") }
        assertTrue(indentedLines.isNotEmpty(), "should have 2-space indented lines")
    }

    // =========================================================================
    // Test 7: Empty game round-trip
    // =========================================================================

    @Test
    fun `empty game with no scenes actors or variables round-trips through JSON`() {
        val game = GameIR(name = "EmptyGame")

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertEquals("EmptyGame", back.name)
        assertEquals(null, back.startScene)
        assertTrue(back.scenes.isEmpty())
        assertTrue(back.actors.isEmpty())
        assertTrue(back.variables.isEmpty())
        assertTrue(back.arrays.isEmpty())
        assertTrue(back.hashTables.isEmpty())
        assertTrue(back.pools.isEmpty())
        assertTrue(back.ringBuffers.isEmpty())
        assertTrue(back.fixedSlots.isEmpty())
    }

    // =========================================================================
    // Additional: Expr hierarchy round-trip
    // =========================================================================

    @Test
    fun `all core Expr subtypes serialize and deserialize correctly`() {
        val exprs: List<Expr> =
            listOf(
                Literal(42),
                StringLiteral("hello"),
                VarRef("score"),
                BinaryExpr(VarRef("x"), BinaryOp.ADD, Literal(5)),
                UnaryExpr(UnaryOp.NEGATE, VarRef("score")),
                CallExpr("myFunc", listOf(Literal(1), Literal(2))),
                TernaryExpr(VarRef("flag"), Literal(1), Literal(0)),
                ArrayAccessExpr("bricks", VarRef("idx")),
                PropertyAccessExpr("player", "x"),
                CastExpr(VarType.U16, VarRef("score")),
                PoolGetActiveCount("bulletPool"),
            )

        for (original in exprs) {
            val json = GameIRSerializer.serializeExpr(original)
            val back = GameIRSerializer.deserializeExpr(json)
            // Verify type is preserved
            assertEquals(
                original::class,
                back::class,
                "Type mismatch for ${original::class.simpleName}",
            )
        }

        // Verify specific values
        val literal = GameIRSerializer.deserializeExpr(GameIRSerializer.serializeExpr(Literal(42)))
        assertIs<Literal>(literal)
        assertEquals(42, literal.value)

        val varRef =
            GameIRSerializer.deserializeExpr(GameIRSerializer.serializeExpr(VarRef("score")))
        assertIs<VarRef>(varRef)
        assertEquals("score", varRef.name)

        val binary =
            GameIRSerializer.deserializeExpr(
                GameIRSerializer.serializeExpr(BinaryExpr(VarRef("x"), BinaryOp.ADD, Literal(5)))
            )
        assertIs<BinaryExpr>(binary)
        assertEquals(BinaryOp.ADD, binary.op)

        val cast =
            GameIRSerializer.deserializeExpr(
                GameIRSerializer.serializeExpr(CastExpr(VarType.U16, VarRef("score")))
            )
        assertIs<CastExpr>(cast)
        assertEquals(VarType.U16, cast.targetType)
    }

    // =========================================================================
    // Additional: ScriptOp hierarchy round-trip
    // =========================================================================

    @Test
    fun `all core ScriptOp subtypes serialize and deserialize correctly`() {
        val ops: List<ScriptOp> =
            listOf(
                Assign("score", Literal(10)),
                ArrayAssign("bricks", VarRef("i"), Literal(1)),
                IfOp(
                    VarRef("flag"),
                    then = listOf(Assign("x", Literal(0))),
                    otherwise = emptyList(),
                ),
                WhileOp(VarRef("running"), body = listOf(Assign("x", Literal(1)))),
                ForOp("i", Literal(0), Literal(10), body = listOf(Assign("x", VarRef("i")))),
                SetPosition("player", Literal(80), Literal(72)),
                MoveBy("player", Literal(-3), Literal(0)),
                NavigateTo("gameplay"),
                PlaySound("coin"),
                MusicPlay("theme", fadeInFrames = 60, resume = false),
                MusicStop(fadeOutFrames = 30),
                MusicPause(),
                MusicResume(),
                PrintAt(5, 8, "SCORE", FontMode.FIXED_WIDTH),
                PrintCentered("Game Over", 9),
                PrintAligned("END", 10, TextAlignment.CENTER),
                ClearRegion(0, 0, 20, 4),
                ScreenClear(),
                ScreenFill(0x00),
                PrintOp("%d", listOf(VarRef("score"))),
                FadeOp(fadeIn = true, frames = 16),
                SetVisible("enemy", false),
                SpawnActor("bullet"),
                DestroyActor("enemy"),
                PoolSpawnActor("bulletPool", Literal(80), Literal(40)),
                PoolDestroyActor("bulletPool", VarRef("slot")),
                PoolForEachActive(
                    "bulletPool",
                    8,
                    "slot",
                    body = listOf(Assign("x", VarRef("slot"))),
                ),
                PoolDestroyAll("bulletPool", 8),
                AnimateOp("player", "walk"),
                CameraOp(CameraAction.FOLLOW, args = emptyMap()),
                WaitFrames(60),
                CallOp("myFunc", listOf(Literal(1))),
                ReturnOp(Literal(0)),
                MathOp(
                    "result",
                    MathFunction.CLAMP,
                    listOf(VarRef("score"), Literal(0), Literal(100)),
                ),
                RawOp("// raw C code"),
                GotoXYOp(VarRef("x"), VarRef("y")),
                PathfindStep("npc", "player"),
                WaypointStep("guard"),
                ActivatePuzzleObject("door1"),
                DeactivatePuzzleObject("door1"),
                RevealPuzzleObject("chest"),
                HidePuzzleObject("trap"),
                PhysicsStep("player"),
                SetAnimationState("player", "jump"),
            )

        for (original in ops) {
            val json = GameIRSerializer.serializeOp(original)
            val back = GameIRSerializer.deserializeOp(json)
            assertEquals(
                original::class,
                back::class,
                "Type mismatch for ${original::class.simpleName}: expected ${original::class.simpleName}, got ${back::class.simpleName}",
            )
        }
    }

    // =========================================================================
    // Additional: Scene with collision data round-trip
    // =========================================================================

    @Test
    fun `scene with collision data round-trips through JSON`() {
        val collisionData = byteArrayOf(0, 1, 0, 1, 1, 0, 0, 1)
        val game =
            GameIR(
                name = "MapGame",
                scenes =
                    listOf(
                        SceneIR(
                            id = "dungeon",
                            tilesetRef = AssetRef("tilesets/dungeon.png", AssetType.TILESET),
                            collisionData = collisionData,
                            mapWidth = 4,
                            actorIds = listOf("hero"),
                        )
                    ),
            )

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertEquals(1, back.scenes.size)
        val scene = back.scenes[0]
        assertEquals("dungeon", scene.id)
        assertEquals(4, scene.mapWidth)
        val tilesetRef = scene.tilesetRef
        assertTrue(tilesetRef != null)
        assertEquals("tilesets/dungeon.png", tilesetRef.path)
        assertEquals(AssetType.TILESET, tilesetRef.type)
        val sceneCollisionData = scene.collisionData
        assertTrue(sceneCollisionData != null)
        assertEquals(collisionData.size, sceneCollisionData.size)
        for (i in collisionData.indices) {
            assertEquals(
                collisionData[i],
                sceneCollisionData[i],
                "Collision data mismatch at index $i",
            )
        }
    }

    // =========================================================================
    // Additional: Unknown Expr type returns Literal placeholder
    // =========================================================================

    @Test
    fun `unknown Expr type in JSON produces Literal placeholder without throwing`() {
        val unknownExprJson = JSONObject().put("type", "UnknownFutureExpr")
        val expr = GameIRSerializer.deserializeExpr(unknownExprJson)
        assertIs<Literal>(expr)
        assertEquals(0, expr.value)
    }

    // =========================================================================
    // Additional: ForOp with body ops round-trips
    // =========================================================================

    @Test
    fun `ForOp with nested body round-trips through JSON`() {
        val forOp =
            ForOp(
                variable = "i",
                from = Literal(0),
                to = Literal(10),
                body =
                    listOf(Assign("total", BinaryExpr(VarRef("total"), BinaryOp.ADD, VarRef("i")))),
            )

        val json = GameIRSerializer.serializeOp(forOp)
        val back = GameIRSerializer.deserializeOp(json)

        assertIs<ForOp>(back)
        assertEquals("i", back.variable)
        assertIs<Literal>(back.from)
        assertEquals(0, back.from.value)
        assertIs<Literal>(back.to)
        assertEquals(10, back.to.value)
        assertEquals(1, back.body.size)
        val bodyAssign = back.body[0]
        assertIs<Assign>(bodyAssign)
        assertEquals("total", bodyAssign.target)
        assertIs<BinaryExpr>(bodyAssign.value)
        assertEquals(BinaryOp.ADD, bodyAssign.value.op)
    }

    // =========================================================================
    // Additional: TriggerSystem with args round-trips
    // =========================================================================

    @Test
    fun `TriggerSystem with Expr args round-trips through JSON`() {
        val op =
            TriggerSystem(
                systemId = "battle",
                args = mapOf("enemyId" to VarRef("current_enemy"), "type" to Literal(1)),
            )

        val json = GameIRSerializer.serializeOp(op)
        val back = GameIRSerializer.deserializeOp(json)

        assertIs<TriggerSystem>(back)
        assertEquals("battle", back.systemId)
        assertEquals(2, back.args.size)
        val enemyIdArg = back.args["enemyId"]
        assertIs<VarRef>(enemyIdArg)
        assertEquals("current_enemy", enemyIdArg.name)
        val typeArg = back.args["type"]
        assertIs<Literal>(typeArg)
        assertEquals(1, typeArg.value)
    }

    // =========================================================================
    // Additional: AssignOp variants round-trip
    // =========================================================================

    @Test
    fun `Assign with compound AssignOp variants round-trip through JSON`() {
        for (assignOp in AssignOp.entries) {
            val original = Assign(target = "score", value = Literal(10), op = assignOp)
            val json = GameIRSerializer.serializeOp(original)
            val back = GameIRSerializer.deserializeOp(json)
            assertIs<Assign>(back)
            assertEquals(assignOp, back.op, "AssignOp ${assignOp.name} not preserved")
        }
    }

    // =========================================================================
    // CR-01: BindCurrentLevel serializer round-trip
    //
    // Verifies that BindCurrentLevel survives a full GameIRSerializer toJson/fromJson
    // cycle as a typed BindCurrentLevel node — NOT as a RawOp placeholder.
    //
    // RED-capability: remove the `is BindCurrentLevel` branch from serializeOp() or
    // deserializeOp() and this test fails — the round-trip produces a RawOp whose
    // class is assertIs<RawOp> rather than assertIs<BindCurrentLevel>.
    // =========================================================================

    @Test
    fun `BindCurrentLevel round-trips through GameIRSerializer as typed BindCurrentLevel not RawOp`() {
        val game =
            GameIR(
                name = "BindCurrentLevelGame",
                startScene = "gameplay",
                scenes =
                    listOf(
                        SceneIR(
                            id = "gameplay",
                            enterOps = listOf(BindCurrentLevel()),
                        )
                    ),
            )

        val json = GameIRSerializer.toJson(game)

        // Structural assertion: the serialized JSON must contain the "BindCurrentLevel" type
        // discriminator, confirming serializeOp() has a dedicated branch (not the "Unknown" else).
        assertTrue(
            json.contains("\"BindCurrentLevel\""),
            "Serialized JSON must contain type discriminator \"BindCurrentLevel\". " +
                "Got (first 2000 chars):\n${json.take(2000)}",
        )

        val back = GameIRSerializer.fromJson(json)

        // Deserialized scene must have exactly one op and it must be BindCurrentLevel.
        assertEquals(1, back.scenes.size)
        assertEquals(1, back.scenes[0].enterOps.size)
        val op = back.scenes[0].enterOps[0]
        assertIs<BindCurrentLevel>(
            op,
            "Round-tripped op must be BindCurrentLevel, not ${op::class.simpleName}. " +
                "If this is RawOp, the deserializeOp 'BindCurrentLevel' branch is missing.",
        )
    }
}
