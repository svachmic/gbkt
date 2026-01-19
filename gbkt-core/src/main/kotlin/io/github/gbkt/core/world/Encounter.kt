/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.rpg.Monster
import io.github.gbkt.core.rpg.MonsterVariant

// =============================================================================
// RANDOM ENCOUNTER SYSTEM
// =============================================================================

/**
 * Wrapper for monsters in encounters.
 *
 * Supports both base [Monster] instances and [MonsterVariant] instances for tier-scaled variations
 * of the same monster.
 */
sealed class EncounterMonster {
    /** The base monster definition */
    abstract val baseMonster: Monster

    /** The effective stat multiplier for this monster */
    abstract val effectiveMultiplier: Int

    /** Scaled HP for this monster */
    abstract val scaledHp: Int
    abstract val scaledAtk: Int
    abstract val scaledDef: Int
    abstract val scaledMatk: Int
    abstract val scaledMdef: Int
    abstract val scaledAgl: Int

    /** Scaled experience reward */
    abstract val scaledExpReward: Int

    /** A base monster without tier override */
    data class Base(override val baseMonster: Monster) : EncounterMonster() {
        override val effectiveMultiplier: Int
            get() = baseMonster.effectiveStatMultiplier

        override val scaledHp: Int
            get() = baseMonster.scaledHp

        override val scaledAtk: Int
            get() = baseMonster.scaledAtk

        override val scaledDef: Int
            get() = baseMonster.scaledDef

        override val scaledMatk: Int
            get() = baseMonster.scaledMatk

        override val scaledMdef: Int
            get() = baseMonster.scaledMdef

        override val scaledAgl: Int
            get() = baseMonster.scaledAgl

        override val scaledExpReward: Int
            get() = baseMonster.expReward
    }

    /** A monster variant with tier override */
    data class Variant(val variant: MonsterVariant) : EncounterMonster() {
        override val baseMonster: Monster
            get() = variant.baseMonster

        override val effectiveMultiplier: Int
            get() = variant.effectiveMultiplier

        override val scaledHp: Int
            get() = variant.scaledHp

        override val scaledAtk: Int
            get() = variant.scaledAtk

        override val scaledDef: Int
            get() = variant.scaledDef

        override val scaledMatk: Int
            get() = variant.scaledMatk

        override val scaledMdef: Int
            get() = variant.scaledMdef

        override val scaledAgl: Int
            get() = variant.scaledAgl

        override val scaledExpReward: Int
            get() = variant.scaledExpReward
    }
}

/** Convert a Monster to an EncounterMonster */
fun Monster.toEncounterMonster(): EncounterMonster = EncounterMonster.Base(this)

/** Convert a MonsterVariant to an EncounterMonster */
fun MonsterVariant.toEncounterMonster(): EncounterMonster = EncounterMonster.Variant(this)

/** A single encounter entry in an encounter table. */
data class EncounterEntry(
    /** Weight for random selection (higher = more likely) */
    val weight: Int,
    /** Monsters in this encounter (supports both base monsters and variants) */
    val encounterMonsters: List<EncounterMonster>,
) {
    /** Convenience accessor for backwards compatibility - returns base monsters only */
    val monsters: List<Monster>
        get() = encounterMonsters.map { it.baseMonster }
}

/**
 * An encounter table for a floor or area.
 *
 * Defines the possible random encounters and their probabilities.
 *
 * Usage (simple table):
 * ```kotlin
 * val floor1Encounters = encounterTable {
 *     safeSteps(10)
 *     initialChance(5) // out of 256
 *     incrementPerStep(3)
 *
 *     entry(weight = 30) { +kobold }
 *     entry(weight = 25) { +goblin }
 *     entry(weight = 20) { +kobold; +kobold }
 *     entry(weight = 15) { +zombie }
 *     entry(weight = 10) { +bugbear }
 * }
 * ```
 *
 * Usage (level-gated dual tables):
 * ```kotlin
 * val floor1Encounters = encounterTable {
 *     levelThreshold(9)  // Use lowLevel table below level 9
 *
 *     lowLevel {
 *         safeSteps(12)
 *         entry(weight = 35) { +kobold; +kobold }
 *         entry(weight = 30) { +kobold; +goblin }
 *     }
 *
 *     highLevel {
 *         safeSteps(8)
 *         entry(weight = 25) { +koboldB; +goblinB }
 *         entry(weight = 30) { +koboldA; +koboldB }
 *     }
 * }
 * ```
 */
class EncounterTable(
    /** Table identifier */
    val id: String,
    /** Number of safe steps before encounters can start */
    val safeSteps: Int,
    /** Initial encounter chance (out of 256) */
    val initialChance: Int,
    /** Chance increment per step */
    val incrementPerStep: Int,
    /** Maximum encounter chance */
    val maxChance: Int,
    /** Encounter entries with weights (used when not level-gated) */
    val entries: List<EncounterEntry>,
    /** Whether encounters are disabled (repel effect) */
    var disabled: Boolean = false,
    // -------------------------------------------------------------------------
    // LEVEL-GATED ENCOUNTER SUPPORT
    // -------------------------------------------------------------------------
    /** Level threshold for switching between low/high level tables (null = no level gating) */
    val levelThreshold: Int? = null,
    /** Low level encounter entries (used when player level < levelThreshold) */
    val lowLevelEntries: List<EncounterEntry>? = null,
    /** Low level safe steps override */
    val lowLevelSafeSteps: Int? = null,
    /** High level encounter entries (used when player level >= levelThreshold) */
    val highLevelEntries: List<EncounterEntry>? = null,
    /** High level safe steps override */
    val highLevelSafeSteps: Int? = null,
) {
    /** Total weight of all entries */
    val totalWeight: Int = entries.sumOf { it.weight }

    /** Total weight of low-level entries (if level-gated) */
    val lowLevelTotalWeight: Int = lowLevelEntries?.sumOf { it.weight } ?: 0

    /** Total weight of high-level entries (if level-gated) */
    val highLevelTotalWeight: Int = highLevelEntries?.sumOf { it.weight } ?: 0

    /** Whether this table uses level-gating */
    val isLevelGated: Boolean = levelThreshold != null

    /**
     * Get a random encounter based on weights.
     *
     * @param randomValue Random value used for selection (will be taken modulo [totalWeight]). Any
     *   non-negative integer is valid.
     * @return The selected encounter entry, or null if no entries exist
     */
    fun rollEncounter(randomValue: Int): EncounterEntry? {
        if (entries.isEmpty()) return null

        var remaining = randomValue % totalWeight
        for (entry in entries) {
            remaining -= entry.weight
            if (remaining < 0) {
                return entry
            }
        }
        return entries.lastOrNull()
    }

    /**
     * Get a random encounter from the level-appropriate table.
     *
     * @param randomValue Random value used for selection
     * @param playerLevel Current player level to determine which table to use
     * @return The selected encounter entry, or null if no entries exist
     */
    fun rollEncounter(randomValue: Int, playerLevel: Int): EncounterEntry? {
        if (!isLevelGated) return rollEncounter(randomValue)

        val useHighLevel = playerLevel >= (levelThreshold ?: 0)
        val tableEntries = if (useHighLevel) highLevelEntries else lowLevelEntries
        val tableTotalWeight = if (useHighLevel) highLevelTotalWeight else lowLevelTotalWeight

        if (tableEntries.isNullOrEmpty() || tableTotalWeight == 0) return null

        var remaining = randomValue % tableTotalWeight
        for (entry in tableEntries) {
            remaining -= entry.weight
            if (remaining < 0) {
                return entry
            }
        }
        return tableEntries.lastOrNull()
    }

    /** Get the effective safe steps for the given player level. */
    fun getEffectiveSafeSteps(playerLevel: Int): Int {
        if (!isLevelGated) return safeSteps

        val useHighLevel = playerLevel >= (levelThreshold ?: 0)
        return if (useHighLevel) {
            highLevelSafeSteps ?: safeSteps
        } else {
            lowLevelSafeSteps ?: safeSteps
        }
    }
}

// =============================================================================
// ENCOUNTER TABLE BUILDER
// =============================================================================

/** Builder for encounter tables. */
@GbktDsl
class EncounterTableBuilder(private val tableId: String) {
    private var safeSteps: Int = 10
    private var initialChance: Int = 5
    private var incrementPerStep: Int = 3
    private var maxChance: Int = 128
    private val entries = mutableListOf<EncounterEntry>()

    // Level-gated encounter support
    private var levelThreshold: Int? = null
    private var lowLevelEntries: MutableList<EncounterEntry>? = null
    private var lowLevelSafeSteps: Int? = null
    private var highLevelEntries: MutableList<EncounterEntry>? = null
    private var highLevelSafeSteps: Int? = null

    /** Set number of safe steps before encounters can occur */
    fun safeSteps(steps: Int) {
        require(steps >= 0) { "Safe steps must be non-negative" }
        safeSteps = steps
    }

    /** Set initial encounter chance (out of 256) */
    fun initialChance(chance: Int) {
        require(chance in 0..255) { "Initial chance must be 0-255" }
        initialChance = chance
    }

    /** Set chance increment per step */
    fun incrementPerStep(increment: Int) {
        require(increment >= 0) { "Increment must be non-negative" }
        incrementPerStep = increment
    }

    /** Set maximum encounter chance */
    fun maxChance(chance: Int) {
        require(chance in 1..255) { "Max chance must be 1-255" }
        maxChance = chance
    }

    /** Add an encounter entry */
    fun entry(weight: Int, init: EncounterEntryScope.() -> Unit) {
        require(weight > 0) { "Entry weight must be positive" }
        val scope = EncounterEntryScope()
        scope.init()
        entries.add(EncounterEntry(weight, scope.encounterMonsters))
    }

    // -------------------------------------------------------------------------
    // LEVEL-GATED ENCOUNTER DSL
    // -------------------------------------------------------------------------

    /**
     * Set the level threshold for dual encounter tables.
     *
     * When set, encounters will use the [lowLevel] table when player level is below this threshold,
     * and the [highLevel] table when player level is at or above this threshold.
     *
     * @param level The level threshold (1-99). Players below this level use lowLevel table.
     */
    fun levelThreshold(level: Int) {
        require(level in 1..99) { "Level threshold must be 1-99" }
        levelThreshold = level
    }

    /**
     * Define encounters for low-level players (below [levelThreshold]).
     *
     * Use this to define easier encounters for players who haven't reached the level threshold yet.
     *
     * Usage:
     * ```kotlin
     * lowLevel {
     *     safeSteps(12)  // More safe steps for lower levels
     *     entry(weight = 35) { +kobold; +kobold }
     *     entry(weight = 30) { +kobold; +goblin }
     * }
     * ```
     */
    fun lowLevel(init: LevelGatedEncounterScope.() -> Unit) {
        val scope = LevelGatedEncounterScope()
        scope.init()
        lowLevelEntries = scope.entries
        lowLevelSafeSteps = scope.safeStepsOverride
    }

    /**
     * Define encounters for high-level players (at or above [levelThreshold]).
     *
     * Use this to define harder encounters with tier variants and tougher monster combinations.
     *
     * Usage:
     * ```kotlin
     * highLevel {
     *     safeSteps(8)  // Fewer safe steps for higher levels
     *     entry(weight = 25) { +koboldB; +goblinB }
     *     entry(weight = 30) { +koboldA; +koboldB }
     * }
     * ```
     */
    fun highLevel(init: LevelGatedEncounterScope.() -> Unit) {
        val scope = LevelGatedEncounterScope()
        scope.init()
        highLevelEntries = scope.entries
        highLevelSafeSteps = scope.safeStepsOverride
    }

    internal fun build(): EncounterTable {
        // Validation for level-gated tables
        if (levelThreshold != null) {
            require(lowLevelEntries != null || highLevelEntries != null) {
                "Level threshold set but no lowLevel or highLevel blocks defined"
            }
        }

        return EncounterTable(
            id = tableId,
            safeSteps = safeSteps,
            initialChance = initialChance,
            incrementPerStep = incrementPerStep,
            maxChance = maxChance,
            entries = entries.toList(),
            levelThreshold = levelThreshold,
            lowLevelEntries = lowLevelEntries?.toList(),
            lowLevelSafeSteps = lowLevelSafeSteps,
            highLevelEntries = highLevelEntries?.toList(),
            highLevelSafeSteps = highLevelSafeSteps,
        )
    }
}

/**
 * Scope for building level-gated encounter entries.
 *
 * Similar to the main encounter builder but for a specific level range (low or high).
 */
@GbktDsl
class LevelGatedEncounterScope {
    internal val entries = mutableListOf<EncounterEntry>()
    internal var safeStepsOverride: Int? = null

    /** Override safe steps for this level range */
    fun safeSteps(steps: Int) {
        require(steps >= 0) { "Safe steps must be non-negative" }
        safeStepsOverride = steps
    }

    /** Add an encounter entry */
    fun entry(weight: Int, init: EncounterEntryScope.() -> Unit) {
        require(weight > 0) { "Entry weight must be positive" }
        val scope = EncounterEntryScope()
        scope.init()
        entries.add(EncounterEntry(weight, scope.encounterMonsters))
    }
}

/** Scope for building encounter entries. */
@GbktDsl
class EncounterEntryScope {
    internal val encounterMonsters = mutableListOf<EncounterMonster>()

    /** Add a base monster to this encounter using unary plus */
    operator fun Monster.unaryPlus() {
        encounterMonsters.add(this.toEncounterMonster())
    }

    /** Add a monster variant to this encounter using unary plus */
    operator fun MonsterVariant.unaryPlus() {
        encounterMonsters.add(this.toEncounterMonster())
    }

    /** Add multiple of the same base monster */
    fun add(monster: Monster, count: Int = 1) {
        repeat(count) { encounterMonsters.add(monster.toEncounterMonster()) }
    }

    /** Add multiple of the same monster variant */
    fun add(variant: MonsterVariant, count: Int = 1) {
        repeat(count) { encounterMonsters.add(variant.toEncounterMonster()) }
    }
}

// =============================================================================
// ENCOUNTER STATE
// =============================================================================

/** Tracks encounter state for a floor/area. */
data class EncounterState(
    /** Current step count */
    var stepCount: Int = 0,
    /** Current encounter chance */
    var currentChance: Int = 0,
    /** Whether encounters are disabled */
    var disabled: Boolean = false,
)

/** Encounter system runtime operations. */
class EncounterSystem(
    private val table: EncounterTable,
    private val state: EncounterState = EncounterState(),
) {
    /** Reset encounter state (called on floor entry) */
    fun reset() {
        state.stepCount = 0
        state.currentChance = table.initialChance
        state.disabled = table.disabled
    }

    /** Process a step and potentially trigger encounter */
    fun onStep(): EncounterEntry? {
        if (state.disabled) return null

        state.stepCount++

        // Check if past safe steps
        if (state.stepCount <= table.safeSteps) return null

        // Roll for encounter
        val roll = (0..255).random()
        if (roll < state.currentChance) {
            // Encounter triggered - reset chance
            state.currentChance = table.initialChance
            return table.rollEncounter(roll)
        }

        // No encounter - increment chance
        state.currentChance = minOf(state.currentChance + table.incrementPerStep, table.maxChance)
        return null
    }

    /** Disable encounters (repel effect) */
    fun disable() {
        state.disabled = true
    }

    /** Enable encounters */
    fun enable() {
        state.disabled = false
    }
}

// =============================================================================
// DSL FUNCTION
// =============================================================================

/** Create an encounter table. */
fun encounterTable(id: String, init: EncounterTableBuilder.() -> Unit): EncounterTable {
    val builder = EncounterTableBuilder(id)
    builder.init()
    return builder.build()
}

// =============================================================================
// ENCOUNTER DSL FUNCTIONS FOR RUNTIME USE
// =============================================================================

/**
 * Check for a random encounter after taking a step.
 *
 * Call this in your gameplay scene after player movement completes. If an encounter is triggered,
 * it will automatically set up the pending encounter data and transition to the specified battle
 * scene.
 *
 * Usage:
 * ```kotlin
 * // In gameplay scene, after movement
 * whenever(state.moveCooldown isEqualTo 0) {
 *     whenever(dpad.up.held) {
 *         state.playerY -= 1
 *         state.stepCount += 1
 *         checkEncounter("battle")  // Check after each step
 *     }
 * }
 * ```
 *
 * @param battleSceneName The name of the battle scene to transition to if an encounter triggers
 */
fun checkEncounter(battleSceneName: String) {
    if (io.github.gbkt.core.dsl.RecordingContext.isRecording) {
        io.github.gbkt.core.dsl.RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRCheckEncounterStep(battleSceneName))
    }
}

/**
 * Set the active encounter table for the current area.
 *
 * Call this when entering a new floor or area to switch the active encounter table. This resets the
 * step counter and encounter chance.
 *
 * Usage:
 * ```kotlin
 * scene("floor1") {
 *     enter {
 *         setEncounterTable(0)  // Use encounter table at index 0
 *     }
 * }
 * ```
 *
 * @param tableIndex The index of the encounter table (assigned during codegen based on floor order)
 */
fun setEncounterTable(tableIndex: Int) {
    require(tableIndex >= 0) { "Encounter table index must be non-negative" }
    if (io.github.gbkt.core.dsl.RecordingContext.isRecording) {
        io.github.gbkt.core.dsl.RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRSetEncounterTable(tableIndex))
    }
}

/**
 * Set the active encounter table based on an expression (e.g., currentFloor variable).
 *
 * Call this when entering a new floor or area to switch the active encounter table. This resets the
 * step counter and encounter chance.
 *
 * Usage:
 * ```kotlin
 * scene("gameplay") {
 *     enter {
 *         setEncounterTable(state.currentFloor)  // Use variable floor index
 *     }
 * }
 * ```
 *
 * @param tableIndexExpr An expression that evaluates to the encounter table index
 */
fun setEncounterTable(tableIndexExpr: io.github.gbkt.core.ir.Expr) {
    if (io.github.gbkt.core.dsl.RecordingContext.isRecording) {
        io.github.gbkt.core.dsl.RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRSetEncounterTableExpr(tableIndexExpr.ir))
    }
}
