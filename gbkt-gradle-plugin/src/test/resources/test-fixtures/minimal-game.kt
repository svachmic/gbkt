/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package test

import io.github.gbkt.core.*

/** Minimal game fixture - simplest possible gbkt game. Used for testing basic code generation. */
val minimalGame =
    gbGame("MinimalGame") {
        var counter by u8Var(0)

        val mainScene = scene("main") { every.frame { counter += 1 } }

        start = mainScene
    }
