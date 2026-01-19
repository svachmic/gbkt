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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the RPG damage calculation system.
 *
 * Validates:
 * - Aspect enum and damage modifiers
 * - Aspect profile building
 * - Damage calculation DSL
 * - Code generation for damage calculations
 */
class DamageTest {

    // =========================================================================
    // ASPECT SYSTEM
    // =========================================================================

    @Test
    fun `aspect enum has all expected values`() {
        val aspects = Aspect.entries
        assertTrue(aspects.contains(Aspect.PHYSICAL))
        assertTrue(aspects.contains(Aspect.MAGICAL))
        assertTrue(aspects.contains(Aspect.FIRE))
        assertTrue(aspects.contains(Aspect.ICE))
        assertTrue(aspects.contains(Aspect.LIGHTNING))
        assertTrue(aspects.contains(Aspect.EARTH))
        assertTrue(aspects.contains(Aspect.WIND))
        assertTrue(aspects.contains(Aspect.WATER))
        assertTrue(aspects.contains(Aspect.LIGHT))
        assertTrue(aspects.contains(Aspect.DARK))
        assertTrue(aspects.contains(Aspect.PURE))
        assertEquals(11, aspects.size, "Should have 11 aspects")
    }

    @Test
    fun `damage modifiers have correct multipliers`() {
        assertEquals(0, DamageModifier.IMMUNE.multiplier)
        assertEquals(50, DamageModifier.RESIST.multiplier)
        assertEquals(100, DamageModifier.NORMAL.multiplier)
        assertEquals(150, DamageModifier.WEAK.multiplier)
        assertEquals(200, DamageModifier.VULNERABLE.multiplier)
    }

    // =========================================================================
    // ASPECT PROFILE
    // =========================================================================

    @Test
    fun `aspect profile builder creates correct profile`() {
        val builder = AspectProfileBuilder("hero")
        builder.immune(Aspect.FIRE)
        builder.resist(Aspect.ICE)
        builder.weak(Aspect.LIGHTNING)
        builder.vulnerable(Aspect.DARK)
        val profile = builder.build()

        assertEquals("hero", profile.ownerName)
        assertEquals(DamageModifier.IMMUNE, profile.getModifier(Aspect.FIRE))
        assertEquals(DamageModifier.RESIST, profile.getModifier(Aspect.ICE))
        assertEquals(DamageModifier.WEAK, profile.getModifier(Aspect.LIGHTNING))
        assertEquals(DamageModifier.VULNERABLE, profile.getModifier(Aspect.DARK))
        assertEquals(DamageModifier.NORMAL, profile.getModifier(Aspect.PHYSICAL))
    }

    @Test
    fun `aspect profile hasModifier returns correct values`() {
        val builder = AspectProfileBuilder("hero")
        builder.resist(Aspect.FIRE)
        val profile = builder.build()

        assertTrue(profile.hasModifier(Aspect.FIRE))
        assertFalse(profile.hasModifier(Aspect.ICE))
    }

    @Test
    fun `aspect profile builder supports vararg aspects`() {
        val builder = AspectProfileBuilder("hero")
        builder.resist(Aspect.FIRE, Aspect.ICE, Aspect.LIGHTNING)
        val profile = builder.build()

        assertEquals(DamageModifier.RESIST, profile.getModifier(Aspect.FIRE))
        assertEquals(DamageModifier.RESIST, profile.getModifier(Aspect.ICE))
        assertEquals(DamageModifier.RESIST, profile.getModifier(Aspect.LIGHTNING))
    }

    // =========================================================================
    // DAMAGE CALCULATION BUILDER
    // =========================================================================

    @Test
    fun `damage calculation builder sets attacker`() {
        val game =
            gbGame("DamageCalculationTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(20)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            val dmg = calculateDamage {
                                attacker(hero)
                                power(100)
                            }
                        }
                    }
            }

        // If we got here without exception, the calculation was built successfully
        assertNotNull(game.scenes["main"])
    }

    @Test
    fun `damage calculation defaults to physical`() {
        val builder = DamageCalculationBuilder()
        builder.attacker("hero")
        builder.power(100)
        val calc = builder.build()

        assertTrue(calc.usePhysical, "Default should be physical damage")
        assertEquals(Aspect.PHYSICAL, calc.aspect)
    }

    @Test
    fun `damage calculation can be set to magical`() {
        val builder = DamageCalculationBuilder()
        builder.attacker("hero")
        builder.power(100)
        builder.magical()
        val calc = builder.build()

        assertFalse(calc.usePhysical, "Should use magical stats")
    }

    @Test
    fun `damage calculation supports elemental aspects`() {
        val builder = DamageCalculationBuilder()
        builder.attacker("hero")
        builder.power(100)
        builder.aspect(Aspect.FIRE)
        val calc = builder.build()

        assertEquals(Aspect.FIRE, calc.aspect)
        assertFalse(calc.usePhysical, "Fire damage should use magical stats by default")
    }

    @Test
    fun `damage calculation supports flat bonus`() {
        val builder = DamageCalculationBuilder()
        builder.attacker("hero")
        builder.power(100)
        builder.bonus(10)
        val calc = builder.build()

        assertEquals(10, calc.flatBonus)
    }

    @Test
    fun `damage calculation supports ignore defense`() {
        val builder = DamageCalculationBuilder()
        builder.attacker("hero")
        builder.power(100)
        builder.ignoreDefense()
        val calc = builder.build()

        assertTrue(calc.ignoreDefense)
    }

    // =========================================================================
    // CODE GENERATION - DAMAGE CALCULATION
    // =========================================================================

    @Test
    fun `calculateDamage generates expression code`() {
        val game =
            gbGame("DamageExprTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(20)
                        def(5)
                    }
                }

                val enemy by character {
                    stats {
                        hp(50)
                        atk(10)
                        def(3)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            val damage = calculateDamage {
                                attacker(hero)
                                defender(enemy)
                                power(150)
                            }
                            enemy.hp -= damage
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate ATK-based calculation
        assertTrue(code.contains("hero_atk"), "Should reference attacker's ATK")
        assertTrue(code.contains("150u"), "Should include power multiplier")
    }

    @Test
    fun `dealDamage generates damage application code`() {
        val game =
            gbGame("DealDamageTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(20)
                    }
                }

                val enemy by character {
                    stats {
                        hp(50)
                        def(3)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            dealDamage(enemy) {
                                attacker(hero)
                                power(100)
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate damage calculation and HP subtraction
        assertTrue(code.contains("enemy_hp"), "Should reference target's HP")
        assertTrue(code.contains("_damage"), "Should use damage variable")
    }

    @Test
    fun `dealFlatDamage generates flat damage code`() {
        val game =
            gbGame("FlatDamageTest") {
                val enemy by character { stats { hp(100) } }

                start = scene("main") { every.frame { dealFlatDamage(enemy, 25, Aspect.PURE) } }
            }

        val code = game.compileForTest()

        // Should generate safe subtraction with flat amount
        assertTrue(code.contains("enemy_hp >= 25u"), "Should check for underflow")
        assertTrue(code.contains("enemy_hp -= 25u"), "Should subtract flat damage")
    }

    @Test
    fun `dealFlatDamage with expression works`() {
        val game =
            gbGame("FlatDamageExprTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(15)
                    }
                }

                val enemy by character { stats { hp(100) } }

                start =
                    scene("main") {
                        every.frame {
                            // Deal damage equal to hero's ATK
                            dealFlatDamage(enemy, hero.atk, Aspect.PHYSICAL)
                        }
                    }
            }

        val code = game.compileForTest()

        // Should reference hero's ATK stat
        assertTrue(code.contains("hero_atk"), "Should use hero's ATK value")
        assertTrue(code.contains("enemy_hp"), "Should modify enemy's HP")
    }

    // =========================================================================
    // CODE GENERATION - MAGICAL DAMAGE
    // =========================================================================

    @Test
    fun `magical damage uses MATK and MDEF`() {
        val game =
            gbGame("MagicalDamageTest") {
                val hero by character {
                    stats {
                        hp(100)
                        matk(25)
                    }
                }

                val enemy by character {
                    stats {
                        hp(50)
                        mdef(10)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            dealDamage(enemy) {
                                attacker(hero)
                                power(100)
                                aspect(Aspect.MAGICAL)
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should use MATK for damage
        assertTrue(code.contains("hero_matk"), "Should use attacker's MATK")
        // Should use MDEF for defense
        assertTrue(code.contains("enemy_mdef"), "Should use defender's MDEF")
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Test
    fun `damage calculation with ignore defense skips defense stat`() {
        val game =
            gbGame("IgnoreDefenseTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(30)
                    }
                }

                val enemy by character {
                    stats {
                        hp(50)
                        def(100) // High defense
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            dealDamage(enemy) {
                                attacker(hero)
                                power(100)
                                ignoreDefense()
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should NOT subtract defense when ignoreDefense is set
        assertTrue(code.contains("hero_atk"), "Should use attacker's ATK")
        // The formula should not include subtraction of def
        assertFalse(
            code.contains("enemy_def") && code.contains("hero_atk * 100u / 100u > enemy_def"),
            "Should not use defense in formula when ignoring",
        )
    }

    @Test
    fun `damage calculation with flat bonus adds bonus`() {
        val game =
            gbGame("FlatBonusTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(20)
                    }
                }

                val enemy by character {
                    stats {
                        hp(50)
                        def(5)
                    }
                }

                start =
                    scene("main") {
                        every.frame {
                            dealDamage(enemy) {
                                attacker(hero)
                                power(100)
                                bonus(15)
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should include the flat bonus
        assertTrue(code.contains("15u"), "Should include flat bonus in calculation")
    }
}
