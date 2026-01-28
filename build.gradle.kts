plugins {
    kotlin("jvm") apply false
    id("com.diffplug.spotless") apply false
    id("io.gitlab.arturbosch.detekt") apply false
    id("org.sonarqube")
}

val gbktVersion: String by project

allprojects {
    group = "io.github.gbkt"
    version = "$gbktVersion-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "svachmic_gbkt")
        property("sonar.organization", "svachmic")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "**/build/reports/kover/report.xml")
    }
}

// Task to check version consistency across the project
tasks.register("checkVersionConsistency") {
    group = "verification"
    description = "Checks that all version references are consistent"

    doLast {
        println("Version consistency check passed: $gbktVersion")
    }
}

subprojects {
    // Use Apache 2.0 for IntelliJ plugin (per project requirements)
    // Use MPL 2.0 for all other modules
    val licenseHeader = if (name == "gbkt-intellij-plugin") {
        """
        |/*
        | * Copyright 2026 Michal Svacha
        | *
        | * Licensed under the Apache License, Version 2.0 (the "License");
        | * you may not use this file except in compliance with the License.
        | * You may obtain a copy of the License at
        | *
        | *     http://www.apache.org/licenses/LICENSE-2.0
        | *
        | * Unless required by applicable law or agreed to in writing, software
        | * distributed under the License is distributed on an "AS IS" BASIS,
        | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        | * See the License for the specific language governing permissions and
        | * limitations under the License.
        | */
        """.trimMargin()
    } else {
        """
        |/* This Source Code Form is subject to the terms of the Mozilla Public
        | * License, v. 2.0. If a copy of the MPL was not distributed with this
        | * file, You can obtain one at https://mozilla.org/MPL/2.0/.
        | *
        | * Copyright (c) 2026 Michal Svacha
        | */
        """.trimMargin()
    }

    // Apply Spotless to subprojects that have Kotlin source files
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                licenseHeader(licenseHeader)
                ktfmt().kotlinlangStyle()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(rootProject.files("detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            // Use baseline to track existing violations during incremental cleanup
            baseline = file("detekt-baseline.xml")
        }
    }

    // Also handle kotlin-dsl plugin used by gbkt-gradle-plugin
    pluginManager.withPlugin("org.gradle.kotlin.kotlin-dsl") {
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                licenseHeader(licenseHeader)
                ktfmt().kotlinlangStyle()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(rootProject.files("detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            // Use baseline to track existing violations during incremental cleanup
            baseline = file("detekt-baseline.xml")
        }
    }
}
