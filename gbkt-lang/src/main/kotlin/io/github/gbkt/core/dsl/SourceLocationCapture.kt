/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.SourceLocation

/**
 * Captures the caller's source location by inspecting the current thread's stack trace.
 *
 * Walks the stack frames and returns the first frame that is NOT part of gbkt DSL or IR
 * implementation packages, Kotlin runtime, or JVM internals. This frame represents the game
 * author's DSL call site — the location that should appear in source maps and error messages.
 *
 * Packages skipped:
 * - `io.github.gbkt.core.dsl` — DSL builder implementation
 * - `io.github.gbkt.core.ir` — IR node construction
 * - `kotlin.` — Kotlin standard library
 * - `java.` / `jdk.` — JVM internals
 *
 * @return [SourceLocation] for the caller's frame, or null if no suitable frame is found.
 */
@Suppress("LoopWithTooManyJumpStatements") // Stack trace filtering requires multiple skips
internal fun captureV2Location(): SourceLocation? {
    val stackTrace = Thread.currentThread().stackTrace

    for (frame in stackTrace) {
        val className = frame.className

        // Skip JVM internal frames
        if (className.startsWith("java.") || className.startsWith("jdk.")) continue

        // Skip Kotlin standard library frames
        if (className.startsWith("kotlin.")) continue

        // Skip gbkt DSL implementation frames (builders, recording context)
        if (className.startsWith("io.github.gbkt.core.dsl")) continue

        // Skip gbkt IR construction frames
        if (className.startsWith("io.github.gbkt.core.ir")) continue

        // Found a user-code frame — return its location
        val fileName = frame.fileName ?: continue
        val lineNumber = frame.lineNumber
        if (lineNumber <= 0) continue

        return SourceLocation(file = fileName, line = lineNumber, col = 0)
    }

    return null
}
