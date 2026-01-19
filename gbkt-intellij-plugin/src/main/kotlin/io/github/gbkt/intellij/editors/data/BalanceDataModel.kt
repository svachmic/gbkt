/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.editors.data

/**
 * Model for balance data tables used in game development.
 *
 * Supports common RPG data patterns like:
 * - Experience curves (level -> exp required)
 * - Stat progression (level -> HP, ATK, DEF, etc.)
 * - Tier multipliers (C/B/A/S tier scaling)
 * - Damage formulas (base damage, scaling factors)
 */
data class BalanceDataModel(
    val name: String,
    val type: DataType,
    val columns: List<ColumnDefinition>,
    val rows: MutableList<MutableList<Any>>,
) {
    /** Types of balance data tables. */
    enum class DataType {
        EXP_CURVE,
        STAT_PROGRESSION,
        TIER_MULTIPLIER,
        DAMAGE_FORMULA,
        CUSTOM,
    }

    /** Column definition with type and constraints. */
    data class ColumnDefinition(
        val name: String,
        val type: ColumnType,
        val minValue: Double = 0.0,
        val maxValue: Double = Double.MAX_VALUE,
        val isKey: Boolean = false,
    )

    /** Supported column types. */
    enum class ColumnType {
        INT,
        FLOAT,
        STRING,
        TIER,
    }

    /** Gets the value at the specified row and column. */
    fun getValue(row: Int, col: Int): Any? {
        return rows.getOrNull(row)?.getOrNull(col)
    }

    /** Sets the value at the specified row and column. */
    fun setValue(row: Int, col: Int, value: Any) {
        if (row < rows.size && col < columns.size) {
            rows[row][col] = value
        }
    }

    /** Adds a new row with default values. */
    fun addRow() {
        val newRow =
            columns.map { col ->
                when (col.type) {
                    ColumnType.INT -> 0
                    ColumnType.FLOAT -> 0.0
                    ColumnType.STRING -> ""
                    ColumnType.TIER -> "C"
                }
            }
        rows.add(newRow.toMutableList())
    }

    /** Removes a row at the specified index. */
    fun removeRow(index: Int) {
        if (index in rows.indices) {
            rows.removeAt(index)
        }
    }

    /** Generates Kotlin code for this data table. */
    fun toKotlinCode(): String {
        val sb = StringBuilder()
        sb.append("object ${name}Data {\n")

        when (type) {
            DataType.EXP_CURVE -> generateExpCurveCode(sb)
            DataType.STAT_PROGRESSION -> generateStatProgressionCode(sb)
            DataType.TIER_MULTIPLIER -> generateTierMultiplierCode(sb)
            else -> generateGenericCode(sb)
        }

        sb.append("}\n")
        return sb.toString()
    }

    private fun generateExpCurveCode(sb: StringBuilder) {
        sb.append("    val expForLevel = intArrayOf(\n        ")
        sb.append(rows.map { it[1] }.joinToString(", "))
        sb.append("\n    )\n\n")
        sb.append(
            "    fun getExpForLevel(level: Int): Int = expForLevel.getOrElse(level - 1) { 0 }\n"
        )
    }

    private fun generateStatProgressionCode(sb: StringBuilder) {
        for ((index, col) in columns.withIndex()) {
            if (col.isKey) continue
            sb.append("    val ${col.name}ByLevel = intArrayOf(\n        ")
            sb.append(rows.map { it[index] }.joinToString(", "))
            sb.append("\n    )\n\n")
        }
    }

    private fun generateTierMultiplierCode(sb: StringBuilder) {
        sb.append("    enum class Tier(val multiplier: Float) {\n")
        for (row in rows) {
            val tier = row[0]
            val mult = row[1]
            sb.append("        $tier(${mult}f),\n")
        }
        sb.append("    }\n")
    }

    private fun generateGenericCode(sb: StringBuilder) {
        sb.append("    // Custom data table: ${columns.map { it.name }.joinToString(", ")}\n")
        sb.append("    val data = listOf(\n")
        for (row in rows) {
            sb.append("        listOf(${row.joinToString(", ")}),\n")
        }
        sb.append("    )\n")
    }

    companion object {
        /** Creates an experience curve table with preset values. */
        fun createExpCurve(name: String = "ExpCurve", maxLevel: Int = 99): BalanceDataModel {
            val columns =
                listOf(
                    ColumnDefinition(
                        "Level",
                        ColumnType.INT,
                        1.0,
                        maxLevel.toDouble(),
                        isKey = true,
                    ),
                    ColumnDefinition("Exp Required", ColumnType.INT, 0.0, 999999.0),
                )

            val rows =
                (1..maxLevel).map { level -> mutableListOf<Any>(level, calculateDefaultExp(level)) }

            return BalanceDataModel(name, DataType.EXP_CURVE, columns, rows.toMutableList())
        }

        /** Creates a stat progression table. */
        fun createStatProgression(
            name: String = "StatProgression",
            stats: List<String> = listOf("HP", "ATK", "DEF", "AGL"),
            maxLevel: Int = 99,
        ): BalanceDataModel {
            val columns =
                listOf(
                    ColumnDefinition(
                        "Level",
                        ColumnType.INT,
                        1.0,
                        maxLevel.toDouble(),
                        isKey = true,
                    )
                ) + stats.map { ColumnDefinition(it, ColumnType.INT, 1.0, 999.0) }

            val rows =
                (1..maxLevel).map { level ->
                    val baseStats =
                        stats.map { stat ->
                            when (stat) {
                                "HP" -> 10 + level * 5
                                "ATK" -> 5 + level * 2
                                "DEF" -> 5 + level * 2
                                "AGL" -> 5 + level
                                else -> level
                            }
                        }
                    (mutableListOf<Any>(level) + baseStats).toMutableList()
                }

            return BalanceDataModel(name, DataType.STAT_PROGRESSION, columns, rows.toMutableList())
        }

        /** Creates a tier multiplier table. */
        fun createTierMultiplier(name: String = "TierMultiplier"): BalanceDataModel {
            val columns =
                listOf(
                    ColumnDefinition("Tier", ColumnType.TIER, isKey = true),
                    ColumnDefinition("Multiplier", ColumnType.FLOAT, 0.1, 10.0),
                )

            val rows =
                mutableListOf(
                    mutableListOf<Any>("C", 1.0),
                    mutableListOf<Any>("B", 1.5),
                    mutableListOf<Any>("A", 2.0),
                    mutableListOf<Any>("S", 3.0),
                )

            return BalanceDataModel(name, DataType.TIER_MULTIPLIER, columns, rows)
        }

        private fun calculateDefaultExp(level: Int): Int {
            // Standard RPG curve: exp = base * level^1.5
            val base = 10
            return (base * Math.pow(level.toDouble(), 1.5)).toInt()
        }
    }
}
