/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.gbkt"
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Core dependency — StepAgent, GameMetadata, AgentSessionConfig
    api(project(":gbkt-emulator"))

    // JUnit5 extension API — compileOnly so consumers bring their own JUnit5 version
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter.api)

    // Test dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
