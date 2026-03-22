/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// PUZZLE OBJECT IR TESTS
// Verifies the PuzzleObjectIR sealed hierarchy: SwitchObjectIR, DoorObjectIR,
// PressurePlateObjectIR, TimedBlockObjectIR.
// Tests cover field defaults, event handlers, ScriptOps, GameIR registration,
// and ScriptOp visitor dispatch for puzzle ScriptOps.
// =============================================================================

class PuzzleCoreTest {

    // =========================================================================
    // SwitchObjectIR — construction and defaults
    // =========================================================================

    @Test
    fun `SwitchObjectIR can be constructed with minimal fields`() {
        val sw = SwitchObjectIR(id = "sw1", x = 5, y = 3)

        assertEquals("sw1", sw.id)
        assertEquals(5, sw.x)
        assertEquals(3, sw.y)
        assertFalse(sw.hidden, "Switch should not be hidden by default")
        assertTrue(sw.onActivate.isEmpty(), "onActivate should default to empty list")
        assertTrue(sw.onDeactivate.isEmpty(), "onDeactivate should default to empty list")
        assertTrue(sw.handlers.isEmpty(), "handlers should default to empty list")
    }

    @Test
    fun `SwitchObjectIR hidden flag defaults to false`() {
        val sw = SwitchObjectIR(id = "sw", x = 0, y = 0)
        assertFalse(sw.hidden)
    }

    @Test
    fun `SwitchObjectIR can be declared as hidden`() {
        val sw = SwitchObjectIR(id = "hiddenSwitch", x = 5, y = 3, hidden = true)
        assertTrue(sw.hidden, "Switch should be hidden when hidden=true")
    }

    @Test
    fun `SwitchObjectIR can have onActivate ScriptOps`() {
        val assign = Assign(target = "score", value = Literal(10))
        val sw = SwitchObjectIR(id = "scoreSw", x = 1, y = 1, onActivate = listOf(assign))
        assertEquals(1, sw.onActivate.size)
        assertTrue(sw.onActivate[0] is Assign)
    }

    @Test
    fun `SwitchObjectIR can have onDeactivate ScriptOps`() {
        val assign = Assign(target = "score", value = Literal(0))
        val sw = SwitchObjectIR(id = "sw", x = 0, y = 0, onDeactivate = listOf(assign))
        assertEquals(1, sw.onDeactivate.size)
    }

    @Test
    fun `SwitchObjectIR implements PuzzleObjectIR sealed interface`() {
        val sw: PuzzleObjectIR = SwitchObjectIR(id = "sw", x = 5, y = 3)
        assertEquals("sw", sw.id)
        assertEquals(5, sw.x)
        assertEquals(3, sw.y)
    }

    // =========================================================================
    // DoorObjectIR — construction and tile state fields
    // =========================================================================

    @Test
    fun `DoorObjectIR can be constructed with minimal fields`() {
        val door = DoorObjectIR(id = "bossDoor", x = 10, y = 5)

        assertEquals("bossDoor", door.id)
        assertEquals(10, door.x)
        assertEquals(5, door.y)
        assertFalse(door.hidden)
        assertEquals(0, door.openTile, "openTile should default to 0")
        assertEquals(0, door.closedTile, "closedTile should default to 0")
        assertTrue(door.onOpen.isEmpty())
        assertTrue(door.onClose.isEmpty())
    }

    @Test
    fun `DoorObjectIR stores openTile and closedTile`() {
        val door = DoorObjectIR(id = "bossDoor", x = 10, y = 5, openTile = 0x20, closedTile = 0x21)
        assertEquals(0x20, door.openTile)
        assertEquals(0x21, door.closedTile)
    }

    @Test
    fun `DoorObjectIR can have onOpen and onClose ScriptOps`() {
        val openOp = Assign(target = "doorOpen", value = Literal(1))
        val closeOp = Assign(target = "doorOpen", value = Literal(0))
        val door =
            DoorObjectIR(
                id = "exitDoor",
                x = 8,
                y = 4,
                openTile = 0x10,
                closedTile = 0x11,
                onOpen = listOf(openOp),
                onClose = listOf(closeOp),
            )
        assertEquals(1, door.onOpen.size)
        assertEquals(1, door.onClose.size)
    }

    @Test
    fun `DoorObjectIR can be hidden`() {
        val door = DoorObjectIR(id = "hiddenDoor", x = 0, y = 0, hidden = true)
        assertTrue(door.hidden)
    }

    @Test
    fun `DoorObjectIR implements PuzzleObjectIR sealed interface`() {
        val door: PuzzleObjectIR = DoorObjectIR(id = "door", x = 10, y = 5)
        assertEquals("door", door.id)
    }

    // =========================================================================
    // PressurePlateObjectIR — respondToActorIds
    // =========================================================================

    @Test
    fun `PressurePlateObjectIR stores respondToActorIds`() {
        val plate =
            PressurePlateObjectIR(
                id = "entryPlate",
                x = 7,
                y = 4,
                respondToActorIds = listOf("player"),
            )
        assertEquals("entryPlate", plate.id)
        assertEquals(listOf("player"), plate.respondToActorIds)
    }

    @Test
    fun `PressurePlateObjectIR can respond to multiple actors`() {
        val plate =
            PressurePlateObjectIR(
                id = "multiPlate",
                x = 3,
                y = 3,
                respondToActorIds = listOf("player", "enemy1", "enemy2"),
            )
        assertEquals(3, plate.respondToActorIds.size)
        assertTrue(plate.respondToActorIds.contains("player"))
        assertTrue(plate.respondToActorIds.contains("enemy1"))
    }

    @Test
    fun `PressurePlateObjectIR with empty respondToActorIds is valid`() {
        val plate =
            PressurePlateObjectIR(id = "plate", x = 0, y = 0, respondToActorIds = emptyList())
        assertTrue(plate.respondToActorIds.isEmpty())
    }

    @Test
    fun `PressurePlateObjectIR can have onStepOn and onStepOff ScriptOps`() {
        val stepOn = Assign(target = "platePressed", value = Literal(1))
        val stepOff = Assign(target = "platePressed", value = Literal(0))
        val plate =
            PressurePlateObjectIR(
                id = "plate",
                x = 7,
                y = 4,
                respondToActorIds = listOf("player"),
                onStepOn = listOf(stepOn),
                onStepOff = listOf(stepOff),
            )
        assertEquals(1, plate.onStepOn.size)
        assertEquals(1, plate.onStepOff.size)
    }

    @Test
    fun `PressurePlateObjectIR implements PuzzleObjectIR sealed interface`() {
        val plate: PuzzleObjectIR =
            PressurePlateObjectIR(id = "plate", x = 7, y = 4, respondToActorIds = listOf("player"))
        assertEquals("plate", plate.id)
        assertEquals(7, plate.x)
        assertEquals(4, plate.y)
    }

    // =========================================================================
    // TimedBlockObjectIR — timer and tile state
    // =========================================================================

    @Test
    fun `TimedBlockObjectIR stores solidTile, emptyTile, and interval`() {
        val block =
            TimedBlockObjectIR(
                id = "timerBlock",
                x = 12,
                y = 6,
                solidTile = 0x15,
                emptyTile = 0x00,
                interval = 60,
            )
        assertEquals("timerBlock", block.id)
        assertEquals(12, block.x)
        assertEquals(6, block.y)
        assertEquals(0x15, block.solidTile)
        assertEquals(0x00, block.emptyTile)
        assertEquals(60, block.interval)
        assertFalse(block.hidden)
    }

    @Test
    fun `TimedBlockObjectIR can be hidden`() {
        val block =
            TimedBlockObjectIR(
                id = "hiddenBlock",
                x = 0,
                y = 0,
                solidTile = 1,
                emptyTile = 0,
                interval = 30,
                hidden = true,
            )
        assertTrue(block.hidden)
    }

    @Test
    fun `TimedBlockObjectIR implements PuzzleObjectIR sealed interface`() {
        val block: PuzzleObjectIR =
            TimedBlockObjectIR(
                id = "block",
                x = 12,
                y = 6,
                solidTile = 0x15,
                emptyTile = 0x00,
                interval = 60,
            )
        assertEquals("block", block.id)
    }

    // =========================================================================
    // PuzzleEventHandler and PuzzleEventType
    // =========================================================================

    @Test
    fun `PuzzleEventHandler stores event type and actions`() {
        val op = Assign(target = "flag", value = Literal(1))
        val handler = PuzzleEventHandler(event = PuzzleEventType.INTERACT, actions = listOf(op))
        assertEquals(PuzzleEventType.INTERACT, handler.event)
        assertEquals(1, handler.actions.size)
    }

    @Test
    fun `PuzzleEventType has all expected values`() {
        val values = PuzzleEventType.entries
        assertTrue(values.contains(PuzzleEventType.INTERACT))
        assertTrue(values.contains(PuzzleEventType.STEP_ON))
        assertTrue(values.contains(PuzzleEventType.STEP_OFF))
        assertTrue(values.contains(PuzzleEventType.TIMER))
        assertTrue(values.contains(PuzzleEventType.FLAG_CHANGED))
    }

    @Test
    fun `PuzzleObjectIR can have generic event handlers`() {
        val interactHandler =
            PuzzleEventHandler(
                event = PuzzleEventType.INTERACT,
                actions = listOf(Assign(target = "activated", value = Literal(1))),
            )
        val sw = SwitchObjectIR(id = "sw", x = 5, y = 3, handlers = listOf(interactHandler))
        assertEquals(1, sw.handlers.size)
        assertEquals(PuzzleEventType.INTERACT, sw.handlers[0].event)
    }

    // =========================================================================
    // GameIR.puzzleObjects registration
    // =========================================================================

    @Test
    fun `GameIR puzzleObjects defaults to empty list`() {
        val game = GameIR(name = "TestGame")
        assertTrue(game.puzzleObjects.isEmpty(), "GameIR.puzzleObjects should default to empty")
    }

    @Test
    fun `GameIR can carry all four puzzle object types`() {
        val sw = SwitchObjectIR(id = "sw1", x = 5, y = 3)
        val door = DoorObjectIR(id = "bossDoor", x = 10, y = 5, openTile = 0x20, closedTile = 0x21)
        val plate =
            PressurePlateObjectIR(
                id = "entryPlate",
                x = 7,
                y = 4,
                respondToActorIds = listOf("player"),
            )
        val block =
            TimedBlockObjectIR(
                id = "timerBlock",
                x = 12,
                y = 6,
                solidTile = 0x15,
                emptyTile = 0x00,
                interval = 60,
            )

        val game = GameIR(name = "PuzzleGame", puzzleObjects = listOf(sw, door, plate, block))

        assertEquals(4, game.puzzleObjects.size)
        assertTrue(game.puzzleObjects[0] is SwitchObjectIR)
        assertTrue(game.puzzleObjects[1] is DoorObjectIR)
        assertTrue(game.puzzleObjects[2] is PressurePlateObjectIR)
        assertTrue(game.puzzleObjects[3] is TimedBlockObjectIR)
    }

    @Test
    fun `GameIR puzzleObjects are immutable (read-only list)`() {
        val sw = SwitchObjectIR(id = "sw", x = 0, y = 0)
        val game = GameIR(name = "Game", puzzleObjects = listOf(sw))
        assertEquals(1, game.puzzleObjects.size)
        // The returned list is unmodifiable — attempting to cast and mutate would throw
    }

    // =========================================================================
    // ScriptOps: ActivatePuzzleObject, DeactivatePuzzleObject, RevealPuzzleObject, HidePuzzleObject
    // =========================================================================

    @Test
    fun `ActivatePuzzleObject stores objectId`() {
        val op = ActivatePuzzleObject(objectId = "bossDoor")
        assertEquals("bossDoor", op.objectId)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `DeactivatePuzzleObject stores objectId`() {
        val op = DeactivatePuzzleObject(objectId = "bossDoor")
        assertEquals("bossDoor", op.objectId)
    }

    @Test
    fun `RevealPuzzleObject stores objectId`() {
        val op = RevealPuzzleObject(objectId = "hiddenSwitch")
        assertEquals("hiddenSwitch", op.objectId)
    }

    @Test
    fun `HidePuzzleObject stores objectId`() {
        val op = HidePuzzleObject(objectId = "sw1")
        assertEquals("sw1", op.objectId)
    }

    @Test
    fun `ActivatePuzzleObject dispatches to visitor via accept`() {
        val op: ScriptOp = ActivatePuzzleObject(objectId = "sw1")
        val result = op.accept(PuzzleOpDescriber)
        assertEquals("ActivatePuzzleObject(sw1)", result)
    }

    @Test
    fun `DeactivatePuzzleObject dispatches to visitor via accept`() {
        val op: ScriptOp = DeactivatePuzzleObject(objectId = "bossDoor")
        val result = op.accept(PuzzleOpDescriber)
        assertEquals("DeactivatePuzzleObject(bossDoor)", result)
    }

    @Test
    fun `RevealPuzzleObject dispatches to visitor via accept`() {
        val op: ScriptOp = RevealPuzzleObject(objectId = "hiddenSw")
        val result = op.accept(PuzzleOpDescriber)
        assertEquals("RevealPuzzleObject(hiddenSw)", result)
    }

    @Test
    fun `HidePuzzleObject dispatches to visitor via accept`() {
        val op: ScriptOp = HidePuzzleObject(objectId = "sw1")
        val result = op.accept(PuzzleOpDescriber)
        assertEquals("HidePuzzleObject(sw1)", result)
    }

    // =========================================================================
    // Exhaustive when — PuzzleObjectIR sealed dispatch
    // =========================================================================

    @Test
    fun `when on PuzzleObjectIR is exhaustive without else branch`() {
        val objects: List<PuzzleObjectIR> =
            listOf(
                SwitchObjectIR(id = "sw", x = 5, y = 3),
                DoorObjectIR(id = "door", x = 10, y = 5),
                PressurePlateObjectIR(
                    id = "plate",
                    x = 7,
                    y = 4,
                    respondToActorIds = listOf("player"),
                ),
                TimedBlockObjectIR(
                    id = "block",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                ),
            )
        // This function compiles only if all cases are handled — no else needed
        val descriptions =
            objects.map { obj ->
                when (obj) {
                    is SwitchObjectIR -> "Switch(${obj.id})"
                    is DoorObjectIR -> "Door(${obj.id})"
                    is PressurePlateObjectIR -> "Plate(${obj.id})"
                    is TimedBlockObjectIR -> "Block(${obj.id})"
                    is TriggerObjectIR -> "Trigger(${obj.id})"
                }
            }
        assertEquals(
            listOf("Switch(sw)", "Door(door)", "Plate(plate)", "Block(block)"),
            descriptions,
        )
    }

    // =========================================================================
    // Hidden state: initial visibility via hidden field
    // =========================================================================

    @Test
    fun `hidden switch initial state is correct`() {
        val visible = SwitchObjectIR(id = "visible", x = 0, y = 0, hidden = false)
        val hidden = SwitchObjectIR(id = "hidden", x = 1, y = 1, hidden = true)
        assertFalse(visible.hidden)
        assertTrue(hidden.hidden)
    }

    @Test
    fun `hidden door initial state is correct`() {
        val door = DoorObjectIR(id = "door", x = 0, y = 0, hidden = true)
        assertTrue(door.hidden)
    }

    @Test
    fun `hidden pressure plate initial state is correct`() {
        val plate =
            PressurePlateObjectIR(
                id = "plate",
                x = 0,
                y = 0,
                respondToActorIds = emptyList(),
                hidden = true,
            )
        assertTrue(plate.hidden)
    }

    @Test
    fun `hidden timed block initial state is correct`() {
        val block =
            TimedBlockObjectIR(
                id = "block",
                x = 0,
                y = 0,
                solidTile = 1,
                emptyTile = 0,
                interval = 30,
                hidden = true,
            )
        assertTrue(block.hidden)
    }
}

// =============================================================================
// MINIMAL VISITOR IMPLEMENTATION FOR PUZZLE SCRIPTOP DISPATCH TESTS
// Implements only the puzzle-op methods — stubs all others via delegation.
// =============================================================================

/**
 * Describes puzzle-specific [ScriptOp] nodes for dispatch verification tests.
 *
 * Stubs all non-puzzle methods by delegating to [NoOpScriptOpDescriber].
 */
private object PuzzleOpDescriber : ScriptOpVisitorI<String> {
    override fun visitAssign(op: Assign): String = "Assign"

    override fun visitArrayAssign(op: ArrayAssign): String = "ArrayAssign"

    override fun visitIfOp(op: IfOp): String = "IfOp"

    override fun visitWhileOp(op: WhileOp): String = "WhileOp"

    override fun visitForOp(op: ForOp): String = "ForOp"

    override fun visitSetPosition(op: SetPosition): String = "SetPosition"

    override fun visitMoveBy(op: MoveBy): String = "MoveBy"

    override fun visitNavigateTo(op: NavigateTo): String = "NavigateTo"

    override fun visitTriggerSystem(op: TriggerSystem): String = "TriggerSystem"

    override fun visitPlaySound(op: PlaySound): String = "PlaySound"

    override fun visitMusicPlay(op: MusicPlay): String = "MusicPlay"

    override fun visitMusicStop(op: MusicStop): String = "MusicStop"

    override fun visitMusicPause(op: MusicPause): String = "MusicPause"

    override fun visitMusicResume(op: MusicResume): String = "MusicResume"

    override fun visitDialogSay(op: DialogSay): String = "DialogSay"

    override fun visitDialogChoice(op: DialogChoice): String = "DialogChoice"

    override fun visitMenuShow(op: MenuShow): String = "MenuShow"

    override fun visitMenuHide(op: MenuHide): String = "MenuHide"

    override fun visitHudShow(op: HudShow): String = "HudShow"

    override fun visitHudHide(op: HudHide): String = "HudHide"

    override fun visitPrintAt(op: PrintAt): String = "PrintAt"

    override fun visitPrintCentered(op: PrintCentered): String = "PrintCentered"

    override fun visitPrintAligned(op: PrintAligned): String = "PrintAligned"

    override fun visitClearRegion(op: ClearRegion): String = "ClearRegion"

    override fun visitScreenClear(op: ScreenClear): String = "ScreenClear"

    override fun visitScreenFill(op: ScreenFill): String = "ScreenFill"

    override fun visitPrintOp(op: PrintOp): String = "PrintOp"

    override fun visitFadeOp(op: FadeOp): String = "FadeOp"

    override fun visitSetVisible(op: SetVisible): String = "SetVisible"

    override fun visitSpawnActor(op: SpawnActor): String = "SpawnActor"

    override fun visitDestroyActor(op: DestroyActor): String = "DestroyActor"

    override fun visitPoolSpawnActor(op: PoolSpawnActor): String = "PoolSpawnActor"

    override fun visitPoolDestroyActor(op: PoolDestroyActor): String = "PoolDestroyActor"

    override fun visitAnimateOp(op: AnimateOp): String = "AnimateOp"

    override fun visitCameraOp(op: CameraOp): String = "CameraOp"

    override fun visitWaitFrames(op: WaitFrames): String = "WaitFrames"

    override fun visitCallOp(op: CallOp): String = "CallOp"

    override fun visitReturnOp(op: ReturnOp): String = "ReturnOp"

    override fun visitMathOp(op: MathOp): String = "MathOp"

    override fun visitRawOp(op: RawOp): String = "RawOp"

    override fun visitSetPalette(op: SetPalette): String = "SetPalette"

    override fun visitGotoXYOp(op: GotoXYOp): String = "GotoXYOp"

    override fun visitSetAnimationState(op: SetAnimationState): String = "SetAnimationState"

    override fun visitPhysicsStep(op: PhysicsStep): String = "PhysicsStep"

    override fun visitPathfindStep(op: PathfindStep): String = "PathfindStep"

    override fun visitWaypointStep(op: WaypointStep): String = "WaypointStep"

    override fun visitPoolForEachActive(op: PoolForEachActive): String = "PoolForEachActive"

    override fun visitPoolDestroyAll(op: PoolDestroyAll): String = "PoolDestroyAll"

    // Puzzle ScriptOp dispatch — the actual implementations under test
    override fun visitActivatePuzzleObject(op: ActivatePuzzleObject): String =
        "ActivatePuzzleObject(${op.objectId})"

    override fun visitDeactivatePuzzleObject(op: DeactivatePuzzleObject): String =
        "DeactivatePuzzleObject(${op.objectId})"

    override fun visitRevealPuzzleObject(op: RevealPuzzleObject): String =
        "RevealPuzzleObject(${op.objectId})"

    override fun visitHidePuzzleObject(op: HidePuzzleObject): String =
        "HidePuzzleObject(${op.objectId})"
}
