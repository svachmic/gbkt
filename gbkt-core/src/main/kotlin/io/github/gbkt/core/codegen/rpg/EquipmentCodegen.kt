/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.IREquipItem
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetEffectiveStat
import io.github.gbkt.core.ir.IRGetEquipmentBonus
import io.github.gbkt.core.ir.IRGetEquippedItem
import io.github.gbkt.core.ir.IRHasEquippedItem
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRUnequipItem
import io.github.gbkt.core.rpg.EquipmentSlot
import io.github.gbkt.core.rpg.StatBonusType

// =============================================================================
// EQUIPMENT CODE GENERATION
// =============================================================================

/**
 * Get all equipment slots (built-in + custom) for this game.
 *
 * Returns built-in slots first, then custom slots in registration order.
 */
private fun CodeGenerator.getAllEquipmentSlots(): List<EquipmentSlot> {
    return EquipmentSlot.BUILT_IN_SLOTS + game.equipmentSlots
}

/**
 * Generate equipment system code.
 *
 * Creates:
 * - Equipment slot constants
 * - Per-character equipment arrays
 * - Equipment stat bonus lookup tables
 * - Equipment management functions
 */
internal fun CodeGenerator.generateEquipmentSystem() {
    val characters = game.characters
    if (characters.isEmpty()) return

    // Check if any items have equipment slots
    val hasEquipment = game.items.any { it.equipSlot != null }
    if (!hasEquipment) return

    line("// =============================================================================")
    line("// EQUIPMENT SYSTEM")
    line("// =============================================================================")
    line()

    // Generate slot constants
    generateEquipmentSlotConstants()

    // Generate stat bonus type constants
    generateStatBonusTypeConstants()

    // Generate per-character equipment arrays
    generateCharacterEquipmentArrays(characters)

    // Generate item stat bonus lookup tables
    generateItemStatBonusTables()

    // Generate equipment helper functions
    generateEquipmentFunctions()
}

/** Generate equipment slot constants. */
private fun CodeGenerator.generateEquipmentSlotConstants() {
    val allSlots = getAllEquipmentSlots()
    line("// Equipment slot constants")
    for (slot in allSlots) {
        // Use the slot ID directly since custom slots have auto-assigned IDs
        val safeName = slot.name.uppercase().replace(" ", "_").replace("-", "_")
        line("#define EQUIP_SLOT_$safeName ${slot.id}u")
    }
    line("#define EQUIP_SLOT_COUNT ${allSlots.size}u")
    line("#define EQUIP_NONE 255u")
    line()
}

/** Generate stat bonus type constants. */
private fun CodeGenerator.generateStatBonusTypeConstants() {
    line("// Stat bonus type constants for equipment")
    for ((index, type) in StatBonusType.entries.withIndex()) {
        line("#define EQUIP_STAT_${type.name} ${index}u")
    }
    line("#define EQUIP_STAT_COUNT ${StatBonusType.entries.size}u")
    line()
}

/** Generate per-character equipment arrays. */
private fun CodeGenerator.generateCharacterEquipmentArrays(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    val allSlots = getAllEquipmentSlots()
    line("// Character equipment arrays (item index per slot, 255 = empty)")
    line("static UINT8 _char_equipment[${characters.size}][EQUIP_SLOT_COUNT];")
    line()

    // Generate initialization function
    line("// Initialize character equipment to starting loadout")
    line("static void _equipment_init(void) {")
    indent++

    for ((charIdx, character) in characters.withIndex()) {
        val equipment = character.equipment
        if (equipment != null) {
            val equipped = equipment.getAll()
            for (slot in allSlots) {
                val item = equipped[slot]
                val itemIndex =
                    if (item != null) {
                        game.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    } else {
                        255
                    }
                val safeName = slot.name.uppercase().replace(" ", "_").replace("-", "_")
                line("_char_equipment[$charIdx][EQUIP_SLOT_$safeName] = ${itemIndex}u;")
            }
        } else {
            // No equipment, set all slots to empty
            for (slot in allSlots) {
                val safeName = slot.name.uppercase().replace(" ", "_").replace("-", "_")
                line("_char_equipment[$charIdx][EQUIP_SLOT_$safeName] = EQUIP_NONE;")
            }
        }
    }

    indent--
    line("}")
    line()
}

/** Generate item stat bonus lookup tables. */
private fun CodeGenerator.generateItemStatBonusTables() {
    val equipmentItems = game.items.filter { it.equipSlot != null }
    if (equipmentItems.isEmpty()) return

    line("// Item stat bonus lookup tables")
    line("// Index by item_id, returns bonus for each stat type")

    // Create a table for each stat type
    for (statType in StatBonusType.entries) {
        line("static const INT8 _item_${statType.name.lowercase()}_bonus[${game.items.size}] = {")
        indent++
        val bonusLine =
            game.items.joinToString(", ") { item ->
                val bonus = item.statBonuses[statType] ?: 0
                "$bonus"
            }
        line(bonusLine)
        indent--
        line("};")
    }
    line()
}

/** Generate equipment helper functions. */
private fun CodeGenerator.generateEquipmentFunctions() {
    line("// =============================================================================")
    line("// EQUIPMENT FUNCTIONS")
    line("// =============================================================================")
    line()

    // Equip item
    line("// Equip an item to a character slot")
    line("static void _equipment_equip(UINT8 char_idx, UINT8 slot, UINT8 item_idx) {")
    indent++
    line("_char_equipment[char_idx][slot] = item_idx;")
    indent--
    line("}")
    line()

    // Unequip item
    line("// Unequip item from a character slot")
    line("static UINT8 _equipment_unequip(UINT8 char_idx, UINT8 slot) {")
    indent++
    line("UINT8 old_item = _char_equipment[char_idx][slot];")
    line("_char_equipment[char_idx][slot] = EQUIP_NONE;")
    line("return old_item;")
    indent--
    line("}")
    line()

    // Get equipped item
    line("// Get item equipped in a slot (returns 255 if empty)")
    line("static UINT8 _equipment_get(UINT8 char_idx, UINT8 slot) {")
    indent++
    line("return _char_equipment[char_idx][slot];")
    indent--
    line("}")
    line()

    // Check if slot has equipment
    line("// Check if a slot has equipment")
    line("static UINT8 _equipment_has(UINT8 char_idx, UINT8 slot) {")
    indent++
    line("return _char_equipment[char_idx][slot] != EQUIP_NONE ? 1u : 0u;")
    indent--
    line("}")
    line()

    // Calculate total equipment bonus for a stat
    line("// Calculate total equipment stat bonus for a character")
    line("static INT16 _equipment_get_bonus(UINT8 char_idx, UINT8 stat_type) {")
    indent++
    line("INT16 total = 0;")
    line("for (UINT8 slot = 0u; slot < EQUIP_SLOT_COUNT; slot++) {")
    indent++
    line("UINT8 item_idx = _char_equipment[char_idx][slot];")
    line("if (item_idx == EQUIP_NONE) continue;")
    line("switch (stat_type) {")
    indent++
    for (statType in StatBonusType.entries) {
        line(
            "case EQUIP_STAT_${statType.name}: total += _item_${statType.name.lowercase()}_bonus[item_idx]; break;"
        )
    }
    indent--
    line("}")
    indent--
    line("}")
    line("return total;")
    indent--
    line("}")
    line()

    // Get effective stat (base + equipment)
    line("// Get character's effective stat (base + equipment bonuses)")
    line("static UINT16 _equipment_effective_stat(UINT8 char_idx, UINT8 stat_type) {")
    indent++
    line("INT16 base = _party_get_stat(char_idx, stat_type);")
    line("INT16 bonus = _equipment_get_bonus(char_idx, stat_type);")
    line("INT16 result = base + bonus;")
    line("if (result < 0) return 0u;")
    line("if (result > 999) return 999u;")
    line("return (UINT16)result;")
    indent--
    line("}")
    line()
}

// =============================================================================
// EQUIPMENT STATEMENT GENERATION
// =============================================================================

/** Convert slot name to a safe C identifier. */
private fun slotToCName(slot: EquipmentSlot): String =
    slot.name.uppercase().replace(" ", "_").replace("-", "_")

/**
 * Handle equipment-related IR statements.
 *
 * @return true if this was an equipment statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateEquipmentStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IREquipItem -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == stmt.characterName }.coerceAtLeast(0)
            val itemIdx = game.items.indexOfFirst { it.id == stmt.itemId }.coerceAtLeast(0)
            val slotName = slotToCName(stmt.slot)
            lineWithSource(
                "// Equip ${stmt.itemId} to ${stmt.characterName}",
                stmt.sourceLocation,
                stmt.characterName,
            )
            line("_equipment_equip(${charIdx}u, EQUIP_SLOT_$slotName, ${itemIdx}u);")
            true
        }
        is IRUnequipItem -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == stmt.characterName }.coerceAtLeast(0)
            val slotName = slotToCName(stmt.slot)
            lineWithSource(
                "// Unequip ${stmt.slot.name} from ${stmt.characterName}",
                stmt.sourceLocation,
                stmt.characterName,
            )
            line("_equipment_unequip(${charIdx}u, EQUIP_SLOT_$slotName);")
            true
        }
        else -> false
    }

// =============================================================================
// EQUIPMENT EXPRESSION GENERATION
// =============================================================================

/**
 * Generate C expression for equipment-related queries.
 *
 * @return the C expression string, or null if not an equipment expression
 */
internal fun CodeGenerator.generateEquipmentExpr(expr: IRExpression): String? =
    when (expr) {
        is IRGetEquippedItem -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            val slotName = slotToCName(expr.slot)
            "_equipment_get(${charIdx}u, EQUIP_SLOT_$slotName)"
        }
        is IRHasEquippedItem -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            val slotName = slotToCName(expr.slot)
            "_equipment_has(${charIdx}u, EQUIP_SLOT_$slotName)"
        }
        is IRGetEquipmentBonus -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            "_equipment_get_bonus(${charIdx}u, EQUIP_STAT_${expr.statType.name})"
        }
        is IRGetEffectiveStat -> {
            val charIdx =
                game.characters.indexOfFirst { it.name == expr.characterName }.coerceAtLeast(0)
            "_equipment_effective_stat(${charIdx}u, EQUIP_STAT_${expr.statType.name})"
        }
        else -> null
    }
