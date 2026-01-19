/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// SHOP/ECONOMY FRAMEWORK
// =============================================================================

/** Shop type for different kinds of stores. */
enum class ShopType {
    /** General store with mixed items */
    GENERAL,

    /** Weapons and armor shop */
    EQUIPMENT,

    /** Magic shop (scrolls, spells) */
    MAGIC,

    /** Item/consumables shop */
    ITEM,

    /** Inn/rest services */
    INN,

    /** Blacksmith (upgrades, repairs) */
    BLACKSMITH,

    /** Special/unique items */
    SPECIAL,

    /** Black market (rare/illegal) */
    BLACK_MARKET,
}

/** Currency type for multi-currency systems. */
data class CurrencyType(
    /** Unique currency identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Maximum amount player can hold */
    val maxAmount: Int = 999999,
    /** Currency index for code generation */
    var currencyIndex: Int = -1,
)

/** Shop listing - an item available for purchase. */
data class ShopListing(
    /** Item reference */
    val itemId: String,
    /** Buy price (what player pays) */
    val buyPrice: Int,
    /** Sell price (what shop pays for this item) */
    val sellPrice: Int,
    /** Maximum quantity in stock (-1 = unlimited) */
    val stock: Int,
    /** Whether this item restocks over time */
    val restocks: Boolean,
    /** Restock interval in frames (0 = instant) */
    val restockInterval: Int,
    /** Minimum reputation/level required to see this item */
    val levelRequired: Int,
    /** Currency type (null = default gold) */
    val currencyId: String?,
    /** Whether item is currently available */
    val available: Boolean,
)

/** Shop service - non-item services like rest, repair, etc. */
data class ShopService(
    /** Service identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Price for this service */
    val price: Int,
    /** Currency type (null = default gold) */
    val currencyId: String?,
    /** Callback when service is purchased */
    val onPurchaseStatements: List<IRStatement>,
)

/**
 * Shop definition.
 *
 * Represents a store where players can buy and sell items.
 *
 * Usage:
 * ```kotlin
 * val weaponShop by shop {
 *     name("Armory")
 *     type(ShopType.EQUIPMENT)
 *     greeting("Welcome to the Armory!")
 *
 *     sell(ironSword) { price(100); stock(5) }
 *     sell(steelSword) { price(500); stock(3); levelRequired(5) }
 *     sell(potion) { price(50); unlimited() }
 *
 *     buyBackRate(50)  // Shop buys items at 50% of sell price
 *
 *     service("sharpen") {
 *         name("Sharpen Weapon")
 *         price(25)
 *         onPurchase { increaseWeaponDamage(5) }
 *     }
 *
 *     onEnter { playShopMusic() }
 *     onExit { stopShopMusic() }
 * }
 * ```
 */
data class Shop(
    /** Unique shop identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Shop type */
    val shopType: ShopType,
    /** Greeting message */
    val greeting: String,
    /** Farewell message */
    val farewell: String,
    /** Items for sale */
    val listings: List<ShopListing>,
    /** Services offered */
    val services: List<ShopService>,
    /** Buy-back rate as percentage (0-100) */
    val buyBackRate: Int,
    /** Whether shop buys items from player */
    val allowSelling: Boolean,
    /** Whether prices fluctuate based on reputation/events */
    val dynamicPricing: Boolean,
    /** Price modifier percentage (100 = normal, 150 = 50% more expensive) */
    val priceModifier: Int,
    /** Callback when entering shop */
    val onEnterStatements: List<IRStatement>,
    /** Callback when leaving shop */
    val onExitStatements: List<IRStatement>,
    /** Callback when purchasing an item */
    val onPurchaseStatements: List<IRStatement>,
    /** Callback when selling an item */
    val onSellStatements: List<IRStatement>,
    /** System index for code generation */
    var shopIndex: Int = -1,
)

// =============================================================================
// SHOP BUILDER
// =============================================================================

/** Property delegate for shops. */
class ShopDelegate(private val gameBuilder: GameBuilder, private val init: ShopBuilder.() -> Unit) :
    PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Shop>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Shop> {
        val builder = ShopBuilder(property.name)
        builder.init()
        val shop = builder.build()
        gameBuilder.registerShop(shop)

        return ReadOnlyProperty { _, _ -> shop }
    }
}

/** Builder for shops. */
@GbktDsl
class ShopBuilder(private val shopId: String) {
    private var displayName: String = shopId.replaceFirstChar { it.uppercaseChar() }
    private var shopType: ShopType = ShopType.GENERAL
    private var greeting: String = "Welcome!"
    private var farewell: String = "Come again!"
    private val listings = mutableListOf<ShopListing>()
    private val services = mutableListOf<ShopService>()
    private var buyBackRate: Int = 50
    private var allowSelling: Boolean = true
    private var dynamicPricing: Boolean = false
    private var priceModifier: Int = 100
    private var onEnterStatements: List<IRStatement> = emptyList()
    private var onExitStatements: List<IRStatement> = emptyList()
    private var onPurchaseStatements: List<IRStatement> = emptyList()
    private var onSellStatements: List<IRStatement> = emptyList()

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set shop type */
    fun type(type: ShopType) {
        shopType = type
    }

    /** Set greeting message */
    fun greeting(message: String) {
        greeting = message
    }

    /** Set farewell message */
    fun farewell(message: String) {
        farewell = message
    }

    /** Add an item for sale */
    fun sell(item: Item, init: ListingBuilder.() -> Unit = {}) {
        val builder = ListingBuilder(item.id)
        builder.price(item.buyPrice) // Default to item's base price
        builder.init()
        listings.add(builder.build())
    }

    /** Add an item for sale by ID */
    fun sell(itemId: String, init: ListingBuilder.() -> Unit) {
        val builder = ListingBuilder(itemId)
        builder.init()
        listings.add(builder.build())
    }

    /** Add a service */
    fun service(id: String, init: ServiceBuilder.() -> Unit) {
        val builder = ServiceBuilder(id)
        builder.init()
        services.add(builder.build())
    }

    /** Set buy-back rate as percentage (0-100) */
    fun buyBackRate(percent: Int) {
        require(percent in 0..100) { "Buy-back rate must be 0-100%" }
        buyBackRate = percent
    }

    /** Disable selling items to this shop */
    fun noSelling() {
        allowSelling = false
    }

    /** Enable dynamic pricing based on reputation/events */
    fun dynamicPricing(enabled: Boolean = true) {
        dynamicPricing = enabled
    }

    /** Set price modifier (100 = normal, 150 = 50% markup) */
    fun priceModifier(percent: Int) {
        priceModifier = percent
    }

    /** Make this shop 50% cheaper */
    fun discount() = priceModifier(50)

    /** Make this shop 50% more expensive */
    fun markup() = priceModifier(150)

    /** Callback when entering shop */
    fun onEnter(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onEnterStatements = recorder.statements
    }

    /** Callback when leaving shop */
    fun onExit(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onExitStatements = recorder.statements
    }

    /** Callback when purchasing an item */
    fun onPurchase(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onPurchaseStatements = recorder.statements
    }

    /** Callback when selling an item */
    fun onSell(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onSellStatements = recorder.statements
    }

    internal fun build() =
        Shop(
            id = shopId,
            displayName = displayName,
            shopType = shopType,
            greeting = greeting,
            farewell = farewell,
            listings = listings.toList(),
            services = services.toList(),
            buyBackRate = buyBackRate,
            allowSelling = allowSelling,
            dynamicPricing = dynamicPricing,
            priceModifier = priceModifier,
            onEnterStatements = onEnterStatements,
            onExitStatements = onExitStatements,
            onPurchaseStatements = onPurchaseStatements,
            onSellStatements = onSellStatements,
        )
}

/** Builder for shop listings. */
@GbktDsl
class ListingBuilder(private val itemId: String) {
    private var buyPrice: Int = 0
    private var sellPrice: Int = -1 // -1 means use buyPrice * buyBackRate
    private var stock: Int = -1 // -1 = unlimited
    private var restocks: Boolean = false
    private var restockInterval: Int = 0
    private var levelRequired: Int = 0
    private var currencyId: String? = null
    private var available: Boolean = true

    /** Set buy price */
    fun price(amount: Int) {
        buyPrice = amount
    }

    /** Set sell price (what shop pays when buying from player) */
    fun sellPrice(amount: Int) {
        sellPrice = amount
    }

    /** Set stock quantity */
    fun stock(quantity: Int) {
        stock = quantity
    }

    /** Unlimited stock */
    fun unlimited() {
        stock = -1
    }

    /** Item restocks over time */
    fun restocks(interval: Int = 0) {
        restocks = true
        restockInterval = interval
    }

    /** Level/reputation required to see this item */
    fun levelRequired(level: Int) {
        levelRequired = level
    }

    /** Use alternate currency */
    fun currency(currencyId: String) {
        this.currencyId = currencyId
    }

    /** Mark item as unavailable */
    fun unavailable() {
        available = false
    }

    internal fun build() =
        ShopListing(
            itemId = itemId,
            buyPrice = buyPrice,
            sellPrice = if (sellPrice >= 0) sellPrice else buyPrice / 2,
            stock = stock,
            restocks = restocks,
            restockInterval = restockInterval,
            levelRequired = levelRequired,
            currencyId = currencyId,
            available = available,
        )
}

/** Builder for shop services. */
@GbktDsl
class ServiceBuilder(private val serviceId: String) {
    private var displayName: String = serviceId.replaceFirstChar { it.uppercaseChar() }
    private var price: Int = 0
    private var currencyId: String? = null
    private var onPurchaseStatements: List<IRStatement> = emptyList()

    /** Set service name */
    fun name(name: String) {
        displayName = name
    }

    /** Set price */
    fun price(amount: Int) {
        price = amount
    }

    /** Use alternate currency */
    fun currency(currencyId: String) {
        this.currencyId = currencyId
    }

    /** Callback when service is purchased */
    fun onPurchase(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onPurchaseStatements = recorder.statements
    }

    internal fun build() =
        ShopService(
            id = serviceId,
            displayName = displayName,
            price = price,
            currencyId = currencyId,
            onPurchaseStatements = onPurchaseStatements,
        )
}

// =============================================================================
// ECONOMY SYSTEM
// =============================================================================

/**
 * Economy configuration.
 *
 * Manages currencies, gold, and economy-wide settings.
 */
data class Economy(
    /** Default currency ID (usually "gold") */
    val defaultCurrencyId: String,
    /** All defined currencies */
    val currencies: List<CurrencyType>,
    /** Starting gold amount */
    val startingGold: Int,
    /** Maximum gold player can hold */
    val maxGold: Int,
    /** Global price modifier (100 = normal) */
    val globalPriceModifier: Int,
    /** System index for code generation */
    var economyIndex: Int = -1,
)

/** Builder for economy configuration. */
@GbktDsl
class EconomyBuilder {
    private var defaultCurrencyId: String = "gold"
    private val currencies = mutableListOf<CurrencyType>()
    private var startingGold: Int = 0
    private var maxGold: Int = 999999
    private var globalPriceModifier: Int = 100

    init {
        // Default gold currency
        currencies.add(CurrencyType("gold", "Gold"))
    }

    /** Set starting gold */
    fun startingGold(amount: Int) {
        startingGold = amount
    }

    /** Set maximum gold */
    fun maxGold(amount: Int) {
        maxGold = amount
    }

    /** Define a custom currency */
    fun currency(id: String, init: CurrencyBuilder.() -> Unit): CurrencyType {
        val builder = CurrencyBuilder(id)
        builder.init()
        val currency = builder.build()
        currencies.add(currency)
        return currency
    }

    /** Set default currency */
    fun defaultCurrency(currencyId: String) {
        defaultCurrencyId = currencyId
    }

    /** Set global price modifier */
    fun priceModifier(percent: Int) {
        globalPriceModifier = percent
    }

    internal fun build() =
        Economy(
            defaultCurrencyId = defaultCurrencyId,
            currencies = currencies.toList(),
            startingGold = startingGold,
            maxGold = maxGold,
            globalPriceModifier = globalPriceModifier,
        )
}

/** Builder for currency types. */
@GbktDsl
class CurrencyBuilder(private val currencyId: String) {
    private var displayName: String = currencyId.replaceFirstChar { it.uppercaseChar() }
    private var maxAmount: Int = 999999

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set maximum amount */
    fun maxAmount(amount: Int) {
        maxAmount = amount
    }

    internal fun build() =
        CurrencyType(id = currencyId, displayName = displayName, maxAmount = maxAmount)
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a shop.
 *
 * Usage:
 * ```kotlin
 * val itemShop by shop {
 *     name("Item Shop")
 *     type(ShopType.ITEM)
 *     greeting("What can I get for you?")
 *
 *     sell(potion) { price(50) }
 *     sell(antidote) { price(30) }
 *     sell(phoenix_down) { price(500); stock(3) }
 *
 *     buyBackRate(40)  // Shop buys items at 40% value
 * }
 * ```
 */
fun GameBuilder.shop(init: ShopBuilder.() -> Unit): ShopDelegate {
    return ShopDelegate(this, init)
}

/**
 * Configure the economy system.
 *
 * Usage:
 * ```kotlin
 * economy {
 *     startingGold(100)
 *     maxGold(99999)
 *
 *     currency("gems") {
 *         name("Gems")
 *         maxAmount(999)
 *     }
 *
 *     defaultCurrency("gold")
 * }
 * ```
 */
fun GameBuilder.economy(init: EconomyBuilder.() -> Unit): Economy {
    val builder = EconomyBuilder()
    builder.init()
    val economy = builder.build()
    registerEconomy(economy)
    return economy
}
