/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.combat

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// BATTLE ENGINE - Abstract combat system for different game genres
// =============================================================================

/**
 * Battle engine type for different combat styles.
 *
 * The battle engine abstracts the fundamental combat model, allowing games to use turn-based,
 * real-time, or tactical combat without changing game logic.
 */
enum class CombatType {
    /** Traditional turn-based JRPG combat (Dragon Quest, Final Fantasy 1-3) */
    TURN_BASED,

    /** Active Time Battle - turns based on speed gauge (Final Fantasy 4-9) */
    ACTIVE_TIME,

    /** Real-time action combat (Zelda, Secret of Mana) */
    REAL_TIME,

    /** Grid-based tactical combat (Fire Emblem, Final Fantasy Tactics) */
    TACTICAL,

    /** Wave-based survival (Tower defense, Arena) */
    WAVE_SURVIVAL,
}

/**
 * Battle outcome types.
 *
 * Expanded beyond simple victory/defeat to support more game types.
 */
enum class BattleOutcome {
    /** Player won the battle */
    VICTORY,

    /** Player lost the battle */
    DEFEAT,

    /** Player successfully fled */
    FLED,

    /** Battle ended in a draw (time limit, mutual destruction) */
    DRAW,

    /** Enemy surrendered or was captured */
    CAPTURE,

    /** Battle interrupted by event */
    INTERRUPTED,
}

/**
 * Abstract battle engine interface.
 *
 * This interface defines the contract for all combat systems. Implementations handle the specifics
 * of turn-based, real-time, or tactical combat.
 *
 * Usage:
 * ```kotlin
 * val combat by turnBasedBattle {
 *     name("Main Combat")
 *     maxPartySize(4)
 *     maxEnemies(4)
 *
 *     onVictory { awardExp(); scene(gameplayScene) }
 *     onDefeat { scene(gameOverScene) }
 * }
 *
 * // Or for action games:
 * val combat by realTimeBattle {
 *     name("Action Combat")
 *     hitStunFrames(10)
 *     invincibilityFrames(60)
 *
 *     onHit { flashSprite(); playSound(hit) }
 *     onDeath { scene(gameOverScene) }
 * }
 * ```
 */
interface BattleEngine {
    /** Unique identifier */
    val id: String

    /** Combat type */
    val combatType: CombatType

    /** Maximum party size */
    val maxPartySize: Int

    /** Maximum enemy count */
    val maxEnemies: Int

    /** Callback statements for victory */
    val onVictoryStatements: List<IRStatement>

    /** Callback statements for defeat */
    val onDefeatStatements: List<IRStatement>

    /** System index for code generation */
    var systemIndex: Int
}

// =============================================================================
// TURN-BASED BATTLE ENGINE
// =============================================================================

/** Turn order strategy for turn-based combat. */
enum class TurnOrderStrategy {
    /** Speed-based ordering (faster characters act first) */
    SPEED_BASED,

    /** Fixed order (party first, then enemies) */
    FIXED_ORDER,

    /** Round-robin (alternate party/enemy) */
    ROUND_ROBIN,

    /** Initiative roll (random with speed modifier) */
    INITIATIVE,

    /** Custom (fully user-defined) */
    CUSTOM,
}

/**
 * Turn-based battle engine.
 *
 * Traditional JRPG combat with discrete turns and action selection.
 */
class TurnBasedBattleEngine(
    override val id: String,
    /** Display name */
    val name: String,
    override val maxPartySize: Int,
    override val maxEnemies: Int,
    /** Turn order strategy */
    val turnOrderStrategy: TurnOrderStrategy,
    /** Base flee chance (percentage) */
    val fleeChanceBase: Int,
    /** Flee chance bonus per agility point */
    val fleeChancePerAgility: Int,
    /** Whether to allow fleeing */
    val allowFlee: Boolean,
    /** Custom battle states beyond the built-in ones */
    val customStates: List<String>,
    override val onVictoryStatements: List<IRStatement>,
    override val onDefeatStatements: List<IRStatement>,
    /** Callback when fleeing succeeds */
    val onFleeStatements: List<IRStatement>,
    /** Callbacks for specific battle states */
    val stateCallbacks: Map<String, List<IRStatement>>,
    override var systemIndex: Int = -1,
) : BattleEngine {
    override val combatType: CombatType = CombatType.TURN_BASED
}

// =============================================================================
// ACTIVE TIME BATTLE ENGINE
// =============================================================================

/**
 * Active Time Battle engine.
 *
 * ATB-style combat where turns are determined by a charging gauge.
 */
class ActiveTimeBattleEngine(
    override val id: String,
    /** Display name */
    val name: String,
    override val maxPartySize: Int,
    override val maxEnemies: Int,
    /** Base gauge fill rate */
    val baseFillRate: Int,
    /** Speed stat multiplier for gauge fill */
    val speedMultiplier: Int,
    /** Whether time pauses during menus */
    val pauseOnMenu: Boolean,
    /** Whether time pauses during animations */
    val pauseOnAnimation: Boolean,
    override val onVictoryStatements: List<IRStatement>,
    override val onDefeatStatements: List<IRStatement>,
    /** Callback when a character's gauge fills */
    val onGaugeFullStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : BattleEngine {
    override val combatType: CombatType = CombatType.ACTIVE_TIME
}

// =============================================================================
// REAL-TIME BATTLE ENGINE
// =============================================================================

/**
 * Real-time battle engine.
 *
 * Action combat with hit detection, i-frames, and real-time movement.
 */
class RealTimeBattleEngine(
    override val id: String,
    /** Display name */
    val name: String,
    override val maxPartySize: Int,
    override val maxEnemies: Int,
    /** Hit stun duration in frames */
    val hitStunFrames: Int,
    /** Invincibility frames after taking damage */
    val invincibilityFrames: Int,
    /** Knockback distance on hit */
    val knockbackDistance: Int,
    /** Whether attacks can be cancelled into other attacks */
    val attackCancelling: Boolean,
    /** Whether player can block */
    val allowBlock: Boolean,
    /** Damage reduction when blocking (percentage) */
    val blockReduction: Int,
    override val onVictoryStatements: List<IRStatement>,
    override val onDefeatStatements: List<IRStatement>,
    /** Callback when player takes damage */
    val onHitStatements: List<IRStatement>,
    /** Callback when player blocks */
    val onBlockStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : BattleEngine {
    override val combatType: CombatType = CombatType.REAL_TIME
}

// =============================================================================
// TACTICAL BATTLE ENGINE
// =============================================================================

/**
 * Tactical battle engine.
 *
 * Grid-based tactical combat with movement and positioning.
 */
class TacticalBattleEngine(
    override val id: String,
    /** Display name */
    val name: String,
    override val maxPartySize: Int,
    override val maxEnemies: Int,
    /** Grid width in tiles */
    val gridWidth: Int,
    /** Grid height in tiles */
    val gridHeight: Int,
    /** Base movement range */
    val baseMoveRange: Int,
    /** Whether facing direction matters */
    val facingMatters: Boolean,
    /** Bonus damage from flanking/backstab */
    val flankingBonus: Int,
    /** Height difference damage modifier */
    val heightBonus: Int,
    /** Turn order strategy */
    val turnOrder: TurnOrderStrategy,
    override val onVictoryStatements: List<IRStatement>,
    override val onDefeatStatements: List<IRStatement>,
    /** Callback when unit moves */
    val onUnitMoveStatements: List<IRStatement>,
    /** Callback when unit attacks */
    val onUnitAttackStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : BattleEngine {
    override val combatType: CombatType = CombatType.TACTICAL
}

// =============================================================================
// BATTLE ENGINE BUILDERS
// =============================================================================

/** Base builder for battle engines. */
@GbktDsl
abstract class BattleEngineBuilder(protected val engineId: String) {
    protected var name: String = engineId
    protected var maxPartySize: Int = 4
    protected var maxEnemies: Int = 4
    protected var onVictoryStatements: List<IRStatement> = emptyList()
    protected var onDefeatStatements: List<IRStatement> = emptyList()

    /** Set the display name */
    fun name(value: String) {
        name = value
    }

    /** Set maximum party size */
    fun maxPartySize(size: Int) {
        require(size in 1..8) { "Party size must be between 1 and 8" }
        maxPartySize = size
    }

    /** Set maximum enemy count */
    fun maxEnemies(count: Int) {
        require(count in 1..8) { "Enemy count must be between 1 and 8" }
        maxEnemies = count
    }

    /** Callback on victory */
    fun onVictory(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onVictoryStatements = recorder.statements
    }

    /** Callback on defeat */
    fun onDefeat(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onDefeatStatements = recorder.statements
    }

    abstract fun build(): BattleEngine
}

/** Builder for turn-based battle engines. */
@GbktDsl
class TurnBasedBattleBuilder(engineId: String) : BattleEngineBuilder(engineId) {
    private var turnOrderStrategy: TurnOrderStrategy = TurnOrderStrategy.SPEED_BASED
    private var fleeChanceBase: Int = 50
    private var fleeChancePerAgility: Int = 2
    private var allowFlee: Boolean = true
    private val customStates = mutableListOf<String>()
    private var onFleeStatements: List<IRStatement> = emptyList()
    private val stateCallbacks = mutableMapOf<String, List<IRStatement>>()

    /** Set turn order strategy */
    fun turnOrder(strategy: TurnOrderStrategy) {
        turnOrderStrategy = strategy
    }

    /** Configure flee mechanics */
    fun fleeMechanics(baseChance: Int = 50, perAgility: Int = 2) {
        fleeChanceBase = baseChance
        fleeChancePerAgility = perAgility
    }

    /** Disable fleeing */
    fun disableFlee() {
        allowFlee = false
    }

    /** Add a custom battle state */
    fun customState(stateName: String) {
        customStates.add(stateName)
    }

    /** Callback on successful flee */
    fun onFlee(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onFleeStatements = recorder.statements
    }

    /** Callback for a specific battle state */
    fun onState(stateName: String, init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        stateCallbacks[stateName] = recorder.statements
    }

    override fun build() =
        TurnBasedBattleEngine(
            id = engineId,
            name = name,
            maxPartySize = maxPartySize,
            maxEnemies = maxEnemies,
            turnOrderStrategy = turnOrderStrategy,
            fleeChanceBase = fleeChanceBase,
            fleeChancePerAgility = fleeChancePerAgility,
            allowFlee = allowFlee,
            customStates = customStates.toList(),
            onVictoryStatements = onVictoryStatements,
            onDefeatStatements = onDefeatStatements,
            onFleeStatements = onFleeStatements,
            stateCallbacks = stateCallbacks.toMap(),
        )
}

/** Builder for active time battle engines. */
@GbktDsl
class ActiveTimeBattleBuilder(engineId: String) : BattleEngineBuilder(engineId) {
    private var baseFillRate: Int = 4
    private var speedMultiplier: Int = 2
    private var pauseOnMenu: Boolean = true
    private var pauseOnAnimation: Boolean = false
    private var onGaugeFullStatements: List<IRStatement> = emptyList()

    /** Set base gauge fill rate */
    fun baseFillRate(rate: Int) {
        baseFillRate = rate
    }

    /** Set speed stat multiplier */
    fun speedMultiplier(multiplier: Int) {
        speedMultiplier = multiplier
    }

    /** Configure time pausing */
    fun pauseSettings(onMenu: Boolean = true, onAnimation: Boolean = false) {
        pauseOnMenu = onMenu
        pauseOnAnimation = onAnimation
    }

    /** Callback when gauge fills */
    fun onGaugeFull(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onGaugeFullStatements = recorder.statements
    }

    override fun build() =
        ActiveTimeBattleEngine(
            id = engineId,
            name = name,
            maxPartySize = maxPartySize,
            maxEnemies = maxEnemies,
            baseFillRate = baseFillRate,
            speedMultiplier = speedMultiplier,
            pauseOnMenu = pauseOnMenu,
            pauseOnAnimation = pauseOnAnimation,
            onVictoryStatements = onVictoryStatements,
            onDefeatStatements = onDefeatStatements,
            onGaugeFullStatements = onGaugeFullStatements,
        )
}

/** Builder for real-time battle engines. */
@GbktDsl
class RealTimeBattleBuilder(engineId: String) : BattleEngineBuilder(engineId) {
    private var hitStunFrames: Int = 10
    private var invincibilityFrames: Int = 60
    private var knockbackDistance: Int = 8
    private var attackCancelling: Boolean = false
    private var allowBlock: Boolean = true
    private var blockReduction: Int = 50
    private var onHitStatements: List<IRStatement> = emptyList()
    private var onBlockStatements: List<IRStatement> = emptyList()

    /** Set hit stun duration */
    fun hitStun(frames: Int) {
        hitStunFrames = frames
    }

    /** Set invincibility frames */
    fun invincibility(frames: Int) {
        invincibilityFrames = frames
    }

    /** Set knockback distance */
    fun knockback(distance: Int) {
        knockbackDistance = distance
    }

    /** Enable attack cancelling */
    fun attackCancelling(enabled: Boolean) {
        attackCancelling = enabled
    }

    /** Configure blocking */
    fun blocking(enabled: Boolean, reduction: Int = 50) {
        allowBlock = enabled
        blockReduction = reduction
    }

    /** Callback when taking damage */
    fun onHit(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onHitStatements = recorder.statements
    }

    /** Callback when blocking */
    fun onBlock(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onBlockStatements = recorder.statements
    }

    override fun build() =
        RealTimeBattleEngine(
            id = engineId,
            name = name,
            maxPartySize = maxPartySize,
            maxEnemies = maxEnemies,
            hitStunFrames = hitStunFrames,
            invincibilityFrames = invincibilityFrames,
            knockbackDistance = knockbackDistance,
            attackCancelling = attackCancelling,
            allowBlock = allowBlock,
            blockReduction = blockReduction,
            onVictoryStatements = onVictoryStatements,
            onDefeatStatements = onDefeatStatements,
            onHitStatements = onHitStatements,
            onBlockStatements = onBlockStatements,
        )
}

/** Builder for tactical battle engines. */
@GbktDsl
class TacticalBattleBuilder(engineId: String) : BattleEngineBuilder(engineId) {
    private var gridWidth: Int = 16
    private var gridHeight: Int = 16
    private var baseMoveRange: Int = 4
    private var facingMatters: Boolean = true
    private var flankingBonus: Int = 25
    private var heightBonus: Int = 10
    private var turnOrder: TurnOrderStrategy = TurnOrderStrategy.SPEED_BASED
    private var onUnitMoveStatements: List<IRStatement> = emptyList()
    private var onUnitAttackStatements: List<IRStatement> = emptyList()

    /** Set grid size */
    fun gridSize(width: Int, height: Int) {
        gridWidth = width
        gridHeight = height
    }

    /** Set base movement range */
    fun baseMoveRange(range: Int) {
        baseMoveRange = range
    }

    /** Configure facing mechanics */
    fun facing(enabled: Boolean, flankBonus: Int = 25) {
        facingMatters = enabled
        flankingBonus = flankBonus
    }

    /** Set height bonus */
    fun heightBonus(bonus: Int) {
        heightBonus = bonus
    }

    /** Set turn order strategy */
    fun turnOrder(strategy: TurnOrderStrategy) {
        turnOrder = strategy
    }

    /** Callback when unit moves */
    fun onUnitMove(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onUnitMoveStatements = recorder.statements
    }

    /** Callback when unit attacks */
    fun onUnitAttack(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onUnitAttackStatements = recorder.statements
    }

    override fun build() =
        TacticalBattleEngine(
            id = engineId,
            name = name,
            maxPartySize = maxPartySize,
            maxEnemies = maxEnemies,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            baseMoveRange = baseMoveRange,
            facingMatters = facingMatters,
            flankingBonus = flankingBonus,
            heightBonus = heightBonus,
            turnOrder = turnOrder,
            onVictoryStatements = onVictoryStatements,
            onDefeatStatements = onDefeatStatements,
            onUnitMoveStatements = onUnitMoveStatements,
            onUnitAttackStatements = onUnitAttackStatements,
        )
}
