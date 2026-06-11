/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

/**
 * gbkt-all — Convenience meta-module that aggregates all published gbkt modules.
 *
 * Game authors can depend on a single artifact instead of managing individual module coordinates:
 * ```kotlin
 * dependencies {
 *     implementation("io.github.gbkt:gbkt-all:VERSION")
 * }
 * ```
 *
 * This module has no source files — it is a pure dependency aggregator.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Core platform modules
    api(project(":gbkt-core"))
    api(project(":gbkt-ir"))
    api(project(":gbkt-lang"))
    api(project(":gbkt-engine")) // includes combat and inventory engine systems (Phase 06.4)
    api(project(":gbkt-world"))

    // Backend modules
    api(project(":gbkt-backend-api"))
    api(project(":gbkt-backend-gbdk"))

    // Genre packages
    api(project(":gbkt-genre-rpg"))
    api(project(":gbkt-genre-platformer"))
    api(project(":gbkt-genre-puzzle"))
    api(project(":gbkt-genre-sport"))

    // Analysis passes
    api(project(":gbkt-analysis"))

    // Embedded Game Boy emulator — Coffee-GB core with debug log capture and developer UI
    api(project(":gbkt-emulator"))
}
