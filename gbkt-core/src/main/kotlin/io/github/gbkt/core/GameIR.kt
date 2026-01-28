/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Marker interface for game IR passed to code generation backends.
 *
 * The [Game] class implements this interface. This provides a stable contract that backends can
 * depend on without knowing the full Game implementation details.
 */
interface GameIR {
    /** Game name. */
    val name: String
}
