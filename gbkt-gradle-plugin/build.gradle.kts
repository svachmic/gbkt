plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.diffplug.spotless") version "8.1.0"
}

val licenseHeader = """
    |/* This Source Code Form is subject to the terms of the Mozilla Public
    | * License, v. 2.0. If a copy of the MPL was not distributed with this
    | * file, You can obtain one at https://mozilla.org/MPL/2.0/.
    | *
    | * Copyright (c) 2026 Michal Svacha
    | */
""".trimMargin()

spotless {
    kotlin {
        target("src/**/*.kt")
        licenseHeader(licenseHeader)
        ktfmt().kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

repositories {
    mavenLocal()  // For local development with published gbkt-core
    mavenCentral()
}

dependencies {
    // Use JVM variant of gbkt-core (published to mavenLocal)
    implementation("io.github.gbkt:gbkt-core-jvm:0.1.0-SNAPSHOT")

    // JSON parsing for source map loading
    implementation("org.json:json:20231013")

    // Test dependencies
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("gbkt") {
            id = "io.github.gbkt"
            implementationClass = "io.github.gbkt.gradle.GbktPlugin"
            displayName = "gbkt"
            description = "Build retro game ROMs from Kotlin DSL"
        }
    }
}
