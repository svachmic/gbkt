/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import io.github.gbkt.core.constraints.TargetProfile
import io.github.gbkt.core.ir.GameIR

/**
 * Backend interface for code generation.
 *
 * Each backend implements this interface to provide code generation for a specific target platform.
 * Backends are discovered via ServiceLoader and registered with [BackendRegistry].
 *
 * A backend is responsible for:
 * 1. Validating that a game can be compiled for its target platform
 * 2. Generating platform-specific source code (e.g., C for GBDK)
 *
 * Note: Compilation (invoking external toolchains) is handled by the Gradle plugin or CLI, not by
 * backends. This separation allows backends to be pure Kotlin libraries while compilation remains
 * in the build system where it has access to toolchain paths, caching, and exec operations.
 */
interface CodegenBackend {
    /** The target profile describing the platform capabilities. */
    val profile: TargetProfile

    /** Unique identifier for this backend (e.g., "gbdk", "devkitpro-gba"). */
    val id: String

    /** Human-readable name for display (e.g., "GBDK-2020 for Game Boy"). */
    val displayName: String

    /** File extension for generated ROMs (e.g., "gb", "gbc", "gba"). */
    val romExtension: String

    /**
     * Validate that a game can be compiled for this target.
     *
     * This should check:
     * - Resource limits (sprites, tiles, memory)
     * - Feature compatibility
     * - Asset formats
     *
     * @param game The game IR to validate
     * @return Validation result with any errors or warnings
     */
    fun validate(game: GameIR): ValidationResult

    /**
     * Generate source code for the target platform.
     *
     * @param game The validated game IR
     * @param options Code generation options
     * @return Generation result with generated files
     */
    fun generate(game: GameIR, options: GenerationOptions = GenerationOptions()): GenerationResult
}

/** Output format for code generation. */
enum class OutputFormat {
    /** Generate all code in a single file. */
    SINGLE_FILE,

    /** Generate separate files for each bank/module. */
    MULTI_FILE,
}

/** Options for code generation. */
data class GenerationOptions(
    /** Include debug information and comments in generated code. */
    val debug: Boolean = false,

    /** Generate source maps for error mapping. */
    val sourceMap: Boolean = true,

    /** Optimization level (0 = none, 1 = basic, 2 = aggressive). */
    val optimizationLevel: Int = 1,

    /** Output format (single file or multi-file for banking). */
    val outputFormat: OutputFormat = OutputFormat.MULTI_FILE,

    /**
     * Backend-specific custom options.
     *
     * This allows passing target-specific configuration without changing the interface. Examples:
     * - `"gbcFlags"` -> `"-Wm-yc"` (GBC compatibility flags)
     * - `"bankAllocation"` -> `"auto"` (bank assignment strategy)
     * - `"optimizeSize"` -> `true` (prefer code size over speed)
     */
    val customOptions: Map<String, Any> = emptyMap(),
)
