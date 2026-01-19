/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the RPG character stats system.
 *
 * Validates:
 * - Character creation with stats
 * - Stats DSL (hp, sp, atk, def, etc.)
 * - Stat accessor generation
 * - Code generation for stat variables
 * - Code generation for stat operations
 */
class CharacterStatsTest {

    // =========================================================================
    // CHARACTER CREATION WITH STATS
    // =========================================================================

    @Test
    fun `character is created with stats`() {
        val game =
            gbGame("CharacterStatsTest") {
                val hero by character {
                    position(80, 72)
                    stats {
                        hp(100, max = 999)
                        sp(50, max = 99)
                        atk(10)
                        def(8)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.characters.size, "Should have 1 character")
        assertEquals("hero", game.characters[0].name, "Character name should be 'hero'")
        assertTrue(game.characters[0].hasStats, "Character should have stats")
    }

    @Test
    fun `character without stats is valid`() {
        val game =
            gbGame("CharacterNoStatsTest") {
                val npc by character {
                    position(80, 72)
                    // No stats
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.characters.size, "Should have 1 character")
        assertFalse(game.characters[0].hasStats, "Character should not have stats")
    }

    @Test
    fun `character entity is registered`() {
        val game =
            gbGame("CharacterEntityTest") {
                val hero by character {
                    position(80, 72)
                    stats { hp(100) }
                }

                start = scene("main") { every.frame {} }
            }

        // Character's underlying entity should be in the entities list
        assertEquals(1, game.entities.size, "Should have 1 entity")
        assertEquals("hero", game.entities[0].name, "Entity name should be 'hero'")
    }

    // =========================================================================
    // STATS DSL
    // =========================================================================

    @Test
    fun `all stat types can be defined`() {
        val game =
            gbGame("AllStatsTest") {
                val hero by character {
                    stats {
                        hp(100, max = 999)
                        sp(50, max = 99)
                        atk(10)
                        def(8)
                        matk(12)
                        mdef(6)
                        agl(15)
                        level(1, max = 99)
                        exp(0, max = 65535)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val stats = game.characters[0].stats
        assertEquals(9, stats.definition.stats.size, "Should have 9 stats defined")
    }

    @Test
    fun `stats have correct base values`() {
        val game =
            gbGame("StatsBaseValuesTest") {
                val hero by character {
                    stats {
                        hp(100, max = 999)
                        atk(25)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val stats = game.characters[0].stats
        val hpDef = stats.definition.stats.find { it.type == StatType.HP }
        val atkDef = stats.definition.stats.find { it.type == StatType.ATK }

        assertNotNull(hpDef, "HP stat should be defined")
        assertNotNull(atkDef, "ATK stat should be defined")
        assertEquals(100, hpDef.baseValue, "HP base value should be 100")
        assertEquals(999, hpDef.maxValue, "HP max value should be 999")
        assertEquals(25, atkDef.baseValue, "ATK base value should be 25")
        assertEquals(255, atkDef.maxValue, "ATK max value should be 255 (default)")
    }

    // =========================================================================
    // CODE GENERATION - VARIABLES
    // =========================================================================

    @Test
    fun `stat variables are generated`() {
        val game =
            gbGame("StatsCodegenVarsTest") {
                val hero by character {
                    stats {
                        hp(100, max = 999)
                        atk(10)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_hp"), "Should generate hero_hp variable")
        assertTrue(code.contains("hero_hp_max"), "Should generate hero_hp_max variable")
        assertTrue(code.contains("hero_atk"), "Should generate hero_atk variable")
        assertTrue(code.contains("hero_atk_max"), "Should generate hero_atk_max variable")
    }

    @Test
    fun `stat variables have correct initial values`() {
        val game =
            gbGame("StatsInitValuesTest") {
                val hero by character {
                    stats {
                        hp(100, max = 999)
                        atk(25)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_hp = 100u"), "HP should initialize to 100")
        assertTrue(code.contains("hero_hp_max = 999u"), "HP max should initialize to 999")
        assertTrue(code.contains("hero_atk = 25u"), "ATK should initialize to 25")
    }

    @Test
    fun `16-bit stats use UINT16 type`() {
        val game =
            gbGame("Stats16BitTest") {
                val hero by character {
                    stats {
                        hp(100, max = 999) // HP is 16-bit
                        atk(10) // ATK is 8-bit
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("UINT16 hero_hp"), "HP should be UINT16")
        assertTrue(code.contains("UINT8 hero_atk"), "ATK should be UINT8")
    }

    // =========================================================================
    // CODE GENERATION - STAT OPERATIONS
    // =========================================================================

    @Test
    fun `stat modification generates correct code`() {
        val game =
            gbGame("StatsModifyTest") {
                val hero by character { stats { hp(100, max = 999) } }

                start = scene("main") { every.frame { hero.hp += 10 } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_hp += 10u"), "Should generate hp addition")
        assertTrue(
            code.contains("if (hero_hp > hero_hp_max) hero_hp = hero_hp_max"),
            "Should generate clamp after addition",
        )
    }

    @Test
    fun `stat damage generates safe subtraction`() {
        val game =
            gbGame("StatsDamageTest") {
                val hero by character { stats { hp(100, max = 999) } }

                start = scene("main") { every.frame { hero.hp -= 10 } }
            }

        val code = game.compileForTest()

        // Should generate safe subtraction with floor at 0
        assertTrue(
            code.contains("if (hero_hp >= 10u) hero_hp -= 10u; else hero_hp = 0"),
            "Should generate safe damage subtraction",
        )
    }

    @Test
    fun `stat restore generates percentage calculation`() {
        val game =
            gbGame("StatsRestoreTest") {
                val hero by character { stats { hp(100, max = 999) } }

                start = scene("main") { every.frame { hero.hp.restore(50) } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("hero_hp += (hero_hp_max * 50u) / 100u"),
            "Should generate percentage restore",
        )
    }

    // =========================================================================
    // CODE GENERATION - CONDITION CHECKS
    // =========================================================================

    @Test
    fun `isZero condition generates correct code`() {
        val game =
            gbGame("StatsIsZeroTest") {
                val hero by character { stats { hp(100, max = 999) } }

                start =
                    scene("main") {
                        every.frame {
                            whenever(hero.hp.isZero) {
                                // Game over logic
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_hp == 0u"), "Should generate isZero check")
    }

    @Test
    fun `isFull condition generates correct code`() {
        val game =
            gbGame("StatsIsFullTest") {
                val hero by character { stats { hp(100, max = 999) } }

                start =
                    scene("main") {
                        every.frame {
                            whenever(hero.hp.isFull) {
                                // HP full logic
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_hp >= hero_hp_max"), "Should generate isFull check")
    }

    // =========================================================================
    // CHARACTER ENTITY FEATURES
    // =========================================================================

    @Test
    fun `character has entity features`() {
        val game =
            gbGame("CharacterEntityFeaturesTest") {
                val hero by character {
                    position(80, 72)
                    sprite(SpriteAsset("hero.png")) { size = 8 x 16 }
                    stats { hp(100) }
                }

                start = scene("main") { every.frame {} }
            }

        val character = game.characters[0]
        assertTrue(character.hasPosition, "Character should have position")
        assertTrue(character.hasSprite, "Character should have sprite")
    }

    @Test
    fun `character position is accessible`() {
        val game =
            gbGame("CharacterPositionTest") {
                val hero by character {
                    position(80, 72)
                    stats { hp(100) }
                }

                start = scene("main") { every.frame { hero.x += 1 } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("hero_x"), "Should access character position")
    }

    // =========================================================================
    // BASE ATTACK
    // =========================================================================

    @Test
    fun `character without base attack has hasBaseAttack false`() {
        val game =
            gbGame("CharacterNoBaseAttackTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(15)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val character = game.characters[0]
        assertFalse(character.hasBaseAttack, "Character should not have base attack")
    }

    @Test
    fun `character can have inline base attack`() {
        val game =
            gbGame("CharacterInlineBaseAttackTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(15)
                    }
                    baseAttack {
                        name("Sword Slash")
                        physical()
                        power(100)
                        execute { dealDamage() }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val character = game.characters[0]
        assertTrue(character.hasBaseAttack, "Character should have base attack")
        assertNotNull(character.baseAttackAbility)
        assertEquals("Sword Slash", character.baseAttackAbility?.displayName)
        assertEquals(AbilityCost.Free, character.baseAttackAbility?.cost)
    }

    @Test
    fun `character can reference existing ability as base attack`() {
        val game =
            gbGame("CharacterRefBaseAttackTest") {
                val swordSlash by ability {
                    name("Sword Slash")
                    physical()
                    power(100)
                    execute { dealDamage() }
                }

                val hero by character {
                    stats {
                        hp(100)
                        atk(15)
                    }
                    baseAttack(swordSlash)
                }

                start = scene("main") { every.frame {} }
            }

        val character = game.characters[0]
        assertTrue(character.hasBaseAttack, "Character should have base attack")
        assertEquals("Sword Slash", character.baseAttackAbility?.displayName)
    }

    @Test
    fun `inline base attack is registered as ability`() {
        val game =
            gbGame("CharacterBaseAttackRegisteredTest") {
                val hero by character {
                    stats {
                        hp(100)
                        atk(15)
                    }
                    baseAttack {
                        name("Fire Punch")
                        aspect(Aspect.FIRE)
                        execute { dealDamage() }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        // Base attack should be in the abilities list
        assertEquals(1, game.abilities.size, "Should have 1 ability registered")
        assertEquals("hero_base_attack", game.abilities[0].id)
        assertEquals("Fire Punch", game.abilities[0].displayName)
    }
}
