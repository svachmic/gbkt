/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.ir.x
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MonsterSizeTest {
    @Test
    fun `all monster sizes exist`() {
        val sizes = MonsterSize.entries
        assertEquals(4, sizes.size)
        assertTrue(MonsterSize.SMALL in sizes)
        assertTrue(MonsterSize.MEDIUM in sizes)
        assertTrue(MonsterSize.LARGE in sizes)
        assertTrue(MonsterSize.BOSS in sizes)
    }
}

class MonsterTierTest {
    @Test
    fun `all monster tiers exist with correct multipliers`() {
        assertEquals(100, MonsterTier.C.statMultiplier)
        assertEquals(125, MonsterTier.B.statMultiplier)
        assertEquals(150, MonsterTier.A.statMultiplier)
        assertEquals(200, MonsterTier.S.statMultiplier)
    }
}

class MonsterBaseStatsTest {
    @Test
    fun `base stats can be created`() {
        val stats = MonsterBaseStats(hp = 50, atk = 10, def = 5, matk = 8, mdef = 4, agl = 12)

        assertEquals(50, stats.hp)
        assertEquals(10, stats.atk)
        assertEquals(5, stats.def)
        assertEquals(8, stats.matk)
        assertEquals(4, stats.mdef)
        assertEquals(12, stats.agl)
    }

    @Test
    fun `matk and mdef default to zero`() {
        val stats = MonsterBaseStats(hp = 30, atk = 5, def = 3, agl = 8)

        assertEquals(0, stats.matk)
        assertEquals(0, stats.mdef)
    }
}

class MonsterStatsBuilderTest {
    @Test
    fun `builder creates stats correctly`() {
        val builder = MonsterStatsBuilder()
        builder.hp(100)
        builder.atk(15)
        builder.def(10)
        builder.matk(20)
        builder.mdef(15)
        builder.agl(18)

        val stats = builder.build()
        assertEquals(100, stats.hp)
        assertEquals(15, stats.atk)
        assertEquals(10, stats.def)
        assertEquals(20, stats.matk)
        assertEquals(15, stats.mdef)
        assertEquals(18, stats.agl)
    }

    @Test
    fun `builder has sensible defaults`() {
        val builder = MonsterStatsBuilder()
        val stats = builder.build()

        assertEquals(10, stats.hp)
        assertEquals(5, stats.atk)
        assertEquals(5, stats.def)
        assertEquals(0, stats.matk)
        assertEquals(0, stats.mdef)
        assertEquals(5, stats.agl)
    }
}

class MonsterTest {
    @Test
    fun `monster scales stats based on tier`() {
        val baseStats =
            MonsterBaseStats(hp = 100, atk = 20, def = 10, matk = 15, mdef = 8, agl = 12)

        // Tier C (100%)
        val tierCMonster =
            Monster(
                id = "testMonster",
                displayName = "Test Monster",
                size = MonsterSize.SMALL,
                tier = MonsterTier.C,
                baseStats = baseStats,
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 10,
                lootDrops = emptyList(),
                sprite = null,
            )

        assertEquals(100, tierCMonster.scaledHp)
        assertEquals(20, tierCMonster.scaledAtk)
        assertEquals(10, tierCMonster.scaledDef)

        // Tier S (200%)
        val tierSMonster =
            Monster(
                id = "bossMonster",
                displayName = "Boss Monster",
                size = MonsterSize.BOSS,
                tier = MonsterTier.S,
                baseStats = baseStats,
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 100,
                lootDrops = emptyList(),
                sprite = null,
            )

        assertEquals(200, tierSMonster.scaledHp)
        assertEquals(40, tierSMonster.scaledAtk)
        assertEquals(20, tierSMonster.scaledDef)
    }

    @Test
    fun `monster with custom tier multiplier uses custom value`() {
        val baseStats =
            MonsterBaseStats(hp = 100, atk = 20, def = 10, matk = 15, mdef = 8, agl = 12)

        // Custom multiplier of 130%
        val customTierMonster =
            Monster(
                id = "eliteMonster",
                displayName = "Elite Monster",
                size = MonsterSize.MEDIUM,
                tier = MonsterTier.C, // Base tier C (100%), but overridden
                customTierMultiplier = 130,
                baseStats = baseStats,
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 25,
                lootDrops = emptyList(),
                sprite = null,
            )

        assertEquals(130, customTierMonster.effectiveStatMultiplier)
        assertEquals(130, customTierMonster.scaledHp) // 100 * 130 / 100 = 130
        assertEquals(26, customTierMonster.scaledAtk) // 20 * 130 / 100 = 26
        assertEquals(13, customTierMonster.scaledDef) // 10 * 130 / 100 = 13
    }

    @Test
    fun `monster effectiveStatMultiplier falls back to tier when no custom multiplier`() {
        val baseStats = MonsterBaseStats(100, 20, 10, 0, 0, 12)

        val tierBMonster =
            Monster(
                id = "tierBMonster",
                displayName = "Tier B Monster",
                size = MonsterSize.SMALL,
                tier = MonsterTier.B, // 125%
                customTierMultiplier = null,
                baseStats = baseStats,
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 20,
                lootDrops = emptyList(),
                sprite = null,
            )

        assertEquals(125, tierBMonster.effectiveStatMultiplier)
        assertEquals(125, tierBMonster.scaledHp)
    }

    @Test
    fun `monster without aspect profile returns NORMAL modifier`() {
        val monster =
            Monster(
                id = "test",
                displayName = "Test",
                size = MonsterSize.SMALL,
                tier = MonsterTier.C,
                baseStats = MonsterBaseStats(10, 5, 5, 0, 0, 5),
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 5,
                lootDrops = emptyList(),
                sprite = null,
            )

        assertEquals(DamageModifier.NORMAL, monster.getAspectModifier(Aspect.FIRE))
        assertEquals(DamageModifier.NORMAL, monster.getAspectModifier(Aspect.ICE))
    }
}

class MonsterBuilderTest {
    @Test
    fun `builder creates monster with defaults`() {
        val builder = MonsterBuilder("goblin")
        builder.baseStats {
            hp(20)
            atk(8)
            def(4)
            agl(10)
        }
        val monster = builder.build()

        assertEquals("goblin", monster.id)
        assertEquals("Goblin", monster.displayName) // Auto-capitalized
        assertEquals(MonsterSize.SMALL, monster.size)
        assertEquals(MonsterTier.C, monster.tier)
    }

    @Test
    fun `builder can customize all fields`() {
        val builder = MonsterBuilder("dragon")
        builder.name("Fire Dragon")
        builder.size(MonsterSize.BOSS)
        builder.tier(MonsterTier.S)
        builder.baseStats {
            hp(500)
            atk(50)
            def(40)
            matk(60)
            mdef(45)
            agl(25)
        }
        builder.exp(1000)

        val monster = builder.build()

        assertEquals("dragon", monster.id)
        assertEquals("Fire Dragon", monster.displayName)
        assertEquals(MonsterSize.BOSS, monster.size)
        assertEquals(MonsterTier.S, monster.tier)
        assertEquals(1000, monster.expReward)

        // Check scaled stats (S tier = 200%)
        assertEquals(1000, monster.scaledHp)
        assertEquals(100, monster.scaledAtk)
    }

    @Test
    fun `builder can set custom tier multiplier`() {
        val builder = MonsterBuilder("eliteGuard")
        builder.name("Elite Guard")
        builder.tier(130) // Custom 130% multiplier
        builder.baseStats {
            hp(100)
            atk(20)
            def(15)
            agl(10)
        }

        val monster = builder.build()

        assertEquals(130, monster.effectiveStatMultiplier)
        assertEquals(130, monster.scaledHp)
        assertEquals(26, monster.scaledAtk)
    }

    @Test
    fun `builder tier(MonsterTier) resets custom multiplier`() {
        val builder = MonsterBuilder("monster")
        builder.baseStats {
            hp(100)
            atk(20)
            def(10)
            agl(10)
        }

        // Set custom multiplier first
        builder.tier(175)
        // Then set predefined tier - should reset custom multiplier
        builder.tier(MonsterTier.B)

        val monster = builder.build()

        assertEquals(125, monster.effectiveStatMultiplier) // B tier = 125%
        assertEquals(125, monster.scaledHp)
    }

    @Test
    fun `builder tier(Int) requires positive multiplier`() {
        val builder = MonsterBuilder("test")
        builder.baseStats {
            hp(10)
            atk(5)
            def(3)
            agl(5)
        }

        assertFails { builder.tier(0) }

        assertFails { builder.tier(-10) }
    }

    @Test
    fun `builder can define aspects`() {
        val builder = MonsterBuilder("iceElemental")
        builder.baseStats {
            hp(30)
            atk(10)
            def(5)
            agl(8)
        }
        builder.aspects {
            immune(Aspect.ICE)
            vulnerable(Aspect.FIRE)
            resist(Aspect.WATER)
        }

        val monster = builder.build()

        assertNotNull(monster.aspectProfile)
        assertEquals(DamageModifier.IMMUNE, monster.getAspectModifier(Aspect.ICE))
        assertEquals(DamageModifier.VULNERABLE, monster.getAspectModifier(Aspect.FIRE))
        assertEquals(DamageModifier.RESIST, monster.getAspectModifier(Aspect.WATER))
        assertEquals(DamageModifier.NORMAL, monster.getAspectModifier(Aspect.LIGHTNING))
    }

    @Test
    fun `builder creates sprite info with defaults`() {
        val builder = MonsterBuilder("goblin")
        builder.baseStats {
            hp(20)
            atk(8)
            def(4)
            agl(10)
        }
        builder.sprite(SpriteAsset("monsters/goblin.png"))

        val monster = builder.build()

        assertNotNull(monster.spriteInfo)
        assertEquals("monsters/goblin.png", monster.spriteInfo?.assetPath)
        assertEquals(2, monster.spriteInfo?.tileWidth)
        assertEquals(2, monster.spriteInfo?.tileHeight)
        assertTrue(monster.spriteInfo?.tierPalettes?.isEmpty() ?: true)
    }

    @Test
    fun `builder creates sprite info with custom configuration`() {
        val builder = MonsterBuilder("dragon")
        builder.baseStats {
            hp(500)
            atk(50)
            def(40)
            agl(25)
        }
        builder.sprite(SpriteAsset("monsters/dragon.png")) {
            tileSize = 4 x 4
            palettes(c = 0, b = 1, a = 2, s = 7)
        }

        val monster = builder.build()

        assertNotNull(monster.spriteInfo)
        assertEquals("monsters/dragon.png", monster.spriteInfo?.assetPath)
        assertEquals(4, monster.spriteInfo?.tileWidth)
        assertEquals(4, monster.spriteInfo?.tileHeight)
        assertEquals(0, monster.spriteInfo?.tierPalettes?.get(MonsterTier.C))
        assertEquals(1, monster.spriteInfo?.tierPalettes?.get(MonsterTier.B))
        assertEquals(2, monster.spriteInfo?.tierPalettes?.get(MonsterTier.A))
        assertEquals(7, monster.spriteInfo?.tierPalettes?.get(MonsterTier.S))
    }
}

class LootDropTest {
    @Test
    fun `loot drop can be created`() {
        // Create a mock item for testing
        val testItem =
            Item(
                id = "potion",
                displayName = "Potion",
                description = "Restores HP",
                category = ItemCategory.CONSUMABLE,
                maxStack = 99,
                buyPrice = 50,
                sellPrice = 25,
                equipSlot = null,
                statBonuses = emptyMap(),
                onUseStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = true,
            )

        val drop = LootDrop(testItem, 25)

        assertEquals(testItem, drop.item)
        assertEquals(25, drop.chance)
        assertEquals(1, drop.minQuantity)
        assertEquals(1, drop.maxQuantity)
    }

    @Test
    fun `loot drop with quantity range`() {
        val testItem =
            Item(
                id = "gold",
                displayName = "Gold",
                description = "Currency",
                category = ItemCategory.MATERIAL,
                maxStack = 99,
                buyPrice = 0,
                sellPrice = 1,
                equipSlot = null,
                statBonuses = emptyMap(),
                onUseStatements = emptyList(),
                usableInBattle = false,
                usableOutOfBattle = false,
            )

        val drop = LootDrop(testItem, 100, minQuantity = 5, maxQuantity = 15)

        assertEquals(100, drop.chance)
        assertEquals(5, drop.minQuantity)
        assertEquals(15, drop.maxQuantity)
    }
}

class DropChanceTest {
    @Test
    fun `percent extension creates DropChance`() {
        val chance = 50.percent
        assertEquals(50, chance.percent)
    }

    @Test
    fun `infix drop creates LootDrop`() {
        val testItem =
            Item(
                id = "herb",
                displayName = "Herb",
                description = "Healing herb",
                category = ItemCategory.CONSUMABLE,
                maxStack = 99,
                buyPrice = 10,
                sellPrice = 5,
                equipSlot = null,
                statBonuses = emptyMap(),
                onUseStatements = emptyList(),
                usableInBattle = true,
                usableOutOfBattle = true,
            )

        val loot = testItem drop 30.percent

        assertEquals(testItem, loot.item)
        assertEquals(30, loot.chance)
    }
}

class LootDropBuilderTest {
    @Test
    fun `builder creates drops list`() {
        val item1 =
            Item(
                id = "item1",
                displayName = "Item 1",
                description = "",
                category = ItemCategory.CONSUMABLE,
                maxStack = 99,
                buyPrice = 0,
                sellPrice = 0,
                equipSlot = null,
                statBonuses = emptyMap(),
                onUseStatements = emptyList(),
                usableInBattle = false,
                usableOutOfBattle = false,
            )
        val item2 =
            Item(
                id = "item2",
                displayName = "Item 2",
                description = "",
                category = ItemCategory.MATERIAL,
                maxStack = 99,
                buyPrice = 0,
                sellPrice = 0,
                equipSlot = null,
                statBonuses = emptyMap(),
                onUseStatements = emptyList(),
                usableInBattle = false,
                usableOutOfBattle = false,
            )

        val builder = LootDropBuilder()
        builder.drop(item1, 50)
        builder.drop(item2, 10, minQty = 1, maxQty = 3)

        val drops = builder.build()

        assertEquals(2, drops.size)
        assertEquals(item1, drops[0].item)
        assertEquals(50, drops[0].chance)
        assertEquals(item2, drops[1].item)
        assertEquals(10, drops[1].chance)
        assertEquals(3, drops[1].maxQuantity)
    }
}

// =============================================================================
// MONSTER AI TESTS
// =============================================================================

class MonsterAIScopeTest {
    @Test
    fun `hpBelow generates IR with correct threshold`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterAIScope("goblin")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.hpBelow(25) { scope.flee() }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRMonsterHpCheck>(condition)
        assertEquals("goblin", condition.monsterId)
        assertEquals(25, condition.percent)
        assertTrue(condition.below, "Should check for HP below threshold")
    }

    @Test
    fun `hpAbove generates IR with correct threshold`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterAIScope("dragon")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.hpAbove(75) { scope.basicAttack(scope.context.randomTarget) }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRMonsterHpCheck>(condition)
        assertEquals("dragon", condition.monsterId)
        assertEquals(75, condition.percent)
        assertFalse(condition.below, "Should check for HP above threshold")
    }

    @Test
    fun `chance generates IR with correct percentage`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterAIScope("slime")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.chance(30) { scope.defend() }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRRandomChance>(condition)
        assertEquals(30, condition.percent)
    }

    @Test
    fun `hasAlly generates IR for ally check`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterAIScope("healer")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.hasAlly { scope.defend() }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRMonsterHasAlly>(condition)
        assertEquals("healer", condition.monsterId)
    }

    @Test
    fun `enemyCountIs generates IR for count check`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterAIScope("boss")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.enemyCountIs(1) {
                // Single target remaining - focus attack
                scope.basicAttack(scope.context.randomTarget)
            }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRAIEnemyCountCheck>(condition)
        assertEquals(1, condition.count)
    }

    @Test
    fun `chance requires valid percentage range`() {
        val scope = MonsterAIScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                    scope.chance(101) { scope.defend() }
                }
            }
        assertTrue(exception.message?.contains("1-100") == true)
    }
}

class MonsterAIContextTest {
    @Test
    fun `randomTarget returns valid expression`() {
        val context = MonsterAIContext("test")
        val target = context.randomTarget
        assertNotNull(target)
        assertIs<AITargetExpression>(target)
        assertTrue(target.expressionCode.contains("random"))
    }

    @Test
    fun `weakestEnemy returns valid expression`() {
        val context = MonsterAIContext("test")
        val target = context.weakestEnemy
        assertNotNull(target)
        assertIs<AITargetExpression>(target)
        assertTrue(target.expressionCode.contains("weakest"))
    }

    @Test
    fun `strongestEnemy returns valid expression`() {
        val context = MonsterAIContext("test")
        val target = context.strongestEnemy
        assertNotNull(target)
        assertIs<AITargetExpression>(target)
        assertTrue(target.expressionCode.contains("strongest"))
    }

    @Test
    fun `firstTarget returns valid expression`() {
        val context = MonsterAIContext("test")
        val target = context.firstTarget
        assertNotNull(target)
        assertIs<AITargetExpression>(target)
        assertTrue(target.expressionCode.contains("first"))
    }
}

// =============================================================================
// MONSTER DEATH HOOK TESTS
// =============================================================================

class MonsterDeathScopeTest {
    @Test
    fun `revive generates IRMonsterRevive with correct HP percent`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterDeathScope("deathKnight")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.revive(hpPercent = 25) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterRevive>(stmt)
        assertEquals("deathKnight", stmt.monsterId)
        assertEquals(25, stmt.hpPercent)
    }

    @Test
    fun `revive requires valid HP percent range`() {
        val scope = MonsterDeathScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.revive(hpPercent = 0)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.revive(hpPercent = 101)
            }
        }
    }

    @Test
    fun `transformTo generates IRMonsterTransform`() {
        // Create a mock monster for transformation target
        val phoenixReborn =
            Monster(
                id = "phoenixReborn",
                displayName = "Phoenix Reborn",
                size = MonsterSize.LARGE,
                tier = MonsterTier.S,
                baseStats = MonsterBaseStats(200, 30, 20, 0, 0, 15),
                aspectProfile = null,
                statusImmunities = emptySet(),
                aiStatements = emptyList(),
                onDeathStatements = emptyList(),
                onHitStatements = emptyList(),
                expReward = 500,
                lootDrops = emptyList(),
                sprite = null,
            )

        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterDeathScope("phoenix")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.transformTo(phoenixReborn)
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterTransform>(stmt)
        assertEquals("phoenix", stmt.monsterId)
        assertEquals("phoenixReborn", stmt.newMonsterId)
    }

    @Test
    fun `awardBonusExp generates IRMonsterAwardBonusExp`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterDeathScope("boss")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.awardBonusExp(100) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterAwardBonusExp>(stmt)
        assertEquals("boss", stmt.monsterId)
        assertEquals(100, stmt.amount)
    }

    @Test
    fun `awardBonusExp requires positive amount`() {
        val scope = MonsterDeathScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.awardBonusExp(0) }
        }

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.awardBonusExp(-10) }
        }
    }

    @Test
    fun `chance generates IR with nested death actions`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterDeathScope("undead")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.chance(33) { scope.revive(hpPercent = 25) }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRRandomChance>(condition)
        assertEquals(33, condition.percent)

        // Check the body contains the revive statement
        assertEquals(1, stmt.then.size)
        assertIs<io.github.gbkt.core.ir.IRMonsterRevive>(stmt.then[0])
    }

    @Test
    fun `chance requires valid percentage range`() {
        val scope = MonsterDeathScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.chance(0) { scope.revive(50) }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.chance(101) { scope.revive(50) }
            }
        }
    }

    @Test
    fun `raw generates IRRaw statement`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterDeathScope("custom")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.raw("// Custom death logic here")
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRRaw>(stmt)
        assertEquals("// Custom death logic here", stmt.code)
    }
}

class MonsterBuilderDeathHookTest {
    @Test
    fun `builder can define onDeath hook`() {
        val builder = MonsterBuilder("deathKnight")
        builder.name("Death Knight")
        builder.baseStats {
            hp(150)
            atk(25)
            def(20)
            agl(10)
        }
        builder.onDeath { chance(33) { revive(hpPercent = 25) } }

        val monster = builder.build()

        assertEquals("deathKnight", monster.id)
        assertTrue(monster.onDeathStatements.isNotEmpty())
        assertEquals(1, monster.onDeathStatements.size)
    }

    @Test
    fun `monster without onDeath has empty statements list`() {
        val builder = MonsterBuilder("goblin")
        builder.baseStats {
            hp(20)
            atk(8)
            def(4)
            agl(10)
        }

        val monster = builder.build()

        assertTrue(monster.onDeathStatements.isEmpty())
    }
}

// =============================================================================
// MONSTER HIT HOOK TESTS
// =============================================================================

class MonsterHitScopeTest {
    @Test
    fun `cancelHit generates IRMonsterCancelHit`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("displacerBeast")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.cancelHit() }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterCancelHit>(stmt)
        assertEquals("displacerBeast", stmt.monsterId)
    }

    @Test
    fun `modifyDamage generates IRMonsterModifyHitDamage with correct multiplier`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("armoredBeast")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.modifyDamage(50) // Take only 50% damage
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterModifyHitDamage>(stmt)
        assertEquals("armoredBeast", stmt.monsterId)
        assertEquals(50, stmt.multiplier)
    }

    @Test
    fun `modifyDamage requires non-negative multiplier`() {
        val scope = MonsterHitScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.modifyDamage(-10) }
        }
    }

    @Test
    fun `decrementEvasion generates IRMonsterDecrementEvasion`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("phasingBeast")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) { scope.decrementEvasion() }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRMonsterDecrementEvasion>(stmt)
        assertEquals("phasingBeast", stmt.monsterId)
    }

    @Test
    fun `hasEvasion generates IR with nested hit actions`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("displacerBeast")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.hasEvasion {
                scope.decrementEvasion()
                scope.cancelHit()
            }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRMonsterHasEvasion>(condition)
        assertEquals("displacerBeast", condition.monsterId)

        // Check the body contains both statements
        assertEquals(2, stmt.then.size)
        assertIs<io.github.gbkt.core.ir.IRMonsterDecrementEvasion>(stmt.then[0])
        assertIs<io.github.gbkt.core.ir.IRMonsterCancelHit>(stmt.then[1])
    }

    @Test
    fun `chance generates IR with nested hit actions`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("blinker")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.chance(50) {
                scope.cancelHit() // 50% chance to evade
            }
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRIf>(stmt)
        val condition = stmt.condition
        assertIs<io.github.gbkt.core.ir.IRRandomChance>(condition)
        assertEquals(50, condition.percent)

        // Check the body contains the cancel hit statement
        assertEquals(1, stmt.then.size)
        assertIs<io.github.gbkt.core.ir.IRMonsterCancelHit>(stmt.then[0])
    }

    @Test
    fun `chance requires valid percentage range`() {
        val scope = MonsterHitScope("test")
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.chance(0) { scope.cancelHit() }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
                scope.chance(101) { scope.cancelHit() }
            }
        }
    }

    @Test
    fun `raw generates IRRaw statement`() {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        val scope = MonsterHitScope("custom")

        io.github.gbkt.core.dsl.RecordingContext.record(recorder) {
            scope.raw("// Custom hit logic here")
        }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<io.github.gbkt.core.ir.IRRaw>(stmt)
        assertEquals("// Custom hit logic here", stmt.code)
    }
}

class MonsterBuilderHitHookTest {
    @Test
    fun `builder can define onHit hook`() {
        val builder = MonsterBuilder("displacerBeast")
        builder.name("Displacer Beast")
        builder.baseStats {
            hp(80)
            atk(20)
            def(15)
            agl(25)
        }
        builder.onHit {
            hasEvasion {
                decrementEvasion()
                cancelHit()
            }
        }

        val monster = builder.build()

        assertEquals("displacerBeast", monster.id)
        assertTrue(monster.onHitStatements.isNotEmpty())
        assertEquals(1, monster.onHitStatements.size)
    }

    @Test
    fun `monster without onHit has empty statements list`() {
        val builder = MonsterBuilder("goblin")
        builder.baseStats {
            hp(20)
            atk(8)
            def(4)
            agl(10)
        }

        val monster = builder.build()

        assertTrue(monster.onHitStatements.isEmpty())
    }

    @Test
    fun `builder can define both onDeath and onHit hooks`() {
        val builder = MonsterBuilder("complexMonster")
        builder.baseStats {
            hp(100)
            atk(20)
            def(15)
            agl(15)
        }
        builder.onDeath { chance(33) { revive(hpPercent = 25) } }
        builder.onHit { chance(25) { cancelHit() } }

        val monster = builder.build()

        assertTrue(monster.onDeathStatements.isNotEmpty())
        assertTrue(monster.onHitStatements.isNotEmpty())
    }
}
