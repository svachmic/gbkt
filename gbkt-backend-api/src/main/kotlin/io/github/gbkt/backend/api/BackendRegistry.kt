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
 *
 * Thread safety: each thread gets its own isolated registry state via [ThreadLocal]. This prevents
 * [clear] in one test from wiping backends registered by another test during parallel execution.
 * ServiceLoader discovery results are cached globally and copied into each thread's local state.
 */
object BackendRegistry {

    /** Globally discovered backends (populated once, never cleared). */
    private val globalBackends: MutableMap<String, CodegenBackend> = mutableMapOf()
    private var globalDiscovered = false
    private val globalLock = Any()

    /** Per-thread registry state, isolated for parallel test safety. */
    private class RegistryState {
        val backends: MutableMap<String, CodegenBackend> = mutableMapOf()
        var initialized = false
    }

    private val threadState = ThreadLocal.withInitial { RegistryState() }

    /** Ensure thread-local state is seeded from global discovery. */
    private fun ensureInitialized(): RegistryState {
        val state = threadState.get()
        if (!state.initialized) {
            synchronized(globalLock) {
                if (!globalDiscovered) {
                    val loader = ServiceLoader.load(CodegenBackend::class.java)
                    for (backend in loader) {
                        globalBackends[backend.id] = backend
                    }
                    globalDiscovered = true
                }
                state.backends.putAll(globalBackends)
            }
            state.initialized = true
        }
        return state
    }

    /**
     * Discover all available backends using ServiceLoader.
     *
     * This method is idempotent - calling it multiple times has no effect after the first call.
     *
     * @return List of discovered backends
     */
    fun discover(): List<CodegenBackend> {
        val state = ensureInitialized()
        return state.backends.values.toList()
    }

    /**
     * Manually register a backend.
     *
     * Useful for testing or when not using ServiceLoader.
     *
     * @param backend The backend to register
     */
    fun register(backend: CodegenBackend) {
        val state = ensureInitialized()
        state.backends[backend.id] = backend
    }

    /**
     * Get a backend by its ID.
     *
     * @param id Backend identifier (e.g., "gbdk")
     * @return The backend, or null if not found
     */
    fun forId(id: String): CodegenBackend? {
        val state = ensureInitialized()
        return state.backends[id]
    }

    /**
     * Get a backend for a target platform ID.
     *
     * @param targetId Target platform identifier (e.g., "gbc", "gb")
     * @return The backend supporting this target, or null if not found
     */
    fun forTarget(targetId: String): CodegenBackend? {
        val state = ensureInitialized()
        return state.backends.values.find { it.profile.id == targetId }
    }

    /** Get all registered backends. */
    fun all(): List<CodegenBackend> = discover()

    /** Get all supported target platform IDs. */
    fun supportedTargets(): List<String> {
        val state = ensureInitialized()
        return state.backends.values.map { it.profile.id }.distinct()
    }

    /**
     * Clear all registered backends for the current thread.
     *
     * Only affects the calling thread's registry state — safe for parallel test execution.
     */
    fun clear() {
        val state = threadState.get()
        state.backends.clear()
        state.initialized = false
    }
}
