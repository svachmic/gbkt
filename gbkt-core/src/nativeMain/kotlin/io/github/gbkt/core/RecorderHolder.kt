/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.native.concurrent.ThreadLocal

/**
 * Native implementation of RecorderHolder with thread-local storage.
 *
 * Each thread gets its own recorder instance, preventing concurrent DSL definitions from
 * interfering with each other.
 *
 * Uses a companion object with @ThreadLocal to achieve per-thread isolation in Kotlin/Native's new
 * memory model.
 */
actual class RecorderHolder {
    actual fun get(): StatementRecorder? = Storage.recorder

    actual fun set(recorder: StatementRecorder?) {
        Storage.recorder = recorder
    }

    @ThreadLocal
    private object Storage {
        var recorder: StatementRecorder? = null
    }
}
