/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import eu.rekawek.coffeegb.core.events.EventBus
import eu.rekawek.coffeegb.core.joypad.ButtonPressEvent
import eu.rekawek.coffeegb.core.joypad.ButtonReleaseEvent
import io.github.gbkt.emulator.GbEmulator

/**
 * Executes an [InputScript] against a running [GbEmulator] by dispatching button press/release
 * events into the Coffee-GB [EventBus] and advancing emulation frame-by-frame.
 *
 * The emulator must be paused before calling [play]. This allows deterministic control — the caller
 * pauses, injects input, and resumes.
 *
 * @param emulator The emulator instance to control.
 * @param eventBus The Coffee-GB event bus to dispatch joypad events onto.
 */
class InputScriptPlayer(
    private val emulator: GbEmulator,
    private val eventBus: EventBus,
) {

    /**
     * Executes all steps in the given [InputScript].
     *
     * @param script The input sequence to execute.
     * @throws IllegalStateException if the emulator is not paused.
     */
    fun play(script: InputScript) {
        check(emulator.isPaused()) {
            "Emulator must be paused before playing an InputScript. Call emulator.pause() first."
        }
        for (step in script.steps) {
            executeStep(step)
        }
    }

    private fun executeStep(step: InputStep) {
        when (step) {
            is InputStep.Press -> {
                val coffeeButton = step.button.toCoffeeGb()
                eventBus.post(ButtonPressEvent(coffeeButton))
                repeat(step.frames) { emulator.stepFrame() }
                eventBus.post(ButtonReleaseEvent(coffeeButton))
            }
            is InputStep.Hold -> {
                val coffeeButton = step.button.toCoffeeGb()
                eventBus.post(ButtonPressEvent(coffeeButton))
            }
            is InputStep.Release -> {
                val coffeeButton = step.button.toCoffeeGb()
                eventBus.post(ButtonReleaseEvent(coffeeButton))
            }
            is InputStep.Wait -> {
                repeat(step.frames) { emulator.stepFrame() }
            }
        }
    }

    private fun Button.toCoffeeGb(): eu.rekawek.coffeegb.core.joypad.Button =
        when (this) {
            Button.UP -> eu.rekawek.coffeegb.core.joypad.Button.UP
            Button.DOWN -> eu.rekawek.coffeegb.core.joypad.Button.DOWN
            Button.LEFT -> eu.rekawek.coffeegb.core.joypad.Button.LEFT
            Button.RIGHT -> eu.rekawek.coffeegb.core.joypad.Button.RIGHT
            Button.A -> eu.rekawek.coffeegb.core.joypad.Button.A
            Button.B -> eu.rekawek.coffeegb.core.joypad.Button.B
            Button.START -> eu.rekawek.coffeegb.core.joypad.Button.START
            Button.SELECT -> eu.rekawek.coffeegb.core.joypad.Button.SELECT
        }
}
