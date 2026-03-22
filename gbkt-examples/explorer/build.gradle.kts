/**
 * Explorer - gbkt example game
 *
 * Demonstrates: zones, camera, save/load, 4-directional movement, RPG combat (gbkt-genre-rpg)
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
    // gbkt-genre-rpg is the genre package for RPG DSL builders (character, monster, simpleBattle)
    // It is NOT in gbkt-bom (BOM architecture: genre packages are opt-in per game)
    implementation(project(":gbkt-genre-rpg"))
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
    game("io.github.gbkt.examples.explorer.ExplorerV2Kt::explorerV2")
    assets("res")
    outputName.set("explorer")
}
