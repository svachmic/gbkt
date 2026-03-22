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
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.AoeShape
import io.github.gbkt.rpg.domain.Aspect
import io.github.gbkt.rpg.domain.TargetingMode
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Typed reference to a registered ability definition.
 *
 * Returned by [GameBuilder.ability] delegate syntax for use in builder contexts without raw string
 * IDs.
 */
data class AbilityRef(val id: String)

/**
 * Builder for [AbilityDef] — a skill, spell, or combat ability.
 *
 * Produces an [AbilityDef] domain data class. The DSL extension function wraps it in a
 * [io.github.gbkt.core.ir.GenericSystem].
 *
 * Usage:
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 *     cost(sp = 8)
 *     targeting(TargetingMode.SINGLE_ENEMY)
 *     aspect(Aspect.FIRE)
 *     power(30)
 *     execute { /* effect ops */ }
 * }
 * ```
 *
 * @param id Unique identifier for this ability (inferred from property name when using delegate
 *   syntax).
 */
class AbilityBuilder(private val id: String) {
    private var name: String = id
    private var spCost: Int = 0
    private var hpCost: Int = 0
    private var targeting: TargetingMode = TargetingMode.SINGLE_ENEMY
    private var aspect: Aspect = Aspect.NONE
    private var power: Int = 0
    private var accuracy: Int = 100
    private var chargeTurns: Int = 0
    private var rangeMin: Int = 0
    private var rangeMax: Int = 1
    private var aoeShape: AoeShape = AoeShape.SINGLE
    private var appliesEffect: String? = null
    private var effectChance: Int = 100
    private var executeOps: List<ScriptOp> = emptyList()

    /** Sets the display name for this ability. */
    fun name(n: String) {
        name = n
    }

    /**
     * Sets the resource cost to use this ability.
     *
     * @param sp SP (skill points / mana) consumed on use.
     * @param hp Optional HP cost.
     */
    fun cost(sp: Int = 0, hp: Int = 0) {
        spCost = sp
        hpCost = hp
    }

    /** Sets the targeting mode (who this ability can be used on). */
    fun targeting(mode: TargetingMode) {
        targeting = mode
    }

    /** Sets the elemental/magical aspect for type-effectiveness. */
    fun aspect(a: Aspect) {
        aspect = a
    }

    /** Sets the base power value for damage or healing calculations. */
    fun power(p: Int) {
        power = p
    }

    /** Sets the hit chance as a percentage (0-100). Default is 100. */
    fun accuracy(a: Int) {
        accuracy = a
    }

    /**
     * Sets the number of charge/telegraph turns before this ability executes.
     *
     * 0 = instant (default). Values >0 create a delay before the effect fires.
     */
    fun chargeTurns(n: Int) {
        chargeTurns = n
    }

    /**
     * Sets the tile range for use in the tactical grid combat variant.
     *
     * Ignored by non-grid combat variants (Simple Battle, Combat Engine).
     *
     * @param min Minimum range in grid tiles (0 = melee/self).
     * @param max Maximum range in grid tiles (1 = adjacent).
     */
    fun range(min: Int = 0, max: Int = 1) {
        rangeMin = min
        rangeMax = max
    }

    /**
     * Sets the area-of-effect pattern for use in the tactical grid combat variant.
     *
     * Ignored by non-grid combat variants.
     */
    fun aoeShape(shape: AoeShape) {
        aoeShape = shape
    }

    /**
     * Configures a status effect to apply when this ability hits.
     *
     * @param effectId The ID of the status effect to apply.
     * @param chance Percentage chance to apply the effect (0-100). Default is 100.
     */
    fun appliesEffect(effectId: String, chance: Int = 100) {
        appliesEffect = effectId
        effectChance = chance
    }

    /**
     * Records the script operations that execute this ability's effect.
     *
     * ```kotlin
     * execute {
     *     // damage, heal, navigate, etc.
     * }
     * ```
     */
    fun execute(block: ScriptBuilder.() -> Unit) {
        executeOps = ScriptBuilder.buildOps(block)
    }

    /** Builds and returns the [AbilityDef] domain object. */
    fun build(): AbilityDef =
        AbilityDef(
            id = id,
            name = name,
            spCost = spCost,
            hpCost = hpCost,
            targeting = targeting,
            aspect = aspect,
            power = power,
            accuracy = accuracy,
            chargeTurns = chargeTurns,
            executeOps = executeOps,
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            aoeShape = aoeShape,
            appliesEffect = appliesEffect,
            effectChance = effectChance,
        )
}

// =============================================================================
// ABILITY DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers an ability's ID from the Kotlin property name and registers it with
 * the current [GameBuilder].
 *
 * Usage:
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 *     cost(sp = 8)
 *     aspect(Aspect.FIRE)
 * }
 * ```
 *
 * @param id When empty, the property name is used as the ability ID.
 * @param block The ability configuration block.
 * @param gameBuilder The [GameBuilder] captured from the extension-function call site.
 */
class AbilityDelegate(
    private val id: String,
    private val block: AbilityBuilder.() -> Unit,
    private val gameBuilder: GameBuilder,
) : ReadOnlyProperty<Any?, AbilityRef> {
    private var ref: AbilityRef? = null

    /**
     * Called by Kotlin when `val x by ability { ... }` is evaluated.
     *
     * Captures the property name, calls [GameBuilder.ability], and stores the resulting
     * [AbilityRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, AbilityRef> {
        val resolvedId = id.ifEmpty { property.name }
        ref = gameBuilder.ability(resolvedId, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): AbilityRef =
        ref ?: error("AbilityDelegate not initialized — was provideDelegate called?")
}
