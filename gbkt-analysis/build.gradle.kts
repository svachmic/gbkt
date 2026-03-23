/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    kotlin("jvm")
    id("gbkt.publishing")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Transitively exposes gbkt-core (GameIR, TargetProfile, IR types, etc.)
    api(project(":gbkt-backend-api"))

    // JSON serialization for optimization report output
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
}

// ── Module Boundary Notes ─────────────────────────────────────────────────────
// gbkt-analysis sits above the core layer (depends on gbkt-backend-api, which
// transitively includes all core modules). There are no upward boundary
// constraints needed here — it intentionally consumes all lower layers.
