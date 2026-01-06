/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRCutsceneIsComplete
import io.github.gbkt.core.ir.IRCutsceneIsPlaying
import io.github.gbkt.core.ir.IRCutsceneSkip
import io.github.gbkt.core.ir.IRCutsceneStart
import io.github.gbkt.core.ir.IRCutsceneUpdate
import io.github.gbkt.core.ir.TimelineStep
import io.github.gbkt.core.ir.Transition

/** Definition of a cutscene with timeline-based sequencing. */
data class CutsceneDefinition(
    val name: String,
    val steps: List<TimelineStep>,
    val skippable: Boolean = true,
)

/** Handle to a configured cutscene for use in scenes. */
class CutsceneHandle internal constructor(private val definition: CutsceneDefinition) {
    val name: String
        get() = definition.name

    /** Start the cutscene */
    fun start() {
        RecordingContext.require().emit(IRCutsceneStart(definition.name))
    }

    /** Update cutscene (call in every.frame) */
    fun update() {
        RecordingContext.require().emit(IRCutsceneUpdate(definition.name))
    }

    /** Skip/cancel the cutscene */
    fun skip() {
        RecordingContext.require().emit(IRCutsceneSkip(definition.name))
    }

    /** Check if cutscene is currently playing */
    val isPlaying: Condition
        get() = Condition(IRCutsceneIsPlaying(definition.name))

    /** Check if cutscene has completed */
    val isComplete: Condition
        get() = Condition(IRCutsceneIsComplete(definition.name))
}

/** Builder for configuring cutscenes with timeline-based sequencing. */
@GbktDsl
class CutsceneBuilder(private val name: String) {
    private val steps = mutableListOf<TimelineStep>()
    var skippable: Boolean = true

    /** Wait for specified number of frames */
    fun wait(frames: Int) {
        steps.add(TimelineStep.Wait(frames))
    }

    /** Wait using FrameTiming */
    fun wait(timing: FrameTiming) {
        steps.add(TimelineStep.Wait(timing.count))
    }

    /** Execute actions immediately */
    fun action(block: FrameScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope("cutscene_action").block() }
        steps.add(TimelineStep.Action(recorder.statements))
    }

    /** Execute multiple steps in parallel */
    fun parallel(init: ParallelBuilder.() -> Unit) {
        val builder = ParallelBuilder()
        builder.init()
        steps.add(TimelineStep.Parallel(builder.steps))
    }

    /** Execute a transition effect */
    fun transition(t: Transition) {
        steps.add(TimelineStep.TransitionEffect(t))
    }

    /** Final action when cutscene ends (alias for action) */
    fun then(block: FrameScope.() -> Unit) {
        action(block)
    }

    internal fun build() = CutsceneDefinition(name, steps.toList(), skippable)
}

/** Builder for parallel cutscene steps. */
@GbktDsl
class ParallelBuilder {
    internal val steps = mutableListOf<TimelineStep>()

    fun wait(frames: Int) {
        steps.add(TimelineStep.Wait(frames))
    }

    fun wait(timing: FrameTiming) {
        steps.add(TimelineStep.Wait(timing.count))
    }

    fun action(block: FrameScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope("parallel_action").block() }
        steps.add(TimelineStep.Action(recorder.statements))
    }

    fun transition(t: Transition) {
        steps.add(TimelineStep.TransitionEffect(t))
    }
}
