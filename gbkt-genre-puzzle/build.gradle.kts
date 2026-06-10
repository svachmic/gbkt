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
    implementation(project(":gbkt-core"))
    // Backend API: GenreSystemVisitor interface and GenreVisitorResult
    implementation(project(":gbkt-backend-api"))
    // Backend GBDK: CFunction, CVarDecl, and C AST types needed by PuzzleVisitor
    implementation(project(":gbkt-backend-gbdk"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
