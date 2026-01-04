/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.simulation

import io.github.gbkt.core.*
import io.github.gbkt.core.test.*
import kotlin.test.*

/**
 * Example game logic tests demonstrating the beautiful testing API.
 *
 * These tests show how developers can test their game logic without needing to compile to ROM or
 * run an emulator.
 */
class GameLogicTest {

    @Test
    fun `variable assignment works in simulation`() =
        testScene("test") {
            var counter by u8Var(0)

            every.frame { counter += 1 }

            test {
                expect("counter").toEqual(0)

                advanceFrame()
                expect("counter").toEqual(1)

                advanceFrames(9)
                expect("counter").toEqual(10)
            }
        }

    @Test
    fun `input simulation works correctly`() =
        testScene("test") {
            var buttonPressed by u8Var(0)

            every.frame { whenever(buttons.a.pressed) { buttonPressed set 1 } }

            test {
                expect("buttonPressed").toEqual(0)

                // Tap A button
                tap(Button.A)
                expect("buttonPressed").toEqual(1)
            }
        }

    @Test
    fun `d-pad movement simulation`() =
        testScene("test") {
            var playerX by u8Var(80)

            every.frame { playerX += dpad.x * 2 }

            test {
                expect("playerX").toEqual(80)

                // Move right for 5 frames
                press(Button.RIGHT) { advanceFrames(5) }

                expect("playerX").toEqual(90) // 80 + (2 * 5)

                // Move left for 3 frames
                press(Button.LEFT) { advanceFrames(3) }

                expect("playerX").toEqual(84) // 90 - (2 * 3)
            }
        }

    @Test
    fun `conditional logic works`() =
        testScene("test") {
            var score by u8Var(0)
            var bonusTriggered by u8Var(0)

            every.frame {
                score += 1
                whenever(score isAtLeast 10) { bonusTriggered set 1 }
            }

            test {
                advanceFrames(9)
                expect("bonusTriggered").toEqual(0)

                advanceFrame()
                expect("bonusTriggered").toEqual(1)
            }
        }

    @Test
    fun `advanceUntil with timeout works`() =
        testScene("test") {
            var timer by u8Var(0)

            every.frame { timer += 1 }

            test {
                val result = advanceUntil { getVariable("timer") >= 50 }

                assertEquals(50, result.frames)
                assertTrue(result.conditionMet)
                expect("timer").toEqual(50)
            }
        }

    @Test
    fun `expect fluent assertions work`() =
        testScene("test") {
            var value by u8Var(50)

            every.frame {
                // Touch the variable to ensure it's registered
                whenever(value isEqualTo 50) {}
            }

            test {
                expect("value").toEqual(50)
                expect("value").toBeGreaterThan(40)
                expect("value").toBeLessThan(60)
                expect("value").toBeBetween(40..60)
                expect("value").toBeAtLeast(50)
                expect("value").toBeAtMost(50)
                expect("value").toSatisfy("is even") { it % 2 == 0 }
            }
        }

    @Test
    fun `game scene tracking works`() =
        testScene("test") {
            every.frame {}

            test {
                game.toBeInScene("test")
                expectScene("test")
            }
        }

    @Test
    fun `frame count tracking works`() =
        testScene("test") {
            every.frame {}

            test {
                assertEquals(0, frameCount)
                advanceFrames(100)
                assertEquals(100, frameCount)
                game.toHaveRunForAtLeast(100)
            }
        }

    @Test
    fun `multiple button combinations work`() =
        testScene("test") {
            var aPressed by u8Var(0)
            var bPressed by u8Var(0)

            every.frame {
                whenever(buttons.a.pressed) { aPressed set 1 }
                whenever(buttons.b.pressed) { bPressed set 1 }
            }

            test {
                // Press both A and B simultaneously
                tap(Button.A, Button.B)

                expect("aPressed").toEqual(1)
                expect("bPressed").toEqual(1)
            }
        }

    @Test
    fun `hold and release pattern works`() =
        testScene("test") {
            var holdFrames by u8Var(0)

            every.frame { whenever(buttons.a.held) { holdFrames += 1 } }

            test {
                hold(Button.A)
                advanceFrames(10)
                release(Button.A)
                advanceFrames(5)

                expect("holdFrames").toEqual(10)
            }
        }
}
