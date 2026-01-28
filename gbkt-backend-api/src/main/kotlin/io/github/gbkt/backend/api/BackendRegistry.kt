/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import java.util.ServiceLoader

/**
 * Registry for discovering and accessing codegen backends.
 *
 * Backends are discovered via Java's ServiceLoader mechanism. To register a backend:
 * 1. Implement [CodegenBackend]
 * 2. Create `META-INF/services/io.github.gbkt.backend.api.CodegenBackend`
 * 3. Add the fully qualified class name of your implementation
 */
object BackendRegistry {
    private val backends: MutableMap<String, CodegenBackend> = mutableMapOf()
    private var discovered = false

    /**
     * Discover all available backends using ServiceLoader.
     *
     * This method is idempotent - calling it multiple times has no effect after the first call.
     *
     * @return List of discovered backends
     */
    @Synchronized
    fun discover(): List<CodegenBackend> {
        if (!discovered) {
            val loader = ServiceLoader.load(CodegenBackend::class.java)
            for (backend in loader) {
                register(backend)
            }
            discovered = true
        }
        return backends.values.toList()
    }

    /**
     * Manually register a backend.
     *
     * Useful for testing or when not using ServiceLoader.
     *
     * @param backend The backend to register
     */
    @Synchronized
    fun register(backend: CodegenBackend) {
        backends[backend.id] = backend
    }

    /**
     * Get a backend by its ID.
     *
     * @param id Backend identifier (e.g., "gbdk")
     * @return The backend, or null if not found
     */
    fun forId(id: String): CodegenBackend? {
        discover() // Ensure discovery has run
        return backends[id]
    }

    /**
     * Get a backend for a target platform ID.
     *
     * @param targetId Target platform identifier (e.g., "gbc", "gb")
     * @return The backend supporting this target, or null if not found
     */
    fun forTarget(targetId: String): CodegenBackend? {
        discover() // Ensure discovery has run
        return backends.values.find { it.profile.id == targetId }
    }

    /** Get all registered backends. */
    fun all(): List<CodegenBackend> {
        discover() // Ensure discovery has run
        return backends.values.toList()
    }

    /** Get all supported target platform IDs. */
    fun supportedTargets(): List<String> {
        discover()
        return backends.values.map { it.profile.id }.distinct()
    }

    /**
     * Clear all registered backends.
     *
     * Primarily for testing.
     */
    @Synchronized
    fun clear() {
        backends.clear()
        discovered = false
    }
}
