/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.BuffEffect
import io.github.gbkt.core.ir.ContainerIR
import io.github.gbkt.core.ir.DropEntryIR
import io.github.gbkt.core.ir.DropTableIR
import io.github.gbkt.core.ir.HealEffect
import io.github.gbkt.core.ir.ItemCategoryDef
import io.github.gbkt.core.ir.ItemDef
import io.github.gbkt.core.ir.ItemEffectIR
import io.github.gbkt.core.ir.ScriptEffect
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// TYPED REFERENCES
// =============================================================================

/**
 * Typed reference to a registered item definition.
 *
 * Returned by [ItemCatalogBuilder.item] and [ItemCatalogBuilder.item] delegate for use in drop
 * tables and inventory script ops without raw string IDs.
 */
data class ItemRef(val id: String)

/**
 * Typed reference to a registered inventory container.
 *
 * Returned by [GameBuilder.container] for use in inventory script ops.
 */
data class ContainerRef(val id: String)

// =============================================================================
// ITEM CATEGORY BUILDER
// =============================================================================

/**
 * Builder for an item category definition with shared default stacking rules.
 *
 * Usage (inside [ItemCatalogBuilder]):
 * ```kotlin
 * items {
 *     val consumable by category { defaultMaxStack(10) }
 *     val keyItem by category { defaultMaxStack(1) }
 * }
 * ```
 */
@GbktDsl
class ItemCategoryBuilder(val id: String) {
    private var _defaultMaxStack: Int = 1

    /**
     * Sets the default maximum stack size for all items in this category.
     *
     * Individual items can override this with [ItemBuilder.maxStack].
     */
    fun defaultMaxStack(n: Int) {
        _defaultMaxStack = n
    }

    internal fun build(): ItemCategoryDef =
        ItemCategoryDef(id = id, defaultMaxStack = _defaultMaxStack)
}

// =============================================================================
// ITEM EFFECT BUILDER
// =============================================================================

/**
 * Builder for item effects applied when an item is used.
 *
 * Collects a list of [ItemEffectIR] entries for [ItemBuilder.onUse].
 *
 * Usage:
 * ```kotlin
 * item("potion") {
 *     onUse {
 *         heal(50)
 *     }
 * }
 * item("elixir") {
 *     onUse {
 *         heal(100)
 *         buff("atk", amount = 5, duration = 3)
 *     }
 * }
 * ```
 */
@GbktDsl
class ItemEffectBuilder {
    internal val effects = mutableListOf<ItemEffectIR>()

    /** Adds a [HealEffect] that restores [amount] HP when the item is used. */
    fun heal(amount: Int) {
        effects += HealEffect(amount)
    }

    /**
     * Adds a [BuffEffect] that temporarily boosts [statId] by [amount] for [duration] turns.
     *
     * @param statId ID of the stat to buff (e.g., "atk", "def", "agl").
     * @param amount Flat amount added to the stat.
     * @param duration Number of turns the buff lasts.
     */
    fun buff(statId: String, amount: Int, duration: Int) {
        effects += BuffEffect(statId = statId, amount = amount, duration = duration)
    }

    /**
     * Adds a [ScriptEffect] escape-hatch that runs arbitrary script ops when the item is used.
     *
     * Use for effects that don't fit [heal] or [buff], such as teleportation, flag setting, or
     * complex multi-step game logic.
     */
    fun script(block: ScriptBuilder.() -> Unit) {
        effects += ScriptEffect(ops = recordStatements(block))
    }
}

// =============================================================================
// ITEM BUILDER
// =============================================================================

/**
 * Builder for a single item definition in the item catalog.
 *
 * Usage (inside [ItemCatalogBuilder]):
 * ```kotlin
 * val potion by item {
 *     name("Potion")
 *     category(consumable)  // string or category ID
 *     onUse { heal(50) }
 *     buyPrice(50)
 * }
 * ```
 */
@GbktDsl
class ItemBuilder(val id: String) {
    private var _name: String = id
    private var _categoryId: String = ""
    private var _maxStack: Int? = null
    private var _buyPrice: Int = 0
    private var _dropWeight: Int = 0
    private var _effectsBlock: (ItemEffectBuilder.() -> Unit)? = null

    /** Sets the display name for this item. */
    fun name(n: String) {
        _name = n
    }

    /** Sets the category ID for this item (references an [ItemCategoryDef.id]). */
    fun category(c: String) {
        _categoryId = c
    }

    /**
     * Overrides the category's default max stack size for this specific item.
     *
     * When not called, the item inherits [ItemCategoryDef.defaultMaxStack].
     */
    fun maxStack(n: Int) {
        _maxStack = n
    }

    /** Sets the in-game currency cost when purchased from a shop. 0 = not for sale. */
    fun buyPrice(p: Int) {
        _buyPrice = p
    }

    /** Sets the relative drop weight for random drop tables. 0 = never dropped randomly. */
    fun dropWeight(w: Int) {
        _dropWeight = w
    }

    /** Registers the use effect block. Called when the player uses this item from inventory. */
    fun onUse(block: ItemEffectBuilder.() -> Unit) {
        _effectsBlock = block
    }

    internal fun build(): ItemDef {
        val effectsBuilt =
            _effectsBlock?.let { block -> ItemEffectBuilder().apply(block).effects.toList() }
                ?: emptyList()

        return ItemDef(
            id = id,
            name = _name,
            categoryId = _categoryId,
            maxStack = _maxStack,
            effects = effectsBuilt,
            buyPrice = _buyPrice,
            dropWeight = _dropWeight,
        )
    }
}

// =============================================================================
// ITEM CATALOG BUILDER
// =============================================================================

/**
 * Builder for the complete item catalog (categories + items).
 *
 * Used inside [GameBuilder.items] to define all item categories and item definitions.
 *
 * Supports two syntaxes for categories and items:
 * - Named delegate: `val consumable by category { defaultMaxStack(10) }` — infers ID from property
 * - Explicit ID: `category("consumable") { defaultMaxStack(10) }` — explicit ID string
 *
 * Usage:
 * ```kotlin
 * items {
 *     val consumable by category { defaultMaxStack(10) }
 *     val potion by item { name("Potion"); category(consumable); buyPrice(50); onUse { heal(50) } }
 * }
 * ```
 */
@GbktDsl
class ItemCatalogBuilder {
    internal val categories = mutableListOf<ItemCategoryDef>()
    internal val items = mutableListOf<ItemDef>()

    /**
     * Defines an item category with an explicit [id].
     *
     * @return The category ID string for use in [ItemBuilder.category].
     */
    fun category(id: String, block: ItemCategoryBuilder.() -> Unit): String {
        categories += ItemCategoryBuilder(id).apply(block).build()
        return id
    }

    /**
     * Property delegate factory for defining an item category with ID inferred from property name.
     *
     * Usage: `val consumable by category { defaultMaxStack(10) }`
     *
     * @return A delegate provider that captures the property name and registers the category.
     */
    fun category(
        block: ItemCategoryBuilder.() -> Unit
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, String>> =
        PropertyDelegateProvider { _, property ->
            val id = property.name
            categories += ItemCategoryBuilder(id).apply(block).build()
            ReadOnlyProperty { _, _ -> id }
        }

    /**
     * Defines an item with an explicit [id].
     *
     * @return [ItemRef] for use in drop tables and inventory operations.
     */
    fun item(id: String, block: ItemBuilder.() -> Unit): ItemRef {
        items += ItemBuilder(id).apply(block).build()
        return ItemRef(id)
    }

    /**
     * Property delegate factory for defining an item with ID inferred from property name.
     *
     * Usage: `val potion by item { name("Potion"); category(consumable) }`
     *
     * @return A delegate provider that captures the property name and registers the item.
     */
    fun item(
        block: ItemBuilder.() -> Unit
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ItemRef>> =
        PropertyDelegateProvider { _, property ->
            val id = property.name
            items += ItemBuilder(id).apply(block).build()
            ReadOnlyProperty { _, _ -> ItemRef(id) }
        }
}

// =============================================================================
// CONTAINER BUILDER
// =============================================================================

/**
 * Builder for an inventory container (bag, chest, etc.) with a fixed slot count.
 *
 * Usage:
 * ```kotlin
 * val bag by container { slots(16) }
 * val equipSlots by container { slots(6); categoryFilter("EQUIPMENT") }
 * ```
 */
@GbktDsl
class ContainerBuilder(val id: String) {
    private var _slots: Int = 1
    private var _categoryFilter: String? = null

    /** Sets the maximum number of item stacks the container can hold. */
    fun slots(n: Int) {
        _slots = n
    }

    /**
     * Restricts the container to items from the given category ID.
     *
     * When not called, the container accepts items from all categories.
     */
    fun categoryFilter(c: String) {
        _categoryFilter = c
    }

    internal fun build(): ContainerIR =
        ContainerIR(id = id, slots = _slots, categoryFilter = _categoryFilter)
}

// =============================================================================
// CONTAINER DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers a container's ID from the Kotlin property and registers it with the
 * current [GameBuilder].
 *
 * Mirrors the [ActorDelegate] pattern so that `val bag by container { ... }` syntax works.
 *
 * Usage:
 * ```kotlin
 * val bag by container { slots(16) }
 * val equipSlots by container { slots(6); categoryFilter("EQUIPMENT") }
 * ```
 *
 * @param id When empty, the property name is used as the container ID.
 * @param block The container configuration block.
 */
class ContainerDelegate(private val id: String, private val block: ContainerBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, ContainerRef> {
    private var ref: ContainerRef? = null

    /**
     * Called by Kotlin when `val x by container { ... }` is evaluated.
     *
     * Captures the property name, calls [GameBuilder.container], and stores the resulting
     * [ContainerRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: GameBuilder,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ContainerRef> {
        val resolvedId = id.ifEmpty { property.name }
        ref = thisRef.container(resolvedId, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ContainerRef =
        ref ?: error("ContainerDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// DROP TABLE BUILDER
// =============================================================================

/**
 * Builder for a drop/loot table with weighted random item selection.
 *
 * Usage:
 * ```kotlin
 * dropTable("goblin_drops") {
 *     drop("potion", weight = 60)
 *     drop("iron_key", weight = 10, minCount = 1, maxCount = 1)
 *     drop("gold_coin", weight = 30, minCount = 1, maxCount = 3)
 * }
 * ```
 */
@GbktDsl
class DropTableBuilder(val id: String) {
    private val _entries = mutableListOf<DropEntryIR>()

    /**
     * Adds a weighted drop entry.
     *
     * @param itemId References the [ItemDef.id] to drop.
     * @param weight Relative drop weight — higher values mean more common drops.
     * @param minCount Minimum number of items dropped per selection.
     * @param maxCount Maximum number of items dropped per selection.
     */
    fun drop(itemId: String, weight: Int, minCount: Int = 1, maxCount: Int = 1) {
        _entries +=
            DropEntryIR(itemId = itemId, weight = weight, minCount = minCount, maxCount = maxCount)
    }

    internal fun build(): DropTableIR = DropTableIR(id = id, entries = _entries.toList())
}
