/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.ItemRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RawCodeEscapeHatch
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ITEM SYSTEM
// =============================================================================

/** Categories for items in the inventory. */
enum class ItemCategory {
    /** Consumable items like potions and food */
    CONSUMABLE,
    /** Weapons that can be equipped */
    WEAPON,
    /** Armor and protective gear */
    ARMOR,
    /** Accessories (rings, amulets) */
    ACCESSORY,
    /** Key items for story progression (cannot be sold/dropped) */
    KEY_ITEM,
    /** Crafting materials */
    MATERIAL,
}

/**
 * Represents an equipment slot where items can be equipped.
 *
 * Equipment slots are extensible - use the built-in slots (WEAPON, ARMOR, ACCESSORY, ACCESSORY_2)
 * or define custom slots for your game:
 * ```kotlin
 * val ringSlot by equipmentSlot("Ring")
 * val bootsSlot by equipmentSlot("Boots")
 *
 * val magicRing by item {
 *     category(ItemCategory.ACCESSORY)
 *     equipSlot(ringSlot)
 *     statBonus { matk(5) }
 * }
 * ```
 *
 * @property id Unique numeric ID for this slot (auto-assigned or specified)
 * @property name Display name for the slot
 */
data class EquipmentSlot(val id: Int, val name: String) {
    companion object {
        /** Standard weapon slot */
        val WEAPON = EquipmentSlot(0, "Weapon")

        /** Standard armor slot */
        val ARMOR = EquipmentSlot(1, "Armor")

        /** Standard accessory slot */
        val ACCESSORY = EquipmentSlot(2, "Accessory")

        /** Secondary accessory slot */
        val ACCESSORY_2 = EquipmentSlot(3, "Accessory 2")

        /** All built-in slots */
        val BUILT_IN_SLOTS = listOf(WEAPON, ARMOR, ACCESSORY, ACCESSORY_2)

        /** Next available ID for custom slots */
        private var nextCustomId = 4

        /**
         * Create a custom equipment slot with auto-assigned ID.
         *
         * @param name Display name for the slot
         */
        internal fun createCustom(name: String): EquipmentSlot {
            return EquipmentSlot(nextCustomId++, name)
        }

        /** Reset custom slot ID counter (for testing). */
        internal fun resetCustomIdCounter() {
            nextCustomId = 4
        }
    }
}

// =============================================================================
// EQUIPMENT SLOT DSL
// =============================================================================

/**
 * Property delegate for custom equipment slots.
 *
 * Usage: val ringSlot by equipmentSlot("Ring")
 */
class EquipmentSlotDelegate(private val gameBuilder: GameBuilder, private val displayName: String) :
    PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, EquipmentSlot>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, EquipmentSlot> {
        val slot = EquipmentSlot.createCustom(displayName)
        gameBuilder.registerEquipmentSlot(slot)

        return ReadOnlyProperty { _, _ -> slot }
    }
}

/**
 * Create a custom equipment slot.
 *
 * Usage:
 * ```kotlin
 * val ringSlot by equipmentSlot("Ring")
 * val bootsSlot by equipmentSlot("Boots")
 * val glovesSlot by equipmentSlot("Gloves")
 * ```
 *
 * Custom slots get auto-assigned IDs starting from 4 (after the built-in slots). Items can then use
 * these slots:
 * ```kotlin
 * val magicRing by item {
 *     equipSlot(ringSlot)
 *     statBonus { matk(5) }
 * }
 * ```
 */
fun GameBuilder.equipmentSlot(displayName: String): EquipmentSlotDelegate {
    return EquipmentSlotDelegate(this, displayName)
}

/**
 * An item definition in the game.
 *
 * Items can be consumable (potions, food), equipment (weapons, armor), or key items (story
 * progression).
 *
 * Usage:
 * ```kotlin
 * val potion by item {
 *     name("Potion")
 *     description("Restores 50 HP")
 *     category(ItemCategory.CONSUMABLE)
 *     maxStack(99)
 *     onUse {
 *         target.hp += 50
 *     }
 * }
 *
 * val ironSword by item {
 *     name("Iron Sword")
 *     description("+10 ATK")
 *     category(ItemCategory.WEAPON)
 *     equipSlot(EquipmentSlot.WEAPON)
 *     statBonus { atk(10) }
 * }
 * ```
 */
class Item(
    /** Unique identifier for this item (from property name) */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Item description */
    val description: String,
    /** Item category */
    val category: ItemCategory,
    /** Maximum stack size (1 for equipment, typically 99 for consumables) */
    val maxStack: Int,
    /** Buy price (0 = cannot buy) */
    val buyPrice: Int,
    /** Sell price (0 = cannot sell) */
    val sellPrice: Int,
    /** Equipment slot for equippable items */
    val equipSlot: EquipmentSlot?,
    /** Stat bonuses when equipped */
    val statBonuses: Map<StatBonusType, Int>,
    /** IR statements to execute when item is used */
    val onUseStatements: List<IRStatement>,
    /** Whether this item is usable in battle */
    val usableInBattle: Boolean,
    /** Whether this item is usable outside battle */
    val usableOutOfBattle: Boolean,
    /** Item index for code generation (assigned by GameBuilder) */
    var itemIndex: Int = -1,
) {
    /** Type-safe reference to this item */
    val ref: ItemRef
        get() = ItemRef(id)

    /** Whether this item can be used (has onUse defined) */
    val isUsable: Boolean
        get() = onUseStatements.isNotEmpty()

    /** Whether this item can be equipped */
    val isEquippable: Boolean
        get() = equipSlot != null

    /** Whether this item is a key item (cannot be sold/dropped) */
    val isKeyItem: Boolean
        get() = category == ItemCategory.KEY_ITEM

    /** Whether this item can stack */
    val isStackable: Boolean
        get() = maxStack > 1
}

/** Types of stat bonuses that equipment can provide. */
enum class StatBonusType {
    ATK,
    DEF,
    MATK,
    MDEF,
    AGL,
    MAX_HP,
    MAX_SP,
    CRIT_RATE,
    EVASION,
}

// =============================================================================
// ITEM BUILDER
// =============================================================================

/**
 * Property delegate for items.
 *
 * Usage: val potion by item { ... }
 */
class ItemDelegate(private val gameBuilder: GameBuilder, private val init: ItemBuilder.() -> Unit) :
    PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Item>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Item> {
        val builder = ItemBuilder(property.name)
        builder.init()
        val item = builder.build()
        gameBuilder.registerItem(item)

        return ReadOnlyProperty { _, _ -> item }
    }
}

/** Builder for item construction via DSL. */
@GbktDsl
class ItemBuilder(private val itemId: String) {
    private var displayName: String = itemId.replaceFirstChar { it.uppercase() }
    private var description: String = ""
    private var category: ItemCategory = ItemCategory.CONSUMABLE
    private var maxStack: Int = 99
    private var buyPrice: Int = 0
    private var sellPrice: Int = 0
    private var equipSlot: EquipmentSlot? = null
    private val statBonuses = mutableMapOf<StatBonusType, Int>()
    private var onUseStatements: List<IRStatement> = emptyList()
    private var usableInBattle: Boolean = true
    private var usableOutOfBattle: Boolean = true

    /** Set the display name for this item. */
    fun name(name: String) {
        this.displayName = name
    }

    /** Set the item description. */
    fun description(desc: String) {
        this.description = desc
    }

    /** Set the item category. */
    fun category(cat: ItemCategory) {
        this.category = cat
        // Equipment items default to stack of 1
        if (cat in listOf(ItemCategory.WEAPON, ItemCategory.ARMOR, ItemCategory.ACCESSORY)) {
            maxStack = 1
            equipSlot =
                when (cat) {
                    ItemCategory.WEAPON -> EquipmentSlot.WEAPON
                    ItemCategory.ARMOR -> EquipmentSlot.ARMOR
                    ItemCategory.ACCESSORY -> EquipmentSlot.ACCESSORY
                    else -> null
                }
        }
        // Key items are not usable in battle by default
        if (cat == ItemCategory.KEY_ITEM) {
            usableInBattle = false
        }
    }

    /**
     * Set the maximum stack size.
     *
     * @param max Maximum quantity per inventory slot (1-99)
     */
    fun maxStack(max: Int) {
        require(max in 1..99) { "Max stack must be 1-99, got: $max" }
        this.maxStack = max
    }

    /**
     * Set buy and sell prices.
     *
     * @param buy Price to buy (0 = cannot buy)
     * @param sell Price to sell (0 = cannot sell, defaults to buy/2)
     */
    fun price(buy: Int, sell: Int = buy / 2) {
        require(buy >= 0) { "Buy price must be non-negative" }
        require(sell >= 0) { "Sell price must be non-negative" }
        this.buyPrice = buy
        this.sellPrice = sell
    }

    /** Set the equipment slot for equippable items. */
    fun equipSlot(slot: EquipmentSlot) {
        this.equipSlot = slot
        this.maxStack = 1 // Equipment doesn't stack
    }

    /**
     * Define stat bonuses when this item is equipped.
     *
     * Usage:
     * ```kotlin
     * statBonus {
     *     atk(10)
     *     def(5)
     * }
     * ```
     */
    fun statBonus(init: StatBonusBuilder.() -> Unit) {
        val builder = StatBonusBuilder()
        builder.init()
        statBonuses.putAll(builder.bonuses)
    }

    /**
     * Define what happens when this item is used.
     *
     * Usage:
     * ```kotlin
     * onUse {
     *     target.hp += 50
     * }
     * ```
     */
    fun onUse(block: ItemUseScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { ItemUseScope().block() }
        this.onUseStatements = recorder.statements
    }

    /** Set whether this item can be used in battle. */
    fun usableInBattle(usable: Boolean) {
        this.usableInBattle = usable
    }

    /** Set whether this item can be used outside battle. */
    fun usableOutOfBattle(usable: Boolean) {
        this.usableOutOfBattle = usable
    }

    internal fun build(): Item {
        return Item(
            id = itemId,
            displayName = displayName,
            description = description,
            category = category,
            maxStack = maxStack,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            equipSlot = equipSlot,
            statBonuses = statBonuses.toMap(),
            onUseStatements = onUseStatements,
            usableInBattle = usableInBattle,
            usableOutOfBattle = usableOutOfBattle,
        )
    }
}

/** Builder for stat bonuses on equipment. */
@GbktDsl
class StatBonusBuilder {
    internal val bonuses = mutableMapOf<StatBonusType, Int>()

    fun atk(value: Int) {
        bonuses[StatBonusType.ATK] = value
    }

    fun def(value: Int) {
        bonuses[StatBonusType.DEF] = value
    }

    fun matk(value: Int) {
        bonuses[StatBonusType.MATK] = value
    }

    fun mdef(value: Int) {
        bonuses[StatBonusType.MDEF] = value
    }

    fun agl(value: Int) {
        bonuses[StatBonusType.AGL] = value
    }

    fun maxHp(value: Int) {
        bonuses[StatBonusType.MAX_HP] = value
    }

    fun maxSp(value: Int) {
        bonuses[StatBonusType.MAX_SP] = value
    }
}

/**
 * Scope for item use callbacks. Provides access to target character for applying effects.
 *
 * Usage:
 * ```kotlin
 * onUse {
 *     target.hp += 50           // Heal 50 HP
 *     target.sp.restorePercent(25)  // Restore 25% SP
 *     heal(100)                 // Convenience method
 *     cure(poison)              // Remove status effect
 * }
 * ```
 */
@GbktDsl
class ItemUseScope {
    /**
     * Access to the target character receiving this item's effect. The target is determined at
     * runtime based on battle/menu selection. Uses `_item_target` as a placeholder resolved by
     * codegen.
     */
    val target: ItemTargetScope = ItemTargetScope()

    /**
     * Heal HP by a fixed amount.
     *
     * @param amount HP to restore (will be clamped to max)
     */
    fun heal(amount: Int) {
        require(amount > 0) { "Heal amount must be positive" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatModify(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.HP,
                    io.github.gbkt.core.ir.IRLiteral(amount),
                    io.github.gbkt.core.ir.AssignOp.ADD,
                )
            )
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatClamp(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.HP,
                )
            )
    }

    /**
     * Heal HP by percentage of max HP.
     *
     * @param percent Percentage of max HP to restore (1-100)
     */
    fun healPercent(percent: Int) {
        require(percent in 1..100) { "Heal percent must be 1-100" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatRestorePercent(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.HP,
                    percent,
                )
            )
    }

    /**
     * Restore SP by a fixed amount.
     *
     * @param amount SP to restore (will be clamped to max)
     */
    fun restoreSp(amount: Int) {
        require(amount > 0) { "Restore amount must be positive" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatModify(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.SP,
                    io.github.gbkt.core.ir.IRLiteral(amount),
                    io.github.gbkt.core.ir.AssignOp.ADD,
                )
            )
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatClamp(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.SP,
                )
            )
    }

    /**
     * Restore SP by percentage of max SP.
     *
     * @param percent Percentage of max SP to restore (1-100)
     */
    fun restoreSpPercent(percent: Int) {
        require(percent in 1..100) { "Restore percent must be 1-100" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatRestorePercent(
                    "_item_target",
                    io.github.gbkt.core.ir.StatType.SP,
                    percent,
                )
            )
    }

    /**
     * Cure a specific status effect from the target.
     *
     * @param effect The status effect to remove
     */
    fun cure(effect: StatusEffectDefinition) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRClearStatusEffect(
                    "_item_target",
                    effect.id.value,
                    effect.name,
                )
            )
    }

    /** Cure all status effects from the target. */
    fun cureAll() {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRClearAllStatusEffects("_item_target"))
    }

    /**
     * Apply a status effect to the target.
     *
     * @param effect The effect to apply
     * @param duration Optional override for effect duration
     */
    fun applyEffect(effect: StatusEffectDefinition, duration: EffectDuration? = null) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRApplyStatusEffect(
                    "_item_target",
                    effect.id.value,
                    effect.name,
                    duration ?: effect.baseDuration,
                )
            )
    }

    /** Raw C code escape hatch for custom effects. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }
}

/**
 * Provides DSL access to the item target's stats.
 *
 * Usage: `target.hp += 50`, `target.sp.restorePercent(25)`
 */
@GbktDsl
class ItemTargetScope {
    /** HP stat accessor - target.hp += 50 */
    val hp: ItemStatAccessor = ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.HP)

    /** SP stat accessor - target.sp += 10 */
    val sp: ItemStatAccessor = ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.SP)

    /** ATK stat accessor */
    val atk: ItemStatAccessor =
        ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.ATK)

    /** DEF stat accessor */
    val def: ItemStatAccessor =
        ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.DEF)

    /** MATK stat accessor */
    val matk: ItemStatAccessor =
        ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.MATK)

    /** MDEF stat accessor */
    val mdef: ItemStatAccessor =
        ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.MDEF)

    /** AGL stat accessor */
    val agl: ItemStatAccessor =
        ItemStatAccessor("_item_target", io.github.gbkt.core.ir.StatType.AGL)
}

/**
 * Stat accessor for item use effects.
 *
 * Supports: `+=`, `-=`, and `set()` operations. Implements [DamageCapableStatModifier] to share
 * behavior with other stat accessors.
 */
class ItemStatAccessor(
    private val ownerName: String,
    private val statType: io.github.gbkt.core.ir.StatType,
) : DamageCapableStatModifier {
    /** Add to stat value: target.hp += 50 */
    override operator fun plusAssign(value: Int) {
        StatOperations.emitAdd(ownerName, statType, value)
    }

    /** Subtract from stat value with floor at 0: target.hp -= 20 */
    override operator fun minusAssign(value: Int) {
        StatOperations.emitSubtract(ownerName, statType, value)
    }

    /** Set stat to exact value: target.hp set 100 */
    override infix fun set(value: Int) {
        StatOperations.emitSet(ownerName, statType, value)
    }

    /**
     * Restore percentage of max value.
     *
     * @param percent Percentage of max to restore (1-100)
     */
    fun restorePercent(percent: Int) {
        require(percent in 1..100) { "Percent must be 1-100" }
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(io.github.gbkt.core.ir.IRStatRestorePercent(ownerName, statType, percent))
        }
    }
}

// =============================================================================
// ITEM STACK - Inventory slot contents
// =============================================================================

/**
 * Represents a stack of items in an inventory slot.
 *
 * @property item The item type in this stack
 * @property quantity Number of items (1-99)
 */
data class ItemStack(val item: Item, val quantity: Int = 1) {
    init {
        require(quantity in 1..item.maxStack) {
            "Quantity must be 1-${item.maxStack} for ${item.displayName}, got: $quantity"
        }
    }

    /** Whether more items can be added to this stack */
    val canAddMore: Boolean
        get() = quantity < item.maxStack

    /** How many more items can fit in this stack */
    val spaceRemaining: Int
        get() = item.maxStack - quantity
}

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/**
 * Create an item definition.
 *
 * Usage:
 * ```kotlin
 * val potion by item {
 *     name("Potion")
 *     description("Restores 50 HP")
 *     category(ItemCategory.CONSUMABLE)
 *     price(buy = 50)
 *     onUse {
 *         target.hp += 50
 *     }
 * }
 * ```
 */
fun GameBuilder.item(init: ItemBuilder.() -> Unit): ItemDelegate {
    return ItemDelegate(this, init)
}
