plugins {
    alias(libs.plugins.kotlin.jvm)
    id("gbkt.publishing")
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
