/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// ACTOR POOL IR TESTS
// Verifies ActorPoolIR, ActorPoolConfig, PoolOverflowStrategy,
// PoolSpawnActor, and PoolDestroyActor IR types.
// =============================================================================

class ActorPoolIRTest {

    // =========================================================================
    // PoolOverflowStrategy
    // =========================================================================

    @Test
    fun `PoolOverflowStrategy has SILENT_NOOP and RECYCLE_OLDEST variants`() {
        // Both variants must exist for exhaustive when matching in codegen
        val noop = PoolOverflowStrategy.SILENT_NOOP
        val recycle = PoolOverflowStrategy.RECYCLE_OLDEST
        assertTrue(noop != recycle, "Overflow strategies must be distinct")
    }

    // =========================================================================
    // ActorPoolConfig
    // =========================================================================

    @Test
    fun `ActorPoolConfig defaults to SILENT_NOOP overflow strategy`() {
        val config = ActorPoolConfig(maxSize = 8)
        assertEquals(8, config.maxSize)
        assertEquals(PoolOverflowStrategy.SILENT_NOOP, config.overflowStrategy)
    }

    @Test
    fun `ActorPoolConfig accepts RECYCLE_OLDEST overflow strategy`() {
        val config =
            ActorPoolConfig(maxSize = 4, overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST)
        assertEquals(4, config.maxSize)
        assertEquals(PoolOverflowStrategy.RECYCLE_OLDEST, config.overflowStrategy)
    }

    @Test
    fun `ActorPoolConfig equality is structural`() {
        val a = ActorPoolConfig(maxSize = 8, overflowStrategy = PoolOverflowStrategy.SILENT_NOOP)
        val b = ActorPoolConfig(maxSize = 8, overflowStrategy = PoolOverflowStrategy.SILENT_NOOP)
        assertEquals(a, b, "Two ActorPoolConfig with same values should be equal")
    }

    // =========================================================================
    // ActorPoolIR
    // =========================================================================

    @Test
    fun `ActorPoolIR can be constructed with all fields`() {
        val config = ActorPoolConfig(maxSize = 8)
        val pool = ActorPoolIR(id = "bullets", actorTemplateId = "bullet", config = config)

        assertEquals("bullets", pool.id)
        assertEquals("bullet", pool.actorTemplateId)
        assertEquals(8, pool.config.maxSize)
        assertEquals(PoolOverflowStrategy.SILENT_NOOP, pool.config.overflowStrategy)
    }

    @Test
    fun `ActorPoolIR with RECYCLE_OLDEST strategy`() {
        val config =
            ActorPoolConfig(maxSize = 4, overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST)
        val pool = ActorPoolIR(id = "particles", actorTemplateId = "spark", config = config)

        assertEquals("particles", pool.id)
        assertEquals("spark", pool.actorTemplateId)
        assertEquals(PoolOverflowStrategy.RECYCLE_OLDEST, pool.config.overflowStrategy)
    }

    @Test
    fun `ActorPoolIR equality is structural`() {
        val config = ActorPoolConfig(maxSize = 8)
        val a = ActorPoolIR(id = "bullets", actorTemplateId = "bullet", config = config)
        val b = ActorPoolIR(id = "bullets", actorTemplateId = "bullet", config = config)
        assertEquals(a, b)
    }

    @Test
    fun `ActorPoolIR copy works correctly`() {
        val config = ActorPoolConfig(maxSize = 8)
        val original = ActorPoolIR(id = "bullets", actorTemplateId = "bullet", config = config)
        val copied = original.copy(id = "missiles")

        assertEquals("missiles", copied.id)
        assertEquals("bullet", copied.actorTemplateId)
        assertEquals(8, copied.config.maxSize)
    }

    // =========================================================================
    // GameIR.actorPools
    // =========================================================================

    @Test
    fun `GameIR actorPools defaults to empty list`() {
        val game = GameIR(name = "TestGame")
        assertEquals(emptyList(), game.actorPools)
    }

    @Test
    fun `GameIR carries actorPools field`() {
        val config = ActorPoolConfig(maxSize = 8)
        val pool = ActorPoolIR(id = "bullets", actorTemplateId = "bullet", config = config)
        val game = GameIR(name = "TestGame", actorPools = listOf(pool))

        assertEquals(1, game.actorPools.size)
        assertEquals("bullets", game.actorPools.first().id)
    }

    @Test
    fun `GameIR can carry multiple actor pools`() {
        val bulletPool =
            ActorPoolIR(
                id = "bullets",
                actorTemplateId = "bullet",
                config = ActorPoolConfig(maxSize = 8),
            )
        val particlePool =
            ActorPoolIR(
                id = "sparks",
                actorTemplateId = "spark",
                config =
                    ActorPoolConfig(
                        maxSize = 16,
                        overflowStrategy = PoolOverflowStrategy.RECYCLE_OLDEST,
                    ),
            )
        val game = GameIR(name = "TestGame", actorPools = listOf(bulletPool, particlePool))

        assertEquals(2, game.actorPools.size)
        assertEquals("bullets", game.actorPools[0].id)
        assertEquals("sparks", game.actorPools[1].id)
        assertEquals(
            PoolOverflowStrategy.RECYCLE_OLDEST,
            game.actorPools[1].config.overflowStrategy,
        )
    }

    // =========================================================================
    // PoolSpawnActor ScriptOp
    // =========================================================================

    @Test
    fun `PoolSpawnActor has correct fields`() {
        val x = Literal(80)
        val y = Literal(72)
        val op = PoolSpawnActor(poolId = "bullets", x = x, y = y)

        assertEquals("bullets", op.poolId)
        assertNotNull(op.x)
        assertNotNull(op.y)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `PoolSpawnActor visitor dispatch routes to visitPoolSpawnActor`() {
        val op = PoolSpawnActor(poolId = "bullets", x = Literal(80), y = Literal(72))
        var visited = false
        val visitor =
            object : ScriptOpVisitorI<Unit> {
                override fun visitPoolSpawnActor(op: PoolSpawnActor) {
                    visited = true
                    assertEquals("bullets", op.poolId)
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

                override fun visitPrintOp(op: PrintOp) = Unit

                override fun visitGotoXYOp(op: GotoXYOp) = Unit

                override fun visitFadeOp(op: FadeOp) = Unit

                override fun visitSetVisible(op: SetVisible) = Unit

                override fun visitSpawnActor(op: SpawnActor) = Unit

                override fun visitDestroyActor(op: DestroyActor) = Unit

                override fun visitPoolDestroyActor(op: PoolDestroyActor) = Unit

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

                override fun visitPoolForEachActive(op: PoolForEachActive) = Unit

                override fun visitPoolDestroyAll(op: PoolDestroyAll) = Unit
            }
        op.accept(visitor)
        assertTrue(visited, "visitPoolSpawnActor should have been called")
    }

    // =========================================================================
    // PoolDestroyActor ScriptOp
    // =========================================================================

    @Test
    fun `PoolDestroyActor has correct fields`() {
        val slot = VarRef("bulletSlot")
        val op = PoolDestroyActor(poolId = "bullets", slotExpr = slot)

        assertEquals("bullets", op.poolId)
        assertNotNull(op.slotExpr)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `PoolDestroyActor visitor dispatch routes to visitPoolDestroyActor`() {
        val op = PoolDestroyActor(poolId = "bullets", slotExpr = VarRef("slot"))
        var visited = false
        val visitor =
            object : ScriptOpVisitorI<Unit> {
                override fun visitPoolDestroyActor(op: PoolDestroyActor) {
                    visited = true
                    assertEquals("bullets", op.poolId)
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

                override fun visitPrintOp(op: PrintOp) = Unit

                override fun visitGotoXYOp(op: GotoXYOp) = Unit

                override fun visitFadeOp(op: FadeOp) = Unit

                override fun visitSetVisible(op: SetVisible) = Unit

                override fun visitSpawnActor(op: SpawnActor) = Unit

                override fun visitDestroyActor(op: DestroyActor) = Unit

                override fun visitPoolSpawnActor(op: PoolSpawnActor) = Unit

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

                override fun visitPoolForEachActive(op: PoolForEachActive) = Unit

                override fun visitPoolDestroyAll(op: PoolDestroyAll) = Unit
            }
        op.accept(visitor)
        assertTrue(visited, "visitPoolDestroyActor should have been called")
    }
}
