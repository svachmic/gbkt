plugins {
    kotlin("multiplatform") version "2.3.0" apply false
    kotlin("jvm") version "2.3.0" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("org.sonarqube") version "7.2.2.6593"
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
        property("sonar.sources", "gbkt-core/src,gbkt-cli/src,gbkt-gradle-plugin/src,vscode-extension/src")
        property("sonar.exclusions", "**/build/**,**/out/**,**/*.json,**/*.tmLanguage.json")
    }
}

// Task to sync version to vscode-extension/package.json
tasks.register("syncVscodeVersion") {
    group = "versioning"
    description = "Syncs the gbkt version to vscode-extension/package.json"

    doLast {
        val packageJsonFile = file("vscode-extension/package.json")
        if (packageJsonFile.exists()) {
            val content = packageJsonFile.readText()
            val versionRegex = """"version":\s*"[^"]+"""".toRegex()
            val updatedContent = content.replace(versionRegex, """"version": "$gbktVersion"""")
            packageJsonFile.writeText(updatedContent)
            println("Updated vscode-extension/package.json version to $gbktVersion")
        } else {
            println("Warning: vscode-extension/package.json not found")
        }
    }
}

// Task to check version consistency across the project
tasks.register("checkVersionConsistency") {
    group = "verification"
    description = "Checks that all version references are consistent"

    doLast {
        val packageJsonFile = file("vscode-extension/package.json")
        if (packageJsonFile.exists()) {
            val content = packageJsonFile.readText()
            val versionRegex = """"version":\s*"([^"]+)"""".toRegex()
            val match = versionRegex.find(content)
            val packageJsonVersion = match?.groupValues?.get(1)

            if (packageJsonVersion != gbktVersion) {
                throw GradleException(
                    "Version mismatch! gradle.properties has '$gbktVersion' but " +
                    "vscode-extension/package.json has '$packageJsonVersion'. " +
                    "Run './gradlew syncVscodeVersion' to fix."
                )
            }
            println("Version consistency check passed: $gbktVersion")
        }
    }
}

subprojects {
    val licenseHeader = """
        |/* This Source Code Form is subject to the terms of the Mozilla Public
        | * License, v. 2.0. If a copy of the MPL was not distributed with this
        | * file, You can obtain one at https://mozilla.org/MPL/2.0/.
        | *
        | * Copyright (c) 2026 Michal Svacha
        | */
    """.trimMargin()

    // Apply Spotless to subprojects that have Kotlin source files
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
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
