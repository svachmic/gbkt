/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File

/**
 * Resolves a file under the gbkt repository root by walking up from the test JVM's working
 * directory (the gbkt-gradle-plugin module directory under Gradle) until a directory containing
 * `gbkt-examples/` is found. The marker is the examples directory rather than settings.gradle.kts
 * because gbkt-gradle-plugin is an included build with its own settings file.
 *
 * Tests must use this instead of hardcoding absolute paths — absolute developer-machine paths pass
 * locally but fail on CI runners.
 */
internal fun repoFile(relative: String): File {
    var dir = File(System.getProperty("user.dir")).absoluteFile
    while (!File(dir, "gbkt-examples").isDirectory) {
        dir =
            dir.parentFile
                ?: error("Could not locate gbkt repo root from ${System.getProperty("user.dir")}")
    }
    return File(dir, relative)
}
