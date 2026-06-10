/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ActivatePuzzleObject
import io.github.gbkt.core.ir.DeactivatePuzzleObject
import io.github.gbkt.core.ir.DoorObjectIR
import io.github.gbkt.core.ir.HidePuzzleObject
import io.github.gbkt.core.ir.PressurePlateObjectIR
import io.github.gbkt.core.ir.PuzzleEventHandler
import io.github.gbkt.core.ir.PuzzleEventType
import io.github.gbkt.core.ir.PuzzleObjectIR
import io.github.gbkt.core.ir.RevealPuzzleObject
import io.github.gbkt.core.ir.SwitchObjectIR
import io.github.gbkt.core.ir.TimedBlockObjectIR
import io.github.gbkt.core.ir.TriggerObjectIR
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// PUZZLE OBJECT REFERENCE
// =============================================================================

/**
 * Type-safe reference to a registered puzzle object.
 *
 * Returned by the puzzle delegate when a property like `val sw1 by switch(...)` is initialized.
 * Passed to script ops like `openDoor(bossDoor)` or `activate(sw1)` — no magic strings. Also passed
 * to `requires()` to build type-safe dependency chains.
 */
data class PuzzleObjectRef(val objectId: String)

// =============================================================================
// SWITCH BUILDER
// =============================================================================

/**
 * Builder for a switch puzzle object.
 *
 * Usage:
 * ```kotlin
 * val sw1 by switch(x = 5, y = 3) {
 *     onActivate { openDoor(bossDoor) }
 *     onDeactivate { closeDoor(bossDoor) }
 * }
 * ```
 */
@GbktDsl
class SwitchBuilder(val x: Int, val y: Int) {
    internal var hiddenFlag: Boolean = false
    internal var onActivateOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal var onDeactivateOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal val requiresIds: MutableList<String> = mutableListOf()

    /** Makes this switch invisible until revealed via `reveal(ref)`. */
    fun hidden(value: Boolean = true) {
        hiddenFlag = value
    }

    /** Script ops to run when this switch is activated. */
    fun onActivate(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onActivateOps = builder.build()
    }

    /** Script ops to run when this switch is deactivated. */
    fun onDeactivate(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onDeactivateOps = builder.build()
    }

    /**
     * Declares that all given puzzle objects must be active before this switch responds.
     *
     * Uses type-safe [PuzzleObjectRef] — no magic strings.
     */
    fun requires(vararg objects: PuzzleObjectRef) {
        for (obj in objects) requiresIds.add(obj.objectId)
    }
}

/**
 * Property delegate for declaring a switch puzzle object with an ID inferred from the property
 * name.
 *
 * Usage:
 * ```kotlin
 * val sw1 by switch(x = 5, y = 3) { onActivate { openDoor(bossDoor) } }
 * ```
 */
class SwitchDelegate(val x: Int, val y: Int, val config: SwitchBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, PuzzleObjectRef> {
    private var ref: PuzzleObjectRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PuzzleObjectRef> {
        val id = property.name
        val builder = SwitchBuilder(x, y)
        builder.config()
        val ir =
            SwitchObjectIR(
                id = id,
                x = x,
                y = y,
                hidden = builder.hiddenFlag,
                onActivate = builder.onActivateOps,
                onDeactivate = builder.onDeactivateOps,
                requires = builder.requiresIds.toList(),
            )
        val gameBuilder =
            GameBuilderContext.current ?: error("switch() must be called inside a game {} block")
        gameBuilder.registerPuzzleObject(ir)
        ref = PuzzleObjectRef(id)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): PuzzleObjectRef =
        ref ?: error("SwitchDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// DOOR BUILDER
// =============================================================================

/**
 * Builder for a door puzzle object.
 *
 * Usage:
 * ```kotlin
 * val bossDoor by door(x = 10, y = 5) {
 *     openTile(0x20)
 *     closedTile(0x21)
 *     onOpen { playSound(doorOpen) }
 * }
 * ```
 */
@GbktDsl
class DoorBuilder(val x: Int, val y: Int) {
    internal var hiddenFlag: Boolean = false
    internal var openTileId: Int = 0
    internal var closedTileId: Int = 0
    internal var onOpenOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal var onCloseOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal val requiresIds: MutableList<String> = mutableListOf()

    /** Makes this door invisible until revealed via `reveal(ref)`. */
    fun hidden(value: Boolean = true) {
        hiddenFlag = value
    }

    /** Tile index to display when the door is open. */
    fun openTile(tileId: Int) {
        openTileId = tileId
    }

    /** Tile index to display when the door is closed. */
    fun closedTile(tileId: Int) {
        closedTileId = tileId
    }

    /** Script ops to run when this door opens. */
    fun onOpen(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onOpenOps = builder.build()
    }

    /** Script ops to run when this door closes. */
    fun onClose(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onCloseOps = builder.build()
    }

    /**
     * Declares that all given puzzle objects must be active before this door can be opened.
     *
     * Uses type-safe [PuzzleObjectRef] — no magic strings.
     */
    fun requires(vararg objects: PuzzleObjectRef) {
        for (obj in objects) requiresIds.add(obj.objectId)
    }
}

/**
 * Property delegate for declaring a door puzzle object with an ID inferred from the property name.
 *
 * Usage:
 * ```kotlin
 * val bossDoor by door(x = 10, y = 5) { openTile(0x20); closedTile(0x21) }
 * ```
 */
class DoorDelegate(val x: Int, val y: Int, val config: DoorBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, PuzzleObjectRef> {
    private var ref: PuzzleObjectRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PuzzleObjectRef> {
        val id = property.name
        val builder = DoorBuilder(x, y)
        builder.config()
        val ir =
            DoorObjectIR(
                id = id,
                x = x,
                y = y,
                hidden = builder.hiddenFlag,
                openTile = builder.openTileId,
                closedTile = builder.closedTileId,
                onOpen = builder.onOpenOps,
                onClose = builder.onCloseOps,
                requires = builder.requiresIds.toList(),
            )
        val gameBuilder =
            GameBuilderContext.current ?: error("door() must be called inside a game {} block")
        gameBuilder.registerPuzzleObject(ir)
        ref = PuzzleObjectRef(id)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): PuzzleObjectRef =
        ref ?: error("DoorDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// PRESSURE PLATE BUILDER
// =============================================================================

/**
 * Builder for a pressure plate puzzle object.
 *
 * Usage:
 * ```kotlin
 * val entryPlate by pressurePlate(x = 7, y = 4) {
 *     respondTo(player)
 *     onStepOn { openDoor(exitDoor) }
 *     onStepOff { closeDoor(exitDoor) }
 * }
 * ```
 */
@GbktDsl
class PressurePlateBuilder(val x: Int, val y: Int) {
    internal var hiddenFlag: Boolean = false
    internal val respondToIds: MutableList<String> = mutableListOf()
    internal var onStepOnOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal var onStepOffOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    internal val requiresIds: MutableList<String> = mutableListOf()

    /** Makes this pressure plate invisible until revealed via `reveal(ref)`. */
    fun hidden(value: Boolean = true) {
        hiddenFlag = value
    }

    /**
     * Configures which actors activate this pressure plate.
     *
     * Accepts [ActorRef] instances — the actor IDs are stored in [respondToIds].
     */
    fun respondTo(vararg actors: ActorRef) {
        for (actor in actors) {
            respondToIds.add(actor.id)
        }
    }

    /**
     * Configures which pool entities activate this pressure plate.
     *
     * Accepts [PoolRef] instances — pool entities at the plate position trigger the plate. The pool
     * name is used as a group reference in the generated check function.
     */
    fun respondToPool(vararg pools: PoolRef) {
        for (pool in pools) {
            respondToIds.add("pool:${pool.name}")
        }
    }

    /** Script ops to run when an actor steps onto this plate. */
    fun onStepOn(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onStepOnOps = builder.build()
    }

    /** Script ops to run when all actors step off this plate. */
    fun onStepOff(body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        onStepOffOps = builder.build()
    }

    /**
     * Declares that all given puzzle objects must be active before step-on events fire.
     *
     * Uses type-safe [PuzzleObjectRef] — no magic strings.
     */
    fun requires(vararg objects: PuzzleObjectRef) {
        for (obj in objects) requiresIds.add(obj.objectId)
    }
}

/**
 * Property delegate for declaring a pressure plate puzzle object with an ID inferred from the
 * property name.
 */
class PressurePlateDelegate(val x: Int, val y: Int, val config: PressurePlateBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, PuzzleObjectRef> {
    private var ref: PuzzleObjectRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PuzzleObjectRef> {
        val id = property.name
        val builder = PressurePlateBuilder(x, y)
        builder.config()
        val ir =
            PressurePlateObjectIR(
                id = id,
                x = x,
                y = y,
                hidden = builder.hiddenFlag,
                respondToActorIds = builder.respondToIds.toList(),
                onStepOn = builder.onStepOnOps,
                onStepOff = builder.onStepOffOps,
                requires = builder.requiresIds.toList(),
            )
        val gameBuilder =
            GameBuilderContext.current
                ?: error("pressurePlate() must be called inside a game {} block")
        gameBuilder.registerPuzzleObject(ir)
        ref = PuzzleObjectRef(id)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): PuzzleObjectRef =
        ref ?: error("PressurePlateDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// TIMED BLOCK BUILDER
// =============================================================================

/**
 * Builder for a timed block puzzle object.
 *
 * Usage:
 * ```kotlin
 * val timerBlock by timedBlock(x = 12, y = 6) {
 *     solidTile(0x15)
 *     emptyTile(0x00)
 *     interval(60)
 * }
 * ```
 */
@GbktDsl
class TimedBlockBuilder(val x: Int, val y: Int) {
    internal var hiddenFlag: Boolean = false
    internal var solidTileId: Int = 0
    internal var emptyTileId: Int = 0
    internal var intervalFrames: Int = 60
    internal val requiresIds: MutableList<String> = mutableListOf()

    /** Makes this timed block invisible until revealed via `reveal(ref)`. */
    fun hidden(value: Boolean = true) {
        hiddenFlag = value
    }

    /** Tile index for the solid (blocking) state. */
    fun solidTile(tileId: Int) {
        solidTileId = tileId
    }

    /** Tile index for the empty (passable) state. */
    fun emptyTile(tileId: Int) {
        emptyTileId = tileId
    }

    /** Number of frames between tile state toggles. */
    fun interval(frames: Int) {
        intervalFrames = frames
    }

    /**
     * Declares that all given puzzle objects must be active before this block starts toggling.
     *
     * Uses type-safe [PuzzleObjectRef] — no magic strings.
     */
    fun requires(vararg objects: PuzzleObjectRef) {
        for (obj in objects) requiresIds.add(obj.objectId)
    }
}

/**
 * Property delegate for declaring a timed block puzzle object with an ID inferred from the property
 * name.
 */
class TimedBlockDelegate(val x: Int, val y: Int, val config: TimedBlockBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, PuzzleObjectRef> {
    private var ref: PuzzleObjectRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PuzzleObjectRef> {
        val id = property.name
        val builder = TimedBlockBuilder(x, y)
        builder.config()
        val ir =
            TimedBlockObjectIR(
                id = id,
                x = x,
                y = y,
                hidden = builder.hiddenFlag,
                solidTile = builder.solidTileId,
                emptyTile = builder.emptyTileId,
                interval = builder.intervalFrames,
                requires = builder.requiresIds.toList(),
            )
        val gameBuilder =
            GameBuilderContext.current
                ?: error("timedBlock() must be called inside a game {} block")
        gameBuilder.registerPuzzleObject(ir)
        ref = PuzzleObjectRef(id)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): PuzzleObjectRef =
        ref ?: error("TimedBlockDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// TRIGGER BUILDER (GENERIC)
// =============================================================================

/**
 * Builder for a generic trigger puzzle object.
 *
 * Unlike switch/door/plate/block, the trigger has no built-in behavior — all logic is in handlers.
 * Use `on(eventType) { ... }` to register per-event callbacks.
 *
 * Usage:
 * ```kotlin
 * val secretTrigger by trigger(x = 5, y = 3) {
 *     on(PuzzleEventType.INTERACT) { activate(hiddenDoor) }
 *     on(PuzzleEventType.FLAG_CHANGED) { reveal(reward) }
 * }
 * ```
 */
@GbktDsl
class TriggerBuilder(val x: Int, val y: Int) {
    internal var hiddenFlag: Boolean = false
    internal val handlers: MutableList<PuzzleEventHandler> = mutableListOf()
    internal val requiresIds: MutableList<String> = mutableListOf()

    /** Makes this trigger invisible until revealed via `reveal(ref)`. */
    fun hidden(value: Boolean = true) {
        hiddenFlag = value
    }

    /**
     * Registers a callback for the given [event] type.
     *
     * Multiple handlers for the same event type are supported; all will fire in order.
     */
    fun on(event: PuzzleEventType, body: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.body() }
        handlers.add(PuzzleEventHandler(event, builder.build()))
    }

    /**
     * Declares that all given puzzle objects must be active before events fire on this trigger.
     *
     * Uses type-safe [PuzzleObjectRef] — no magic strings.
     */
    fun requires(vararg objects: PuzzleObjectRef) {
        for (obj in objects) requiresIds.add(obj.objectId)
    }
}

/**
 * Property delegate for declaring a generic trigger puzzle object with an ID inferred from the
 * property name.
 *
 * Usage:
 * ```kotlin
 * val secretTrigger by trigger(x = 5, y = 3) {
 *     on(PuzzleEventType.INTERACT) { activate(hiddenDoor) }
 * }
 * ```
 */
class TriggerDelegate(val x: Int, val y: Int, val config: TriggerBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, PuzzleObjectRef> {
    private var ref: PuzzleObjectRef? = null

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PuzzleObjectRef> {
        val id = property.name
        val builder = TriggerBuilder(x, y)
        builder.config()
        val ir =
            TriggerObjectIR(
                id = id,
                x = x,
                y = y,
                hidden = builder.hiddenFlag,
                handlers = builder.handlers.toList(),
                requires = builder.requiresIds.toList(),
            )
        val gameBuilder =
            GameBuilderContext.current ?: error("trigger() must be called inside a game {} block")
        gameBuilder.registerPuzzleObject(ir)
        ref = PuzzleObjectRef(id)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): PuzzleObjectRef =
        ref ?: error("TriggerDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// TOP-LEVEL DSL FUNCTIONS ON GameBuilder
// =============================================================================

/**
 * Declares a switch puzzle object with an ID inferred from the property name.
 *
 * Usage: `val sw1 by switch(x = 5, y = 3) { onActivate { openDoor(bossDoor) } }`
 */
fun GameBuilder.switch(x: Int, y: Int, config: SwitchBuilder.() -> Unit = {}): SwitchDelegate =
    SwitchDelegate(x, y, config)

/**
 * Declares a door puzzle object with an ID inferred from the property name.
 *
 * Usage: `val bossDoor by door(x = 10, y = 5) { openTile(0x20); closedTile(0x21) }`
 */
fun GameBuilder.door(x: Int, y: Int, config: DoorBuilder.() -> Unit = {}): DoorDelegate =
    DoorDelegate(x, y, config)

/**
 * Declares a pressure plate puzzle object with an ID inferred from the property name.
 *
 * Usage:
 * ```kotlin
 * val plate by pressurePlate(x = 7, y = 4) {
 *     respondTo(player)
 *     onStepOn { openDoor(exitDoor) }
 * }
 * ```
 */
fun GameBuilder.pressurePlate(
    x: Int,
    y: Int,
    config: PressurePlateBuilder.() -> Unit = {},
): PressurePlateDelegate = PressurePlateDelegate(x, y, config)

/**
 * Declares a timed block puzzle object with an ID inferred from the property name.
 *
 * Usage:
 * ```kotlin
 * val timerBlock by timedBlock(x = 12, y = 6) {
 *     solidTile(0x15); emptyTile(0x00); interval(60)
 * }
 * ```
 */
fun GameBuilder.timedBlock(
    x: Int,
    y: Int,
    config: TimedBlockBuilder.() -> Unit = {},
): TimedBlockDelegate = TimedBlockDelegate(x, y, config)

/**
 * Declares a generic trigger puzzle object with an ID inferred from the property name.
 *
 * Usage:
 * ```kotlin
 * val secretTrigger by trigger(x = 5, y = 3) {
 *     on(PuzzleEventType.INTERACT) { activate(hiddenDoor) }
 *     on(PuzzleEventType.FLAG_CHANGED) { reveal(reward) }
 * }
 * ```
 */
fun GameBuilder.trigger(x: Int, y: Int, config: TriggerBuilder.() -> Unit = {}): TriggerDelegate =
    TriggerDelegate(x, y, config)

// =============================================================================
// SCRIPT BUILDER ACTIONS FOR PUZZLE OBJECTS
// =============================================================================

/**
 * Opens a door by emitting [ActivatePuzzleObject] for the given [PuzzleObjectRef].
 *
 * Sets the door state to open, swaps the tile to the door's configured openTile, and runs the
 * door's onOpen callback.
 */
fun ScriptBuilder.openDoor(door: PuzzleObjectRef) {
    emit(ActivatePuzzleObject(door.objectId, sourceLocation = captureV2Location()))
}

/**
 * Closes a door by emitting [DeactivatePuzzleObject] for the given [PuzzleObjectRef].
 *
 * Sets the door state to closed, swaps the tile to the door's configured closedTile, and runs the
 * door's onClose callback.
 */
fun ScriptBuilder.closeDoor(door: PuzzleObjectRef) {
    emit(DeactivatePuzzleObject(door.objectId, sourceLocation = captureV2Location()))
}

/**
 * Activates a puzzle object (opens doors, toggles switches on).
 *
 * Emits [ActivatePuzzleObject] for the given type-safe [PuzzleObjectRef].
 */
fun ScriptBuilder.activate(obj: PuzzleObjectRef) {
    emit(ActivatePuzzleObject(obj.objectId, sourceLocation = captureV2Location()))
}

/**
 * Deactivates a puzzle object (closes doors, toggles switches off).
 *
 * Emits [DeactivatePuzzleObject] for the given type-safe [PuzzleObjectRef].
 */
fun ScriptBuilder.deactivate(obj: PuzzleObjectRef) {
    emit(DeactivatePuzzleObject(obj.objectId, sourceLocation = captureV2Location()))
}

/**
 * Makes a hidden puzzle object visible.
 *
 * Emits [RevealPuzzleObject] for the given type-safe [PuzzleObjectRef].
 */
fun ScriptBuilder.reveal(obj: PuzzleObjectRef) {
    emit(RevealPuzzleObject(obj.objectId, sourceLocation = captureV2Location()))
}

/**
 * Hides a puzzle object (clears its tile and sets hidden flag).
 *
 * Emits [HidePuzzleObject] for the given type-safe [PuzzleObjectRef].
 */
fun ScriptBuilder.hide(obj: PuzzleObjectRef) {
    emit(HidePuzzleObject(obj.objectId, sourceLocation = captureV2Location()))
}

// =============================================================================
// INTERNAL: PuzzleObjectIR registration
// (Added to GameBuilder via extension to avoid circular imports)
// =============================================================================

/** Registers a puzzle object IR node for inclusion in [GameIR.puzzleObjects]. */
internal fun GameBuilder.registerPuzzleObject(ir: PuzzleObjectIR) {
    puzzleObjects.add(ir)
}
