rootProject.name = "gbkt"

pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.0"
        id("com.diffplug.spotless") version "8.1.0"
        id("io.gitlab.arturbosch.detekt") version "1.23.8"
        id("org.sonarqube") version "7.2.2.6593"
        id("org.jetbrains.kotlinx.kover") version "0.9.4"
        id("com.gradle.plugin-publish") version "1.3.1"
    }
    repositories {
        mavenLocal()  // For local development
        mavenCentral()  // For kotlinx.atomicfu and other plugins
        gradlePluginPortal()
    }
    includeBuild("gbkt-gradle-plugin")
}

// Core library modules
include("gbkt-core")         // DSL, IR, all game constructs (platform-agnostic)
include("gbkt-backend-api")  // Backend contract (CodegenBackend interface)
include("gbkt-backend-gbdk") // GB/GBC codegen (implements backend-api)

// Version coordinator
include("gbkt-bom")

// CLI tool
include("gbkt-cli")

// IDE plugin
include("gbkt-intellij-plugin")

// Example game - Labyrinth of the Dragon port
include("LabyrinthOfTheDragon-port")
