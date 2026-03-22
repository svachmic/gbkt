/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.rpg.domain.EffectCategory
import io.github.gbkt.rpg.domain.EffectTrigger
import io.github.gbkt.rpg.domain.ResistType
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.domain.StatusEffectDef
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Typed reference to a registered status effect definition.
 *
 * Returned by [GameBuilder.statusEffect] delegate syntax for use in builder contexts without raw
 * string IDs.
 */
data class StatusEffectRef(val id: String)

/**
 * Builder for [StatusEffectDef] — a status condition applied during combat.
 *
 * Produces a [StatusEffectDef] domain data class. The DSL extension function wraps it in a
 * [io.github.gbkt.core.ir.GenericSystem].
 *
 * Usage:
 * ```kotlin
 * val poison by statusEffect {
 *     name("Poison")
 *     debuff()
 *     duration(5)
 *     damagePerTurn(10)
 *     stackMode(StackMode.REFRESH_DURATION)
 * }
 * ```
 *
 * @param id Unique identifier for this status effect (inferred from property name when using
 *   delegate syntax).
 */
class StatusEffectBuilder(private val id: String) {
    private var name: String = id
    private var category: EffectCategory = EffectCategory.DEBUFF
    private var duration: Int = 3
    private var damagePerTurn: Int = 0
    private var healPerTurn: Int = 0
    private var stackMode: StackMode = StackMode.REFRESH_DURATION
    private var maxStacks: Int = 1
    private val triggers: MutableSet<EffectTrigger> = mutableSetOf()
    private val triggerOps: MutableMap<EffectTrigger, List<ScriptOp>> = mutableMapOf()
    private val immuneCategories: MutableSet<EffectCategory> = mutableSetOf()
    private val interactsWith: MutableMap<String, String> = mutableMapOf()
    private var onStackAppliedOps: List<ScriptOp> = emptyList()
    private var onStackRemovedOps: List<ScriptOp> = emptyList()
    private var applyChance: Int = 100
    private var resistType: ResistType = ResistType.FLAT
    private var resistStat: String = "mdef"
    private val immuneToEffects: MutableSet<String> = mutableSetOf()
    private var perStackScaling: Boolean = false

    /** Sets the display name for this status effect. */
    fun name(n: String) {
        name = n
    }

    /** Sets the effect category to BUFF (positive effect). */
    fun buff() {
        category = EffectCategory.BUFF
    }

    /** Sets the effect category to DEBUFF (negative effect). */
    fun debuff() {
        category = EffectCategory.DEBUFF
    }

    /**
     * Sets the effect category explicitly.
     *
     * Use [buff] / [debuff] for the common cases, or this method for DOT and CROWD_CONTROL.
     */
    fun category(cat: EffectCategory) {
        category = cat
    }

    /**
     * Sets the duration in turns.
     *
     * @param turns Number of turns the effect lasts. Use 0 for permanent until cleansed.
     */
    fun duration(turns: Int) {
        duration = turns
    }

    /**
     * Sets damage applied each turn (damage-over-time).
     *
     * When [perStackScaling] is true and [stackMode] is [StackMode.INTENSITY], the actual damage is
     * `damagePerTurn * currentStackCount`.
     */
    fun damagePerTurn(dmg: Int) {
        damagePerTurn = dmg
    }

    /** Sets HP recovery applied each turn (regeneration). */
    fun healPerTurn(heal: Int) {
        healPerTurn = heal
    }

    /** Sets how multiple applications of this effect interact. */
    fun stackMode(mode: StackMode) {
        stackMode = mode
    }

    /**
     * Sets the maximum number of concurrent stacks.
     *
     * Relevant only when [stackMode] is [StackMode.INTENSITY] or [StackMode.INDEPENDENT].
     */
    fun maxStacks(n: Int) {
        maxStacks = n
    }

    /**
     * Records script operations triggered on a specific event.
     *
     * @param trigger The event that activates these ops.
     * @param block Script operations to execute when the event fires.
     */
    fun onTrigger(trigger: EffectTrigger, block: ScriptBuilder.() -> Unit) {
        triggers.add(trigger)
        triggerOps[trigger] = ScriptBuilder.buildOps(block)
    }

    /**
     * Adds one or more effect categories that this effect is immune to.
     *
     * A character with this status effect cannot be affected by effects in the given categories.
     */
    fun immuneTo(vararg categories: EffectCategory) {
        immuneCategories.addAll(categories)
    }

    /**
     * Defines an interaction between this effect and another named effect.
     *
     * @param effectId The ID of the effect this one interacts with.
     * @param interaction Interaction type: "cancels", "converts_to", or "bonus_damage".
     */
    fun interacts(effectId: String, interaction: String) {
        interactsWith[effectId] = interaction
    }

    /** Sets the flat percentage chance to apply this effect (0-100). */
    fun applyChance(percent: Int) {
        applyChance = percent
    }

    /**
     * Sets the resist resolution type.
     * - [ResistType.FLAT]: uses [applyChance] directly.
     * - [ResistType.STAT_CONTEST]: modifies chance by `applyChance - (target_resistStat -
     *   caster_matk)`.
     *
     * (GAP-5)
     */
    fun resistType(type: ResistType) {
        resistType = type
    }

    /**
     * Sets the target stat used in STAT_CONTEST resist calculation.
     *
     * Only relevant when [resistType] is [ResistType.STAT_CONTEST]. Default is "mdef". (GAP-5)
     */
    fun resistStat(stat: String) {
        resistStat = stat
    }

    /**
     * Adds a specific effect ID to the per-effect immunity set.
     *
     * A character with this status is immune to the named effect regardless of [immuneCategories].
     * (GAP-6)
     */
    fun immuneToEffect(effectId: String) {
        immuneToEffects.add(effectId)
    }

    /**
     * Enables per-stack damage scaling (GAP-7).
     *
     * When true and [stackMode] is [StackMode.INTENSITY], [StatusEffectDef.damagePerTurn] is
     * multiplied by the current stack count instead of using a flat value.
     */
    fun perStackScaling() {
        perStackScaling = true
    }

    /**
     * Records script operations triggered when a new stack is added (INTENSITY mode).
     *
     * Example use case: 5 bleed stacks → hemorrhage burst.
     */
    fun onStackApplied(block: ScriptBuilder.() -> Unit) {
        onStackAppliedOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Records script operations triggered when a stack is removed (INTENSITY mode).
     *
     * Example use case: reward player when a debuff stack drops.
     */
    fun onStackRemoved(block: ScriptBuilder.() -> Unit) {
        onStackRemovedOps = ScriptBuilder.buildOps(block)
    }

    /** Builds and returns the [StatusEffectDef] domain object. */
    fun build(): StatusEffectDef =
        StatusEffectDef(
            id = id,
            name = name,
            category = category,
            duration = duration,
            damagePerTurn = damagePerTurn,
            healPerTurn = healPerTurn,
            stackMode = stackMode,
            maxStacks = maxStacks,
            triggers = triggers.toSet(),
            triggerOps = triggerOps.toMap(),
            immuneCategories = immuneCategories.toSet(),
            interactsWith = interactsWith.toMap(),
            onStackAppliedOps = onStackAppliedOps,
            onStackRemovedOps = onStackRemovedOps,
            applyChance = applyChance,
            resistType = resistType,
            resistStat = resistStat,
            immuneToEffects = immuneToEffects.toSet(),
            perStackScaling = perStackScaling,
        )
}

// =============================================================================
// STATUS EFFECT DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers a status effect's ID from the Kotlin property name and registers it
 * with the current [GameBuilder].
 *
 * Usage:
 * ```kotlin
 * val poison by statusEffect {
 *     name("Poison")
 *     debuff()
 *     duration(5)
 *     damagePerTurn(10)
 * }
 * ```
 *
 * @param id When empty, the property name is used as the status effect ID.
 * @param block The status effect configuration block.
 * @param gameBuilder The [GameBuilder] captured from the extension-function call site.
 */
class StatusEffectDelegate(
    private val id: String,
    private val block: StatusEffectBuilder.() -> Unit,
    private val gameBuilder: GameBuilder,
) : ReadOnlyProperty<Any?, StatusEffectRef> {
    private var ref: StatusEffectRef? = null

    /**
     * Called by Kotlin when `val x by statusEffect { ... }` is evaluated.
     *
     * Captures the property name, calls [GameBuilder.statusEffect], and stores the resulting
     * [StatusEffectRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, StatusEffectRef> {
        val resolvedId = id.ifEmpty { property.name }
        ref = gameBuilder.statusEffect(resolvedId, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): StatusEffectRef =
        ref ?: error("StatusEffectDelegate not initialized — was provideDelegate called?")
}
