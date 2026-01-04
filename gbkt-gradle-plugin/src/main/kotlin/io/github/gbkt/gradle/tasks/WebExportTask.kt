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
 * Task that generates an HTML page with EmulatorJS to run the Game Boy ROM in a browser.
 *
 * This task creates a web-deployable package containing:
 * - The ROM file
 * - An index.html with EmulatorJS integration
 *
 * The output can be served from any static web server or opened locally.
 *
 * Usage:
 * ```
 * ./gradlew webExport
 * ```
 *
 * Then serve the contents of build/web/ or open index.html in a browser.
 */
@CacheableTask
abstract class WebExportTask @Inject constructor() : DefaultTask() {

    /** Input ROM file to embed in the web export. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Title for the HTML page. Defaults to the game name. */
    @get:Input abstract val title: Property<String>

    /** Enable EmulatorJS controls overlay. Default: true */
    @get:Input abstract val enableControls: Property<Boolean>

    /** EmulatorJS CDN version to use. Default: "stable" */
    @get:Input abstract val emulatorJsVersion: Property<String>

    /** Output directory for web export files. Default: build/web/ */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    init {
        description = "Generate HTML page with EmulatorJS for web deployment"
        group = "gbkt"
    }

    @TaskAction
    fun export() {
        val rom = romFile.get().asFile
        val outDir = outputDir.get().asFile
        val pageTitle = title.get()
        val controls = enableControls.get()
        val version = emulatorJsVersion.get()

        // Ensure output directory exists
        outDir.mkdirs()

        // Copy ROM file to output directory
        val romFileName = rom.name
        val targetRom = File(outDir, romFileName)
        rom.copyTo(targetRom, overwrite = true)
        logger.lifecycle("Copied ROM: ${rom.name}")

        // Generate index.html
        val indexHtml = generateHtml(pageTitle, romFileName, controls, version)
        val indexFile = File(outDir, "index.html")
        indexFile.writeText(indexHtml)
        logger.lifecycle("Generated: index.html")

        logger.lifecycle("")
        logger.lifecycle("Web export complete!")
        logger.lifecycle("Output directory: ${outDir.absolutePath}")
        logger.lifecycle("")
        logger.lifecycle("To run locally, serve the directory with a web server:")
        logger.lifecycle("  npx serve ${outDir.absolutePath}")
        logger.lifecycle("  python -m http.server -d ${outDir.absolutePath}")
        logger.lifecycle("")
    }

    private fun generateHtml(
        title: String,
        romFileName: String,
        enableControls: Boolean,
        version: String
    ): String {
        val controlsJs = if (enableControls) "" else "EJS_Buttons = false;"

        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <style>
        body { margin: 0; background: #1a1a2e; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        #game { width: 640px; height: 576px; }
    </style>
</head>
<body>
    <div id="game"></div>
    <script src="https://cdn.emulatorjs.org/$version/data/loader.js"></script>
    <script>
        EJS_player = '#game';
        EJS_core = 'gb';
        EJS_gameUrl = '$romFileName';
        EJS_pathtodata = 'https://cdn.emulatorjs.org/$version/data/';
        $controlsJs
    </script>
</body>
</html>
"""
    }
}
