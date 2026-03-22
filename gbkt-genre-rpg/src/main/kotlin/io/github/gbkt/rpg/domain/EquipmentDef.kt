/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (equipment domain types)

package io.github.gbkt.rpg.domain

// =============================================================================
// EQUIPMENT DOMAIN TYPES
// =============================================================================
//
// Plain Kotlin data classes — NOT IR types. Used by EquipmentBuilder to carry
// equipment configuration data. DSL builders produce core IR types (GenericSystem)
// from this data.
// =============================================================================

/**
 * Standard equipment slot types.
 *
 * Games can extend with custom slots via [EquipSlotDef] entries in [EquipmentConfig].
 */
enum class EquipSlot {
    WEAPON,
    SHIELD,
    HEAD,
    BODY,
    ACCESSORY,
}

/**
 * Custom or extended equipment slot definition.
 *
 * @property slot The [EquipSlot] this slot definition extends or references.
 * @property name Display name for this slot. Defaults to the slot's lowercase enum name.
 * @property isTwoHandedSlot Whether equipping a two-handed item in this slot also blocks the paired
 *   slot (e.g., WEAPON blocks SHIELD when two-handed).
 */
data class EquipSlotDef(
    val slot: EquipSlot,
    val name: String = slot.name.lowercase(),
    val isTwoHandedSlot: Boolean = false,
)

/**
 * A stat modifier applied by an equipped item.
 *
 * Application order: flat bonus applied first, then percentage multiplier.
 *
 * @property stat The stat name (e.g., "hp", "atk", "def", "matk", "mdef", "agl", "sp").
 * @property flat Flat integer bonus added to the stat. May be negative.
 * @property percent Percentage modifier applied after flat. e.g., 10 = +10%.
 */
data class StatModifier(val stat: String, val flat: Int = 0, val percent: Int = 0)

/**
 * Equipment requirement that must be met before an item can be equipped.
 *
 * @property minLevel Minimum character level required. Null = no level requirement.
 * @property minStat A Pair of (statName, minValue) that the character must meet. Null = no stat
 *   requirement.
 * @property classRestriction The ID of the only class that can equip this item. Null = no class
 *   restriction.
 */
data class EquipRequirement(
    val minLevel: Int? = null,
    val minStat: Pair<String, Int>? = null,
    val classRestriction: String? = null,
)

/**
 * A single tier of a set bonus, activated when [piecesRequired] set items are equipped.
 *
 * @property piecesRequired Number of set pieces that must be equipped to activate this tier.
 * @property modifiers List of [StatModifier] entries applied as the set bonus.
 */
data class SetBonusTier(val piecesRequired: Int, val modifiers: List<StatModifier>)

/**
 * Definition of an equipment set that provides bonus stats when multiple pieces are equipped.
 *
 * @property id Unique identifier for this set.
 * @property name Display name for this set.
 * @property tiers Ordered list of bonus tiers (e.g., 2-piece, 3-piece, 4-piece bonuses).
 */
data class EquipSetDef(val id: String, val name: String, val tiers: List<SetBonusTier>)

/**
 * Global equipment system configuration.
 *
 * Controls which slots are available, whether dual-wield is allowed, set bonuses, and optional
 * upgrade/durability/enchanting systems.
 *
 * @property customSlots Additional or modified slot definitions beyond the standard [EquipSlot]
 *   set.
 * @property allowDualWield Whether two WEAPON-slot items can be equipped simultaneously.
 * @property sets Equipment set definitions for set bonus tracking.
 * @property enableUpgrades Whether the upgrade system is enabled (generates `upgrade_item_<slot>()`
 *   functions). GAP-1.
 * @property maxUpgradeLevel Maximum upgrade level allowed per item. Only used when [enableUpgrades]
 *   is true.
 * @property enableDurability Whether equipped items have durability that degrades. Generates
 *   `_equip_durability_<slot>` globals and `degrade_equipment()` function.
 * @property enableEnchanting Whether runtime elemental enchanting is enabled (generates
 *   `enchant_item_<slot>()` functions). GAP-2.
 */
data class EquipmentConfig(
    val customSlots: List<EquipSlotDef> = emptyList(),
    val allowDualWield: Boolean = false,
    val sets: List<EquipSetDef> = emptyList(),
    val enableUpgrades: Boolean = false,
    val maxUpgradeLevel: Int = 3,
    val enableDurability: Boolean = false,
    val enableEnchanting: Boolean = false,
)

/**
 * Equipment data attached to an item definition.
 *
 * Specifies which slot the item occupies, its stat modifiers, any requirements, and optional
 * features like set membership, two-handed use, durability, and enchanting.
 *
 * @property slot The [EquipSlot] this item occupies when equipped.
 * @property modifiers List of [StatModifier] entries applied when the item is equipped.
 * @property requirements Optional [EquipRequirement] for level/stat/class restrictions.
 * @property setId ID of the [EquipSetDef] this item belongs to, or null if not part of a set.
 * @property isTwoHanded Whether this item occupies both WEAPON and SHIELD slots when equipped.
 * @property durability Initial durability value, or null if durability is not tracked.
 * @property aspect Elemental [Aspect] of this item (inherent elemental typing).
 * @property enchantAspect Initial/default enchant [Aspect] (null = no enchantment). Runtime
 *   enchanting via `enchant_item_<slot>(aspect_id)` may override this. GAP-2.
 */
data class EquipmentItemData(
    val slot: EquipSlot,
    val modifiers: List<StatModifier> = emptyList(),
    val requirements: EquipRequirement? = null,
    val setId: String? = null,
    val isTwoHanded: Boolean = false,
    val durability: Int? = null,
    val aspect: Aspect? = null,
    val enchantAspect: Aspect? = null,
)
