/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.EquipSetDef
import io.github.gbkt.rpg.domain.EquipSlot
import io.github.gbkt.rpg.domain.EquipSlotDef
import io.github.gbkt.rpg.domain.EquipmentConfig
import io.github.gbkt.rpg.domain.SetBonusTier
import io.github.gbkt.rpg.domain.StatModifier

// =============================================================================
// EQUIPMENT SYSTEM BUILDER
// =============================================================================
//
// Builds an EquipmentConfig domain object. Used by the equipmentSystem() DSL
// extension on GameBuilder to produce a GenericSystem with type="rpg_equipment_system".
// =============================================================================

/**
 * Builder for [StatModifier] entries. Used inside set bonus tier blocks.
 *
 * Usage:
 * ```kotlin
 * stat("atk") { flat(5); percent(10) }
 * ```
 */
class StatModifierBuilder {
    private var stat: String = ""
    private var flat: Int = 0
    private var percent: Int = 0

    /** Sets the stat name (e.g., "hp", "atk", "def"). */
    fun stat(name: String) {
        stat = name
    }

    /** Sets the flat bonus value (added before percentage). */
    fun flat(value: Int) {
        flat = value
    }

    /** Sets the percentage modifier applied after the flat bonus. e.g., 10 = +10%. */
    fun percent(value: Int) {
        percent = value
    }

    /** Builds the [StatModifier]. */
    fun build(): StatModifier = StatModifier(stat = stat, flat = flat, percent = percent)
}

/**
 * Builder for a single [SetBonusTier].
 *
 * Collects stat modifiers for a given piece-count tier.
 */
class SetBonusTierBuilder {
    private val modifiers = mutableListOf<StatModifier>()

    /**
     * Adds a stat modifier for this tier.
     *
     * Usage:
     * ```kotlin
     * modifier("atk", flat = 5)
     * modifier("def", flat = 3, percent = 5)
     * ```
     */
    fun modifier(stat: String, flat: Int = 0, percent: Int = 0) {
        modifiers.add(StatModifier(stat = stat, flat = flat, percent = percent))
    }

    /** Builds the list of [StatModifier] entries. */
    fun build(): List<StatModifier> = modifiers.toList()
}

/**
 * Builder for an [EquipSetDef].
 *
 * Defines the set name and its bonus tiers.
 *
 * Usage:
 * ```kotlin
 * set("dragon_set") {
 *     name("Dragon Armor Set")
 *     tier(2) { modifier("def", flat = 5) }
 *     tier(4) { modifier("def", flat = 10); modifier("matk", flat = 3) }
 * }
 * ```
 */
class EquipSetBuilder(private val id: String) {
    private var setName: String = id
    private val tiers = mutableListOf<SetBonusTier>()

    /** Sets the display name for the set. */
    fun name(n: String) {
        setName = n
    }

    /**
     * Adds a set bonus tier activated when [piecesRequired] set items are equipped.
     *
     * @param piecesRequired Number of set pieces required to activate this tier.
     * @param block DSL block using [SetBonusTierBuilder] to define stat modifiers.
     */
    fun tier(piecesRequired: Int, block: SetBonusTierBuilder.() -> Unit) {
        val builder = SetBonusTierBuilder()
        builder.block()
        tiers.add(SetBonusTier(piecesRequired = piecesRequired, modifiers = builder.build()))
    }

    /** Builds the [EquipSetDef]. */
    fun build(): EquipSetDef = EquipSetDef(id = id, name = setName, tiers = tiers.toList())
}

/**
 * Builder for the equipment system configuration.
 *
 * Configures available slots, dual-wield rules, set bonuses, upgrades, durability, and enchanting.
 *
 * Usage:
 * ```kotlin
 * equipmentSystem {
 *     slot(EquipSlot.WEAPON) { isTwoHandedSlot = true }
 *     dualWield()
 *     set("dragon_set") {
 *         name("Dragon Set")
 *         tier(2) { modifier("def", flat = 5) }
 *     }
 *     enableUpgrades(maxLevel = 5)
 *     enableDurability()
 *     enableEnchanting()
 * }
 * ```
 */
class EquipmentSystemBuilder {
    private val customSlots = mutableListOf<EquipSlotDef>()
    private var allowDualWield: Boolean = false
    private val sets = mutableListOf<EquipSetDef>()
    private var upgradesEnabled: Boolean = false
    private var maxUpgradeLevel: Int = 3
    private var durabilityEnabled: Boolean = false
    private var enchantingEnabled: Boolean = false

    /**
     * Defines a custom slot configuration for the given [EquipSlot].
     *
     * @param slot The [EquipSlot] to configure.
     * @param block DSL block for [EquipSlotDef] configuration.
     */
    fun slot(slot: EquipSlot, block: EquipSlotDefBuilder.() -> Unit = {}) {
        val builder = EquipSlotDefBuilder(slot)
        builder.block()
        customSlots.add(builder.build())
    }

    /**
     * Enables dual-wield: two WEAPON-slot items can be equipped simultaneously.
     *
     * When enabled, the SHIELD slot is blocked unless explicitly allowed.
     */
    fun dualWield() {
        allowDualWield = true
    }

    /**
     * Defines an equipment set for tiered set bonuses.
     *
     * @param id Unique identifier for the set.
     * @param block DSL block using [EquipSetBuilder] to define name and tiers.
     */
    fun set(id: String, block: EquipSetBuilder.() -> Unit) {
        val builder = EquipSetBuilder(id)
        builder.block()
        sets.add(builder.build())
    }

    /**
     * Enables the item upgrade system. Generates `upgrade_item_<slot>()` C functions.
     *
     * @param maxLevel Maximum upgrade level per item. Default: 3.
     */
    fun enableUpgrades(maxLevel: Int = 3) {
        upgradesEnabled = true
        maxUpgradeLevel = maxLevel
    }

    /**
     * Enables item durability tracking. Generates `_equip_durability_<slot>` globals and
     * `degrade_equipment()` function.
     */
    fun enableDurability() {
        durabilityEnabled = true
    }

    /**
     * Enables runtime elemental enchanting (GAP-2). Generates `enchant_item_<slot>()` C functions
     * and `_equip_<slot>_enchant` globals.
     */
    fun enableEnchanting() {
        enchantingEnabled = true
    }

    /** Builds the [EquipmentConfig]. */
    fun build(): EquipmentConfig =
        EquipmentConfig(
            customSlots = customSlots.toList(),
            allowDualWield = allowDualWield,
            sets = sets.toList(),
            enableUpgrades = upgradesEnabled,
            maxUpgradeLevel = maxUpgradeLevel,
            enableDurability = durabilityEnabled,
            enableEnchanting = enchantingEnabled,
        )
}

/**
 * Builder for [EquipSlotDef].
 *
 * Allows customizing a slot's name and two-handed flag.
 */
class EquipSlotDefBuilder(private val slot: EquipSlot) {
    /** Display name for this slot. Defaults to the slot enum's lowercase name. */
    var name: String = slot.name.lowercase()

    /** Whether this slot blocks the paired slot when a two-handed item is equipped. */
    var isTwoHandedSlot: Boolean = false

    /** Builds the [EquipSlotDef]. */
    fun build(): EquipSlotDef =
        EquipSlotDef(slot = slot, name = name, isTwoHandedSlot = isTwoHandedSlot)
}
