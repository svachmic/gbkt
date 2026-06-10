/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * Round-trip coverage for the subsystem serialize/deserialize pairs not exercised by
 * [GameIRSerializerTest]: movement, animation, physics, waypoints, palettes, sound, structs, actor
 * pools, music, dialogs, menus, HUDs, plus the serialize-only zone/collision-rule contracts and the
 * CartridgeConfig/CollElementType fallback branches.
 *
 * Fixtures deliberately use non-default enum and flag values everywhere so a deserializer that
 * silently falls back to defaults fails the assertions.
 */
class GameIRSerializerSubsystemsTest {

    private fun roundTrip(game: GameIR): GameIR =
        GameIRSerializer.fromJson(GameIRSerializer.toJson(game))

    // -------------------------------------------------------------------------
    // Actor subsystems: movement, animation, physics, waypoints, palette
    // -------------------------------------------------------------------------

    @Test
    fun `actor with movement animation physics waypoint and palette round-trips`() {
        val palette =
            GBCPalette(
                name = "npcPal",
                colors = listOf(GBCColor.WHITE, GBCColor.RED, GBCColor.GREEN, GBCColor.BLACK),
                slot = 2,
                type = PaletteType.BACKGROUND,
            )
        val actor =
            ActorIR(
                id = "npc",
                position = PositionDef(10, 20),
                movementConfig =
                    MovementConfig(
                        style = MovementStyle.SMOOTH,
                        speed = 2,
                        tileSize = 16,
                        smoothConfig =
                            SmoothMovementConfig(
                                speed = 3,
                                acceleration = 12,
                                friction = 8,
                                diagonalMode = DiagonalMode.NORMALIZED,
                                fixedPointMode = FixedPointMode.FP88,
                            ),
                    ),
                animationStates =
                    listOf(
                        AnimationStateDef(
                            name = "walk",
                            startFrame = 0,
                            endFrame = 3,
                            speed = 6,
                            loop = false,
                            transitions =
                                listOf(
                                    AnimTransition(
                                        fromState = "walk",
                                        toState = "idle",
                                        condition =
                                            BinaryExpr(VarRef("vx"), BinaryOp.EQ, Literal(0)),
                                    )
                                ),
                        )
                    ),
                frameSpeed = 4,
                physicsConfig =
                    PhysicsConfig(
                        velocityX = 1,
                        gravity = 2,
                        bounce = 1,
                        maxFallSpeed = 6,
                        platformerMode = true,
                        variableJump = true,
                        jumpCutMultiplier = 3,
                        coyoteFrames = 5,
                        wallResponse = WallResponse.SLIDE,
                        wallJump = true,
                        wallJumpVelocityX = -2,
                        wallJumpVelocityY = -4,
                        fixedPointMode = FixedPointMode.FP44,
                    ),
                waypointRoute = WaypointRoute(points = listOf(8 to 16, 24 to 16), loop = false),
                followTargetId = "player",
                palette = palette,
            )
        val game =
            GameIR(name = "ActorSubsystems", actors = listOf(actor), palettes = listOf(palette))

        val back = roundTrip(game)

        assertEquals(actor, back.actors.single())
        assertEquals(palette, back.palettes.single())
    }

    // -------------------------------------------------------------------------
    // Sound: registers, envelope, sweep, waveform, noise
    // -------------------------------------------------------------------------

    @Test
    fun `sound effects with envelope sweep waveform and noise round-trip`() {
        val pulse =
            SoundEffectDef(
                id = "jump",
                channel = SoundChannel.PULSE1,
                registers =
                    SoundRegisters(
                        frequency = 1750,
                        length = 20,
                        trigger = true,
                        lengthEnable = true,
                        duty = DutyCycle.TWENTY_FIVE,
                        envelope =
                            EnvelopeConfig(
                                volume = 12,
                                direction = EnvelopeDirection.DECREASE,
                                pace = 3,
                            ),
                        sweep =
                            SweepConfig(time = 2, direction = SweepDirection.INCREASE, shift = 1),
                    ),
                priority = SfxPriority.HIGH,
            )
        val wave =
            SoundEffectDef(
                id = "wash",
                channel = SoundChannel.WAVE,
                registers =
                    SoundRegisters(
                        frequency = 1024,
                        waveOutputLevel = 1,
                        waveform = ByteArray(16) { it.toByte() },
                    ),
            )
        val noise =
            SoundEffectDef(
                id = "hit",
                channel = SoundChannel.NOISE,
                registers =
                    SoundRegisters(noiseClockShift = 5, noiseDivisor = 3, noiseWidthMode = true),
                priority = SfxPriority.CRITICAL,
            )
        val game = GameIR(name = "SoundGame", soundEffects = listOf(pulse, wave, noise))

        val back = roundTrip(game)

        // SoundRegisters overrides equals with contentEquals on the waveform ByteArray,
        // so whole-object equality is safe here.
        assertEquals(pulse, back.soundEffects[0])
        assertEquals(wave, back.soundEffects[1])
        assertEquals(noise, back.soundEffects[2])
        assertContentEquals(wave.registers.waveform!!, back.soundEffects[1].registers.waveform!!)
    }

    // -------------------------------------------------------------------------
    // Structs and struct-typed collection elements
    // -------------------------------------------------------------------------

    @Test
    fun `structs and struct-typed hash table elements round-trip`() {
        val enemyStruct =
            StructDef(
                name = "Enemy",
                fields =
                    listOf(StructFieldDef("hp", VarType.U8), StructFieldDef("score", VarType.U16)),
            )
        val table =
            IRCollHashTable(
                name = "byId",
                keyType = CollElementType.Primitive(VarType.U8),
                valueType = CollElementType.Struct(enemyStruct),
                size = 8,
            )
        val game =
            GameIR(name = "StructGame", structs = listOf(enemyStruct), hashTables = listOf(table))

        val back = roundTrip(game)

        assertEquals(enemyStruct, back.structs.single())
        val backTable = back.hashTables.single()
        assertEquals("byId", backTable.name)
        assertEquals(CollElementType.Primitive(VarType.U8), backTable.keyType)
        // The deserializer reconstructs struct value types as a named placeholder; only the
        // struct name survives the round-trip by design.
        val structType = assertIs<CollElementType.Struct>(backTable.valueType)
        assertEquals("Enemy", structType.structDef.name)
    }

    @Test
    fun `unknown CollElementType kind falls back to U8 primitive`() {
        val json =
            """
            {
              "schemaVersion": ${GameIRSerializer.SCHEMA_VERSION},
              "name": "Edge",
              "hashTables": [
                {
                  "name": "h",
                  "keyType": {"kind": "Bogus"},
                  "valueType": {"kind": "Primitive", "varType": "U16"},
                  "size": 4
                }
              ]
            }
            """
                .trimIndent()

        val back = GameIRSerializer.fromJson(json)

        val table = back.hashTables.single()
        assertEquals(CollElementType.Primitive(VarType.U8), table.keyType)
        assertEquals(CollElementType.Primitive(VarType.U16), table.valueType)
    }

    // -------------------------------------------------------------------------
    // Actor pools
    // -------------------------------------------------------------------------

    @Test
    fun `actor pool with recycle strategy and death callback round-trips`() {
        val pool =
            ActorPoolIR(
                id = "bullets",
                actorTemplateId = "bulletTemplate",
                config =
                    ActorPoolConfig(
                        maxSize = 16,
                        overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST,
                    ),
                deathCallback = listOf(Assign("kills", Literal(1), AssignOp.ADD)),
            )
        val game = GameIR(name = "PoolGame", actorPools = listOf(pool))

        val back = roundTrip(game)

        // instanceProperties is not serialized; with the fixture empty, full equality holds.
        assertEquals(pool, back.actorPools.single())
    }

    // -------------------------------------------------------------------------
    // Music
    // -------------------------------------------------------------------------

    @Test
    fun `music defs with source location round-trip`() {
        val music =
            MusicDef(
                id = "theme",
                assetRef = AssetRef("music/theme.uge", AssetType.SOUND),
                sourceLocation = SourceLocation("Game.kt", 10, 5),
            )
        val game = GameIR(name = "MusicGame", musicDefs = listOf(music))

        assertEquals(music, roundTrip(game).musicDefs.single())
    }

    // -------------------------------------------------------------------------
    // Dialogs, menus, HUDs
    // -------------------------------------------------------------------------

    @Test
    fun `dialogs menus and huds round-trip with non-default values`() {
        val dialog =
            DialogDef(
                id = "intro",
                textSpeed = 2,
                border = BorderStyle.CUSTOM,
                speaker = "ELDER",
                portrait = AssetRef("portraits/elder.png", AssetType.SPRITE),
                boxX = 1,
                boxY = 10,
                boxWidth = 18,
                boxHeight = 6,
                customBorderTiles = listOf(1, 2, 3, 4, 5, 6, 7, 8),
                fontMode = FontMode.VARIABLE_WIDTH,
            )
        val menu =
            MenuDef(
                id = "pause",
                layout = MenuLayout.GRID,
                cursorChar = "*",
                cursorSprite = AssetRef("ui/cursor.png", AssetType.SPRITE),
                parentId = "root",
                renderOnWindow = false,
                scrollBehavior = ScrollBehavior.PAGE_BASED,
                sfxOnMove = "blip",
                sfxOnSelect = "ok",
                sfxOnCancel = "back",
                x = 2,
                y = 3,
                width = 16,
                height = 12,
                columns = 2,
                items = listOf(MenuItemDef("RESUME", body = listOf(NavigateTo("gameplay")))),
            )
        val hud =
            HudDef(
                id = "main",
                anchor = Anchor.TOP_RIGHT,
                tileX = 12,
                tileY = 0,
                renderOnWindow = false,
                elements =
                    listOf(
                        HudBar(
                            id = "hp",
                            variable = "player_hp",
                            maxVariable = "player_hp_max",
                            maxValue = 40,
                            width = 10,
                            fillTile = 0x05,
                            emptyTile = 0x06,
                            fillFrames = 4,
                            gbcPalette = 3,
                        ),
                        HudNumber(id = "score", variable = "score", label = "SC:", format = "%05d"),
                        HudIcons(
                            id = "keys",
                            variable = "key_count",
                            maxValue = 3,
                            fullTile = 7,
                            emptyTile = 8,
                            displayMode = IconDisplayMode.FILLED_ONLY,
                        ),
                    ),
            )
        val game =
            GameIR(
                name = "UiGame",
                dialogs = listOf(dialog),
                menus = listOf(menu),
                huds = listOf(hud),
            )

        val back = roundTrip(game)

        assertEquals(dialog, back.dialogs.single())
        // dataSource is not serialized; with the fixture leaving it null, full equality holds.
        assertEquals(menu, back.menus.single())
        assertEquals(hud, back.huds.single())
    }

    // -------------------------------------------------------------------------
    // Dialog/menu/HUD/palette ops via the internal op serializer
    // -------------------------------------------------------------------------

    @Test
    fun `dialog menu hud and palette ops round-trip through serializeOp`() {
        val ops =
            listOf(
                DialogSay(
                    dialogId = "intro",
                    segments =
                        listOf(DialogTextSegment("HP: "), DialogExprSegment(VarRef("player_hp"))),
                ),
                DialogChoice(
                    dialogId = "intro",
                    options =
                        listOf(
                            DialogOption("YES", body = listOf(Assign("accepted", Literal(1)))),
                            DialogOption("NO", body = emptyList()),
                        ),
                ),
                MenuShow("pause"),
                MenuHide("pause"),
                HudShow("main"),
                HudHide("main"),
                SetPalette(paletteName = "npcPal", slot = 3, type = PaletteType.BACKGROUND),
            )

        for (op in ops) {
            val back = GameIRSerializer.deserializeOp(GameIRSerializer.serializeOp(op))
            assertEquals(op, back, "round-trip mismatch for ${op::class.simpleName}")
        }
    }

    // -------------------------------------------------------------------------
    // Serialize-only contracts: zones and collision rules
    // -------------------------------------------------------------------------

    @Test
    fun `zones and collision rules serialize to JSON but deserialize empty`() {
        val game =
            GameIR(
                name = "WorldGame",
                zones =
                    listOf(
                        ZoneIR(
                            id = "z1",
                            name = "Zone 1",
                            tilesetPath = "maps/z1.png",
                            screenMode = true,
                        ),
                        ZoneIR(id = "z2", name = "Zone 2"),
                    ),
                collisionRules = listOf(CollisionRuleIR("npcs", "player", CollisionResponse.BLOCK)),
            )

        val root = JSONObject(GameIRSerializer.toJson(game))

        val zones = root.getJSONArray("zones")
        assertEquals(2, zones.length())
        assertEquals("ZoneIR", zones.getJSONObject(0).getString("type"))
        assertEquals("z1", zones.getJSONObject(0).getString("id"))
        assertEquals("maps/z1.png", zones.getJSONObject(0).getString("tilesetPath"))
        assertTrue(zones.getJSONObject(0).getBoolean("screenMode"))
        assertTrue(zones.getJSONObject(1).isNull("tilesetPath"))

        val rules = root.getJSONArray("collisionRules")
        assertEquals("npcs", rules.getJSONObject(0).getString("groupA"))
        assertEquals("player", rules.getJSONObject(0).getString("groupB"))
        assertEquals("BLOCK", rules.getJSONObject(0).getString("response"))

        // Documented contract: these are serialize-only — deserialization returns them empty.
        val back = GameIRSerializer.fromJson(root.toString())
        assertTrue(back.zones.isEmpty())
        assertTrue(back.collisionRules.isEmpty())
    }

    // -------------------------------------------------------------------------
    // CartridgeConfig edge branches
    // -------------------------------------------------------------------------

    @Test
    fun `cartridge config round-trips non-default values`() {
        val game =
            GameIR(
                name = "CartGame",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.MBC3,
                        romBanks = 64,
                        ramBanks = 4,
                        gbcTarget = GbcTarget.GBC_ONLY,
                    ),
            )

        assertEquals(game.config, roundTrip(game).config)
    }

    @Test
    fun `unknown cartridge name and absent romBanks fall back safely`() {
        val json =
            JSONObject()
                .put("schemaVersion", GameIRSerializer.SCHEMA_VERSION)
                .put("name", "Edge")
                .put("config", JSONObject().put("cartridge", "BOGUS_MBC").put("ramBanks", 2))

        val back = GameIRSerializer.fromJson(json.toString())

        assertEquals(Cartridge.ROM_ONLY, back.config.cartridge)
        assertNull(back.config.romBanks)
        assertEquals(2, back.config.ramBanks)
        assertEquals(GbcTarget.DMG, back.config.gbcTarget)
    }

    @Test
    fun `null romBanks is omitted from the serialized config`() {
        val game = GameIR(name = "NoBanks", config = CartridgeConfig(romBanks = null))

        val configJson = JSONObject(GameIRSerializer.toJson(game)).getJSONObject("config")

        assertTrue(!configJson.has("romBanks"))
    }
}
