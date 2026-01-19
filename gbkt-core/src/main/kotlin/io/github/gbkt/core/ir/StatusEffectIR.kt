/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.EffectDuration
import io.github.gbkt.core.rpg.StackMode

// =============================================================================
// STATUS EFFECT IR NODES
// =============================================================================

/** IR node for applying a status effect to a target. */
data class IRApplyStatusEffect(
    val targetName: String,
    val effectId: Int,
    val effectName: String,
    val duration: EffectDuration,
    val stackMode: StackMode = StackMode.REFRESH_DURATION,
    val maxStacks: Int = 1,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for removing a specific status effect from a target. */
data class IRClearStatusEffect(
    val targetName: String,
    val effectId: Int,
    val effectName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for removing all status effects from a target. */
data class IRClearAllStatusEffects(
    val targetName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** Mode for status effect ticking. */
enum class EffectTickMode {
    /** Tick turn-based effects (at end of turn in combat). */
    TURN,

    /** Tick frame-based effects (every frame in action games). */
    FRAME,

    /** Tick all effects regardless of duration mode. */
    ALL,
}

/**
 * IR node for processing status effect ticks.
 *
 * This handles:
 * - Duration countdown
 * - DoT/HoT application
 * - Effect expiration
 *
 * @param targetName The character whose effects to tick
 * @param tickMode Which effects to tick (TURN for turn-based, FRAME for action games, ALL for both)
 */
data class IRStatusEffectTick(
    val targetName: String,
    val tickMode: EffectTickMode = EffectTickMode.TURN,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR expression for checking if a character has a specific effect. */
data class IRHasStatusEffect(val targetName: String, val effectId: Int) : IRExpression

/** IR expression for getting the stack count of an effect. */
data class IRStatusEffectStacks(val targetName: String, val effectId: Int) : IRExpression

/** IR expression for getting remaining duration of an effect. */
data class IRStatusEffectDuration(val targetName: String, val effectId: Int) : IRExpression

/** IR expression for checking if character can act (not prevented by status). */
data class IRCanAct(val targetName: String) : IRExpression

/**
 * IR statement for skipping a combatant's turn.
 *
 * Used when a character/monster has a status effect that prevents action (stun, sleep, trip, etc.).
 */
data class IRSkipTurn(
    val actorName: String,
    val reason: String = "status_effect",
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
