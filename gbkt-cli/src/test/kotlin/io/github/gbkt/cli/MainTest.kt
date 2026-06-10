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

class MainTest {

    @Test
    fun `no args prints help`() {
        val output = captureStdout { main(emptyArray()) }
        assertTrue(output.contains("gbkt - Game Boy Kotlin CLI"), "Should print help header")
        assertTrue(output.contains("COMMANDS:"), "Should list commands")
    }

    @Test
    fun `help command prints help`() {
        val output = captureStdout { main(arrayOf("help")) }
        assertTrue(output.contains("gbkt - Game Boy Kotlin CLI"), "Should print help")
    }

    @Test
    fun `--help flag prints help`() {
        val output = captureStdout { main(arrayOf("--help")) }
        assertTrue(output.contains("gbkt - Game Boy Kotlin CLI"), "Should print help")
    }

    @Test
    fun `-h flag prints help`() {
        val output = captureStdout { main(arrayOf("-h")) }
        assertTrue(output.contains("gbkt - Game Boy Kotlin CLI"), "Should print help")
    }

    @Test
    fun `version command prints version`() {
        val output = captureStdout { main(arrayOf("version")) }
        assertTrue(output.contains("gbkt version"), "Should print version")
    }

    @Test
    fun `--version flag prints version`() {
        val output = captureStdout { main(arrayOf("--version")) }
        assertTrue(output.contains("gbkt version"), "Should print version")
    }

    @Test
    fun `-v flag prints version`() {
        val output = captureStdout { main(arrayOf("-v")) }
        assertTrue(output.contains("gbkt version"), "Should print version")
    }

    @Test
    fun `unknown command prints error and help`() {
        val output = captureStdout { main(arrayOf("foobar")) }
        assertTrue(output.contains("Unknown command: foobar"), "Should report unknown command")
        assertTrue(output.contains("COMMANDS:"), "Should print help after error")
    }

    @Test
    fun `new command without args prints usage`() {
        val output = captureStdout { main(arrayOf("new")) }
        assertTrue(output.contains("Usage: gbkt new"), "Should print new command usage")
    }

    @Test
    fun `list-targets command runs without error`() {
        val output = captureStdout { main(arrayOf("list-targets")) }
        assertTrue(
            output.contains("Available target platforms:"),
            "Should print target platforms header",
        )
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
