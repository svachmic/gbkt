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
    // Phase 22 (22-06 gap): gbcMode must be explicit here for the same reason as platformer-template
    // (22-07) — the extension convention defaults to "DISABLED" and gbcMode.isPresent is always true,
    // so CompileRomTask never falls back to the gbcMode=COMPATIBLE value derived from
    // target(GbcTarget.GBC_COMPATIBLE) in the DSL. Without this, the ROM 0x143 byte stays 0x00 (DMG)
    // and the D-07 guard in Phase19VisualEvidenceTest / MetaspritePhase20OracleTest aborts before
    // blessing any golden. Explicit set makes lcc emit -Wm-yc → 0x143 = 0x80 (CGB_ENHANCED).
    gbcMode.set("COMPATIBLE")
}
