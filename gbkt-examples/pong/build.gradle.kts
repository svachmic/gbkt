/**
 * Pong - Minimal gbkt example game
 *
 * Demonstrates: entities, input, collision, variables
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
}

gbkt {
    game("io.github.gbkt.examples.pong.PongKt::pong")
    assets("res")
    outputName.set("pong")
}
