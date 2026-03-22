/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem

/** Marker interface for codegen output fragments returned by genre visitors. */
interface CodegenFragment

/**
 * Sanitizes an identifier string for use as a C identifier.
 *
 * Replaces hyphens and spaces with underscores, producing a valid C identifier fragment. This is
 * the canonical implementation — all codegen code should call this instead of inline `.replace('-',
 * '_').replace(' ', '_')` chains.
 */
fun sanitizeCId(id: String): String = id.replace('-', '_').replace(' ', '_')

/**
 * Result type returned by [GenreSystemVisitor.visit].
 *
 * Uses [CodegenFragment] as the element type so that [GenreSystemVisitor] can live in
 * `gbkt-backend-api` (which does NOT depend on `gbkt-backend-gbdk`) while still providing type
 * safety. Concrete backend types (e.g. `CFunction`, `CVarDecl`) implement [CodegenFragment].
 */
data class GenreVisitorResult(
    /** C function definitions to emit (e.g. `CFunction` instances in GBDK). */
    val functions: List<CodegenFragment> = emptyList(),
    /** C variable declarations to emit (e.g. `CVarDecl` instances in GBDK). */
    val varDecls: List<CodegenFragment> = emptyList(),
)

/**
 * Extension point for genre-specific code generation.
 *
 * Genre packages (e.g. `gbkt-genre-rpg`) register implementations of this interface via
 * [java.util.ServiceLoader] so that the GBDK pipeline can discover and invoke them without a hard
 * compile-time dependency.
 *
 * ## Registration
 *
 * Add a service provider file to your genre module:
 * ```
 * src/main/resources/META-INF/services/io.github.gbkt.backend.api.GenreSystemVisitor
 * ```
 *
 * containing the fully-qualified class name of your implementation.
 *
 * ## Discovery
 *
 * `GBDKPipelineV2.buildSystemFunctions()` and `buildSystemGlobalVars()` call
 * [ServiceLoader.load(GenreSystemVisitor::class.java)][java.util.ServiceLoader.load] before falling
 * through to the built-in [GBDKSystemVisitor]. If a visitor returns [canHandle] = `true` for the
 * given system type, its result is used instead.
 *
 * ## Dependency safety
 *
 * This interface uses [CodegenFragment] in [GenreVisitorResult] so that genre modules can depend on
 * the concrete backend (e.g. `gbkt-backend-gbdk`) without creating a circular dependency through
 * `gbkt-backend-api`.
 */
interface GenreSystemVisitor {
    /**
     * Returns `true` if this visitor can generate code for the given system type string.
     *
     * The system type is read from [GenericSystem.config]["type"] and is a stable string identifier
     * such as `"rpg_character_system"` or `"rpg_class_system"`.
     */
    fun canHandle(systemType: String): Boolean

    /**
     * Generate C constructs for the given [system].
     *
     * Called by the GBDK pipeline for each [GenericSystem] whose type string passes [canHandle].
     * The [gameIR] parameter provides access to the full game IR for cross-system lookups (e.g.
     * resolving character references from actor lists).
     *
     * @param systemType The type string from [GenericSystem.config]["type"]
     * @param systemConfig The full config map from [GenericSystem.config]
     * @param gameIR The full [GameIR] for cross-system lookups
     * @return A [GenreVisitorResult] containing C functions and variable declarations
     */
    fun visit(
        systemType: String,
        systemConfig: Map<String, Any>,
        gameIR: GameIR,
    ): GenreVisitorResult
}
