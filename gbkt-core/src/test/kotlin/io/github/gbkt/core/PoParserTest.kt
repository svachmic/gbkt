/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [PoParser] — PO file parsing and auto-padding.
 *
 * Coverage:
 * - Basic parsing of msgctxt / msgid / msgstr
 * - Multi-line strings
 * - Header entry skipping (empty msgid)
 * - Auto-padding: shorter strings get padded to target width
 * - Auto-padding: strings exactly at target width are unchanged
 * - Auto-padding: strings longer than target width are NOT truncated but produce a warning
 * - Auto-padding: entries without a matching context are left unchanged
 * - BankAllocator: namespace-to-bank assignment
 */
class PoParserTest {

    // -------------------------------------------------------------------------
    // Basic parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse single entry with context`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Fireball     "

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("ability", entry.context)
        assertEquals("fireball", entry.msgid)
        assertEquals("Fireball     ", entry.msgstr)
    }

    @Test
    fun `parse multiple entries in different contexts`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Fireball"

            msgctxt "item"
            msgid "potion"
            msgstr "Potion"

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(2, entries.size)
        assertEquals("ability", entries[0].context)
        assertEquals("fireball", entries[0].msgid)
        assertEquals("item", entries[1].context)
        assertEquals("potion", entries[1].msgid)
    }

    @Test
    fun `parse skips PO header entry with empty msgid`() {
        val content =
            """
            msgid ""
            msgstr ""
            "Language: en\n"
            "Content-Type: text/plain; charset=UTF-8\n"

            msgctxt "ability"
            msgid "fireball"
            msgstr "Fireball"

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(1, entries.size)
        assertEquals("fireball", entries[0].msgid)
    }

    @Test
    fun `parse ignores comment lines`() {
        val content =
            """
            #. Extracted comment for translators
            # Translator comment
            msgctxt "battle"
            msgid "enemy_attacks"
            msgstr "Enemy attacks!"

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(1, entries.size)
        assertEquals("battle", entries[0].context)
        assertEquals("enemy_attacks", entries[0].msgid)
    }

    @Test
    fun `parse handles entry without msgctxt`() {
        val content =
            """
            msgid "some_key"
            msgstr "Some Value"

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(1, entries.size)
        assertEquals("", entries[0].context)
        assertEquals("some_key", entries[0].msgid)
        assertEquals("Some Value", entries[0].msgstr)
    }

    @Test
    fun `parse handles empty msgstr in template file`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr ""

            """
                .trimIndent()

        val entries = PoParser.parseContent(content).entries
        assertEquals(1, entries.size)
        assertEquals("", entries[0].msgstr)
    }

    // -------------------------------------------------------------------------
    // Auto-padding
    // -------------------------------------------------------------------------

    @Test
    fun `padding - shorter string gets padded to target width`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Fire"

            """
                .trimIndent()

        val padding = PaddingConfig(mapOf("ability" to 13))
        val result = PoParser.parseContent(content, padding)

        assertEquals(1, result.entries.size)
        val paddedStr = result.entries[0].msgstr
        assertEquals(13, paddedStr.length, "String should be padded to 13 chars")
        assertEquals("Fire         ", paddedStr)
    }

    @Test
    fun `padding - string exactly at width stays unchanged`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Melee Attack!"

            """
                .trimIndent()

        // "Melee Attack!" is exactly 13 chars
        val padding = PaddingConfig(mapOf("ability" to 13))
        val result = PoParser.parseContent(content, padding)

        assertEquals(1, result.entries.size)
        val str = result.entries[0].msgstr
        assertEquals(13, str.length, "String at exact width should be unchanged")
        assertEquals("Melee Attack!", str)
        assertTrue(result.warnings.isEmpty(), "No warnings expected for exact width")
    }

    @Test
    fun `padding - string longer than width is NOT truncated but produces warning`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Very Long Ability Name Here"

            """
                .trimIndent()

        val padding = PaddingConfig(mapOf("ability" to 13))
        val result = PoParser.parseContent(content, padding)

        assertEquals(1, result.entries.size)
        val str = result.entries[0].msgstr
        assertEquals("Very Long Ability Name Here", str, "Over-width string must NOT be truncated")
        assertFalse(result.warnings.isEmpty(), "A warning should be produced for over-width string")
        assertTrue(
            result.warnings[0].contains("ability.fireball"),
            "Warning should identify the entry",
        )
    }

    @Test
    fun `padding - entry without matching context is unmodified`() {
        val content =
            """
            msgctxt "battle"
            msgid "victory"
            msgstr "Victory!"

            """
                .trimIndent()

        // Only "ability" context is configured, "battle" has no padding rule
        val padding = PaddingConfig(mapOf("ability" to 13))
        val result = PoParser.parseContent(content, padding)

        assertEquals(1, result.entries.size)
        assertEquals("Victory!", result.entries[0].msgstr, "Unmatched context should be unchanged")
        assertTrue(result.warnings.isEmpty(), "No warnings for unmatched context")
    }

    @Test
    fun `padding - multiple contexts padded to different widths`() {
        val content =
            """
            msgctxt "ability"
            msgid "fire"
            msgstr "Fire"

            msgctxt "item"
            msgid "pot"
            msgstr "Pot"

            """
                .trimIndent()

        val padding = PaddingConfig(mapOf("ability" to 13, "item" to 6))
        val result = PoParser.parseContent(content, padding)

        assertEquals(2, result.entries.size)
        val abilityStr = result.entries[0].msgstr
        assertEquals(13, abilityStr.length, "Ability should be padded to 13 chars")

        val itemStr = result.entries[1].msgstr
        assertEquals(6, itemStr.length, "Item should be padded to 6 chars")
    }

    @Test
    fun `padding - empty msgstr is not padded`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr ""

            """
                .trimIndent()

        val padding = PaddingConfig(mapOf("ability" to 13))
        val result = PoParser.parseContent(content, padding)

        assertEquals(1, result.entries.size)
        assertEquals("", result.entries[0].msgstr, "Empty msgstr should not be padded")
    }

    @Test
    fun `padding - no padding config leaves all strings unchanged`() {
        val content =
            """
            msgctxt "ability"
            msgid "fireball"
            msgstr "Fire"

            """
                .trimIndent()

        val result = PoParser.parseContent(content, PaddingConfig())

        assertEquals(1, result.entries.size)
        assertEquals("Fire", result.entries[0].msgstr, "Without config, string should be unchanged")
    }

    // -------------------------------------------------------------------------
    // BankAllocator
    // -------------------------------------------------------------------------

    @Test
    fun `bank allocator assigns namespaces to banks`() {
        val entries =
            listOf(
                PoEntry("ability", "fireball", "Fireball     "),
                PoEntry("ability", "cure", "Cure Wounds  "),
                PoEntry("item", "potion", "Potion    "),
                PoEntry("battle", "victory", "You win!"),
            )

        val allocator = BankAllocator()
        val result = allocator.allocateForStrings(entries)

        assertTrue(result.namespaceToBank.isNotEmpty(), "Should assign banks to namespaces")
        assertTrue(result.namespaceToBank.containsKey("ability"))
        assertTrue(result.namespaceToBank.containsKey("item"))
        assertTrue(result.namespaceToBank.containsKey("battle"))
    }

    @Test
    fun `bank allocator all banks are within valid range`() {
        val entries = (1..50).map { i -> PoEntry("ns$i", "key$i", "Value $i") }

        val allocator = BankAllocator(maxBanks = 7)
        val result = allocator.allocateForStrings(entries)

        for ((namespace, bank) in result.namespaceToBank) {
            assertTrue(bank in 0..7, "Bank $bank for namespace '$namespace' should be in 0..7")
        }
    }

    @Test
    fun `bank allocator overflow throws hard error`() {
        // bankSizeBytes = 10, so each namespace with more than 10 bytes of string content
        // cannot fit in any bank. Two namespaces, each with content > 10 bytes.
        val entries =
            listOf(
                PoEntry("ns1", "key1", "12345678901"), // 11 chars + null = 12 bytes, exceeds 10-byte bank
                PoEntry("ns2", "key2", "ABCDEFGHIJK"), // 11 chars + null = 12 bytes, exceeds 10-byte bank
            )

        val allocator = BankAllocator(maxBanks = 1, bankSizeBytes = 10)

        val ex = assertFailsWith<IllegalStateException> { allocator.allocateForStrings(entries) }
        assertTrue(
            ex.message?.contains("Game content exceeds ROM capacity") == true,
            "Exception message should contain 'Game content exceeds ROM capacity', got: ${ex.message}",
        )
    }
}
