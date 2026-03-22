/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.ui

import io.github.gbkt.emulator.MemoryAccess
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for [MemoryInspectorPanel], [NamedVariablesTab], and [HexViewTab].
 *
 * Tests run headless (no display needed) since Swing components are instantiated but not shown. All
 * GUI interaction is tested via the component's data model.
 *
 * Note: JFrame/JTabbedPane can be instantiated in tests on some environments only when the JVM has
 * access to a display (or AWT is set to headless mode). These tests avoid showing any window and
 * only inspect the data-model layer.
 */
class MemoryInspectorPanelTest {

    /** A minimal [MemoryAccess] backed by an in-memory map. Returns 0 for unmapped addresses. */
    private class MockMemoryAccess(private val data: Map<Int, Int> = emptyMap()) : MemoryAccess {
        override fun readByte(address: Int): Int = data[address] ?: 0

        override fun writeByte(address: Int, value: Int) {
            /* no-op in tests */
        }
    }

    @BeforeEach
    fun setup() {
        System.setProperty("java.awt.headless", "true")
    }

    // ── MemoryInspectorPanel tab structure ────────────────────────────────────

    @Test
    fun `panel has exactly two tabs`() {
        val panel = MemoryInspectorPanel(memoryProvider = { MockMemoryAccess() })
        assertEquals(2, panel.tabCount)
    }

    @Test
    fun `first tab title is Variables`() {
        val panel = MemoryInspectorPanel(memoryProvider = { MockMemoryAccess() })
        assertEquals("Variables", panel.getTitleAt(0))
    }

    @Test
    fun `second tab title is Hex View`() {
        val panel = MemoryInspectorPanel(memoryProvider = { MockMemoryAccess() })
        assertEquals("Hex View", panel.getTitleAt(1))
    }

    @Test
    fun `first tab component is NamedVariablesTab`() {
        val panel = MemoryInspectorPanel(memoryProvider = { MockMemoryAccess() })
        assertTrue(panel.getComponentAt(0) is NamedVariablesTab)
    }

    @Test
    fun `second tab component is HexViewTab`() {
        val panel = MemoryInspectorPanel(memoryProvider = { MockMemoryAccess() })
        assertTrue(panel.getComponentAt(1) is HexViewTab)
    }

    // ── HexViewTab formatting ─────────────────────────────────────────────────

    @Test
    fun `hex dump shows correct bytes at base address`() {
        val memory = MockMemoryAccess(mapOf(0xC000 to 0xAB, 0xC001 to 0xCD, 0xC002 to 0xEF))
        val tab = HexViewTab(memoryProvider = { memory })
        val dump = tab.buildDump(memory, 0xC000)

        // Row starts with address
        assertTrue(dump.contains("C000:"), "Dump should contain row address 'C000:'")
        // First three bytes should be AB CD EF
        assertTrue(dump.contains("AB"), "Dump should contain byte AB")
        assertTrue(dump.contains("CD"), "Dump should contain byte CD")
        assertTrue(dump.contains("EF"), "Dump should contain byte EF")
    }

    @Test
    fun `hex dump header line present`() {
        val memory = MockMemoryAccess()
        val tab = HexViewTab(memoryProvider = { memory })
        val dump = tab.buildDump(memory, 0xC000)

        assertTrue(dump.contains("Address"), "Dump should contain 'Address' header")
        assertTrue(dump.contains("ASCII"), "Dump should contain 'ASCII' header")
    }

    @Test
    fun `hex dump shows 16 data rows`() {
        val memory = MockMemoryAccess()
        val tab = HexViewTab(memoryProvider = { memory })
        val dump = tab.buildDump(memory, 0xC000)

        // Count lines that start with a 4-hex-digit address (data rows)
        val dataRows = dump.lines().count { line -> line.matches(Regex("^[0-9A-Fa-f]{4}:.*")) }
        assertEquals(16, dataRows, "Should show exactly 16 data rows")
    }

    @Test
    fun `hex dump shows printable ASCII chars`() {
        // 0x41 = 'A', 0x42 = 'B'
        val memory = MockMemoryAccess(mapOf(0xC000 to 0x41, 0xC001 to 0x42))
        val tab = HexViewTab(memoryProvider = { memory })
        val dump = tab.buildDump(memory, 0xC000)

        // The ASCII column should contain 'AB' (the printable chars)
        // Check the first data line has AB in the ASCII section
        val firstDataLine = dump.lines().first { it.startsWith("C000:") }
        assertTrue(
            firstDataLine.contains("AB"),
            "ASCII section should show 'AB' for bytes 0x41, 0x42",
        )
    }

    @Test
    fun `hex dump replaces non-printable with dot`() {
        // 0x00 is non-printable, should show as '.'
        val memory = MockMemoryAccess(mapOf(0xC000 to 0x00))
        val tab = HexViewTab(memoryProvider = { memory })
        val dump = tab.buildDump(memory, 0xC000)

        val firstDataLine = dump.lines().first { it.startsWith("C000:") }
        // The ASCII section should have dots for zero bytes
        val asciiPart = firstDataLine.substringAfterLast("  ")
        assertTrue(
            asciiPart.contains('.'),
            "ASCII section should contain '.' for non-printable bytes",
        )
    }

    @Test
    fun `hex view default base address is WRAM`() {
        val tab = HexViewTab(memoryProvider = { MockMemoryAccess() })
        assertEquals(0xC000, tab.baseAddress)
    }

    // ── NamedVariablesTab ────────────────────────────────────────────────────

    @Test
    fun `variables tab initially has empty table`() {
        val tab = NamedVariablesTab(memoryProvider = { MockMemoryAccess() })
        assertEquals(0, tab.variableCount())
    }

    @Test
    fun `refresh reads UINT8 value from memory`() {
        val memory = MockMemoryAccess(mapOf(0xC100 to 42))
        val tab = NamedVariablesTab(memoryProvider = { memory })

        // Simulate loading a variable at 0xC100
        tab.loadSymbols(createSymFile("DEF _score 00:C100"))

        tab.refresh()

        val vars = tab.getVariables()
        assertEquals(1, vars.size)
        assertEquals("score", vars[0].name)
        assertEquals("42", vars[0].value)
    }

    @Test
    fun `refresh reads INT8 signed value from memory`() {
        // 0xFF = 255 unsigned = -1 signed INT8
        val memory = MockMemoryAccess(mapOf(0xC100 to 0xFF))
        val tab = NamedVariablesTab(memoryProvider = { memory })
        tab.loadSymbols(createSymFile("DEF _dx 00:C100"))
        tab.setVariableType("dx", "INT8")
        tab.refresh()
        val vars = tab.getVariables()
        assertEquals("INT8", vars[0].type)
        assertEquals("-1", vars[0].value) // 0xFF as INT8 = -1
    }

    @Test
    fun `refresh reads UINT16 little-endian value from memory`() {
        // 0x34 at C100 (lo), 0x12 at C101 (hi) → 0x1234 = 4660
        val memory = MockMemoryAccess(mapOf(0xC100 to 0x34, 0xC101 to 0x12))
        val tab = NamedVariablesTab(memoryProvider = { memory })
        tab.loadSymbols(createSymFile("DEF _score16 00:C100"))
        tab.setVariableType("score16", "UINT16")
        tab.refresh()
        val vars = tab.getVariables()
        assertEquals("UINT16", vars[0].type)
        assertEquals("4660", vars[0].value) // 0x1234 = 4660
    }

    @Test
    fun `refresh reads INT16 signed little-endian value from memory`() {
        // 0xFF at C100 (lo), 0xFF at C101 (hi) → 0xFFFF as INT16 = -1
        val memory = MockMemoryAccess(mapOf(0xC100 to 0xFF, 0xC101 to 0xFF))
        val tab = NamedVariablesTab(memoryProvider = { memory })
        tab.loadSymbols(createSymFile("DEF _offset 00:C100"))
        tab.setVariableType("offset", "INT16")
        tab.refresh()
        val vars = tab.getVariables()
        assertEquals("INT16", vars[0].type)
        assertEquals("-1", vars[0].value) // 0xFFFF as INT16 = -1
    }

    @Test
    fun `sym file loading strips underscore prefix from variable names`() {
        val memory = MockMemoryAccess()
        val tab = NamedVariablesTab(memoryProvider = { memory })
        tab.loadSymbols(createSymFile("DEF _lives 00:C101"))

        val vars = tab.getVariables()
        assertEquals("lives", vars[0].name, "Leading underscore should be stripped")
    }

    @Test
    fun `sym file loading parses address correctly`() {
        val memory = MockMemoryAccess()
        val tab = NamedVariablesTab(memoryProvider = { memory })
        tab.loadSymbols(createSymFile("DEF _score 00:C200"))

        val vars = tab.getVariables()
        assertEquals(0xC200, vars[0].address)
    }

    @Test
    fun `sym file skips lines without DEF prefix`() {
        val memory = MockMemoryAccess()
        val tab = NamedVariablesTab(memoryProvider = { memory })
        val symContent =
            """
            ; SDCC symbol file
            MODULE main
            DEF _score 00:C100
            DEF _lives 00:C101
            """
                .trimIndent()
        tab.loadSymbols(createSymFile(symContent))

        // Only 2 entries should be loaded (the DEF lines)
        assertEquals(2, tab.variableCount())
    }

    @Test
    fun `sym file skips symbols without underscore prefix`() {
        val memory = MockMemoryAccess()
        val tab = NamedVariablesTab(memoryProvider = { memory })
        // "noUnderscore" should be skipped (no leading _)
        val symContent = "DEF noUnderscore 00:C100\nDEF _valid 00:C101"
        tab.loadSymbols(createSymFile(symContent))
        assertEquals(1, tab.variableCount())
    }

    @Test
    fun `refresh does not crash when provider returns null`() {
        val tab = NamedVariablesTab(memoryProvider = { null })
        // Should silently no-op without exception
        tab.refresh()
    }

    @Test
    fun `hex view refresh does not crash when provider returns null`() {
        val tab = HexViewTab(memoryProvider = { null })
        // Should silently no-op without exception
        tab.refresh()
    }

    @Test
    fun `panel refresh delegates to both tabs`() {
        var refreshCount = 0
        val memory = MockMemoryAccess(mapOf(0xC100 to 7))

        val panel = MemoryInspectorPanel(memoryProvider = { memory })
        // Add a variable so named tab actually does work
        panel.namedVariablesTab.loadSymbols(createSymFile("DEF _score 00:C100"))

        panel.refresh()

        // After refresh, the variable should show the value from memory
        val vars = panel.namedVariablesTab.getVariables()
        assertEquals("7", vars[0].value)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @TempDir lateinit var tempDir: File

    private fun createSymFile(content: String): File {
        val file = File(tempDir, "test.sym")
        file.writeText(content)
        return file
    }
}
