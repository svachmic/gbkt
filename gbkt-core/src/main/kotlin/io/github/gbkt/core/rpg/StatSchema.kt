/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// STAT SCHEMA - Configurable stat system for different game types
// =============================================================================

/** C type for stat storage. */
enum class StatStorageType(val cType: String, val maxValue: Int) {
    /** 8-bit unsigned (0-255) - good for most stats */
    UINT8("UINT8", 255),

    /** 16-bit unsigned (0-65535) - for HP, EXP, large values */
    UINT16("UINT16", 65535),

    /** 8-bit signed (-128 to 127) - for modifiers */
    INT8("INT8", 127),

    /** 16-bit signed (-32768 to 32767) - for large modifiers */
    INT16("INT16", 32767),
}

/**
 * A stat definition in a schema.
 *
 * @property id Unique identifier for the stat (e.g., "hp", "str", "mp")
 * @property displayName Display name for UI (e.g., "HP", "STR", "MP")
 * @property storage C type for storage
 * @property defaultMax Default maximum value
 * @property defaultValue Default starting value
 * @property category Optional category for UI grouping
 * @property description Optional description for tooltips/help
 */
data class StatDef(
    val id: String,
    val displayName: String,
    val storage: StatStorageType = StatStorageType.UINT8,
    val defaultMax: Int = storage.maxValue,
    val defaultValue: Int = 0,
    val category: String? = null,
    val description: String? = null,
    /** Order in the schema (for consistent codegen) */
    var index: Int = -1,
)

/**
 * A stat schema defining what stats a game uses.
 *
 * Instead of hardcoding HP, SP, ATK, DEF, MATK, MDEF, AGL, games can define their own stats:
 * ```kotlin
 * // Traditional JRPG
 * val schema by statSchema {
 *     stat("hp") { display("HP"); storage(UINT16); category("vital") }
 *     stat("mp") { display("MP"); storage(UINT16); category("vital") }
 *     stat("str") { display("STR"); category("offense") }
 *     stat("vit") { display("VIT"); category("defense") }
 *     stat("int") { display("INT"); category("magic") }
 *     stat("mnd") { display("MND"); category("magic") }
 *     stat("agi") { display("AGI"); category("speed") }
 *     stat("luk") { display("LUK"); category("misc") }
 * }
 *
 * // Minimalist action game
 * val schema by statSchema {
 *     stat("hp") { display("HP"); storage(UINT8); max(99) }
 *     stat("atk") { display("ATK") }
 *     stat("def") { display("DEF") }
 * }
 *
 * // Roguelike with custom stats
 * val schema by statSchema {
 *     stat("hp") { display("HP"); storage(UINT16) }
 *     stat("hunger") { display("HNG"); defaultValue(100); max(100) }
 *     stat("sanity") { display("SAN"); defaultValue(100); max(100) }
 *     stat("rad") { display("RAD"); max(999); category("danger") }
 * }
 * ```
 */
class StatSchema(
    val id: String,
    val stats: List<StatDef>,
    /** Whether this is the default/standard schema */
    val isDefault: Boolean = false,
) {
    /** Map from stat ID to definition for O(1) lookup */
    private val statMap: Map<String, StatDef> = stats.associateBy { it.id }

    /** Get a stat by ID */
    fun getStat(id: String): StatDef? = statMap[id]

    /** Check if a stat exists */
    fun hasStat(id: String): Boolean = id in statMap

    /** Get all stats in a category */
    fun getStatsByCategory(category: String): List<StatDef> =
        stats.filter { it.category == category }

    /** Get all categories */
    val categories: Set<String>
        get() = stats.mapNotNull { it.category }.toSet()

    /** Number of stats */
    val size: Int
        get() = stats.size

    companion object {
        /**
         * Standard JRPG stat schema with 8 core stats.
         *
         * Provided for backward compatibility with existing games.
         */
        val STANDARD_JRPG =
            StatSchema(
                id = "standard_jrpg",
                stats =
                    listOf(
                            StatDef(
                                "hp",
                                "HP",
                                StatStorageType.UINT16,
                                999,
                                100,
                                "vital",
                                "Hit Points",
                            ),
                            StatDef(
                                "sp",
                                "SP",
                                StatStorageType.UINT8,
                                99,
                                50,
                                "vital",
                                "Skill Points",
                            ),
                            StatDef(
                                "atk",
                                "ATK",
                                StatStorageType.UINT8,
                                255,
                                10,
                                "offense",
                                "Physical Attack",
                            ),
                            StatDef(
                                "def",
                                "DEF",
                                StatStorageType.UINT8,
                                255,
                                10,
                                "defense",
                                "Physical Defense",
                            ),
                            StatDef(
                                "matk",
                                "MATK",
                                StatStorageType.UINT8,
                                255,
                                10,
                                "offense",
                                "Magical Attack",
                            ),
                            StatDef(
                                "mdef",
                                "MDEF",
                                StatStorageType.UINT8,
                                255,
                                10,
                                "defense",
                                "Magical Defense",
                            ),
                            StatDef(
                                "agl",
                                "AGL",
                                StatStorageType.UINT8,
                                255,
                                10,
                                "speed",
                                "Agility",
                            ),
                            StatDef(
                                "level",
                                "LV",
                                StatStorageType.UINT8,
                                99,
                                1,
                                "progression",
                                "Level",
                            ),
                            StatDef(
                                "exp",
                                "EXP",
                                StatStorageType.UINT16,
                                65535,
                                0,
                                "progression",
                                "Experience",
                            ),
                        )
                        .also { stats ->
                            stats.forEachIndexed { index, stat -> stat.index = index }
                        },
                isDefault = true,
            )

        /**
         * Minimalist stat schema for action games.
         *
         * Just HP, ATK, DEF - no magic stats.
         */
        val MINIMALIST =
            StatSchema(
                id = "minimalist",
                stats =
                    listOf(
                            StatDef("hp", "HP", StatStorageType.UINT8, 99, 10, "vital"),
                            StatDef("atk", "ATK", StatStorageType.UINT8, 99, 5, "combat"),
                            StatDef("def", "DEF", StatStorageType.UINT8, 99, 5, "combat"),
                        )
                        .also { stats ->
                            stats.forEachIndexed { index, stat -> stat.index = index }
                        },
                isDefault = false,
            )

        /**
         * Extended stat schema with more attributes.
         *
         * Includes luck, additional resistances, etc.
         */
        val EXTENDED_JRPG =
            StatSchema(
                id = "extended_jrpg",
                stats =
                    listOf(
                            // Vitals
                            StatDef("hp", "HP", StatStorageType.UINT16, 9999, 100, "vital"),
                            StatDef("sp", "SP", StatStorageType.UINT16, 999, 50, "vital"),
                            // Offense
                            StatDef("str", "STR", StatStorageType.UINT8, 255, 10, "offense"),
                            StatDef("mag", "MAG", StatStorageType.UINT8, 255, 10, "offense"),
                            // Defense
                            StatDef("vit", "VIT", StatStorageType.UINT8, 255, 10, "defense"),
                            StatDef("spr", "SPR", StatStorageType.UINT8, 255, 10, "defense"),
                            // Speed
                            StatDef("spd", "SPD", StatStorageType.UINT8, 255, 10, "speed"),
                            StatDef("eva", "EVA", StatStorageType.UINT8, 100, 5, "speed"),
                            // Misc
                            StatDef("luk", "LUK", StatStorageType.UINT8, 100, 10, "misc"),
                            StatDef("hit", "HIT", StatStorageType.UINT8, 100, 95, "misc"),
                            StatDef("crt", "CRT", StatStorageType.UINT8, 100, 5, "misc"),
                            // Progression
                            StatDef("level", "LV", StatStorageType.UINT8, 99, 1, "progression"),
                            StatDef("exp", "EXP", StatStorageType.UINT16, 65535, 0, "progression"),
                        )
                        .also { stats ->
                            stats.forEachIndexed { index, stat -> stat.index = index }
                        },
                isDefault = false,
            )
    }
}

// =============================================================================
// STAT SCHEMA BUILDER
// =============================================================================

/** Builder for individual stat definitions. */
@GbktDsl
class StatDefBuilder(private val statId: String) {
    private var displayName: String = statId.uppercase()
    private var storage: StatStorageType = StatStorageType.UINT8
    private var defaultMax: Int? = null
    private var defaultValue: Int = 0
    private var category: String? = null
    private var description: String? = null

    /** Set display name */
    fun display(name: String) {
        displayName = name
    }

    /** Set storage type */
    fun storage(type: StatStorageType) {
        storage = type
    }

    /** Set maximum value */
    fun max(value: Int) {
        defaultMax = value
    }

    /** Set default starting value */
    fun defaultValue(value: Int) {
        defaultValue = value
    }

    /** Set category for UI grouping */
    fun category(cat: String) {
        category = cat
    }

    /** Set description */
    fun description(desc: String) {
        description = desc
    }

    internal fun build() =
        StatDef(
            id = statId,
            displayName = displayName,
            storage = storage,
            defaultMax = defaultMax ?: storage.maxValue,
            defaultValue = defaultValue,
            category = category,
            description = description,
        )
}

/** Builder for stat schemas. */
@GbktDsl
class StatSchemaBuilder(private val schemaId: String) {
    private val stats = mutableListOf<StatDef>()
    private var isDefault: Boolean = false

    /**
     * Define a stat.
     *
     * Usage:
     * ```kotlin
     * stat("hp") {
     *     display("HP")
     *     storage(UINT16)
     *     max(999)
     *     defaultValue(100)
     *     category("vital")
     * }
     * ```
     */
    fun stat(id: String, init: StatDefBuilder.() -> Unit = {}) {
        val builder = StatDefBuilder(id)
        builder.init()
        val stat = builder.build()
        stat.index = stats.size
        stats.add(stat)
    }

    /** Mark this as the default schema for the game */
    fun asDefault() {
        isDefault = true
    }

    /**
     * Include all stats from a predefined schema.
     *
     * Useful for extending existing schemas:
     * ```kotlin
     * val schema by statSchema {
     *     include(StatSchema.STANDARD_JRPG)
     *     stat("luck") { display("LUK"); category("misc") }
     * }
     * ```
     */
    fun include(other: StatSchema) {
        other.stats.forEach { stat ->
            val copy = stat.copy(index = stats.size)
            stats.add(copy)
        }
    }

    internal fun build(): StatSchema {
        require(stats.isNotEmpty()) { "Stat schema must have at least one stat" }
        return StatSchema(schemaId, stats.toList(), isDefault)
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/** Property delegate for stat schemas. */
class StatSchemaDelegate(
    private val gameBuilder: GameBuilder,
    private val init: StatSchemaBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, StatSchema>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, StatSchema> {
        val builder = StatSchemaBuilder(property.name)
        builder.init()
        val schema = builder.build()
        gameBuilder.registerStatSchema(schema)

        return ReadOnlyProperty { _, _ -> schema }
    }
}

/**
 * Define a custom stat schema.
 *
 * Allows games to define exactly which stats they need instead of using the hardcoded 8 stats.
 *
 * Usage:
 * ```kotlin
 * val stats by statSchema {
 *     stat("hp") { display("HP"); storage(UINT16); max(999); category("vital") }
 *     stat("mp") { display("MP"); storage(UINT16); max(99); category("vital") }
 *     stat("str") { display("STR"); category("offense") }
 *     stat("def") { display("DEF"); category("defense") }
 *     stat("agi") { display("AGI"); category("speed") }
 *     asDefault()
 * }
 * ```
 */
fun GameBuilder.statSchema(init: StatSchemaBuilder.() -> Unit): StatSchemaDelegate {
    return StatSchemaDelegate(this, init)
}

/**
 * Use a predefined stat schema.
 *
 * Convenience function for using standard schemas:
 * ```kotlin
 * useStatSchema(StatSchema.STANDARD_JRPG)
 * // or
 * useStatSchema(StatSchema.MINIMALIST)
 * ```
 */
fun GameBuilder.useStatSchema(schema: StatSchema) {
    registerStatSchema(schema)
}
