/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli

import io.github.gbkt.cli.templates.MinimalTemplate
import io.github.gbkt.cli.templates.PlatformerTemplate
import io.github.gbkt.cli.templates.PuzzleTemplate
import io.github.gbkt.cli.templates.RpgTemplate
import io.github.gbkt.cli.templates.commonBuildGradle
import io.github.gbkt.cli.templates.commonSettingsGradle
import io.github.gbkt.cli.templates.gbktVersion
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TemplateTest {

    @Test
    fun `commonBuildGradle contains project name in outputName`() {
        val content = commonBuildGradle("my-game")
        assertTrue(
            content.contains("outputName.set(\"my-game\")"),
            "Should contain project name in outputName",
        )
    }

    @Test
    fun `commonBuildGradle contains gbkt plugin`() {
        val content = commonBuildGradle("test")
        assertTrue(content.contains("id(\"io.github.gbkt\")"), "Should reference gbkt plugin")
    }

    @Test
    fun `commonBuildGradle contains gbkt-core dependency`() {
        val content = commonBuildGradle("test")
        assertTrue(
            content.contains("io.github.gbkt:gbkt-core"),
            "Should contain gbkt-core dependency",
        )
    }

    @Test
    fun `commonBuildGradle uses consistent version for plugin and dependency`() {
        val content = commonBuildGradle("test")
        val version = gbktVersion()

        assertTrue(
            content.contains("id(\"io.github.gbkt\") version \"$version\""),
            "Plugin version should match gbktVersion()",
        )
        assertTrue(
            content.contains("io.github.gbkt:gbkt-core:$version"),
            "Dependency version should match gbktVersion()",
        )
    }

    @Test
    fun `commonBuildGradle does not contain hardcoded snapshot version`() {
        val content = commonBuildGradle("test")
        val version = gbktVersion()
        // The version in the template should come from gbktVersion(), not be hardcoded
        // Count occurrences of the version - plugin + dependency = exactly 2
        val versionCount = Regex(Regex.escape(version)).findAll(content).count()
        assertEquals(
            2,
            versionCount,
            "Version '$version' should appear exactly twice (plugin + dependency)",
        )
    }

    @Test
    fun `commonSettingsGradle contains project name`() {
        val content = commonSettingsGradle("my-game")
        assertTrue(
            content.contains("rootProject.name = \"my-game\""),
            "Should contain project name in rootProject.name",
        )
    }

    @Test
    fun `commonSettingsGradle includes mavenLocal repository`() {
        val content = commonSettingsGradle("test")
        assertTrue(
            content.contains("mavenLocal()"),
            "Should include mavenLocal for local development",
        )
    }

    @Test
    fun `minimal template has correct metadata`() {
        assertEquals("minimal", MinimalTemplate.name)
        assertTrue(MinimalTemplate.description.isNotBlank(), "Description should not be blank")
    }

    @Test
    fun `platformer template has correct metadata`() {
        assertEquals("platformer", PlatformerTemplate.name)
        assertTrue(PlatformerTemplate.description.isNotBlank(), "Description should not be blank")
    }

    @Test
    fun `rpg template has correct metadata`() {
        assertEquals("rpg", RpgTemplate.name)
        assertTrue(RpgTemplate.description.isNotBlank(), "Description should not be blank")
    }

    @Test
    fun `puzzle template has correct metadata`() {
        assertEquals("puzzle", PuzzleTemplate.name)
        assertTrue(PuzzleTemplate.description.isNotBlank(), "Description should not be blank")
    }

    @Test
    fun `all templates generate valid Kotlin source`() {
        val templates = listOf(MinimalTemplate, PlatformerTemplate, RpgTemplate, PuzzleTemplate)
        for (template in templates) {
            val gameKt = template.gameKt("test-project")
            assertTrue(
                gameKt.contains("fun main()") || gameKt.contains("fun main("),
                "${template.name} template should generate a main function",
            )
            assertFalse(
                gameKt.contains("|"),
                "${template.name} template should have trimMargin applied (no leading |)",
            )
        }
    }

    @Test
    fun `all templates use commonBuildGradle`() {
        val templates = listOf(MinimalTemplate, PlatformerTemplate, RpgTemplate, PuzzleTemplate)
        val expected = commonBuildGradle("test-project")
        for (template in templates) {
            assertEquals(
                expected,
                template.buildGradle("test-project"),
                "${template.name} should use commonBuildGradle",
            )
        }
    }

    @Test
    fun `all templates use commonSettingsGradle`() {
        val templates = listOf(MinimalTemplate, PlatformerTemplate, RpgTemplate, PuzzleTemplate)
        val expected = commonSettingsGradle("test-project")
        for (template in templates) {
            assertEquals(
                expected,
                template.settingsGradle("test-project"),
                "${template.name} should use commonSettingsGradle",
            )
        }
    }

    @Test
    fun `gbktVersion returns non-empty string`() {
        val version = gbktVersion()
        assertTrue(version.isNotBlank(), "gbktVersion should return a non-empty string")
    }
}
