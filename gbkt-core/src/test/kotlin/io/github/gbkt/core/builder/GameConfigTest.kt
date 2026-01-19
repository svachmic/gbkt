/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.builder

import io.github.gbkt.core.*
import io.github.gbkt.core.ir.GBCMode
import io.github.gbkt.core.rpg.character
import kotlin.test.*

/** Tests for GameConfig and ConfigBuilder with configurable limits. */
class GameConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = GameConfig()

        assertEquals(Cartridge.ROM_ONLY, config.cartridge)
        assertEquals(2, config.romBanks)
        assertEquals(0, config.ramBanks)
        assertFalse(config.gbcSupport)
        assertEquals(GBCMode.COMPATIBLE, config.gbcMode)
        assertEquals(99, config.maxLevel)
        assertEquals(4, config.maxPartySize)
        assertEquals(4, config.maxEnemies)
        assertEquals(16, config.maxTweens)
        assertEquals(4, config.animationQueueSize)
        assertEquals(64, config.astarMaxNodes)
        assertEquals(32, config.pathMaxLength)
    }

    @Test
    fun `ConfigBuilder builds with default values`() {
        val config = ConfigBuilder().build()

        assertEquals(4, config.maxPartySize)
        assertEquals(4, config.maxEnemies)
        assertEquals(16, config.maxTweens)
        assertEquals(4, config.animationQueueSize)
        assertEquals(64, config.astarMaxNodes)
        assertEquals(32, config.pathMaxLength)
    }

    @Test
    fun `ConfigBuilder builds with custom values`() {
        val config =
            ConfigBuilder()
                .apply {
                    maxPartySize = 6
                    maxEnemies = 8
                    maxTweens = 24
                    animationQueueSize = 6
                    astarMaxNodes = 128
                    pathMaxLength = 64
                }
                .build()

        assertEquals(6, config.maxPartySize)
        assertEquals(8, config.maxEnemies)
        assertEquals(24, config.maxTweens)
        assertEquals(6, config.animationQueueSize)
        assertEquals(128, config.astarMaxNodes)
        assertEquals(64, config.pathMaxLength)
    }

    @Test
    fun `ConfigBuilder validates maxPartySize range`() {
        assertFailsWith<IllegalArgumentException>("Max party size must be 1-16") {
            ConfigBuilder().apply { maxPartySize = 0 }.build()
        }
        assertFailsWith<IllegalArgumentException>("Max party size must be 1-16") {
            ConfigBuilder().apply { maxPartySize = 17 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { maxPartySize = 1 }.build()
        ConfigBuilder().apply { maxPartySize = 16 }.build()
    }

    @Test
    fun `ConfigBuilder validates maxEnemies range`() {
        assertFailsWith<IllegalArgumentException>("Max enemies must be 1-16") {
            ConfigBuilder().apply { maxEnemies = 0 }.build()
        }
        assertFailsWith<IllegalArgumentException>("Max enemies must be 1-16") {
            ConfigBuilder().apply { maxEnemies = 17 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { maxEnemies = 1 }.build()
        ConfigBuilder().apply { maxEnemies = 16 }.build()
    }

    @Test
    fun `ConfigBuilder validates maxTweens range`() {
        assertFailsWith<IllegalArgumentException>("Max tweens must be 1-64") {
            ConfigBuilder().apply { maxTweens = 0 }.build()
        }
        assertFailsWith<IllegalArgumentException>("Max tweens must be 1-64") {
            ConfigBuilder().apply { maxTweens = 65 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { maxTweens = 1 }.build()
        ConfigBuilder().apply { maxTweens = 64 }.build()
    }

    @Test
    fun `ConfigBuilder validates animationQueueSize range`() {
        assertFailsWith<IllegalArgumentException>("Animation queue size must be 1-16") {
            ConfigBuilder().apply { animationQueueSize = 0 }.build()
        }
        assertFailsWith<IllegalArgumentException>("Animation queue size must be 1-16") {
            ConfigBuilder().apply { animationQueueSize = 17 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { animationQueueSize = 1 }.build()
        ConfigBuilder().apply { animationQueueSize = 16 }.build()
    }

    @Test
    fun `ConfigBuilder validates astarMaxNodes range`() {
        assertFailsWith<IllegalArgumentException>("A* max nodes must be 8-255") {
            ConfigBuilder().apply { astarMaxNodes = 7 }.build()
        }
        assertFailsWith<IllegalArgumentException>("A* max nodes must be 8-255") {
            ConfigBuilder().apply { astarMaxNodes = 256 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { astarMaxNodes = 8 }.build()
        ConfigBuilder().apply { astarMaxNodes = 255 }.build()
    }

    @Test
    fun `ConfigBuilder validates pathMaxLength range`() {
        assertFailsWith<IllegalArgumentException>("Path max length must be 4-128") {
            ConfigBuilder().apply { pathMaxLength = 3 }.build()
        }
        assertFailsWith<IllegalArgumentException>("Path max length must be 4-128") {
            ConfigBuilder().apply { pathMaxLength = 129 }.build()
        }
        // Valid bounds should work
        ConfigBuilder().apply { pathMaxLength = 4 }.build()
        ConfigBuilder().apply { pathMaxLength = 128 }.build()
    }

    @Test
    fun `game DSL config block sets configurable limits`() {
        val game =
            gbGame("TestGame") {
                config {
                    maxPartySize = 6
                    maxEnemies = 8
                    maxTweens = 24
                }
                start = scene("main") { every.frame {} }
            }

        assertEquals(6, game.config.maxPartySize)
        assertEquals(8, game.config.maxEnemies)
        assertEquals(24, game.config.maxTweens)
    }

    @Test
    fun `generated C code uses configured MAX_PARTY_SIZE`() {
        val game =
            gbGame("TestGame") {
                config {
                    maxPartySize = 6
                    maxEnemies = 8
                }
                // Need at least one character to trigger combat core generation
                val hero by character {
                    position(80, 72)
                    stats {
                        hp(100)
                        atk(10)
                    }
                }
                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("#define MAX_PARTY_SIZE 6u"),
            "Generated code should contain MAX_PARTY_SIZE 6u",
        )
        assertTrue(
            code.contains("#define MAX_ENEMIES 8u"),
            "Generated code should contain MAX_ENEMIES 8u",
        )
    }

    @Test
    fun `generated C code uses configured MAX_TWEENS`() {
        val game =
            gbGame("TestGame") {
                config { maxTweens = 24 }
                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("#define MAX_TWEENS 24u"),
            "Generated code should contain MAX_TWEENS 24u",
        )
    }
}
