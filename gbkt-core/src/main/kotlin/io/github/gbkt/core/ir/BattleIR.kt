/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.BattleActionType
import io.github.gbkt.core.rpg.BattlePresentationConfig
import io.github.gbkt.core.rpg.BattleState

// =============================================================================
// BATTLE SYSTEM IR NODES
// =============================================================================

/**
 * IR node for registering a complete battle system.
 *
 * Generates:
 * - Battle state variable and constants
 * - Party and enemy slot arrays
 * - Turn order tracking
 * - State machine update function
 * - State callback functions
 */
data class IRBattleSystem(
    val name: String,
    val maxPartySize: Int,
    val maxEnemies: Int,
    val stateCallbacks: Map<BattleState, List<IRStatement>>,
    val onVictory: List<IRStatement>,
    val onDefeat: List<IRStatement>,
    val onFlee: List<IRStatement>,
    val fleeChanceBase: Int,
    val fleeChancePerAgility: Int,
    val presentation: BattlePresentationConfig = BattlePresentationConfig(),
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for starting a battle.
 *
 * Generates code to:
 * - Initialize battle state to INIT
 * - Populate enemy slots
 * - Reset turn counters
 */
data class IRBattleStart(
    val systemName: String,
    val enemyNames: List<String>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for ending a battle.
 *
 * Generates code to:
 * - Set battle result
 * - Call appropriate end handler (victory/defeat/fled)
 * - Clean up battle state
 */
data class IRBattleEnd(
    val systemName: String,
    val result: BattleState,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for transitioning between battle states. */
data class IRBattleStateTransition(
    val systemName: String,
    val targetState: BattleState,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for queueing a battle action. */
data class IRBattleAction(
    val actionType: BattleActionType,
    val actorName: String,
    val targetNames: List<String>,
    val abilityId: Int?,
    val itemId: Int?,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// BATTLE QUERY EXPRESSIONS
// =============================================================================

/** IR expression for getting current battle state. */
data class IRGetBattleState(val systemName: String) : IRExpression

/** IR expression for checking if in battle. */
data class IRIsInBattle(val systemName: String) : IRExpression

/** IR expression for checking if battle state matches. */
data class IRIsBattleState(val systemName: String, val state: BattleState) : IRExpression

/** IR expression for getting current turn number. */
data class IRGetTurnNumber(val systemName: String) : IRExpression

/** IR expression for getting current actor index. */
data class IRGetCurrentActor(val systemName: String) : IRExpression

/** IR expression for getting enemy count in current battle. */
data class IRGetEnemyCount(val systemName: String) : IRExpression

/** IR expression for getting alive enemy count. */
data class IRGetAliveEnemyCount(val systemName: String) : IRExpression

/** IR expression for getting alive party count. */
data class IRGetAlivePartyCount(val systemName: String) : IRExpression

/** IR expression for checking if all enemies are defeated. */
data class IRAllEnemiesDefeated(val systemName: String) : IRExpression

/** IR expression for checking if all party members are defeated. */
data class IRAllPartyDefeated(val systemName: String) : IRExpression

// =============================================================================
// BATTLE PRESENTATION IR NODES
// =============================================================================

/**
 * IR node for showing a battle message.
 *
 * Generates code to display a message in the battle message area. Messages auto-advance after the
 * configured duration.
 */
data class IRBattleMessage(
    val message: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for showing a floating damage number.
 *
 * Generates code to display a damage or healing number above a target combatant.
 */
data class IRShowDamageNumber(
    val targetIndex: Int,
    val amount: Int,
    val isCrit: Boolean = false,
    val isHeal: Boolean = false,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// BATTLE INITIALIZATION IR NODES
// =============================================================================

/**
 * IR node for initializing the party from a character class selection.
 *
 * Generates code to call `_party_init_from_class(classId)` which populates party member stats based
 * on the selected character class. The class ID mapping is game-specific.
 */
data class IRInitPartyFromClass(
    val classIdExpr: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for initializing battle from a pending encounter.
 *
 * Generates code to call `_battle_init_from_encounter()` which populates enemy slots from the
 * encounter data set by the exploration system.
 */
data class IRInitBattleFromEncounter(override val sourceLocation: SourceLocation? = null) :
    IRStatement

/**
 * IR node for initializing battle with specific monsters.
 *
 * Use this for boss encounters or scripted battles where you want to fight specific monsters rather
 * than using the random encounter system.
 *
 * Generates code to call `_battle_init_with_monsters({monsterIndices})` which directly populates
 * enemy slots with the specified monsters.
 */
data class IRInitBattleWithMonsters(
    val monsterNames: List<String>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// COMBAT QUERY EXPRESSIONS
// =============================================================================

/**
 * IR expression for getting the current party count in combat.
 *
 * Returns the number of party members currently in the battle (used for target index calculations
 * where enemies follow party members in arrays).
 */
data object IRCombatPartyCount : IRExpression

/**
 * IR expression for checking if combat is in a specific state.
 *
 * @param stateName The combat state constant name (e.g., "COMBAT_STATE_VICTORY")
 */
data class IRCombatIsState(val stateName: String) : IRExpression

// =============================================================================
// COMBAT ACTION IR NODES
// =============================================================================

/**
 * IR node for confirming target selection in combat.
 *
 * Generates code to call `_combat_target_confirmed(targetIndex)` which records the selected target
 * for the current action.
 */
data class IRCombatTargetConfirmed(
    val targetIndexExpr: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for selecting an item in combat.
 *
 * Generates code to call `_combat_item_selected(itemIndex)` which queues the item for use on the
 * next action.
 */
data class IRCombatItemSelected(
    val itemIndexExpr: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for transitioning to a combat state.
 *
 * Generates code to call `_combat_battle_transition(state)` which changes the combat state machine
 * to the target state.
 */
data class IRCombatBattleTransition(
    val targetState: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// BATTLE UPDATE IR NODE
// =============================================================================

/**
 * IR node for updating the battle state machine.
 *
 * Generates code to call `_${systemName}_battle_update()` which processes one frame of the battle
 * state machine. This should be called every frame during battle.
 */
data class IRBattleUpdate(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
