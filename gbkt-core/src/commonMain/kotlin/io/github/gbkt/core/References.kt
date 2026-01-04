/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

// =============================================================================
// TYPE-SAFE REFERENCES
// =============================================================================

/**
 * Type-safe reference to a scene.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val titleScene = scene("title") { ... }
 * val gameplayScene = scene("gameplay") { ... }
 *
 * start = titleScene  // Type-safe!
 *
 * scene("gameplay") {
 *     whenever(condition) {
 *         goto(titleScene)  // Type-safe!
 *     }
 * }
 * ```
 */
data class SceneRef(val name: String) {
    override fun toString() = "SceneRef($name)"
}

/**
 * Type-safe reference to an animation.
 *
 * Obtain AnimationRef from the animations block:
 * ```kotlin
 * sprite(SpriteAsset("player.png")) {
 *     animations {
 *         val runAnim = "run" plays (frames(2..5) every 8.frames)
 *     }
 * }
 *
 * player.play(runAnim)  // Type-safe!
 * ```
 */
data class AnimationRef(val name: String) {
    override fun toString() = "AnimationRef($name)"
}

/**
 * Type-safe reference to a state in a state machine.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val playerState = stateMachine("player") {
 *     val idle = state("idle") { ... }      // Returns StateRef
 *     val running = state("running") { ... }
 *
 *     transitions {
 *         on(buttons.left.held) goto running  // Type-safe!
 *     }
 * }
 *
 * whenever(playerState.isIn(idle)) { ... }  // Type-safe!
 * ```
 */
data class StateRef(val machineName: String, val stateName: String) {
    override fun toString() = "StateRef($machineName::$stateName)"
}

/**
 * Type-safe reference to an entity tag.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val enemyTag = tag("enemy")
 * val playerTag = tag("player")
 *
 * entity {
 *     tag(enemyTag)  // Type-safe!
 * }
 *
 * whenever(player collidesWithAny enemyTag) { ... }  // Type-safe!
 * ```
 */
data class TagRef(val name: String) {
    override fun toString() = "TagRef($name)"
}
