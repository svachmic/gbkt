/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "io.github.gbkt"
version = findProperty("gbktVersion")?.toString() ?: "0.1.0"

val intellijVersion = "2024.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(intellijVersion)
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = "gbkt"
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

// Coverage-only Sonar exclusion: this module is excluded from the kover merge by
// design (IDE sandbox test runtime — see root build.gradle.kts koverAggregatedModules),
// so SonarCloud would otherwise count every line here as uncovered. Bugs, smells, and
// hotspots are still scanned. Remove when SEED-019 (IntelliJ Platform test framework
// coverage) lands.
extensions.configure<org.sonarqube.gradle.SonarExtension> {
    properties {
        property("sonar.coverage.exclusions", "**/*")
    }
}
