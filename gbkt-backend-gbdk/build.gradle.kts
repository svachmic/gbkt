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

gbktPublishing {
    artifactId.set("gbkt-backend-gbdk")
    description.set("gbkt GBDK Backend - Game Boy / Game Boy Color code generation using GBDK")
}

dependencies {
    // Implements the backend API - use api() to expose gbkt-core transitively to consumers
    api(project(":gbkt-backend-api"))

    // Analysis pipeline: provides DefaultPipeline, PassContext, BudgetAuditPass, etc.
    api(project(":gbkt-analysis"))

    // Engine module: provides shared PickupSystemConfig, PickupDef, PickupZone for pickup codegen
    implementation(project(":gbkt-engine"))

    // RPG genre package: provides CombatStats, CharacterDef, ExpCurve for RpgVisitor dispatch
    implementation(project(":gbkt-genre-rpg"))

    // JSON parsing for Tiled map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))

    // Plan 07.4-19 (TDD-RED, scene-aware codegen tests):
    // ScreenClearSceneAwareTest and PrintOpSceneAwareTest exercise the racer's exact code
    // path (SportVisitor's enterOps splice into race_enter), so the genre plugin must be on
    // the test classpath for the ServiceLoader to discover it. Test-scoped only — production
    // code never depends on a genre module. Same pattern used in gbkt-analysis (see
    // gbkt-analysis/build.gradle.kts:35).
    testImplementation(project(":gbkt-genre-sport"))

    // Plan 12-09b (D-16 invariant #1 + #5 — JVM-tier shape-lock tests):
    // TitleSceneEmissionTest + LevelSwitchEmissionTest exercise platformerPhysics-gated
    // pipeline emission paths (buildSetupCurrentLevelFunctionIfNeeded /
    // buildMainLoopLevelSwitchGuardIfNeeded), which require PlatformerPhysicsConfig +
    // the `platformerPhysics { ... }` DSL extension. Test-scoped only — production code
    // remains genre-agnostic. Mirrors the gbkt-genre-sport precedent above.
    testImplementation(project(":gbkt-genre-platformer"))
}
