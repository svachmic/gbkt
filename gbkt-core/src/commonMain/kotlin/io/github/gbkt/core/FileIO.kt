/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Platform-independent file I/O operations.
 *
 * This provides basic file operations needed for asset validation across all platforms.
 */
expect object FileIO {
    /**
     * Check if a file exists at the given path.
     *
     * @param path Absolute or relative file path
     * @return True if the file exists
     */
    fun exists(path: String): Boolean

    /**
     * Read the contents of a file as a byte array.
     *
     * @param path Absolute or relative file path
     * @return The file contents, or null if the file cannot be read
     */
    fun readBytes(path: String): ByteArray?

    /**
     * Check if a file is readable.
     *
     * @param path Absolute or relative file path
     * @return True if the file exists and can be read
     */
    fun isReadable(path: String): Boolean

    /**
     * Resolve a relative path against a base directory.
     *
     * @param basePath The base directory path
     * @param relativePath The relative path to resolve
     * @return The resolved absolute path
     */
    fun resolvePath(basePath: String, relativePath: String): String
}
