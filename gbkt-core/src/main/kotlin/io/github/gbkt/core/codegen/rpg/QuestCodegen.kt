/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.rpg.ObjectiveType
import io.github.gbkt.core.rpg.QuestReward
import io.github.gbkt.core.rpg.QuestState

// =============================================================================
// QUEST CODE GENERATION
// =============================================================================

/**
 * Generate quest tracking system code.
 *
 * Creates:
 * - Quest state constants and enums
 * - Quest state variables (per quest)
 * - Objective progress tracking (per objective)
 * - Quest management functions (start, progress, complete)
 * - Reward granting functions
 */
internal fun CodeGenerator.generateQuestSystem() {
    if (game.quests.isEmpty()) return

    line("// =============================================================================")
    line("// QUEST SYSTEM")
    line("// =============================================================================")
    line()

    // Generate constants
    generateQuestConstants()

    // Generate state and objective tracking variables
    generateQuestVariables()

    // Generate objective type checking
    generateObjectiveHelpers()

    // Generate quest management functions
    generateQuestFunctions()

    // Generate reward granting
    generateRewardFunctions()

    // Generate tracker display helpers if tracker is configured
    if (game.questTracker != null) {
        generateTrackerHelpers()
    }
}

/** Generate quest-related constants. */
private fun CodeGenerator.generateQuestConstants() {
    line("// Quest state constants")
    QuestState.entries.forEachIndexed { index, state ->
        line("#define QUEST_STATE_${state.name} ${index}u")
    }
    line()

    line("// Objective type constants")
    ObjectiveType.entries.forEachIndexed { index, type ->
        line("#define OBJECTIVE_${type.name} ${index}u")
    }
    line()

    line("// Quest indices")
    for (quest in game.quests) {
        val constName = "QUEST_${quest.id.uppercase()}"
        line("#define $constName ${quest.questIndex}u")
    }
    line()

    line("// Quest counts")
    line("#define QUEST_COUNT ${game.quests.size}u")
    val maxObjectives = game.quests.maxOfOrNull { it.objectives.size } ?: 0
    line("#define MAX_OBJECTIVES_PER_QUEST ${maxObjectives}u")
    line()

    // Tracker configuration constants
    game.questTracker?.let { tracker ->
        line("// Quest tracker configuration")
        line("#define MAX_ACTIVE_QUESTS ${tracker.maxActiveQuests}u")
        line("#define SHOW_QUEST_NOTIFICATIONS ${if (tracker.showNotifications) 1 else 0}u")
        line("#define SHOW_HUD_TRACKER ${if (tracker.showHudTracker) 1 else 0}u")
        line("#define MAX_HUD_OBJECTIVES ${tracker.maxHudObjectives}u")
        line()
    }
}

/** Generate quest state and objective tracking variables. */
private fun CodeGenerator.generateQuestVariables() {
    line("// Quest state tracking")
    line("// State: 0=NOT_STARTED, 1=IN_PROGRESS, 2=COMPLETED, 3=FAILED, 4=ABANDONED")
    line("static UINT8 _quest_state[QUEST_COUNT];")
    line()

    line("// Objective progress tracking")
    line("// Stores current count for each objective (0-255)")
    line("static UINT8 _quest_objective_progress[QUEST_COUNT][MAX_OBJECTIVES_PER_QUEST];")
    line()

    line("// Objective completion flags (bitfield)")
    line("static UINT8 _quest_objective_complete[QUEST_COUNT];")
    line()

    // Generate objective requirement data
    line("// Objective requirements (required count per objective)")
    line("static const UINT8 _quest_objective_required[QUEST_COUNT][MAX_OBJECTIVES_PER_QUEST] = {")
    indent++
    for (quest in game.quests) {
        val requirements = quest.objectives.map { it.requiredCount }
        val padded =
            requirements +
                List((game.quests.maxOfOrNull { it.objectives.size } ?: 0) - requirements.size) {
                    0
                }
        line("{ ${padded.joinToString(", ") { "${it}u" }} }, // ${quest.id}")
    }
    indent--
    line("};")
    line()

    // Generate objective types
    line("// Objective types")
    line("static const UINT8 _quest_objective_type[QUEST_COUNT][MAX_OBJECTIVES_PER_QUEST] = {")
    indent++
    for (quest in game.quests) {
        val types = quest.objectives.map { it.type.ordinal }
        val maxObj = game.quests.maxOfOrNull { it.objectives.size } ?: 0
        val padded = types + List(maxObj - types.size) { 0 }
        line("{ ${padded.joinToString(", ") { "${it}u" }} }, // ${quest.id}")
    }
    indent--
    line("};")
    line()

    // Generate objective count per quest
    line("// Number of objectives per quest")
    line("static const UINT8 _quest_objective_count[QUEST_COUNT] = {")
    indent++
    val counts = game.quests.map { "${it.objectives.size}u" }
    counts.chunked(8).forEach { chunk -> line("${chunk.joinToString(", ")},") }
    indent--
    line("};")
    line()
}

/** Generate objective type checking helpers. */
private fun CodeGenerator.generateObjectiveHelpers() {
    line("// Check if objective is complete")
    line("static UINT8 _is_objective_complete(UINT8 quest_idx, UINT8 obj_idx) {")
    indent++
    line("return (_quest_objective_complete[quest_idx] & (1u << obj_idx)) != 0u;")
    indent--
    line("}")
    line()

    line("// Mark objective as complete")
    line("static void _mark_objective_complete(UINT8 quest_idx, UINT8 obj_idx) {")
    indent++
    line("_quest_objective_complete[quest_idx] |= (1u << obj_idx);")
    indent--
    line("}")
    line()

    line("// Get objective progress")
    line("static UINT8 _get_objective_progress(UINT8 quest_idx, UINT8 obj_idx) {")
    indent++
    line("return _quest_objective_progress[quest_idx][obj_idx];")
    indent--
    line("}")
    line()

    line("// Check if all required objectives are complete")
    line("static UINT8 _all_objectives_complete(UINT8 quest_idx) {")
    indent++
    line("UINT8 i;")
    line("for (i = 0; i < _quest_objective_count[quest_idx]; i++) {")
    indent++
    line("if (!_is_objective_complete(quest_idx, i)) return 0u;")
    indent--
    line("}")
    line("return 1u;")
    indent--
    line("}")
    line()
}

/** Generate quest management functions. */
private fun CodeGenerator.generateQuestFunctions() {
    line("// =============================================================================")
    line("// QUEST MANAGEMENT FUNCTIONS")
    line("// =============================================================================")
    line()

    // Generate per-quest start callbacks
    for (quest in game.quests) {
        if (quest.onStartStatements.isNotEmpty()) {
            line("// On start callback for ${quest.id}")
            line("static void _quest_${quest.id.lowercase()}_on_start(void) {")
            indent++
            for (stmt in quest.onStartStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (quest.onCompleteStatements.isNotEmpty()) {
            line("// On complete callback for ${quest.id}")
            line("static void _quest_${quest.id.lowercase()}_on_complete(void) {")
            indent++
            for (stmt in quest.onCompleteStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (quest.onFailStatements.isNotEmpty()) {
            line("// On fail callback for ${quest.id}")
            line("static void _quest_${quest.id.lowercase()}_on_fail(void) {")
            indent++
            for (stmt in quest.onFailStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        // Generate per-objective callbacks
        for ((objIdx, objective) in quest.objectives.withIndex()) {
            if (objective.onProgressStatements.isNotEmpty()) {
                line("// Progress callback for ${quest.id}/${objective.id}")
                line("static void _quest_${quest.id.lowercase()}_obj${objIdx}_progress(void) {")
                indent++
                for (stmt in objective.onProgressStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("}")
                line()
            }

            if (objective.onCompleteStatements.isNotEmpty()) {
                line("// Complete callback for ${quest.id}/${objective.id}")
                line("static void _quest_${quest.id.lowercase()}_obj${objIdx}_complete(void) {")
                indent++
                for (stmt in objective.onCompleteStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("}")
                line()
            }
        }
    }

    // Start quest function
    line("// Start a quest")
    line("static void _start_quest(UINT8 quest_idx) {")
    indent++
    line("if (_quest_state[quest_idx] != QUEST_STATE_NOT_STARTED) return;")
    line("_quest_state[quest_idx] = QUEST_STATE_IN_PROGRESS;")
    line()
    line("// Call quest-specific start callback")
    line("switch (quest_idx) {")
    indent++
    for (quest in game.quests) {
        if (quest.onStartStatements.isNotEmpty()) {
            line(
                "case QUEST_${quest.id.uppercase()}: _quest_${quest.id.lowercase()}_on_start(); break;"
            )
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Update objective progress function
    line("// Update objective progress")
    line("static void _update_objective_progress(UINT8 quest_idx, UINT8 obj_idx, UINT8 amount) {")
    indent++
    line("UINT8 old_progress, new_progress, required;")
    line("if (_quest_state[quest_idx] != QUEST_STATE_IN_PROGRESS) return;")
    line("if (_is_objective_complete(quest_idx, obj_idx)) return;")
    line()
    line("old_progress = _quest_objective_progress[quest_idx][obj_idx];")
    line("required = _quest_objective_required[quest_idx][obj_idx];")
    line()
    line("// Add progress with overflow protection")
    line("if (old_progress + amount >= required) {")
    indent++
    line("new_progress = required;")
    indent--
    line("} else {")
    indent++
    line("new_progress = old_progress + amount;")
    indent--
    line("}")
    line("_quest_objective_progress[quest_idx][obj_idx] = new_progress;")
    line()
    line("// Check if objective is now complete")
    line("if (new_progress >= required) {")
    indent++
    line("_mark_objective_complete(quest_idx, obj_idx);")
    line("_call_objective_complete_callback(quest_idx, obj_idx);")
    line()
    line("// Check if quest is complete")
    line("if (_all_objectives_complete(quest_idx)) {")
    indent++
    line("_complete_quest(quest_idx);")
    indent--
    line("}")
    indent--
    line("} else {")
    indent++
    line("_call_objective_progress_callback(quest_idx, obj_idx);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Objective callbacks dispatch
    line("// Call objective progress callback")
    line("static void _call_objective_progress_callback(UINT8 quest_idx, UINT8 obj_idx) {")
    indent++
    generateObjectiveCallbackSwitch("progress")
    indent--
    line("}")
    line()

    line("// Call objective complete callback")
    line("static void _call_objective_complete_callback(UINT8 quest_idx, UINT8 obj_idx) {")
    indent++
    generateObjectiveCallbackSwitch("complete")
    indent--
    line("}")
    line()

    // Complete quest function
    line("// Complete a quest and grant rewards")
    line("static void _complete_quest(UINT8 quest_idx) {")
    indent++
    line("if (_quest_state[quest_idx] != QUEST_STATE_IN_PROGRESS) return;")
    line("_quest_state[quest_idx] = QUEST_STATE_COMPLETED;")
    line()
    line("// Grant rewards")
    line("_grant_quest_rewards(quest_idx);")
    line()
    line("// Call quest-specific complete callback")
    line("switch (quest_idx) {")
    indent++
    for (quest in game.quests) {
        if (quest.onCompleteStatements.isNotEmpty()) {
            line(
                "case QUEST_${quest.id.uppercase()}: _quest_${quest.id.lowercase()}_on_complete(); break;"
            )
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Fail quest function
    line("// Fail a quest")
    line("static void _fail_quest(UINT8 quest_idx) {")
    indent++
    line("if (_quest_state[quest_idx] != QUEST_STATE_IN_PROGRESS) return;")
    line("_quest_state[quest_idx] = QUEST_STATE_FAILED;")
    line()
    line("// Call quest-specific fail callback")
    line("switch (quest_idx) {")
    indent++
    for (quest in game.quests) {
        if (quest.onFailStatements.isNotEmpty()) {
            line(
                "case QUEST_${quest.id.uppercase()}: _quest_${quest.id.lowercase()}_on_fail(); break;"
            )
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Abandon quest function
    line("// Abandon a quest")
    line("static void _abandon_quest(UINT8 quest_idx) {")
    indent++
    line("if (_quest_state[quest_idx] != QUEST_STATE_IN_PROGRESS) return;")
    line("_quest_state[quest_idx] = QUEST_STATE_ABANDONED;")
    indent--
    line("}")
    line()

    // Get quest state function
    line("// Get quest state")
    line("static UINT8 _get_quest_state(UINT8 quest_idx) {")
    indent++
    line("return _quest_state[quest_idx];")
    indent--
    line("}")
    line()

    // Check quest available
    line("// Check if quest prerequisites are met")
    line("static UINT8 _is_quest_available(UINT8 quest_idx) {")
    indent++
    line("// Check if not already started/completed")
    line("if (_quest_state[quest_idx] != QUEST_STATE_NOT_STARTED) return 0u;")
    line()
    generatePrerequisiteChecks()
    line()
    line("return 1u;")
    indent--
    line("}")
    line()
}

/** Generate objective callback switch statement. */
private fun CodeGenerator.generateObjectiveCallbackSwitch(callbackType: String) {
    // Generate nested switch for quest → objective
    val questsWithCallbacks =
        game.quests.filter { quest ->
            quest.objectives.any { obj ->
                when (callbackType) {
                    "progress" -> obj.onProgressStatements.isNotEmpty()
                    "complete" -> obj.onCompleteStatements.isNotEmpty()
                    else -> false
                }
            }
        }

    if (questsWithCallbacks.isEmpty()) {
        line("// No callbacks defined")
        line("(void)quest_idx;")
        line("(void)obj_idx;")
        return
    }

    line("switch (quest_idx) {")
    indent++
    for (quest in questsWithCallbacks) {
        line("case QUEST_${quest.id.uppercase()}:")
        indent++
        line("switch (obj_idx) {")
        indent++
        for ((objIdx, obj) in quest.objectives.withIndex()) {
            val hasCallback =
                when (callbackType) {
                    "progress" -> obj.onProgressStatements.isNotEmpty()
                    "complete" -> obj.onCompleteStatements.isNotEmpty()
                    else -> false
                }
            if (hasCallback) {
                line(
                    "case ${objIdx}u: _quest_${quest.id.lowercase()}_obj${objIdx}_$callbackType(); break;"
                )
            }
        }
        line("default: break;")
        indent--
        line("}")
        line("break;")
        indent--
    }
    line("default: break;")
    indent--
    line("}")
}

/** Generate prerequisite check code. */
private fun CodeGenerator.generatePrerequisiteChecks() {
    val questsWithPrereqs = game.quests.filter { it.prerequisites.isNotEmpty() }
    if (questsWithPrereqs.isEmpty()) {
        line("// No prerequisites defined")
        return
    }

    line("// Check prerequisites")
    line("switch (quest_idx) {")
    indent++
    for (quest in questsWithPrereqs) {
        line("case QUEST_${quest.id.uppercase()}:")
        indent++
        for (prereqId in quest.prerequisites) {
            val prereqQuest = game.quests.find { it.id == prereqId }
            if (prereqQuest != null) {
                line(
                    "if (_quest_state[QUEST_${prereqId.uppercase()}] != QUEST_STATE_COMPLETED) return 0u;"
                )
            }
        }
        line("break;")
        indent--
    }
    line("default: break;")
    indent--
    line("}")
}

/** Generate reward granting functions. */
private fun CodeGenerator.generateRewardFunctions() {
    line("// =============================================================================")
    line("// QUEST REWARDS")
    line("// =============================================================================")
    line()

    line("// Grant rewards for completing a quest")
    line("static void _grant_quest_rewards(UINT8 quest_idx) {")
    indent++

    val questsWithRewards = game.quests.filter { it.rewards.isNotEmpty() }
    if (questsWithRewards.isEmpty()) {
        line("(void)quest_idx; // No rewards defined")
    } else {
        line("switch (quest_idx) {")
        indent++
        for (quest in questsWithRewards) {
            line("case QUEST_${quest.id.uppercase()}:")
            indent++
            for (reward in quest.rewards) {
                generateRewardCode(reward)
            }
            line("break;")
            indent--
        }
        line("default: break;")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate code for a single reward. */
private fun CodeGenerator.generateRewardCode(reward: QuestReward) {
    when (reward) {
        is QuestReward.Experience -> {
            // Add exp to all characters with leveling
            val leveledCharacters = game.characters.filter { it.hasLeveling }
            if (leveledCharacters.isNotEmpty()) {
                line("// Grant ${reward.amount} experience to all party members")
                for (character in leveledCharacters) {
                    val prefix = character.name.lowercase()
                    line("_${prefix}_add_exp(${reward.amount}u);")
                }
            }
        }
        is QuestReward.Gold -> {
            line("// Grant ${reward.amount} gold")
            if (game.economy != null) {
                // Multi-currency system - use default currency (gold is usually index 0)
                line("_add_currency(0u, ${reward.amount}u);")
            } else if (game.shops.isNotEmpty()) {
                // Shop system exists without full economy - use simple gold
                line("_add_gold(${reward.amount}u);")
            } else {
                // No economy system - this is a no-op, just comment
                line("// (No economy system defined)")
            }
        }
        is QuestReward.Item -> {
            val item = game.items.find { it.id == reward.itemId }
            if (item != null) {
                line("// Grant ${reward.quantity}x ${reward.itemId}")
                if (game.inventories.isNotEmpty()) {
                    val invIdx = 0 // Default to first inventory
                    line("_inventory_add(${invIdx}u, ${item.itemIndex}u, ${reward.quantity}u);")
                } else {
                    line("// (No inventory system defined)")
                }
            } else {
                line("// WARNING: Unknown item '${reward.itemId}'")
            }
        }
        is QuestReward.Ability -> {
            val ability = game.abilities.find { it.id == reward.abilityId }
            if (ability != null) {
                line("// Grant ability: ${reward.abilityId}")
                // Abilities are typically unlocked by setting a flag or adding to a learned
                // abilities array
                // For now, we set a flag indicating the ability is learned
                val globalFlags = game.globalFlags
                if (globalFlags != null) {
                    // If flags system exists, check if this flag is defined
                    val flag =
                        globalFlags.pages
                            .flatMap { it.flags }
                            .find {
                                it.name.equals(reward.abilityId, ignoreCase = true) ||
                                    it.name.equals("${reward.abilityId}_learned", ignoreCase = true)
                            }
                    if (flag != null) {
                        val constName = "FLAG_${flag.name.uppercase()}"
                        line("FLAG_SET(${constName}_BYTE, ${constName}_MASK);")
                    } else {
                        line(
                            "// Ability unlock: define flag '${reward.abilityId}_learned' to track this"
                        )
                    }
                } else {
                    line("// (No flags system for ability tracking)")
                }
            } else {
                line("// WARNING: Unknown ability '${reward.abilityId}'")
            }
        }
        is QuestReward.StatBonus -> {
            line("// Grant stat bonus: ${reward.statId} +${reward.amount}")
            // Stat bonuses are applied to all characters or the primary character
            val characters = game.characters.ifEmpty { null }
            if (characters != null && characters.isNotEmpty()) {
                val character = characters.first()
                val prefix = character.name.lowercase()
                val statName = reward.statId.lowercase()
                // Use the stat modification function if stats system exists
                line("_${prefix}_${statName} += ${reward.amount}u;")
            } else {
                line("// (No characters defined for stat bonus)")
            }
        }
        is QuestReward.Flag -> {
            line("// Set flag: ${reward.flagId}")
            val globalFlags = game.globalFlags
            if (globalFlags != null) {
                // Look up the flag in the flags system
                val flag =
                    globalFlags.pages
                        .flatMap { it.flags }
                        .find { it.name.equals(reward.flagId, ignoreCase = true) }
                if (flag != null) {
                    val constName = "FLAG_${flag.name.uppercase()}"
                    line("FLAG_SET(${constName}_BYTE, ${constName}_MASK);")
                } else {
                    line("// WARNING: Unknown flag '${reward.flagId}' - define it in flags system")
                }
            } else {
                line("// (No flags system defined)")
            }
        }
        is QuestReward.Custom -> {
            line("// Custom reward: ${reward.rewardId} (${reward.description})")
            line("// Implement custom reward handling in game logic")
        }
    }
}

/** Generate quest tracker display helpers. */
private fun CodeGenerator.generateTrackerHelpers() {
    val tracker = game.questTracker ?: return

    line("// =============================================================================")
    line("// QUEST TRACKER")
    line("// =============================================================================")
    line()

    // Active quest tracking
    line("// Currently tracked/pinned quest (-1 = none)")
    line("static INT8 _tracked_quest = -1;")
    line()

    line("// Get count of active (in-progress) quests")
    line("static UINT8 _get_active_quest_count(void) {")
    indent++
    line("UINT8 i, count = 0;")
    line("for (i = 0; i < QUEST_COUNT; i++) {")
    indent++
    line("if (_quest_state[i] == QUEST_STATE_IN_PROGRESS) count++;")
    indent--
    line("}")
    line("return count;")
    indent--
    line("}")
    line()

    line("// Track/pin a quest")
    line("static void _track_quest(UINT8 quest_idx) {")
    indent++
    line("if (_quest_state[quest_idx] == QUEST_STATE_IN_PROGRESS) {")
    indent++
    line("_tracked_quest = (INT8)quest_idx;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Untrack quest")
    line("static void _untrack_quest(void) {")
    indent++
    line("_tracked_quest = -1;")
    indent--
    line("}")
    line()

    line("// Get tracked quest index (-1 if none)")
    line("static INT8 _get_tracked_quest(void) {")
    indent++
    line("return _tracked_quest;")
    indent--
    line("}")
    line()
}

// =============================================================================
// QUEST EXPRESSION GENERATION
// =============================================================================

/**
 * Generate quest-related expressions.
 *
 * @return the C expression string, or null if not a quest expression
 */
internal fun CodeGenerator.generateQuestExpr(
    @Suppress("UNUSED_PARAMETER") expr: io.github.gbkt.core.ir.IRExpression
): String? {
    // Quest expressions would be added here when IR nodes are created
    // e.g., IRGetQuestState, IRGetObjectiveProgress, etc.
    return null
}

// =============================================================================
// QUEST STATEMENT GENERATION
// =============================================================================

/**
 * Handle quest-related IR statements.
 *
 * @return true if this was a quest statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateQuestStatement(
    @Suppress("UNUSED_PARAMETER") stmt: io.github.gbkt.core.ir.IRStatement
): Boolean {
    // Quest statements would be added here when IR nodes are created
    // e.g., IRStartQuest, IRUpdateObjective, etc.
    return false
}
