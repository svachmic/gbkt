/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// SYSTEM IR HIERARCHY
// =============================================================================

/**
 * Non-sealed interface for engine-level game systems.
 *
 * Systems are singleton services (dialog engine, sound engine, save manager, etc.) that scenes
 * interact with via [TriggerSystem] script ops. Unsealed so that external modules (gbkt-rpg,
 * gbkt-exploration) can define their own system types.
 *
 * All dispatch is performed via the visitor pattern — callers invoke [accept] and the subtype
 * routes to the correct [SystemIRVisitorI] method.
 *
 * Each system can carry optional [PlatformAnnotatable] fields so the backend can assign bank and
 * memory ranges during resource allocation.
 */
interface SystemIR : PlatformAnnotatable {
    val id: String
    val sourceLocation: SourceLocation?

    fun <T> accept(visitor: SystemIRVisitorI<T>): T
}

/**
 * Dialog box rendering and text display system.
 *
 * Carries global dialog configuration that applies to all dialogs unless overridden at the
 * individual [DialogDef] level.
 *
 * @property textSpeed Default typewriter speed in characters per frame. Individual [DialogDef]
 *   instances can override this via their own [DialogDef.textSpeed] field.
 * @property defaultBorder Default border style for dialog boxes that don't specify their own.
 */
data class DialogSystem(
    override val id: String,
    val textSpeed: Int = 1,
    val defaultBorder: BorderStyle = BorderStyle.NONE,
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitDialogSystem(this)
}

/** Sound and music playback system. */
data class SoundSystem(
    override val id: String,
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitSoundSystem(this)
}

/** Save/load game state system. */
data class SaveSystem(
    override val id: String,
    /** Number of independent save slots in SRAM. Slot N starts at offset N * slotSize. */
    val slots: Int = 1,
    /** When true, an 8-bit rolling checksum byte is appended after each slot's sentinel. */
    val useChecksum: Boolean = false,
    /** Save format version number for future migration support. */
    val version: Int = 1,
    /** Variable names excluded from save/load. Marked transient via `u8Var(transient=true)`. */
    val transientVarNames: Set<String> = emptySet(),
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitSaveSystem(this)
}

/**
 * Dungeon exploration and overworld map system.
 *
 * Carries the full configuration for the grid-based exploration engine: tile dimensions, movement
 * style, resource gauges (torch, stamina, etc.), key-item counters, and lifecycle callbacks.
 *
 * @property tileSize Tile size in pixels (default 8 for 8x8 tiles).
 * @property movementStyle Movement mode string: "GRID" for tile-by-tile or "SMOOTH" for free.
 * @property movementSpeed Frames per tile for grid movement; lower = faster.
 * @property startZoneId ID of the first zone to load on exploration start (null = no auto-load).
 * @property stepStatements Script ops executed on each player step.
 * @property blockedStatements Script ops executed when the player is blocked by a tile or entity.
 * @property interactStatements Script ops executed when the player presses the interact button.
 * @property gauges Resource gauges that decrement per step (e.g., torch, stamina).
 * @property keys Key-item counters for locked interactive objects.
 */
data class ExplorationSystem(
    override val id: String,
    val tileSize: Int = 8,
    val movementStyle: String = "GRID",
    val movementSpeed: Int = 8,
    val startZoneId: String? = null,
    val stepStatements: List<ScriptOp> = emptyList(),
    val blockedStatements: List<ScriptOp> = emptyList(),
    val interactStatements: List<ScriptOp> = emptyList(),
    val gauges: List<ExplorationGaugeIR> = emptyList(),
    val keys: List<ExplorationKeyIR> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitExplorationSystem(this)
}

/** Camera follow, pan, and shake system. */
data class CameraSystem(
    override val id: String,
    /** ID of the actor to follow. Null means free camera (no follow target). */
    val followActorId: String? = null,
    /** Map width in pixels for horizontal bounds clamping. Null means no clamping. */
    val boundsWidth: Int? = null,
    /** Map height in pixels for vertical bounds clamping. Null means no clamping. */
    val boundsHeight: Int? = null,
    /** Follow smoothing factor. 0.0 = instant snap, 1.0 = very smooth (barely moves). */
    val smoothing: Float = 0.0f,
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitCameraSystem(this)
}

/** Grid-based A* pathfinding infrastructure for NPC navigation. */
data class PathfindingSystem(
    override val id: String,
    /** Tile size in pixels — used to convert pixel positions to tile coordinates. */
    val gridSize: Int = 8,
    /** Map width in tiles — used to compute bit-packed closed set array size. */
    val mapWidth: Int = 32,
    /** Map height in tiles — used to compute bit-packed closed set array size. */
    val mapHeight: Int = 32,
    /** A* open list capacity. Limits WRAM usage: maxOpenNodes * 4 bytes. */
    val maxOpenNodes: Int = 32,
    /** Maximum path length in steps. Limits path arrays: maxPathLength * 2 bytes. */
    val maxPathLength: Int = 32,
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitPathfindingSystem(this)
}

/**
 * Generic user-defined system for extensions and future subsystems.
 *
 * [config] carries arbitrary key-value pairs for backend-specific configuration.
 */
data class GenericSystem(
    override val id: String,
    val config: Map<String, Any> = emptyMap(),
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitGenericSystem(this)
}
