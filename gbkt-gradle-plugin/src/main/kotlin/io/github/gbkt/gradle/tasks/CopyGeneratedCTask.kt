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
import org.gradle.api.tasks.*

/**
 * Task that copies generated C files to a user-accessible location.
 *
 * This task allows developers to inspect the generated C code for debugging and learning purposes.
 * It can optionally copy source map files that allow mapping from generated C code back to the
 * original Kotlin DSL.
 *
 * Usage:
 * ```kotlin
 * gbkt {
 *     output {
 *         keepGeneratedC.set(true)                              // Enable copying
 *         cOutputDir.set(layout.projectDirectory.dir("gen-c"))  // Custom location
 *         keepSourceMaps.set(true)                              // Include .gbkt.map
 *     }
 * }
 * ```
 */
abstract class CopyGeneratedCTask @Inject constructor() : DefaultTask() {

    /** The source C file to copy. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceCFile: RegularFileProperty

    /**
     * Optional source map file to copy alongside the C file. Source maps enable mapping from
     * generated C code back to Kotlin DSL.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceMapFile: RegularFileProperty

    /** Output directory for copied files. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    /** Whether to copy source map files alongside C files. */
    @get:Input abstract val copySourceMaps: Property<Boolean>

    init {
        description = "Copy generated C files to user-accessible location"
        group = "gbkt"
    }

    @TaskAction
    fun copy() {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        // Copy C file
        val cFile = sourceCFile.get().asFile
        val destCFile = File(outDir, cFile.name)
        cFile.copyTo(destCFile, overwrite = true)
        logger.lifecycle("Copied generated C: ${destCFile.absolutePath}")

        // Copy source map if enabled and exists
        if (copySourceMaps.getOrElse(true)) {
            val mapFile = sourceMapFile.orNull?.asFile
            if (mapFile != null && mapFile.exists()) {
                val destMapFile = File(outDir, mapFile.name)
                mapFile.copyTo(destMapFile, overwrite = true)
                logger.lifecycle("Copied source map: ${destMapFile.absolutePath}")
            }
        }
    }
}
