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

    /** Source map for mapping C lines to Kotlin sources. */
    data class SourceMap(val mappings: Map<Int, SourceLocation>) {
        data class SourceLocation(val file: String, val line: Int, val column: Int = 0)
    }

    private var lastResult: CodegenResult? = null

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
        val canGenerate =
            generationLock.withLock {
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

    /** Gets the last generated C code, or null if not available. */
    fun getLastCCode(): String? {
        return lastResult?.cCode ?: readCachedCCode()
    }

    /** Gets the last source map, or null if not available. */
    fun getLastSourceMap(): SourceMap? {
        return lastResult?.sourceMap ?: readCachedSourceMap()
    }

    /** Clears the cached generation result. */
    fun clearCache() {
        lastResult = null
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
            val sourceMap = readCachedSourceMap()

            CodegenResult(
                success = true,
                cCode = cCode,
                sourceMap = sourceMap,
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

    private fun readCachedSourceMap(): SourceMap? {
        val projectPath = project.basePath ?: return null
        val mapFile = File(projectPath, "build/gbkt/generated/main.c.gbkt.map")
        if (!mapFile.exists()) return null

        return try {
            parseSourceMap(mapFile.readText())
        } catch (@Suppress("SwallowedException") e: Exception) {
            null
        }
    }

    /**
     * Parses a source map file in the gbkt format.
     *
     * Format: Each line is `cLine:sourceFile:sourceLine[:column]`
     *
     * Note: Windows paths contain colons (e.g., C:\path\file.kt), so we parse carefully by working
     * from both ends of the line.
     */
    private fun parseSourceMap(content: String): SourceMap {
        val mappings = mutableMapOf<Int, SourceMap.SourceLocation>()

        for (line in content.lines()) {
            if (line.isBlank() || line.startsWith("#")) continue

            val parsed = parseSourceMapLine(line) ?: continue
            mappings[parsed.cLine] =
                SourceMap.SourceLocation(parsed.sourceFile, parsed.sourceLine, parsed.column)
        }

        return SourceMap(mappings)
    }

    /**
     * Parses a single source map line, handling Windows paths correctly.
     *
     * Strategy: Parse from both ends to avoid issues with colons in Windows paths.
     * - First colon separates cLine from the rest
     * - Last colon(s) separate sourceLine and optional column
     * - Everything in between is the file path
     */
    private fun parseSourceMapLine(line: String): ParsedMapping? {
        // Find first colon to get cLine
        val firstColonIdx = line.indexOf(':')
        if (firstColonIdx == -1) return null

        val cLine = line.substring(0, firstColonIdx).toIntOrNull() ?: return null
        val remainder = line.substring(firstColonIdx + 1)

        // Find last colon to get sourceLine (and maybe column)
        val lastColonIdx = remainder.lastIndexOf(':')
        if (lastColonIdx == -1) return null

        // Check if there's a column (second-to-last colon)
        val afterLastColon = remainder.substring(lastColonIdx + 1)
        val beforeLastColon = remainder.substring(0, lastColonIdx)

        // Try to parse what's after the last colon as a number
        val lastNumber = afterLastColon.toIntOrNull()
        if (lastNumber == null) {
            // Last segment isn't a number - malformed line
            return null
        }

        // Check if there's another colon for the column
        val secondLastColonIdx = beforeLastColon.lastIndexOf(':')

        return if (secondLastColonIdx != -1) {
            // Might have column - check if the segment between colons is a number
            val potentialSourceLine =
                beforeLastColon.substring(secondLastColonIdx + 1).toIntOrNull()
            if (potentialSourceLine != null) {
                // Format: cLine:path:sourceLine:column
                ParsedMapping(
                    cLine = cLine,
                    sourceFile = beforeLastColon.substring(0, secondLastColonIdx),
                    sourceLine = potentialSourceLine,
                    column = lastNumber,
                )
            } else {
                // The segment between colons isn't a number, so no column
                // Format: cLine:path:sourceLine (path might contain colons like C:\...)
                ParsedMapping(
                    cLine = cLine,
                    sourceFile = beforeLastColon,
                    sourceLine = lastNumber,
                    column = 0,
                )
            }
        } else {
            // Only one colon in remainder - format: cLine:path:sourceLine
            ParsedMapping(
                cLine = cLine,
                sourceFile = beforeLastColon,
                sourceLine = lastNumber,
                column = 0,
            )
        }
    }

    private data class ParsedMapping(
        val cLine: Int,
        val sourceFile: String,
        val sourceLine: Int,
        val column: Int,
    )

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
