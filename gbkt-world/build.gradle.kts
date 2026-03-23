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
    // World and exploration types depend on IR types for floor/zone/encounter IR nodes
    api(project(":gbkt-ir"))
    // DSL builders for world/exploration require recording context from gbkt-lang
    api(project(":gbkt-lang"))
}

// ── Module Boundary Enforcement ──────────────────────────────────────────────
// gbkt-world depends on gbkt-ir and gbkt-lang. It must not depend on gbkt-engine
// (which is a sibling) or gbkt-core (which is the aggregator).
// This task runs as part of `check` to catch boundary violations during CI.
tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validates that gbkt-world does not depend on sibling or aggregator gbkt modules"

    doLast {
        val forbidden = setOf("gbkt-engine", "gbkt-core")
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
