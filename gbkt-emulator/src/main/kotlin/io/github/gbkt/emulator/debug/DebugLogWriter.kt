/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Writes [DebugLogEntry] instances to a log file in real-time with auto-flush.
 *
 * Each emulator run starts with a fresh log file — the existing file is overwritten (not appended).
 * Auto-flush on every entry ensures `tail -f` compatibility and Claude-in-the-loop analysis can
 * observe entries as they are produced.
 *
 * Usage:
 * ```kotlin
 * DebugLogWriter(File("build/gbkt/logs/debug.log")).use { writer ->
 *     writer.write(entry)  // Immediately flushed to disk
 * }
 * ```
 *
 * **Known limitation:** No log rotation or size limit is applied. Long-running emulator sessions
 * may produce large log files on disk.
 *
 * @param logFile The file to write log entries to. Parent directories are created automatically if
 *   they do not exist. The file is overwritten on each instantiation.
 */
class DebugLogWriter(logFile: File) : Closeable {

    private val writer: PrintWriter

    init {
        logFile.parentFile?.mkdirs()
        // false = overwrite on each run (fresh per invocation, not append)
        // autoFlush = true ensures each println flushes immediately for tail -f
        writer = PrintWriter(BufferedWriter(FileWriter(logFile, false)), true)
    }

    /**
     * Writes a formatted log entry to the file and flushes immediately.
     *
     * The entry is formatted via [DebugLogEntry.formatted], which produces: `[MM:SS.mmm]
     * file.kt:line (context) > message\n`
     *
     * @param entry The log entry to write.
     */
    @Synchronized
    fun write(entry: DebugLogEntry) {
        writer.print(entry.formatted())
        writer.flush() // Explicit flush — ensures real-time visibility even with autoFlush quirks
    }

    /** Closes the underlying writer, flushing any buffered data to disk. */
    override fun close() {
        writer.close()
    }
}
