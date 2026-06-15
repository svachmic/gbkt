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
 *     runIf(condition) {
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
 * runIf(playerState.isIn(idle)) { ... }  // Type-safe!
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
 * runIf(player collidesWithAny enemyTag) { ... }  // Type-safe!
 * ```
 */
data class TagRef(val name: String) {
    override fun toString() = "TagRef($name)"
}

// =============================================================================
// RPG TYPE-SAFE REFERENCES
// =============================================================================

/**
 * Type-safe reference to a character.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val hero by character { name("Hero"); ... }
 *
 * // In battle or other contexts
 * hero.ref.level  // Type-safe access
 * ```
 */
data class CharacterRef(val id: String) {
    override fun toString() = "CharacterRef($id)"
}

/**
 * Type-safe reference to an ability.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val fireball by ability { name("Fireball"); ... }
 *
 * // When using abilities
 * hero.useAbility(fireball.ref)  // Type-safe!
 * ```
 */
data class AbilityRef(val id: String) {
    override fun toString() = "AbilityRef($id)"
}

/**
 * Type-safe reference to an item.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val potion by item { name("Potion"); ... }
 *
 * // When using items
 * inventory.add(potion.ref, 3)  // Type-safe!
 * ```
 */
data class ItemRef(val id: String) {
    override fun toString() = "ItemRef($id)"
}

/**
 * Type-safe reference to a monster.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val goblin by monster { name("Goblin"); ... }
 *
 * // In encounter tables
 * entry(weight = 30) { +goblin.ref }  // Type-safe!
 * ```
 */
data class MonsterRef(val id: String) {
    override fun toString() = "MonsterRef($id)"
}

/**
 * Type-safe reference to a status effect.
 *
 * Use this instead of raw strings for compile-time safety:
 * ```kotlin
 * val poison by statusEffect { name("Poison"); ... }
 *
 * // When applying effects
 * target.applyStatus(poison.ref)  // Type-safe!
 * ```
 */
data class StatusEffectRef(val id: String) {
    override fun toString() = "StatusEffectRef($id)"
}
