plugins {
    kotlin("jvm")
    id("io.github.gbkt")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":gbkt-core"))
}

gbkt {
    game("modular.ModularGameKt::modularGame")
    assets("src/main/resources/sprites")
    outputName.set("modular")
}

application {
    mainClass.set("modular.ModularGameKt")
}
