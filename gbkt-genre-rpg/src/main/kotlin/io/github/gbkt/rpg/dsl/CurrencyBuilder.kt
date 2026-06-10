/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.rpg.domain.CurrencyDef
import io.github.gbkt.rpg.domain.CurrencyExchange
import io.github.gbkt.rpg.domain.CurrencyRef
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// CURRENCY BUILDER AND DELEGATE (Plan 06.8-03, H11)
// =============================================================================
//
// CurrencyBuilder produces CurrencyDef domain objects.
// CurrencyDelegate provides the `val gold by currency { }` delegate syntax.
//
// The delegate pattern mirrors AbilityDelegate / StatusEffectDelegate:
//   - provideDelegate captures the property name as the currency ID
//   - GameBuilder.currency(id, block) registers a GenericSystem(type="rpg_currency")
//   - Returns CurrencyRef for type-safe usage in merchant/monster builders
// =============================================================================

/**
 * Builder for [CurrencyDef] — a named in-game currency with max cap and exchange rates.
 *
 * Produces a [CurrencyDef] domain data class. The DSL extension function wraps it in a
 * [io.github.gbkt.core.ir.GenericSystem] with type `"rpg_currency"`.
 *
 * Usage:
 * ```kotlin
 * val gold by currency {
 *     max(9999)
 * }
 * val gems by currency {
 *     max(99)
 *     exchange(to = gold, rate = 10)  // 1 gem = 10 gold
 * }
 * ```
 *
 * @param id Unique identifier for this currency (inferred from property name when using delegate
 *   syntax).
 */
class CurrencyBuilder(private val id: String) {
    private var max: Int = 9999
    private val exchanges: MutableList<CurrencyExchange> = mutableListOf()

    /**
     * Sets the maximum amount of this currency the player can hold.
     *
     * @param value Maximum cap. Default 9999. Must fit in UINT16 (0-65535).
     */
    fun max(value: Int) {
        max = value
    }

    /**
     * Adds a one-directional exchange rate from this currency to another.
     *
     * Generates an `exchange_{this}_{to.id}(amount)` C function.
     *
     * @param to The target currency reference.
     * @param rate Exchange rate: 1 unit of this currency = rate units of the target.
     */
    fun exchange(to: CurrencyRef, rate: Int) {
        exchanges.add(CurrencyExchange(toId = to.id, rate = rate))
    }

    /** Builds and returns the [CurrencyDef] domain object. */
    fun build(): CurrencyDef = CurrencyDef(id = id, max = max, exchanges = exchanges.toList())
}

// =============================================================================
// CURRENCY DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers a currency's ID from the Kotlin property name and registers it with
 * the current [GameBuilder].
 *
 * Usage:
 * ```kotlin
 * val gold by currency {
 *     max(9999)
 * }
 * val gems by currency {
 *     max(99)
 *     exchange(to = gold, rate = 10)
 * }
 * ```
 *
 * @param id When empty, the property name is used as the currency ID.
 * @param block The currency configuration block.
 * @param gameBuilder The [GameBuilder] captured from the extension-function call site.
 */
class CurrencyDelegate(
    private val id: String,
    private val block: CurrencyBuilder.() -> Unit,
    private val gameBuilder: GameBuilder,
) : ReadOnlyProperty<Any?, CurrencyRef> {
    private var ref: CurrencyRef? = null

    /**
     * Called by Kotlin when `val x by currency { ... }` is evaluated.
     *
     * Captures the property name, calls [GameBuilder.currency], and stores the resulting
     * [CurrencyRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, CurrencyRef> {
        val resolvedId = id.ifEmpty { property.name }
        ref = gameBuilder.currency(resolvedId, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): CurrencyRef =
        ref ?: error("CurrencyDelegate not initialized — was provideDelegate called?")
}
