/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.debug.DebugLogEntry
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LogCatPanel].
 *
 * Tests focus on entry accumulation, level filtering, clear, formatted output, and auto-scroll
 * caret positioning — without verifying visual rendering.
 *
 * All assertions are made after [SwingUtilities.invokeAndWait] to ensure that EDT-deferred updates
 * from [LogCatPanel.appendEntry] have completed.
 */
class LogCatPanelTest {

    private lateinit var panel: LogCatPanel

    @BeforeEach
    fun setup() {
        // Create panel on EDT to satisfy Swing component requirements
        SwingUtilities.invokeAndWait { panel = LogCatPanel() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeEntry(
        level: LogLevel,
        message: String,
        timestampMs: Long = 0L,
        kotlinFile: String? = null,
        kotlinLine: Int? = null,
        context: String? = null,
    ): DebugLogEntry =
        DebugLogEntry(
            timestampMs = timestampMs,
            level = level,
            message = message,
            kotlinFile = kotlinFile,
            kotlinLine = kotlinLine,
            context = context,
        )

    /** Appends an entry and waits for the EDT to process it. */
    private fun appendAndWait(entry: DebugLogEntry) {
        panel.appendEntry(entry)
        SwingUtilities.invokeAndWait { /* drain EDT queue */ }
    }

    // ── Entry accumulation ────────────────────────────────────────────────────

    @Test
    fun `single entry is stored and displayed`() {
        val entry = makeEntry(LogLevel.GAME, "score updated")
        appendAndWait(entry)

        assertEquals(1, panel.entries.size)
        assertTrue(panel.displayText().contains("score updated"))
    }

    @Test
    fun `multiple entries accumulate in order`() {
        appendAndWait(makeEntry(LogLevel.GAME, "first"))
        appendAndWait(makeEntry(LogLevel.EMU, "second"))
        appendAndWait(makeEntry(LogLevel.WARN, "third"))

        assertEquals(3, panel.entries.size)
        val text = panel.displayText()
        // All three messages appear in order
        val firstIdx = text.indexOf("first")
        val secondIdx = text.indexOf("second")
        val thirdIdx = text.indexOf("third")
        assertTrue(firstIdx < secondIdx, "first should appear before second")
        assertTrue(secondIdx < thirdIdx, "second should appear before third")
    }

    @Test
    fun `entries from all log levels are stored`() {
        appendAndWait(makeEntry(LogLevel.GAME, "game msg"))
        appendAndWait(makeEntry(LogLevel.EMU, "emu msg"))
        appendAndWait(makeEntry(LogLevel.WARN, "warn msg"))
        appendAndWait(makeEntry(LogLevel.ERROR, "error msg"))

        assertEquals(4, panel.entries.size)
    }

    // ── Level filtering ───────────────────────────────────────────────────────

    @Test
    fun `filter GAME shows only GAME entries`() {
        appendAndWait(makeEntry(LogLevel.GAME, "game message"))
        appendAndWait(makeEntry(LogLevel.EMU, "emu message"))
        appendAndWait(makeEntry(LogLevel.WARN, "warn message"))
        appendAndWait(makeEntry(LogLevel.ERROR, "error message"))

        // Select GAME filter via combo box
        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "GAME" }
        // Drain EDT after filter change triggers refreshDisplay
        SwingUtilities.invokeAndWait {}

        val text = panel.displayText()
        assertTrue(text.contains("game message"), "GAME filter should show game entries")
        assertTrue(!text.contains("emu message"), "GAME filter should hide EMU entries")
        assertTrue(!text.contains("warn message"), "GAME filter should hide WARN entries")
        assertTrue(!text.contains("error message"), "GAME filter should hide ERROR entries")
    }

    @Test
    fun `filter ERROR shows only ERROR entries`() {
        appendAndWait(makeEntry(LogLevel.GAME, "game message"))
        appendAndWait(makeEntry(LogLevel.ERROR, "fatal error"))

        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "ERROR" }
        SwingUtilities.invokeAndWait {}

        val text = panel.displayText()
        assertTrue(text.contains("fatal error"), "ERROR filter should show ERROR entries")
        assertTrue(!text.contains("game message"), "ERROR filter should hide GAME entries")
    }

    @Test
    fun `filter WARN shows only WARN entries`() {
        appendAndWait(makeEntry(LogLevel.WARN, "low battery"))
        appendAndWait(makeEntry(LogLevel.EMU, "bank switched"))

        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "WARN" }
        SwingUtilities.invokeAndWait {}

        val text = panel.displayText()
        assertTrue(text.contains("low battery"), "WARN filter should show WARN entries")
        assertTrue(!text.contains("bank switched"), "WARN filter should hide EMU entries")
    }

    @Test
    fun `filter EMU shows only EMU entries`() {
        appendAndWait(makeEntry(LogLevel.EMU, "rom loaded"))
        appendAndWait(makeEntry(LogLevel.GAME, "scene entered"))

        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "EMU" }
        SwingUtilities.invokeAndWait {}

        val text = panel.displayText()
        assertTrue(text.contains("rom loaded"), "EMU filter should show EMU entries")
        assertTrue(!text.contains("scene entered"), "EMU filter should hide GAME entries")
    }

    @Test
    fun `filter ALL restores all entries after narrowing`() {
        appendAndWait(makeEntry(LogLevel.GAME, "game message"))
        appendAndWait(makeEntry(LogLevel.EMU, "emu message"))

        // First narrow to GAME
        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "GAME" }
        SwingUtilities.invokeAndWait {}

        // Then restore to ALL
        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "ALL" }
        SwingUtilities.invokeAndWait {}

        val text = panel.displayText()
        assertTrue(text.contains("game message"), "ALL filter should show game entries")
        assertTrue(text.contains("emu message"), "ALL filter should show emu entries")
        assertEquals(null, panel.currentFilter)
    }

    @Test
    fun `entries added while filtered are stored but not displayed`() {
        // Set filter to ERROR before adding a GAME entry
        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "ERROR" }
        SwingUtilities.invokeAndWait {}

        appendAndWait(makeEntry(LogLevel.GAME, "hidden game msg"))

        // Entry is stored
        assertEquals(1, panel.entries.size)
        // But not shown in display
        assertTrue(!panel.displayText().contains("hidden game msg"))

        // Switch to ALL — now it should appear
        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "ALL" }
        SwingUtilities.invokeAndWait {}

        assertTrue(panel.displayText().contains("hidden game msg"))
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    @Test
    fun `clear removes all entries and empties display`() {
        appendAndWait(makeEntry(LogLevel.GAME, "entry one"))
        appendAndWait(makeEntry(LogLevel.EMU, "entry two"))

        SwingUtilities.invokeAndWait { panel.clearButton.doClick() }
        SwingUtilities.invokeAndWait {}

        assertEquals(0, panel.entries.size)
        assertEquals("", panel.displayText())
    }

    @Test
    fun `clear then append shows only new entry`() {
        appendAndWait(makeEntry(LogLevel.GAME, "old entry"))

        SwingUtilities.invokeAndWait { panel.clearButton.doClick() }
        SwingUtilities.invokeAndWait {}

        appendAndWait(makeEntry(LogLevel.GAME, "new entry"))

        val text = panel.displayText()
        assertTrue(!text.contains("old entry"), "Old entry should not appear after clear")
        assertTrue(text.contains("new entry"), "New entry should appear")
        assertEquals(1, panel.entries.size)
    }

    // ── Formatted output ──────────────────────────────────────────────────────

    @Test
    fun `GAME entry includes level prefix and formatted location`() {
        val entry =
            makeEntry(
                level = LogLevel.GAME,
                message = "Hello",
                timestampMs = 0L,
                kotlinFile = "Test.kt",
                kotlinLine = 42,
                context = "gameplay/frame",
            )
        appendAndWait(entry)

        val text = panel.displayText()
        // Level prefix
        assertTrue(text.contains("[GAME]"), "Should contain GAME level prefix")
        // Timestamp at 0ms
        assertTrue(text.contains("[00:00.000]"), "Should contain formatted timestamp")
        // Source location
        assertTrue(text.contains("Test.kt:42"), "Should contain Kotlin file and line")
        // Context
        assertTrue(text.contains("(gameplay/frame)"), "Should contain context")
        // Message
        assertTrue(text.contains("Hello"), "Should contain message")
    }

    @Test
    fun `EMU entry has EMU prefix and no source location when absent`() {
        val entry =
            makeEntry(level = LogLevel.EMU, message = "ROM bank 3 switched", timestampMs = 2341L)
        appendAndWait(entry)

        val text = panel.displayText()
        assertTrue(text.contains("[EMU]"), "Should contain EMU level prefix")
        assertTrue(text.contains("[00:02.341]"), "Should contain formatted timestamp for 2341ms")
        assertTrue(text.contains("ROM bank 3 switched"), "Should contain message")
    }

    @Test
    fun `WARN entry has WARN prefix`() {
        appendAndWait(makeEntry(LogLevel.WARN, "unimplemented opcode 0xD3"))

        val text = panel.displayText()
        assertTrue(text.contains("[WARN]"), "Should contain WARN level prefix")
        assertTrue(text.contains("unimplemented opcode 0xD3"), "Should contain message")
    }

    @Test
    fun `ERROR entry has ERR prefix`() {
        appendAndWait(makeEntry(LogLevel.ERROR, "stack overflow"))

        val text = panel.displayText()
        assertTrue(text.contains("[ERR]"), "Should contain ERR level prefix")
        assertTrue(text.contains("stack overflow"), "Should contain message")
    }

    @Test
    fun `full formatted line matches expected pattern`() {
        val entry =
            makeEntry(
                level = LogLevel.GAME,
                message = "Hello",
                timestampMs = 0L,
                kotlinFile = "Test.kt",
                kotlinLine = 42,
                context = "gameplay/frame",
            )
        appendAndWait(entry)

        val text = panel.displayText()
        // The line should contain: [GAME] [00:00.000] Test.kt:42 (gameplay/frame) > Hello
        assertTrue(
            text.contains("[GAME] [00:00.000] Test.kt:42 (gameplay/frame) > Hello"),
            "Full formatted output should match expected pattern. Actual: $text",
        )
    }

    // ── Auto-scroll ───────────────────────────────────────────────────────────

    @Test
    fun `caret is positioned at document end after appendEntry`() {
        appendAndWait(makeEntry(LogLevel.GAME, "line one"))
        appendAndWait(makeEntry(LogLevel.GAME, "line two"))
        appendAndWait(makeEntry(LogLevel.GAME, "line three"))

        // Verify caret is at document end (auto-scroll property)
        val results = IntArray(2)
        SwingUtilities.invokeAndWait {
            results[0] = panel.getCaretPosition()
            results[1] = panel.getDocumentLength()
        }

        assertTrue(
            results[0] > 0,
            "Caret position should be greater than 0 after appending entries",
        )
        assertEquals(
            results[1],
            results[0],
            "Caret position should equal document length (auto-scrolled to end)",
        )
    }

    // ── Visible entry count ───────────────────────────────────────────────────

    @Test
    fun `visibleEntryCount returns total with ALL filter`() {
        appendAndWait(makeEntry(LogLevel.GAME, "a"))
        appendAndWait(makeEntry(LogLevel.EMU, "b"))
        appendAndWait(makeEntry(LogLevel.WARN, "c"))

        var count = 0
        SwingUtilities.invokeAndWait { count = panel.visibleEntryCount() }

        assertEquals(3, count)
    }

    @Test
    fun `visibleEntryCount returns filtered count with level filter`() {
        appendAndWait(makeEntry(LogLevel.GAME, "game1"))
        appendAndWait(makeEntry(LogLevel.GAME, "game2"))
        appendAndWait(makeEntry(LogLevel.EMU, "emu1"))

        SwingUtilities.invokeAndWait { panel.filterCombo.selectedItem = "GAME" }
        SwingUtilities.invokeAndWait {}

        var count = 0
        SwingUtilities.invokeAndWait { count = panel.visibleEntryCount() }

        assertEquals(2, count)
    }

    // ── Max entries eviction ──────────────────────────────────────────────────

    @Test
    fun `entries beyond maxEntries are evicted`() {
        // Create panel with small maxEntries for testing
        lateinit var smallPanel: LogCatPanel
        SwingUtilities.invokeAndWait { smallPanel = LogCatPanel(maxEntries = 5) }

        // Add 8 entries
        repeat(8) { i ->
            val entry = makeEntry(LogLevel.GAME, "msg-$i")
            smallPanel.appendEntry(entry)
            SwingUtilities.invokeAndWait { /* drain EDT */ }
        }

        // Should only retain 5 entries
        SwingUtilities.invokeAndWait {}
        assertEquals(5, smallPanel.entries.size, "Should evict oldest entries beyond maxEntries")
        // Oldest entries (0, 1, 2) should be gone; newest (3-7) remain
        assertTrue(smallPanel.displayText().contains("msg-7"), "Newest entry should be visible")
        assertFalse(smallPanel.displayText().contains("msg-0"), "Oldest entry should be evicted")
    }
}
