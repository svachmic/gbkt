/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import io.github.gbkt.emulator.LogLevel
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Tests for [DebugLogWriter] — verifies real-time auto-flush log file writing. */
class DebugLogWriterTest {

    @TempDir lateinit var tempDir: Path

    // ── Format correctness ────────────────────────────────────────────────────

    @Test
    fun `write produces correct format with full source map info`() {
        val logFile = tempDir.resolve("debug.log").toFile()
        val entry =
            DebugLogEntry(
                timestampMs = 2341L,
                level = LogLevel.GAME,
                message = "Score: 10",
                kotlinFile = "ScriptBuilder.kt",
                kotlinLine = 45,
                context = "gameplay/frame",
            )

        DebugLogWriter(logFile).use { writer -> writer.write(entry) }

        val content = logFile.readText()
        assertTrue(content.contains("[00:02.341]"), "Should contain timestamp [MM:SS.mmm]")
        assertTrue(content.contains("ScriptBuilder.kt:45"), "Should contain kotlin file:line")
        assertTrue(content.contains("(gameplay/frame)"), "Should contain context")
        assertTrue(content.contains("Score: 10"), "Should contain message")
        assertTrue(content.contains("> "), "Should contain > separator")
        assertTrue(content.endsWith("\n"), "Entry should end with newline")
    }

    @Test
    fun `write produces correct format without source map info`() {
        val logFile = tempDir.resolve("debug.log").toFile()
        val entry =
            DebugLogEntry(
                timestampMs = 2341L,
                level = LogLevel.EMU,
                message = "ROM bank 3 switched",
            )

        DebugLogWriter(logFile).use { writer -> writer.write(entry) }

        val content = logFile.readText()
        assertTrue(content.contains("[00:02.341]"), "Should contain timestamp")
        assertFalse(content.contains(".kt:"), "Should not contain kotlin file when null")
        assertTrue(content.contains("ROM bank 3 switched"), "Should contain message")
        assertTrue(content.endsWith("\n"), "Entry should end with newline")
    }

    @Test
    fun `write produces correct format with context but no source map`() {
        val logFile = tempDir.resolve("debug.log").toFile()
        val entry =
            DebugLogEntry(
                timestampMs = 5000L,
                level = LogLevel.GAME,
                message = "battle started",
                context = "battle/enter",
            )

        DebugLogWriter(logFile).use { writer -> writer.write(entry) }

        val content = logFile.readText()
        assertTrue(
            content.contains("(battle/enter)"),
            "Should contain context even without source map",
        )
        assertTrue(content.contains("battle started"), "Should contain message")
    }

    // ── Multiple entries ──────────────────────────────────────────────────────

    @Test
    fun `multiple writes produce multiple lines`() {
        val logFile = tempDir.resolve("debug.log").toFile()
        val entries =
            listOf(
                DebugLogEntry(0L, LogLevel.GAME, "First"),
                DebugLogEntry(100L, LogLevel.EMU, "Second"),
                DebugLogEntry(200L, LogLevel.WARN, "Third"),
            )

        DebugLogWriter(logFile).use { writer -> entries.forEach { writer.write(it) } }

        val lines = logFile.readLines()
        assertEquals(3, lines.size, "Three entries should produce three lines")
        assertTrue(lines[0].contains("First"), "First line should contain 'First'")
        assertTrue(lines[1].contains("Second"), "Second line should contain 'Second'")
        assertTrue(lines[2].contains("Third"), "Third line should contain 'Third'")
    }

    // ── Overwrite semantics (fresh per run) ───────────────────────────────────

    @Test
    fun `new DebugLogWriter overwrites existing file content`() {
        val logFile = tempDir.resolve("debug.log").toFile()

        // First run
        DebugLogWriter(logFile).use { writer ->
            writer.write(DebugLogEntry(0L, LogLevel.GAME, "first run content"))
        }

        // Second run — should overwrite, not append
        DebugLogWriter(logFile).use { writer ->
            writer.write(DebugLogEntry(0L, LogLevel.GAME, "second run content"))
        }

        val content = logFile.readText()
        assertFalse(content.contains("first run content"), "Old content should be overwritten")
        assertTrue(content.contains("second run content"), "New content should be present")
    }

    // ── Parent directory creation ─────────────────────────────────────────────

    @Test
    fun `DebugLogWriter creates parent directories automatically`() {
        val nestedFile = tempDir.resolve("build/gbkt/logs/debug.log").toFile()
        assertFalse(nestedFile.parentFile.exists(), "Parent dirs should not exist before write")

        DebugLogWriter(nestedFile).use { writer ->
            writer.write(DebugLogEntry(0L, LogLevel.GAME, "nested log"))
        }

        assertTrue(nestedFile.exists(), "Log file should be created at nested path")
        assertTrue(nestedFile.readText().contains("nested log"), "Log content should be correct")
    }

    // ── Close behavior ────────────────────────────────────────────────────────

    @Test
    fun `close flushes remaining data to disk`() {
        val logFile = tempDir.resolve("debug.log").toFile()
        val writer = DebugLogWriter(logFile)
        writer.write(DebugLogEntry(0L, LogLevel.GAME, "before close"))
        writer.close()

        // File should be readable and contain the entry after close
        val content = logFile.readText()
        assertTrue(content.contains("before close"), "Data should be flushed to disk on close")
    }

    @Test
    fun `use block closes writer automatically`() {
        val logFile = tempDir.resolve("debug.log").toFile()

        DebugLogWriter(logFile).use { writer ->
            writer.write(DebugLogEntry(0L, LogLevel.GAME, "auto-closed"))
        }

        // After use block, writer is closed and data is on disk
        assertTrue(logFile.readText().contains("auto-closed"), "Use block should auto-close writer")
    }

    // ── Timestamp format ──────────────────────────────────────────────────────

    @Test
    fun `timestamp format is MM colon SS dot mmm`() {
        val logFile = tempDir.resolve("debug.log").toFile()

        // 90500ms = 1 minute 30.500 seconds
        DebugLogWriter(logFile).use { writer ->
            writer.write(DebugLogEntry(90_500L, LogLevel.EMU, "timing test"))
        }

        val content = logFile.readText()
        assertTrue(
            content.contains("[01:30.500]"),
            "Timestamp should be [01:30.500] for 90500ms, got: $content",
        )
    }

    @Test
    fun `zero timestamp formats correctly`() {
        val logFile = tempDir.resolve("debug.log").toFile()

        DebugLogWriter(logFile).use { writer ->
            writer.write(DebugLogEntry(0L, LogLevel.GAME, "zero timestamp"))
        }

        val content = logFile.readText()
        assertTrue(content.contains("[00:00.000]"), "Zero timestamp should format as [00:00.000]")
    }
}
