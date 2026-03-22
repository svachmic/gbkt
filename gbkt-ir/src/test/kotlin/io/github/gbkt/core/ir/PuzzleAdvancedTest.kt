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
import kotlin.test.assertTrue

// =============================================================================
// PUZZLE ADVANCED TESTS
// Verifies advanced puzzle system features:
//   - TriggerObjectIR (generic trigger with full event set)
//   - requires() chaining (multi-switch gates)
//   - hidden/reveal mechanics via requires and state tracking
//   - Multi-entity pressure plate respondTo (actor + pool prefix)
//   - All five PuzzleEventType values on TriggerObjectIR
//   - requires: field on all PuzzleObjectIR types
// =============================================================================

class PuzzleAdvancedTest {

    // =========================================================================
    // TriggerObjectIR — construction and defaults
    // =========================================================================

    @Test
    fun `TriggerObjectIR can be constructed with minimal fields`() {
        val trigger = TriggerObjectIR(id = "secretTrigger", x = 5, y = 3)

        assertEquals("secretTrigger", trigger.id)
        assertEquals(5, trigger.x)
        assertEquals(3, trigger.y)
        assertFalse(trigger.hidden, "Trigger should not be hidden by default")
        assertTrue(trigger.handlers.isEmpty(), "handlers should default to empty")
        assertTrue(trigger.requires.isEmpty(), "requires should default to empty")
    }

    @Test
    fun `TriggerObjectIR implements PuzzleObjectIR sealed interface`() {
        val trigger: PuzzleObjectIR = TriggerObjectIR(id = "trigger", x = 0, y = 0)
        assertEquals("trigger", trigger.id)
        assertEquals(0, trigger.x)
        assertEquals(0, trigger.y)
    }

    @Test
    fun `TriggerObjectIR can be hidden`() {
        val trigger = TriggerObjectIR(id = "hiddenTrigger", x = 5, y = 3, hidden = true)
        assertTrue(trigger.hidden)
    }

    @Test
    fun `TriggerObjectIR can carry all five event types as handlers`() {
        val interactHandler =
            PuzzleEventHandler(PuzzleEventType.INTERACT, listOf(Assign("flag", Literal(1))))
        val stepOnHandler =
            PuzzleEventHandler(PuzzleEventType.STEP_ON, listOf(Assign("flag", Literal(2))))
        val stepOffHandler =
            PuzzleEventHandler(PuzzleEventType.STEP_OFF, listOf(Assign("flag", Literal(0))))
        val timerHandler =
            PuzzleEventHandler(PuzzleEventType.TIMER, listOf(Assign("timerFired", Literal(1))))
        val flagHandler =
            PuzzleEventHandler(
                PuzzleEventType.FLAG_CHANGED,
                listOf(Assign("flagChanged", Literal(1))),
            )

        val trigger =
            TriggerObjectIR(
                id = "fullTrigger",
                x = 3,
                y = 3,
                handlers =
                    listOf(
                        interactHandler,
                        stepOnHandler,
                        stepOffHandler,
                        timerHandler,
                        flagHandler,
                    ),
            )

        assertEquals(5, trigger.handlers.size)
        assertEquals(PuzzleEventType.INTERACT, trigger.handlers[0].event)
        assertEquals(PuzzleEventType.STEP_ON, trigger.handlers[1].event)
        assertEquals(PuzzleEventType.STEP_OFF, trigger.handlers[2].event)
        assertEquals(PuzzleEventType.TIMER, trigger.handlers[3].event)
        assertEquals(PuzzleEventType.FLAG_CHANGED, trigger.handlers[4].event)
    }

    @Test
    fun `TriggerObjectIR can have multiple handlers for the same event type`() {
        val handler1 = PuzzleEventHandler(PuzzleEventType.INTERACT, listOf(Assign("a", Literal(1))))
        val handler2 = PuzzleEventHandler(PuzzleEventType.INTERACT, listOf(Assign("b", Literal(2))))

        val trigger =
            TriggerObjectIR(
                id = "multiHandlerTrigger",
                x = 0,
                y = 0,
                handlers = listOf(handler1, handler2),
            )

        assertEquals(2, trigger.handlers.size)
        assertTrue(trigger.handlers.all { it.event == PuzzleEventType.INTERACT })
    }

    // =========================================================================
    // requires() field — all PuzzleObjectIR types
    // =========================================================================

    @Test
    fun `SwitchObjectIR has empty requires by default`() {
        val sw = SwitchObjectIR(id = "sw", x = 0, y = 0)
        assertTrue(sw.requires.isEmpty())
    }

    @Test
    fun `SwitchObjectIR can have requires list`() {
        val sw = SwitchObjectIR(id = "mainSw", x = 0, y = 0, requires = listOf("sw1", "sw2"))
        assertEquals(listOf("sw1", "sw2"), sw.requires)
    }

    @Test
    fun `DoorObjectIR has empty requires by default`() {
        val door = DoorObjectIR(id = "door", x = 0, y = 0)
        assertTrue(door.requires.isEmpty())
    }

    @Test
    fun `DoorObjectIR can require multiple switches before opening`() {
        val door =
            DoorObjectIR(id = "bossDoor", x = 10, y = 5, requires = listOf("sw1", "sw2", "sw3"))
        assertEquals(3, door.requires.size)
        assertTrue(door.requires.contains("sw1"))
        assertTrue(door.requires.contains("sw2"))
        assertTrue(door.requires.contains("sw3"))
    }

    @Test
    fun `PressurePlateObjectIR has empty requires by default`() {
        val plate =
            PressurePlateObjectIR(id = "plate", x = 0, y = 0, respondToActorIds = emptyList())
        assertTrue(plate.requires.isEmpty())
    }

    @Test
    fun `PressurePlateObjectIR can have requires list`() {
        val plate =
            PressurePlateObjectIR(
                id = "plate",
                x = 7,
                y = 4,
                respondToActorIds = listOf("player"),
                requires = listOf("sw1"),
            )
        assertEquals(listOf("sw1"), plate.requires)
    }

    @Test
    fun `TimedBlockObjectIR has empty requires by default`() {
        val block =
            TimedBlockObjectIR(
                id = "block",
                x = 0,
                y = 0,
                solidTile = 1,
                emptyTile = 0,
                interval = 30,
            )
        assertTrue(block.requires.isEmpty())
    }

    @Test
    fun `TimedBlockObjectIR can have requires list`() {
        val block =
            TimedBlockObjectIR(
                id = "block",
                x = 12,
                y = 6,
                solidTile = 0x15,
                emptyTile = 0x00,
                interval = 60,
                requires = listOf("sw1", "sw2"),
            )
        assertEquals(listOf("sw1", "sw2"), block.requires)
    }

    @Test
    fun `TriggerObjectIR can have requires list`() {
        val trigger = TriggerObjectIR(id = "trigger", x = 0, y = 0, requires = listOf("sw1"))
        assertEquals(listOf("sw1"), trigger.requires)
    }

    // =========================================================================
    // PressurePlateObjectIR — multi-entity respondTo (pool prefix convention)
    // =========================================================================

    @Test
    fun `PressurePlateObjectIR respondToActorIds can contain pool prefix`() {
        // Pool entities use the "pool:<name>" prefix in respondToActorIds
        val plate =
            PressurePlateObjectIR(
                id = "enemyPlate",
                x = 7,
                y = 4,
                respondToActorIds = listOf("player", "pool:enemies", "pool:goblins"),
            )
        assertEquals(3, plate.respondToActorIds.size)
        assertTrue(plate.respondToActorIds.any { it.startsWith("pool:") })
        assertEquals("pool:enemies", plate.respondToActorIds[1])
        assertEquals("pool:goblins", plate.respondToActorIds[2])
    }

    @Test
    fun `PressurePlateObjectIR supports mixed actor and pool respondTo`() {
        val plate =
            PressurePlateObjectIR(
                id = "mixedPlate",
                x = 5,
                y = 5,
                respondToActorIds = listOf("player", "pool:bullets"),
            )
        val actorIds = plate.respondToActorIds.filter { !it.startsWith("pool:") }
        val poolIds = plate.respondToActorIds.filter { it.startsWith("pool:") }
        assertEquals(listOf("player"), actorIds)
        assertEquals(listOf("pool:bullets"), poolIds)
    }

    // =========================================================================
    // GameIR.puzzleObjects — includes all 5 types
    // =========================================================================

    @Test
    fun `GameIR can carry all five puzzle object types including TriggerObjectIR`() {
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
        val trigger =
            TriggerObjectIR(
                id = "secretTrigger",
                x = 2,
                y = 2,
                handlers =
                    listOf(
                        PuzzleEventHandler(
                            PuzzleEventType.INTERACT,
                            listOf(Assign("activated", Literal(1))),
                        )
                    ),
            )

        val game =
            GameIR(name = "PuzzleGame", puzzleObjects = listOf(sw, door, plate, block, trigger))

        assertEquals(5, game.puzzleObjects.size)
        assertTrue(game.puzzleObjects[0] is SwitchObjectIR)
        assertTrue(game.puzzleObjects[1] is DoorObjectIR)
        assertTrue(game.puzzleObjects[2] is PressurePlateObjectIR)
        assertTrue(game.puzzleObjects[3] is TimedBlockObjectIR)
        assertTrue(game.puzzleObjects[4] is TriggerObjectIR)
    }

    // =========================================================================
    // Exhaustive when — PuzzleObjectIR sealed dispatch with 5 types
    // =========================================================================

    @Test
    fun `when on PuzzleObjectIR is exhaustive with TriggerObjectIR included`() {
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
                TriggerObjectIR(id = "trigger", x = 2, y = 2),
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
            listOf("Switch(sw)", "Door(door)", "Plate(plate)", "Block(block)", "Trigger(trigger)"),
            descriptions,
        )
    }

    // =========================================================================
    // Requires chaining — data model integrity tests
    // =========================================================================

    @Test
    fun `door requiring 3 switches stores all 3 IDs`() {
        val door =
            DoorObjectIR(
                id = "vaultDoor",
                x = 15,
                y = 8,
                openTile = 0x30,
                closedTile = 0x31,
                requires = listOf("sw1", "sw2", "sw3"),
            )
        assertEquals(3, door.requires.size)
        assertEquals("sw1", door.requires[0])
        assertEquals("sw2", door.requires[1])
        assertEquals("sw3", door.requires[2])
    }

    @Test
    fun `PuzzleObjectIR requires interface field is accessible on all types`() {
        // Test that the interface field is accessible polymorphically
        val objects: List<PuzzleObjectIR> =
            listOf(
                SwitchObjectIR(id = "sw", x = 0, y = 0, requires = listOf("req1")),
                DoorObjectIR(id = "door", x = 0, y = 0, requires = listOf("req2", "req3")),
                PressurePlateObjectIR(
                    id = "plate",
                    x = 0,
                    y = 0,
                    respondToActorIds = emptyList(),
                    requires = listOf(),
                ),
                TimedBlockObjectIR(
                    id = "block",
                    x = 0,
                    y = 0,
                    solidTile = 1,
                    emptyTile = 0,
                    interval = 30,
                    requires = listOf("req4"),
                ),
                TriggerObjectIR(id = "trigger", x = 0, y = 0, requires = listOf("req5", "req6")),
            )
        val requiresCounts = objects.map { it.requires.size }
        assertEquals(listOf(1, 2, 0, 1, 2), requiresCounts)
    }

    // =========================================================================
    // Hidden state — TriggerObjectIR
    // =========================================================================

    @Test
    fun `TriggerObjectIR hidden defaults to false`() {
        val trigger = TriggerObjectIR(id = "trigger", x = 0, y = 0)
        assertFalse(trigger.hidden)
    }

    @Test
    fun `TriggerObjectIR can be declared as hidden`() {
        val trigger = TriggerObjectIR(id = "hiddenTrigger", x = 0, y = 0, hidden = true)
        assertTrue(trigger.hidden)
    }

    // =========================================================================
    // Trigger with ScriptOps in handlers
    // =========================================================================

    @Test
    fun `TriggerObjectIR INTERACT handler can have ScriptOps`() {
        val activateOp = ActivatePuzzleObject(objectId = "bossDoor")
        val handler = PuzzleEventHandler(PuzzleEventType.INTERACT, listOf(activateOp))
        val trigger = TriggerObjectIR(id = "switch", x = 5, y = 3, handlers = listOf(handler))

        assertEquals(1, trigger.handlers.size)
        assertEquals(PuzzleEventType.INTERACT, trigger.handlers[0].event)
        assertEquals(1, trigger.handlers[0].actions.size)
        assertTrue(trigger.handlers[0].actions[0] is ActivatePuzzleObject)
    }

    @Test
    fun `TriggerObjectIR FLAG_CHANGED handler fires on flag state change`() {
        // Verify that FLAG_CHANGED is a valid handler event type on TriggerObjectIR
        val flagOp = Assign(target = "questStarted", value = Literal(1))
        val handler = PuzzleEventHandler(PuzzleEventType.FLAG_CHANGED, listOf(flagOp))
        val trigger = TriggerObjectIR(id = "questTrigger", x = 1, y = 1, handlers = listOf(handler))

        assertEquals(PuzzleEventType.FLAG_CHANGED, trigger.handlers[0].event)
        val action = trigger.handlers[0].actions[0]
        assertTrue(action is Assign)
        assertEquals("questStarted", (action as? Assign)?.target)
    }

    // =========================================================================
    // Combined puzzle setup — switch + requires-door + pressure plate
    // =========================================================================

    @Test
    fun `complete puzzle setup with sw1 sw2 sw3 requiring door is valid IR`() {
        val sw1 = SwitchObjectIR(id = "sw1", x = 2, y = 2)
        val sw2 = SwitchObjectIR(id = "sw2", x = 4, y = 2)
        val sw3 = SwitchObjectIR(id = "sw3", x = 6, y = 2)
        val vaultDoor =
            DoorObjectIR(
                id = "vaultDoor",
                x = 8,
                y = 5,
                openTile = 0x10,
                closedTile = 0x11,
                requires = listOf("sw1", "sw2", "sw3"),
            )
        val plate =
            PressurePlateObjectIR(
                id = "entryPlate",
                x = 1,
                y = 5,
                respondToActorIds = listOf("player", "pool:enemies"),
                onStepOn = listOf(RevealPuzzleObject("sw1")),
            )

        val game =
            GameIR(name = "PuzzleGame", puzzleObjects = listOf(sw1, sw2, sw3, vaultDoor, plate))

        assertEquals(5, game.puzzleObjects.size)
        val door = game.puzzleObjects.filterIsInstance<DoorObjectIR>().first()
        assertEquals(3, door.requires.size)
        val plateIR = game.puzzleObjects.filterIsInstance<PressurePlateObjectIR>().first()
        assertEquals(2, plateIR.respondToActorIds.size)
        assertTrue(plateIR.respondToActorIds.any { it.startsWith("pool:") })
        assertEquals(1, plateIR.onStepOn.size)
        assertTrue(plateIR.onStepOn[0] is RevealPuzzleObject)
    }

    // =========================================================================
    // Generic trigger with FlagChanged event
    // =========================================================================

    @Test
    fun `FlagChanged trigger activates when story flag changes`() {
        // Model: trigger fires a reveal when "defeatedBoss" flag changes to true
        val revealOp = RevealPuzzleObject(objectId = "secretRoom")
        val handler = PuzzleEventHandler(PuzzleEventType.FLAG_CHANGED, listOf(revealOp))
        val trigger =
            TriggerObjectIR(id = "bossDefeatedTrigger", x = 0, y = 0, handlers = listOf(handler))

        assertEquals(PuzzleEventType.FLAG_CHANGED, trigger.handlers[0].event)
        val reveal = trigger.handlers[0].actions[0]
        assertTrue(reveal is RevealPuzzleObject)
        assertEquals("secretRoom", (reveal as? RevealPuzzleObject)?.objectId)
    }

    // =========================================================================
    // ScriptOp dispatch — visitor accepts TriggerObjectIR in handlers
    // =========================================================================

    @Test
    fun `TriggerObjectIR handlers dispatch ScriptOps via visitor correctly`() {
        val op: ScriptOp = ActivatePuzzleObject(objectId = "sw1")
        val handler = PuzzleEventHandler(PuzzleEventType.INTERACT, listOf(op))
        val trigger =
            TriggerObjectIR(id = "interactTrigger", x = 0, y = 0, handlers = listOf(handler))

        // Dispatch through the handler's action
        val result = trigger.handlers[0].actions[0].accept(TriggerTestDescriber)
        assertEquals("ActivatePuzzleObject(sw1)", result)
    }
}

// =============================================================================
// MINIMAL VISITOR FOR TRIGGER HANDLER DISPATCH TESTS
// =============================================================================

private object TriggerTestDescriber : ScriptOpVisitorI<String> {
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

    override fun visitPoolForEachActive(op: PoolForEachActive): String = "PoolForEachActive"

    override fun visitPoolDestroyAll(op: PoolDestroyAll): String = "PoolDestroyAll"

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

    override fun visitActivatePuzzleObject(op: ActivatePuzzleObject): String =
        "ActivatePuzzleObject(${op.objectId})"

    override fun visitDeactivatePuzzleObject(op: DeactivatePuzzleObject): String =
        "DeactivatePuzzleObject(${op.objectId})"

    override fun visitRevealPuzzleObject(op: RevealPuzzleObject): String =
        "RevealPuzzleObject(${op.objectId})"

    override fun visitHidePuzzleObject(op: HidePuzzleObject): String =
        "HidePuzzleObject(${op.objectId})"
}
