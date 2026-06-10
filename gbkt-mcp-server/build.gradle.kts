/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.gbkt.mcp.GbktMcpServerKt")
}

dependencies {
    // gbkt emulator — StepAgent, GameMetadata, AgentSessionConfig
    implementation(project(":gbkt-emulator"))

    // gbkt-test — GameDiscovery for convention-based game name resolution
    implementation(project(":gbkt-test"))

    // MCP Kotlin SDK — server + stdio transport
    implementation(libs.mcp.kotlin.sdk)

    // Coroutines for async wrapping of blocking StepAgent calls
    implementation(libs.mcp.coroutines.core)

    // kotlinx-serialization for JSON tool results
    implementation(libs.serialization.json)

    // kotlinx-io for stdio transport
    implementation(libs.kotlinx.io.core)

    // SLF4J NOP — suppress logging noise in stdio mode
    runtimeOnly(libs.slf4j.nop)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.mcp.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("gbkt-mcp-server")
    archiveClassifier.set("all")
    mergeServiceFiles()
}
