/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli.templates

/** Base interface for project templates. */
interface Template {
    /** Template name (e.g., "minimal", "platformer") */
    val name: String

    /** Short description of what this template provides */
    val description: String

    /** Generate build.gradle.kts content */
    fun buildGradle(projectName: String): String

    /** Generate settings.gradle.kts content */
    fun settingsGradle(projectName: String): String

    /** Generate the main Game.kt file */
    fun gameKt(projectName: String): String
}

/** Common build.gradle.kts template used by all project types */
fun commonBuildGradle(projectName: String): String =
    """
    |plugins {
    |    kotlin("jvm") version "2.3.0"
    |    id("io.github.gbkt") version "0.1.0-SNAPSHOT"
    |}
    |
    |group = "com.example"
    |version = "1.0.0"
    |
    |repositories {
    |    mavenLocal()
    |    mavenCentral()
    |}
    |
    |dependencies {
    |    implementation("io.github.gbkt:gbkt-core-jvm:0.1.0-SNAPSHOT")
    |}
    |
    |gbkt {
    |    // Game Boy ROM configuration
    |    gameName = "$projectName"
    |
    |    // Path to GBDK-2020 installation (set via environment variable or here)
    |    // gbdkPath = "/opt/gbdk"
    |
    |    // Optional: specify emulator for 'runEmulator' task
    |    // emulatorPath = "/usr/bin/mgba"
    |}
    """
        .trimMargin()

/** Common settings.gradle.kts template */
fun commonSettingsGradle(projectName: String): String =
    """
    |rootProject.name = "$projectName"
    |
    |pluginManagement {
    |    repositories {
    |        mavenLocal()
    |        gradlePluginPortal()
    |    }
    |}
    """
        .trimMargin()
