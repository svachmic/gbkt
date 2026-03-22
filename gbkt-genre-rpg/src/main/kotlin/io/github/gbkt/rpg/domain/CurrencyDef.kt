/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// MULTI-CURRENCY DOMAIN TYPES (Plan 06.8-03, H11)
// =============================================================================
//
// CurrencyDef defines a named currency with a max cap and optional exchange
// rates to other currencies. The backend generates:
//   - `_currency_{id}` UINT16 global (current amount)
//   - `_currency_{id}_max` UINT16 const (max cap)
//   - `add_{id}(amount)` — add with max clamping
//   - `sub_{id}(amount)` — subtract (clamps to 0)
//   - `exchange_{from}_{to}(amount)` — exchange rate function (if defined)
//   - Localization string reference `str_currency_{id}` for currency name
//
// Key design decisions:
//   - CurrencyRef is a lightweight typed reference (NOT a sealed IR subtype)
//   - exchange rates are one-directional (from this currency to another)
//   - max=9999 default fits in UINT16 and suits typical RPG gold/currency caps
//   - null currencyRef in merchants/drops = use the first/default currency
// =============================================================================

/**
 * Defines a one-directional exchange rate from this currency to another.
 *
 * @property toId The ID of the target currency to exchange to.
 * @property rate Exchange rate: 1 unit of from-currency = rate units of to-currency.
 */
data class CurrencyExchange(val toId: String, val rate: Int)

/**
 * Domain data class representing a named currency.
 *
 * Plain Kotlin data class — NOT an IR type. Used by [io.github.gbkt.rpg.dsl.CurrencyBuilder] to
 * carry currency data. Registered as a [io.github.gbkt.core.ir.GenericSystem] with type
 * `"rpg_currency"`.
 *
 * @property id Unique identifier (used in generated C variable names, e.g. `_currency_gold`).
 * @property max Maximum amount this currency can hold. Default 9999.
 * @property exchanges Optional exchange rates to other currencies.
 */
data class CurrencyDef(
    val id: String,
    val max: Int = 9999,
    val exchanges: List<CurrencyExchange> = emptyList(),
)

/**
 * Typed reference to a registered currency definition.
 *
 * Returned by the [io.github.gbkt.rpg.dsl.CurrencyDelegate] delegate syntax. Equality is based
 * solely on the currency [id].
 *
 * ```kotlin
 * val gold by currency { max(9999) }
 * val gems by currency { max(99) }
 * merchant("shop") {
 *     item("potion") { price(50, gold) }
 *     item("rare_ring") { price(3, gems) }
 * }
 * ```
 */
data class CurrencyRef(val id: String)
