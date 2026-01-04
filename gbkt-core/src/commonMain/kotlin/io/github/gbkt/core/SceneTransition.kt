/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.Transition

// =============================================================================
// SCENE TRANSITION BUILDER - For fluent 'using' syntax
// =============================================================================

/**
 * Builder for scene transitions with 'using' syntax.
 *
 * Usage:
 * ```kotlin
 * goto("menu") using cinematicFade
 * goto("gameover") using FadeOutTransition()
 * ```
 */
class SceneTransitionBuilder(private val sceneName: String) {
    /** Apply a predefined transition to this scene change. */
    infix fun using(transition: TransitionDefinition) {
        RecordingContext.require().emit(IRComposedTransition(transition.transition, sceneName))
    }

    /** Apply a transition value directly. */
    infix fun using(transition: Transition) {
        RecordingContext.require().emit(IRComposedTransition(transition, sceneName))
    }
}
