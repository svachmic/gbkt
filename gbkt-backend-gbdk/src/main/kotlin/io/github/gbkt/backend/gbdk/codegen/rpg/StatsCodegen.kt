/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateExpr
import io.github.gbkt.core.ir.IRStatClamp
import io.github.gbkt.core.ir.IRStatDamage
import io.github.gbkt.core.ir.IRStatModify
import io.github.gbkt.core.ir.IRStatRestorePercent
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.StatType

// =============================================================================
// RPG STATS CODE GENERATION
// =============================================================================

/**
 * Generate variable declarations for character stats.
 *
 * For each character with stats defined, generates:
 * - Current value variables (e.g., hero_hp)
 * - Max value variables (e.g., hero_hp_max)
 */
internal fun GBDKCodeGenerator.generateStatsVariables() {
    if (game.characters.isEmpty()) return

    val charactersWithStats = game.characters.filter { it.hasStats }
    if (charactersWithStats.isEmpty()) return

    line("// === RPG Character Stats ===")
    for (character in charactersWithStats) {
        val stats = character.stats
        line("// ${character.name} stats")

        // Generate built-in stats
        for (stat in stats.definition.stats) {
            val varName = "${character.name}_${stat.type.name.lowercase()}"
            val cType = stat.type.cType
            line("static $cType $varName = ${stat.baseValue}u;")
            line("static $cType ${varName}_max = ${stat.maxValue}u;")
        }

        // Generate custom stats
        for (customStat in stats.definition.customStats) {
            val varName = "${character.name}_${customStat.customType.varNameSuffix}"
            val cType = customStat.customType.cType
            line("static $cType $varName = ${customStat.baseValue}u;")
            line("static $cType ${varName}_max = ${customStat.maxValue}u;")
        }
    }
    line()
}

/**
 * Handle stat-related IR statements.
 *
 * @return true if this was a stat statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateStatStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRStatModify -> {
            generateStatModify(stmt)
            true
        }
        is IRStatClamp -> {
            generateStatClamp(stmt)
            true
        }
        is IRStatDamage -> {
            generateStatDamage(stmt)
            true
        }
        is IRStatRestorePercent -> {
            generateStatRestore(stmt)
            true
        }
        else -> false
    }

private fun GBDKCodeGenerator.generateStatModify(stmt: IRStatModify) {
    val varName = statVarName(stmt.ownerName, stmt.statType, stmt.useMax)
    val value = generateExpr(stmt.value)
    lineWithSource("$varName ${stmt.op.c} $value;", stmt.sourceLocation, varName)
}

private fun GBDKCodeGenerator.generateStatClamp(stmt: IRStatClamp) {
    val currentVar = statVarName(stmt.ownerName, stmt.statType, useMax = false)
    val maxVar = statVarName(stmt.ownerName, stmt.statType, useMax = true)
    lineWithSource(
        "if ($currentVar > $maxVar) $currentVar = $maxVar;",
        stmt.sourceLocation,
        currentVar,
    )
}

private fun GBDKCodeGenerator.generateStatDamage(stmt: IRStatDamage) {
    val varName = statVarName(stmt.ownerName, stmt.statType, useMax = false)
    val damage = generateExpr(stmt.damage)
    // Use safe subtraction to prevent underflow (floor at 0)
    lineWithSource(
        "if ($varName >= $damage) $varName -= $damage; else $varName = 0;",
        stmt.sourceLocation,
        varName,
    )
}

private fun GBDKCodeGenerator.generateStatRestore(stmt: IRStatRestorePercent) {
    val currentVar = statVarName(stmt.ownerName, stmt.statType, useMax = false)
    val maxVar = statVarName(stmt.ownerName, stmt.statType, useMax = true)
    // Calculate restoration amount: (max * percent) / 100
    // Then clamp to max
    lineWithSource(
        "$currentVar += ($maxVar * ${stmt.percent}u) / 100u;",
        stmt.sourceLocation,
        currentVar,
    )
    line("if ($currentVar > $maxVar) $currentVar = $maxVar;")
}

/** Generate C variable name for a stat. */
internal fun statVarName(ownerName: String, statType: StatType, useMax: Boolean): String {
    val base = "${ownerName}_${statType.name.lowercase()}"
    return if (useMax) "${base}_max" else base
}

/**
 * Generate aspect modifier table for characters.
 *
 * Each character can have resistances/vulnerabilities to different damage aspects. The table is
 * indexed as _char_aspect_mod[character_idx][aspect_id].
 */
internal fun GBDKCodeGenerator.generateCharacterAspectTable() {
    val characters = game.characters
    if (characters.isEmpty()) return

    val aspectCount = io.github.gbkt.core.rpg.Aspect.entries.size

    // Check if any character has aspect modifiers defined
    val hasAspectMods = characters.any { it.aspectProfile != null }
    if (!hasAspectMods) {
        // No characters have aspect profiles - generate a simple constant
        line("// No characters have aspect modifiers defined")
        line("// _get_char_aspect_mod returns 100 (normal) for all")
        line("static UINT8 _get_char_aspect_mod(UINT8 char_idx, UINT8 aspect) {")
        indent++
        line("(void)char_idx; (void)aspect;")
        line("return 100u; // Normal damage")
        indent--
        line("}")
        line()
        return
    }

    line("// =============================================================================")
    line("// CHARACTER ASPECT MODIFIERS")
    line("// =============================================================================")
    line()
    line(
        "// Aspect modifiers per character (0=immune, 50=resist, 100=normal, 150=weak, 200=vulnerable)"
    )
    line(
        "// Aspects: PHYSICAL=0, MAGICAL=1, FIRE=2, ICE=3, LIGHTNING=4, EARTH=5, WIND=6, WATER=7, LIGHT=8, DARK=9, PURE=10"
    )
    line("static const UINT8 _char_aspect_mod[${characters.size}][$aspectCount] = {")
    indent++

    for (character in characters) {
        val mods =
            io.github.gbkt.core.rpg.Aspect.entries.map { aspect ->
                character.getAspectModifier(aspect).multiplier
            }
        line("{ ${mods.joinToString(", ") { "${it}u" }} }, // ${character.name}")
    }

    indent--
    line("};")
    line()

    // Generate helper function to get aspect modifier for a character
    line("// Get aspect modifier for a character")
    line("static UINT8 _get_char_aspect_mod(UINT8 char_idx, UINT8 aspect) {")
    indent++
    line("if (char_idx >= ${characters.size}u || aspect >= ${aspectCount}u) return 100u;")
    line("return _char_aspect_mod[char_idx][aspect];")
    indent--
    line("}")
    line()
}
