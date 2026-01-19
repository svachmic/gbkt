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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AbilityCategoryTest {
    @Test
    fun `all ability categories exist`() {
        val categories = AbilityCategory.entries
        assertEquals(4, categories.size)
        assertTrue(AbilityCategory.PHYSICAL in categories)
        assertTrue(AbilityCategory.MAGIC in categories)
        assertTrue(AbilityCategory.SUPPORT in categories)
        assertTrue(AbilityCategory.SPECIAL in categories)
    }
}

class AbilityCostTest {
    @Test
    fun `sp cost can be created`() {
        val cost = 10.sp
        assertEquals(10, cost.amount)
    }

    @Test
    fun `hp cost can be created`() {
        val cost = 20.hp
        assertEquals(20, cost.amount)
    }

    @Test
    fun `hp percent cost can be created`() {
        val cost = 25.hpPercent
        assertEquals(25, cost.percent)
    }

    @Test
    fun `free cost exists`() {
        val cost = AbilityCost.Free
        assertIs<AbilityCost.Free>(cost)
    }
}

class AbilityTest {
    @Test
    fun `ability can be created`() {
        val ability =
            Ability(
                id = "fireball",
                displayName = "Fireball",
                description = "Launches a ball of fire at all enemies",
                category = AbilityCategory.MAGIC,
                targeting = TargetingMode.ALL_ENEMIES,
                cost = 10.sp,
                power = 120,
                aspect = Aspect.FIRE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 5,
                classRestrictions = setOf("MAGE"),
            )

        assertEquals("fireball", ability.id)
        assertEquals("Fireball", ability.displayName)
        assertEquals(AbilityCategory.MAGIC, ability.category)
        assertEquals(TargetingMode.ALL_ENEMIES, ability.targeting)
        assertEquals(10, ability.spCost)
        assertEquals(120, ability.power)
        assertEquals(Aspect.FIRE, ability.aspect)
        assertTrue(ability.usableInBattle)
        assertFalse(ability.usableOutOfBattle)
        assertEquals(5, ability.levelRequirement)
        assertTrue("MAGE" in ability.classRestrictions)
    }

    @Test
    fun `ability can check if affordable`() {
        val spAbility =
            Ability(
                id = "heal",
                displayName = "Heal",
                description = "",
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
            )

        // Has enough SP
        assertTrue(spAbility.canAfford(currentSp = 10, currentHp = 50, maxHp = 100))
        // Exactly enough SP
        assertTrue(spAbility.canAfford(currentSp = 5, currentHp = 50, maxHp = 100))
        // Not enough SP
        assertFalse(spAbility.canAfford(currentSp = 4, currentHp = 50, maxHp = 100))

        val hpAbility =
            Ability(
                id = "sacrifice",
                displayName = "Sacrifice",
                description = "",
                category = AbilityCategory.SPECIAL,
                targeting = TargetingMode.ALL_ENEMIES,
                cost = 20.hp,
                power = 200,
                aspect = Aspect.DARK,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 10,
                classRestrictions = emptySet(),
            )

        // Has enough HP (and will survive)
        assertTrue(hpAbility.canAfford(currentSp = 0, currentHp = 50, maxHp = 100))
        // Exactly at threshold (won't survive)
        assertFalse(hpAbility.canAfford(currentSp = 0, currentHp = 20, maxHp = 100))
        // Not enough HP
        assertFalse(hpAbility.canAfford(currentSp = 0, currentHp = 15, maxHp = 100))

        val freeAbility =
            Ability(
                id = "focus",
                displayName = "Focus",
                description = "",
                category = AbilityCategory.SUPPORT,
                targeting = TargetingMode.SELF,
                cost = AbilityCost.Free,
                power = 0,
                aspect = Aspect.PURE,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = true,
                levelRequirement = 1,
                classRestrictions = emptySet(),
            )

        // Free abilities always affordable
        assertTrue(freeAbility.canAfford(currentSp = 0, currentHp = 1, maxHp = 100))
    }

    @Test
    fun `sp cost property returns correct value`() {
        val spAbility =
            Ability(
                id = "test",
                displayName = "Test",
                description = "",
                category = AbilityCategory.PHYSICAL,
                targeting = TargetingMode.SINGLE_ENEMY,
                cost = 15.sp,
                power = 100,
                aspect = Aspect.PHYSICAL,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 1,
                classRestrictions = emptySet(),
            )

        assertEquals(15, spAbility.spCost)

        val hpAbility =
            Ability(
                id = "test2",
                displayName = "Test 2",
                description = "",
                category = AbilityCategory.PHYSICAL,
                targeting = TargetingMode.SINGLE_ENEMY,
                cost = 10.hp,
                power = 100,
                aspect = Aspect.PHYSICAL,
                statusEffects = emptyList(),
                executeStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = false,
                levelRequirement = 1,
                classRestrictions = emptySet(),
            )

        // HP-cost abilities return 0 for spCost
        assertEquals(0, hpAbility.spCost)
    }
}

class AbilityBuilderTest {
    @Test
    fun `builder has sensible defaults`() {
        val builder = AbilityBuilder("strike")
        val ability = builder.build()

        assertEquals("strike", ability.id)
        assertEquals("Strike", ability.displayName) // Auto-capitalized
        assertEquals(AbilityCategory.PHYSICAL, ability.category)
        assertEquals(TargetingMode.SINGLE_ENEMY, ability.targeting)
        assertEquals(AbilityCost.Free, ability.cost)
        assertEquals(100, ability.power)
        assertEquals(Aspect.PHYSICAL, ability.aspect)
        assertTrue(ability.usableInBattle)
        assertFalse(ability.usableOutOfBattle)
        assertEquals(1, ability.levelRequirement)
        assertTrue(ability.classRestrictions.isEmpty())
    }

    @Test
    fun `builder can set all properties`() {
        val builder = AbilityBuilder("blizzard")
        builder.name("Blizzard")
        builder.description("Freezes all enemies with ice magic")
        builder.category(AbilityCategory.MAGIC)
        builder.targeting(TargetingMode.ALL_ENEMIES)
        builder.cost(20.sp)
        builder.power(150)
        builder.aspect(Aspect.ICE)
        builder.usableIn(battle = true, field = false)
        builder.levelRequired(15)
        builder.restrictToClass("MAGE", "SAGE")

        val ability = builder.build()

        assertEquals("blizzard", ability.id)
        assertEquals("Blizzard", ability.displayName)
        assertEquals("Freezes all enemies with ice magic", ability.description)
        assertEquals(AbilityCategory.MAGIC, ability.category)
        assertEquals(TargetingMode.ALL_ENEMIES, ability.targeting)
        assertEquals(20, ability.spCost)
        assertEquals(150, ability.power)
        assertEquals(Aspect.ICE, ability.aspect)
        assertTrue(ability.usableInBattle)
        assertFalse(ability.usableOutOfBattle)
        assertEquals(15, ability.levelRequirement)
        assertTrue("MAGE" in ability.classRestrictions)
        assertTrue("SAGE" in ability.classRestrictions)
    }

    @Test
    fun `builder physical helper sets correct defaults`() {
        val builder = AbilityBuilder("slash")
        builder.physical()

        val ability = builder.build()

        assertEquals(AbilityCategory.PHYSICAL, ability.category)
        assertEquals(Aspect.PHYSICAL, ability.aspect)
    }

    @Test
    fun `builder magical helper sets correct defaults`() {
        val builder = AbilityBuilder("spark")
        builder.magical()

        val ability = builder.build()

        assertEquals(AbilityCategory.MAGIC, ability.category)
        assertEquals(Aspect.MAGICAL, ability.aspect)
    }
}

class AbilityStatusEffectTest {
    @Test
    fun `ability status effect can be created`() {
        val poisonEffect =
            StatusEffectDefinition(
                name = "Poison",
                id = StatusEffectId(1),
                category = EffectCategory.DEBUFF,
                baseDuration = 3.turns,
                tier = EffectTier.C,
                stackMode = StackMode.REFRESH_DURATION,
                maxStacks = 1,
                statModifiers = emptyMap(),
                damagePerTurn = 10,
                healPerTurn = 0,
                preventsAction = false,
                iconIndex = 0,
            )

        val effect = AbilityStatusEffect(effect = poisonEffect, chance = 50, applyToSelf = false)

        assertEquals(poisonEffect, effect.effect)
        assertEquals(50, effect.chance)
        assertFalse(effect.applyToSelf)
    }
}

class AbilityUnlockTest {
    @Test
    fun `unlocksAt sets level requirement`() {
        val builder = AbilityBuilder("fireball")
        builder.name("Fireball")
        builder.unlocksAt(level = 10)

        val ability = builder.build()

        assertEquals(10, ability.levelRequirement)
    }

    @Test
    fun `unlocksAt defaults to level 1`() {
        val builder = AbilityBuilder("slash")
        val ability = builder.build()

        assertEquals(1, ability.levelRequirement)
    }

    @Test
    fun `levelRequired and unlocksAt are equivalent`() {
        val builder1 = AbilityBuilder("ability1")
        builder1.levelRequired(15)

        val builder2 = AbilityBuilder("ability2")
        builder2.unlocksAt(level = 15)

        assertEquals(builder1.build().levelRequirement, builder2.build().levelRequirement)
    }

    @Test
    fun `unlocksAt requires positive level`() {
        val builder = AbilityBuilder("test")
        var exception: IllegalArgumentException? = null
        try {
            builder.unlocksAt(level = 0)
        } catch (e: IllegalArgumentException) {
            exception = e
        }
        kotlin.test.assertNotNull(exception)
    }
}

class AbilityExecuteScopeTest {
    @Test
    fun `instantKill method emits correct IR node`() {
        val builder = AbilityBuilder("deathSpell")
        builder.name("Death Spell")
        builder.targeting(TargetingMode.SINGLE_ENEMY)
        builder.execute { instantKill(chance = 25, ignoreImmunity = false) }

        val ability = builder.build()
        assertTrue(ability.hasExecute)
        assertEquals(1, ability.executeStatements.size)
        val stmt = ability.executeStatements.first()
        assertIs<io.github.gbkt.core.ir.IRAbilityInstantKill>(stmt)
        assertEquals(25, stmt.chance)
        assertFalse(stmt.ignoreImmunity)
    }

    @Test
    fun `instantKill with ignoreImmunity true`() {
        val builder = AbilityBuilder("trueKill")
        builder.execute { instantKill(chance = 50, ignoreImmunity = true) }

        val ability = builder.build()
        val stmt = ability.executeStatements.first()
        assertIs<io.github.gbkt.core.ir.IRAbilityInstantKill>(stmt)
        assertEquals(50, stmt.chance)
        assertTrue(stmt.ignoreImmunity)
    }

    @Test
    fun `cureDebuffs method emits correct IR node`() {
        val builder = AbilityBuilder("cleanse")
        builder.name("Cleanse")
        builder.targeting(TargetingMode.SINGLE_ALLY)
        builder.execute { cureDebuffs() }

        val ability = builder.build()
        assertTrue(ability.hasExecute)
        assertEquals(1, ability.executeStatements.size)
        assertIs<io.github.gbkt.core.ir.IRAbilityCureDebuffs>(ability.executeStatements.first())
    }

    @Test
    fun `restoreSp method emits correct IR node`() {
        val builder = AbilityBuilder("osmose")
        builder.name("Osmose")
        builder.targeting(TargetingMode.SINGLE_ALLY)
        builder.execute { restoreSp(amount = 30) }

        val ability = builder.build()
        assertTrue(ability.hasExecute)
        assertEquals(1, ability.executeStatements.size)
        val stmt = ability.executeStatements.first()
        assertIs<io.github.gbkt.core.ir.IRAbilityRestoreSp>(stmt)
        assertEquals(30, stmt.amount)
    }

    @Test
    fun `fullHeal method emits correct IR node`() {
        val builder = AbilityBuilder("fullRestore")
        builder.name("Full Restore")
        builder.targeting(TargetingMode.SINGLE_ALLY)
        builder.execute { fullHeal() }

        val ability = builder.build()
        assertTrue(ability.hasExecute)
        assertEquals(1, ability.executeStatements.size)
        assertIs<io.github.gbkt.core.ir.IRAbilityFullHeal>(ability.executeStatements.first())
    }

    @Test
    fun `complex ability with multiple effects`() {
        val builder = AbilityBuilder("divineWrath")
        builder.name("Divine Wrath")
        builder.targeting(TargetingMode.ALL_ENEMIES)
        builder.category(AbilityCategory.MAGIC)
        builder.cost(50.sp)
        builder.execute {
            dealDamage(power = 200, aspect = Aspect.LIGHT)
            instantKill(chance = 10)
            showMessage("The heavens strike down!")
        }

        val ability = builder.build()
        assertTrue(ability.hasExecute)
        assertEquals(3, ability.executeStatements.size)
        assertIs<io.github.gbkt.core.ir.IRAbilityDealDamage>(ability.executeStatements[0])
        assertIs<io.github.gbkt.core.ir.IRAbilityInstantKill>(ability.executeStatements[1])
        assertIs<io.github.gbkt.core.ir.IRAbilityShowMessage>(ability.executeStatements[2])
    }
}
