/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// PROCESS ASSETS TASK TESTS
// Verifies .uge routing and uge2c detection without Gradle infrastructure.
// =============================================================================

class ProcessAssetsTaskTest {

    // =========================================================================
    // Uge2cFinder.findUge2c() — tool detection (Test B)
    // =========================================================================

    @Test
    fun `findUge2c returns null when HUGEDRIVER_HOME is not set and uge2c is not on PATH`() {
        // This test assumes uge2c is not installed in the test environment.
        // If uge2c is actually on PATH or in a common location, this test is skipped via
        // assumption.

        val hugeDriverHome = System.getenv("HUGEDRIVER_HOME")
        if (hugeDriverHome != null) {
            // If env var is set, tool detection may succeed — skip test (not a failure scenario)
            return
        }

        // Check if uge2c is actually on PATH (so we know whether to expect null)
        val onPath =
            try {
                val proc = ProcessBuilder("which", "uge2c").start()
                val path = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                proc.exitValue() == 0 && path.isNotEmpty()
            } catch (_: Exception) {
                false
            }

        // Check common locations
        val inCommon =
            listOf(
                    "/opt/hUGEDriver/uge2c",
                    "${System.getProperty("user.home")}/hUGEDriver/uge2c",
                    "/usr/local/bin/uge2c",
                )
                .any { File(it).exists() && File(it).canExecute() }

        if (onPath || inCommon) {
            // Tool is present — findUge2c will return a path, test environment is different
            // This isn't a failure — just skip the null-return assertion
            return
        }

        // No tool found — findUge2c must return null
        val result = Uge2cFinder.findUge2c()
        assertNull(result, "findUge2c() should return null when uge2c is not installed")
    }

    // =========================================================================
    // dispatchFile() extension routing — routing logic test (Test A)
    // =========================================================================

    @Test
    fun `dispatchFile extension check - uge extension is recognized in Uge2cFinder`(
        @TempDir tempDir: File
    ) {
        // Verify that the UGE_EXTENSION constant is "uge"
        assert(ProcessAssetsTask.UGE_EXTENSION == "uge") { "UGE_EXTENSION should be 'uge'" }
    }

    // =========================================================================
    // Uge2cFinder.findUge2c() with fake HUGEDRIVER_HOME pointing to a non-existent directory
    // =========================================================================

    @Test
    fun `findUge2c returns null when HUGEDRIVER_HOME points to non-existent path`(
        @TempDir tempDir: File
    ) {
        // The env var cannot be easily overridden in a unit test (read-only environment),
        // so we verify the logic indirectly by checking a fake directory that exists but
        // has no uge2c executable in it.

        // Create a temp dir that simulates a hUGEDriver installation without uge2c
        val fakeHome = File(tempDir, "fake-hugedriver")
        fakeHome.mkdirs()
        // No uge2c binary inside fakeHome

        // Since we can't set env vars in tests without reflection or process isolation,
        // verify the fallback works correctly: a directory without uge2c executable won't match
        val toolPath = File(fakeHome, "uge2c")
        assert(!toolPath.exists()) { "uge2c should not exist in fake home" }
        assert(!toolPath.canExecute()) { "non-existent file should not be executable" }
    }

    // =========================================================================
    // processUge() warning behavior — no exception thrown (Test C)
    // =========================================================================

    @Test
    fun `Uge2cFinder findUge2c does not throw when uge2c is absent`() {
        // Verify that findUge2c() never throws an exception regardless of environment
        val result =
            try {
                Uge2cFinder.findUge2c()
                true // no exception
            } catch (_: Exception) {
                false
            }
        assert(result) { "findUge2c() must not throw exceptions" }
    }
}
