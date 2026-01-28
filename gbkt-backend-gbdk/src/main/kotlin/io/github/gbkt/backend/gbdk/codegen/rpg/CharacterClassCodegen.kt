/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.rpg.CharacterClass
import io.github.gbkt.core.rpg.GrowthRate

// =============================================================================
// CHARACTER CLASS CODE GENERATION
// =============================================================================

/**
 * Generate character class system code.
 *
 * Creates:
 * - Class constants and lookup tables
 * - Stat modifier tables
 * - Growth rate tables
 * - Ability unlock tables
 * - Class promotion logic
 */
internal fun GBDKCodeGenerator.generateCharacterClassSystem() {
    val classes = game.characterClasses
    if (classes.isEmpty()) return

    line("// =============================================================================")
    line("// CHARACTER CLASS SYSTEM")
    line("// =============================================================================")
    line()

    // Generate class index constants
    generateClassConstants(classes)

    // Generate stat modifier tables
    generateStatModifierTables(classes)

    // Generate growth rate tables
    generateGrowthRateTables(classes)

    // Generate ability unlock tables
    generateClassAbilityTables(classes)

    // Generate promotion chain helpers
    val classesWithPromotion = classes.filter { it.promotesFrom != null }
    if (classesWithPromotion.isNotEmpty()) {
        generatePromotionSystem(classes, classesWithPromotion)
    }

    // Generate multi-class support
    val classesWithMulticlass = classes.filter { it.allowsMulticlass }
    if (classesWithMulticlass.isNotEmpty()) {
        generateMultiClassSystem(classes)
    }

    // Generate class helper functions
    generateClassHelperFunctions(classes)
}

/** Generate class constants. */
private fun GBDKCodeGenerator.generateClassConstants(classes: List<CharacterClass>) {
    line("// Class index constants")
    for ((index, charClass) in classes.withIndex()) {
        line("#define CLASS_${charClass.id.uppercase()} ${index}u")
    }
    line("#define CLASS_COUNT ${classes.size}u")
    line()
}

/** Generate stat modifier lookup tables. */
private fun GBDKCodeGenerator.generateStatModifierTables(classes: List<CharacterClass>) {
    line("// Stat modifier tables (percentage, 100 = 1.0x)")
    line("// Format: HP, SP, ATK, DEF, MATK, MDEF, AGL")
    line("static const UINT8 _class_stat_mods[CLASS_COUNT][7] = {")
    indent++

    for (charClass in classes) {
        val mods = charClass.statModifiers
        line(
            "{ ${mods.hp}u, ${mods.sp}u, ${mods.atk}u, ${mods.def}u, " +
                "${mods.matk}u, ${mods.mdef}u, ${mods.agl}u }, // ${charClass.displayName}"
        )
    }

    indent--
    line("};")
    line()

    // Generate helper function
    line("// Apply class stat modifiers to base value")
    line("static UINT16 _apply_class_mod(UINT8 class_id, UINT8 stat_type, UINT16 base_value) {")
    indent++
    line("if (class_id >= CLASS_COUNT) return base_value;")
    line("return (base_value * _class_stat_mods[class_id][stat_type]) / 100u;")
    indent--
    line("}")
    line()
}

/** Convert GrowthRate to numeric value for codegen. */
private fun growthRateToInt(rate: GrowthRate): Int =
    when (rate) {
        GrowthRate.NONE -> 0
        GrowthRate.LOW -> 3 // +1 per 3 levels
        GrowthRate.MEDIUM -> 5 // +1 per 2 levels
        GrowthRate.STANDARD -> 10 // +1 per level
        GrowthRate.HIGH -> 20 // +2 per level
        GrowthRate.VERY_HIGH -> 30 // +3 per level
    }

/** Generate growth rate lookup tables. */
private fun GBDKCodeGenerator.generateGrowthRateTables(classes: List<CharacterClass>) {
    // Check if any class has custom growth rates
    val hasGrowthRates = classes.any { it.growthRates != null }
    if (!hasGrowthRates) return

    line("// Growth rate tables (points per level, 8-bit)")
    line("// Format: HP, SP, ATK, DEF, MATK, MDEF, AGL")
    line("static const UINT8 _class_growth_rates[CLASS_COUNT][7] = {")
    indent++

    for (charClass in classes) {
        val growth = charClass.growthRates
        if (growth != null) {
            line(
                "{ ${growthRateToInt(growth.hp)}u, ${growthRateToInt(growth.sp)}u, " +
                    "${growthRateToInt(growth.atk)}u, ${growthRateToInt(growth.def)}u, " +
                    "${growthRateToInt(growth.matk)}u, ${growthRateToInt(growth.mdef)}u, " +
                    "${growthRateToInt(growth.agl)}u }, // ${charClass.displayName}"
            )
        } else {
            // Default growth rates
            line("{ 10u, 5u, 2u, 2u, 2u, 2u, 2u }, // ${charClass.displayName} (default)")
        }
    }

    indent--
    line("};")
    line()

    // Generate helper function
    line("// Get growth rate for stat")
    line("static UINT8 _get_class_growth(UINT8 class_id, UINT8 stat_type) {")
    indent++
    line("if (class_id >= CLASS_COUNT) return 0u;")
    line("return _class_growth_rates[class_id][stat_type];")
    indent--
    line("}")
    line()
}

/** Generate ability unlock tables for classes. */
private fun GBDKCodeGenerator.generateClassAbilityTables(classes: List<CharacterClass>) {
    // Check if any class has learned abilities
    val classesWithAbilities = classes.filter { it.learnedAbilities.isNotEmpty() }
    if (classesWithAbilities.isEmpty()) return

    // Calculate max abilities per class
    val maxAbilities = classesWithAbilities.maxOf { it.learnedAbilities.size }

    line("// Class ability unlock tables")
    line("#define MAX_CLASS_ABILITIES ${maxAbilities}u")
    line()

    // Generate ability unlock level table
    line("// Ability unlock levels per class (255 = not learned)")
    line("static const UINT8 _class_ability_levels[CLASS_COUNT][MAX_CLASS_ABILITIES] = {")
    indent++

    for (charClass in classes) {
        val abilities = charClass.learnedAbilities.sortedBy { it.learnLevel }
        val levels = abilities.map { it.learnLevel.toString() + "u" }.toMutableList()
        // Pad with 255 for unused slots
        while (levels.size < maxAbilities) {
            levels.add("255u")
        }
        line("{ ${levels.joinToString(", ")} }, // ${charClass.displayName}")
    }

    indent--
    line("};")
    line()

    // Generate ability ID table
    line("// Ability IDs per class (255 = none)")
    line("static const UINT8 _class_ability_ids[CLASS_COUNT][MAX_CLASS_ABILITIES] = {")
    indent++

    for (charClass in classes) {
        val abilities = charClass.learnedAbilities.sortedBy { it.learnLevel }
        val ids =
            abilities
                .map {
                    val abilityIndex = game.abilities.indexOfFirst { a -> a.id == it.ability.id }
                    if (abilityIndex >= 0) "${abilityIndex}u" else "255u"
                }
                .toMutableList()
        // Pad with 255 for unused slots
        while (ids.size < maxAbilities) {
            ids.add("255u")
        }
        line("{ ${ids.joinToString(", ")} }, // ${charClass.displayName}")
    }

    indent--
    line("};")
    line()

    // Generate helper function
    line("// Grant class abilities for level")
    line(
        "static void _grant_class_abilities_for_level(UINT8 char_idx, UINT8 class_id, UINT8 level) {"
    )
    indent++
    line("if (class_id >= CLASS_COUNT) return;")
    line("for (UINT8 i = 0u; i < MAX_CLASS_ABILITIES; i++) {")
    indent++
    line("if (_class_ability_levels[class_id][i] == level) {")
    indent++
    line("UINT8 ability_id = _class_ability_ids[class_id][i];")
    line("if (ability_id != 255u) {")
    indent++
    line("_char_grant_ability(char_idx, ability_id);")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate class promotion system. */
private fun GBDKCodeGenerator.generatePromotionSystem(
    allClasses: List<CharacterClass>,
    classesWithPromotion: List<CharacterClass>,
) {
    line("// =============================================================================")
    line("// CLASS PROMOTION SYSTEM")
    line("// =============================================================================")
    line()

    // Generate promotion requirements data
    for (charClass in classesWithPromotion) {
        val fromClass = charClass.promotesFrom ?: continue
        val chainName = "${fromClass.id}_${charClass.id}".uppercase()
        line("// Promotion: ${fromClass.displayName} -> ${charClass.displayName}")
        line("#define PROMO_${chainName}_MIN_LEVEL ${charClass.promotionLevel}u")

        for ((i, req) in charClass.promotionItems.withIndex()) {
            val itemIdx = game.items.indexOfFirst { it.id == req.itemId }
            if (itemIdx >= 0) {
                line("#define PROMO_${chainName}_ITEM_${i} ${itemIdx}u")
                line("#define PROMO_${chainName}_QTY_${i} ${req.quantity}u")
            }
        }
    }
    line()

    // Generate promotion check function
    line("// Check if character can promote from current class to target class")
    line(
        "static UINT8 _can_promote(UINT8 char_idx, UINT8 from_class, UINT8 to_class, UINT8 level) {"
    )
    indent++
    line("(void)char_idx; // May be used for character-specific checks")
    line("// Check promotion chains")
    line("switch (to_class) {")
    indent++

    for (charClass in classesWithPromotion) {
        val fromClass = charClass.promotesFrom ?: continue
        val fromIdx = allClasses.indexOf(fromClass)
        val toIdx = allClasses.indexOf(charClass)
        if (fromIdx >= 0 && toIdx >= 0) {
            line("case CLASS_${charClass.id.uppercase()}:")
            indent++
            line("if (from_class != CLASS_${fromClass.id.uppercase()}) return 0u;")
            line("return (level >= ${charClass.promotionLevel}u) ? 1u : 0u;")
            indent--
        }
    }

    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate promotion function
    line("// Promote character to new class")
    line("static void _promote_class(UINT8 char_idx, UINT8 new_class) {")
    indent++
    line("// Set new class (implementation depends on character storage)")
    line("// This would update the character's class field")
    line("// _char_set_class(char_idx, new_class);")
    line("(void)char_idx; // Suppress unused warning")
    line("(void)new_class;")
    indent--
    line("}")
    line()
}

/** Generate multi-class system. */
private fun GBDKCodeGenerator.generateMultiClassSystem(classes: List<CharacterClass>) {
    line("// =============================================================================")
    line("// MULTI-CLASS SYSTEM")
    line("// =============================================================================")
    line()

    // Find max class level from classes that allow multi-classing
    val maxClassLevel =
        classes.filter { it.allowsMulticlass }.maxOfOrNull { it.maxClassLevel } ?: 99

    line("// Max class level for multi-class tracking")
    line("#define MAX_CLASS_LEVEL ${maxClassLevel}u")
    line()

    line("// Secondary class tracking per character")
    line("// Format: class_id (255 = none)")
    line("static UINT8 _char_secondary_classes[MAX_PARTY_SIZE][2]; // Up to 2 secondary classes")
    line("static UINT8 _char_secondary_levels[MAX_PARTY_SIZE][2];")
    line()

    line("// Add secondary class to character")
    line("static UINT8 _add_secondary_class(UINT8 char_idx, UINT8 class_id) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return 0u;")
    line("for (UINT8 i = 0u; i < 2u; i++) {")
    indent++
    line("if (_char_secondary_classes[char_idx][i] == 255u) {")
    indent++
    line("_char_secondary_classes[char_idx][i] = class_id;")
    line("_char_secondary_levels[char_idx][i] = 1u;")
    line("return 1u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return 0u; // No slots available")
    indent--
    line("}")
    line()

    line("// Check if character has secondary class")
    line("static UINT8 _has_secondary_class(UINT8 char_idx, UINT8 class_id) {")
    indent++
    line("if (char_idx >= MAX_PARTY_SIZE) return 0u;")
    line("for (UINT8 i = 0u; i < 2u; i++) {")
    indent++
    line("if (_char_secondary_classes[char_idx][i] == class_id) return 1u;")
    indent--
    line("}")
    line("return 0u;")
    indent--
    line("}")
    line()
}

/** Generate class helper functions. */
private fun GBDKCodeGenerator.generateClassHelperFunctions(classes: List<CharacterClass>) {
    line("// =============================================================================")
    line("// CLASS HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    // Class name lookup
    line("// Class name lookup (for UI)")
    for (charClass in classes) {
        val escapedName = charClass.displayName.replace("\"", "\\\"")
        line("static const char _class_${charClass.id}_name[] = \"$escapedName\";")
    }
    line()

    line("static const char* const _class_names[CLASS_COUNT] = {")
    indent++
    line(classes.joinToString(", ") { "_class_${it.id}_name" })
    indent--
    line("};")
    line()

    line("// Get class name")
    line("static const char* _get_class_name(UINT8 class_id) {")
    indent++
    line("if (class_id >= CLASS_COUNT) return \"???\";")
    line("return _class_names[class_id];")
    indent--
    line("}")
    line()

    // Check if class is available (based on tier or prerequisites)
    line("// Check if class is unlocked for character")
    line("static UINT8 _is_class_available(UINT8 char_idx, UINT8 class_id) {")
    indent++
    line("(void)char_idx; // May be used for character-specific checks")
    line("if (class_id >= CLASS_COUNT) return 0u;")
    line("// Add unlock checks here based on game requirements")
    line("return 1u;")
    indent--
    line("}")
    line()
}
