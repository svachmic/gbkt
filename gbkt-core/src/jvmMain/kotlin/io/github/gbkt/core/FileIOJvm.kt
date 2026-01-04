/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.io.File

/** JVM implementation of FileIO using java.io.File. */
actual object FileIO {
    /** Check if a file exists at the given path. */
    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }

    /** Read the contents of a file as a byte array. */
    actual fun readBytes(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /** Check if a file is readable. */
    actual fun isReadable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canRead()
    }

    /** Resolve a relative path against a base directory. */
    actual fun resolvePath(basePath: String, relativePath: String): String {
        return File(basePath, relativePath).absolutePath
    }
}
