/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// SCRIPT OP VISITOR INTERFACE
// =============================================================================

/**
 * Visitor interface for [ScriptOp] dispatch.
 *
 * Provides one `visit*` method per [ScriptOp] subtype (36 total). Implementations convert IR nodes
 * to a result of type [T].
 *
 * The `I` suffix distinguishes this interface from the backend's `ScriptOpVisitor` object (which is
 * an implementation of this interface).
 *
 * Usage:
 * ```kotlin
 * object MyVisitor : ScriptOpVisitorI<String> {
 *     override fun visitAssign(op: Assign): String = "assign ${op.target}"
 *     // ...
 * }
 * val result = someOp.accept(MyVisitor)
 * ```
 */
interface ScriptOpVisitorI<T> {

    // --- State mutation ---

    fun visitAssign(op: Assign): T

    fun visitArrayAssign(op: ArrayAssign): T

    // --- Control flow ---

    fun visitIfOp(op: IfOp): T

    fun visitWhileOp(op: WhileOp): T

    fun visitForOp(op: ForOp): T

    // --- Movement ---

    fun visitSetPosition(op: SetPosition): T

    fun visitMoveBy(op: MoveBy): T

    // --- Navigation ---

    fun visitNavigateTo(op: NavigateTo): T

    // --- Systems ---

    fun visitTriggerSystem(op: TriggerSystem): T

    // --- Audio ---

    fun visitPlaySound(op: PlaySound): T

    fun visitMusicPlay(op: MusicPlay): T

    fun visitMusicStop(op: MusicStop): T

    fun visitMusicPause(op: MusicPause): T

    fun visitMusicResume(op: MusicResume): T

    // --- Dialog and menus ---

    fun visitDialogSay(op: DialogSay): T

    fun visitDialogChoice(op: DialogChoice): T

    fun visitMenuShow(op: MenuShow): T

    fun visitMenuHide(op: MenuHide): T

    fun visitHudShow(op: HudShow): T

    fun visitHudHide(op: HudHide): T

    fun visitPrintAt(op: PrintAt): T

    fun visitPrintCentered(op: PrintCentered): T

    fun visitPrintAligned(op: PrintAligned): T

    fun visitClearRegion(op: ClearRegion): T

    fun visitScreenClear(op: ScreenClear): T

    fun visitScreenFill(op: ScreenFill): T

    // --- Display ---

    fun visitPrintOp(op: PrintOp): T

    fun visitFadeOp(op: FadeOp): T

    fun visitSetVisible(op: SetVisible): T

    // --- Entity lifecycle ---

    fun visitSpawnActor(op: SpawnActor): T

    fun visitDestroyActor(op: DestroyActor): T

    // --- Actor pool lifecycle ---

    fun visitPoolSpawnActor(op: PoolSpawnActor): T

    fun visitPoolDestroyActor(op: PoolDestroyActor): T

    fun visitPoolForEachActive(op: PoolForEachActive): T

    fun visitPoolDestroyAll(op: PoolDestroyAll): T

    fun visitAnimateOp(op: AnimateOp): T

    // --- Camera ---

    fun visitCameraOp(op: CameraOp): T

    // --- Timing ---

    fun visitWaitFrames(op: WaitFrames): T

    // --- Function calls ---

    fun visitCallOp(op: CallOp): T

    fun visitReturnOp(op: ReturnOp): T

    // --- Math ---

    fun visitMathOp(op: MathOp): T

    // --- Escape hatch ---

    fun visitRawOp(op: RawOp): T

    // --- Palette ---

    fun visitSetPalette(op: SetPalette): T

    // --- Cursor positioning ---

    fun visitGotoXYOp(op: GotoXYOp): T

    // --- Puzzle objects ---

    fun visitActivatePuzzleObject(op: ActivatePuzzleObject): T

    fun visitDeactivatePuzzleObject(op: DeactivatePuzzleObject): T

    fun visitRevealPuzzleObject(op: RevealPuzzleObject): T

    fun visitHidePuzzleObject(op: HidePuzzleObject): T

    // --- Animation state machine ---

    fun visitSetAnimationState(op: SetAnimationState): T

    // --- Physics ---

    fun visitPhysicsStep(op: PhysicsStep): T

    // --- Pathfinding ---

    fun visitPathfindStep(op: PathfindStep): T

    fun visitWaypointStep(op: WaypointStep): T
}
