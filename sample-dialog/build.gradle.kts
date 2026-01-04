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
    game("sample.DialogGameKt::dialogGame")
    assets("assets")
    outputName.set("dialog")
    gbcMode.set("DMG")  // Classic Game Boy mode
}

// Keep application plugin for testing/debugging code generation
application {
    mainClass.set("sample.DialogGameKt")
}

tasks.named<JavaExec>("run") {
    args = project.findProperty("gameArgs")?.toString()?.split(" ") ?: emptyList()
}
