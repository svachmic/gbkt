plugins {
    alias(libs.plugins.kotlin.jvm)
    id("gbkt.publishing")
}

kotlin {
    jvmToolchain(21)
}

gbktPublishing {
    artifactId.set("gbkt-core")
    description.set("gbkt Core - Kotlin DSL, IR, and game constructs for Game Boy development")
}

dependencies {
    // Re-export layered modules for backward compatibility.
    // Consumers that depend on gbkt-core transitively see all v2 types.
    api(project(":gbkt-ir"))
    api(project(":gbkt-lang"))
    api(project(":gbkt-engine"))
    api(project(":gbkt-world"))

    // JSON parsing for Tiled map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    testImplementation(libs.coroutines.test)

    // Backend for test code generation
    testImplementation(project(":gbkt-backend-gbdk"))
}
