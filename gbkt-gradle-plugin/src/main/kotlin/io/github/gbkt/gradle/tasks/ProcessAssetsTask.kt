/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions", // Asset processing has many per-format handlers
    "LongMethod", // processAll and processIncrementally iterate complex logic
    "CyclomaticComplexMethod", // File routing has many branches per extension
    "NestedBlockDepth", // Incremental change handling is inherently nested
    "TooGenericExceptionCaught", // Asset processing wraps all exceptions for user-facing messages
)

package io.github.gbkt.gradle.tasks

import io.github.gbkt.core.AssetManifest
import io.github.gbkt.core.AssetManifestEntry
import io.github.gbkt.core.AssetPipeline
import io.github.gbkt.core.LdtkParser
import io.github.gbkt.core.TileDeduplicator
import io.github.gbkt.core.TiledParser
import java.io.File
import javax.imageio.ImageIO
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Incrementally processes game assets into 2bpp tile data and a JSON manifest.
 *
 * Processes:
 * - PNG sprite sheets → `.2bpp` raw tile data files + SpriteEntry in manifest
 * - TMX files (Tiled JSON) → TilemapEntry in manifest with collision detection
 * - LDtk files → TilemapEntry in manifest with collision detection
 *
 * All processed output goes to [outputDirectory] (`build/generated/assets/`). The manifest is
 * written to [manifestFile] (`build/generated/assets/asset-manifest.json`).
 *
 * Incremental builds (via Gradle's [InputChanges] API) only reprocess files that changed since the
 * last build — existing manifest entries for unchanged files are preserved.
 */
@CacheableTask
abstract class ProcessAssetsTask @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {

    /**
     * Input directory containing sprite assets. Marked as @Incremental for incremental processing.
     */
    @get:Incremental
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /** Output directory for processed tile data and the manifest. */
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    /** Manifest file path. Must be inside [outputDirectory] (or a child). */
    @get:OutputFile abstract val manifestFile: RegularFileProperty

    init {
        description = "Process sprite assets incrementally"
        group = "gbkt"
    }

    @TaskAction
    fun processAssets(inputChanges: InputChanges) {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        if (inputChanges.isIncremental) {
            logger.lifecycle("Incremental asset processing...")
            val existingManifest = loadExistingManifest()
            val updatedEntries = existingManifest.toMutableMap()
            processIncrementally(inputChanges, outDir, updatedEntries)
            writeManifest(updatedEntries.values.toList())
        } else {
            logger.lifecycle("Full asset processing...")
            val entries = mutableListOf<AssetManifestEntry>()
            processAll(outDir, entries)
            writeManifest(entries)
        }
    }

    // -------------------------------------------------------------------------
    // Full processing
    // -------------------------------------------------------------------------

    private fun processAll(outDir: File, entries: MutableList<AssetManifestEntry>) {
        val assetDir = assetDirectory.get().asFile
        if (!assetDir.exists()) {
            logger.lifecycle("  No asset directory found: ${assetDir.absolutePath}")
            return
        }

        assetDir
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                logger.lifecycle("  Processing: ${file.name}")
                val entry = dispatchFile(file, assetDir, outDir)
                if (entry != null) entries.add(entry)
            }
    }

    // -------------------------------------------------------------------------
    // Incremental processing
    // -------------------------------------------------------------------------

    private fun processIncrementally(
        inputChanges: InputChanges,
        outDir: File,
        entries: MutableMap<String, AssetManifestEntry>,
    ) {
        val assetDir = assetDirectory.get().asFile

        inputChanges.getFileChanges(assetDirectory).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach

            val file = change.file
            val relPath = file.relativeTo(assetDir).path

            when (change.changeType) {
                ChangeType.ADDED,
                ChangeType.MODIFIED -> {
                    logger.lifecycle("  Processing: ${file.name}")
                    val entry = dispatchFile(file, assetDir, outDir)
                    if (entry != null) {
                        entries[relPath] = entry
                    } else {
                        entries.remove(relPath)
                    }
                }
                ChangeType.REMOVED -> {
                    logger.lifecycle("  Removing: ${file.name}")
                    entries.remove(relPath)
                    // Delete associated output file
                    val outputFile = File(outDir, "${file.nameWithoutExtension}.2bpp")
                    if (outputFile.exists()) outputFile.delete()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // File routing by extension
    // -------------------------------------------------------------------------

    /**
     * Dispatch processing by file extension. Returns the manifest entry or null for skipped files.
     */
    private fun dispatchFile(file: File, assetDir: File, outDir: File): AssetManifestEntry? {
        val relPath = file.relativeTo(assetDir).path
        return when (file.extension.lowercase()) {
            "png" -> processPng(file, relPath, outDir)
            "tmx",
            "json" -> processTmx(file, relPath)
            "ldtk" -> processLdtk(file, relPath)
            "uge" -> {
                processUge(file, relPath, outDir)
                null // .uge files produce C output, not a manifest entry
            }
            else -> {
                logger.warn("  Skipping unsupported asset type: ${file.name}")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // PNG processing
    // -------------------------------------------------------------------------

    private fun processPng(
        file: File,
        relPath: String,
        outDir: File,
    ): AssetManifestEntry.SpriteEntry {
        val image =
            try {
                ImageIO.read(file)
                    ?: throw GradleException(
                        "Asset validation failed: $relPath — could not read image (null result from ImageIO)"
                    )
            } catch (e: GradleException) {
                throw e
            } catch (e: Exception) {
                throw GradleException("Asset validation failed: $relPath — ${e.message}")
            }

        // Validate dimensions are multiples of 8 before conversion
        if (image.width % 8 != 0 || image.height % 8 != 0) {
            throw GradleException(
                "Asset validation failed: $relPath — " +
                    "image dimensions ${image.width}x${image.height} must be multiples of 8"
            )
        }

        val sheet =
            try {
                AssetPipeline.convertImage(image)
            } catch (e: Exception) {
                throw GradleException("Asset validation failed: $relPath — ${e.message}")
            }

        // Deduplicate tiles
        val deduplicator = TileDeduplicator()
        val rawTiles = sheet.tiles.map { it.data }
        val (uniqueTiles, _) = deduplicator.deduplicate(rawTiles)

        // Write raw 2bpp tile data to output directory
        val outputFile = File(outDir, "${file.nameWithoutExtension}.2bpp")
        outputFile.parentFile.mkdirs()
        val rawBytes = uniqueTiles.flatMap { it.toList() }.toByteArray()
        outputFile.writeBytes(rawBytes)

        logger.lifecycle(
            "    ${file.name}: ${sheet.tiles.size} tiles → ${uniqueTiles.size} unique tiles"
        )

        // GB luminance palette thresholds for tile conversion (default 2bpp mode)
        val paletteList = AssetPipeline.DEFAULT_PALETTE.toList()

        // Frame metadata: default 8x8 frame size (can be overridden later)
        val frameWidth = 8
        val frameHeight = 8
        val sheetWidthPx = image.width
        val sheetHeightPx = image.height
        val frameCount = (sheetWidthPx / frameWidth) * (sheetHeightPx / frameHeight)

        return AssetManifestEntry.SpriteEntry(
            path = relPath,
            tileCount = sheet.tiles.size,
            uniqueTileCount = uniqueTiles.size,
            widthInTiles = sheet.widthInTiles,
            heightInTiles = sheet.heightInTiles,
            palette = paletteList,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            frameCount = frameCount,
        )
    }

    // -------------------------------------------------------------------------
    // TMX processing (Tiled JSON format)
    // -------------------------------------------------------------------------

    private fun processTmx(file: File, relPath: String): AssetManifestEntry.TilemapEntry {
        val tiledMap =
            try {
                TiledParser.parse(file)
            } catch (e: Exception) {
                throw GradleException("Asset validation failed: $relPath — ${e.message}")
            }

        val hasCollision = tiledMap.layers.any { it.isCollisionLayer }

        // Use the first layer for map dimensions
        val firstLayer = tiledMap.layers.firstOrNull()
        val width = firstLayer?.width ?: tiledMap.width
        val height = firstLayer?.height ?: tiledMap.height

        // Tileset path from first tileset (relative reference)
        val tilesetPath = tiledMap.tilesets.firstOrNull()?.image?.ifEmpty { null }

        return AssetManifestEntry.TilemapEntry(
            path = relPath,
            width = width,
            height = height,
            hasCollision = hasCollision,
            tilesetPath = tilesetPath,
        )
    }

    // -------------------------------------------------------------------------
    // LDtk processing
    // -------------------------------------------------------------------------

    private fun processLdtk(file: File, relPath: String): AssetManifestEntry.TilemapEntry {
        val ldtkMap =
            try {
                LdtkParser.parse(file.readText())
            } catch (e: Exception) {
                throw GradleException("Asset validation failed: $relPath — ${e.message}")
            }

        val hasCollision = ldtkMap.layers.any { it.isCollision }

        // Use dimensions from first layer that has tile data
        val firstLayer = ldtkMap.layers.firstOrNull()
        val width = firstLayer?.cWid ?: 0
        val height = firstLayer?.cHei ?: 0

        return AssetManifestEntry.TilemapEntry(
            path = relPath,
            width = width,
            height = height,
            hasCollision = hasCollision,
            tilesetPath = null,
        )
    }

    // -------------------------------------------------------------------------
    // hUGETracker .uge music processing
    // -------------------------------------------------------------------------

    /**
     * Converts a hUGETracker `.uge` music file to C source via the `uge2c` tool.
     *
     * If `uge2c` is not found (via [findUge2c]), emits a warning and returns without failing the
     * build. This allows projects to build even when hUGEDriver is not installed — the game will
     * compile without music until uge2c is available.
     *
     * Output files: `song_<name>.c` and `song_<name>.h` in [outDir].
     */
    internal fun processUge(file: File, relPath: String, outDir: File) {
        val songName = file.nameWithoutExtension
        val outputC = File(outDir, "song_${songName}.c")

        val uge2cPath = findUge2c()
        if (uge2cPath == null) {
            logger.warn(
                "WARNING: uge2c not found — cannot convert '${file.name}' to C. " +
                    "Install hUGEDriver (https://github.com/SuperDisk/hUGEDriver) and ensure " +
                    "uge2c is on PATH or set HUGEDRIVER_HOME environment variable."
            )
            return
        }

        val result = execOperations.exec {
            commandLine(uge2cPath, file.absolutePath, "-o", outputC.absolutePath)
            isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            logger.error("uge2c failed for '${file.name}' with exit code ${result.exitValue}")
        } else {
            logger.lifecycle("Converted ${file.name} → song_${songName}.c")
        }
    }

    /** Locates the `uge2c` tool. Delegates to [Uge2cFinder.findUge2c] for testability. */
    internal fun findUge2c(): String? = Uge2cFinder.findUge2c()

    companion object {
        // Extensions that indicate hUGETracker music files
        const val UGE_EXTENSION = "uge"
    }

    // -------------------------------------------------------------------------
    // Manifest persistence
    // -------------------------------------------------------------------------

    /**
     * Load the existing manifest from disk and return entries keyed by relative path.
     *
     * Returns an empty map if the manifest does not exist or cannot be parsed.
     */
    private fun loadExistingManifest(): Map<String, AssetManifestEntry> {
        val file = manifestFile.get().asFile
        if (!file.exists()) return emptyMap()

        return try {
            val manifest = AssetManifest.fromJson(file.readText())
            manifest.assets.associateBy { it.path }
        } catch (e: Exception) {
            logger.warn("  Could not load existing manifest (will rebuild): ${e.message}")
            emptyMap()
        }
    }

    /** Write the manifest JSON to [manifestFile]. */
    private fun writeManifest(entries: List<AssetManifestEntry>) {
        val manifest = AssetManifest(assets = entries)
        val file = manifestFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(manifest.toJson())
        logger.lifecycle("  Manifest written: ${file.absolutePath} (${entries.size} assets)")
    }
}

// =============================================================================
// UGE2C TOOL FINDER — standalone for testability
// =============================================================================

/**
 * Locates the `uge2c` hUGETracker tool on the local system.
 *
 * Separated from [ProcessAssetsTask] so this pure detection logic can be unit-tested without Gradle
 * infrastructure.
 */
internal object Uge2cFinder {

    /**
     * Locates the `uge2c` tool by checking:
     * 1. `HUGEDRIVER_HOME` environment variable → `$HUGEDRIVER_HOME/uge2c`
     * 2. `PATH` lookup via `which uge2c`
     * 3. Common installation paths
     *
     * Returns null if the tool cannot be found, so callers can emit a graceful warning instead of
     * failing the build.
     */
    fun findUge2c(): String? {
        // 1. HUGEDRIVER_HOME environment variable
        val envHome = System.getenv("HUGEDRIVER_HOME")
        if (envHome != null) {
            val tool = File(envHome, "uge2c")
            if (tool.exists() && tool.canExecute()) return tool.absolutePath
        }

        // 2. Check PATH via `which`
        val pathResult =
            try {
                val proc = ProcessBuilder("which", "uge2c").start()
                val path = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (proc.exitValue() == 0 && path.isNotEmpty()) path else null
            } catch (_: Exception) {
                null
            }
        if (pathResult != null) return pathResult

        // 3. Common installation paths
        val commonPaths =
            listOf(
                "/opt/hUGEDriver/uge2c",
                "${System.getProperty("user.home")}/hUGEDriver/uge2c",
                "/usr/local/bin/uge2c",
            )
        return commonPaths.firstOrNull { File(it).exists() && File(it).canExecute() }
    }
}
