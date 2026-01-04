/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

// =============================================================================
// DSL MARKER - Prevents accidental access to outer scope in nested builders
// =============================================================================

/**
 * DSL marker annotation for gbkt builders.
 *
 * This prevents accidentally calling outer-scope functions from nested builders:
 * ```kotlin
 * scene("gameplay") {
 *     sprite(SpriteAsset("player.png")) {
 *         // scene("oops")  // Compile error! Can't access outer scope
 *     }
 * }
 * ```
 */
@DslMarker @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE) annotation class GbktDsl
