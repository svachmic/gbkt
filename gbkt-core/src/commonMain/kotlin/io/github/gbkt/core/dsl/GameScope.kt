/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBArray
import io.github.gbkt.core.ir.GBVar

// =============================================================================
// GAME SCOPE - Base class for DSL contexts
// =============================================================================

abstract class GameScope {
    internal val variables = mutableListOf<GBVar<*>>()
    internal val arrays = mutableListOf<GBArray>()

    fun registerVariable(v: GBVar<*>) {
        if (variables.none { it.name == v.name }) {
            variables.add(v)
        }
    }

    fun registerArray(arr: GBArray) {
        if (arrays.none { it.name == arr.name }) {
            arrays.add(arr)
        }
    }
}
