plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // JSON parsing for Tiled map files
    implementation(libs.json)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    testImplementation(libs.coroutines.test)
}
