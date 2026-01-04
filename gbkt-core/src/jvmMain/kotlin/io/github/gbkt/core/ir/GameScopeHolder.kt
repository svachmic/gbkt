/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.dsl.GameScope

actual class GameScopeHolder {
    private val threadLocal = ThreadLocal<GameScope?>()

    actual fun get(): GameScope? = threadLocal.get()

    actual fun set(scope: GameScope?) = threadLocal.set(scope)
}
