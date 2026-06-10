/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

// =============================================================================
// COLLECTION CODEGEN BACKEND TRAIT
// =============================================================================

/**
 * Backend trait interface for generating C code for static collection types.
 *
 * Each backend (GBDK, GBA, etc.) that wishes to support collection abstractions implements this
 * interface and mixes it into its [CodegenBackend] implementation.
 *
 * Collections are split into two code sections:
 * - **Data**: Static variable declarations in the appropriate ROM/RAM bank (emitted at file scope)
 * - **Functions**: Helper functions (init, lookup, insert, etc.) also at file scope
 *
 * Type parameters are passed as C type name strings (e.g. "UINT8", "UINT16") using
 * [io.github.gbkt.core.ir.GBVar.VarType.cType].
 */
interface CollectionCodegen {

    // -----------------------------------------------------------------------
    // Hash table
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a hash table.
     *
     * Expected output: Three parallel arrays — `{name}_keys[]`, `{name}_values[]`, `{name}_used[]`
     * — pre-zeroed so the table starts empty.
     *
     * @param name C identifier for the table
     * @param keyType C type name for keys (e.g. "UINT8")
     * @param valueType C type name for values (e.g. "UINT16")
     * @param size Number of slots
     * @return C source fragment (no trailing newline required)
     */
    fun generateHashTableData(name: String, keyType: String, valueType: String, size: Int): String

    /**
     * Generate helper functions for a hash table (init, lookup, insert, clear).
     *
     * @param name C identifier for the table
     * @param keyType C type name for keys
     * @param valueType C type name for values
     * @param size Number of slots
     * @return C source fragment containing function definitions
     */
    fun generateHashTableFunctions(
        name: String,
        keyType: String,
        valueType: String,
        size: Int,
    ): String

    // -----------------------------------------------------------------------
    // Object pool
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for an object pool.
     *
     * Expected output: `{name}_data[]` element array + `{name}_free[]` free-list bitmap +
     * `{name}_count` active-count variable.
     *
     * @param name C identifier for the pool
     * @param elementType C type name for each element (e.g. "UINT8")
     * @param capacity Maximum number of live elements
     * @return C source fragment
     */
    fun generatePoolData(name: String, elementType: String, capacity: Int): String

    /**
     * Generate helper functions for an object pool (alloc, free, activeCount, hasSpace).
     *
     * @param name C identifier for the pool
     * @param elementType C type name for each element
     * @param capacity Maximum number of live elements
     * @return C source fragment containing function definitions
     */
    fun generatePoolFunctions(name: String, elementType: String, capacity: Int): String

    // -----------------------------------------------------------------------
    // Ring buffer
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a ring buffer.
     *
     * Expected output: `{name}_buf[]` element array + `{name}_head`, `{name}_tail`, `{name}_count`
     * bookkeeping bytes.
     *
     * @param name C identifier for the buffer
     * @param elementType C type name for each element (e.g. "UINT8")
     * @param capacity Maximum number of buffered elements
     * @return C source fragment
     */
    fun generateRingBufferData(name: String, elementType: String, capacity: Int): String

    /**
     * Generate helper functions for a ring buffer (push, pop, peek, count).
     *
     * @param name C identifier for the buffer
     * @param elementType C type name for each element
     * @param capacity Maximum number of buffered elements
     * @return C source fragment containing function definitions
     */
    fun generateRingBufferFunctions(name: String, elementType: String, capacity: Int): String

    // -----------------------------------------------------------------------
    // Fixed slots
    // -----------------------------------------------------------------------

    /**
     * Generate static data declarations for a fixed-slots collection.
     *
     * Expected output: `{name}_data[]` slot array + `{name}_active` bitfield (UINT8 for ≤8 slots,
     * UINT16 for 9–16 slots).
     *
     * @param name C identifier for the fixed-slots collection
     * @param elementType C type name for each slot element (e.g. "UINT8")
     * @param count Number of fixed slots (1–16)
     * @return C source fragment
     */
    fun generateFixedSlotsData(name: String, elementType: String, count: Int): String

    /**
     * Generate helper functions for a fixed-slots collection (claim, release, isActive).
     *
     * @param name C identifier for the fixed-slots collection
     * @param elementType C type name for each slot element
     * @param count Number of fixed slots (1–16)
     * @return C source fragment containing function definitions
     */
    fun generateFixedSlotsFunctions(name: String, elementType: String, count: Int): String
}
