/**
 * Dungeon - gbkt example game
 *
 * Demonstrates: zones, exploration, encounters, flags, camera, save/load, RPG combat
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
    game("io.github.gbkt.examples.dungeon.DungeonKt::dungeon")
    assets("res")
    outputName.set("dungeon")
}
