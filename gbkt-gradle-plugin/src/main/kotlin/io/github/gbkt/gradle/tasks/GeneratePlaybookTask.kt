/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.json.JSONObject

/**
 * Generates a `PLAYBOOK.md` skeleton from `game_metadata.json`.
 *
 * The generated file is co-located in the game project root (not in `build/`) so that developers
 * can edit it alongside source code. Once the file exists it is never overwritten — the task is
 * idempotent with respect to human-authored content.
 *
 * ## Generated Output
 *
 * ```markdown
 * # GameName
 *
 * ## Overview
 * <!-- Describe what this game is in 2-3 sentences -->
 *
 * ## How to Play
 * <!-- Describe core mechanics and goals -->
 *
 * ## Controls
 * | Scene | Button | Effect |
 * |-------|--------|--------|
 * | game  | UP     | held   |
 *
 * ## Scene Flow
 * - title -> game
 * - game -> gameover
 *
 * ## Win / Lose Conditions
 * <!-- Describe winning and losing -->
 *
 * ## Known Quirks
 * <!-- Document any game-specific quirks or edge cases -->
 *
 * ## Variables Reference
 * | Variable | Type | Semantic | Description |
 * |----------|------|----------|-------------|
 * | score | U8 | score | <!-- describe --> |
 * ```
 */
@CacheableTask
abstract class GeneratePlaybookTask @Inject constructor() : DefaultTask() {

    /** Input metadata JSON file produced by `generateC`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    /**
     * Output `PLAYBOOK.md` file. Set to `project.file("PLAYBOOK.md")` so it lands in the game
     * project root alongside source files — not in `build/`. Developers edit this file.
     */
    @get:OutputFile abstract val outputFile: RegularFileProperty

    /** Human-readable game name used as the top-level heading. */
    @get:Input abstract val gameName: Property<String>

    init {
        description = "Generate PLAYBOOK.md skeleton from game metadata for LLM agent consumption"
        group = "gbkt"
    }

    @TaskAction
    fun generate() {
        val metadata = metadataFile.get().asFile
        if (!metadata.exists()) {
            logger.warn(
                "Metadata file not found: ${metadata.absolutePath} — skipping playbook generation"
            )
            return
        }

        val json = JSONObject(metadata.readText())
        val name = gameName.get()

        val playbook = buildString {
            appendLine("# $name")
            appendLine()
            appendLine("## Overview")
            appendLine("<!-- Describe what this game is in 2-3 sentences -->")
            appendLine()
            appendLine("## How to Play")
            appendLine("<!-- Describe core mechanics and goals -->")
            appendLine()

            // Controls from enriched metadata — sorted by scene name for stable output
            appendLine("## Controls")
            val controls = json.optJSONObject("controls")
            if (controls != null) {
                appendLine("| Scene | Button | Effect |")
                appendLine("|-------|--------|--------|")
                for (scene in controls.keys().asSequence().sorted()) {
                    val buttons = controls.optJSONArray(scene) ?: continue
                    for (i in 0 until buttons.length()) {
                        val mapping = buttons.getJSONObject(i)
                        val button = mapping.optString("button", "?")
                        val type = mapping.optString("type", "?")
                        appendLine("| $scene | $button | $type |")
                    }
                }
            } else {
                appendLine("<!-- No control mappings found in metadata -->")
            }
            appendLine()

            // Scene flow from transitions
            appendLine("## Scene Flow")
            val transitions = json.optJSONArray("transitions")
            if (transitions != null && transitions.length() > 0) {
                for (i in 0 until transitions.length()) {
                    val t = transitions.getJSONObject(i)
                    val from = t.optString("from", "?")
                    val to = t.optString("to", "?")
                    appendLine("- $from -> $to")
                }
            } else {
                appendLine("<!-- No transitions found in metadata -->")
            }
            appendLine()

            appendLine("## Win / Lose Conditions")
            appendLine("<!-- Describe winning and losing -->")
            appendLine()

            appendLine("## Known Quirks")
            appendLine("<!-- Document any game-specific quirks or edge cases -->")
            appendLine()

            // Variables from metadata with semantics
            appendLine("## Variables Reference")
            val variables = json.optJSONArray("variables")
            if (variables != null && variables.length() > 0) {
                appendLine("| Variable | Type | Semantic | Description |")
                appendLine("|----------|------|----------|-------------|")
                for (i in 0 until variables.length()) {
                    val v = variables.getJSONObject(i)
                    val varName = v.optString("name", "?")
                    val varType = v.optString("type", "?")
                    val semantic = v.optString("semantic", "unknown")
                    appendLine("| $varName | $varType | $semantic | <!-- describe --> |")
                }
            } else {
                appendLine("<!-- No variables found in metadata -->")
            }
        }

        val output = outputFile.get().asFile
        // Only write if file does not already exist — never overwrite human-authored content
        if (!output.exists()) {
            output.parentFile?.mkdirs()
            output.writeText(playbook)
            logger.lifecycle("Generated playbook: ${output.absolutePath}")
        } else {
            logger.lifecycle("Playbook already exists — skipping: ${output.absolutePath}")
        }
    }
}
