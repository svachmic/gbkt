/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// COMBAT ENGINE IR TYPES
// =============================================================================

/**
 * Lifecycle injection points in the combat state machine.
 *
 * Each value represents a hook point where custom [ScriptOp] lists can be injected by the
 * developer. Hooks registered on a [CombatEngineSystem] via [CombatEngineSystem.combatHooks] are
 * emitted as generated C functions at each corresponding point in the combat loop.
 *
 * Zero overhead when no hooks are registered — hook functions are only emitted when
 * [CombatEngineSystem.combatHooks] contains entries for the given point.
 */
enum class CombatHookPoint {
    /** Before any combatant's action is executed. */
    BEFORE_ACTION,

    /** After any combatant's action completes. */
    AFTER_ACTION,

    /** After damage is dealt to any combatant. */
    AFTER_DAMAGE,

    /** Before each combat turn begins. */
    BEFORE_TURN,

    /** After each combat turn completes. */
    AFTER_TURN,

    /** When victory condition is met (before state transition). */
    ON_VICTORY,

    /** When defeat condition is met (before state transition). */
    ON_DEFEAT,
}

/**
 * Top-level IR node for the turn-based or real-time combat engine system.
 *
 * Carries the complete configuration for the combat engine: combatant slots, state hierarchy,
 * victory/defeat conditions, and action ops. Codegen (Plan 02) translates this into C state machine
 * code.
 *
 * @property id Unique identifier for this combat engine instance.
 * @property combatType Whether combat is turn-based, real-time, ATB, or wave-survival.
 * @property combatants Abstract combatant slot definitions (player/enemy sides with turn control).
 * @property onVictoryCondition Declarative condition predicate — checked each frame; triggers
 *   VICTORY state when evaluates to true.
 * @property onDefeatCondition Declarative condition predicate — checked each frame; triggers DEFEAT
 *   state when evaluates to true.
 * @property onVictoryOps Script ops executed after entering the VICTORY state.
 * @property onDefeatOps Script ops executed after entering the DEFEAT state.
 * @property customStates Extensibility hook: game-defined state IDs beyond the built-in set.
 * @property stateHierarchy Parent→children sub-state map for hierarchical state nesting (e.g.
 *   PLAYER_TURN → [SELECTING_ACTION, SELECTING_TARGET]).
 * @property damageFormula Optional reference to a pluggable C damage formula function.
 * @property maxCombatants Maximum active combatants — used for array sizing in codegen.
 * @property waveSurvivalConfig Wave survival configuration. Only used when [combatType] is
 *   [CombatType.WAVE_SURVIVAL]. Null for non-wave-survival games.
 * @property atbConfig ATB-specific configuration. Only used when [combatType] is [CombatType.ATB].
 *   Null for non-ATB games.
 * @property turnOrderStrategy How turn order is determined. Null uses the default for each
 *   [CombatType] (TURN_BASED defaults to FIXED_ORDER; ATB uses gauge fill order).
 * @property combatHooks Map from [CombatHookPoint] to lists of [ScriptOp] to execute at each
 *   lifecycle injection point. Empty by default — no hooks emitted (zero overhead).
 * @property tacticalGridConfig Tactical grid configuration. Only used when [combatType] is
 *   [CombatType.TACTICAL_GRID]. Null for non-tactical-grid games.
 * @property encounterConfig Optional encounter/party configuration map populated by
 *   [io.github.gbkt.rpg.dsl.SimpleBattleBuilder]. Keys: `"partyIds"` (List<String>),
 *   `"encounterData"` (List of encounter entries), `"onVictoryOps"`, `"onDefeatOps"`. Null for
 *   non-simpleBattle combat systems. Backward-compatible null default.
 */
data class CombatEngineSystem(
    override val id: String,
    val combatType: CombatType = CombatType.TURN_BASED,
    val combatants: List<CombatantDef> = emptyList(),
    val onVictoryCondition: List<ScriptOp> = emptyList(),
    val onDefeatCondition: List<ScriptOp> = emptyList(),
    val onVictoryOps: List<ScriptOp> = emptyList(),
    val onDefeatOps: List<ScriptOp> = emptyList(),
    val customStates: List<CombatStateId> = emptyList(),
    val stateHierarchy: Map<CombatStateId, List<CombatStateId>> = emptyMap(),
    val damageFormula: DamageFormulaRef? = null,
    val maxCombatants: Int = 8,
    val waveSurvivalConfig: WaveSurvivalConfig? = null,
    val atbConfig: AtbConfig? = null,
    val turnOrderStrategy: TurnOrderStrategy? = null,
    val combatHooks: Map<CombatHookPoint, List<ScriptOp>> = emptyMap(),
    val tacticalGridConfig: TacticalGridConfig? = null,
    val encounterConfig: Map<String, Any>? = null,
    override val sourceLocation: SourceLocation? = null,
    override val bankSlot: BankSlot? = null,
    override val vramRange: VRAMRange? = null,
    override val oamSlot: OAMSlot? = null,
) : SystemIR {
    override fun <T> accept(visitor: SystemIRVisitorI<T>): T = visitor.visitCombatEngineSystem(this)
}

/** Whether the combat engine runs in turn-based or real-time mode. */
enum class CombatType {
    /** Each combatant acts in sequence determined by turn order strategy. */
    TURN_BASED,

    /** All combatants act simultaneously in real time. */
    REAL_TIME,

    /**
     * Active Time Battle — classic FF-style gauge-fill model.
     *
     * Each combatant has a gauge that fills over time based on their speed (agility) stat. When a
     * gauge fills to [AtbConfig.maxGauge], the combatant can act. Gauge behavior is controlled by
     * [AtbConfig.waitMode] and [AtbConfig.gaugeModel].
     */
    ATB,

    /** Endless wave-based survival mode — waves of enemies spawn in sequence. */
    WAVE_SURVIVAL,

    /**
     * Grid-based tactical combat (SRPG variant).
     *
     * Combatants occupy tiles on a 2D grid. Movement range, terrain types, elevation, facing, and
     * AoE ability patterns are all supported. Requires a [TacticalGridConfig] to be set on the
     * [CombatEngineSystem] via [CombatEngineSystem.tacticalGridConfig].
     */
    TACTICAL_GRID,
}

/**
 * Strategy for ordering combatant turns.
 *
 * Applies to both [CombatType.TURN_BASED] and [CombatType.ATB] modes. For [CombatType.REAL_TIME]
 * all combatants act simultaneously so turn order is not applicable.
 */
enum class TurnOrderStrategy {
    /**
     * Faster combatants act first — order determined by agility (agl) stat each round.
     *
     * Codegen emits a `compute_turn_order_<id>()` function using insertion sort (O(n^2), fine for
     * N<=8 combatants on Game Boy hardware).
     */
    SPEED_BASED,

    /**
     * Predefined sequence: players first, then enemies, in registration order.
     *
     * Codegen emits a `static const UINT8 _turn_order_<id>[]` array.
     */
    FIXED_ORDER,
}

/**
 * ATB (Active Time Battle) configuration for [CombatEngineSystem] with [CombatType.ATB].
 *
 * Controls how gauges fill, how wait/active mode affects gauge progression during menus, and the
 * base fill rate relative to combatant agility.
 *
 * @property gaugeModel Classic FILL (gauge from 0 to max then act) or CHARGE (per-action
 *   countdown).
 * @property waitMode WAIT pauses gauges when a menu is open; ACTIVE keeps them filling.
 * @property baseGaugeFillRate Base gauge fill per frame before agility modifier. Actual rate is
 *   `baseGaugeFillRate + (agl >> 2)`.
 * @property maxGauge Maximum gauge value (255 = UINT8 max — no overflow risk).
 * @property allowPlayerToggle When true, exposes a Wait/Active toggle to the player in-game.
 */
data class AtbConfig(
    val gaugeModel: AtbGaugeModel = AtbGaugeModel.FILL,
    val waitMode: AtbWaitMode = AtbWaitMode.WAIT,
    val baseGaugeFillRate: Int = 4,
    val maxGauge: Int = 255,
    val allowPlayerToggle: Boolean = false,
)

/** ATB gauge progression model. */
enum class AtbGaugeModel {
    /** Classic FF — gauge fills from 0 to max, then combatant acts. */
    FILL,

    /** Actions have cast times — gauge fills per-action charge counter (counts down). */
    CHARGE,
}

/** Controls whether ATB gauges continue filling while menus are open. */
enum class AtbWaitMode {
    /** Gauge pauses when any menu is open — safe for slower players. */
    WAIT,

    /** Gauge keeps filling during menus — increases tension but requires fast input. */
    ACTIVE,
}

/**
 * Tactical grid combat configuration for [CombatEngineSystem] with [CombatType.TACTICAL_GRID].
 *
 * Placed in gbkt-ir (alongside [AtbConfig]) so that [CombatEngineSystem] can reference it directly
 * without circular module dependencies (gbkt-ir has zero external deps by design).
 *
 * @property gridWidth Number of grid columns.
 * @property gridHeight Number of grid rows.
 * @property enableTerrain Whether terrain movement cost and defense effects are active.
 * @property enableElevation Whether height differences affect damage calculations.
 * @property enableFacing Whether facing direction affects flanking/backstab bonuses.
 * @property flankingBonus Percentage damage bonus when attacking from the side (+25% default).
 * @property backstabBonus Percentage damage bonus when attacking from behind (+50% default).
 * @property terrainTypes List of terrain type definitions. Defaults include PLAIN, FOREST, WALL.
 * @property elevationDamageBonus Percentage damage bonus per height advantage tile (+10% default).
 * @property baseMovementRange Default movement range in tiles for a unit with no stat modifiers.
 */
data class TacticalGridConfig(
    val gridWidth: Int = 8,
    val gridHeight: Int = 8,
    val enableTerrain: Boolean = true,
    val enableElevation: Boolean = false,
    val enableFacing: Boolean = false,
    val flankingBonus: Int = 25,
    val backstabBonus: Int = 50,
    val terrainTypes: List<TerrainTypeDef> = defaultTerrainTypes(),
    val elevationDamageBonus: Int = 10,
    val baseMovementRange: Int = 3,
)

/**
 * Definition of a terrain tile type used in the tactical grid.
 *
 * @property id Unique string identifier (e.g. "plain", "forest", "wall").
 * @property name Display name for UI.
 * @property movementCost Movement points required to enter this tile. 1 = normal, 2 = slow, 0 =
 *   blocked, -1 = impassable.
 * @property damagePerTurn Damage dealt to any unit occupying this tile at turn end (terrain DOT).
 * @property defenseBonus Cover/defense percentage bonus for units on this terrain.
 */
data class TerrainTypeDef(
    val id: String,
    val name: String,
    val movementCost: Int = 1,
    val damagePerTurn: Int = 0,
    val defenseBonus: Int = 0,
)

/** Facing direction for a unit on the tactical grid. Used for flanking/backstab calculations. */
enum class FacingDirection {
    NORTH,
    SOUTH,
    EAST,
    WEST,
}

/**
 * Returns the default terrain type list: PLAIN (normal), FOREST (slow/cover), WALL (impassable).
 */
fun defaultTerrainTypes(): List<TerrainTypeDef> =
    listOf(
        TerrainTypeDef(id = "plain", name = "Plain", movementCost = 1),
        TerrainTypeDef(id = "forest", name = "Forest", movementCost = 2, defenseBonus = 20),
        TerrainTypeDef(id = "wall", name = "Wall", movementCost = -1),
    )

/**
 * Typed state identifier for combat states.
 *
 * Replaces magic strings in state configuration. Use the predefined constants in `gbkt-engine`'s
 * `CombatTypes.kt` (COMBAT_INIT, PLAYER_TURN, etc.) or define custom states via
 * [CombatEngineSystem.customStates].
 */
@JvmInline value class CombatStateId(val id: String)

/**
 * An abstract combatant slot definition.
 *
 * @property id Unique identifier for this combatant slot.
 * @property side Which side of the battle this combatant is on (player or enemy).
 * @property canAct Whether this combatant receives turns. False = environmental/passive entity that
 *   participates in combat but never acts (e.g., a trapped character, an obstacle).
 */
data class CombatantDef(val id: String, val side: CombatantSide, val canAct: Boolean = true)

/** Which side of the battle a combatant belongs to. */
enum class CombatantSide {
    PLAYER,
    ENEMY,
}

/**
 * Opaque reference to a C damage formula function.
 *
 * The referenced function must have signature `UINT16 <name>(UINT8 atk, UINT8 def)` and will be
 * called by the generated combat engine update loop.
 */
@JvmInline value class DamageFormulaRef(val functionName: String)
