/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.scene

/** Scene identifier type. Matches the string IDs used in [SceneIR.id]. */
typealias SceneId = String

/**
 * Lifecycle contract for scene implementations.
 *
 * Implementors are called by the runtime engine when scene state transitions occur. In the v2
 * pipeline, these callbacks correspond to the enter, frame, and exit blocks recorded via
 * [SceneBuilder].
 */
interface SceneLifecycle {
    /** Called once when the engine transitions into this scene. */
    fun onEnter()

    /** Called every game frame while this scene is active. */
    fun onFrame()

    /** Called once when the engine transitions away from this scene. */
    fun onExit()
}

/** Type of transition fade effect to apply between scene changes. */
enum class FadeType {
    /** No visual transition effect — scene switches instantly. */
    NONE,

    /** Screen fades to black before the new scene appears. */
    FADE_BLACK,

    /** Screen fades to white before the new scene appears. */
    FADE_WHITE,
}

/**
 * Describes a pending scene transition requested from within a scene frame.
 *
 * @property targetSceneId The [SceneId] of the scene to transition into.
 * @property fadeType The fade effect to apply during the transition. Defaults to [FadeType.NONE].
 */
data class SceneTransitionRequest(
    val targetSceneId: SceneId,
    val fadeType: FadeType = FadeType.NONE,
)
