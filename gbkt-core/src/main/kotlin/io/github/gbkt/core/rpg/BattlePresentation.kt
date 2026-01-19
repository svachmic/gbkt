/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRBattleMessage
import io.github.gbkt.core.ir.IRShowDamageNumber
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// BATTLE PRESENTATION SYSTEM
// Visual feedback configuration for turn-based battles
// =============================================================================

/** Default initial delay before death animation starts (in frames). */
private const val DEFAULT_DEATH_ANIM_INITIAL_DELAY = 23

/** Default delay between each palette step in death animation (in frames). */
private const val DEFAULT_DEATH_ANIM_STEP_DELAY = 5

/**
 * Configuration for battle presentation (visual feedback).
 *
 * Controls how battles display damage numbers, messages, animations, and screen effects. All fields
 * are optional - if not configured, the default minimal presentation is used.
 */
data class BattlePresentationConfig(
    /** Whether to show floating damage/heal numbers */
    val showDamageNumbers: Boolean = false,

    /** Screen shake intensity on physical hits (0 = disabled) */
    val hitShakeIntensity: Int = 0,

    /** Screen shake duration in frames */
    val hitShakeDuration: Int = 8,

    /** Screen shake intensity on critical hits */
    val critShakeIntensity: Int = 0,

    /** Flash screen on critical hits */
    val flashOnCrit: Boolean = false,

    /** Duration of crit flash in frames */
    val critFlashDuration: Int = 4,

    /** Show "X attacks Y!" style messages */
    val showActionMessages: Boolean = false,

    /** Show "Critical hit!" messages */
    val showCritMessages: Boolean = false,

    /** Show "X defeated!" messages */
    val showDefeatMessages: Boolean = false,

    /** Callback when any attack is executed */
    val onAttack: List<IRStatement> = emptyList(),

    /** Callback when damage is dealt */
    val onDamage: List<IRStatement> = emptyList(),

    /** Callback when healing is applied */
    val onHeal: List<IRStatement> = emptyList(),

    /** Callback when a combatant is defeated */
    val onDefeat: List<IRStatement> = emptyList(),

    /** Callback when a critical hit occurs */
    val onCrit: List<IRStatement> = emptyList(),

    /** Callback when an action misses */
    val onMiss: List<IRStatement> = emptyList(),

    /** Message display duration in frames (for auto-advance) */
    val messageDisplayDuration: Int = 60,

    /** Damage number float speed (pixels per frame) */
    val damageNumberSpeed: Int = 1,

    /** Damage number display duration in frames */
    val damageNumberDuration: Int = 30,

    /** Whether to show palette fade-to-white death animation for monsters */
    val monsterDeathAnimation: Boolean = false,

    /** Initial delay before starting death animation (in frames) */
    val deathAnimationInitialDelay: Int = DEFAULT_DEATH_ANIM_INITIAL_DELAY,

    /** Delay between each palette step in death animation (in frames) */
    val deathAnimationStepDelay: Int = DEFAULT_DEATH_ANIM_STEP_DELAY,
)

/**
 * Builder for battle presentation configuration.
 *
 * Usage:
 * ```kotlin
 * val combat = battleSystem("main") {
 *     presentation {
 *         damageNumbers(true)
 *         screenShakeOnHit(4, 8.frames)
 *         screenShakeOnCrit(6, 12.frames)
 *         flashOnCrit(4.frames)
 *
 *         actionMessages(true)
 *         critMessages(true)
 *         defeatMessages(true)
 *
 *         onAttack {
 *             // Play attack animation
 *         }
 *
 *         onDamage {
 *             // Play hit sound
 *         }
 *
 *         onDefeat {
 *             // Play death animation
 *         }
 *     }
 * }
 * ```
 */
@GbktDsl
class BattlePresentationBuilder {
    private var showDamageNumbers = false
    private var hitShakeIntensity = 0
    private var hitShakeDuration = 8
    private var critShakeIntensity = 0
    private var flashOnCrit = false
    private var critFlashDuration = 4
    private var showActionMessages = false
    private var showCritMessages = false
    private var showDefeatMessages = false
    private var messageDisplayDuration = 60
    private var damageNumberSpeed = 1
    private var damageNumberDuration = 30
    private var onAttackStatements: List<IRStatement> = emptyList()
    private var onDamageStatements: List<IRStatement> = emptyList()
    private var onHealStatements: List<IRStatement> = emptyList()
    private var onDefeatStatements: List<IRStatement> = emptyList()
    private var onCritStatements: List<IRStatement> = emptyList()
    private var onMissStatements: List<IRStatement> = emptyList()
    private var monsterDeathAnimation = false
    private var deathAnimationInitialDelay = DEFAULT_DEATH_ANIM_INITIAL_DELAY
    private var deathAnimationStepDelay = DEFAULT_DEATH_ANIM_STEP_DELAY

    // =========================================================================
    // DAMAGE NUMBERS
    // =========================================================================

    /**
     * Enable/disable floating damage numbers.
     *
     * When enabled, damage and healing values appear above targets and float upward before fading.
     */
    fun damageNumbers(enabled: Boolean) {
        showDamageNumbers = enabled
    }

    /**
     * Configure damage number display.
     *
     * @param speed Float speed in pixels per frame (default: 1)
     * @param duration Display duration in frames (default: 30)
     */
    fun damageNumbers(enabled: Boolean, speed: Int = 1, duration: Int = 30) {
        showDamageNumbers = enabled
        damageNumberSpeed = speed
        damageNumberDuration = duration
    }

    // =========================================================================
    // SCREEN SHAKE
    // =========================================================================

    /**
     * Enable screen shake on physical hits.
     *
     * @param intensity Shake magnitude in pixels (1-8 recommended)
     * @param duration Shake duration in frames
     */
    fun screenShakeOnHit(intensity: Int, duration: Int = 8) {
        hitShakeIntensity = intensity
        hitShakeDuration = duration
    }

    /**
     * Enable screen shake on critical hits.
     *
     * @param intensity Shake magnitude in pixels (typically larger than hit shake)
     * @param duration Shake duration in frames
     */
    fun screenShakeOnCrit(intensity: Int, duration: Int = 12) {
        critShakeIntensity = intensity
    }

    // =========================================================================
    // SCREEN FLASH
    // =========================================================================

    /**
     * Enable screen flash on critical hits.
     *
     * @param duration Flash duration in frames
     */
    fun flashOnCrit(duration: Int = 4) {
        flashOnCrit = true
        critFlashDuration = duration
    }

    // =========================================================================
    // BATTLE MESSAGES
    // =========================================================================

    /** Enable "X attacks Y!" style action messages. */
    fun actionMessages(enabled: Boolean) {
        showActionMessages = enabled
    }

    /** Enable "Critical hit!" messages. */
    fun critMessages(enabled: Boolean) {
        showCritMessages = enabled
    }

    /** Enable "X defeated!" messages. */
    fun defeatMessages(enabled: Boolean) {
        showDefeatMessages = enabled
    }

    /**
     * Configure message auto-advance timing.
     *
     * @param duration Frames before message auto-advances (default: 60)
     */
    fun messageDisplayDuration(duration: Int) {
        messageDisplayDuration = duration
    }

    // =========================================================================
    // EVENT CALLBACKS
    // =========================================================================

    /**
     * Callback executed when an attack action is performed.
     *
     * Use for playing attack animations, sounds, etc.
     */
    fun onAttack(block: () -> Unit) {
        onAttackStatements = recordCallback(block)
    }

    /**
     * Callback executed when damage is dealt.
     *
     * Use for hit effects, sounds, etc.
     */
    fun onDamage(block: () -> Unit) {
        onDamageStatements = recordCallback(block)
    }

    /**
     * Callback executed when healing is applied.
     *
     * Use for heal effects, sounds, etc.
     */
    fun onHeal(block: () -> Unit) {
        onHealStatements = recordCallback(block)
    }

    /**
     * Callback executed when a combatant is defeated.
     *
     * Use for death animations, sounds, etc.
     */
    fun onDefeat(block: () -> Unit) {
        onDefeatStatements = recordCallback(block)
    }

    /**
     * Callback executed when a critical hit occurs.
     *
     * Use for special crit effects beyond the automatic shake/flash.
     */
    fun onCrit(block: () -> Unit) {
        onCritStatements = recordCallback(block)
    }

    /**
     * Callback executed when an attack misses.
     *
     * Use for miss sound effects, "Miss!" display, etc.
     */
    fun onMiss(block: () -> Unit) {
        onMissStatements = recordCallback(block)
    }

    // =========================================================================
    // DEATH ANIMATION
    // =========================================================================

    /**
     * Enable palette fade-to-white death animation for monsters.
     *
     * When enabled, defeated monsters will fade through a 6-step palette transition from red-tint
     * to white before disappearing.
     *
     * @param enabled Whether to show death animation
     */
    fun monsterDeathAnimation(enabled: Boolean) {
        monsterDeathAnimation = enabled
    }

    /**
     * Configure death animation timing.
     *
     * @param initialDelay Frames to wait before starting animation
     * @param stepDelay Frames between each palette step
     */
    fun monsterDeathAnimation(
        enabled: Boolean,
        initialDelay: Int = DEFAULT_DEATH_ANIM_INITIAL_DELAY,
        stepDelay: Int = DEFAULT_DEATH_ANIM_STEP_DELAY,
    ) {
        monsterDeathAnimation = enabled
        deathAnimationInitialDelay = initialDelay
        deathAnimationStepDelay = stepDelay
    }

    private fun recordCallback(block: () -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        return recorder.statements
    }

    internal fun build(): BattlePresentationConfig =
        BattlePresentationConfig(
            showDamageNumbers = showDamageNumbers,
            hitShakeIntensity = hitShakeIntensity,
            hitShakeDuration = hitShakeDuration,
            critShakeIntensity = critShakeIntensity,
            flashOnCrit = flashOnCrit,
            critFlashDuration = critFlashDuration,
            showActionMessages = showActionMessages,
            showCritMessages = showCritMessages,
            showDefeatMessages = showDefeatMessages,
            onAttack = onAttackStatements,
            onDamage = onDamageStatements,
            onHeal = onHealStatements,
            onDefeat = onDefeatStatements,
            onCrit = onCritStatements,
            onMiss = onMissStatements,
            messageDisplayDuration = messageDisplayDuration,
            damageNumberSpeed = damageNumberSpeed,
            damageNumberDuration = damageNumberDuration,
            monsterDeathAnimation = monsterDeathAnimation,
            deathAnimationInitialDelay = deathAnimationInitialDelay,
            deathAnimationStepDelay = deathAnimationStepDelay,
        )
}

// =============================================================================
// BATTLE MESSAGE DISPLAY (Runtime Operations)
// =============================================================================

/**
 * Show a battle message in the message area.
 *
 * @param message The message to display
 */
fun showBattleMessage(message: String) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRBattleMessage(message))
    }
}

/**
 * Show a damage number floating above a target.
 *
 * @param targetIndex Index of the target combatant
 * @param amount Damage amount to display
 * @param isCrit Whether this is a critical hit (affects color/style)
 * @param isHeal Whether this is healing (affects color)
 */
fun showDamageNumber(
    targetIndex: Int,
    amount: Int,
    isCrit: Boolean = false,
    isHeal: Boolean = false,
) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRShowDamageNumber(targetIndex, amount, isCrit, isHeal))
    }
}
