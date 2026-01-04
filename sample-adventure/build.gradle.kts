plugins {
    kotlin("jvm")
    id("io.github.gbkt")  // gbkt Gradle plugin
    application           // Keep for standalone testing
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":gbkt-core"))
}

// gbkt plugin configuration
gbkt {
    game("sample.AdventureGameKt::adventureGame")
    assets("assets")
    outputName.set("adventure")
    gbcMode.set("COMPATIBLE")  // GBC compatible mode
}

// Keep application plugin for testing/debugging code generation
application {
    mainClass.set("sample.AdventureGameKt")
}

tasks.named<JavaExec>("run") {
    args = project.findProperty("gameArgs")?.toString()?.split(" ") ?: emptyList()
}
