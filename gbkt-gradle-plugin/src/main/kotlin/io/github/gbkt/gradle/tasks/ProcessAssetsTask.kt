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
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Incrementally process sprite assets.
 *
 * Only reprocesses files that have changed since the last build, using Gradle's incremental task
 * API.
 */
@CacheableTask
abstract class ProcessAssetsTask @Inject constructor() : DefaultTask() {

    /**
     * Input directory containing sprite assets. Marked as @Incremental for incremental processing.
     */
    @get:Incremental
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /** Output directory for processed tile data. */
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    /** Manifest file tracking processed assets. */
    @get:OutputFile abstract val manifestFile: RegularFileProperty

    init {
        description = "Process sprite assets incrementally"
        group = "gbkt"
    }

    @TaskAction
    fun processAssets(inputChanges: InputChanges) {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val manifest = loadManifest()

        if (inputChanges.isIncremental) {
            logger.lifecycle("Incremental asset processing...")
            processIncrementally(inputChanges, outDir, manifest)
        } else {
            logger.lifecycle("Full asset processing...")
            processAll(outDir, manifest)
        }

        saveManifest(manifest)
    }

    private fun processIncrementally(
        inputChanges: InputChanges,
        outDir: File,
        manifest: MutableMap<String, AssetEntry>
    ) {
        inputChanges.getFileChanges(assetDirectory).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach

            val file = change.file
            if (!file.name.endsWith(".png")) return@forEach

            when (change.changeType) {
                ChangeType.ADDED,
                ChangeType.MODIFIED -> {
                    logger.lifecycle("  Processing: ${file.name}")
                    processSprite(file, outDir, manifest)
                }
                ChangeType.REMOVED -> {
                    logger.lifecycle("  Removing: ${file.name}")
                    removeProcessedSprite(file, outDir, manifest)
                }
            }
        }
    }

    private fun processAll(outDir: File, manifest: MutableMap<String, AssetEntry>) {
        manifest.clear()

        val assetDir = assetDirectory.get().asFile
        if (!assetDir.exists()) {
            logger.lifecycle("  No asset directory found: ${assetDir.absolutePath}")
            return
        }

        assetDir
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".png") }
            .forEach { file ->
                logger.lifecycle("  Processing: ${file.name}")
                processSprite(file, outDir, manifest)
            }
    }

    private fun processSprite(file: File, outDir: File, manifest: MutableMap<String, AssetEntry>) {
        // Create a marker file to indicate this sprite was processed
        // The actual tile conversion happens in GenerateCTask via AssetPipeline
        // This task just tracks what needs processing

        val markerFile = File(outDir, "${file.nameWithoutExtension}.processed")
        markerFile.writeText(file.lastModified().toString())

        manifest[file.name] =
            AssetEntry(
                name = file.nameWithoutExtension,
                hash = file.readBytes().contentHashCode(),
                lastModified = file.lastModified()
            )
    }

    private fun removeProcessedSprite(
        file: File,
        outDir: File,
        manifest: MutableMap<String, AssetEntry>
    ) {
        val markerFile = File(outDir, "${file.nameWithoutExtension}.processed")
        if (markerFile.exists()) {
            markerFile.delete()
        }
        manifest.remove(file.name)
    }

    private fun loadManifest(): MutableMap<String, AssetEntry> {
        val file = manifestFile.get().asFile
        if (!file.exists()) return mutableMapOf()

        return try {
            val lines = file.readLines()
            lines
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 3) {
                        parts[0] to
                            AssetEntry(
                                parts[0],
                                parts[1].toIntOrNull() ?: 0,
                                parts[2].toLongOrNull() ?: 0
                            )
                    } else null
                }
                .toMap()
                .toMutableMap()
        } catch (e: Exception) {
            logger.warn("Could not load manifest: ${e.message}")
            mutableMapOf()
        }
    }

    private fun saveManifest(manifest: Map<String, AssetEntry>) {
        val file = manifestFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            manifest.values.joinToString("\n") { "${it.name}|${it.hash}|${it.lastModified}" }
        )
    }

    data class AssetEntry(val name: String, val hash: Int, val lastModified: Long)
}
