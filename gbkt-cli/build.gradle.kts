/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    kotlin("jvm")
    application
    id("gbkt.publishing")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.gbkt.cli.MainKt")
}

gbktPublishing {
    artifactId.set("gbkt-cli")
    description.set("gbkt CLI - Command-line interface for building Game Boy games from Kotlin DSL")
}

dependencies {
    // Core library - DSL, IR, all game constructs
    implementation(project(":gbkt-core"))

    // Backend API for discovering and using backends
    implementation(project(":gbkt-backend-api"))

    // Bundle GBDK backend in distribution for ServiceLoader discovery
    runtimeOnly(project(":gbkt-backend-gbdk"))

    // Test dependencies
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Create distribution with shell wrapper scripts
tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "gbkt"
}
