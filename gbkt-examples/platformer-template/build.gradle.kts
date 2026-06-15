/**
 * Platformer Template - GBDK platformer_template reference port (Phase 12)
 *
 * Demonstrates: tilemap-collision (D-12), horizontal scroll codegen (D-13), variable-height jump (D-14),
 * multi-tileset bank allocation (D-15), 6-frame metasprite hflip, banked tile-data title + NextLevel cards,
 * 3-level substrate with level-switch.
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
    implementation(project(":gbkt-genre-platformer"))
    testImplementation(kotlin("test"))
    testImplementation(project(":gbkt-emulator"))
    testImplementation(project(":gbkt-test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    // Phase 15 F3/F4: PlayerMetaspriteGeometryTest reads the png2asset-native
    // build/gbkt/generated/sprites/player.c (player_metasprite0[]), produced by convertSprites
    // (which depends on generateC). Wire the dependency so the asset is fresh before :test runs
    // on a GBDK-equipped host (the test EXECUTES rather than skips). On a host without GBDK the
    // task fails/skips and the test skips gracefully via Assumptions — a genuine missing
    // prerequisite, not a failure.
    dependsOn("convertSprites")
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}

gbkt {
    game("io.github.gbkt.examples.platformer_template.PlatformerTemplateKt::platformerTemplate")
    assets("res")
    outputName.set("platformer-template")
    // Phase 22 (22-07): gbcMode must be explicit here because the extension convention defaults to
    // "DISABLED" and gbcMode.isPresent is always true, so CompileRomTask never falls back to the
    // gbcMode=COMPATIBLE value written by GenerateCTask into gbkt-build.properties via
    // target(GbcTarget.GBC_COMPATIBLE) in the DSL. Explicit set ensures -Wm-yc is passed to lcc
    // so the ROM 0x143 byte is 0x80 (CGB_ENHANCED), which the D-07 guard in PlatformerTemplateUatTest
    // and related oracle tests checks before blessing any golden screenshot.
    gbcMode.set("COMPATIBLE")
    sprites {
        strictTransparency.set(true)
    }
}
