/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [InputScript] DSL.
 *
 * Verifies that the builder constructs the correct [InputStep] list without requiring any emulator
 * instance.
 */
class InputScriptTest {

    @Test
    fun `press with default frames produces Press step with 1 frame`() {
        val script = inputScript { press(Button.A) }
        assertEquals(listOf(InputStep.Press(Button.A, 1)), script.steps)
    }

    @Test
    fun `press with custom frames produces Press step with given frames`() {
        val script = inputScript { press(Button.RIGHT, frames = 30) }
        assertEquals(listOf(InputStep.Press(Button.RIGHT, 30)), script.steps)
    }

    @Test
    fun `hold produces Hold step`() {
        val script = inputScript { hold(Button.LEFT) }
        assertEquals(listOf(InputStep.Hold(Button.LEFT)), script.steps)
    }

    @Test
    fun `release produces Release step`() {
        val script = inputScript { release(Button.LEFT) }
        assertEquals(listOf(InputStep.Release(Button.LEFT)), script.steps)
    }

    @Test
    fun `wait produces Wait step with given frames`() {
        val script = inputScript { wait(60) }
        assertEquals(listOf(InputStep.Wait(60)), script.steps)
    }

    @Test
    fun `complex script builds steps in order`() {
        val script = inputScript {
            press(Button.RIGHT, frames = 10)
            wait(5)
            press(Button.A)
            hold(Button.B)
            wait(20)
            release(Button.B)
        }
        assertEquals(
            listOf(
                InputStep.Press(Button.RIGHT, 10),
                InputStep.Wait(5),
                InputStep.Press(Button.A, 1),
                InputStep.Hold(Button.B),
                InputStep.Wait(20),
                InputStep.Release(Button.B),
            ),
            script.steps,
        )
    }

    @Test
    fun `empty script has no steps`() {
        val script = inputScript {}
        assertEquals(emptyList<InputStep>(), script.steps)
    }

    @Test
    fun `all button values are accessible`() {
        val script = inputScript {
            press(Button.UP)
            press(Button.DOWN)
            press(Button.LEFT)
            press(Button.RIGHT)
            press(Button.A)
            press(Button.B)
            press(Button.START)
            press(Button.SELECT)
        }
        assertEquals(8, script.steps.size)
    }
}
