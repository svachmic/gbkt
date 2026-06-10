/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// LOOT TABLE DOMAIN TYPES
// =============================================================================
//
// Unified loot tables with rarity tiers. Shared across monster drops, chests,
// and quest rewards. The backend generates roll_loot_<id>() C functions with
// weighted random selection and rarity-weighted probabilities.
// =============================================================================

/** Item rarity tiers for loot drops and shop stock. */
enum class Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
}

/**
 * A single entry in a loot table.
 *
 * @param itemId The item definition ID.
 * @param chance Drop chance as a percentage (0-100).
 * @param rarity Item rarity tier (cosmetic + affects weighted roll).
 * @param minQuantity Minimum quantity to drop (default 1).
 * @param maxQuantity Maximum quantity to drop (default 1).
 */
data class LootEntry(
    val itemId: String,
    val chance: Int,
    val rarity: Rarity = Rarity.COMMON,
    val minQuantity: Int = 1,
    val maxQuantity: Int = 1,
) {
    init {
        require(chance in 0..100) { "LootEntry chance must be 0-100, got $chance" }
        require(minQuantity <= maxQuantity) {
            "LootEntry minQuantity ($minQuantity) must not exceed maxQuantity ($maxQuantity)"
        }
    }
}

/**
 * A reusable loot table definition.
 *
 * Loot tables are shared across monster drops, chests, and quest rewards. The backend generates
 * `roll_loot_<id>()` with weighted random selection.
 *
 * @param id Unique identifier.
 * @param entries List of possible loot entries with their drop chances.
 * @param guaranteedDrop Optional item ID that always drops from this table.
 */
data class LootTableDef(
    val id: String,
    val entries: List<LootEntry>,
    val guaranteedDrop: String? = null,
)
