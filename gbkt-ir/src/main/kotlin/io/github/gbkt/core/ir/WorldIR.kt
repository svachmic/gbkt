/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// WORLD / DUNGEON SYSTEM IR TYPES
// =============================================================================

/**
 * A single navigable zone (dungeon floor, overworld region, town, etc.).
 *
 * Zones carry their tilemap data, encounter configuration, transition edges to adjacent zones, and
 * lifecycle callbacks for zone entry/exit events.
 *
 * @property id Unique identifier for this zone (used in DSL navigation and codegen).
 * @property name Human-readable zone name (used in debug output and save data labels).
 * @property tilesetPath Path to the tileset image (relative to assets root).
 * @property tilemapPath Path to a separate tilemap PNG (relative to assets root). When set,
 *   ConvertZoneTilesetsTask runs a second png2asset invocation with `-maps_only` to extract real
 *   tilemap bytes (Phase 12.2 D-01 two-invocation path). When null, the tileset PNG doubles as the
 *   tilemap source (Phase 12.2 D-01 one-invocation path). Phase 12.2 D-03: AssetRef-only DSL
 *   signature; field stores the underlying string path for codegen consumption.
 * @property mapWidth Map width in tiles. `null` means "derive automatically" — either from the
 *   tilemap PNG pixel dimensions (in ConvertZoneTilesetsTask) or fall back to 20×18. Mirrors the
 *   bankOverride nullable-sentinel pattern.
 * @property mapHeight Map height in tiles. `null` means "derive automatically" — same policy as
 *   [mapWidth].
 * @property tileData Raw tile index array (one byte per tile, row-major). Empty means no tilemap.
 * @property collisionData Raw collision array (0=walkable, 1=blocked), same dimensions as tileData.
 * @property encounterTable Random encounter configuration for this zone; null means no encounters.
 * @property isSafeZone When true, no random encounters occur regardless of encounter table.
 * @property transitions Edge-based transitions to adjacent zones.
 * @property transitionStyle Visual effect when entering/exiting this zone.
 * @property onEnter Script ops run once when the player enters this zone.
 * @property onExit Script ops run once when the player exits this zone.
 */
data class ZoneIR(
    val id: String,
    val name: String,
    val tilesetPath: String? = null,
    val tilemapPath: String? = null,
    val mapWidth: Int? = null,
    val mapHeight: Int? = null,
    val tileData: List<Int> = emptyList(),
    val collisionData: List<Int> = emptyList(),
    val encounterTable: EncounterTableIR? = null,
    val isSafeZone: Boolean = false,
    val transitions: List<ZoneTransitionIR> = emptyList(),
    val transitionStyle: TransitionStyle = TransitionStyle.CUT,
    val onEnter: List<ScriptOp> = emptyList(),
    val onExit: List<ScriptOp> = emptyList(),
    /**
     * Manual ROM bank override for this zone's tilemap data.
     *
     * When non-null, the zone's tile array is placed in the specified bank instead of being
     * auto-allocated by the first-fit bin-packing algorithm. Use with caution — manual overrides
     * bypass capacity checks. A warning is logged at build time when this is set.
     */
    val bankOverride: Int? = null,
    /**
     * Per-level platformer-physics overrides (D-12). Stored as opaque `Map<String, Any>?` to keep
     * gbkt-ir module independent of gbkt-genre-platformer. Keys: 'gravity', 'jumpForce',
     * 'terminalVelocity', 'solidThreshold', 'jumpHoldMaxFrames'. PlatformerVisitor casts values
     * back to Int at codegen time.
     */
    val platformerPhysicsOverride: Map<String, Any>? = null,
    /**
     * Per-level platformer-input numeric overrides (Phase 12.3 R1). Stored as opaque `Map<String,
     * Any>?` to keep gbkt-ir module independent of gbkt-genre-platformer. Keys: `walkSpeed`,
     * `friction`, `airFriction`, `walkFrameCount`, `cyclePeriod`. PlatformerVisitor casts values
     * back to Int at codegen time. AssignableVar binders are NEVER per-zone (only at game level per
     * D-03 / L-2.2 — `OverrideTrackingInputBuilder` does not override binders).
     */
    val platformerInputOverride: Map<String, Any>? = null,
    /**
     * Per-zone player spawn position (Phase 12.6 D-06 / D-07). Coordinates are in PIXELS (codegen
     * applies <<4 shift to convert to subpixel form). When null, the framework emits a build-time
     * WARNING and defaults to (16, 120). Consumed by
     * GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded (per-case body extension) +
     * buildLevelSpawnTablesIfNeeded (HOME-bank const-array emission).
     */
    val spawnX: UByte? = null,
    val spawnY: UByte? = null,
    /** Interactive objects within this zone (chests, signs, sconces, NPCs, levers, doors). */
    val objects: List<ZoneObjectIR> = emptyList(),
    /**
     * When `true`, this zone was synthesized by
     * [SceneBuilder.screen][io.github.gbkt.core.dsl.SceneBuilder] and gates the SceneVisitor
     * screenMode superset: hide_sprites_range + move_bkg(0,0) + fill_bkg_rect(full BG plane
     * clear) + centered _bkg_tiles_load_banked placement.
     *
     * Set only by `SceneBuilder.screen(assetRef)` — never by the user-facing `zone { }` DSL.
     * Default `false` preserves backward-compat for all existing [ZoneIR] construction sites.
     */
    val screenMode: Boolean = false,
)

/** Visual transition effect when moving between zones. */
enum class TransitionStyle {
    /** Instant cut — no visual transition effect. */
    CUT,
    /** Fade to black and fade back in. */
    FADE,
    /** Scroll the camera toward the new zone. */
    SCROLL,
}

/**
 * Describes a directional transition from one zone to an adjacent zone.
 *
 * @property targetZoneId ID of the destination zone.
 * @property edge Map edge that triggers this transition (null = trigger anywhere).
 * @property entryPoint Named entry point in the target zone (null = default start position).
 * @property entryX Override X tile coordinate for entry in the target zone (null = auto).
 * @property entryY Override Y tile coordinate for entry in the target zone (null = auto).
 * @property conditionFlag Optional story flag ID; if set, the transition only executes when the
 *   flag is active. When the flag is not set, the player is blocked from crossing this exit.
 */
data class ZoneTransitionIR(
    val targetZoneId: String,
    val edge: TransitionEdge? = null,
    val entryPoint: String? = null,
    val entryX: Int? = null,
    val entryY: Int? = null,
    val conditionFlag: String? = null,
)

/** Cardinal map edge that triggers a zone transition when the player crosses it. */
enum class TransitionEdge {
    NORTH,
    SOUTH,
    EAST,
    WEST,
}

/**
 * Random encounter configuration for a zone.
 *
 * Implements the classic JRPG model: a guaranteed safe-step window followed by random encounter
 * checks within a configurable step range.
 *
 * @property safeSteps Number of steps guaranteed without an encounter after entering the zone.
 * @property minStepsBeforeRoll Minimum additional steps before an encounter roll is attempted.
 * @property maxStepsBeforeRoll Maximum additional steps before an encounter roll is attempted.
 * @property entries Weighted encounter entries. Total weight is used as denominator for
 *   probability.
 */
data class EncounterTableIR(
    val safeSteps: Int = 10,
    val minStepsBeforeRoll: Int = 0,
    val maxStepsBeforeRoll: Int = 4,
    val entries: List<EncounterEntryIR> = emptyList(),
)

/**
 * A single entry in an encounter table.
 *
 * @property id Encounter identifier — used as the combat trigger argument.
 * @property weight Relative encounter weight; higher values = more common.
 * @property conditionFlag Optional story flag ID; if set, this entry only appears when the flag is
 *   active. Enables story-progression-based encounter changes.
 * @property minLevel Optional minimum player level required for this entry to appear.
 * @property maxLevel Optional maximum player level (exclusive) for this entry to appear.
 */
data class EncounterEntryIR(
    val id: String,
    val weight: Int,
    val conditionFlag: String? = null,
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
)

// =============================================================================
// GLOBAL FLAGS IR
// =============================================================================

/**
 * A global flags container grouping named boolean flags into named pages.
 *
 * Flags track story progression, discovered items, completed quests, etc. Pages group related flags
 * and may map to individual SRAM bytes for efficient save/load.
 *
 * @property id Unique identifier for this flags container.
 * @property pages Ordered list of flag pages; each page groups up to 8 flags in one SRAM byte.
 */
data class GlobalFlagsIR(val id: String, val pages: List<FlagPageIR> = emptyList())

/**
 * A page of up to 8 named boolean flags packed into one SRAM byte.
 *
 * @property name Page name (used as prefix in generated C constants).
 * @property flags Ordered list of flag names in this page. Index determines the bit position.
 */
data class FlagPageIR(val name: String, val flags: List<String> = emptyList())

// =============================================================================
// EXPLORATION GAUGE AND KEY IR
// =============================================================================

/**
 * A resource gauge that decrements each step during exploration (e.g., torch durability, stamina).
 *
 * @property id Gauge identifier (used in generated C globals and DSL callbacks).
 * @property max Maximum gauge value (initial full charge).
 * @property initial Starting value of the gauge.
 * @property decrementPerStep How much the gauge decreases per player step.
 * @property onLowThreshold Threshold value at which [onLowStatements] fires (null = no callback).
 * @property onLowStatements Script ops executed when the gauge falls at or below [onLowThreshold].
 * @property onDepletedStatements Script ops executed when the gauge reaches zero.
 */
data class ExplorationGaugeIR(
    val id: String,
    val max: Int = 255,
    val initial: Int = 255,
    val decrementPerStep: Int = 1,
    val onLowThreshold: Int? = null,
    val onLowStatements: List<ScriptOp> = emptyList(),
    val onDepletedStatements: List<ScriptOp> = emptyList(),
)

/**
 * A key-item counter used to unlock doors, chests, or other interactive zone objects.
 *
 * @property id Key identifier (used in generated C globals and DSL access functions).
 * @property max Maximum number of keys the player can carry.
 * @property initial Number of keys at exploration start.
 */
data class ExplorationKeyIR(val id: String, val max: Int = 99, val initial: Int = 0)

// =============================================================================
// ZONE OBJECT IR
// Interactive objects within zones: chests, signs, sconces, NPCs, levers, doors.
// Each object has a position (x, y) in tile coordinates, an optional "used" flag
// ID for state tracking, and an onInteract callback (List<ScriptOp>).
//
// Design: non-sealed for extensibility. Codegen dispatches via when() exhaustion
// across all known subtypes.
// =============================================================================

/**
 * Base interface for all interactive zone objects.
 *
 * Zone objects are placed at tile coordinates within a [ZoneIR] and respond to player interaction
 * (pressing the action button while adjacent). Each object tracks its own state (opened/closed,
 * lit/unlit, etc.) via optional global flag variables.
 *
 * @property id Unique identifier within the zone (used for generated C state variables and function
 *   names).
 * @property x Tile column position of the object.
 * @property y Tile row position of the object.
 * @property usedFlagId Optional flag ID that tracks whether this object has been used (e.g., chest
 *   opened). When set, the framework sets the flag on first interaction and suppresses subsequent
 *   interactions.
 * @property onInteract Script ops to execute when the player interacts with this object.
 */
sealed interface ZoneObjectIR {
    val id: String
    val x: Int
    val y: Int
    val usedFlagId: String?
    val onInteract: List<ScriptOp>
}

/**
 * A treasure chest that can contain items, currency, or trigger scripted events.
 *
 * Generates a `_chest_{id}_opened` state variable and a `zone_chest_{id}_interact()` function. When
 * [usedFlagId] is set, the chest can only be opened once (the used flag prevents re-opening).
 *
 * @property id Unique chest identifier.
 * @property x Tile column position.
 * @property y Tile row position.
 * @property usedFlagId Optional flag ID to set when the chest is opened (prevents re-opening).
 * @property onInteract Script ops to execute when the player opens this chest.
 */
data class ChestObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val usedFlagId: String? = null,
    override val onInteract: List<ScriptOp> = emptyList(),
) : ZoneObjectIR

/**
 * An information sign that displays a text message when the player interacts with it.
 *
 * Generates a `zone_sign_{id}_interact()` function that triggers a textbox dialog. Signs are always
 * re-readable (no used flag by default).
 *
 * @property id Unique sign identifier.
 * @property x Tile column position.
 * @property y Tile row position.
 * @property usedFlagId Optional flag ID (rarely used for signs, but included for consistency).
 * @property onInteract Script ops to execute when the player reads this sign.
 */
data class SignObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val usedFlagId: String? = null,
    override val onInteract: List<ScriptOp> = emptyList(),
) : ZoneObjectIR

/**
 * A light sconce that can be lit or unlit, potentially unlocking doors or chests.
 *
 * Generates a `_sconce_{id}_lit` state variable and a `zone_sconce_{id}_interact()` function. The
 * [onLit] callback fires when the sconce transitions from unlit to lit. The [onExtinguished]
 * callback fires when the sconce transitions from lit to unlit.
 *
 * @property id Unique sconce identifier.
 * @property x Tile column position.
 * @property y Tile row position.
 * @property usedFlagId Optional flag ID used to persist lit state across sessions.
 * @property onInteract Script ops to execute when the player interacts (toggles lit state).
 * @property onLit Script ops to execute when the sconce becomes lit.
 * @property onExtinguished Script ops to execute when the sconce becomes extinguished.
 */
data class SconceObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val usedFlagId: String? = null,
    override val onInteract: List<ScriptOp> = emptyList(),
    val onLit: List<ScriptOp> = emptyList(),
    val onExtinguished: List<ScriptOp> = emptyList(),
) : ZoneObjectIR

/**
 * A non-player character (NPC) that can initiate dialog, battles, or scripted events.
 *
 * Generates a `zone_npc_{id}_interact()` function. NPCs can be conditionally hidden/shown via
 * [visibleFlagId] — the NPC only appears (and is interactable) when the flag is set (or not set if
 * [visibleWhenFlagUnset] is true).
 *
 * @property id Unique NPC identifier.
 * @property x Tile column position.
 * @property y Tile row position.
 * @property usedFlagId Optional flag ID set after interaction (for one-time NPCs).
 * @property visibleFlagId Optional flag ID controlling NPC visibility.
 * @property visibleWhenFlagUnset When true, NPC is visible only when [visibleFlagId] is NOT set.
 * @property onInteract Script ops to execute when the player talks to this NPC.
 */
data class NpcObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val usedFlagId: String? = null,
    val visibleFlagId: String? = null,
    val visibleWhenFlagUnset: Boolean = false,
    override val onInteract: List<ScriptOp> = emptyList(),
) : ZoneObjectIR

/**
 * A lever that toggles between two states (on/off), triggering callbacks on each transition.
 *
 * Generates a `_lever_{id}_active` state variable and a `zone_lever_{id}_interact()` function.
 *
 * @property id Unique lever identifier.
 * @property x Tile column position.
 * @property y Tile row position.
 * @property usedFlagId Optional flag ID to persist lever state across sessions.
 * @property onInteract Script ops to execute when the lever is toggled (in either direction).
 * @property onActivate Script ops to execute when the lever is turned on.
 * @property onDeactivate Script ops to execute when the lever is turned off.
 */
data class LeverObjectIR(
    override val id: String,
    override val x: Int,
    override val y: Int,
    override val usedFlagId: String? = null,
    override val onInteract: List<ScriptOp> = emptyList(),
    val onActivate: List<ScriptOp> = emptyList(),
    val onDeactivate: List<ScriptOp> = emptyList(),
) : ZoneObjectIR
