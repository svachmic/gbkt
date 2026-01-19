/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the Combat Formulas system.
 *
 * Validates:
 * - Hit formula strategy configuration
 * - Critical hit formula configuration
 * - Damage variance configuration
 * - Fumble configuration
 * - DSL builder functionality
 */
class CombatFormulasTest {

    // =========================================================================
    // HIT FORMULA STRATEGIES
    // =========================================================================

    @Test
    fun `default combat formulas has always hit strategy`() {
        val formulas = combatFormulas {}

        assertEquals(HitFormulaStrategy.AlwaysHit, formulas.hitFormula)
    }

    @Test
    fun `d20HitRoll sets D20Based strategy`() {
        val formulas = combatFormulas { d20HitRoll(baseAC = 12) }

        val hitFormula = formulas.hitFormula
        assertTrue(hitFormula is HitFormulaStrategy.D20Based)
        assertEquals(12, (hitFormula as HitFormulaStrategy.D20Based).baseAC)
    }

    @Test
    fun `percentageHitChance sets PercentageBased strategy`() {
        val formulas = combatFormulas {
            percentageHitChance(baseChance = 80, minChance = 25, maxChance = 90, perDiff = 3)
        }

        val hitFormula = formulas.hitFormula
        assertTrue(hitFormula is HitFormulaStrategy.PercentageBased)
        val percentageFormula = hitFormula as HitFormulaStrategy.PercentageBased
        assertEquals(80, percentageFormula.baseChance)
        assertEquals(25, percentageFormula.minChance)
        assertEquals(90, percentageFormula.maxChance)
        assertEquals(3, percentageFormula.perDiff)
    }

    @Test
    fun `agilityBasedHit sets AgilityBased strategy`() {
        val formulas = combatFormulas {
            agilityBasedHit(baseChance = 70, minChance = 15, maxChance = 85)
        }

        val hitFormula = formulas.hitFormula
        assertTrue(hitFormula is HitFormulaStrategy.AgilityBased)
        val agilityFormula = hitFormula as HitFormulaStrategy.AgilityBased
        assertEquals(70, agilityFormula.baseChance)
        assertEquals(15, agilityFormula.minChance)
        assertEquals(85, agilityFormula.maxChance)
    }

    @Test
    fun `alwaysHits sets AlwaysHit strategy`() {
        val formulas = combatFormulas {
            d20HitRoll() // First set something else
            alwaysHits() // Then override
        }

        assertEquals(HitFormulaStrategy.AlwaysHit, formulas.hitFormula)
    }

    // =========================================================================
    // CRITICAL FORMULA STRATEGIES
    // =========================================================================

    @Test
    fun `default combat formulas has no critical hits`() {
        val formulas = combatFormulas {}

        assertEquals(CriticalFormulaStrategy.NoCrits, formulas.criticalFormula)
    }

    @Test
    fun `criticalChance sets FlatChance strategy`() {
        val formulas = combatFormulas { criticalChance(10) }

        val critFormula = formulas.criticalFormula
        assertTrue(critFormula is CriticalFormulaStrategy.FlatChance)
        assertEquals(10, (critFormula as CriticalFormulaStrategy.FlatChance).chance)
    }

    @Test
    fun `criticalOnHighRoll sets HighRoll strategy`() {
        val formulas = combatFormulas { criticalOnHighRoll(threshold = 18, dieSize = 20) }

        val critFormula = formulas.criticalFormula
        assertTrue(critFormula is CriticalFormulaStrategy.HighRoll)
        val highRoll = critFormula as CriticalFormulaStrategy.HighRoll
        assertEquals(18, highRoll.threshold)
        assertEquals(20, highRoll.dieSize)
    }

    @Test
    fun `noCriticalHits sets NoCrits strategy`() {
        val formulas = combatFormulas {
            criticalChance(5) // First enable crits
            noCriticalHits() // Then disable
        }

        assertEquals(CriticalFormulaStrategy.NoCrits, formulas.criticalFormula)
    }

    @Test
    fun `criticalMultiplier sets multiplier value`() {
        val formulas = combatFormulas { criticalMultiplier(200) }

        assertEquals(200, formulas.critMultiplier)
    }

    @Test
    fun `default critical multiplier is 150`() {
        val formulas = combatFormulas {}

        assertEquals(150, formulas.critMultiplier)
    }

    // =========================================================================
    // DAMAGE VARIANCE STRATEGIES
    // =========================================================================

    @Test
    fun `default combat formulas has no variance`() {
        val formulas = combatFormulas {}

        assertEquals(DamageVarianceStrategy.NoVariance, formulas.damageVariance)
    }

    @Test
    fun `damageVariance sets PercentageVariance strategy`() {
        val formulas = combatFormulas { damageVariance(30) }

        val variance = formulas.damageVariance
        assertTrue(variance is DamageVarianceStrategy.PercentageVariance)
        assertEquals(30, (variance as DamageVarianceStrategy.PercentageVariance).variancePercent)
    }

    @Test
    fun `damageMultiplierRange sets MultiplierTable strategy`() {
        val formulas = combatFormulas { damageMultiplierRange(min = 80, max = 120) }

        val variance = formulas.damageVariance
        assertTrue(variance is DamageVarianceStrategy.MultiplierTable)
        val table = variance as DamageVarianceStrategy.MultiplierTable
        assertEquals(80, table.min)
        assertEquals(120, table.max)
    }

    @Test
    fun `noVariance sets NoVariance strategy`() {
        val formulas = combatFormulas {
            damageVariance(25) // First enable variance
            noVariance() // Then disable
        }

        assertEquals(DamageVarianceStrategy.NoVariance, formulas.damageVariance)
    }

    // =========================================================================
    // FUMBLE CONFIGURATION
    // =========================================================================

    @Test
    fun `default combat formulas has fumble disabled`() {
        val formulas = combatFormulas {}

        assertFalse(formulas.fumbleEnabled)
    }

    @Test
    fun `enableFumble enables fumble with threshold`() {
        val formulas = combatFormulas { enableFumble(threshold = 3) }

        assertTrue(formulas.fumbleEnabled)
        assertEquals(3, formulas.fumbleThreshold)
    }

    @Test
    fun `enableFumble default threshold is 2`() {
        val formulas = combatFormulas { enableFumble() }

        assertTrue(formulas.fumbleEnabled)
        assertEquals(2, formulas.fumbleThreshold)
    }

    // =========================================================================
    // COMPLETE CONFIGURATION EXAMPLES
    // =========================================================================

    @Test
    fun `D&D-style combat formulas configuration`() {
        val formulas = combatFormulas {
            // D&D-style hit rolls
            d20HitRoll(baseAC = 10)

            // Critical on natural 20
            criticalOnHighRoll(threshold = 20, dieSize = 20)
            criticalMultiplier(200) // 2x damage

            // ±12.5% damage variance
            damageVariance(25)

            // Fumble on natural 1
            enableFumble(threshold = 1)
        }

        assertTrue(formulas.hitFormula is HitFormulaStrategy.D20Based)
        assertTrue(formulas.criticalFormula is CriticalFormulaStrategy.HighRoll)
        assertEquals(200, formulas.critMultiplier)
        assertTrue(formulas.damageVariance is DamageVarianceStrategy.PercentageVariance)
        assertTrue(formulas.fumbleEnabled)
        assertEquals(1, formulas.fumbleThreshold)
    }

    @Test
    fun `simple RPG combat formulas configuration`() {
        val formulas = combatFormulas {
            alwaysHits() // No miss chance
            criticalChance(5) // 5% crit chance
            noVariance() // Exact damage
        }

        assertEquals(HitFormulaStrategy.AlwaysHit, formulas.hitFormula)
        assertTrue(formulas.criticalFormula is CriticalFormulaStrategy.FlatChance)
        assertEquals(DamageVarianceStrategy.NoVariance, formulas.damageVariance)
        assertFalse(formulas.fumbleEnabled)
    }

    @Test
    fun `Dragon-style combat formulas configuration`() {
        val formulas = combatFormulas {
            // Dragon uses percentage-based hit chance
            percentageHitChance(baseChance = 75, minChance = 20, maxChance = 95, perDiff = 2)

            // Critical on 14-15 of d16
            criticalOnHighRoll(threshold = 14, dieSize = 16)
            criticalMultiplier(150) // 1.5x damage

            // Multiplier table variance (like Dragon's damage_roll_modifier)
            damageMultiplierRange(min = 75, max = 125)

            // Fumble on 0-1 of d16
            enableFumble(threshold = 2)
        }

        val hitFormula = formulas.hitFormula as HitFormulaStrategy.PercentageBased
        assertEquals(75, hitFormula.baseChance)
        assertEquals(20, hitFormula.minChance)
        assertEquals(95, hitFormula.maxChance)

        val critFormula = formulas.criticalFormula as CriticalFormulaStrategy.HighRoll
        assertEquals(14, critFormula.threshold)
        assertEquals(16, critFormula.dieSize)

        val variance = formulas.damageVariance as DamageVarianceStrategy.MultiplierTable
        assertEquals(75, variance.min)
        assertEquals(125, variance.max)

        assertTrue(formulas.fumbleEnabled)
        assertEquals(2, formulas.fumbleThreshold)
    }

    // =========================================================================
    // HIT CHECK FUNCTION
    // =========================================================================

    @Test
    fun `hitCheck creates condition with attacker and defender names`() {
        val condition = hitCheck("hero", "goblin")

        // The condition wraps an IRHitCheck
        assertTrue(condition.ir is io.github.gbkt.core.ir.IRHitCheck)
        val hitCheck = condition.ir as io.github.gbkt.core.ir.IRHitCheck
        assertEquals("hero", hitCheck.attackerName)
        assertEquals("goblin", hitCheck.defenderName)
    }
}
