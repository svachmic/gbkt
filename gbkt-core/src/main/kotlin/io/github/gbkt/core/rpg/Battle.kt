/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("TooManyFunctions") // Battle system requires many helper functions

package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRBattleAction
import io.github.gbkt.core.ir.IRBattleEnd
import io.github.gbkt.core.ir.IRBattleStart
import io.github.gbkt.core.ir.IRBattleStateTransition
import io.github.gbkt.core.ir.IRBattleUpdate
import io.github.gbkt.core.ir.IRCombatBattleTransition
import io.github.gbkt.core.ir.IRCombatIsState
import io.github.gbkt.core.ir.IRCombatItemSelected
import io.github.gbkt.core.ir.IRCombatPartyCount
import io.github.gbkt.core.ir.IRCombatTargetConfirmed
import io.github.gbkt.core.ir.IRInitBattleFromEncounter
import io.github.gbkt.core.ir.IRInitBattleWithMonsters
import io.github.gbkt.core.ir.IRInitPartyFromClass
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// BATTLE STATE DEFINITIONS
// =============================================================================

/**
 * Battle state definition for turn-based combat.
 *
 * The framework provides 19 built-in states that follow a typical JRPG battle flow. Custom states
 * can be created via the DSL for games with different battle mechanics.
 *
 * Built-in states: INIT, INTRO, TURN_START, CHECK_COMBATANTS, PLAYER_MENU, TARGET_SELECT,
 * ABILITY_SELECT, ITEM_SELECT, PLAYER_CONFIRM, ENEMY_THINK, ENEMY_DECIDE, ACTION_EXECUTE,
 * SHOW_RESULT, APPLY_RESULT, TURN_END, NEXT_TURN, VICTORY, DEFEAT, FLED
 *
 * Custom states can be defined via:
 * ```kotlin
 * val cutsceneState by battleState("Cutscene")
 * val specialAnimState by battleState("Special Animation")
 * ```
 */
data class BattleState(
    /** Unique numeric ID for this state (used in code generation) */
    val id: Int,
    /** Display name for the state */
    val name: String,
    /** Whether this is a built-in state or custom */
    val isBuiltIn: Boolean = false,
) {
    companion object {
        // --- Initialization ---
        /** Battle is starting, initialize variables */
        val INIT = BattleState(0, "INIT", isBuiltIn = true)

        /** Battle intro animation/transition */
        val INTRO = BattleState(1, "INTRO", isBuiltIn = true)

        // --- Turn Start ---
        /** Beginning of a new turn, process status effects */
        val TURN_START = BattleState(2, "TURN_START", isBuiltIn = true)

        /** Check if any combatant is dead/incapacitated */
        val CHECK_COMBATANTS = BattleState(3, "CHECK_COMBATANTS", isBuiltIn = true)

        // --- Player Phase ---
        /** Waiting for player to select action from main menu */
        val PLAYER_MENU = BattleState(4, "PLAYER_MENU", isBuiltIn = true)

        /** Player is selecting a target for their action */
        val TARGET_SELECT = BattleState(5, "TARGET_SELECT", isBuiltIn = true)

        /** Player is selecting an ability from submenu */
        val ABILITY_SELECT = BattleState(6, "ABILITY_SELECT", isBuiltIn = true)

        /** Player is selecting an item from inventory */
        val ITEM_SELECT = BattleState(7, "ITEM_SELECT", isBuiltIn = true)

        /** Player confirmed action, preparing execution */
        val PLAYER_CONFIRM = BattleState(8, "PLAYER_CONFIRM", isBuiltIn = true)

        // --- Enemy Phase ---
        /** Enemy AI is deciding its action */
        val ENEMY_THINK = BattleState(9, "ENEMY_THINK", isBuiltIn = true)

        /** Enemy has selected its action */
        val ENEMY_DECIDE = BattleState(10, "ENEMY_DECIDE", isBuiltIn = true)

        // --- Action Execution ---
        /** Executing the current action (animation) */
        val ACTION_EXECUTE = BattleState(11, "ACTION_EXECUTE", isBuiltIn = true)

        /** Showing damage/heal numbers */
        val SHOW_RESULT = BattleState(12, "SHOW_RESULT", isBuiltIn = true)

        /** Applying damage and effects */
        val APPLY_RESULT = BattleState(13, "APPLY_RESULT", isBuiltIn = true)

        // --- Turn End ---
        /** End of turn processing (status ticks, etc.) */
        val TURN_END = BattleState(14, "TURN_END", isBuiltIn = true)

        /** Advance to next combatant */
        val NEXT_TURN = BattleState(15, "NEXT_TURN", isBuiltIn = true)

        // --- Battle End ---
        /** Player won the battle */
        val VICTORY = BattleState(16, "VICTORY", isBuiltIn = true)

        /** Player lost the battle */
        val DEFEAT = BattleState(17, "DEFEAT", isBuiltIn = true)

        /** Player successfully fled */
        val FLED = BattleState(18, "FLED", isBuiltIn = true)

        /** All built-in battle states in order */
        val BUILT_IN_STATES =
            listOf(
                INIT,
                INTRO,
                TURN_START,
                CHECK_COMBATANTS,
                PLAYER_MENU,
                TARGET_SELECT,
                ABILITY_SELECT,
                ITEM_SELECT,
                PLAYER_CONFIRM,
                ENEMY_THINK,
                ENEMY_DECIDE,
                ACTION_EXECUTE,
                SHOW_RESULT,
                APPLY_RESULT,
                TURN_END,
                NEXT_TURN,
                VICTORY,
                DEFEAT,
                FLED,
            )

        /** ID counter for custom states (starts after built-in states) */
        private var nextCustomId = 19

        /**
         * Create a custom battle state.
         *
         * Custom states are assigned IDs starting from 19 (after the built-in states).
         */
        internal fun createCustom(name: String): BattleState {
            return BattleState(nextCustomId++, name, isBuiltIn = false)
        }

        /** Reset the custom ID counter. For testing only. */
        internal fun resetCustomIdCounter() {
            nextCustomId = 19
        }
    }
}

// =============================================================================
// BATTLE ACTIONS
// =============================================================================

/** Types of actions that can be taken in battle. */
enum class BattleActionType {
    /** Basic physical attack */
    ATTACK,

    /** Use an ability/skill */
    ABILITY,

    /** Use an item */
    ITEM,

    /** Defend (reduce damage taken) */
    DEFEND,

    /** Attempt to flee from battle */
    FLEE,

    /** Wait/skip turn */
    WAIT,
}

/** Targeting modes for battle actions. */
enum class TargetingMode {
    /** Target a single enemy */
    SINGLE_ENEMY,

    /** Target all enemies */
    ALL_ENEMIES,

    /** Target a single ally */
    SINGLE_ALLY,

    /** Target all allies */
    ALL_ALLIES,

    /** Target self only */
    SELF,

    /** No target needed */
    NONE,
}

/** Represents a queued battle action. */
data class BattleAction(
    val type: BattleActionType,
    val actorName: String,
    val targetNames: List<String> = emptyList(),
    val abilityId: Int? = null,
    val itemId: Int? = null,
)

// =============================================================================
// BATTLE LAYOUT
// =============================================================================

/** Layout configuration for enemy positions in battle. */
enum class BattleLayout {
    /** Single large enemy (boss) */
    SINGLE_LARGE,

    /** Two medium enemies */
    TWO_MEDIUM,

    /** Three small enemies */
    THREE_SMALL,

    /** One large + one small */
    ONE_LARGE_ONE_SMALL,

    /** Two small enemies */
    TWO_SMALL,

    /** Four small enemies (max) */
    FOUR_SMALL,
}

// =============================================================================
// BATTLE SYSTEM DEFINITION
// =============================================================================

/**
 * Complete battle system definition.
 *
 * Contains all configuration for turn-based combat including:
 * - Party and enemy slots
 * - State callbacks
 * - Victory/defeat handlers
 * - Flee mechanics
 */
class BattleSystem
internal constructor(
    val name: String,
    val maxPartySize: Int,
    val maxEnemies: Int,
    internal val stateCallbacks: Map<BattleState, List<IRStatement>>,
    internal val onVictory: List<IRStatement>,
    internal val onDefeat: List<IRStatement>,
    internal val onFlee: List<IRStatement>,
    internal val fleeChanceBase: Int,
    internal val fleeChancePerAgility: Int,
    /** Presentation configuration for visual feedback */
    val presentation: BattlePresentationConfig = BattlePresentationConfig(),
)

/** Builder for battle system configuration. */
@GbktDsl
@Suppress("TooManyFunctions") // DSL builder requires many configuration methods
class BattleSystemBuilder internal constructor(private val name: String) {
    private var maxPartySize: Int = 4
    private var maxEnemies: Int = 4
    private val stateCallbacks = mutableMapOf<BattleState, () -> Unit>()
    private var onVictory: (() -> Unit)? = null
    private var onDefeat: (() -> Unit)? = null
    private var onFlee: (() -> Unit)? = null
    private var fleeChanceBase: Int = 50
    private var fleeChancePerAgility: Int = 2
    private var presentationConfig: BattlePresentationConfig = BattlePresentationConfig()

    /** Set maximum party size (1-4, default 4) */
    fun maxPartySize(size: Int) {
        require(size in 1..4) { "Party size must be 1-4" }
        maxPartySize = size
    }

    /** Set maximum enemy count (1-4, default 4) */
    fun maxEnemies(count: Int) {
        require(count in 1..4) { "Enemy count must be 1-4" }
        maxEnemies = count
    }

    /** Configure flee mechanics */
    fun fleeMechanics(baseChance: Int = 50, perAgility: Int = 2) {
        fleeChanceBase = baseChance
        fleeChancePerAgility = perAgility
    }

    /**
     * Configure battle presentation (visual feedback).
     *
     * Controls damage numbers, screen shake, messages, and animations.
     *
     * Usage:
     * ```kotlin
     * battleSystem("combat") {
     *     presentation {
     *         damageNumbers(true)
     *         screenShakeOnHit(4, 8)
     *         flashOnCrit(4)
     *         actionMessages(true)
     *         critMessages(true)
     *         defeatMessages(true)
     *
     *         onAttack { /* play attack animation */ }
     *         onDamage { /* play hit sound */ }
     *         onDefeat { /* play death animation */ }
     *     }
     * }
     * ```
     */
    fun presentation(init: BattlePresentationBuilder.() -> Unit) {
        val builder = BattlePresentationBuilder()
        builder.init()
        presentationConfig = builder.build()
    }

    /** Define callback for a specific battle state */
    fun onState(state: BattleState, block: () -> Unit) {
        stateCallbacks[state] = block
    }

    /** Shorthand for common state callbacks */
    fun onInit(block: () -> Unit) = onState(BattleState.INIT, block)

    fun onIntro(block: () -> Unit) = onState(BattleState.INTRO, block)

    fun onTurnStart(block: () -> Unit) = onState(BattleState.TURN_START, block)

    fun onPlayerMenu(block: () -> Unit) = onState(BattleState.PLAYER_MENU, block)

    fun onTargetSelect(block: () -> Unit) = onState(BattleState.TARGET_SELECT, block)

    fun onAbilitySelect(block: () -> Unit) = onState(BattleState.ABILITY_SELECT, block)

    fun onItemSelect(block: () -> Unit) = onState(BattleState.ITEM_SELECT, block)

    fun onEnemyThink(block: () -> Unit) = onState(BattleState.ENEMY_THINK, block)

    fun onActionExecute(block: () -> Unit) = onState(BattleState.ACTION_EXECUTE, block)

    fun onShowResult(block: () -> Unit) = onState(BattleState.SHOW_RESULT, block)

    fun onApplyResult(block: () -> Unit) = onState(BattleState.APPLY_RESULT, block)

    fun onTurnEnd(block: () -> Unit) = onState(BattleState.TURN_END, block)

    /** Define victory handler */
    fun onVictory(block: () -> Unit) {
        onVictory = block
    }

    /** Define defeat handler */
    fun onDefeat(block: () -> Unit) {
        onDefeat = block
    }

    /** Define flee success handler */
    fun onFlee(block: () -> Unit) {
        onFlee = block
    }

    private fun recordBlock(block: () -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { block() }
        return recorder.statements
    }

    internal fun build(): BattleSystem {
        val recordedCallbacks = stateCallbacks.mapValues { (_, block) -> recordBlock(block) }

        return BattleSystem(
            name = name,
            maxPartySize = maxPartySize,
            maxEnemies = maxEnemies,
            stateCallbacks = recordedCallbacks,
            onVictory = onVictory?.let { recordBlock(it) } ?: emptyList(),
            onDefeat = onDefeat?.let { recordBlock(it) } ?: emptyList(),
            onFlee = onFlee?.let { recordBlock(it) } ?: emptyList(),
            fleeChanceBase = fleeChanceBase,
            fleeChancePerAgility = fleeChancePerAgility,
            presentation = presentationConfig,
        )
    }
}

/**
 * Create a battle system configuration.
 *
 * Usage:
 * ```kotlin
 * val battleSystem = battleSystem("main") {
 *     maxPartySize(4)
 *     maxEnemies(4)
 *
 *     onInit { /* setup battle */ }
 *     onPlayerMenu { /* show menu */ }
 *     onVictory { /* award exp */ }
 *     onDefeat { /* game over */ }
 * }
 * ```
 */
fun battleSystem(name: String, block: BattleSystemBuilder.() -> Unit): BattleSystem {
    val builder = BattleSystemBuilder(name)
    builder.block()
    return builder.build()
}

// =============================================================================
// BATTLE RUNTIME OPERATIONS
// =============================================================================

/**
 * Start a battle with the configured system.
 *
 * @param system The battle system configuration
 * @param enemies List of enemy characters to fight
 */
fun startBattle(system: BattleSystem, enemies: List<Character>) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(IRBattleStart(systemName = system.name, enemyNames = enemies.map { it.name }))
    }
}

/** Start a battle with a single enemy. */
fun startBattle(system: BattleSystem, enemy: Character) {
    startBattle(system, listOf(enemy))
}

/**
 * Start a battle with specific monsters.
 *
 * Use this for boss encounters or scripted battles where you want to fight specific monsters rather
 * than using the random encounter system.
 *
 * @param system The battle system configuration
 * @param monsters Monsters to fight
 */
fun startBattleWithMonsters(system: BattleSystem, vararg monsters: Monster) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(IRBattleStart(systemName = system.name, enemyNames = monsters.map { it.id }))
    }
}

/** Start a battle with a single monster. */
fun startBattleWithMonster(system: BattleSystem, monster: Monster) {
    startBattleWithMonsters(system, monster)
}

/**
 * End the current battle.
 *
 * @param result The battle outcome
 * @param systemName Name of the battle system (defaults to "battle")
 */
fun endBattle(result: BattleState, systemName: String = "battle") {
    require(result in listOf(BattleState.VICTORY, BattleState.DEFEAT, BattleState.FLED)) {
        "Battle can only end with VICTORY, DEFEAT, or FLED"
    }
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRBattleEnd(systemName = systemName, result = result))
    }
}

/** End the battle using a BattleSystem reference. */
fun endBattle(system: BattleSystem, result: BattleState) {
    endBattle(result, system.name)
}

/** Transition to a specific battle state. */
fun battleTransition(targetState: BattleState, systemName: String = "battle") {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(IRBattleStateTransition(systemName = systemName, targetState = targetState))
    }
}

/** Transition to a specific battle state using a BattleSystem reference. */
fun battleTransition(system: BattleSystem, targetState: BattleState) {
    battleTransition(targetState, system.name)
}

/** Queue a battle action for execution. */
fun queueAction(action: BattleAction) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRBattleAction(
                    actionType = action.type,
                    actorName = action.actorName,
                    targetNames = action.targetNames,
                    abilityId = action.abilityId,
                    itemId = action.itemId,
                )
            )
    }
}

/** Queue an attack action. */
fun queueAttack(actor: Character, target: Character) {
    queueAction(
        BattleAction(
            type = BattleActionType.ATTACK,
            actorName = actor.name,
            targetNames = listOf(target.name),
        )
    )
}

/** Queue an ability action. */
fun queueAbility(actor: Character, abilityId: Int, targets: List<Character>) {
    queueAction(
        BattleAction(
            type = BattleActionType.ABILITY,
            actorName = actor.name,
            targetNames = targets.map { it.name },
            abilityId = abilityId,
        )
    )
}

/** Queue an item use action. */
fun queueItem(actor: Character, itemId: Int, target: Character) {
    queueAction(
        BattleAction(
            type = BattleActionType.ITEM,
            actorName = actor.name,
            targetNames = listOf(target.name),
            itemId = itemId,
        )
    )
}

/** Queue a defend action. */
fun queueDefend(actor: Character) {
    queueAction(BattleAction(type = BattleActionType.DEFEND, actorName = actor.name))
}

/** Queue a flee attempt. */
fun queueFlee(actor: Character) {
    queueAction(BattleAction(type = BattleActionType.FLEE, actorName = actor.name))
}

/**
 * Register the battle system for code generation.
 *
 * Battle systems must be registered at game scope (inside `gbGame { }` but outside scene lifecycle
 * blocks) to ensure their definitions are generated at file scope in the C output.
 */
fun registerBattleSystem(system: BattleSystem) {
    val gameBuilder =
        GameScopeContext.current as? GameBuilder
            ?: error(
                "registerBattleSystem must be called inside a game builder context " +
                    "(inside gbGame { } but outside scene enter/frame/exit blocks)"
            )
    gameBuilder.registerBattleSystem(system)
}

// =============================================================================
// CUSTOM BATTLE STATE DSL
// =============================================================================

/**
 * Property delegate for custom battle state registration.
 *
 * Used with the `battleState()` DSL function to define custom battle states:
 * ```kotlin
 * val cutsceneState by battleState("Cutscene")
 * val specialAnimState by battleState("Special Animation")
 * ```
 *
 * Custom states are registered with the game builder and assigned unique IDs starting from 19
 * (after the 19 built-in states).
 */
class BattleStateDelegate(
    private val gameBuilder: io.github.gbkt.core.builder.GameBuilder,
    private val displayName: String,
) :
    kotlin.properties.PropertyDelegateProvider<
        Any?,
        kotlin.properties.ReadOnlyProperty<Any?, BattleState>,
    > {

    override fun provideDelegate(
        thisRef: Any?,
        property: kotlin.reflect.KProperty<*>,
    ): kotlin.properties.ReadOnlyProperty<Any?, BattleState> {
        val state = BattleState.createCustom(displayName)
        gameBuilder.registerBattleState(state)
        return kotlin.properties.ReadOnlyProperty { _, _ -> state }
    }
}

/**
 * Define a custom battle state.
 *
 * Custom states can be used for game-specific battle mechanics like:
 * - Cutscene states between battle phases
 * - Special animation states
 * - Game-specific phases (e.g., summon phases, combo phases)
 *
 * Usage:
 * ```kotlin
 * val cutsceneState by battleState("Cutscene")
 * val summonPhase by battleState("Summon Phase")
 *
 * val battle = battleSystem("main") {
 *     onState(cutsceneState) {
 *         // Handle cutscene during battle
 *     }
 * }
 * ```
 *
 * @param displayName The display name for this state
 * @return A property delegate that registers and provides the battle state
 */
fun io.github.gbkt.core.builder.GameBuilder.battleState(displayName: String): BattleStateDelegate {
    return BattleStateDelegate(this, displayName)
}

// =============================================================================
// BATTLE INITIALIZATION DSL FUNCTIONS
// =============================================================================

/**
 * Initialize the party from a character class selection.
 *
 * This function should be called at the start of a battle to populate the party member stats based
 * on the selected character class.
 *
 * Usage:
 * ```kotlin
 * enter {
 *     initPartyFromClass(selectedClass)
 *     initBattleFromEncounter()
 * }
 * ```
 *
 * @param classId The character class ID. Mapping to actual classes is game-specific.
 */
fun initPartyFromClass(classId: AssignableExpr) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRInitPartyFromClass(classId.ir))
    }
}

/**
 * Initialize battle from a pending encounter.
 *
 * This function should be called at the start of a battle to populate the enemy slots from the
 * encounter data set by the exploration system.
 *
 * Usage:
 * ```kotlin
 * enter {
 *     initPartyFromClass(selectedClass)
 *     initBattleFromEncounter()
 * }
 * ```
 */
fun initBattleFromEncounter() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRInitBattleFromEncounter())
    }
}

/**
 * Initialize battle with specific monsters.
 *
 * Use this for boss encounters or scripted battles where you want to fight specific monsters rather
 * than using the random encounter system.
 *
 * Usage:
 * ```kotlin
 * // In an NPC interaction or trigger callback
 * onInteract {
 *     showMessage("The Dragon attacks!")
 *     initBattleWithMonsters(dragon)
 *     scene(battleScene)
 * }
 * ```
 *
 * @param monsters The monsters to fight
 */
fun initBattleWithMonsters(vararg monsters: Monster) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRInitBattleWithMonsters(monsters.map { it.id }))
    }
}

/**
 * Initialize battle with a list of monsters.
 *
 * @param monsters The list of monsters to fight
 */
fun initBattleWithMonsters(monsters: List<Monster>) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRInitBattleWithMonsters(monsters.map { it.id }))
    }
}

// =============================================================================
// COMBAT ACTION DSL FUNCTIONS
// =============================================================================

/**
 * Confirm target selection in combat.
 *
 * Called when the player confirms their target selection for an attack or ability. The target index
 * is typically the enemy position offset by the party count.
 *
 * Usage:
 * ```kotlin
 * whenever(buttons.a.pressed) {
 *     confirmCombatTarget(partyCount + battleTargetCursor)
 * }
 * ```
 *
 * @param targetIndex The index of the selected target in the combatant array
 */
fun confirmCombatTarget(targetIndex: AssignableExpr) {
    emitTargetConfirmed(targetIndex.ir)
}

/**
 * Select an item for use in combat.
 *
 * Called when the player selects an item from the inventory during battle.
 *
 * Usage:
 * ```kotlin
 * whenever(buttons.a.pressed) {
 *     selectCombatItem(battleItemCursor)
 * }
 * ```
 *
 * @param itemIndex The index of the selected item
 */
fun selectCombatItem(itemIndex: AssignableExpr) {
    emitItemSelected(itemIndex.ir)
}

/**
 * Transition to a combat state.
 *
 * Changes the combat state machine to the specified state.
 *
 * Usage:
 * ```kotlin
 * // After player action, transition to enemy phase
 * transitionToCombatState("COMBAT_STATE_ENEMY_THINK")
 * ```
 *
 * @param stateName The name of the target state constant
 */
fun transitionToCombatState(stateName: String) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRCombatBattleTransition(stateName))
    }
}

// =============================================================================
// COMBAT QUERY EXPRESSIONS
// =============================================================================

/**
 * Expression for the current party count in combat.
 *
 * This can be used to calculate target indices where enemies follow party members in the combatant
 * array.
 *
 * Usage:
 * ```kotlin
 * // Select enemy at index relative to party
 * confirmCombatTarget(combatPartyCount + battleTargetCursor)
 * ```
 */
val combatPartyCount: Expr
    get() = Expr(IRCombatPartyCount)

/**
 * Condition for checking if combat is in a specific state.
 *
 * Usage:
 * ```kotlin
 * whenever(combatIsInState("COMBAT_STATE_VICTORY")) {
 *     // Battle won!
 * }
 * ```
 *
 * @param stateName The combat state constant name (e.g., "COMBAT_STATE_VICTORY")
 * @return A Condition that evaluates to true when combat is in the specified state
 */
fun combatIsInState(stateName: String): Condition = Condition(IRCombatIsState(stateName))

// =============================================================================
// EXPRESSION-BASED OVERLOADS
// =============================================================================

/**
 * Confirm target selection in combat using an expression.
 *
 * Overload that accepts an expression, useful for computed target indices.
 *
 * Usage:
 * ```kotlin
 * // Target is party count + cursor position
 * confirmCombatTarget(combatPartyCount + battleTargetCursor)
 * ```
 *
 * @param targetIndex Expression evaluating to the target index
 */
fun confirmCombatTarget(targetIndex: Expr) {
    emitTargetConfirmed(targetIndex.ir)
}

/**
 * Select an item for use in combat using an expression.
 *
 * Overload that accepts an expression for computed item indices.
 *
 * Usage:
 * ```kotlin
 * selectCombatItem(itemSlot)
 * ```
 *
 * @param itemIndex Expression evaluating to the item index
 */
fun selectCombatItem(itemIndex: Expr) {
    emitItemSelected(itemIndex.ir)
}

// =============================================================================
// PRIVATE EMISSION HELPERS
// =============================================================================

private fun emitTargetConfirmed(targetIndex: io.github.gbkt.core.ir.IRExpression) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRCombatTargetConfirmed(targetIndex))
    }
}

private fun emitItemSelected(itemIndex: io.github.gbkt.core.ir.IRExpression) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRCombatItemSelected(itemIndex))
    }
}

// =============================================================================
// BATTLE UPDATE DSL FUNCTION
// =============================================================================

/**
 * Update the battle state machine.
 *
 * This function should be called every frame during battle to drive the combat state machine. It
 * processes state transitions, AI decisions, action execution, and victory/defeat checks.
 *
 * Usage:
 * ```kotlin
 * scene("battle") {
 *     every.frame {
 *         // Update battle state machine each frame
 *         battleUpdate(combatSystem)
 *
 *         // UI logic can check current state
 *         whenever(combatIsInState("COMBAT_STATE_PLAYER_MENU")) {
 *             // Show player menu
 *         }
 *     }
 * }
 * ```
 *
 * @param system The battle system to update
 */
fun battleUpdate(system: BattleSystem) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRBattleUpdate(systemName = system.name))
    }
}

/**
 * Update the battle state machine by system name.
 *
 * Overload that accepts a system name directly, useful when the BattleSystem object isn't directly
 * available.
 *
 * @param systemName Name of the battle system (default: "battle")
 */
fun battleUpdate(systemName: String = "battle") {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRBattleUpdate(systemName = systemName))
    }
}
