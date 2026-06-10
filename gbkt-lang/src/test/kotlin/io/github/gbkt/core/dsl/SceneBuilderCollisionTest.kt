/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// SCENE BUILDER COLLISION DATA TESTS
// Verifies that SceneBuilder.collisionData() wires correctly into SceneIR.
// =============================================================================

class SceneBuilderCollisionTest {

    @Test
    fun `collisionData sets SceneIR fields`() {
        val builder = SceneBuilder("test_scene", RefRegistry())
        val data = byteArrayOf(1, 1, 0, 0, 0, 0, 1, 1)
        builder.collisionData(data, 4)
        val sceneIR = builder.build()

        assertTrue(
            sceneIR.collisionData?.contentEquals(byteArrayOf(1, 1, 0, 0, 0, 0, 1, 1)) == true,
            "collisionData should match the provided byte array",
        )
        assertEquals(4, sceneIR.mapWidth, "mapWidth should be 4")
    }

    @Test
    fun `collisionData with no collision produces null fields`() {
        val builder = SceneBuilder("test_scene", RefRegistry())
        val sceneIR = builder.build()

        assertNull(sceneIR.collisionData, "collisionData should be null when not set")
        assertNull(sceneIR.mapWidth, "mapWidth should be null when not set")
    }

    @Test
    fun `collisionData rejects zero mapWidth`() {
        val builder = SceneBuilder("test_scene", RefRegistry())
        val ex =
            assertFailsWith<IllegalArgumentException> { builder.collisionData(byteArrayOf(1), 0) }
        assertTrue(
            ex.message?.contains("positive") == true,
            "Error message should mention 'positive'",
        )
    }

    @Test
    fun `collisionData rejects empty data`() {
        val builder = SceneBuilder("test_scene", RefRegistry())
        val ex =
            assertFailsWith<IllegalArgumentException> { builder.collisionData(byteArrayOf(), 4) }
        assertTrue(ex.message?.contains("empty") == true, "Error message should mention 'empty'")
    }

    @Test
    fun `collisionData rejects mismatched size`() {
        val builder = SceneBuilder("test_scene", RefRegistry())
        val ex =
            assertFailsWith<IllegalArgumentException> {
                builder.collisionData(byteArrayOf(1, 0, 1), 2)
            }
        assertTrue(
            ex.message?.contains("divisible") == true,
            "Error message should mention 'divisible'",
        )
    }

    @Test
    fun `collisionData integrates with tileset`() {
        val builder = SceneBuilder("dungeon_room", RefRegistry())
        val data = byteArrayOf(1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1)
        builder.tileset("dungeon.png")
        builder.collisionData(data, 4)
        val sceneIR = builder.build()

        assertNotNull(sceneIR.tilesetRef, "tilesetRef should not be null when tileset() was called")
        assertNotNull(
            sceneIR.collisionData,
            "collisionData should not be null when collisionData() was called",
        )
    }
}
