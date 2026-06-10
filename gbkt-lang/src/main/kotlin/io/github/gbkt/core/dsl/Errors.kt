/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File named for its purpose (DSL errors); contains DSLValidationError

package io.github.gbkt.core.dsl

/**
 * Thrown when DSL validation fails — either at recording time (immediate errors) or at
 * [GameBuilder.build] time (ref resolution, completeness checks).
 *
 * Error format follows compiler conventions:
 * ```
 * error: Unresolved reference "X". Did you mean 'Y'?
 * ```
 */
class DSLValidationError(message: String) : Exception(message)
