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
include("gbkt-ir")           // v2 IR types (ScriptOp, Expr, SystemIR, GameIR — pure data, zero external deps)
include("gbkt-lang")         // v2 DSL recording (GameBuilder, ScriptBuilder, variable delegates)
include("gbkt-engine")       // v2 engine constructs (scenes, actors, input, graphics — foundation module)
include("gbkt-world")        // World and exploration types (floor, zone, encounter, dungeon crawling)
include("gbkt-core")         // DSL, IR, all game constructs (platform-agnostic)
include("gbkt-backend-api")  // Backend contract (CodegenBackend interface)
include("gbkt-backend-gbdk") // GB/GBC codegen (implements backend-api)
include("gbkt-analysis")     // Compiler analysis passes (resource allocation, validation)
include("gbkt-emulator")     // Embedded emulator for debug loop

// Genre packages (BOM architecture — DSL ergonomics on top of core IR)
include("gbkt-genre-rpg")         // RPG genre package (renamed from gbkt-rpg)
include("gbkt-genre-platformer")  // Platformer genre package
include("gbkt-genre-puzzle")      // Puzzle genre package (match-3, block-push)
include("gbkt-genre-sport")       // Sport and racing genre package

// Convenience meta-module (aggregates all published modules for single-artifact import)
include("gbkt-all")

// Version coordinator
include("gbkt-bom")

// CLI tool
include("gbkt-cli")

// MCP server for AI agent game testing
include("gbkt-mcp-server")

// Test infrastructure (JUnit5 extension, assertions, recipes)
include("gbkt-test")

// IDE plugin
include("gbkt-intellij-plugin")

// Example games
include("gbkt-examples:pong")
include("gbkt-examples:breakout")
include("gbkt-examples:explorer")
include("gbkt-examples:rpg-lite")
include("gbkt-examples:dungeon")
include("gbkt-examples:platformer")
include("gbkt-examples:platformer-gbc")
include("gbkt-examples:shmup")
include("gbkt-examples:racer")

// Reference RPG implementation
include("LabyrinthOfTheDragon-port")
