/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.rpg.domain.AutoLearn
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.ItemTeach
import io.github.gbkt.rpg.domain.SkillPointUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// =============================================================================
// CHARACTER LEARNS TESTS
// Verifies CharacterBuilder.learns {} block wires AbilityLearningConfig (GAP-08).
// =============================================================================

class CharacterLearnsTest {

    private fun buildCharacter(block: CharacterBuilder.() -> Unit): CharacterDef {
        var captured: CharacterDef? = null

        game("CharacterTest") {
                captured =
                    character("hero") {
                        name("Hero")
                        stats {
                            hp(100)
                            atk(15)
                            def(10)
                        }
                        block()
                    }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        return assertNotNull(captured)
    }

    // =========================================================================
    // CharacterDef.learningConfig — null by default (backward compat)
    // =========================================================================

    @Test
    fun `CharacterDef learningConfig is null when learns block is absent`() {
        val char = buildCharacter {}
        assertNull(
            char.learningConfig,
            "learningConfig must default to null when learns {} is not called",
        )
    }

    @Test
    fun `CharacterDef construction with only id name stats compiles without learningConfig`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        val char = CharacterDef(id = "hero", name = "Hero", stats = stats)
        assertNull(char.learningConfig)
    }

    // =========================================================================
    // learns {} with autoLearn entries
    // =========================================================================

    @Test
    fun `learns with autoLearn entries populates learningConfig methods`() {
        val char = buildCharacter {
            learns {
                autoLearn("slash", atLevel = 1)
                autoLearn("power_slash", atLevel = 5)
                autoLearn("blade_storm", atLevel = 15)
            }
        }

        val config = assertNotNull(char.learningConfig)
        assertEquals(3, config.methods.size)
    }

    @Test
    fun `learns autoLearn creates AutoLearn with correct ability id and level`() {
        val char = buildCharacter { learns { autoLearn("fireball", atLevel = 8) } }

        val config = assertNotNull(char.learningConfig)
        val method = assertIs<AutoLearn>(config.methods[0])
        assertEquals("fireball", method.abilityId)
        assertEquals(8, method.atLevel)
    }

    @Test
    fun `learns autoLearn order is preserved`() {
        val char = buildCharacter {
            learns {
                autoLearn("slash", atLevel = 1)
                autoLearn("power_slash", atLevel = 5)
                autoLearn("blade_storm", atLevel = 15)
                autoLearn("final_blow", atLevel = 35)
            }
        }

        val methods = assertNotNull(char.learningConfig).methods
        assertEquals(4, methods.size)
        assertEquals("slash", (methods[0] as AutoLearn).abilityId)
        assertEquals(1, (methods[0] as AutoLearn).atLevel)
        assertEquals("final_blow", (methods[3] as AutoLearn).abilityId)
        assertEquals(35, (methods[3] as AutoLearn).atLevel)
    }

    // =========================================================================
    // learns {} with skillPoint entries
    // =========================================================================

    @Test
    fun `learns with skillPoint entry creates SkillPointUnlock with correct cost`() {
        val char = buildCharacter { learns { skillPoint("magic_barrier", cost = 3) } }

        val config = assertNotNull(char.learningConfig)
        val method = assertIs<SkillPointUnlock>(config.methods[0])
        assertEquals("magic_barrier", method.abilityId)
        assertEquals(3, method.cost)
    }

    // =========================================================================
    // learns {} with teachItem entries
    // =========================================================================

    @Test
    fun `learns with teachItem entry creates ItemTeach with correct item id`() {
        val char = buildCharacter { learns { teachItem("holy_sword", itemId = "angel_feather") } }

        val config = assertNotNull(char.learningConfig)
        val method = assertIs<ItemTeach>(config.methods[0])
        assertEquals("holy_sword", method.abilityId)
        assertEquals("angel_feather", method.itemId)
    }

    // =========================================================================
    // learns {} with mixed methods
    // =========================================================================

    @Test
    fun `learns with mixed methods records all entries`() {
        val char = buildCharacter {
            learns {
                autoLearn("slash", atLevel = 1)
                skillPoint("power_slash", cost = 2)
                teachItem("legendary_strike", itemId = "dragon_tome")
            }
        }

        val config = assertNotNull(char.learningConfig)
        assertEquals(3, config.methods.size)
        assertIs<AutoLearn>(config.methods[0])
        assertIs<SkillPointUnlock>(config.methods[1])
        assertIs<ItemTeach>(config.methods[2])
    }

    // =========================================================================
    // learns {} mastery support
    // =========================================================================

    @Test
    fun `learns with mastery block sets enableMastery and levels`() {
        val char = buildCharacter { learns { mastery(enabled = true, levels = 5) } }

        val config = assertNotNull(char.learningConfig)
        assertEquals(true, config.enableMastery)
        assertEquals(5, config.masteryLevels)
    }

    @Test
    fun `learns default mastery is disabled`() {
        val char = buildCharacter { learns { autoLearn("slash", atLevel = 1) } }

        val config = assertNotNull(char.learningConfig)
        assertEquals(false, config.enableMastery)
    }

    // =========================================================================
    // CharacterDef.learningConfig integration — can be set directly
    // =========================================================================

    @Test
    fun `CharacterDef copy with learningConfig non-null preserves value`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        val char = CharacterDef(id = "hero", name = "Hero", stats = stats)
        assertNull(char.learningConfig)

        val char2 = buildCharacter { learns { autoLearn("slash", atLevel = 1) } }
        val copied = char2.copy(name = "Renamed Hero")
        assertNotNull(copied.learningConfig)
        assertEquals(1, copied.learningConfig!!.methods.size)
    }
}
