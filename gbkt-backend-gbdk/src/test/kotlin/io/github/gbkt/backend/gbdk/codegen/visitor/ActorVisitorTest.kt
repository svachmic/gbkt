/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.WaypointRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActorVisitorTest {

    // =========================================================================
    // TEST 1: actor produces position variables
    // =========================================================================
    @Test
    fun `actor produces x and y position variables`() {
        val actor = ActorIR(id = "paddle1", position = PositionDef(16, 64))
        val decls = ActorVisitor.visit(actor)

        assertEquals(2, decls.size)
        assertEquals(CVarDecl("_paddle1_x", CU8, CLiteral(16)), decls[0])
        assertEquals(CVarDecl("_paddle1_y", CU8, CLiteral(64)), decls[1])
    }

    // =========================================================================
    // TEST 2: actor with sprite produces declarations
    // =========================================================================
    @Test
    fun `actor with sprite still produces position variable declarations`() {
        val actor = ActorIR(id = "ball", position = PositionDef(80, 72))
        val decls = ActorVisitor.visit(actor)

        // Position variables always produced regardless of sprite
        assertTrue(decls.any { it.name == "_ball_x" }, "Expected _ball_x declaration")
        assertTrue(decls.any { it.name == "_ball_y" }, "Expected _ball_y declaration")
    }

    // =========================================================================
    // TEST 3: multiple actors produce separate variable sets
    // =========================================================================
    @Test
    fun `multiple actors produce separate position variable sets`() {
        val actor1 = ActorIR(id = "paddle1", position = PositionDef(16, 64))
        val actor2 = ActorIR(id = "paddle2", position = PositionDef(144, 64))

        val decls1 = ActorVisitor.visit(actor1)
        val decls2 = ActorVisitor.visit(actor2)

        assertEquals(2, decls1.size)
        assertEquals(2, decls2.size)

        assertEquals("_paddle1_x", decls1[0].name)
        assertEquals("_paddle1_y", decls1[1].name)
        assertEquals("_paddle2_x", decls2[0].name)
        assertEquals("_paddle2_y", decls2[1].name)
    }

    // =========================================================================
    // TEST 4: actor ID sanitization
    // =========================================================================
    @Test
    fun `actor ID with dot is sanitized to valid C identifier`() {
        val actor = ActorIR(id = "player.entity", position = PositionDef(0, 0))
        val decls = ActorVisitor.visit(actor)

        // Dots in actor ID should be replaced with underscores
        assertTrue(decls.none { "." in it.name }, "Actor ID dots should be sanitized")
        assertTrue(decls.any { it.name.startsWith("_") }, "Expected underscore prefix")
    }

    // =========================================================================
    // BONUS: initial value matches position
    // =========================================================================
    @Test
    fun `actor position initializer values match PositionDef`() {
        val actor = ActorIR(id = "ball", position = PositionDef(80, 72))
        val decls = ActorVisitor.visit(actor)

        val xDecl = decls.find { it.name == "_ball_x" }
        val yDecl = decls.find { it.name == "_ball_y" }

        assertEquals(CLiteral(80), xDecl?.initializer)
        assertEquals(CLiteral(72), yDecl?.initializer)
    }

    // =========================================================================
    // Sprite frame layout — generateFrameOffsetInit (E4)
    // =========================================================================

    @Test
    fun `generateFrameOffsetInit returns empty list for actor without sprite`() {
        val actor = ActorIR(id = "ball", position = PositionDef(80, 72))
        val result = ActorVisitor.generateFrameOffsetInit(actor, tileStart = 0, oamSlotStart = 0)
        assertTrue(result.isEmpty(), "No sprite = no frame offset function")
    }

    @Test
    fun `generateFrameOffsetInit returns empty list when frameWidth not set`() {
        val actor =
            ActorIR(
                id = "ball",
                position = PositionDef(80, 72),
                sprite = SpriteDef(assetRef = AssetRef("sprites/ball.png"), size = SizeDef(8, 8)),
            )
        val result = ActorVisitor.generateFrameOffsetInit(actor, tileStart = 0, oamSlotStart = 0)
        assertTrue(result.isEmpty(), "No frameWidth = no frame offset function")
    }

    @Test
    fun `generateFrameOffsetInit with frameWidth generates set_ball_frame function`() {
        val actor =
            ActorIR(
                id = "ball",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/ball.png"),
                        size = SizeDef(8, 8),
                        frameWidth = 8,
                        frameHeight = 8,
                    ),
            )
        val result = ActorVisitor.generateFrameOffsetInit(actor, tileStart = 0, oamSlotStart = 0)

        assertEquals(1, result.size, "Should generate exactly 1 frame function")
        val fn = result[0]
        assertEquals("set_ball_frame", fn.name, "Function should be named set_ball_frame")
        assertEquals(1, fn.params.size, "Function should have 1 parameter (frame)")
        assertEquals("frame", fn.params[0].name)
    }

    @Test
    fun `generateFrameOffsetInit function body contains set_sprite_tile with frame offset`() {
        val actor =
            ActorIR(
                id = "player",
                position = PositionDef(80, 72),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/player.png"),
                        size = SizeDef(16, 16), // 2x2 tiles = 4 OAM slots
                        frameWidth = 16,
                        frameHeight = 16,
                    ),
            )
        val result = ActorVisitor.generateFrameOffsetInit(actor, tileStart = 4, oamSlotStart = 0)

        assertEquals(1, result.size)
        val fn = result[0]
        // Emit the function to string for assertion
        val emitted =
            CEmitter.emitStatement(io.github.gbkt.backend.gbdk.codegen.ast.CBlock(fn.body))
        assertTrue(
            emitted.contains("set_sprite_tile"),
            "Frame function should call set_sprite_tile, got: $emitted",
        )
        assertTrue(
            emitted.contains("frame"),
            "Frame function body should reference frame parameter, got: $emitted",
        )
    }

    @Test
    fun `generateFrameOffsetInit 16x8 sprite has tilesPerFrame of 2`() {
        // 16px wide / 8 = 2 tiles wide, 8px high / 8 = 1 tile high -> 2 tiles per frame
        val actor =
            ActorIR(
                id = "hero",
                position = PositionDef(0, 0),
                sprite =
                    SpriteDef(
                        assetRef = AssetRef("sprites/hero.png"),
                        size = SizeDef(16, 8), // overall sprite is 16x8
                        frameWidth = 16,
                        frameHeight = 8,
                    ),
            )
        val result = ActorVisitor.generateFrameOffsetInit(actor, tileStart = 0, oamSlotStart = 0)

        assertEquals(1, result.size)
        val fn = result[0]
        // 2 tiles wide, 1 tile high = 2 set_sprite_tile calls
        assertEquals(
            2,
            fn.body.size,
            "16x8 sprite has 2 OAM slots, should emit 2 set_sprite_tile calls",
        )
    }

    // =========================================================================
    // Waypoint array generation — generateWaypointVars / generateWaypointDefines
    // =========================================================================

    @Test
    fun `generateWaypointVars emits const arrays and index for actor with route`() {
        val actor =
            ActorIR(
                id = "guard",
                position = PositionDef(40, 40),
                waypointRoute =
                    WaypointRoute(
                        points = listOf(Pair(40, 40), Pair(120, 40), Pair(120, 80), Pair(40, 80)),
                        loop = true,
                    ),
            )

        val vars = ActorVisitor.generateWaypointVars(actor)

        assertEquals(3, vars.size, "Should produce wp_x, wp_y, and wp_idx variables")

        // wp_x: const UINT8 array
        val wpX = vars[0]
        assertEquals("_guard_wp_x", wpX.name)
        assertIs<CArray>(wpX.type, "wp_x should be a CArray type")
        assertTrue(wpX.isConst, "wp_x should be const")

        // wp_y: const UINT8 array
        val wpY = vars[1]
        assertEquals("_guard_wp_y", wpY.name)
        assertIs<CArray>(wpY.type, "wp_y should be a CArray type")
        assertTrue(wpY.isConst, "wp_y should be const")

        // wp_idx: UINT8 initialized to 0
        val wpIdx = vars[2]
        assertEquals("_guard_wp_idx", wpIdx.name)
        assertEquals(CU8, wpIdx.type, "wp_idx should be UINT8")
        assertEquals(CLiteral(0), wpIdx.initializer, "wp_idx should initialize to 0")

        // Verify emitted C contains array values
        val emitted = CEmitter.emitStatement(io.github.gbkt.backend.gbdk.codegen.ast.CBlock(vars))
        assertTrue(emitted.contains("40, 120, 120, 40"), "wp_x array should contain X coordinates")
        assertTrue(emitted.contains("40, 40, 80, 80"), "wp_y array should contain Y coordinates")
    }

    @Test
    fun `generateWaypointDefines emits wp_count define for actor with route`() {
        val actor =
            ActorIR(
                id = "guard",
                position = PositionDef(40, 40),
                waypointRoute =
                    WaypointRoute(
                        points = listOf(Pair(64, 64), Pair(128, 64), Pair(128, 128)),
                        loop = true,
                    ),
            )

        val defines = ActorVisitor.generateWaypointDefines(actor)

        assertEquals(1, defines.size, "Should produce exactly 1 define for wp_count")
        assertEquals("_guard_wp_count", defines[0].name)
        assertEquals("3", defines[0].value, "wp_count should match number of waypoints")
    }

    @Test
    fun `generateWaypointVars returns empty for actor without route`() {
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))

        val vars = ActorVisitor.generateWaypointVars(actor)
        assertTrue(vars.isEmpty(), "Actor without waypoint route should produce no waypoint vars")

        val defines = ActorVisitor.generateWaypointDefines(actor)
        assertTrue(
            defines.isEmpty(),
            "Actor without waypoint route should produce no waypoint defines",
        )
    }
}
