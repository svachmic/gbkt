/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.graphics.Camera
import io.github.gbkt.core.graphics.frames
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.print
import io.github.gbkt.core.screen

/**
 * Credits Scene
 *
 * Displays 8 pages of story and credits text with fade transitions. Matches original behavior from
 * credits.c:
 * - State machine: FADE_IN (30f) -> HOLD (240f) -> FADE_OUT (30f) -> next page
 * - 8 text pages with centered text
 * - START button returns to title
 *
 * Text Content (8 pages):
 * 1. "Long ago, a dragon terrorized the land..."
 * 2. "The king sealed it in a labyrinth..."
 * 3. "Now, a brave hero must descend..."
 * 4. "Developed by NESHacker"
 * 5. "Programming & Design: Ryan, Tommy"
 * 6. "Art: Ryan, Ledu, Mono"
 * 7. "Thanks to Patreon supporters"
 * 8. "Thank you for playing!"
 */
@Suppress("LongMethod", "MagicNumber")
fun GameBuilder.initCreditsScene(
    creditsState: CreditsSceneState,
    title: SceneRef,
    camera: Camera,
): SceneRef =
    scene("credits") {
        enter {
            screen.clear()
            // Reset state machine
            creditsState.creditsState set 0 // FADE_IN
            creditsState.pageIndex set 0 // First page
            creditsState.frameCounter set 0

            // Draw first page
            print("Long ago,") at (5 to 6)
            print("a dragon terrorized") at (0 to 8)
            print("the land...") at (4 to 10)

            // Start fade in from black/white
            camera.fadeIn(30.frames)
        }

        every.frame {
            // Update camera for fade transitions
            camera.update()

            // =========================================================
            // STATE: FADE_IN (state 0)
            // =========================================================
            whenever(creditsState.creditsState isEqualTo 0) {
                creditsState.frameCounter += 1
                // After 30 frames, transition to HOLD
                whenever(creditsState.frameCounter isAtLeast 30) {
                    creditsState.creditsState set 1 // HOLD
                    creditsState.frameCounter set 0
                }
            }

            // =========================================================
            // STATE: HOLD (state 1)
            // =========================================================
            whenever(creditsState.creditsState isEqualTo 1) {
                creditsState.frameCounter += 1
                // After 240 frames (4 seconds), start fade out transition
                whenever(creditsState.frameCounter isAtLeast 240) {
                    creditsState.creditsState set 2 // FADE_OUT
                    creditsState.frameCounter set 0
                    // Trigger camera fade out
                    camera.fadeOut(30.frames)
                }
            }

            // =========================================================
            // STATE: FADE_OUT (state 2)
            // =========================================================
            whenever(creditsState.creditsState isEqualTo 2) {
                creditsState.frameCounter += 1
                // After 30 frames, advance to next page or finish
                whenever(creditsState.frameCounter isAtLeast 30) {
                    creditsState.pageIndex += 1

                    // Check if we've shown all 8 pages
                    whenever(creditsState.pageIndex isAtLeast 8) {
                        // Credits complete, return to title
                        scene(title)
                    }

                    // Otherwise, show next page and restart FADE_IN
                    whenever(creditsState.pageIndex isBelow 8) {
                        creditsState.creditsState set 0 // Back to FADE_IN
                        creditsState.frameCounter set 0
                        screen.clear()
                        // Trigger camera fade in for new page
                        camera.fadeIn(30.frames)

                        // Draw page based on index
                        // Page 1: King's seal
                        whenever(creditsState.pageIndex isEqualTo 1) {
                            print("The king sealed") at (2 to 6)
                            print("it in a labyrinth") at (1 to 8)
                            print("beneath the castle.") at (0 to 10)
                        }

                        // Page 2: Hero descends
                        whenever(creditsState.pageIndex isEqualTo 2) {
                            print("Now, a brave hero") at (1 to 6)
                            print("must descend into") at (1 to 8)
                            print("the darkness...") at (2 to 10)
                        }

                        // Page 3: Developer
                        whenever(creditsState.pageIndex isEqualTo 3) {
                            print("Developed by") at (3 to 6)
                            print("NESHacker") at (5 to 9)
                        }

                        // Page 4: Programming
                        whenever(creditsState.pageIndex isEqualTo 4) {
                            print("Programming") at (4 to 5)
                            print("& Design") at (6 to 7)
                            print("Ryan, Tommy") at (4 to 10)
                        }

                        // Page 5: Art
                        whenever(creditsState.pageIndex isEqualTo 5) {
                            print("Art") at (8 to 6)
                            print("Ryan, Ledu, Mono") at (2 to 9)
                        }

                        // Page 6: Supporters
                        whenever(creditsState.pageIndex isEqualTo 6) {
                            print("Thanks to") at (5 to 6)
                            print("Patreon") at (6 to 8)
                            print("supporters") at (4 to 10)
                        }

                        // Page 7: Thank you
                        whenever(creditsState.pageIndex isEqualTo 7) {
                            print("Thank you") at (5 to 6)
                            print("for playing!") at (4 to 9)
                        }
                    }
                }
            }

            // =========================================================
            // START BUTTON: Skip to title
            // =========================================================
            whenever(buttons.start.pressed) { scene(title) }
        }
    }
