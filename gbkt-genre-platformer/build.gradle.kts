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
    implementation(project(":gbkt-lang"))
    implementation(project(":gbkt-backend-api"))
    implementation(project(":gbkt-backend-gbdk"))
    implementation(project(":gbkt-engine"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-core"))
}

tasks.test {
    useJUnitPlatform()
}
