/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ChestObjectIR
import io.github.gbkt.core.ir.EncounterEntryIR
import io.github.gbkt.core.ir.EncounterTableIR
import io.github.gbkt.core.ir.ExplorationGaugeIR
import io.github.gbkt.core.ir.ExplorationKeyIR
import io.github.gbkt.core.ir.FlagPageIR
import io.github.gbkt.core.ir.GlobalFlagsIR
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SignObjectIR
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.core.ir.TransitionStyle
import io.github.gbkt.core.ir.ZoneIR
import io.github.gbkt.core.ir.ZoneObjectIR
import io.github.gbkt.core.ir.ZoneTransitionIR

// =============================================================================
// ZONE REFERENCE
// =============================================================================

/**
 * Lightweight typed reference to a zone.
 *
 * Returned by [GameBuilder.zone] for use in exploration() [startZone] and navigate operations
 * without requiring string literals.
 */
data class ZoneRef(val id: String) {
    override fun toString(): String = id
}

// =============================================================================
// ZONE BUILDER
// =============================================================================

/**
 * Builder for a navigable zone (dungeon floor, overworld area, town, etc.).
 *
 * Produces a [ZoneIR] with full tilemap, encounter, and lifecycle configuration.
 *
 * Usage:
 * ```kotlin
 * val floor1 by zone {
 *     name("Dungeon Level 1")
 *     tileset("dungeon.png")
 *     size(32, 32)
 *     encounters {
 *         safeSteps(10)
 *         entry("goblin_pack", weight = 30)
 *     }
 *     onEnter { soundEffect("dungeon_music") }
 * }
 * ```
 */
@GbktDsl
class ZoneBuilder(private val id: String) {
    private var zoneName: String = id
    private var tilesetPath: String? = null
    private var mapWidth: Int = 32
    private var mapHeight: Int = 32
    private var tileData: List<Int> = emptyList()
    private var collisionData: List<Int> = emptyList()
    private var encounterBuilder: EncounterBuilder? = null
    private var safeZone: Boolean = false
    private val zoneTransitions = mutableListOf<ZoneTransitionIR>()
    private var transitionStyle: TransitionStyle = TransitionStyle.CUT
    private var onEnterCallback: (ScriptBuilder.() -> Unit)? = null
    private var onExitCallback: (ScriptBuilder.() -> Unit)? = null
    private var bankOverride: Int? = null
    private val zoneObjects = mutableListOf<ZoneObjectIR>()

    /** Sets the human-readable zone name (used in save data labels and debug output). */
    fun name(n: String) {
        zoneName = n
    }

    /** Sets the tileset image path (relative to assets root, e.g. "dungeon.png"). */
    fun tileset(path: String) {
        tilesetPath = path
    }

    /** Sets the map dimensions in tiles. */
    fun size(w: Int, h: Int) {
        mapWidth = w
        mapHeight = h
    }

    /** Provides raw tile index data (one Int per tile, row-major). */
    fun tiles(data: List<Int>) {
        tileData = data
    }

    /** Provides raw collision data (0=walkable, 1=blocked), same dimensions as tile data. */
    fun collision(data: List<Int>) {
        collisionData = data
    }

    /** Configures the encounter table for this zone. */
    fun encounters(block: EncounterBuilder.() -> Unit) {
        encounterBuilder = EncounterBuilder().apply(block)
    }

    /** Marks this zone as safe — no random encounters occur regardless of encounter table. */
    fun safeZone() {
        safeZone = true
    }

    /** Adds a directional transition to an adjacent zone. */
    fun transition(block: TransitionBuilder.() -> Unit) {
        zoneTransitions += TransitionBuilder().apply(block).build()
    }

    /** Sets the visual transition style for entering/exiting this zone. */
    fun transitionStyle(style: TransitionStyle) {
        transitionStyle = style
    }

    /** Registers a callback run once when the player enters this zone. */
    fun onEnter(block: ScriptBuilder.() -> Unit) {
        onEnterCallback = block
    }

    /** Registers a callback run once when the player exits this zone. */
    fun onExit(block: ScriptBuilder.() -> Unit) {
        onExitCallback = block
    }

    /**
     * Manually pins this zone's tilemap data to the specified ROM bank.
     *
     * Overrides auto-allocation by the framework's first-fit bin-packing algorithm. When set, the
     * zone's tile array is placed in bank [n] regardless of capacity. A warning is logged at build
     * time: "Zone {id}: manual bank override bank(N). Trusting developer."
     *
     * @param n ROM bank number (must be >= 2; banks 0 and 1 are reserved for HOME and scenes).
     */
    fun bank(n: Int) {
        bankOverride = n
    }

    /**
     * Defines interactive objects (chests, signs, sconces, NPCs, levers) within this zone.
     *
     * Each object has a tile position and an optional scripted interaction callback. The framework
     * generates a per-zone object dispatch table that routes `tryInteractWithObject(x, y)` calls to
     * the correct per-object handler function.
     *
     * Usage:
     * ```kotlin
     * objects {
     *     chest("chest1", x = 5, y = 3) {
     *         usedFlag("chest1_opened")
     *         onOpen { grantItem("torch") }
     *     }
     *     sign("entrance_sign", x = 2, y = 8) {
     *         onRead { showMessage("Welcome to the dungeon!") }
     *     }
     * }
     * ```
     */
    fun objects(block: ZoneObjectsBuilder.() -> Unit) {
        zoneObjects += ZoneObjectsBuilder().apply(block).build()
    }

    internal fun build(): ZoneIR =
        ZoneIR(
            id = id,
            name = zoneName,
            tilesetPath = tilesetPath,
            mapWidth = mapWidth,
            mapHeight = mapHeight,
            tileData = tileData,
            collisionData = collisionData,
            encounterTable = encounterBuilder?.build(),
            isSafeZone = safeZone,
            transitions = zoneTransitions.toList(),
            transitionStyle = transitionStyle,
            onEnter = onEnterCallback?.let { recordStatements(it) } ?: emptyList(),
            onExit = onExitCallback?.let { recordStatements(it) } ?: emptyList(),
            bankOverride = bankOverride,
            objects = zoneObjects.toList(),
        )
}

// =============================================================================
// TRANSITION BUILDER
// =============================================================================

/**
 * Builder for a zone edge transition.
 *
 * Configures target zone, triggering edge, and optional entry point override.
 *
 * Usage:
 * ```kotlin
 * transition {
 *     to("floor2")
 *     edge(TransitionEdge.NORTH)
 *     entryX(5); entryY(29)
 * }
 * ```
 */
@GbktDsl
class TransitionBuilder {
    private var targetZoneId: String = ""
    private var edge: TransitionEdge? = null
    private var entryPoint: String? = null
    private var entryX: Int? = null
    private var entryY: Int? = null
    private var conditionFlagId: String? = null

    /** Sets the target zone ID. */
    fun to(zoneId: String) {
        targetZoneId = zoneId
    }

    /** Sets the target zone via a [ZoneRef]. */
    fun to(zone: ZoneRef) {
        targetZoneId = zone.id
    }

    /** Sets the map edge that triggers this transition. */
    fun edge(e: TransitionEdge) {
        edge = e
    }

    /** Sets a named entry point in the target zone (alternative to explicit coordinates). */
    fun entryPoint(name: String) {
        entryPoint = name
    }

    /** Overrides the X tile coordinate for player entry in the target zone. */
    fun entryX(x: Int) {
        entryX = x
    }

    /** Overrides the Y tile coordinate for player entry in the target zone. */
    fun entryY(y: Int) {
        entryY = y
    }

    /**
     * Gates this transition on a story flag. Player is blocked unless the flag is set.
     *
     * String-based overload — prefer the typed [conditionFlag] overload with [FlagRef].
     *
     * @param flagId Raw flag name string. Generates `if (_flag_{flagId})` guard in C.
     */
    fun conditionFlag(flagId: String) {
        conditionFlagId = flagId
    }

    /**
     * Gates this transition on a story flag. Player is blocked unless the flag is set.
     *
     * Typed overload — eliminates magic flag name strings. Prefer over `conditionFlag(String)`.
     *
     * @param flag Typed [FlagRef] returned by [FlagPageBuilder.flag].
     */
    fun conditionFlag(flag: FlagRef) {
        conditionFlagId = flag.name
    }

    internal fun build(): ZoneTransitionIR =
        ZoneTransitionIR(
            targetZoneId = targetZoneId,
            edge = edge,
            entryPoint = entryPoint,
            entryX = entryX,
            entryY = entryY,
            conditionFlag = conditionFlagId,
        )
}

// =============================================================================
// ENCOUNTER BUILDER
// =============================================================================

/**
 * Builder for a zone's random encounter table.
 *
 * Implements the classic JRPG encounter model: safe steps followed by random rolls within a range.
 *
 * Usage:
 * ```kotlin
 * encounters {
 *     safeSteps(10)
 *     stepRange(1, 5)
 *     entry("goblin_pack", weight = 30)
 *     entry("orc_scout", weight = 15) { condition("metElder") }
 * }
 * ```
 */
@GbktDsl
class EncounterBuilder {
    private var safeSteps: Int = 10
    private var minRange: Int = 0
    private var maxRange: Int = 4
    private val entries = mutableListOf<EncounterEntryIR>()

    /** Sets the number of guaranteed safe steps after entering the zone. */
    fun safeSteps(count: Int) {
        safeSteps = count
    }

    /**
     * Sets the random step range within which encounter rolls occur (min..max additional steps).
     */
    fun stepRange(min: Int, max: Int) {
        minRange = min
        maxRange = max
    }

    /**
     * Adds a weighted encounter entry.
     *
     * @param id Encounter identifier (used as combat trigger argument).
     * @param weight Relative encounter probability weight — higher = more common.
     * @param block Optional builder block for setting a condition flag.
     */
    fun entry(id: String, weight: Int, block: EncounterEntryBuilder.() -> Unit = {}) {
        entries += EncounterEntryBuilder(id, weight).apply(block).build()
    }

    internal fun build(): EncounterTableIR =
        EncounterTableIR(
            safeSteps = safeSteps,
            minStepsBeforeRoll = minRange,
            maxStepsBeforeRoll = maxRange,
            entries = entries.toList(),
        )
}

// =============================================================================
// ENCOUNTER ENTRY BUILDER
// =============================================================================

/**
 * Builder for a single encounter entry in an encounter table.
 *
 * @param id Encounter identifier.
 * @param weight Relative encounter weight.
 */
@GbktDsl
class EncounterEntryBuilder(private val id: String, private val weight: Int) {
    private var conditionFlag: String? = null
    private var minLevel: Int? = null
    private var maxLevel: Int? = null

    /**
     * Restricts this entry to appear only when the given story flag is active.
     *
     * Enables story-progression-based encounter changes (e.g., different monsters after key event).
     *
     * String-based overload — prefer the typed [condition] overload with [FlagRef].
     */
    fun condition(flagId: String) {
        conditionFlag = flagId
    }

    /**
     * Restricts this entry to appear only when the given story flag is active.
     *
     * Typed overload — eliminates magic flag name strings. Prefer over `condition(String)`.
     *
     * @param flag Typed [FlagRef] returned by [FlagPageBuilder.flag].
     */
    fun condition(flag: FlagRef) {
        conditionFlag = flag.name
    }

    /**
     * Restricts this entry to appear only when the player's level is at or above [level].
     *
     * Enables level-progressive encounter tables (e.g., stronger monsters on later floors at higher
     * levels). Combine with [maxLevel] to create level-banded encounter sets.
     *
     * @param level Minimum player level (inclusive) for this entry to appear.
     */
    fun minLevel(level: Int) {
        minLevel = level
    }

    /**
     * Restricts this entry to appear only when the player's level is below [level].
     *
     * @param level Maximum player level (exclusive) for this entry to appear.
     */
    fun maxLevel(level: Int) {
        maxLevel = level
    }

    internal fun build(): EncounterEntryIR =
        EncounterEntryIR(
            id = id,
            weight = weight,
            conditionFlag = conditionFlag,
            minLevel = minLevel,
            maxLevel = maxLevel,
        )
}

// =============================================================================
// FLAG REFERENCE (GAP-11)
// =============================================================================

/**
 * Typed reference to a named story flag.
 *
 * Returned by [FlagPageBuilder.flag] for type-safe flag operations. Use a [FlagRef] instead of raw
 * flag name strings in [ScriptBuilder.setFlag], [ScriptBuilder.clearFlag],
 * [ScriptBuilder.checkFlag], [EncounterEntryBuilder.condition], and
 * [TransitionBuilder.conditionFlag] calls.
 *
 * Usage:
 * ```kotlin
 * val storyFlags by flags {
 *     page("story") {
 *         val bossDefeated = flag("bossDefeated")
 *         val hasKey = flag("hasKey")
 *     }
 * }
 *
 * // In scene frame:
 * whenever(checkFlag(bossDefeated)) { navigate(victoryScene) }
 * setFlag(hasKey)
 * clearFlag(hasKey)
 * ```
 *
 * @property name The raw flag name string (used to generate `_flag_{name}` C variable).
 */
data class FlagRef(val name: String)

// =============================================================================
// FLAGS BUILDER
// =============================================================================

/**
 * Builder for a global flags container grouping named boolean flags into pages.
 *
 * Usage:
 * ```kotlin
 * val storyFlags by flags {
 *     page("story") {
 *         val bossDefeated = flag("bossDefeated")
 *         val hasKey = flag("hasKey")
 *         val defeatedBoss = flag("defeatedBoss")
 *     }
 *     page("exploration") {
 *         val visitedFloor1 = flag("visitedFloor1")
 *         val visitedFloor2 = flag("visitedFloor2")
 *     }
 * }
 * ```
 */
@GbktDsl
class FlagsBuilder(private val id: String) {
    private val pages = mutableListOf<FlagPageIR>()

    /** Adds a named page of up to 8 boolean flags. */
    fun page(name: String, block: FlagPageBuilder.() -> Unit) {
        pages += FlagPageBuilder(name).apply(block).build()
    }

    internal fun build(): GlobalFlagsIR = GlobalFlagsIR(id = id, pages = pages.toList())
}

// =============================================================================
// FLAG PAGE BUILDER
// =============================================================================

/**
 * Builder for a single page of boolean flags.
 *
 * Up to 8 flags per page — each page maps to one SRAM byte for efficient save/load.
 */
@GbktDsl
class FlagPageBuilder(private val name: String) {
    private val flagNames = mutableListOf<String>()

    /**
     * Registers a named flag in this page and returns a [FlagRef] for type-safe access.
     *
     * Bit position is determined by registration order (first flag = bit 0, max 8 per page).
     *
     * @param flagName The flag identifier. Generates `_flag_{flagName}` C variable.
     * @return A [FlagRef] for use in [ScriptBuilder.setFlag], [ScriptBuilder.clearFlag],
     *   [ScriptBuilder.checkFlag], and condition gates.
     */
    fun flag(flagName: String): FlagRef {
        flagNames += flagName
        return FlagRef(flagName)
    }

    internal fun build(): FlagPageIR = FlagPageIR(name = name, flags = flagNames.toList())
}

// =============================================================================
// GAUGE BUILDER
// =============================================================================

/**
 * Builder for a resource gauge that decrements per exploration step.
 *
 * Usage:
 * ```kotlin
 * gauge("torch") {
 *     max(255)
 *     initial(255)
 *     decrementPerStep(1)
 *     onLow(50) { showMessage("Torch dimming...") }
 *     onDepleted { setFlag("torchOut") }
 * }
 * ```
 */
@GbktDsl
class GaugeBuilder(private val id: String) {
    private var max: Int = 255
    private var initial: Int = 255
    private var decrementPerStep: Int = 1
    private var onLowThreshold: Int? = null
    private var onLowCallback: (ScriptBuilder.() -> Unit)? = null
    private var onDepletedCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets the maximum gauge value (full charge). */
    fun max(value: Int) {
        max = value
    }

    /** Sets the initial gauge value at exploration start. */
    fun initial(value: Int) {
        initial = value
    }

    /** Sets how much the gauge decreases per player step. */
    fun decrementPerStep(amount: Int) {
        decrementPerStep = amount
    }

    /**
     * Registers a threshold callback — fired when the gauge falls at or below [threshold].
     *
     * @param threshold Value at which the callback fires.
     * @param block Script ops to execute when threshold is reached.
     */
    fun onLow(threshold: Int, block: ScriptBuilder.() -> Unit) {
        onLowThreshold = threshold
        onLowCallback = block
    }

    /** Registers a callback executed when the gauge reaches zero. */
    fun onDepleted(block: ScriptBuilder.() -> Unit) {
        onDepletedCallback = block
    }

    internal fun build(): ExplorationGaugeIR =
        ExplorationGaugeIR(
            id = id,
            max = max,
            initial = initial,
            decrementPerStep = decrementPerStep,
            onLowThreshold = onLowThreshold,
            onLowStatements = onLowCallback?.let { recordStatements(it) } ?: emptyList(),
            onDepletedStatements = onDepletedCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

// =============================================================================
// KEY BUILDER
// =============================================================================

/**
 * Builder for a key-item counter used to unlock doors, chests, and other interactive zone objects.
 *
 * Usage:
 * ```kotlin
 * keys("magic_key") {
 *     max(99)
 *     initial(0)
 * }
 * ```
 */
@GbktDsl
class KeyBuilder(private val id: String) {
    private var max: Int = 99
    private var initial: Int = 0

    /** Sets the maximum number of keys the player can carry. */
    fun max(value: Int) {
        max = value
    }

    /** Sets the number of keys at exploration start. */
    fun initial(value: Int) {
        initial = value
    }

    internal fun build(): ExplorationKeyIR = ExplorationKeyIR(id = id, max = max, initial = initial)
}

// =============================================================================
// ZONE OBJECTS BUILDER
// =============================================================================

/**
 * Builder for interactive objects within a zone (chests, signs, sconces, NPCs, levers).
 *
 * Each object is placed at a tile coordinate and responds to player interaction via the
 * `tryInteractWithObject(x, y)` dispatch function generated per zone.
 *
 * Usage:
 * ```kotlin
 * objects {
 *     chest("chest1", x = 5, y = 3) {
 *         usedFlag("chest1_opened")
 *         onOpen { grantItem("torch") }
 *     }
 *     sign("entrance_sign", x = 2, y = 8) {
 *         onRead { showMessage("Welcome to the dungeon!") }
 *     }
 *     sconce("torch1", x = 4, y = 6) {
 *         onLit { soundEffect("torch_ignite") }
 *     }
 *     npc("elder", x = 10, y = 5) {
 *         onTalk { showDialog("elder_greeting") }
 *     }
 *     lever("gate_lever", x = 3, y = 7) {
 *         onActivate { setFlag("gate_open") }
 *     }
 * }
 * ```
 */
@GbktDsl
class ZoneObjectsBuilder {
    private val objects = mutableListOf<ZoneObjectIR>()

    /** Adds a treasure chest at the given tile coordinates. */
    fun chest(id: String, x: Int, y: Int, block: ChestBuilder.() -> Unit = {}) {
        objects += ChestBuilder(id, x, y).apply(block).build()
    }

    /** Adds an information sign at the given tile coordinates. */
    fun sign(id: String, x: Int, y: Int, block: SignBuilder.() -> Unit = {}) {
        objects += SignBuilder(id, x, y).apply(block).build()
    }

    /** Adds a light sconce at the given tile coordinates. */
    fun sconce(id: String, x: Int, y: Int, block: SconceBuilder.() -> Unit = {}) {
        objects += SconceBuilder(id, x, y).apply(block).build()
    }

    /** Adds an NPC at the given tile coordinates. */
    fun npc(id: String, x: Int, y: Int, block: NpcBuilder.() -> Unit = {}) {
        objects += NpcBuilder(id, x, y).apply(block).build()
    }

    /** Adds a lever at the given tile coordinates. */
    fun lever(id: String, x: Int, y: Int, block: LeverBuilder.() -> Unit = {}) {
        objects += LeverBuilder(id, x, y).apply(block).build()
    }

    internal fun build(): List<ZoneObjectIR> = objects.toList()
}

// -------------------------------------------------------------------------
// Individual object builders
// -------------------------------------------------------------------------

/** Builder for a [ChestObjectIR]. */
@GbktDsl
class ChestBuilder(private val id: String, private val x: Int, private val y: Int) {
    private var usedFlagId: String? = null
    private var onOpenCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets the flag ID that marks this chest as opened (prevents re-opening). */
    fun usedFlag(flagId: String) {
        usedFlagId = flagId
    }

    /** Script ops to execute when the player opens this chest. */
    fun onOpen(block: ScriptBuilder.() -> Unit) {
        onOpenCallback = block
    }

    internal fun build(): ChestObjectIR =
        ChestObjectIR(
            id = id,
            x = x,
            y = y,
            usedFlagId = usedFlagId,
            onInteract = onOpenCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

/** Builder for a [SignObjectIR]. */
@GbktDsl
class SignBuilder(private val id: String, private val x: Int, private val y: Int) {
    private var usedFlagId: String? = null
    private var onReadCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets an optional flag ID (rarely needed for signs, but included for consistency). */
    fun usedFlag(flagId: String) {
        usedFlagId = flagId
    }

    /** Script ops to execute when the player reads this sign. */
    fun onRead(block: ScriptBuilder.() -> Unit) {
        onReadCallback = block
    }

    internal fun build(): SignObjectIR =
        SignObjectIR(
            id = id,
            x = x,
            y = y,
            usedFlagId = usedFlagId,
            onInteract = onReadCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

/** Builder for a [SconceObjectIR]. */
@GbktDsl
class SconceBuilder(private val id: String, private val x: Int, private val y: Int) {
    private var usedFlagId: String? = null
    private var onInteractCallback: (ScriptBuilder.() -> Unit)? = null
    private var onLitCallback: (ScriptBuilder.() -> Unit)? = null
    private var onExtinguishedCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets a flag ID used to persist lit state across sessions. */
    fun usedFlag(flagId: String) {
        usedFlagId = flagId
    }

    /** Script ops executed when the player interacts (toggles lit state). */
    fun onInteract(block: ScriptBuilder.() -> Unit) {
        onInteractCallback = block
    }

    /** Script ops executed when the sconce becomes lit. */
    fun onLit(block: ScriptBuilder.() -> Unit) {
        onLitCallback = block
    }

    /** Script ops executed when the sconce becomes extinguished. */
    fun onExtinguished(block: ScriptBuilder.() -> Unit) {
        onExtinguishedCallback = block
    }

    internal fun build(): SconceObjectIR =
        SconceObjectIR(
            id = id,
            x = x,
            y = y,
            usedFlagId = usedFlagId,
            onInteract = onInteractCallback?.let { recordStatements(it) } ?: emptyList(),
            onLit = onLitCallback?.let { recordStatements(it) } ?: emptyList(),
            onExtinguished = onExtinguishedCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

/** Builder for a [NpcObjectIR]. */
@GbktDsl
class NpcBuilder(private val id: String, private val x: Int, private val y: Int) {
    private var usedFlagId: String? = null
    private var visibleFlagId: String? = null
    private var visibleWhenFlagUnset: Boolean = false
    private var onTalkCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets an optional flag ID set after interaction (for one-time NPCs). */
    fun usedFlag(flagId: String) {
        usedFlagId = flagId
    }

    /** Sets a flag ID controlling NPC visibility. */
    fun visibleFlag(flagId: String, visibleWhenUnset: Boolean = false) {
        visibleFlagId = flagId
        visibleWhenFlagUnset = visibleWhenUnset
    }

    /** Script ops executed when the player talks to this NPC. */
    fun onTalk(block: ScriptBuilder.() -> Unit) {
        onTalkCallback = block
    }

    internal fun build(): NpcObjectIR =
        NpcObjectIR(
            id = id,
            x = x,
            y = y,
            usedFlagId = usedFlagId,
            visibleFlagId = visibleFlagId,
            visibleWhenFlagUnset = visibleWhenFlagUnset,
            onInteract = onTalkCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

/** Builder for a [LeverObjectIR]. */
@GbktDsl
class LeverBuilder(private val id: String, private val x: Int, private val y: Int) {
    private var usedFlagId: String? = null
    private var onInteractCallback: (ScriptBuilder.() -> Unit)? = null
    private var onActivateCallback: (ScriptBuilder.() -> Unit)? = null
    private var onDeactivateCallback: (ScriptBuilder.() -> Unit)? = null

    /** Sets a flag ID used to persist lever state across sessions. */
    fun usedFlag(flagId: String) {
        usedFlagId = flagId
    }

    /** Script ops executed when the lever is toggled in either direction. */
    fun onInteract(block: ScriptBuilder.() -> Unit) {
        onInteractCallback = block
    }

    /** Script ops executed when the lever is turned on. */
    fun onActivate(block: ScriptBuilder.() -> Unit) {
        onActivateCallback = block
    }

    /** Script ops executed when the lever is turned off. */
    fun onDeactivate(block: ScriptBuilder.() -> Unit) {
        onDeactivateCallback = block
    }

    internal fun build(): LeverObjectIR =
        LeverObjectIR(
            id = id,
            x = x,
            y = y,
            usedFlagId = usedFlagId,
            onInteract = onInteractCallback?.let { recordStatements(it) } ?: emptyList(),
            onActivate = onActivateCallback?.let { recordStatements(it) } ?: emptyList(),
            onDeactivate = onDeactivateCallback?.let { recordStatements(it) } ?: emptyList(),
        )
}

// =============================================================================
// SHARED UTILITY — STATEMENT RECORDING
// =============================================================================

/**
 * Records a block of [ScriptBuilder] ops into a [List] of [ScriptOp] IR nodes.
 *
 * Used by [ZoneBuilder], [GaugeBuilder], and [ExplorationBuilder] to convert callback lambdas into
 * IR statement lists. Follows the exact pattern established in [SceneBuilder].
 */
internal fun recordStatements(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
    val builder = ScriptBuilder()
    ScriptBuilderContext.with(builder) { builder.block() }
    return builder.build()
}
