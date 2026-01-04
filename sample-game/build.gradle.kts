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
    game("sample.RunnerGameKt::runnerGame")
    assets("src/main/resources/sprites")
    outputName.set("runner")
    gbcMode.set("COMPATIBLE")  // Build for Game Boy Color!
}

// Keep application plugin for testing/debugging code generation
application {
    mainClass.set("sample.RunnerGameKt")
}

tasks.named<JavaExec>("run") {
    args = project.findProperty("gameArgs")?.toString()?.split(" ") ?: emptyList()
}
