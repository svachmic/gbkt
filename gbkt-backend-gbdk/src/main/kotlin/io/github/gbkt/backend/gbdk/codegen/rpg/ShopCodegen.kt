/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.rpg.ShopType

// =============================================================================
// SHOP/ECONOMY CODE GENERATION
// =============================================================================

/**
 * Generate shop and economy system code.
 *
 * Creates:
 * - Currency variables and management functions
 * - Shop data structures (items, prices, stock)
 * - Buy/sell functions
 * - Shop UI helpers
 */
internal fun GBDKCodeGenerator.generateShopSystem() {
    if (game.shops.isEmpty() && game.economy == null) return

    line("// =============================================================================")
    line("// SHOP/ECONOMY SYSTEM")
    line("// =============================================================================")
    line()

    // Generate economy system first (currencies)
    generateEconomySystem()

    // Generate shop data and functions
    if (game.shops.isNotEmpty()) {
        generateShopConstants()
        generateShopData()
        generateShopFunctions()
    }
}

/** Generate economy/currency system. */
private fun GBDKCodeGenerator.generateEconomySystem() {
    val economy = game.economy

    line("// Currency system")
    if (economy != null) {
        // Generate currency constants
        line("// Currency indices")
        for (currency in economy.currencies) {
            line("#define CURRENCY_${currency.id.uppercase()} ${currency.currencyIndex}u")
        }
        line("#define CURRENCY_COUNT ${economy.currencies.size}u")
        line()

        // Generate currency max amounts
        line("// Currency max amounts")
        line("static const UINT32 _currency_max[CURRENCY_COUNT] = {")
        indent++
        for (currency in economy.currencies) {
            line("${currency.maxAmount}u, // ${currency.displayName}")
        }
        indent--
        line("};")
        line()

        // Generate currency variables
        line("// Currency amounts")
        line("static UINT32 _currency[CURRENCY_COUNT];")
        line()

        // Initialize currencies
        line("// Initialize economy")
        line("static void _init_economy(void) {")
        indent++
        line("UINT8 i;")
        line("for (i = 0; i < CURRENCY_COUNT; i++) {")
        indent++
        line("_currency[i] = 0u;")
        indent--
        line("}")
        // Set starting gold if specified
        val goldIdx = economy.currencies.indexOfFirst { it.id == "gold" }
        if (goldIdx >= 0 && economy.startingGold > 0) {
            line(
                "_currency[CURRENCY_${economy.defaultCurrencyId.uppercase()}] = ${economy.startingGold}u;"
            )
        }
        indent--
        line("}")
        line()
    } else {
        // Default single-currency (gold) system
        line("#define MAX_GOLD 999999u")
        line("static UINT32 _gold = 0u;")
        line()
    }

    // Currency management functions
    line("// Get currency amount")
    if (economy != null) {
        line("static UINT32 _get_currency(UINT8 currency_idx) {")
        indent++
        line("return _currency[currency_idx];")
        indent--
        line("}")
    } else {
        line("static UINT32 _get_gold(void) {")
        indent++
        line("return _gold;")
        indent--
        line("}")
    }
    line()

    line("// Add currency (with overflow protection)")
    if (economy != null) {
        line("static void _add_currency(UINT8 currency_idx, UINT32 amount) {")
        indent++
        line("UINT32 max = _currency_max[currency_idx];")
        line("if (_currency[currency_idx] + amount > max) {")
        indent++
        line("_currency[currency_idx] = max;")
        indent--
        line("} else {")
        indent++
        line("_currency[currency_idx] += amount;")
        indent--
        line("}")
        indent--
        line("}")
    } else {
        line("static void _add_gold(UINT32 amount) {")
        indent++
        line("if (_gold + amount > MAX_GOLD) {")
        indent++
        line("_gold = MAX_GOLD;")
        indent--
        line("} else {")
        indent++
        line("_gold += amount;")
        indent--
        line("}")
        indent--
        line("}")
    }
    line()

    line("// Remove currency (returns 1 if successful, 0 if insufficient)")
    if (economy != null) {
        line("static UINT8 _remove_currency(UINT8 currency_idx, UINT32 amount) {")
        indent++
        line("if (_currency[currency_idx] < amount) return 0u;")
        line("_currency[currency_idx] -= amount;")
        line("return 1u;")
        indent--
        line("}")
    } else {
        line("static UINT8 _remove_gold(UINT32 amount) {")
        indent++
        line("if (_gold < amount) return 0u;")
        line("_gold -= amount;")
        line("return 1u;")
        indent--
        line("}")
    }
    line()

    line("// Check if can afford")
    if (economy != null) {
        line("static UINT8 _can_afford(UINT8 currency_idx, UINT32 amount) {")
        indent++
        line("return _currency[currency_idx] >= amount;")
        indent--
        line("}")
    } else {
        line("static UINT8 _can_afford_gold(UINT32 amount) {")
        indent++
        line("return _gold >= amount;")
        indent--
        line("}")
    }
    line()
}

/** Generate shop constants. */
private fun GBDKCodeGenerator.generateShopConstants() {
    line("// Shop constants")
    line("#define SHOP_COUNT ${game.shops.size}u")
    val maxListings = game.shops.maxOfOrNull { it.listings.size } ?: 0
    line("#define MAX_SHOP_LISTINGS ${maxListings}u")
    val maxServices = game.shops.maxOfOrNull { it.services.size } ?: 0
    line("#define MAX_SHOP_SERVICES ${maxServices}u")
    line()

    line("// Shop type constants")
    ShopType.entries.forEachIndexed { index, type ->
        line("#define SHOP_TYPE_${type.name} ${index}u")
    }
    line()

    line("// Shop indices")
    for (shop in game.shops) {
        line("#define SHOP_${shop.id.uppercase()} ${shop.shopIndex}u")
    }
    line()
}

/** Generate shop data structures. */
private fun GBDKCodeGenerator.generateShopData() {
    line("// Shop listing data")
    line("typedef struct {")
    indent++
    line("UINT8 item_id;      // Item index")
    line("UINT16 buy_price;   // Buy price")
    line("UINT16 sell_price;  // Sell price")
    line("INT8 stock;         // Current stock (-1 = unlimited)")
    line("INT8 max_stock;     // Max stock for restocking")
    line("UINT8 level_req;    // Level required to see item")
    line("UINT8 currency_id;  // Currency type (0 = default)")
    line("UINT8 available;    // Is item available?")
    indent--
    line("} ShopListing;")
    line()

    line("// Shop service data")
    line("typedef struct {")
    indent++
    line("UINT16 price;       // Service price")
    line("UINT8 currency_id;  // Currency type")
    indent--
    line("} ShopService;")
    line()

    // Generate listing arrays for each shop
    line("// Shop listings")
    for (shop in game.shops) {
        val shopPrefix = shop.id.lowercase()
        line(
            "static ShopListing _${shopPrefix}_listings[${shop.listings.size.coerceAtLeast(1)}] = {"
        )
        indent++
        for (listing in shop.listings) {
            val itemIdx = game.items.find { it.id == listing.itemId }?.itemIndex ?: 0
            val currencyIdx =
                listing.currencyId?.let { id ->
                    game.economy?.currencies?.find { it.id == id }?.currencyIndex ?: 0
                } ?: 0
            line(
                "{ ${itemIdx}u, ${listing.buyPrice}u, ${listing.sellPrice}u, ${listing.stock}, ${listing.stock}, ${listing.levelRequired}u, ${currencyIdx}u, ${if (listing.available) 1 else 0}u },"
            )
        }
        if (shop.listings.isEmpty()) {
            line("{ 0u, 0u, 0u, 0, 0, 0u, 0u, 0u },")
        }
        indent--
        line("};")
    }
    line()

    // Generate services arrays for each shop
    if (game.shops.any { it.services.isNotEmpty() }) {
        line("// Shop services")
        for (shop in game.shops) {
            if (shop.services.isNotEmpty()) {
                val shopPrefix = shop.id.lowercase()
                line("static ShopService _${shopPrefix}_services[${shop.services.size}] = {")
                indent++
                for (service in shop.services) {
                    val currencyIdx =
                        service.currencyId?.let { id ->
                            game.economy?.currencies?.find { it.id == id }?.currencyIndex ?: 0
                        } ?: 0
                    line("{ ${service.price}u, ${currencyIdx}u },")
                }
                indent--
                line("};")
            }
        }
        line()
    }

    // Shop metadata
    line("// Shop metadata")
    line("static const UINT8 _shop_type[SHOP_COUNT] = {")
    indent++
    for (shop in game.shops) {
        line("SHOP_TYPE_${shop.shopType.name}, // ${shop.id}")
    }
    indent--
    line("};")
    line()

    line("static const UINT8 _shop_listing_count[SHOP_COUNT] = {")
    indent++
    for (shop in game.shops) {
        line("${shop.listings.size}u, // ${shop.id}")
    }
    indent--
    line("};")
    line()

    line("static const UINT8 _shop_service_count[SHOP_COUNT] = {")
    indent++
    for (shop in game.shops) {
        line("${shop.services.size}u, // ${shop.id}")
    }
    indent--
    line("};")
    line()

    line("static const UINT8 _shop_buyback_rate[SHOP_COUNT] = {")
    indent++
    for (shop in game.shops) {
        line("${shop.buyBackRate}u, // ${shop.id}")
    }
    indent--
    line("};")
    line()

    line("static const UINT8 _shop_price_modifier[SHOP_COUNT] = {")
    indent++
    for (shop in game.shops) {
        line("${shop.priceModifier}u, // ${shop.id}")
    }
    indent--
    line("};")
    line()

    // Current shop state
    line("// Current shop state")
    line("static UINT8 _current_shop = 0u;")
    line("static UINT8 _shop_cursor = 0u;")
    line()
}

/** Generate shop management functions. */
private fun GBDKCodeGenerator.generateShopFunctions() {
    line("// =============================================================================")
    line("// SHOP FUNCTIONS")
    line("// =============================================================================")
    line()

    // Generate per-shop callbacks
    for (shop in game.shops) {
        val prefix = shop.id.lowercase()

        if (shop.onEnterStatements.isNotEmpty()) {
            line("// On enter callback for ${shop.id}")
            line("static void _shop_${prefix}_on_enter(void) {")
            indent++
            for (stmt in shop.onEnterStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (shop.onExitStatements.isNotEmpty()) {
            line("// On exit callback for ${shop.id}")
            line("static void _shop_${prefix}_on_exit(void) {")
            indent++
            for (stmt in shop.onExitStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (shop.onPurchaseStatements.isNotEmpty()) {
            line("// On purchase callback for ${shop.id}")
            line("static void _shop_${prefix}_on_purchase(void) {")
            indent++
            for (stmt in shop.onPurchaseStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        if (shop.onSellStatements.isNotEmpty()) {
            line("// On sell callback for ${shop.id}")
            line("static void _shop_${prefix}_on_sell(void) {")
            indent++
            for (stmt in shop.onSellStatements) {
                generateStatement(stmt)
            }
            indent--
            line("}")
            line()
        }

        // Generate service callbacks
        for ((serviceIdx, service) in shop.services.withIndex()) {
            if (service.onPurchaseStatements.isNotEmpty()) {
                line("// Service callback: ${shop.id}/${service.id}")
                line("static void _shop_${prefix}_service${serviceIdx}(void) {")
                indent++
                for (stmt in service.onPurchaseStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("}")
                line()
            }
        }
    }

    // Enter shop function
    line("// Enter a shop")
    line("static void _enter_shop(UINT8 shop_idx) {")
    indent++
    line("_current_shop = shop_idx;")
    line("_shop_cursor = 0u;")
    line()
    line("// Call shop-specific enter callback")
    line("switch (shop_idx) {")
    indent++
    for (shop in game.shops) {
        if (shop.onEnterStatements.isNotEmpty()) {
            line(
                "case SHOP_${shop.id.uppercase()}: _shop_${shop.id.lowercase()}_on_enter(); break;"
            )
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Exit shop function
    line("// Exit current shop")
    line("static void _exit_shop(void) {")
    indent++
    line("// Call shop-specific exit callback")
    line("switch (_current_shop) {")
    indent++
    for (shop in game.shops) {
        if (shop.onExitStatements.isNotEmpty()) {
            line("case SHOP_${shop.id.uppercase()}: _shop_${shop.id.lowercase()}_on_exit(); break;")
        }
    }
    line("default: break;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Get listing pointer function
    line("// Get listings for a shop")
    line("static ShopListing* _get_shop_listings(UINT8 shop_idx) {")
    indent++
    line("switch (shop_idx) {")
    indent++
    for (shop in game.shops) {
        line("case SHOP_${shop.id.uppercase()}: return _${shop.id.lowercase()}_listings;")
    }
    line("default: return (ShopListing*)0;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Calculate adjusted price function
    line("// Calculate adjusted buy price")
    line("static UINT16 _get_buy_price(UINT8 shop_idx, UINT8 listing_idx) {")
    indent++
    line("ShopListing* listings = _get_shop_listings(shop_idx);")
    line("UINT16 base_price = listings[listing_idx].buy_price;")
    line("UINT8 modifier = _shop_price_modifier[shop_idx];")
    line("return (UINT16)((base_price * modifier) / 100u);")
    indent--
    line("}")
    line()

    // Calculate sell price function
    line("// Calculate sell price (what shop pays for item)")
    line("static UINT16 _get_sell_price(UINT8 shop_idx, UINT8 item_idx) {")
    indent++
    if (game.items.isNotEmpty()) {
        line("UINT16 base_price;")
        line("UINT8 buyback_rate;")
        line()
        line("// Get item's base sell price from item data")
        line("base_price = item_data[item_idx].sell_price_lo |")
        line("             ((UINT16)item_data[item_idx].sell_price_hi << 8);")
        line()
        line("// If no explicit sell price, use buy price as base")
        line("if (base_price == 0u) {")
        indent++
        line("base_price = item_data[item_idx].buy_price_lo |")
        line("             ((UINT16)item_data[item_idx].buy_price_hi << 8);")
        indent--
        line("}")
        line()
        line("// Apply shop's buyback rate")
        line("buyback_rate = _shop_buyback_rate[shop_idx];")
        line("return (UINT16)((base_price * buyback_rate) / 100u);")
    } else {
        line("// No items defined - return 0")
        line("(void)shop_idx;")
        line("(void)item_idx;")
        line("return 0u;")
    }
    indent--
    line("}")
    line()

    // Buy item function
    line("// Buy an item from the shop")
    line("static UINT8 _shop_buy_item(UINT8 shop_idx, UINT8 listing_idx, UINT8 quantity) {")
    indent++
    line("ShopListing* listings = _get_shop_listings(shop_idx);")
    line("ShopListing* listing = &listings[listing_idx];")
    line("UINT16 price;")
    line("UINT8 currency_idx;")
    line()
    line("// Check if available")
    line("if (!listing->available) return 0u;")
    line()
    line("// Check stock")
    line("if (listing->stock >= 0 && listing->stock < (INT8)quantity) return 0u;")
    line()
    line("// Calculate total price")
    line("price = _get_buy_price(shop_idx, listing_idx) * quantity;")
    line("currency_idx = listing->currency_id;")
    line()
    line("// Check if can afford")
    if (game.economy != null) {
        line("if (!_can_afford(currency_idx, price)) return 0u;")
    } else {
        line("if (!_can_afford_gold(price)) return 0u;")
    }
    line()
    line("// Deduct money")
    if (game.economy != null) {
        line("_remove_currency(currency_idx, price);")
    } else {
        line("_remove_gold(price);")
    }
    line()
    line("// Add item to inventory")
    if (game.inventories.isNotEmpty()) {
        line("_inventory_add(0u, listing->item_id, quantity);")
    } else {
        line("// No inventory system configured - item is consumed immediately")
        line("// (e.g., service shop, or game doesn't track items)")
        line("(void)quantity;")
    }
    line()
    line("// Update stock")
    line("if (listing->stock >= 0) {")
    indent++
    line("listing->stock -= (INT8)quantity;")
    indent--
    line("}")
    line()
    line("// Call purchase callback")
    line("switch (shop_idx) {")
    indent++
    for (shop in game.shops) {
        if (shop.onPurchaseStatements.isNotEmpty()) {
            line(
                "case SHOP_${shop.id.uppercase()}: _shop_${shop.id.lowercase()}_on_purchase(); break;"
            )
        }
    }
    line("default: break;")
    indent--
    line("}")
    line()
    line("return 1u;")
    indent--
    line("}")
    line()

    // Sell item function (if any shop allows selling)
    if (game.shops.any { it.allowSelling }) {
        line("// Sell an item to the shop")
        line("static UINT8 _shop_sell_item(UINT8 shop_idx, UINT8 item_idx, UINT8 quantity) {")
        indent++
        line("UINT16 price = _get_sell_price(shop_idx, item_idx) * quantity;")
        line()
        line("// Remove item from inventory")
        if (game.inventories.isNotEmpty()) {
            line("if (!_inventory_remove(0u, item_idx, quantity)) return 0u;")
        } else {
            line("// No inventory system configured - cannot sell items")
            line("(void)item_idx; (void)quantity;")
            line("return 0u;")
        }
        line()
        line("// Add money")
        if (game.economy != null) {
            line("_add_currency(0u, price); // Default currency")
        } else {
            line("_add_gold(price);")
        }
        line()
        line("// Call sell callback")
        line("switch (shop_idx) {")
        indent++
        for (shop in game.shops) {
            if (shop.allowSelling && shop.onSellStatements.isNotEmpty()) {
                line(
                    "case SHOP_${shop.id.uppercase()}: _shop_${shop.id.lowercase()}_on_sell(); break;"
                )
            }
        }
        line("default: break;")
        indent--
        line("}")
        line()
        line("return 1u;")
        indent--
        line("}")
        line()
    }

    // Purchase service function (if any shop has services)
    if (game.shops.any { it.services.isNotEmpty() }) {
        line("// Purchase a service")
        line("static UINT8 _shop_buy_service(UINT8 shop_idx, UINT8 service_idx) {")
        indent++
        line("UINT16 price;")
        line("UINT8 currency_idx;")
        line()
        line("// Get service data and check affordability")
        line("switch (shop_idx) {")
        indent++
        for (shop in game.shops) {
            if (shop.services.isNotEmpty()) {
                val prefix = shop.id.lowercase()
                line("case SHOP_${shop.id.uppercase()}:")
                indent++
                line("if (service_idx >= ${shop.services.size}u) return 0u;")
                line("price = _${prefix}_services[service_idx].price;")
                line("currency_idx = _${prefix}_services[service_idx].currency_id;")
                line("break;")
                indent--
            }
        }
        line("default: return 0u;")
        indent--
        line("}")
        line()
        line("// Check if can afford")
        if (game.economy != null) {
            line("if (!_can_afford(currency_idx, price)) return 0u;")
        } else {
            line("if (!_can_afford_gold(price)) return 0u;")
        }
        line()
        line("// Deduct money")
        if (game.economy != null) {
            line("_remove_currency(currency_idx, price);")
        } else {
            line("_remove_gold(price);")
        }
        line()
        line("// Execute service callback")
        generateServiceCallbackSwitch()
        line()
        line("return 1u;")
        indent--
        line("}")
        line()
    }

    // Get current shop info
    line("// Get current shop index")
    line("static UINT8 _get_current_shop(void) {")
    indent++
    line("return _current_shop;")
    indent--
    line("}")
    line()

    line("// Get shop listing count")
    line("static UINT8 _get_shop_listing_count(UINT8 shop_idx) {")
    indent++
    line("return _shop_listing_count[shop_idx];")
    indent--
    line("}")
    line()
}

/** Generate service callback switch statement. */
private fun GBDKCodeGenerator.generateServiceCallbackSwitch() {
    val shopsWithServices =
        game.shops.filter { it.services.any { s -> s.onPurchaseStatements.isNotEmpty() } }

    if (shopsWithServices.isEmpty()) {
        line("// No service callbacks defined")
        line("(void)service_idx;")
        return
    }

    line("switch (shop_idx) {")
    indent++
    for (shop in shopsWithServices) {
        line("case SHOP_${shop.id.uppercase()}:")
        indent++
        line("switch (service_idx) {")
        indent++
        for ((idx, service) in shop.services.withIndex()) {
            if (service.onPurchaseStatements.isNotEmpty()) {
                line("case ${idx}u: _shop_${shop.id.lowercase()}_service$idx(); break;")
            }
        }
        line("default: break;")
        indent--
        line("}")
        line("break;")
        indent--
    }
    line("default: break;")
    indent--
    line("}")
}

// =============================================================================
// SHOP EXPRESSION GENERATION
// =============================================================================

/**
 * Generate shop-related expressions.
 *
 * @return the C expression string, or null if not a shop expression
 */
internal fun GBDKCodeGenerator.generateShopExpr(
    @Suppress("UNUSED_PARAMETER") expr: io.github.gbkt.core.ir.IRExpression
): String? {
    // Shop expressions would be added here when IR nodes are created
    return null
}

// =============================================================================
// SHOP STATEMENT GENERATION
// =============================================================================

/**
 * Handle shop-related IR statements.
 *
 * @return true if this was a shop statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateShopStatement(
    @Suppress("UNUSED_PARAMETER") stmt: io.github.gbkt.core.ir.IRStatement
): Boolean {
    // Shop statements would be added here when IR nodes are created
    return false
}
