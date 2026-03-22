/**
 * Platformer (GBC) - gbkt example game
 *
 * Demonstrates: GBC color mode, gravity, jumping, tile collision
 */
plugins {
    kotlin("jvm")
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(project(":gbkt-bom")))
    implementation(project(":gbkt-backend-gbdk"))
    // gbkt-genre-platformer is the genre package for platformer DSL builders (gravity, jump, tile collision)
    // It is NOT in gbkt-bom (BOM architecture: genre packages are opt-in per game)
    implementation(project(":gbkt-genre-platformer"))
    testImplementation(kotlin("test"))
    // gbkt-emulator provides AgentDebugSession and AgentSessionConfig for headless emulator smoke tests
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

gbkt {
    game("io.github.gbkt.examples.platformergbc.PlatformerGbcKt::platformerGbc")
    assets("res")
    outputName.set("platformer-gbc")
}
