/**
 * SimplePhysics - GBDK simple_physics reference port
 *
 * Demonstrates: i16Var, signed comparison, sub-pixel physics (12.4 fixed-point), D-pad/A input
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
    game("io.github.gbkt.examples.simple_physics.SimplePhysicsKt::simplePhysics")
    assets("res")
    outputName.set("simple-physics")
}
