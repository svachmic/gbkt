/**
 * Banks - GBDK banks reference port
 *
 * Demonstrates: multi-bank ROM (MBC5_RAM_BATTERY), BANKED calling convention,
 * cross-bank scene navigation, SRAM persistence via SaveDataBuilder.
 */
plugins {
    kotlin("jvm")
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
    game("io.github.gbkt.examples.banks.BanksKt::banks")
    assets("res")
    outputName.set("banks")
}
