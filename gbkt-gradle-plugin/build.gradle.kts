plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "8.1.0"
    id("com.gradle.plugin-publish") version "1.3.1"
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
        ktfmt().kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

repositories {
    mavenLocal()  // For local development with published gbkt-core
    mavenCentral()
}

dependencies {
    // Use gbkt-core (published to mavenLocal)
    implementation("io.github.gbkt:gbkt-core:0.1.0-SNAPSHOT")

    // Backend API for type-safe access to backend interfaces
    // Note: compileOnly because the actual backend implementation comes from user's classpath
    // at runtime via classloader isolation. This provides IDE support and compile-time checks.
    compileOnly("io.github.gbkt:gbkt-backend-api:0.1.0-SNAPSHOT")

    // JSON parsing for source map loading
    implementation("org.json:json:20251224")

    // Test dependencies
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
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
