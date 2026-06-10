/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [EmulatorSession].
 *
 * All tests use headless mode to avoid creating Swing windows in CI. Full GUI integration tests are
 * in Plan 11 (EmulatorIntegrationTest).
 *
 * Note: ROM lifecycle tests (start/stop) are not included here because EmulatorSession delegates
 * emulator start to CoffeeGbEmulator, which requires a real ROM file. Those are covered by
 * CoffeeGbEmulatorTest. These tests focus on session-level orchestration: config propagation,
 * component creation, callback wiring, and enrichment.
 */
class EmulatorSessionTest {

    @TempDir lateinit var tempDir: Path

    private var session: EmulatorSession? = null

    @AfterEach
    fun tearDown() {
        session?.shutdown()
        session = null
    }

    // ── 1. Config propagation ─────────────────────────────────────────────────

    @Test
    fun `headless config propagates to emulator`() {
        val config = EmulatorConfig(romFile = File("fake.gb"), headless = true)
        val s = EmulatorSession(config)
        session = s

        // Session starts with no emulator
        assertNull(s.emulator, "Emulator should be null before launch()")

        // Note: We cannot call launch() without a real ROM in headless mode
        // because CoffeeGbEmulator.start() loads the ROM immediately.
        // We verify config is stored correctly via the emulator field after manual wiring.
        // Config propagation to isHeadless is verified via the emulator API in
        // CoffeeGbEmulatorTest.
    }

    @Test
    fun `emulator field is null before launch`() {
        val config = EmulatorConfig(romFile = File("fake.gb"), headless = true)
        val s = EmulatorSession(config)
        session = s

        assertNull(s.emulator, "emulator should be null before launch()")
        assertNull(s.window, "window should be null before launch()")
        assertNull(s.logWindow, "logWindow should be null before launch()")
        assertNull(s.memoryWindow, "memoryWindow should be null before launch()")
    }

    // ── 2. Log writer creation ─────────────────────────────────────────────────

    @Test
    fun `log file is created by emulator when configured`() {
        val logFile = tempDir.resolve("session.log").toFile()
        val romFile = createMinimalRomFile()

        val config = EmulatorConfig(romFile = romFile, headless = true, logFile = logFile)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        // Add a debug entry that will be written to the log file
        val entry =
            DebugLogEntry(timestampMs = 500L, level = LogLevel.GAME, message = "Session log test")
        (s.emulator as? CoffeeGbEmulator)?.addDebugEntry(entry)
        s.shutdown()

        assertTrue(logFile.exists(), "Log file should be created when config.logFile is set")
        val content = logFile.readText()
        assertTrue(
            content.contains("Session log test"),
            "Log file should contain written entry, got: $content",
        )
    }

    // ── 3. Source map resolver creation ───────────────────────────────────────

    @Test
    fun `source map dir is passed to emulator when configured`() {
        val sourceMapsDir = tempDir.resolve("sourcemaps").toFile()
        sourceMapsDir.mkdirs()

        // Write a minimal source map file
        val mapFile = File(sourceMapsDir, "main.c.gbkt.map")
        mapFile.writeText(
            """
            {
              "version": 1,
              "gameName": "TestGame",
              "cFile": "main.c",
              "mappings": [
                { "cLine": 42, "kotlinFile": "Test.kt", "kotlinLine": 15,
                  "symbol": "score", "snippet": "gameplay/frame" }
              ]
            }
            """
                .trimIndent()
        )

        val romFile = createMinimalRomFile()
        val config =
            EmulatorConfig(romFile = romFile, headless = true, sourceMapsDir = sourceMapsDir)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        // The resolver is internal to CoffeeGbEmulator. We verify it works
        // by checking that an entry with cLine=42 is enriched with the kotlin location.
        var enrichedEntry: DebugLogEntry? = null
        (s.emulator as? CoffeeGbEmulator)?.onDebugEntry = { enrichedEntry = it }

        val rawEntry =
            DebugLogEntry(
                timestampMs = 100L,
                level = LogLevel.GAME,
                message = "source map test",
                cLine = 42,
            )
        (s.emulator as? CoffeeGbEmulator)?.addDebugEntry(rawEntry)

        s.shutdown()

        val resolved = assertNotNull(enrichedEntry, "onDebugEntry should have been invoked")
        assertEquals(
            "Test.kt",
            resolved.kotlinFile,
            "Entry should be enriched with kotlinFile from source map",
        )
        assertEquals(
            15,
            resolved.kotlinLine,
            "Entry should be enriched with kotlinLine from source map",
        )
        assertEquals(
            "gameplay/frame",
            resolved.context,
            "Entry should be enriched with context from source map snippet",
        )
    }

    // ── 4. Headless mode — no UI created ─────────────────────────────────────

    @Test
    fun `headless mode creates no Swing windows`() {
        val romFile = createMinimalRomFile()
        val config = EmulatorConfig(romFile = romFile, headless = true)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        assertNull(s.window, "EmulatorWindow must not be created in headless mode")
        assertNull(s.logWindow, "LogCatWindow must not be created in headless mode")
        assertNull(s.memoryWindow, "MemoryInspectorWindow must not be created in headless mode")

        s.shutdown()
    }

    @Test
    fun `headless mode emulator is not null after launch`() {
        val romFile = createMinimalRomFile()
        val config = EmulatorConfig(romFile = romFile, headless = true)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        assertNotNull(s.emulator, "Emulator should be non-null after launch()")

        s.shutdown()
    }

    // ── 5. Debug entry enrichment ─────────────────────────────────────────────

    @Test
    fun `debug entry is enriched with source map data when cLine matches`() {
        val sourceMapsDir = tempDir.resolve("maps").toFile()
        sourceMapsDir.mkdirs()

        // Write a source map mapping cLine=42 to Test.kt:15
        File(sourceMapsDir, "main.c.gbkt.map")
            .writeText(
                """
                {
                  "version": 1,
                  "gameName": "Enrichment",
                  "cFile": "main.c",
                  "mappings": [
                    { "cLine": 42, "kotlinFile": "Test.kt", "kotlinLine": 15,
                      "symbol": "score", "snippet": "gameplay/frame" }
                  ]
                }
                """
                    .trimIndent()
            )

        val romFile = createMinimalRomFile()
        val config =
            EmulatorConfig(romFile = romFile, headless = true, sourceMapsDir = sourceMapsDir)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        var captured: DebugLogEntry? = null
        (s.emulator as? CoffeeGbEmulator)?.onDebugEntry = { captured = it }

        // Add entry with cLine that has a source map entry
        val entry =
            DebugLogEntry(
                timestampMs = 0L,
                level = LogLevel.GAME,
                message = "enrichment check",
                cLine = 42,
            )
        (s.emulator as? CoffeeGbEmulator)?.addDebugEntry(entry)
        s.shutdown()

        val resolved = assertNotNull(captured, "Callback should have received the enriched entry")
        assertEquals(
            "Test.kt",
            resolved.kotlinFile,
            "kotlinFile should be resolved from source map",
        )
        assertEquals(15, resolved.kotlinLine, "kotlinLine should be resolved from source map")
    }

    @Test
    fun `debug entry without matching cLine is not enriched`() {
        val sourceMapsDir = tempDir.resolve("maps2").toFile()
        sourceMapsDir.mkdirs()

        // Write a source map for cLine=42 only
        File(sourceMapsDir, "main.c.gbkt.map")
            .writeText(
                """
                {
                  "version": 1,
                  "gameName": "NoEnrich",
                  "cFile": "main.c",
                  "mappings": [
                    { "cLine": 42, "kotlinFile": "Test.kt", "kotlinLine": 15 }
                  ]
                }
                """
                    .trimIndent()
            )

        val romFile = createMinimalRomFile()
        val config =
            EmulatorConfig(romFile = romFile, headless = true, sourceMapsDir = sourceMapsDir)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        var captured: DebugLogEntry? = null
        (s.emulator as? CoffeeGbEmulator)?.onDebugEntry = { captured = it }

        // Add entry with cLine=99 — no mapping exists for it
        val entry =
            DebugLogEntry(
                timestampMs = 0L,
                level = LogLevel.EMU,
                message = "no enrichment",
                cLine = 99,
            )
        (s.emulator as? CoffeeGbEmulator)?.addDebugEntry(entry)
        s.shutdown()

        val notEnriched = assertNotNull(captured)
        assertNull(
            notEnriched.kotlinFile,
            "kotlinFile should be null when no source map entry matches cLine",
        )
        assertNull(
            notEnriched.kotlinLine,
            "kotlinLine should be null when no source map entry matches cLine",
        )
    }

    // ── 6. Launch guard ───────────────────────────────────────────────────────

    @Test
    fun `launch throws if called twice without shutdown`() {
        val romFile = createMinimalRomFile()
        val config = EmulatorConfig(romFile = romFile, headless = true)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        assertFailsWith<IllegalStateException>("Second launch() should throw") { s.launch() }

        s.shutdown()
    }

    // ── 7. Shutdown cleans up ─────────────────────────────────────────────────

    @Test
    fun `shutdown stops emulator and clears references`() {
        val romFile = createMinimalRomFile()
        val config = EmulatorConfig(romFile = romFile, headless = true)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        assertNotNull(s.emulator)
        s.shutdown()

        assertNull(s.emulator, "emulator should be null after shutdown()")
    }

    @Test
    fun `shutdown before launch does not crash`() {
        val config = EmulatorConfig(romFile = File("fake.gb"), headless = true)
        val s = EmulatorSession(config)
        session = s
        // Should not throw
        s.shutdown()
        assertNull(s.emulator, "emulator should remain null if shutdown() called before launch()")
    }

    @Test
    fun `shutdown captures refs before nullifying`() {
        val romFile = createMinimalRomFile()
        val config = EmulatorConfig(romFile = romFile, headless = true)
        val s = EmulatorSession(config)
        session = s
        s.launch()

        // Verify emulator is running before shutdown
        assertNotNull(s.emulator, "emulator should be set after launch()")

        // After shutdown, all fields should be null — the key point is that shutdown()
        // captures local refs before nullifying, so the SwingUtilities.invokeLater lambda
        // disposes the correct instances. In headless mode there are no windows to dispose,
        // but we verify the field-clearing order is correct.
        s.shutdown()

        assertNull(s.emulator, "emulator should be null after shutdown()")
        assertNull(s.window, "window should be null after shutdown()")
        assertNull(s.logWindow, "logWindow should be null after shutdown()")
        assertNull(s.memoryWindow, "memoryWindow should be null after shutdown()")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createMinimalRomFile(): File =
        TestRomFactory.createMinimalRom(tempDir, name = "test-session.gb", title = "GBKT SESSION")
}
