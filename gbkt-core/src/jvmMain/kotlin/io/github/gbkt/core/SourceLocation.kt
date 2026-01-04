/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/** JVM implementation of source location capture using stack trace. */
actual fun captureSourceLocation(): SourceLocation? {
    val stackTrace = Thread.currentThread().stackTrace

    // Find the first frame outside gbkt.core package that's in user code
    // Skip frames from:
    // - java.lang.Thread (getStackTrace)
    // - gbkt.core package (our DSL code)
    for (frame in stackTrace) {
        val className = frame.className

        // Skip JVM internal frames
        if (className.startsWith("java.") || className.startsWith("jdk.")) {
            continue
        }

        // Skip gbkt.core package (DSL implementation)
        if (className.startsWith("gbkt.core.")) {
            continue
        }

        // Skip Kotlin internal frames
        if (className.startsWith("kotlin.")) {
            continue
        }

        // Found user code frame
        val fileName = frame.fileName ?: return null
        val lineNumber = frame.lineNumber

        if (lineNumber <= 0) {
            continue
        }

        return SourceLocation(file = fileName, line = lineNumber)
    }

    return null
}
