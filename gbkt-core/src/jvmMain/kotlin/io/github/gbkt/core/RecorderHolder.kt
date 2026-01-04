/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

actual class RecorderHolder {
    private val threadLocal = ThreadLocal<StatementRecorder?>()

    actual fun get(): StatementRecorder? = threadLocal.get()

    actual fun set(recorder: StatementRecorder?) = threadLocal.set(recorder)
}
