/**
 * Metasprites - GBDK metasprites reference port
 *
 * Demonstrates: metasprite primitive, spritePalette, GBC_COMPATIBLE target, bgFillCheckerboard
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform(project(":gbkt-bom")))
    implementation(project(":gbkt-backend-gbdk"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}

gbkt {
    game("io.github.gbkt.examples.metasprites.MetaspritesKt::metasprites")
    assets("res")
    outputName.set("metasprites")
}
