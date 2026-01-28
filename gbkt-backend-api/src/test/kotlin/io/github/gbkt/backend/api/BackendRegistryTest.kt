/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import io.github.gbkt.core.Game
import io.github.gbkt.core.constraints.AudioSpec
import io.github.gbkt.core.constraints.MemorySpec
import io.github.gbkt.core.constraints.ScreenSpec
import io.github.gbkt.core.constraints.SpriteSpec
import io.github.gbkt.core.constraints.TargetProfile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [BackendRegistry].
 *
 * Tests manual registration and lookup. ServiceLoader discovery is tested in the backend module
 * (gbkt-backend-gbdk).
 */
class BackendRegistryTest {

    @BeforeTest
    fun setUp() {
        BackendRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        BackendRegistry.clear()
    }

    @Test
    fun `manually registered backend can be retrieved by id`() {
        val mockBackend = createMockBackend("test-backend", "test-target")
        BackendRegistry.register(mockBackend)

        val result = BackendRegistry.forId("test-backend")

        assertNotNull(result)
        assertEquals("test-backend", result.id)
        assertEquals("test-target", result.profile.id)
    }

    @Test
    fun `manually registered backend can be retrieved by target`() {
        val mockBackend = createMockBackend("test-backend", "custom-platform")
        BackendRegistry.register(mockBackend)

        val result = BackendRegistry.forTarget("custom-platform")

        assertNotNull(result)
        assertEquals("test-backend", result.id)
    }

    @Test
    fun `all returns all registered backends`() {
        val backend1 = createMockBackend("backend1", "target1")
        val backend2 = createMockBackend("backend2", "target2")
        BackendRegistry.register(backend1)
        BackendRegistry.register(backend2)

        val results = BackendRegistry.all()

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "backend1" })
        assertTrue(results.any { it.id == "backend2" })
    }

    @Test
    fun `supportedTargets returns all target IDs`() {
        val backend1 = createMockBackend("backend1", "target1")
        val backend2 = createMockBackend("backend2", "target2")
        BackendRegistry.register(backend1)
        BackendRegistry.register(backend2)

        val targets = BackendRegistry.supportedTargets()

        assertEquals(2, targets.size)
        assertTrue(targets.contains("target1"))
        assertTrue(targets.contains("target2"))
    }

    @Test
    fun `forId returns null for unknown backend`() {
        val result = BackendRegistry.forId("nonexistent")

        assertNull(result)
    }

    @Test
    fun `forTarget returns null for unknown target`() {
        val result = BackendRegistry.forTarget("nonexistent")

        assertNull(result)
    }

    @Test
    fun `clear removes all registered backends`() {
        val mockBackend = createMockBackend("test-backend", "test-target")
        BackendRegistry.register(mockBackend)
        assertEquals(1, BackendRegistry.all().size)

        BackendRegistry.clear()

        assertEquals(0, BackendRegistry.all().size)
    }

    @Test
    fun `later registration overwrites earlier with same id`() {
        val backend1 = createMockBackend("same-id", "target1")
        val backend2 = createMockBackend("same-id", "target2")
        BackendRegistry.register(backend1)
        BackendRegistry.register(backend2)

        val result = BackendRegistry.forId("same-id")

        assertNotNull(result)
        assertEquals("target2", result.profile.id)
    }

    private fun createMockBackend(id: String, targetId: String): CodegenBackend {
        return object : CodegenBackend {
            override val profile = createMockProfile(targetId)
            override val id = id
            override val displayName = "Mock Backend: $id"
            override val romExtension = "rom"

            override fun validate(game: Game) = ValidationResult.SUCCESS

            override fun generate(game: Game, options: GenerationOptions) =
                GenerationResult.failed("Not implemented")
        }
    }

    private fun createMockProfile(id: String): TargetProfile {
        return object : TargetProfile {
            override val name = "Mock Platform: $id"
            override val id = id
            override val screen =
                ScreenSpec(
                    width = 160,
                    height = 144,
                    bitsPerPixel = 2,
                    tileSize = 8,
                    backgroundLayers = 1,
                    supportsPalettes = false,
                    paletteCount = 0,
                    colorsPerPalette = 4,
                )
            override val sprites =
                SpriteSpec(
                    maxSprites = 40,
                    maxPerScanline = 10,
                    sizes = emptyList(),
                    supportsPalettes = false,
                    paletteCount = 0,
                    supportsFlipping = true,
                    supportsPriority = true,
                )
            override val memory =
                MemorySpec(
                    workRam = 8192,
                    videoRam = 8192,
                    oamSize = 160,
                    hiRam = 127,
                    romBankSize = 16384,
                    ramBankSize = 8192,
                    stackSize = 256,
                )
            override val audio =
                AudioSpec(
                    channels = emptyList(),
                    sampleRate = 0,
                    supportsPCM = false,
                    supportsWavetable = false,
                )
            override val supportsBanking = false
            override val maxRomSize = 32768
            override val defaultRomBanks = 2
            override val maxRamBanks = 0
        }
    }
}
