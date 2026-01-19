/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// QUEST TRACKING FRAMEWORK
// =============================================================================

/** Quest state for tracking progress. */
enum class QuestState {
    /** Quest not yet started/discovered */
    NOT_STARTED,

    /** Quest is active and in progress */
    IN_PROGRESS,

    /** Quest completed successfully */
    COMPLETED,

    /** Quest failed (if applicable) */
    FAILED,

    /** Quest abandoned by player */
    ABANDONED,
}

/** Quest type for different kinds of quests. */
enum class QuestType {
    /** Main story quest */
    MAIN,

    /** Side quest */
    SIDE,

    /** Repeatable quest */
    REPEATABLE,

    /** Daily/timed quest */
    DAILY,

    /** Hidden/secret quest */
    HIDDEN,

    /** Tutorial quest */
    TUTORIAL,
}

/** Objective type for different completion conditions. */
enum class ObjectiveType {
    /** Defeat a number of specific enemies */
    KILL,

    /** Collect items */
    COLLECT,

    /** Talk to an NPC */
    TALK,

    /** Reach a location */
    REACH,

    /** Deliver items to someone */
    DELIVER,

    /** Escort/protect someone */
    ESCORT,

    /** Complete within time limit */
    TIMED,

    /** Use an ability or item */
    USE,

    /** Custom condition */
    CUSTOM,
}

/** A quest objective that must be completed. */
data class QuestObjective(
    /** Unique objective ID within the quest */
    val id: String,
    /** Description shown to player */
    val description: String,
    /** Objective type */
    val type: ObjectiveType,
    /** Target ID (monster ID, item ID, NPC ID, zone ID, etc.) */
    val targetId: String?,
    /** Required count (monsters to kill, items to collect, etc.) */
    val requiredCount: Int,
    /** Whether this objective is optional */
    val optional: Boolean,
    /** Whether this objective is hidden until revealed */
    val hidden: Boolean,
    /** Order index for sequential objectives (0 = parallel) */
    val sequenceOrder: Int,
    /** Callback when objective progress is made */
    val onProgressStatements: List<IRStatement>,
    /** Callback when objective is completed */
    val onCompleteStatements: List<IRStatement>,
)

/** Quest reward definition. */
sealed class QuestReward {
    /** Experience points */
    data class Experience(val amount: Int) : QuestReward()

    /** Gold/currency */
    data class Gold(val amount: Int) : QuestReward()

    /** Item reward */
    data class Item(val itemId: String, val quantity: Int = 1) : QuestReward()

    /** Ability unlock */
    data class Ability(val abilityId: String) : QuestReward()

    /** Stat increase */
    data class StatBonus(val statId: String, val amount: Int) : QuestReward()

    /** Flag set (for unlocking content) */
    data class Flag(val flagId: String) : QuestReward()

    /** Custom reward */
    data class Custom(val rewardId: String, val description: String) : QuestReward()
}

/**
 * Quest definition.
 *
 * Represents a trackable quest with objectives, rewards, and callbacks.
 *
 * Usage:
 * ```kotlin
 * val mainQuest by quest {
 *     name("Defeat the Dragon")
 *     description("The village is threatened by a fearsome dragon.")
 *     type(QuestType.MAIN)
 *
 *     objective("find_sword") {
 *         description("Find the legendary sword")
 *         type(ObjectiveType.COLLECT)
 *         target("legendary_sword")
 *         count(1)
 *     }
 *
 *     objective("defeat_dragon") {
 *         description("Defeat the dragon")
 *         type(ObjectiveType.KILL)
 *         target("dragon_boss")
 *         count(1)
 *         sequenceAfter("find_sword")
 *     }
 *
 *     reward { exp(1000) }
 *     reward { gold(500) }
 *     reward { item("dragon_scale", 1) }
 *
 *     onStart { showMessage("Quest started!") }
 *     onComplete { showMessage("Quest complete!"); unlockEnding() }
 * }
 * ```
 */
data class Quest(
    /** Unique quest identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Description shown in quest log */
    val description: String,
    /** Quest type */
    val questType: QuestType,
    /** Quest objectives */
    val objectives: List<QuestObjective>,
    /** Rewards for completion */
    val rewards: List<QuestReward>,
    /** Prerequisites (other quest IDs that must be completed) */
    val prerequisites: List<String>,
    /** Level requirement */
    val levelRequirement: Int,
    /** Whether quest auto-starts when prerequisites met */
    val autoStart: Boolean,
    /** Whether quest can be abandoned */
    val canAbandon: Boolean,
    /** Whether quest is repeatable */
    val repeatable: Boolean,
    /** Time limit in frames (0 = no limit) */
    val timeLimit: Int,
    /** Callback when quest starts */
    val onStartStatements: List<IRStatement>,
    /** Callback when quest completes */
    val onCompleteStatements: List<IRStatement>,
    /** Callback when quest fails */
    val onFailStatements: List<IRStatement>,
    /** System index for code generation */
    var questIndex: Int = -1,
) {
    /** Number of required (non-optional) objectives */
    val requiredObjectiveCount: Int
        get() = objectives.count { !it.optional }

    /** Check if objectives have a sequence order */
    val hasSequentialObjectives: Boolean
        get() = objectives.any { it.sequenceOrder > 0 }
}

// =============================================================================
// QUEST BUILDER
// =============================================================================

/** Property delegate for quests. */
class QuestDelegate(
    private val gameBuilder: GameBuilder,
    private val init: QuestBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Quest>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Quest> {
        val builder = QuestBuilder(property.name)
        builder.init()
        val quest = builder.build()
        gameBuilder.registerQuest(quest)

        return ReadOnlyProperty { _, _ -> quest }
    }
}

/** Builder for quests. */
@GbktDsl
class QuestBuilder(private val questId: String) {
    private var displayName: String = questId.replaceFirstChar { it.uppercaseChar() }
    private var description: String = ""
    private var questType: QuestType = QuestType.SIDE
    private val objectiveBuilders = mutableListOf<ObjectiveBuilder>()
    private val rewards = mutableListOf<QuestReward>()
    private val prerequisites = mutableListOf<String>()
    private var levelRequirement: Int = 0
    private var autoStart: Boolean = false
    private var canAbandon: Boolean = true
    private var repeatable: Boolean = false
    private var timeLimit: Int = 0
    private var onStartStatements: List<IRStatement> = emptyList()
    private var onCompleteStatements: List<IRStatement> = emptyList()
    private var onFailStatements: List<IRStatement> = emptyList()

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set description */
    fun description(desc: String) {
        description = desc
    }

    /** Set quest type */
    fun type(type: QuestType) {
        questType = type
    }

    /** Add an objective */
    fun objective(id: String, init: ObjectiveBuilder.() -> Unit) {
        val builder = ObjectiveBuilder(id)
        builder.init()
        objectiveBuilders.add(builder)
    }

    /** Add a reward */
    fun reward(init: RewardBuilder.() -> Unit) {
        val builder = RewardBuilder()
        builder.init()
        builder.build()?.let { rewards.add(it) }
    }

    /** Add prerequisite quest */
    fun requires(questId: String) {
        prerequisites.add(questId)
    }

    /** Add multiple prerequisite quests */
    fun requires(vararg questIds: String) {
        prerequisites.addAll(questIds)
    }

    /** Set level requirement */
    fun levelRequired(level: Int) {
        levelRequirement = level
    }

    /** Enable auto-start when prerequisites met */
    fun autoStart(enabled: Boolean = true) {
        autoStart = enabled
    }

    /** Disable abandoning */
    fun cannotAbandon() {
        canAbandon = false
    }

    /** Make quest repeatable */
    fun repeatable(enabled: Boolean = true) {
        repeatable = enabled
    }

    /** Set time limit in frames */
    fun timeLimit(frames: Int) {
        timeLimit = frames
    }

    /** Callback when quest starts */
    fun onStart(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStartStatements = recorder.statements
    }

    /** Callback when quest completes */
    fun onComplete(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onCompleteStatements = recorder.statements
    }

    /** Callback when quest fails */
    fun onFail(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onFailStatements = recorder.statements
    }

    /**
     * Resolve sequence orders for objectives with sequenceAfter() dependencies.
     *
     * Uses topological sort to compute proper order values:
     * - Objectives with no dependencies get order 0
     * - Objectives that depend on another get (dependency's order + 1)
     */
    private fun resolveSequenceOrders(): Map<String, Int> {
        val resolvedOrders = mutableMapOf<String, Int>()
        val idToBuilder = objectiveBuilders.associateBy { it.getId() }

        // Process objectives until all are resolved
        val remaining = objectiveBuilders.toMutableList()
        var maxIterations = objectiveBuilders.size * 2 // Prevent infinite loops

        while (remaining.isNotEmpty() && maxIterations > 0) {
            maxIterations--
            val iterator = remaining.iterator()

            while (iterator.hasNext()) {
                val builder = iterator.next()
                val dependsOn = builder.getSequenceAfter()

                if (dependsOn == null) {
                    // No dependency - use explicit order or 0
                    resolvedOrders[builder.getId()] = builder.getExplicitSequenceOrder()
                    iterator.remove()
                } else if (resolvedOrders.containsKey(dependsOn)) {
                    // Dependency resolved - this comes after
                    resolvedOrders[builder.getId()] = resolvedOrders[dependsOn]!! + 1
                    iterator.remove()
                } else if (!idToBuilder.containsKey(dependsOn)) {
                    // Invalid dependency - treat as no dependency
                    resolvedOrders[builder.getId()] = builder.getExplicitSequenceOrder()
                    iterator.remove()
                }
                // Otherwise, dependency not yet resolved - try again next iteration
            }
        }

        // If any remain (circular dependency), assign order 0
        for (builder in remaining) {
            resolvedOrders[builder.getId()] = 0
        }

        return resolvedOrders
    }

    internal fun build(): Quest {
        require(objectiveBuilders.isNotEmpty()) {
            "Quest '$questId' must have at least one objective"
        }

        // Resolve sequence orders
        val sequenceOrders = resolveSequenceOrders()

        // Build objectives with resolved orders
        val objectives =
            objectiveBuilders.map { builder -> builder.build(sequenceOrders[builder.getId()] ?: 0) }

        return Quest(
            id = questId,
            displayName = displayName,
            description = description,
            questType = questType,
            objectives = objectives,
            rewards = rewards.toList(),
            prerequisites = prerequisites.toList(),
            levelRequirement = levelRequirement,
            autoStart = autoStart,
            canAbandon = canAbandon,
            repeatable = repeatable,
            timeLimit = timeLimit,
            onStartStatements = onStartStatements,
            onCompleteStatements = onCompleteStatements,
            onFailStatements = onFailStatements,
        )
    }
}

/** Builder for quest objectives. */
@GbktDsl
class ObjectiveBuilder(private val objectiveId: String) {
    private var description: String = ""
    private var type: ObjectiveType = ObjectiveType.CUSTOM
    private var targetId: String? = null
    private var requiredCount: Int = 1
    private var optional: Boolean = false
    private var hidden: Boolean = false
    private var sequenceOrder: Int = 0
    private var sequenceAfterObjectiveId: String? = null
    private var onProgressStatements: List<IRStatement> = emptyList()
    private var onCompleteStatements: List<IRStatement> = emptyList()

    /** Set description */
    fun description(desc: String) {
        description = desc
    }

    /** Set objective type */
    fun type(objectiveType: ObjectiveType) {
        type = objectiveType
    }

    /** Set target ID (monster, item, NPC, zone) */
    fun target(id: String) {
        targetId = id
    }

    /** Set required count */
    fun count(amount: Int) {
        requiredCount = amount
    }

    /** Mark as optional */
    fun optional(isOptional: Boolean = true) {
        optional = isOptional
    }

    /** Mark as hidden until revealed */
    fun hidden(isHidden: Boolean = true) {
        hidden = isHidden
    }

    /** Set sequence order (objectives with lower order must complete first) */
    fun sequenceOrder(order: Int) {
        sequenceOrder = order
    }

    /**
     * Make this objective sequential after another objective. The objective with
     * [previousObjectiveId] must be completed before this one can progress.
     */
    fun sequenceAfter(previousObjectiveId: String) {
        sequenceAfterObjectiveId = previousObjectiveId
    }

    /** Callback when progress is made */
    fun onProgress(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onProgressStatements = recorder.statements
    }

    /** Callback when completed */
    fun onComplete(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onCompleteStatements = recorder.statements
    }

    /** Get the objective ID */
    internal fun getId() = objectiveId

    /** Get the ID of the objective this one depends on (if any) */
    internal fun getSequenceAfter() = sequenceAfterObjectiveId

    /** Get the explicitly set sequence order */
    internal fun getExplicitSequenceOrder() = sequenceOrder

    internal fun build(resolvedSequenceOrder: Int) =
        QuestObjective(
            id = objectiveId,
            description = description,
            type = type,
            targetId = targetId,
            requiredCount = requiredCount,
            optional = optional,
            hidden = hidden,
            sequenceOrder = resolvedSequenceOrder,
            onProgressStatements = onProgressStatements,
            onCompleteStatements = onCompleteStatements,
        )
}

/** Builder for quest rewards. */
@GbktDsl
class RewardBuilder {
    private var reward: QuestReward? = null

    /** Experience reward */
    fun exp(amount: Int) {
        reward = QuestReward.Experience(amount)
    }

    /** Gold/currency reward */
    fun gold(amount: Int) {
        reward = QuestReward.Gold(amount)
    }

    /** Item reward */
    fun item(itemId: String, quantity: Int = 1) {
        reward = QuestReward.Item(itemId, quantity)
    }

    /** Ability unlock reward */
    fun ability(abilityId: String) {
        reward = QuestReward.Ability(abilityId)
    }

    /** Stat bonus reward */
    fun statBonus(statId: String, amount: Int) {
        reward = QuestReward.StatBonus(statId, amount)
    }

    /** Flag set reward */
    fun flag(flagId: String) {
        reward = QuestReward.Flag(flagId)
    }

    /** Custom reward */
    fun custom(rewardId: String, description: String) {
        reward = QuestReward.Custom(rewardId, description)
    }

    internal fun build(): QuestReward? = reward
}

// =============================================================================
// QUEST TRACKER
// =============================================================================

/**
 * Quest tracker configuration.
 *
 * Manages quest state and UI integration.
 */
data class QuestTracker(
    /** Maximum active quests at once */
    val maxActiveQuests: Int,
    /** Whether to show quest notifications */
    val showNotifications: Boolean,
    /** Whether to track objectives in HUD */
    val showHudTracker: Boolean,
    /** Maximum objectives shown in HUD */
    val maxHudObjectives: Int,
    /** System index for code generation */
    var trackerIndex: Int = -1,
)

/** Builder for quest tracker configuration. */
@GbktDsl
class QuestTrackerBuilder {
    private var maxActiveQuests: Int = 10
    private var showNotifications: Boolean = true
    private var showHudTracker: Boolean = true
    private var maxHudObjectives: Int = 3

    /** Set maximum active quests */
    fun maxActive(count: Int) {
        maxActiveQuests = count
    }

    /** Enable/disable notifications */
    fun notifications(enabled: Boolean) {
        showNotifications = enabled
    }

    /** Enable/disable HUD tracker */
    fun hudTracker(enabled: Boolean) {
        showHudTracker = enabled
    }

    /** Set max HUD objectives */
    fun maxHudObjectives(count: Int) {
        maxHudObjectives = count
    }

    internal fun build() =
        QuestTracker(
            maxActiveQuests = maxActiveQuests,
            showNotifications = showNotifications,
            showHudTracker = showHudTracker,
            maxHudObjectives = maxHudObjectives,
        )
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a quest.
 *
 * Quests are trackable objectives with rewards.
 *
 * Usage:
 * ```kotlin
 * val rescuePrincess by quest {
 *     name("Rescue the Princess")
 *     description("Save the princess from the tower")
 *     type(QuestType.MAIN)
 *
 *     objective("find_key") {
 *         description("Find the tower key")
 *         type(ObjectiveType.COLLECT)
 *         target("tower_key")
 *     }
 *
 *     objective("defeat_guard") {
 *         description("Defeat the tower guard")
 *         type(ObjectiveType.KILL)
 *         target("tower_guard")
 *         sequenceAfter("find_key")
 *     }
 *
 *     objective("rescue") {
 *         description("Rescue the princess")
 *         type(ObjectiveType.TALK)
 *         target("princess")
 *         sequenceAfter("defeat_guard")
 *     }
 *
 *     reward { exp(500) }
 *     reward { gold(1000) }
 *
 *     onComplete { unlockEnding("good") }
 * }
 * ```
 */
fun GameBuilder.quest(init: QuestBuilder.() -> Unit): QuestDelegate {
    return QuestDelegate(this, init)
}

/**
 * Configure the quest tracker.
 *
 * Usage:
 * ```kotlin
 * questTracker {
 *     maxActive(5)
 *     notifications(true)
 *     hudTracker(true)
 *     maxHudObjectives(3)
 * }
 * ```
 */
fun GameBuilder.questTracker(init: QuestTrackerBuilder.() -> Unit): QuestTracker {
    val builder = QuestTrackerBuilder()
    builder.init()
    val tracker = builder.build()
    registerQuestTracker(tracker)
    return tracker
}
