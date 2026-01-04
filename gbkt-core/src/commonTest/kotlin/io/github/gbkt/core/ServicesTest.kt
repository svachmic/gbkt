/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.TagComponent
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.services.DefaultAssetService
import io.github.gbkt.core.services.DefaultEntityService
import io.github.gbkt.core.services.DefaultGameServices
import io.github.gbkt.core.services.DefaultSpriteService
import io.github.gbkt.core.services.DefaultVariableService
import io.github.gbkt.core.services.MockAssetService
import io.github.gbkt.core.services.MockEntityService
import io.github.gbkt.core.services.MockSpriteService
import io.github.gbkt.core.services.MockVariableService
import io.github.gbkt.core.services.TestGameServices
import io.github.gbkt.core.test.testGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for dependency injection services.
 *
 * Validates:
 * - DefaultServices implementations work correctly
 * - MockServices track registrations for testing
 * - TestGameServices aggregates mocks
 * - Services reset functionality works
 * - GameBuilder uses injected services
 */
class ServicesTest {

    // Helper function to create a simple entity
    private fun createEntity(name: String, tags: Set<String>? = null) =
        Entity(
            name = name,
            positionComponent = null,
            velocityComponent = null,
            spriteComponent = null,
            hitboxComponent = null,
            statesComponent = null,
            tagComponent = tags?.let { TagComponent(it) }
        )

    // Helper function to create a GBVar
    private fun createU8Var(name: String, initial: Int = 0) = GBVar(name, initial, GBVar.VarType.U8)

    private fun createU16Var(name: String, initial: Int = 0) =
        GBVar(name, initial, GBVar.VarType.U16)

    // =========================================================================
    // DEFAULT ASSET SERVICE
    // =========================================================================

    @Test
    fun `DefaultAssetService registers assets`() {
        val service = DefaultAssetService()

        service.registerAsset("player.png")
        service.registerAsset("enemy.png")

        val paths = service.getAssetPaths()
        assertTrue(paths.contains("player.png"), "Should contain player.png")
        assertTrue(paths.contains("enemy.png"), "Should contain enemy.png")
        assertEquals(2, paths.size, "Should have 2 assets")
    }

    @Test
    fun `DefaultAssetService resolves registered assets`() {
        val service = DefaultAssetService()

        service.registerAsset("sprite.png")

        val resolved = service.resolveAsset("sprite.png")
        assertEquals("sprite.png", resolved, "Should resolve registered asset")

        val notFound = service.resolveAsset("missing.png")
        assertEquals(null, notFound, "Should return null for missing asset")
    }

    @Test
    fun `DefaultAssetService validates assets`() {
        val service = DefaultAssetService()

        service.registerAsset("valid.png")

        assertTrue(service.validateAsset("valid.png"), "Should validate registered asset")
        assertFalse(service.validateAsset("invalid.png"), "Should not validate missing asset")
    }

    // =========================================================================
    // DEFAULT SPRITE SERVICE
    // =========================================================================

    @Test
    fun `DefaultSpriteService allocates slots sequentially`() {
        val service = DefaultSpriteService()

        val slot1 = service.allocateSlot()
        val slot2 = service.allocateSlot()
        val slot3 = service.allocateSlot()

        assertEquals(0, slot1, "First slot should be 0")
        assertEquals(1, slot2, "Second slot should be 1")
        assertEquals(2, slot3, "Third slot should be 2")
    }

    @Test
    fun `DefaultSpriteService registers sprites`() {
        val service = DefaultSpriteService()

        val sprite =
            Sprite(name = "player", asset = "player.png", width = 8, height = 16, oamSlot = 0)

        service.registerSprite(sprite)

        val sprites = service.getSprites()
        assertEquals(1, sprites.size, "Should have 1 sprite")
        assertEquals("player", sprites[0].name, "Sprite name should be 'player'")
    }

    // =========================================================================
    // DEFAULT VARIABLE SERVICE
    // =========================================================================

    @Test
    fun `DefaultVariableService registers variables`() {
        val service = DefaultVariableService()

        val variable = createU8Var("counter")
        service.registerVariable(variable)

        val variables = service.getVariables()
        assertEquals(1, variables.size, "Should have 1 variable")
        assertEquals("counter", variables[0].name, "Variable name should be 'counter'")
    }

    // =========================================================================
    // DEFAULT ENTITY SERVICE
    // =========================================================================

    @Test
    fun `DefaultEntityService registers entities`() {
        val service = DefaultEntityService()

        val entity = createEntity("player")
        service.registerEntity(entity)

        val entities = service.getEntities()
        assertEquals(1, entities.size, "Should have 1 entity")
        assertEquals("player", entities[0].name, "Entity name should be 'player'")
    }

    @Test
    fun `DefaultEntityService queries by tag`() {
        val service = DefaultEntityService()

        val enemyTag = TagRef("enemy")

        val enemy1 = createEntity("goblin", setOf("enemy"))
        val enemy2 = createEntity("orc", setOf("enemy"))
        val npc = createEntity("merchant", setOf("npc"))

        service.registerEntity(enemy1)
        service.registerEntity(enemy2)
        service.registerEntity(npc)

        val enemies = service.queryByTag(enemyTag)
        assertEquals(2, enemies.size, "Should find 2 enemies")
        assertTrue(enemies.all { it.tagComponent?.tags?.contains("enemy") == true })
    }

    // =========================================================================
    // MOCK ASSET SERVICE
    // =========================================================================

    @Test
    fun `MockAssetService tracks registered assets`() {
        val mock = MockAssetService()

        mock.registerAsset("a.png")
        mock.registerAsset("b.png")

        assertEquals(2, mock.registeredAssets.size, "Should track 2 assets")
        assertTrue(mock.registeredAssets.contains("a.png"))
        assertTrue(mock.registeredAssets.contains("b.png"))
    }

    @Test
    fun `MockAssetService tracks validation calls`() {
        val mock = MockAssetService()

        mock.registerAsset("exists.png")
        mock.validateAsset("exists.png")
        mock.validateAsset("missing.png")

        assertEquals(2, mock.validationCalls.size, "Should track 2 validation calls")
        assertTrue(mock.validationCalls.contains("exists.png"))
        assertTrue(mock.validationCalls.contains("missing.png"))
    }

    @Test
    fun `MockAssetService reset clears state`() {
        val mock = MockAssetService()

        mock.registerAsset("test.png")
        mock.validateAsset("test.png")

        mock.reset()

        assertEquals(0, mock.registeredAssets.size, "Should have no assets after reset")
        assertEquals(0, mock.validationCalls.size, "Should have no validation calls after reset")
    }

    // =========================================================================
    // MOCK SPRITE SERVICE
    // =========================================================================

    @Test
    fun `MockSpriteService tracks registered sprites`() {
        val mock = MockSpriteService()

        val sprite = Sprite("test", "test.png", 8, 8, mock.allocateSlot())
        mock.registerSprite(sprite)

        assertEquals(1, mock.registeredSprites.size, "Should track 1 sprite")
        assertEquals("test", mock.registeredSprites[0].name)
    }

    @Test
    fun `MockSpriteService tracks allocated slots`() {
        val mock = MockSpriteService()

        mock.allocateSlot()
        mock.allocateSlot()
        mock.allocateSlot()

        assertEquals(3, mock.allocatedSlots, "Should have allocated 3 slots")
    }

    @Test
    fun `MockSpriteService reset clears state`() {
        val mock = MockSpriteService()

        mock.allocateSlot()
        mock.registerSprite(Sprite("test", "test.png", 8, 8, 0))

        mock.reset()

        assertEquals(0, mock.registeredSprites.size, "Should have no sprites after reset")
        assertEquals(0, mock.allocatedSlots, "Should have 0 allocated slots after reset")
    }

    // =========================================================================
    // MOCK VARIABLE SERVICE
    // =========================================================================

    @Test
    fun `MockVariableService tracks registered variables`() {
        val mock = MockVariableService()

        mock.registerVariable(createU8Var("a"))
        mock.registerVariable(createU16Var("b"))

        assertEquals(2, mock.registeredVariables.size, "Should track 2 variables")
    }

    @Test
    fun `MockVariableService reset clears state`() {
        val mock = MockVariableService()

        mock.registerVariable(createU8Var("test"))

        mock.reset()

        assertEquals(0, mock.registeredVariables.size, "Should have no variables after reset")
    }

    // =========================================================================
    // MOCK ENTITY SERVICE
    // =========================================================================

    @Test
    fun `MockEntityService tracks registered entities`() {
        val mock = MockEntityService()

        mock.registerEntity(createEntity("player"))
        mock.registerEntity(createEntity("enemy"))

        assertEquals(2, mock.registeredEntities.size, "Should track 2 entities")
    }

    @Test
    fun `MockEntityService reset clears state`() {
        val mock = MockEntityService()

        mock.registerEntity(createEntity("test"))

        mock.reset()

        assertEquals(0, mock.registeredEntities.size, "Should have no entities after reset")
    }

    // =========================================================================
    // TEST GAME SERVICES
    // =========================================================================

    @Test
    fun `TestGameServices provides all mock services by default`() {
        val services = TestGameServices()

        assertNotNull(services.assets, "Should have assets service")
        assertNotNull(services.sprites, "Should have sprites service")
        assertNotNull(services.variables, "Should have variables service")
        assertNotNull(services.entities, "Should have entities service")
    }

    @Test
    fun `TestGameServices accepts custom services`() {
        val customSprites = MockSpriteService()
        val services = TestGameServices(sprites = customSprites)

        assertEquals(customSprites, services.sprites, "Should use custom sprites service")
    }

    @Test
    fun `TestGameServices reset clears all mock services`() {
        val services = TestGameServices()

        // Add some data
        (services.assets as MockAssetService).registerAsset("test.png")
        (services.sprites as MockSpriteService).allocateSlot()
        (services.variables as MockVariableService).registerVariable(createU8Var("test"))
        (services.entities as MockEntityService).registerEntity(createEntity("test"))

        services.reset()

        assertEquals(0, (services.assets as MockAssetService).registeredAssets.size)
        assertEquals(0, (services.sprites as MockSpriteService).allocatedSlots)
        assertEquals(0, (services.variables as MockVariableService).registeredVariables.size)
        assertEquals(0, (services.entities as MockEntityService).registeredEntities.size)
    }

    // =========================================================================
    // DEFAULT GAME SERVICES
    // =========================================================================

    @Test
    fun `DefaultGameServices provides all default services`() {
        val services = DefaultGameServices()

        assertNotNull(services.assets, "Should have assets service")
        assertNotNull(services.sprites, "Should have sprites service")
        assertNotNull(services.variables, "Should have variables service")
        assertNotNull(services.entities, "Should have entities service")
    }

    // =========================================================================
    // INTEGRATION WITH TESTGAME
    // =========================================================================

    @Test
    @Suppress("DEPRECATION")
    fun `testGame uses injected services`() {
        val mockSprites = MockSpriteService()
        val services = TestGameServices(sprites = mockSprites)

        testGame("ServiceInjectionTest", services) {
            sprite(SpriteAsset("player.png")) { size = 8 x 16 }

            start = scene("main") { every.frame {} }

            test {
                // Verify sprite was registered with mock service
                assertEquals(1, mockSprites.registeredSprites.size, "Should have 1 sprite")
                assertEquals("player.png", mockSprites.registeredSprites[0].asset)
            }
        }
    }

    @Test
    fun `testGame services parameter has default value`() {
        // Should work without explicitly passing services
        testGame("DefaultServicesTest") {
            start = scene("main") { every.frame {} }

            test {
                // Just verify it runs
                assertTrue(true)
            }
        }
    }
}
