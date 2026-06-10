/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.agent.Observation

/**
 * Fluent assertion DSL for Game Boy game state observations.
 *
 * Assertions throw [AssertionError] with descriptive failure messages that include the frame number
 * and current scene — making test failures easy to diagnose without needing the source line.
 *
 * These functions are available both as top-level functions (for direct use in tests) and as
 * extension functions on [GbktTestExtension] (for use inside `@RegisterExtension` test classes).
 *
 * Example:
 * ```kotlin
 * val obs = game.stepN(120)
 * assertScene(obs, "title")
 * assertTextOnScreen(obs, "PRESS START")
 * ```
 */

/**
 * Asserts that the current scene in [obs] matches [expected].
 *
 * @param obs The [Observation] to check.
 * @param expected The expected scene name.
 * @param message Optional message prefix prepended to the failure message.
 * @throws AssertionError if the scene does not match.
 */
fun assertScene(obs: Observation, expected: String, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    if (obs.scene != expected) {
        throw AssertionError(
            "${prefix}Frame ${obs.frame}: expected scene '$expected' but was '${obs.scene}'"
        )
    }
}

/**
 * Asserts that the named variable in [obs] has the [expected] value.
 *
 * @param obs The [Observation] to check.
 * @param name Variable name as declared in the DSL (e.g., `"score"`).
 * @param expected Expected integer value.
 * @param message Optional message prefix.
 * @throws AssertionError if the variable is absent or does not match.
 */
fun assertVariable(obs: Observation, name: String, expected: Int, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    val actual =
        obs.variables[name]
            ?: throw AssertionError(
                "${prefix}Frame ${obs.frame}: variable '$name' not found in observation (scene='${obs.scene}'). " +
                    "Available: ${obs.variables.keys.sorted()}"
            )
    if (actual != expected) {
        throw AssertionError(
            "${prefix}Frame ${obs.frame}: variable '$name' expected $expected but was $actual (scene='${obs.scene}')"
        )
    }
}

/**
 * Asserts that an actor with [actorName] is present in [obs].
 *
 * @param obs The [Observation] to check.
 * @param actorName Actor name as declared in the DSL (e.g., `"ball"`).
 * @param message Optional message prefix.
 * @throws AssertionError if no actor with the given name is present.
 */
fun assertActorVisible(obs: Observation, actorName: String, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    if (obs.actors.none { it.name == actorName }) {
        throw AssertionError(
            "${prefix}Frame ${obs.frame}: actor '$actorName' not found in observation (scene='${obs.scene}'). " +
                "Present actors: ${obs.actors.map { it.name }}"
        )
    }
}

/**
 * Asserts that [text] appears somewhere on screen in either the background or window tilemap.
 *
 * @param obs The [Observation] to check.
 * @param text Substring to search for on screen.
 * @param message Optional message prefix.
 * @throws AssertionError if the text is not found on either tilemap layer.
 */
fun assertTextOnScreen(obs: Observation, text: String, message: String? = null) {
    val prefix = if (message != null) "$message: " else ""
    val found = obs.bgText.any { text in it } || obs.winText.any { text in it }
    if (!found) {
        throw AssertionError(
            "${prefix}Frame ${obs.frame}: text '$text' not found on screen (scene='${obs.scene}'). " +
                "BG: ${obs.bgText.filter { it.isNotBlank() }}, " +
                "WIN: ${obs.winText.filter { it.isNotBlank() }}"
        )
    }
}

// ── GbktTestExtension delegation extensions ───────────────────────────────────
// These allow calling assertions as extension functions on GbktTestExtension,
// forwarding to the top-level implementations above.

/** @see assertScene */
fun GbktTestExtension.assertScene(obs: Observation, expected: String, message: String? = null) =
    io.github.gbkt.test.assertScene(obs, expected, message)

/** @see assertVariable */
fun GbktTestExtension.assertVariable(
    obs: Observation,
    name: String,
    expected: Int,
    message: String? = null,
) = io.github.gbkt.test.assertVariable(obs, name, expected, message)

/** @see assertActorVisible */
fun GbktTestExtension.assertActorVisible(
    obs: Observation,
    actorName: String,
    message: String? = null,
) = io.github.gbkt.test.assertActorVisible(obs, actorName, message)

/** @see assertTextOnScreen */
fun GbktTestExtension.assertTextOnScreen(obs: Observation, text: String, message: String? = null) =
    io.github.gbkt.test.assertTextOnScreen(obs, text, message)
