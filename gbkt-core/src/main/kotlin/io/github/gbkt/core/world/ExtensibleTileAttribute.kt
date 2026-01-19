/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// EXTENSIBLE TILE ATTRIBUTE FRAMEWORK
// =============================================================================

/** Tile behavior flags for extensible tile attributes. */
enum class TileBehavior {
    /** Tile is passable (can walk on) */
    PASSABLE,

    /** Tile blocks movement */
    BLOCKING,

    /** Tile is hazardous (causes damage) */
    HAZARDOUS,

    /** Tile slows movement */
    SLOWING,

    /** Tile causes sliding (ice) */
    SLIDING,

    /** Tile pushes in a direction (conveyor) */
    CONVEYOR,

    /** Tile bounces entities (spring) */
    BOUNCING,

    /** Tile triggers callback on enter */
    TRIGGER_ON_ENTER,

    /** Tile triggers callback on exit */
    TRIGGER_ON_EXIT,

    /** Tile triggers callback while standing */
    TRIGGER_WHILE_ON,

    /** Tile is climbable (ladder, vine) */
    CLIMBABLE,

    /** Tile requires special ability to cross */
    REQUIRES_ABILITY,

    /** Tile is destructible */
    DESTRUCTIBLE,

    /** Tile teleports to another location */
    TELEPORT,

    /** Tile changes elevation */
    ELEVATION_CHANGE,
}

/** Direction for directional tile effects (conveyors, slopes). */
enum class TileDirection {
    NONE,
    NORTH,
    SOUTH,
    EAST,
    WEST,
    NORTH_EAST,
    NORTH_WEST,
    SOUTH_EAST,
    SOUTH_WEST,
}

/**
 * Extensible tile attribute definition.
 *
 * Allows games to define custom tile types beyond the built-in set.
 *
 * Usage:
 * ```kotlin
 * val iceTile by tileAttribute {
 *     name("Ice")
 *     behaviors(TileBehavior.PASSABLE, TileBehavior.SLIDING)
 *     frictionMultiplier(0.1f)  // Very slippery
 * }
 *
 * val conveyorBelt by tileAttribute {
 *     name("Conveyor Belt")
 *     behaviors(TileBehavior.PASSABLE, TileBehavior.CONVEYOR)
 *     direction(TileDirection.EAST)
 *     speed(2)
 * }
 *
 * val spikeTrap by tileAttribute {
 *     name("Spike Trap")
 *     behaviors(TileBehavior.PASSABLE, TileBehavior.HAZARDOUS, TileBehavior.TRIGGER_WHILE_ON)
 *     damage(10)
 *     damageInterval(30)  // Damage every 30 frames
 *     onEnter { playSound("spike_activate") }
 * }
 * ```
 */
data class ExtensibleTileAttributeDefinition(
    /** Unique attribute identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Tile behaviors (flags) */
    val behaviors: Set<TileBehavior>,
    /** Movement speed modifier (1.0 = normal) */
    val speedModifier: Float,
    /** Friction modifier for sliding (0.0 = infinite slide, 1.0 = instant stop) */
    val frictionModifier: Float,
    /** Direction for directional effects */
    val direction: TileDirection,
    /** Speed for conveyor/push effects */
    val conveyorSpeed: Int,
    /** Damage per hit for hazardous tiles */
    val damage: Int,
    /** Frames between damage applications */
    val damageInterval: Int,
    /** Bounce strength for bouncing tiles */
    val bounceStrength: Int,
    /** Required ability ID for REQUIRES_ABILITY tiles */
    val requiredAbility: String?,
    /** Teleport destination coordinates */
    val teleportDestX: Int,
    val teleportDestY: Int,
    val teleportDestFloor: String?,
    /** Elevation change (positive = up, negative = down) */
    val elevationChange: Int,
    /** Callback when entity enters tile */
    val onEnterStatements: List<IRStatement>,
    /** Callback when entity exits tile */
    val onExitStatements: List<IRStatement>,
    /** Callback while entity is on tile (each frame) */
    val onStandingStatements: List<IRStatement>,
    /** Callback when tile is destroyed (DESTRUCTIBLE) */
    val onDestroyStatements: List<IRStatement>,
    /** System index for code generation */
    var attributeIndex: Int = -1,
) {
    /** Whether this tile blocks movement */
    val isBlocking: Boolean
        get() = TileBehavior.BLOCKING in behaviors

    /** Whether this tile is passable */
    val isPassable: Boolean
        get() = TileBehavior.PASSABLE in behaviors

    /** Whether this tile causes damage */
    val isHazardous: Boolean
        get() = TileBehavior.HAZARDOUS in behaviors

    /** Whether this tile has any callbacks */
    val hasCallbacks: Boolean
        get() =
            onEnterStatements.isNotEmpty() ||
                onExitStatements.isNotEmpty() ||
                onStandingStatements.isNotEmpty() ||
                onDestroyStatements.isNotEmpty()
}

// =============================================================================
// TILE ATTRIBUTE BUILDER
// =============================================================================

/** Property delegate for tile attributes. */
class TileAttributeDelegate(
    private val gameBuilder: GameBuilder,
    private val init: TileAttributeBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ExtensibleTileAttributeDefinition>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ExtensibleTileAttributeDefinition> {
        val builder = TileAttributeBuilder(property.name)
        builder.init()
        val attr = builder.build()
        gameBuilder.registerTileAttribute(attr)

        return ReadOnlyProperty { _, _ -> attr }
    }
}

/** Builder for extensible tile attributes. */
@GbktDsl
class TileAttributeBuilder(private val attrId: String) {
    private var displayName: String = attrId.replaceFirstChar { it.uppercaseChar() }
    private val behaviors = mutableSetOf<TileBehavior>()
    private var speedModifier: Float = 1.0f
    private var frictionModifier: Float = 1.0f
    private var direction: TileDirection = TileDirection.NONE
    private var conveyorSpeed: Int = 1
    private var damage: Int = 0
    private var damageInterval: Int = 60
    private var bounceStrength: Int = 8
    private var requiredAbility: String? = null
    private var teleportDestX: Int = 0
    private var teleportDestY: Int = 0
    private var teleportDestFloor: String? = null
    private var elevationChange: Int = 0
    private var onEnterStatements: List<IRStatement> = emptyList()
    private var onExitStatements: List<IRStatement> = emptyList()
    private var onStandingStatements: List<IRStatement> = emptyList()
    private var onDestroyStatements: List<IRStatement> = emptyList()

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Add behaviors */
    fun behaviors(vararg behaviorList: TileBehavior) {
        behaviors.addAll(behaviorList)
    }

    /** Mark as passable (convenience) */
    fun passable() {
        behaviors.add(TileBehavior.PASSABLE)
    }

    /** Mark as blocking (convenience) */
    fun blocking() {
        behaviors.add(TileBehavior.BLOCKING)
    }

    /** Mark as hazardous with damage */
    fun hazardous(damageAmount: Int, intervalFrames: Int = 60) {
        behaviors.add(TileBehavior.HAZARDOUS)
        damage = damageAmount
        damageInterval = intervalFrames
    }

    /** Set speed modifier (1.0 = normal, 0.5 = half speed, 2.0 = double speed) */
    fun speedModifier(modifier: Float) {
        speedModifier = modifier
    }

    /** Convenience for slowing tiles */
    fun slowing(modifier: Float = 0.5f) {
        behaviors.add(TileBehavior.SLOWING)
        speedModifier = modifier
    }

    /** Set friction modifier for sliding (0.0 = infinite slide) */
    fun frictionModifier(modifier: Float) {
        frictionModifier = modifier
    }

    /** Make tile icy/sliding */
    fun sliding(friction: Float = 0.1f) {
        behaviors.add(TileBehavior.SLIDING)
        frictionModifier = friction
    }

    /** Make tile a conveyor belt */
    fun conveyor(dir: TileDirection, speed: Int = 1) {
        behaviors.add(TileBehavior.CONVEYOR)
        direction = dir
        conveyorSpeed = speed
    }

    /** Make tile bouncy */
    fun bouncing(strength: Int = 8) {
        behaviors.add(TileBehavior.BOUNCING)
        bounceStrength = strength
    }

    /** Make tile climbable */
    fun climbable() {
        behaviors.add(TileBehavior.CLIMBABLE)
    }

    /** Require ability to cross */
    fun requiresAbility(abilityId: String) {
        behaviors.add(TileBehavior.REQUIRES_ABILITY)
        requiredAbility = abilityId
    }

    /** Make tile destructible */
    fun destructible() {
        behaviors.add(TileBehavior.DESTRUCTIBLE)
    }

    /** Make tile a teleporter */
    fun teleportTo(x: Int, y: Int, floorId: String? = null) {
        behaviors.add(TileBehavior.TELEPORT)
        teleportDestX = x
        teleportDestY = y
        teleportDestFloor = floorId
    }

    /** Set elevation change */
    fun elevationChange(amount: Int) {
        behaviors.add(TileBehavior.ELEVATION_CHANGE)
        elevationChange = amount
    }

    /** Set direction for directional effects */
    fun direction(dir: TileDirection) {
        direction = dir
    }

    /** Callback when entity enters tile */
    fun onEnter(init: () -> Unit) {
        behaviors.add(TileBehavior.TRIGGER_ON_ENTER)
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onEnterStatements = recorder.statements
    }

    /** Callback when entity exits tile */
    fun onExit(init: () -> Unit) {
        behaviors.add(TileBehavior.TRIGGER_ON_EXIT)
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onExitStatements = recorder.statements
    }

    /** Callback while entity is standing on tile */
    fun onStanding(init: () -> Unit) {
        behaviors.add(TileBehavior.TRIGGER_WHILE_ON)
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStandingStatements = recorder.statements
    }

    /** Callback when tile is destroyed */
    fun onDestroy(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onDestroyStatements = recorder.statements
    }

    internal fun build() =
        ExtensibleTileAttributeDefinition(
            id = attrId,
            displayName = displayName,
            behaviors = behaviors.toSet(),
            speedModifier = speedModifier,
            frictionModifier = frictionModifier,
            direction = direction,
            conveyorSpeed = conveyorSpeed,
            damage = damage,
            damageInterval = damageInterval,
            bounceStrength = bounceStrength,
            requiredAbility = requiredAbility,
            teleportDestX = teleportDestX,
            teleportDestY = teleportDestY,
            teleportDestFloor = teleportDestFloor,
            elevationChange = elevationChange,
            onEnterStatements = onEnterStatements,
            onExitStatements = onExitStatements,
            onStandingStatements = onStandingStatements,
            onDestroyStatements = onDestroyStatements,
        )
}

// =============================================================================
// PREDEFINED TILE ATTRIBUTE PRESETS
// =============================================================================

/**
 * Built-in tile attribute presets for common tile types.
 *
 * Games can use these as starting points or define completely custom attributes.
 */
object TilePresets {
    /** Create a ground tile preset */
    fun ground(): TileAttributeBuilder.() -> Unit = {
        name("Ground")
        passable()
    }

    /** Create a wall tile preset */
    fun wall(): TileAttributeBuilder.() -> Unit = {
        name("Wall")
        blocking()
    }

    /** Create an ice tile preset */
    fun ice(): TileAttributeBuilder.() -> Unit = {
        name("Ice")
        passable()
        sliding(0.05f)
    }

    /** Create a water tile preset */
    fun water(damageOnStanding: Int = 0): TileAttributeBuilder.() -> Unit = {
        name("Water")
        passable()
        slowing(0.5f)
        if (damageOnStanding > 0) {
            hazardous(damageOnStanding, 60)
        }
    }

    /** Create a lava tile preset */
    fun lava(damage: Int = 20): TileAttributeBuilder.() -> Unit = {
        name("Lava")
        passable()
        hazardous(damage, 30)
    }

    /** Create a pit tile preset */
    fun pit(): TileAttributeBuilder.() -> Unit = {
        name("Pit")
        passable()
        hazardous(999, 1) // Instant death
    }

    /** Create a ladder tile preset */
    fun ladder(): TileAttributeBuilder.() -> Unit = {
        name("Ladder")
        passable()
        climbable()
    }

    /** Create a conveyor belt preset */
    fun conveyor(dir: TileDirection, speed: Int = 1): TileAttributeBuilder.() -> Unit = {
        name("Conveyor")
        passable()
        conveyor(dir, speed)
    }

    /** Create a spring/bounce tile preset */
    fun spring(strength: Int = 8): TileAttributeBuilder.() -> Unit = {
        name("Spring")
        passable()
        bouncing(strength)
    }

    /** Create a spike trap preset */
    fun spikes(damage: Int = 10, interval: Int = 60): TileAttributeBuilder.() -> Unit = {
        name("Spikes")
        passable()
        hazardous(damage, interval)
    }

    /** Create a mud/swamp tile preset */
    fun mud(): TileAttributeBuilder.() -> Unit = {
        name("Mud")
        passable()
        slowing(0.3f)
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a custom tile attribute.
 *
 * Usage:
 * ```kotlin
 * val iceTile by tileAttribute {
 *     name("Ice")
 *     passable()
 *     sliding(0.1f)
 * }
 *
 * val spikeTrap by tileAttribute {
 *     name("Spikes")
 *     passable()
 *     hazardous(10, 30)
 *     onEnter { playSound("spike") }
 * }
 * ```
 */
fun GameBuilder.tileAttribute(init: TileAttributeBuilder.() -> Unit): TileAttributeDelegate {
    return TileAttributeDelegate(this, init)
}

// Note: Presets like TilePresets.ice() can be passed directly to tileAttribute()
// since they return the same function type (TileAttributeBuilder.() -> Unit)
