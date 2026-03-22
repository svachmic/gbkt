/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

/** DMG (original Game Boy) 4-shade grayscale palette indices. */
object DmgColor {
    /** Lightest shade (index 0). */
    const val WHITE = 0

    /** Second lightest shade (index 1). */
    const val LIGHT_GRAY = 1

    /** Second darkest shade (index 2). */
    const val DARK_GRAY = 2

    /** Darkest shade (index 3). */
    const val BLACK = 3
}
