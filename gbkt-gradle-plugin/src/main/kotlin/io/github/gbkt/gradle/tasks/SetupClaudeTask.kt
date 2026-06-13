/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Installs/updates Claude Code skills and MCP server configuration for gbkt.
 *
 * Writes:
 * - `.claude/commands/gbkt-play-game.md` — interactive game play skill
 * - `.claude/commands/gbkt-test-game.md` — automated game verification skill
 * - `.claude/mcp_servers.json` — MCP server config (merged, not replaced)
 * - `.claude/.gbkt-version` — version marker for staleness detection
 */
@DisableCachingByDefault(
    because = "Claude setup task modifies project config files — cannot be cached"
)
abstract class SetupClaudeTask @Inject constructor() : DefaultTask() {

    @get:Internal abstract val pluginVersion: Property<String>

    @get:Internal abstract val claudeDir: DirectoryProperty

    @get:Internal abstract val headedMode: Property<Boolean>

    @get:Internal abstract val mcpServerJar: RegularFileProperty

    init {
        description = "Install/update Claude Code skills and MCP server configuration for gbkt"
        group = "gbkt"
    }

    @TaskAction
    fun setup() {
        val claudeRoot = claudeDir.get().asFile
        claudeRoot.mkdirs()

        installSkills(claudeRoot)
        configureMcpServer(claudeRoot)
        writeVersionMarker(claudeRoot)
    }

    private fun installSkills(claudeRoot: File) {
        val commandsDir = File(claudeRoot, "commands")
        commandsDir.mkdirs()

        val skills = listOf("gbkt-play-game.md", "gbkt-test-game.md")
        for (skill in skills) {
            val content =
                javaClass.classLoader.getResourceAsStream("claude-code/$skill")
                    ?: error("Skill resource not found: claude-code/$skill")
            val target = File(commandsDir, skill)
            target.writeBytes(content.readBytes())
            logger.lifecycle("Installed skill: ${target.absolutePath}")
        }

        // Clean up old skill files that have been renamed
        val oldNames = listOf("play.md", "test-game.md")
        for (old in oldNames) {
            val oldFile = File(commandsDir, old)
            if (oldFile.exists()) {
                oldFile.delete()
                logger.lifecycle("Removed superseded skill: ${oldFile.absolutePath}")
            }
        }
    }

    private fun configureMcpServer(claudeRoot: File) {
        val jarFile = discoverMcpJar()
        if (jarFile == null) {
            logger.warn(
                "gbkt: MCP server JAR not found. Skipping MCP config.\n" +
                    "  Build it with: ./gradlew :gbkt-mcp-server:shadowJar"
            )
            return
        }

        val configFile = File(claudeRoot, "mcp_servers.json")
        val config = loadOrCreateConfig(configFile)

        val args = mutableListOf("-jar", jarFile.absolutePath)
        if (headedMode.get()) {
            args.add("--headed")
        }

        val entry = JSONObject()
        entry.put("type", "stdio")
        entry.put("command", "java")
        entry.put("args", args)

        config.put("gbkt-emulator", entry)

        configFile.writeText(config.toString(2) + "\n")
        logger.lifecycle("Configured MCP server: ${configFile.absolutePath}")
        logger.lifecycle("  JAR: ${jarFile.absolutePath}")
    }

    private fun discoverMcpJar(): File? {
        // Explicit property takes priority
        if (mcpServerJar.isPresent) {
            val explicit = mcpServerJar.get().asFile
            if (explicit.exists()) return explicit
            logger.warn("gbkt: Explicit MCP JAR not found: ${explicit.absolutePath}")
            return null
        }

        // Monorepo path: look for shadow JAR in gbkt-mcp-server/build/libs/
        val rootDir = project.rootProject.projectDir
        val libsDir = File(rootDir, "gbkt-mcp-server/build/libs")
        if (libsDir.isDirectory) {
            val jar =
                libsDir
                    .listFiles()
                    ?.filter { it.name.endsWith("-all.jar") }
                    ?.maxByOrNull { it.lastModified() }
            if (jar != null) return jar
        }

        // Downstream: resolve from repositories (works once gbkt-mcp-server is published)
        try {
            val dep =
                project.dependencies.create(
                    "io.github.gbkt:gbkt-mcp-server:${pluginVersion.get()}:all@jar"
                )
            val config = project.configurations.detachedConfiguration(dep)
            config.isTransitive = false
            val resolved = config.resolve().firstOrNull()
            if (resolved != null) return resolved
        } catch (e: Exception) {
            logger.info("gbkt: Could not resolve MCP server from repositories: ${e.message}")
        }

        return null
    }

    private fun loadOrCreateConfig(configFile: File): JSONObject {
        if (!configFile.exists()) return JSONObject()

        val text = configFile.readText().trim()
        if (text.isEmpty()) return JSONObject()

        return try {
            JSONObject(JSONTokener(text))
        } catch (e: Exception) {
            // Invalid JSON — back up and start fresh
            val backup = File(configFile.parentFile, "mcp_servers.json.bak")
            configFile.copyTo(backup, overwrite = true)
            logger.warn(
                "gbkt: Invalid mcp_servers.json — backed up to ${backup.name}, creating fresh config"
            )
            JSONObject()
        }
    }

    private fun writeVersionMarker(claudeRoot: File) {
        val versionFile = File(claudeRoot, ".gbkt-version")
        versionFile.writeText(pluginVersion.get())
        logger.lifecycle("Version marker: ${pluginVersion.get()}")
    }
}
