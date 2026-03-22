/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

/**
 * Per-game test summary reporter that aggregates test results and prints a human-readable summary.
 *
 * Typically instantiated alongside a [GbktTestExtension] in a test class and populated via
 * recording calls from test methods. Call [printSummary] in an `@AfterAll` method to emit the
 * summary after all tests in the class complete.
 *
 * Usage:
 * ```kotlin
 * class PongTest {
 *     companion object {
 *         @JvmField val reporter = GbktGameReporter("pong")
 *
 *         @AfterAll @JvmStatic fun teardown() { reporter.printSummary() }
 *     }
 *
 *     @JvmField @RegisterExtension val game = GbktTestExtension("pong")
 *
 *     @Test fun `title screen boots correctly`() {
 *         val obs = game.verifyTitleScreen(listOf("PONG"))
 *         reporter.recordScene("title")
 *         reporter.recordPass()
 *     }
 * }
 * ```
 *
 * @param gameName Display name for the game, used in the printed summary header.
 */
class GbktGameReporter(private val gameName: String) {

    private val scenesVerified = mutableSetOf<String>()
    private var assertionsPassed = 0
    private var assertionsFailed = 0
    private var screenshotsCaptured = 0

    /** Records that [scene] was visited and verified during the test run. */
    fun recordScene(scene: String) {
        scenesVerified.add(scene)
    }

    /** Records that an assertion passed. */
    fun recordPass() {
        assertionsPassed++
    }

    /** Records that an assertion failed. */
    fun recordFail() {
        assertionsFailed++
    }

    /** Records that a screenshot was captured. */
    fun recordScreenshot() {
        screenshotsCaptured++
    }

    /**
     * Prints a formatted per-game test summary to standard output.
     *
     * Example output:
     * ```
     * === pong Test Summary ===
     *   Scenes verified: title, game
     *   Assertions: 12 passed, 0 failed
     *   Screenshots: 3 captured
     * ==============================
     * ```
     */
    fun printSummary() {
        println("=== $gameName Test Summary ===")
        println("  Scenes verified: ${scenesVerified.sorted().joinToString(", ").ifEmpty { "none" }}")
        println("  Assertions: $assertionsPassed passed, $assertionsFailed failed")
        println("  Screenshots: $screenshotsCaptured captured")
        println("==============================")
    }
}
