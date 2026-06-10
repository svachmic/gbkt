/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.io.File

/** File I/O operations for asset validation. */
object FileIO {
    /**
     * Check if a file exists at the given path.
     *
     * @param path Absolute or relative file path
     * @return True if the file exists
     */
    fun exists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * Read the contents of a file as a byte array.
     *
     * @param path Absolute or relative file path
     * @return The file contents, or null if the file cannot be read
     */
    fun readBytes(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Check if a file is readable.
     *
     * @param path Absolute or relative file path
     * @return True if the file exists and can be read
     */
    fun isReadable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canRead()
    }

    /**
     * Resolve a relative path against a base directory.
     *
     * @param basePath The base directory path
     * @param relativePath The relative path to resolve
     * @return The resolved absolute path
     */
    fun resolvePath(basePath: String, relativePath: String): String {
        return File(basePath, relativePath).absolutePath
    }
}
