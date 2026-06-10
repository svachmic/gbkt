/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Tests for [SourceMapResolver] — verifies .gbkt.map JSON parsing and C line resolution. */
class SourceMapResolverTest {

    @TempDir lateinit var tempDir: Path

    // ── Happy path: single map file ───────────────────────────────────────────

    @Test
    fun `resolve returns correct Kotlin location for mapped C line`() {
        val mapDir = createMapDir()
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 42, "kotlinFile": "GameDef.kt", "kotlinLine": 15, "symbol": "score", "snippet": "gameplay/frame"},
                {"cLine": 100, "kotlinFile": "Battle.kt", "kotlinLine": 30, "symbol": "damage", "snippet": "battle/turn"}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        val loc42 = resolver.resolve(42)
        assertEquals("GameDef.kt", loc42?.file, "cLine 42 should map to GameDef.kt")
        assertEquals(15, loc42?.line, "cLine 42 should map to line 15")
        assertEquals("score", loc42?.symbol, "cLine 42 should carry symbol 'score'")
        assertEquals(
            "gameplay/frame",
            loc42?.context,
            "cLine 42 should carry context 'gameplay/frame'",
        )

        val loc100 = resolver.resolve(100)
        assertEquals("Battle.kt", loc100?.file, "cLine 100 should map to Battle.kt")
        assertEquals(30, loc100?.line, "cLine 100 should map to line 30")
        assertEquals("damage", loc100?.symbol, "cLine 100 should carry symbol 'damage'")
        assertEquals("battle/turn", loc100?.context, "cLine 100 should carry context 'battle/turn'")
    }

    @Test
    fun `resolve returns null for unmapped C line`() {
        val mapDir = createMapDir()
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 42, "kotlinFile": "GameDef.kt", "kotlinLine": 15}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        assertNull(resolver.resolve(999), "Unmapped C line should return null")
        assertNull(resolver.resolve(0), "C line 0 not in map should return null")
        assertNull(resolver.resolve(-1), "Negative C line should return null")
    }

    // ── Optional fields ───────────────────────────────────────────────────────

    @Test
    fun `resolve handles missing optional fields with defaults`() {
        val mapDir = createMapDir()
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 10, "kotlinFile": "Minimal.kt", "kotlinLine": 5}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        val loc = resolver.resolve(10)
        assertEquals("Minimal.kt", loc?.file)
        assertEquals(5, loc?.line)
        assertEquals("", loc?.symbol, "Missing symbol should default to empty string")
        assertEquals("", loc?.context, "Missing snippet should default to empty string")
    }

    // ── Null/missing directory ────────────────────────────────────────────────

    @Test
    fun `null sourceMapsDir means all resolves return null`() {
        val resolver = SourceMapResolver(null)
        assertNull(resolver.resolve(42), "Null sourceMapsDir: all resolves should be null")
        assertNull(resolver.resolve(0), "Null sourceMapsDir: all resolves should be null")
    }

    @Test
    fun `non-existent directory means all resolves return null`() {
        val nonExistent = File(tempDir.toFile(), "does-not-exist")
        val resolver = SourceMapResolver(nonExistent)
        assertNull(resolver.resolve(42), "Non-existent dir: all resolves should be null")
    }

    @Test
    fun `empty directory means all resolves return null`() {
        val emptyDir = createMapDir()
        val resolver = SourceMapResolver(emptyDir)
        assertNull(resolver.resolve(42), "Empty dir: all resolves should be null")
    }

    // ── Multiple map files ────────────────────────────────────────────────────

    @Test
    fun `multiple gbkt map files are all loaded`() {
        val mapDir = createMapDir()
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 10, "kotlinFile": "SceneA.kt", "kotlinLine": 1}
              ]
            }
            """
                .trimIndent(),
        )
        writeMapFile(
            mapDir,
            "bank1.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "bank1.c",
              "mappings": [
                {"cLine": 200, "kotlinFile": "SceneB.kt", "kotlinLine": 50}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        // Lines from first file (backward-compatible single-arg resolve)
        val loc10 = resolver.resolve(10)
        assertEquals("SceneA.kt", loc10?.file, "cLine 10 should be resolved from main.c.gbkt.map")
        assertEquals(1, loc10?.line)

        // Lines from second file (backward-compatible single-arg resolve)
        val loc200 = resolver.resolve(200)
        assertEquals(
            "SceneB.kt",
            loc200?.file,
            "cLine 200 should be resolved from bank1.c.gbkt.map",
        )
        assertEquals(50, loc200?.line)

        // File-qualified resolve
        val loc10Qualified = resolver.resolve("main.c", 10)
        assertEquals("SceneA.kt", loc10Qualified?.file, "resolve(main.c, 10) should find SceneA.kt")

        val loc200Qualified = resolver.resolve("bank1.c", 200)
        assertEquals(
            "SceneB.kt",
            loc200Qualified?.file,
            "resolve(bank1.c, 200) should find SceneB.kt",
        )
    }

    @Test
    fun `multi-bank entries with same line number resolve correctly`() {
        val mapDir = createMapDir()
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 10, "kotlinFile": "MainScene.kt", "kotlinLine": 5, "symbol": "score", "snippet": "main/frame"}
              ]
            }
            """
                .trimIndent(),
        )
        writeMapFile(
            mapDir,
            "bank1.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "bank1.c",
              "mappings": [
                {"cLine": 10, "kotlinFile": "BattleScene.kt", "kotlinLine": 20, "symbol": "damage", "snippet": "battle/turn"}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        // File-qualified resolve distinguishes same cLine across different C files
        val mainLoc = resolver.resolve("main.c", 10)
        assertNotNull(mainLoc, "resolve(main.c, 10) should find a mapping")
        assertEquals("MainScene.kt", mainLoc.file)
        assertEquals(5, mainLoc.line)
        assertEquals("score", mainLoc.symbol)
        assertEquals("main/frame", mainLoc.context)

        val bankLoc = resolver.resolve("bank1.c", 10)
        assertNotNull(bankLoc, "resolve(bank1.c, 10) should find a mapping")
        assertEquals("BattleScene.kt", bankLoc.file)
        assertEquals(20, bankLoc.line)
        assertEquals("damage", bankLoc.symbol)
        assertEquals("battle/turn", bankLoc.context)

        // The two-arg resolve for different files must return different locations
        assertNotEquals(
            mainLoc,
            bankLoc,
            "Same cLine in different C files must resolve differently",
        )

        // Single-arg resolve still works (returns one of the two — no collision loss)
        val anyLoc = resolver.resolve(10)
        assertNotNull(anyLoc, "Single-arg resolve(10) should still find at least one mapping")
    }

    @Test
    fun `non-gbkt-map files in directory are ignored`() {
        val mapDir = createMapDir()
        // Write a non-.gbkt.map file — should be ignored
        File(mapDir, "notes.txt").writeText("this is not a source map")
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 5, "kotlinFile": "Game.kt", "kotlinLine": 10}
              ]
            }
            """
                .trimIndent(),
        )

        val resolver = SourceMapResolver(mapDir)

        // The .gbkt.map file is loaded
        assertEquals("Game.kt", resolver.resolve(5)?.file)
        // The txt file is ignored (no crash)
        assertNull(resolver.resolve(99))
    }

    @Test
    fun `malformed gbkt map file is silently skipped`() {
        val mapDir = createMapDir()
        // Write a malformed JSON file
        File(mapDir, "bad.gbkt.map").writeText("{ this is not valid json }")
        // Write a valid file alongside it
        writeMapFile(
            mapDir,
            "good.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 7, "kotlinFile": "Good.kt", "kotlinLine": 3}
              ]
            }
            """
                .trimIndent(),
        )

        // Should not throw — bad file is skipped, good file is loaded
        val resolver = SourceMapResolver(mapDir)
        assertEquals("Good.kt", resolver.resolve(7)?.file, "Good map file should still be loaded")
        assertNull(resolver.resolve(999))
    }

    // ── PC resolution via .noi ───────────────────────────────────────────────

    @Test
    fun `resolveByPc returns null when no noi file provided`() {
        val mapDir = createMapDir()
        val resolver = SourceMapResolver(mapDir, noiFile = null)
        assertNull(resolver.resolveByPc(0x200))
    }

    @Test
    fun `resolveByPc returns null for null pc`() {
        val mapDir = createMapDir()
        val noiFile = createNoiFile("DEF _game_frame 0x200\n")
        val resolver = SourceMapResolver(mapDir, noiFile)
        assertNull(resolver.resolveByPc(null))
    }

    @Test
    fun `resolveByPc resolves PC to function first cLine`() {
        val mapDir = createMapDir()
        // Write generated C file with two functions
        File(mapDir, "bank1.c")
            .writeText(
                """
                // Generated by gbkt
                #pragma bank 1
                #include "game.h"
                void game_enter(void) BANKED {
                    cls();
                }
                void game_frame(void) BANKED {
                    _ball_x += _ballDx;
                    if (_ball_y < 16u) {
                        _ballDy = 1u;
                    }
                }
                """
                    .trimIndent()
            )
        // Write source map with entries for game_frame starting at cLine 7
        writeMapFile(
            mapDir,
            "bank1.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "bank1.c",
              "mappings": [
                {"cLine": 5, "kotlinFile": "Game.kt", "kotlinLine": 10},
                {"cLine": 8, "kotlinFile": "Game.kt", "kotlinLine": 30},
                {"cLine": 9, "kotlinFile": "Game.kt", "kotlinLine": 35}
              ]
            }
            """
                .trimIndent(),
        )
        // NOI file: game_enter at 0x100, game_frame at 0x200
        val noiFile =
            createNoiFile(
                """
                DEF _game_enter 0x100
                DEF _game_frame 0x200
                """
                    .trimIndent()
            )

        val resolver = SourceMapResolver(mapDir, noiFile)

        // PC 0x210 is inside game_frame → should resolve to first cLine of game_frame (8)
        val cLine = resolver.resolveByPc(0x210)
        assertNotNull(cLine, "PC inside game_frame should resolve to a cLine")
        assertEquals(8, cLine, "Should resolve to first mapped cLine of game_frame")
    }

    @Test
    fun `resolveByPc filters out non-function symbols`() {
        val mapDir = createMapDir()
        File(mapDir, "main.c").writeText("void main(void) {\n}\n")
        writeMapFile(
            mapDir,
            "main.c.gbkt.map",
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                {"cLine": 1, "kotlinFile": "Game.kt", "kotlinLine": 1}
              ]
            }
            """
                .trimIndent(),
        )
        // NOI with section markers and register aliases that should be filtered
        val noiFile =
            createNoiFile(
                """
                DEF l__CODE_0 0x0
                DEF s__CODE 0x100
                DEF _rRAMG 0x0
                DEF _shadow_OAM 0xC000
                DEF _main 0x200
                """
                    .trimIndent()
            )

        val resolver = SourceMapResolver(mapDir, noiFile)

        // PC 0x50 has no matching function (only non-function symbols below 0x200)
        assertNull(resolver.resolveByPc(0x50), "Non-function symbols should be filtered")

        // PC 0x210 is inside _main
        assertNotNull(resolver.resolveByPc(0x210), "PC inside _main should resolve")
    }

    @Test
    fun `resolveByPc with non-existent noi file returns null`() {
        val mapDir = createMapDir()
        val fakeNoi = File(tempDir.toFile(), "nonexistent.noi")
        val resolver = SourceMapResolver(mapDir, fakeNoi)
        assertNull(resolver.resolveByPc(0x200))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createMapDir(): File {
        val dir = tempDir.resolve("sourcemaps").toFile()
        dir.mkdirs()
        return dir
    }

    private fun writeMapFile(dir: File, name: String, content: String) {
        File(dir, name).writeText(content)
    }

    private fun createNoiFile(content: String): File {
        val file = File(tempDir.toFile(), "test.noi")
        file.writeText(content)
        return file
    }
}
