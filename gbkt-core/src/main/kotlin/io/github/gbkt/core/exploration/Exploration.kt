/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.exploration

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.world.Zone
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// EXPLORATION SYSTEM - Unified dungeon/world exploration controller
// =============================================================================

/** Movement style for the exploration system. */
enum class MovementStyle {
    /** Grid-based movement - snap to tiles */
    GRID,

    /** Smooth movement - pixel-based with tile collision */
    SMOOTH,
}

/** Player movement state during exploration. */
enum class MovementState {
    /** Not moving */
    IDLE,

    /** Currently moving between tiles */
    WALKING,

    /** Movement blocked by collision */
    BLOCKED,

    /** Interacting with something (NPC, chest, etc.) */
    INTERACTING,

    /** In transition (door, stairs, etc.) */
    TRANSITIONING,
}

/** A resource gauge tracked during exploration (torch, health, etc.). */
data class ExplorationGauge(
    /** Gauge identifier */
    val id: String,
    /** Maximum value */
    val maxValue: Int,
    /** Initial value */
    val initialValue: Int,
    /** Amount to decrement per step (0 = no decrement) */
    val decrementPerStep: Int,
    /** Amount to decrement per frame (0 = no decrement) */
    val decrementPerFrame: Int,
    /** Callback when gauge reaches zero */
    val onDepletedStatements: List<IRStatement>,
    /** Callback when gauge falls below threshold */
    val onLowStatements: List<IRStatement>,
    /** Low threshold value */
    val lowThreshold: Int,
)

/** A key/collectible counter tracked during exploration. */
data class ExplorationKey(
    /** Key identifier */
    val id: String,
    /** Maximum count */
    val maxCount: Int,
    /** Initial count */
    val initialCount: Int,
)

/**
 * Exploration system configuration.
 *
 * Ties together movement, collision, encounters, and floor transitions into a unified controller.
 *
 * Usage:
 * ```kotlin
 * val exploration by exploration {
 *     tileSize(8)
 *     movementSpeed(4)  // frames per tile
 *     movementStyle(MovementStyle.GRID)
 *
 *     gauge("torch") {
 *         max(255)
 *         initial(255)
 *         decrementPerStep(1)
 *         onDepleted { setFlag("torchOut", true) }
 *         onLow(50) { showMessage("Torch is getting dim...") }
 *     }
 *
 *     keys("magic_key") { max(99) }
 *
 *     onStep { checkEncounter() }
 *     onInteract { checkMapObject() }
 *     onBlocked { playSound(bump) }
 * }
 * ```
 */
class Exploration(
    /** Unique identifier */
    val id: String,
    /** Tile size in pixels */
    val tileSize: Int,
    /** Movement speed (frames per tile for GRID, pixels per frame for SMOOTH) */
    val movementSpeed: Int,
    /** Movement style */
    val movementStyle: MovementStyle,
    /** Whether wall collision is enabled */
    val wallCollisionEnabled: Boolean,
    /** Whether water tiles block movement (or require swimming) */
    val waterBlocks: Boolean,
    /** Whether pit tiles cause damage/fall */
    val pitDamage: Int,
    /** Resource gauges */
    val gauges: List<ExplorationGauge>,
    /** Key/collectible counters */
    val keys: List<ExplorationKey>,
    /** Callback on each step completion */
    val onStepStatements: List<IRStatement>,
    /** Callback when movement is blocked */
    val onBlockedStatements: List<IRStatement>,
    /** Callback on interact button press */
    val onInteractStatements: List<IRStatement>,
    /** Callback on entering water */
    val onWaterStatements: List<IRStatement>,
    /** Callback on falling into pit */
    val onPitStatements: List<IRStatement>,
    /** Starting zone/floor reference */
    val startZone: Zone?,
    /** Player sprite to update during movement (for smooth position interpolation) */
    val playerSprite: Sprite?,
    /** System index for code generation */
    var systemIndex: Int = -1,
)

// =============================================================================
// EXPLORATION BUILDER
// =============================================================================

/** Property delegate for exploration systems. */
class ExplorationDelegate(
    private val gameBuilder: GameBuilder,
    private val init: ExplorationBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Exploration>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Exploration> {
        val builder = ExplorationBuilder(property.name)
        builder.init()
        val exploration = builder.build()
        gameBuilder.registerExploration(exploration)

        return ReadOnlyProperty { _, _ -> exploration }
    }
}

/** Builder for exploration systems. */
@GbktDsl
class ExplorationBuilder(private val explorationId: String) {
    private var tileSize: Int = 8
    private var movementSpeed: Int = 4
    private var movementStyle: MovementStyle = MovementStyle.GRID
    private var wallCollisionEnabled: Boolean = true
    private var waterBlocks: Boolean = true
    private var pitDamage: Int = 10
    private val gauges = mutableListOf<ExplorationGauge>()
    private val keys = mutableListOf<ExplorationKey>()
    private var onStepStatements: List<IRStatement> = emptyList()
    private var onBlockedStatements: List<IRStatement> = emptyList()
    private var onInteractStatements: List<IRStatement> = emptyList()
    private var onWaterStatements: List<IRStatement> = emptyList()
    private var onPitStatements: List<IRStatement> = emptyList()
    private var startZone: Zone? = null
    private var playerSprite: Sprite? = null

    /** Set the tile size in pixels */
    fun tileSize(size: Int) {
        require(size in 1..32) { "Tile size must be between 1 and 32" }
        tileSize = size
    }

    /** Set the movement speed */
    fun movementSpeed(speed: Int) {
        require(speed > 0) { "Movement speed must be positive" }
        movementSpeed = speed
    }

    /** Set the movement style */
    fun movementStyle(style: MovementStyle) {
        movementStyle = style
    }

    /** Enable/disable wall collision */
    fun wallCollision(enabled: Boolean) {
        wallCollisionEnabled = enabled
    }

    /** Set whether water blocks movement */
    fun waterBlocks(blocks: Boolean) {
        waterBlocks = blocks
    }

    /** Set pit damage amount */
    fun pitDamage(damage: Int) {
        pitDamage = damage
    }

    /** Set the starting zone */
    fun startZone(zone: Zone) {
        startZone = zone
    }

    /**
     * Set the player sprite for position updates during movement.
     *
     * When set, the exploration system will automatically call `move_sprite()` to update the player
     * sprite's position during movement interpolation. This enables smooth visual movement during
     * tile transitions.
     *
     * Usage:
     * ```kotlin
     * val playerSprite by sprite(SpriteAsset("player.png")) { ... }
     *
     * exploration {
     *     playerSprite(playerSprite)
     *     movementStyle(MovementStyle.SMOOTH)
     *     // ...
     * }
     * ```
     */
    fun playerSprite(sprite: Sprite) {
        playerSprite = sprite
    }

    /** Define a resource gauge */
    fun gauge(id: String, init: GaugeBuilder.() -> Unit) {
        val builder = GaugeBuilder(id)
        builder.init()
        gauges.add(builder.build())
    }

    /** Define a key/collectible counter */
    fun keys(id: String, init: KeyBuilder.() -> Unit = {}) {
        val builder = KeyBuilder(id)
        builder.init()
        keys.add(builder.build())
    }

    /** Callback on each completed step */
    fun onStep(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStepStatements = recorder.statements
    }

    /** Callback when movement is blocked */
    fun onBlocked(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onBlockedStatements = recorder.statements
    }

    /** Callback on interact button press */
    fun onInteract(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onInteractStatements = recorder.statements
    }

    /** Callback when entering water */
    fun onWater(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onWaterStatements = recorder.statements
    }

    /** Callback when falling into pit */
    fun onPit(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onPitStatements = recorder.statements
    }

    internal fun build() =
        Exploration(
            id = explorationId,
            tileSize = tileSize,
            movementSpeed = movementSpeed,
            movementStyle = movementStyle,
            wallCollisionEnabled = wallCollisionEnabled,
            waterBlocks = waterBlocks,
            pitDamage = pitDamage,
            gauges = gauges.toList(),
            keys = keys.toList(),
            onStepStatements = onStepStatements,
            onBlockedStatements = onBlockedStatements,
            onInteractStatements = onInteractStatements,
            onWaterStatements = onWaterStatements,
            onPitStatements = onPitStatements,
            startZone = startZone,
            playerSprite = playerSprite,
        )
}

/** Builder for exploration gauges. */
@GbktDsl
class GaugeBuilder(private val gaugeId: String) {
    private var maxValue: Int = 255
    private var initialValue: Int = 255
    private var decrementPerStep: Int = 0
    private var decrementPerFrame: Int = 0
    private var onDepletedStatements: List<IRStatement> = emptyList()
    private var onLowStatements: List<IRStatement> = emptyList()
    private var lowThreshold: Int = 0

    /** Set the maximum value */
    fun max(value: Int) {
        require(value in 1..65535) { "Max value must be between 1 and 65535" }
        maxValue = value
    }

    /** Set the initial value */
    fun initial(value: Int) {
        require(value >= 0) { "Initial value must be non-negative" }
        initialValue = value
    }

    /** Set decrement per step */
    fun decrementPerStep(amount: Int) {
        require(amount >= 0) { "Decrement must be non-negative" }
        decrementPerStep = amount
    }

    /** Set decrement per frame */
    fun decrementPerFrame(amount: Int) {
        require(amount >= 0) { "Decrement must be non-negative" }
        decrementPerFrame = amount
    }

    /** Callback when gauge is depleted */
    fun onDepleted(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onDepletedStatements = recorder.statements
    }

    /** Callback when gauge falls below threshold */
    fun onLow(threshold: Int, init: () -> Unit) {
        lowThreshold = threshold
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onLowStatements = recorder.statements
    }

    internal fun build() =
        ExplorationGauge(
            id = gaugeId,
            maxValue = maxValue,
            initialValue = initialValue,
            decrementPerStep = decrementPerStep,
            decrementPerFrame = decrementPerFrame,
            onDepletedStatements = onDepletedStatements,
            onLowStatements = onLowStatements,
            lowThreshold = lowThreshold,
        )
}

/** Builder for exploration keys. */
@GbktDsl
class KeyBuilder(private val keyId: String) {
    private var maxCount: Int = 99
    private var initialCount: Int = 0

    /** Set the maximum count */
    fun max(count: Int) {
        require(count in 1..255) { "Max count must be between 1 and 255" }
        maxCount = count
    }

    /** Set the initial count */
    fun initial(count: Int) {
        require(count >= 0) { "Initial count must be non-negative" }
        initialCount = count
    }

    internal fun build() =
        ExplorationKey(id = keyId, maxCount = maxCount, initialCount = initialCount)
}

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/** Create an exploration system. */
fun GameBuilder.exploration(init: ExplorationBuilder.() -> Unit): ExplorationDelegate {
    return ExplorationDelegate(this, init)
}

// =============================================================================
// EXPLORATION DSL FUNCTIONS
// =============================================================================

/**
 * Refill the torch fuel to the specified value.
 *
 * Used in sconce onLit callbacks and other light source interactions to restore the player's torch.
 *
 * Usage:
 * ```kotlin
 * sconce("torch1") {
 *     position(10, 5)
 *     startsLit(true)
 *     onLit { refillTorch(100) }
 * }
 * ```
 *
 * @param value The torch fuel value to set (0-255)
 */
fun refillTorch(value: Int) {
    require(value in 0..255) { "Torch fuel value must be between 0 and 255, got $value" }
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRSetTorchFuel(value))
    }
}

/**
 * Try to interact with a map object at the specified position.
 *
 * Checks if there's an interactable object (chest, NPC, sign, door, etc.) at the given coordinates
 * on the specified floor, and if so, triggers its interaction handler.
 *
 * Usage:
 * ```kotlin
 * // In gameplay scene
 * whenever(buttons.a.pressed) {
 *     tryInteractWithObject(state.currentFloor, state.playerX, state.playerY)
 * }
 * ```
 *
 * @param floor The floor ID expression
 * @param x The X coordinate expression
 * @param y The Y coordinate expression
 */
fun tryInteractWithObject(
    floor: io.github.gbkt.core.ir.AssignableExpr,
    x: io.github.gbkt.core.ir.AssignableExpr,
    y: io.github.gbkt.core.ir.AssignableExpr,
) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRTryObjectInteraction(floor.ir, x.ir, y.ir))
    }
}
