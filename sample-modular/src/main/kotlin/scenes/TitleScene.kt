/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.buttons
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen

/**
 * Title Scene
 *
 * The game's title/start screen.
 */

// We use lateinit to allow TitleScene to reference GameplayScene
// which is created after TitleScene
private lateinit var gameplaySceneRef: SceneRef

/** Set the gameplay scene reference for navigation. Call this after creating the gameplay scene. */
fun setGameplayScene(ref: SceneRef) {
    gameplaySceneRef = ref
}

/**
 * Create the title scene.
 *
 * @return SceneRef for the title scene
 *
 * Usage:
 * ```kotlin
 * val myGame = gbGame("MyGame") {
 *     val titleScene = createTitleScene()
 *     val gameplayScene = createGameplayScene(...)
 *     setGameplayScene(gameplayScene)  // Link scenes
 *     start = titleScene
 * }
 * ```
 */
fun GameBuilder.createTitleScene(): SceneRef =
    scene("title") {
        enter {
            screen.clear()
            screen.hideSprites()

            printCentered("MODULAR DEMO") at 6
            printCentered("Multi-file") at 9
            printCentered("Organization") at 10
            printCentered("PRESS START") at 14
        }

        every.frame { whenever(buttons.start.pressed) { scene(gameplaySceneRef) } }
    }
