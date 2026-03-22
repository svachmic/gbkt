/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("MatchingDeclarationName") // File intentionally named for its purpose (DSL markers)

package io.github.gbkt.core.dsl

/**
 * DSL marker for the gbkt v2 builder DSL.
 *
 * Prevents implicit receiver leakage between nested builder scopes. Applied to all builder classes
 * to ensure methods are only callable on the correct receiver.
 */
@DslMarker annotation class GbktDsl
