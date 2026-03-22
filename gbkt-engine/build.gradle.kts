/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":gbkt-lang"))
}

// ── Module Boundary Enforcement ──────────────────────────────────────────────
// gbkt-engine depends on gbkt-ir and gbkt-lang (via transitive). It must not
// depend on gbkt-world (which is a peer) or gbkt-core (which aggregates all).
// This task runs as part of `check` to catch boundary violations during CI.
tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validates that gbkt-engine does not depend on peer or aggregator gbkt modules"

    doLast {
        val forbidden = setOf("gbkt-world", "gbkt-core")
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
