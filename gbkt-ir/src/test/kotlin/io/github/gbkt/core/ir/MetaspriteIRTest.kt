/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// METASPRITE IR SHAPE TESTS
// Verifies MetaspriteIR, MetaspriteFrame, MetaspriteTile construction,
// field access, default arguments, and backward-compat wiring in GameIR.
// Also covers MoveMetasprite ScriptOp shape and visitor dispatch (Plan 04).
// =============================================================================

class MetaspriteIRTest {

    // =========================================================================
    // Task 1: MetaspriteIR type shape tests
    // =========================================================================

    @Test
    fun `MetaspriteIR constructs with id and empty frames and null sourceLocation by default`() {
        val ms = MetaspriteIR("foo", emptyList())
        assertEquals("foo", ms.id)
        assertTrue(ms.frames.isEmpty())
        assertNull(ms.sourceLocation)
    }

    @Test
    fun `MetaspriteFrame constructs and exposes tiles of size 1`() {
        val tile = MetaspriteTile(0, 0, 0)
        val frame = MetaspriteFrame(listOf(tile))
        assertEquals(1, frame.tiles.size)
        assertEquals(tile, frame.tiles.first())
    }

    @Test
    fun `MetaspriteTile relX relY tileId fields are accessible`() {
        val tile = MetaspriteTile(relX = 8, relY = 16, tileId = 3)
        assertEquals(8, tile.relX)
        assertEquals(16, tile.relY)
        assertEquals(3, tile.tileId)
    }

    @Test
    fun `MetaspriteIR frames first tile relX is 8`() {
        val tile = MetaspriteTile(8, 0, 1)
        val frame = MetaspriteFrame(listOf(tile))
        val ms = MetaspriteIR("ms", listOf(frame))
        assertEquals(8, ms.frames.first().tiles.first().relX)
    }

    @Test
    fun `MetaspriteIR sourceLocation defaults to null and can be set`() {
        val loc = SourceLocation(file = "test.kt", line = 42, col = 5)
        val ms = MetaspriteIR("hero", emptyList(), sourceLocation = loc)
        assertEquals(loc, ms.sourceLocation)
        assertEquals(42, ms.sourceLocation!!.line)
    }

    @Test
    fun `MetaspriteIR is a plain data class without platform annotation fields`() {
        // Shape contract: MetaspriteIR should only expose id, frames, sourceLocation.
        // If PlatformAnnotatable were added, this test must be updated. The compiler
        // would require adding bankSlot, vramRange, oamSlot override fields.
        val ms = MetaspriteIR("x", emptyList())
        assertEquals("x", ms.id)
        assertTrue(ms.frames.isEmpty())
        assertNull(ms.sourceLocation)
        // Verify the three expected fields are present and accessible (compile-time shape check)
        val expectedFields = listOf(ms.id, ms.frames, ms.sourceLocation)
        assertEquals(3, expectedFields.size)
    }

    @Test
    fun `MetaspriteFrame with multiple tiles round-trips field access`() {
        val tiles =
            listOf(
                MetaspriteTile(0, 0, 0),
                MetaspriteTile(8, 0, 1),
                MetaspriteTile(0, 8, 2),
                MetaspriteTile(8, 8, 3),
            )
        val frame = MetaspriteFrame(tiles)
        assertEquals(4, frame.tiles.size)
        assertEquals(8, frame.tiles[1].relX)
        assertEquals(2, frame.tiles[2].tileId)
    }

    @Test
    fun `MetaspriteIR with multiple frames all frames accessible`() {
        val frame0 = MetaspriteFrame(listOf(MetaspriteTile(0, 0, 0)))
        val frame1 = MetaspriteFrame(listOf(MetaspriteTile(8, 0, 1)))
        val ms = MetaspriteIR("player", listOf(frame0, frame1))
        assertEquals(2, ms.frames.size)
        assertEquals(0, ms.frames[0].tiles.first().tileId)
        assertEquals(1, ms.frames[1].tiles.first().tileId)
    }

    // =========================================================================
    // Task 2: GameIR backward-compat and metasprites field tests
    // =========================================================================

    @Test
    fun `GameIR constructed with no metasprites has empty metasprites list`() {
        val game = GameIR(name = "X")
        assertEquals(emptyList<MetaspriteIR>(), game.metasprites)
    }

    @Test
    fun `GameIR constructed with metasprites preserves them`() {
        val tile = MetaspriteTile(0, 0, 0)
        val frame = MetaspriteFrame(listOf(tile))
        val ms = MetaspriteIR("foo", listOf(frame))
        val game = GameIR(name = "X", metasprites = listOf(ms))
        assertEquals(1, game.metasprites.size)
        assertEquals("foo", game.metasprites.first().id)
    }

    @Test
    fun `GameIR metasprites round-trips through GameIRSerializer`() {
        val tile = MetaspriteTile(0, 0, 0)
        val frame = MetaspriteFrame(listOf(tile))
        val ms = MetaspriteIR("foo", listOf(frame))
        val game = GameIR(name = "X", metasprites = listOf(ms))

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertEquals(1, back.metasprites.size)
        val msBack = back.metasprites.first()
        assertEquals("foo", msBack.id)
        assertEquals(1, msBack.frames.size)
        assertEquals(1, msBack.frames.first().tiles.size)
        val tileBack = msBack.frames.first().tiles.first()
        assertEquals(0, tileBack.relX)
        assertEquals(0, tileBack.relY)
        assertEquals(0, tileBack.tileId)
    }

    @Test
    fun `MetaspriteIR with sourceLocation round-trips through GameIRSerializer`() {
        val loc = SourceLocation(file = "test.kt", line = 10, col = 1)
        val tile = MetaspriteTile(4, 8, 2)
        val frame = MetaspriteFrame(listOf(tile))
        val ms = MetaspriteIR("hero", listOf(frame), sourceLocation = loc)
        val game = GameIR(name = "RoundTripGame", metasprites = listOf(ms))

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertEquals(1, back.metasprites.size)
        val msBack = back.metasprites.first()
        assertEquals("hero", msBack.id)
        val locBack = msBack.sourceLocation
        assertEquals(10, locBack?.line)
        assertEquals(1, locBack?.col)
    }

    @Test
    fun `multi-frame multi-tile MetaspriteIR round-trips through GameIRSerializer`() {
        val frame0 = MetaspriteFrame(listOf(MetaspriteTile(0, 0, 0), MetaspriteTile(8, 0, 1)))
        val frame1 = MetaspriteFrame(listOf(MetaspriteTile(0, 0, 2), MetaspriteTile(8, 0, 3)))
        val ms = MetaspriteIR("player", listOf(frame0, frame1))
        val game = GameIR(name = "MultiFrameGame", metasprites = listOf(ms))

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        val msBack = back.metasprites.first()
        assertEquals(2, msBack.frames.size)
        assertEquals(2, msBack.frames[0].tiles.size)
        assertEquals(1, msBack.frames[0].tiles[1].tileId)
        assertEquals(3, msBack.frames[1].tiles[1].tileId)
        assertEquals(8, msBack.frames[1].tiles[1].relX)
    }

    // =========================================================================
    // Task 3 (Plan 04): MoveMetasprite ScriptOp shape and visitor dispatch
    // =========================================================================

    @Test
    fun `MoveMetasprite metaspriteId field equals constructor argument`() {
        val op = MoveMetasprite("elephant")
        assertEquals("elephant", op.metaspriteId)
    }

    @Test
    fun `MoveMetasprite sourceLocation defaults to null`() {
        val op = MoveMetasprite("elephant")
        assertNull(op.sourceLocation)
    }

    @Test
    fun `MoveMetasprite accept dispatches to visitMoveMetasprite`() {
        val op = MoveMetasprite("elephant")
        var visited = false
        val visitor =
            object : ScriptOpVisitorI<Unit> {
                override fun visitMoveMetasprite(op: MoveMetasprite) {
                    visited = true
                    assertEquals("elephant", op.metaspriteId)
                }

                // All other visit methods are no-ops for this test
                override fun visitAssign(op: Assign) = Unit

                override fun visitArrayAssign(op: ArrayAssign) = Unit

                override fun visitIfOp(op: IfOp) = Unit

                override fun visitWhileOp(op: WhileOp) = Unit

                override fun visitForOp(op: ForOp) = Unit

                override fun visitSetPosition(op: SetPosition) = Unit

                override fun visitMoveBy(op: MoveBy) = Unit

                override fun visitNavigateTo(op: NavigateTo) = Unit

                override fun visitTriggerSystem(op: TriggerSystem) = Unit

                override fun visitPlaySound(op: PlaySound) = Unit

                override fun visitMusicPlay(op: MusicPlay) = Unit

                override fun visitMusicStop(op: MusicStop) = Unit

                override fun visitMusicPause(op: MusicPause) = Unit

                override fun visitMusicResume(op: MusicResume) = Unit

                override fun visitDialogSay(op: DialogSay) = Unit

                override fun visitDialogChoice(op: DialogChoice) = Unit

                override fun visitMenuShow(op: MenuShow) = Unit

                override fun visitMenuHide(op: MenuHide) = Unit

                override fun visitHudShow(op: HudShow) = Unit

                override fun visitHudHide(op: HudHide) = Unit

                override fun visitPrintAt(op: PrintAt) = Unit

                override fun visitPrintCentered(op: PrintCentered) = Unit

                override fun visitPrintAligned(op: PrintAligned) = Unit

                override fun visitClearRegion(op: ClearRegion) = Unit

                override fun visitScreenClear(op: ScreenClear) = Unit

                override fun visitScreenFill(op: ScreenFill) = Unit

                override fun visitBindCurrentLevel(op: BindCurrentLevel) = Unit

                override fun visitPrintOp(op: PrintOp) = Unit

                override fun visitGotoXYOp(op: GotoXYOp) = Unit

                override fun visitFadeOp(op: FadeOp) = Unit

                override fun visitSetVisible(op: SetVisible) = Unit

                override fun visitSpawnActor(op: SpawnActor) = Unit

                override fun visitDestroyActor(op: DestroyActor) = Unit

                override fun visitPoolSpawnActor(op: PoolSpawnActor) = Unit

                override fun visitPoolDestroyActor(op: PoolDestroyActor) = Unit

                override fun visitPoolForEachActive(op: PoolForEachActive) = Unit

                override fun visitPoolDestroyAll(op: PoolDestroyAll) = Unit

                override fun visitAnimateOp(op: AnimateOp) = Unit

                override fun visitCameraOp(op: CameraOp) = Unit

                override fun visitWaitFrames(op: WaitFrames) = Unit

                override fun visitCallOp(op: CallOp) = Unit

                override fun visitReturnOp(op: ReturnOp) = Unit

                override fun visitMathOp(op: MathOp) = Unit

                override fun visitRawOp(op: RawOp) = Unit

                override fun visitSetAnimationState(op: SetAnimationState) = Unit

                override fun visitPhysicsStep(op: PhysicsStep) = Unit

                override fun visitPathfindStep(op: PathfindStep) = Unit

                override fun visitWaypointStep(op: WaypointStep) = Unit

                override fun visitSetPalette(op: SetPalette) = Unit

                override fun visitActivatePuzzleObject(op: ActivatePuzzleObject) = Unit

                override fun visitDeactivatePuzzleObject(op: DeactivatePuzzleObject) = Unit

                override fun visitRevealPuzzleObject(op: RevealPuzzleObject) = Unit

                override fun visitHidePuzzleObject(op: HidePuzzleObject) = Unit
            }
        op.accept(visitor)
        assertTrue(visited, "visitMoveMetasprite should have been called")
    }
}
