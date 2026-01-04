/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// SYSTEM IR NODES - Domain-specific statements and expressions
// =============================================================================

// =============================================================================
// PALETTE IR NODES (GBC color support)
// =============================================================================

/** Apply a palette to a slot at runtime */
data class IRPaletteApply(val paletteName: String, val slot: Int, val type: PaletteType) :
    IRStatement

/** Set a single color in a palette at runtime */
data class IRPaletteSetColor(
    val paletteName: String,
    val colorIndex: Int,
    val color: GBCColor,
    val type: PaletteType
) : IRStatement

/** Fade palette toward target colors (progress: 0-255) */
data class IRPaletteFade(
    val paletteName: String,
    val targetColors: List<GBCColor>,
    val progress: IRExpression,
    val type: PaletteType
) : IRStatement

/** Set sprite's palette attribute (which of 8 palettes to use) */
data class IRSpriteSetPalette(val spriteSlot: Int, val paletteIndex: Int) : IRStatement

/** Flash effect - temporarily override palette then restore */
data class IRPaletteFlash(
    val paletteName: String,
    val flashColor: GBCColor,
    val type: PaletteType
) : IRStatement

// =============================================================================
// TEXT RENDERING IR NODES
// =============================================================================

/** Clear the screen */
data object IRClearScreen : IRStatement

/** Show or hide sprites */
data class IRShowSprites(val show: Boolean) : IRStatement

/** Show or hide background */
data class IRShowBackground(val show: Boolean) : IRStatement

/** Print text at position */
data class IRPrintAt(val x: Int, val y: Int, val parts: List<TextPart>) : IRStatement

/** Parts of a text string - either literal text or variable reference */
sealed class TextPart {
    data class Literal(val text: String) : TextPart()

    data class Variable(val expr: IRExpression, val format: String = "%u") : TextPart()
}

// =============================================================================
// ANIMATION IR NODES
// =============================================================================

/** Play a sprite animation */
data class IRAnimationPlay(
    val spriteName: String,
    val animationName: String,
    val loop: Boolean = true,
    val speed: Int = 100, // Fixed-point: 100 = 1.0x, 50 = 0.5x, 200 = 2.0x
    val reverse: Boolean = false
) : IRStatement

/** Stop a sprite animation */
data class IRAnimationStop(val spriteName: String) : IRStatement

/** Set animation frame directly */
data class IRAnimationSetFrame(val spriteName: String, val frameIndex: Int) : IRStatement

/** Pause a sprite animation */
data class IRAnimationPause(val spriteName: String) : IRStatement

/** Resume a paused animation */
data class IRAnimationResume(val spriteName: String) : IRStatement

/** Set animation speed dynamically */
data class IRAnimationSetSpeed(
    val spriteName: String,
    val speed: Int // Fixed-point: 100 = 1.0x
) : IRStatement

/** Queue an animation to play after current one finishes */
data class IRAnimationQueue(
    val spriteName: String,
    val animationName: String,
    val clearQueue: Boolean = false // If true, clears existing queue first
) : IRStatement

// =============================================================================
// STATE MACHINE IR NODES
// =============================================================================

/** Start a state machine in a specific state */
data class IRStateMachineStart(val machineName: String, val initialState: String) : IRStatement

/** Transition to a new state */
data class IRStateMachineGoto(val machineName: String, val targetState: String) : IRStatement

// =============================================================================
// ENTITY IR NODES
// =============================================================================

/** Batch update all entities (positions, animations, states) */
data class IREntityUpdate(
    val entityNames: List<String>,
    val updatePosition: Boolean = true,
    val updateAnimation: Boolean = true,
    val updateStates: Boolean = true
) : IRStatement

/**
 * Apply physics to an entity each frame.
 * - Adds gravity to velocityY (fixed-point 8.8)
 * - Multiplies velocityX by friction (fixed-point 8.8)
 * - Clamps velocity to max bounds
 * - Updates position from velocity
 */
data class IRPhysicsApply(val entityName: String) : IRStatement

// =============================================================================
// PHYSICS WORLD IR NODES - Global physics system with collision response
// =============================================================================

/**
 * Update physics world - applies physics to all tagged entities and handles collisions. This emits
 * optimized fixed-point physics loop code.
 */
data object IRPhysicsWorldUpdate : IRStatement

/**
 * Enable automatic collision response between entities with tag1 and entities with tag2. When
 * collisions are detected, entities will bounce based on configured bounce coefficient.
 */
data class IRCollisionResponse(val tag1: String, val tag2: String) : IRStatement

// =============================================================================
// POOL IR NODES - Entity pooling and lifecycle management
// =============================================================================

/** Update all active entities in a pool (runs onFrame, checks despawn conditions) */
data class IRPoolUpdate(val poolName: String) : IRStatement

/** Spawn an entity from pool with initialization statements */
data class IRPoolSpawn(val poolName: String, val initStatements: List<IRStatement>) : IRStatement

/** Spawn an entity at a specific position */
data class IRPoolSpawnAt(
    val poolName: String,
    val x: IRExpression,
    val y: IRExpression,
    val initStatements: List<IRStatement>
) : IRStatement

/** Try to spawn with fallback if pool is full */
data class IRPoolTrySpawn(
    val poolName: String,
    val initStatements: List<IRStatement>,
    val elseStatements: List<IRStatement>
) : IRStatement

/** Despawn a specific entity by index */
data class IRPoolDespawn(val poolName: String, val indexExpr: IRExpression) : IRStatement

/** Despawn all active entities in the pool */
data class IRPoolDespawnAll(val poolName: String) : IRStatement

/** Iterate over all active entities in the pool */
data class IRPoolForEach(
    val poolName: String,
    val bodyStatements: List<IRStatement>,
    val indexVar: String
) : IRStatement

/** Despawn entities matching a condition */
data class IRPoolDespawnWhere(
    val poolName: String,
    val condition: IRExpression,
    val indexVar: String
) : IRStatement

// Pool expression nodes

/** Number of active entities in pool */
data class IRPoolActiveCount(val poolName: String) : IRExpression

/** Check if pool has space for spawning (active < size) */
data class IRPoolHasSpace(val poolName: String) : IRExpression

/** Check if pool is full (active == size) */
data class IRPoolIsFull(val poolName: String) : IRExpression

/** Access pool entity's variable by current index */
data class IRPoolEntityVar(val poolName: String, val fieldName: String, val indexVar: String) :
    IRExpression

// =============================================================================
// SAVE SYSTEM IR NODES
// =============================================================================

/** Load save data from SRAM into RAM buffer */
data class IRSaveLoad(val saveName: String, val slot: IRExpression) : IRStatement

/** Save data from RAM buffer to SRAM */
data class IRSaveSave(val saveName: String, val slot: IRExpression) : IRStatement

/** Erase a save slot (fill with 0xFF) */
data class IRSaveErase(val saveName: String, val slot: IRExpression) : IRStatement

/** Copy save data from one slot to another */
data class IRSaveCopy(val saveName: String, val fromSlot: IRExpression, val toSlot: IRExpression) :
    IRStatement

/** Write to a save field in RAM buffer */
data class IRSaveFieldWrite(val saveName: String, val fieldName: String, val value: IRExpression) :
    IRStatement

/** Write to an array element in save data */
data class IRSaveArrayWrite(
    val saveName: String,
    val fieldName: String,
    val index: IRExpression,
    val value: IRExpression
) : IRStatement

/** Read a save field (expression) */
data class IRSaveFieldRead(val saveName: String, val fieldName: String) : IRExpression

/** Check if a save slot has valid data (expression returning 0 or 1) */
data class IRSaveExists(val saveName: String, val slot: IRExpression) : IRExpression

/** Access array element in save data (expression) */
data class IRSaveArrayAccess(val saveName: String, val fieldName: String, val index: IRExpression) :
    IRExpression

// =============================================================================
// CAMERA IR NODES - Scrolling, following, shake, and transitions
// =============================================================================

/** Set camera position directly */
data class IRCameraSetPosition(val x: IRExpression, val y: IRExpression) : IRStatement

/** Update camera (call in every.frame) - processes follow, shake, transitions */
data object IRCameraUpdate : IRStatement

/** Start following a target's position variables */
data class IRCameraFollow(
    val targetXVar: String,
    val targetYVar: String,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val smoothing: Int = 26 // Fixed-point 8.8: 26 ≈ 0.1
) : IRStatement

/** Stop following target */
data object IRCameraStopFollow : IRStatement

/** Snap camera to position instantly (no smoothing) */
data class IRCameraSnapTo(val x: IRExpression, val y: IRExpression) : IRStatement

/** Set camera bounds */
data class IRCameraSetBounds(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) :
    IRStatement

// Camera Shake

/** Start screen shake effect */
data class IRCameraShake(
    val intensity: Int,
    val durationFrames: Int,
    val decay: ShakeDecay = ShakeDecay.LINEAR
) : IRStatement

/** Stop screen shake immediately */
data object IRCameraShakeStop : IRStatement

enum class ShakeDecay {
    NONE,
    LINEAR,
    EXPONENTIAL
}

// Camera Transitions

/** Fade screen to black */
data class IRTransitionFadeOut(
    val durationFrames: Int,
    val onComplete: List<IRStatement> = emptyList()
) : IRStatement

/** Fade screen in from black */
data class IRTransitionFadeIn(
    val durationFrames: Int,
    val onComplete: List<IRStatement> = emptyList()
) : IRStatement

/** Flash the screen a color */
data class IRTransitionFlash(val color: GBCColor, val durationFrames: Int) : IRStatement

/** Wipe transition */
data class IRTransitionWipe(
    val direction: WipeDirection,
    val durationFrames: Int,
    val onComplete: List<IRStatement> = emptyList()
) : IRStatement

enum class WipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}

/** Iris transition (circle close/open) */
data class IRTransitionIris(
    val type: IrisType,
    val centerX: IRExpression,
    val centerY: IRExpression,
    val durationFrames: Int,
    val onComplete: List<IRStatement> = emptyList()
) : IRStatement

enum class IrisType {
    OPEN,
    CLOSE
}

// Camera Expressions

/** Camera X position */
data object IRCameraX : IRExpression

/** Camera Y position */
data object IRCameraY : IRExpression

/** Check if any transition is active */
data object IRTransitionActive : IRExpression

// =============================================================================
// COMPOSABLE TRANSITIONS - First-class transition values
// =============================================================================

/**
 * A composed transition with optional target scene. This is the main IR node that represents any
 * composed transition.
 */
data class IRComposedTransition(val transition: Transition, val targetScene: String? = null) :
    IRStatement

/** Cancel the currently running transition */
data object IRTransitionCancel : IRStatement

// =============================================================================
// LINK CABLE IR NODES - Serial communication for multiplayer
// =============================================================================

/** Initialize link cable communication */
data object IRLinkInit : IRStatement

/** Update link cable state (call in frame loop) */
data object IRLinkUpdate : IRStatement

/** Send data over link cable */
data class IRLinkSend(val data: IRExpression) : IRStatement

/** Check if link is connected */
data object IRLinkConnected : IRExpression

/** Check if link has received data */
data object IRLinkHasData : IRExpression

/** Access received data byte */
data object IRLinkReceivedData : IRExpression

/** Check if link is master (initiated connection) */
data object IRLinkIsMaster : IRExpression

// =============================================================================
// CUTSCENE/TIMELINE IR NODES - Sequenced actions and events
// =============================================================================

/** Start a cutscene by name */
data class IRCutsceneStart(val cutsceneName: String) : IRStatement

/** Update cutscene state (call in frame loop) */
data class IRCutsceneUpdate(val cutsceneName: String) : IRStatement

/** Skip/cancel current cutscene */
data class IRCutsceneSkip(val cutsceneName: String) : IRStatement

/** Check if cutscene is playing */
data class IRCutsceneIsPlaying(val cutsceneName: String) : IRExpression

/** Check if cutscene is complete */
data class IRCutsceneIsComplete(val cutsceneName: String) : IRExpression

/** Timeline step types for cutscene sequences */
sealed class TimelineStep {
    data class Wait(val frames: Int) : TimelineStep()

    data class Action(val statements: List<IRStatement>) : TimelineStep()

    data class Parallel(val steps: List<TimelineStep>) : TimelineStep()

    data class TransitionEffect(val transition: Transition) : TimelineStep()
}
