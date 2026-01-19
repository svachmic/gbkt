/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.StatusEffectRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IRApplyStatusEffect
import io.github.gbkt.core.ir.IRClearAllStatusEffects
import io.github.gbkt.core.ir.IRClearStatusEffect
import io.github.gbkt.core.ir.StatType
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// STATUS EFFECT TYPES
// =============================================================================

/**
 * Category of status effect.
 *
 * Effects are categorized for stacking rules and UI display.
 */
enum class EffectCategory {
    /** Harmful effects (poison, burn, etc.) */
    DEBUFF,

    /** Beneficial effects (regen, haste, etc.) */
    BUFF,

    /** Stat modifications (ATK up, DEF down, etc.) */
    STAT_MOD,

    /** Special conditions (stun, sleep, etc.) */
    CONDITION,
}

/**
 * Potency tier for status effects.
 *
 * Higher tiers have stronger effects but may be harder to apply.
 */
enum class EffectTier(val multiplier: Int) {
    /** Common/weak effects */
    C(100),

    /** Uncommon/moderate effects */
    B(125),

    /** Rare/strong effects */
    A(150),

    /** Legendary/extreme effects */
    S(200),
}

/** Duration type for status effects. */
sealed class EffectDuration {
    /** Effect lasts for a set number of turns (for turn-based combat) */
    data class Turns(val count: Int) : EffectDuration()

    /**
     * Effect lasts for a set number of frames (for action/real-time games).
     *
     * At 60 FPS: 60 frames = 1 second, 300 frames = 5 seconds Useful for: stun locks, invincibility
     * frames, temporary buffs in action games
     */
    data class Frames(val count: Int) : EffectDuration()

    /** Effect lasts until end of battle */
    data object UntilBattleEnd : EffectDuration()

    /** Effect is permanent until cured */
    data object Permanent : EffectDuration()
}

/** Helper extensions for creating durations. */
val Int.turns: EffectDuration.Turns
    get() = EffectDuration.Turns(this)

/** Frame-based duration for action/real-time games. At 60 FPS: 60 frames = 1 second */
val Int.frames: EffectDuration.Frames
    get() = EffectDuration.Frames(this)

// =============================================================================
// DURATION CONSTANTS
// =============================================================================

/**
 * Sentinel value indicating the effect lasts until battle ends. Used in code generation to
 * distinguish from turn-counted effects.
 */
const val DURATION_UNTIL_BATTLE_END = 255

/**
 * Sentinel value indicating the effect is permanent until cured. Used in code generation to
 * distinguish from turn-counted effects.
 */
const val DURATION_PERMANENT = 254

/** Stacking behavior when applying a status effect that already exists. */
enum class StackMode {
    /** Replace existing effect with new application (reset all state) */
    REPLACE,

    /** Refresh duration to full, keep stacks unchanged */
    REFRESH_DURATION,

    /** Increment stack counter (up to maxStacks), keep current duration */
    STACK_INTENSITY,

    /** Add duration to current (up to 2x base), keep stacks unchanged */
    STACK_DURATION,

    /** Skip application if effect already active (no changes) */
    NONE,
}

/**
 * Target redirection mode for confused/charmed effects.
 *
 * When a character has a status effect with target redirection, their attacks may be redirected to
 * different targets during action execution.
 */
enum class TargetRedirectMode {
    /** Redirect to a random ally (same side as caster, including self) */
    RANDOM_SAME_SIDE,

    /** Redirect to a random enemy (opposite side from original target) */
    RANDOM_OPPOSITE_SIDE,

    /** Redirect to any random combatant in the battle */
    RANDOM_ANY,

    /** Always target self */
    SELF,
}

// =============================================================================
// STATUS EFFECT DEFINITION
// =============================================================================

/** Unique identifier for a status effect type. */
@kotlin.jvm.JvmInline value class StatusEffectId(val value: Int)

/**
 * Defines a status effect type.
 *
 * Status effects can modify stats, deal damage over time, prevent actions, or provide other
 * benefits/penalties.
 *
 * @param name Display name of the effect
 * @param id Unique numeric identifier for code generation
 * @param category Type of effect (buff, debuff, stat mod, condition)
 * @param baseDuration Default duration when applied
 * @param tier Potency tier affecting strength
 * @param stackMode How effect behaves when reapplied (see [StackMode])
 * @param maxStacks Maximum stacks if using STACK_INTENSITY mode
 * @param statModifiers Map of stat types to modifier percentages
 * @param damagePerTurn Damage dealt at turn start (for DoT effects)
 * @param healPerTurn Healing at turn start (for HoT effects)
 * @param preventsAction Whether this effect prevents taking actions
 * @param iconIndex Index into effect icon tileset for display
 * @param damageMultiplier Multiplier for outgoing damage dealt (100 = normal, 200 = 2x)
 * @param healingMultiplier Multiplier for outgoing healing done (100 = normal, 200 = 2x)
 * @param incomingDamageMultiplier Multiplier for incoming damage received (100 = normal, 50 =
 *   halve)
 * @param incomingHealingMultiplier Multiplier for incoming healing received (100 = normal, 200 =
 *   2x)
 * @param hitChanceModifier Modifier for attacker's hit chance (-50 = 50% less likely to hit, +25 =
 *   25% more likely)
 * @param evasionModifier Modifier for defender's evasion (+50 = 50% more likely to evade, -25 = 25%
 *   less likely)
 * @param targetRedirectMode How this effect redirects the caster's attacks (for confusion effects)
 * @param customTierMultiplier Custom tier multiplier (overrides tier.multiplier if set)
 */
data class StatusEffectDefinition(
    val name: String,
    val id: StatusEffectId,
    val category: EffectCategory,
    val baseDuration: EffectDuration = EffectDuration.Turns(3),
    val tier: EffectTier = EffectTier.C,
    val stackMode: StackMode = StackMode.REFRESH_DURATION,
    val maxStacks: Int = 1,
    val statModifiers: Map<StatType, Int> = emptyMap(),
    val damagePerTurn: Int = 0,
    val healPerTurn: Int = 0,
    val preventsAction: Boolean = false,
    val iconIndex: Int = 0,
    val damageMultiplier: Int = 100,
    val healingMultiplier: Int = 100,
    val incomingDamageMultiplier: Int = 100,
    val incomingHealingMultiplier: Int = 100,
    val hitChanceModifier: Int = 0,
    val evasionModifier: Int = 0,
    val targetRedirectMode: TargetRedirectMode? = null,
    val customTierMultiplier: Int? = null,
) {
    /**
     * The effective tier multiplier for this status effect.
     *
     * Uses customTierMultiplier if set, otherwise falls back to tier.multiplier.
     */
    val effectiveMultiplier: Int
        get() = customTierMultiplier ?: tier.multiplier

    /**
     * Whether this effect uses frame-based duration instead of turn-based.
     *
     * Frame-based effects are decremented every frame (for action/real-time games). Turn-based
     * effects are decremented at the end of each turn (for traditional RPGs).
     */
    val isFrameBased: Boolean
        get() = baseDuration is EffectDuration.Frames

    /** The raw duration value in the appropriate units (turns or frames). */
    val durationValue: Int
        get() =
            when (val d = baseDuration) {
                is EffectDuration.Turns -> d.count
                is EffectDuration.Frames -> d.count
                is EffectDuration.UntilBattleEnd -> DURATION_UNTIL_BATTLE_END
                is EffectDuration.Permanent -> DURATION_PERMANENT
            }
}

/** Builder for creating status effect definitions. */
@GbktDsl
@Suppress("TooManyFunctions") // DSL builder requires many configuration methods
class StatusEffectBuilder(private var name: String, private val id: Int) {
    private var category: EffectCategory = EffectCategory.DEBUFF
    private var duration: EffectDuration = EffectDuration.Turns(3)
    private var tier: EffectTier = EffectTier.C
    private var customTierMultiplier: Int? = null
    private var stackMode: StackMode = StackMode.REFRESH_DURATION
    private var maxStacks: Int = 1
    private val statMods = mutableMapOf<StatType, Int>()
    private var damagePerTurn: Int = 0
    private var healPerTurn: Int = 0
    private var preventsAction: Boolean = false
    private var iconIndex: Int = 0
    private var damageMultiplier: Int = 100
    private var healingMultiplier: Int = 100
    private var incomingDamageMultiplier: Int = 100
    private var incomingHealingMultiplier: Int = 100
    private var hitChanceModifier: Int = 0
    private var evasionModifier: Int = 0
    private var targetRedirectMode: TargetRedirectMode? = null

    /** Set effect category */
    fun category(value: EffectCategory) {
        category = value
    }

    /** Set effect as a buff */
    fun buff() {
        category = EffectCategory.BUFF
    }

    /** Set effect as a debuff */
    fun debuff() {
        category = EffectCategory.DEBUFF
    }

    /** Set effect duration */
    fun duration(value: EffectDuration) {
        duration = value
    }

    /** Set duration in turns (for turn-based combat) */
    fun duration(turns: Int) {
        duration = EffectDuration.Turns(turns)
    }

    /**
     * Set duration in frames (for action/real-time games).
     *
     * At 60 FPS: 60 frames = 1 second, 300 frames = 5 seconds. Use this for invincibility frames,
     * stun locks, or temporary buffs in action games.
     *
     * @param frameCount Number of frames the effect lasts
     */
    fun durationFrames(frameCount: Int) {
        require(frameCount > 0) { "Frame count must be positive" }
        duration = EffectDuration.Frames(frameCount)
    }

    /** Set effect tier using predefined tier. */
    fun tier(value: EffectTier) {
        tier = value
        customTierMultiplier = null
    }

    /**
     * Set a custom tier multiplier for this status effect.
     *
     * This allows for more granular control over effect potency than the predefined tiers. The
     * multiplier is a percentage where 100 = normal, 150 = 50% stronger, etc.
     *
     * Usage:
     * ```kotlin
     * val enhancedPoison by statusEffect {
     *     tier(175)  // 75% stronger than base
     *     damagePerTurn(10)
     * }
     * ```
     *
     * @param multiplier Potency percentage (100 = base, 200 = 2x potency)
     */
    fun tier(multiplier: Int) {
        require(multiplier > 0) { "Tier multiplier must be positive" }
        customTierMultiplier = multiplier
    }

    /** Set stacking mode for when effect is reapplied */
    fun stackMode(mode: StackMode) {
        stackMode = mode
    }

    /** Make effect stackable with intensity stacking (max = maximum stacks) */
    fun stackable(max: Int = 4) {
        stackMode = StackMode.STACK_INTENSITY
        maxStacks = max
    }

    /** Add stat modifier (percentage: 100 = normal, 150 = +50%, 50 = -50%) */
    fun modifyStat(stat: StatType, percentage: Int) {
        statMods[stat] = percentage
    }

    /** Increase ATK by percentage */
    fun atkUp(percentage: Int = 125) {
        statMods[StatType.ATK] = percentage
    }

    /** Decrease ATK by percentage */
    fun atkDown(percentage: Int = 75) {
        statMods[StatType.ATK] = percentage
    }

    /** Increase DEF by percentage */
    fun defUp(percentage: Int = 125) {
        statMods[StatType.DEF] = percentage
    }

    /** Decrease DEF by percentage */
    fun defDown(percentage: Int = 75) {
        statMods[StatType.DEF] = percentage
    }

    /** Increase MATK by percentage */
    fun matkUp(percentage: Int = 125) {
        statMods[StatType.MATK] = percentage
    }

    /** Decrease MATK by percentage */
    fun matkDown(percentage: Int = 75) {
        statMods[StatType.MATK] = percentage
    }

    /** Increase MDEF by percentage */
    fun mdefUp(percentage: Int = 125) {
        statMods[StatType.MDEF] = percentage
    }

    /** Decrease MDEF by percentage */
    fun mdefDown(percentage: Int = 75) {
        statMods[StatType.MDEF] = percentage
    }

    /** Increase AGL by percentage */
    fun aglUp(percentage: Int = 125) {
        statMods[StatType.AGL] = percentage
    }

    /** Decrease AGL by percentage */
    fun aglDown(percentage: Int = 75) {
        statMods[StatType.AGL] = percentage
    }

    /** Set damage per turn (for DoT effects like poison) */
    fun damagePerTurn(amount: Int) {
        damagePerTurn = amount
    }

    /** Set healing per turn (for HoT effects like regen) */
    fun healPerTurn(amount: Int) {
        healPerTurn = amount
    }

    /** Make effect prevent actions (stun, sleep, etc.) */
    fun preventsAction() {
        preventsAction = true
    }

    /** Set icon index for UI display */
    fun icon(index: Int) {
        iconIndex = index
    }

    /** Set display name (optional, defaults to property name) */
    fun name(displayName: String) {
        name = displayName
    }

    /**
     * Set damage multiplier for this effect.
     *
     * When a character has this effect, their outgoing damage is multiplied.
     *
     * @param percentage Multiplier percentage (100 = normal, 200 = 2x damage, 50 = 0.5x damage)
     */
    fun damageMultiplier(percentage: Int) {
        require(percentage > 0) { "Damage multiplier must be positive" }
        damageMultiplier = percentage
    }

    /**
     * Set healing multiplier for this effect.
     *
     * When a character has this effect, their outgoing healing is multiplied.
     *
     * @param percentage Multiplier percentage (100 = normal, 200 = 2x healing, 50 = 0.5x healing)
     */
    fun healingMultiplier(percentage: Int) {
        require(percentage > 0) { "Healing multiplier must be positive" }
        healingMultiplier = percentage
    }

    /** Double damage output while this effect is active. Shorthand for damageMultiplier(200). */
    fun doubleDamage() {
        damageMultiplier = 200
    }

    /** Halve damage output while this effect is active. Shorthand for damageMultiplier(50). */
    fun halveDamage() {
        damageMultiplier = 50
    }

    /** Double healing output while this effect is active. Shorthand for healingMultiplier(200). */
    fun doubleHealing() {
        healingMultiplier = 200
    }

    /** Halve healing output while this effect is active. Shorthand for healingMultiplier(50). */
    fun halveHealing() {
        healingMultiplier = 50
    }

    /**
     * Set incoming damage multiplier for this effect.
     *
     * When a character has this effect, incoming damage they receive is multiplied.
     *
     * @param percentage Multiplier percentage (100 = normal, 50 = halve damage taken, 200 = 2x
     *   damage taken)
     */
    fun incomingDamageMultiplier(percentage: Int) {
        require(percentage > 0) { "Incoming damage multiplier must be positive" }
        incomingDamageMultiplier = percentage
    }

    /**
     * Set incoming healing multiplier for this effect.
     *
     * When a character has this effect, incoming healing they receive is multiplied.
     *
     * @param percentage Multiplier percentage (100 = normal, 50 = halve healing received, 200 = 2x
     *   healing received)
     */
    fun incomingHealingMultiplier(percentage: Int) {
        require(percentage > 0) { "Incoming healing multiplier must be positive" }
        incomingHealingMultiplier = percentage
    }

    /**
     * Halve incoming damage while this effect is active. Useful for defensive buffs like Barkskin.
     * Shorthand for incomingDamageMultiplier(50).
     */
    fun halveIncomingDamage() {
        incomingDamageMultiplier = 50
    }

    /**
     * Double incoming damage while this effect is active. Useful for vulnerability debuffs.
     * Shorthand for incomingDamageMultiplier(200).
     */
    fun doubleIncomingDamage() {
        incomingDamageMultiplier = 200
    }

    /**
     * Halve incoming healing while this effect is active. Useful for healing reduction debuffs.
     * Shorthand for incomingHealingMultiplier(50).
     */
    fun halveIncomingHealing() {
        incomingHealingMultiplier = 50
    }

    /**
     * Double incoming healing while this effect is active. Useful for healing amplification buffs.
     * Shorthand for incomingHealingMultiplier(200).
     */
    fun doubleIncomingHealing() {
        incomingHealingMultiplier = 200
    }

    /**
     * Set hit chance modifier for this effect.
     *
     * When a character has this effect, their attacks are modified by this amount. Positive values
     * increase hit chance, negative values decrease it.
     *
     * @param modifier Modifier percentage (-100 to +100). Example: -50 means 50% less likely to
     *   hit.
     */
    fun hitChanceModifier(modifier: Int) {
        require(modifier in -100..100) { "Hit chance modifier must be between -100 and 100" }
        hitChanceModifier = modifier
    }

    /**
     * Reduce attacker's hit chance while this effect is active. Useful for effects like Sleet Storm
     * that make attacks miss more often.
     *
     * @param amount Percentage to reduce hit chance (default 50 = 50% less likely to hit)
     */
    fun reduceHitChance(amount: Int = 50) {
        require(amount in 1..100) { "Hit chance reduction must be between 1 and 100" }
        hitChanceModifier = -amount
    }

    /**
     * Improve attacker's hit chance while this effect is active. Useful for accuracy buffs.
     *
     * @param amount Percentage to improve hit chance (default 25 = 25% more likely to hit)
     */
    fun improveHitChance(amount: Int = 25) {
        require(amount in 1..100) { "Hit chance improvement must be between 1 and 100" }
        hitChanceModifier = amount
    }

    /**
     * Set evasion modifier for this effect.
     *
     * When a character has this effect, attacks against them are modified. Positive values make
     * them harder to hit, negative values make them easier to hit.
     *
     * @param modifier Modifier percentage (-100 to +100). Example: +50 means 50% more likely to
     *   evade.
     */
    fun evasionModifier(modifier: Int) {
        require(modifier in -100..100) { "Evasion modifier must be between -100 and 100" }
        evasionModifier = modifier
    }

    /**
     * Increase defender's evasion while this effect is active. Useful for effects like Blur or
     * Mirror Image.
     *
     * @param amount Percentage to increase evasion (default 50 = 50% more likely to evade)
     */
    fun increaseEvasion(amount: Int = 50) {
        require(amount in 1..100) { "Evasion increase must be between 1 and 100" }
        evasionModifier = amount
    }

    /**
     * Reduce defender's evasion while this effect is active. Useful for effects that make targets
     * easier to hit.
     *
     * @param amount Percentage to reduce evasion (default 25 = 25% easier to hit)
     */
    fun reduceEvasion(amount: Int = 25) {
        require(amount in 1..100) { "Evasion reduction must be between 1 and 100" }
        evasionModifier = -amount
    }

    /**
     * Set target redirection mode for this effect.
     *
     * When a character has this effect, their attacks may be redirected during execution. This is
     * used for confusion, charm, and similar effects.
     *
     * @param mode The redirection mode to use
     */
    fun targetRedirect(mode: TargetRedirectMode) {
        targetRedirectMode = mode
    }

    /**
     * Make this effect cause random targeting confusion.
     *
     * The character will attack random targets - could be allies, enemies, or self. Shorthand for
     * targetRedirect(TargetRedirectMode.RANDOM_ANY).
     */
    fun confuseRandomly() {
        targetRedirectMode = TargetRedirectMode.RANDOM_ANY
    }

    /**
     * Make this effect redirect attacks to allies.
     *
     * The character will attack their own allies instead of enemies. Shorthand for
     * targetRedirect(TargetRedirectMode.RANDOM_SAME_SIDE).
     */
    fun redirectToAllies() {
        targetRedirectMode = TargetRedirectMode.RANDOM_SAME_SIDE
    }

    /**
     * Make this effect redirect attacks to self.
     *
     * The character will always attack themselves. Shorthand for
     * targetRedirect(TargetRedirectMode.SELF).
     */
    fun redirectToSelf() {
        targetRedirectMode = TargetRedirectMode.SELF
    }

    /**
     * Make this effect redirect attacks to the opposite side.
     *
     * The character will attack the opposite side from their intended target. For player
     * characters, this means attacking allies when trying to attack enemies. Shorthand for
     * targetRedirect(TargetRedirectMode.RANDOM_OPPOSITE_SIDE).
     */
    fun redirectToOpposite() {
        targetRedirectMode = TargetRedirectMode.RANDOM_OPPOSITE_SIDE
    }

    internal fun build(): StatusEffectDefinition =
        StatusEffectDefinition(
            name = name,
            id = StatusEffectId(id),
            category = category,
            baseDuration = duration,
            tier = tier,
            stackMode = stackMode,
            maxStacks = maxStacks,
            statModifiers = statMods.toMap(),
            damagePerTurn = damagePerTurn,
            healPerTurn = healPerTurn,
            preventsAction = preventsAction,
            iconIndex = iconIndex,
            damageMultiplier = damageMultiplier,
            healingMultiplier = healingMultiplier,
            incomingDamageMultiplier = incomingDamageMultiplier,
            incomingHealingMultiplier = incomingHealingMultiplier,
            hitChanceModifier = hitChanceModifier,
            evasionModifier = evasionModifier,
            targetRedirectMode = targetRedirectMode,
            customTierMultiplier = customTierMultiplier,
        )
}

// =============================================================================
// STATUS EFFECT APPLICATION
// =============================================================================

/**
 * Represents an active status effect on a character.
 *
 * This is a runtime reference for DSL operations.
 */
class StatusEffect internal constructor(val definition: StatusEffectDefinition) {
    val name: String
        get() = definition.name

    val id: StatusEffectId
        get() = definition.id

    /** Type-safe reference to this status effect */
    val ref: StatusEffectRef
        get() = StatusEffectRef(name)
}

/**
 * Apply a status effect to a character.
 *
 * Usage:
 * ```kotlin
 * applyEffect(hero, poison)
 * applyEffect(enemy, atkUp) { duration(5.turns) }
 * ```
 */
fun applyEffect(target: Character, effect: StatusEffect, duration: EffectDuration? = null) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRApplyStatusEffect(
                    targetName = target.name,
                    effectId = effect.id.value,
                    effectName = effect.name,
                    duration = duration ?: effect.definition.baseDuration,
                    stackMode = effect.definition.stackMode,
                    maxStacks = effect.definition.maxStacks,
                )
            )
    }
}

/**
 * Remove a specific status effect from a character.
 *
 * Usage:
 * ```kotlin
 * clearEffect(hero, poison)
 * ```
 */
fun clearEffect(target: Character, effect: StatusEffect) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRClearStatusEffect(
                    targetName = target.name,
                    effectId = effect.id.value,
                    effectName = effect.name,
                )
            )
    }
}

/**
 * Remove all status effects from a character.
 *
 * Usage:
 * ```kotlin
 * clearAllEffects(hero)
 * ```
 */
fun clearAllEffects(target: Character) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRClearAllStatusEffects(targetName = target.name))
    }
}

// =============================================================================
// CHARACTER STATUS EFFECT TRACKING
// =============================================================================

/**
 * Maximum number of active status effects per character.
 *
 * This is a Game Boy constraint to limit memory usage.
 */
const val MAX_ACTIVE_EFFECTS = 4

/**
 * Status effect slot for tracking active effects on a character.
 *
 * Each slot contains:
 * - Effect ID (0 = empty)
 * - Remaining duration
 * - Stack count
 */
data class StatusEffectSlot(val effectId: Int = 0, val duration: Int = 0, val stacks: Int = 0)

/** Container for a character's active status effects. */
class ActiveStatusEffects(val ownerName: String) {
    internal val slots = Array(MAX_ACTIVE_EFFECTS) { StatusEffectSlot() }
}

// =============================================================================
// STATUS EFFECT PROPERTY DELEGATE
// =============================================================================

/**
 * Property delegate for status effects.
 *
 * Usage: val poison by statusEffect { debuff(); duration(5); damagePerTurn(10) }
 */
class StatusEffectDelegate(
    private val gameBuilder: GameBuilder,
    private val init: StatusEffectBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, StatusEffect>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, StatusEffect> {
        val id = gameBuilder.nextStatusEffectId()
        val builder = StatusEffectBuilder(property.name.replaceFirstChar { it.uppercase() }, id)
        builder.init()
        val definition = builder.build()
        gameBuilder.registerStatusEffect(definition)
        val effect = StatusEffect(definition)

        return ReadOnlyProperty { _, _ -> effect }
    }
}

/**
 * Create a status effect using property delegate syntax.
 *
 * Usage:
 * ```kotlin
 * val poison by statusEffect {
 *     name("Poison")  // optional, defaults to "Poison" from property name
 *     debuff()
 *     duration(5)
 *     damagePerTurn(10)
 * }
 * ```
 */
fun GameBuilder.statusEffect(init: StatusEffectBuilder.() -> Unit): StatusEffectDelegate {
    return StatusEffectDelegate(this, init)
}
