/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File intentionally named for its purpose (multiple inventory type declarations)

package io.github.gbkt.core.inventory

// =============================================================================
// INVENTORY TYPES (engine-level extension points)
// =============================================================================

/**
 * Engine-level contract for item effect implementations.
 *
 * RPG and genre packages extend this interface with domain-specific effects: stat restoration,
 * status cures, ability unlocks, etc. The engine base effects ([HealEffect], [BuffEffect],
 * [ScriptEffect]) are defined in the IR layer in `InventoryIR.kt`.
 */
fun interface ItemEffect {
    /** Applies this effect. Called when the item is used from the inventory. */
    fun apply()
}

// =============================================================================
// PREDEFINED CATEGORY ID CONSTANTS
// =============================================================================

/** Standard consumable items category ID (potions, ethers, antidotes, etc.). */
const val CATEGORY_CONSUMABLE = "CONSUMABLE"

/** Key items category ID — unique items that cannot be discarded or stacked beyond 1. */
const val CATEGORY_KEY_ITEM = "KEY_ITEM"
