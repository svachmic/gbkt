/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("gbkt.publishing")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.json)
    testImplementation(kotlin("test"))
}

// ── Module Boundary Enforcement ──────────────────────────────────────────────
// gbkt-ir is the lowest layer. It must not depend on any higher-level gbkt modules.
// This task runs as part of `check` to catch boundary violations during CI.
tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validates that gbkt-ir does not depend on higher-level gbkt modules"

    doLast {
        val forbidden = setOf("gbkt-lang", "gbkt-engine", "gbkt-world", "gbkt-core")
        val violations = mutableListOf<String>()

        configurations.findByName("compileClasspath")
            ?.resolvedConfiguration
            ?.firstLevelModuleDependencies
            ?.forEach { dep ->
                if (dep.moduleName in forbidden) {
                    violations += "${project.name} must not depend on ${dep.moduleName}"
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violations in ${project.name}:\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
        logger.lifecycle("Module boundary check passed for ${project.name}")
    }
}

tasks.named("check") { dependsOn("validateModuleBoundaries") }
