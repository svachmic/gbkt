/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName") // File defines DSL marker annotation

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

// =============================================================================
// RAW CODE ESCAPE HATCH MARKER
// =============================================================================

/**
 * Marks the raw() escape hatch function that emits arbitrary C code.
 *
 * This is an advanced feature for cases where the DSL doesn't cover a specific need. Using raw()
 * bypasses:
 * - Type safety (you write C directly)
 * - Source mapping (harder to debug)
 * - Framework guarantees (could break generated code)
 *
 * Usage requires explicit opt-in:
 * ```kotlin
 * @OptIn(RawCodeEscapeHatch::class)
 * fun myFunction() {
 *     raw("_custom_c_function();")
 * }
 * ```
 *
 * Prefer using framework abstractions when possible. If you find yourself using raw() frequently
 * for a specific pattern, consider filing a feature request.
 */
@RequiresOptIn(
    message =
        "raw() bypasses type safety and framework guarantees. " +
            "Only use when the DSL doesn't support your specific need.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class RawCodeEscapeHatch
