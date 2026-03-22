/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import io.github.gbkt.emulator.LogLevel

/**
 * A single structured log entry captured by the emulator's debug interceptor.
 *
 * Entries are produced by two sources:
 * 1. **Game code**: `gbkt_log()` calls in generated C code that correspond to DSL `log()` /
 *    `debug()` statements. These carry Kotlin source location from source maps.
 * 2. **Emulator internals**: CPU/PPU/APU events captured by [LogLevel.EMU], [LogLevel.WARN], and
 *    [LogLevel.ERROR] hooks.
 *
 * Example formatted output:
 * ```
 * [00:02.341] ScriptBuilder.kt:45 (gameplay/frame) > Score: 10
 * [00:02.341] (gameplay/frame) > Score: 10
 * [00:02.341] > ROM bank 3 switched
 * ```
 *
 * @param timestampMs Emulated wall-clock time in milliseconds since emulator start. This tracks
 *   Game Boy time (4.194 MHz / 70224 cycles per frame = ~59.73 fps).
 * @param level Severity level of the entry.
 * @param message The log message string.
 * @param pc The CPU program counter (PC) address at the time of the EMU_printf trap. Used for
 *   best-effort C line resolution when [cLine] is not available directly.
 * @param cLine C source line number from the generated .c file, if available.
 * @param kotlinFile Kotlin source file name (without path), resolved via source map. Null if source
 *   maps are not configured or line could not be resolved.
 * @param kotlinLine Kotlin source line number, resolved via source map. Null if source maps are not
 *   configured or line could not be resolved.
 * @param context Execution context string (e.g., "gameplay/frame", "battle/enter"). Derived from
 *   the scene ID and lifecycle phase of the generating script.
 */
data class DebugLogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val message: String,
    val pc: Int? = null,
    val cLine: Int? = null,
    val kotlinFile: String? = null,
    val kotlinLine: Int? = null,
    val context: String? = null,
) {
    /**
     * Returns a human-readable formatted string for this log entry.
     *
     * Format: `[MM:SS.mmm] file.kt:line (context) > message\n`
     *
     * Examples:
     * - `[00:02.341] ScriptBuilder.kt:45 (gameplay/frame) > Score: 10\n`
     * - `[00:02.341] (gameplay/frame) > Score: 10\n` (no source map)
     * - `[00:02.341] > ROM bank 3 switched\n` (emulator internal)
     */
    fun formatted(): String {
        val ts =
            "[%02d:%02d.%03d]"
                .format(timestampMs / 60000, (timestampMs / 1000) % 60, timestampMs % 1000)
        val location =
            if (kotlinFile != null && kotlinLine != null) {
                " $kotlinFile:$kotlinLine"
            } else if (kotlinFile != null) {
                " $kotlinFile"
            } else {
                ""
            }
        val ctx = if (context != null) " ($context)" else ""
        return "$ts$location$ctx > $message\n"
    }
}
