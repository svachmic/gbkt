/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetaspriteVisitorTileDataTest {

    // =========================================================================
    // TEST 1: single-frame metasprite with 2 tiles → totalTiles = max(tileId)+1 = 2
    // =========================================================================
    @Test
    fun `generateMetaspriteTileData single frame two tiles emits set_sprite_data with totalTiles 2`() {
        val metasprite =
            MetaspriteIR(
                id = "elephant",
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
            )

        val result =
            MetaspriteVisitor.generateMetaspriteTileData(
                metasprite = metasprite,
                tileDataArrayName = "sprite_tiles",
                startTile = 0,
            )

        assertEquals(1, result.size)
        val stmt = result[0] as CExprStatement
        assertEquals(
            CExprStatement(
                CCall("set_sprite_data", listOf(CLiteral(0), CLiteral(2), CVar("sprite_tiles")))
            ),
            stmt,
        )
    }

    // =========================================================================
    // TEST 2: multi-frame metasprite with max tileId=4 → totalTiles = 5
    // =========================================================================
    @Test
    fun `generateMetaspriteTileData multi-frame max tileId 4 emits totalTiles 5`() {
        val metasprite =
            MetaspriteIR(
                id = "elephant",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles =
                                listOf(
                                    MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                    MetaspriteTile(relX = 8, relY = 0, tileId = 2),
                                )
                        ),
                        MetaspriteFrame(
                            tiles =
                                listOf(
                                    MetaspriteTile(relX = 0, relY = 0, tileId = 3),
                                    MetaspriteTile(relX = 8, relY = 0, tileId = 4),
                                )
                        ),
                    ),
            )

        val result =
            MetaspriteVisitor.generateMetaspriteTileData(
                metasprite = metasprite,
                tileDataArrayName = "sprite_tiles",
                startTile = 0,
            )

        assertEquals(1, result.size)
        val stmt = result[0] as CExprStatement
        assertEquals(
            CExprStatement(
                CCall("set_sprite_data", listOf(CLiteral(0), CLiteral(5), CVar("sprite_tiles")))
            ),
            stmt,
        )
    }

    // =========================================================================
    // TEST 3: startTile non-zero is passed as first argument
    // =========================================================================
    @Test
    fun `generateMetaspriteTileData startTile is passed as first CLiteral argument`() {
        val metasprite =
            MetaspriteIR(
                id = "player",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles =
                                listOf(
                                    MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                    MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                                    MetaspriteTile(relX = 16, relY = 0, tileId = 2),
                                )
                        )
                    ),
            )

        val result =
            MetaspriteVisitor.generateMetaspriteTileData(
                metasprite = metasprite,
                tileDataArrayName = "player_tiles",
                startTile = 8,
            )

        assertEquals(1, result.size)
        val stmt = result[0] as CExprStatement
        val call = stmt.expr as CCall
        assertEquals("set_sprite_data", call.function)
        // First arg: startTile = 8 as CLiteral (unsigned context)
        assertEquals(CLiteral(8), call.args[0])
        // Second arg: totalTiles = max(2)+1 = 3 as CLiteral (unsigned context)
        assertEquals(CLiteral(3), call.args[1])
        // Third arg: array name as CVar
        assertEquals(CVar("player_tiles"), call.args[2])
    }

    // =========================================================================
    // TEST 4: custom array name is passed as CVar
    // =========================================================================
    @Test
    fun `generateMetaspriteTileData passes custom array name as CVar`() {
        val metasprite =
            MetaspriteIR(
                id = "enemy",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0))
                        )
                    ),
            )

        val result =
            MetaspriteVisitor.generateMetaspriteTileData(
                metasprite = metasprite,
                tileDataArrayName = "_enemy_tiles",
                startTile = 0,
            )

        assertEquals(1, result.size)
        val stmt = result[0] as CExprStatement
        val call = stmt.expr as CCall
        assertEquals(CVar("_enemy_tiles"), call.args[2])
    }

    // =========================================================================
    // TEST 5: literals are CLiteral (unsigned), NOT CIntLiteral (signed)
    // Per PATTERNS §Pattern D and emit CLAUDE.md Literal Emission Convention
    // =========================================================================
    @Test
    fun `generateMetaspriteTileData uses CLiteral not CIntLiteral for unsigned context`() {
        val metasprite =
            MetaspriteIR(
                id = "test",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 5))
                        )
                    ),
            )

        val result =
            MetaspriteVisitor.generateMetaspriteTileData(
                metasprite = metasprite,
                tileDataArrayName = "sprite_tiles",
                startTile = 4,
            )

        val stmt = result[0] as CExprStatement
        val call = stmt.expr as CCall
        // Both startTile and totalTiles must be CLiteral (unsigned context)
        assertTrue(
            call.args[0] is CLiteral,
            "startTile must be CLiteral, got ${call.args[0]::class.simpleName}",
        )
        assertTrue(
            call.args[1] is CLiteral,
            "totalTiles must be CLiteral, got ${call.args[1]::class.simpleName}",
        )
    }
}
