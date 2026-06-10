/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.AutoSaveTrigger
import io.github.gbkt.rpg.domain.LootEntry
import io.github.gbkt.rpg.domain.LootTableDef
import io.github.gbkt.rpg.domain.Rarity
import io.github.gbkt.rpg.domain.RpgSaveConfig
import io.github.gbkt.rpg.domain.SaveMode

// =============================================================================
// RPG SAVE BUILDER AND LOOT TABLE BUILDER
// =============================================================================

/**
 * Builder for [RpgSaveConfig] — RPG save system configuration.
 *
 * ```kotlin
 * rpgSave {
 *     slots(3)
 *     mode(SaveMode.SAVE_POINT)
 *     autoSave(SCENE_TRANSITION, AFTER_BATTLE)
 *     previewFields("name", "level", "time")
 *     newGamePlus {
 *         carryOver("inventory", "gold")
 *     }
 * }
 * ```
 */
class RpgSaveBuilder {
    private var slotCount: Int = 3
    private var saveMode: SaveMode = SaveMode.SAVE_POINT
    private var autoSaveEnabled: Boolean = false
    private val autoSaveTriggers = mutableSetOf<AutoSaveTrigger>()
    private var savePreviewFields: List<String> = listOf("name", "level", "time")
    private var enableNewGamePlus: Boolean = false
    private val ngPlusCarryOver = mutableSetOf<String>()
    private val excludeFromSave = mutableSetOf<String>()

    /** Sets the number of save slots (default 3). */
    fun slots(n: Int) {
        slotCount = n
    }

    /** Sets when saving is allowed. */
    fun mode(m: SaveMode) {
        saveMode = m
    }

    /** Enables auto-save with the given trigger events. */
    fun autoSave(vararg triggers: AutoSaveTrigger) {
        autoSaveEnabled = true
        autoSaveTriggers.addAll(triggers)
    }

    /** Sets the fields shown in the load menu preview. */
    fun previewFields(vararg fields: String) {
        savePreviewFields = fields.toList()
    }

    /** Configures NG+ carry-over behavior. */
    fun newGamePlus(block: NgPlusBuilder.() -> Unit) {
        enableNewGamePlus = true
        val builder = NgPlusBuilder()
        builder.block()
        ngPlusCarryOver.addAll(builder.carryOver)
    }

    /** Excludes field names from save data (volatile/transient state). */
    fun exclude(vararg fields: String) {
        excludeFromSave.addAll(fields)
    }

    fun build(): RpgSaveConfig =
        RpgSaveConfig(
            slotCount = slotCount,
            saveMode = saveMode,
            autoSaveEnabled = autoSaveEnabled,
            autoSaveTriggers = autoSaveTriggers.toSet(),
            savePreviewFields = savePreviewFields,
            enableNewGamePlus = enableNewGamePlus,
            ngPlusCarryOver = ngPlusCarryOver.toSet(),
            excludeFromSave = excludeFromSave.toSet(),
        )
}

/** Nested builder for NG+ carry-over configuration. */
class NgPlusBuilder {
    internal val carryOver = mutableSetOf<String>()

    /** Registers field names to carry over into a new game. */
    fun carryOver(vararg fields: String) {
        carryOver.addAll(fields)
    }
}

// =============================================================================
// LOOT TABLE BUILDER
// =============================================================================

/**
 * Builder for [LootTableDef] — loot tables with rarity tiers.
 *
 * ```kotlin
 * lootTable("goblin_drops") {
 *     entry("gold_coin") { chance(60) }
 *     entry("herb") { chance(30); rarity(Rarity.UNCOMMON) }
 *     entry("magic_gem") { chance(5); rarity(Rarity.RARE); quantity(min = 1, max = 2) }
 *     guaranteed("goblin_fang")
 * }
 * ```
 *
 * @param id Unique identifier for this loot table.
 */
class LootTableBuilder(private val id: String) {
    private val entries = mutableListOf<LootEntry>()
    private var guaranteedDrop: String? = null

    /** Adds a loot entry with the given item ID. */
    fun entry(itemId: String, block: LootEntryBuilder.() -> Unit = {}) {
        val builder = LootEntryBuilder(itemId)
        builder.block()
        entries.add(builder.build())
    }

    /** Sets an item that always drops from this table. */
    fun guaranteed(itemId: String) {
        guaranteedDrop = itemId
    }

    fun build(): LootTableDef =
        LootTableDef(id = id, entries = entries.toList(), guaranteedDrop = guaranteedDrop)
}

/** Nested builder for individual loot entries. */
class LootEntryBuilder(val itemId: String) {
    private var chance: Int = 10
    private var rarity: Rarity = Rarity.COMMON
    private var minQuantity: Int = 1
    private var maxQuantity: Int = 1

    /** Sets the drop chance as a percentage (0-100). */
    fun chance(c: Int) {
        chance = c
    }

    /** Sets the item rarity tier. */
    fun rarity(r: Rarity) {
        rarity = r
    }

    /** Sets the quantity range. */
    fun quantity(min: Int = 1, max: Int = min) {
        minQuantity = min
        maxQuantity = max
    }

    fun build(): LootEntry =
        LootEntry(
            itemId = itemId,
            chance = chance,
            rarity = rarity,
            minQuantity = minQuantity,
            maxQuantity = maxQuantity,
        )
}

// =============================================================================
// CRAFTING BUILDER
// =============================================================================

/** Builder for crafting recipes (optional module), used in GameBuilder.craftingRecipes. */
class CraftingBuilder {
    internal val recipes = mutableListOf<io.github.gbkt.rpg.domain.CraftingRecipe>()

    /** Adds a crafting recipe. */
    fun recipe(resultItemId: String, block: CraftingRecipeBuilder.() -> Unit) {
        val builder = CraftingRecipeBuilder(resultItemId)
        builder.block()
        recipes.add(builder.build())
    }
}
