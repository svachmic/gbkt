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
    // Transitively exposes gbkt-core (GameIR, TargetProfile, IR types, etc.)
    api(project(":gbkt-backend-api"))

    // JSON serialization for optimization report output
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))

    // Test-only dep on gbkt-genre-sport for RacingValidationPassTest fixtures.
    // Plan 07.4-06 (D-04 / D-05 / D-06 / D-08 / D-10) — the test exercises the
    // pass against real RacingConfig / Vehicle / WaypointDef / AiVehicleSlot
    // fixtures. The PRODUCTION pass (RacingValidationPass.kt) reads these via
    // reflection on the GenericSystem.config map-typed payload — there is NO
    // compile-time dep from gbkt-analysis main on gbkt-genre-sport, which would
    // create the cycle gbkt-analysis -> gbkt-genre-sport -> gbkt-backend-gbdk
    // -> gbkt-analysis (gbkt-backend-gbdk depends on gbkt-analysis).
    testImplementation(project(":gbkt-genre-sport"))
}

// ── Module Boundary Notes ─────────────────────────────────────────────────────
// gbkt-analysis sits above the core layer (depends on gbkt-backend-api, which
// transitively includes all core modules). There are no upward boundary
// constraints needed here — it intentionally consumes all lower layers.
// The testImplementation(":gbkt-genre-sport") dep above is test-scoped only —
// gbkt-analysis main src never references gbkt-genre-sport; cross-module data
// is read via the GenericSystem.config["sport_racing"] map-typed payload.
