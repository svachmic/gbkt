/**
 * Racer - gbkt example game
 *
 * Demonstrates: racing system, camera follow, tilemap scrolling, smooth movement
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
    // gbkt-genre-sport is the genre package for racing/sport DSL builders
    // It is NOT in gbkt-bom (BOM architecture: genre packages are opt-in per game)
    implementation(project(":gbkt-genre-sport"))
    testImplementation(kotlin("test"))
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
    game("io.github.gbkt.examples.racer.RacerKt::racer")
    assets("res")
    outputName.set("racer")
}
