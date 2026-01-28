plugins {
    kotlin("jvm")
    id("gbkt.publishing")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    jvmToolchain(21)
}

gbktPublishing {
    artifactId.set("gbkt-core")
    description.set("gbkt Core - Kotlin DSL, IR, and game constructs for Game Boy development")
}

dependencies {
    // JSON parsing for Tiled map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    testImplementation(libs.coroutines.test)

    // Backend for test code generation
    testImplementation(project(":gbkt-backend-gbdk"))
}
