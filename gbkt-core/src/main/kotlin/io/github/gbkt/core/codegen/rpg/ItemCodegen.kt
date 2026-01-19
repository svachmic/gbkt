/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

// Equipment IR types are in EquipmentIR.kt and handled by EquipmentCodegen.kt
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.SENTINEL_NO_EQUIP_SLOT
import io.github.gbkt.core.codegen.SENTINEL_NO_ITEM
import io.github.gbkt.core.codegen.core.generateExpr
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRInventoryAddItem
import io.github.gbkt.core.ir.IRInventoryClear
import io.github.gbkt.core.ir.IRInventoryRemoveItem
import io.github.gbkt.core.ir.IRInventoryUseItem
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.EquipmentSlot
import io.github.gbkt.core.rpg.Inventory
import io.github.gbkt.core.rpg.ItemCategory
import io.github.gbkt.core.rpg.StatBonusType

// =============================================================================
// ITEM AND INVENTORY CODE GENERATION
// =============================================================================

/** Maximum number of characters that can have equipment tracked (Game Boy RAM constraint) */
private const val MAX_EQUIPPABLE_CHARACTERS = 8

/** Generate item definitions and inventory system. */
internal fun CodeGenerator.generateItemSystem() {
    if (game.items.isEmpty() && game.inventories.isEmpty()) return

    generateItemConstants()
    generateItemData()
    generateEquipmentBonusTables()
    generateInventoryStructures()

    // Forward declaration for banked item use function
    line("// Forward declaration for banked item use")
    line("void _execute_item_use(UINT8 item_id, UINT8 target) BANKED;")
    line()

    // Equipment system is generated separately by EquipmentCodegen.generateEquipmentSystem()
    generateInventoryFunctions()
    generateItemUseDispatch()
}

/** Generate item ID constants. */
private fun CodeGenerator.generateItemConstants() {
    if (game.items.isEmpty()) return

    line("// === Item Constants ===")
    line("#define ITEM_NONE $SENTINEL_NO_ITEM")
    for (item in game.items) {
        line("#define ITEM_${item.id.uppercase()} ${item.itemIndex}")
    }
    line("#define ITEM_COUNT ${game.items.size}")
    line()

    // Generate item category constants
    line("// Item Categories")
    for (category in ItemCategory.entries) {
        line("#define ITEM_CAT_${category.name} ${category.ordinal}")
    }
    line()

    // Generate equipment slot constants (built-in + custom)
    line("// Equipment Slots")
    val allSlots = EquipmentSlot.BUILT_IN_SLOTS + game.equipmentSlots
    for (slot in allSlots) {
        val safeName = slot.name.uppercase().replace(" ", "_").replace("-", "_")
        line("#define EQUIP_$safeName ${slot.id}")
    }
    line("#define EQUIP_SLOT_COUNT ${allSlots.size}")
    line()
}

/** Generate item data table. */
private fun CodeGenerator.generateItemData() {
    if (game.items.isEmpty()) return

    // Generate item data structure
    line("// === Item Data Structure ===")
    line("typedef struct {")
    line("    UINT8 category;")
    line("    UINT8 max_stack;")
    line("    UINT8 buy_price_lo;")
    line("    UINT8 buy_price_hi;")
    line("    UINT8 sell_price_lo;")
    line("    UINT8 sell_price_hi;")
    line("    UINT8 equip_slot;")
    line("    UINT8 flags;")
    line("} ItemData;")
    line()

    // Generate item flags
    line("#define ITEM_FLAG_USABLE_BATTLE  0x01")
    line("#define ITEM_FLAG_USABLE_FIELD   0x02")
    line("#define ITEM_FLAG_EQUIPPABLE     0x04")
    line("#define ITEM_FLAG_KEY_ITEM       0x08")
    line()

    // Generate item data table
    line("static const ItemData item_data[ITEM_COUNT] = {")
    for (item in game.items) {
        val flags = mutableListOf<String>()
        if (item.usableInBattle) flags.add("ITEM_FLAG_USABLE_BATTLE")
        if (item.usableOutOfBattle) flags.add("ITEM_FLAG_USABLE_FIELD")
        if (item.isEquippable) flags.add("ITEM_FLAG_EQUIPPABLE")
        if (item.isKeyItem) flags.add("ITEM_FLAG_KEY_ITEM")
        val flagsExpr = if (flags.isEmpty()) "0" else flags.joinToString(" | ")

        val equipSlot =
            item.equipSlot?.let {
                val safeName = it.name.uppercase().replace(" ", "_").replace("-", "_")
                "EQUIP_$safeName"
            } ?: SENTINEL_NO_EQUIP_SLOT

        line(
            "    { ITEM_CAT_${item.category.name}, ${item.maxStack}, " +
                "${item.buyPrice and 0xFF}, ${(item.buyPrice shr 8) and 0xFF}, " +
                "${item.sellPrice and 0xFF}, ${(item.sellPrice shr 8) and 0xFF}, " +
                "$equipSlot, $flagsExpr }, // ${item.id}"
        )
    }
    line("};")
    line()
}

/** Generate equipment stat bonus tables. */
private fun CodeGenerator.generateEquipmentBonusTables() {
    val equippableItems = game.items.filter { it.isEquippable }
    if (equippableItems.isEmpty()) return

    line("// === Equipment Stat Bonus Tables ===")

    // Generate bonus arrays for each stat type
    val statTypes = listOf("ATK", "DEF", "MATK", "MDEF", "AGL", "MAX_HP", "MAX_SP")

    for (statType in statTypes) {
        val bonusType =
            when (statType) {
                "ATK" -> StatBonusType.ATK
                "DEF" -> StatBonusType.DEF
                "MATK" -> StatBonusType.MATK
                "MDEF" -> StatBonusType.MDEF
                "AGL" -> StatBonusType.AGL
                "MAX_HP" -> StatBonusType.MAX_HP
                "MAX_SP" -> StatBonusType.MAX_SP
                else -> continue
            }

        line("static const INT8 item_bonus_${statType.lowercase()}[ITEM_COUNT] = {")
        for ((index, item) in game.items.withIndex()) {
            val bonus = item.statBonuses[bonusType] ?: 0
            val comment = if (index == game.items.size - 1) "" else ","
            line("    $bonus$comment // ${item.id}")
        }
        line("};")
    }
    line()
}

// Equipment system is now generated by EquipmentCodegen.kt

/** Generate inventory data structures. */
private fun CodeGenerator.generateInventoryStructures() {
    // Generate inventory structures if inventories exist OR if items exist (for battle item use)
    val hasItemsOrInventories = game.inventories.isNotEmpty() || game.items.isNotEmpty()
    if (!hasItemsOrInventories) return

    line("// === Inventory Structures ===")
    line("typedef struct {")
    line("    UINT8 item_id;")
    line("    UINT8 quantity;")
    line("} InventorySlot;")
    line()

    if (game.inventories.isEmpty() && game.items.isNotEmpty()) {
        // Generate a default inventory for battle item use when no explicit inventory is defined
        val defaultSlots = 16
        line("// Default inventory (auto-generated for battle item use)")
        line("static InventorySlot inventory_slots[$defaultSlots];")
        line("#define INVENTORY_MAX_SLOTS $defaultSlots")
    } else {
        for (inventory in game.inventories) {
            line("// ${inventory.id} (${inventory.maxSlots} slots)")
            line("static InventorySlot ${inventory.id}_slots[${inventory.maxSlots}];")
            line("#define ${inventory.id.uppercase()}_MAX_SLOTS ${inventory.maxSlots}")
        }
    }
    line()
}

/** Generate inventory helper functions. */
private fun CodeGenerator.generateInventoryFunctions() {
    // Generate inventory functions if inventories exist OR if items exist (for battle item use)
    val hasItemsOrInventories = game.inventories.isNotEmpty() || game.items.isNotEmpty()
    if (!hasItemsOrInventories) return

    line("// === Inventory Functions ===")

    // Initialize inventory function
    line("static void inventory_init(InventorySlot* slots, UINT8 max_slots) {")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        slots[i].item_id = ITEM_NONE;")
    line("        slots[i].quantity = 0;")
    line("    }")
    line("}")
    line()

    // Find item in inventory
    line("static INT8 inventory_find_item(InventorySlot* slots, UINT8 max_slots, UINT8 item_id) {")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == item_id) return (INT8)i;")
    line("    }")
    line("    return -1;")
    line("}")
    line()

    // Find empty slot
    line("static INT8 inventory_find_empty(InventorySlot* slots, UINT8 max_slots) {")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == ITEM_NONE) return (INT8)i;")
    line("    }")
    line("    return -1;")
    line("}")
    line()

    // Add item to inventory
    line(
        "static UINT8 inventory_add_item(InventorySlot* slots, UINT8 max_slots, " +
            "UINT8 item_id, UINT8 quantity) {"
    )
    line("    UINT8 max_stack = item_data[item_id].max_stack;")
    line("    UINT8 remaining = quantity;")
    line("    ")
    line("    // Try to stack with existing items first")
    line("    for (UINT8 i = 0; i < max_slots && remaining > 0; i++) {")
    line("        if (slots[i].item_id == item_id) {")
    line("            UINT8 space = max_stack - slots[i].quantity;")
    line("            UINT8 to_add = (remaining < space) ? remaining : space;")
    line("            slots[i].quantity += to_add;")
    line("            remaining -= to_add;")
    line("        }")
    line("    }")
    line("    ")
    line("    // Add to empty slots if needed")
    line("    while (remaining > 0) {")
    line("        INT8 slot = inventory_find_empty(slots, max_slots);")
    line("        if (slot < 0) break;")
    line("        UINT8 to_add = (remaining < max_stack) ? remaining : max_stack;")
    line("        slots[slot].item_id = item_id;")
    line("        slots[slot].quantity = to_add;")
    line("        remaining -= to_add;")
    line("    }")
    line("    ")
    line("    return remaining; // Returns amount that couldn't be added")
    line("}")
    line()

    // Remove item from inventory
    line(
        "static UINT8 inventory_remove_item(InventorySlot* slots, UINT8 max_slots, " +
            "UINT8 item_id, UINT8 quantity) {"
    )
    line("    UINT8 remaining = quantity;")
    line("    ")
    line("    for (UINT8 i = 0; i < max_slots && remaining > 0; i++) {")
    line("        if (slots[i].item_id == item_id) {")
    line(
        "            UINT8 to_remove = (remaining < slots[i].quantity) ? remaining : slots[i].quantity;"
    )
    line("            slots[i].quantity -= to_remove;")
    line("            remaining -= to_remove;")
    line("            if (slots[i].quantity == 0) {")
    line("                slots[i].item_id = ITEM_NONE;")
    line("            }")
    line("        }")
    line("    }")
    line("    ")
    line("    return remaining; // Returns amount that couldn't be removed")
    line("}")
    line()

    // Has item check
    line(
        "static UINT8 inventory_has_item(InventorySlot* slots, UINT8 max_slots, " +
            "UINT8 item_id, UINT8 quantity) {"
    )
    line("    UINT8 count = 0;")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == item_id) {")
    line("            count += slots[i].quantity;")
    line("            if (count >= quantity) return 1;")
    line("        }")
    line("    }")
    line("    return 0;")
    line("}")
    line()

    // Get item quantity
    line(
        "static UINT8 inventory_get_quantity(InventorySlot* slots, UINT8 max_slots, UINT8 item_id) {"
    )
    line("    UINT8 count = 0;")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == item_id) {")
    line("            count += slots[i].quantity;")
    line("        }")
    line("    }")
    line("    return count;")
    line("}")
    line()

    // Is inventory full
    line("static UINT8 inventory_is_full(InventorySlot* slots, UINT8 max_slots) {")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == ITEM_NONE) return 0;")
    line("    }")
    line("    return 1;")
    line("}")
    line()

    // Is inventory empty
    line("static UINT8 inventory_is_empty(InventorySlot* slots, UINT8 max_slots) {")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id != ITEM_NONE) return 0;")
    line("    }")
    line("    return 1;")
    line("}")
    line()

    // Empty slots count
    line("static UINT8 inventory_empty_slots(InventorySlot* slots, UINT8 max_slots) {")
    line("    UINT8 count = 0;")
    line("    for (UINT8 i = 0; i < max_slots; i++) {")
    line("        if (slots[i].item_id == ITEM_NONE) count++;")
    line("    }")
    line("    return count;")
    line("}")
    line()

    // Clear inventory
    line("static void inventory_clear(InventorySlot* slots, UINT8 max_slots) {")
    line("    inventory_init(slots, max_slots);")
    line("}")
    line()
}

/** Generate item use dispatch function for battle system. */
private fun CodeGenerator.generateItemUseDispatch() {
    val usableItems = game.items.filter { it.usableInBattle && it.onUseStatements.isNotEmpty() }
    if (game.items.isEmpty()) return

    line("// === Item Use Dispatch (for battle system) ===")
    line()

    // Item target context variable for onUse statements
    line("// Item target context for battle item usage")
    line("static UINT8 _item_target_idx = 0u;")
    line()

    // Bank this function - it's 80+ lines and called only when items are used
    setBank(codeBankCombat)
    line("// Execute item effect on target")
    line("void _execute_item_use(UINT8 item_id, UINT8 target) BANKED {")
    indent++
    line("_item_target_idx = target;")
    line()

    if (usableItems.isEmpty()) {
        line("// No items with onUse blocks defined")
        line("(void)item_id;")
    } else {
        line("switch (item_id) {")
        indent++

        for (item in usableItems) {
            line("case ITEM_${item.id.uppercase()}: {")
            indent++

            // Generate the onUse statements
            for (stmt in item.onUseStatements) {
                generateItemUseStatement(stmt)
            }

            line("break;")
            indent--
            line("}")
        }

        line("default:")
        indent++
        line("break;")
        indent--

        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate a single item use statement, handling special _item_target references. */
private fun CodeGenerator.generateItemUseStatement(stmt: IRStatement) {
    // Item onUse statements use _item_target as owner, which we need to map to _item_target_idx
    when (stmt) {
        is io.github.gbkt.core.ir.IRStatModify -> {
            // Replace _item_target with _item_target_idx for stat modifications
            if (stmt.ownerName == "_item_target") {
                val statName = stmt.statType.name
                when (stmt.statType) {
                    io.github.gbkt.core.ir.StatType.HP -> {
                        if (stmt.op == io.github.gbkt.core.ir.AssignOp.ADD) {
                            // Use combat_heal for adding HP
                            line("// Heal target HP")
                            line("_combat_heal(_item_target_idx, ${generateExpr(stmt.value)});")
                        } else {
                            // Direct modification for other ops
                            line("// Modify target HP")
                            line(
                                "_party_modify_stat(_item_target_idx, STAT_HP, ${if (stmt.op == io.github.gbkt.core.ir.AssignOp.SUB) "-" else ""}${generateExpr(stmt.value)});"
                            )
                        }
                    }
                    io.github.gbkt.core.ir.StatType.SP -> {
                        line("// Modify target SP")
                        line(
                            "_party_modify_stat(_item_target_idx, STAT_SP, ${if (stmt.op == io.github.gbkt.core.ir.AssignOp.SUB) "-" else ""}${generateExpr(stmt.value)});"
                        )
                    }
                    else -> {
                        line("// Modify target $statName")
                        line(
                            "_party_modify_stat(_item_target_idx, STAT_$statName, ${if (stmt.op == io.github.gbkt.core.ir.AssignOp.SUB) "-" else ""}${generateExpr(stmt.value)});"
                        )
                    }
                }
            } else {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRStatClamp -> {
            // Clamping is handled by _party_modify_stat which already clamps
            if (stmt.ownerName != "_item_target") {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRStatRestorePercent -> {
            if (stmt.ownerName == "_item_target") {
                val statName = stmt.statType.name
                line("// Restore ${stmt.percent}% of target max $statName")
                line("{")
                indent++
                line("UINT16 max_val = _combatant_${statName.lowercase()}_max[_item_target_idx];")
                line("UINT16 restore = max_val * ${stmt.percent}u / 100u;")
                line("_party_modify_stat(_item_target_idx, STAT_$statName, (INT16)restore);")
                indent--
                line("}")
            } else {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRApplyStatusEffect -> {
            if (stmt.targetName == "_item_target") {
                line("// Apply status effect ${stmt.effectName} to target")
                line("_status_apply(_item_target_idx, ${stmt.effectId}u);")
            } else {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRClearStatusEffect -> {
            if (stmt.targetName == "_item_target") {
                line("// Clear status effect ${stmt.effectName} from target")
                line("_status_clear_effect(_item_target_idx, ${stmt.effectId}u);")
            } else {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRClearAllStatusEffects -> {
            if (stmt.targetName == "_item_target") {
                line("// Clear all status effects from target")
                line("_status_clear_debuffs(_item_target_idx);")
            } else {
                generateStatement(stmt)
            }
        }
        is io.github.gbkt.core.ir.IRRaw -> {
            // Replace _item_target in raw code with _item_target_idx
            val processedCode = stmt.code.replace("_item_target", "_item_target_idx")
            line(processedCode)
        }
        else -> {
            // Fallback to generic statement generation
            generateStatement(stmt)
        }
    }
}

/** Generate inventory initialization code (called in setup). */
internal fun CodeGenerator.generateInventoryInit() {
    if (game.inventories.isEmpty()) return

    line("    // Initialize inventories")
    for (inventory in game.inventories) {
        line("    inventory_init(${inventory.id}_slots, ${inventory.id.uppercase()}_MAX_SLOTS);")

        // Add starting items
        for (stack in inventory.startingItems) {
            line(
                "    inventory_add_item(${inventory.id}_slots, ${inventory.id.uppercase()}_MAX_SLOTS, " +
                    "ITEM_${stack.item.id.uppercase()}, ${stack.quantity});"
            )
        }
    }
    line()
}

/**
 * Handle item-related IR statements.
 *
 * @return true if this was an item statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateItemStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRInventoryAddItem -> {
            generateInventoryAddItem(stmt)
            true
        }
        is IRInventoryRemoveItem -> {
            generateInventoryRemoveItem(stmt)
            true
        }
        is IRInventoryUseItem -> {
            generateInventoryUseItem(stmt)
            true
        }
        is IRInventoryClear -> {
            generateInventoryClear(stmt)
            true
        }
        // Equipment statements are handled by EquipmentCodegen.kt
        else -> false
    }

private fun CodeGenerator.generateInventoryAddItem(stmt: IRInventoryAddItem) {
    val invName = stmt.inventory.id
    val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
    val itemConst = "ITEM_${stmt.item.id.uppercase()}"
    lineWithSource(
        "inventory_add_item(${invName}_slots, $invMaxSlots, $itemConst, ${stmt.quantity});",
        stmt.sourceLocation,
        invName,
    )
}

private fun CodeGenerator.generateInventoryRemoveItem(stmt: IRInventoryRemoveItem) {
    val invName = stmt.inventory.id
    val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
    val itemConst = "ITEM_${stmt.item.id.uppercase()}"
    lineWithSource(
        "inventory_remove_item(${invName}_slots, $invMaxSlots, $itemConst, ${stmt.quantity});",
        stmt.sourceLocation,
        invName,
    )
}

private fun CodeGenerator.generateInventoryUseItem(stmt: IRInventoryUseItem) {
    val invName = stmt.inventory.id
    val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
    val itemConst = "ITEM_${stmt.item.id.uppercase()}"

    blockWithSource(
        "if (inventory_has_item(${invName}_slots, $invMaxSlots, $itemConst, 1))",
        stmt.sourceLocation,
        invName,
    ) {
        line("inventory_remove_item(${invName}_slots, $invMaxSlots, $itemConst, 1);")

        // Generate onUse statements
        if (stmt.item.onUseStatements.isNotEmpty()) {
            for (onUseStmt in stmt.item.onUseStatements) {
                generateStatement(onUseStmt)
            }
        }
    }
}

private fun CodeGenerator.generateInventoryClear(stmt: IRInventoryClear) {
    val invName = stmt.inventory.id
    val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
    lineWithSource("inventory_clear(${invName}_slots, $invMaxSlots);", stmt.sourceLocation, invName)
}

// Equipment item handling moved to EquipmentCodegen.kt

/** Generate item variable name for an inventory slot reference. */
internal fun inventorySlotVar(inventory: Inventory, slotIndex: Int): String =
    "${inventory.id}_slots[$slotIndex]"
