/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */

plugins {
    `java-platform`
    `maven-publish`
    signing
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        // Core library - DSL, IR, all game constructs
        api(project(":gbkt-core"))

        // Backend API - for custom backend implementations
        api(project(":gbkt-backend-api"))

        // GBDK Backend - Game Boy / Game Boy Color code generation
        api(project(":gbkt-backend-gbdk"))
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "gbkt-bom"

            pom {
                name.set("gbkt-bom")
                description.set("gbkt Bill of Materials - Version coordinator for all gbkt modules")
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
            gradle.taskGraph.hasTask("publishToOSSRH")
    }
}
