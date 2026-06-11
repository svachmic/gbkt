plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    alias(libs.plugins.spotless)
    alias(libs.plugins.plugin.publish)
}

val licenseHeader = """
    |/* This Source Code Form is subject to the terms of the Mozilla Public
    | * License, v. 2.0. If a copy of the MPL was not distributed with this
    | * file, You can obtain one at https://mozilla.org/MPL/2.0/.
    | *
    | * Copyright (c) 2026 Michal Svacha
    | */
""".trimMargin()

spotless {
    kotlin {
        target("src/**/*.kt")
        licenseHeader(licenseHeader)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

repositories {
    mavenLocal()  // For local development with published gbkt-core
    mavenCentral()
}

val gbktVersion: String = findProperty("gbktVersion")?.toString() ?: "0.1.0"

group = "io.github.gbkt"
version = "$gbktVersion-SNAPSHOT"

dependencies {
    // Use gbkt-core (published to mavenLocal)
    implementation("io.github.gbkt:gbkt-core:$gbktVersion-SNAPSHOT")

    // Embedded emulator for runEmulator and emulatorTest tasks
    implementation("io.github.gbkt:gbkt-emulator:$gbktVersion-SNAPSHOT")

    // Backend API for type-safe access to backend interfaces
    // Note: compileOnly because the actual backend implementation comes from user's classpath
    // at runtime via classloader isolation. This provides IDE support and compile-time checks.
    compileOnly("io.github.gbkt:gbkt-backend-api:$gbktVersion-SNAPSHOT")

    // JSON parsing for source map loading
    implementation(libs.json)

    // Test dependencies
    // Note: gbkt-gradle-plugin uses JUnit 5 directly because GradleTestKit integration tests
    // use JUnit 5 lifecycle annotations (@BeforeEach, @TempDir). kotlin("test") is added for
    // consistency; the explicit junit-jupiter dep satisfies the JUnit 5 API import requirement.
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Backend needed for integration tests (ServiceLoader discovery via withPluginClasspath)
    testImplementation("io.github.gbkt:gbkt-backend-api:$gbktVersion-SNAPSHOT")
    testImplementation("io.github.gbkt:gbkt-backend-gbdk:$gbktVersion-SNAPSHOT")
    // IR types needed for D-01b validation gate unit tests (Plan 12.4-05 Task 2)
    // The production code uses reflection (worker classloader isolation), but the test can use
    // typed GameIR/MetaspriteIR directly since tests run in the plugin's own classloader.
    testImplementation("io.github.gbkt:gbkt-ir:$gbktVersion-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

// ============================================================================
// Plugin Configuration
// ============================================================================

gradlePlugin {
    website.set("https://github.com/svachmic/gbkt")
    vcsUrl.set("https://github.com/svachmic/gbkt.git")

    plugins {
        create("gbkt") {
            id = "io.github.gbkt"
            implementationClass = "io.github.gbkt.gradle.GbktPlugin"
            displayName = "gbkt - Game Boy Kotlin"
            description = "Build Game Boy and Game Boy Color ROMs from Kotlin DSL. " +
                "Provides a type-safe DSL for sprites, scenes, entities, RPG systems, and more."
            tags.set(listOf("game-boy", "gameboy", "gbc", "retro-gaming", "kotlin-dsl", "game-development", "gbdk"))
        }
    }
}

// ============================================================================
// Publishing Configuration
// ============================================================================

publishing {
    publications {
        // Configure the marker publication created by java-gradle-plugin
        withType<MavenPublication> {
            pom {
                name.set("gbkt Gradle Plugin")
                description.set("Build Game Boy and Game Boy Color ROMs from Kotlin DSL")
                url.set("https://github.com/svachmic/gbkt")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://mozilla.org/MPL/2.0/")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("svachmic")
                        name.set("Michal Svacha")
                        url.set("https://github.com/svachmic")
                    }
                }

                scm {
                    url.set("https://github.com/svachmic/gbkt")
                    connection.set("scm:git:git://github.com/svachmic/gbkt.git")
                    developerConnection.set("scm:git:ssh://git@github.com/svachmic/gbkt.git")
                }

                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/svachmic/gbkt/issues")
                }
            }
        }
    }

    repositories {
        // Maven Central via OSSRH (for the plugin jar itself)
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = findProperty("ossrhUsername") as String?
                    ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String?
                    ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

// Configure signing for releases
signing {
    val signingKey = findProperty("signingKey") as String?
        ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signingPassword") as String?
        ?: System.getenv("SIGNING_PASSWORD")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }

    setRequired {
        !version.toString().endsWith("SNAPSHOT") &&
            gradle.taskGraph.hasTask("publishPlugins")
    }
}

// Ensure javadoc and sources are published
java {
    withJavadocJar()
    withSourcesJar()
}
