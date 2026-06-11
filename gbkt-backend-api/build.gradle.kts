/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("gbkt.publishing")
}

kotlin {
    jvmToolchain(21)
}

gbktPublishing {
    artifactId.set("gbkt-backend-api")
    description.set("gbkt Backend API - Interface contract for code generation backends")
}

dependencies {
    // Depends on core for TargetProfile, Game model, and IR types
    api(project(":gbkt-core"))

    // Test dependencies
    testImplementation(kotlin("test"))
}
