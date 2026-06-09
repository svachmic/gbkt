/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.EnvelopeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.SfxPriority
import io.github.gbkt.core.ir.SoundChannel
import io.github.gbkt.core.ir.SoundEffectDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.core.ir.SoundRegisters
import io.github.gbkt.core.ir.SweepConfig
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// EXPLORATION PRESET
// =============================================================================

/**
 * Pre-configured exploration styles that set sensible defaults for common use cases.
 *
 * Calling [ExplorationBuilder.preset] applies these defaults. Every setting remains individually
 * overridable after calling preset — the preset just sets initial values.
 */
enum class ExplorationPreset {
    /**
     * Classic dungeon-crawling configuration.
     *
     * Defaults: tileSize=8, movementStyle="GRID", movementSpeed=8, plus a torch gauge (id="torch",
     * max=255, initial=255, decrementPerStep=1).
     */
    DUNGEON_CRAWLER
}

// =============================================================================
// CAMERA SYSTEM BUILDER
// =============================================================================

/**
 * Builder for the camera follow/pan/shake system.
 *
 * Configure [smoothing] (0.0–1.0) for smooth follow behavior and an optional follow target via
 * [follow]. Use [bounds] to clamp the camera to a map region.
 *
 * Usage:
 * ```kotlin
 * camera {
 *     follow(hero)              // actor ref
 *     bounds(256, 256)          // 256x256 pixel map
 *     smoothing = 0.2f
 * }
 * ```
 */
@GbktDsl
class CameraBuilder(private val id: String = "camera") {
    /** Smoothing factor for camera follow. 0.0 = instant, 1.0 = no movement. */
    var smoothing: Float = 0.0f

    private var followTarget: String? = null
    private var boundsW: Int? = null
    private var boundsH: Int? = null

    /**
     * Sets the actor to follow by [ActorRef].
     *
     * The camera will center on this actor every frame. Use [bounds] to restrict the camera from
     * scrolling outside the map boundaries.
     */
    fun follow(actor: ActorRef) {
        followTarget = actor.id
    }

    /**
     * Sets the actor to follow by actor ID string.
     *
     * String-based overload for cases where an [ActorRef] is not available.
     */
    fun follow(actorId: String) {
        followTarget = actorId
    }

    /**
     * Clamps the camera to a map region of the given pixel dimensions.
     *
     * The camera will not scroll past the map edges. Use INT16 intermediate math internally to
     * avoid UINT8 underflow near the left/top edges.
     *
     * @param mapWidth Total map width in pixels (e.g. 256 for a 32-tile wide map with 8px tiles).
     * @param mapHeight Total map height in pixels (e.g. 256 for a 32-tile tall map with 8px tiles).
     */
    fun bounds(mapWidth: Int, mapHeight: Int) {
        boundsW = mapWidth
        boundsH = mapHeight
    }

    internal fun build(): CameraSystem =
        CameraSystem(
            id = id,
            followActorId = followTarget,
            boundsWidth = boundsW,
            boundsHeight = boundsH,
            smoothing = smoothing,
        )
}

// =============================================================================
// SAVE DATA BUILDER
// =============================================================================

/**
 * Builder for the save/load game state system.
 *
 * Configure number of save [slots], [checksum] verification, and format [version]. Variables marked
 * as transient via `u8Var(transient = true)` are automatically excluded from the SRAM layout by
 * reading [GameBuilderContext.transientVarNames] at build time.
 *
 * Usage:
 * ```kotlin
 * saveData("saves") {
 *     slots(3)           // 3 independent save slots in SRAM
 *     checksum()         // enable 8-bit checksum verification on load
 *     version(2)         // save format version for future migration
 * }
 * // In game block:
 * var tempScore by u8Var(0, transient = true)   // excluded from save
 * ```
 */
@GbktDsl
class SaveDataBuilder(private val id: String) {
    private var slotCount: Int = 1
    private var checksumEnabled: Boolean = false
    private var formatVersion: Int = 1

    /** Sets the number of independent save slots in SRAM. Slot N starts at offset N * slotSize. */
    fun slots(count: Int) {
        slotCount = count
    }

    /** Enables 8-bit rolling checksum verification on load. Detects save data corruption. */
    fun checksum(enabled: Boolean = true) {
        checksumEnabled = enabled
    }

    /** Sets the save format version number (for future migration support). */
    fun version(v: Int) {
        formatVersion = v
    }

    internal fun build(): SaveSystem =
        SaveSystem(
            id = id,
            slots = slotCount,
            useChecksum = checksumEnabled,
            version = formatVersion,
            transientVarNames = GameBuilderContext.transientVarNames.toSet(),
        )
}

// =============================================================================
// EXPLORATION SYSTEM BUILDER
// =============================================================================

/**
 * Builder for the dungeon exploration / overworld map system.
 *
 * Configures tile size, movement style, speed, start zone, gauges, keys, and lifecycle callbacks.
 *
 * Use [preset] for a pre-configured starting point (e.g., [ExplorationPreset.DUNGEON_CRAWLER]),
 * then override individual settings as needed.
 *
 * Usage:
 * ```kotlin
 * exploration {
 *     preset(ExplorationPreset.DUNGEON_CRAWLER)   // sets tile=8, grid, torch gauge
 *     startZone(floor1)
 *     onStep { checkEncounter("battle") }
 *     onBlocked { sounds.bump.play() }
 * }
 * ```
 */
@GbktDsl
class ExplorationBuilder(private val id: String = "exploration") {
    /** Tile size in pixels (e.g. 8 for 8x8 tiles). All defaults preserve backward compatibility. */
    var tileSize: Int = 8

    /** Movement style: "GRID" for tile-by-tile or "SMOOTH" for free movement. */
    var movementStyle: String = "GRID"

    /** Frames per tile for grid movement. Lower = faster. */
    var movementSpeed: Int = 8

    /** Starting zone/floor ID (string overload). Prefer [startZone] with [ZoneRef]. */
    var startZone: String? = null

    private var stepCallback: (ScriptBuilder.() -> Unit)? = null
    private var blockedCallback: (ScriptBuilder.() -> Unit)? = null
    private var interactCallback: (ScriptBuilder.() -> Unit)? = null
    private val gaugeBuilders = mutableListOf<GaugeBuilder>()
    private val keyBuilders = mutableListOf<KeyBuilder>()

    /**
     * Applies a pre-configured exploration preset as a starting-point baseline.
     *
     * All settings remain individually overridable after calling [preset]. Applying
     * [ExplorationPreset.DUNGEON_CRAWLER] then `tileSize = 16` results in tileSize=16 with all
     * other dungeon-crawler defaults.
     *
     * Preset effects by value:
     * - [ExplorationPreset.DUNGEON_CRAWLER]: tileSize=8, movementStyle=GRID, movementSpeed=8, adds
     *   a default torch gauge (id="torch", max=255, initial=255, decrementPerStep=1).
     */
    fun preset(p: ExplorationPreset) {
        when (p) {
            ExplorationPreset.DUNGEON_CRAWLER -> {
                tileSize = 8
                movementStyle = "GRID"
                movementSpeed = 8
                // Add default torch gauge — individual override possible by calling
                // gauge("torch"){..}
                gaugeBuilders +=
                    GaugeBuilder("torch").apply {
                        max(255)
                        initial(255)
                        decrementPerStep(1)
                    }
            }
        }
    }

    /** Sets the starting zone via a [ZoneRef]. */
    fun startZone(zone: ZoneRef) {
        startZone = zone.id
    }

    /** Registers a callback to run on each player step. */
    fun onStep(block: ScriptBuilder.() -> Unit) {
        stepCallback = block
    }

    /** Registers a callback to run when the player is blocked by a tile or entity. */
    fun onBlocked(block: ScriptBuilder.() -> Unit) {
        blockedCallback = block
    }

    /** Registers a callback to run when the player presses the interact button. */
    fun onInteract(block: ScriptBuilder.() -> Unit) {
        interactCallback = block
    }

    /**
     * Adds a resource gauge that decrements per exploration step (e.g., torch, stamina).
     *
     * @param id Gauge identifier used in generated globals and DSL callbacks.
     * @param block Configuration block for [GaugeBuilder].
     */
    fun gauge(id: String, block: GaugeBuilder.() -> Unit) {
        gaugeBuilders += GaugeBuilder(id).apply(block)
    }

    /**
     * Adds a key-item counter for unlocking doors and chests.
     *
     * @param id Key type identifier.
     * @param block Configuration block for [KeyBuilder].
     */
    fun keys(id: String, block: KeyBuilder.() -> Unit) {
        keyBuilders += KeyBuilder(id).apply(block)
    }

    internal fun build(): ExplorationSystem =
        ExplorationSystem(
            id = id,
            tileSize = tileSize,
            movementStyle = movementStyle,
            movementSpeed = movementSpeed,
            startZoneId = startZone,
            stepStatements = stepCallback?.let { recordStatements(it) } ?: emptyList(),
            blockedStatements = blockedCallback?.let { recordStatements(it) } ?: emptyList(),
            interactStatements = interactCallback?.let { recordStatements(it) } ?: emptyList(),
            gauges = gaugeBuilders.reversed().map { it.build() }.distinctBy { it.id }.reversed(),
            keys = keyBuilders.map { it.build() },
        )
}

// =============================================================================
// PATHFINDING SYSTEM BUILDER
// =============================================================================

/**
 * Builder for the grid-based A* pathfinding system.
 *
 * Configures the A* infrastructure: tile size, map dimensions, and WRAM budget.
 *
 * Usage:
 * ```kotlin
 * pathfinding {
 *     gridSize(8)            // 8x8 pixel tiles
 *     mapSize(32, 32)        // 32x32 tile map
 *     maxOpenNodes(32)       // A* open list capacity
 *     maxPathLength(32)      // max path steps
 * }
 * ```
 */
@GbktDsl
class PathfindingBuilder(private val id: String = "pathfinding") {
    private var gridSize: Int = 8
    private var mapW: Int = 32
    private var mapH: Int = 32
    private var maxOpen: Int = 32
    private var maxPath: Int = 32

    /** Sets the tile size in pixels (used to convert pixel positions to tile coordinates). */
    fun gridSize(px: Int) {
        gridSize = px
    }

    /** Sets the map dimensions in tiles (used for bit-packed closed set sizing). */
    fun mapSize(widthTiles: Int, heightTiles: Int) {
        mapW = widthTiles
        mapH = heightTiles
    }

    /** Sets the A* open list capacity (maxOpenNodes * 4 bytes of WRAM). */
    fun maxOpenNodes(count: Int) {
        maxOpen = count
    }

    /** Sets the maximum path length in steps (maxPathLength * 2 bytes of WRAM). */
    fun maxPathLength(length: Int) {
        maxPath = length
    }

    internal fun build(): PathfindingSystem =
        PathfindingSystem(
            id = id,
            gridSize = gridSize,
            mapWidth = mapW,
            mapHeight = mapH,
            maxOpenNodes = maxOpen,
            maxPathLength = maxPath,
        )
}

// =============================================================================
// SOUND EFFECT BUILDER
// =============================================================================

/**
 * Builder for a sound effect entry in the sound system.
 *
 * Configure via [preset] for a predefined waveform, or set fields manually for custom sounds.
 *
 * Usage with preset:
 * ```kotlin
 * val bump by soundEffect { preset(SoundPreset.BUMP) }
 * ```
 */
@GbktDsl
class SoundEffectBuilder(private val id: String) {
    private var selectedPreset: SoundPreset? = null
    private var channel: SoundChannel = SoundChannel.PULSE1
    private var registers: SoundRegisters = SoundRegisters()
    private var priorityValue: SfxPriority = SfxPriority.MEDIUM

    /** Selects a predefined sound preset. Overrides any manual register configuration. */
    fun preset(p: SoundPreset) {
        selectedPreset = p
    }

    /**
     * Manually set the audio channel (for custom sound effects without a preset).
     *
     * Defaults to [SoundChannel.PULSE1] if not set.
     */
    fun channel(ch: SoundChannel) {
        channel = ch
    }

    /**
     * Manually set the register values (for custom sound effects without a preset).
     *
     * Ignored when [preset] is set.
     */
    fun registers(block: SoundRegistersBuilder.() -> Unit) {
        registers = SoundRegistersBuilder().apply(block).build()
    }

    /**
     * Sets the priority level for AudioMixer channel preemption.
     *
     * Higher priority sounds preempt lower priority sounds on the same channel group. Defaults to
     * [SfxPriority.MEDIUM].
     */
    fun priority(p: SfxPriority) {
        priorityValue = p
    }

    internal fun buildSoundEffectDef(): SoundEffectDef {
        val preset = selectedPreset
        return if (preset != null) {
            SoundEffectDef.fromPreset(id, preset).copy(priority = priorityValue)
        } else {
            SoundEffectDef(
                id = id,
                channel = channel,
                registers = registers,
                priority = priorityValue,
            )
        }
    }
}

/**
 * Typed reference to a declared sound effect.
 *
 * Returned by the `soundEffect()` delegate when the game DSL property is initialized:
 * ```kotlin
 * val hit by soundEffect { preset(SoundPreset.HIT) }
 * ```
 *
 * Use a [SoundRef] in [ScriptBuilder.playSound] or [MenuBuilder.sfx] for type-safe sound effect
 * references that the compiler can verify.
 */
data class SoundRef(val id: String)

/**
 * Property delegate that registers a [SoundEffectDef] in the current [GameBuilder] and provides a
 * [SoundRef] to the property.
 *
 * Created by the top-level [soundEffect] function:
 * ```kotlin
 * val hit by soundEffect { preset(SoundPreset.HIT) }
 * val jump by soundEffect(SoundPreset.JUMP)
 * ```
 *
 * The property name is captured via [provideDelegate] and used as the sound effect ID.
 */
class SoundEffectDelegate(private val block: SoundEffectBuilder.() -> Unit) {
    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name, registers the
     * [SoundEffectDef] with the active [GameBuilder], and returns a [ReadOnlyProperty] that yields
     * [SoundRef].
     *
     * @throws IllegalStateException if called outside a `game {}` block.
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, SoundRef> {
        val id = property.name
        val builder = SoundEffectBuilder(id)
        builder.block()
        val def = builder.buildSoundEffectDef()
        GameBuilderContext.current?.registerSoundEffect(def)
            ?: error("soundEffect() called outside a game {} block")
        return ReadOnlyProperty { _, _ -> SoundRef(id) }
    }
}

/**
 * Declares a sound effect using a builder block. The property name becomes the sound effect ID.
 *
 * ```kotlin
 * val hit by soundEffect { preset(SoundPreset.HIT) }
 * ```
 */
fun soundEffect(block: SoundEffectBuilder.() -> Unit): SoundEffectDelegate =
    SoundEffectDelegate(block)

/**
 * Declares a sound effect from a preset directly (no block needed). The property name becomes the
 * sound effect ID.
 *
 * ```kotlin
 * val hit by soundEffect(SoundPreset.HIT)
 * ```
 */
fun soundEffect(preset: SoundPreset): SoundEffectDelegate = SoundEffectDelegate { preset(preset) }

/** Builder for manually specifying [SoundRegisters] fields. */
@GbktDsl
class SoundRegistersBuilder {
    var frequency: Int = 0
    var length: Int = 0
    var trigger: Boolean = true
    var lengthEnable: Boolean = false
    var envelope: EnvelopeConfig? = null
    var sweep: SweepConfig? = null
    var noiseClockShift: Int = 0
    var noiseDivisor: Int = 0
    var noiseWidthMode: Boolean = false
    var waveOutputLevel: Int = 2
    var waveform: ByteArray? = null

    internal fun build() =
        SoundRegisters(
            frequency = frequency,
            length = length,
            trigger = trigger,
            lengthEnable = lengthEnable,
            envelope = envelope,
            sweep = sweep,
            noiseClockShift = noiseClockShift,
            noiseDivisor = noiseDivisor,
            noiseWidthMode = noiseWidthMode,
            waveOutputLevel = waveOutputLevel,
            waveform = waveform,
        )
}

// =============================================================================
// CONFIG BUILDER
// =============================================================================

/**
 * Builder for cartridge hardware configuration.
 *
 * Maps to [io.github.gbkt.core.ir.CartridgeConfig].
 */
@GbktDsl
class ConfigBuilder {
    /** Cartridge hardware type. Defaults to [Cartridge.ROM_ONLY]. */
    var cartridge: Cartridge = Cartridge.ROM_ONLY

    /** Number of ROM banks. Null means derive automatically from BankingAnalysisPass (D-05). */
    var romBanks: Int? = null

    /** Number of RAM banks. */
    var ramBanks: Int = 0

    /**
     * GBC compatibility target — controls GBDK compiler flags via gbkt-build.properties.
     *
     * Defaults to [GbcTarget.DMG] (classic grayscale). Set to [GbcTarget.GBC_COMPATIBLE] for games
     * that run on both DMG and GBC, or [GbcTarget.GBC_ONLY] for GBC-exclusive games.
     */
    var gbcTarget: GbcTarget = GbcTarget.DMG

    /**
     * Sets the GBC compatibility target mode.
     *
     * Usage:
     * ```kotlin
     * config {
     *     target(GbcTarget.GBC_COMPATIBLE)  // Runs on both DMG and GBC
     *     target(GbcTarget.GBC_ONLY)        // GBC exclusive
     * }
     * ```
     */
    fun target(mode: GbcTarget) {
        gbcTarget = mode
    }

    /**
     * Sets the cartridge hardware type.
     *
     * Usage:
     * ```kotlin
     * config {
     *     cartridge(Cartridge.MBC5_RAM_BATTERY)
     * }
     * ```
     */
    fun cartridge(type: Cartridge) {
        this.cartridge = type
    }

    internal fun build() =
        io.github.gbkt.core.ir.CartridgeConfig(
            cartridge = cartridge,
            romBanks = romBanks,
            ramBanks = ramBanks,
            gbcTarget = gbcTarget,
        )
}

// =============================================================================
// SYSTEM REF (marker interface + typed references)
// =============================================================================

/**
 * Marker interface for typed system references.
 *
 * Implementations: [SaveDataRef] (and future system kinds).
 * Consumed by [ScriptBuilder.triggerSystem] to resolve the system id at DSL recording time.
 */
interface SystemRef {
    val systemId: String
}

/**
 * Typed reference to a save/load system registered via [saveData].
 *
 * Returned by [SaveDataDelegate] when `val saves by saveData { }` is evaluated inside a
 * `game { }` block. The [id] is inferred from the Kotlin property name (Project Rule #1).
 */
data class SaveDataRef(val id: String) : SystemRef {
    override val systemId: String get() = id
}

// =============================================================================
// SAVE DATA DELEGATE
// =============================================================================

/**
 * Kotlin property delegate that infers the [SaveSystem] id from the property name.
 *
 * Used via:
 * ```kotlin
 * val saves by saveData { slots(2) }
 * ```
 *
 * The delegate captures the property name in [provideDelegate], builds a [SaveDataBuilder],
 * and registers the resulting [SaveSystem] with the enclosing [GameBuilder]. The [getValue]
 * method returns a [SaveDataRef] for use in [ScriptBuilder.triggerSystem].
 *
 * @see saveData
 * @see SaveDataRef
 */
/**
 * Single-use: each `val x by saveData { }` binding must use its own delegate instance.
 * Reusing one instance across two `by` bindings throws [IllegalStateException] at build time.
 */
class SaveDataDelegate(private val block: SaveDataBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, SaveDataRef> {
    private var ref: SaveDataRef? = null

    /**
     * Single-use guard. Prevents silent double-registration when the same delegate instance
     * is accidentally bound to two `val` properties.
     */
    private var delegateUsed: Boolean = false

    /**
     * Called by Kotlin when `val x by saveData { ... }` is evaluated.
     *
     * Captures the property name, builds the [SaveSystem], registers it with the current
     * [GameBuilder], and stores the resulting [SaveDataRef] for retrieval by [getValue].
     *
     * @throws IllegalStateException if called outside a `game { }` block or if the delegate
     *   instance is reused across two `val` bindings.
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, SaveDataRef> {
        check(!delegateUsed) {
            "SaveDataDelegate instance reused: was already bound to '${ref?.systemId ?: "<unknown>"}'. " +
                "Each 'val x by saveData { }' must use its own delegate instance."
        }
        delegateUsed = true
        val name = property.name
        val gameBuilder = GameBuilderContext.current
            ?: error("saveData {} must be called inside a game {} block")
        val builder = SaveDataBuilder(name)
        builder.block()
        val system = builder.build()
        gameBuilder.registerSaveData(system)
        ref = SaveDataRef(name)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): SaveDataRef =
        ref ?: error("SaveDataDelegate not initialized — was provideDelegate called?")
}

/**
 * Creates a [SaveDataDelegate] for use with the `by` keyword inside a `game { }` block.
 *
 * The save system id is inferred from the Kotlin property name (Project Rule #1 — no magic
 * string parameter). If the compiler warns about the unused binding, add
 * `@file:Suppress("UNUSED_VARIABLE")` at the top of your game file.
 *
 * Usage:
 * ```kotlin
 * val saves by saveData { slots(2) }
 * ```
 *
 * Single-use: each `by saveData { }` binding must use its own delegate instance.
 *
 * @see SaveDataDelegate
 */
fun saveData(block: SaveDataBuilder.() -> Unit): SaveDataDelegate = SaveDataDelegate(block)
