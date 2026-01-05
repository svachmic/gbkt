plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

kotlin {
    // Target JVM for running the compiler/codegen
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Target Native for potential CLI tool
    linuxX64()
    macosX64()
    macosArm64()
    
    sourceSets {
        commonMain.dependencies {
            // No external dependencies - pure Kotlin
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.property)
            implementation(libs.coroutines.test)
        }

        jvmMain.dependencies {
            // JSON parsing for Tiled map files
            implementation(libs.json)
        }
    }
}
