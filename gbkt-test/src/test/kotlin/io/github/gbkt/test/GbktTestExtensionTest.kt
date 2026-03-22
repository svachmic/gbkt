/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.agent.ActorState
import io.github.gbkt.emulator.agent.Observation
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [GbktTestExtension] assertion methods and [GbktGameAssertions].
 *
 * These tests verify assertion helper behavior against synthetic [Observation] instances —
 * no actual emulator or ROM is required.
 */
class GbktTestExtensionTest {

    private fun makeObservation(
        frame: Int = 1,
        scene: String? = null,
        variables: Map<String, Int> = emptyMap(),
        actors: List<ActorState> = emptyList(),
        bgText: List<String> = emptyList(),
        winText: List<String> = emptyList(),
    ): Observation = Observation(
        frame = frame,
        scene = scene,
        variables = variables,
        actors = actors,
        bgText = bgText,
        winText = winText,
        sprites = emptyList(),
        newLogEntries = emptyList(),
    )

    // ── assertScene ──────────────────────────────────────────────────────────

    @Test
    fun `assertScene passes when observation scene matches expected`() {
        val obs = makeObservation(frame = 10, scene = "gameplay")
        // Should not throw
        assertScene(obs, "gameplay")
    }

    @Test
    fun `assertScene throws AssertionError when scene does not match`() {
        val obs = makeObservation(frame = 10, scene = "title")
        assertFailsWith<AssertionError> {
            assertScene(obs, "gameplay")
        }
    }

    @Test
    fun `assertScene throws AssertionError when scene is null`() {
        val obs = makeObservation(frame = 5, scene = null)
        assertFailsWith<AssertionError> {
            assertScene(obs, "title")
        }
    }

    // ── assertVariable ───────────────────────────────────────────────────────

    @Test
    fun `assertVariable passes when variable matches expected value`() {
        val obs = makeObservation(frame = 1, variables = mapOf("score" to 42))
        assertVariable(obs, "score", 42)
    }

    @Test
    fun `assertVariable throws AssertionError when variable value does not match`() {
        val obs = makeObservation(frame = 1, variables = mapOf("score" to 10))
        assertFailsWith<AssertionError> {
            assertVariable(obs, "score", 42)
        }
    }

    @Test
    fun `assertVariable throws AssertionError when variable is absent`() {
        val obs = makeObservation(frame = 1, variables = emptyMap())
        assertFailsWith<AssertionError> {
            assertVariable(obs, "score", 0)
        }
    }

    // ── assertActorVisible ───────────────────────────────────────────────────

    @Test
    fun `assertActorVisible passes when actor exists in observation`() {
        val actors = listOf(ActorState(name = "ball", x = 80, y = 72, sprites = emptyList()))
        val obs = makeObservation(frame = 1, actors = actors)
        assertActorVisible(obs, "ball")
    }

    @Test
    fun `assertActorVisible throws AssertionError when actor is absent`() {
        val obs = makeObservation(frame = 1, actors = emptyList())
        assertFailsWith<AssertionError> {
            assertActorVisible(obs, "ball")
        }
    }

    // ── assertTextOnScreen ───────────────────────────────────────────────────

    @Test
    fun `assertTextOnScreen passes when text found in bgText`() {
        val obs = makeObservation(frame = 1, bgText = listOf("PONG", "PRESS START"))
        assertTextOnScreen(obs, "PONG")
    }

    @Test
    fun `assertTextOnScreen passes when text found in winText`() {
        val obs = makeObservation(frame = 1, winText = listOf("SCORE: 5"))
        assertTextOnScreen(obs, "SCORE")
    }

    @Test
    fun `assertTextOnScreen passes when text found in winText but not bgText`() {
        val obs = makeObservation(
            frame = 1,
            bgText = listOf("background content"),
            winText = listOf("WIN TEXT SCORE: 100"),
        )
        assertTextOnScreen(obs, "WIN TEXT")
    }

    @Test
    fun `assertTextOnScreen throws AssertionError when text not on screen`() {
        val obs = makeObservation(
            frame = 1,
            bgText = listOf("hello"),
            winText = listOf("world"),
        )
        assertFailsWith<AssertionError> {
            assertTextOnScreen(obs, "MISSING")
        }
    }

    @Test
    fun `assertTextOnScreen throws AssertionError when both layers are empty`() {
        val obs = makeObservation(frame = 1)
        assertFailsWith<AssertionError> {
            assertTextOnScreen(obs, "anything")
        }
    }
}
