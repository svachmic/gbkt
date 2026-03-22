/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CommandsTest {

    @Test
    fun `handleNew with no args prints usage`() {
        val output = captureStdout { handleNew(emptyList()) }
        assertTrue(output.contains("Usage: gbkt new"), "Should print usage when no args provided")
        assertTrue(output.contains("Available templates:"), "Should list available templates")
    }

    @Test
    fun `handleNew with only template arg prints usage`() {
        val output = captureStdout { handleNew(listOf("minimal")) }
        assertTrue(output.contains("Usage: gbkt new"), "Should print usage with only one arg")
    }

    @Test
    fun `handleNew with unknown template prints error`() {
        val output = captureStdout { handleNew(listOf("nonexistent", "my-game")) }
        assertTrue(
            output.contains("Unknown template: nonexistent"),
            "Should report unknown template",
        )
        assertTrue(output.contains("Available templates:"), "Should list available templates")
    }

    @Test
    fun `handleNew usage lists all four templates`() {
        val output = captureStdout { handleNew(emptyList()) }
        assertTrue(output.contains("minimal"), "Should list minimal template")
        assertTrue(output.contains("platformer"), "Should list platformer template")
        assertTrue(output.contains("rpg"), "Should list rpg template")
        assertTrue(output.contains("puzzle"), "Should list puzzle template")
    }

    private fun captureStdout(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(old)
        }
        return baos.toString()
    }
}
