/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

/**
 * Convention plugin for Maven Central publishing.
 *
 * Applies:
 * - maven-publish plugin
 * - signing plugin (for release builds)
 * - Configures POM metadata (license, developers, SCM)
 * - Configures OSSRH/Maven Central repository
 *
 * Required environment variables for release:
 * - OSSRH_USERNAME: Sonatype OSSRH username
 * - OSSRH_PASSWORD: Sonatype OSSRH password or token
 * - SIGNING_KEY: GPG private key (armored)
 * - SIGNING_PASSWORD: GPG key passphrase
 *
 * See: https://central.sonatype.org/publish/publish-gradle/
 */

plugins {
    `maven-publish`
    signing
}

// Extension for configuring publication-specific settings
interface GbktPublishingExtension {
    val artifactId: Property<String>
    val description: Property<String>
}

val gbktPublishing = extensions.create<GbktPublishingExtension>("gbktPublishing")

// Set defaults
gbktPublishing.artifactId.convention(project.name)
gbktPublishing.description.convention("gbkt - Game Boy Kotlin DSL framework")

// Configure after evaluation to pick up extension values
afterEvaluate {
    publishing {
        publications {
            // Only create if not already created (e.g., by java-platform)
            publications.findByName("maven") ?: create<MavenPublication>("maven") {
                // For regular JVM projects, publish from java component
                if (components.findByName("java") != null) {
                    from(components["java"])
                }
            }

            withType<MavenPublication> {
                artifactId = gbktPublishing.artifactId.get()

                pom {
                    name.set(gbktPublishing.artifactId.get())
                    description.set(gbktPublishing.description.get())
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
            // Maven Central via OSSRH
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

            // GitHub Packages (alternative)
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/svachmic/gbkt")
                credentials {
                    username = findProperty("gpr.user") as String?
                        ?: System.getenv("GITHUB_ACTOR")
                    password = findProperty("gpr.key") as String?
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    // Configure signing for releases
    signing {
        // Only sign release versions and when credentials are available
        val signingKey = findProperty("signingKey") as String?
            ?: System.getenv("SIGNING_KEY")
        val signingPassword = findProperty("signingPassword") as String?
            ?: System.getenv("SIGNING_PASSWORD")

        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }

        // Skip signing for SNAPSHOT versions
        setRequired {
            !version.toString().endsWith("SNAPSHOT") &&
                gradle.taskGraph.hasTask("publishToOSSRH")
        }
    }
}

// Ensure javadoc and sources are published
plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
    }
}
