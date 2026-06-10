/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import java.io.File

/**
 * Resolves the gbkt repository root by walking up from the test JVM's working directory (the module
 * directory under Gradle) until a directory containing `gbkt-examples/` is found.
 *
 * Tests must use this instead of hardcoding absolute paths — absolute developer-machine paths pass
 * locally but throw [java.io.FileNotFoundException] on CI runners.
 */
internal fun findRepoRoot(): File {
    var dir = File(System.getProperty("user.dir")).absoluteFile
    while (!File(dir, "gbkt-examples").isDirectory) {
        dir =
            dir.parentFile
                ?: error("Could not locate gbkt repo root from ${System.getProperty("user.dir")}")
    }
    return dir
}
