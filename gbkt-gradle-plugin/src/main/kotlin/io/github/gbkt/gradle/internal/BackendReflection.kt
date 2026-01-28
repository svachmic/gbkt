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
        // Try to find the constructor with default parameters
        // Kotlin data classes have complex constructor signatures due to defaults
        val constructors = optionsClass.constructors
        val primaryConstructor =
            constructors.find { it.parameterCount >= 3 } ?: constructors.first()

        return when (primaryConstructor.parameterCount) {
            // Full constructor with all parameters including defaults marker
            in 5..10 -> {
                // Kotlin data class with defaults: (debug, sourceMap, optLevel, outputFormat,
                // customOptions, ...)
                val outputFormatClass = Class.forName("io.github.gbkt.backend.api.OutputFormat")
                val multiFile =
                    outputFormatClass.enumConstants.find { it.toString() == "MULTI_FILE" }
                primaryConstructor.newInstance(
                    debug,
                    sourceMap,
                    optimizationLevel,
                    multiFile,
                    emptyMap<String, Any>(),
                )
            }
            3 -> primaryConstructor.newInstance(debug, sourceMap, optimizationLevel)
            else -> primaryConstructor.newInstance(debug, sourceMap, optimizationLevel)
        }
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
            val errorMessages =
                errors.mapIndexed { i, e -> "  ${i + 1}. [${e.category ?: ""}] ${e.message}" }
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

    /** Get files or throw if generation failed. */
    fun getFilesOrThrow(): Map<String, String> {
        if (!success) {
            throw GradleException("Backend generation failed: ${error ?: "Unknown error"}")
        }
        return files
    }
}
