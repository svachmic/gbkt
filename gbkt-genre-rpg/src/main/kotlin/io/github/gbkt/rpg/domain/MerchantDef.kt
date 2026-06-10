/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// Note: CurrencyRef is in the same package (io.github.gbkt.rpg.domain) — no import needed.

// =============================================================================
// ECONOMY / SHOP DOMAIN TYPES
// =============================================================================
//
// MerchantDef defines a shop with inventory, pricing, flag-gated stock, and
// per-item sell price overrides. The backend generates typed buy_from_<id>()
// and sell_to_<id>() C functions from these types.
//
// Key design decisions:
//   - null stockLimit means unlimited stock (emitted as 0xFF sentinel in C arrays)
//   - sellPriceOverride takes precedence over the global sellRatio (GAP-10)
//   - flagGatedStock maps flag name -> additional stock items gated by that flag
// =============================================================================

/**
 * An item available in a merchant's shop inventory.
 *
 * @param itemId The item definition ID (maps to ItemDef.id in GameIR.items).
 * @param price Base purchase price in currency units.
 * @param stockLimit Maximum purchasable quantity (null = unlimited).
 * @param sellPriceOverride Override sell price; takes precedence over MerchantDef.sellRatio
 *   (GAP-10).
 * @param currencyRef Per-item currency override. When non-null, this item's price is in that
 *   specific currency instead of the merchant's default currency (H11).
 */
data class ShopItem(
    val itemId: String,
    val price: Int,
    val stockLimit: Int? = null,
    val sellPriceOverride: Int? = null,
    val currencyRef: CurrencyRef? = null,
)

/**
 * A crafting recipe that produces an item from a list of ingredient items.
 *
 * @param resultItemId The resulting item ID.
 * @param ingredients List of (itemId, quantity) pairs required.
 */
data class CraftingRecipe(val resultItemId: String, val ingredients: List<Pair<String, Int>>)

/**
 * Defines a shop/merchant with inventory, pricing, flag-gated stock, and crafting.
 *
 * The backend generates:
 * - `_shop_<id>_stock[N]` const array (item IDs)
 * - `_shop_<id>_prices[N]` const array (buy prices)
 * - `_shop_<id>_stock_limit[N]` array (0xFF = unlimited)
 * - `_shop_<id>_sell_override[N]` const array (0xFF = use global ratio)
 * - `buy_from_<id>(slot_idx)` — currency check, inventory add, currency deduct
 * - `sell_to_<id>(item_id)` — sell price calculation respecting overrides (GAP-10)
 * - `is_<id>_stock_available(slot_idx)` — flag-gated stock availability check
 *
 * @param id Unique identifier for this merchant.
 * @param name Display name of the merchant.
 * @param stock Base inventory items always available.
 * @param currencyName Legacy currency name (default "Gold"). Ignored when [currencyRef] is set.
 * @param currencyRef Type-safe currency reference for the merchant's default currency (H11). When
 *   non-null, takes precedence over [currencyName]. Individual items may also override with
 *   [ShopItem.currencyRef].
 * @param sellRatio Global sell price ratio as percentage of buy price (0-100, default 50).
 * @param flagGatedStock Map of flag name to additional stock items gated by that flag.
 * @param craftingRecipes Optional crafting recipes available at this merchant.
 */
data class MerchantDef(
    val id: String,
    val name: String,
    val stock: List<ShopItem>,
    val currencyName: String = "Gold",
    val currencyRef: CurrencyRef? = null,
    val sellRatio: Int = 50,
    val flagGatedStock: Map<String, List<ShopItem>> = emptyMap(),
    val craftingRecipes: List<CraftingRecipe> = emptyList(),
)
