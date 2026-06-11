/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.internal

import org.gradle.api.GradleException

/**
 * Reflection-based access to backend API classes.
 *
 * This helper encapsulates reflection calls needed to interact with gbkt-backend-api when running
 * in a Gradle worker with classloader isolation. The worker runs with the user's classpath (which
 * includes the actual backend implementations), while the plugin itself only has compile-time
 * access to the API interfaces.
 *
 * Why reflection is required: Gradle's `classLoaderIsolation` runs the worker action with the
 * user's runtime classpath. Even though we have `compileOnly` access to backend-api, at runtime the
 * classes are loaded by a different classloader, making direct type casts impossible.
 */
object BackendReflection {

    private const val REGISTRY_CLASS = "io.github.gbkt.backend.api.BackendRegistry"
    private const val OPTIONS_CLASS = "io.github.gbkt.backend.api.GenerationOptions"
    private const val GAME_CLASS = "io.github.gbkt.core.Game"

    /**
     * Load and discover backends from the registry.
     *
     * @return List of discovered backend instances, or null if registry not available
     */
    fun discoverBackends(): List<Any>? {
        return try {
            val registryClass = Class.forName(REGISTRY_CLASS)
            val registry = getObjectInstance(registryClass)
            val discoverMethod = registryClass.getMethod("discover")
            @Suppress("UNCHECKED_CAST")
            discoverMethod.invoke(registry) as List<Any>
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    /**
     * Find a backend for the specified target platform.
     *
     * @param target Target platform identifier (e.g., "gbc", "gb")
     * @return Backend instance or null if not found
     */
    fun findBackendForTarget(target: String): Any? {
        val registryClass = Class.forName(REGISTRY_CLASS)
        val registry = getObjectInstance(registryClass)
        val forTargetMethod = registryClass.getMethod("forTarget", String::class.java)
        return forTargetMethod.invoke(registry, target)
    }

    /** Get the display name of a backend. */
    fun getBackendDisplayName(backend: Any): String {
        return backend.javaClass.getMethod("getDisplayName").invoke(backend) as String
    }

    /** Get the ID of a backend. */
    fun getBackendId(backend: Any): String {
        return backend.javaClass.getMethod("getId").invoke(backend) as String
    }

    /**
     * Validate a game using the backend.
     *
     * @return ValidationResultWrapper with validation state
     */
    fun validateGame(backend: Any, game: Any): ValidationResultWrapper {
        val gameClass = Class.forName(GAME_CLASS)
        val validateMethod = backend.javaClass.getMethod("validate", gameClass)
        val result = validateMethod.invoke(backend, game)
        return ValidationResultWrapper(result)
    }

    /**
     * Generate code using the backend.
     *
     * @return GenerationResultWrapper with generated files
     */
    fun generateCode(
        backend: Any,
        game: Any,
        debug: Boolean = false,
        sourceMap: Boolean = true,
        optimizationLevel: Int = 1,
    ): GenerationResultWrapper {
        val gameClass = Class.forName(GAME_CLASS)
        val optionsClass = Class.forName(OPTIONS_CLASS)

        // Create GenerationOptions instance
        // The constructor signature is: (debug, sourceMap, optimizationLevel, outputFormat,
        // customOptions)
        val options = createGenerationOptions(optionsClass, debug, sourceMap, optimizationLevel)

        val generateMethod = backend.javaClass.getMethod("generate", gameClass, optionsClass)
        val result = generateMethod.invoke(backend, game, options)
        return GenerationResultWrapper(result)
    }

    private fun createGenerationOptions(
        optionsClass: Class<*>,
        debug: Boolean,
        sourceMap: Boolean,
        optimizationLevel: Int,
    ): Any {
        val outputFormatClass = Class.forName("io.github.gbkt.backend.api.OutputFormat")
        val multiFile = outputFormatClass.enumConstants.find { it.toString() == "MULTI_FILE" }
        val constructors = optionsClass.constructors

        // Prefer the 5-param primary constructor (all explicit fields)
        val exactConstructor = constructors.find { it.parameterCount == 5 }
        if (exactConstructor != null) {
            return exactConstructor.newInstance(
                debug,
                sourceMap,
                optimizationLevel,
                multiFile,
                emptyMap<String, Any>(),
            )
        }

        // Kotlin data classes with all-default params generate a synthetic constructor:
        // (field1, field2, ..., fieldN, Int defaultsMask, DefaultConstructorMarker)
        // Pass 0 for mask (= all values explicit) and null for marker
        val syntheticConstructor =
            constructors.find { it.parameterCount == 7 } ?: constructors.first()
        return syntheticConstructor.newInstance(
            debug,
            sourceMap,
            optimizationLevel,
            multiFile,
            emptyMap<String, Any>(),
            0, // defaults bitmask: all values provided explicitly
            null, // DefaultConstructorMarker
        )
    }

    private fun getObjectInstance(clazz: Class<*>): Any {
        return clazz.kotlin.objectInstance ?: clazz.getDeclaredField("INSTANCE").get(null)
    }
}

/** Wrapper for ValidationResult accessed via reflection. */
class ValidationResultWrapper(private val result: Any) {
    val isValid: Boolean
        get() = result.javaClass.getMethod("isValid").invoke(result) as Boolean

    val errors: List<ValidationMessageWrapper>
        get() {
            @Suppress("UNCHECKED_CAST")
            val errorList = result.javaClass.getMethod("getErrors").invoke(result) as List<Any>
            return errorList.map { ValidationMessageWrapper(it) }
        }

    val warnings: List<ValidationMessageWrapper>
        get() {
            @Suppress("UNCHECKED_CAST")
            val warningList = result.javaClass.getMethod("getWarnings").invoke(result) as List<Any>
            return warningList.map { ValidationMessageWrapper(it) }
        }

    /** Throw a GradleException if validation failed. */
    fun throwIfInvalid() {
        if (!isValid) {
            val errorMessages = errors.mapIndexed { i, e ->
                "  ${i + 1}. [${e.category ?: ""}] ${e.message}"
            }
            throw GradleException(
                "Game validation failed with ${errors.size} error(s):\n${errorMessages.joinToString("\n")}"
            )
        }
    }

    /** Print errors and warnings to stderr/stdout. */
    fun printDiagnostics() {
        if (!isValid) {
            System.err.println("Validation failed with ${errors.size} error(s):")
            errors.forEachIndexed { index, error ->
                System.err.println("  ${index + 1}. [${error.category ?: ""}] ${error.message}")
            }
        }
        if (warnings.isNotEmpty()) {
            val prefix = if (isValid) "Validation passed with" else "\nAdditionally,"
            println("$prefix ${warnings.size} warning(s):")
            warnings.forEachIndexed { index, warning ->
                println("  ${index + 1}. ${warning.message}")
            }
        }
    }
}

/** Wrapper for ValidationMessage accessed via reflection. */
class ValidationMessageWrapper(private val underlying: Any) {
    val message: String
        get() = underlying.javaClass.getMethod("getMessage").invoke(underlying) as String

    val category: String?
        get() = underlying.javaClass.getMethod("getCategory").invoke(underlying) as String?
}

/** Wrapper for GenerationResult accessed via reflection. */
class GenerationResultWrapper(private val result: Any) {
    val success: Boolean
        get() = result.javaClass.getMethod("getSuccess").invoke(result) as Boolean

    val error: String?
        get() = result.javaClass.getMethod("getError").invoke(result) as String?

    val files: Map<String, String>
        get() {
            @Suppress("UNCHECKED_CAST")
            val filesMap = result.javaClass.getMethod("getFiles").invoke(result) as Map<String, Any>
            return filesMap.mapValues { (_, generatedFile) ->
                generatedFile.javaClass.getMethod("getContent").invoke(generatedFile) as String
            }
        }

    /**
     * Get the v2 source map JSON for a specific file, if available.
     *
     * Uses reflection to access [GeneratedFile.sourceMapJson] introduced in Plan 02. Returns null
     * for v1 games (where the field does not exist) and for header files (game.h) which have no
     * DSL-originated statements.
     *
     * @param filename The C filename to look up (e.g. "main.c", "bank1.c")
     * @return Source map JSON string, or null if not available
     */
    fun getSourceMapJsonForFile(filename: String): String? {
        @Suppress("UNCHECKED_CAST")
        val filesMap = result.javaClass.getMethod("getFiles").invoke(result) as Map<String, Any>
        val generatedFile = filesMap[filename] ?: return null
        return try {
            generatedFile.javaClass.getMethod("getSourceMapJson").invoke(generatedFile) as String?
        } catch (e: NoSuchMethodException) {
            // Backward compat: old GeneratedFile without sourceMapJson (v1 games)
            null
        }
    }

    /** Get files or throw if generation failed. */
    fun getFilesOrThrow(): Map<String, String> {
        if (!success) {
            throw GradleException("Backend generation failed: ${error ?: "Unknown error"}")
        }
        return files
    }
}
