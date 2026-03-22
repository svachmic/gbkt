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

gbktPublishing {
    artifactId.set("gbkt-backend-gbdk")
    description.set("gbkt GBDK Backend - Game Boy / Game Boy Color code generation using GBDK")
}

dependencies {
    // Implements the backend API - use api() to expose gbkt-core transitively to consumers
    api(project(":gbkt-backend-api"))

    // Analysis pipeline: provides DefaultPipeline, PassContext, BudgetAuditPass, etc.
    api(project(":gbkt-analysis"))

    // Engine module: provides shared PickupSystemConfig, PickupDef, PickupZone for pickup codegen
    implementation(project(":gbkt-engine"))

    // RPG genre package: provides CombatStats, CharacterDef, ExpCurve for RpgVisitor dispatch
    implementation(project(":gbkt-genre-rpg"))

    // JSON parsing for Tiled map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
}
