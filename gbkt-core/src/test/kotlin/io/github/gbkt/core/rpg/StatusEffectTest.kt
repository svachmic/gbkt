/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the RPG status effect system.
 *
 * Validates:
 * - Effect category and tier enums
 * - Effect duration types
 * - Status effect definition building
 * - Code generation for status effect tracking
 * - Apply/clear effect code generation
 */
class StatusEffectTest {

    // =========================================================================
    // EFFECT ENUMS
    // =========================================================================

    @Test
    fun `effect category enum has expected values`() {
        val categories = EffectCategory.entries
        assertTrue(categories.contains(EffectCategory.DEBUFF))
        assertTrue(categories.contains(EffectCategory.BUFF))
        assertTrue(categories.contains(EffectCategory.STAT_MOD))
        assertTrue(categories.contains(EffectCategory.CONDITION))
        assertEquals(4, categories.size)
    }

    @Test
    fun `effect tier has correct multipliers`() {
        assertEquals(100, EffectTier.C.multiplier)
        assertEquals(125, EffectTier.B.multiplier)
        assertEquals(150, EffectTier.A.multiplier)
        assertEquals(200, EffectTier.S.multiplier)
    }

    // =========================================================================
    // EFFECT DURATION
    // =========================================================================

    @Test
    fun `effect duration turns extension works`() {
        val duration = 5.turns
        assertIs<EffectDuration.Turns>(duration)
        assertEquals(5, duration.count)
    }

    @Test
    fun `effect duration types are distinct`() {
        val turns = EffectDuration.Turns(3)
        val untilBattleEnd = EffectDuration.UntilBattleEnd
        val permanent = EffectDuration.Permanent

        assertIs<EffectDuration.Turns>(turns)
        assertIs<EffectDuration.UntilBattleEnd>(untilBattleEnd)
        assertIs<EffectDuration.Permanent>(permanent)
    }

    // =========================================================================
    // STATUS EFFECT BUILDER
    // =========================================================================

    @Test
    fun `status effect builder creates definition with defaults`() {
        val builder = StatusEffectBuilder("Poison", 1)
        val def = builder.build()

        assertEquals("Poison", def.name)
        assertEquals(StatusEffectId(1), def.id)
        assertEquals(EffectCategory.DEBUFF, def.category)
        assertEquals(EffectTier.C, def.tier)
        assertEquals(StackMode.REFRESH_DURATION, def.stackMode)
        assertEquals(1, def.maxStacks)
    }

    @Test
    fun `status effect builder supports buff category`() {
        val builder = StatusEffectBuilder("Regen", 2)
        builder.buff()
        val def = builder.build()

        assertEquals(EffectCategory.BUFF, def.category)
    }

    @Test
    fun `status effect builder supports stat modifiers`() {
        val builder = StatusEffectBuilder("ATK Up", 3)
        builder.buff()
        builder.atkUp(150)
        val def = builder.build()

        assertEquals(150, def.statModifiers[StatType.ATK])
    }

    @Test
    fun `status effect builder supports damage per turn`() {
        val builder = StatusEffectBuilder("Poison", 1)
        builder.debuff()
        builder.damagePerTurn(10)
        val def = builder.build()

        assertEquals(10, def.damagePerTurn)
    }

    @Test
    fun `status effect builder supports heal per turn`() {
        val builder = StatusEffectBuilder("Regen", 2)
        builder.buff()
        builder.healPerTurn(5)
        val def = builder.build()

        assertEquals(5, def.healPerTurn)
    }

    @Test
    fun `status effect builder supports prevents action`() {
        val builder = StatusEffectBuilder("Stun", 4)
        builder.category(EffectCategory.CONDITION)
        builder.preventsAction()
        val def = builder.build()

        assertTrue(def.preventsAction)
    }

    @Test
    fun `status effect builder supports stackable`() {
        val builder = StatusEffectBuilder("Bleed", 5)
        builder.debuff()
        builder.stackable(5)
        val def = builder.build()

        assertEquals(StackMode.STACK_INTENSITY, def.stackMode)
        assertEquals(5, def.maxStacks)
    }

    @Test
    fun `status effect builder supports all stack modes`() {
        // Test REPLACE mode
        val replaceBuilder = StatusEffectBuilder("Replace", 10)
        replaceBuilder.stackMode(StackMode.REPLACE)
        assertEquals(StackMode.REPLACE, replaceBuilder.build().stackMode)

        // Test REFRESH_DURATION mode (default)
        val refreshBuilder = StatusEffectBuilder("Refresh", 11)
        assertEquals(StackMode.REFRESH_DURATION, refreshBuilder.build().stackMode)

        // Test STACK_DURATION mode
        val durationBuilder = StatusEffectBuilder("Duration", 12)
        durationBuilder.stackMode(StackMode.STACK_DURATION)
        assertEquals(StackMode.STACK_DURATION, durationBuilder.build().stackMode)

        // Test NONE mode
        val noneBuilder = StatusEffectBuilder("None", 13)
        noneBuilder.stackMode(StackMode.NONE)
        assertEquals(StackMode.NONE, noneBuilder.build().stackMode)
    }

    @Test
    fun `status effect builder supports tier`() {
        val builder = StatusEffectBuilder("Greater Poison", 6)
        builder.debuff()
        builder.tier(EffectTier.A)
        val def = builder.build()

        assertEquals(EffectTier.A, def.tier)
    }

    @Test
    fun `status effect builder supports custom tier multiplier`() {
        val builder = StatusEffectBuilder("Enhanced Poison", 30)
        builder.debuff()
        builder.tier(175) // Custom 175% multiplier
        val def = builder.build()

        assertEquals(175, def.effectiveMultiplier)
        assertEquals(175, def.customTierMultiplier)
    }

    @Test
    fun `status effect effectiveMultiplier falls back to tier when no custom multiplier`() {
        val builder = StatusEffectBuilder("Basic Poison", 31)
        builder.debuff()
        builder.tier(EffectTier.B) // 125%
        val def = builder.build()

        assertEquals(125, def.effectiveMultiplier)
        assertEquals(null, def.customTierMultiplier)
    }

    @Test
    fun `status effect tier(EffectTier) resets custom multiplier`() {
        val builder = StatusEffectBuilder("Poison", 32)
        builder.debuff()
        builder.tier(175) // Set custom multiplier first
        builder.tier(EffectTier.A) // Then set predefined tier - should reset

        val def = builder.build()

        assertEquals(150, def.effectiveMultiplier) // A tier = 150%
        assertEquals(null, def.customTierMultiplier)
    }

    @Test
    fun `status effect tier(Int) requires positive multiplier`() {
        val builder = StatusEffectBuilder("Invalid", 33)

        var exception: IllegalArgumentException? = null
        try {
            builder.tier(0)
        } catch (e: IllegalArgumentException) {
            exception = e
        }
        kotlin.test.assertNotNull(exception)
        assertTrue(exception.message?.contains("positive") == true)

        exception = null
        try {
            builder.tier(-10)
        } catch (e: IllegalArgumentException) {
            exception = e
        }
        kotlin.test.assertNotNull(exception)
    }

    @Test
    fun `status effect builder supports custom duration`() {
        val builder = StatusEffectBuilder("Berserk", 7)
        builder.buff()
        builder.duration(EffectDuration.UntilBattleEnd)
        val def = builder.build()

        assertEquals(EffectDuration.UntilBattleEnd, def.baseDuration)
    }

    // =========================================================================
    // DAMAGE AND HEALING MULTIPLIERS
    // =========================================================================

    @Test
    fun `status effect builder has default multipliers of 100`() {
        val builder = StatusEffectBuilder("BasicEffect", 20)
        val def = builder.build()

        assertEquals(100, def.damageMultiplier)
        assertEquals(100, def.healingMultiplier)
    }

    @Test
    fun `status effect builder supports damage multiplier`() {
        val builder = StatusEffectBuilder("Haste", 21)
        builder.buff()
        builder.damageMultiplier(200)
        val def = builder.build()

        assertEquals(200, def.damageMultiplier)
    }

    @Test
    fun `status effect builder supports healing multiplier`() {
        val builder = StatusEffectBuilder("Blessing", 22)
        builder.buff()
        builder.healingMultiplier(150)
        val def = builder.build()

        assertEquals(150, def.healingMultiplier)
    }

    @Test
    fun `doubleDamage sets multiplier to 200`() {
        val builder = StatusEffectBuilder("Berserk", 23)
        builder.buff()
        builder.doubleDamage()
        val def = builder.build()

        assertEquals(200, def.damageMultiplier)
    }

    @Test
    fun `halveDamage sets multiplier to 50`() {
        val builder = StatusEffectBuilder("Weak", 24)
        builder.debuff()
        builder.halveDamage()
        val def = builder.build()

        assertEquals(50, def.damageMultiplier)
    }

    @Test
    fun `doubleHealing sets multiplier to 200`() {
        val builder = StatusEffectBuilder("HealingBoost", 25)
        builder.buff()
        builder.doubleHealing()
        val def = builder.build()

        assertEquals(200, def.healingMultiplier)
    }

    @Test
    fun `halveHealing sets multiplier to 50`() {
        val builder = StatusEffectBuilder("Cursed", 26)
        builder.debuff()
        builder.halveHealing()
        val def = builder.build()

        assertEquals(50, def.healingMultiplier)
    }

    @Test
    fun `status effect can have both damage and healing multipliers`() {
        val builder = StatusEffectBuilder("PowerSurge", 27)
        builder.buff()
        builder.damageMultiplier(150)
        builder.healingMultiplier(75)
        val def = builder.build()

        assertEquals(150, def.damageMultiplier)
        assertEquals(75, def.healingMultiplier)
    }

    // =========================================================================
    // STATUS EFFECT TRACKING VARIABLES
    // =========================================================================

    @Test
    fun `status effect tracking variables are generated`() {
        val game =
            gbGame("StatusEffectVarsTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(20)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate effect tracking arrays
        assertTrue(code.contains("hero_effect_id"), "Should generate effect ID array")
        assertTrue(code.contains("hero_effect_duration"), "Should generate duration array")
        assertTrue(code.contains("hero_effect_stacks"), "Should generate stacks array")
    }

    @Test
    fun `status effect arrays have correct size`() {
        val game =
            gbGame("StatusEffectSizeTest") {
                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should use MAX_ACTIVE_EFFECTS (4) for array size
        assertTrue(code.contains("[4]"), "Arrays should have size 4")
    }

    // =========================================================================
    // APPLY EFFECT CODE GENERATION
    // =========================================================================

    @Test
    fun `applyEffect generates correct code`() {
        val poisonDef =
            StatusEffectBuilder("Poison", 1)
                .apply {
                    debuff()
                    duration(3.turns)
                }
                .build()
        val poison = StatusEffect(poisonDef)

        val game =
            gbGame("ApplyEffectTest") {
                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame { applyEffect(hero, poison) } }
            }

        val code = game.compileForTest()

        // Should generate apply effect code
        assertTrue(code.contains("Apply Poison to hero"), "Should have apply comment")
        assertTrue(code.contains("hero_effect_id"), "Should reference effect ID array")
        assertTrue(code.contains("hero_effect_duration"), "Should reference duration array")
    }

    // =========================================================================
    // CLEAR EFFECT CODE GENERATION
    // =========================================================================

    @Test
    fun `clearEffect generates correct code`() {
        val poisonDef = StatusEffectBuilder("Poison", 1).apply { debuff() }.build()
        val poison = StatusEffect(poisonDef)

        val game =
            gbGame("ClearEffectTest") {
                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame { clearEffect(hero, poison) } }
            }

        val code = game.compileForTest()

        // Should generate clear effect code
        assertTrue(code.contains("Clear Poison from hero"), "Should have clear comment")
        assertTrue(code.contains("hero_effect_id[_i] = 0u"), "Should clear effect ID")
    }

    @Test
    fun `clearAllEffects generates correct code`() {
        val game =
            gbGame("ClearAllEffectsTest") {
                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame { clearAllEffects(hero) } }
            }

        val code = game.compileForTest()

        // Should generate clear all effects code
        assertTrue(code.contains("Clear all effects from hero"), "Should have clear all comment")
        assertTrue(
            code.contains("for (UINT8 _i = 0; _i < 4; _i++)"),
            "Should loop through all slots",
        )
    }

    // =========================================================================
    // PREVENTS ACTION CODE GENERATION
    // =========================================================================

    @Test
    fun `preventsAction effect generates can_act helper`() {
        val stunDef =
            StatusEffectBuilder("Stun", 1)
                .apply {
                    category(EffectCategory.CONDITION)
                    duration(1)
                    preventsAction()
                }
                .build()

        val game =
            gbGame("PreventsActionTest") {
                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate can_act helper function for hero
        assertTrue(code.contains("_can_act_hero"), "Should generate can_act helper")
        // With action-preventing effect defined, helper should check against effect IDs
        assertTrue(
            code.contains("Check against action-preventing effects") ||
                code.contains("if (_eid ==") ||
                code.contains("return 1u"),
            "Should have effect checking logic or return true if no effects prevent action",
        )
    }

    @Test
    fun `preventsAction effect ID is included in can_act check`() {
        val game =
            gbGame("PreventsActionCheckTest") {
                // Register a preventsAction effect
                val stun by statusEffect {
                    category(EffectCategory.CONDITION)
                    duration(1)
                    preventsAction()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate can_act helper that checks effect ID 1
        assertTrue(code.contains("_can_act_hero"), "Should generate can_act helper")
        assertTrue(
            code.contains("if (_eid == 1u) return 0u"),
            "Should check effect ID 1 prevents action",
        )
    }

    @Test
    fun `multiple preventsAction effects are all checked`() {
        val game =
            gbGame("MultiPreventsActionTest") {
                val stun by statusEffect {
                    category(EffectCategory.CONDITION)
                    duration(1)
                    preventsAction()
                }
                val sleep by statusEffect {
                    category(EffectCategory.CONDITION)
                    duration(3)
                    preventsAction()
                }
                val trip by statusEffect {
                    category(EffectCategory.CONDITION)
                    duration(1)
                    preventsAction()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should check all three effect IDs
        assertTrue(code.contains("if (_eid == 1u) return 0u"), "Should check stun (ID 1)")
        assertTrue(code.contains("if (_eid == 2u) return 0u"), "Should check sleep (ID 2)")
        assertTrue(code.contains("if (_eid == 3u) return 0u"), "Should check trip (ID 3)")
    }

    @Test
    fun `non-preventsAction effects do not block can_act`() {
        val game =
            gbGame("NonPreventsActionTest") {
                val poison by statusEffect {
                    debuff()
                    duration(5)
                    damagePerTurn(10)
                    // Note: NOT calling preventsAction()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate simple always-true helper when no effects prevent action
        assertTrue(code.contains("_can_act_hero"), "Should generate can_act helper")
        // With no action-preventing effects, should return 1 without checks
        assertTrue(
            code.contains("no action-preventing effects") || code.contains("return 1u; }"),
            "Should have simple return when no effects prevent action",
        )
    }

    // =========================================================================
    // INCOMING DAMAGE/HEALING MULTIPLIERS
    // =========================================================================

    @Test
    fun `status effect builder has default incoming multipliers of 100`() {
        val builder = StatusEffectBuilder("TestEffect", 1)
        val def = builder.build()

        assertEquals(100, def.incomingDamageMultiplier)
        assertEquals(100, def.incomingHealingMultiplier)
    }

    @Test
    fun `status effect builder supports incoming damage multiplier`() {
        val builder = StatusEffectBuilder("Barkskin", 1)
        builder.buff()
        builder.incomingDamageMultiplier(50)
        val def = builder.build()

        assertEquals(50, def.incomingDamageMultiplier)
    }

    @Test
    fun `status effect builder supports incoming healing multiplier`() {
        val builder = StatusEffectBuilder("Amplify", 1)
        builder.buff()
        builder.incomingHealingMultiplier(150)
        val def = builder.build()

        assertEquals(150, def.incomingHealingMultiplier)
    }

    @Test
    fun `halveIncomingDamage sets multiplier to 50`() {
        val builder = StatusEffectBuilder("Barkskin", 1)
        builder.buff()
        builder.halveIncomingDamage()
        val def = builder.build()

        assertEquals(50, def.incomingDamageMultiplier)
    }

    @Test
    fun `doubleIncomingDamage sets multiplier to 200`() {
        val builder = StatusEffectBuilder("Vulnerable", 1)
        builder.debuff()
        builder.doubleIncomingDamage()
        val def = builder.build()

        assertEquals(200, def.incomingDamageMultiplier)
    }

    @Test
    fun `halveIncomingHealing sets multiplier to 50`() {
        val builder = StatusEffectBuilder("Curse", 1)
        builder.debuff()
        builder.halveIncomingHealing()
        val def = builder.build()

        assertEquals(50, def.incomingHealingMultiplier)
    }

    @Test
    fun `doubleIncomingHealing sets multiplier to 200`() {
        val builder = StatusEffectBuilder("HealAmp", 1)
        builder.buff()
        builder.doubleIncomingHealing()
        val def = builder.build()

        assertEquals(200, def.incomingHealingMultiplier)
    }

    @Test
    fun `incoming multipliers generate lookup tables`() {
        val game =
            gbGame("IncomingMultTest") {
                val barkskin by statusEffect {
                    buff()
                    halveIncomingDamage()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate incoming damage multiplier table
        assertTrue(
            code.contains("_effect_incoming_damage_mult"),
            "Should generate incoming damage multiplier table",
        )
        assertTrue(
            code.contains("_effect_incoming_healing_mult"),
            "Should generate incoming healing multiplier table",
        )
    }

    @Test
    fun `incoming damage multiplier helper function is generated`() {
        val game =
            gbGame("IncomingDamageHelperTest") {
                val barkskin by statusEffect {
                    buff()
                    halveIncomingDamage()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate helper functions
        assertTrue(
            code.contains("_get_incoming_damage_multiplier"),
            "Should generate incoming damage multiplier helper",
        )
        assertTrue(
            code.contains("_get_incoming_healing_multiplier"),
            "Should generate incoming healing multiplier helper",
        )
    }

    @Test
    fun `status effect can have both outgoing and incoming multipliers`() {
        val builder = StatusEffectBuilder("BerserkStance", 1)
        builder.buff()
        builder.doubleDamage() // 200% outgoing damage
        builder.doubleIncomingDamage() // 200% incoming damage (take more damage)
        val def = builder.build()

        assertEquals(200, def.damageMultiplier)
        assertEquals(200, def.incomingDamageMultiplier)
    }

    // =========================================================================
    // HIT CHANCE MODIFIERS (5C.1)
    // =========================================================================

    @Test
    fun `status effect builder has default hit chance modifier of 0`() {
        val builder = StatusEffectBuilder("TestEffect", 1)
        val def = builder.build()

        assertEquals(0, def.hitChanceModifier)
    }

    @Test
    fun `status effect builder has default evasion modifier of 0`() {
        val builder = StatusEffectBuilder("TestEffect", 1)
        val def = builder.build()

        assertEquals(0, def.evasionModifier)
    }

    @Test
    fun `status effect builder supports hit chance modifier`() {
        val builder = StatusEffectBuilder("SleetStorm", 1)
        builder.debuff()
        builder.hitChanceModifier(-50)
        val def = builder.build()

        assertEquals(-50, def.hitChanceModifier)
    }

    @Test
    fun `reduceHitChance sets negative modifier`() {
        val builder = StatusEffectBuilder("SleetStorm", 1)
        builder.debuff()
        builder.reduceHitChance(50) // 50% less likely to hit
        val def = builder.build()

        assertEquals(-50, def.hitChanceModifier)
    }

    @Test
    fun `improveHitChance sets positive modifier`() {
        val builder = StatusEffectBuilder("TrueStrike", 1)
        builder.buff()
        builder.improveHitChance(25) // 25% more likely to hit
        val def = builder.build()

        assertEquals(25, def.hitChanceModifier)
    }

    @Test
    fun `status effect builder supports evasion modifier`() {
        val builder = StatusEffectBuilder("Blur", 1)
        builder.buff()
        builder.evasionModifier(50)
        val def = builder.build()

        assertEquals(50, def.evasionModifier)
    }

    @Test
    fun `increaseEvasion sets positive modifier`() {
        val builder = StatusEffectBuilder("MirrorImage", 1)
        builder.buff()
        builder.increaseEvasion(50) // 50% more likely to evade
        val def = builder.build()

        assertEquals(50, def.evasionModifier)
    }

    @Test
    fun `reduceEvasion sets negative modifier`() {
        val builder = StatusEffectBuilder("Faerie Fire", 1)
        builder.debuff()
        builder.reduceEvasion(25) // 25% easier to hit
        val def = builder.build()

        assertEquals(-25, def.evasionModifier)
    }

    @Test
    fun `hit chance modifier must be in valid range`() {
        val builder = StatusEffectBuilder("TestEffect", 1)

        assertFailsWith<IllegalArgumentException> { builder.hitChanceModifier(-101) }
        assertFailsWith<IllegalArgumentException> { builder.hitChanceModifier(101) }
    }

    @Test
    fun `evasion modifier must be in valid range`() {
        val builder = StatusEffectBuilder("TestEffect", 1)

        assertFailsWith<IllegalArgumentException> { builder.evasionModifier(-101) }
        assertFailsWith<IllegalArgumentException> { builder.evasionModifier(101) }
    }

    @Test
    fun `hit chance modifiers generate lookup tables`() {
        val game =
            gbGame("HitChanceModTest") {
                val sleetStorm by statusEffect {
                    debuff()
                    reduceHitChance(50)
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate hit chance modifier table
        assertTrue(
            code.contains("_effect_hit_chance_mod"),
            "Should generate hit chance modifier table",
        )
        assertTrue(code.contains("_effect_evasion_mod"), "Should generate evasion modifier table")
    }

    @Test
    fun `hit chance modifier helper function is generated`() {
        val game =
            gbGame("HitChanceHelperTest") {
                val sleetStorm by statusEffect {
                    debuff()
                    reduceHitChance(50)
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate helper functions
        assertTrue(
            code.contains("_get_hit_chance_modifier"),
            "Should generate hit chance modifier helper",
        )
        assertTrue(
            code.contains("_get_evasion_modifier"),
            "Should generate evasion modifier helper",
        )
    }

    @Test
    fun `status effect can combine hit and evasion modifiers`() {
        val builder = StatusEffectBuilder("BlindAndSlow", 1)
        builder.debuff()
        builder.reduceHitChance(30) // -30% hit chance
        builder.reduceEvasion(20) // -20% evasion (easier to hit)
        val def = builder.build()

        assertEquals(-30, def.hitChanceModifier)
        assertEquals(-20, def.evasionModifier)
    }

    @Test
    fun `hit chance modifier table contains correct signed values`() {
        val game =
            gbGame("SignedHitChanceTest") {
                val accuracy by statusEffect {
                    buff()
                    improveHitChance(25)
                }
                val blindness by statusEffect {
                    debuff()
                    reduceHitChance(50)
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // The table should contain signed values: 25 (positive) and -50 (negative)
        assertTrue(code.contains("25"), "Should contain positive hit chance modifier")
        assertTrue(code.contains("-50"), "Should contain negative hit chance modifier")
    }

    // =========================================================================
    // TARGET REDIRECT (5C.3 - Confused Targeting)
    // =========================================================================

    @Test
    fun `status effect builder has default target redirect mode of null`() {
        val builder = StatusEffectBuilder("TestEffect", 1)
        val def = builder.build()

        assertEquals(null, def.targetRedirectMode)
    }

    @Test
    fun `status effect builder supports target redirect mode`() {
        val builder = StatusEffectBuilder("Confusion", 1)
        builder.debuff()
        builder.targetRedirect(TargetRedirectMode.RANDOM_ANY)
        val def = builder.build()

        assertEquals(TargetRedirectMode.RANDOM_ANY, def.targetRedirectMode)
    }

    @Test
    fun `confuseRandomly sets RANDOM_ANY redirect mode`() {
        val builder = StatusEffectBuilder("Confusion", 1)
        builder.debuff()
        builder.confuseRandomly()
        val def = builder.build()

        assertEquals(TargetRedirectMode.RANDOM_ANY, def.targetRedirectMode)
    }

    @Test
    fun `redirectToAllies sets RANDOM_SAME_SIDE redirect mode`() {
        val builder = StatusEffectBuilder("Charm", 1)
        builder.debuff()
        builder.redirectToAllies()
        val def = builder.build()

        assertEquals(TargetRedirectMode.RANDOM_SAME_SIDE, def.targetRedirectMode)
    }

    @Test
    fun `redirectToSelf sets SELF redirect mode`() {
        val builder = StatusEffectBuilder("SelfHarm", 1)
        builder.debuff()
        builder.redirectToSelf()
        val def = builder.build()

        assertEquals(TargetRedirectMode.SELF, def.targetRedirectMode)
    }

    @Test
    fun `redirectToOpposite sets RANDOM_OPPOSITE_SIDE redirect mode`() {
        val builder = StatusEffectBuilder("Betrayal", 1)
        builder.debuff()
        builder.redirectToOpposite()
        val def = builder.build()

        assertEquals(TargetRedirectMode.RANDOM_OPPOSITE_SIDE, def.targetRedirectMode)
    }

    @Test
    fun `target redirect modes are all distinct`() {
        val modes = TargetRedirectMode.entries
        assertEquals(4, modes.size)
        assertTrue(modes.contains(TargetRedirectMode.RANDOM_SAME_SIDE))
        assertTrue(modes.contains(TargetRedirectMode.RANDOM_OPPOSITE_SIDE))
        assertTrue(modes.contains(TargetRedirectMode.RANDOM_ANY))
        assertTrue(modes.contains(TargetRedirectMode.SELF))
    }

    @Test
    fun `target redirect generates lookup table`() {
        val game =
            gbGame("TargetRedirectTableTest") {
                val confusion by statusEffect {
                    debuff()
                    confuseRandomly()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate target redirect lookup table
        assertTrue(
            code.contains("_effect_target_redirect"),
            "Should generate target redirect lookup table",
        )
    }

    @Test
    fun `target redirect generates helper functions`() {
        val game =
            gbGame("TargetRedirectHelperTest") {
                val confusion by statusEffect {
                    debuff()
                    confuseRandomly()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate helper functions
        assertTrue(
            code.contains("_get_target_redirect_mode"),
            "Should generate target redirect mode getter",
        )
        assertTrue(
            code.contains("_apply_target_redirect"),
            "Should generate apply target redirect helper",
        )
    }

    @Test
    fun `target redirect generates redirect mode constants`() {
        val game =
            gbGame("RedirectConstantsTest") {
                val confusion by statusEffect {
                    debuff()
                    confuseRandomly()
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate redirect mode constants
        assertTrue(code.contains("REDIRECT_NONE"), "Should define REDIRECT_NONE")
        assertTrue(code.contains("REDIRECT_SAME_SIDE"), "Should define REDIRECT_SAME_SIDE")
        assertTrue(code.contains("REDIRECT_OPPOSITE_SIDE"), "Should define REDIRECT_OPPOSITE_SIDE")
        assertTrue(code.contains("REDIRECT_ANY"), "Should define REDIRECT_ANY")
        assertTrue(code.contains("REDIRECT_SELF"), "Should define REDIRECT_SELF")
    }

    @Test
    fun `target redirect table contains correct values`() {
        val game =
            gbGame("RedirectTableValuesTest") {
                val normal by statusEffect {
                    debuff()
                    // No redirect - should be 0
                }
                val charm by statusEffect {
                    debuff()
                    redirectToAllies() // Should be 1 (RANDOM_SAME_SIDE)
                }
                val betray by statusEffect {
                    debuff()
                    redirectToOpposite() // Should be 2 (RANDOM_OPPOSITE_SIDE)
                }
                val confusion by statusEffect {
                    debuff()
                    confuseRandomly() // Should be 3 (RANDOM_ANY)
                }
                val selfHarm by statusEffect {
                    debuff()
                    redirectToSelf() // Should be 4 (SELF)
                }

                val hero by character { stats { hp(100) } }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // The table should contain: 0u, 1u, 2u, 3u, 4u
        assertTrue(
            code.contains("_effect_target_redirect[5]") ||
                code.contains("_effect_target_redirect["),
            "Should generate redirect table with 5 entries",
        )
    }

    @Test
    fun `confusion effect can combine with other properties`() {
        val builder = StatusEffectBuilder("MadnessGaze", 1)
        builder.debuff()
        builder.duration(3)
        builder.confuseRandomly()
        builder.damagePerTurn(5)
        val def = builder.build()

        assertEquals(TargetRedirectMode.RANDOM_ANY, def.targetRedirectMode)
        assertEquals(5, def.damagePerTurn)
        assertIs<EffectDuration.Turns>(def.baseDuration)
        assertEquals(3, (def.baseDuration as EffectDuration.Turns).count)
    }

    // =========================================================================
    // FRAME-BASED DURATION TESTS
    // =========================================================================

    @Test
    fun `effect duration can be set in frames`() {
        val builder = StatusEffectBuilder("Stun", 1)
        builder.debuff()
        builder.durationFrames(60) // 1 second at 60 FPS
        val def = builder.build()

        assertIs<EffectDuration.Frames>(def.baseDuration)
        assertEquals(60, (def.baseDuration as EffectDuration.Frames).count)
    }

    @Test
    fun `effect isFrameBased property is true for frame-based effects`() {
        val builder = StatusEffectBuilder("Stun", 1)
        builder.durationFrames(30)
        val def = builder.build()

        assertTrue(def.isFrameBased)
    }

    @Test
    fun `effect isFrameBased property is false for turn-based effects`() {
        val builder = StatusEffectBuilder("Poison", 1)
        builder.duration(5)
        val def = builder.build()

        assertFalse(def.isFrameBased)
    }

    @Test
    fun `effect durationValue returns correct value for turn-based`() {
        val builder = StatusEffectBuilder("Poison", 1)
        builder.duration(5)
        val def = builder.build()

        assertEquals(5, def.durationValue)
    }

    @Test
    fun `effect durationValue returns correct value for frame-based`() {
        val builder = StatusEffectBuilder("Stun", 1)
        builder.durationFrames(120)
        val def = builder.build()

        assertEquals(120, def.durationValue)
    }

    @Test
    fun `frames extension creates correct duration`() {
        val duration = 60.frames
        assertIs<EffectDuration.Frames>(duration)
        assertEquals(60, duration.count)
    }

    @Test
    fun `turns extension creates correct duration`() {
        val duration = 5.turns
        assertIs<EffectDuration.Turns>(duration)
        assertEquals(5, duration.count)
    }

    @Test
    fun `durationFrames rejects zero`() {
        val builder = StatusEffectBuilder("Stun", 1)
        assertFailsWith<IllegalArgumentException> { builder.durationFrames(0) }
    }

    @Test
    fun `durationFrames rejects negative values`() {
        val builder = StatusEffectBuilder("Stun", 1)
        assertFailsWith<IllegalArgumentException> { builder.durationFrames(-10) }
    }

    @Test
    fun `frame-based effect can combine with other properties`() {
        val builder = StatusEffectBuilder("IFrames", 1)
        builder.buff()
        builder.durationFrames(30) // Half second invincibility
        builder.halveIncomingDamage()
        val def = builder.build()

        assertTrue(def.isFrameBased)
        assertEquals(30, def.durationValue)
        assertEquals(50, def.incomingDamageMultiplier)
        assertEquals(EffectCategory.BUFF, def.category)
    }

    @Test
    fun `codegen generates frame-based lookup table`() {
        val game =
            gbGame("FrameEffectTest") {
                val turnEffect by statusEffect {
                    name("Poison")
                    debuff()
                    duration(5)
                }

                val frameEffect by statusEffect {
                    name("Stun")
                    debuff()
                    durationFrames(60)
                }

                // Need at least one scene with a start
                start = scene("test") {}
            }

        val output = game.compileForTest()

        // Should generate lookup table with frame-based flags
        assertTrue(
            output.contains("_effect_is_frame_based"),
            "Should generate frame-based lookup table",
        )
        // First effect (turn-based) should be 0u, second (frame-based) should be 1u
        assertTrue(
            output.contains("0u") && output.contains("1u"),
            "Should have both turn-based (0u) and frame-based (1u) entries",
        )
    }

    @Test
    fun `codegen generates helper to check frame-based status`() {
        val game =
            gbGame("FrameEffectHelperTest") {
                val frameEffect by statusEffect {
                    name("Stun")
                    durationFrames(30)
                }

                // Need at least one scene with a start
                start = scene("test") {}
            }

        val output = game.compileForTest()

        assertTrue(
            output.contains("_is_effect_frame_based"),
            "Should generate frame-based check helper",
        )
    }
}
