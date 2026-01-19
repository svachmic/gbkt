/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// EXTENSIBLE MAP OBJECT SYSTEM
// =============================================================================

/**
 * Map object type definition.
 *
 * Unlike the hardcoded MapObjectType enum, this allows games to define custom interactive object
 * types for any genre.
 *
 * Usage:
 * ```kotlin
 * // Define custom type
 * val conveyorBelt by mapObjectType {
 *     name("Conveyor Belt")
 *     category(ObjectCategory.ENVIRONMENTAL)
 *     interactable(false)  // Auto-triggers when stepped on
 *     persistent(false)     // No save state needed
 * }
 *
 * // Use in zone/floor
 * map("factory") {
 *     customObject("conveyor1", conveyorBelt) {
 *         position(5, 10)
 *         property("direction", "right")
 *         property("speed", "2")
 *         onStep { movePlayer(direction) }
 *     }
 * }
 * ```
 */
data class MapObjectTypeDefinition(
    /** Unique type identifier */
    val id: String,
    /** Display name for debugging/editors */
    val displayName: String,
    /** Category for grouping in editors */
    val category: ObjectCategory,
    /** Whether the object requires player interaction (A button) */
    val interactable: Boolean,
    /** Whether state should be persisted (needs flag) */
    val persistent: Boolean,
    /** Whether the object blocks movement */
    val solid: Boolean,
    /** Whether the object is visible by default */
    val visible: Boolean,
    /** Whether stepping on it triggers an effect */
    val triggerOnStep: Boolean,
    /** Default sprite (can be overridden per instance) */
    val defaultSprite: SpriteAsset?,
    /** System index for code generation */
    var typeIndex: Int = -1,
)

/**
 * Object categories for organization.
 *
 * These are suggestions - games can use any category.
 */
enum class ObjectCategory {
    /** Interactive containers (chests, barrels, crates) */
    CONTAINER,

    /** Passage/portal objects (doors, stairs, warps) */
    PASSAGE,

    /** Toggle objects (switches, levers, buttons) */
    SWITCH,

    /** Information objects (signs, NPCs, terminals) */
    INFORMATIONAL,

    /** Environment hazards/effects (spikes, ice, conveyor) */
    ENVIRONMENTAL,

    /** Service points (save, heal, shop) */
    SERVICE,

    /** Hidden/secret objects */
    HIDDEN,

    /** Collectible items (coins, pickups) */
    COLLECTIBLE,

    /** Destructible objects (walls, boxes) */
    DESTRUCTIBLE,

    /** Custom category */
    CUSTOM,
}

// =============================================================================
// PREDEFINED OBJECT TYPES (Backward Compatibility)
// =============================================================================

/**
 * Predefined map object types.
 *
 * These provide backward compatibility with the original MapObjectType enum while allowing new
 * games to define custom types.
 */
object PredefinedObjectTypes {
    val CHEST =
        MapObjectTypeDefinition(
            id = "chest",
            displayName = "Chest",
            category = ObjectCategory.CONTAINER,
            interactable = true,
            persistent = true,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val DOOR =
        MapObjectTypeDefinition(
            id = "door",
            displayName = "Door",
            category = ObjectCategory.PASSAGE,
            interactable = true,
            persistent = true,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val LEVER =
        MapObjectTypeDefinition(
            id = "lever",
            displayName = "Lever",
            category = ObjectCategory.SWITCH,
            interactable = true,
            persistent = true,
            solid = false,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val SIGN =
        MapObjectTypeDefinition(
            id = "sign",
            displayName = "Sign",
            category = ObjectCategory.INFORMATIONAL,
            interactable = true,
            persistent = false,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val NPC =
        MapObjectTypeDefinition(
            id = "npc",
            displayName = "NPC",
            category = ObjectCategory.INFORMATIONAL,
            interactable = true,
            persistent = false,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val SCONCE =
        MapObjectTypeDefinition(
            id = "sconce",
            displayName = "Sconce",
            category = ObjectCategory.ENVIRONMENTAL,
            interactable = true,
            persistent = true,
            solid = false,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val SAVE_POINT =
        MapObjectTypeDefinition(
            id = "save_point",
            displayName = "Save Point",
            category = ObjectCategory.SERVICE,
            interactable = true,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val FOUNTAIN =
        MapObjectTypeDefinition(
            id = "fountain",
            displayName = "Fountain",
            category = ObjectCategory.SERVICE,
            interactable = true,
            persistent = false,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val HIDDEN =
        MapObjectTypeDefinition(
            id = "hidden",
            displayName = "Hidden Object",
            category = ObjectCategory.HIDDEN,
            interactable = true,
            persistent = true,
            solid = false,
            visible = false,
            triggerOnStep = false,
            defaultSprite = null,
        )

    // Additional common types for other genres

    val PRESSURE_PLATE =
        MapObjectTypeDefinition(
            id = "pressure_plate",
            displayName = "Pressure Plate",
            category = ObjectCategory.SWITCH,
            interactable = false,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    val CONVEYOR =
        MapObjectTypeDefinition(
            id = "conveyor",
            displayName = "Conveyor Belt",
            category = ObjectCategory.ENVIRONMENTAL,
            interactable = false,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    val SPRING =
        MapObjectTypeDefinition(
            id = "spring",
            displayName = "Spring",
            category = ObjectCategory.ENVIRONMENTAL,
            interactable = false,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    val DESTRUCTIBLE =
        MapObjectTypeDefinition(
            id = "destructible",
            displayName = "Destructible",
            category = ObjectCategory.DESTRUCTIBLE,
            interactable = true,
            persistent = true,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val SPIKE =
        MapObjectTypeDefinition(
            id = "spike",
            displayName = "Spike",
            category = ObjectCategory.ENVIRONMENTAL,
            interactable = false,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    val WARP =
        MapObjectTypeDefinition(
            id = "warp",
            displayName = "Warp Point",
            category = ObjectCategory.PASSAGE,
            interactable = false,
            persistent = false,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    val SHOP =
        MapObjectTypeDefinition(
            id = "shop",
            displayName = "Shop",
            category = ObjectCategory.SERVICE,
            interactable = true,
            persistent = false,
            solid = true,
            visible = true,
            triggerOnStep = false,
            defaultSprite = null,
        )

    val COLLECTIBLE =
        MapObjectTypeDefinition(
            id = "collectible",
            displayName = "Collectible",
            category = ObjectCategory.COLLECTIBLE,
            interactable = false,
            persistent = true,
            solid = false,
            visible = true,
            triggerOnStep = true,
            defaultSprite = null,
        )

    /** All predefined types for registration */
    val all: List<MapObjectTypeDefinition> =
        listOf(
            CHEST,
            DOOR,
            LEVER,
            SIGN,
            NPC,
            SCONCE,
            SAVE_POINT,
            FOUNTAIN,
            HIDDEN,
            PRESSURE_PLATE,
            CONVEYOR,
            SPRING,
            DESTRUCTIBLE,
            SPIKE,
            WARP,
            SHOP,
            COLLECTIBLE,
        )
}

// =============================================================================
// GENERIC MAP OBJECT
// =============================================================================

/**
 * Generic map object that can use any object type.
 *
 * This provides more flexibility than the sealed MapObject classes while maintaining full
 * functionality.
 */
data class GenericMapObject(
    /** Unique object identifier */
    val id: String,
    /** Object type definition */
    val objectType: MapObjectTypeDefinition,
    /** Position on the map */
    val position: MapPosition,
    /** Flag index for persistence (if type is persistent) */
    val flagIndex: Int?,
    /** Sprite override (uses type default if null) */
    val sprite: SpriteAsset?,
    /** Alternative sprite for state change (open/on/lit) */
    val alternateSprite: SpriteAsset?,
    /** Custom properties for game-specific behavior */
    val properties: Map<String, String>,
    /** Callback when player interacts (A button) */
    val onInteractStatements: List<IRStatement>,
    /** Callback when player steps on (if triggerOnStep) */
    val onStepStatements: List<IRStatement>,
    /** Callback when state changes */
    val onStateChangeStatements: List<IRStatement>,
    /** Initial state (on/off, open/closed, etc.) */
    val initialState: Boolean,
    /** System index for code generation */
    var objectIndex: Int = -1,
)

// =============================================================================
// MAP OBJECT TYPE BUILDER
// =============================================================================

/** Property delegate for custom map object types. */
class MapObjectTypeDelegate(
    private val gameBuilder: GameBuilder,
    private val init: MapObjectTypeBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, MapObjectTypeDefinition>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, MapObjectTypeDefinition> {
        val builder = MapObjectTypeBuilder(property.name)
        builder.init()
        val typeDef = builder.build()
        gameBuilder.registerMapObjectType(typeDef)

        return ReadOnlyProperty { _, _ -> typeDef }
    }
}

/** Builder for custom map object types. */
@GbktDsl
class MapObjectTypeBuilder(private val typeId: String) {
    private var displayName: String = typeId.replaceFirstChar { it.uppercaseChar() }
    private var category: ObjectCategory = ObjectCategory.CUSTOM
    private var interactable: Boolean = true
    private var persistent: Boolean = false
    private var solid: Boolean = false
    private var visible: Boolean = true
    private var triggerOnStep: Boolean = false
    private var defaultSprite: SpriteAsset? = null

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set category */
    fun category(cat: ObjectCategory) {
        category = cat
    }

    /** Whether object requires A button interaction */
    fun interactable(value: Boolean) {
        interactable = value
    }

    /** Whether state should be persisted */
    fun persistent(value: Boolean) {
        persistent = value
    }

    /** Whether object blocks movement */
    fun solid(value: Boolean) {
        solid = value
    }

    /** Whether object is visible by default */
    fun visible(value: Boolean) {
        visible = value
    }

    /** Whether stepping on triggers the effect */
    fun triggerOnStep(value: Boolean) {
        triggerOnStep = value
    }

    /** Default sprite for this type */
    fun defaultSprite(sprite: SpriteAsset) {
        defaultSprite = sprite
    }

    internal fun build() =
        MapObjectTypeDefinition(
            id = typeId,
            displayName = displayName,
            category = category,
            interactable = interactable,
            persistent = persistent,
            solid = solid,
            visible = visible,
            triggerOnStep = triggerOnStep,
            defaultSprite = defaultSprite,
        )
}

// =============================================================================
// GENERIC MAP OBJECT BUILDER
// =============================================================================

/** Builder for generic map objects. */
@GbktDsl
class GenericMapObjectBuilder(
    private val objectId: String,
    private val objectType: MapObjectTypeDefinition,
) {
    private var position: MapPosition = MapPosition(0, 0)
    private var flagIndex: Int? = null
    private var sprite: SpriteAsset? = null
    private var alternateSprite: SpriteAsset? = null
    private val properties = mutableMapOf<String, String>()
    private var onInteractStatements: List<IRStatement> = emptyList()
    private var onStepStatements: List<IRStatement> = emptyList()
    private var onStateChangeStatements: List<IRStatement> = emptyList()
    private var initialState: Boolean = false

    /** Set position */
    fun position(x: Int, y: Int) {
        position = MapPosition(x, y)
    }

    /** Set flag index for persistence */
    fun flag(index: Int) {
        flagIndex = index
    }

    /** Set main sprite */
    fun sprite(sprite: SpriteAsset) {
        this.sprite = sprite
    }

    /** Set alternate sprite (for state change) */
    fun alternateSprite(sprite: SpriteAsset) {
        this.alternateSprite = sprite
    }

    /** Set custom property */
    fun property(key: String, value: String) {
        properties[key] = value
    }

    /** Set custom property with int value */
    fun property(key: String, value: Int) {
        properties[key] = value.toString()
    }

    /** Set initial state */
    fun initialState(state: Boolean) {
        initialState = state
    }

    /** Callback when player interacts */
    fun onInteract(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onInteractStatements = recorder.statements
    }

    /** Callback when player steps on */
    fun onStep(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStepStatements = recorder.statements
    }

    /** Callback when state changes */
    fun onStateChange(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStateChangeStatements = recorder.statements
    }

    internal fun build() =
        GenericMapObject(
            id = objectId,
            objectType = objectType,
            position = position,
            flagIndex = flagIndex,
            sprite = sprite,
            alternateSprite = alternateSprite,
            properties = properties.toMap(),
            onInteractStatements = onInteractStatements,
            onStepStatements = onStepStatements,
            onStateChangeStatements = onStateChangeStatements,
            initialState = initialState,
        )
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a custom map object type.
 *
 * For games that need interactive objects beyond the predefined types.
 *
 * Usage:
 * ```kotlin
 * val conveyorBelt by mapObjectType {
 *     name("Conveyor Belt")
 *     category(ObjectCategory.ENVIRONMENTAL)
 *     interactable(false)
 *     triggerOnStep(true)
 * }
 *
 * val iceFloor by mapObjectType {
 *     name("Ice Floor")
 *     category(ObjectCategory.ENVIRONMENTAL)
 *     interactable(false)
 *     triggerOnStep(true)
 * }
 *
 * val crumblingFloor by mapObjectType {
 *     name("Crumbling Floor")
 *     category(ObjectCategory.DESTRUCTIBLE)
 *     persistent(true)  // Remember which have collapsed
 *     triggerOnStep(true)
 * }
 * ```
 */
fun GameBuilder.mapObjectType(init: MapObjectTypeBuilder.() -> Unit): MapObjectTypeDelegate {
    return MapObjectTypeDelegate(this, init)
}
