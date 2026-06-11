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
    artifactId.set("gbkt-emulator")
    description.set("gbkt Emulator - Embedded Coffee-GB emulator with debug tooling for Game Boy development")
}

dependencies {
    // Coffee-GB emulator core — headless Game Boy emulation engine
    implementation(libs.coffeegb.core)

    // JSON parsing for .gbkt.map source map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

// ── Module Boundary Notes ─────────────────────────────────────────────────────
// gbkt-emulator is a standalone debug/tooling module. It does NOT depend on
// any gbkt-* library modules — it only depends on Coffee-GB and JSON.
// Source map integration (gbkt-backend-gbdk source maps) happens at the
// gradle plugin level, keeping this module clean of backend dependencies.
