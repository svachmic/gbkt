/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.Transition

// =============================================================================
// SCENE
// =============================================================================

class Scene(
    val name: String,
    val onEnter: List<IRStatement>,
    val onFrame: List<IRStatement>,
    val onExit: List<IRStatement>,
)

@GbktDsl
class SceneBuilder(private val name: String, private val gameScope: GameScope) {
    private var enterStatements = emptyList<IRStatement>()
    private var frameStatements = emptyList<IRStatement>()
    private var exitStatements = emptyList<IRStatement>()

    /** The 'every' object for timing-based blocks */
    val every = TimingBlocks(this)

    /** Called once when entering this scene */
    fun enter(block: FrameScope.() -> Unit) {
        check(enterStatements.isEmpty()) {
            "Scene '$name' already has an enter block defined. Combine all enter logic into a single block."
        }
        enterStatements = recordBlock(block)
    }

    /** Called once when exiting this scene */
    fun exit(block: FrameScope.() -> Unit) {
        check(exitStatements.isEmpty()) {
            "Scene '$name' already has an exit block defined. Combine all exit logic into a single block."
        }
        exitStatements = recordBlock(block)
    }

    internal fun setFrameStatements(statements: List<IRStatement>) {
        check(frameStatements.isEmpty()) {
            "Scene '$name' already has a timing block defined (every.frame, every.second, etc.). " +
                "Combine all frame logic into a single timing block."
        }
        frameStatements = statements
    }

    private fun recordBlock(block: FrameScope.() -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope(name).block() }
        return recorder.statements
    }

    fun build() = Scene(name, enterStatements, frameStatements, exitStatements)
}

/**
 * Timing-based block definitions.
 *
 * Usage: every.frame { ... } every.second { ... } every.halfSecond { ... } every.quarterSecond {
 * ... } every(30).frames { ... }
 */
/**
 * Timing blocks for scene frame logic.
 *
 * ## Frame Rate Assumption
 *
 * All timing calculations assume **60 FPS** (frames per second), which is the standard for **NTSC**
 * (North America, Japan) Game Boy systems.
 *
 * **PAL** (Europe, Australia) systems run at approximately **50 FPS** instead. This means timing
 * will be approximately 20% slower on PAL systems:
 *
 * | Timing Block          | NTSC (60 FPS) | PAL (50 FPS) |
 * |-----------------------|---------------|--------------|
 * | `every.second`        | 60 frames     | ~1.2 seconds |
 * | `every.halfSecond`    | 30 frames     | ~0.6 seconds |
 * | `every.quarterSecond` | 15 frames     | ~0.3 seconds |
 *
 * For frame-rate-independent timing, use [every.frame] and track time manually, or consider
 * implementing VBlank-based timing in your game loop.
 */
class TimingBlocks(private val sceneBuilder: SceneBuilder) {

    /**
     * Execute every frame.
     *
     * On NTSC systems, this runs at ~60 FPS (59.7 Hz). On PAL systems, this runs at ~50 FPS (49.76
     * Hz).
     */
    fun frame(block: FrameScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope(sceneBuilder.toString()).block() }
        sceneBuilder.setFrameStatements(recorder.statements)
    }

    /**
     * Execute once per second (every 60 frames on NTSC).
     *
     * Note: On PAL systems, this will take ~1.2 seconds due to the 50 FPS refresh rate.
     */
    fun second(block: FrameScope.() -> Unit) {
        everyNFrames(60, block)
    }

    /**
     * Execute twice per second (every 30 frames on NTSC).
     *
     * Note: On PAL systems, this will take ~0.6 seconds due to the 50 FPS refresh rate.
     */
    fun halfSecond(block: FrameScope.() -> Unit) {
        everyNFrames(30, block)
    }

    /**
     * Execute four times per second (every 15 frames on NTSC).
     *
     * Note: On PAL systems, this will take ~0.3 seconds due to the 50 FPS refresh rate.
     */
    fun quarterSecond(block: FrameScope.() -> Unit) {
        everyNFrames(15, block)
    }

    private fun everyNFrames(n: Int, block: FrameScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope(sceneBuilder.toString()).block() }
        // Wrap in: if (frame_count % n == 0) { ... }
        val wrapped =
            listOf(
                IRIf(
                    IRBinary(
                        IRBinary(IRVar("_frame_count"), BinaryOp.MOD, IRLiteral(n)),
                        BinaryOp.EQ,
                        IRLiteral(0),
                    ),
                    recorder.statements,
                )
            )
        sceneBuilder.setFrameStatements(wrapped)
    }

    /** Execute every N frames */
    operator fun invoke(n: Int) = FrameInterval(n, sceneBuilder)
}

class FrameInterval(private val n: Int, private val sceneBuilder: SceneBuilder) {
    fun frames(block: FrameScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope(sceneBuilder.toString()).block() }
        val wrapped =
            listOf(
                IRIf(
                    IRBinary(
                        IRBinary(IRVar("_frame_count"), BinaryOp.MOD, IRLiteral(n)),
                        BinaryOp.EQ,
                        IRLiteral(0),
                    ),
                    recorder.statements,
                )
            )
        sceneBuilder.setFrameStatements(wrapped)
    }
}

// =============================================================================
// FRAME SCOPE - The context inside frame/enter/exit blocks
// =============================================================================

@GbktDsl
class FrameScope(private val sceneName: String) {

    /**
     * Include a reusable logic block.
     *
     * Logic blocks are recorded once and expanded at each call site. This allows extracting common
     * game logic into reusable functions.
     *
     * Usage:
     * ```kotlin
     * val applyGravity = logicBlock("gravity") {
     *     velocityY += 1
     *     whenever(velocityY isAbove 8) { velocityY set 8 }
     * }
     *
     * every.frame {
     *     include(applyGravity)
     *     playerY += velocityY
     * }
     * ```
     */
    fun include(block: io.github.gbkt.core.dsl.LogicBlock) {
        block()
    }

    /**
     * Go to another scene.
     *
     * Usage:
     * ```kotlin
     * scene(titleScene)
     * scene(gameplayScene)
     * ```
     */
    fun scene(ref: SceneRef) {
        RecordingContext.require().emit(IRSceneChange(ref.name))
    }

    /**
     * Go to another scene (alias for [scene]).
     *
     * Usage:
     * ```kotlin
     * goto(titleScene)
     * ```
     */
    fun goto(ref: SceneRef) {
        scene(ref)
    }

    /**
     * Go to another scene with an inline transition.
     *
     * Usage:
     * ```kotlin
     * transitionTo(gameoverScene) {
     *     shake(4) then fadeOut then wait(30.frames) then fadeIn
     * }
     * ```
     */
    fun transitionTo(ref: SceneRef, init: SceneTransitionScope.() -> Transition) {
        val scope = SceneTransitionScope()
        val trans = scope.init()
        RecordingContext.require().emit(IRComposedTransition(trans, ref.name))
    }

    /**
     * Create a SceneTransitionBuilder for fluent 'using' syntax.
     *
     * Usage:
     * ```kotlin
     * goto(menuScene) using cinematicFade
     * goto(gameoverScene) using fadeOut
     * ```
     */
    fun goto(ref: SceneRef, dummy: Unit = Unit): SceneTransitionBuilder {
        return SceneTransitionBuilder(ref.name)
    }

    /** Conditional execution - returns WheneverResult for chaining with otherwise */
    inline fun whenever(condition: Condition, noinline block: () -> Unit): WheneverResult {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        RecordingContext.require().emit(IRIf(condition.ir, recorder.statements))
        return WheneverResult(condition.ir, recorder.statements)
    }

    /** If-else (explicit two-argument version) */
    inline fun whenever(
        condition: Condition,
        noinline then: () -> Unit,
        noinline elseBlock: () -> Unit,
    ) {
        val thenRecorder = StatementRecorder()
        val elseRecorder = StatementRecorder()
        RecordingContext.record(thenRecorder, then)
        RecordingContext.record(elseRecorder, elseBlock)
        RecordingContext.require()
            .emit(IRIf(condition.ir, thenRecorder.statements, elseRecorder.statements))
    }

    /** Play a sound effect */
    fun sound(id: Int) {
        RecordingContext.require().emit(IRCall("play_sound", listOf(IRLiteral(id))))
    }

    /** Print debug text (emulator only) */
    fun debug(message: String) {
        RecordingContext.require().emit(IRCall("EMU_printf", listOf(IRLiteral(message))))
    }

    /** Raw C escape hatch */
    fun raw(code: String) {
        RecordingContext.require().emit(IRRaw(code))
    }
}
