/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.CraftingRecipe
import io.github.gbkt.rpg.domain.CurrencyRef
import io.github.gbkt.rpg.domain.MerchantDef
import io.github.gbkt.rpg.domain.ShopItem

// =============================================================================
// MERCHANT / SHOP DSL BUILDERS
// =============================================================================

/**
 * Builder for a single shop item within a merchant's inventory.
 *
 * Used inside [MerchantBuilder.stock] and [MerchantBuilder.flagStock] blocks.
 *
 * @param itemId The item definition ID.
 */
class ShopItemBuilder(val itemId: String) {
    private var price: Int = 0
    private var stockLimit: Int? = null
    private var sellPriceOverride: Int? = null
    private var currencyRef: CurrencyRef? = null

    /** Sets the base purchase price. */
    fun price(p: Int) {
        price = p
    }

    /** Sets the maximum purchasable quantity (null = unlimited). */
    fun limit(n: Int) {
        stockLimit = n
    }

    /** Sets the sell price override, taking precedence over global sellRatio (GAP-10). */
    fun sellPrice(p: Int) {
        sellPriceOverride = p
    }

    /**
     * Sets the per-item currency override (H11). When set, this item's price is in the given
     * currency instead of the merchant's default currency.
     *
     * ```kotlin
     * item("rare_ring") { price(5); currency(gems) }
     * ```
     */
    fun currency(ref: CurrencyRef) {
        currencyRef = ref
    }

    fun build(): ShopItem =
        ShopItem(
            itemId = itemId,
            price = price,
            stockLimit = stockLimit,
            sellPriceOverride = sellPriceOverride,
            currencyRef = currencyRef,
        )
}

/**
 * Builder for a crafting recipe.
 *
 * @param resultItemId The item produced by this recipe.
 */
class CraftingRecipeBuilder(val resultItemId: String) {
    private val ingredients = mutableListOf<Pair<String, Int>>()

    /** Adds an ingredient item with the required quantity. */
    fun ingredient(itemId: String, quantity: Int = 1) {
        ingredients.add(itemId to quantity)
    }

    fun build(): CraftingRecipe =
        CraftingRecipe(resultItemId = resultItemId, ingredients = ingredients.toList())
}

/**
 * Builder for [MerchantDef] — defines a shop with inventory, pricing, flag-gated stock.
 *
 * ```kotlin
 * merchant("blacksmith") {
 *     name("Blacksmith")
 *     item("iron_sword") { price(200) }
 *     item("iron_shield") { price(150); limit(3) }
 *     currency("Gold")             // legacy string-based currency name
 *     sellRatio(40)
 *     flagStock("has_quest_reward") {
 *         item("legendary_blade") { price(1000) }
 *     }
 * }
 *
 * // Or with type-safe multi-currency (H11):
 * val gems by currency { max(99) }
 * merchant("gem_shop") {
 *     name("Gem Shop")
 *     currency(gems)               // type-safe CurrencyRef
 *     item("rare_ring") { price(5); currency(gems) }
 * }
 * ```
 *
 * @param id Unique identifier for this merchant.
 */
class MerchantBuilder(private val id: String) {
    private var name: String = id
    private var currencyName: String = "Gold"
    private var currencyRef: CurrencyRef? = null
    private var sellRatio: Int = 50
    private val stock = mutableListOf<ShopItem>()
    private val flagGatedStock = mutableMapOf<String, MutableList<ShopItem>>()
    private val craftingRecipes = mutableListOf<CraftingRecipe>()

    /** Sets the display name of the merchant. */
    fun name(n: String) {
        name = n
    }

    /** Sets the legacy currency name shown in the shop UI (backward compat). */
    fun currency(name: String) {
        currencyName = name
    }

    /**
     * Sets the merchant's default currency using a type-safe [CurrencyRef] (H11).
     *
     * Takes precedence over the string-based [currency(name)] override.
     */
    fun currency(ref: CurrencyRef) {
        currencyRef = ref
    }

    /** Sets the global sell price ratio as percentage of buy price (0-100). Default 50. */
    fun sellRatio(ratio: Int) {
        sellRatio = ratio
    }

    /** Adds a shop item to the base inventory. */
    fun item(itemId: String, block: ShopItemBuilder.() -> Unit = {}) {
        val builder = ShopItemBuilder(itemId)
        builder.block()
        stock.add(builder.build())
    }

    /** Adds flag-gated stock: items only available when the given flag is set. */
    fun flagStock(flagName: String, block: FlagStockBuilder.() -> Unit) {
        val builder = FlagStockBuilder()
        builder.block()
        flagGatedStock.getOrPut(flagName) { mutableListOf() }.addAll(builder.items)
    }

    /** Adds a crafting recipe available at this merchant. */
    fun recipe(resultItemId: String, block: CraftingRecipeBuilder.() -> Unit) {
        val builder = CraftingRecipeBuilder(resultItemId)
        builder.block()
        craftingRecipes.add(builder.build())
    }

    fun build(): MerchantDef =
        MerchantDef(
            id = id,
            name = name,
            stock = stock.toList(),
            currencyName = currencyName,
            currencyRef = currencyRef,
            sellRatio = sellRatio,
            flagGatedStock = flagGatedStock.mapValues { it.value.toList() },
            craftingRecipes = craftingRecipes.toList(),
        )
}

/** Nested builder for flag-gated stock within [MerchantBuilder.flagStock]. */
class FlagStockBuilder {
    internal val items = mutableListOf<ShopItem>()

    /** Adds an item to this flag-gated stock. */
    fun item(itemId: String, block: ShopItemBuilder.() -> Unit = {}) {
        val builder = ShopItemBuilder(itemId)
        builder.block()
        items.add(builder.build())
    }
}
