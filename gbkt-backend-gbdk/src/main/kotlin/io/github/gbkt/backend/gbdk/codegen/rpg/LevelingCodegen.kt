/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRAddExp
import io.github.gbkt.core.ir.IRApplyStatGrowth
import io.github.gbkt.core.ir.IRCheckLevelUp
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetCurrentExp
import io.github.gbkt.core.ir.IRGetExpForLevel
import io.github.gbkt.core.ir.IRGetExpToNextLevel
import io.github.gbkt.core.ir.IRGetLevel
import io.github.gbkt.core.ir.IRIsMaxLevel
import io.github.gbkt.core.ir.IRSetLevel
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.GrowthRate
import io.github.gbkt.core.rpg.StatGrowthType
import io.github.gbkt.core.rpg.calculateExpForLevel

// =============================================================================
// LEVELING CODE GENERATION
// =============================================================================

/**
 * Generate leveling system code.
 *
 * Creates:
 * - Experience point variables per character
 * - Level variables per character
 * - Experience tables (cumulative exp required per level)
 * - Leveling functions (add_exp, check_level_up)
 */
internal fun GBDKCodeGenerator.generateLevelingSystem() {
    val charactersWithLeveling = game.characters.filter { it.hasLeveling }
    if (charactersWithLeveling.isEmpty()) return

    line("// =============================================================================")
    line("// LEVELING SYSTEM")
    line("// =============================================================================")
    line()

    // Generate constants
    generateLevelingConstants(charactersWithLeveling)

    // Generate experience tables
    generateExpTables(charactersWithLeveling)

    // Generate stat growth tables
    generateGrowthTables(charactersWithLeveling)

    // Generate character level/exp variables
    generateLevelingVariables(charactersWithLeveling)

    // Generate leveling functions
    generateLevelingFunctions(charactersWithLeveling)
}

/** Generate leveling-related constants. */
private fun GBDKCodeGenerator.generateLevelingConstants(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    line("// Leveling constants")
    for (character in characters) {
        val config = character.levelingConfig ?: continue
        val prefix = character.name.uppercase()
        line("#define ${prefix}_MAX_LEVEL ${config.maxLevel}u")
        line("#define ${prefix}_START_LEVEL ${config.startLevel}u")
    }
    line()
}

/** Generate experience requirement tables. */
private fun GBDKCodeGenerator.generateExpTables(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    line("// Experience tables (cumulative exp required for each level)")
    for (character in characters) {
        val config = character.levelingConfig ?: continue
        val prefix = character.name.lowercase()
        val maxLevel = config.maxLevel

        // Generate exp requirements for each level
        val expValues =
            (0..maxLevel).map { level ->
                calculateExpForLevel(level, config.expCurve, config.baseExp)
            }

        // Determine exp table type based on max value to avoid overflow warnings
        val maxExpValue = expValues.maxOrNull() ?: 0
        val expTableType =
            when {
                maxExpValue <= 255 -> "UINT8"
                maxExpValue <= 65535 -> "UINT16"
                else -> "UINT32"
            }

        line("static const $expTableType _${prefix}_exp_table[${maxLevel + 1}] = {")
        indent++

        // Output in rows of 8
        expValues.chunked(8).forEach { chunk -> line(chunk.joinToString(", ") { "${it}u" } + ",") }

        indent--
        line("};")
    }
    line()
}

/** Generate stat growth tables. */
@Suppress("LoopWithTooManyJumpStatements") // Filtering loops are readable with continues
private fun GBDKCodeGenerator.generateGrowthTables(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    val hasGrowth = characters.any { it.levelingConfig?.growthRates?.isNotEmpty() == true }
    if (!hasGrowth) return

    line("// Stat growth values per growth rate")
    line("// Returns bonus stat points for reaching a given level")
    line("static UINT8 _growth_for_level(UINT8 level, UINT8 rate) {")
    indent++
    line("if (level <= 1) return 0;")
    line("switch (rate) {")
    indent++
    line("case 0: return 0; // NONE")
    line("case 1: return (level - 1) / 3; // LOW")
    line("case 2: return (level - 1) / 2; // MEDIUM")
    line("case 3: return level - 1; // STANDARD")
    line("case 4: return (level - 1) * 2; // HIGH")
    line("case 5: return (level - 1) * 3; // VERY_HIGH")
    line("default: return 0;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate growth rate constants for each character's stats
    for (character in characters) {
        val config = character.levelingConfig ?: continue
        if (config.growthRates.isEmpty()) continue

        val prefix = character.name.uppercase()
        for ((stat, rate) in config.growthRates) {
            val rateValue = rate.ordinal
            line("#define ${prefix}_GROWTH_${stat.name} ${rateValue}u")
        }
    }
    line()
}

/** Generate per-character level and exp variables. */
private fun GBDKCodeGenerator.generateLevelingVariables(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    line("// Character level and experience variables")
    for (character in characters) {
        val config = character.levelingConfig ?: continue
        val prefix = character.name.lowercase()
        val maxLevel = config.maxLevel

        // Calculate max exp value to determine variable type
        val maxExpValue = calculateExpForLevel(maxLevel, config.expCurve, config.baseExp)
        val expType =
            when {
                maxExpValue <= 255 -> "UINT8"
                maxExpValue <= 65535 -> "UINT16"
                else -> "UINT32"
            }

        line("static UINT8 _${prefix}_level = ${config.startLevel}u;")
        line("static $expType _${prefix}_exp = 0u;")
    }
    line()
}

/** Generate leveling helper functions. */
private fun GBDKCodeGenerator.generateLevelingFunctions(
    characters: List<io.github.gbkt.core.rpg.Character>
) {
    line("// =============================================================================")
    line("// LEVELING FUNCTIONS")
    line("// =============================================================================")
    line()

    // Forward declaration for ability unlock function (defined in ability system)
    if (
        game.abilities.isNotEmpty() &&
            characters.any { it.levelingConfig?.autoLearnAbilities == true }
    ) {
        line("// Forward declaration for ability unlock (defined in ability system)")
        line("static void _grant_abilities_for_level(UINT8 char_idx, UINT8 level);")
        line()
    }

    // Generate functions for each character in correct dependency order:
    // 1. apply_growth (called by check_level_up)
    // 2. on_level_up (called by check_level_up)
    // 3. check_level_up (called by add_exp)
    // 4. add_exp
    // 5. set_level
    for (character in characters) {
        val config = character.levelingConfig ?: continue
        val prefix = character.name.lowercase()
        val prefixUpper = character.name.uppercase()
        val maxLevel = config.maxLevel

        // Calculate max exp value to determine type for function parameters
        val maxExpValue = calculateExpForLevel(maxLevel, config.expCurve, config.baseExp)
        val expType =
            when {
                maxExpValue <= 255 -> "UINT8"
                maxExpValue <= 65535 -> "UINT16"
                else -> "UINT32"
            }

        // 1. Apply growth function if there are growth rates (called by check_level_up)
        if (config.growthRates.isNotEmpty()) {
            line("// Apply stat growth for ${character.name} level up")
            line("static void _${prefix}_apply_growth(void) {")
            indent++
            val charIdx = game.characters.indexOfFirst { it.name == character.name }
            for ((stat, rate) in config.growthRates) {
                if (rate == GrowthRate.NONE) continue
                val statIdx =
                    when (stat) {
                        StatGrowthType.MAX_HP -> 0
                        StatGrowthType.MAX_SP -> 1
                        StatGrowthType.ATK -> 2
                        StatGrowthType.DEF -> 3
                        StatGrowthType.MATK -> 4
                        StatGrowthType.MDEF -> 5
                        StatGrowthType.AGL -> 6
                    }
                line("// ${stat.name} growth")
                line(
                    "_party_stats[${charIdx}u][${statIdx}u] += _growth_for_level(_${prefix}_level, ${prefixUpper}_GROWTH_${stat.name});"
                )
            }
            indent--
            line("}")
            line()
        }

        // 2. On level up callback function if there are statements (called by check_level_up)
        if (config.onLevelUpStatements.isNotEmpty()) {
            line("// Level up callback for ${character.name}")
            line("static void _${prefix}_on_level_up(void) {")
            indent++
            for (stmt in config.onLevelUpStatements) {
                // Substitute _levelup_char with actual character name
                generateLevelUpStatement(stmt, character.name)
            }
            indent--
            line("}")
            line()
        }

        // 3. Check level up function (called by add_exp)
        line("// Check and process level up for ${character.name}")
        line("static void _${prefix}_check_level_up(void) {")
        indent++
        line("while (_${prefix}_level < ${prefixUpper}_MAX_LEVEL &&")
        line("       _${prefix}_exp >= _${prefix}_exp_table[_${prefix}_level + 1]) {")
        indent++
        line("_${prefix}_level++;")
        // Apply stat growth if configured
        if (config.growthRates.isNotEmpty()) {
            line("_${prefix}_apply_growth();")
        }
        // Execute onLevelUp statements
        if (config.onLevelUpStatements.isNotEmpty()) {
            line("_${prefix}_on_level_up();")
        }
        // Grant abilities that unlock at this level
        if (game.abilities.isNotEmpty() && config.autoLearnAbilities) {
            val charIdx = game.characters.indexOfFirst { it.name == character.name }
            line("// Grant abilities unlocked at this level")
            line("_grant_abilities_for_level(${charIdx}u, _${prefix}_level);")
        }
        indent--
        line("}")
        indent--
        line("}")
        line()

        // 4. Add experience function
        line("// Add experience to ${character.name}")
        line("static void _${prefix}_add_exp($expType amount) {")
        indent++
        line("if (_${prefix}_level >= ${prefixUpper}_MAX_LEVEL) return;")
        line("_${prefix}_exp += amount;")
        line("_${prefix}_check_level_up();")
        indent--
        line("}")
        line()

        // 5. Set level directly function
        line("// Set ${character.name} level directly")
        line("static void _${prefix}_set_level(UINT8 level) {")
        indent++
        line("if (level > ${prefixUpper}_MAX_LEVEL) level = ${prefixUpper}_MAX_LEVEL;")
        line("if (level < 1) level = 1;")
        line("_${prefix}_level = level;")
        line("_${prefix}_exp = _${prefix}_exp_table[level];")
        indent--
        line("}")
        line()
    }
}

/** Generate a level-up statement, substituting _levelup_char placeholder. */
private fun GBDKCodeGenerator.generateLevelUpStatement(stmt: IRStatement, characterName: String) {
    // For now, just call generateStatement for the transformed statement
    // The _levelup_char substitution is handled by a dedicated mechanism
    generateStatement(stmt)
}

// =============================================================================
// LEVELING STATEMENT GENERATION
// =============================================================================

/**
 * Handle leveling-related IR statements.
 *
 * @return true if this was a leveling statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateLevelingStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRAddExp -> {
            val prefix = stmt.characterName.lowercase()
            lineWithSource(
                "_${prefix}_add_exp(${stmt.amount}u);",
                stmt.sourceLocation,
                stmt.characterName,
            )
            true
        }
        is IRCheckLevelUp -> {
            val prefix = stmt.characterName.lowercase()
            lineWithSource("_${prefix}_check_level_up();", stmt.sourceLocation, stmt.characterName)
            true
        }
        is IRSetLevel -> {
            val prefix = stmt.characterName.lowercase()
            lineWithSource(
                "_${prefix}_set_level(${stmt.level}u);",
                stmt.sourceLocation,
                stmt.characterName,
            )
            true
        }
        is IRApplyStatGrowth -> {
            val prefix = stmt.characterName.lowercase()
            lineWithSource("_${prefix}_apply_growth();", stmt.sourceLocation, stmt.characterName)
            true
        }
        else -> false
    }

// =============================================================================
// LEVELING EXPRESSION GENERATION
// =============================================================================

/**
 * Generate C expression for leveling-related queries.
 *
 * @return the C expression string, or null if not a leveling expression
 */
internal fun GBDKCodeGenerator.generateLevelingExpr(expr: IRExpression): String? =
    when (expr) {
        is IRGetLevel -> {
            val prefix = expr.characterName.lowercase()
            "_${prefix}_level"
        }
        is IRGetCurrentExp -> {
            val prefix = expr.characterName.lowercase()
            "_${prefix}_exp"
        }
        is IRGetExpToNextLevel -> {
            val prefix = expr.characterName.lowercase()
            val prefixUpper = expr.characterName.uppercase()
            "(_${prefix}_level < ${prefixUpper}_MAX_LEVEL ? " +
                "(_${prefix}_exp_table[_${prefix}_level + 1] - _${prefix}_exp) : 0u)"
        }
        is IRIsMaxLevel -> {
            val prefix = expr.characterName.lowercase()
            val prefixUpper = expr.characterName.uppercase()
            "(_${prefix}_level >= ${prefixUpper}_MAX_LEVEL)"
        }
        is IRGetExpForLevel -> {
            val prefix = expr.characterName.lowercase()
            "_${prefix}_exp_table[${expr.level}u]"
        }
        else -> null
    }
