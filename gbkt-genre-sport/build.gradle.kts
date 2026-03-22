/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

/**
 * gbkt-genre-sport — Sport and racing genre package for gbkt.
 *
 * Provides domain types and DSL builders for:
 * - Racing games (tile-based tracks, waypoints, vehicles, AI opponents, time trial)
 * - Ball sports (field/court, ball physics, scoring rules, match structure)
 * - Tournament management (single-elimination, round-robin brackets, standings)
 * - Pickup/power-up system integration via gbkt-engine
 *
 * Follows the BOM separation pattern: depends on gbkt-core, produces GenericSystem
 * IR nodes from genre-specific domain types. No new sealed IR subtypes created.
 */
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":gbkt-core"))
    implementation(project(":gbkt-backend-api"))
    implementation(project(":gbkt-backend-gbdk"))
    implementation(project(":gbkt-engine"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
