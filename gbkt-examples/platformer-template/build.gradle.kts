/**
 * Platformer Template - GBDK platformer_template reference port (Phase 12)
 *
 * Demonstrates: tilemap-collision (D-12), horizontal scroll codegen (D-13), variable-height jump (D-14),
 * multi-tileset bank allocation (D-15), 6-frame metasprite hflip, banked tile-data title + NextLevel cards,
 * 3-level substrate with level-switch.
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
}

gbkt {
    game("io.github.gbkt.examples.platformer_template.PlatformerTemplateKt::platformerTemplate")
    assets("res")
    outputName.set("platformer-template")
    sprites {
        strictTransparency.set(true)
    }
}
