plugins {
    kotlin("jvm") apply false
    id("com.diffplug.spotless") apply false
    id("io.gitlab.arturbosch.detekt") apply false
    id("org.sonarqube")
}

val gbktVersion: String by project
val isRelease = project.hasProperty("release")

allprojects {
    group = "io.github.gbkt"
    version = if (isRelease) gbktVersion else "$gbktVersion-SNAPSHOT"

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

// ============================================================================
// Composite-build test wiring
//
// IntegrationTest writes a TestKit sandbox whose build file declares:
//   implementation("io.github.gbkt:gbkt-core:0.1.0-SNAPSHOT")
//   implementation("io.github.gbkt:gbkt-backend-api:0.1.0-SNAPSHOT")
//   runtimeOnly("io.github.gbkt:gbkt-backend-gbdk:0.1.0-SNAPSHOT")
// plus transitive deps resolved from mavenLocal(). Against a stale ~/.m2
// the sandbox Kotlin compile fails (13 failures when GameBuilder.start was
// still String? in cache while DSL surface had already changed to SceneRef?).
//
// The :gbkt-gradle-plugin is an includeBuild so its :test task cannot directly
// depend on root-project tasks via dependsOn. The supported pattern is a root
// aggregator lifecycle task that callers (CI / local dev) invoke instead of
// reaching the plugin :test task directly.
// ============================================================================
val mavenLocalModulesForPluginTest = listOf(
    ":gbkt-ir", ":gbkt-lang", ":gbkt-engine", ":gbkt-world",
    ":gbkt-core", ":gbkt-backend-api", ":gbkt-backend-gbdk",
    // gbkt-analysis is a transitive api() dependency of gbkt-backend-gbdk
    // (gbkt-backend-gbdk/build.gradle.kts:27). The IntegrationTest sandbox resolves it
    // via the runtimeOnly gbkt-backend-gbdk:0.1.0-SNAPSHOT edge, so it MUST be republished
    // too — otherwise a stale gbkt-analysis links against the fresh gbkt-ir and throws
    // NoSuchMethodError: SceneIR.copy$default (Phase 15 F1 / D-05 — the actual root cause).
    ":gbkt-analysis",
)

tasks.register("publishConsumedModulesToMavenLocal") {
    group = "verification"
    description = "Publish all modules consumed by the gbkt-gradle-plugin IntegrationTest TestKit sandbox to mavenLocal"
    mavenLocalModulesForPluginTest.forEach { path ->
        dependsOn("$path:publishToMavenLocal")
    }
}

tasks.register("pluginTest") {
    group = "verification"
    description = "Publish consumed SNAPSHOT modules to mavenLocal then run :gbkt-gradle-plugin:test (use instead of :gbkt-gradle-plugin:test to avoid stale-mavenLocal IntegrationTest failures)"
    dependsOn("publishConsumedModulesToMavenLocal")
    dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":test"))
}

// Task to check version consistency across the project
tasks.register("checkVersionConsistency") {
    group = "verification"
    description = "Checks that all version references are consistent"

    doLast {
        val rootVersion = project.property("gbktVersion") as String
        val pluginPropsFile = file("gbkt-gradle-plugin/gradle.properties")
        val pluginProps = java.util.Properties().apply { pluginPropsFile.inputStream().use { load(it) } }
        val pluginVersion = pluginProps.getProperty("gbktVersion")
            ?: error("gbkt-gradle-plugin/gradle.properties missing gbktVersion")
        require(rootVersion == pluginVersion) {
            "Version mismatch: root=$rootVersion, gbkt-gradle-plugin=$pluginVersion"
        }
        println("Version consistency check passed: $rootVersion")
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
                ktfmt("0.62").kotlinlangStyle()
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
                ktfmt("0.62").kotlinlangStyle()
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
