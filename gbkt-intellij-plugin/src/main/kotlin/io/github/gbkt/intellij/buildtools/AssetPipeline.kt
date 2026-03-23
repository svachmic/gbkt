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
package io.github.gbkt.intellij.buildtools

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Represents an asset in the build pipeline.
 *
 * Tracks source files, output files, and build status.
 */
data class AssetInfo(
    val name: String,
    val type: AssetType,
    val sourcePath: String,
    val outputPath: String?,
    val status: AssetStatus,
    val lastModified: Long,
    val lastBuilt: Long?,
    val sizeBytes: Long?,
    val errors: List<String> = emptyList(),
) {
    /** Types of assets in the pipeline. */
    enum class AssetType(val extension: String, val displayName: String) {
        SCRIPT(".gbkt.kts", "Script"),
        SPRITE(".png", "Sprite"),
        TILEMAP(".tmx", "Tilemap"),
        PALETTE(".pal", "Palette"),
        STRINGS(".strings", "Strings"),
        DATA(".csv", "Data"),
        MUSIC(".mod", "Music"),
        SFX(".wav", "Sound Effect"),
        GENERATED(".c", "Generated C"),
        BINARY(".bin", "Binary"),
    }

    /** Build status of an asset. */
    enum class AssetStatus(val displayName: String, val needsBuild: Boolean) {
        UP_TO_DATE("Up to date", false),
        NEEDS_BUILD("Needs build", true),
        BUILDING("Building...", false),
        ERROR("Error", true),
        NOT_FOUND("Not found", true),
    }

    /** Whether this asset needs to be rebuilt. */
    val needsRebuild: Boolean
        get() = status.needsBuild || (lastBuilt != null && lastModified > lastBuilt)
}

/**
 * Manages the asset pipeline for a gbkt project.
 *
 * Tracks all assets, their build status, and dependencies.
 */
class AssetPipeline(private val project: Project) {

    private val logger = Logger.getInstance(AssetPipeline::class.java)
    private val assets = mutableListOf<AssetInfo>()
    private val listeners = mutableListOf<() -> Unit>()
    private val scanErrors = mutableListOf<String>()

    /** All assets in the pipeline. */
    val allAssets: List<AssetInfo>
        get() = assets.toList()

    /** Assets that need to be rebuilt. */
    val assetsNeedingBuild: List<AssetInfo>
        get() = assets.filter { it.needsRebuild }

    /** Total number of assets. */
    val totalAssets: Int
        get() = assets.size

    /** Number of assets that are up to date. */
    val upToDateCount: Int
        get() = assets.count { it.status == AssetInfo.AssetStatus.UP_TO_DATE }

    /** Number of assets with errors. */
    val errorCount: Int
        get() = assets.count { it.status == AssetInfo.AssetStatus.ERROR }

    /** Errors encountered during the last scan. */
    val lastScanErrors: List<String>
        get() = scanErrors.toList()

    /** Whether the last scan encountered any errors. */
    val hasScanErrors: Boolean
        get() = scanErrors.isNotEmpty()

    /** Adds a listener for pipeline changes. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /** Removes a listener. */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    /**
     * Scans the project for assets.
     *
     * @return true if scan completed successfully, false if there were errors
     */
    fun scanProject(): Boolean {
        assets.clear()
        scanErrors.clear()

        val basePath = project.basePath
        if (basePath == null) {
            val error = "Project base path is not available"
            logger.warn(error)
            scanErrors.add(error)
            notifyListeners()
            return false
        }

        val baseDir = File(basePath)
        if (!baseDir.exists()) {
            val error = "Project directory does not exist: $basePath"
            logger.warn(error)
            scanErrors.add(error)
            notifyListeners()
            return false
        }

        if (!baseDir.isDirectory) {
            val error = "Project path is not a directory: $basePath"
            logger.warn(error)
            scanErrors.add(error)
            notifyListeners()
            return false
        }

        logger.info("Scanning project for assets: $basePath")

        // Scan for different asset types
        scanForAssets(baseDir, "**/*.gbkt.kts", AssetInfo.AssetType.SCRIPT)
        scanForAssets(baseDir, "**/assets/**/*.png", AssetInfo.AssetType.SPRITE)
        scanForAssets(baseDir, "**/sprites/**/*.png", AssetInfo.AssetType.SPRITE)
        scanForAssets(baseDir, "**/gfx/**/*.png", AssetInfo.AssetType.SPRITE)
        scanForAssets(baseDir, "**/*.tmx", AssetInfo.AssetType.TILEMAP)
        scanForAssets(baseDir, "**/*.pal", AssetInfo.AssetType.PALETTE)

        // Check build directory for generated files
        val buildDir = File(baseDir, "build/generated")
        if (buildDir.exists() && buildDir.isDirectory) {
            scanForAssets(buildDir, "**/*.c", AssetInfo.AssetType.GENERATED)
            scanForAssets(buildDir, "**/*.bin", AssetInfo.AssetType.BINARY)
        }

        logger.info("Asset scan complete: ${assets.size} assets found, ${scanErrors.size} errors")
        notifyListeners()
        return scanErrors.isEmpty()
    }

    /** Context for directory scanning. */
    private data class ScanContext(
        val baseDir: File,
        val patternParts: List<String>,
        val type: AssetInfo.AssetType,
    )

    private fun scanForAssets(baseDir: File, pattern: String, type: AssetInfo.AssetType) {
        val ctx = ScanContext(baseDir, pattern.split("/"), type)
        scanDirectory(ctx, baseDir, 0)
    }

    private fun scanDirectory(ctx: ScanContext, currentDir: File, partIndex: Int) {
        if (!currentDir.exists() || !currentDir.isDirectory) return
        if (partIndex >= ctx.patternParts.size) return

        // Check read permissions
        if (!currentDir.canRead()) {
            val error = "Cannot read directory: ${currentDir.path}"
            logger.warn(error)
            scanErrors.add(error)
            return
        }

        val part = ctx.patternParts[partIndex]
        val isLast = partIndex == ctx.patternParts.size - 1

        try {
            when {
                part == "**" -> scanGlobstar(ctx, currentDir, partIndex, isLast)
                part.contains("*") -> scanWildcard(ctx, currentDir, partIndex, isLast, part)
                else -> scanExact(ctx, currentDir, partIndex, isLast, part)
            }
        } catch (e: SecurityException) {
            val error = "Security exception scanning ${currentDir.path}: ${e.message}"
            logger.warn(error, e)
            scanErrors.add(error)
        }
    }

    private fun scanGlobstar(ctx: ScanContext, currentDir: File, partIndex: Int, isLast: Boolean) {
        if (isLast) return
        val files =
            try {
                currentDir.listFiles()
            } catch (e: SecurityException) {
                logger.warn("Cannot list files in ${currentDir.path}: ${e.message}")
                null
            }

        files?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(ctx, file, partIndex)
                scanDirectory(ctx, file, partIndex + 1)
            } else if (isLast) {
                checkFile(ctx.baseDir, file, ctx.patternParts.last(), ctx.type)
            }
        }
        scanDirectory(ctx, currentDir, partIndex + 1)
    }

    private fun scanWildcard(
        ctx: ScanContext,
        currentDir: File,
        partIndex: Int,
        isLast: Boolean,
        part: String,
    ) {
        val regex = part.replace(".", "\\.").replace("*", ".*").toRegex()
        val files =
            try {
                currentDir.listFiles()
            } catch (e: SecurityException) {
                logger.warn("Cannot list files in ${currentDir.path}: ${e.message}")
                null
            }

        files?.forEach { file ->
            if (file.name.matches(regex)) {
                if (isLast && file.isFile) {
                    addAsset(ctx.baseDir, file, ctx.type)
                } else if (file.isDirectory) {
                    scanDirectory(ctx, file, partIndex + 1)
                }
            }
        }
    }

    private fun scanExact(
        ctx: ScanContext,
        currentDir: File,
        partIndex: Int,
        isLast: Boolean,
        part: String,
    ) {
        val target = File(currentDir, part)
        if (target.exists()) {
            if (isLast && target.isFile) {
                addAsset(ctx.baseDir, target, ctx.type)
            } else if (target.isDirectory) {
                scanDirectory(ctx, target, partIndex + 1)
            }
        }
    }

    private fun checkFile(baseDir: File, file: File, pattern: String, type: AssetInfo.AssetType) {
        val regex = pattern.replace(".", "\\.").replace("*", ".*").toRegex()
        if (file.isFile && file.name.matches(regex)) {
            addAsset(baseDir, file, type)
        }
    }

    private fun addAsset(baseDir: File, file: File, type: AssetInfo.AssetType) {
        try {
            val relativePath = file.relativeTo(baseDir).path
            val outputPath = getOutputPath(relativePath, type)
            val outputFile = outputPath?.let { File(baseDir, it) }

            val status =
                when {
                    outputFile == null -> AssetInfo.AssetStatus.NEEDS_BUILD
                    !outputFile.exists() -> AssetInfo.AssetStatus.NEEDS_BUILD
                    outputFile.lastModified() < file.lastModified() ->
                        AssetInfo.AssetStatus.NEEDS_BUILD
                    else -> AssetInfo.AssetStatus.UP_TO_DATE
                }

            assets.add(
                AssetInfo(
                    name = file.nameWithoutExtension,
                    type = type,
                    sourcePath = relativePath,
                    outputPath = outputPath,
                    status = status,
                    lastModified = file.lastModified(),
                    lastBuilt = outputFile?.takeIf { it.exists() }?.lastModified(),
                    sizeBytes = file.length(),
                )
            )
        } catch (e: IllegalArgumentException) {
            // Can happen if file is not relative to baseDir
            val error = "Cannot determine relative path for ${file.path}: ${e.message}"
            logger.warn(error, e)
            scanErrors.add(error)
        } catch (e: SecurityException) {
            val error = "Security exception accessing ${file.path}: ${e.message}"
            logger.warn(error, e)
            scanErrors.add(error)
        }
    }

    private fun getOutputPath(sourcePath: String, type: AssetInfo.AssetType): String? {
        return when (type) {
            AssetInfo.AssetType.SCRIPT -> "build/generated/${sourcePath.replace(".gbkt.kts", ".c")}"
            AssetInfo.AssetType.SPRITE -> "build/generated/${sourcePath.replace(".png", ".bin")}"
            AssetInfo.AssetType.TILEMAP -> "build/generated/${sourcePath.replace(".tmx", ".c")}"
            AssetInfo.AssetType.GENERATED,
            AssetInfo.AssetType.BINARY -> null
            else -> null
        }
    }

    /** Updates the status of an asset. */
    fun updateAssetStatus(
        sourcePath: String,
        status: AssetInfo.AssetStatus,
        errors: List<String> = emptyList(),
    ) {
        val index = assets.indexOfFirst { it.sourcePath == sourcePath }
        if (index >= 0) {
            val asset = assets[index]
            assets[index] = asset.copy(status = status, errors = errors)
            notifyListeners()
        }
    }

    /** Marks an asset as building. */
    fun markBuilding(sourcePath: String) {
        updateAssetStatus(sourcePath, AssetInfo.AssetStatus.BUILDING)
    }

    /** Marks an asset as built successfully. */
    fun markBuilt(sourcePath: String) {
        val index = assets.indexOfFirst { it.sourcePath == sourcePath }
        if (index >= 0) {
            val asset = assets[index]
            assets[index] =
                asset.copy(
                    status = AssetInfo.AssetStatus.UP_TO_DATE,
                    lastBuilt = System.currentTimeMillis(),
                    errors = emptyList(),
                )
            notifyListeners()
        }
    }

    /** Marks an asset as having an error. */
    fun markError(sourcePath: String, errors: List<String>) {
        updateAssetStatus(sourcePath, AssetInfo.AssetStatus.ERROR, errors)
    }

    /** Clears all assets. */
    fun clear() {
        assets.clear()
        notifyListeners()
    }
}
