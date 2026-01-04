/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlinx.cinterop.*
import platform.posix.*

/** Native implementation of FileIO using POSIX file operations. */
@OptIn(ExperimentalForeignApi::class)
actual object FileIO {
    /** Check if a file exists at the given path. */
    actual fun exists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    /** Read the contents of a file as a byte array. */
    actual fun readBytes(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null

        try {
            // Get file size
            if (fseek(file, 0, SEEK_END) != 0) {
                fclose(file)
                return null
            }
            val size = ftell(file)
            if (size < 0) {
                fclose(file)
                return null
            }
            if (fseek(file, 0, SEEK_SET) != 0) {
                fclose(file)
                return null
            }

            // Allocate buffer and read
            val buffer = ByteArray(size.toInt())
            if (size > 0) {
                buffer.usePinned { pinned ->
                    val bytesRead = fread(pinned.addressOf(0), 1u, size.toULong(), file)
                    if (bytesRead.toLong() != size) {
                        fclose(file)
                        return null
                    }
                }
            }

            fclose(file)
            return buffer
        } catch (e: Exception) {
            fclose(file)
            return null
        }
    }

    /** Check if a file is readable. */
    actual fun isReadable(path: String): Boolean {
        return access(path, R_OK) == 0
    }

    /** Resolve a relative path against a base directory. */
    actual fun resolvePath(basePath: String, relativePath: String): String {
        // Simple path joining - handle trailing/leading slashes
        val base = basePath.trimEnd('/')
        val relative = relativePath.trimStart('/')
        return "$base/$relative"
    }
}
