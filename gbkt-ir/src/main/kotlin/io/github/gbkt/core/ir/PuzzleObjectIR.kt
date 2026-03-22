/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// PUZZLE OBJECT IR
// Defines IR nodes for interactive world puzzle elements:
// switches, doors, pressure plates, timed blocks, and generic triggers.
// =============================================================================

/**
 * The type of event that can trigger a puzzle object handler.
 * - [INTERACT] — player presses action button while adjacent to the object
 * - [STEP_ON] — an entity moves onto the object's tile
 * - [STEP_OFF] — an entity moves off the object's tile
 * - [TIMER] — a periodic timer fires (used by timed blocks)
 * - [FLAG_CHANGED] — a global story flag changes value
 */
enum class PuzzleEventType {
    INTERACT,
    STEP_ON,
    STEP_OFF,
    TIMER,
    FLAG_CHANGED,
}

/**
 * A handler that fires a list of [ScriptOp] actions when a [PuzzleEventType] is triggered.
 *
 * @param event The event type that triggers this handler.
 * @param actions The script operations to execute when the event fires.
 */
data class PuzzleEventHandler(val event: PuzzleEventType, val actions: List<ScriptOp>)

// =============================================================================
// PUZZLE OBJECT SEALED INTERFACE
// =============================================================================

/**
 * Base IR node for all interactive puzzle world objects.
 *
 * All puzzle objects have:
 * - An [id] (inferred from the Kotlin property name via delegate pattern)
 * - A tile position ([x], [y]) on the background map
 * - A [hidden] flag for objects revealed by scripted events
 * - A list of [handlers] for event-driven callbacks
 * - A [requires] list: IDs of other puzzle objects that must all be active before this object
 *   responds to activation events (empty = no prerequisites)
 *
 * Sealed to ensure exhaustive `when` dispatch in codegen.
 */
sealed interface PuzzleObjectIR {
    val id: String
    val x: Int
    val y: Int
    val hidden: Boolean
    val handlers: List<PuzzleEventHandler>

    /**
     * IDs of puzzle objects that must all be active before this object responds to events.
     *
     * Empty by default (no prerequisites). When non-empty, codegen emits a guard that checks all
     * required objects before allowing activation (e.g., `if (!_switch_sw1_active || ...)
     * return;`).
     */
    val requires: List<String>
}

// =============================================================================
// SWITCH
// =============================================================================

/**
 * A toggleable switch that the player can interact with.
 *
 * Generates a per-object state variable (`_switch_{id}_active`) and an activate function
 * (`puzzle_activate_{id}`) that toggles state and runs the appropriate callback.
 *
 * @param id Unique identifier (inferred from property name).
 * @param x Tile column position on the background map.
 * @param y Tile row position on the background map.
 * @param hidden When true, the switch is invisible until revealed via [RevealPuzzleObject].
 * @param onActivate Script ops to execute when the switch is activated.
 * @param onDeactivate Script ops to execute when the switch is deactivated.
 * @param handlers Additional generic event handlers.
 * @param requires IDs of puzzle objects that must all be active before this switch responds.
 */
data class SwitchObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val hidden: Boolean = false,
    val onActivate: List<ScriptOp> = emptyList(),
    val onDeactivate: List<ScriptOp> = emptyList(),
    override val handlers: List<PuzzleEventHandler> = emptyList(),
    override val requires: List<String> = emptyList(),
) : PuzzleObjectIR

// =============================================================================
// DOOR
// =============================================================================

/**
 * A door that can be opened or closed, swapping between two tile states.
 *
 * Generates a per-object state variable (`_door_{id}_open`) and two functions:
 * - `puzzle_activate_{id}()` — opens the door, swaps tile to [openTile], runs [onOpen] callback
 * - `puzzle_deactivate_{id}()` — closes the door, swaps tile to [closedTile], runs [onClose]
 *
 * @param id Unique identifier (inferred from property name).
 * @param x Tile column position on the background map.
 * @param y Tile row position on the background map.
 * @param hidden When true, the door is invisible until revealed via [RevealPuzzleObject].
 * @param openTile Tile index to display when the door is open.
 * @param closedTile Tile index to display when the door is closed.
 * @param onOpen Script ops to execute when the door opens.
 * @param onClose Script ops to execute when the door closes.
 * @param handlers Additional generic event handlers.
 * @param requires IDs of puzzle objects that must all be active before this door can be opened.
 */
data class DoorObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val hidden: Boolean = false,
    val openTile: Int = 0,
    val closedTile: Int = 0,
    val onOpen: List<ScriptOp> = emptyList(),
    val onClose: List<ScriptOp> = emptyList(),
    override val handlers: List<PuzzleEventHandler> = emptyList(),
    override val requires: List<String> = emptyList(),
) : PuzzleObjectIR

// =============================================================================
// PRESSURE PLATE
// =============================================================================

/**
 * A pressure plate that activates when one of the configured actors steps onto its tile.
 *
 * Generates a per-object state variable (`_plate_{id}_pressed`) and a check function
 * (`puzzle_check_plate_{id}()`) that tests actor positions against the plate coordinates, running
 * [onStepOn]/[onStepOff] callbacks on state transitions.
 *
 * @param id Unique identifier (inferred from property name).
 * @param x Tile column position on the background map.
 * @param y Tile row position on the background map.
 * @param hidden When true, the plate is invisible until revealed via [RevealPuzzleObject].
 * @param respondToActorIds IDs of actors that can activate this plate.
 * @param onStepOn Script ops to execute when an actor steps onto the plate.
 * @param onStepOff Script ops to execute when all actors step off the plate.
 * @param handlers Additional generic event handlers.
 * @param requires IDs of puzzle objects that must all be active before step-on events fire.
 */
data class PressurePlateObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val hidden: Boolean = false,
    val respondToActorIds: List<String>,
    val onStepOn: List<ScriptOp> = emptyList(),
    val onStepOff: List<ScriptOp> = emptyList(),
    override val handlers: List<PuzzleEventHandler> = emptyList(),
    override val requires: List<String> = emptyList(),
) : PuzzleObjectIR

// =============================================================================
// TIMED BLOCK
// =============================================================================

/**
 * A block that alternates between solid and empty tile states on a configurable interval.
 *
 * Generates a per-object timer variable (`_timedblock_{id}_timer`) and an update function
 * (`puzzle_update_timedblock_{id}()`) that increments the timer and swaps the tile when the
 * [interval] is reached.
 *
 * @param id Unique identifier (inferred from property name).
 * @param x Tile column position on the background map.
 * @param y Tile row position on the background map.
 * @param hidden When true, the block is invisible until revealed via [RevealPuzzleObject].
 * @param solidTile Tile index for the solid (passable-blocking) state.
 * @param emptyTile Tile index for the empty (passable) state.
 * @param interval Number of frames between tile swaps.
 * @param handlers Additional generic event handlers.
 * @param requires IDs of puzzle objects that must all be active before this block starts toggling.
 */
data class TimedBlockObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val hidden: Boolean = false,
    val solidTile: Int,
    val emptyTile: Int,
    val interval: Int,
    override val handlers: List<PuzzleEventHandler> = emptyList(),
    override val requires: List<String> = emptyList(),
) : PuzzleObjectIR

// =============================================================================
// TRIGGER (GENERIC)
// =============================================================================

/**
 * A generic trigger object with no built-in behavior — all logic is in [handlers].
 *
 * Useful for custom puzzle interactions beyond the specific switch/door/plate/block types.
 * Generates `puzzle_trigger_{id}_fire(UINT8 event)` with switch-case dispatch to per-event handler
 * callbacks. All five [PuzzleEventType] values are supported.
 *
 * @param id Unique identifier (inferred from property name).
 * @param x Tile column position on the background map.
 * @param y Tile row position on the background map.
 * @param hidden When true, the trigger is invisible until revealed via [RevealPuzzleObject].
 * @param handlers Event handler callbacks — fired when the trigger receives an event.
 * @param requires IDs of puzzle objects that must all be active before events fire.
 */
data class TriggerObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val hidden: Boolean = false,
    override val handlers: List<PuzzleEventHandler> = emptyList(),
    override val requires: List<String> = emptyList(),
) : PuzzleObjectIR
