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
package io.github.gbkt.intellij.codegen

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Service for managing C code generation from gbkt DSL.
 *
 * Provides:
 * - Gradle-based code generation via `./gradlew generateC`
 * - Access to generated C code and source maps
 * - Caching and async generation support
 */
@Service(Service.Level.PROJECT)
class GbktCodegenService(private val project: Project) {

    /** Result of a code generation operation. */
    data class CodegenResult(
        val success: Boolean,
        val cCode: String?,
        val sourceMap: SourceMap?,
        val errorMessage: String?,
        val generationTimeMs: Long,
    )

    /** Source map for mapping C lines to Kotlin sources (single-file, keyed by cLine). */
    data class SourceMap(val mappings: Map<Int, SourceLocation>) {
        data class SourceLocation(
            val file: String,
            val line: Int,
            val column: Int = 0,
            val irNodeType: String? = null,
        )
    }

    /**
     * Multi-file source map keyed by C file name (e.g., "main.c", "bank1.c"). Each value is a map
     * from cLine to SourceLocation.
     */
    data class MultiFileSourceMap(
        val filesMappings: Map<String, Map<Int, SourceMap.SourceLocation>>
    ) {
        /** Flat view merging all file mappings for backward-compatible single-file lookups. */
        fun toSingleFileMap(): SourceMap {
            val merged = mutableMapOf<Int, SourceMap.SourceLocation>()
            for ((_, mappings) in filesMappings) {
                merged.putAll(mappings)
            }
            return SourceMap(merged)
        }
    }

    private var lastResult: CodegenResult? = null

    /** Cached multi-file source maps, invalidated whenever readCachedSourceMap() is called. */
    @Volatile private var lastMultiFileSourceMap: MultiFileSourceMap? = null

    /** Generated C files keyed by filename, for combined view. */
    @Volatile private var lastGeneratedFiles: Map<String, String>? = null

    /** Lock for thread-safe generation state management. */
    private val generationLock = ReentrantLock()

    /** Thread-safe flag indicating if generation is in progress. */
    private val isGeneratingFlag = AtomicBoolean(false)

    /** Current process handler for cancellation support. */
    @Volatile private var currentProcessHandler: OSProcessHandler? = null

    /**
     * Generates C code asynchronously using Gradle.
     *
     * This method is thread-safe. If generation is already in progress, the returned future will
     * complete exceptionally with an IllegalStateException.
     *
     * @param forceRegenerate If true, ignores any cached result
     * @return CompletableFuture with the generation result
     */
    fun generateAsync(forceRegenerate: Boolean = false): CompletableFuture<CodegenResult> {
        val future = CompletableFuture<CodegenResult>()

        if (!forceRegenerate && lastResult?.success == true) {
            future.complete(lastResult)
            return future
        }

        // Thread-safe check-and-set for generation flag
        val canGenerate = generationLock.withLock {
            if (isGeneratingFlag.get()) {
                false
            } else {
                isGeneratingFlag.set(true)
                true
            }
        }

        if (!canGenerate) {
            future.completeExceptionally(IllegalStateException("Generation already in progress"))
            return future
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = runGradleGenerate()
                lastResult = result
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            } finally {
                currentProcessHandler = null
                isGeneratingFlag.set(false)
            }
        }

        return future
    }

    /** Gets the last generated C code (main.c only), or null if not available. */
    fun getLastCCode(): String? {
        return lastResult?.cCode ?: readCachedCCode()
    }

    /** Gets all generated C files (main.c, bank1.c, etc.) keyed by filename. */
    fun getGeneratedFiles(): Map<String, String>? {
        return lastGeneratedFiles ?: readCachedGeneratedFiles()
    }

    /** Gets the last source map (single-file view), or null if not available. */
    fun getLastSourceMap(): SourceMap? {
        return lastResult?.sourceMap ?: readCachedSourceMap()?.toSingleFileMap()
    }

    /** Gets the multi-file source map, or null if not available. */
    fun getLastMultiFileSourceMap(): MultiFileSourceMap? {
        return lastMultiFileSourceMap ?: readCachedSourceMap()
    }

    /**
     * Forward lookup: given a C file and line number, returns the Kotlin source location that
     * generated that C line.
     *
     * This enables C->DSL reverse mapping in the CCodePreviewPanel: when the user places their
     * caret on a line in the generated C view, this method resolves the corresponding Kotlin file
     * and line. If there is no exact match, the nearest preceding entry is returned as the best
     * approximation.
     *
     * @param cFile C filename key (e.g., "main.c", "bank1.c")
     * @param cLine 1-based line number within the C file
     * @return The [SourceMap.SourceLocation] that maps to this C line, or null if not found
     */
    fun findKotlinLocationForCLine(cFile: String, cLine: Int): SourceMap.SourceLocation? {
        val multiMap = lastMultiFileSourceMap ?: readCachedSourceMap() ?: return null
        val fileMappings = multiMap.filesMappings[cFile] ?: return null

        // Exact match first
        fileMappings[cLine]?.let {
            return it
        }

        // Fall back to nearest preceding entry
        return fileMappings.entries
            .filter { (line, _) -> line < cLine }
            .maxByOrNull { (line, _) -> line }
            ?.value
    }

    /**
     * Reverse lookup: given a Kotlin source file and line, returns all matching (cFile, cLine)
     * pairs across all loaded source maps.
     *
     * @param kotlinFile Absolute path to the Kotlin source file
     * @param kotlinLine 1-based line number in the Kotlin source file
     * @return List of (cFile, cLine) pairs ordered by cFile name for deterministic output
     */
    fun findCLinesForKotlinLocation(kotlinFile: String, kotlinLine: Int): List<Pair<String, Int>> {
        val multiMap = lastMultiFileSourceMap ?: readCachedSourceMap() ?: return emptyList()
        val results = mutableListOf<Pair<String, Int>>()

        for ((cFile, mappings) in multiMap.filesMappings.entries.sortedBy { it.key }) {
            for ((cLine, sourceLocation) in mappings) {
                if (sourceLocation.file == kotlinFile && sourceLocation.line == kotlinLine) {
                    results.add(cFile to cLine)
                }
            }
        }

        return results
    }

    /** Clears the cached generation result. */
    fun clearCache() {
        lastResult = null
        lastMultiFileSourceMap = null
        lastGeneratedFiles = null
    }

    private fun runGradleGenerate(): CodegenResult {
        val startTime = System.currentTimeMillis()

        return try {
            val projectPath =
                project.basePath
                    ?: return CodegenResult(
                        success = false,
                        cCode = null,
                        sourceMap = null,
                        errorMessage = "Project path not found",
                        generationTimeMs = 0,
                    )

            // Determine the Gradle wrapper path
            val gradleWrapper =
                if (System.getProperty("os.name").lowercase().contains("win")) {
                    File(projectPath, "gradlew.bat")
                } else {
                    File(projectPath, "gradlew")
                }

            if (!gradleWrapper.exists()) {
                return CodegenResult(
                    success = false,
                    cCode = null,
                    sourceMap = null,
                    errorMessage = "Gradle wrapper not found: ${gradleWrapper.absolutePath}",
                    generationTimeMs = 0,
                )
            }

            // Build command
            val commandLine =
                GeneralCommandLine(gradleWrapper.absolutePath, "generateC", "--quiet")
                    .withWorkDirectory(projectPath)
                    .withEnvironment(System.getenv())

            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            val handler = OSProcessHandler(commandLine)
            currentProcessHandler = handler

            handler.addProcessListener(
                object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        if (outputType.toString().contains("STDERR")) {
                            errorBuilder.append(event.text)
                        } else {
                            outputBuilder.append(event.text)
                        }
                    }
                }
            )

            handler.startNotify()

            // Wait with timeout to prevent indefinite blocking if Gradle hangs
            val timeoutMs = 120_000L // 2 minutes
            val completed = handler.waitFor(timeoutMs)

            if (!completed) {
                handler.destroyProcess()
                return CodegenResult(
                    success = false,
                    cCode = null,
                    sourceMap = null,
                    errorMessage =
                        "Generation timed out after ${timeoutMs / 1000} seconds. " +
                            "Try running './gradlew generateC' manually to diagnose the issue.",
                    generationTimeMs = timeoutMs,
                )
            }

            val exitCode = handler.exitCode ?: -1
            val elapsedTime = System.currentTimeMillis() - startTime

            if (exitCode != 0) {
                return CodegenResult(
                    success = false,
                    cCode = null,
                    sourceMap = null,
                    errorMessage =
                        "Gradle generateC failed (exit code $exitCode):\n${errorBuilder}",
                    generationTimeMs = elapsedTime,
                )
            }

            // Read generated files
            val cCode = readCachedCCode()
            val multiMap = readCachedSourceMap()
            lastMultiFileSourceMap = multiMap
            lastGeneratedFiles = readCachedGeneratedFiles()

            CodegenResult(
                success = true,
                cCode = cCode,
                sourceMap = multiMap?.toSingleFileMap(),
                errorMessage = null,
                generationTimeMs = elapsedTime,
            )
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            CodegenResult(
                success = false,
                cCode = null,
                sourceMap = null,
                errorMessage = "Unexpected error: ${e.message}",
                generationTimeMs = elapsedTime,
            )
        }
    }

    private fun readCachedCCode(): String? {
        val projectPath = project.basePath ?: return null
        val cFile = File(projectPath, "build/gbkt/generated/main.c")
        return if (cFile.exists()) cFile.readText() else null
    }

    /** Read all generated C files from the generated directory. */
    private fun readCachedGeneratedFiles(): Map<String, String>? {
        val projectPath = project.basePath ?: return null
        val generatedDir = File(projectPath, "build/gbkt/generated")
        if (!generatedDir.exists()) return null

        val cFiles = generatedDir.listFiles { file -> file.extension == "c" } ?: return null
        if (cFiles.isEmpty()) return null

        // Sort with main.c first, then by bank number
        val sorted =
            cFiles.sortedWith(
                compareBy { file ->
                    when {
                        file.name == "main.c" -> 0
                        file.name.startsWith("bank") ->
                            file.name.removePrefix("bank").removeSuffix(".c").toIntOrNull() ?: 999
                        else -> 1000
                    }
                }
            )

        return sorted.associate { file -> file.name to file.readText() }
    }

    /**
     * Scan the generated directory for all *.gbkt.map files and parse them as v2 JSON format.
     * Returns a MultiFileSourceMap with per-file mappings.
     */
    private fun readCachedSourceMap(): MultiFileSourceMap? {
        val projectPath = project.basePath ?: return null
        val generatedDir = File(projectPath, "build/gbkt/generated")
        if (!generatedDir.exists()) return null

        val mapFiles = generatedDir.listFiles { file -> file.name.endsWith(".gbkt.map") }
        if (mapFiles.isNullOrEmpty()) return null

        val filesMappings = mutableMapOf<String, Map<Int, SourceMap.SourceLocation>>()

        for (mapFile in mapFiles) {
            try {
                val parsed = parseSourceMap(mapFile.readText(), mapFile.name)
                if (parsed != null) {
                    filesMappings[parsed.first] = parsed.second
                }
            } catch (@Suppress("SwallowedException") e: Exception) {
                // Skip malformed map files
            }
        }

        if (filesMappings.isEmpty()) return null

        val result = MultiFileSourceMap(filesMappings)
        lastMultiFileSourceMap = result
        return result
    }

    /**
     * Parse a source map file in v2 JSON format using a simple regex-based approach.
     *
     * Format: {"version":"2.0","gameName":"...","cFile":"...","bankNumber":0,"mappings":[...]} Each
     * mapping: {"cLine":N,"kotlinFile":"...","kotlinLine":N,"kotlinColumn":N,"irNodeType":"..."}
     *
     * Uses regex extraction instead of a JSON library to avoid dependency on org.json (Gradle-only)
     * or Gson/Jackson (not guaranteed on IntelliJ plugin classpath).
     *
     * @param content The JSON content of the source map file
     * @param fallbackFileName Filename used as cFile key if JSON doesn't contain it
     * @return Pair of (cFileName, mappings) or null if parsing fails
     */
    private fun parseSourceMap(
        content: String,
        fallbackFileName: String,
    ): Pair<String, Map<Int, SourceMap.SourceLocation>>? {
        val cFile =
            extractJsonString(content, "cFile") ?: fallbackFileName.removeSuffix(".gbkt.map")

        // Extract the mappings array content between the outer [ and ]
        val mappingsStart = content.indexOf("\"mappings\"")
        if (mappingsStart < 0) return null
        val arrayStart = content.indexOf('[', mappingsStart)
        if (arrayStart < 0) return null

        val mappings = mutableMapOf<Int, SourceMap.SourceLocation>()

        // Split on object boundaries — find each {...} object in the array
        var depth = 0
        var objStart = -1
        var i = arrayStart + 1

        while (i < content.length) {
            val ch = content[i]
            when (ch) {
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        val objStr = content.substring(objStart, i + 1)
                        parseMappingObject(objStr)?.let { (cLine, loc) -> mappings[cLine] = loc }
                        objStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
            i++
        }

        return cFile to mappings
    }

    private fun parseMappingObject(obj: String): Pair<Int, SourceMap.SourceLocation>? {
        val cLine = extractJsonInt(obj, "cLine") ?: return null
        val kotlinFile = extractJsonString(obj, "kotlinFile") ?: return null
        val kotlinLine = extractJsonInt(obj, "kotlinLine") ?: return null
        val kotlinColumn = extractJsonInt(obj, "kotlinColumn") ?: 0
        val irNodeType = extractJsonString(obj, "irNodeType")

        return cLine to
            SourceMap.SourceLocation(
                file = kotlinFile,
                line = kotlinLine,
                column = kotlinColumn,
                irNodeType = irNodeType,
            )
    }

    /** Extract a string value from a simple JSON object string. */
    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\/", "/")
    }

    /** Extract an integer value from a simple JSON object string. */
    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Checks if C code generation is currently in progress. */
    fun isGenerating(): Boolean = isGeneratingFlag.get()

    /**
     * Cancels the current code generation if one is in progress.
     *
     * @return true if a generation was cancelled, false if none was running
     */
    fun cancelGeneration(): Boolean {
        val handler = currentProcessHandler ?: return false

        return generationLock.withLock {
            if (isGeneratingFlag.get()) {
                handler.destroyProcess()
                currentProcessHandler = null
                isGeneratingFlag.set(false)
                true
            } else {
                false
            }
        }
    }

    companion object {
        fun getInstance(project: Project): GbktCodegenService {
            return project.getService(GbktCodegenService::class.java)
        }
    }
}
