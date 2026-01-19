/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.ir.CustomStatType
import io.github.gbkt.core.ir.StatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for the custom stats system.
 *
 * Validates:
 * - Custom stat type creation
 * - Stats builder with custom stats
 * - Stats builder with aliases
 * - Maximum custom stat limits
 */
class CustomStatsTest {

    // =========================================================================
    // CUSTOM STAT TYPE
    // =========================================================================

    @Test
    fun `custom stat type has correct properties`() {
        val luck =
            CustomStatType(name = "luck", displayName = "LCK", cType = "UINT8", defaultMax = 99)

        assertEquals("luck", luck.name)
        assertEquals("LCK", luck.displayName)
        assertEquals("UINT8", luck.cType)
        assertEquals(99, luck.defaultMax)
        assertEquals("luck", luck.varNameSuffix)
    }

    @Test
    fun `custom stat type defaults to UINT8 and max 255`() {
        val faith = CustomStatType(name = "FAITH", displayName = "FAI")

        assertEquals("UINT8", faith.cType)
        assertEquals(255, faith.defaultMax)
    }

    @Test
    fun `custom stat type varNameSuffix is lowercase`() {
        val charisma = CustomStatType(name = "CHARISMA", displayName = "CHA")

        assertEquals("charisma", charisma.varNameSuffix)
    }

    // =========================================================================
    // STATS BUILDER - CUSTOM STATS
    // =========================================================================

    @Test
    fun `stats builder supports custom stats`() {
        val builder = StatsBuilder("hero")
        builder.hp(100)
        builder.custom("luck", "LCK", base = 10, max = 99)

        val stats = builder.build()

        assertEquals(1, stats.stats.size, "Should have 1 built-in stat")
        assertEquals(1, stats.customStats.size, "Should have 1 custom stat")

        val customStat = stats.customStats[0]
        assertEquals("luck", customStat.customType.name)
        assertEquals("LCK", customStat.customType.displayName)
        assertEquals(10, customStat.baseValue)
        assertEquals(99, customStat.maxValue)
    }

    @Test
    fun `stats builder allows up to 3 custom stats`() {
        val builder = StatsBuilder("hero")
        builder.custom("luck", base = 10)
        builder.custom("faith", base = 5)
        builder.custom("charisma", base = 8)

        val stats = builder.build()
        assertEquals(3, stats.customStats.size)
    }

    @Test
    fun `stats builder rejects more than 3 custom stats`() {
        val builder = StatsBuilder("hero")
        builder.custom("luck", base = 10)
        builder.custom("faith", base = 5)
        builder.custom("charisma", base = 8)

        val exception =
            assertFailsWith<IllegalArgumentException> { builder.custom("wisdom", base = 7) }
        assertTrue(exception.message!!.contains("Maximum of 3 custom stats"))
    }

    @Test
    fun `stats builder rejects blank custom stat name`() {
        val builder = StatsBuilder("hero")

        assertFailsWith<IllegalArgumentException> { builder.custom("", base = 10) }
    }

    @Test
    fun `stats builder rejects base greater than max`() {
        val builder = StatsBuilder("hero")

        assertFailsWith<IllegalArgumentException> { builder.custom("luck", base = 100, max = 50) }
    }

    @Test
    fun `stats builder supports 16-bit custom stats`() {
        val builder = StatsBuilder("hero")
        builder.custom("karma", base = 1000, max = 65535, use16Bit = true)

        val stats = builder.build()
        val customStat = stats.customStats[0]

        assertEquals("UINT16", customStat.customType.cType)
        assertEquals(65535, customStat.maxValue)
    }

    @Test
    fun `custom stat default display name is first 3 chars uppercase`() {
        val builder = StatsBuilder("hero")
        builder.custom("wisdom", base = 10)

        val stats = builder.build()
        assertEquals("WIS", stats.customStats[0].customType.displayName)
    }

    // =========================================================================
    // STATS BUILDER - ALIASES
    // =========================================================================

    @Test
    fun `stats builder supports aliases`() {
        val builder = StatsBuilder("hero")
        builder.hp(100)
        builder.alias(StatType.HP, "LIFE")
        builder.sp(50)
        builder.alias(StatType.SP, "MANA")

        val stats = builder.build()

        assertEquals(2, stats.aliases.size)
        assertEquals("LIFE", stats.aliases[StatType.HP])
        assertEquals("MANA", stats.aliases[StatType.SP])
    }

    @Test
    fun `stats builder aliases can override`() {
        val builder = StatsBuilder("hero")
        builder.hp(100)
        builder.alias(StatType.HP, "LIFE")
        builder.alias(StatType.HP, "HEALTH") // Override

        val stats = builder.build()

        assertEquals("HEALTH", stats.aliases[StatType.HP])
    }

    // =========================================================================
    // STATS DEFINITION
    // =========================================================================

    @Test
    fun `stats definition contains all components`() {
        val builder = StatsBuilder("hero")
        builder.hp(100)
        builder.atk(20)
        builder.alias(StatType.HP, "LIFE")
        builder.custom("luck", base = 10)

        val stats = builder.build()

        assertEquals("hero", stats.ownerName)
        assertEquals(2, stats.stats.size)
        assertEquals(1, stats.customStats.size)
        assertEquals(1, stats.aliases.size)
    }

    @Test
    fun `stats definition defaults to empty custom stats and aliases`() {
        val builder = StatsBuilder("hero")
        builder.hp(100)

        val stats = builder.build()

        assertTrue(stats.customStats.isEmpty())
        assertTrue(stats.aliases.isEmpty())
    }
}
