/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.dsl.GameScope
import kotlin.native.concurrent.ThreadLocal

/**
 * Native implementation of GameScopeHolder with thread-local storage.
 *
 * Each thread gets its own scope instance, preventing concurrent DSL definitions from interfering
 * with each other.
 *
 * Uses a nested object with @ThreadLocal to achieve per-thread isolation in Kotlin/Native's new
 * memory model.
 */
actual class GameScopeHolder {
    actual fun get(): GameScope? = Storage.scope

    actual fun set(scope: GameScope?) {
        Storage.scope = scope
    }

    @ThreadLocal
    private object Storage {
        var scope: GameScope? = null
    }
}
