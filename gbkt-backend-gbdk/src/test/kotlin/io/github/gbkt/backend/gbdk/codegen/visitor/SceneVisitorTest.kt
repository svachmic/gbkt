/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneVisitorTest {

    // =========================================================================
    // TEST 1: scene with enterOps produces enter function
    // =========================================================================
    @Test
    fun `scene produces enter function`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(PrintOp(text = "PONG", position = PositionDef(6, 4))),
            )
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn, "Expected title_enter function")
        assertEquals(CVoid, enterFn.returnType)
        assertTrue(enterFn.isBanked)
    }

    // =========================================================================
    // TEST 2: scene produces frame function
    // =========================================================================
    @Test
    fun `scene produces frame function`() {
        val scene =
            SceneIR(id = "game", frameOps = listOf(Assign("score", Literal(1), AssignOp.ADD)))
        val functions = SceneVisitor.visit(scene)

        val frameFn = functions.find { it.name == "game_frame" }
        assertNotNull(frameFn, "Expected game_frame function")
    }

    // =========================================================================
    // TEST 3: scene produces exit function
    // =========================================================================
    @Test
    fun `scene produces exit function`() {
        val scene = SceneIR(id = "game", exitOps = listOf(FadeOp(fadeIn = false, frames = 0)))
        val functions = SceneVisitor.visit(scene)

        val exitFn = functions.find { it.name == "game_exit" }
        assertNotNull(exitFn, "Expected game_exit function")
    }

    // =========================================================================
    // TEST 4: empty lifecycle handler skips function
    // =========================================================================
    @Test
    fun `empty lifecycle handlers are not generated as functions`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(PrintOp(text = "PONG")),
                frameOps = emptyList(),
                exitOps = emptyList(),
            )
        val functions = SceneVisitor.visit(scene)
        val names = functions.map { it.name }

        assertTrue("title_enter" in names, "Expected title_enter to exist")
        assertFalse("title_frame" in names, "Expected title_frame to be absent (empty frameOps)")
        assertFalse("title_exit" in names, "Expected title_exit to be absent (empty exitOps)")
    }

    // =========================================================================
    // TEST 5: function naming convention preserved
    // =========================================================================
    @Test
    fun `function naming convention preserves scene ID`() {
        val scene =
            SceneIR(
                id = "gameover",
                enterOps = listOf(Assign("score", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("timer", Literal(1), AssignOp.ADD)),
            )
        val functions = SceneVisitor.visit(scene)
        val names = functions.map { it.name }

        assertTrue("gameover_enter" in names)
        assertTrue("gameover_frame" in names)
    }

    // =========================================================================
    // TEST 6: functions are marked isBanked=true
    // =========================================================================
    @Test
    fun `all generated scene functions are marked isBanked true`() {
        val scene =
            SceneIR(
                id = "gameplay",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
                exitOps = listOf(Assign("z", Literal(0), AssignOp.SET)),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        assertTrue(functions.all { it.isBanked }, "All scene functions should be isBanked=true")
    }

    // =========================================================================
    // TEST 7: sectionComment includes scene name
    // =========================================================================
    @Test
    fun `enter function sectionComment includes scene name`() {
        val scene = SceneIR(id = "title", enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)))
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn)
        assertEquals("Scene: title", enterFn.sectionComment)
    }

    @Test
    fun `non-enter functions do not repeat sectionComment`() {
        val scene =
            SceneIR(
                id = "game",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
            )
        val functions = SceneVisitor.visit(scene)

        val frameFn = functions.find { it.name == "game_frame" }
        assertNotNull(frameFn)
        // Frame function should not repeat the section comment (only enter gets it)
        assertEquals(null, frameFn.sectionComment)
    }

    // =========================================================================
    // TEST 8: enter function body contains ScriptOp visitor output
    // =========================================================================
    @Test
    fun `enter function body contains CStatements from ScriptOpVisitor`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps =
                    listOf(
                        Assign("score", Literal(0), AssignOp.SET),
                        Assign("lives", Literal(3), AssignOp.SET),
                    ),
            )
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn)
        assertEquals(2, enterFn.body.size)
    }

    // =========================================================================
    // TEST 9: generateSceneEnum produces define constants
    // =========================================================================
    @Test
    fun `generateSceneEnum produces CDefine constants with sequential indices`() {
        val sceneIds = listOf("title", "game", "gameover")
        val defines = SceneVisitor.generateSceneEnum(sceneIds)

        assertEquals(3, defines.size)
        assertEquals(CDefine("SCENE_TITLE", "0"), defines[0])
        assertEquals(CDefine("SCENE_GAME", "1"), defines[1])
        assertEquals(CDefine("SCENE_GAMEOVER", "2"), defines[2])
    }

    @Test
    fun `generateSceneEnum with single scene produces one define`() {
        val defines = SceneVisitor.generateSceneEnum(listOf("menu"))
        assertEquals(1, defines.size)
        assertEquals(CDefine("SCENE_MENU", "0"), defines[0])
    }

    // =========================================================================
    // TEST 10: scene with bankSlot sets bank on CFunction
    // =========================================================================
    @Test
    fun `scene with bankSlot sets bank on CFunction`() {
        val scene =
            SceneIR(
                id = "gameplay",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
                bankSlot = BankSlot(bank = 2),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertEquals(2, fn.bank, "Expected bank=2 on function ${fn.name}")
            assertTrue(fn.isBanked, "Expected isBanked=true on function ${fn.name}")
        }
    }

    // =========================================================================
    // TEST 11: scene without bankSlot keeps isBanked true with null bank
    // =========================================================================
    @Test
    fun `scene without bankSlot keeps isBanked true with null bank`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                // bankSlot = null (default)
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertNull(fn.bank, "Expected bank=null when no bankSlot on function ${fn.name}")
            assertTrue(fn.isBanked, "Expected isBanked=true for backward compat on ${fn.name}")
        }
    }

    // =========================================================================
    // TEST 12: scene with bankSlot=0 keeps backward compat (isBanked=false for HOME bank)
    // =========================================================================
    @Test
    fun `scene with bankSlot bank=0 sets isBanked=false`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                bankSlot = BankSlot(bank = 0),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertEquals(0, fn.bank, "Expected bank=0 on function ${fn.name}")
            assertFalse(fn.isBanked, "Expected isBanked=false for HOME bank function ${fn.name}")
        }
    }

    // =========================================================================
    // TEST 13 (Phase 11.2-05 REQ-3 / D-A3 / D-claude-4):
    // Scene with zoneRef whose ZoneIR.tilesetPath != null emits set_bkg_data BEFORE
    // _bkg_tiles_load_banked (pixel data reaches VRAM before the tile-index map
    // references it). Symbol shape: set_bkg_data(0, _zone_<id>_tileset_count,
    // _zone_<id>_tileset) — the count + byte array are symbolic CVar references
    // resolved to the synthesized header by Plan 06.
    // =========================================================================
    @Test
    fun `scene with zoneRef whose tilesetPath is non-null emits set_bkg_data before _bkg_tiles_load_banked`() {
        val zone =
            ZoneIR(
                id = "play_zone",
                name = "Play Zone",
                tilesetPath = "tiles/checker.png",
                mapWidth = 20,
                mapHeight = 18,
            )
        val scene = SceneIR(id = "play", zoneRefs = listOf("play_zone"))
        val functions =
            SceneVisitor.visit(
                scene,
                zoneBankAllocation = mapOf("play_zone" to 2),
                zones = listOf(zone),
            )

        val enterFn = functions.find { it.name == "play_enter" }
        assertNotNull(enterFn, "Expected play_enter function")

        // Locate set_bkg_data CExprStatement.
        val setBkgDataIdx =
            enterFn.body.indexOfFirst {
                it is CExprStatement && (it.expr as? CCall)?.function == "set_bkg_data"
            }
        assertTrue(
            setBkgDataIdx >= 0,
            "Expected play_enter body to contain set_bkg_data CCall; body=${enterFn.body}",
        )

        // Locate _bkg_tiles_load_banked CExprStatement.
        val tilesLoadIdx =
            enterFn.body.indexOfFirst {
                it is CExprStatement && (it.expr as? CCall)?.function == "_bkg_tiles_load_banked"
            }
        assertTrue(
            tilesLoadIdx >= 0,
            "Expected play_enter body to contain _bkg_tiles_load_banked CCall; body=${enterFn.body}",
        )

        // Ordering invariant — set_bkg_data must precede _bkg_tiles_load_banked (D-claude-4).
        assertTrue(
            setBkgDataIdx < tilesLoadIdx,
            "set_bkg_data (idx=$setBkgDataIdx) must precede _bkg_tiles_load_banked " +
                "(idx=$tilesLoadIdx); body=${enterFn.body}",
        )

        // Verify the symbolic argument shape of set_bkg_data.
        val setBkgDataCall = (enterFn.body[setBkgDataIdx] as CExprStatement).expr as CCall
        assertEquals(3, setBkgDataCall.args.size, "set_bkg_data expects 3 args")
        assertEquals(CLiteral(0), setBkgDataCall.args[0], "first arg is literal 0 (first_tile)")
        assertEquals(
            CVar("_zone_play_zone_tileset_count"),
            setBkgDataCall.args[1],
            "second arg is symbolic _zone_<id>_tileset_count (D-A3)",
        )
        assertEquals(
            CVar("_zone_play_zone_tileset"),
            setBkgDataCall.args[2],
            "third arg is symbolic _zone_<id>_tileset byte-array",
        )
    }

    // =========================================================================
    // TEST 14 (Phase 11.2-05 Pitfall 6 mitigation):
    // Scene with zoneRef whose ZoneIR.tilesetPath == null (procedural / sport-racing
    // zones authored bytes on the LEGACY path) MUST NOT emit set_bkg_data — the
    // _zone_<id>_tileset symbol does not exist for procedural zones; emitting the
    // call would produce a linker error.
    // =========================================================================
    @Test
    fun `scene with zoneRef whose tilesetPath is null does NOT emit set_bkg_data`() {
        val zone =
            ZoneIR(
                id = "procedural_zone",
                name = "Procedural Zone",
                tilesetPath = null,
                mapWidth = 32,
                mapHeight = 32,
            )
        val scene = SceneIR(id = "play", zoneRefs = listOf("procedural_zone"))
        val functions =
            SceneVisitor.visit(
                scene,
                zoneBankAllocation = mapOf("procedural_zone" to 2),
                zones = listOf(zone),
            )

        val enterFn = functions.find { it.name == "play_enter" }
        assertNotNull(enterFn, "Expected play_enter function")

        // No set_bkg_data CCall must appear — Pitfall 6 mitigation.
        val hasSetBkgData =
            enterFn.body.any {
                it is CExprStatement && (it.expr as? CCall)?.function == "set_bkg_data"
            }
        assertFalse(
            hasSetBkgData,
            "play_enter for a tilesetPath=null zone must NOT emit set_bkg_data; " +
                "body=${enterFn.body}",
        )

        // _bkg_tiles_load_banked is still present (back-compat with 11.1-05).
        val hasTilesLoad =
            enterFn.body.any {
                it is CExprStatement && (it.expr as? CCall)?.function == "_bkg_tiles_load_banked"
            }
        assertTrue(
            hasTilesLoad,
            "play_enter must still emit _bkg_tiles_load_banked even for tilesetPath=null zones",
        )
    }

    // =========================================================================
    // TEST 15 (D-01 Path A debug fix — title-zone-path-a-render):
    // NEW-path zones (tilesetPath != null) MUST emit _zone_<id>_tilemap_WIDTH and
    // _zone_<id>_tilemap_HEIGHT as CVar macro references for the w/h args of
    // _bkg_tiles_load_banked — NOT CLiteral(zone.mapWidth) / CLiteral(zone.mapHeight).
    //
    // Root cause: ZoneIR.mapWidth / mapHeight default to 32. Title-screen.png is
    // 20x9 tiles (180 bytes). Emitting CLiteral(32) for WIDTH and CLiteral(32) for
    // HEIGHT told GBDK to read 32x32=1024 tile indices from a 180-byte buffer,
    // producing the row-doubling visual defect (AC13 FAIL, Phase 12.2).
    //
    // ConvertZoneTilesetsTask (Phase 12.2-06) emits `#define _zone_<id>_tilemap_WIDTH N`
    // and `#define _zone_<id>_tilemap_HEIGHT N` from the actual PNG IHDR dimensions.
    // These macros are the single source of truth — the visitor must reference them,
    // not the stale ZoneIR defaults.
    //
    // LEGACY-path zones (tilesetPath == null) have no emitted macros; they MUST keep
    // the literal fallback (see TEST 16).
    // =========================================================================
    @Test
    fun `NEW-path zone bkg_tiles_load_banked uses macro CVar for WIDTH and HEIGHT, not CLiteral`() {
        val zone =
            ZoneIR(
                id = "titleZone",
                name = "Title Zone",
                tilesetPath = "res/graphics/title-screen.png",
                // mapWidth/mapHeight are the ZoneIR defaults (32, 32) — the visitor must NOT
                // emit these as literals; it must emit the _tilemap_WIDTH / _tilemap_HEIGHT macros.
                mapWidth = 32,
                mapHeight = 32,
            )
        val scene = SceneIR(id = "title", zoneRefs = listOf("titleZone"))
        val functions =
            SceneVisitor.visit(
                scene,
                zoneBankAllocation = mapOf("titleZone" to 2),
                zones = listOf(zone),
            )

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn, "Expected title_enter function")

        // Locate _bkg_tiles_load_banked CExprStatement.
        val tilesLoadStmt =
            enterFn.body.firstOrNull {
                it is CExprStatement && (it.expr as? CCall)?.function == "_bkg_tiles_load_banked"
            } as? CExprStatement
        assertNotNull(
            tilesLoadStmt,
            "title_enter must contain _bkg_tiles_load_banked; body=${enterFn.body}",
        )
        val call = tilesLoadStmt.expr as CCall
        // args: bank(0), x(1), y(2), w(3), h(4), tiles(5)
        assertEquals(6, call.args.size, "_bkg_tiles_load_banked must have 6 args; got=${call.args}")

        // WIDTH arg (index 3) — must be CVar macro, not CLiteral.
        assertEquals(
            CVar("_zone_titleZone_tilemap_WIDTH"),
            call.args[3],
            "WIDTH arg (idx=3) must be CVar(_zone_titleZone_tilemap_WIDTH) for a NEW-path zone " +
                "(tilesetPath != null). Got ${call.args[3]}. " +
                "Emitting CLiteral(zone.mapWidth) uses the ZoneIR default (32) rather than the " +
                "actual tilemap dimension from ConvertZoneTilesetsTask — root cause of the " +
                "title row-doubling defect (D-01 Path A, Phase 12.2 AC13 FAIL).",
        )

        // HEIGHT arg (index 4) — must be CVar macro, not CLiteral.
        assertEquals(
            CVar("_zone_titleZone_tilemap_HEIGHT"),
            call.args[4],
            "HEIGHT arg (idx=4) must be CVar(_zone_titleZone_tilemap_HEIGHT) for a NEW-path zone " +
                "(tilesetPath != null). Got ${call.args[4]}. " +
                "Emitting CLiteral(zone.mapHeight) uses the ZoneIR default (32) rather than the " +
                "actual tilemap dimension from ConvertZoneTilesetsTask — root cause of the " +
                "title row-doubling defect (D-01 Path A, Phase 12.2 AC13 FAIL).",
        )
    }

    // =========================================================================
    // TEST 16 (D-01 Path A debug fix — LEGACY-path regression guard):
    // LEGACY-path zones (tilesetPath == null) MUST keep CLiteral(zone.mapWidth) and
    // CLiteral(zone.mapHeight) — there are no emitted macros for procedural zones.
    // This test ensures the fix for NEW-path zones (TEST 15) does NOT regress the
    // sport-racing LEGACY path whose _zone_track1_tiles[361] emission depends on
    // literal zone dimensions.
    // =========================================================================
    @Test
    fun `LEGACY-path zone bkg_tiles_load_banked keeps CLiteral for WIDTH and HEIGHT`() {
        val zone =
            ZoneIR(
                id = "track1",
                name = "Track 1",
                tilesetPath = null, // LEGACY path — no png2asset macros emitted
                mapWidth = 19,
                mapHeight = 19,
            )
        val scene = SceneIR(id = "race", zoneRefs = listOf("track1"))
        val functions =
            SceneVisitor.visit(
                scene,
                zoneBankAllocation = mapOf("track1" to 1),
                zones = listOf(zone),
            )

        val enterFn = functions.find { it.name == "race_enter" }
        assertNotNull(enterFn, "Expected race_enter function")

        val tilesLoadStmt =
            enterFn.body.firstOrNull {
                it is CExprStatement && (it.expr as? CCall)?.function == "_bkg_tiles_load_banked"
            } as? CExprStatement
        assertNotNull(
            tilesLoadStmt,
            "race_enter must contain _bkg_tiles_load_banked; body=${enterFn.body}",
        )
        val call = tilesLoadStmt.expr as CCall
        assertEquals(6, call.args.size, "_bkg_tiles_load_banked must have 6 args; got=${call.args}")

        // WIDTH arg (index 3) — must stay CLiteral for LEGACY-path zones.
        assertEquals(
            CLiteral(19),
            call.args[3],
            "WIDTH arg (idx=3) must be CLiteral(zone.mapWidth)=CLiteral(19) for a LEGACY-path " +
                "zone (tilesetPath == null). Got ${call.args[3]}. " +
                "Sport-racing procedural zones have no _tilemap_WIDTH macro; emitting CVar " +
                "would produce an unresolved symbol linker error.",
        )

        // HEIGHT arg (index 4) — must stay CLiteral for LEGACY-path zones.
        assertEquals(
            CLiteral(19),
            call.args[4],
            "HEIGHT arg (idx=4) must be CLiteral(zone.mapHeight)=CLiteral(19) for a LEGACY-path " +
                "zone (tilesetPath == null). Got ${call.args[4]}.",
        )
    }
}
