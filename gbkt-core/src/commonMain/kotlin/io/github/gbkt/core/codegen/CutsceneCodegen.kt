/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.CutsceneDefinition
import io.github.gbkt.core.ir.TimelineStep

/** Code generation for cutscene/timeline system. */
internal fun CodeGenerator.generateCutsceneFunctions() {
    if (game.cutscenes.isEmpty()) return

    line("// =============================================================================")
    line("// CUTSCENE FUNCTIONS")
    line("// =============================================================================")
    line()

    for (cutscene in game.cutscenes) {
        generateCutsceneStart(cutscene)
        generateCutsceneUpdate(cutscene)
        generateCutsceneSkip(cutscene)
    }
}

private fun CodeGenerator.generateCutsceneStart(cutscene: CutsceneDefinition) {
    line("void _${cutscene.name}_start(void) {")
    indent++
    line("_${cutscene.name}_playing = 1;")
    line("_${cutscene.name}_complete = 0;")
    line("_${cutscene.name}_step = 0;")
    line("_${cutscene.name}_timer = 0;")
    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateCutsceneUpdate(cutscene: CutsceneDefinition) {
    line("void _${cutscene.name}_update(void) {")
    indent++
    line("if (!_${cutscene.name}_playing) return;")
    line()

    if (cutscene.steps.isEmpty()) {
        line("// No steps - mark complete immediately")
        line("_${cutscene.name}_playing = 0;")
        line("_${cutscene.name}_complete = 1;")
    } else {
        line("switch (_${cutscene.name}_step) {")
        indent++

        var stepIndex = 0
        for (step in cutscene.steps) {
            line("case $stepIndex:")
            indent++
            generateTimelineStep(cutscene, step, stepIndex)
            stepIndex++
            indent--
        }

        // Final step to mark complete
        line("case $stepIndex:")
        indent++
        line("_${cutscene.name}_playing = 0;")
        line("_${cutscene.name}_complete = 1;")
        line("break;")
        indent--

        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateTimelineStep(
    cutscene: CutsceneDefinition,
    step: TimelineStep,
    stepIndex: Int,
) {
    when (step) {
        is TimelineStep.Wait -> {
            line("_${cutscene.name}_timer++;")
            line("if (_${cutscene.name}_timer >= ${step.frames}) {")
            indent++
            line("_${cutscene.name}_timer = 0;")
            line("_${cutscene.name}_step++;")
            indent--
            line("}")
            line("break;")
        }
        is TimelineStep.Action -> {
            for (stmt in step.statements) {
                generateStatement(stmt)
            }
            line("_${cutscene.name}_step++;")
            line("break;")
        }
        is TimelineStep.Parallel -> {
            // For simplicity, execute all actions in sequence
            // A more sophisticated implementation would track multiple timers
            for (subStep in step.steps) {
                if (subStep is TimelineStep.Action) {
                    for (stmt in subStep.statements) {
                        generateStatement(stmt)
                    }
                }
            }
            line("_${cutscene.name}_step++;")
            line("break;")
        }
        is TimelineStep.TransitionEffect -> {
            // Generate transition - simplified version
            line("// Transition effect")
            line("_${cutscene.name}_timer++;")
            line("if (_${cutscene.name}_timer >= ${step.transition.estimatedFrames}) {")
            indent++
            line("_${cutscene.name}_timer = 0;")
            line("_${cutscene.name}_step++;")
            indent--
            line("}")
            line("break;")
        }
    }
}

private fun CodeGenerator.generateCutsceneSkip(cutscene: CutsceneDefinition) {
    if (!cutscene.skippable) return

    line("void _${cutscene.name}_skip(void) {")
    indent++
    line("_${cutscene.name}_playing = 0;")
    line("_${cutscene.name}_complete = 1;")
    line("_${cutscene.name}_step = ${cutscene.steps.size};")
    indent--
    line("}")
    line()
}
