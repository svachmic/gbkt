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
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ZONE - Generic area/region abstraction
// =============================================================================

/**
 * Zone type for different game genres.
 *
 * Zones can represent different types of areas depending on the game:
 * - DUNGEON: Multi-floor dungeon crawler (roguelikes, dungeon crawlers)
 * - OVERWORLD: Open world map with regions (Zelda, Pokemon)
 * - SIDE_SCROLLING: Horizontal levels (platformers, beat-em-ups)
 * - ARENA: Single-area combat zone (fighting games, arena survival)
 * - ROOM: Single screen room (puzzle games, single-screen platformers)
 */
enum class ZoneType {
    /** Multi-floor dungeon with stairs/elevators */
    DUNGEON,

    /** Open world overworld map */
    OVERWORLD,

    /** Side-scrolling level */
    SIDE_SCROLLING,

    /** Single arena/combat zone */
    ARENA,

    /** Single screen room */
    ROOM,
}

/**
 * Connection type between zones.
 *
 * More generic than the dungeon-specific ExitType.
 */
enum class ConnectionType {
    /** Standard transition (walk into edge) */
    WALK,

    /** Door/portal that requires interaction */
    DOOR,

    /** Instant teleport/warp */
    WARP,

    /** Vertical transition (stairs, ladder, elevator) */
    VERTICAL,

    /** Automatic transition (triggered by event) */
    AUTO,

    /** Hidden/secret passage */
    SECRET,
}

/** A connection between zones. */
data class ZoneConnection(
    /** Connection type */
    val type: ConnectionType,
    /** Source zone ID */
    val fromZone: String,
    /** Source position (tile or pixel coordinates) */
    val fromX: Int,
    val fromY: Int,
    /** Destination zone ID */
    val toZone: String,
    /** Destination position */
    val toX: Int,
    val toY: Int,
    /** Whether this connection requires a key/item */
    val requiresItem: String? = null,
    /** Whether the item is consumed on use */
    val consumesItem: Boolean = false,
    /** Whether the connection is currently active */
    val startActive: Boolean = true,
    /** Flag index for persistence */
    val flagIndex: Int? = null,
)

/**
 * Abstract zone interface.
 *
 * Zones are the basic building blocks for game worlds. Different implementations support different
 * game genres.
 *
 * Usage:
 * ```kotlin
 * // For dungeon crawlers (backward compatible with Floor)
 * val dungeon1 by floor {
 *     name("Dungeon Level 1")
 *     // ... dungeon-specific config
 * }
 *
 * // For overworld games
 * val route1 by zone {
 *     type(ZoneType.OVERWORLD)
 *     name("Route 1")
 *     map("route1_map") { ... }
 *     connect(to = "town", via = ConnectionType.WALK, at = 0 to 10)
 * }
 *
 * // For platformers
 * val level1 by zone {
 *     type(ZoneType.SIDE_SCROLLING)
 *     name("World 1-1")
 *     map("level1_map") { ... }
 *     scrollDirection(ScrollDirection.HORIZONTAL)
 * }
 * ```
 */
interface Zone {
    /** Unique identifier */
    val id: String

    /** Display name */
    val displayName: String

    /** Zone type */
    val zoneType: ZoneType

    /** Maps/areas within this zone */
    val maps: Map<String, ZoneMap>

    /** Default starting map */
    val defaultMap: String

    /** Default starting position */
    val defaultX: Int
    val defaultY: Int

    /** Connections to other zones */
    val connections: List<ZoneConnection>

    /** Callback statements for entering the zone */
    val onEnterStatements: List<IRStatement>

    /** Callback statements for exiting the zone */
    val onExitStatements: List<IRStatement>

    /** Zone index for code generation */
    var zoneIndex: Int
}

/**
 * A map area within a zone.
 *
 * Generic version of FloorMap that works for all zone types.
 */
data class ZoneMap(
    /** Map identifier */
    val name: String,
    /** Associated tilemap */
    val tilemap: TileMap?,
    /** Tileset asset path */
    val tilesetAsset: String?,
    /** Width in tiles */
    val width: Int,
    /** Height in tiles */
    val height: Int,
    /** Interactive objects on this map */
    val objects: List<GenericMapObject> = emptyList(),
    /** Tile attributes for collision/interaction. */
    val tileAttributes: Map<Int, ExtensibleTileAttributeDefinition> = emptyMap(),
)

// =============================================================================
// GENERIC ZONE IMPLEMENTATION
// =============================================================================

/**
 * Generic zone implementation.
 *
 * Can be configured for any zone type.
 */
class GenericZone(
    override val id: String,
    override val displayName: String,
    override val zoneType: ZoneType,
    override val maps: Map<String, ZoneMap>,
    override val defaultMap: String,
    override val defaultX: Int,
    override val defaultY: Int,
    override val connections: List<ZoneConnection>,
    override val onEnterStatements: List<IRStatement>,
    override val onExitStatements: List<IRStatement>,
    /** Encounter table for this zone */
    val encounterTable: EncounterTable?,
    /** Palette IDs for this zone */
    val paletteIds: List<Int>,
    /** Scroll direction for side-scrolling zones */
    val scrollDirection: ScrollDirection = ScrollDirection.NONE,
    /** Interactive objects in this zone (similar to Floor.objects) */
    val objects: List<GenericMapObject> = emptyList(),
    override var zoneIndex: Int = -1,
) : Zone

/** Scroll direction for side-scrolling zones. */
enum class ScrollDirection {
    NONE,
    HORIZONTAL,
    VERTICAL,
    BOTH,
}

// =============================================================================
// EXIT DSL HELPER TYPES
// =============================================================================

/** A position on the map. */
data class MapPosition(val x: Int, val y: Int)

/** Infix operator to create a position: `5 x 5` */
infix fun Int.x(other: Int): MapPosition = MapPosition(this, other)

/**
 * Source position for an exit.
 *
 * Created using the `at` infix operator: `"mapName" at 5 x 5`
 */
data class ExitSource(val mapName: String, val position: MapPosition)

/** Create an exit source from a map name and position. */
infix fun String.at(position: MapPosition): ExitSource = ExitSource(this, position)

/**
 * Destination for an exit.
 *
 * Created using the `at` or `atDest` infix operators:
 * - `zone at 5 x 5` - destination in another zone
 * - `"mapName" atDest 5 x 5` - destination map in same zone
 */
data class ExitDestination(val targetId: String?, val position: MapPosition)

/** Create an exit destination from a zone and position. */
infix fun Zone.at(position: MapPosition): ExitDestination = ExitDestination(this.id, position)

/** Create an exit destination within the same zone. */
infix fun String.atDest(position: MapPosition): ExitDestination = ExitDestination(this, position)

// =============================================================================
// ZONE BUILDER
// =============================================================================

/** Property delegate for zones. */
class ZoneDelegate(private val gameBuilder: GameBuilder, private val init: ZoneBuilder.() -> Unit) :
    PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, GenericZone>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GenericZone> {
        val builder = ZoneBuilder(property.name)
        builder.init()
        val zone = builder.build()
        gameBuilder.registerZone(zone)

        return ReadOnlyProperty { _, _ -> zone }
    }
}

/** Builder for zones. */
@GbktDsl
class ZoneBuilder(private val zoneId: String) {
    private var displayName: String = zoneId.replaceFirstChar { it.uppercaseChar() }
    private var zoneType: ZoneType = ZoneType.DUNGEON
    private var defaultMap: String = ""
    private var defaultX: Int = 0
    private var defaultY: Int = 0
    private val maps = mutableMapOf<String, ZoneMap>()
    private val connections = mutableListOf<ZoneConnection>()
    private val zoneObjects = mutableListOf<GenericMapObject>()
    private var encounterTable: EncounterTable? = null
    private val paletteIds = mutableListOf<Int>()
    private var scrollDirection: ScrollDirection = ScrollDirection.NONE
    private var onEnterStatements: List<IRStatement> = emptyList()
    private var onExitStatements: List<IRStatement> = emptyList()

    /** Set the display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set the zone type */
    fun type(type: ZoneType) {
        zoneType = type
    }

    /** Set the default starting position */
    fun defaultPosition(x: Int, y: Int) {
        defaultX = x
        defaultY = y
    }

    /** Set the default starting map */
    fun defaultMap(mapName: String) {
        defaultMap = mapName
    }

    /** Add palette IDs for this zone */
    fun palettes(vararg ids: Int) {
        paletteIds.addAll(ids.toList())
    }

    /** Set scroll direction for side-scrolling zones */
    fun scrollDirection(direction: ScrollDirection) {
        scrollDirection = direction
    }

    /** Define a map within this zone */
    fun map(name: String, init: ZoneMapBuilder.() -> Unit) {
        val builder = ZoneMapBuilder(name)
        builder.init()
        val zoneMap = builder.build()
        maps[name] = zoneMap

        // Set as default if first map
        if (defaultMap.isEmpty()) {
            defaultMap = name
        }
    }

    /**
     * Define interactive map objects for this zone.
     *
     * Objects defined here are associated with the zone as a whole, similar to Floor objects.
     *
     * Usage:
     * ```kotlin
     * zone {
     *     type(ZoneType.DUNGEON)
     *     name("Dungeon Level 1")
     *     objects {
     *         chest("chest1") { position(10, 5); contains(potion) }
     *         npc("elder") { position(20, 10); name("Elder") }
     *     }
     * }
     * ```
     */
    fun objects(init: MapObjectsBuilder.() -> Unit) {
        val builder = MapObjectsBuilder()
        builder.init()
        zoneObjects.addAll(builder.build())
    }

    /**
     * Define exits/connections for backward compatibility with Floor DSL.
     *
     * This provides the same syntax as Floor's exits {} builder.
     *
     * Usage:
     * ```kotlin
     * zone {
     *     exits {
     *         door(from = "entrance" at 15 x 5, to = "hallway" atDest 0 x 5)
     *         stairsDown(from = "hallway" at 60 x 10, toFloor = floor2 at 5 x 5)
     *     }
     * }
     * ```
     */
    fun exits(init: ZoneExitBuilder.() -> Unit) {
        val builder = ZoneExitBuilder(zoneId)
        builder.init()
        connections.addAll(builder.build())
    }

    /** Add a connection to another zone */
    fun connect(
        toZone: String,
        type: ConnectionType = ConnectionType.WALK,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        requiresItem: String? = null,
        consumesItem: Boolean = false,
    ) {
        connections.add(
            ZoneConnection(
                type = type,
                fromZone = zoneId,
                fromX = fromX,
                fromY = fromY,
                toZone = toZone,
                toX = toX,
                toY = toY,
                requiresItem = requiresItem,
                consumesItem = consumesItem,
            )
        )
    }

    /** Link an existing encounter table to this zone */
    fun encounters(table: EncounterTable) {
        encounterTable = table
    }

    /** Define encounters inline for this zone */
    fun encounters(init: EncounterTableBuilder.() -> Unit) {
        val builder = EncounterTableBuilder("${zoneId}_encounters")
        builder.init()
        encounterTable = builder.build()
    }

    /** Callback when entering the zone */
    fun onEnter(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onEnterStatements = recorder.statements
    }

    /** Callback when leaving the zone */
    fun onExit(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onExitStatements = recorder.statements
    }

    internal fun build(): GenericZone {
        require(maps.isNotEmpty()) { "Zone '$zoneId' must have at least one map" }
        require(defaultMap.isNotEmpty()) { "Zone '$zoneId' must have a default map" }
        require(defaultMap in maps) {
            "Zone '$zoneId' defaultMap '$defaultMap' not found in maps: ${maps.keys.joinToString()}"
        }

        return GenericZone(
            id = zoneId,
            displayName = displayName,
            zoneType = zoneType,
            maps = maps.toMap(),
            defaultMap = defaultMap,
            defaultX = defaultX,
            defaultY = defaultY,
            connections = connections.toList(),
            onEnterStatements = onEnterStatements,
            onExitStatements = onExitStatements,
            encounterTable = encounterTable,
            paletteIds = paletteIds.toList(),
            scrollDirection = scrollDirection,
            objects = zoneObjects.toList(),
        )
    }
}

// =============================================================================
// ZONE EXIT BUILDER (Floor compatibility)
// =============================================================================

/**
 * Builder for zone exits using Floor-compatible syntax.
 *
 * This builder provides backward compatibility with the Floor DSL's exits {} syntax, converting to
 * ZoneConnection objects.
 *
 * Usage:
 * ```kotlin
 * zone {
 *     exits {
 *         door(from = "entrance" at 15 x 5, to = "hallway" atDest 0 x 5)
 *         stairsDown(from = "hallway" at 60 x 10, toFloor = nextFloor at 5 x 5)
 *     }
 * }
 * ```
 */
@GbktDsl
class ZoneExitBuilder(private val zoneId: String) {
    private val connections = mutableListOf<ZoneConnection>()

    /** Create a door exit */
    fun door(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.DOOR, from, to))
    }

    /** Create stairs going up */
    fun stairsUp(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.VERTICAL, from, to))
    }

    /** Create stairs going down */
    fun stairsDown(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.VERTICAL, from, to))
    }

    /** Create a ladder */
    fun ladder(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.VERTICAL, from, to))
    }

    /** Create a portal */
    fun portal(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.WARP, from, to))
    }

    /** Create an auto/invisible exit */
    fun auto(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.AUTO, from, to))
    }

    /** Create a walk-through transition */
    fun walk(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.WALK, from, to))
    }

    /** Create a secret passage */
    fun secret(from: ExitSource, to: ExitDestination) {
        connections.add(createConnection(ConnectionType.SECRET, from, to))
    }

    private fun createConnection(
        type: ConnectionType,
        from: ExitSource,
        to: ExitDestination,
    ): ZoneConnection {
        return ZoneConnection(
            type = type,
            fromZone = zoneId,
            fromX = from.position.x,
            fromY = from.position.y,
            toZone = to.targetId ?: zoneId, // Uses zone or floor ID, or same zone
            toX = to.position.x,
            toY = to.position.y,
            requiresItem = null,
            consumesItem = false,
            startActive = true,
            flagIndex = null,
        )
    }

    internal fun build(): List<ZoneConnection> = connections.toList()
}

/** Builder for zone maps. */
@GbktDsl
class ZoneMapBuilder(private val mapName: String) {
    private var tilesetAsset: String? = null
    private var width: Int = 32
    private var height: Int = 32
    private var tilemap: TileMap? = null
    private val tileAttributesMap = mutableMapOf<Int, ExtensibleTileAttributeDefinition>()
    private val objects = mutableListOf<GenericMapObject>()

    /** Set the tileset asset */
    fun tileset(asset: String) {
        tilesetAsset = asset
    }

    /** Set the map size */
    fun size(w: Int, h: Int) {
        width = w
        height = h
    }

    /** Use an existing tilemap */
    fun useTilemap(map: TileMap) {
        tilemap = map
        width = map.widthInTiles
        height = map.heightInTiles
    }

    /**
     * Define tile attribute for a specific tile index.
     *
     * Usage:
     * ```kotlin
     * tileAttribute(0, DungeonTilePresets.WALL)
     * tileAttribute(5, customIceTile)  // Custom ExtensibleTileAttributeDefinition
     * ```
     */
    fun tileAttribute(tileIndex: Int, attr: ExtensibleTileAttributeDefinition) {
        tileAttributesMap[tileIndex] = attr
    }

    /**
     * Define tile attributes for multiple tile indices.
     *
     * Usage:
     * ```kotlin
     * tileAttributes(DungeonTilePresets.WALL, 0, 1, 2, 3)
     * ```
     */
    fun tileAttributes(attr: ExtensibleTileAttributeDefinition, vararg tileIndices: Int) {
        tileIndices.forEach { tileAttributesMap[it] = attr }
    }

    internal fun build() =
        ZoneMap(
            name = mapName,
            tilemap = tilemap,
            tilesetAsset = tilesetAsset,
            width = width,
            height = height,
            objects = objects.toList(),
            tileAttributes = tileAttributesMap.toMap(),
        )
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Create a generic zone.
 *
 * For games that don't fit the dungeon crawler model.
 *
 * Usage:
 * ```kotlin
 * val route1 by zone {
 *     type(ZoneType.OVERWORLD)
 *     name("Route 1")
 *     map("route1") {
 *         tileset("overworld.png")
 *         size(64, 64)
 *     }
 *     connect(toZone = "town", type = ConnectionType.WALK, fromX = 0, fromY = 10, toX = 63, toY = 10)
 * }
 * ```
 */
fun GameBuilder.zone(init: ZoneBuilder.() -> Unit): ZoneDelegate {
    return ZoneDelegate(this, init)
}
