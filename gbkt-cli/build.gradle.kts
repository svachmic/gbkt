plugins {
    kotlin("multiplatform")
}

kotlin {
    // Native targets for CLI executable
    linuxX64 {
        binaries {
            executable {
                entryPoint = "io.github.gbkt.cli.main"
                baseName = "gbkt"
            }
        }
    }

    macosX64 {
        binaries {
            executable {
                entryPoint = "io.github.gbkt.cli.main"
                baseName = "gbkt"
            }
        }
    }

    macosArm64 {
        binaries {
            executable {
                entryPoint = "io.github.gbkt.cli.main"
                baseName = "gbkt"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            // Pure Kotlin - no external dependencies needed for CLI
        }

        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Task to copy the executable to a convenient location
tasks.register("installCli") {
    group = "build"
    description = "Build and install the CLI executable"

    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val targetName = when {
        os.contains("linux") -> "linuxX64"
        os.contains("mac") && arch.contains("aarch64") -> "macosArm64"
        os.contains("mac") -> "macosX64"
        else -> throw GradleException("Unsupported platform: $os $arch")
    }

    dependsOn("linkReleaseExecutable${targetName.replaceFirstChar { it.uppercase() }}")

    doLast {
        val executableDir = layout.buildDirectory.dir("bin/$targetName/releaseExecutable").get().asFile
        val executable = executableDir.listFiles()?.find { it.name == "gbkt.kexe" || it.name == "gbkt" }
        if (executable != null) {
            println("CLI executable built at: ${executable.absolutePath}")
            println("To install globally, run:")
            println("  sudo cp ${executable.absolutePath} /usr/local/bin/gbkt")
        }
    }
}
