/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SetupClaudeTaskTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-setup-claude"

            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            """
                .trimIndent()
        )

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }
            """
                .trimIndent()
        )
    }

    @Test
    fun `gbktSetupClaude is registered without game configured`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        assertTrue(
            result.output.contains("gbktSetupClaude"),
            "gbktSetupClaude should be registered even without game configured",
        )
    }

    @Test
    fun `gbktSetupClaude installs skill files with correct content`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val skills = listOf("gbkt-play-game.md", "gbkt-test-game.md")
        for (skill in skills) {
            val installed = File(testProjectDir, ".claude/commands/$skill")
            assertTrue(installed.exists(), "$skill should be installed")

            val expected =
                javaClass.classLoader.getResourceAsStream("claude-code/$skill")!!.readBytes()
            assertEquals(
                String(expected),
                installed.readText(),
                "$skill content should match bundled resource byte-for-byte",
            )
        }
    }

    @Test
    fun `gbktSetupClaude writes version marker`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val versionFile = File(testProjectDir, ".claude/.gbkt-version")
        assertTrue(versionFile.exists(), ".gbkt-version should be created")
        // In GradleTestKit, classes are loaded from build/classes/ (not a JAR), so
        // resolvePluginVersion() falls back to "dev". Assert the exact value.
        assertEquals(
            "dev",
            versionFile.readText().trim(),
            ".gbkt-version should contain 'dev' in GradleTestKit (no JAR manifest)",
        )
    }

    @Test
    fun `gbktSetupClaude skips MCP config when JAR not found`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        assertTrue(
            result.output.contains("MCP server JAR not found"),
            "Should warn that MCP JAR was not found",
        )

        // Skills should still be installed even without MCP
        val playSkill = File(testProjectDir, ".claude/commands/gbkt-play-game.md")
        assertTrue(playSkill.exists(), "Skills should still be installed without MCP JAR")
    }

    @Test
    fun `gbktSetupClaude configures MCP when JAR explicitly set`() {
        val fakeJar = File(testProjectDir, "fake-mcp-server.jar")
        fakeJar.writeText("fake jar content")

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            tasks.named<io.github.gbkt.gradle.tasks.SetupClaudeTask>("gbktSetupClaude") {
                mcpServerJar.set(file("fake-mcp-server.jar"))
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val mcpConfig = File(testProjectDir, ".claude/mcp_servers.json")
        assertTrue(mcpConfig.exists(), "mcp_servers.json should be created")

        val content = mcpConfig.readText()
        assertTrue(content.contains("gbkt-emulator"), "Should contain gbkt-emulator entry")
        assertTrue(content.contains("fake-mcp-server.jar"), "Should reference the JAR path")
    }

    @Test
    fun `gbktSetupClaude merges into existing mcp_servers_json`() {
        val claudeDir = File(testProjectDir, ".claude")
        claudeDir.mkdirs()
        File(claudeDir, "mcp_servers.json")
            .writeText(
                """
                {
                  "serena": {
                    "type": "stdio",
                    "command": "serena"
                  }
                }
                """
                    .trimIndent()
            )

        val fakeJar = File(testProjectDir, "fake-mcp-server.jar")
        fakeJar.writeText("fake jar content")

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            tasks.named<io.github.gbkt.gradle.tasks.SetupClaudeTask>("gbktSetupClaude") {
                mcpServerJar.set(file("fake-mcp-server.jar"))
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val content = File(claudeDir, "mcp_servers.json").readText()
        assertTrue(content.contains("serena"), "Pre-existing 'serena' entry should be preserved")
        assertTrue(content.contains("gbkt-emulator"), "gbkt-emulator entry should be added")
    }

    @Test
    fun `gbktSetupClaude overwrites existing gbkt-emulator entry`() {
        val claudeDir = File(testProjectDir, ".claude")
        claudeDir.mkdirs()
        File(claudeDir, "mcp_servers.json")
            .writeText(
                """
                {
                  "gbkt-emulator": {
                    "type": "stdio",
                    "command": "java",
                    "args": ["-jar", "/old/path/mcp-server.jar"]
                  },
                  "serena": {
                    "type": "stdio",
                    "command": "serena"
                  }
                }
                """
                    .trimIndent()
            )

        val fakeJar = File(testProjectDir, "fake-mcp-server.jar")
        fakeJar.writeText("fake jar content")

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            tasks.named<io.github.gbkt.gradle.tasks.SetupClaudeTask>("gbktSetupClaude") {
                mcpServerJar.set(file("fake-mcp-server.jar"))
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val content = File(claudeDir, "mcp_servers.json").readText()
        assertTrue(content.contains("serena"), "Pre-existing 'serena' entry should be preserved")
        assertTrue(
            content.contains("fake-mcp-server.jar"),
            "gbkt-emulator should reference the new JAR",
        )
        assertFalse(
            content.contains("/old/path/mcp-server.jar"),
            "Old gbkt-emulator JAR path should be replaced",
        )
    }

    @Test
    fun `gbktSetupClaude backs up invalid JSON`() {
        val claudeDir = File(testProjectDir, ".claude")
        claudeDir.mkdirs()
        File(claudeDir, "mcp_servers.json").writeText("not valid json {{{")

        val fakeJar = File(testProjectDir, "fake-mcp-server.jar")
        fakeJar.writeText("fake jar content")

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            tasks.named<io.github.gbkt.gradle.tasks.SetupClaudeTask>("gbktSetupClaude") {
                mcpServerJar.set(file("fake-mcp-server.jar"))
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        val backup = File(claudeDir, "mcp_servers.json.bak")
        assertTrue(backup.exists(), "Backup file should be created for invalid JSON")
        assertEquals(
            "not valid json {{{",
            backup.readText(),
            "Backup should contain original content",
        )

        val mcpConfig = File(claudeDir, "mcp_servers.json")
        assertTrue(mcpConfig.readText().contains("gbkt-emulator"), "Fresh config should be written")
    }

    @Test
    fun `gbktSetupClaude cleans up old skill names`() {
        val commandsDir = File(testProjectDir, ".claude/commands")
        commandsDir.mkdirs()
        File(commandsDir, "play.md").writeText("old play skill")
        File(commandsDir, "test-game.md").writeText("old test-game skill")

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":gbktSetupClaude")?.outcome)

        assertFalse(File(commandsDir, "play.md").exists(), "Old play.md should be removed")
        assertFalse(
            File(commandsDir, "test-game.md").exists(),
            "Old test-game.md should be removed",
        )
        assertTrue(File(commandsDir, "gbkt-play-game.md").exists(), "New skill should be installed")
    }

    @Test
    fun `gbktSetupClaude always runs and is never UP-TO-DATE`() {
        // First run
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("gbktSetupClaude")
            .withPluginClasspath()
            .build()

        // Second run — should NOT be UP-TO-DATE
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("gbktSetupClaude")
                .withPluginClasspath()
                .build()

        val outcome = result.task(":gbktSetupClaude")?.outcome
        assertNotEquals(
            TaskOutcome.UP_TO_DATE,
            outcome,
            "Task must never be UP-TO-DATE (no @Output annotations)",
        )
        assertEquals(TaskOutcome.SUCCESS, outcome, "Task should execute again, was: $outcome")
    }

    @Test
    fun `version staleness warning when versions differ`() {
        // In GradleTestKit, resolvePluginVersion() returns "dev" (no JAR manifest).
        // Writing "0.0.1-old" guarantees a mismatch ("0.0.1-old" != "dev") and triggers the
        // warning.
        val claudeDir = File(testProjectDir, ".claude")
        claudeDir.mkdirs()
        File(claudeDir, ".gbkt-version").writeText("0.0.1-old")

        // Trigger afterEvaluate by running any task (--warn to capture warnings)
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--warn")
                .withPluginClasspath()
                .build()

        assertTrue(
            result.output.contains("Claude Code skills are outdated"),
            "Should warn about outdated skills. Output:\n${result.output}",
        )
    }

    @Test
    fun `no version warning when marker matches resolved version`() {
        // In GradleTestKit, resolvePluginVersion() returns "dev". Writing "dev" to the marker
        // means versions match — no staleness warning should fire.
        val claudeDir = File(testProjectDir, ".claude")
        claudeDir.mkdirs()
        File(claudeDir, ".gbkt-version").writeText("dev")

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--warn")
                .withPluginClasspath()
                .build()

        assertFalse(
            result.output.contains("Claude Code skills are outdated"),
            "Should not warn when marker matches resolved version ('dev')",
        )
    }

    @Test
    fun `no version warning when marker absent`() {
        // No .gbkt-version file exists
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--warn")
                .withPluginClasspath()
                .build()

        assertFalse(
            result.output.contains("Claude Code skills are outdated"),
            "Should not warn when no version marker exists",
        )
    }
}
