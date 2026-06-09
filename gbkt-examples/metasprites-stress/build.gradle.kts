/**
 * MetaspritesStress — synthetic codegen-verification ROM (Phase 10.1 D-06 / D-07).
 *
 * THROWAWAY. NOT a user-facing example. Forces composition of the 4 latent-cluster defects
 * (CR-01 actor+metasprite VRAM coexistence, CR-02 per-bank include, CR-03 distinct
 * metasprite symbol namespacing, WR-05 multi-metasprite-per-frame hiwater scope) + the 2
 * absorbed warnings (WR-01 distinct var-ref parameterization, WR-02 game.h extern) so that
 * the SDCC link of the composed output is the binding integration evidence the JVM
 * emission tests cannot produce.
 *
 * See: gbkt-examples/metasprites-stress/README.md
 *      .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-CONTEXT.md
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
    game("io.github.gbkt.examples.metasprites_stress.MetaspritesStressKt::metaspritesStress")
    assets("res")
    outputName.set("metasprites-stress")
}
