/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// SCRIPT INSTRUCTION SET
// =============================================================================

/**
 * Non-sealed instruction interface for game scripts (scene lifecycle handlers, events).
 *
 * Unsealed so that external modules (gbkt-rpg, gbkt-exploration) can define their own IR node
 * types. All dispatch is performed via the visitor pattern — callers invoke [accept] and the
 * subtype routes to the correct [ScriptOpVisitorI] method.
 *
 * Source location is optional and defaults to null for instructions that don't need sourcemap
 * tracking.
 */
interface ScriptOp {
    val sourceLocation: SourceLocation?
        get() = null

    fun <T> accept(visitor: ScriptOpVisitorI<T>): T
}

// --- State mutation ----------------------------------------------------------

/** Variable assignment: `target = value` (or compound: `target += value` etc.). */
data class Assign(
    val target: String,
    val value: Expr,
    val op: AssignOp = AssignOp.SET,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitAssign(this)
}

/** Array element assignment: `array[index] = value`. */
data class ArrayAssign(
    val array: String,
    val index: Expr,
    val value: Expr,
    val op: AssignOp = AssignOp.SET,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitArrayAssign(this)
}

// --- Control flow ------------------------------------------------------------

/** Conditional branch: if (condition) then else otherwise. */
data class IfOp(
    val condition: Expr,
    val then: List<ScriptOp>,
    val otherwise: List<ScriptOp> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitIfOp(this)
}

/** Counted loop: while (condition) { body }. */
data class WhileOp(
    val condition: Expr,
    val body: List<ScriptOp>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitWhileOp(this)
}

/** Ranged for loop: for (variable in from..to) { body }. */
data class ForOp(
    val variable: String,
    val from: Expr,
    val to: Expr,
    val body: List<ScriptOp>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitForOp(this)
}

// --- Movement ----------------------------------------------------------------

/** Teleport an actor to an absolute position. */
data class SetPosition(
    val actorId: String,
    val x: Expr,
    val y: Expr,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitSetPosition(this)
}

/** Move an actor by a relative delta. */
data class MoveBy(
    val actorId: String,
    val dx: Expr,
    val dy: Expr,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMoveBy(this)
}

// --- Navigation --------------------------------------------------------------

/** Transition to another scene by ID. */
data class NavigateTo(val sceneId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitNavigateTo(this)
}

// --- Systems -----------------------------------------------------------------

/** Fire a system event by system ID with optional arguments. */
data class TriggerSystem(
    val systemId: String,
    val args: Map<String, Expr> = emptyMap(),
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitTriggerSystem(this)
}

// --- Audio -------------------------------------------------------------------

/** Play a sound effect by ID. */
data class PlaySound(val soundId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPlaySound(this)
}

/**
 * Start playing a music track by song ID.
 *
 * Generates a `hUGE_init(&song_<songId>)` call when targeting the GBDK backend with hUGETracker
 * support. The song must be converted from a .uge file to a C symbol using hUGETracker's export.
 *
 * @param fadeInFrames Number of frames to fade in (0 = instant). Only effective when AudioMixer
 *   system is configured; otherwise falls back to instant play with a C comment warning.
 * @param resume If true, attempts to resume playback from the saved position rather than restarting
 *   from the beginning (requires AudioMixer state functions).
 */
data class MusicPlay(
    val songId: String,
    val fadeInFrames: Int = 0,
    val resume: Boolean = false,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMusicPlay(this)
}

/**
 * Stop music playback by muting all four audio channels.
 *
 * Generates hUGEDriver channel mute calls for all four Game Boy channels (CH1-CH4).
 *
 * @param fadeOutFrames Number of frames to fade out (0 = instant mute). Only effective when
 *   AudioMixer system is configured; otherwise falls back to instant mute.
 */
data class MusicStop(
    val fadeOutFrames: Int = 0,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMusicStop(this)
}

/**
 * Pause music playback.
 *
 * Suspends hUGEDriver updates without resetting the song position.
 */
data class MusicPause(override val sourceLocation: SourceLocation? = null) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMusicPause(this)
}

/**
 * Resume paused music playback.
 *
 * Re-enables hUGEDriver updates after a [MusicPause].
 */
data class MusicResume(override val sourceLocation: SourceLocation? = null) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMusicResume(this)
}

// --- Dialog and menus -------------------------------------------------------

/** Emit dialog text in a named dialog box (typewriter-style rendering on window layer). */
data class DialogSay(
    val dialogId: String,
    val segments: List<DialogSegment>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitDialogSay(this)
}

/**
 * Show a choice prompt inside a named dialog box.
 *
 * Renders the dialog box and a list of selectable options. The script op for the selected option is
 * executed after the player makes a choice.
 */
data class DialogChoice(
    val dialogId: String,
    val options: List<DialogOption>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitDialogChoice(this)
}

/** Open and run a named menu (blocks until the player dismisses or selects an item). */
data class MenuShow(val menuId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMenuShow(this)
}

/** Close a named menu programmatically. */
data class MenuHide(val menuId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMenuHide(this)
}

/** Show a named HUD panel (makes the panel and all its elements visible on the window layer). */
data class HudShow(val hudId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitHudShow(this)
}

/** Hide a named HUD panel (removes the panel from the window layer). */
data class HudHide(val hudId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitHudHide(this)
}

/**
 * Print text at a specific tile coordinate on the window layer.
 *
 * [fontMode] determines the rendering path: [FontMode.FIXED_WIDTH] calls `_win_print_at(x, y,
 * text)` (tile-based, DMG-compatible); [FontMode.VARIABLE_WIDTH] calls `_vwf_print_at(x, y, text)`
 * (variable-width font, GBC preferred).
 */
data class PrintAt(
    val x: Int,
    val y: Int,
    val text: String,
    val fontMode: FontMode = FontMode.FIXED_WIDTH,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPrintAt(this)
}

/**
 * Print text centered horizontally on a specific screen row.
 *
 * [fontMode] determines the rendering path (same as [PrintAt]).
 */
data class PrintCentered(
    val text: String,
    val row: Int,
    val fontMode: FontMode = FontMode.FIXED_WIDTH,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPrintCentered(this)
}

/**
 * Print text with the given alignment on a specific screen row.
 *
 * [alignment] controls whether text is LEFT, CENTER, or RIGHT aligned within the screen width.
 * [fontMode] determines the rendering path (same as [PrintAt]).
 */
data class PrintAligned(
    val text: String,
    val row: Int,
    val alignment: TextAlignment,
    val fontMode: FontMode = FontMode.FIXED_WIDTH,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPrintAligned(this)
}

/** Clear a rectangular region on the window layer (fills with space/blank tiles). */
data class ClearRegion(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitClearRegion(this)
}

/** Clear the entire screen (both window and background layers). */
data class ScreenClear(override val sourceLocation: SourceLocation? = null) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitScreenClear(this)
}

/** Fill the entire screen with a specified tile index. */
data class ScreenFill(val tile: Int, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitScreenFill(this)
}

// --- Palette -----------------------------------------------------------------

/**
 * Load a GBC palette into a hardware slot.
 *
 * @param paletteName C identifier for the palette data array (e.g. "hero_pal")
 * @param slot Hardware palette index (0-7)
 * @param type Whether this is a BACKGROUND or SPRITE palette
 */
data class SetPalette(
    val paletteName: String,
    val slot: Int,
    val type: PaletteType,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitSetPalette(this)
}

// --- Display -----------------------------------------------------------------

/** Print formatted text at an optional screen position. */
data class PrintOp(
    val text: String,
    val values: List<Expr> = emptyList(),
    val position: PositionDef? = null,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPrintOp(this)
}

/** Screen fade in or out over a number of frames, with optional continuation. */
data class FadeOp(
    val fadeIn: Boolean,
    val frames: Int,
    val after: List<ScriptOp> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitFadeOp(this)
}

/** Show or hide an actor's sprite. */
data class SetVisible(
    val actorId: String,
    val visible: Boolean,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitSetVisible(this)
}

// --- Entity lifecycle --------------------------------------------------------

/** Spawn (activate) an actor by ID. */
data class SpawnActor(val actorId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitSpawnActor(this)
}

/** Destroy (deactivate) an actor by ID. */
data class DestroyActor(val actorId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitDestroyActor(this)
}

/**
 * Spawn a new entity from an actor pool at the given position.
 *
 * Calls `pool_<poolId>_spawn(x, y)` — finds a free slot in the pool bitmap, sets the sprite
 * position and tile, and returns the slot index (0xFF if pool is full with SILENT_NOOP strategy).
 *
 * @param poolId ID of the actor pool to spawn from.
 * @param x X position for the spawned entity.
 * @param y Y position for the spawned entity.
 */
data class PoolSpawnActor(
    val poolId: String,
    val x: Expr,
    val y: Expr,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPoolSpawnActor(this)
}

/**
 * Destroy (deactivate) a pool slot, hiding the associated sprite.
 *
 * Calls `pool_<poolId>_destroy(slot)` — optionally runs [deathCallbackOps] before marking the slot
 * inactive and moving the sprite offscreen.
 *
 * @param poolId ID of the actor pool to return the slot to.
 * @param slotExpr Expression evaluating to the slot index to destroy.
 * @param deathCallbackOps Script ops to execute before the slot is released (optional).
 */
data class PoolDestroyActor(
    val poolId: String,
    val slotExpr: Expr,
    val deathCallbackOps: List<ScriptOp> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPoolDestroyActor(this)
}

/**
 * Iterate over all active slots in a pool, executing [body] for each active slot.
 *
 * The slot index variable is available as [slotVarName] inside the body block.
 *
 * Generates:
 * ```c
 * for (UINT8 <slotVar> = 0; <slotVar> < maxSize; <slotVar>++) {
 *     if (_pool_<id>_active[<slotVar>]) {
 *         // body
 *     }
 * }
 * ```
 *
 * @param poolId ID of the actor pool to iterate.
 * @param maxSize Maximum pool size — used as the loop upper bound in generated C.
 * @param slotVarName Name for the loop slot index variable (default: "slot").
 * @param body Script ops to execute for each active slot.
 */
data class PoolForEachActive(
    val poolId: String,
    val maxSize: Int,
    val slotVarName: String,
    val body: List<ScriptOp>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPoolForEachActive(this)
}

/**
 * Destroy all active slots in a pool simultaneously.
 *
 * Generates a for loop that zeros the active bitmap and hides all sprites:
 * ```c
 * for (UINT8 i = 0; i < maxSize; i++) {
 *     _pool_<id>_active[i] = 0;
 *     move_sprite(_pool_<id>_oam_base + i, 0, 0);
 * }
 * ```
 *
 * @param poolId ID of the actor pool to bulk-destroy.
 * @param maxSize Maximum pool size — used as the loop upper bound in generated C.
 */
data class PoolDestroyAll(
    val poolId: String,
    val maxSize: Int,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPoolDestroyAll(this)
}

/** Play a named animation on an actor. */
data class AnimateOp(
    val actorId: String,
    val animation: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitAnimateOp(this)
}

// --- Camera ------------------------------------------------------------------

/** Camera actions available via CameraOp. */
enum class CameraAction {
    FOLLOW,
    UNFOLLOW,
    SHAKE,
    MOVE_TO,
}

/** Camera control instruction. */
data class CameraOp(
    val action: CameraAction,
    val args: Map<String, Expr> = emptyMap(),
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitCameraOp(this)
}

// --- Timing ------------------------------------------------------------------

/** Pause script execution for the given number of frames. */
data class WaitFrames(val frames: Int, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitWaitFrames(this)
}

// --- Function calls ----------------------------------------------------------

/** Call a helper function by name with arguments. */
data class CallOp(
    val function: String,
    val args: List<Expr>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitCallOp(this)
}

/** Return from the current function, optionally with a value. */
data class ReturnOp(val value: Expr? = null, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitReturnOp(this)
}

// --- Math --------------------------------------------------------------------

/** Evaluate a math utility function and store the result in a named variable. */
data class MathOp(
    val result: String,
    val op: MathFunction,
    val args: List<Expr>,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitMathOp(this)
}

// --- Level binding -----------------------------------------------------------

/** Bind the current level's tileset+tilemap into VRAM (lowers to setup_current_level()). */
data class BindCurrentLevel(override val sourceLocation: SourceLocation? = null) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitBindCurrentLevel(this)
}

// --- Escape hatch ------------------------------------------------------------

/** Inject raw C code directly into the output. Use sparingly. */
data class RawOp(val code: String, override val sourceLocation: SourceLocation? = null) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitRawOp(this)
}

/** Move the text cursor to an expression-based screen position. */
data class GotoXYOp(val x: Expr, val y: Expr, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitGotoXYOp(this)
}

// --- Pathfinding -------------------------------------------------------------

/** Compute and take one A* pathfinding step from NPC toward target actor. */
data class PathfindStep(
    val npcActorId: String,
    val targetActorId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPathfindStep(this)
}

/** Advance NPC one step along its waypoint patrol route. */
data class WaypointStep(
    val npcActorId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitWaypointStep(this)
}

// --- Puzzle objects ----------------------------------------------------------

/**
 * Programmatically activate a puzzle object (opens doors, toggles switches on).
 *
 * Emitted by `openDoor(ref)` and `activate(ref)` helpers in [ScriptBuilder]. Takes a type-safe
 * [PuzzleObjectRef] — the [objectId] is the puzzle object's registered ID.
 */
data class ActivatePuzzleObject(
    val objectId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T =
        visitor.visitActivatePuzzleObject(this)
}

/**
 * Programmatically deactivate a puzzle object (closes doors, toggles switches off).
 *
 * Emitted by `closeDoor(ref)` and `deactivate(ref)` helpers in [ScriptBuilder]. Takes a type-safe
 * [PuzzleObjectRef] — the [objectId] is the puzzle object's registered ID.
 */
data class DeactivatePuzzleObject(
    val objectId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T =
        visitor.visitDeactivatePuzzleObject(this)
}

/**
 * Make a hidden puzzle object visible.
 *
 * Emitted by `reveal(ref)` helper in [ScriptBuilder]. Sets the object's hidden flag to false and
 * makes its tile visible.
 */
data class RevealPuzzleObject(
    val objectId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitRevealPuzzleObject(this)
}

/**
 * Hide a puzzle object (clear its tile and set hidden flag).
 *
 * Emitted by `hide(ref)` helper in [ScriptBuilder]. Sets the object's hidden flag to true and
 * clears its tile.
 */
data class HidePuzzleObject(
    val objectId: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitHidePuzzleObject(this)
}

// --- Animation state machine -------------------------------------------------

// --- Metasprites -------------------------------------------------------------

/**
 * Render a metasprite at its current position each frame.
 *
 * Emitted by `moveMetasprite(ref)` in [ScriptBuilder] frame blocks. The visitor (Plan 07) lowers
 * this to a `move_metasprite()` GBDK call with the flip and sub-palette attributes looked up from
 * the metasprite's runtime state variables (`_<id>_flipX`, `_<id>_flipY`, `_<id>_subPalette`).
 *
 * Resolution approach (b) from RESEARCH Open Question 3: the user explicitly emits this op in the
 * frame loop rather than having the runtime always call it, giving explicit control over render
 * ordering relative to physics/collision ops.
 *
 * @param metaspriteId The ID of the declared metasprite (matches [MetaspriteIR.id]).
 */
data class MoveMetasprite(
    val metaspriteId: String,
    /**
     * Optional name of the user-declared variable bound as this metasprite's X position (mirrored
     * from [MetaspriteIR.posXVarName] by the `moveMetasprite()` DSL helper). When `null`, the
     * visitor (Plan 05) falls back to the canonical `_posX` global for back-compat with the Phase
     * 10 port — substrate for CR-03 (per-metasprite namespacing) and WR-01 (no hardcoded
     * `_posX`/`_posY`/`_idx`/`_rot` literals in the visitor).
     */
    val posXVar: String? = null,
    /** Optional bound Y-position variable name. Same null-fallback semantics as [posXVar]. */
    val posYVar: String? = null,
    /** Optional bound frame-index variable name. Same null-fallback semantics as [posXVar]. */
    val idxVar: String? = null,
    /** Optional bound rotation-state variable name. Same null-fallback semantics as [posXVar]. */
    val rotVar: String? = null,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <R> accept(visitor: ScriptOpVisitorI<R>): R = visitor.visitMoveMetasprite(this)
}

// --- Physics -----------------------------------------------------------------

/**
 * Apply per-frame physics to an actor: acceleration, gravity accumulation, fall speed clamping, and
 * velocity application.
 *
 * Floor/bounce detection is game-specific (via runIf() collision conditions) — PhysicsStep handles
 * only the physics integration step. The actor must have a [PhysicsConfig] on its [ActorIR] for the
 * backend to have generated the velocity variables and constants.
 *
 * Generated C applies, in order:
 * 1. Acceleration: `_actorId_vx += ACCEL_X_ACTORID; _actorId_vy += ACCEL_Y_ACTORID`
 * 2. Gravity: `_actorId_vy += GRAVITY_ACTORID`
 * 3. Fall clamp: `if (_actorId_vy > MAX_FALL_ACTORID) _actorId_vy = MAX_FALL_ACTORID`
 * 4. Velocity to position: `_actorId_y += (UINT8)_actorId_vy; _actorId_x += (UINT8)_actorId_vx`
 */
data class PhysicsStep(val actorId: String, override val sourceLocation: SourceLocation? = null) :
    ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitPhysicsStep(this)
}

/**
 * Manually transition an actor's animation state machine to a named state.
 *
 * Resets the frame counter and counter to 0, and sets the state variable to the target state
 * constant. Used for manual transitions (as opposed to condition-based auto-transitions which are
 * checked every frame by the generated state machine).
 *
 * Example: `setAnimationState(player, "walk")` → `_player_anim_state = ANIM_PLAYER_WALK;
 * _player_anim_frame = 0; _player_anim_counter = 0;`
 */
data class SetAnimationState(
    val actorId: String,
    val stateName: String,
    override val sourceLocation: SourceLocation? = null,
) : ScriptOp {
    override fun <T> accept(visitor: ScriptOpVisitorI<T>): T = visitor.visitSetAnimationState(this)
}
