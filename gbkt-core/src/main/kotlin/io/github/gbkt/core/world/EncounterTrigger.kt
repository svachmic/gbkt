/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// ENCOUNTER TRIGGER - Pluggable encounter trigger system
// =============================================================================

/**
 * Encounter trigger type.
 *
 * Different games trigger encounters in different ways. This abstraction allows games to use
 * step-based, time-based, region-based, or custom triggers.
 */
enum class TriggerType {
    /** Step-based: encounters trigger after a number of steps */
    STEP_BASED,

    /** Time-based: encounters trigger after a certain amount of time */
    TIME_BASED,

    /** Region-based: encounters trigger when entering specific regions */
    REGION_BASED,

    /** Event-based: encounters trigger from scripted events */
    EVENT_BASED,

    /** Wave-based: encounters spawn in waves */
    WAVE_BASED,

    /** Proximity-based: encounters trigger when near enemies */
    PROXIMITY_BASED,
}

/**
 * Abstract encounter trigger interface.
 *
 * Defines the contract for all encounter trigger systems.
 *
 * Usage:
 * ```kotlin
 * // Step-based (traditional JRPG)
 * val trigger by stepTrigger {
 *     safeSteps(10)
 *     initialChance(5)
 *     incrementPerStep(3)
 *     maxChance(100)
 * }
 *
 * // Time-based (overworld game)
 * val trigger by timeTrigger {
 *     safeFrames(300)  // 5 seconds at 60fps
 *     checkInterval(60)  // Check every second
 *     baseChance(10)
 * }
 *
 * // Region-based (danger zones)
 * val trigger by regionTrigger {
 *     dangerZone(x1 = 0, y1 = 0, x2 = 10, y2 = 10, chance = 50)
 *     dangerZone(x1 = 20, y1 = 20, x2 = 30, y2 = 30, chance = 100)
 * }
 * ```
 */
interface EncounterTrigger {
    /** Unique identifier */
    val id: String

    /** Trigger type */
    val triggerType: TriggerType

    /** Encounter table to use */
    val encounterTable: EncounterTable?

    /** Callback when encounter triggers */
    val onTriggerStatements: List<IRStatement>

    /** System index for code generation */
    var systemIndex: Int
}

// =============================================================================
// STEP-BASED TRIGGER (Traditional JRPG)
// =============================================================================

/**
 * Step-based encounter trigger.
 *
 * The classic JRPG random encounter system. Chance increases with each step, with optional safe
 * steps at the start.
 */
class StepBasedTrigger(
    override val id: String,
    /** Number of safe steps before encounters can happen */
    val safeSteps: Int,
    /** Initial encounter chance (0-100) */
    val initialChance: Int,
    /** Chance increase per step */
    val incrementPerStep: Int,
    /** Maximum encounter chance (cap) */
    val maxChance: Int,
    /** Whether to reset counter after encounter */
    val resetOnEncounter: Boolean,
    override val encounterTable: EncounterTable?,
    override val onTriggerStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : EncounterTrigger {
    override val triggerType: TriggerType = TriggerType.STEP_BASED
}

// =============================================================================
// TIME-BASED TRIGGER
// =============================================================================

/**
 * Time-based encounter trigger.
 *
 * Encounters happen based on elapsed time rather than steps. Good for games with free-roaming
 * movement.
 */
class TimeBasedTrigger(
    override val id: String,
    /** Safe frames before encounters can happen */
    val safeFrames: Int,
    /** How often to check for encounters (in frames) */
    val checkInterval: Int,
    /** Base encounter chance per check (0-100) */
    val baseChance: Int,
    /** Whether being stationary affects encounter rate */
    val idleMultiplier: Int,
    /** Whether moving affects encounter rate */
    val movingMultiplier: Int,
    override val encounterTable: EncounterTable?,
    override val onTriggerStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : EncounterTrigger {
    override val triggerType: TriggerType = TriggerType.TIME_BASED
}

// =============================================================================
// REGION-BASED TRIGGER
// =============================================================================

/** A danger zone that triggers encounters. */
data class DangerZone(
    /** Zone bounds */
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    /** Encounter chance in this zone (0-100) */
    val chance: Int,
    /** Optional check interval in frames */
    val checkInterval: Int = 60,
)

/**
 * Region-based encounter trigger.
 *
 * Encounters happen when player is in specific danger zones.
 */
class RegionBasedTrigger(
    override val id: String,
    /** Danger zones where encounters can happen */
    val dangerZones: List<DangerZone>,
    /** How often to check for encounters when in a zone */
    val checkInterval: Int,
    override val encounterTable: EncounterTable?,
    override val onTriggerStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : EncounterTrigger {
    override val triggerType: TriggerType = TriggerType.REGION_BASED
}

// =============================================================================
// EVENT-BASED TRIGGER
// =============================================================================

/**
 * Event-based encounter trigger.
 *
 * Encounters are triggered programmatically from game scripts. Provides manual control over when
 * encounters happen.
 */
class EventBasedTrigger(
    override val id: String,
    override val encounterTable: EncounterTable?,
    override val onTriggerStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : EncounterTrigger {
    override val triggerType: TriggerType = TriggerType.EVENT_BASED
}

// =============================================================================
// WAVE-BASED TRIGGER
// =============================================================================

/** Configuration for a single wave. */
data class WaveConfig(
    /** Wave number (1-based) */
    val waveNumber: Int,
    /** Delay in frames before this wave starts */
    val delay: Int,
    /** Monsters in this wave (IDs) */
    val monsters: List<String>,
    /** Optional spawn positions */
    val spawnPositions: List<Pair<Int, Int>>?,
)

/**
 * Wave-based encounter trigger.
 *
 * For arena/survival modes where enemies spawn in waves.
 */
class WaveBasedTrigger(
    override val id: String,
    /** Wave configurations */
    val waves: List<WaveConfig>,
    /** Whether waves loop after last one */
    val loopWaves: Boolean,
    /** Difficulty scaling per loop */
    val loopScaling: Int,
    override val encounterTable: EncounterTable?,
    override val onTriggerStatements: List<IRStatement>,
    /** Callback between waves */
    val onWaveCompleteStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : EncounterTrigger {
    override val triggerType: TriggerType = TriggerType.WAVE_BASED
}

// =============================================================================
// TRIGGER BUILDERS
// =============================================================================

/** Base builder for encounter triggers. */
@GbktDsl
abstract class EncounterTriggerBuilder(protected val triggerId: String) {
    protected var encounterTable: EncounterTable? = null
    protected var onTriggerStatements: List<IRStatement> = emptyList()

    /** Set the encounter table to use */
    fun encounters(table: EncounterTable) {
        encounterTable = table
    }

    /** Callback when encounter triggers */
    fun onTrigger(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onTriggerStatements = recorder.statements
    }

    abstract fun build(): EncounterTrigger
}

/** Builder for step-based triggers. */
@GbktDsl
class StepTriggerBuilder(triggerId: String) : EncounterTriggerBuilder(triggerId) {
    private var safeSteps: Int = 10
    private var initialChance: Int = 5
    private var incrementPerStep: Int = 3
    private var maxChance: Int = 100
    private var resetOnEncounter: Boolean = true

    /** Set safe steps before encounters */
    fun safeSteps(steps: Int) {
        safeSteps = steps
    }

    /** Set initial encounter chance */
    fun initialChance(chance: Int) {
        initialChance = chance
    }

    /** Set chance increment per step */
    fun incrementPerStep(increment: Int) {
        incrementPerStep = increment
    }

    /** Set maximum encounter chance */
    fun maxChance(chance: Int) {
        maxChance = chance
    }

    /** Set whether to reset counter after encounter */
    fun resetOnEncounter(reset: Boolean) {
        resetOnEncounter = reset
    }

    override fun build() =
        StepBasedTrigger(
            id = triggerId,
            safeSteps = safeSteps,
            initialChance = initialChance,
            incrementPerStep = incrementPerStep,
            maxChance = maxChance,
            resetOnEncounter = resetOnEncounter,
            encounterTable = encounterTable,
            onTriggerStatements = onTriggerStatements,
        )
}

/** Builder for time-based triggers. */
@GbktDsl
class TimeTriggerBuilder(triggerId: String) : EncounterTriggerBuilder(triggerId) {
    private var safeFrames: Int = 300
    private var checkInterval: Int = 60
    private var baseChance: Int = 10
    private var idleMultiplier: Int = 50
    private var movingMultiplier: Int = 100

    /** Set safe frames before encounters */
    fun safeFrames(frames: Int) {
        safeFrames = frames
    }

    /** Set check interval in frames */
    fun checkInterval(frames: Int) {
        checkInterval = frames
    }

    /** Set base encounter chance */
    fun baseChance(chance: Int) {
        baseChance = chance
    }

    /** Set multiplier when idle (percentage) */
    fun idleMultiplier(multiplier: Int) {
        idleMultiplier = multiplier
    }

    /** Set multiplier when moving (percentage) */
    fun movingMultiplier(multiplier: Int) {
        movingMultiplier = multiplier
    }

    override fun build() =
        TimeBasedTrigger(
            id = triggerId,
            safeFrames = safeFrames,
            checkInterval = checkInterval,
            baseChance = baseChance,
            idleMultiplier = idleMultiplier,
            movingMultiplier = movingMultiplier,
            encounterTable = encounterTable,
            onTriggerStatements = onTriggerStatements,
        )
}

/** Builder for region-based triggers. */
@GbktDsl
class RegionTriggerBuilder(triggerId: String) : EncounterTriggerBuilder(triggerId) {
    private val dangerZones = mutableListOf<DangerZone>()
    private var checkInterval: Int = 60

    /** Add a danger zone */
    fun dangerZone(x1: Int, y1: Int, x2: Int, y2: Int, chance: Int, checkInterval: Int = 60) {
        dangerZones.add(DangerZone(x1, y1, x2, y2, chance, checkInterval))
    }

    /** Set default check interval */
    fun checkInterval(frames: Int) {
        checkInterval = frames
    }

    override fun build() =
        RegionBasedTrigger(
            id = triggerId,
            dangerZones = dangerZones.toList(),
            checkInterval = checkInterval,
            encounterTable = encounterTable,
            onTriggerStatements = onTriggerStatements,
        )
}

/** Builder for wave-based triggers. */
@GbktDsl
class WaveTriggerBuilder(triggerId: String) : EncounterTriggerBuilder(triggerId) {
    private val waves = mutableListOf<WaveConfig>()
    private var loopWaves: Boolean = false
    private var loopScaling: Int = 10
    private var onWaveCompleteStatements: List<IRStatement> = emptyList()

    /** Add a wave */
    fun wave(waveNumber: Int, delay: Int = 0, init: WaveBuilder.() -> Unit) {
        val builder = WaveBuilder(waveNumber, delay)
        builder.init()
        waves.add(builder.build())
    }

    /** Enable wave looping */
    fun loop(scaling: Int = 10) {
        loopWaves = true
        loopScaling = scaling
    }

    /** Callback between waves */
    fun onWaveComplete(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onWaveCompleteStatements = recorder.statements
    }

    override fun build() =
        WaveBasedTrigger(
            id = triggerId,
            waves = waves.toList(),
            loopWaves = loopWaves,
            loopScaling = loopScaling,
            encounterTable = encounterTable,
            onTriggerStatements = onTriggerStatements,
            onWaveCompleteStatements = onWaveCompleteStatements,
        )
}

/** Builder for individual waves. */
@GbktDsl
class WaveBuilder(private val waveNumber: Int, private val delay: Int) {
    private val monsters = mutableListOf<String>()
    private val spawnPositions = mutableListOf<Pair<Int, Int>>()

    /** Add a monster to this wave */
    fun monster(monsterId: String) {
        monsters.add(monsterId)
    }

    /** Add multiple monsters */
    fun monsters(vararg monsterIds: String) {
        monsters.addAll(monsterIds)
    }

    /** Add a spawn position */
    fun spawnAt(x: Int, y: Int) {
        spawnPositions.add(x to y)
    }

    internal fun build() =
        WaveConfig(
            waveNumber = waveNumber,
            delay = delay,
            monsters = monsters.toList(),
            spawnPositions = if (spawnPositions.isEmpty()) null else spawnPositions.toList(),
        )
}
