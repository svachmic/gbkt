/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.DailyChallengeConfig
import io.github.gbkt.rpg.domain.MetaProgressionConfig
import io.github.gbkt.rpg.domain.RoguelikeConfig
import io.github.gbkt.rpg.domain.RoguelikeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests proving that the roguelike DSL builder produces correct [GenericSystem] IR nodes.
 *
 * Key constraint: NO new sealed IR subtypes — all data travels in the GenericSystem config map.
 */
class RoguelikeTest {

    // -------------------------------------------------------------------------
    // Basic mode and permadeath
    // -------------------------------------------------------------------------

    @Test
    fun `roguelike builder defaults to PURE mode with permadeath and seedBased`() {
        var system: GenericSystem? = null

        game("RogueTest") {
                system = roguelike("run") {}
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(system)
        val config = system!!.config["config"] as RoguelikeConfig
        assertEquals(RoguelikeMode.PURE, config.mode)
        assertTrue(config.permadeath)
        assertTrue(config.seedBased)
        assertFalse(config.roomClearGating)
        assertNull(config.dailyChallenge)
        assertNull(config.metaProgression)
    }

    @Test
    fun `roguelike builder captures PURE mode`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") {
                        mode(RoguelikeMode.PURE)
                        permadeath(true)
                        seedBased(true)
                    }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertEquals(RoguelikeMode.PURE, config!!.mode)
        assertTrue(config!!.permadeath)
        assertTrue(config!!.seedBased)
    }

    @Test
    fun `roguelike builder captures ROGUELITE mode`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") {
                        mode(RoguelikeMode.ROGUELITE)
                        metaProgression { unlockSlots(8) }
                    }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertEquals(RoguelikeMode.ROGUELITE, config!!.mode)
        assertNotNull(config!!.metaProgression)
    }

    @Test
    fun `roguelike builder with permadeath false`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") { permadeath(false) }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertFalse(config!!.permadeath)
    }

    @Test
    fun `roguelike builder with seedBased false`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") { seedBased(false) }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertFalse(config!!.seedBased)
    }

    // -------------------------------------------------------------------------
    // Daily challenge
    // -------------------------------------------------------------------------

    @Test
    fun `roguelike builder captures daily challenge config`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") { dailyChallenge { enabled(true) } }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertNotNull(config!!.dailyChallenge)
        val dc = config!!.dailyChallenge!!
        assertIs<DailyChallengeConfig>(dc)
        assertTrue(dc.enabled)
    }

    @Test
    fun `roguelike builder daily challenge enabled false`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") { dailyChallenge { enabled(false) } }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        val dc = config!!.dailyChallenge
        assertNotNull(dc)
        assertFalse(dc!!.enabled)
    }

    // -------------------------------------------------------------------------
    // Meta-progression
    // -------------------------------------------------------------------------

    @Test
    fun `roguelike builder captures meta-progression config with unlock slots`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") {
                        mode(RoguelikeMode.ROGUELITE)
                        metaProgression { unlockSlots(16) }
                    }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        val meta = config!!.metaProgression
        assertNotNull(meta)
        assertIs<MetaProgressionConfig>(meta)
        assertEquals(16, meta!!.unlockSlots)
    }

    @Test
    fun `roguelike builder captures carry-over currencies`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") {
                        mode(RoguelikeMode.ROGUELITE)
                        metaProgression {
                            unlockSlots(8)
                            carryOver("meta_gold")
                            carryOver("prestige_tokens")
                        }
                    }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        val meta = config!!.metaProgression
        assertNotNull(meta)
        assertEquals(listOf("meta_gold", "prestige_tokens"), meta!!.carryOverCurrencies)
    }

    // -------------------------------------------------------------------------
    // Room-clear gating
    // -------------------------------------------------------------------------

    @Test
    fun `roguelike builder captures room-clear gating flag`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") { roomClearGating(true) }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertTrue(config!!.roomClearGating)
    }

    @Test
    fun `roguelike builder room-clear gating defaults to false`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("run") {}.let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertFalse(config!!.roomClearGating)
    }

    // -------------------------------------------------------------------------
    // GenericSystem structure
    // -------------------------------------------------------------------------

    @Test
    fun `roguelike builder produces GenericSystem with correct config type`() {
        var system: GenericSystem? = null

        game("RogueTest") {
                system = roguelike("dungeon_run") { mode(RoguelikeMode.PURE) }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(system)
        assertEquals("dungeon_run", system!!.id)
        assertEquals("roguelike_system", system!!.config["type"])
        assertIs<RoguelikeConfig>(system!!.config["config"])
    }

    @Test
    fun `roguelike builder registers system in game IR`() {
        val gameIR =
            game("RogueTest") {
                    roguelike("dungeon_run") { mode(RoguelikeMode.PURE) }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rogueSystem =
            gameIR.systems.filterIsInstance<GenericSystem>().find { it.id == "dungeon_run" }
        assertNotNull(rogueSystem)
        assertEquals("roguelike_system", rogueSystem!!.config["type"])
    }

    @Test
    fun `full roguelite configuration captured correctly`() {
        var config: RoguelikeConfig? = null

        game("RogueTest") {
                roguelike("dungeon_run") {
                        mode(RoguelikeMode.ROGUELITE)
                        permadeath(true)
                        seedBased(true)
                        dailyChallenge { enabled(true) }
                        metaProgression {
                            unlockSlots(16)
                            carryOver("meta_gold")
                        }
                        roomClearGating(true)
                    }
                    .let { config = it.config["config"] as RoguelikeConfig }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertNotNull(config)
        assertEquals(RoguelikeMode.ROGUELITE, config!!.mode)
        assertTrue(config!!.permadeath)
        assertTrue(config!!.seedBased)
        assertNotNull(config!!.dailyChallenge)
        assertTrue(config!!.dailyChallenge!!.enabled)
        assertNotNull(config!!.metaProgression)
        assertEquals(16, config!!.metaProgression!!.unlockSlots)
        assertEquals(listOf("meta_gold"), config!!.metaProgression!!.carryOverCurrencies)
        assertTrue(config!!.roomClearGating)
    }
}
