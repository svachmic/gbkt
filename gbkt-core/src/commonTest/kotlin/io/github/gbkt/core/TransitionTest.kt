/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransitionTest {

    // =========================================================================
    // BASIC TRANSITION TYPES
    // =========================================================================

    @Test
    fun testFadeOutTransition() {
        val scope = SceneTransitionScope()
        val t = scope.fadeOut(30.frames)

        assertIs<FadeOutTransition>(t)
        assertEquals(30, t.frames)
    }

    @Test
    fun testFadeOutTransitionIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.fadeOut(45)

        assertIs<FadeOutTransition>(t)
        assertEquals(45, t.frames)
    }

    @Test
    fun testFadeOutProperty() {
        val scope = SceneTransitionScope()
        val t = scope.fadeOut

        assertIs<FadeOutTransition>(t)
        assertEquals(30, t.frames) // Default is 30
    }

    @Test
    fun testFadeInTransition() {
        val scope = SceneTransitionScope()
        val t = scope.fadeIn(20.frames)

        assertIs<FadeInTransition>(t)
        assertEquals(20, t.frames)
    }

    @Test
    fun testFadeInTransitionIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.fadeIn(35)

        assertIs<FadeInTransition>(t)
        assertEquals(35, t.frames)
    }

    @Test
    fun testFadeInProperty() {
        val scope = SceneTransitionScope()
        val t = scope.fadeIn

        assertIs<FadeInTransition>(t)
        assertEquals(20, t.frames) // Default is 20
    }

    @Test
    fun testWaitTransition() {
        val scope = SceneTransitionScope()
        val t = scope.wait(10.frames)

        assertIs<WaitTransition>(t)
        assertEquals(10, t.frames)
    }

    @Test
    fun testWaitTransitionIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.wait(25)

        assertIs<WaitTransition>(t)
        assertEquals(25, t.frames)
    }

    @Test
    fun testFlashTransition() {
        val scope = SceneTransitionScope()
        val t = scope.flash(8.frames)

        assertIs<FlashTransition>(t)
        assertEquals(8, t.frames)
        assertEquals(GBCColor.WHITE, t.color)
    }

    @Test
    fun testFlashTransitionIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.flash(12)

        assertIs<FlashTransition>(t)
        assertEquals(12, t.frames)
    }

    @Test
    fun testFlashWithColor() {
        val scope = SceneTransitionScope()
        val t = scope.flash(GBCColor.RED, 10.frames)

        assertIs<FlashTransition>(t)
        assertEquals(10, t.frames)
        assertEquals(GBCColor.RED, t.color)
    }

    @Test
    fun testFlashProperty() {
        val scope = SceneTransitionScope()
        val t = scope.flash

        assertIs<FlashTransition>(t)
        assertEquals(8, t.frames)
        assertEquals(GBCColor.WHITE, t.color)
    }

    @Test
    fun testShakeTransition() {
        val scope = SceneTransitionScope()
        val t = scope.shake(4, 10.frames)

        assertIs<ShakeTransition>(t)
        assertEquals(4, t.intensity)
        assertEquals(10, t.frames)
        assertEquals(ShakeDecay.LINEAR, t.decay)
    }

    @Test
    fun testShakeTransitionIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.shake(6, 20)

        assertIs<ShakeTransition>(t)
        assertEquals(6, t.intensity)
        assertEquals(20, t.frames)
    }

    @Test
    fun testShakeBuilderConfig() {
        val scope = SceneTransitionScope()
        val t =
            scope.shake {
                intensity = 8
                duration = 30.frames
                decay = ShakeDecay.EXPONENTIAL
            }

        assertIs<ShakeTransition>(t)
        assertEquals(8, t.intensity)
        assertEquals(30, t.frames)
        assertEquals(ShakeDecay.EXPONENTIAL, t.decay)
    }

    @Test
    fun testImpactShake() {
        val scope = SceneTransitionScope()
        val t = scope.impact(5)

        assertIs<ShakeTransition>(t)
        assertEquals(5, t.intensity)
        assertEquals(8, t.frames)
        assertEquals(ShakeDecay.EXPONENTIAL, t.decay)
    }

    // =========================================================================
    // WIPE TRANSITIONS
    // =========================================================================

    @Test
    fun testWipeLeftTransition() {
        val scope = SceneTransitionScope()
        val t = scope.wipeLeft(45.frames)

        assertIs<WipeTransition>(t)
        assertEquals(45, t.frames)
        assertEquals(WipeDirection.LEFT, t.direction)
    }

    @Test
    fun testWipeRightTransition() {
        val scope = SceneTransitionScope()
        val t = scope.wipeRight(40.frames)

        assertIs<WipeTransition>(t)
        assertEquals(40, t.frames)
        assertEquals(WipeDirection.RIGHT, t.direction)
    }

    @Test
    fun testWipeUpTransition() {
        val scope = SceneTransitionScope()
        val t = scope.wipeUp(50.frames)

        assertIs<WipeTransition>(t)
        assertEquals(50, t.frames)
        assertEquals(WipeDirection.UP, t.direction)
    }

    @Test
    fun testWipeDownTransition() {
        val scope = SceneTransitionScope()
        val t = scope.wipeDown(55.frames)

        assertIs<WipeTransition>(t)
        assertEquals(55, t.frames)
        assertEquals(WipeDirection.DOWN, t.direction)
    }

    @Test
    fun testWipeProperties() {
        val scope = SceneTransitionScope()

        val left = scope.wipeLeft
        val right = scope.wipeRight
        val up = scope.wipeUp
        val down = scope.wipeDown

        assertEquals(WipeDirection.LEFT, left.direction)
        assertEquals(WipeDirection.RIGHT, right.direction)
        assertEquals(WipeDirection.UP, up.direction)
        assertEquals(WipeDirection.DOWN, down.direction)

        // All default to 45 frames
        assertEquals(45, left.frames)
        assertEquals(45, right.frames)
        assertEquals(45, up.frames)
        assertEquals(45, down.frames)
    }

    // =========================================================================
    // IRIS TRANSITIONS
    // =========================================================================

    @Test
    fun testIrisCloseTransition() {
        val scope = SceneTransitionScope()
        val t = scope.irisClose(60.frames)

        assertIs<IrisTransition>(t)
        assertEquals(60, t.frames)
        assertEquals(IrisType.CLOSE, t.type)
    }

    @Test
    fun testIrisCloseWithCoordinates() {
        val scope = SceneTransitionScope()
        val t = scope.irisClose(50.frames, 80, 72)

        assertIs<IrisTransition>(t)
        assertEquals(50, t.frames)
        assertEquals(IrisType.CLOSE, t.type)
        assertIs<IRLiteral>(t.centerX)
        assertIs<IRLiteral>(t.centerY)
        assertEquals(80, (t.centerX as IRLiteral).value)
        assertEquals(72, (t.centerY as IRLiteral).value)
    }

    @Test
    fun testIrisOpenTransition() {
        val scope = SceneTransitionScope()
        val t = scope.irisOpen(55.frames)

        assertIs<IrisTransition>(t)
        assertEquals(55, t.frames)
        assertEquals(IrisType.OPEN, t.type)
    }

    @Test
    fun testIrisOpenWithCoordinates() {
        val scope = SceneTransitionScope()
        val t = scope.irisOpen(45.frames, 100, 80)

        assertIs<IrisTransition>(t)
        assertEquals(45, t.frames)
        assertEquals(IrisType.OPEN, t.type)
        assertIs<IRLiteral>(t.centerX)
        assertIs<IRLiteral>(t.centerY)
    }

    @Test
    fun testIrisProperties() {
        val scope = SceneTransitionScope()

        val close = scope.irisClose
        val open = scope.irisOpen

        assertEquals(IrisType.CLOSE, close.type)
        assertEquals(IrisType.OPEN, open.type)
        assertEquals(60, close.frames)
        assertEquals(60, open.frames)
    }

    // =========================================================================
    // COMPOSITION OPERATORS - SEQUENCE (then)
    // =========================================================================

    @Test
    fun testThenOperatorBasic() {
        val scope = SceneTransitionScope()
        val t = scope.fadeOut then scope.fadeIn

        assertIs<SequenceTransition>(t)
        assertEquals(2, t.steps.size)
        assertIs<FadeOutTransition>(t.steps[0])
        assertIs<FadeInTransition>(t.steps[1])
    }

    @Test
    fun testThenOperatorChain() {
        val scope = SceneTransitionScope()
        val t = scope.fadeOut then scope.wait(10) then scope.fadeIn

        assertIs<SequenceTransition>(t)
        assertEquals(3, t.steps.size)
        assertIs<FadeOutTransition>(t.steps[0])
        assertIs<WaitTransition>(t.steps[1])
        assertIs<FadeInTransition>(t.steps[2])
    }

    @Test
    fun testThenOperatorMergesSequences() {
        val scope = SceneTransitionScope()
        val seq1 = scope.fadeOut then scope.wait(5)
        val seq2 = scope.flash then scope.fadeIn
        val combined = seq1 then seq2

        assertIs<SequenceTransition>(combined)
        assertEquals(4, combined.steps.size)
        assertIs<FadeOutTransition>(combined.steps[0])
        assertIs<WaitTransition>(combined.steps[1])
        assertIs<FlashTransition>(combined.steps[2])
        assertIs<FadeInTransition>(combined.steps[3])
    }

    @Test
    fun testThenWithSequenceOnLeft() {
        val scope = SceneTransitionScope()
        val seq = scope.fadeOut then scope.wait(5)
        val result = seq then scope.fadeIn

        assertIs<SequenceTransition>(result)
        assertEquals(3, result.steps.size)
    }

    @Test
    fun testThenWithSequenceOnRight() {
        val scope = SceneTransitionScope()
        val seq = scope.wait(5) then scope.fadeIn
        val result = scope.fadeOut then seq

        assertIs<SequenceTransition>(result)
        assertEquals(3, result.steps.size)
    }

    // =========================================================================
    // COMPOSITION OPERATORS - PARALLEL (and)
    // =========================================================================

    @Test
    fun testAndOperatorBasic() {
        val scope = SceneTransitionScope()
        val t = scope.shake(4, 10) and scope.fadeOut

        assertIs<ParallelTransition>(t)
        assertEquals(2, t.effects.size)
        assertIs<ShakeTransition>(t.effects[0])
        assertIs<FadeOutTransition>(t.effects[1])
    }

    @Test
    fun testAndOperatorChain() {
        val scope = SceneTransitionScope()
        val t = scope.shake(4, 10) and scope.fadeOut and scope.flash

        assertIs<ParallelTransition>(t)
        assertEquals(3, t.effects.size)
    }

    @Test
    fun testAndOperatorMergesParallel() {
        val scope = SceneTransitionScope()
        val par1 = scope.shake(4, 10) and scope.fadeOut
        val par2 = scope.flash and scope.wipeLeft
        val combined = par1 and par2

        assertIs<ParallelTransition>(combined)
        assertEquals(4, combined.effects.size)
    }

    @Test
    fun testAndWithParallelOnLeft() {
        val scope = SceneTransitionScope()
        val par = scope.shake(4, 10) and scope.fadeOut
        val result = par and scope.flash

        assertIs<ParallelTransition>(result)
        assertEquals(3, result.effects.size)
    }

    @Test
    fun testAndWithParallelOnRight() {
        val scope = SceneTransitionScope()
        val par = scope.fadeOut and scope.flash
        val result = scope.shake(4, 10) and par

        assertIs<ParallelTransition>(result)
        assertEquals(3, result.effects.size)
    }

    // =========================================================================
    // MIXED COMPOSITION
    // =========================================================================

    @Test
    fun testMixedComposition() {
        val scope = SceneTransitionScope()
        // (shake and fadeOut) then wait then fadeIn
        val t = (scope.shake(4, 10) and scope.fadeOut) then scope.wait(10) then scope.fadeIn

        assertIs<SequenceTransition>(t)
        assertEquals(3, t.steps.size)
        assertIs<ParallelTransition>(t.steps[0])
        assertIs<WaitTransition>(t.steps[1])
        assertIs<FadeInTransition>(t.steps[2])
    }

    // =========================================================================
    // TRANSITION DEFINITION
    // =========================================================================

    @Test
    fun testTransitionDefinition() {
        val scope = SceneTransitionScope()
        val trans = scope.fadeOut then scope.wait(10) then scope.fadeIn
        val def = TransitionDefinition("cinematic", trans)

        assertEquals("cinematic", def.name)
        assertIs<SequenceTransition>(def.transition)
    }

    // =========================================================================
    // SHAKE BUILDER
    // =========================================================================

    @Test
    fun testShakeBuilderDefaults() {
        val builder = ShakeBuilder()

        assertEquals(4, builder.intensity)
        assertEquals(15, builder.durationFrames)
        assertEquals(ShakeDecay.LINEAR, builder.decay)
    }

    @Test
    fun testShakeBuilderDurationProperty() {
        val builder = ShakeBuilder()
        builder.duration = 30.frames

        assertEquals(30, builder.durationFrames)
        assertEquals(30, builder.duration.count)
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun testTransitionGeneratesCode() {
        lateinit var nextScene: SceneRef
        val game =
            gbGame("test") {
                nextScene = scene("next") { every.frame {} }
                start =
                    scene("main") {
                        enter {
                            transitionTo(nextScene) {
                                fadeOut(30.frames) then wait(10.frames) then fadeIn(20.frames)
                            }
                        }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate transition code",
        )
    }

    @Test
    fun testParallelTransitionGeneratesCode() {
        lateinit var nextScene: SceneRef
        val game =
            gbGame("test") {
                nextScene = scene("next") { every.frame {} }
                start =
                    scene("main") {
                        enter {
                            transitionTo(nextScene) { shake(4, 10.frames) and fadeOut(30.frames) }
                        }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("shake") || code.contains("transition"),
            "Should generate transition code",
        )
    }

    @Test
    fun testTransitionCancelGeneratesCode() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame { whenever(buttons.start.pressed) { transition.cancel() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("cancel") || code.contains("transition"),
            "Should generate transition cancel code",
        )
    }

    @Test
    fun testIrisTransitionWithIntOverloads() {
        val scope = SceneTransitionScope()

        val close = scope.irisClose(40)
        val open = scope.irisOpen(35)
        val closeCoord = scope.irisClose(50, 80, 72)
        val openCoord = scope.irisOpen(45, 100, 80)

        assertEquals(40, close.frames)
        assertEquals(35, open.frames)
        assertEquals(50, closeCoord.frames)
        assertEquals(45, openCoord.frames)
    }

    @Test
    fun testWipeTransitionIntOverloads() {
        val scope = SceneTransitionScope()

        val left = scope.wipeLeft(30)
        val right = scope.wipeRight(35)
        val up = scope.wipeUp(40)
        val down = scope.wipeDown(45)

        assertEquals(30, left.frames)
        assertEquals(35, right.frames)
        assertEquals(40, up.frames)
        assertEquals(45, down.frames)
    }

    @Test
    fun testFlashWithColorIntOverload() {
        val scope = SceneTransitionScope()
        val t = scope.flash(GBCColor.BLUE, 15)

        assertEquals(15, t.frames)
        assertEquals(GBCColor.BLUE, t.color)
    }
}
