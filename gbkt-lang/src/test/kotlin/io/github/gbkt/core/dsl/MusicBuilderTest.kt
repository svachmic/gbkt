/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicStop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// MUSIC BUILDER DSL TESTS
// Verifies music declarations, scene sugar, and play()/stopMusic() script ops.
// =============================================================================

class MusicBuilderTest {

    // -------------------------------------------------------------------------
    // MusicPlay data class defaults
    // -------------------------------------------------------------------------

    @Test
    fun `MusicPlay has default fadeInFrames = 0`() {
        val op = MusicPlay("theme")
        assertEquals(0, op.fadeInFrames, "default fadeInFrames should be 0")
    }

    @Test
    fun `MusicPlay has default resume = false`() {
        val op = MusicPlay("theme")
        assertFalse(op.resume, "default resume should be false")
    }

    @Test
    fun `MusicPlay stores custom fadeInFrames and resume`() {
        val op = MusicPlay("theme", fadeInFrames = 30, resume = true)
        assertEquals(30, op.fadeInFrames, "fadeInFrames should be 30")
        assertTrue(op.resume, "resume should be true")
    }

    @Test
    fun `MusicPlay stores songId`() {
        val op = MusicPlay("dungeon_theme")
        assertEquals("dungeon_theme", op.songId, "songId should match")
    }

    // -------------------------------------------------------------------------
    // MusicStop data class defaults
    // -------------------------------------------------------------------------

    @Test
    fun `MusicStop has default fadeOutFrames = 0`() {
        val op = MusicStop()
        assertEquals(0, op.fadeOutFrames, "default fadeOutFrames should be 0")
    }

    @Test
    fun `MusicStop stores custom fadeOutFrames`() {
        val op = MusicStop(fadeOutFrames = 15)
        assertEquals(15, op.fadeOutFrames, "fadeOutFrames should be 15")
    }

    // -------------------------------------------------------------------------
    // music() delegate creates MusicRef with correct ID
    // -------------------------------------------------------------------------

    @Test
    fun `music() delegate creates MusicRef with property name as ID`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    // Use theme to verify it resolves correctly
                    assertEquals("theme", theme.id, "MusicRef id should match property name")
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.musicDefs.size, "Should have 1 music def")
        assertEquals("theme", ir.musicDefs[0].id, "music def id should be 'theme'")
        assertEquals("music/theme.uge", ir.musicDefs[0].assetRef.path, "asset path should match")
    }

    @Test
    fun `multiple music() delegates register distinct tracks`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val battle by music(asset("music/battle.uge"))
                    assertEquals("theme", theme.id)
                    assertEquals("battle", battle.id)
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(2, ir.musicDefs.size, "Should have 2 music defs")
        val ids = ir.musicDefs.map { it.id }
        assertTrue("theme" in ids, "Should contain 'theme'")
        assertTrue("battle" in ids, "Should contain 'battle'")
    }

    // -------------------------------------------------------------------------
    // scene music() sugar emits MusicPlay in enter and MusicStop in exit
    // -------------------------------------------------------------------------

    @Test
    fun `scene music() sugar prepends MusicPlay to enter ops`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val dungeonScene = scene("dungeon") {
                        music(theme)
                        // No explicit enter block — music() should still produce enter ops
                    }
                    start = dungeonScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "dungeon" }
        assertTrue(scene.enterOps.isNotEmpty(), "enterOps should not be empty after music()")
        val firstOp = scene.enterOps.first()
        assertIs<MusicPlay>(firstOp, "First enter op should be MusicPlay")
        assertEquals("theme", firstOp.songId, "MusicPlay songId should be 'theme'")
    }

    @Test
    fun `scene music() sugar appends MusicStop to exit ops`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val dungeonScene = scene("dungeon") { music(theme) }
                    start = dungeonScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "dungeon" }
        assertTrue(scene.exitOps.isNotEmpty(), "exitOps should not be empty after music()")
        val lastOp = scene.exitOps.last()
        assertIs<MusicStop>(lastOp, "Last exit op should be MusicStop")
    }

    @Test
    fun `scene music() merges with existing enter ops`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val dungeonScene = scene("dungeon") {
                        music(theme)
                        enter {
                            // Some enter logic
                            playSound(SoundRef("door_open"))
                        }
                    }
                    start = dungeonScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "dungeon" }
        // MusicPlay should be first, then the enter ops
        assertTrue(
            scene.enterOps.size >= 2,
            "Should have at least 2 enter ops (MusicPlay + enter block)",
        )
        assertIs<MusicPlay>(scene.enterOps.first(), "First op should be MusicPlay")
    }

    @Test
    fun `scene music() merges with existing exit ops`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val dungeonScene = scene("dungeon") {
                        music(theme)
                        exit { playSound(SoundRef("door_close")) }
                    }
                    start = dungeonScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "dungeon" }
        // Exit op should come after the exit block ops
        assertTrue(
            scene.exitOps.size >= 2,
            "Should have at least 2 exit ops (exit block + MusicStop)",
        )
        assertIs<MusicStop>(scene.exitOps.last(), "Last op should be MusicStop")
    }

    // -------------------------------------------------------------------------
    // play() and stopMusic() DSL functions in ScriptBuilder
    // -------------------------------------------------------------------------

    @Test
    fun `play() emits MusicPlay with default parameters`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val mainScene = scene("main") { enter { play(theme) } }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "main" }
        val playOp = scene.enterOps.filterIsInstance<MusicPlay>().firstOrNull()
        assertIs<MusicPlay>(playOp, "enter ops should contain MusicPlay")
        assertEquals("theme", playOp.songId)
        assertEquals(0, playOp.fadeInFrames, "default fadeIn should be 0")
        assertFalse(playOp.resume, "default resume should be false")
    }

    @Test
    fun `play() with fadeIn emits MusicPlay with fadeInFrames`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val mainScene = scene("main") { enter { play(theme, fadeIn = 30) } }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "main" }
        val playOp = scene.enterOps.filterIsInstance<MusicPlay>().firstOrNull()
        assertIs<MusicPlay>(playOp)
        assertEquals(30, playOp.fadeInFrames, "fadeInFrames should be 30")
    }

    @Test
    fun `play() with resume emits MusicPlay with resume=true`() {
        val ir =
            game("TestGame") {
                    val theme by music(asset("music/theme.uge"))
                    val mainScene = scene("main") { enter { play(theme, resume = true) } }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "main" }
        val playOp = scene.enterOps.filterIsInstance<MusicPlay>().firstOrNull()
        assertIs<MusicPlay>(playOp)
        assertTrue(playOp.resume, "resume should be true")
    }

    @Test
    fun `stopMusic() emits MusicStop with default parameters`() {
        val ir =
            game("TestGame") {
                    val mainScene = scene("main") { exit { stopMusic() } }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "main" }
        val stopOp = scene.exitOps.filterIsInstance<MusicStop>().firstOrNull()
        assertIs<MusicStop>(stopOp, "exit ops should contain MusicStop")
        assertEquals(0, stopOp.fadeOutFrames, "default fadeOut should be 0")
    }

    @Test
    fun `stopMusic() with fadeOut emits MusicStop with fadeOutFrames`() {
        val ir =
            game("TestGame") {
                    val mainScene = scene("main") { exit { stopMusic(fadeOut = 15) } }
                    start = mainScene
                }
                .build()

        val scene = ir.scenes.first { it.id == "main" }
        val stopOp = scene.exitOps.filterIsInstance<MusicStop>().firstOrNull()
        assertIs<MusicStop>(stopOp)
        assertEquals(15, stopOp.fadeOutFrames, "fadeOutFrames should be 15")
    }

    // -------------------------------------------------------------------------
    // Helper to create a top-level asset reference in test context
    // -------------------------------------------------------------------------

    private fun asset(path: String): AssetRef = AssetRef(path, AssetType.MUSIC)
}
