/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// NPC COLLISION IR TESTS (Plan 06.7-03)
// Verifies IR data model for NPC-to-NPC collision:
//  - CollisionGroupIR default values and companion constants
//  - CollisionResponse enum ordinals match codegen assumptions
//  - CollisionRuleIR defaults (interval = 1, emptyList for onCollide)
//  - NpcCollisionConfig defaults (groupIds empty, collidesWithNpcs false, mass = 1)
//  - ActorIR npcCollisionConfig field is nullable (null = no collision)
//  - GameIR collisionGroups and collisionRules fields are present and default to emptyList
// =============================================================================

class NpcCollisionTest {

    // =========================================================================
    // CollisionGroupIR
    // =========================================================================

    @Test
    fun `CollisionGroupIR stores group id`() {
        val group = CollisionGroupIR("bullets")
        assertEquals("bullets", group.id)
    }

    @Test
    fun `DEFAULT_NPC_GROUP constant is _default_npc`() {
        assertEquals("_default_npc", CollisionGroupIR.DEFAULT_NPC_GROUP)
    }

    @Test
    fun `CollisionGroupIR data class equality uses id`() {
        val a = CollisionGroupIR("enemies")
        val b = CollisionGroupIR("enemies")
        assertEquals(a, b)
    }

    // =========================================================================
    // CollisionResponse
    // =========================================================================

    @Test
    fun `CollisionResponse enum has exactly four values`() {
        assertEquals(4, CollisionResponse.values().size)
    }

    @Test
    fun `CollisionResponse values are OVERLAP BLOCK BOUNCE PUSH in order`() {
        val names = CollisionResponse.values().map { it.name }
        assertEquals(listOf("OVERLAP", "BLOCK", "BOUNCE", "PUSH"), names)
    }

    // =========================================================================
    // CollisionRuleIR
    // =========================================================================

    @Test
    fun `CollisionRuleIR defaults interval to 1`() {
        val rule = CollisionRuleIR("groupA", "groupB")
        assertEquals(1, rule.interval)
    }

    @Test
    fun `CollisionRuleIR defaults response to OVERLAP`() {
        val rule = CollisionRuleIR("groupA", "groupB")
        assertEquals(CollisionResponse.OVERLAP, rule.response)
    }

    @Test
    fun `CollisionRuleIR defaults onCollide to emptyList`() {
        val rule = CollisionRuleIR("groupA", "groupB")
        assertTrue(rule.onCollide.isEmpty())
    }

    @Test
    fun `CollisionRuleIR stores custom interval`() {
        val rule = CollisionRuleIR("a", "b", interval = 4)
        assertEquals(4, rule.interval)
    }

    @Test
    fun `CollisionRuleIR stores PUSH response`() {
        val rule = CollisionRuleIR("heavy", "light", response = CollisionResponse.PUSH)
        assertEquals(CollisionResponse.PUSH, rule.response)
    }

    @Test
    fun `CollisionRuleIR supports intra-group check (groupA equals groupB)`() {
        val rule = CollisionRuleIR("_default_npc", "_default_npc")
        assertEquals("_default_npc", rule.groupA)
        assertEquals("_default_npc", rule.groupB)
    }

    // =========================================================================
    // NpcCollisionConfig
    // =========================================================================

    @Test
    fun `NpcCollisionConfig defaults groupIds to emptyList`() {
        val config = NpcCollisionConfig()
        assertTrue(config.groupIds.isEmpty())
    }

    @Test
    fun `NpcCollisionConfig defaults collidesWithNpcs to false`() {
        val config = NpcCollisionConfig()
        assertFalse(config.collidesWithNpcs)
    }

    @Test
    fun `NpcCollisionConfig defaults mass to 1`() {
        val config = NpcCollisionConfig()
        assertEquals(1, config.mass)
    }

    @Test
    fun `NpcCollisionConfig stores custom mass for PUSH displacement`() {
        val config = NpcCollisionConfig(mass = 5)
        assertEquals(5, config.mass)
    }

    @Test
    fun `NpcCollisionConfig stores multiple group ids`() {
        val config = NpcCollisionConfig(groupIds = listOf("bullets", "enemies"))
        assertEquals(2, config.groupIds.size)
        assertTrue(config.groupIds.contains("bullets"))
        assertTrue(config.groupIds.contains("enemies"))
    }

    // =========================================================================
    // ActorIR npcCollisionConfig field
    // =========================================================================

    @Test
    fun `ActorIR defaults npcCollisionConfig to null`() {
        val actor = ActorIR(id = "npc", position = PositionDef(x = 0, y = 0))
        assertNull(actor.npcCollisionConfig)
    }

    @Test
    fun `ActorIR stores NpcCollisionConfig when provided`() {
        val config = NpcCollisionConfig(collidesWithNpcs = true, mass = 2)
        val actor =
            ActorIR(
                id = "enemy",
                position = PositionDef(x = 16, y = 16),
                npcCollisionConfig = config,
            )
        assertNotNull(actor.npcCollisionConfig)
        assertTrue(actor.npcCollisionConfig!!.collidesWithNpcs)
        assertEquals(2, actor.npcCollisionConfig!!.mass)
    }

    // =========================================================================
    // GameIR collisionGroups and collisionRules fields
    // =========================================================================

    @Test
    fun `GameIR defaults collisionGroups to emptyList`() {
        val game =
            GameIR(
                name = "Test",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = emptyList(),
                actors = emptyList(),
                startScene = "main",
            )
        assertTrue(game.collisionGroups.isEmpty())
    }

    @Test
    fun `GameIR defaults collisionRules to emptyList`() {
        val game =
            GameIR(
                name = "Test",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = emptyList(),
                actors = emptyList(),
                startScene = "main",
            )
        assertTrue(game.collisionRules.isEmpty())
    }

    @Test
    fun `GameIR stores collision groups when provided`() {
        val groups = listOf(CollisionGroupIR("projectiles"), CollisionGroupIR("enemies"))
        val game =
            GameIR(
                name = "Test",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = emptyList(),
                actors = emptyList(),
                startScene = "main",
                collisionGroups = groups,
            )
        assertEquals(2, game.collisionGroups.size)
    }

    @Test
    fun `GameIR stores collision rules when provided`() {
        val rules =
            listOf(
                CollisionRuleIR("projectiles", "enemies", CollisionResponse.OVERLAP),
                CollisionRuleIR("_default_npc", "_default_npc", CollisionResponse.BLOCK),
            )
        val game =
            GameIR(
                name = "Test",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = emptyList(),
                actors = emptyList(),
                startScene = "main",
                collisionRules = rules,
            )
        assertEquals(2, game.collisionRules.size)
        assertEquals(CollisionResponse.OVERLAP, game.collisionRules[0].response)
        assertEquals(CollisionResponse.BLOCK, game.collisionRules[1].response)
    }
}
