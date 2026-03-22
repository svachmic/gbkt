/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ContainerIR
import io.github.gbkt.core.ir.DropEntryIR
import io.github.gbkt.core.ir.DropTableIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HealEffect
import io.github.gbkt.core.ir.ItemCategoryDef
import io.github.gbkt.core.ir.ItemDef
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// INVENTORY CODEGEN TESTS
// Verifies that InventoryVisitor generates correct C89-compliant code for:
// 1. Container globals (_inv_<id>_items[], _inv_<id>_counts[], _inv_<id>_size)
// 2. Container operations (add/remove/count/contains)
// 3. Item catalog constants (ITEM_ID, ITEM_STACK, CATEGORY_DEFAULT_STACK)
// 4. use_item dispatch via CSwitch
// 5. Drop table weighted random selection via _prng()
// 6. Multiple containers generating separate function sets
// =============================================================================

// =============================================================================
// Test fixture helpers
// =============================================================================

/**
 * Build a minimal GameIR with items and containers (1 scene, 0 actors).
 *
 * Used by container globals, operations, use_item, and item catalog tests.
 */
private fun buildInventoryGameIR(
    categories: List<ItemCategoryDef> = emptyList(),
    items: List<ItemDef> = emptyList(),
    containers: List<ContainerIR> = emptyList(),
): GameIR =
    GameIR(
        name = "InventoryTest",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        itemCategories = categories,
        items = items,
        containers = containers,
    )

/**
 * Build a minimal GameIR with items and drop tables (1 scene, 0 actors).
 *
 * Used by drop table tests.
 */
private fun buildDropTableGameIR(
    items: List<ItemDef> = emptyList(),
    dropTables: List<DropTableIR> = emptyList(),
): GameIR =
    GameIR(
        name = "DropTableTest",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        itemCategories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 5)),
        items = items,
        dropTables = dropTables,
    )

/** Helper to generate main.c from a GameIR via GBDKPipelineV2. */
private fun generateMainC(gameIR: GameIR): String {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(gameIR)
    return output.files["main.c"] ?: error("main.c not generated")
}

/** Helper assertion: assert mainC contains expected substring. */
private fun assertInMainC(mainC: String, expected: String, message: String) {
    assertTrue(mainC.contains(expected), "$message — substring '$expected' not found")
}

/** Helper assertion: assert mainC does not contain substring. */
private fun assertNotInMainC(mainC: String, unexpected: String, message: String) {
    assertFalse(mainC.contains(unexpected), "$message — unexpected '$unexpected' found")
}

// =============================================================================
// Tests
// =============================================================================

class InventoryCodegenTest {

    // =========================================================================
    // Test 1: container generates _inv_items and _counts globals
    // =========================================================================

    @Test
    fun `container generates inv_items and counts globals`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "_inv_bag_items", "Expected _inv_bag_items array in main.c")
        assertInMainC(mainC, "_inv_bag_counts", "Expected _inv_bag_counts array in main.c")
    }

    // =========================================================================
    // Test 2: container generates _inv_size global initialized to zero
    // =========================================================================

    @Test
    fun `container generates inv_size global initialized to zero`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "_inv_bag_size", "Expected _inv_bag_size variable in main.c")
        // Size initialized to 0u (CLiteral(0) emits as 0u)
        assertTrue(
            mainC.contains("_inv_bag_size") && mainC.contains("0u"),
            "Expected _inv_bag_size initialized to 0u",
        )
    }

    // =========================================================================
    // Test 3: container add function with stack search and new slot
    // =========================================================================

    @Test
    fun `container add function generated with stack search and new slot`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "inv_bag_add", "Expected inv_bag_add function in main.c")
        assertInMainC(mainC, "_inv_bag_items", "Expected items array access in add function")
        assertInMainC(mainC, "_inv_bag_counts", "Expected counts array access in add function")
        assertInMainC(mainC, "_inv_bag_size", "Expected size check in add function")
    }

    // =========================================================================
    // Test 4: container remove function with compact
    // =========================================================================

    @Test
    fun `container remove function with compact generated`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "inv_bag_remove", "Expected inv_bag_remove function in main.c")
        assertInMainC(mainC, "_inv_bag_size", "Expected size decrement in remove function")
    }

    // =========================================================================
    // Test 5: container count function returns item count
    // =========================================================================

    @Test
    fun `container count function generated`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "inv_bag_count", "Expected inv_bag_count function in main.c")
        assertInMainC(mainC, "_inv_bag_counts", "Expected counts array in count function")
    }

    // =========================================================================
    // Test 6: container contains function returns boolean
    // =========================================================================

    @Test
    fun `container contains function returns boolean`() {
        val gameIR = buildInventoryGameIR(containers = listOf(ContainerIR(id = "bag", slots = 16)))
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "inv_bag_contains", "Expected inv_bag_contains function in main.c")
        assertInMainC(mainC, "1u", "Expected return 1u in contains function")
        assertInMainC(mainC, "0u", "Expected return 0u in contains function")
    }

    // =========================================================================
    // Test 7: item catalog generates ITEM_ID constants
    // =========================================================================

    @Test
    fun `item catalog generates ITEM_ID constant for first item`() {
        val gameIR =
            buildInventoryGameIR(
                categories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 5)),
                items =
                    listOf(
                        ItemDef(
                            id = "potion",
                            name = "Potion",
                            categoryId = "consumable",
                            maxStack = 10,
                        )
                    ),
                containers = listOf(ContainerIR(id = "bag", slots = 8)),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "ITEM_POTION_ID", "Expected ITEM_POTION_ID constant in main.c")
    }

    // =========================================================================
    // Test 8: item catalog generates ITEM_STACK constants from item override
    // =========================================================================

    @Test
    fun `item catalog generates ITEM_STACK constant from item override`() {
        val gameIR =
            buildInventoryGameIR(
                categories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 5)),
                items =
                    listOf(
                        ItemDef(
                            id = "potion",
                            name = "Potion",
                            categoryId = "consumable",
                            maxStack = 10,
                        )
                    ),
                containers = listOf(ContainerIR(id = "bag", slots = 8)),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "ITEM_POTION_STACK", "Expected ITEM_POTION_STACK constant in main.c")
        assertInMainC(mainC, "10u", "Expected ITEM_POTION_STACK = 10u in main.c")
    }

    // =========================================================================
    // Test 13: item catalog resolves ITEM_STACK from category default when item maxStack is null
    // =========================================================================

    @Test
    fun `item catalog resolves ITEM_STACK from category default when maxStack is null`() {
        val gameIR =
            buildInventoryGameIR(
                categories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 5)),
                items =
                    listOf(
                        ItemDef(
                            id = "herb",
                            name = "Herb",
                            categoryId = "consumable",
                            maxStack = null, // inherits category default
                        )
                    ),
                containers = listOf(ContainerIR(id = "bag", slots = 8)),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "ITEM_HERB_STACK", "Expected ITEM_HERB_STACK constant in main.c")
        assertInMainC(mainC, "5u", "Expected ITEM_HERB_STACK = 5u (from category default)")
    }

    // =========================================================================
    // Test 14: item category generates CATEGORY_DEFAULT_STACK constants
    // =========================================================================

    @Test
    fun `item category generates CATEGORY_DEFAULT_STACK constant`() {
        val gameIR =
            buildInventoryGameIR(
                categories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 10)),
                items = listOf(ItemDef(id = "potion", name = "Potion", categoryId = "consumable")),
                containers = listOf(ContainerIR(id = "bag", slots = 8)),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(
            mainC,
            "CATEGORY_CONSUMABLE_DEFAULT_STACK",
            "Expected CATEGORY_CONSUMABLE_DEFAULT_STACK in main.c",
        )
        assertInMainC(mainC, "10u", "Expected CATEGORY_CONSUMABLE_DEFAULT_STACK = 10u")
    }

    // =========================================================================
    // Test 9: use_item dispatcher generates switch over item effects
    // =========================================================================

    @Test
    fun `use_item dispatcher generated for items with effects`() {
        val gameIR =
            buildInventoryGameIR(
                categories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 5)),
                items =
                    listOf(
                        ItemDef(
                            id = "potion",
                            name = "Potion",
                            categoryId = "consumable",
                            effects = listOf(HealEffect(amount = 50)),
                        )
                    ),
                containers = listOf(ContainerIR(id = "bag", slots = 8)),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "use_item_bag", "Expected use_item_bag function in main.c")
        assertInMainC(mainC, "item_id", "Expected item_id parameter in use_item function")
        assertInMainC(mainC, "target_hp", "Expected target_hp heal operation in use_item")
    }

    // =========================================================================
    // Test 10: drop table generates roll function with weighted selection
    // =========================================================================

    @Test
    fun `drop table generates roll function with weighted selection`() {
        val gameIR =
            buildDropTableGameIR(
                items =
                    listOf(
                        ItemDef(id = "potion", name = "Potion", categoryId = "consumable"),
                        ItemDef(id = "herb", name = "Herb", categoryId = "consumable"),
                    ),
                dropTables =
                    listOf(
                        DropTableIR(
                            id = "goblin_drops",
                            entries =
                                listOf(
                                    DropEntryIR(itemId = "potion", weight = 30),
                                    DropEntryIR(itemId = "herb", weight = 70),
                                ),
                        )
                    ),
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(
            mainC,
            "roll_drop_table_goblin_drops",
            "Expected roll_drop_table_goblin_drops function in main.c",
        )
        assertInMainC(mainC, "_prng", "Expected _prng() helper in main.c")
        // Total weight is 100
        assertInMainC(mainC, "100", "Expected total weight (100) as modulus in roll function")
    }

    // =========================================================================
    // Test 11: multiple containers generate separate function sets
    // =========================================================================

    @Test
    fun `multiple containers generate separate function sets`() {
        val gameIR =
            buildInventoryGameIR(
                containers =
                    listOf(ContainerIR(id = "bag", slots = 16), ContainerIR(id = "keys", slots = 4))
            )
        val mainC = generateMainC(gameIR)

        // Bag functions
        assertInMainC(mainC, "inv_bag_add", "Expected inv_bag_add in main.c")
        assertInMainC(mainC, "inv_bag_remove", "Expected inv_bag_remove in main.c")
        assertInMainC(mainC, "inv_bag_count", "Expected inv_bag_count in main.c")
        assertInMainC(mainC, "inv_bag_contains", "Expected inv_bag_contains in main.c")

        // Keys functions
        assertInMainC(mainC, "inv_keys_add", "Expected inv_keys_add in main.c")
        assertInMainC(mainC, "inv_keys_remove", "Expected inv_keys_remove in main.c")
        assertInMainC(mainC, "inv_keys_count", "Expected inv_keys_count in main.c")
        assertInMainC(mainC, "inv_keys_contains", "Expected inv_keys_contains in main.c")
    }

    // =========================================================================
    // Test 12: container with categoryFilter documented in comment
    // =========================================================================

    @Test
    fun `container with categoryFilter generates comment in add function`() {
        val gameIR =
            buildInventoryGameIR(
                containers =
                    listOf(ContainerIR(id = "bag", slots = 8, categoryFilter = "consumable"))
            )
        val mainC = generateMainC(gameIR)

        assertInMainC(mainC, "inv_bag_add", "Expected inv_bag_add in main.c")
        assertInMainC(mainC, "consumable", "Expected categoryFilter comment in add function")
    }

    // =========================================================================
    // Test: game without inventory generates no inventory functions
    // =========================================================================

    @Test
    fun `game without inventory generates no inventory functions`() {
        val gameIR =
            GameIR(
                name = "NoInventory",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
            )
        val mainC = generateMainC(gameIR)

        assertNotInMainC(mainC, "inv_", "Expected no inv_ functions in game without inventory")
        assertNotInMainC(mainC, "ITEM_", "Expected no ITEM_ constants in game without inventory")
    }
}
