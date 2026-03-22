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
    implementation("io.modelcontextprotocol:kotlin-sdk:0.9.0")

    // Coroutines for async wrapping of blocking StepAgent calls
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // kotlinx-serialization for JSON tool results
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // kotlinx-io for stdio transport
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")

    // SLF4J NOP — suppress logging noise in stdio mode
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("gbkt-mcp-server")
    archiveClassifier.set("all")
    mergeServiceFiles()
}
