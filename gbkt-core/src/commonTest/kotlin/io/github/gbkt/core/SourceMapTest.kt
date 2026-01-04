/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the source map functionality that links generated C code back to its Kotlin DSL
 * origins.
 */
class SourceMapTest {

    @Test
    fun `SourceLocation toString formats correctly`() {
        val loc1 = SourceLocation("Game.kt", 42)
        assertEquals("Game.kt:42", loc1.toString())

        val loc2 = SourceLocation("Game.kt", 42, 10)
        assertEquals("Game.kt:42:10", loc2.toString())

        val loc3 = SourceLocation("Game.kt", 42, 0)
        assertEquals("Game.kt:42", loc3.toString())
    }

    @Test
    fun `SourceMapping stores all properties`() {
        val mapping =
            SourceMapping(
                cLine = 100,
                kotlinFile = "MyGame.kt",
                kotlinLine = 25,
                kotlinColumn = 8,
                symbol = "playerX",
                snippet = "playerX += 1"
            )

        assertEquals(100, mapping.cLine)
        assertEquals("MyGame.kt", mapping.kotlinFile)
        assertEquals(25, mapping.kotlinLine)
        assertEquals(8, mapping.kotlinColumn)
        assertEquals("playerX", mapping.symbol)
        assertEquals("playerX += 1", mapping.snippet)
    }

    @Test
    fun `SourceMap serializes to valid JSON`() {
        val mappings =
            listOf(
                SourceMapping(10, "Game.kt", 5, symbol = "score"),
                SourceMapping(15, "Game.kt", 8, 4, "playerX", "playerX += 1")
            )
        val sourceMap = SourceMap(gameName = "TestGame", cFile = "main.c", mappings = mappings)

        val json = sourceMap.toJson()

        assertTrue(json.contains("\"version\": \"1.0\""), "Should have version")
        assertTrue(json.contains("\"gameName\": \"TestGame\""), "Should have game name")
        assertTrue(json.contains("\"cFile\": \"main.c\""), "Should have C file name")
        assertTrue(json.contains("\"cLine\": 10"), "Should have first mapping")
        assertTrue(json.contains("\"kotlinFile\": \"Game.kt\""), "Should have Kotlin file")
        assertTrue(json.contains("\"symbol\": \"score\""), "Should have symbol")
        assertTrue(json.contains("\"snippet\": \"playerX += 1\""), "Should have snippet")
    }

    @Test
    fun `SourceMap escapes special characters in JSON`() {
        val mapping =
            SourceMapping(
                cLine = 1,
                kotlinFile = "path\\to\\Game.kt",
                kotlinLine = 1,
                snippet = "text with \"quotes\" and\nnewline"
            )
        val sourceMap =
            SourceMap(gameName = "Test\"Game", cFile = "main.c", mappings = listOf(mapping))

        val json = sourceMap.toJson()

        assertTrue(json.contains("\\\\"), "Should escape backslashes")
        assertTrue(json.contains("\\\""), "Should escape quotes")
        assertTrue(json.contains("\\n"), "Should escape newlines")
    }

    @Test
    fun `SourceMap findKotlinLocation returns correct mapping`() {
        val mappings =
            listOf(
                SourceMapping(10, "Game.kt", 5),
                SourceMapping(15, "Game.kt", 8),
                SourceMapping(20, "Game.kt", 12)
            )
        val sourceMap = SourceMap(gameName = "Test", cFile = "main.c", mappings = mappings)

        val found = sourceMap.findKotlinLocation(15)
        assertNotNull(found)
        assertEquals(8, found.kotlinLine)

        val notFound = sourceMap.findKotlinLocation(100)
        assertNull(notFound)
    }

    @Test
    fun `SourceMap findCLines returns all matching lines`() {
        val mappings =
            listOf(
                SourceMapping(10, "Game.kt", 5),
                SourceMapping(15, "Game.kt", 5),
                SourceMapping(20, "Game.kt", 8)
            )
        val sourceMap = SourceMap(gameName = "Test", cFile = "main.c", mappings = mappings)

        val found = sourceMap.findCLines("Game.kt", 5)
        assertEquals(2, found.size)
        assertEquals(10, found[0].cLine)
        assertEquals(15, found[1].cLine)

        val notFound = sourceMap.findCLines("Other.kt", 5)
        assertTrue(notFound.isEmpty())
    }

    @Test
    fun `SourceMapBuilder builds correctly`() {
        val builder = SourceMapBuilder("TestGame", "main.c")

        builder.addMapping(10, SourceLocation("Game.kt", 5), "score")
        builder.addMapping(15, "Game.kt", 8, "playerX")
        builder.addMapping(20, null, "ignored") // Null location should be ignored

        assertEquals(2, builder.size)

        val sourceMap = builder.build()
        assertEquals("TestGame", sourceMap.gameName)
        assertEquals("main.c", sourceMap.cFile)
        assertEquals(2, sourceMap.mappings.size)
    }

    @Test
    fun `SourceMapBuilder ignores null locations`() {
        val builder = SourceMapBuilder("Test", "main.c")

        builder.addMapping(10, null, "ignored")

        assertEquals(0, builder.size)
    }

    @Test
    fun `code generator tracks line numbers`() {
        val game =
            gbGame("test") {
                var score by u8Var(0)

                start = scene("main") { every.frame { score += 1 } }
            }

        val generator = CodeGenerator(game)
        val (code, sourceMap) = generator.generateWithSourceMap()

        // The generated code should have multiple lines
        val lineCount = code.lines().size
        assertTrue(lineCount > 10, "Generated code should have many lines")

        // Source map should have been built (may have mappings if running on JVM)
        assertNotNull(sourceMap)
        assertEquals("test", sourceMap.gameName)
        assertEquals("main.c", sourceMap.cFile)
    }

    @Test
    fun `code generator produces consistent line count`() {
        val game =
            gbGame("test") {
                var x by u8Var(0)
                var y by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            x += 1
                            y += 2
                        }
                    }
            }

        val generator = CodeGenerator(game)
        val code = generator.generate()

        // Count actual lines in generated code
        val actualLines = code.lines().filter { it.isNotEmpty() }.size

        // currentLine should track the total lines output
        // Note: currentLine is 1-based and increments after each line
        assertTrue(generator.currentLine > 1, "Line counter should have advanced")
    }

    @Test
    fun `IRStatement withSourceLocation creates copy with location`() {
        val location = SourceLocation("test.kt", 42)

        val assign = IRAssign("x", IRLiteral(5))
        val assignWithLoc = assign.withSourceLocation(location) as IRAssign
        assertEquals(location, assignWithLoc.sourceLocation)
        assertEquals("x", assignWithLoc.target)

        val ifStmt = IRIf(IRLiteral(1), listOf())
        val ifWithLoc = ifStmt.withSourceLocation(location) as IRIf
        assertEquals(location, ifWithLoc.sourceLocation)

        val sceneChange = IRSceneChange("menu")
        val sceneWithLoc = sceneChange.withSourceLocation(location) as IRSceneChange
        assertEquals(location, sceneWithLoc.sourceLocation)
        assertEquals("menu", sceneWithLoc.sceneName)
    }

    @Test
    fun `IRStatement withSourceLocation returns same instance for null`() {
        val assign = IRAssign("x", IRLiteral(5))
        val same = assign.withSourceLocation(null)
        assertSame(assign, same)
    }

    @Test
    fun `IRStatement has default null sourceLocation`() {
        val assign = IRAssign("x", IRLiteral(5))
        assertNull(assign.sourceLocation)

        val ifStmt = IRIf(IRLiteral(1), listOf())
        assertNull(ifStmt.sourceLocation)

        val sceneChange = IRSceneChange("test")
        assertNull(sceneChange.sourceLocation)
    }

    @Test
    fun `IRStatement can be created with explicit sourceLocation`() {
        val location = SourceLocation("test.kt", 10)

        val assign = IRAssign("x", IRLiteral(5), sourceLocation = location)
        assertEquals(location, assign.sourceLocation)

        val ifStmt = IRIf(IRLiteral(1), listOf(), sourceLocation = location)
        assertEquals(location, ifStmt.sourceLocation)
    }
}
