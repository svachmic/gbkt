/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [SceneMap].
 */
class SceneMapTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `fromGameHeader parses SCENE defines`() {
        val header = File(tempDir, "game.h").also {
            it.writeText(
                """
                #ifndef GAME_H
                #define GAME_H

                #define SCENE_GAMEOVER 0
                #define SCENE_GAME 1
                #define SCENE_TITLE 2

                extern UINT8 current_scene;
                #endif
                """.trimIndent(),
            )
        }

        val map = SceneMap.fromGameHeader(header)

        assertEquals(0, map.indexOf("gameover"))
        assertEquals(1, map.indexOf("game"))
        assertEquals(2, map.indexOf("title"))
        assertEquals("gameover", map.nameOf(0))
        assertEquals("game", map.nameOf(1))
        assertEquals("title", map.nameOf(2))
    }

    @Test
    fun `of creates manual mapping`() {
        val map = SceneMap.of("title" to 0, "game" to 1)

        assertEquals(0, map.indexOf("title"))
        assertEquals(1, map.indexOf("game"))
        assertEquals("title", map.nameOf(0))
        assertEquals("game", map.nameOf(1))
    }

    @Test
    fun `indexOf and nameOf are inverses`() {
        val map = SceneMap.of("title" to 2, "game" to 1, "gameover" to 0)

        for (name in map.sceneNames) {
            val index = map.indexOf(name)!!
            assertEquals(name, map.nameOf(index))
        }
    }

    @Test
    fun `returns null for unknown scene`() {
        val map = SceneMap.of("title" to 0)

        assertNull(map.indexOf("nonexistent"))
        assertNull(map.nameOf(99))
    }

    @Test
    fun `fromGameHeader ignores non-SCENE defines`() {
        val header = File(tempDir, "game.h").also {
            it.writeText(
                """
                #define MAX_ACTORS 10
                #define SCENE_TITLE 0
                #define GAME_VERSION 1
                """.trimIndent(),
            )
        }

        val map = SceneMap.fromGameHeader(header)

        assertEquals(setOf("title"), map.sceneNames)
    }

    @Test
    fun `fromGameHeader handles empty file`() {
        val header = File(tempDir, "game.h").also { it.writeText("") }

        val map = SceneMap.fromGameHeader(header)

        assertEquals(emptySet<String>(), map.sceneNames)
    }
}
