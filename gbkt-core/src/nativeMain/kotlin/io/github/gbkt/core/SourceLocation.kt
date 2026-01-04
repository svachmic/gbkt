/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Native implementation of source location capture. Stack trace is not readily available in
 * Kotlin/Native, so we return null. Source maps will have limited functionality on native targets.
 */
actual fun captureSourceLocation(): SourceLocation? = null
