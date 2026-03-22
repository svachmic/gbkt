plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":gbkt-core"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
