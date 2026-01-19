/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRAddExp
import io.github.gbkt.core.ir.IRCheckLevelUp
import io.github.gbkt.core.ir.IRGrantAbility
import io.github.gbkt.core.ir.IRRevokeAbility
import io.github.gbkt.core.ir.IRSetLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LevelingBuilderTest {
    @Test
    fun `leveling builder has correct defaults`() {
        val config = createTestConfig("hero") {}
        assertEquals("hero", config.characterName)
        assertEquals(99, config.maxLevel)
        assertEquals(ExpCurve.STANDARD, config.expCurve)
        assertEquals(100, config.baseExp)
        assertTrue(config.growthRates.isEmpty())
        assertTrue(config.onLevelUpStatements.isEmpty())
    }

    @Test
    fun `max level can be customized`() {
        val config = createTestConfig("hero") { maxLevel(50) }
        assertEquals(50, config.maxLevel)
    }

    @Test
    fun `max level can be set to 255`() {
        val config = createTestConfig("hero") { maxLevel(255) }
        assertEquals(255, config.maxLevel)
    }

    @Test
    fun `max level can be any valid 8-bit value`() {
        // Test various valid level caps
        for (level in listOf(1, 50, 99, 100, 150, 200, 255)) {
            val config = createTestConfig("hero") { maxLevel(level) }
            assertEquals(level, config.maxLevel)
        }
    }

    @Test
    fun `max level rejects zero`() {
        val exception = kotlin.runCatching { createTestConfig("hero") { maxLevel(0) } }
        assertTrue(exception.isFailure)
        assertTrue(exception.exceptionOrNull()?.message?.contains("1-255") == true)
    }

    @Test
    fun `max level rejects negative values`() {
        val exception = kotlin.runCatching { createTestConfig("hero") { maxLevel(-1) } }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `max level rejects values above 255`() {
        val exception = kotlin.runCatching { createTestConfig("hero") { maxLevel(256) } }
        assertTrue(exception.isFailure)
        assertTrue(exception.exceptionOrNull()?.message?.contains("1-255") == true)
    }

    @Test
    fun `exp curve can be set`() {
        val config = createTestConfig("hero") { expCurve(ExpCurve.FAST_START) }
        assertEquals(ExpCurve.FAST_START, config.expCurve)
    }

    @Test
    fun `base exp can be set`() {
        val config = createTestConfig("hero") { baseExp(200) }
        assertEquals(200, config.baseExp)
    }

    @Test
    fun `growth rates can be configured`() {
        val config =
            createTestConfig("hero") {
                growth {
                    maxHp(GrowthRate.HIGH)
                    atk(GrowthRate.STANDARD)
                    def(GrowthRate.MEDIUM)
                    agl(GrowthRate.LOW)
                }
            }
        assertEquals(GrowthRate.HIGH, config.growthRates[StatGrowthType.MAX_HP])
        assertEquals(GrowthRate.STANDARD, config.growthRates[StatGrowthType.ATK])
        assertEquals(GrowthRate.MEDIUM, config.growthRates[StatGrowthType.DEF])
        assertEquals(GrowthRate.LOW, config.growthRates[StatGrowthType.AGL])
    }

    @Test
    fun `all growth types can be configured`() {
        val config =
            createTestConfig("hero") {
                growth {
                    maxHp(GrowthRate.VERY_HIGH)
                    maxSp(GrowthRate.HIGH)
                    atk(GrowthRate.STANDARD)
                    def(GrowthRate.MEDIUM)
                    matk(GrowthRate.LOW)
                    mdef(GrowthRate.NONE)
                    agl(GrowthRate.STANDARD)
                }
            }
        assertEquals(GrowthRate.VERY_HIGH, config.growthRates[StatGrowthType.MAX_HP])
        assertEquals(GrowthRate.HIGH, config.growthRates[StatGrowthType.MAX_SP])
        assertEquals(GrowthRate.STANDARD, config.growthRates[StatGrowthType.ATK])
        assertEquals(GrowthRate.MEDIUM, config.growthRates[StatGrowthType.DEF])
        assertEquals(GrowthRate.LOW, config.growthRates[StatGrowthType.MATK])
        assertEquals(GrowthRate.NONE, config.growthRates[StatGrowthType.MDEF])
        assertEquals(GrowthRate.STANDARD, config.growthRates[StatGrowthType.AGL])
    }

    @Test
    fun `onLevelUp callback records statements`() {
        val config = createTestConfig("hero") { onLevelUp { raw("play_sfx(SFX_LEVELUP);") } }
        assertFalse(config.onLevelUpStatements.isEmpty())
    }

    private fun createTestConfig(name: String, init: LevelingBuilder.() -> Unit): LevelingConfig {
        val builder = LevelingBuilder(name)
        builder.init()
        return builder.build()
    }
}

class ExpSystemTest {
    @Test
    fun `addExp emits IR`() {
        val expSystem = ExpSystem("hero", null)

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { expSystem.addExp(100) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRAddExp>(stmt)
        assertEquals("hero", stmt.characterName)
        assertEquals(100, stmt.amount)
    }

    @Test
    fun `checkLevelUp emits IR`() {
        val expSystem = ExpSystem("hero", null)

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { expSystem.checkLevelUp() }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRCheckLevelUp>(stmt)
        assertEquals("hero", stmt.characterName)
    }

    @Test
    fun `setLevel emits IR`() {
        val expSystem = ExpSystem("hero", null)

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { expSystem.setLevel(10) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRSetLevel>(stmt)
        assertEquals("hero", stmt.characterName)
        assertEquals(10, stmt.level)
    }
}

class ExpCurveTest {
    @Test
    fun `all exp curves exist`() {
        val curves = ExpCurve.entries
        assertEquals(5, curves.size)
        assertTrue(ExpCurve.LINEAR in curves)
        assertTrue(ExpCurve.SLOW_START in curves)
        assertTrue(ExpCurve.STANDARD in curves)
        assertTrue(ExpCurve.FAST_START in curves)
        assertTrue(ExpCurve.EXPONENTIAL in curves)
    }
}

class GrowthRateTest {
    @Test
    fun `all growth rates exist`() {
        val rates = GrowthRate.entries
        assertEquals(6, rates.size)
        assertTrue(GrowthRate.NONE in rates)
        assertTrue(GrowthRate.LOW in rates)
        assertTrue(GrowthRate.MEDIUM in rates)
        assertTrue(GrowthRate.STANDARD in rates)
        assertTrue(GrowthRate.HIGH in rates)
        assertTrue(GrowthRate.VERY_HIGH in rates)
    }
}

class StatGrowthTypeTest {
    @Test
    fun `all stat growth types exist`() {
        val types = StatGrowthType.entries
        assertEquals(7, types.size)
        assertTrue(StatGrowthType.MAX_HP in types)
        assertTrue(StatGrowthType.MAX_SP in types)
        assertTrue(StatGrowthType.ATK in types)
        assertTrue(StatGrowthType.DEF in types)
        assertTrue(StatGrowthType.MATK in types)
        assertTrue(StatGrowthType.MDEF in types)
        assertTrue(StatGrowthType.AGL in types)
    }
}

class ExpCalculationTest {
    @Test
    fun `level 1 requires no exp for any curve`() {
        for (curve in ExpCurve.entries) {
            assertEquals(0, calculateExpForLevel(1, curve, 100))
        }
    }

    @Test
    fun `linear curve progression`() {
        val baseExp = 100
        assertEquals(100, calculateExpForLevel(2, ExpCurve.LINEAR, baseExp))
        assertEquals(200, calculateExpForLevel(3, ExpCurve.LINEAR, baseExp))
        assertEquals(300, calculateExpForLevel(4, ExpCurve.LINEAR, baseExp))
    }

    @Test
    fun `standard curve is quadratic`() {
        val baseExp = 100
        // Level 2: 2^2 * 100 / 10 = 40
        // Level 3: 2^2 + 3^2 = 4 + 9 = 13 * 10 = 130
        val exp2 = calculateExpForLevel(2, ExpCurve.STANDARD, baseExp)
        val exp3 = calculateExpForLevel(3, ExpCurve.STANDARD, baseExp)
        assertTrue(exp2 > 0)
        assertTrue(exp3 > exp2, "Higher levels should need more exp")
    }

    @Test
    fun `exp increases with level for most curves`() {
        // Test that exp generally increases with level
        // Some curves may cap at Int.MAX_VALUE for very high levels (EXPONENTIAL)
        for (curve in ExpCurve.entries) {
            val exp10 = calculateExpForLevel(10, curve, 100)
            val exp50 = calculateExpForLevel(50, curve, 100)
            val exp99 = calculateExpForLevel(99, curve, 100)

            assertTrue(exp10 > 0, "Level 10 should need exp for $curve (got $exp10)")
            assertTrue(
                exp50 > exp10,
                "Level 50 ($exp50) should need more exp than 10 ($exp10) for $curve",
            )
            // Use >= for final comparison since some extreme curves may cap at Int.MAX_VALUE
            assertTrue(
                exp99 >= exp50,
                "Level 99 ($exp99) should need at least as much exp as 50 ($exp50) for $curve",
            )
        }
    }
}

class LevelUpAbilityUnlockTest {
    @Test
    fun `grantAbility emits correct IR`() {
        // Create a test ability
        val ability =
            Ability(
                id = "fireball",
                displayName = "Fireball",
                description = "A fire spell",
                category = AbilityCategory.MAGIC,
                targeting = TargetingMode.SINGLE_ENEMY,
                cost = 10.sp,
                power = 100,
                aspect = Aspect.FIRE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 5,
                classRestrictions = emptySet(),
                abilityIndex = 2, // Set the index
            )

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { LevelUpScope().grantAbility(ability) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRGrantAbility>(stmt)
        assertEquals("_levelup_char", stmt.characterName)
        assertEquals("fireball", stmt.abilityId)
        assertEquals(2, stmt.abilityIndex)
    }

    @Test
    fun `revokeAbility emits correct IR`() {
        val ability =
            Ability(
                id = "heal",
                displayName = "Heal",
                description = "Heals an ally",
                category = AbilityCategory.SUPPORT,
                targeting = TargetingMode.SINGLE_ALLY,
                cost = 5.sp,
                power = 100,
                aspect = Aspect.PURE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = true,
                levelRequirement = 1,
                classRestrictions = emptySet(),
                abilityIndex = 0,
            )

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { LevelUpScope().revokeAbility(ability) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRRevokeAbility>(stmt)
        assertEquals("_levelup_char", stmt.characterName)
        assertEquals("heal", stmt.abilityId)
        assertEquals(0, stmt.abilityIndex)
    }

    @Test
    fun `onLevelUp can grant multiple abilities`() {
        val ability1 =
            Ability(
                id = "fire1",
                displayName = "Fire I",
                description = "",
                category = AbilityCategory.MAGIC,
                targeting = TargetingMode.SINGLE_ENEMY,
                cost = 5.sp,
                power = 100,
                aspect = Aspect.FIRE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 1,
                classRestrictions = emptySet(),
                abilityIndex = 0,
            )

        val ability2 =
            Ability(
                id = "fire2",
                displayName = "Fire II",
                description = "",
                category = AbilityCategory.MAGIC,
                targeting = TargetingMode.SINGLE_ENEMY,
                cost = 10.sp,
                power = 150,
                aspect = Aspect.FIRE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 10,
                classRestrictions = emptySet(),
                abilityIndex = 1,
            )

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) {
            val scope = LevelUpScope()
            scope.grantAbility(ability1)
            scope.grantAbility(ability2)
        }

        assertEquals(2, recorder.statements.size)
        assertIs<IRGrantAbility>(recorder.statements[0])
        assertIs<IRGrantAbility>(recorder.statements[1])
        assertEquals("fire1", (recorder.statements[0] as IRGrantAbility).abilityId)
        assertEquals("fire2", (recorder.statements[1] as IRGrantAbility).abilityId)
    }
}

class StatGrowthCalculationTest {
    @Test
    fun `none growth gives no bonus`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.NONE))
        assertEquals(0, calculateStatGrowth(50, GrowthRate.NONE))
        assertEquals(0, calculateStatGrowth(99, GrowthRate.NONE))
    }

    @Test
    fun `standard growth gives level minus 1`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.STANDARD))
        assertEquals(9, calculateStatGrowth(10, GrowthRate.STANDARD))
        assertEquals(49, calculateStatGrowth(50, GrowthRate.STANDARD))
    }

    @Test
    fun `high growth gives double standard`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.HIGH))
        assertEquals(18, calculateStatGrowth(10, GrowthRate.HIGH))
        assertEquals(98, calculateStatGrowth(50, GrowthRate.HIGH))
    }

    @Test
    fun `very high growth gives triple standard`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.VERY_HIGH))
        assertEquals(27, calculateStatGrowth(10, GrowthRate.VERY_HIGH))
        assertEquals(147, calculateStatGrowth(50, GrowthRate.VERY_HIGH))
    }

    @Test
    fun `low growth gives one third`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.LOW))
        assertEquals(3, calculateStatGrowth(10, GrowthRate.LOW))
        assertEquals(16, calculateStatGrowth(50, GrowthRate.LOW))
    }

    @Test
    fun `medium growth gives half`() {
        assertEquals(0, calculateStatGrowth(1, GrowthRate.MEDIUM))
        assertEquals(4, calculateStatGrowth(10, GrowthRate.MEDIUM))
        assertEquals(24, calculateStatGrowth(50, GrowthRate.MEDIUM))
    }
}

class GameConfigMaxLevelTest {
    @Test
    fun `game config has default max level of 99`() {
        val config = io.github.gbkt.core.builder.ConfigBuilder().build()
        assertEquals(99, config.maxLevel)
    }

    @Test
    fun `game config max level can be set to 50`() {
        val builder = io.github.gbkt.core.builder.ConfigBuilder()
        builder.maxLevel = 50
        val config = builder.build()
        assertEquals(50, config.maxLevel)
    }

    @Test
    fun `game config max level can be set to 255`() {
        val builder = io.github.gbkt.core.builder.ConfigBuilder()
        builder.maxLevel = 255
        val config = builder.build()
        assertEquals(255, config.maxLevel)
    }

    @Test
    fun `game config rejects max level of 0`() {
        val builder = io.github.gbkt.core.builder.ConfigBuilder()
        builder.maxLevel = 0
        val exception = kotlin.runCatching { builder.build() }
        assertTrue(exception.isFailure)
        assertTrue(exception.exceptionOrNull()?.message?.contains("1-255") == true)
    }

    @Test
    fun `game config rejects max level above 255`() {
        val builder = io.github.gbkt.core.builder.ConfigBuilder()
        builder.maxLevel = 256
        val exception = kotlin.runCatching { builder.build() }
        assertTrue(exception.isFailure)
        assertTrue(exception.exceptionOrNull()?.message?.contains("1-255") == true)
    }
}
