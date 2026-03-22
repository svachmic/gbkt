/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.AbilityLearnEntry
import io.github.gbkt.rpg.domain.ClassDef
import io.github.gbkt.rpg.domain.EquipSlot
import io.github.gbkt.rpg.domain.JobChangeMode
import io.github.gbkt.rpg.domain.StatGrowthRate
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// CLASS BUILDER
// =============================================================================
//
// Builds a ClassDef domain object and registers it as a GenericSystem.
// Used by the characterClass() DSL extension on GameBuilder.
// =============================================================================

/**
 * Typed reference to a registered class definition.
 *
 * Returned by [GameBuilder.characterClass] for use in builder contexts without raw string IDs.
 */
data class ClassRef(val id: String)

/**
 * Builder for [StatGrowthRate]. Collects per-level growth values for all 7 stats.
 *
 * All fields default to 0.
 *
 * Usage:
 * ```kotlin
 * growthRates {
 *     hp(10)
 *     atk(2)
 *     def(1)
 * }
 * ```
 */
class StatGrowthRateBuilder {
    private var hp: Int = 0
    private var sp: Int = 0
    private var atk: Int = 0
    private var def: Int = 0
    private var matk: Int = 0
    private var mdef: Int = 0
    private var agl: Int = 0

    /** HP gained per level. */
    fun hp(value: Int) {
        hp = value
    }

    /** SP gained per level. */
    fun sp(value: Int) {
        sp = value
    }

    /** ATK gained per level. */
    fun atk(value: Int) {
        atk = value
    }

    /** DEF gained per level. */
    fun def(value: Int) {
        def = value
    }

    /** MATK gained per level. */
    fun matk(value: Int) {
        matk = value
    }

    /** MDEF gained per level. */
    fun mdef(value: Int) {
        mdef = value
    }

    /** AGL gained per level. */
    fun agl(value: Int) {
        agl = value
    }

    /** Builds the [StatGrowthRate]. */
    fun build(): StatGrowthRate =
        StatGrowthRate(hp = hp, sp = sp, atk = atk, def = def, matk = matk, mdef = mdef, agl = agl)
}

/**
 * Builder for [ClassDef]. Defines a character class or job.
 *
 * @param id Unique identifier for this class.
 */
class ClassBuilder(val id: String) {
    private var className: String = id
    private var growthRates: StatGrowthRate = StatGrowthRate()
    private val equipRestrictions = mutableSetOf<EquipSlot>().apply { addAll(EquipSlot.entries) }
    private val learnableAbilities = mutableListOf<AbilityLearnEntry>()
    private var jobChangeMode: JobChangeMode = JobChangeMode.LOCKED

    /** Sets the display name for this class. */
    fun name(n: String) {
        className = n
    }

    /**
     * Configures the per-level stat growth rates using [StatGrowthRateBuilder].
     *
     * Usage:
     * ```kotlin
     * growthRates {
     *     hp(10); atk(2)
     * }
     * ```
     */
    fun growthRates(block: StatGrowthRateBuilder.() -> Unit) {
        val builder = StatGrowthRateBuilder()
        builder.block()
        growthRates = builder.build()
    }

    /**
     * Sets the equipment slot restrictions for this class.
     *
     * Only the specified slots are accessible. Calling this replaces the default (all slots).
     *
     * Usage:
     * ```kotlin
     * equips(EquipSlot.WEAPON, EquipSlot.HEAD, EquipSlot.BODY)
     * ```
     */
    fun equips(vararg slots: EquipSlot) {
        equipRestrictions.clear()
        equipRestrictions.addAll(slots.toList())
    }

    /**
     * Registers an ability that this class automatically learns at the given level.
     *
     * @param abilityId The ID of the ability to learn.
     * @param atLevel The level at which the ability is learned.
     */
    fun learns(abilityId: String, atLevel: Int) {
        learnableAbilities.add(AbilityLearnEntry(abilityId = abilityId, level = atLevel))
    }

    /**
     * Sets the job-change mode for this class.
     *
     * @param mode One of [JobChangeMode.LOCKED], [JobChangeMode.SWITCHABLE_FRESH], or
     *   [JobChangeMode.SWITCHABLE_WITH_SKILLS].
     */
    fun jobChangeMode(mode: JobChangeMode) {
        jobChangeMode = mode
    }

    /** Builds the [ClassDef] domain object. */
    fun build(): ClassDef =
        ClassDef(
            id = id,
            name = className,
            growthRates = growthRates,
            equipRestrictions = equipRestrictions.toSet(),
            learnableAbilities = learnableAbilities.toList(),
            jobChangeMode = jobChangeMode,
        )
}

/**
 * Delegate for property-name inference syntax: `val warrior by characterClass { }`.
 *
 * Implements [ReadOnlyProperty] so the class ID is inferred from the Kotlin property name.
 *
 * @param id Optional explicit ID. If empty, inferred from the property name via [provideDelegate].
 * @param block The [ClassBuilder] configuration block.
 * @param gameBuilder The [GameBuilder] captured from the extension-function call site.
 */
class ClassDelegate(
    private val id: String,
    private val block: ClassBuilder.() -> Unit,
    private val gameBuilder: GameBuilder,
) : ReadOnlyProperty<Any?, ClassRef> {

    private var ref: ClassRef? = null

    /** Called when delegate is accessed via a property — infers ID from property name. */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ClassRef> {
        val resolvedId = if (id.isEmpty()) property.name else id
        val builder = ClassBuilder(resolvedId)
        builder.block()
        val def = builder.build()

        val system =
            GenericSystem(id = resolvedId, config = mapOf("type" to "rpg_class", "def" to def))
        gameBuilder.registerSystem(system)
        ref = ClassRef(resolvedId)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ClassRef =
        ref ?: ClassRef(property.name)
}
